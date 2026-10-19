package com.neueda.app.services;

import com.neueda.app.enums.PieVisibility;
import com.neueda.app.models.Pie;
import com.neueda.app.repositories.PieRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Records the daily index of every public pie. Each pie is its own transaction, so one failing does not stop the rest. */
@Slf4j
@Component
public class PieIndexJob {

    private final PieRepository pieRepository;
    private final PieIndexService pieIndexService;

    public PieIndexJob(PieRepository pieRepository, PieIndexService pieIndexService) {
        this.pieRepository = pieRepository;
        this.pieIndexService = pieIndexService;
    }

    @Scheduled(cron = "0 30 0 * * *", zone = "UTC")
    public void run() {
        for (Pie pie : pieRepository.findByVisibilityAndDeletedAtIsNull(PieVisibility.PUBLIC)) {
            try {
                pieIndexService.catchUp(pie.getPieId());
            } catch (RuntimeException e) {
                log.error("Could not record the index of pie {}", pie.getPieId(), e);
            }
        }
    }
}
