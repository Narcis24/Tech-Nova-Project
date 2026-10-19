package com.neueda.app.services;

import com.neueda.app.dtos.AccountPieResponse;
import com.neueda.app.dtos.PortfolioSnapshotResponse;
import com.neueda.app.dtos.PositionResponse;
import com.neueda.app.dtos.PreviewLeg;
import com.neueda.app.dtos.RebalancePreviewResponse;
import com.neueda.app.dtos.RebalanceRunResponse;
import com.neueda.app.dtos.RiskWarning;
import com.neueda.app.enums.PieLinkMode;
import com.neueda.app.exceptions.AccountNotActiveException;
import com.neueda.app.exceptions.PieNotFoundException;
import com.neueda.app.exceptions.PieNotLinkedException;
import com.neueda.app.exceptions.PriceNotFoundException;
import com.neueda.app.enums.RebalanceStatus;
import com.neueda.app.exceptions.RebalanceConflictException;
import com.neueda.app.models.Account;
import com.neueda.app.models.AccountPie;
import com.neueda.app.models.Pie;
import com.neueda.app.models.RebalanceRun;
import com.neueda.app.repositories.AccountPieRepository;
import com.neueda.app.repositories.AccountRepository;
import com.neueda.app.repositories.PieRepository;
import com.neueda.app.repositories.RebalanceRunRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class AccountPieServiceTest {

    private static final String ACCOUNT = "ACC-1";
    private static final UUID PIE_ID = UUID.randomUUID();

    private AccountPieRepository accountPieRepository;
    private PieRepository pieRepository;
    private PieService pieService;
    private PortfolioService portfolioService;
    private PriceService priceService;
    private RiskService riskService;
    private AccountRepository accountRepository;
    private RebalanceRunRepository rebalanceRunRepository;
    private RebalanceRunService runService;
    private Account account;
    private Pie pie;
    private AccountPieService service;

    @BeforeEach
    void setUp() {
        accountPieRepository = mock(AccountPieRepository.class);
        pieRepository = mock(PieRepository.class);
        pieService = mock(PieService.class);
        portfolioService = mock(PortfolioService.class);
        priceService = mock(PriceService.class);
        riskService = mock(RiskService.class);
        accountRepository = mock(AccountRepository.class);
        rebalanceRunRepository = mock(RebalanceRunRepository.class);
        runService = mock(RebalanceRunService.class);
        account = mock(Account.class);
        when(accountRepository.findByIdForUpdate(ACCOUNT)).thenReturn(Optional.of(account));
        when(accountRepository.findById(ACCOUNT)).thenReturn(Optional.of(account));
        service = new AccountPieService(accountPieRepository, pieRepository, pieService,
            new RebalancePlanner(portfolioService, priceService,
                new RebalanceCalculator(new BigDecimal("10"), new BigDecimal("0.5"), BigDecimal.ONE)),
            riskService, accountRepository, rebalanceRunRepository, runService);

        pie = mock(Pie.class);
        when(pie.getPieId()).thenReturn(PIE_ID);
        when(pie.getOwnerUsername()).thenReturn("author");
        when(pie.isPublished()).thenReturn(true);
        when(pie.getName()).thenReturn("Tech");
        when(pie.getLatestVersion()).thenReturn(2);
        when(pieRepository.findById(PIE_ID)).thenReturn(Optional.of(pie));
        Map<String, BigDecimal> targets = new LinkedHashMap<>();
        targets.put("AAPL", new BigDecimal("60"));
        targets.put("MSFT", new BigDecimal("30"));
        when(pieService.weightsAt(PIE_ID, 2)).thenReturn(targets);
        when(accountPieRepository.save(any(AccountPie.class))).thenAnswer(inv -> inv.getArgument(0));
        when(priceService.getCurrentPrice(any())).thenReturn(new BigDecimal("100"));
    }

    private void linked() {
        when(accountPieRepository.findById(ACCOUNT)).thenReturn(Optional.of(AccountPie.own(ACCOUNT, PIE_ID)));
    }

    private static PositionResponse position(String symbol, int quantity, String price) {
        return new PositionResponse(ACCOUNT, symbol, quantity, BigDecimal.ONE, new BigDecimal(price),
            new BigDecimal(price).multiply(BigDecimal.valueOf(quantity)), BigDecimal.ZERO);
    }

    private void portfolio(String cash, PositionResponse... positions) {
        when(portfolioService.getPortfolioSnapshot(ACCOUNT)).thenReturn(new PortfolioSnapshotResponse(
            ACCOUNT, new BigDecimal(cash), BigDecimal.ZERO, BigDecimal.ZERO, List.of(positions)));
    }

    @Test
    void link_ownPie_savesLink() {
        portfolio("1000");
        when(priceService.getCurrentPrice(any())).thenReturn(new BigDecimal("100"));

        AccountPieResponse response = service.link(ACCOUNT, "jay", PIE_ID);

        verify(pieService).requireOwned(PIE_ID, "jay");
        ArgumentCaptor<AccountPie> saved = ArgumentCaptor.forClass(AccountPie.class);
        verify(accountPieRepository).save(saved.capture());
        assertEquals(ACCOUNT, saved.getValue().getAccountId());
        assertEquals(PIE_ID, saved.getValue().getPieId());
        assertEquals("OWN", response.getMode());
        assertEquals("Tech", response.getPieName());
        assertEquals(2, response.getLatestVersion());
    }

    @Test
    void link_unpricedSymbol_priceNotFoundAndNothingSaved() {
        portfolio("1000");
        when(priceService.getCurrentPrice(any())).thenThrow(new PriceNotFoundException("none"));

        assertThrows(PriceNotFoundException.class, () -> service.link(ACCOUNT, "jay", PIE_ID));
        verify(accountPieRepository, never()).save(any());
    }

    @Test
    void link_otherUsersPie_pieNotFound() {
        when(pieService.requireOwned(PIE_ID, "mallory")).thenThrow(new PieNotFoundException("nope"));

        assertThrows(PieNotFoundException.class, () -> service.link(ACCOUNT, "mallory", PIE_ID));
        verify(accountPieRepository, never()).save(any());
    }

    @Test
    void linkAndUnlink_whileRunActive_conflictAndNothingChanges() {
        linked();
        when(rebalanceRunRepository.existsByAccountIdAndStatusIn(ACCOUNT, RebalanceStatus.ACTIVE)).thenReturn(true);

        assertThrows(RebalanceConflictException.class, () -> service.link(ACCOUNT, "jay", PIE_ID));
        assertThrows(RebalanceConflictException.class, () -> service.unlink(ACCOUNT));
        verify(accountPieRepository, never()).save(any());
        verify(accountPieRepository, never()).delete(any());
    }

    @Test
    void get_unlinked_pieNotLinked() {
        when(accountPieRepository.findById(ACCOUNT)).thenReturn(Optional.empty());

        assertThrows(PieNotLinkedException.class, () -> service.get(ACCOUNT));
        assertThrows(PieNotLinkedException.class, () -> service.unlink(ACCOUNT));
    }

    @Test
    void get_returnsDriftAndMax() {
        linked();
        // total 1000: AAPL 50% (target 60), MSFT 20% (target 30), GOOG not in the pie
        portfolio("0", position("AAPL", 5, "100"), position("MSFT", 2, "100"), position("GOOG", 3, "100"));

        AccountPieResponse response = service.get(ACCOUNT);

        assertEquals(2, response.getDrift().size());
        assertEquals(0, new BigDecimal("10").compareTo(response.getMaxDriftPct()));
        assertTrue(response.getDrift().stream().allMatch(d -> d.getSymbol().equals("AAPL") || d.getSymbol().equals("MSFT")));
    }

    @Test
    void get_unpricedSymbol_returnsTheLinkWithNoDrift() {
        linked();
        portfolio("1000");
        when(priceService.getCurrentPrice(any())).thenThrow(new PriceNotFoundException("none"));

        AccountPieResponse response = service.get(ACCOUNT);

        assertEquals(PIE_ID, response.getPieId());
        assertTrue(response.getDrift().isEmpty());
        assertNull(response.getMaxDriftPct());
    }

    @Test
    void preview_combinesPlanAndWarnings() {
        linked();
        portfolio("1000");
        when(priceService.getCurrentPrice(any())).thenReturn(new BigDecimal("100"));
        RiskWarning warning = new RiskWarning();
        when(riskService.checkHoldings(eq(ACCOUNT), anyMap(), any())).thenReturn(List.of(warning));

        RebalancePreviewResponse preview = service.preview(ACCOUNT);

        // budget 990 after the 1% buffer: AAPL 6 @ 100 and MSFT 3 @ 100
        assertEquals(2, preview.getVersion());
        assertEquals(0, new BigDecimal("10").compareTo(preview.getCashTargetPct()));
        assertEquals(0, new BigDecimal("100").compareTo(preview.getCashAfter()));
        assertEquals(List.of(warning), preview.getWarnings());
        PreviewLeg apple = preview.getLegs().stream().filter(l -> l.getSymbol().equals("AAPL")).findFirst().orElseThrow();
        assertEquals(0, new BigDecimal("600").compareTo(apple.getEstimatedValue()));
        assertEquals(0, BigDecimal.ZERO.compareTo(apple.getCurrentValue()));
        assertEquals(0, new BigDecimal("600").compareTo(apple.getTargetValue()));
        assertEquals(0, new BigDecimal("600").compareTo(apple.getDifference()));

        ArgumentCaptor<Map<String, BigDecimal>> values = ArgumentCaptor.forClass(Map.class);
        verify(riskService).checkHoldings(eq(ACCOUNT), values.capture(), eq(preview.getCashAfter()));
        assertEquals(0, new BigDecimal("600").compareTo(values.getValue().get("AAPL")));
        assertEquals(0, new BigDecimal("300").compareTo(values.getValue().get("MSFT")));
    }

    @Test
    void preview_heldSymbolUnpriced_priceNotFound() {
        linked();
        portfolio("1000", position("GOOG", 3, "0"));
        when(priceService.getCurrentPrice(any())).thenReturn(new BigDecimal("100"));

        PriceNotFoundException ex = assertThrows(PriceNotFoundException.class, () -> service.preview(ACCOUNT));
        assertEquals("No price for GOOG, so the rebalance can't be planned", ex.getMessage());
    }

    @Test
    void preview_pieSymbolUnpriced_priceNotFound() {
        linked();
        portfolio("1000");
        when(priceService.getCurrentPrice("AAPL")).thenReturn(new BigDecimal("100"));
        when(priceService.getCurrentPrice("MSFT")).thenThrow(new PriceNotFoundException("none"));

        PriceNotFoundException ex = assertThrows(PriceNotFoundException.class, () -> service.preview(ACCOUNT));
        assertEquals("No price for MSFT, so the rebalance can't be planned", ex.getMessage());
    }

    // ---------- copying ----------

    private void copyOf(Integer applied) {
        AccountPie link = AccountPie.copy(ACCOUNT, PIE_ID, true);
        if (applied != null) {
            link.applyVersion(applied);
        }
        when(accountPieRepository.findById(ACCOUNT)).thenReturn(Optional.of(link));
    }

    @Test
    void previewCopy_plansTheLatestVersionOfAPublicPie_andLinksAndPlacesNothing() {
        portfolio("1000");
        when(priceService.getCurrentPrice(any())).thenReturn(new BigDecimal("100"));

        RebalancePreviewResponse preview = service.previewCopy(ACCOUNT, "jay", PIE_ID);

        assertEquals(PIE_ID, preview.getPieId());
        assertEquals(2, preview.getVersion());
        assertEquals(2, preview.getLegs().size());
        verify(accountPieRepository, never()).save(any());
        verifyNoInteractions(runService);
    }

    @Test
    void previewCopyAndCopy_ownPie_conflictEvenWhenPrivate() {
        when(pie.getOwnerUsername()).thenReturn("jay");
        when(pie.isPublished()).thenReturn(false);

        assertThrows(RebalanceConflictException.class, () -> service.previewCopy(ACCOUNT, "jay", PIE_ID));
        assertThrows(RebalanceConflictException.class, () -> service.copy(ACCOUNT, "jay", PIE_ID));
        verify(accountPieRepository, never()).save(any());
        verifyNoInteractions(runService);
    }

    @Test
    void previewCopyAndCopy_privatePie_notFound() {
        when(pie.isPublished()).thenReturn(false);

        assertThrows(PieNotFoundException.class, () -> service.previewCopy(ACCOUNT, "jay", PIE_ID));
        assertThrows(PieNotFoundException.class, () -> service.copy(ACCOUNT, "jay", PIE_ID));
        verify(accountPieRepository, never()).save(any());
        verifyNoInteractions(runService);
    }

    @Test
    void previewCopyAndCopy_deletedPieOrUnknownPie_notFound() {
        when(pie.getDeletedAt()).thenReturn(java.time.LocalDateTime.now());

        assertThrows(PieNotFoundException.class, () -> service.previewCopy(ACCOUNT, "jay", PIE_ID));
        assertThrows(PieNotFoundException.class, () -> service.copy(ACCOUNT, "jay", UUID.randomUUID()));
        verify(accountPieRepository, never()).save(any());
    }

    @Test
    void previewCopyAndCopy_inactiveAccount_refused() {
        doThrow(new AccountNotActiveException("SUSPENDED")).when(account).validateStatus();

        assertThrows(AccountNotActiveException.class, () -> service.previewCopy(ACCOUNT, "jay", PIE_ID));
        assertThrows(AccountNotActiveException.class, () -> service.copy(ACCOUNT, "jay", PIE_ID));
        verify(accountPieRepository, never()).save(any());
        verifyNoInteractions(runService);
    }

    @Test
    void copy_linksTheAccountAsACopyAndThenStartsTheRun() {
        RebalanceRunResponse run = mock(RebalanceRunResponse.class);
        when(runService.start(ACCOUNT)).thenReturn(run);

        assertSame(run, service.copy(ACCOUNT, "jay", PIE_ID));

        ArgumentCaptor<AccountPie> saved = ArgumentCaptor.forClass(AccountPie.class);
        InOrder order = inOrder(accountPieRepository, runService);
        order.verify(accountPieRepository).save(saved.capture());
        order.verify(runService).start(ACCOUNT);
        assertEquals(PieLinkMode.COPY, saved.getValue().getMode());
        assertEquals(PIE_ID, saved.getValue().getPieId());
        assertTrue(saved.getValue().isAutoSync());
        assertNull(saved.getValue().getAppliedVersion());
    }

    @Test
    void copy_alreadyCopyingThePie_conflict() {
        copyOf(1);

        assertThrows(RebalanceConflictException.class, () -> service.copy(ACCOUNT, "jay", PIE_ID));
        verify(accountPieRepository, never()).save(any());
        verifyNoInteractions(runService);
    }

    @Test
    void updateCopySettings_turningItOffDropsTheWaitingSync() {
        copyOf(1);
        AccountPie link = accountPieRepository.findById(ACCOUNT).orElseThrow();
        portfolio("0");

        AccountPieResponse response = service.updateCopySettings(ACCOUNT, false);

        assertFalse(link.isAutoSync());
        assertFalse(response.isSyncPending());
        assertFalse(response.isAutoSync());
        verifyNoInteractions(runService);
    }

    @Test
    void updateCopySettings_ownLink_conflict() {
        linked();

        assertThrows(RebalanceConflictException.class, () -> service.updateCopySettings(ACCOUNT, true));
    }

    @Test
    void get_copyBehindAPublicPie_offersTheUpdate() {
        copyOf(1);
        UUID runId = UUID.randomUUID();
        RebalanceRun run = mock(RebalanceRun.class);
        when(run.getRunId()).thenReturn(runId);
        when(run.getPieId()).thenReturn(PIE_ID);
        when(run.getTargetVersion()).thenReturn(2);
        when(rebalanceRunRepository.findFirstByAccountIdAndStatusIn(ACCOUNT, RebalanceStatus.ACTIVE))
            .thenReturn(Optional.of(run));
        portfolio("1000");

        AccountPieResponse response = service.get(ACCOUNT);

        assertTrue(response.isPublished());
        assertTrue(response.isUpdateAvailable());
        assertTrue(response.isAutoSync());
        assertEquals(1, response.getAppliedVersion());
        assertEquals(2, response.getLatestVersion());
        assertEquals(runId, response.getActiveRunId());
        assertFalse(response.isSyncPending());

        when(run.getTargetVersion()).thenReturn(1);
        assertTrue(service.get(ACCOUNT).isSyncPending());
    }

    @Test
    void getAndPreview_copyOfAPieNoLongerPublic_stayOnTheAppliedVersion() {
        copyOf(1);
        when(pie.isPublished()).thenReturn(false);
        when(pie.getLatestVersion()).thenReturn(3);  // the author kept editing in private
        Map<String, BigDecimal> applied = new LinkedHashMap<>();
        applied.put("AAPL", new BigDecimal("50"));
        when(pieService.weightsAt(PIE_ID, 1)).thenReturn(applied);
        portfolio("1000");
        when(priceService.getCurrentPrice(any())).thenReturn(new BigDecimal("100"));

        AccountPieResponse response = service.get(ACCOUNT);
        RebalancePreviewResponse preview = service.preview(ACCOUNT);

        assertFalse(response.isPublished());
        assertFalse(response.isUpdateAvailable());
        assertEquals(1, response.getLatestVersion());
        assertEquals(1, preview.getVersion());
        assertEquals(List.of("AAPL"), preview.getLegs().stream().filter(l -> l.isInPie()).map(l -> l.getSymbol()).toList());
        verify(pieService, never()).weightsAt(PIE_ID, 3);
    }

    @Test
    void getAndPreview_copyOfAPieNoLongerPublicWithNothingApplied_hasNoTarget() {
        copyOf(null);
        when(pie.isPublished()).thenReturn(false);

        AccountPieResponse response = service.get(ACCOUNT);

        assertTrue(response.getDrift().isEmpty());
        assertEquals(0, response.getLatestVersion());
        assertThrows(PieNotFoundException.class, () -> service.preview(ACCOUNT));
        verifyNoInteractions(portfolioService);
    }

    // ---------- make it mine ----------

    @Test
    void makeMine_duplicatesTheAppliedVersionAndRelinksWithoutOrders() {
        copyOf(1);
        Pie mine = mock(Pie.class);
        UUID mineId = UUID.randomUUID();
        when(mine.getPieId()).thenReturn(mineId);
        when(mine.getName()).thenReturn("Tech");
        when(mine.getLatestVersion()).thenReturn(1);
        when(pieService.duplicate("jay", pie, 1)).thenReturn(mine);
        when(pieRepository.findById(mineId)).thenReturn(Optional.of(mine));
        when(pieService.weightsAt(mineId, 1)).thenReturn(Map.of("AAPL", new BigDecimal("50")));
        portfolio("1000");

        AccountPieResponse response = service.makeMine(ACCOUNT, "jay");

        AccountPie link = accountPieRepository.findById(ACCOUNT).orElseThrow();
        assertEquals(PieLinkMode.OWN, link.getMode());
        assertEquals(mineId, link.getPieId());
        assertEquals(1, link.getAppliedVersion());
        assertFalse(link.isAutoSync());
        assertEquals("OWN", response.getMode());
        assertFalse(response.isUpdateAvailable());
        verifyNoInteractions(runService);
    }

    @Test
    void makeMine_nothingAppliedYet_conflict() {
        copyOf(null);

        assertThrows(RebalanceConflictException.class, () -> service.makeMine(ACCOUNT, "jay"));
        verify(pieService, never()).duplicate(any(), any(), anyInt());
    }

    @Test
    void makeMine_ownLink_conflict() {
        linked();

        assertThrows(RebalanceConflictException.class, () -> service.makeMine(ACCOUNT, "jay"));
    }

    @Test
    void makeMine_whileRunActive_conflict() {
        copyOf(1);
        when(rebalanceRunRepository.existsByAccountIdAndStatusIn(ACCOUNT, RebalanceStatus.ACTIVE)).thenReturn(true);

        assertThrows(RebalanceConflictException.class, () -> service.makeMine(ACCOUNT, "jay"));
        verify(pieService, never()).duplicate(any(), any(), anyInt());
    }
}
