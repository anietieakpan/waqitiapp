package com.waqiti.payment.entity;

import com.waqiti.common.entity.BaseEntity;
import lombok.*;
import org.hibernate.envers.Audited;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;

/**
 * Entity representing an NFC contact relationship between users for peer-to-peer payments
 */
@Entity
@Table(name = "nfc_contacts", indexes = {
    @Index(name = "idx_user_id", columnList = "user_id"),
    @Index(name = "idx_contact_user_id", columnList = "contact_user_id"),
    @Index(name = "idx_user_contact", columnList = "user_id, contact_user_id"),
    @Index(name = "idx_status", columnList = "status"),
    @Index(name = "idx_last_interaction", columnList = "last_interaction_at")
}, uniqueConstraints = {
    @UniqueConstraint(name = "uk_user_contact", columnNames = {"user_id", "contact_user_id"})
})
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Audited
public class NFCContact extends BaseEntity {

    @Column(name = "user_id", nullable = false, length = 255)
    private String userId;
    
    @Column(name = "contact_user_id", nullable = false, length = 255)
    private String contactUserId;
    
    @Column(name = "display_name", length = 255)
    private String displayName;
    
    @Column(name = "nickname", length = 100)
    private String nickname;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 50)
    private NFCContactStatus status;
    
    @Column(name = "added_via_nfc")
    @Builder.Default
    private Boolean addedViaNFC = false;
    
    @Column(name = "is_favorite")
    @Builder.Default
    private Boolean isFavorite = false;
    
    // Interaction tracking
    @Column(name = "first_interaction_at")
    private Instant firstInteractionAt;
    
    @Column(name = "last_interaction_at")
    private Instant lastInteractionAt;
    
    @Column(name = "interaction_count")
    @Builder.Default
    private Long interactionCount = 0L;
    
    // Transaction history
    @Column(name = "transaction_count")
    @Builder.Default
    private Long transactionCount = 0L;
    
    @Column(name = "total_transaction_amount", precision = 19, scale = 4)
    @Builder.Default
    private BigDecimal totalTransactionAmount = BigDecimal.ZERO;
    
    @Column(name = "last_transaction_amount", precision = 19, scale = 4)
    private BigDecimal lastTransactionAmount;
    
    @Column(name = "last_transaction_type", length = 100)
    private String lastTransactionType;
    
    @Column(name = "last_transaction_at")
    private Instant lastTransactionAt;
    
    // Trust and security
    @Column(name = "trust_score")
    @Builder.Default
    private Double trustScore = 0.5;
    
    @Column(name = "trust_score_updated_at")
    private Instant trustScoreUpdatedAt;
    
    @Column(name = "is_verified")
    @Builder.Default
    private Boolean isVerified = false;
    
    @Column(name = "verified_at")
    private Instant verifiedAt;
    
    @Column(name = "verification_method", length = 100)
    private String verificationMethod;
    
    // Location context (for NFC exchanges)
    @Column(name = "added_at_latitude")
    private Double addedAtLatitude;
    
    @Column(name = "added_at_longitude")
    private Double addedAtLongitude;
    
    @Column(name = "added_at_location", length = 255)
    private String addedAtLocation;
    
    @Column(name = "last_known_latitude")
    private Double lastKnownLatitude;
    
    @Column(name = "last_known_longitude")
    private Double lastKnownLongitude;
    
    @Column(name = "last_known_location", length = 255)
    private String lastKnownLocation;
    
    @Column(name = "location_updated_at")
    private Instant locationUpdatedAt;
    
    // Communication preferences
    @Column(name = "notify_on_payment")
    @Builder.Default
    private Boolean notifyOnPayment = true;
    
    @Column(name = "allow_payment_requests")
    @Builder.Default
    private Boolean allowPaymentRequests = true;
    
    @Column(name = "auto_accept_small_amounts")
    @Builder.Default
    private Boolean autoAcceptSmallAmounts = false;
    
    @Column(name = "auto_accept_threshold", precision = 19, scale = 4)
    private BigDecimal autoAcceptThreshold;
    
    // Social features
    @Column(name = "shared_memo", columnDefinition = "TEXT")
    private String sharedMemo;
    
    @Column(name = "contact_notes", columnDefinition = "TEXT")
    private String contactNotes;
    
    @Column(name = "tags", length = 500)
    private String tags;
    
    // NFC specific data
    @Column(name = "nfc_exchange_id", length = 255)
    private String nfcExchangeId;
    
    @Column(name = "nfc_protocol_version", length = 50)
    private String nfcProtocolVersion;
    
    @Column(name = "exchange_device_info", columnDefinition = "TEXT")
    private String exchangeDeviceInfo;
    
    // Relationship metadata
    @Column(name = "relationship_type", length = 100)
    private String relationshipType;
    
    @Column(name = "mutual_connection")
    @Builder.Default
    private Boolean mutualConnection = false;
    
    @Column(name = "blocked_at")
    private Instant blockedAt;
    
    @Column(name = "blocked_reason", length = 500)
    private String blockedReason;
    
    @Column(name = "metadata", columnDefinition = "TEXT")
    private String metadata;
    
    /**
     * Check if contact is active
     */
    public boolean isActive() {
        return status == NFCContactStatus.ACTIVE;
    }
    
    /**
     * Check if contact is blocked
     */
    public boolean isBlocked() {
        return status == NFCContactStatus.BLOCKED;
    }
    
    /**
     * Update interaction details
     */
    public void updateInteraction(BigDecimal transactionAmount, String transactionType) {
        this.lastInteractionAt = Instant.now();
        this.interactionCount++;
        
        if (transactionAmount != null) {
            this.lastTransactionAmount = transactionAmount;
            this.lastTransactionType = transactionType;
            this.lastTransactionAt = Instant.now();
            this.transactionCount++;
            
            if (this.totalTransactionAmount == null) {
                this.totalTransactionAmount = BigDecimal.ZERO;
            }
            this.totalTransactionAmount = this.totalTransactionAmount.add(transactionAmount);
        }
        
        if (this.firstInteractionAt == null) {
            this.firstInteractionAt = Instant.now();
        }
    }
    
    /**
     * Block this contact
     */
    public void block(String reason) {
        this.status = NFCContactStatus.BLOCKED;
        this.blockedAt = Instant.now();
        this.blockedReason = reason;
    }
    
    /**
     * Unblock this contact
     */
    public void unblock() {
        this.status = NFCContactStatus.ACTIVE;
        this.blockedAt = null;
        this.blockedReason = null;
    }
    
    /**
     * Check if amounts should be auto-accepted
     */
    public boolean shouldAutoAccept(BigDecimal amount) {
        return autoAcceptSmallAmounts && 
               autoAcceptThreshold != null && 
               amount.compareTo(autoAcceptThreshold) <= 0;
    }
}