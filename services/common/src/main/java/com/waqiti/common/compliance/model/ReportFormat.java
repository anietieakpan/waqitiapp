package com.waqiti.common.compliance.model;

/**
 * Supported formats for compliance report generation
 */
public enum ReportFormat {
    
    // Document Formats
    PDF("application/pdf", ".pdf", "Portable Document Format", true),
    WORD("application/vnd.openxmlformats-officedocument.wordprocessingml.document", ".docx", "Microsoft Word Document", true),
    
    // Spreadsheet Formats
    EXCEL("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", ".xlsx", "Microsoft Excel Spreadsheet", true),
    CSV("text/csv", ".csv", "Comma-Separated Values", false),
    
    // Data Formats
    JSON("application/json", ".json", "JavaScript Object Notation", false),
    XML("application/xml", ".xml", "Extensible Markup Language", false),
    
    // Regulatory Specific
    FinCEN_BSA("application/xml", ".xml", "FinCEN BSA XML Format", false),
    SWIFT_MT("text/plain", ".txt", "SWIFT MT Message Format", false),
    ISO_20022("application/xml", ".xml", "ISO 20022 XML Format", false),
    
    // Archive Formats
    ZIP("application/zip", ".zip", "ZIP Archive", true),
    TAR_GZ("application/gzip", ".tar.gz", "TAR GZIP Archive", true),
    
    // Encrypted Formats
    PGP_ENCRYPTED("application/pgp-encrypted", ".pgp", "PGP Encrypted File", true),
    PKCS7_ENCRYPTED("application/pkcs7-mime", ".p7m", "PKCS#7 Encrypted", true),
    
    // Web Formats
    HTML("text/html", ".html", "HyperText Markup Language", false),
    
    // Binary Formats
    BINARY("application/octet-stream", ".bin", "Binary Format", true);
    
    private final String mimeType;
    private final String fileExtension;
    private final String description;
    private final boolean supportsBinaryContent;
    
    ReportFormat(String mimeType, String fileExtension, String description, boolean supportsBinaryContent) {
        this.mimeType = mimeType;
        this.fileExtension = fileExtension;
        this.description = description;
        this.supportsBinaryContent = supportsBinaryContent;
    }
    
    public String getMimeType() {
        return mimeType;
    }
    
    public String getFileExtension() {
        return fileExtension;
    }
    
    public String getDescription() {
        return description;
    }
    
    public boolean supportsBinaryContent() {
        return supportsBinaryContent;
    }
    
    /**
     * Get format by file extension
     */
    public static ReportFormat fromExtension(String extension) {
        String ext = extension.toLowerCase();
        if (!ext.startsWith(".")) {
            ext = "." + ext;
        }
        
        for (ReportFormat format : values()) {
            if (format.getFileExtension().equals(ext)) {
                return format;
            }
        }
        throw new IllegalArgumentException("Unsupported file extension: " + extension);
    }
    
    /**
     * Get format by MIME type
     */
    public static ReportFormat fromMimeType(String mimeType) {
        for (ReportFormat format : values()) {
            if (format.getMimeType().equals(mimeType)) {
                return format;
            }
        }
        throw new IllegalArgumentException("Unsupported MIME type: " + mimeType);
    }
    
    /**
     * Check if format is suitable for regulatory submission
     */
    public boolean isRegulatoryCompliant() {
        return this == PDF || this == XML || this == FinCEN_BSA || 
               this == SWIFT_MT || this == ISO_20022;
    }
    
    /**
     * Check if format supports digital signatures
     */
    public boolean supportsDigitalSignatures() {
        return this == PDF || this == XML || this == PKCS7_ENCRYPTED;
    }
    
    /**
     * Get recommended format for report type
     */
    public static ReportFormat getRecommendedFormat(ComplianceReportType reportType) {
        return switch (reportType.getCategory()) {
            case "AML" -> FinCEN_BSA;
            case "GDPR" -> PDF;
            case "PCI_DSS" -> PDF;
            case "SOX" -> EXCEL;
            case "BASEL_III" -> XML;
            default -> PDF;
        };
    }
}