package com.neueda.app.repositories;

import com.neueda.app.models.PieIndexPoint;
import com.neueda.app.models.PieIndexPointKey;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface PieIndexPointRepository extends JpaRepository<PieIndexPoint, PieIndexPointKey> {
    List<PieIndexPoint> findByPieIdOrderByDay(UUID pieId);

    List<PieIndexPoint> findByPieIdInOrderByDay(Collection<UUID> pieIds);

    Optional<PieIndexPoint> findFirstByPieIdOrderByDayDesc(UUID pieId);

    /** A bulk delete, so it runs at once and a point for the same day can be saved right after. */
    @Modifying
    @Query("delete from PieIndexPoint p where p.pieId = :pieId")
    void deleteByPieId(@Param("pieId") UUID pieId);
}
