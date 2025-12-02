package com.waqiti.billpayment.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Domain object representing the result of submitting a payment to a biller
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentSubmissionResult {

    /**
     * Whether the submission was successful
     */
    private boolean success;

    /**
     * Biller's confirmation number
     */
    private String billerConfirmationNumber;

    /**
     * Biller's transaction reference
     */
    private String billerTransactionReference;

    /**
     * Payment status from biller
     */
    private String billerPaymentStatus;

    /**
     * Error message if submission failed
     */
    private String errorMessage;

    /**
     * Error code if submission failed
     */
    private String errorCode;

    /**
     * Amount processed by biller
     */
    private BigDecimal processedAmount;

    /**
     * Processing fee charged by biller
     */
    private BigDecimal billerFee;

    /**
     * Estimated posting date to account
     */
    private LocalDateTime estimatedPostingDate;

    /**
     * Submission timestamp
     */
    private LocalDateTime submissionTimestamp;

    /**
     * Whether the payment requires additional verification
     */
    private Boolean requiresVerification;

    /**
     * Verification instructions if required
     */
    private String verificationInstructions;

    /**
     * Receipt URL from biller
     */
    private String receiptUrl;

    /**
     * Helper method to create a successful submission result
     */
    public static PaymentSubmissionResult success(
            String billerConfirmationNumber,
            String billerTransactionReference,
            BigDecimal processedAmount) {

        return PaymentSubmissionResult.builder()
                .success(true)
                .billerConfirmationNumber(billerConfirmationNumber)
                .billerTransactionReference(billerTransactionReference)
                .billerPaymentStatus("SUBMITTED")
                .processedAmount(processedAmount)
                .submissionTimestamp(LocalDateTime.now())
                .build();
    }

    /**
     * Helper method to create a failed submission result
     */
    public static PaymentSubmissionResult failure(String errorCode, String errorMessage) {
        return PaymentSubmissionResult.builder()
                .success(false)
                .errorCode(errorCode)
                .errorMessage(errorMessage)
                .billerPaymentStatus("FAILED")
                .submissionTimestamp(LocalDateTime.now())
                .build();
    }

    /**
     * Helper method to create a result requiring verification
     */
    public static PaymentSubmissionResult requiresVerification(
            String verificationInstructions,
            String transactionReference) {

        return PaymentSubmissionResult.builder()
                .success(false)
                .requiresVerification(true)
                .verificationInstructions(verificationInstructions)
                .billerTransactionReference(transactionReference)
                .billerPaymentStatus("PENDING_VERIFICATION")
                .submissionTimestamp(LocalDateTime.now())
                .build();
    }
}
