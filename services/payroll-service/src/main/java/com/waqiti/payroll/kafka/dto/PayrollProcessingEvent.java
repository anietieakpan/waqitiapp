package com.waqiti.payroll.kafka.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Kafka Event: Payroll Processing Request
 * Topic: payroll-processing-events
 * Producer: API Gateway, Scheduled Jobs
 * Consumer: PayrollProcessingEventConsumer
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PayrollProcessingEvent {

    // Event metadata
    private String eventId;
    private String eventType; // "PAYROLL_PROCESSING_REQUEST"

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
    private String timestamp;

    private String correlationId;
    private Integer version; // Event schema version
    private boolean retry;
    private Integer retryCount;

    // Batch information
    private String payrollBatchId;
    private String companyId;
    private String companyName;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate payPeriod;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate payDate;

    private String payrollType; // REGULAR, BONUS, COMMISSION, etc.

    // Employees in batch
    private List<EmployeePayrollData> employees;

    // Financial summary
    private BigDecimal totalGrossAmount;
    private BigDecimal estimatedNetAmount;
    private Integer employeeCount;

    // Approval workflow
    private boolean requiresApproval;
    private String submittedBy;

    // Additional metadata
    private Map<String, Object> metadata;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EmployeePayrollData {
        private String employeeId;
        private String employeeName;
        private String employeeEmail;
        private String ssn; // Encrypted

        // Pay information
        private BigDecimal hourlyRate;
        private BigDecimal hoursWorked;
        private BigDecimal basePay;
        private BigDecimal overtimePay;
        private BigDecimal bonusPay;
        private BigDecimal commissionPay;
        private BigDecimal grossAmount;

        // Tax information
        private String filingStatus; // SINGLE, MARRIED_JOINT, etc.
        private Integer exemptions;
        private String state;
        private String city;

        // Bank account
        private String routingNumber; // Encrypted
        private String accountNumber; // Encrypted
        private String accountType; // CHECKING, SAVINGS

        // Deductions
        private BigDecimal health401k;
        private BigDecimal healthInsurance;
        private BigDecimal otherDeductions;

        // Metadata
        private Map<String, Object> metadata;
    }
}
