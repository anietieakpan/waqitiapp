package com.waqiti.insurance.repository;

import com.waqiti.insurance.entity.ActuarialData;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ActuarialDataRepository extends JpaRepository<ActuarialData, UUID> {

    @Query("SELECT a FROM ActuarialData a WHERE a.policyType = :policyType AND a.ageGroup = :ageGroup AND a.riskCategory = :riskCategory AND a.effectiveFrom <= :now AND (a.effectiveTo IS NULL OR a.effectiveTo >= :now)")
    Optional<ActuarialData> findCurrentRates(
            @Param("policyType") String policyType,
            @Param("ageGroup") String ageGroup,
            @Param("riskCategory") String riskCategory,
            @Param("now") LocalDateTime now);
}
