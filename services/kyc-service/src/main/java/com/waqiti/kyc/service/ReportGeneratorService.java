package com.waqiti.kyc.service;

import java.util.Map;

/**
 * Service for generating report files in various formats
 */
public interface ReportGeneratorService {
    
    /**
     * Generate a report in the specified format
     * @param data The report data
     * @param format The output format (PDF, EXCEL, CSV)
     * @return The generated report as byte array
     */
    byte[] generateReport(Map<String, Object> data, String format);
    
    /**
     * Generate a regulatory filing document
     * @param data The filing data
     * @param filingType The type of filing (SAR, CTR, etc.)
     * @return The generated filing document
     */
    byte[] generateRegulatoryFiling(Map<String, Object> data, String filingType);
    
    /**
     * Convert a report from one format to another
     * @param reportData The original report data
     * @param fromFormat The original format
     * @param toFormat The target format
     * @return The converted report
     */
    byte[] convertReport(byte[] reportData, String fromFormat, String toFormat);
    
    /**
     * Generate a report template
     * @param reportType The type of report
     * @param format The output format
     * @return The template as byte array
     */
    byte[] generateTemplate(String reportType, String format);
}