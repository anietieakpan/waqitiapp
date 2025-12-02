package com.waqiti.common.idempotency;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Duration;

/**
 * Context object for idempotency operations containing all metadata needed
 * for duplicate detection, audit trail, and compliance
 *
 * @author Waqiti Platform Team
 * @version 1.0
 * @since 2025-10-01
 */
@Data
@Builder
public class IdempotencyContext {

    /**
     * Unique idempotency key (required)
     * Format: service:operation:identifier
     * Example: "payment-service:PROCESS_PAYMENT:user123:tx456"
     */
    private String idempotencyKey;

    /**
     * Service name (required)
     * Example: "payment-service", "wallet-service"
     */
    private String serviceName;

    /**
     * Operation type (required)
     * Example: "PROCESS_PAYMENT", "CREATE_WALLET", "TRANSFER_FUNDS"
     */
    private String operationType;

    /**
     * Request payload for duplicate detection
     * Automatically hashed for comparison
     */
    private Object requestPayload;

    /**
     * Time-to-live for idempotency record
     * Default: 24 hours
     */
    private Duration ttl;

    /**
     * Correlation ID for distributed tracing
     * Links related operations across services
     */
    private String correlationId;

    /**
     * User ID (for audit trail)
     */
    private String userId;

    /**
     * Session ID (for security analysis)
     */
    private String sessionId;

    /**
     * Client IP address (for fraud detection)
     */
    private String clientIpAddress;

    /**
     * User agent (for device analysis)
     */
    private String userAgent;

    /**
     * Device fingerprint (for fraud prevention)
     */
    private String deviceFingerprint;

    /**
     * Transaction amount (for financial audit)
     */
    private BigDecimal amount;

    /**
     * Currency code (ISO 4217)
     */
    private String currency;
}
