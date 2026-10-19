package com.neueda.app.repositories;

import com.neueda.app.models.Pie;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PieRepository extends JpaRepository<Pie, UUID> {
    List<Pie> findByOwnerUsernameAndDeletedAtIsNullOrderByCreatedAt(String owner);

    Optional<Pie> findByPieIdAndOwnerUsernameAndDeletedAtIsNull(UUID id, String owner);
}
