package com.waqiti.audit.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.math.BigDecimal;
import java.util.List;

@Data
@Configuration
@ConfigurationProperties(prefix = "audit")
public class AuditProperties {
    
    private RetentionProperties retention = new RetentionProperties();
    private IntegrityProperties integrity = new IntegrityProperties();
    private ComplianceProperties compliance = new ComplianceProperties();
    private ArchiveProperties archive = new ArchiveProperties();
    private ExportProperties export = new ExportProperties();
    private SearchProperties search = new SearchProperties();
    
    @Data
    public static class RetentionProperties {
        private Integer defaultDays = 2555;
        private Integer archiveAfterDays = 365;
        private Boolean deleteAfterArchive = false;
    }
    
    @Data
    public static class IntegrityProperties {
        private String hashAlgorithm = "SHA-256";
        private Boolean chainVerification = true;
        private Boolean periodicVerification = true;
        private String verificationInterval = "24h";
    }
    
    @Data
    public static class ComplianceProperties {
        private Boolean autoGenerateReports = true;
        private String reportSchedule = "0 0 1 * * ?";
        private BigDecimal sarThresholdAmount = new BigDecimal("10000");
        private List<String> suspiciousPatterns;
    }
    
    @Data
    public static class ArchiveProperties {
        private String storageType = "s3";
        private String bucketName;
        private String compression = "gzip";
        private String encryption = "aes-256";
    }
    
    @Data
    public static class ExportProperties {
        private Integer maxRecords = 50000;
        private String supportedFormats = "csv,json,pdf";
    }
    
    @Data
    public static class SearchProperties {
        private Boolean indexEnabled = true;
        private String indexRefreshInterval = "5s";
    }
}