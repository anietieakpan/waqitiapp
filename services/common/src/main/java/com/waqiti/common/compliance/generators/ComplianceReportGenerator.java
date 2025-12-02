package com.waqiti.common.compliance.generators;

import com.waqiti.common.compliance.dto.ComplianceDTOs.*;
import com.waqiti.common.compliance.model.ComplianceReportType;
import com.waqiti.common.compliance.model.ReportFormat;

/**
 * Interface for compliance report generators
 * Each implementation handles specific report types (SAR, CTR, BSA, etc.)
 */
public interface ComplianceReportGenerator {
    
    /**
     * Check if this generator supports the given report type
     */
    boolean supports(ComplianceReportType reportType);
    
    /**
     * Generate report data from compliance report request
     */
    ComplianceReportData generateReportData(com.waqiti.common.compliance.ComplianceReportRequest request);

    /**
     * Generate compliance report based on the provided data
     */
    ComplianceReportDocument generate(ComplianceReportData data, ComplianceReportRequest request);

    /**
     * Validate report data before generation
     */
    ComplianceValidationResult validate(ComplianceReportData data);
    
    /**
     * Get the report format this generator produces
     */
    ReportFormat getFormat();
    
    /**
     * Get metadata about the report generator
     */
    GeneratorMetadata getMetadata();
    
    /**
     * Inner class for generator metadata
     */
    class GeneratorMetadata {
        private String name;
        private String version;
        private String description;
        private ComplianceReportType[] supportedTypes;
        
        public GeneratorMetadata(String name, String version, String description, ComplianceReportType[] supportedTypes) {
            this.name = name;
            this.version = version;
            this.description = description;
            this.supportedTypes = supportedTypes;
        }
        
        // Getters
        public String getName() { return name; }
        public String getVersion() { return version; }
        public String getDescription() { return description; }
        public ComplianceReportType[] getSupportedTypes() { return supportedTypes; }
    }
}