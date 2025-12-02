package com.waqiti.crypto.repository;

import com.waqiti.crypto.entity.SuspiciousActivityReport;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SarRepository extends JpaRepository<SuspiciousActivityReport, String> {
    List<SuspiciousActivityReport> findByCustomerId(String customerId);
    List<SuspiciousActivityReport> findByStatus(String status);
    List<SuspiciousActivityReport> findByTransactionId(String transactionId);
    List<SuspiciousActivityReport> findByCorrelationId(String correlationId);
}
