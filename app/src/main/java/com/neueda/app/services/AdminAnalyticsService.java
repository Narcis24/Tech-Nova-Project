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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Trading activity across all accounts for the admin home page: headline totals, chart points and
 * rankings for a period, each compared with the period before it. Data from OrderRepository.findActivity.
 */
@Service
public class AdminAnalyticsService {

    // Days and hours are New York time, like MarketHours.java in the engine and market-hours.ts in the UI
    static final ZoneId MARKET_ZONE = ZoneId.of("America/New_York");
    private static final int TOP_TRADED = 6;
    private static final int RATE_SCALE = 4;
    private static final int LIVE_TRADING_DAYS = 3;
    private static final int LIVE_TOP = 3;
    // The hours the Live bars cover: 9:00 (the 9:30 open falls in it) to the 15:00 hour, before the 16:00 close
    private static final int MARKET_FIRST_HOUR = 9;
    private static final int MARKET_LAST_HOUR = 15;
    // Statuses an order is still waiting in
    private static final Set<OrderStatus> OPEN = EnumSet.of(OrderStatus.PENDING, OrderStatus.PUBLISHED);
    // A plain local time, which spreadsheets read as a date without any setup
    private static final DateTimeFormatter CSV_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final String CSV_HEADER = "start_new_york,traded_value,orders,fill_rate,active_traders,"
        + "buy_value,sell_value,"
        + Arrays.stream(AssetClass.values()).map(c -> c.name().toLowerCase() + "_value").collect(Collectors.joining(","));
    // Statuses an order can't leave; the fill rate is taken over these
    private static final Set<OrderStatus> FINISHED =
        EnumSet.of(OrderStatus.FILLED, OrderStatus.CANCELLED, OrderStatus.REJECTED, OrderStatus.EXPIRED);

    private final OrderRepository orderRepository;
    private final Clock clock;

    public AdminAnalyticsService(OrderRepository orderRepository, Clock clock) {
        this.orderRepository = orderRepository;
        this.clock = clock;
    }

    /** Each filter is optional: null includes everything. */
    @Transactional(readOnly = true)
    public AdminAnalyticsResponse analytics(AnalyticsPeriod period, AssetClass assetClass,
                                            OrderSide side, OrderType orderType, String trader) {
        ZonedDateTime now = ZonedDateTime.now(clock).withZoneSameInstant(MARKET_ZONE);
        Window current = Window.of(period, now);
        Window previous = current.previous();

        // One query covers both periods; split by time afterwards
        ZonedDateTime queryFrom = previous != null ? previous.from() : current.from();
        List<OrderActivity> rows = orderRepository.findActivity(
            utc(queryFrom), utc(current.to()), assetClass, side, orderType, trader);
        Map<Boolean, List<OrderActivity>> beforeCurrent = rows.stream()
            .collect(Collectors.partitioningBy(row -> market(row.createdOn()).isBefore(current.from())));
        List<OrderActivity> currentRows = beforeCurrent.get(false);
        List<OrderActivity> previousRows = beforeCurrent.get(true);

        AnalyticsBucketSize size = period.bucketSize();
        return new AdminAnalyticsResponse(
            period,
            size,
            current.from().toInstant(),
            current.to().toInstant(),
            previous == null ? null : previous.from().toInstant(),
            previous == null ? null : previous.to().toInstant(),
            totals(currentRows),
            previous == null ? null : totals(previousRows),
            buckets(current, size, currentRows, now),
            previous == null ? List.of() : buckets(previous, size, previousRows, now),
            topTraded(currentRows),
            assetMix(currentRows));
    }

    /**
     * The same period and filters as analytics, one CSV row per chart point. Hours that haven't
     * happened yet are left out. Times are New York, as the card shows them.
     */
    @Transactional(readOnly = true)
    public String exportCsv(AnalyticsPeriod period, AssetClass assetClass,
                            OrderSide side, OrderType orderType, String trader) {
        StringBuilder csv = new StringBuilder(CSV_HEADER).append('\n');
        for (AnalyticsBucket bucket : analytics(period, assetClass, side, orderType, trader).buckets()) {
            if (bucket.future()) {
                continue;
            }
            AnalyticsTotals totals = bucket.totals();
            List<Object> cells = new ArrayList<>(List.of(
                CSV_TIME.format(bucket.start().atZone(MARKET_ZONE)),
                totals.tradedValue(),
                totals.orders(),
                totals.fillRate() == null ? "" : totals.fillRate(),   // blank, not 0, when nothing finished
                totals.activeTraders(),
                bucket.bySide().get(OrderSide.BUY).tradedValue(),
                bucket.bySide().get(OrderSide.SELL).tradedValue()));
            for (AssetClass assetClassColumn : AssetClass.values()) {
                cells.add(bucket.byAssetClass().get(assetClassColumn).tradedValue());
            }
            // toPlainString, as toString can give 1E+3, which spreadsheets misread
            csv.append(cells.stream()
                .map(cell -> cell instanceof BigDecimal number ? number.toPlainString() : String.valueOf(cell))
                .collect(Collectors.joining(","))).append('\n');
        }
        return csv.toString();
    }

    /**
     * What's happening right now for the Live panel, with the card's filters but not its period:
     * counts for today and the last hour, open orders, and orders per market hour over the last
     * three trading days.
     */
    @Transactional(readOnly = true)
    public AdminLiveResponse live(AssetClass assetClass, OrderSide side, OrderType orderType, String trader) {
        ZonedDateTime now = ZonedDateTime.now(clock).withZoneSameInstant(MARKET_ZONE);
        ZonedDateTime today = now.toLocalDate().atStartOfDay(MARKET_ZONE);
        List<LocalDate> tradingDays = lastTradingDays(now.toLocalDate(), LIVE_TRADING_DAYS);

        // From the first trading day shown up to midnight tonight; today's orders are in the same rows
        ZonedDateTime from = tradingDays.get(0).atStartOfDay(MARKET_ZONE);
        List<OrderActivity> rows = orderRepository.findActivity(
            utc(from), utc(today.plusDays(1)), assetClass, side, orderType, trader);

        List<OrderActivity> todayRows = rows.stream()
            .filter(row -> !market(row.createdOn()).isBefore(today)).toList();
        ZonedDateTime hourAgo = now.minusHours(1);
        long lastHour = todayRows.stream()
            .filter(row -> market(row.createdOn()).isAfter(hourAgo) && !market(row.createdOn()).isAfter(now))
            .count();
        long open = orderRepository.countWithStatus(OPEN, assetClass, side, orderType, trader);

        return new AdminLiveResponse(now.toInstant(), todayRows.size(), lastHour, open,
            liveHours(tradingDays, rows, now), topToday(todayRows));
    }

    // The given day if it's a weekday, and the weekdays before it, oldest first. Holidays aren't handled,
    // as in MarketHours.java.
    private static List<LocalDate> lastTradingDays(LocalDate day, int count) {
        List<LocalDate> days = new ArrayList<>();
        for (LocalDate d = day; days.size() < count; d = d.minusDays(1)) {
            if (d.getDayOfWeek() != DayOfWeek.SATURDAY && d.getDayOfWeek() != DayOfWeek.SUNDAY) {
                days.add(0, d);
            }
        }
        return days;
    }

    private static List<LiveHour> liveHours(List<LocalDate> tradingDays, List<OrderActivity> rows, ZonedDateTime now) {
        Map<Instant, Long> byHour = rows.stream().collect(Collectors.groupingBy(
            row -> market(row.createdOn()).truncatedTo(ChronoUnit.HOURS).toInstant(), Collectors.counting()));
        List<LiveHour> hours = new ArrayList<>();
        for (LocalDate day : tradingDays) {
            for (int hour = MARKET_FIRST_HOUR; hour <= MARKET_LAST_HOUR; hour++) {
                ZonedDateTime start = day.atTime(hour, 0).atZone(MARKET_ZONE);
                hours.add(new LiveHour(start.toInstant(), byHour.getOrDefault(start.toInstant(), 0L),
                    start.isAfter(now)));
            }
        }
        return hours;
    }

    private static List<LiveInstrument> topToday(List<OrderActivity> todayRows) {
        return todayRows.stream()
            .collect(Collectors.groupingBy(OrderActivity::symbol))
            .values().stream()
            .map(orders -> {
                OrderActivity first = orders.get(0);
                return new LiveInstrument(first.symbol(), first.instrumentName(), first.assetClass(), orders.size());
            })
            // Ties broken by symbol, so the list doesn't reorder between refreshes
            .sorted(Comparator.comparingLong(LiveInstrument::orders).reversed()
                .thenComparing(LiveInstrument::symbol))
            .limit(LIVE_TOP)
            .toList();
    }

    /** A half-open span of time, [from, to), in market time. */
    private record Window(AnalyticsPeriod period, ZonedDateTime from, ZonedDateTime to) {

        static Window of(AnalyticsPeriod period, ZonedDateTime now) {
            ZonedDateTime today = now.toLocalDate().atStartOfDay(MARKET_ZONE);
            ZonedDateTime from = switch (period) {
                case TODAY -> today;
                case LAST_7_DAYS -> today.minusDays(6);      // today and the 6 days before it
                case LAST_30_DAYS -> today.minusDays(29);
                case LAST_90_DAYS -> today.minusDays(89);
                case YEAR_TO_DATE -> now.toLocalDate().withDayOfYear(1).atStartOfDay(MARKET_ZONE);
            };
            // Up to midnight tonight, so today's later hours exist as (future) chart points
            return new Window(period, from, today.plusDays(1));
        }

        /** The same number of days just before this one; null for YEAR_TO_DATE, which isn't compared. */
        Window previous() {
            if (period == AnalyticsPeriod.YEAR_TO_DATE) {
                return null;
            }
            // Calendar days rather than hours, so a daylight-saving change doesn't shift the window
            long days = ChronoUnit.DAYS.between(from.toLocalDate(), to.toLocalDate());
            return new Window(period, from.minusDays(days), from);
        }
    }

    static AnalyticsTotals totals(List<OrderActivity> rows) {
        List<OrderActivity> filled = filled(rows);
        long finished = rows.stream().filter(row -> FINISHED.contains(row.status())).count();
        // null rather than 0 while nothing has finished, so the UI doesn't show a 0% fill rate
        BigDecimal fillRate = finished == 0 ? null
            : BigDecimal.valueOf(filled.size()).divide(BigDecimal.valueOf(finished), RATE_SCALE, RoundingMode.HALF_UP);
        return new AnalyticsTotals(sum(filled), rows.size(), fillRate, traders(rows));
    }

    private static List<AnalyticsBucket> buckets(Window window, AnalyticsBucketSize size,
                                                 List<OrderActivity> rows, ZonedDateTime now) {
        // Instant keys, as ZonedDateTime equality also compares the zone
        Map<Instant, List<OrderActivity>> byBucket = rows.stream()
            .collect(Collectors.groupingBy(row -> bucketStart(market(row.createdOn()), size).toInstant()));

        List<AnalyticsBucket> buckets = new ArrayList<>();
        for (ZonedDateTime start = bucketStart(window.from(), size); start.isBefore(window.to());
             start = next(start, size)) {
            // Every bucket is listed, empty ones with zero totals, so the chart has no gaps
            List<OrderActivity> in = byBucket.getOrDefault(start.toInstant(), List.of());
            buckets.add(new AnalyticsBucket(start.toInstant(), start.isAfter(now), totals(in),
                byAssetClass(in), bySide(in)));
        }
        return buckets;
    }

    private static ZonedDateTime bucketStart(ZonedDateTime time, AnalyticsBucketSize size) {
        return switch (size) {
            case HOUR -> time.truncatedTo(ChronoUnit.HOURS);
            case DAY -> time.toLocalDate().atStartOfDay(MARKET_ZONE);
            case WEEK -> time.toLocalDate().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                .atStartOfDay(MARKET_ZONE);
        };
    }

    private static ZonedDateTime next(ZonedDateTime start, AnalyticsBucketSize size) {
        return switch (size) {
            case HOUR -> start.plusHours(1);
            case DAY -> start.plusDays(1);
            case WEEK -> start.plusWeeks(1);
        };
    }

    // Every key is present, even with no orders, so clients needn't check for missing ones
    private static Map<AssetClass, AnalyticsTotals> byAssetClass(List<OrderActivity> rows) {
        Map<AssetClass, AnalyticsTotals> byClass = new EnumMap<>(AssetClass.class);
        for (AssetClass assetClass : AssetClass.values()) {
            byClass.put(assetClass, totals(rows.stream().filter(row -> row.assetClass() == assetClass).toList()));
        }
        return byClass;
    }

    private static Map<OrderSide, AnalyticsTotals> bySide(List<OrderActivity> rows) {
        Map<OrderSide, AnalyticsTotals> bySide = new EnumMap<>(OrderSide.class);
        for (OrderSide side : OrderSide.values()) {
            bySide.put(side, totals(rows.stream().filter(row -> row.side() == side).toList()));
        }
        return bySide;
    }

    private static List<TopTradedInstrument> topTraded(List<OrderActivity> rows) {
        return filled(rows).stream()
            .collect(Collectors.groupingBy(OrderActivity::symbol))
            .values().stream()
            .map(orders -> {
                OrderActivity first = orders.get(0);
                BigDecimal buyValue = sum(orders.stream().filter(row -> row.side() == OrderSide.BUY).toList());
                return new TopTradedInstrument(first.symbol(), first.instrumentName(), first.assetClass(),
                    orders.size(), traders(orders), sum(orders), buyValue);
            })
            // Ties broken by symbol, so the order doesn't change between requests
            .sorted(Comparator.comparing(TopTradedInstrument::tradedValue).reversed()
                .thenComparing(TopTradedInstrument::symbol))
            .limit(TOP_TRADED)
            .toList();
    }

    private static List<AssetMixEntry> assetMix(List<OrderActivity> rows) {
        Map<AssetClass, AnalyticsTotals> byClass = byAssetClass(rows);
        BigDecimal total = sum(filled(rows));
        return Arrays.stream(AssetClass.values())
            .map(assetClass -> {
                BigDecimal value = byClass.get(assetClass).tradedValue();
                BigDecimal share = total.signum() == 0 ? BigDecimal.ZERO
                    : value.divide(total, RATE_SCALE, RoundingMode.HALF_UP);
                return new AssetMixEntry(assetClass, value, share);
            })
            .toList();
    }

    private static List<OrderActivity> filled(List<OrderActivity> rows) {
        return rows.stream().filter(row -> row.status() == OrderStatus.FILLED).toList();
    }

    private static BigDecimal sum(List<OrderActivity> rows) {
        return rows.stream().map(OrderActivity::value).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    // Orders on unclaimed accounts have no owner and count as no one
    private static long traders(List<OrderActivity> rows) {
        return rows.stream().map(OrderActivity::trader).filter(Objects::nonNull).distinct().count();
    }

    private static LocalDateTime utc(ZonedDateTime time) {
        return time.withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime();
    }

    // orders.created_on is stored as UTC
    private static ZonedDateTime market(LocalDateTime utcTime) {
        return utcTime.atOffset(ZoneOffset.UTC).atZoneSameInstant(MARKET_ZONE);
    }
}
