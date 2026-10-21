package com.neueda.app.services;

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
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Public pie pages show a pie's slices and follower count, never
 * any account or money. The seeded demo pies belong to the reserved author "demo".
 */
@Service
public class PublicPieService {

    static final String DEMO_AUTHOR = "demo";

    private final PieRepository pieRepository;
    private final PieSliceRepository pieSliceRepository;
    private final AccountPieRepository accountPieRepository;
    private final PieService pieService;

    public PublicPieService(PieRepository pieRepository, PieSliceRepository pieSliceRepository,
                            AccountPieRepository accountPieRepository,
                            PieService pieService) {
        this.pieRepository = pieRepository;
        this.pieSliceRepository = pieSliceRepository;
        this.accountPieRepository = accountPieRepository;
        this.pieService = pieService;
    }

    /** Published pies are immediately available, ordered by name. */
    @Transactional(readOnly = true)
    public List<PublicPieSummary> list() {
        List<Pie> pies = pieRepository.findByVisibilityAndDeletedAtIsNull(PieVisibility.PUBLIC);
        if (pies.isEmpty()) {
            return List.of();
        }
        Map<UUID, Long> followers = followers(pies.stream().map(Pie::getPieId).toList());
        return pies.stream()
            .map(pie -> summary(pie, followers.getOrDefault(pie.getPieId(), 0L),
                pieSliceRepository.countByPieIdAndVersion(pie.getPieId(), pie.getLatestVersion())))
            .sorted(Comparator.comparing(PublicPieSummary::name, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(PublicPieSummary::pieId))
            .toList();
    }

    @Transactional(readOnly = true)
    public PublicPieResponse get(UUID pieId) {
        Pie pie = pieRepository.findByPieIdAndVisibilityAndDeletedAtIsNull(pieId, PieVisibility.PUBLIC)
            .orElseThrow(() -> new PieNotFoundException("Pie not found: " + pieId));
        List<PieSlice> slices = pieService.latestSlices(pie);
        PublicPieSummary summary = summary(pie, followers(List.of(pieId)).getOrDefault(pieId, 0L), slices.size());
        return new PublicPieResponse(summary.pieId(), summary.name(), summary.description(), summary.authorUsername(),
            summary.sliceCount(), summary.followers(), summary.publishedAt(), summary.demo(),
            pieService.sliceResponses(slices), PieService.cashPct(slices));
    }

    private Map<UUID, Long> followers(List<UUID> ids) {
        return accountPieRepository.countFollowers(ids).stream()
            .collect(Collectors.toMap(FollowerCount::getPieId, FollowerCount::getFollowers));
    }

    private static PublicPieSummary summary(Pie pie, long followers, int sliceCount) {
        return new PublicPieSummary(pie.getPieId(), pie.getName(), pie.getDescription(), pie.getOwnerUsername(),
            sliceCount, followers, pie.getPublishedAt(), Objects.equals(DEMO_AUTHOR, pie.getOwnerUsername()));
    }
}
