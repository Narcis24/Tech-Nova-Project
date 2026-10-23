package com.neueda.app.repositories;

import com.neueda.app.models.InstrumentProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface InstrumentProfileRepository extends JpaRepository<InstrumentProfile, String> {
}
