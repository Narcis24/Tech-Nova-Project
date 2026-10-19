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
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The daily strategy index of public pies, in UTC calendar days. It is 100 on the publish day. Day d then
 * uses the newest version created before the start of d, so a version made during d takes effect on d + 1
 * and nobody can pick the winners after the day has ended. Prices are the latest close on or before each day.
 * Limitation: a day is recorded once and never revised, so closes the data pipeline loads late are not
 * picked up for days already recorded.
 */
@Service
public class PieIndexService {

    static final BigDecimal START_VALUE = new BigDecimal("100.000000");

    private static final BigDecimal HUNDRED = new BigDecimal("100");
    private static final MathContext PRECISION = new MathContext(20, RoundingMode.HALF_UP);

    private final PieRepository pieRepository;
    private final PieVersionRepository pieVersionRepository;
    private final PieSliceRepository pieSliceRepository;
    private final PieIndexPointRepository pointRepository;
    private final PriceRepository priceRepository;
    private final Clock clock;

    public PieIndexService(PieRepository pieRepository, PieVersionRepository pieVersionRepository,
                           PieSliceRepository pieSliceRepository, PieIndexPointRepository pointRepository,
                           PriceRepository priceRepository, Clock clock) {
        this.pieRepository = pieRepository;
        this.pieVersionRepository = pieVersionRepository;
        this.pieSliceRepository = pieSliceRepository;
        this.pointRepository = pointRepository;
        this.priceRepository = priceRepository;
        this.clock = clock;
    }

    /** Throws away any history and starts the index at 100 on the given day. */
    @Transactional
    public void restart(UUID pieId, LocalDate day) {
        pointRepository.deleteByPieId(pieId);
        pointRepository.save(new PieIndexPoint(pieId, day, START_VALUE));
    }

    @Transactional
    public void clear(UUID pieId) {
        pointRepository.deleteByPieId(pieId);
    }

    /**
     * Records every day after the last recorded one up to yesterday (UTC), so a missed run is made up
     * and running twice changes nothing. Does nothing for a pie that is private or deleted.
     */
    @Transactional
    public void catchUp(UUID pieId) {
        Pie pie = pieRepository.lockPublic(pieId).orElse(null);
        if (pie == null) {
            return;
        }
        PieIndexPoint last = pointRepository.findFirstByPieIdOrderByDayDesc(pieId)
            .orElseGet(() -> pointRepository.save(new PieIndexPoint(pieId, pie.getPublishedAt().toLocalDate(), START_VALUE)));
        LocalDate yesterday = LocalDate.now(clock).minusDays(1);
        BigDecimal value = last.getValue();
        for (LocalDate day = last.getDay().plusDays(1); !day.isAfter(yesterday); day = day.plusDays(1)) {
            value = valueOn(pieId, day, value);
            pointRepository.save(new PieIndexPoint(pieId, day, value));
        }
    }

    private BigDecimal valueOn(UUID pieId, LocalDate day, BigDecimal previous) {
        PieVersion version = pieVersionRepository
            .findFirstByPieIdAndCreatedAtBeforeOrderByVersionDesc(pieId, day.atStartOfDay()).orElse(null);
        if (version == null) {
            return previous;
        }
        Map<String, BigDecimal> weights = pieSliceRepository
            .findByPieIdAndVersionOrderByWeightPctDescSymbolAsc(pieId, version.getVersion()).stream()
            .collect(Collectors.toMap(PieSlice::getSymbol, PieSlice::getWeightPct));
        return next(previous, weights, closesOn(weights.keySet(), day.minusDays(1)), closesOn(weights.keySet(), day));
    }

    private Map<String, BigDecimal> closesOn(Collection<String> symbols, LocalDate day) {
        if (symbols.isEmpty()) {
            return Map.of();
        }
        List<Price> closes = priceRepository.findClosesOnOrBefore(symbols, day);
        return closes.stream().collect(Collectors.toMap(Price::getSymbol, Price::getPrice));
    }

    /**
     * previous x (cash + sum of weight x close today / close yesterday), weights in percent. Cash is whatever
     * the weights leave out and does not move. A missing or non-positive close counts as no change.
     */
    static BigDecimal next(BigDecimal previous, Map<String, BigDecimal> weightsPct,
                           Map<String, BigDecimal> closesYesterday, Map<String, BigDecimal> closesToday) {
        BigDecimal total = HUNDRED;
        BigDecimal grown = BigDecimal.ZERO;
        for (Map.Entry<String, BigDecimal> slice : weightsPct.entrySet()) {
            BigDecimal weight = slice.getValue();
            total = total.subtract(weight);
            grown = grown.add(weight.multiply(ratio(closesYesterday.get(slice.getKey()), closesToday.get(slice.getKey())), PRECISION));
        }
        BigDecimal growth = total.add(grown).divide(HUNDRED, PRECISION);
        return previous.multiply(growth, PRECISION).setScale(6, RoundingMode.HALF_UP);
    }

    private static BigDecimal ratio(BigDecimal yesterday, BigDecimal today) {
        if (yesterday == null || today == null || yesterday.signum() <= 0 || today.signum() <= 0) {
            return BigDecimal.ONE;
        }
        return today.divide(yesterday, PRECISION);
    }
}
