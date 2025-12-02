package com.waqiti.payroll.kafka.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Kafka Event: Payroll Processing Completed
 * Topic: payroll-processed-events
 * Producer: PayrollProcessingEventConsumer
 * Consumer: Notification Service, Reporting Service, Analytics Service
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PayrollProcessedEvent {

    // Event metadata
    private String eventId;
    private String eventType; // "PAYROLL_PROCESSED"

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
    private LocalDateTime timestamp;

    private String correlationId;

    // Batch information
    private String payrollBatchId;
    private String companyId;
    private String companyName;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate payPeriod;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate payDate;

    private String payrollType;
    private String status; // COMPLETED, PARTIAL_SUCCESS, FAILED

    // Results summary
    private Integer totalEmployees;
    private Integer successfulPayments;
    private Integer failedPayments;

    // Financial summary
    private BigDecimal totalGrossAmount;
    private BigDecimal totalDeductions;
    private BigDecimal totalTaxWithheld;
    private BigDecimal totalNetAmount;

    // Tax breakdown
    private BigDecimal totalFederalTax;
    private BigDecimal totalStateTax;
    private BigDecimal totalSocialSecurityTax;
    private BigDecimal totalMedicareTax;

    // Processing metadata
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
    private LocalDateTime processingStartedAt;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
    private LocalDateTime processingCompletedAt;

    private Long processingTimeMs;

    // Compliance
    private Integer complianceViolations;
    private List<String> complianceViolationTypes;

    // Failed payments (if any)
    private List<FailedPayment> failedPaymentDetails;

    // Settlement information
    private String fundReservationId;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate settlementDate;

    // Additional metadata
    private Map<String, Object> metadata;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FailedPayment {
        private String employeeId;
        private String employeeName;
        private BigDecimal amount;
        private String failureReason;
        private String errorCode;
    }
}
