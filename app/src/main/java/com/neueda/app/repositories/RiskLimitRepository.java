package com.neueda.app.repositories;

import com.neueda.app.enums.RiskRule;
import com.neueda.app.models.RiskLimit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface RiskLimitRepository extends JpaRepository<RiskLimit, RiskRule> {
}
