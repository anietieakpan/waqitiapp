package com.waqiti.compliance.repository;

import com.waqiti.compliance.domain.ManualFlag;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface ManualFlagRepository extends JpaRepository<ManualFlag, UUID> {

    @Query("SELECT m FROM ManualFlag m WHERE m.accountId = :accountId " +
           "AND m.flaggedAt BETWEEN :startDate AND :endDate " +
           "ORDER BY m.flaggedAt DESC")
    List<ManualFlag> findByAccountIdAndDateRange(
            @Param("accountId") UUID accountId,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate);

    List<ManualFlag> findByAccountId(UUID accountId);

    List<ManualFlag> findByAnalystId(String analystId);

    @Query("SELECT m FROM ManualFlag m WHERE m.status = 'ACTIVE' " +
           "ORDER BY m.flaggedAt DESC")
    List<ManualFlag> findActiveFlags();
}
