package com.neueda.app.services;

import com.neueda.app.dtos.PieSliceResponse;
import com.neueda.app.dtos.PublicPieResponse;
import com.neueda.app.dtos.PublicPieSummary;
import com.neueda.app.enums.PieVisibility;
import com.neueda.app.exceptions.PieNotFoundException;
import com.neueda.app.models.Pie;
import com.neueda.app.models.PieSlice;
import com.neueda.app.repositories.AccountPieRepository;
import com.neueda.app.repositories.AccountPieRepository.FollowerCount;
import com.neueda.app.repositories.PieRepository;
import com.neueda.app.repositories.PieSliceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PublicPieServiceTest {
    private PieRepository pieRepository;
    private PieSliceRepository pieSliceRepository;
    private AccountPieRepository accountPieRepository;
    private PieService pieService;
    private PublicPieService service;

    @BeforeEach
    void setUp() {
        pieRepository = mock(PieRepository.class);
        pieSliceRepository = mock(PieSliceRepository.class);
        accountPieRepository = mock(AccountPieRepository.class);
        pieService = mock(PieService.class);
        service = new PublicPieService(pieRepository, pieSliceRepository, accountPieRepository, pieService);
    }

    private static Pie publicPie(String owner, String name) {
        Pie pie = Pie.create(owner, name, "about " + name);
        pie.publish(LocalDateTime.now());
        return pie;
    }

    private static FollowerCount followers(UUID pieId, long count) {
        return new FollowerCount() {
            @Override public UUID getPieId() { return pieId; }
            @Override public long getFollowers() { return count; }
        };
    }

    @Test
    void newlyPublishedPiesAppearImmediatelyOrderedByNameRegardlessOfFollowers() {
        Pie zulu = publicPie("ana", "Zulu");
        Pie beta = publicPie("bob", "beta");
        Pie alpha = publicPie("cy", "Alpha");
        when(pieRepository.findByVisibilityAndDeletedAtIsNull(PieVisibility.PUBLIC))
            .thenReturn(List.of(zulu, beta, alpha));
        when(accountPieRepository.countFollowers(anyCollection()))
            .thenReturn(List.of(followers(zulu.getPieId(), 9), followers(beta.getPieId(), 1)));
        when(pieSliceRepository.countByPieIdAndVersion(any(), anyInt())).thenReturn(3);

        List<PublicPieSummary> list = service.list();

        assertEquals(List.of("Alpha", "beta", "Zulu"), list.stream().map(PublicPieSummary::name).toList());
        assertEquals(0, list.get(0).followers());
        assertEquals(9, list.get(2).followers());
        assertEquals(3, list.get(0).sliceCount());
        assertEquals("cy", list.get(0).authorUsername());
        assertEquals(alpha.getPublishedAt(), list.get(0).publishedAt());
    }

    @Test
    void namesEqualIgnoringCaseHaveStableIdOrder() {
        Pie second = publicPie("ana", "Tech");
        Pie first = publicPie("bob", "tech");
        ReflectionTestUtils.setField(first, "pieId", UUID.fromString("00000000-0000-0000-0000-000000000001"));
        ReflectionTestUtils.setField(second, "pieId", UUID.fromString("00000000-0000-0000-0000-000000000002"));
        when(pieRepository.findByVisibilityAndDeletedAtIsNull(PieVisibility.PUBLIC)).thenReturn(List.of(second, first));

        assertEquals(List.of(first.getPieId(), second.getPieId()), service.list().stream().map(PublicPieSummary::pieId).toList());
    }

    @Test
    void onlyReservedDemoAuthorIsMarkedAsDemo() {
        Pie demo = publicPie("demo", "[Demo] Tech");
        Pie lookalike = publicPie("demo2", "Not a demo");
        when(pieRepository.findByVisibilityAndDeletedAtIsNull(PieVisibility.PUBLIC)).thenReturn(List.of(demo, lookalike));

        List<PublicPieSummary> list = service.list();

        assertEquals(1, list.stream().filter(PublicPieSummary::demo).count());
        assertEquals("[Demo] Tech", list.stream().filter(PublicPieSummary::demo).findFirst().orElseThrow().name());
    }

    @Test
    void noPublicPiesReturnsEmptyList() {
        when(pieRepository.findByVisibilityAndDeletedAtIsNull(PieVisibility.PUBLIC)).thenReturn(List.of());
        assertTrue(service.list().isEmpty());
    }

    @Test
    void publicDetailsIncludeCompositionAndFollowerCount() {
        Pie pie = publicPie("ana", "Tech");
        List<PieSlice> slices = List.of(new PieSlice(pie.getPieId(), 1, "AAPL", new BigDecimal("60")));
        List<PieSliceResponse> sliceResponses = List.of(new PieSliceResponse("AAPL", "Apple", new BigDecimal("60")));
        when(pieRepository.findByPieIdAndVisibilityAndDeletedAtIsNull(pie.getPieId(), PieVisibility.PUBLIC))
            .thenReturn(Optional.of(pie));
        when(pieService.latestSlices(pie)).thenReturn(slices);
        when(pieService.sliceResponses(slices)).thenReturn(sliceResponses);
        when(accountPieRepository.countFollowers(List.of(pie.getPieId()))).thenReturn(List.of(followers(pie.getPieId(), 7)));

        PublicPieResponse response = service.get(pie.getPieId());

        assertEquals("ana", response.authorUsername());
        assertEquals("about Tech", response.description());
        assertEquals(sliceResponses, response.slices());
        assertEquals(1, response.sliceCount());
        assertEquals(new BigDecimal("40"), response.cashPct());
        assertEquals(7, response.followers());
        assertFalse(response.demo());
    }

    @Test
    void privateDeletedOrUnknownPieIsNotFound() {
        UUID id = UUID.randomUUID();
        when(pieRepository.findByPieIdAndVisibilityAndDeletedAtIsNull(id, PieVisibility.PUBLIC)).thenReturn(Optional.empty());
        assertThrows(PieNotFoundException.class, () -> service.get(id));
    }
}
