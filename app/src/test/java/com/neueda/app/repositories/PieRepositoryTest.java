package com.neueda.app.repositories;

import com.neueda.app.models.AccountPie;
import com.neueda.app.models.Pie;
import com.neueda.app.models.PieSlice;
import com.neueda.app.models.PieVersion;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

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
}
