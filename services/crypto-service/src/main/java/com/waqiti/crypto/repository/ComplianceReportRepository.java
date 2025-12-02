package com.waqiti.crypto.repository;

import com.waqiti.crypto.entity.ComplianceReport;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ComplianceReportRepository extends JpaRepository<ComplianceReport, String> {
    List<ComplianceReport> findByTransactionId(String transactionId);
    List<ComplianceReport> findByComplianceType(String complianceType);
    List<ComplianceReport> findByStatus(String status);
}
