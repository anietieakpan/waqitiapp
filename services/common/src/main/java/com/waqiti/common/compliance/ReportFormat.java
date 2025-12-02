package com.waqiti.common.compliance;

/**
 * CRITICAL PRODUCTION FIX - ReportFormat
 * Enumeration of supported compliance report output formats
 */
public enum ReportFormat {
    
    // Standard Document Formats
    PDF("Portable Document Format", "pdf", "application/pdf", true, true),
    HTML("HyperText Markup Language", "html", "text/html", false, true),
    CSV("Comma-Separated Values", "csv", "text/csv", false, false),
    EXCEL("Microsoft Excel", "xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", true, true),
    
    // Regulatory Specific Formats
    XML("Extensible Markup Language", "xml", "application/xml", false, true),
    JSON("JavaScript Object Notation", "json", "application/json", false, true),
    FINCEN_BSA("FinCEN BSA E-Filing Format", "bsa", "application/xml", false, true),
    XBRL("eXtensible Business Reporting Language", "xbrl", "application/xbrl+xml", true, true),
    
    // Plain Text Formats
    TXT("Plain Text", "txt", "text/plain", false, false),
    RTF("Rich Text Format", "rtf", "application/rtf", true, true),
    
    // Archive Formats
    ZIP("ZIP Archive", "zip", "application/zip", true, false),
    
    // Legacy Formats
    DOC("Microsoft Word (Legacy)", "doc", "application/msword", true, true),
    XLS("Microsoft Excel (Legacy)", "xls", "application/vnd.ms-excel", true, true),
    
    // Electronic Formats for Regulatory Filing
    EDI("Electronic Data Interchange", "edi", "application/edi-x12", false, true),
    SWIFT("SWIFT Message Format", "swift", "application/swift", false, true),
    
    // Custom/Proprietary Formats
    CUSTOM("Custom Format", "custom", "application/octet-stream", false, false);
    
    private final String displayName;
    private final String fileExtension;
    private final String mimeType;
    private final boolean supportsPagination;
    private final boolean supportsFormatting;
    
    ReportFormat(String displayName, String fileExtension, String mimeType, 
                boolean supportsPagination, boolean supportsFormatting) {
        this.displayName = displayName;
        this.fileExtension = fileExtension;
        this.mimeType = mimeType;
        this.supportsPagination = supportsPagination;
        this.supportsFormatting = supportsFormatting;
    }
    
    public String getDisplayName() {
        return displayName;
    }
    
    public String getFileExtension() {
        return fileExtension;
    }
    
    public String getMimeType() {
        return mimeType;
    }
    
    public boolean supportsPagination() {
        return supportsPagination;
    }
    
    public boolean supportsFormatting() {
        return supportsFormatting;
    }
    
    /**
     * Get report format by file extension
     */
    public static ReportFormat fromExtension(String extension) {
        if (extension == null) return null;
        
        String ext = extension.toLowerCase().replaceAll("^\\.", "");
        for (ReportFormat format : values()) {
            if (format.fileExtension.equalsIgnoreCase(ext)) {
                return format;
            }
        }
        return null;
    }
    
    /**
     * Get report format by MIME type
     */
    public static ReportFormat fromMimeType(String mimeType) {
        if (mimeType == null) return null;
        
        for (ReportFormat format : values()) {
            if (format.mimeType.equalsIgnoreCase(mimeType)) {
                return format;
            }
        }
        return null;
    }
    
    /**
     * Check if format is suitable for regulatory filing
     */
    public boolean isRegulatoryCompliant() {
        return switch (this) {
            case PDF, XML, FINCEN_BSA, XBRL, EDI, SWIFT -> true;
            default -> false;
        };
    }
    
    /**
     * Check if format supports digital signatures
     */
    public boolean supportsDigitalSignatures() {
        return switch (this) {
            case PDF, XML, XBRL -> true;
            default -> false;
        };
    }
    
    /**
     * Check if format is human-readable
     */
    public boolean isHumanReadable() {
        return switch (this) {
            case PDF, HTML, TXT, RTF, DOC, CSV -> true;
            default -> false;
        };
    }
    
    /**
     * Check if format supports embedded metadata
     */
    public boolean supportsMetadata() {
        return switch (this) {
            case PDF, EXCEL, DOC, XLS, XML, XBRL -> true;
            default -> false;
        };
    }
    
    /**
     * Get recommended format for specific compliance report types
     */
    public static ReportFormat getRecommendedFormat(ComplianceReportType reportType) {
        return switch (reportType) {
            case SAR, CTR, LCTR -> FINCEN_BSA;
            case OFAC_SCREENING, PATRIOT_ACT -> XML;
            case CAPITAL_ADEQUACY, STRESS_TESTING -> XBRL;
            case CYBERSECURITY_INCIDENT -> PDF;
            case AML_COMPLIANCE, KYC_COMPLIANCE, BSA_COMPLIANCE -> PDF;
            case MONITORING_EXCEPTION -> EXCEL;
            default -> PDF;
        };
    }
    
    /**
     * Get file name with proper extension
     */
    public String formatFileName(String baseName) {
        if (baseName == null || baseName.isEmpty()) {
            baseName = "report";
        }
        
        // Remove existing extension if present
        if (baseName.contains(".")) {
            baseName = baseName.substring(0, baseName.lastIndexOf("."));
        }
        
        return baseName + "." + fileExtension;
    }
    
    /**
     * Check if format requires special processing
     */
    public boolean requiresSpecialProcessing() {
        return switch (this) {
            case FINCEN_BSA, XBRL, EDI, SWIFT -> true;
            default -> false;
        };
    }
    
    /**
     * Get maximum file size limit in bytes (0 = no limit)
     */
    public long getMaxFileSizeBytes() {
        return switch (this) {
            case CSV, TXT -> 10 * 1024 * 1024; // 10MB
            case HTML, XML, JSON -> 50 * 1024 * 1024; // 50MB
            case PDF, EXCEL, DOC, XLS -> 100 * 1024 * 1024; // 100MB
            case ZIP -> 500 * 1024 * 1024; // 500MB
            default -> 0; // No limit
        };
    }
}