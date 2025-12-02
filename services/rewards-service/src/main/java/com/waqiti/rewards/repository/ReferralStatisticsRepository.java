package com.waqiti.rewards.repository;

import com.waqiti.rewards.domain.ReferralStatistics;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ReferralStatisticsRepository extends JpaRepository<ReferralStatistics, UUID> {

    Optional<ReferralStatistics> findByProgramIdAndPeriodStartAndPeriodEnd(
        String programId, LocalDate periodStart, LocalDate periodEnd
    );

    List<ReferralStatistics> findByProgramId(String programId);

    @Query("SELECT s FROM ReferralStatistics s WHERE s.programId = :programId " +
           "ORDER BY s.periodEnd DESC")
    List<ReferralStatistics> findByProgramIdOrderByPeriodDesc(@Param("programId") String programId);

    @Query("SELECT s FROM ReferralStatistics s WHERE s.programId = :programId " +
           "AND s.periodEnd = (SELECT MAX(s2.periodEnd) FROM ReferralStatistics s2 " +
           "WHERE s2.programId = :programId)")
    Optional<ReferralStatistics> findLatestStatistics(@Param("programId") String programId);

    @Query("SELECT s FROM ReferralStatistics s WHERE s.periodStart >= :startDate " +
           "AND s.periodEnd <= :endDate")
    List<ReferralStatistics> findStatisticsBetween(
        @Param("startDate") LocalDate startDate,
        @Param("endDate") LocalDate endDate
    );

    @Query("SELECT s FROM ReferralStatistics s WHERE s.conversionRate >= :minRate " +
           "ORDER BY s.conversionRate DESC")
    List<ReferralStatistics> findHighPerformingPeriods(@Param("minRate") java.math.BigDecimal minRate);
}
