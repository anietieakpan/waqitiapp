package com.waqiti.kyc.service;

import com.waqiti.kyc.dto.request.ComplianceReportRequest;
import com.waqiti.kyc.dto.response.ComplianceReportResponse;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Service for generating compliance and regulatory reports
 */
public interface ComplianceReportService {
    
    /**
     * Generate a compliance report
     * @param request The report request
     * @return The generated report
     */
    ComplianceReportResponse generateReport(ComplianceReportRequest request);
    
    /**
     * Generate regulatory filing report (e.g., SAR, CTR)
     * @param reportType The type of regulatory report
     * @param startDate Start date for the report
     * @param endDate End date for the report
     * @return The report data
     */
    byte[] generateRegulatoryFiling(String reportType, LocalDate startDate, LocalDate endDate);
    
    /**
     * Generate PEP (Politically Exposed Person) screening report
     * @param organizationId The organization ID
     * @param startDate Start date
     * @param endDate End date
     * @return PEP screening report
     */
    Map<String, Object> generatePEPReport(String organizationId, LocalDate startDate, LocalDate endDate);
    
    /**
     * Generate sanctions screening report
     * @param organizationId The organization ID
     * @param startDate Start date
     * @param endDate End date
     * @return Sanctions screening report
     */
    Map<String, Object> generateSanctionsReport(String organizationId, LocalDate startDate, LocalDate endDate);
    
    /**
     * Generate risk assessment report
     * @param organizationId The organization ID
     * @param period Report period
     * @return Risk assessment report
     */
    Map<String, Object> generateRiskAssessmentReport(String organizationId, String period);
    
    /**
     * Generate audit trail report
     * @param entityType The entity type (USER, TRANSACTION, etc.)
     * @param entityId The entity ID
     * @param startDate Start date
     * @param endDate End date
     * @return Audit trail report
     */
    List<Map<String, Object>> generateAuditTrail(String entityType, String entityId, 
                                                 LocalDate startDate, LocalDate endDate);
    
    /**
     * Generate customer due diligence (CDD) report
     * @param userId The user ID
     * @return CDD report
     */
    Map<String, Object> generateCDDReport(String userId);
    
    /**
     * Generate enhanced due diligence (EDD) report
     * @param userId The user ID
     * @return EDD report
     */
    Map<String, Object> generateEDDReport(String userId);
    
    /**
     * Generate transaction monitoring report
     * @param organizationId The organization ID
     * @param startDate Start date
     * @param endDate End date
     * @return Transaction monitoring report
     */
    Map<String, Object> generateTransactionMonitoringReport(String organizationId, 
                                                           LocalDate startDate, 
                                                           LocalDate endDate);
    
    /**
     * Schedule a recurring compliance report
     * @param request The report request with schedule information
     * @return The scheduled report ID
     */
    String scheduleRecurringReport(ComplianceReportRequest request);
    
    /**
     * Get all scheduled reports for an organization
     * @param organizationId The organization ID
     * @return List of scheduled reports
     */
    List<Map<String, Object>> getScheduledReports(String organizationId);
    
    /**
     * Cancel a scheduled report
     * @param reportId The scheduled report ID
     */
    void cancelScheduledReport(String reportId);
    
    /**
     * Export report in different formats
     * @param reportId The report ID
     * @param format The export format (PDF, EXCEL, CSV)
     * @return The exported report data
     */
    byte[] exportReport(String reportId, String format);
}