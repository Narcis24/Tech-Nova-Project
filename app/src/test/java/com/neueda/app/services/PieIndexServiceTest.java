package com.neueda.app.services;

import com.neueda.app.models.Pie;
import com.neueda.app.models.PieIndexPoint;
import com.neueda.app.models.PieSlice;
import com.neueda.app.models.PieVersion;
import com.neueda.app.models.Price;
import com.neueda.app.repositories.PieIndexPointRepository;
import com.neueda.app.repositories.PieRepository;
import com.neueda.app.repositories.PieSliceRepository;
import com.neueda.app.repositories.PieVersionRepository;
import com.neueda.app.repositories.PriceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest
@ActiveProfiles("test")
@Import({PieIndexService.class, PieIndexServiceTest.FixedClock.class})
class PieIndexServiceTest {

    @TestConfiguration
    static class FixedClock {
        @Bean
        Clock clock() {
            return Clock.fixed(LocalDateTime.of(2026, 3, 10, 8, 0).toInstant(ZoneOffset.UTC), ZoneOffset.UTC);
        }
    }

    @Autowired private PieIndexService service;
    @Autowired private TestEntityManager entityManager;
    @Autowired private PieRepository pies;
    @Autowired private PieVersionRepository versions;
    @Autowired private PieSliceRepository slices;
    @Autowired private PieIndexPointRepository points;
    @Autowired private PriceRepository prices;

    private static BigDecimal d(String value) {
        return new BigDecimal(value);
    }

    private static LocalDate day(int dayOfMarch) {
        return LocalDate.of(2026, 3, dayOfMarch);
    }

    private Pie pie;

    @BeforeEach
    void setUp() {
        pie = Pie.create("jay", "Mix", null);
        pie.publish(LocalDateTime.of(2026, 3, 5, 10, 0));
        pies.save(pie);
    }

    private void version(int version, LocalDateTime createdAt, String... symbolWeight) {
        versions.save(new PieVersion(pie.getPieId(), version, createdAt));
        for (int i = 0; i < symbolWeight.length; i += 2) {
            slices.save(new PieSlice(pie.getPieId(), version, symbolWeight[i], d(symbolWeight[i + 1])));
        }
    }

    private void close(String symbol, int dayOfMarch, String close) {
        prices.save(new Price(symbol, day(dayOfMarch), d(close)));
    }

    private BigDecimal valueOn(int dayOfMarch) {
        return points.findByPieIdOrderByDay(pie.getPieId()).stream()
            .filter(p -> p.getDay().equals(day(dayOfMarch))).findFirst().orElseThrow().getValue();
    }

    @Test
    void next_cashStaysPutAndSlicesMoveWithTheirPrice() {
        BigDecimal value = PieIndexService.next(d("100"), Map.of("AAPL", d("50")),
            Map.of("AAPL", d("100")), Map.of("AAPL", d("200")));

        assertEquals(d("150.000000"), value);
    }

    @Test
    void next_missingOrNonPositivePriceCountsAsNoChange() {
        Map<String, BigDecimal> weights = Map.of("AAPL", d("40"), "MSFT", d("30"), "GOOG", d("30"));

        BigDecimal value = PieIndexService.next(d("100"), weights,
            Map.of("AAPL", d("100"), "MSFT", d("0")), Map.of("AAPL", d("110"), "MSFT", d("50")));

        // AAPL +10% on 40%, MSFT has a non-positive close, GOOG has no price at all
        assertEquals(d("104.000000"), value);
    }

    @Test
    void next_compoundsOnThePreviousValue_roundedToSixPlaces() {
        BigDecimal value = PieIndexService.next(d("103.456789"), Map.of("AAPL", d("100")),
            Map.of("AAPL", d("3")), Map.of("AAPL", d("4")));

        assertEquals(d("137.942385"), value);
    }

    @Test
    void catchUp_startsAtPublishDay_andFollowsTheVersionLiveEachDay() {
        version(1, LocalDateTime.of(2026, 3, 1, 9, 0), "AAPL", "50", "MSFT", "25");
        // made during Saturday 7 March, so it first counts on Sunday 8 March
        version(2, LocalDateTime.of(2026, 3, 7, 15, 0), "AAPL", "100");
        close("AAPL", 4, "100"); close("AAPL", 5, "100"); close("AAPL", 6, "110"); close("AAPL", 9, "99");
        close("MSFT", 4, "200"); close("MSFT", 5, "200"); close("MSFT", 6, "200"); close("MSFT", 9, "220");

        service.catchUp(pie.getPieId());

        assertEquals(d("100.000000"), valueOn(5));
        // version 1: 25% cash + 50% x 1.10 + 25% x 1.00
        assertEquals(d("105.000000"), valueOn(6));
        assertEquals(d("105.000000"), valueOn(7)); // weekend, no new close, still version 1
        assertEquals(d("105.000000"), valueOn(8)); // version 2 now, nothing moved
        // version 2 is all AAPL: 99 against the Friday close of 110, so MSFT's jump no longer counts
        assertEquals(d("94.500000"), valueOn(9));
        assertEquals(5, points.findByPieIdOrderByDay(pie.getPieId()).size());
    }

    @Test
    void catchUp_carriesLastCloseForwardOverGapsAndUnknownSymbols() {
        version(1, LocalDateTime.of(2026, 3, 1, 9, 0), "AAPL", "60", "NOPRICE", "40");
        close("AAPL", 5, "100"); close("AAPL", 8, "120");

        service.catchUp(pie.getPieId());

        assertEquals(d("100.000000"), valueOn(6));
        assertEquals(d("100.000000"), valueOn(7));
        // Sunday's close is Friday's carried forward, which is the 03-08 bar here: +20% on 60%
        assertEquals(d("112.000000"), valueOn(8));
        assertEquals(d("112.000000"), valueOn(9));
    }

    @Test
    void catchUp_runningAgainOrAfterAMissedNightChangesNothing() {
        version(1, LocalDateTime.of(2026, 3, 1, 9, 0), "AAPL", "100");
        close("AAPL", 5, "100"); close("AAPL", 6, "110"); close("AAPL", 9, "121");

        service.catchUp(pie.getPieId());
        List<BigDecimal> first = points.findByPieIdOrderByDay(pie.getPieId()).stream().map(PieIndexPoint::getValue).toList();
        service.catchUp(pie.getPieId());

        assertEquals(first, points.findByPieIdOrderByDay(pie.getPieId()).stream().map(PieIndexPoint::getValue).toList());
        assertEquals(5, first.size());
        assertEquals(d("121.000000"), valueOn(9));
    }

    @Test
    void catchUp_resumesFromTheLastRecordedDay() {
        version(1, LocalDateTime.of(2026, 3, 1, 9, 0), "AAPL", "100");
        close("AAPL", 5, "100"); close("AAPL", 6, "110"); close("AAPL", 9, "121");
        points.save(new PieIndexPoint(pie.getPieId(), day(5), d("100")));
        points.save(new PieIndexPoint(pie.getPieId(), day(6), d("110")));

        service.catchUp(pie.getPieId());

        assertEquals(d("110"), valueOn(6));
        assertEquals(d("110.000000"), valueOn(7));
        assertEquals(d("121.000000"), valueOn(9));
    }

    @Test
    void catchUp_pieMadeVersionAfterDayEnds_cannotChangeThatDay() {
        version(1, LocalDateTime.of(2026, 3, 1, 9, 0), "AAPL", "100");
        close("AAPL", 5, "100"); close("AAPL", 6, "50"); close("AAPL", 9, "50");
        // changed to cash only at 00:00:00 on the 6th: not yet "before" that day started
        version(2, LocalDateTime.of(2026, 3, 6, 0, 0), "MSFT", "1");

        service.catchUp(pie.getPieId());

        assertEquals(d("50.000000"), valueOn(6));
    }

    @Test
    void catchUp_publishedToday_onlyHasTheStartingPoint() {
        pie.publish(LocalDateTime.of(2026, 3, 10, 1, 0));
        pies.save(pie);
        version(1, LocalDateTime.of(2026, 3, 1, 9, 0), "AAPL", "100");

        service.catchUp(pie.getPieId());

        List<PieIndexPoint> found = points.findByPieIdOrderByDay(pie.getPieId());
        assertEquals(1, found.size());
        assertEquals(day(10), found.get(0).getDay());
    }

    @Test
    void catchUp_ignoresPrivateAndDeletedPies() {
        Pie priv = pies.save(Pie.create("jay", "Private", null));
        Pie gone = Pie.create("jay", "Gone", null);
        gone.publish(LocalDateTime.of(2026, 3, 5, 10, 0));
        gone.setDeletedAt(LocalDateTime.of(2026, 3, 6, 10, 0));
        pies.save(gone);

        service.catchUp(priv.getPieId());
        service.catchUp(gone.getPieId());
        service.catchUp(UUID.randomUUID());

        assertTrue(points.findAll().isEmpty());
    }

    @Test
    void restart_dropsOldHistoryAndStartsAt100() {
        points.save(new PieIndexPoint(pie.getPieId(), day(5), d("100")));
        points.save(new PieIndexPoint(pie.getPieId(), day(6), d("180")));
        entityManager.flush();
        entityManager.clear();

        service.restart(pie.getPieId(), day(5));

        List<PieIndexPoint> found = points.findByPieIdOrderByDay(pie.getPieId());
        assertEquals(1, found.size());
        assertEquals(d("100.000000"), found.get(0).getValue());
    }

    @Test
    void clear_removesOnlyThisPiesPoints() {
        Pie other = pies.save(Pie.create("ana", "Other", null));
        points.save(new PieIndexPoint(pie.getPieId(), day(5), d("100")));
        points.save(new PieIndexPoint(other.getPieId(), day(5), d("100")));

        service.clear(pie.getPieId());

        assertTrue(points.findByPieIdOrderByDay(pie.getPieId()).isEmpty());
        assertEquals(1, points.findByPieIdOrderByDay(other.getPieId()).size());
    }
}
