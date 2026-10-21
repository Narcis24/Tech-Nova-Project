package com.neueda.app.services;

import com.neueda.app.dtos.AccountPieResponse;
import com.neueda.app.dtos.PortfolioSnapshotResponse;
import com.neueda.app.dtos.PositionResponse;
import com.neueda.app.dtos.PreviewLeg;
import com.neueda.app.dtos.RebalancePreviewResponse;
import com.neueda.app.dtos.RiskWarning;
import com.neueda.app.exceptions.PieNotFoundException;
import com.neueda.app.exceptions.PieNotLinkedException;
import com.neueda.app.exceptions.PriceNotFoundException;
import com.neueda.app.enums.RebalanceStatus;
import com.neueda.app.exceptions.RebalanceConflictException;
import com.neueda.app.models.Account;
import com.neueda.app.models.AccountPie;
import com.neueda.app.models.Pie;
import com.neueda.app.repositories.AccountPieRepository;
import com.neueda.app.repositories.AccountRepository;
import com.neueda.app.repositories.PieRepository;
import com.neueda.app.repositories.RebalanceRunRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

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
    private PieService pieService;
    private PortfolioService portfolioService;
    private PriceService priceService;
    private RiskService riskService;
    private AccountRepository accountRepository;
    private RebalanceRunRepository rebalanceRunRepository;
    private AccountPieService service;

    @BeforeEach
    void setUp() {
        accountPieRepository = mock(AccountPieRepository.class);
        PieRepository pieRepository = mock(PieRepository.class);
        pieService = mock(PieService.class);
        portfolioService = mock(PortfolioService.class);
        priceService = mock(PriceService.class);
        riskService = mock(RiskService.class);
        accountRepository = mock(AccountRepository.class);
        rebalanceRunRepository = mock(RebalanceRunRepository.class);
        when(accountRepository.findByIdForUpdate(ACCOUNT)).thenReturn(Optional.of(mock(Account.class)));
        service = new AccountPieService(accountPieRepository, pieRepository, pieService,
            new RebalancePlanner(portfolioService, priceService,
                new RebalanceCalculator(new BigDecimal("10"), new BigDecimal("0.5"), BigDecimal.ONE)),
            riskService, accountRepository, rebalanceRunRepository);

        Pie pie = mock(Pie.class);
        when(pie.getPieId()).thenReturn(PIE_ID);
        when(pie.getName()).thenReturn("Tech");
        when(pie.getLatestVersion()).thenReturn(2);
        when(pieRepository.findById(PIE_ID)).thenReturn(Optional.of(pie));
        Map<String, BigDecimal> targets = new LinkedHashMap<>();
        targets.put("AAPL", new BigDecimal("60"));
        targets.put("MSFT", new BigDecimal("30"));
        when(pieService.latestWeights(PIE_ID)).thenReturn(targets);
        when(accountPieRepository.save(any(AccountPie.class))).thenAnswer(inv -> inv.getArgument(0));
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
}
