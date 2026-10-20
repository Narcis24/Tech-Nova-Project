package com.neueda.app.services;

import com.neueda.app.dtos.AdminAnalyticsResponse;
import com.neueda.app.dtos.AdminLiveResponse;
import com.neueda.app.dtos.AnalyticsBucket;
import com.neueda.app.dtos.AnalyticsTotals;
import com.neueda.app.dtos.AssetMixEntry;
import com.neueda.app.dtos.LiveHour;
import com.neueda.app.dtos.LiveInstrument;
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
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
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
            filled("2026-10-13T14:00:00", "AAPL", 1, "100"),    // 10:00 on 13 Oct in New York: previous period
            filled("2026-10-14T03:59:00", "AAPL", 1, "50"),     // 23:59 on 13 Oct: previous, but after "so far"
            filled("2026-10-14T04:00:00", "AAPL", 1, "200"));   // 00:00 on 14 Oct: current period

        AdminAnalyticsResponse response = analytics(AnalyticsPeriod.LAST_7_DAYS);

        assertMoney("200", response.totals().tradedValue());
        assertMoney("100", response.previousTotals().tradedValue());
        // Both previous orders are on the chart's dashed line, which shows the whole previous period
        assertEquals(2, response.previousBuckets().stream().mapToLong(bucket -> bucket.totals().orders()).sum());
    }

    @Test
    void todayIsComparedWithYesterdayUpToTheSameTime() {
        orders(
            filled("2026-10-19T13:00:00", "AAPL", 1, "100"),    // yesterday 09:00 in New York
            filled("2026-10-19T18:29:00", "AAPL", 1, "10"),     // yesterday 14:29, just before now's time of day
            filled("2026-10-19T18:31:00", "AAPL", 1, "1000"),   // yesterday 14:31, after it
            filled("2026-10-19T20:00:00", "AAPL", 1, "1000"),   // yesterday 16:00
            filled("2026-10-20T14:00:00", "AAPL", 1, "300"));   // today 10:00

        AdminAnalyticsResponse response = analytics(AnalyticsPeriod.TODAY);

        assertMoney("300", response.totals().tradedValue());
        // Today until 14:30 against yesterday until 14:30, not against all of yesterday
        assertMoney("110", response.previousTotals().tradedValue());
        assertEquals(2, response.previousTotals().orders());
        // The dashed line still has all of yesterday
        assertEquals(4, response.previousBuckets().stream().mapToLong(bucket -> bucket.totals().orders()).sum());
    }

    @Test
    void longerPeriodsAreComparedUpToTheSameTimeTheSameNumberOfDaysEarlier() {
        orders(
            filled("2026-10-13T18:29:00", "AAPL", 1, "10"),     // 13 Oct 14:29: 7 days before now, just inside
            filled("2026-10-13T18:31:00", "AAPL", 1, "1000"));  // 13 Oct 14:31: just after

        AdminAnalyticsResponse response = analytics(AnalyticsPeriod.LAST_7_DAYS);

        assertMoney("10", response.previousTotals().tradedValue());
    }

    @Test
    void theComparisonKeepsTheClockTimeAcrossAClockChange() {
        // Wednesday 4 Nov 2026, noon in New York (EST); 7 days earlier, 28 Oct, was still EDT
        AdminAnalyticsService afterClockChange = new AdminAnalyticsService(orderRepository,
            Clock.fixed(Instant.parse("2026-11-04T17:00:00Z"), ZoneOffset.UTC));
        orders(
            filled("2026-10-28T15:59:00", "AAPL", 1, "10"),     // 28 Oct 11:59 EDT: before noon, included
            filled("2026-10-28T16:01:00", "AAPL", 1, "1000"));  // 28 Oct 12:01 EDT: after noon, left out

        AdminAnalyticsResponse response = afterClockChange.analytics(AnalyticsPeriod.LAST_7_DAYS, null, null, null, null);

        assertMoney("10", response.previousTotals().tradedValue());
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

    // --- Live

    @Test
    void liveHasSevenMarketHoursForTodayAndTheTwoTradingDaysBefore() {
        AdminLiveResponse live = live();

        assertEquals(NOW, live.asOf());
        assertEquals(21, live.hourly().size());
        // Tuesday, so Friday and Monday; the weekend is skipped
        assertEquals(Instant.parse("2026-10-16T13:00:00Z"), live.hourly().get(0).start());    // Fri 09:00 New York
        assertEquals(Instant.parse("2026-10-16T19:00:00Z"), live.hourly().get(6).start());    // Fri 15:00
        assertEquals(Instant.parse("2026-10-19T13:00:00Z"), live.hourly().get(7).start());    // Mon 09:00
        assertEquals(Instant.parse("2026-10-20T13:00:00Z"), live.hourly().get(14).start());   // Tue 09:00
    }

    @Test
    void liveMarksTodaysLaterHoursAsFuture() {
        List<LiveHour> hourly = live().hourly();

        assertFalse(hourly.get(13).future());   // Mon 15:00
        assertFalse(hourly.get(19).future());   // Tue 14:00, the hour it is now
        assertTrue(hourly.get(20).future());    // Tue 15:00
    }

    @Test
    void liveOnMondayGoesBackToThursdayAndFriday() {
        // Monday 19 Oct 2026, 10:00 in New York
        AdminLiveResponse live = new AdminAnalyticsService(orderRepository,
            Clock.fixed(Instant.parse("2026-10-19T14:00:00Z"), ZoneOffset.UTC)).live(null, null, null, null);

        assertEquals(Instant.parse("2026-10-15T13:00:00Z"), live.hourly().get(0).start());    // Thu 09:00
        assertEquals(Instant.parse("2026-10-16T13:00:00Z"), live.hourly().get(7).start());    // Fri 09:00
        assertEquals(Instant.parse("2026-10-19T13:00:00Z"), live.hourly().get(14).start());   // Mon 09:00
    }

    @Test
    void liveAtTheWeekendShowsTheLastThreeWeekdaysWithNothingInTheFuture() {
        // Saturday 17 Oct 2026, noon in New York
        AdminLiveResponse live = new AdminAnalyticsService(orderRepository,
            Clock.fixed(Instant.parse("2026-10-17T16:00:00Z"), ZoneOffset.UTC)).live(null, null, null, null);

        assertEquals(Instant.parse("2026-10-14T13:00:00Z"), live.hourly().get(0).start());    // Wed 09:00
        assertEquals(Instant.parse("2026-10-16T19:00:00Z"), live.hourly().get(20).start());   // Fri 15:00
        assertTrue(live.hourly().stream().noneMatch(LiveHour::future));
    }

    @Test
    void liveReadsFromTheFirstTradingDayToMidnightTonightWithTheFilters() {
        service.live(AssetClass.ETF, OrderSide.SELL, OrderType.LIMIT, "alice");

        verify(orderRepository).findActivity(LocalDateTime.of(2026, 10, 16, 4, 0), LocalDateTime.of(2026, 10, 21, 4, 0),
            AssetClass.ETF, OrderSide.SELL, OrderType.LIMIT, "alice");
        verify(orderRepository).countWithStatus(argThat(statuses -> statuses.size() == 2
                && statuses.containsAll(List.of(OrderStatus.PENDING, OrderStatus.PUBLISHED))),
            eq(AssetClass.ETF), eq(OrderSide.SELL), eq(OrderType.LIMIT), eq("alice"));
    }

    @Test
    void liveCountsOrdersTodayAndInTheLastHour() {
        orders(
            filled("2026-10-19T15:00:00", "AAPL", 1, "1"),    // yesterday
            filled("2026-10-20T12:00:00", "AAPL", 1, "1"),    // 08:00, before the open
            filled("2026-10-20T17:30:00", "AAPL", 1, "1"),    // 13:30, exactly an hour ago: not in the last hour
            filled("2026-10-20T17:45:00", "AAPL", 1, "1"),    // 13:45
            filled("2026-10-20T18:30:00", "AAPL", 1, "1"));   // 14:30, now

        AdminLiveResponse live = live();

        assertEquals(4, live.ordersToday());
        assertEquals(2, live.ordersLastHour());
    }

    @Test
    void liveBarsCountOrdersInTheirNewYorkMarketHour() {
        orders(
            filled("2026-10-16T19:59:00", "AAPL", 1, "1"),    // Fri 15:59, the last bar of Friday
            filled("2026-10-20T12:00:00", "AAPL", 1, "1"),    // Tue 08:00: today's total, but no bar
            filled("2026-10-20T13:15:00", "AAPL", 1, "1"),    // Tue 09:15
            filled("2026-10-20T13:45:00", "GLD", 1, "1"));    // Tue 09:45

        AdminLiveResponse live = live();

        assertEquals(1, live.hourly().get(6).orders());
        assertEquals(2, live.hourly().get(14).orders());
        assertEquals(3, live.hourly().stream().mapToLong(LiveHour::orders).sum());
        assertEquals(3, live.ordersToday());
    }

    @Test
    void liveOpenOrdersComeFromTheStatusCount() {
        when(orderRepository.countWithStatus(any(), any(), any(), any(), any())).thenReturn(4L);

        assertEquals(4, live().openOrders());
    }

    @Test
    void liveTopTodayCountsEveryOrderTodayAndKeepsTheTopThree() {
        orders(
            filled("2026-10-19T15:00:00", "NVDA", 1, "1"),    // yesterday: not counted
            filled("2026-10-19T15:01:00", "NVDA", 1, "1"),
            filled("2026-10-19T15:02:00", "NVDA", 1, "1"),
            row("2026-10-20T14:00:00", "AAPL", OrderSide.BUY, OrderStatus.CANCELLED, 1, "1", "alice"),
            filled("2026-10-20T14:01:00", "AAPL", 1, "1"),
            filled("2026-10-20T14:02:00", "AAPL", 1, "1"),
            filled("2026-10-20T14:03:00", "GLD", 1, "1"),
            filled("2026-10-20T14:04:00", "GLD", 1, "1"),
            filled("2026-10-20T14:05:00", "MSFT", 1, "1"),
            filled("2026-10-20T14:06:00", "IBM", 1, "1"));   // ties with MSFT; MSFT loses on symbol order

        List<LiveInstrument> top = live().topToday();

        assertEquals(List.of("AAPL", "GLD", "IBM"), top.stream().map(LiveInstrument::symbol).toList());
        assertEquals(3, top.get(0).orders());
        assertEquals("Apple Inc.", top.get(0).name());
        assertEquals(AssetClass.COMMODITY, top.get(1).assetClass());
    }

    @Test
    void liveWithNoOrdersIsAllZero() {
        AdminLiveResponse live = live();

        assertEquals(0, live.ordersToday());
        assertEquals(0, live.ordersLastHour());
        assertEquals(0, live.openOrders());
        assertTrue(live.hourly().stream().allMatch(hour -> hour.orders() == 0));
        assertTrue(live.topToday().isEmpty());
    }

    // --- CSV export

    @Test
    void exportHasAHeaderAndOneRowPerChartPointSoFar() {
        noOrders();

        List<String> lines = service.exportCsv(AnalyticsPeriod.TODAY, null, null, null, null).lines().toList();

        assertEquals("start_new_york,traded_value,orders,fill_rate,active_traders,buy_value,sell_value,"
            + "equity_value,bond_value,commodity_value,etf_value", lines.get(0));
        // Midnight to the 14:00 hour; 15:00 onwards hasn't happened yet
        assertEquals(1 + 15, lines.size());
        assertEquals("2026-10-20 00:00,0,0,,0,0,0,0,0,0,0", lines.get(1));
        assertTrue(lines.get(15).startsWith("2026-10-20 14:00,"));
    }

    @Test
    void exportRowsMatchTheChartPointsInNewYorkTime() {
        orders(
            row("2026-10-20T13:15:00", "AAPL", OrderSide.BUY, OrderStatus.FILLED, 10, "150.00", "alice"),   // 09:15
            row("2026-10-20T13:20:00", "GLD", OrderSide.SELL, OrderStatus.FILLED, 2, "100.00", "bob"),
            row("2026-10-20T13:25:00", "AAPL", OrderSide.BUY, OrderStatus.CANCELLED, 1, "150.00", "bob"));

        String nineOClock = service.exportCsv(AnalyticsPeriod.TODAY, null, null, null, null).lines()
            .filter(line -> line.startsWith("2026-10-20 09:00,")).findFirst().orElseThrow();

        // traded, orders, fill rate (2 of 3), traders, buy, sell, then equity, bond, commodity, etf
        assertEquals("2026-10-20 09:00,1700.00,3,0.6667,2,1500.00,200.00,1500.00,0,200.00,0", nineOClock);
    }

    @Test
    void exportWritesLargeValuesWithoutScientificNotation() {
        orders(filled("2026-10-20T14:00:00", "AAPL", 100000, "1000.00"));

        String csv = service.exportCsv(AnalyticsPeriod.TODAY, null, null, null, null);

        assertTrue(csv.contains(",100000000.00,"));
        assertFalse(csv.contains("E+"));
    }

    @Test
    void exportUsesTheSameQueryAsTheDashboard() {
        noOrders();

        service.exportCsv(AnalyticsPeriod.LAST_7_DAYS, AssetClass.ETF, OrderSide.SELL, OrderType.LIMIT, "alice");

        verify(orderRepository).findActivity(LocalDateTime.of(2026, 10, 7, 4, 0), LocalDateTime.of(2026, 10, 21, 4, 0),
            AssetClass.ETF, OrderSide.SELL, OrderType.LIMIT, "alice");
    }

    // --- Helpers

    private AdminLiveResponse live() {
        return service.live(null, null, null, null);
    }

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
