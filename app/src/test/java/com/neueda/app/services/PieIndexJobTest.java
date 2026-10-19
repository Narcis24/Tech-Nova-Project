package com.neueda.app.services;

import com.neueda.app.enums.PieVisibility;
import com.neueda.app.models.Pie;
import com.neueda.app.repositories.PieRepository;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PieIndexJobTest {

    @Test
    void run_oneFailingPieDoesNotStopTheOthers() {
        PieRepository pieRepository = mock(PieRepository.class);
        PieIndexService indexService = mock(PieIndexService.class);
        Pie broken = publicPie("Broken");
        Pie fine = publicPie("Fine");
        when(pieRepository.findByVisibilityAndDeletedAtIsNull(PieVisibility.PUBLIC)).thenReturn(List.of(broken, fine));
        doThrow(new IllegalStateException("boom")).when(indexService).catchUp(broken.getPieId());

        new PieIndexJob(pieRepository, indexService).run();

        verify(indexService).catchUp(broken.getPieId());
        verify(indexService).catchUp(fine.getPieId());
    }

    private static Pie publicPie(String name) {
        Pie pie = Pie.create("jay", name, null);
        pie.publish(LocalDateTime.of(2026, 3, 1, 0, 0));
        return pie;
    }
}
