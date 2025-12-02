package com.waqiti.billingorchestrator.repository;

import com.waqiti.billingorchestrator.domain.LateFee;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Late Fee repository
 * Migrated from billing-service
 */
@Repository
public interface LateFeeRepository extends JpaRepository<LateFee, UUID> {

    List<LateFee> findByBillId(UUID billId);

    List<LateFee> findByUserId(UUID userId);
}
