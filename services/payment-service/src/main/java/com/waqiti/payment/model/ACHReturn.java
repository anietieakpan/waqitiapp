package com.waqiti.payment.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * ACH Return Model
 * Represents an ACH return/reversal transaction
 *
 * @author Waqiti Platform Team
 * @since October 2025
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ACHReturn {

    private String returnId;
    private String originalTransactionId;
    private String returnCode;
    private BigDecimal amount;
    private String accountNumber;
    private String routingNumber;
    private LocalDateTime returnDate;
    private String processingStatus;
    private String correlationId;

    // Additional fields for comprehensive return processing
    private String returnReason;
    private String returnDescription;
    private LocalDateTime receivedDate;
    private LocalDateTime processedDate;
    private String status;
    private String customerNotified;
    private String fraudCheckPassed;
    private String complianceValidated;
    private String reversalTransactionId;
    private BigDecimal feeAmount;
    private String feeTransactionId;
    private String notificationSent;
    private String reconciliationStatus;

}
