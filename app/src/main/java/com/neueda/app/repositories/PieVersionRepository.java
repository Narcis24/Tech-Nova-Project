package com.neueda.app.repositories;

import com.neueda.app.models.PieVersion;
import com.neueda.app.models.PieVersionKey;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PieVersionRepository extends JpaRepository<PieVersion, PieVersionKey> {
}
