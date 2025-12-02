package com.waqiti.payroll.kafka.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * Kafka Event: Tax Filing Notification
 * Topic: tax-filing-events
 * Producer: PayrollProcessingEventConsumer, ReportingService
 * Consumer: Tax Filing Service, Compliance Service
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaxFilingEvent {

    // Event metadata
    private String eventId;
    private String eventType; // "TAX_FILING_REQUIRED", "TAX_REPORT_GENERATED"

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
    private LocalDateTime timestamp;

    private String correlationId;

    // Tax filing details
    private String companyId;
    private String companyEIN;
    private String companyName;

    private String taxFormType; // W-2, 1099-NEC, 941, 940
    private Integer taxYear;
    private Integer taxQuarter; // For Form 941

    // Tax amounts
    private BigDecimal totalWages;
    private BigDecimal totalFederalTaxWithheld;
    private BigDecimal totalStateTaxWithheld;
    private BigDecimal totalSocialSecurityTax;
    private BigDecimal totalMedicareTax;
    private BigDecimal totalFUTATax;
    private BigDecimal totalSUITax;

    // Filing information
    private String filingStatus; // PENDING, FILED, ACKNOWLEDGED, REJECTED
    private String filingMethod; // E_FILE, PAPER

    @JsonFormat(pattern = "yyyy-MM-dd")
    private java.time.LocalDate dueDate;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private java.time.LocalDate filedDate;

    // Employee/Contractor count
    private Integer employeeCount;
    private Integer contractorCount;

    // Report location (if generated)
    private String reportUrl;
    private String reportStoragePath;

    // Additional metadata
    private Map<String, Object> metadata;
}
