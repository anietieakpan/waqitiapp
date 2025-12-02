package com.waqiti.insurance.events.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

/**
 * Base event model for insurance domain events
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InsuranceEvent implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    private String eventId;
    private String eventType;
    private String eventVersion;
    private String policyId;
    private String claimId;
    private String paymentId;
    private String userId;
    private String policyType;
    private String coverageType;
    private String claimType;
    private String claimReason;
    private String submissionMethod;
    private String adjustorId;
    private String decisionReason;
    private String paymentMethod;
    private String cancellationType;
    private String cancellationReason;
    private String initiatedBy;
    private String underwritingResult;
    private String incidentDescription;
    private String billingPeriod;
    private String status;
    private BigDecimal coverageAmount;
    private BigDecimal premiumAmount;
    private BigDecimal claimAmount;
    private BigDecimal approvedAmount;
    private BigDecimal payoutAmount;
    private BigDecimal refundAmount;
    private String currency;
    private Instant effectiveDate;
    private Instant expirationDate;
    private Instant incidentDate;
    private Instant processedDate;
    private Instant dueDate;
    private Instant paidDate;
    private Instant cancellationDate;
    private Instant timestamp;
    private String correlationId;
    private String version;
    private Long sequenceNumber;
    private Integer retryCount;
    private Map<String, Object> metadata;
    
    public void incrementRetryCount() {
        this.retryCount = (this.retryCount == null ? 0 : this.retryCount) + 1;
    }
}