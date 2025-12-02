package com.waqiti.billingorchestrator.entity;

import com.waqiti.common.audit.Auditable;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.GenericGenerator;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Billing Event Entity
 * Comprehensive audit trail for all billing cycle events
 *
 * CRITICAL AUDIT ENTITY - Immutable after creation for compliance
 *
 * @author Waqiti Billing Team
 * @version 1.0
 * @since 2025-10-17
 */
@Entity
@Table(name = "billing_events", indexes = {
    @Index(name = "idx_billing_event_cycle", columnList = "billing_cycle_id"),
    @Index(name = "idx_billing_event_type", columnList = "event_type"),
    @Index(name = "idx_billing_event_timestamp", columnList = "event_timestamp"),
    @Index(name = "idx_billing_event_reference", columnList = "reference_type, reference_id")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class BillingEvent extends Auditable {

    @Id
    @GeneratedValue(generator = "UUID")
    @GenericGenerator(name = "UUID", strategy = "org.hibernate.id.UUIDGenerator")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "billing_cycle_id", nullable = false)
    private BillingCycle billingCycle;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false)
    private EventType eventType;

    @Column(name = "event_timestamp", nullable = false)
    private LocalDateTime eventTimestamp;

    @Column(name = "description", nullable = false, columnDefinition = "TEXT")
    private String description;

    @Column(name = "performed_by")
    private String performedBy;

    @Column(name = "system_generated")
    private Boolean systemGenerated = true;

    // Event details
    @Column(name = "old_status")
    private String oldStatus;

    @Column(name = "new_status")
    private String newStatus;

    @Column(name = "reference_id")
    private String referenceId;

    @Column(name = "reference_type")
    private String referenceType;

    @Column(name = "amount")
    private String amount;

    @Column(name = "currency")
    private String currency;

    // Notification details
    @Column(name = "notification_sent")
    private Boolean notificationSent = false;

    @Column(name = "notification_type")
    private String notificationType;

    @Column(name = "notification_channel")
    private String notificationChannel;

    @Column(name = "notification_recipient")
    private String notificationRecipient;

    // Error details
    @Column(name = "error_code")
    private String errorCode;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "retry_count")
    private Integer retryCount = 0;

    @Column(name = "max_retries")
    private Integer maxRetries;

    @Column(name = "next_retry_at")
    private LocalDateTime nextRetryAt;

    // Metadata
    @ElementCollection
    @CollectionTable(name = "billing_event_metadata", joinColumns = @JoinColumn(name = "event_id"))
    @MapKeyColumn(name = "key")
    @Column(name = "value")
    private Map<String, String> metadata = new HashMap<>();

    @Column(name = "ip_address")
    private String ipAddress;

    @Column(name = "user_agent")
    private String userAgent;

    @Column(name = "session_id")
    private String sessionId;

    /**
     * Billing event types - comprehensive coverage
     */
    public enum EventType {
        // Cycle events
        CYCLE_CREATED,
        CYCLE_OPENED,
        CYCLE_CLOSED,
        CYCLE_REOPENED,
        CYCLE_CANCELLED,

        // Invoice events
        INVOICE_GENERATED,
        INVOICE_SENT,
        INVOICE_VIEWED,
        INVOICE_DOWNLOADED,
        INVOICE_REGENERATED,
        INVOICE_VOIDED,

        // Payment events
        PAYMENT_INITIATED,
        PAYMENT_SUCCEEDED,
        PAYMENT_FAILED,
        PAYMENT_RETRY_SCHEDULED,
        PAYMENT_RETRIED,
        PARTIAL_PAYMENT_RECEIVED,
        REFUND_INITIATED,
        REFUND_COMPLETED,
        REFUND_FAILED,

        // Charge events
        CHARGE_ADDED,
        CHARGE_MODIFIED,
        CHARGE_REMOVED,
        ADJUSTMENT_APPLIED,
        CREDIT_APPLIED,
        TAX_CALCULATED,

        // Dunning events
        DUNNING_STARTED,
        DUNNING_REMINDER_SENT,
        DUNNING_LEVEL_INCREASED,
        DUNNING_SUSPENDED,
        DUNNING_RESUMED,
        DUNNING_CLEARED,

        // Dispute events
        DISPUTE_OPENED,
        DISPUTE_UPDATED,
        DISPUTE_RESOLVED,
        DISPUTE_LOST,
        DISPUTE_WON,

        // Notification events
        NOTIFICATION_SENT,
        NOTIFICATION_FAILED,
        NOTIFICATION_BOUNCED,
        NOTIFICATION_DELIVERED,

        // System events
        AUTO_CHARGE_SCHEDULED,
        AUTO_CHARGE_EXECUTED,
        AUTO_CHARGE_FAILED,
        GRACE_PERIOD_STARTED,
        GRACE_PERIOD_ENDED,
        SERVICE_SUSPENDED,
        SERVICE_REACTIVATED,

        // Subscription events
        SUBSCRIPTION_CREATED,
        SUBSCRIPTION_ACTIVATED,
        SUBSCRIPTION_PAUSED,
        SUBSCRIPTION_RESUMED,
        SUBSCRIPTION_CANCELLED,
        SUBSCRIPTION_RENEWED,

        // Other
        MANUAL_INTERVENTION,
        NOTES_ADDED,
        CONFIGURATION_CHANGED,
        ERROR_OCCURRED,
        COMPLIANCE_CHECK_PASSED,
        COMPLIANCE_CHECK_FAILED
    }

    // BUSINESS METHODS

    /**
     * Create a simple cycle event
     */
    public static BillingEvent createCycleEvent(BillingCycle cycle, EventType type, String description) {
        return BillingEvent.builder()
                .billingCycle(cycle)
                .eventType(type)
                .eventTimestamp(LocalDateTime.now())
                .description(description)
                .systemGenerated(true)
                .build();
    }

    /**
     * Create a status change event
     */
    public static BillingEvent createStatusChangeEvent(BillingCycle cycle, String oldStatus, String newStatus, String performedBy) {
        return BillingEvent.builder()
                .billingCycle(cycle)
                .eventType(EventType.CONFIGURATION_CHANGED)
                .eventTimestamp(LocalDateTime.now())
                .description("Status changed from " + oldStatus + " to " + newStatus)
                .oldStatus(oldStatus)
                .newStatus(newStatus)
                .performedBy(performedBy)
                .systemGenerated(false)
                .build();
    }

    /**
     * Create a payment event with amount
     */
    public static BillingEvent createPaymentEvent(BillingCycle cycle, EventType type, String amount, String currency, String description) {
        return BillingEvent.builder()
                .billingCycle(cycle)
                .eventType(type)
                .eventTimestamp(LocalDateTime.now())
                .amount(amount)
                .currency(currency)
                .description(description)
                .systemGenerated(true)
                .build();
    }

    /**
     * Create an error event
     */
    public static BillingEvent createErrorEvent(BillingCycle cycle, EventType type, String errorCode, String errorMessage) {
        return BillingEvent.builder()
                .billingCycle(cycle)
                .eventType(type)
                .eventTimestamp(LocalDateTime.now())
                .description("Error occurred: " + errorCode)
                .errorCode(errorCode)
                .errorMessage(errorMessage)
                .systemGenerated(true)
                .retryCount(0)
                .maxRetries(3)
                .build();
    }

    /**
     * Create a notification event
     */
    public static BillingEvent createNotificationEvent(BillingCycle cycle, String notificationType,
                                                       String channel, String recipient, boolean sent) {
        return BillingEvent.builder()
                .billingCycle(cycle)
                .eventType(sent ? EventType.NOTIFICATION_SENT : EventType.NOTIFICATION_FAILED)
                .eventTimestamp(LocalDateTime.now())
                .description("Notification " + (sent ? "sent" : "failed") + " via " + channel)
                .notificationType(notificationType)
                .notificationChannel(channel)
                .notificationRecipient(recipient)
                .notificationSent(sent)
                .systemGenerated(true)
                .build();
    }

    /**
     * Check if event is retryable
     */
    public boolean isRetryable() {
        return retryCount < (maxRetries != null ? maxRetries : 3) &&
               (eventType == EventType.PAYMENT_FAILED ||
                eventType == EventType.NOTIFICATION_FAILED ||
                eventType == EventType.ERROR_OCCURRED ||
                eventType == EventType.AUTO_CHARGE_FAILED);
    }

    /**
     * Schedule next retry
     */
    public void scheduleRetry() {
        if (isRetryable()) {
            this.retryCount++;
            // Exponential backoff: 5min, 15min, 1hour
            long minutesToAdd = (long) Math.pow(3, retryCount) * 5;
            this.nextRetryAt = LocalDateTime.now().plusMinutes(minutesToAdd);
        }
    }

    /**
     * Mark event as requiring manual intervention
     */
    public boolean requiresManualIntervention() {
        return eventType == EventType.ERROR_OCCURRED ||
               eventType == EventType.DISPUTE_OPENED ||
               eventType == EventType.COMPLIANCE_CHECK_FAILED ||
               (retryCount >= maxRetries && isRetryable());
    }
}
