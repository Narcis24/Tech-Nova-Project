package com.neueda.app.services;

import com.neueda.app.dtos.IndexPoint;
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
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The leaderboard and public pie pages. They show a pie's slices, strategy index and follower count, never
 * any account or money. The seeded demo pies belong to the reserved author "demo".
 */
@Service
public class PublicPieService {

    static final String DEMO_AUTHOR = "demo";

    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private final PieRepository pieRepository;
    private final PieSliceRepository pieSliceRepository;
    private final PieIndexPointRepository pointRepository;
    private final AccountPieRepository accountPieRepository;
    private final PieService pieService;

    public PublicPieService(PieRepository pieRepository, PieSliceRepository pieSliceRepository,
                            PieIndexPointRepository pointRepository, AccountPieRepository accountPieRepository,
                            PieService pieService) {
        this.pieRepository = pieRepository;
        this.pieSliceRepository = pieSliceRepository;
        this.pointRepository = pointRepository;
        this.accountPieRepository = accountPieRepository;
        this.pieService = pieService;
    }

    /** Public pies old enough for the window, best return first. Ties go to the pie with more followers. */
    @Transactional(readOnly = true)
    public List<PublicPieSummary> list(String window) {
        PieWindow parsed = PieWindow.parse(window);
        List<Pie> pies = pieRepository.findByVisibilityAndDeletedAtIsNull(PieVisibility.PUBLIC);
        if (pies.isEmpty()) {
            return List.of();
        }
        List<UUID> ids = pies.stream().map(Pie::getPieId).toList();
        Map<UUID, List<PieIndexPoint>> points = pointRepository.findByPieIdInOrderByDay(ids).stream()
            .collect(Collectors.groupingBy(PieIndexPoint::getPieId));
        Map<UUID, Long> followers = followers(ids);

        return pies.stream()
            .map(pie -> summary(pie, returnPct(points.getOrDefault(pie.getPieId(), List.of()), parsed),
                followers.getOrDefault(pie.getPieId(), 0L),
                pieSliceRepository.countByPieIdAndVersion(pie.getPieId(), pie.getLatestVersion())))
            .filter(summary -> summary.returnPct() != null)
            .sorted(Comparator.comparing(PublicPieSummary::returnPct).reversed()
                .thenComparing(Comparator.comparingLong(PublicPieSummary::followers).reversed())
                .thenComparing(PublicPieSummary::publishedAt)
                .thenComparing(PublicPieSummary::pieId))
            .toList();
    }

    @Transactional(readOnly = true)
    public PublicPieResponse get(UUID pieId) {
        Pie pie = pieRepository.findByPieIdAndVisibilityAndDeletedAtIsNull(pieId, PieVisibility.PUBLIC)
            .orElseThrow(() -> new PieNotFoundException("Pie not found: " + pieId));
        List<PieSlice> slices = pieService.latestSlices(pie);
        List<PieIndexPoint> points = pointRepository.findByPieIdOrderByDay(pieId);
        Map<String, BigDecimal> returns = new LinkedHashMap<>();
        for (PieWindow window : PieWindow.values()) {
            returns.put(window.label(), returnPct(points, window));
        }
        PublicPieSummary summary = summary(pie, returns.get(PieWindow.ALL.label()),
            followers(List.of(pieId)).getOrDefault(pieId, 0L), slices.size());
        return new PublicPieResponse(summary.pieId(), summary.name(), summary.description(), summary.authorUsername(),
            summary.sliceCount(), summary.returnPct(), summary.followers(), summary.publishedAt(), summary.demo(),
            pieService.sliceResponses(slices), PieService.cashPct(slices),
            points.stream().map(p -> new IndexPoint(p.getDay(), p.getValue())).toList(), returns);
    }

    /**
     * Index return in percent over the window, ending at the latest recorded day. Null when the pie has no
     * history reaching back that far. ALL starts at the first day.
     */
    static BigDecimal returnPct(List<PieIndexPoint> points, PieWindow window) {
        if (points.isEmpty()) {
            return null;
        }
        PieIndexPoint last = points.get(points.size() - 1);
        PieIndexPoint base = points.get(0);
        if (window.days() != null) {
            LocalDate start = last.getDay().minusDays(window.days());
            base = points.stream().filter(p -> !p.getDay().isAfter(start)).reduce((a, b) -> b).orElse(null);
            if (base == null) {
                return null;
            }
        }
        return last.getValue().divide(base.getValue(), 10, RoundingMode.HALF_UP)
            .subtract(BigDecimal.ONE).multiply(HUNDRED).setScale(2, RoundingMode.HALF_UP);
    }

    private Map<UUID, Long> followers(List<UUID> ids) {
        return accountPieRepository.countFollowers(ids).stream()
            .collect(Collectors.toMap(FollowerCount::getPieId, FollowerCount::getFollowers));
    }

    private static PublicPieSummary summary(Pie pie, BigDecimal returnPct, long followers, int sliceCount) {
        return new PublicPieSummary(pie.getPieId(), pie.getName(), pie.getDescription(), pie.getOwnerUsername(),
            sliceCount, returnPct, followers, pie.getPublishedAt(), Objects.equals(DEMO_AUTHOR, pie.getOwnerUsername()));
    }
}
