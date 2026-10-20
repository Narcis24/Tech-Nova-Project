package com.neueda.app.services;

import com.neueda.app.dtos.AdminAnalyticsResponse;
import com.neueda.app.dtos.AnalyticsBucket;
import com.neueda.app.dtos.AnalyticsTotals;
import com.neueda.app.dtos.AssetMixEntry;
import com.neueda.app.dtos.TopTradedInstrument;
import com.neueda.app.enums.AnalyticsBucketSize;
import com.neueda.app.enums.AnalyticsPeriod;
import com.neueda.app.enums.AssetClass;
import com.neueda.app.enums.OrderSide;
import com.neueda.app.enums.OrderStatus;
import com.neueda.app.enums.OrderType;
import com.neueda.app.repositories.OrderActivity;
import com.neueda.app.repositories.OrderRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdminAnalyticsServiceTest {

    // Tuesday 20 Oct 2026, 14:30 in New York (EDT, UTC-4)
    private static final Instant NOW = Instant.parse("2026-10-20T18:30:00Z");

    private final OrderRepository orderRepository = mock(OrderRepository.class);
    private final AdminAnalyticsService service =
        new AdminAnalyticsService(orderRepository, Clock.fixed(NOW, ZoneOffset.UTC));

    // --- Windows and buckets

    @Test
    void todayHas24HourlyBucketsInNewYorkTimeAndLaterHoursAreFuture() {
        AdminAnalyticsResponse response = analytics(AnalyticsPeriod.TODAY);

        assertEquals(AnalyticsBucketSize.HOUR, response.bucketSize());
        assertEquals(Instant.parse("2026-10-20T04:00:00Z"), response.from());   // midnight in New York
        assertEquals(Instant.parse("2026-10-21T04:00:00Z"), response.to());
        assertEquals(24, response.buckets().size());
        assertFalse(response.buckets().get(14).future());   // 14:00-15:00, the hour it is now
        assertTrue(response.buckets().get(15).future());
    }

    @Test
    void todayIsComparedWithYesterday() {
        AdminAnalyticsResponse response = analytics(AnalyticsPeriod.TODAY);

        assertEquals(Instant.parse("2026-10-19T04:00:00Z"), response.previousFrom());
        assertEquals(Instant.parse("2026-10-20T04:00:00Z"), response.previousTo());
        assertEquals(24, response.previousBuckets().size());
        assertTrue(response.previousBuckets().stream().noneMatch(AnalyticsBucket::future));
    }

    @Test
    void lastSevenDaysIsTodayAndTheSixDaysBefore() {
        AdminAnalyticsResponse response = analytics(AnalyticsPeriod.LAST_7_DAYS);

        assertEquals(Instant.parse("2026-10-14T04:00:00Z"), response.from());
        assertEquals(Instant.parse("2026-10-21T04:00:00Z"), response.to());
        assertEquals(Instant.parse("2026-10-07T04:00:00Z"), response.previousFrom());
        assertEquals(7, response.buckets().size());
        assertEquals(7, response.previousBuckets().size());
    }

    @Test
    void longerPeriodsHaveOneBucketPerDay() {
        assertEquals(30, analytics(AnalyticsPeriod.LAST_30_DAYS).buckets().size());
        assertEquals(90, analytics(AnalyticsPeriod.LAST_90_DAYS).buckets().size());
        assertEquals(90, analytics(AnalyticsPeriod.LAST_90_DAYS).previousBuckets().size());
    }

    @Test
    void yearToDateHasWeeklyBucketsFromTheMondayBeforeNewYearAndNoComparison() {
        AdminAnalyticsResponse response = analytics(AnalyticsPeriod.YEAR_TO_DATE);

        assertEquals(AnalyticsBucketSize.WEEK, response.bucketSize());
        assertEquals(Instant.parse("2026-01-01T05:00:00Z"), response.from());   // EST in January
        // 1 Jan 2026 is a Thursday, so the first week starts on Monday 29 Dec
        assertEquals(Instant.parse("2025-12-29T05:00:00Z"), response.buckets().get(0).start());
        assertEquals(Instant.parse("2026-10-19T04:00:00Z"), response.buckets().get(42).start());
        assertEquals(43, response.buckets().size());
        assertNull(response.previousFrom());
        assertNull(response.previousTo());
        assertNull(response.previousTotals());
        assertTrue(response.previousBuckets().isEmpty());
    }

    @Test
    void daysFollowTheClockChange() {
        // Wednesday 4 Nov 2026, noon in New York; clocks went back on Sunday 1 Nov
        AdminAnalyticsService afterClockChange = new AdminAnalyticsService(orderRepository,
            Clock.fixed(Instant.parse("2026-11-04T17:00:00Z"), ZoneOffset.UTC));
        noOrders();

        List<AnalyticsBucket> buckets = afterClockChange.analytics(AnalyticsPeriod.LAST_7_DAYS, null, null, null, null)
            .buckets();

        assertEquals(7, buckets.size());
        assertEquals(Instant.parse("2026-11-01T04:00:00Z"), buckets.get(3).start());   // still EDT at midnight
        assertEquals(Instant.parse("2026-11-02T05:00:00Z"), buckets.get(4).start());   // EST from here
    }

    // --- The query

    @Test
    void readsBothPeriodsInOneQueryAndPassesTheFiltersOn() {
        noOrders();

        service.analytics(AnalyticsPeriod.LAST_7_DAYS, AssetClass.ETF, OrderSide.SELL, OrderType.LIMIT, "alice");

        verify(orderRepository).findActivity(LocalDateTime.of(2026, 10, 7, 4, 0), LocalDateTime.of(2026, 10, 21, 4, 0),
            AssetClass.ETF, OrderSide.SELL, OrderType.LIMIT, "alice");
    }

    @Test
    void yearToDateReadsOnlyTheCurrentPeriod() {
        noOrders();

        service.analytics(AnalyticsPeriod.YEAR_TO_DATE, null, null, null, null);

        verify(orderRepository).findActivity(LocalDateTime.of(2026, 1, 1, 5, 0), LocalDateTime.of(2026, 10, 21, 4, 0),
            null, null, null, null);
    }

    @Test
    void ordersAreSplitIntoPeriodsAtNewYorkMidnight() {
        orders(
            filled("2026-10-14T03:59:00", "AAPL", 1, "100"),    // 23:59 on 13 Oct in New York: previous period
            filled("2026-10-14T04:00:00", "AAPL", 1, "200"));   // 00:00 on 14 Oct: current period

        AdminAnalyticsResponse response = analytics(AnalyticsPeriod.LAST_7_DAYS);

        assertMoney("200", response.totals().tradedValue());
        assertMoney("100", response.previousTotals().tradedValue());
    }

    @Test
    void ordersLandInTheirNewYorkHourAndDay() {
        orders(filled("2026-10-20T13:15:00", "AAPL", 1, "100"));   // 09:15 in New York

        AdminAnalyticsResponse today = analytics(AnalyticsPeriod.TODAY);
        assertEquals(1, today.buckets().get(9).totals().orders());

        orders(filled("2026-10-20T02:00:00", "AAPL", 1, "100"));   // 22:00 on 19 Oct in New York
        List<AnalyticsBucket> days = analytics(AnalyticsPeriod.LAST_7_DAYS).buckets();
        assertEquals(1, days.get(5).totals().orders());   // 19 Oct
        assertEquals(0, days.get(6).totals().orders());   // 20 Oct
    }

    // --- Totals

    @Test
    void tradedValueCountsOnlyFilledOrdersButOrdersCountsThemAll() {
        orders(
            filled("2026-10-20T14:00:00", "AAPL", 10, "150.00"),
            row("2026-10-20T14:05:00", "AAPL", OrderSide.BUY, OrderStatus.PENDING, 5, "149.00", "bob"),
            row("2026-10-20T14:10:00", "AAPL", OrderSide.BUY, OrderStatus.REJECTED, 5, "151.00", "bob"));

        AnalyticsTotals totals = analytics(AnalyticsPeriod.TODAY).totals();

        assertMoney("1500.00", totals.tradedValue());
        assertEquals(3, totals.orders());
    }

    @Test
    void fillRateIsFilledOutOfFinishedOrders() {
        orders(
            filled("2026-10-20T14:00:00", "AAPL", 1, "1"),
            filled("2026-10-20T14:01:00", "AAPL", 1, "1"),
            filled("2026-10-20T14:02:00", "AAPL", 1, "1"),
            row("2026-10-20T14:03:00", "AAPL", OrderSide.BUY, OrderStatus.CANCELLED, 1, "1", "alice"),
            row("2026-10-20T14:04:00", "AAPL", OrderSide.BUY, OrderStatus.PENDING, 1, "1", "alice"),
            row("2026-10-20T14:05:00", "AAPL", OrderSide.BUY, OrderStatus.PUBLISHED, 1, "1", "alice"));

        // 3 filled out of 4 finished; the PENDING and PUBLISHED orders haven't finished yet
        assertMoney("0.75", analytics(AnalyticsPeriod.TODAY).totals().fillRate());
    }

    @Test
    void fillRateIsNullWhileNothingHasFinished() {
        orders(row("2026-10-20T14:00:00", "AAPL", OrderSide.BUY, OrderStatus.PENDING, 1, "1", "alice"));

        assertNull(analytics(AnalyticsPeriod.TODAY).totals().fillRate());
    }

    @Test
    void activeTradersAreDistinctOwnersAndUnclaimedAccountsCountAsNoOne() {
        orders(
            row("2026-10-20T14:00:00", "AAPL", OrderSide.BUY, OrderStatus.FILLED, 1, "1", "alice"),
            row("2026-10-20T14:01:00", "AAPL", OrderSide.BUY, OrderStatus.FILLED, 1, "1", "alice"),
            row("2026-10-20T14:02:00", "AAPL", OrderSide.BUY, OrderStatus.FILLED, 1, "1", "bob"),
            row("2026-10-20T14:03:00", "AAPL", OrderSide.BUY, OrderStatus.FILLED, 1, "1", null));

        assertEquals(2, analytics(AnalyticsPeriod.TODAY).totals().activeTraders());
    }

    @Test
    void noOrdersGivesZeroTotals() {
        noOrders();

        AnalyticsTotals totals = analytics(AnalyticsPeriod.LAST_30_DAYS).totals();

        assertMoney("0", totals.tradedValue());
        assertEquals(0, totals.orders());
        assertNull(totals.fillRate());
        assertEquals(0, totals.activeTraders());
    }

    // --- Splits, rankings and mix

    @Test
    void everyBucketHasEveryAssetClassAndSide() {
        orders(filled("2026-10-20T14:00:00", "AAPL", 2, "10"));

        AnalyticsBucket bucket = analytics(AnalyticsPeriod.TODAY).buckets().get(10);

        assertEquals(AssetClass.values().length, bucket.byAssetClass().size());
        assertEquals(OrderSide.values().length, bucket.bySide().size());
        assertMoney("20", bucket.byAssetClass().get(AssetClass.EQUITY).tradedValue());
        assertMoney("0", bucket.byAssetClass().get(AssetClass.BOND).tradedValue());
        assertMoney("20", bucket.bySide().get(OrderSide.BUY).tradedValue());
        assertEquals(0, bucket.bySide().get(OrderSide.SELL).orders());
    }

    @Test
    void topTradedInstrumentsAreFilledOrdersRankedByValueAndCappedAtSix() {
        List<OrderActivity> rows = new ArrayList<>();
        String[] symbols = {"A", "B", "C", "D", "E", "F", "G"};
        for (int i = 0; i < symbols.length; i++) {
            rows.add(filled("2026-10-20T14:00:00", symbols[i], i + 1, "100"));   // G is worth most
        }
        rows.add(row("2026-10-20T14:00:00", "A", OrderSide.BUY, OrderStatus.CANCELLED, 1000, "100", "alice"));
        orders(rows.toArray(OrderActivity[]::new));

        List<TopTradedInstrument> top = analytics(AnalyticsPeriod.TODAY).topTradedInstruments();

        assertEquals(List.of("G", "F", "E", "D", "C", "B"), top.stream().map(TopTradedInstrument::symbol).toList());
        assertMoney("700", top.get(0).tradedValue());
    }

    @Test
    void topTradedInstrumentSplitsBuyValueAndCountsTraders() {
        orders(
            row("2026-10-20T14:00:00", "AAPL", OrderSide.BUY, OrderStatus.FILLED, 3, "100", "alice"),
            row("2026-10-20T14:01:00", "AAPL", OrderSide.SELL, OrderStatus.FILLED, 1, "100", "bob"),
            row("2026-10-20T14:02:00", "AAPL", OrderSide.SELL, OrderStatus.FILLED, 1, "100", "bob"));

        TopTradedInstrument apple = analytics(AnalyticsPeriod.TODAY).topTradedInstruments().get(0);

        assertEquals("Apple Inc.", apple.name());
        assertEquals(AssetClass.EQUITY, apple.assetClass());
        assertEquals(3, apple.orders());
        assertEquals(2, apple.traders());
        assertMoney("500", apple.tradedValue());
        assertMoney("300", apple.buyValue());
    }

    @Test
    void assetMixSharesTheFilledValueAcrossEveryAssetClass() {
        orders(
            filled("2026-10-20T14:00:00", "AAPL", 3, "100"),
            filled("2026-10-20T14:01:00", "GLD", 1, "100"));

        List<AssetMixEntry> mix = analytics(AnalyticsPeriod.TODAY).assetMix();

        assertEquals(AssetClass.values().length, mix.size());
        assertMoney("0.75", share(mix, AssetClass.EQUITY));
        assertMoney("0.25", share(mix, AssetClass.COMMODITY));
        assertMoney("0", share(mix, AssetClass.BOND));
    }

    @Test
    void assetMixIsAllZeroWhenNothingTraded() {
        noOrders();

        assertTrue(analytics(AnalyticsPeriod.TODAY).assetMix().stream().allMatch(entry -> entry.share().signum() == 0));
    }

    @Test
    void rankingsAndMixLeaveOutThePreviousPeriod() {
        orders(
            filled("2026-10-19T14:00:00", "GLD", 100, "100"),   // yesterday: previous period only
            filled("2026-10-20T14:00:00", "AAPL", 1, "100"));

        AdminAnalyticsResponse response = analytics(AnalyticsPeriod.TODAY);

        assertEquals(List.of("AAPL"), response.topTradedInstruments().stream().map(TopTradedInstrument::symbol).toList());
        assertMoney("1", share(response.assetMix(), AssetClass.EQUITY));
    }

    // --- Helpers

    private AdminAnalyticsResponse analytics(AnalyticsPeriod period) {
        return service.analytics(period, null, null, null, null);
    }

    // An unstubbed mock already returns an empty list; this just makes the test read clearly
    private void noOrders() {
        orders();
    }

    private void orders(OrderActivity... rows) {
        when(orderRepository.findActivity(any(), any(), any(), any(), any(), any())).thenReturn(List.of(rows));
    }

    private static OrderActivity filled(String utcTime, String symbol, int quantity, String price) {
        return row(utcTime, symbol, OrderSide.BUY, OrderStatus.FILLED, quantity, price, "alice");
    }

    private static OrderActivity row(String utcTime, String symbol, OrderSide side, OrderStatus status,
                                     int quantity, String price, String trader) {
        String name = switch (symbol) {
            case "AAPL" -> "Apple Inc.";
            case "GLD" -> "SPDR Gold Shares";
            default -> symbol + " Corp";
        };
        AssetClass assetClass = symbol.equals("GLD") ? AssetClass.COMMODITY : AssetClass.EQUITY;
        return new OrderActivity(symbol, name, assetClass, side, OrderType.MARKET, status, quantity,
            new BigDecimal(price), LocalDateTime.parse(utcTime), trader);
    }

    private static BigDecimal share(List<AssetMixEntry> mix, AssetClass assetClass) {
        return mix.stream().filter(entry -> entry.assetClass() == assetClass).findFirst().orElseThrow().share();
    }

    // compareTo, so 1500 and 1500.00 count as equal
    private static void assertMoney(String expected, BigDecimal actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual), () -> "expected " + expected + " but was " + actual);
    }
}
