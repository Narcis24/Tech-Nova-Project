package com.neueda.app.repositories;

import com.neueda.app.enums.PieVisibility;
import com.neueda.app.models.Pie;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface PieRepository extends JpaRepository<Pie, UUID> {
    List<Pie> findByOwnerUsernameAndDeletedAtIsNullOrderByCreatedAt(String owner);

    Optional<Pie> findByPieIdAndOwnerUsernameAndDeletedAtIsNull(UUID id, String owner);

    /** Locks the row until the transaction ends, so edits, publishing and the index job of one pie run one at a time. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Pie p where p.pieId = :id and p.ownerUsername = :owner and p.deletedAt is null")
    Optional<Pie> lockOwned(@Param("id") UUID id, @Param("owner") String owner);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Pie p where p.pieId = :id and p.visibility = com.neueda.app.enums.PieVisibility.PUBLIC and p.deletedAt is null")
    Optional<Pie> lockPublic(@Param("id") UUID id);

    List<Pie> findByVisibilityAndDeletedAtIsNull(PieVisibility visibility);

    Optional<Pie> findByPieIdAndVisibilityAndDeletedAtIsNull(UUID id, PieVisibility visibility);
}
