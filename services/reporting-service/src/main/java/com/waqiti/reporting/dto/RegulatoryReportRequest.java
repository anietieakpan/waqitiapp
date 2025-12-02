package com.waqiti.reporting.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RegulatoryReportRequest {

    @NotNull
    private RegulatoryReportType reportType;

    @NotNull
    private LocalDate fromDate;

    @NotNull
    private LocalDate toDate;

    @NotNull
    private OutputFormat outputFormat;

    @NotNull
    private String requestedBy;

    private Boolean autoSubmit;

    private String submissionAuthority;

    private String comments;

    private Map<String, String> additionalParameters;

    // Regulatory authority specific fields
    private String filingInstitutionCode;
    private String regulatoryContactEmail;
    private String submissionDeadline;
    private Boolean expeditedProcessing;

    public Map<String, String> toParameterMap() {
        Map<String, String> params = new HashMap<>();
        params.put("reportType", reportType.toString());
        params.put("fromDate", fromDate.toString());
        params.put("toDate", toDate.toString());
        params.put("outputFormat", outputFormat.toString());
        params.put("requestedBy", requestedBy);
        params.put("autoSubmit", String.valueOf(autoSubmit != null ? autoSubmit : false));
        
        if (submissionAuthority != null) {
            params.put("submissionAuthority", submissionAuthority);
        }
        if (comments != null) {
            params.put("comments", comments);
        }
        if (filingInstitutionCode != null) {
            params.put("filingInstitutionCode", filingInstitutionCode);
        }
        if (regulatoryContactEmail != null) {
            params.put("regulatoryContactEmail", regulatoryContactEmail);
        }
        if (submissionDeadline != null) {
            params.put("submissionDeadline", submissionDeadline);
        }
        if (expeditedProcessing != null) {
            params.put("expeditedProcessing", String.valueOf(expeditedProcessing));
        }
        
        if (additionalParameters != null) {
            params.putAll(additionalParameters);
        }
        
        return params;
    }

    public enum RegulatoryReportType {
        BSA_CURRENCY_TRANSACTION_REPORT("BSA CTR", "Currency Transaction Report"),
        SUSPICIOUS_ACTIVITY_REPORT("SAR", "Suspicious Activity Report"),
        FFIEC_CALL_REPORT("FFIEC", "Federal Financial Institutions Examination Council Call Report"),
        BANK_SECRECY_ACT_REPORT("BSA", "Bank Secrecy Act Report"),
        ANTI_MONEY_LAUNDERING_REPORT("AML", "Anti-Money Laundering Report"),
        CUSTOMER_DUE_DILIGENCE_REPORT("CDD", "Customer Due Diligence Report"),
        OFAC_SANCTIONS_REPORT("OFAC", "Office of Foreign Assets Control Sanctions Report"),
        PEP_SCREENING_REPORT("PEP", "Politically Exposed Person Screening Report"),
        TRANSACTION_MONITORING_REPORT("TMR", "Transaction Monitoring Report"),
        REGULATORY_CAPITAL_REPORT("RCR", "Regulatory Capital Report");

        private final String code;
        private final String description;

        RegulatoryReportType(String code, String description) {
            this.code = code;
            this.description = description;
        }

        public String getCode() {
            return code;
        }

        public String getDescription() {
            return description;
        }
    }

    public enum OutputFormat {
        PDF("Portable Document Format"),
        EXCEL("Microsoft Excel Spreadsheet"),
        CSV("Comma Separated Values"),
        XML("Extensible Markup Language"),
        JSON("JavaScript Object Notation"),
        XBRL("eXtensible Business Reporting Language"),
        EDI("Electronic Data Interchange");

        private final String description;

        OutputFormat(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }
    }
}