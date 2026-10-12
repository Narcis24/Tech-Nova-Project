package com.neueda.app.services;

import com.neueda.app.dtos.PlaceOrderRequest;
import com.neueda.app.dtos.PortfolioSnapshotResponse;
import com.neueda.app.dtos.PositionResponse;
import com.neueda.app.dtos.RiskWarning;
import com.neueda.app.enums.AssetClass;
import com.neueda.app.enums.LimitSource;
import com.neueda.app.enums.RiskRule;
import com.neueda.app.exceptions.PriceNotFoundException;
import com.neueda.app.models.AccountRiskLimit;
import com.neueda.app.models.Instrument;
import com.neueda.app.models.RiskLimit;
import com.neueda.app.repositories.AccountRiskLimitRepository;
import com.neueda.app.repositories.InstrumentRepository;
import com.neueda.app.repositories.RiskLimitRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class RiskServiceTest {

    private static final String ACCOUNT = "ACC001";

    private PortfolioService portfolioService;
    private PriceService priceService;
    private InstrumentRepository instrumentRepository;
    private RiskLimitRepository riskLimitRepository;
    private AccountRiskLimitRepository accountRiskLimitRepository;
    private RiskService riskService;

    @BeforeEach
    void setUp() {
        portfolioService = mock(PortfolioService.class);
        priceService = mock(PriceService.class);
        instrumentRepository = mock(InstrumentRepository.class);
        riskLimitRepository = mock(RiskLimitRepository.class);
        accountRiskLimitRepository = mock(AccountRiskLimitRepository.class);
        // Real RiskLimitService over the mocked repositories, so these tests still check how limits combine
        riskService = new RiskService(portfolioService, priceService, instrumentRepository,
            new RiskLimitService(riskLimitRepository, accountRiskLimitRepository));

        when(riskLimitRepository.findAll()).thenReturn(List.of(
            new RiskLimit(RiskRule.SINGLE_INSTRUMENT, new BigDecimal("20"), "ABOVE"),
            new RiskLimit(RiskRule.ASSET_CLASS, new BigDecimal("60"), "ABOVE"),
            new RiskLimit(RiskRule.CASH_FLOOR, new BigDecimal("20"), "BELOW")
        ));
        when(accountRiskLimitRepository.findByAccountId(ACCOUNT)).thenReturn(List.of());
        when(instrumentRepository.findAllById(any())).thenReturn(List.of(
            instrument("AAPL", AssetClass.EQUITY),
            instrument("MSFT", AssetClass.EQUITY),
            instrument("NVDA", AssetClass.EQUITY),
            instrument("JPM", AssetClass.EQUITY),
            instrument("SPY", AssetClass.ETF),
            instrument("XYZ", AssetClass.EQUITY)
        ));
        when(priceService.getCurrentPrice("AAPL")).thenReturn(new BigDecimal("254.10"));
        when(priceService.getCurrentPrice("XYZ")).thenReturn(new BigDecimal("100.00"));
    }

    // ========== No warnings ==========

    @Test
    void noWarningsWhenOrderStaysWithinLimits() {
        givenAlicePortfolio();

        // AAPL 15.1%, equity 39.8%, cash 51.4%
        assertTrue(riskService.check(buy("AAPL", 20, null)).isEmpty());
    }

    @Test
    void sellNeverWarnsAndSkipsTheCheck() {
        assertTrue(riskService.check(order("AAPL", "SELL", 40, null)).isEmpty());
        verifyNoInteractions(portfolioService);
    }

    @Test
    void justShortOfLimitDoesNotWarn() {
        givenCashOnly("2000.00");

        // 15 x 100 = 1,500: cash 500 of 2,000 = 25%
        List<RiskWarning> warnings = riskService.check(buy("XYZ", 15, null));

        assertNull(find(warnings, RiskRule.CASH_FLOOR));
    }

    @Test
    void noWarningsWhenOrderSymbolHasNoPrice() {
        givenAlicePortfolio();
        when(priceService.getCurrentPrice("NEW")).thenThrow(new PriceNotFoundException("No price data for symbol: NEW"));

        assertTrue(riskService.check(buy("NEW", 10, null)).isEmpty());
    }

    @Test
    void noWarningsWhenAHoldingIsUnpriced() {
        when(portfolioService.getPortfolioSnapshot(ACCOUNT)).thenReturn(snapshot("1000.00",
            position("AAPL", 40, "0", "0")));

        assertTrue(riskService.check(buy("AAPL", 100, null)).isEmpty());
    }

    // ========== Each rule ==========

    @Test
    void warnsWhenSingleInstrumentPassesLimit() {
        givenAlicePortfolio();

        // AAPL 100 x 254.10 = 25,410 of 100,751.95 = 25.22%
        List<RiskWarning> warnings = riskService.check(buy("AAPL", 60, null));

        assertEquals(1, warnings.size());
        RiskWarning w = warnings.get(0);
        assertEquals(RiskRule.SINGLE_INSTRUMENT, w.getRule());
        assertEquals("AAPL", w.getSubject());
        assertEquals(new BigDecimal("25.22"), w.getResultingPct());
        assertEquals(LimitSource.DEFAULT, w.getLimitSource());
        assertEquals("After this order, AAPL would be 25.2% of your portfolio. Your limit is 20%.", w.getMessage());
    }

    @Test
    void warnsWhenAssetClassPassesLimit() {
        givenAlicePortfolio();

        // Equities 60,468.25 of 100,751.95 = 60.02%
        RiskWarning w = find(riskService.check(buy("AAPL", 100, null)), RiskRule.ASSET_CLASS);

        assertNotNull(w);
        assertEquals("EQUITY", w.getSubject());
        assertEquals(new BigDecimal("60.02"), w.getResultingPct());
    }

    @Test
    void warnsWhenCashFallsBelowFloor() {
        givenCashOnly("2000.00");

        // 17 x 100 = 1,700: cash 300 of 2,000 = 15%
        RiskWarning w = find(riskService.check(buy("XYZ", 17, null)), RiskRule.CASH_FLOOR);

        assertNotNull(w);
        assertEquals(new BigDecimal("15.00"), w.getResultingPct());
        assertEquals("After this order, cash would be 15.0% of your portfolio. Your minimum is 20%.", w.getMessage());
    }

    // ========== Boundaries ==========

    @Test
    void warnsWhenCashLandsExactlyOnFloor() {
        givenCashOnly("2000.00");

        // 16 x 100 = 1,600: cash 400 of 2,000 = exactly 20%
        RiskWarning w = find(riskService.check(buy("XYZ", 16, null)), RiskRule.CASH_FLOOR);

        assertNotNull(w);
        assertEquals(new BigDecimal("20.00"), w.getResultingPct());
    }

    @Test
    void warnsWhenSingleInstrumentLandsExactlyOnLimit() {
        givenCashOnly("2000.00");
        when(accountRiskLimitRepository.findByAccountId(ACCOUNT)).thenReturn(List.of(
            new AccountRiskLimit(ACCOUNT, RiskRule.SINGLE_INSTRUMENT, new BigDecimal("40"), true)));

        // 8 x 100 = 800 of 2,000 = exactly 40%
        RiskWarning w = find(riskService.check(buy("XYZ", 8, null)), RiskRule.SINGLE_INSTRUMENT);

        assertNotNull(w);
        assertEquals(new BigDecimal("40.00"), w.getResultingPct());
    }

    // ========== Account's own limits ==========

    @Test
    void customLimitReplacesDefault() {
        givenAlicePortfolio();
        when(accountRiskLimitRepository.findByAccountId(ACCOUNT)).thenReturn(List.of(
            new AccountRiskLimit(ACCOUNT, RiskRule.SINGLE_INSTRUMENT, new BigDecimal("12.5"), true)));

        // AAPL 15.1%: under the default 20% but over the holder's 12.5%
        RiskWarning w = find(riskService.check(buy("AAPL", 20, null)), RiskRule.SINGLE_INSTRUMENT);

        assertNotNull(w);
        assertEquals(LimitSource.CUSTOM, w.getLimitSource());
        assertEquals(new BigDecimal("12.5"), w.getLimitPct());
        assertEquals("After this order, AAPL would be 15.1% of your portfolio. Your limit is 12.5%.", w.getMessage());
    }

    @Test
    void customRowWithoutThresholdKeepsDefault() {
        givenAlicePortfolio();
        when(accountRiskLimitRepository.findByAccountId(ACCOUNT)).thenReturn(List.of(
            new AccountRiskLimit(ACCOUNT, RiskRule.SINGLE_INSTRUMENT, null, true)));

        RiskWarning w = find(riskService.check(buy("AAPL", 60, null)), RiskRule.SINGLE_INSTRUMENT);

        assertNotNull(w);
        assertEquals(LimitSource.DEFAULT, w.getLimitSource());
        assertEquals(new BigDecimal("20"), w.getLimitPct());
    }

    @Test
    void disabledRuleNeverWarns() {
        givenAlicePortfolio();
        when(accountRiskLimitRepository.findByAccountId(ACCOUNT)).thenReturn(List.of(
            new AccountRiskLimit(ACCOUNT, RiskRule.SINGLE_INSTRUMENT, null, false)));

        assertTrue(riskService.check(buy("AAPL", 60, null)).isEmpty());
    }

    // ========== Limit orders ==========

    @Test
    void limitOrderMovesCashAtLimitPrice() {
        givenCashOnly("2000.00");

        // At market (100) cash would be exactly 20%. At the 90 limit: cash 560 of 2,160 = 25.9%
        List<RiskWarning> warnings = riskService.check(buy("XYZ", 16, new BigDecimal("90.00")));

        assertNull(find(warnings, RiskRule.CASH_FLOOR));
    }

    // ========== Helpers ==========

    /** ACC001 from the UI samples: total 100,751.95, of which cash 56,852.70. */
    private void givenAlicePortfolio() {
        when(portfolioService.getPortfolioSnapshot(ACCOUNT)).thenReturn(snapshot("56852.70",
            position("AAPL", 40, "254.10", "10164.00"),
            position("MSFT", 25, "432.55", "10813.75"),
            position("SPY", 15, "589.40", "8841.00"),
            position("NVDA", 60, "124.85", "7491.00"),
            position("JPM", 30, "219.65", "6589.50")));
    }

    private void givenCashOnly(String cash) {
        when(portfolioService.getPortfolioSnapshot(ACCOUNT)).thenReturn(snapshot(cash));
    }

    private static PortfolioSnapshotResponse snapshot(String cash, PositionResponse... positions) {
        BigDecimal marketValue = BigDecimal.ZERO;
        for (PositionResponse p : positions) {
            marketValue = marketValue.add(p.getMarketValue());
        }
        BigDecimal cashBalance = new BigDecimal(cash);
        return new PortfolioSnapshotResponse(ACCOUNT, cashBalance, marketValue, cashBalance.add(marketValue), List.of(positions));
    }

    private static PositionResponse position(String symbol, int quantity, String price, String marketValue) {
        return new PositionResponse(ACCOUNT, symbol, quantity, new BigDecimal("100.00"),
            new BigDecimal(price), new BigDecimal(marketValue), BigDecimal.ZERO);
    }

    private static Instrument instrument(String symbol, AssetClass assetClass) {
        return new Instrument(symbol, symbol + " Inc.", assetClass, "USD", true);
    }

    private static PlaceOrderRequest buy(String symbol, int quantity, BigDecimal limitPrice) {
        return order(symbol, "BUY", quantity, limitPrice);
    }

    private static PlaceOrderRequest order(String symbol, String side, int quantity, BigDecimal limitPrice) {
        return new PlaceOrderRequest(ACCOUNT, symbol, side, limitPrice == null ? "MARKET" : "LIMIT",
            quantity, limitPrice, "key-1");
    }

    private static RiskWarning find(List<RiskWarning> warnings, RiskRule rule) {
        return warnings.stream().filter(w -> w.getRule() == rule).findFirst().orElse(null);
    }
}
