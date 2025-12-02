package com.waqiti.common.events.compliance;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * Event representing an AML (Anti-Money Laundering) screening request.
 * Triggered when entities (customers, transactions) need to be screened
 * against sanctions lists, PEP lists, and adverse media.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AMLScreeningEvent {

    /**
     * Unique identifier for this event
     */
    private String eventId;

    /**
     * Correlation ID for tracing across services
     */
    private String correlationId;

    /**
     * Entity being screened (customer ID, transaction ID, etc.)
     */
    private String entityId;

    /**
     * Type of entity being screened (CUSTOMER, TRANSACTION, MERCHANT, etc.)
     */
    private String entityType;

    /**
     * Name of the entity being screened
     */
    private String entityName;

    /**
     * Entity's date of birth (for individuals)
     */
    private String entityDateOfBirth;

    /**
     * Entity's nationality/country
     */
    private String entityCountry;

    /**
     * Entity's address
     */
    private String entityAddress;

    /**
     * Entity's identification number (passport, SSN, tax ID, etc.)
     */
    private String entityIdentification;

    /**
     * Transaction ID (if screening is transaction-related)
     */
    private String transactionId;

    /**
     * Transaction amount (if applicable)
     */
    private BigDecimal transactionAmount;

    /**
     * Transaction currency (if applicable)
     */
    private String transactionCurrency;

    /**
     * Transaction type (PAYMENT, TRANSFER, WITHDRAWAL, etc.)
     */
    private String transactionType;

    /**
     * Origin country of the transaction
     */
    private String originCountry;

    /**
     * Destination country of the transaction
     */
    private String destinationCountry;

    /**
     * Counterparty name (other party in transaction)
     */
    private String counterpartyName;

    /**
     * Counterparty country
     */
    private String counterpartyCountry;

    /**
     * Reason for screening (ONBOARDING, TRANSACTION, PERIODIC_REVIEW, ALERT, etc.)
     */
    private String screeningReason;

    /**
     * Priority of the screening (URGENT, HIGH, NORMAL, LOW)
     */
    private String priority;

    /**
     * User ID who initiated the screening (for audit purposes)
     */
    private String initiatedBy;

    /**
     * Timestamp when the screening was requested
     */
    private LocalDateTime requestedAt;

    /**
     * Additional metadata for the screening
     */
    private Map<String, Object> metadata;

    /**
     * Source system that generated this event
     */
    private String sourceSystem;

    /**
     * Whether this is a real-time screening (requires immediate response)
     */
    private Boolean realtimeScreening;

    /**
     * Previous screening ID (if this is a re-screening)
     */
    private String previousScreeningId;

    /**
     * Business unit/division
     */
    private String businessUnit;

    /**
     * Customer risk profile ID (if available)
     */
    private String customerRiskProfileId;

    /**
     * Entity identifiers (passport, SSN, tax ID, etc.) as key-value pairs
     */
    private Map<String, String> entityIdentifiers;

    /**
     * Related parties (family members, business associates, etc.)
     */
    private Map<String, String> relatedParties;

    /**
     * Screening keywords for adverse media screening
     */
    private java.util.List<String> screeningKeywords;
}
