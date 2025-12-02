package com.waqiti.payment.audit.repository;

import com.waqiti.payment.audit.model.PaymentAuditRecord;
import com.waqiti.payment.audit.model.SecurityAuditRecord;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Payment Audit Repository Interface
 * 
 * Repository for persisting and querying audit records.
 * In production, this would be implemented with appropriate data store
 * (e.g., Elasticsearch, MongoDB, PostgreSQL with partitioning).
 * 
 * @version 2.0.0
 * @since 2025-01-18
 */
public interface PaymentAuditRepository {
    
    // Save operations
    void save(PaymentAuditRecord record);
    void saveSecurityRecord(SecurityAuditRecord record);
    
    // Query operations
    List<PaymentAuditRecord> findByPaymentId(String paymentId);
    List<PaymentAuditRecord> findByUserIdAndTimestampBetween(UUID userId, LocalDateTime startTime, LocalDateTime endTime);
    List<PaymentAuditRecord> findByTimestampBetween(LocalDateTime startTime, LocalDateTime endTime);
    List<PaymentAuditRecord> findRecentFailuresByUser(UUID userId, int limit);
    
    // Security queries
    List<SecurityAuditRecord> findSecurityViolationsByUserId(UUID userId, int limit);
    List<SecurityAuditRecord> findSuspiciousEvents(LocalDateTime startTime, LocalDateTime endTime);
    
    // Health check
    boolean isHealthy();
}