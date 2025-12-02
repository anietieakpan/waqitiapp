package com.waqiti.billingorchestrator.repository;

import com.waqiti.billingorchestrator.domain.Bill;
import com.waqiti.billingorchestrator.domain.BillStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Bill repository
 * Migrated from billing-service
 */
@Repository
public interface BillRepository extends JpaRepository<Bill, UUID> {

    Optional<Bill> findByBillId(UUID billId);

    List<Bill> findByUserIdAndStatus(UUID userId, BillStatus status);

    List<Bill> findByStatus(BillStatus status);

    List<Bill> findByStatusAndDueDateBefore(BillStatus status, LocalDateTime dueDate);

    List<Bill> findByUserId(UUID userId);

    long countByStatus(BillStatus status);
}
