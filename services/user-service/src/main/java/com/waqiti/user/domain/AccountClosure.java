package com.waqiti.user.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "account_closures")
public class AccountClosure {
    @Id
    private String id;
    private String eventId;
    private String userId;
    private String userEmail;
    
    // Closure details
    private ClosureReason reason;
    private String initiatedBy;
    private String initiationType;
    private String closureNotes;
    private String feedbackProvided;
    
    // Status
    private ClosureStatus status;
    private boolean eligibilityVerified;
    
    // Outstanding items
    private BigDecimal outstandingBalance;
    private boolean balanceResolutionRequired;
    private Integer pendingTransactions;
    private Integer activeDisputes;
    private boolean regulatoryHoldActive;
    
    // Pre-closure checks
    private List<String> activeSubscriptions;
    private boolean subscriptionsCancellationRequired;
    private Integer subscriptionsCancelled;
    private List<String> linkedAccounts;
    private Integer recurringPayments;
    private boolean recurringPaymentsCancellationRequired;
    private Integer recurringPaymentsCancelled;
    private BigDecimal loyaltyPointsBalance;
    private boolean loyaltyPointsForfeited;
    private String preCheckError;
    
    // Balance withdrawal
    private boolean balanceWithdrawn;
    private String withdrawalTransactionId;
    private String withdrawalMethod;
    private LocalDateTime withdrawalProcessedAt;
    private String withdrawalError;
    
    // Access revocation
    private Integer tokensRevoked;
    private Integer apiKeysInvalidated;
    private Integer sessionsTerminated;
    
    // Payment methods
    private Integer paymentMethodsDeleted;
    
    // Partner notifications
    private List<String> partnerServicesNotified;
    
    // Closure certificate
    private String closureCertificateId;
    private String closureCertificateData;
    private LocalDateTime certificateGeneratedAt;
    
    // Data handling
    private DataRetentionPolicy retentionPolicy;
    private LocalDateTime dataRetentionUntil;
    private boolean dataAnonymized;
    private LocalDateTime anonymizedAt;
    private boolean dataArchived;
    private String archiveLocation;
    private Long archiveSize;
    private LocalDateTime archivedAt;
    private String archivalError;
    private String auditSnapshot;
    
    // Processing tracking
    private Integer successfulSteps;
    private List<String> failedSteps;
    private Integer completionPercentage;
    private String finalizationError;
    
    // Timestamps
    private LocalDateTime initiatedAt;
    private LocalDateTime scheduledClosureDate;
    private LocalDateTime finalizedAt;
    private LocalDateTime completedAt;
    private Long processingTimeMs;
    
    // Flags
    private boolean immediateClosure;
    
    // Metadata
    private String correlationId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}