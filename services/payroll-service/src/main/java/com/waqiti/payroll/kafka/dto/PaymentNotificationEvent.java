package com.waqiti.payroll.kafka.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * Kafka Event: Employee Payment Notification
 * Topic: payment-notification-events
 * Producer: PayrollProcessingEventConsumer
 * Consumer: Notification Service (sends email/SMS/push to employees)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentNotificationEvent {

    // Event metadata
    private String eventId;
    private String eventType; // "PAYMENT_PROCESSED", "PAYMENT_FAILED"

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
    private LocalDateTime timestamp;

    private String correlationId;

    // Employee information
    private String employeeId;
    private String employeeName;
    private String employeeEmail;
    private String employeePhoneNumber;

    // Company information
    private String companyId;
    private String companyName;

    // Payment details
    private String payrollBatchId;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate payPeriod;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate payDate;

    // Financial breakdown
    private BigDecimal grossAmount;
    private BigDecimal totalDeductions;
    private BigDecimal totalTaxWithheld;
    private BigDecimal netAmount;

    // Tax breakdown
    private BigDecimal federalTax;
    private BigDecimal stateTax;
    private BigDecimal socialSecurityTax;
    private BigDecimal medicareTax;

    // Deduction breakdown
    private BigDecimal health401k;
    private BigDecimal healthInsurance;
    private BigDecimal otherDeductions;

    // Bank transfer details
    private String paymentMethod; // ACH, WIRE, CHECK
    private String accountLast4; // Last 4 digits of account number
    private String transactionId;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate settlementDate;

    private String paymentStatus; // SUBMITTED, PENDING, SETTLED, FAILED

    // Notification preferences
    private boolean sendEmail;
    private boolean sendSMS;
    private boolean sendPush;

    // Failure details (if applicable)
    private String failureReason;
    private String errorCode;

    // Additional metadata
    private Map<String, Object> metadata;
}
