package com.waqiti.common.events;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * Event published when a wallet freeze is requested
 * 
 * This event triggers mandatory compliance review according to:
 * - Bank Secrecy Act (BSA)
 * - Anti-Money Laundering (AML) regulations
 * - Know Your Customer (KYC) requirements
 * - FinCEN Suspicious Activity Report (SAR) requirements
 * 
 * @author Waqiti Platform
 * @version 1.0
 * @since 2025-09-27
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WalletFreezeRequestedEvent implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    /**
     * Unique event identifier for idempotency
     */
    @JsonProperty("event_id")
    private String eventId;
    
    /**
     * Wallet UUID being frozen
     */
    @JsonProperty("wallet_id")
    private String walletId;
    
    /**
     * User ID of wallet owner
     */
    @JsonProperty("user_id")
    private String userId;
    
    /**
     * Reason for freeze
     * Valid values:
     * - FRAUD_SUSPECTED: Fraudulent activity detected
     * - MONEY_LAUNDERING: AML concerns
     * - TERRORIST_FINANCING: Terrorist financing suspected
     * - SANCTIONS_HIT: OFAC/sanctions list match
     * - COURT_ORDER: Legal hold/court order
     * - REGULATORY_HOLD: Regulatory investigation
     * - SUSPICIOUS_ACTIVITY: General suspicious activity
     * - CUSTOMER_REQUEST: Customer-initiated freeze
     */
    @JsonProperty("reason")
    private String reason;
    
    /**
     * User who requested the freeze (system, admin, compliance officer)
     */
    @JsonProperty("requested_by")
    private String requestedBy;
    
    /**
     * Timestamp when freeze was requested
     */
    @JsonProperty("requested_at")
    private LocalDateTime requestedAt;
    
    /**
     * Additional metadata (transaction IDs, alert IDs, case numbers, etc.)
     */
    @JsonProperty("metadata")
    private Map<String, Object> metadata;
    
    /**
     * Freeze duration in hours (null = indefinite)
     */
    @JsonProperty("freeze_duration_hours")
    private Integer freezeDurationHours;
    
    /**
     * Priority level: CRITICAL, HIGH, MEDIUM, LOW
     */
    @JsonProperty("priority")
    private String priority;
    
    /**
     * Source system that triggered the freeze
     */
    @JsonProperty("source_system")
    private String sourceSystem;
}