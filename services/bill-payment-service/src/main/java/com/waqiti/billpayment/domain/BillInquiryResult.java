package com.waqiti.billpayment.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Domain object representing the result of a bill inquiry from a biller
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BillInquiryResult {

    /**
     * Whether the inquiry was successful
     */
    private boolean success;

    /**
     * Error message if inquiry failed
     */
    private String errorMessage;

    /**
     * Error code if inquiry failed
     */
    private String errorCode;

    /**
     * Account name from biller
     */
    private String accountName;

    /**
     * Full bill amount
     */
    private BigDecimal billAmount;

    /**
     * Minimum amount due
     */
    private BigDecimal minimumDue;

    /**
     * Currency code (ISO 4217)
     */
    private String currency;

    /**
     * Bill due date
     */
    private LocalDate dueDate;

    /**
     * Bill issue date
     */
    private LocalDate issueDate;

    /**
     * Billing period description
     */
    private String billPeriod;

    /**
     * Biller's reference number for this bill
     */
    private String billerReferenceNumber;

    /**
     * Additional information from biller
     */
    private String additionalInfo;

    /**
     * Whether partial payments are allowed
     */
    private Boolean canPayPartial;

    /**
     * Whether scheduled payments are supported
     */
    private Boolean canSchedule;

    /**
     * Bill status from biller
     */
    private String billStatus;

    /**
     * Outstanding balance if any
     */
    private BigDecimal outstandingBalance;

    /**
     * Late fee if applicable
     */
    private BigDecimal lateFee;

    /**
     * Service charges
     */
    private BigDecimal serviceCharge;

    /**
     * Timestamp of the inquiry
     */
    private java.time.LocalDateTime inquiryTimestamp;

    /**
     * Helper method to create a successful result
     */
    public static BillInquiryResult success(
            String accountName,
            BigDecimal billAmount,
            BigDecimal minimumDue,
            String currency,
            LocalDate dueDate,
            String billerReferenceNumber) {

        return BillInquiryResult.builder()
                .success(true)
                .accountName(accountName)
                .billAmount(billAmount)
                .minimumDue(minimumDue)
                .currency(currency)
                .dueDate(dueDate)
                .billerReferenceNumber(billerReferenceNumber)
                .inquiryTimestamp(java.time.LocalDateTime.now())
                .build();
    }

    /**
     * Helper method to create a failed result
     */
    public static BillInquiryResult failure(String errorCode, String errorMessage) {
        return BillInquiryResult.builder()
                .success(false)
                .errorCode(errorCode)
                .errorMessage(errorMessage)
                .inquiryTimestamp(java.time.LocalDateTime.now())
                .build();
    }
}
