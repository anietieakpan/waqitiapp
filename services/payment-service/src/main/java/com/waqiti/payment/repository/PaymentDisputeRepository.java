package com.waqiti.payment.repository;

import com.waqiti.payment.domain.PaymentDispute;
import com.waqiti.payment.domain.DisputeStatus;
import com.waqiti.payment.domain.DisputeReason;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.data.mongodb.repository.Aggregation;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * Repository for payment dispute operations.
 * Provides comprehensive querying capabilities for dispute management.
 */
@Repository
public interface PaymentDisputeRepository extends MongoRepository<PaymentDispute, String> {

    // Basic lookups
    boolean existsByPaymentIdAndEventId(String paymentId, String eventId);
    
    Optional<PaymentDispute> findByPaymentId(String paymentId);
    
    List<PaymentDispute> findByPaymentIdIn(List<String> paymentIds);

    // Status-based queries
    List<PaymentDispute> findByStatus(DisputeStatus status);
    
    List<PaymentDispute> findByStatusIn(List<DisputeStatus> statuses);
    
    @Query("{ 'status': { $in: ['INITIATED', 'UNDER_REVIEW', 'INVESTIGATION'] } }")
    List<PaymentDispute> findActiveDisputes();

    // Date-based queries
    List<PaymentDispute> findByInitiatedAtBetween(LocalDateTime start, LocalDateTime end);
    
    @Query("{ 'dueDate': { $lt: ?0 }, 'status': { $nin: ['RESOLVED', 'REJECTED', 'WITHDRAWN', 'EXPIRED', 'CANCELLED'] } }")
    List<PaymentDispute> findOverdueDisputes(LocalDateTime currentTime);

    // User-based queries
    List<PaymentDispute> findByCustomerId(String customerId);
    
    List<PaymentDispute> findByMerchantId(String merchantId);
    
    List<PaymentDispute> findByInitiatedBy(String userId);
    
    Page<PaymentDispute> findByCustomerIdOrderByInitiatedAtDesc(String customerId, Pageable pageable);

    // Priority and risk-based queries
    List<PaymentDispute> findByPriority(String priority);
    
    @Query("{ 'fraudRiskScore': { $gte: ?0 } }")
    List<PaymentDispute> findByFraudRiskScoreGreaterThanEqual(Double riskThreshold);
    
    @Query("{ 'requiresManualReview': true }")
    List<PaymentDispute> findRequiringManualReview();

    // Amount-based queries
    @Query("{ 'disputeAmount': { $gte: ?0 } }")
    List<PaymentDispute> findByDisputeAmountGreaterThanEqual(BigDecimal amount);
    
    @Query("{ 'disputeAmount': { $gte: ?0, $lte: ?1 } }")
    List<PaymentDispute> findByDisputeAmountBetween(BigDecimal minAmount, BigDecimal maxAmount);

    // Reason-based queries
    List<PaymentDispute> findByDisputeReason(DisputeReason reason);
    
    List<PaymentDispute> findByDisputeReasonIn(List<DisputeReason> reasons);
    
    @Query("{ 'disputeReason': { $in: ['FRAUD', 'UNAUTHORIZED', 'CARD_NOT_PRESENT'] } }")
    List<PaymentDispute> findFraudRelatedDisputes();

    // Assignment queries
    List<PaymentDispute> findByAssignedToUserId(String userId);
    
    @Query("{ 'assignedToUserId': { $exists: false } }")
    List<PaymentDispute> findUnassignedDisputes();

    // Complex business queries
    @Query("{ 'status': 'UNDER_REVIEW', 'priority': 'HIGH', 'dueDate': { $lt: ?0 } }")
    List<PaymentDispute> findHighPriorityOverdueDisputes(LocalDateTime currentTime);

    @Query("{ 'fraudRiskScore': { $gte: 0.8 }, 'status': { $nin: ['RESOLVED', 'REJECTED'] } }")
    List<PaymentDispute> findHighRiskActiveDisputes();

    @Query("{ 'merchantId': ?0, 'status': { $nin: ['RESOLVED', 'REJECTED', 'WITHDRAWN'] }, 'initiatedAt': { $gte: ?1 } }")
    List<PaymentDispute> findActiveMerchantDisputesSince(String merchantId, LocalDateTime since);

    // Aggregation queries
    @Aggregation(pipeline = {
        "{ $match: { 'initiatedAt': { $gte: ?0, $lte: ?1 } } }",
        "{ $group: { _id: '$disputeReason', count: { $sum: 1 }, totalAmount: { $sum: '$disputeAmount' } } }",
        "{ $sort: { count: -1 } }"
    })
    List<DisputeReasonSummary> getDisputeReasonSummary(LocalDateTime start, LocalDateTime end);

    @Aggregation(pipeline = {
        "{ $match: { 'merchantId': ?0, 'initiatedAt': { $gte: ?1 } } }",
        "{ $group: { _id: '$status', count: { $sum: 1 } } }"
    })
    List<DisputeStatusSummary> getMerchantDisputeStatusSummary(String merchantId, LocalDateTime since);

    @Query(value = "{ 'status': { $nin: ['RESOLVED', 'REJECTED', 'WITHDRAWN', 'EXPIRED', 'CANCELLED'] } }", 
           count = true)
    long countActiveDisputes();

    @Query(value = "{ 'merchantId': ?0, 'status': { $nin: ['RESOLVED', 'REJECTED', 'WITHDRAWN'] } }", 
           count = true)
    long countActiveMerchantDisputes(String merchantId);

    @Query(value = "{ 'customerId': ?0, 'status': { $nin: ['RESOLVED', 'REJECTED', 'WITHDRAWN'] } }", 
           count = true)
    long countActiveCustomerDisputes(String customerId);

    // Recent disputes for monitoring
    @Query("{ 'initiatedAt': { $gte: ?0 } }")
    List<PaymentDispute> findRecentDisputes(LocalDateTime since);

    @Query("{ 'status': 'ESCALATED_TO_CHARGEBACK', 'updatedAt': { $gte: ?0 } }")
    List<PaymentDispute> findRecentChargebacks(LocalDateTime since);

    // Compliance and audit queries
    @Query("{ 'complianceMetadata.requiresRegulatorReporting': true }")
    List<PaymentDispute> findRequiringRegulatoryReporting();

    @Query("{ 'regulatoryFlags': { $exists: true, $not: { $size: 0 } } }")
    List<PaymentDispute> findWithRegulatoryFlags();

    // Projection interfaces for aggregation results
    interface DisputeReasonSummary {
        DisputeReason getId();
        Long getCount();
        BigDecimal getTotalAmount();
    }

    interface DisputeStatusSummary {
        DisputeStatus getId();
        Long getCount();
    }

    // Custom methods for business logic
    default List<PaymentDispute> findEscalationCandidates() {
        LocalDateTime threeDaysAgo = LocalDateTime.now().minusDays(3);
        return findByStatusAndInitiatedAtBefore(DisputeStatus.UNDER_REVIEW, threeDaysAgo);
    }

    List<PaymentDispute> findByStatusAndInitiatedAtBefore(DisputeStatus status, LocalDateTime before);

    default List<PaymentDispute> findStalledDisputes() {
        LocalDateTime oneWeekAgo = LocalDateTime.now().minusWeeks(1);
        return findByStatusAndUpdatedAtBefore(DisputeStatus.AWAITING_EVIDENCE, oneWeekAgo);
    }

    List<PaymentDispute> findByStatusAndUpdatedAtBefore(DisputeStatus status, LocalDateTime before);
}