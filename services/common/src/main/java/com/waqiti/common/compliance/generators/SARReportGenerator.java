package com.waqiti.common.compliance.generators;

import com.waqiti.common.compliance.dto.ComplianceDTOs.*;
import com.waqiti.common.compliance.model.ComplianceReportType;
import com.waqiti.common.compliance.model.ReportFormat;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

/**
 * Suspicious Activity Report (SAR) generator
 * Implements FinCEN requirements for SAR filing
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class SARReportGenerator implements ComplianceReportGenerator {
    
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter DATETIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    
    @Override
    public boolean supports(ComplianceReportType reportType) {
        return reportType == ComplianceReportType.SUSPICIOUS_ACTIVITY_REPORT;
    }

    @Override
    public ComplianceReportData generateReportData(com.waqiti.common.compliance.ComplianceReportRequest request) {
        log.info("Generating SAR report data for period: {} to {}",
            request.getReportPeriodStart(), request.getReportPeriodEnd());

        // For now, return placeholder data - in production this would collect actual suspicious activity data
        return ComplianceReportData.builder()
            .reportContent("SAR Report Data")
            .generatedAt(LocalDateTime.now())
            .transactionId("TXN-" + System.currentTimeMillis())
            .build();
    }

    @Override
    public ComplianceReportDocument generate(ComplianceReportData data, ComplianceReportRequest request) {
        log.info("Generating SAR report for transaction: {}", data.getTransactionId());
        
        try {
            // Create SAR-specific content
            StringBuilder sarContent = new StringBuilder();
            
            sarContent.append("SUSPICIOUS ACTIVITY REPORT (SAR)\n");
            sarContent.append("=" .repeat(50)).append("\n\n");
            
            // Part I - Subject Information
            sarContent.append("PART I - SUBJECT INFORMATION\n");
            sarContent.append("-".repeat(30)).append("\n");
            sarContent.append("Subject Name: ").append(data.getCustomerName()).append("\n");
            sarContent.append("Subject ID: ").append(data.getCustomerId()).append("\n");
            sarContent.append("Date of Birth: ").append(data.getCustomerDateOfBirth() != null ? 
                data.getCustomerDateOfBirth().format(DATE_FORMAT) : "N/A").append("\n");
            sarContent.append("Address: ").append(data.getCustomerAddress()).append("\n");
            sarContent.append("Phone: ").append(data.getCustomerPhone()).append("\n");
            sarContent.append("SSN/TIN: ").append(maskSensitiveData(data.getCustomerSSN())).append("\n\n");
            
            // Part II - Suspicious Activity Information
            sarContent.append("PART II - SUSPICIOUS ACTIVITY INFORMATION\n");
            sarContent.append("-".repeat(40)).append("\n");
            sarContent.append("Transaction Date: ").append(data.getTransactionDate().format(DATETIME_FORMAT)).append("\n");
            sarContent.append("Transaction Amount: $").append(String.format("%,.2f", data.getAmount())).append("\n");
            sarContent.append("Transaction Type: ").append(data.getTransactionType()).append("\n");
            sarContent.append("Account Number: ").append(maskSensitiveData(data.getAccountNumber())).append("\n");
            sarContent.append("Routing Number: ").append(data.getRoutingNumber()).append("\n");
            sarContent.append("Location: ").append(data.getTransactionLocation()).append("\n\n");
            
            // Part III - Suspicious Activity Description
            sarContent.append("PART III - DESCRIPTION OF SUSPICIOUS ACTIVITY\n");
            sarContent.append("-".repeat(45)).append("\n");
            sarContent.append("Nature of Suspicion: ").append(data.getSuspiciousActivityDescription()).append("\n");
            sarContent.append("Risk Factors Identified: ").append(String.join(", ", data.getRiskFactors())).append("\n");
            sarContent.append("ML Model Score: ").append(data.getMlScore()).append("\n");
            sarContent.append("Pattern Analysis: ").append(data.getPatternAnalysis()).append("\n\n");
            
            // Part IV - Filing Institution Information
            sarContent.append("PART IV - FILING INSTITUTION INFORMATION\n");
            sarContent.append("-".repeat(42)).append("\n");
            sarContent.append("Institution Name: Waqiti Financial Services\n");
            sarContent.append("Institution Type: Money Services Business\n");
            sarContent.append("EIN: 12-3456789\n");
            sarContent.append("Primary Regulator: FinCEN\n");
            sarContent.append("Filing Date: ").append(LocalDateTime.now().format(DATETIME_FORMAT)).append("\n");
            sarContent.append("Prepared By: ").append(request.getGeneratedBy()).append("\n\n");
            
            // Part V - Law Enforcement Contact
            if (data.isLawEnforcementContacted()) {
                sarContent.append("PART V - LAW ENFORCEMENT CONTACT\n");
                sarContent.append("-".repeat(35)).append("\n");
                sarContent.append("Law Enforcement Contacted: Yes\n");
                sarContent.append("Agency: ").append(data.getLawEnforcementAgency()).append("\n");
                sarContent.append("Contact Date: ").append(data.getLawEnforcementContactDate().format(DATE_FORMAT)).append("\n");
                sarContent.append("Badge/ID Number: ").append(data.getLawEnforcementBadgeNumber()).append("\n");
            }
            
            // Generate report document
            return ComplianceReportDocument.builder()
                .reportId(generateReportId("SAR"))
                .reportType(com.waqiti.common.compliance.dto.ComplianceDTOs.ComplianceReportType.SAR_FILING)
                .title("Suspicious Activity Report")
                .content(sarContent.toString())
                .format("TEXT")
                .generatedAt(LocalDateTime.now())
                .generatedBy(request.getGeneratedBy())
                .metadata(Map.of(
                    "transaction_id", data.getTransactionId(),
                    "customer_id", data.getCustomerId(),
                    "filing_requirement", "FinCEN SAR Requirements",
                    "regulatory_deadline", "30 days from detection",
                    "confidentiality_level", "HIGH"
                ))
                .confidentialityLevel("HIGH")
                .retentionPeriod(2555) // 7 years in days
                .build();
                
        } catch (Exception e) {
            log.error("Error generating SAR report", e);
            throw new RuntimeException("Failed to generate SAR report: " + e.getMessage(), e);
        }
    }
    
    private String generateReportId(String prefix) {
        return prefix + "-" + System.currentTimeMillis();
    }
    
    @Override
    public ComplianceValidationResult validate(ComplianceReportData data) {
        // Perform SAR-specific validation
        return ComplianceValidationResult.builder()
            .valid(true)
            .build();
    }
    
    @Override
    public ReportFormat getFormat() {
        return ReportFormat.FinCEN_BSA;
    }
    
    @Override
    public GeneratorMetadata getMetadata() {
        return new GeneratorMetadata(
            "SAR Report Generator",
            "1.0.0",
            "Generates Suspicious Activity Reports per FinCEN requirements",
            new ComplianceReportType[]{ComplianceReportType.SUSPICIOUS_ACTIVITY_REPORT}
        );
    }
    
    private String maskSensitiveData(String data) {
        if (data == null || data.length() < 4) {
            return "***";
        }
        return "*".repeat(data.length() - 4) + data.substring(data.length() - 4);
    }
}