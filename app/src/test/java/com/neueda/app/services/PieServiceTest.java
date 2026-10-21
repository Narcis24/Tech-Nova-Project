package com.neueda.app.services;

import com.neueda.app.dtos.PieResponse;
import com.neueda.app.enums.PieVisibility;
import com.neueda.app.dtos.PieSliceRequest;
import com.neueda.app.dtos.SavePieRequest;
import com.neueda.app.exceptions.InvalidPieException;
import com.neueda.app.exceptions.PieInUseException;
import com.neueda.app.exceptions.PieNotFoundException;
import com.neueda.app.models.Account;
import com.neueda.app.models.AccountPie;
import com.neueda.app.models.Instrument;
import com.neueda.app.models.Pie;
import com.neueda.app.models.PieSlice;
import com.neueda.app.models.PieVersion;
import com.neueda.app.repositories.AccountPieRepository;
import com.neueda.app.repositories.AccountRepository;
import com.neueda.app.repositories.InstrumentRepository;
import com.neueda.app.repositories.PieRepository;
import com.neueda.app.repositories.PieSliceRepository;
import com.neueda.app.repositories.PieVersionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;


import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class PieServiceTest {

    private static final String OWNER = "jay";

    private PieRepository pieRepository;
    private PieVersionRepository pieVersionRepository;
    private PieSliceRepository pieSliceRepository;
    private AccountPieRepository accountPieRepository;
    private AccountRepository accountRepository;
    private InstrumentRepository instrumentRepository;
    private PieService pieService;

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 3, 10, 12, 0);
    private final Clock clock = Clock.fixed(NOW.toInstant(ZoneOffset.UTC), ZoneOffset.UTC);

    @BeforeEach
    void setUp() {
        pieRepository = mock(PieRepository.class);
        pieVersionRepository = mock(PieVersionRepository.class);
        pieSliceRepository = mock(PieSliceRepository.class);
        accountPieRepository = mock(AccountPieRepository.class);
        accountRepository = mock(AccountRepository.class);
        instrumentRepository = mock(InstrumentRepository.class);
        pieService = new PieService(pieRepository, pieVersionRepository, pieSliceRepository,
            accountPieRepository, accountRepository, instrumentRepository, clock);

        when(pieRepository.save(any(Pie.class))).thenAnswer(inv -> inv.getArgument(0));
        for (String symbol : List.of("AAPL", "MSFT", "GOOG")) {
            tradable(symbol, true);
        }
        tradable("HALT", false);
        when(instrumentRepository.findBySymbol("NOPE")).thenReturn(Optional.empty());
    }

    private void tradable(String symbol, boolean tradable) {
        Instrument instrument = mock(Instrument.class);
        when(instrument.getName()).thenReturn(symbol + " Inc");
        when(instrument.isTradable()).thenReturn(tradable);
        when(instrumentRepository.findBySymbol(symbol)).thenReturn(Optional.of(instrument));
    }

    private static SavePieRequest request(String name, String... symbolWeight) {
        List<PieSliceRequest> slices = new java.util.ArrayList<>();
        for (int i = 0; i < symbolWeight.length; i += 2) {
            slices.add(new PieSliceRequest(symbolWeight[i], new BigDecimal(symbolWeight[i + 1])));
        }
        return new SavePieRequest(name, "desc", slices);
    }

    private Pie existingPie(int version, String... symbolWeight) {
        Pie pie = Pie.create(OWNER, "Old", "old");
        pie.setLatestVersion(version);
        when(pieRepository.findByPieIdAndOwnerUsernameAndDeletedAtIsNull(pie.getPieId(), OWNER))
            .thenReturn(Optional.of(pie));
        when(pieRepository.lockOwned(pie.getPieId(), OWNER)).thenReturn(Optional.of(pie));
        List<PieSlice> slices = new java.util.ArrayList<>();
        for (int i = 0; i < symbolWeight.length; i += 2) {
            slices.add(new PieSlice(pie.getPieId(), version, symbolWeight[i], new BigDecimal(symbolWeight[i + 1])));
        }
        when(pieSliceRepository.findByPieIdAndVersionOrderByWeightPctDescSymbolAsc(pie.getPieId(), version))
            .thenReturn(slices);
        return pie;
    }

    @Test
    void create_normalisesSymbols_andSavesVersion1() {
        PieResponse response = pieService.create(OWNER, request("Tech", " aapl ", "60", "MSFT", "30"));

        assertEquals(1, response.getVersion());
        assertEquals(new BigDecimal("10"), response.getCashPct());
        verify(pieVersionRepository).save(any(PieVersion.class));
        verify(pieSliceRepository).saveAll(argThat(slices -> {
            List<String> symbols = new java.util.ArrayList<>();
            slices.forEach(s -> symbols.add(s.getSymbol()));
            return symbols.containsAll(List.of("AAPL", "MSFT")) && symbols.size() == 2;
        }));
    }

    @Test
    void create_rejectsDuplicateAfterUppercase() {
        assertThrows(InvalidPieException.class,
            () -> pieService.create(OWNER, request("Tech", "AAPL", "10", "aapl", "10")));
        verify(pieRepository, never()).save(any());
    }

    static Stream<Arguments> weightCases() {
        return Stream.of(
            Arguments.of(new String[]{"33.33", "33.33", "33.33"}, true),
            Arguments.of(new String[]{"33.34", "33.34", "33.34"}, false),
            Arguments.of(new String[]{"10.005"}, false),
            Arguments.of(new String[]{"0"}, false),
            Arguments.of(new String[]{"100.01"}, false));
    }

    @ParameterizedTest
    @MethodSource("weightCases")
    void create_weightRules(String[] weights, boolean ok) {
        String[] symbols = {"AAPL", "MSFT", "GOOG"};
        String[] pairs = new String[weights.length * 2];
        for (int i = 0; i < weights.length; i++) {
            pairs[2 * i] = symbols[i];
            pairs[2 * i + 1] = weights[i];
        }
        SavePieRequest req = request("Tech", pairs);

        if (ok) {
            assertEquals(new BigDecimal("0.01"), pieService.create(OWNER, req).getCashPct());
        } else {
            assertThrows(InvalidPieException.class, () -> pieService.create(OWNER, req));
        }
    }

    @Test
    void create_rejectsUnknownAndUntradable() {
        InvalidPieException unknown = assertThrows(InvalidPieException.class,
            () -> pieService.create(OWNER, request("Tech", "NOPE", "10")));
        assertEquals("Unknown symbol: NOPE", unknown.getMessage());

        InvalidPieException halted = assertThrows(InvalidPieException.class,
            () -> pieService.create(OWNER, request("Tech", "HALT", "10")));
        assertEquals("Not tradable: HALT", halted.getMessage());
    }

    @Test
    void update_sameSlices_keepsVersion() {
        Pie pie = existingPie(2, "AAPL", "60", "MSFT", "30");

        PieResponse response = pieService.update(pie.getPieId(), OWNER, request("New", "msft", "30.0", "AAPL", "60"));

        assertEquals(2, response.getVersion());
        assertEquals("New", pie.getName());
        verify(pieVersionRepository, never()).save(any());
        verify(pieSliceRepository, never()).saveAll(any());
    }

    @Test
    void update_changedSlices_bumpsVersion() {
        Pie pie = existingPie(2, "AAPL", "60", "MSFT", "30");

        PieResponse response = pieService.update(pie.getPieId(), OWNER, request("New", "AAPL", "50", "MSFT", "30"));

        assertEquals(3, response.getVersion());
        assertEquals(3, pie.getLatestVersion());
        verify(pieVersionRepository).save(any(PieVersion.class));
        verify(pieSliceRepository).saveAll(any());
    }

    @Test
    void getUpdateDelete_otherOwner_notFound() {
        Pie pie = existingPie(1, "AAPL", "60");

        assertThrows(PieNotFoundException.class, () -> pieService.get(pie.getPieId(), "eve"));
        assertThrows(PieNotFoundException.class,
            () -> pieService.update(pie.getPieId(), "eve", request("New", "AAPL", "10")));
        assertThrows(PieNotFoundException.class, () -> pieService.delete(pie.getPieId(), "eve"));
    }

    @Test
    void delete_linkedToOwnAccount_inUse() {
        Pie pie = existingPie(1, "AAPL", "60");
        Account account = mock(Account.class);
        when(account.getAccountId()).thenReturn("ACC1");
        when(accountRepository.findByOwnerUsernameOrderByAccountId(OWNER)).thenReturn(List.of(account));
        when(accountPieRepository.existsByPieIdAndAccountIdIn(pie.getPieId(), List.of("ACC1"))).thenReturn(true);

        assertThrows(PieInUseException.class, () -> pieService.delete(pie.getPieId(), OWNER));
        assertNull(pie.getDeletedAt());
    }

    @Test
    void delete_unlinked_softDeletes() {
        Pie pie = existingPie(1, "AAPL", "60");
        when(accountRepository.findByOwnerUsernameOrderByAccountId(OWNER)).thenReturn(List.of());
        when(accountPieRepository.existsByPieIdAndAccountIdIn(eq(pie.getPieId()), anyCollection())).thenReturn(false);

        pieService.delete(pie.getPieId(), OWNER);

        assertNotNull(pie.getDeletedAt());
        verify(pieRepository).save(pie);
    }

    private Pie publicPie(int version, LocalDateTime publishedAt, String... symbolWeight) {
        Pie pie = existingPie(version, symbolWeight);
        pie.publish(publishedAt);
        return pie;
    }

    @Test
    void publish_makesPublicToday() {
        Pie pie = existingPie(2, "AAPL", "60");

        PieResponse response = pieService.publish(pie.getPieId(), OWNER);

        assertEquals("PUBLIC", response.getVisibility());
        assertEquals(NOW, response.getPublishedAt());
        assertEquals(NOW, pie.getPublishedAt());
    }

    @Test
    void publish_alreadyPublic_changesNothing() {
        LocalDateTime earlier = NOW.minusDays(3);
        Pie pie = publicPie(1, earlier, "AAPL", "60");

        PieResponse response = pieService.publish(pie.getPieId(), OWNER);

        assertEquals(earlier, response.getPublishedAt());
    }

    @Test
    void publish_otherOwner_notFound() {
        Pie pie = existingPie(1, "AAPL", "60");

        assertThrows(PieNotFoundException.class, () -> pieService.publish(pie.getPieId(), "eve"));
        assertThrows(PieNotFoundException.class, () -> pieService.unpublish(pie.getPieId(), "eve"));
        assertEquals(PieVisibility.PRIVATE, pie.getVisibility());
    }

    @Test
    void unpublish_makesPrivate() {
        Pie pie = publicPie(1, NOW.minusDays(3), "AAPL", "60");

        PieResponse response = pieService.unpublish(pie.getPieId(), OWNER);

        assertEquals("PRIVATE", response.getVisibility());
        assertNull(response.getPublishedAt());
        assertNull(pie.getPublishedAt());
    }

    @Test
    void unpublish_private_changesNothing() {
        Pie pie = existingPie(1, "AAPL", "60");

        PieResponse response = pieService.unpublish(pie.getPieId(), OWNER);

        assertEquals("PRIVATE", response.getVisibility());
        assertNull(response.getPublishedAt());
        assertEquals(1, response.getVersion());
    }

    @Test
    void publicAndPrivatePies_allowRepeatedAllocationEditsImmediately() {
        for (boolean published : List.of(false, true)) {
            Pie pie = existingPie(1, "AAPL", "60");
            if (published) {
                pie.publish(NOW);
            }
            assertEquals(2, pieService.update(pie.getPieId(), OWNER, request("New", "AAPL", "50")).getVersion());
            when(pieSliceRepository.findByPieIdAndVersionOrderByWeightPctDescSymbolAsc(pie.getPieId(), 2))
                .thenReturn(List.of(new PieSlice(pie.getPieId(), 2, "AAPL", new BigDecimal("50"))));
            assertEquals(3, pieService.update(pie.getPieId(), OWNER, request("New", "AAPL", "40")).getVersion());
        }
    }

    @Test
    void update_savesNewVersionAtClockTime() {
        Pie pie = existingPie(1, "AAPL", "60");

        pieService.update(pie.getPieId(), OWNER, request("New", "AAPL", "50"));

        verify(pieVersionRepository).save(argThat(v -> v.getCreatedAt().equals(NOW) && v.getVersion() == 2));
    }

    @Test
    void response_listsOnlyOwnersLinkedAccounts() {
        Pie pie = existingPie(1, "AAPL", "60");
        Account mine = mock(Account.class);
        when(mine.getAccountId()).thenReturn("ACC1");
        when(accountRepository.findByOwnerUsernameOrderByAccountId(OWNER)).thenReturn(List.of(mine));
        when(accountPieRepository.findByPieId(pie.getPieId())).thenReturn(List.of(
            AccountPie.own("ACC1", pie.getPieId()), AccountPie.own("SOMEONE_ELSES", pie.getPieId())));

        assertEquals(List.of("ACC1"), pieService.get(pie.getPieId(), OWNER).getLinkedAccountIds());
        when(pieRepository.findByOwnerUsernameAndDeletedAtIsNullOrderByCreatedAt(OWNER)).thenReturn(List.of(pie));
        assertEquals(List.of("ACC1"), pieService.listMine(OWNER).get(0).getLinkedAccountIds());
    }

    @Test
    void delete_publicPie_goesPrivate() {
        Pie pie = publicPie(1, NOW.minusDays(3), "AAPL", "60");

        pieService.delete(pie.getPieId(), OWNER);

        assertEquals(PieVisibility.PRIVATE, pie.getVisibility());
        assertNull(pie.getPublishedAt());
        assertEquals(NOW, pie.getDeletedAt());
    }
}
