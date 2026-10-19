package com.neueda.app.repositories;

import com.neueda.app.models.PieVersion;
import com.neueda.app.models.PieVersionKey;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PieVersionRepository extends JpaRepository<PieVersion, PieVersionKey> {
    boolean existsByPieIdAndVersionGreaterThanAndCreatedAtAfter(UUID pieId, int version, LocalDateTime since);

    /** The version live at that instant: the newest one created before it. */
    Optional<PieVersion> findFirstByPieIdAndCreatedAtBeforeOrderByVersionDesc(UUID pieId, LocalDateTime before);
}
