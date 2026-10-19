package com.neueda.app.repositories;

import com.neueda.app.enums.PieLinkMode;
import com.neueda.app.enums.PieVisibility;
import com.neueda.app.models.AccountPie;
import com.neueda.app.models.Pie;
import com.neueda.app.models.PieSlice;
import com.neueda.app.models.PieVersion;
import com.neueda.app.models.Price;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest
@ActiveProfiles("test")
class PieRepositoryTest {

    @Autowired
    private PieRepository pies;

    @Autowired
    private PieVersionRepository versions;

    @Autowired
    private PieSliceRepository slices;

    @Autowired
    private AccountPieRepository accountPies;

    @Autowired
    private PriceRepository prices;

    @Test
    void savesPieWithVersionAndSlices_andReadsSlicesByWeight() {
        Pie pie = pies.save(Pie.create("jay", "Core", null));
        versions.save(new PieVersion(pie.getPieId(), 1, LocalDateTime.now(ZoneOffset.UTC)));
        slices.save(new PieSlice(pie.getPieId(), 1, "AAPL", new BigDecimal("30.00")));
        slices.save(new PieSlice(pie.getPieId(), 1, "VOO", new BigDecimal("50.00")));

        List<PieSlice> found = slices.findByPieIdAndVersionOrderByWeightPctDescSymbolAsc(pie.getPieId(), 1);

        assertEquals(List.of("VOO", "AAPL"), found.stream().map(PieSlice::getSymbol).toList());
    }

    @Test
    void ownerLookupIgnoresDeletedAndOtherOwners() {
        Pie kept = pies.save(Pie.create("jay", "Kept", null));
        Pie deleted = Pie.create("jay", "Gone", null);
        deleted.setDeletedAt(LocalDateTime.now(ZoneOffset.UTC));
        pies.save(deleted);
        Pie others = pies.save(Pie.create("ana", "Hers", null));

        List<Pie> mine = pies.findByOwnerUsernameAndDeletedAtIsNullOrderByCreatedAt("jay");

        assertEquals(1, mine.size());
        assertEquals(kept.getPieId(), mine.get(0).getPieId());
        assertTrue(pies.findByPieIdAndOwnerUsernameAndDeletedAtIsNull(others.getPieId(), "jay").isEmpty());
    }

    @Test
    void existsByPieIdAndAccountIdIn() {
        Pie pie = pies.save(Pie.create("jay", "Core", null));
        accountPies.save(AccountPie.own("ACC1", pie.getPieId()));

        assertTrue(accountPies.existsByPieIdAndAccountIdIn(pie.getPieId(), List.of("ACC1", "ACC2")));
        assertFalse(accountPies.existsByPieIdAndAccountIdIn(pie.getPieId(), List.of("ACC2")));
    }

    private Pie publicPie(String owner, String name) {
        Pie pie = Pie.create(owner, name, null);
        pie.publish(LocalDateTime.of(2026, 3, 1, 0, 0));
        return pies.save(pie);
    }

    @Test
    void lockOwned_findsOnlyTheOwnersLivePies() {
        Pie mine = pies.save(Pie.create("jay", "Mine", null));
        Pie deleted = Pie.create("jay", "Gone", null);
        deleted.setDeletedAt(LocalDateTime.now(ZoneOffset.UTC));
        pies.save(deleted);

        assertEquals(mine.getPieId(), pies.lockOwned(mine.getPieId(), "jay").orElseThrow().getPieId());
        assertTrue(pies.lockOwned(mine.getPieId(), "ana").isEmpty());
        assertTrue(pies.lockOwned(deleted.getPieId(), "jay").isEmpty());
    }

    @Test
    void publicQueries_skipPrivateAndDeletedPies() {
        Pie shown = publicPie("jay", "Shown");
        pies.save(Pie.create("jay", "Private", null));
        Pie deleted = publicPie("ana", "Deleted");
        deleted.setDeletedAt(LocalDateTime.now(ZoneOffset.UTC));
        pies.save(deleted);

        List<Pie> found = pies.findByVisibilityAndDeletedAtIsNull(PieVisibility.PUBLIC);

        assertEquals(List.of(shown.getPieId()), found.stream().map(Pie::getPieId).toList());
        assertTrue(pies.findByPieIdAndVisibilityAndDeletedAtIsNull(shown.getPieId(), PieVisibility.PUBLIC).isPresent());
        assertTrue(pies.findByPieIdAndVisibilityAndDeletedAtIsNull(deleted.getPieId(), PieVisibility.PUBLIC).isEmpty());
        assertTrue(pies.lockPublic(shown.getPieId()).isPresent());
        assertTrue(pies.lockPublic(deleted.getPieId()).isEmpty());
    }

    @Test
    void lockPublic_ignoresPrivatePie() {
        Pie pie = pies.save(Pie.create("jay", "Private", null));

        assertTrue(pies.lockPublic(pie.getPieId()).isEmpty());
        pie.publish(LocalDateTime.now(ZoneOffset.UTC));
        pies.save(pie);
        assertTrue(pies.lockPublic(pie.getPieId()).isPresent());
    }

    @Test
    void versionQueries_findNewVersionsAndTheOneLiveAtAnInstant() {
        Pie pie = pies.save(Pie.create("jay", "Core", null));
        LocalDateTime t1 = LocalDateTime.of(2026, 3, 1, 9, 0);
        LocalDateTime t2 = LocalDateTime.of(2026, 3, 5, 9, 0);
        versions.save(new PieVersion(pie.getPieId(), 1, t1));
        assertFalse(versions.existsByPieIdAndVersionGreaterThanAndCreatedAtAfter(pie.getPieId(), 1, t1.minusSeconds(1)));
        versions.save(new PieVersion(pie.getPieId(), 2, t2));

        assertTrue(versions.existsByPieIdAndVersionGreaterThanAndCreatedAtAfter(pie.getPieId(), 1, t2.minusSeconds(1)));
        assertFalse(versions.existsByPieIdAndVersionGreaterThanAndCreatedAtAfter(pie.getPieId(), 1, t2));
        assertEquals(2, versions.findFirstByPieIdAndCreatedAtBeforeOrderByVersionDesc(pie.getPieId(), t2.plusSeconds(1))
            .orElseThrow().getVersion());
        // created exactly at the instant is not yet live
        assertEquals(1, versions.findFirstByPieIdAndCreatedAtBeforeOrderByVersionDesc(pie.getPieId(), t2)
            .orElseThrow().getVersion());
        assertTrue(versions.findFirstByPieIdAndCreatedAtBeforeOrderByVersionDesc(pie.getPieId(), t1).isEmpty());
    }

    @Test
    void countFollowers_countsCopiesOnly() {
        Pie pie = publicPie("jay", "Core");
        Pie other = publicPie("jay", "Other");
        accountPies.save(AccountPie.own("OWNER_ACC", pie.getPieId()));
        for (String account : List.of("C1", "C2")) {
            AccountPie copy = AccountPie.own(account, pie.getPieId());
            ReflectionTestUtils.setField(copy, "mode", PieLinkMode.COPY);
            accountPies.save(copy);
        }

        Map<UUID, Long> counts = accountPies.countFollowers(List.of(pie.getPieId(), other.getPieId())).stream()
            .collect(Collectors.toMap(AccountPieRepository.FollowerCount::getPieId, AccountPieRepository.FollowerCount::getFollowers));

        assertEquals(Map.of(pie.getPieId(), 2L), counts);
    }

    @Test
    void findClosesOnOrBefore_isTheLatestCloseByThatDay() {
        prices.save(new Price("AAPL", LocalDate.of(2026, 3, 5), new BigDecimal("100")));
        prices.save(new Price("AAPL", LocalDate.of(2026, 3, 9), new BigDecimal("120")));
        prices.save(new Price("MSFT", LocalDate.of(2026, 3, 9), new BigDecimal("300")));

        Map<String, BigDecimal> weekend = prices.findClosesOnOrBefore(List.of("AAPL", "MSFT", "NOPE"), LocalDate.of(2026, 3, 8))
            .stream().collect(Collectors.toMap(Price::getSymbol, Price::getPrice));
        Map<String, BigDecimal> monday = prices.findClosesOnOrBefore(List.of("AAPL", "MSFT"), LocalDate.of(2026, 3, 9))
            .stream().collect(Collectors.toMap(Price::getSymbol, Price::getPrice));

        assertEquals(0, new BigDecimal("100").compareTo(weekend.get("AAPL")));
        assertEquals(1, weekend.size()); // MSFT has no bar yet, NOPE never will
        assertEquals(0, new BigDecimal("120").compareTo(monday.get("AAPL")));
        assertEquals(0, new BigDecimal("300").compareTo(monday.get("MSFT")));
    }
}
