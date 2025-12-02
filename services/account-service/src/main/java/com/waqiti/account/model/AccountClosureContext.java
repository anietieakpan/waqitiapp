package com.waqiti.account.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Account Closure Context
 *
 * Holds all contextual information for processing an account closure event
 *
 * @author Waqiti Platform Team
 * @version 2.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AccountClosureContext {

    private String eventId;
    private String closureType;
    private String accountId;
    private String customerId;
    private String closureReason;
    private LocalDateTime requestDate;
    private LocalDateTime scheduledDate;

    // Financial details
    private BigDecimal currentBalance;
    private BigDecimal pendingBalance;
    private BigDecimal finalBalance;
    private BigDecimal accruedInterest;
    private BigDecimal closureFees;
    private BigDecimal netDisbursement;

    // Disbursement info
    private String disbursementMethod;
    private String disbursementAccountId;

    // Status flags
    private boolean hasPendingTransactions;
    private boolean hasLegalHolds;
    private boolean eligible;
    private boolean requiresManualReview;

    // Processing details
    private String processedBy;
    private LocalDateTime processedAt;
    private String requestId;

    // Metadata
    @Builder.Default
    private Map<String, Object> metadata = new HashMap<>();

    // Validation results
    @Builder.Default
    private Map<String, Boolean> validationResults = new HashMap<>();

    // Error tracking
    private String errorCode;
    private String errorMessage;

    public void addMetadata(String key, Object value) {
        this.metadata.put(key, value);
    }

    public void addValidationResult(String check, boolean passed) {
        this.validationResults.put(check, passed);
    }
}
