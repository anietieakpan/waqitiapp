package com.waqiti.common.compliance.generators;

import com.waqiti.common.compliance.dto.ComplianceDTOs.*;
import com.waqiti.common.compliance.model.ComplianceReportType;
import com.waqiti.common.compliance.model.ReportFormat;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

/**
 * Currency Transaction Report (CTR) generator
 * Implements FinCEN requirements for CTR filing
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class CTRReportGenerator implements ComplianceReportGenerator {
    
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter DATETIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    
    @Override
    public boolean supports(ComplianceReportType reportType) {
        return reportType == ComplianceReportType.CURRENCY_TRANSACTION_REPORT;
    }

    @Override
    public ComplianceReportData generateReportData(com.waqiti.common.compliance.ComplianceReportRequest request) {
        log.info("Generating CTR report data for period: {} to {}", request.getReportPeriodStart(), request.getReportPeriodEnd());

        // For now, return placeholder data - in production this would collect actual cash transactions >= $10,000
        return ComplianceReportData.builder()
            .reportContent("CTR Report Data")
            .generatedAt(LocalDateTime.now())
            .transactionId("CTR-" + System.currentTimeMillis())
            .amount(new BigDecimal("10000.00"))
            .build();
    }

    @Override
    public ComplianceReportDocument generate(ComplianceReportData data, ComplianceReportRequest request) {
        log.info("Generating CTR report for transaction: {}", data.getTransactionId());
        
        try {
            StringBuilder ctrContent = new StringBuilder();
            
            ctrContent.append("CURRENCY TRANSACTION REPORT (CTR)\n");
            ctrContent.append("=".repeat(50)).append("\n\n");
            
            // Part A - Transaction Location
            ctrContent.append("PART A - TRANSACTION LOCATION\n");
            ctrContent.append("-".repeat(30)).append("\n");
            ctrContent.append("Institution Name: Waqiti Financial Services\n");
            ctrContent.append("Institution Type: Money Services Business\n");
            ctrContent.append("EIN: 12-3456789\n");
            ctrContent.append("Address: 123 Financial St, New York, NY 10001\n");
            ctrContent.append("Transaction Location: ").append(data.getTransactionLocation()).append("\n\n");
            
            // Part B - Individual/Entity Conducting Transaction
            ctrContent.append("PART B - INDIVIDUAL/ENTITY CONDUCTING TRANSACTION\n");
            ctrContent.append("-".repeat(48)).append("\n");
            ctrContent.append("Name: ").append(data.getCustomerName()).append("\n");
            ctrContent.append("Address: ").append(data.getCustomerAddress()).append("\n");
            ctrContent.append("Date of Birth: ").append(data.getCustomerDateOfBirth() != null ? 
                data.getCustomerDateOfBirth().format(DATE_FORMAT) : "N/A").append("\n");
            ctrContent.append("SSN/TIN: ").append(maskSensitiveData(data.getCustomerSSN())).append("\n");
            ctrContent.append("Phone: ").append(data.getCustomerPhone()).append("\n");
            ctrContent.append("Occupation: ").append(data.getCustomerOccupation()).append("\n\n");
            
            // Part C - Transaction Information
            ctrContent.append("PART C - TRANSACTION INFORMATION\n");
            ctrContent.append("-".repeat(35)).append("\n");
            ctrContent.append("Date: ").append(data.getTransactionDate().format(DATETIME_FORMAT)).append("\n");
            ctrContent.append("Total Cash In: $").append(String.format("%,.2f", data.getAmount())).append("\n");
            ctrContent.append("Transaction Type: ").append(data.getTransactionType()).append("\n");
            ctrContent.append("Account Number: ").append(maskSensitiveData(data.getAccountNumber())).append("\n");
            ctrContent.append("Method of Payment: Cash\n");
            ctrContent.append("Currency: USD\n\n");
            
            // Part D - Person on Whose Behalf Transaction Conducted
            if (data.getBeneficiaryName() != null) {
                ctrContent.append("PART D - PERSON ON WHOSE BEHALF TRANSACTION CONDUCTED\n");
                ctrContent.append("-".repeat(52)).append("\n");
                ctrContent.append("Beneficiary Name: ").append(data.getBeneficiaryName()).append("\n");
                ctrContent.append("Beneficiary Address: ").append(data.getBeneficiaryAddress()).append("\n");
                ctrContent.append("Relationship: ").append(data.getBeneficiaryRelationship()).append("\n\n");
            }
            
            // Filing Information
            ctrContent.append("FILING INFORMATION\n");
            ctrContent.append("-".repeat(18)).append("\n");
            ctrContent.append("Filed By: ").append(request.getGeneratedBy()).append("\n");
            ctrContent.append("Filing Date: ").append(LocalDateTime.now().format(DATETIME_FORMAT)).append("\n");
            ctrContent.append("Report ID: ").append(generateReportId("CTR")).append("\n");
            
            return ComplianceReportDocument.builder()
                .reportId(generateReportId("CTR"))
                .reportType(com.waqiti.common.compliance.mapper.ComplianceMapper.toDTO(com.waqiti.common.compliance.ComplianceReportType.CTR_FILING))
                .title("Currency Transaction Report")
                .content(ctrContent.toString())
                .format("XML")
                .generatedAt(LocalDateTime.now())
                .generatedBy(request.getGeneratedBy())
                .metadata(Map.of(
                    "transaction_id", data.getTransactionId(),
                    "customer_id", data.getCustomerId(),
                    "amount", data.getAmount(),
                    "filing_requirement", "FinCEN CTR Requirements",
                    "regulatory_deadline", "15 days from transaction",
                    "confidentiality_level", "HIGH"
                ))
                .confidentialityLevel("HIGH")
                .retentionPeriod(1825) // 5 years in days
                .build();
                
        } catch (Exception e) {
            log.error("Error generating CTR report", e);
            throw new RuntimeException("Failed to generate CTR report: " + e.getMessage(), e);
        }
    }
    
    @Override
    public ComplianceValidationResult validate(ComplianceReportData data) {
        return ComplianceValidationResult.builder()
            .valid(data.getAmount().compareTo(new BigDecimal("10000.00")) >= 0) // CTR threshold
            .build();
    }
    
    @Override
    public ReportFormat getFormat() {
        return ReportFormat.FinCEN_BSA;
    }
    
    @Override
    public GeneratorMetadata getMetadata() {
        return new GeneratorMetadata(
            "CTR Report Generator",
            "1.0.0",
            "Generates Currency Transaction Reports per FinCEN requirements",
            new ComplianceReportType[]{ComplianceReportType.CURRENCY_TRANSACTION_REPORT}
        );
    }
    
    private String generateReportId(String prefix) {
        return prefix + "-" + System.currentTimeMillis();
    }
    
    private String maskSensitiveData(String data) {
        if (data == null || data.length() < 4) {
            return "***";
        }
        return "*".repeat(data.length() - 4) + data.substring(data.length() - 4);
    }
}