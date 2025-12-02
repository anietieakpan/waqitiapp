package com.waqiti.grouppayment.repository;

import com.waqiti.grouppayment.entity.GroupPayment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface GroupPaymentRepository extends JpaRepository<GroupPayment, Long> {

    Optional<GroupPayment> findByGroupPaymentId(String groupPaymentId);

    Page<GroupPayment> findByCreatedBy(String userId, Pageable pageable);

    @Query("SELECT gp FROM GroupPayment gp JOIN gp.participants p WHERE p.userId = :userId")
    Page<GroupPayment> findByParticipantUserId(@Param("userId") String userId, Pageable pageable);

    @Query("SELECT gp FROM GroupPayment gp WHERE gp.createdBy = :userId OR gp.id IN " +
           "(SELECT p.groupPayment.id FROM GroupPaymentParticipant p WHERE p.userId = :userId)")
    Page<GroupPayment> findByUserInvolvement(@Param("userId") String userId, Pageable pageable);

    List<GroupPayment> findByStatusAndDueDateBefore(GroupPayment.GroupPaymentStatus status, Instant date);

    @Query("SELECT gp FROM GroupPayment gp WHERE gp.status = :status AND gp.dueDate BETWEEN :start AND :end")
    List<GroupPayment> findByStatusAndDueDateBetween(
        @Param("status") GroupPayment.GroupPaymentStatus status,
        @Param("start") Instant start,
        @Param("end") Instant end
    );

    @Query("SELECT COUNT(gp) FROM GroupPayment gp WHERE gp.createdBy = :userId AND gp.status = :status")
    long countByCreatedByAndStatus(@Param("userId") String userId, @Param("status") GroupPayment.GroupPaymentStatus status);

    @Query("SELECT gp FROM GroupPayment gp WHERE gp.category = :category AND gp.status = 'ACTIVE'")
    List<GroupPayment> findActiveByCategory(@Param("category") String category);

    @Query("SELECT gp FROM GroupPayment gp WHERE " +
           "(gp.createdBy = :userId OR EXISTS (" +
           "  SELECT 1 FROM GroupPaymentParticipant p WHERE p.groupPayment = gp AND p.userId = :userId" +
           ")) AND gp.status IN ('ACTIVE', 'PARTIALLY_PAID')")
    Page<GroupPayment> findActiveGroupPaymentsByUser(@Param("userId") String userId, Pageable pageable);
}