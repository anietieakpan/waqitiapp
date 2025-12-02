package com.waqiti.payment.core.model;

import lombok.*;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Group payment data model for collective payments and contributions
 * Production-ready implementation for group payment scenarios
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString(exclude = {"participants", "metadata"})
public class GroupPaymentData {
    
    @NotNull
    private UUID groupId;
    
    @NotNull
    @NotBlank
    private String groupName;
    
    @NotNull
    private GroupPaymentType type;
    
    @NotNull
    @Size(min = 2, max = 1000)
    private List<GroupParticipant> participants;
    
    @NotNull
    private UUID organizerId;
    
    @NotNull
    private BigDecimal totalAmount;
    
    @NotNull
    private String currency;
    
    private String description;
    
    @Builder.Default
    private CollectionMethod collectionMethod = CollectionMethod.INDIVIDUAL;
    
    @Builder.Default
    private DistributionStrategy distributionStrategy = DistributionStrategy.EQUAL;
    
    private LocalDateTime deadline;
    
    @Builder.Default
    private boolean allowPartialPayments = true;
    
    @Builder.Default
    private boolean autoReminders = true;
    
    private Integer reminderFrequencyDays;
    
    @Builder.Default
    private GroupPaymentStatus status = GroupPaymentStatus.PENDING;
    
    private BigDecimal collectedAmount;
    
    private Map<String, Object> metadata;
    
    @Builder.Default
    private List<GroupPaymentRule> rules = new ArrayList<>();
    
    public enum GroupPaymentType {
        GIFT,              // Group gift collection
        EXPENSE_SPLIT,     // Splitting shared expenses
        CROWDFUNDING,      // Crowdfunding campaign
        EVENT_COLLECTION,  // Event fee collection
        POOL,             // Money pool
        SAVINGS_GROUP,    // Group savings
        INVESTMENT_CLUB,  // Investment club contribution
        CHARITY,          // Charity collection
        CUSTOM            // Custom group payment
    }
    
    public enum CollectionMethod {
        INDIVIDUAL,       // Each pays individually
        BATCH,           // Collect in batches
        MILESTONE,       // Based on milestones
        SCHEDULED,       // Scheduled collection
        ON_DEMAND        // On-demand collection
    }
    
    public enum DistributionStrategy {
        EQUAL,           // Equal distribution
        PROPORTIONAL,    // Based on usage/contribution
        CUSTOM,          // Custom distribution logic
        WEIGHTED,        // Weighted by factors
        VOLUNTARY        // Voluntary contributions
    }
    
    public enum GroupPaymentStatus {
        PENDING,         // Not yet started
        ACTIVE,          // Actively collecting
        PARTIALLY_COLLECTED, // Some payments received
        COMPLETED,       // Fully collected
        EXPIRED,         // Past deadline
        CANCELLED,       // Cancelled by organizer
        DISBURSING,      // Disbursing funds
        DISBURSED,       // Funds disbursed
        REFUNDING,       // Refunding participants
        REFUNDED         // Fully refunded
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GroupParticipant {
        @NotNull
        private UUID participantId;
        
        @NotNull
        private String name;
        
        @Email
        private String email;
        
        private String phone;
        
        @NotNull
        private BigDecimal contributionAmount;
        
        @Builder.Default
        private ParticipantStatus status = ParticipantStatus.PENDING;
        
        private LocalDateTime paidAt;
        
        private String paymentMethod;
        
        private String transactionId;
        
        @Builder.Default
        private boolean isOptional = false;
        
        private BigDecimal actualPaidAmount;
        
        private String notes;
    }
    
    public enum ParticipantStatus {
        PENDING,
        NOTIFIED,
        COMMITTED,
        PAID,
        PARTIALLY_PAID,
        DECLINED,
        EXEMPTED,
        REFUNDED
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GroupPaymentRule {
        private String ruleType;
        private String condition;
        private String action;
        private Map<String, Object> parameters;
        private boolean isActive;
    }
    
    // Business logic methods
    public BigDecimal getRemainingAmount() {
        return totalAmount.subtract(collectedAmount != null ? collectedAmount : BigDecimal.ZERO);
    }
    
    public double getCollectionProgress() {
        if (totalAmount.compareTo(BigDecimal.ZERO) == 0) {
            return 0.0;
        }
        BigDecimal collected = collectedAmount != null ? collectedAmount : BigDecimal.ZERO;
        return collected.divide(totalAmount, 4, RoundingMode.HALF_UP)
            .multiply(new BigDecimal(100)).doubleValue();
    }
    
    public boolean isFullyCollected() {
        return collectedAmount != null && 
               collectedAmount.compareTo(totalAmount) >= 0;
    }
    
    public boolean isExpired() {
        return deadline != null && LocalDateTime.now().isAfter(deadline);
    }
    
    public long getParticipantsPaid() {
        return participants.stream()
            .filter(p -> p.getStatus() == ParticipantStatus.PAID)
            .count();
    }
    
    public BigDecimal getAverageContribution() {
        if (participants.isEmpty()) {
            return BigDecimal.ZERO;
        }
        return totalAmount.divide(
            new BigDecimal(participants.size()), 
            2, 
            RoundingMode.HALF_UP
        );
    }
}