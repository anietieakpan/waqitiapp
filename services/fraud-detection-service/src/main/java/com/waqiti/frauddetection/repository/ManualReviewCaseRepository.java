package com.waqiti.frauddetection.repository;

import com.waqiti.frauddetection.entity.ManualReviewCase;
import com.waqiti.frauddetection.service.ManualReviewQueueService.ReviewStatus;
import com.waqiti.frauddetection.service.ManualReviewQueueService.ReviewPriority;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Repository for Manual Review Case operations
 */
@Repository
public interface ManualReviewCaseRepository extends JpaRepository<ManualReviewCase, Long> {

    Optional<ManualReviewCase> findByCaseId(String caseId);

    List<ManualReviewCase> findByStatus(ReviewStatus status);

    long countByStatus(ReviewStatus status);

    long countByStatusAndReviewedAtAfter(ReviewStatus status, LocalDateTime after);

    long countBySlaViolatedTrueAndCreatedAtAfter(LocalDateTime after);

    List<ManualReviewCase> findByStatusAndReviewedAtAfter(ReviewStatus status, LocalDateTime after);

    List<ManualReviewCase> findByReviewedAtAfter(LocalDateTime after);

    List<ManualReviewCase> findByAssignedTo(String analystId);

    List<ManualReviewCase> findByPriorityAndStatus(ReviewPriority priority, ReviewStatus status);

    @Query("SELECT c FROM ManualReviewCase c WHERE c.status IN ('PENDING', 'ASSIGNED', 'IN_PROGRESS') " +
           "AND c.slaDeadline < :deadline ORDER BY c.priority DESC, c.createdAt ASC")
    List<ManualReviewCase> findOverdueCases(@Param("deadline") LocalDateTime deadline);

    @Query("SELECT c FROM ManualReviewCase c WHERE c.assignedTo = :analystId " +
           "AND c.status IN ('ASSIGNED', 'IN_PROGRESS')")
    List<ManualReviewCase> findActiveCasesByAnalyst(@Param("analystId") String analystId);

    @Query("SELECT COUNT(c) FROM ManualReviewCase c WHERE c.assignedTo = :analystId " +
           "AND c.status IN ('ASSIGNED', 'IN_PROGRESS')")
    long countActiveCasesByAnalyst(@Param("analystId") String analystId);
}
