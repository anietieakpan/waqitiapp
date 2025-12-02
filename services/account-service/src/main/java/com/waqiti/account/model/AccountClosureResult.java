package com.waqiti.account.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Account Closure Result
 *
 * Contains the outcome of an account closure processing operation
 *
 * @author Waqiti Platform Team
 * @version 2.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AccountClosureResult {

    private boolean success;
    private String status; // COMPLETED, PENDING, FAILED, DELAYED
    private String accountId;
    private String closureId;

    // Financial summary
    private BigDecimal finalBalance;
    private BigDecimal disbursementAmount;
    private String disbursementMethod;
    private String disbursementId;

    // Processing details
    private LocalDateTime processedAt;
    private LocalDateTime scheduledCompletionDate;
    private long processingTimeMs;

    // Actions taken
    @Builder.Default
    private List<String> actionsCompleted = new ArrayList<>();

    @Builder.Default
    private List<String> actionsPending = new ArrayList<>();

    // Errors and warnings
    @Builder.Default
    private List<String> errors = new ArrayList<>();

    @Builder.Default
    private List<String> warnings = new ArrayList<>();

    // Metadata
    private String eventId;
    private String correlationId;
    private String requestId;

    public void addAction(String action) {
        this.actionsCompleted.add(action);
    }

    public void addPendingAction(String action) {
        this.actionsPending.add(action);
    }

    public void addError(String error) {
        this.errors.add(error);
        this.success = false;
    }

    public void addWarning(String warning) {
        this.warnings.add(warning);
    }
}
