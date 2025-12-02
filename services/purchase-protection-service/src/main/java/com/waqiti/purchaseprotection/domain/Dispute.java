package com.waqiti.purchaseprotection.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

/**
 * Dispute entity representing a transaction dispute between buyer and seller.
 *
 * @author Waqiti Platform Team
 * @since 1.0
 */
@Getter
@Setter
@Entity
@Table(name = "disputes", indexes = {
    @Index(name = "idx_disputes_transaction", columnList = "transaction_id"),
    @Index(name = "idx_disputes_buyer", columnList = "buyer_id"),
    @Index(name = "idx_disputes_seller", columnList = "seller_id"),
    @Index(name = "idx_disputes_status", columnList = "status"),
    @Index(name = "idx_disputes_deadline", columnList = "deadline_at")
})
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Dispute {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;
    
    @Column(name = "transaction_id", nullable = false)
    private String transactionId;
    
    @Column(name = "buyer_id", nullable = false)
    private String buyerId;
    
    @Column(name = "seller_id", nullable = false)
    private String sellerId;
    
    @Column(name = "amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;
    
    @Column(name = "reason", nullable = false)
    private String reason;
    
    @Column(name = "description", length = 2000)
    private String description;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private DisputeStatus status;
    
    @Column(name = "initiated_by", nullable = false)
    private String initiatedBy;
    
    @Column(name = "initiated_at", nullable = false)
    private Instant initiatedAt;
    
    @Column(name = "deadline_at", nullable = false)
    private Instant deadlineAt;
    
    @Column(name = "has_protection")
    private boolean hasProtection;
    
    @Column(name = "protection_policy_id")
    private String protectionPolicyId;
    
    @ElementCollection
    @CollectionTable(name = "buyer_evidence", joinColumns = @JoinColumn(name = "dispute_id"))
    @Column(name = "evidence_url")
    private List<String> evidenceFromBuyer;
    
    @ElementCollection
    @CollectionTable(name = "seller_evidence", joinColumns = @JoinColumn(name = "dispute_id"))
    @Column(name = "evidence_url")
    private List<String> evidenceFromSeller;
    
    @Column(name = "seller_response", length = 2000)
    private String sellerResponse;
    
    @Column(name = "seller_responded_at")
    private Instant sellerRespondedAt;
    
    @Column(name = "funds_held")
    private boolean fundsHeld;
    
    @Column(name = "mediator_id")
    private String mediatorId;
    
    @Column(name = "mediation_started_at")
    private Instant mediationStartedAt;
    
    @Embedded
    private DisputeResolution resolution;
    
    @Column(name = "resolved_at")
    private Instant resolvedAt;
    
    @OneToMany(mappedBy = "dispute", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<DisputeMessage> messages;
    
    @OneToMany(mappedBy = "dispute", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<DisputeEvent> events;
    
    @ElementCollection
    @CollectionTable(name = "dispute_metadata", joinColumns = @JoinColumn(name = "dispute_id"))
    @MapKeyColumn(name = "key")
    @Column(name = "value")
    @Builder.Default
    private Map<String, String> metadata = new HashMap<>();
    
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    
    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;
    
    @Version
    @Column(name = "version")
    private Long version;
    
    /**
     * Check if dispute is still open.
     */
    public boolean isOpen() {
        return status == DisputeStatus.OPEN || 
               status == DisputeStatus.SELLER_RESPONDED ||
               status == DisputeStatus.UNDER_MEDIATION;
    }
    
    /**
     * Check if dispute has expired.
     */
    public boolean hasExpired() {
        return Instant.now().isAfter(deadlineAt) && 
               sellerResponse == null;
    }
}

/**
 * Dispute status enumeration.
 */
enum DisputeStatus {
    OPEN,
    SELLER_RESPONDED,
    UNDER_MEDIATION,
    RESOLVED,
    CANCELLED,
    EXPIRED
}

/**
 * Dispute resolution embedded entity.
 */
@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
class DisputeResolution {
    
    @Column(name = "dispute_id")
    private String disputeId;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "decision")
    private DisputeDecision decision;
    
    @Column(name = "refund_amount", precision = 19, scale = 4)
    private BigDecimal refundAmount;
    
    @Column(name = "seller_payout", precision = 19, scale = 4)
    private BigDecimal sellerPayout;
    
    @Column(name = "resolution_reason", length = 1000)
    private String reason;
    
    @Column(name = "resolved_by")
    private String resolvedBy;
    
    @Column(name = "resolved_at")
    private Instant resolvedAt;
    
    @Column(name = "auto_resolved")
    private boolean autoResolved;
}

/**
 * Dispute decision enumeration.
 */
enum DisputeDecision {
    FAVOR_BUYER,
    FAVOR_SELLER,
    PARTIAL_REFUND,
    MUTUAL_AGREEMENT,
    NO_ACTION
}

/**
 * Dispute message entity for communication tracking.
 */
@Getter
@Setter
@Entity
@Table(name = "dispute_messages", indexes = {
    @Index(name = "idx_messages_dispute", columnList = "dispute_id"),
    @Index(name = "idx_messages_sender", columnList = "sender_id")
})
@Builder
@NoArgsConstructor
@AllArgsConstructor
class DisputeMessage {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "dispute_id", nullable = false)
    private Dispute dispute;
    
    @Column(name = "sender_id", nullable = false)
    private String senderId;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "sender_type", nullable = false)
    private SenderType senderType;
    
    @Column(name = "message", nullable = false, length = 2000)
    private String message;
    
    @ElementCollection
    @CollectionTable(name = "message_attachments", joinColumns = @JoinColumn(name = "message_id"))
    @Column(name = "attachment_url")
    private List<String> attachments;
    
    @Column(name = "is_system_message")
    private boolean systemMessage;
    
    @CreationTimestamp
    @Column(name = "sent_at", nullable = false)
    private Instant sentAt;
}

/**
 * Dispute event entity for audit trail.
 */
@Getter
@Setter
@Entity
@Table(name = "dispute_events", indexes = {
    @Index(name = "idx_events_dispute", columnList = "dispute_id"),
    @Index(name = "idx_events_timestamp", columnList = "timestamp")
})
@Builder
@NoArgsConstructor
@AllArgsConstructor
class DisputeEvent {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "dispute_id", nullable = false)
    private Dispute dispute;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false)
    private DisputeEventType eventType;
    
    @Column(name = "description", nullable = false)
    private String description;
    
    @Column(name = "performed_by")
    private String performedBy;
    
    @Column(name = "old_status")
    private String oldStatus;
    
    @Column(name = "new_status")
    private String newStatus;
    
    @ElementCollection
    @CollectionTable(name = "event_details", joinColumns = @JoinColumn(name = "event_id"))
    @MapKeyColumn(name = "key")
    @Column(name = "value")
    private Map<String, String> details;
    
    @Column(name = "timestamp", nullable = false)
    private Instant timestamp;
}

/**
 * Sender type enumeration.
 */
enum SenderType {
    BUYER,
    SELLER,
    MEDIATOR,
    SYSTEM
}

/**
 * Dispute event type enumeration.
 */
enum DisputeEventType {
    CREATED,
    SELLER_NOTIFIED,
    SELLER_RESPONDED,
    EVIDENCE_SUBMITTED,
    MEDIATION_STARTED,
    MEDIATOR_ASSIGNED,
    FUNDS_HELD,
    FUNDS_RELEASED,
    RESOLVED,
    CANCELLED,
    EXPIRED
}