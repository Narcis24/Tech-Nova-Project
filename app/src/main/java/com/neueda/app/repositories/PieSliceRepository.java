package com.neueda.app.repositories;

import com.neueda.app.models.PieSlice;
import com.neueda.app.models.PieSliceKey;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PieSliceRepository extends JpaRepository<PieSlice, PieSliceKey> {
    List<PieSlice> findByPieIdAndVersionOrderByWeightPctDescSymbolAsc(UUID pieId, int version);

    int countByPieIdAndVersion(UUID pieId, int version);
}
