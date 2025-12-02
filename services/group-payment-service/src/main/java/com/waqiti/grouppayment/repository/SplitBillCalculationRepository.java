package com.waqiti.grouppayment.repository;

import com.waqiti.grouppayment.domain.SplitBillCalculation;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Repository for Split Bill Calculations
 */
@Repository
public interface SplitBillCalculationRepository extends JpaRepository<SplitBillCalculation, UUID> {

    /**
     * Find all calculations by organizer
     */
    List<SplitBillCalculation> findByOrganizerIdOrderByCreatedAtDesc(String organizerId);

    /**
     * Find calculations by status
     */
    List<SplitBillCalculation> findByStatus(SplitBillCalculation.Status status);

    /**
     * Find calculations by organizer with pagination
     */
    Page<SplitBillCalculation> findByOrganizerId(String organizerId, Pageable pageable);

    /**
     * Find expired calculations
     */
    @Query("SELECT s FROM SplitBillCalculation s WHERE s.expiresAt < :now AND s.status IN ('CALCULATED', 'IN_PROGRESS')")
    List<SplitBillCalculation> findExpiredCalculations(@Param("now") LocalDateTime now);

    /**
     * Count active calculations by organizer
     */
    long countByOrganizerIdAndStatusIn(String organizerId, List<SplitBillCalculation.Status> statuses);
}
