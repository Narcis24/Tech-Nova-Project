package com.neueda.app.services;

import com.neueda.app.dtos.PieSliceResponse;
import com.neueda.app.dtos.PublicPieResponse;
import com.neueda.app.dtos.PublicPieSummary;
import com.neueda.app.enums.PieVisibility;
import com.neueda.app.enums.PieWindow;
import com.neueda.app.exceptions.PieNotFoundException;
import com.neueda.app.models.Pie;
import com.neueda.app.models.PieIndexPoint;
import com.neueda.app.models.PieSlice;
import com.neueda.app.repositories.AccountPieRepository;
import com.neueda.app.repositories.AccountPieRepository.FollowerCount;
import com.neueda.app.repositories.PieIndexPointRepository;
import com.neueda.app.repositories.PieRepository;
import com.neueda.app.repositories.PieSliceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PublicPieServiceTest {

    private static final LocalDate START = LocalDate.of(2026, 1, 1);

    private PieRepository pieRepository;
    private PieSliceRepository pieSliceRepository;
    private PieIndexPointRepository pointRepository;
    private AccountPieRepository accountPieRepository;
    private PieService pieService;
    private PublicPieService service;

    @BeforeEach
    void setUp() {
        pieRepository = mock(PieRepository.class);
        pieSliceRepository = mock(PieSliceRepository.class);
        pointRepository = mock(PieIndexPointRepository.class);
        accountPieRepository = mock(AccountPieRepository.class);
        pieService = mock(PieService.class);
        service = new PublicPieService(pieRepository, pieSliceRepository, pointRepository, accountPieRepository, pieService);
    }

    /** Daily points from START, valued 100, 101, 102 ... */
    private static List<PieIndexPoint> points(UUID pieId, int count) {
        List<PieIndexPoint> points = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            points.add(new PieIndexPoint(pieId, START.plusDays(i), new BigDecimal(100 + i)));
        }
        return points;
    }

    private static Pie publicPie(String owner, String name, int publishedDay) {
        Pie pie = Pie.create(owner, name, "about " + name);
        pie.publish(START.plusDays(publishedDay).atStartOfDay());
        return pie;
    }

    private static FollowerCount followers(UUID pieId, long count) {
        return new FollowerCount() {
            @Override
            public UUID getPieId() {
                return pieId;
            }

            @Override
            public long getFollowers() {
                return count;
            }
        };
    }

    @Test
    void returnPct_windowNeedsThatMuchHistory() {
        UUID id = UUID.randomUUID();

        // 31 points are exactly 30 days of history, 30 points are one day short
        assertEquals(new BigDecimal("30.00"), PublicPieService.returnPct(points(id, 31), PieWindow.MONTH));
        assertNull(PublicPieService.returnPct(points(id, 30), PieWindow.MONTH));
        assertNull(PublicPieService.returnPct(points(id, 7), PieWindow.WEEK));
        assertEquals(new BigDecimal("7.00"), PublicPieService.returnPct(points(id, 8), PieWindow.WEEK));
    }

    @Test
    void returnPct_windowEndsAtLatestPoint() {
        UUID id = UUID.randomUUID();
        List<PieIndexPoint> points = points(id, 100); // 100 .. 199

        assertEquals(new BigDecimal("3.65"), PublicPieService.returnPct(points, PieWindow.WEEK));   // 199 / 192
        assertEquals(new BigDecimal("82.57"), PublicPieService.returnPct(points, PieWindow.QUARTER)); // 199 / 109
        assertEquals(new BigDecimal("99.00"), PublicPieService.returnPct(points, PieWindow.ALL));
    }

    @Test
    void returnPct_allNeedsNoAge_butNeedsAPoint() {
        UUID id = UUID.randomUUID();

        assertEquals(new BigDecimal("0.00"), PublicPieService.returnPct(points(id, 1), PieWindow.ALL));
        assertNull(PublicPieService.returnPct(List.of(), PieWindow.ALL));
    }

    @Test
    void returnPct_gapsUseTheLatestPointOnOrBeforeTheWindowStart() {
        UUID id = UUID.randomUUID();
        List<PieIndexPoint> points = List.of(
            new PieIndexPoint(id, START, new BigDecimal("100")),
            new PieIndexPoint(id, START.plusDays(3), new BigDecimal("110")),
            new PieIndexPoint(id, START.plusDays(10), new BigDecimal("121")));

        // window start is day 3 exactly
        assertEquals(new BigDecimal("10.00"), PublicPieService.returnPct(points, PieWindow.WEEK));
    }

    @Test
    void list_ranksByReturn_dropsYoungPies_andBreaksTiesByFollowers() {
        Pie best = publicPie("ana", "Best", 0);
        Pie tieFew = publicPie("bob", "TieFew", 0);
        Pie tieMany = publicPie("cy", "TieMany", 1);
        Pie young = publicPie("dee", "Young", 90);
        when(pieRepository.findByVisibilityAndDeletedAtIsNull(PieVisibility.PUBLIC))
            .thenReturn(List.of(tieFew, young, tieMany, best));
        List<PieIndexPoint> all = new ArrayList<>();
        all.addAll(points(best.getPieId(), 100));
        all.addAll(points(tieFew.getPieId(), 50));   // 100..149 over its last 7 days
        all.addAll(points(tieMany.getPieId(), 50));
        all.addAll(points(young.getPieId(), 3));     // not 7 days old yet
        when(pointRepository.findByPieIdInOrderByDay(anyCollection())).thenReturn(all);
        when(accountPieRepository.countFollowers(anyCollection()))
            .thenReturn(List.of(followers(tieMany.getPieId(), 4), followers(tieFew.getPieId(), 1)));
        when(pieSliceRepository.countByPieIdAndVersion(any(), org.mockito.ArgumentMatchers.anyInt())).thenReturn(3);

        List<PublicPieSummary> week = service.list("7d");

        // best: 199 / 192 = 3.65%. The two 50-point pies: 149 / 142 = 4.93%, so they lead; more followers first
        assertEquals(List.of("TieMany", "TieFew", "Best"), week.stream().map(PublicPieSummary::name).toList());
        assertEquals(new BigDecimal("4.93"), week.get(0).returnPct());
        assertEquals(4, week.get(0).followers());
        assertEquals(0, week.get(2).followers());
        assertEquals(3, week.get(0).sliceCount());
        assertEquals("cy", week.get(0).authorUsername());
        assertEquals(tieMany.getPublishedAt(), week.get(0).publishedAt());
    }

    @Test
    void list_allWindowIncludesEveryPublicPieWithAPoint() {
        Pie young = publicPie("dee", "Young", 90);
        when(pieRepository.findByVisibilityAndDeletedAtIsNull(PieVisibility.PUBLIC)).thenReturn(List.of(young));
        when(pointRepository.findByPieIdInOrderByDay(anyCollection())).thenReturn(points(young.getPieId(), 1));
        when(accountPieRepository.countFollowers(anyCollection())).thenReturn(List.of());

        List<PublicPieSummary> all = service.list("ALL");

        assertEquals(1, all.size());
        assertEquals(new BigDecimal("0.00"), all.get(0).returnPct());
        assertTrue(service.list("90D").isEmpty());
    }

    @Test
    void list_marksOnlyTheReservedAuthorAsDemo() {
        Pie demo = publicPie("demo", "[Demo] Tech", 0);
        Pie lookalike = publicPie("demo2", "Not a demo", 0);
        when(pieRepository.findByVisibilityAndDeletedAtIsNull(PieVisibility.PUBLIC)).thenReturn(List.of(demo, lookalike));
        List<PieIndexPoint> all = new ArrayList<>(points(demo.getPieId(), 10));
        all.addAll(points(lookalike.getPieId(), 10));
        when(pointRepository.findByPieIdInOrderByDay(anyCollection())).thenReturn(all);
        when(accountPieRepository.countFollowers(anyCollection())).thenReturn(List.of());

        List<PublicPieSummary> list = service.list("ALL");

        assertEquals(1, list.stream().filter(PublicPieSummary::demo).count());
        assertEquals("[Demo] Tech", list.stream().filter(PublicPieSummary::demo).findFirst().orElseThrow().name());
    }

    @Test
    void list_noPublicPies_isEmpty_andBadWindowIsRejected() {
        when(pieRepository.findByVisibilityAndDeletedAtIsNull(PieVisibility.PUBLIC)).thenReturn(List.of());

        assertTrue(service.list("ALL").isEmpty());
        assertThrows(IllegalArgumentException.class, () -> service.list("1Y"));
    }

    @Test
    void get_returnsSlicesPointsReturnsAndFollowers() {
        Pie pie = publicPie("ana", "Tech", 0);
        List<PieSlice> slices = List.of(new PieSlice(pie.getPieId(), 1, "AAPL", new BigDecimal("60")));
        when(pieRepository.findByPieIdAndVisibilityAndDeletedAtIsNull(pie.getPieId(), PieVisibility.PUBLIC))
            .thenReturn(Optional.of(pie));
        when(pieService.latestSlices(pie)).thenReturn(slices);
        when(pieService.sliceResponses(slices)).thenReturn(List.of(new PieSliceResponse("AAPL", "Apple", new BigDecimal("60"))));
        when(pointRepository.findByPieIdOrderByDay(pie.getPieId())).thenReturn(points(pie.getPieId(), 31));
        when(accountPieRepository.countFollowers(List.of(pie.getPieId()))).thenReturn(List.of(followers(pie.getPieId(), 7)));

        PublicPieResponse response = service.get(pie.getPieId());

        assertEquals("ana", response.authorUsername());
        assertEquals("about Tech", response.description());
        assertEquals(1, response.sliceCount());
        assertEquals(new BigDecimal("40"), response.cashPct());
        assertEquals(7, response.followers());
        assertFalse(response.demo());
        assertEquals(31, response.points().size());
        assertEquals(START, response.points().get(0).date());
        assertEquals(new BigDecimal("100"), response.points().get(0).value());
        assertEquals(List.of("7D", "30D", "90D", "ALL"), List.copyOf(response.returns().keySet()));
        assertEquals(new BigDecimal("30.00"), response.returns().get("30D"));
        assertNull(response.returns().get("90D"));
        assertEquals(response.returns().get("ALL"), response.returnPct());
    }

    @Test
    void get_privateOrDeletedOrUnknown_isNotFound() {
        UUID id = UUID.randomUUID();
        when(pieRepository.findByPieIdAndVisibilityAndDeletedAtIsNull(id, PieVisibility.PUBLIC)).thenReturn(Optional.empty());

        assertThrows(PieNotFoundException.class, () -> service.get(id));
    }
}
