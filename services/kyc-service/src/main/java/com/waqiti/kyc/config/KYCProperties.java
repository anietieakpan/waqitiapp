package com.waqiti.kyc.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotNull;
import java.util.Map;

@Data
@Validated
@ConfigurationProperties(prefix = "kyc")
public class KYCProperties {

    @NotNull
    private Providers providers = new Providers();
    
    private Storage storage = new Storage();
    
    private Verification verification = new Verification();
    
    private Security security = new Security();
    
    private Compliance compliance = new Compliance();

    @Data
    public static class Providers {
        private Onfido onfido = new Onfido();
        private Jumio jumio = new Jumio();
        private ComplyAdvantage complyAdvantage = new ComplyAdvantage();
        private String defaultProvider = "ONFIDO";
        
        @Data
        public static class Onfido {
            private String apiKey;
            private String apiUrl = "https://api.onfido.com/v3";
            private String webhookSecret;
            private boolean enabled = true;
            private int timeoutSeconds = 30;
        }
        
        @Data
        public static class Jumio {
            private String apiKey;
            private String apiSecret;
            private String apiUrl = "https://netverify.com/api/v4";
            private String webhookSecret;
            private boolean enabled = false;
            private int timeoutSeconds = 30;
        }
        
        @Data
        public static class ComplyAdvantage {
            private String apiKey;
            private String apiUrl = "https://api.complyadvantage.com";
            private boolean enabled = false;
            private int timeoutSeconds = 30;
        }
    }
    
    @Data
    public static class Storage {
        private String type = "S3"; // S3, AZURE, GCS, LOCAL
        private String bucketName = "waqiti-kyc-documents";
        private String region = "us-east-1";
        private String encryptionKey;
        private long maxFileSizeBytes = 10485760; // 10MB
        private String[] allowedFileTypes = {"image/jpeg", "image/png", "application/pdf"};
    }
    
    @Data
    public static class Verification {
        private int maxAttemptsPerUser = 3;
        private int sessionTimeoutMinutes = 30;
        private int documentExpiryDays = 365;
        private int verificationExpiryDays = 365;
        private boolean autoApproveEnabled = false;
        private double autoApproveThreshold = 0.95;
        private Map<String, Integer> levelRequirements = Map.of(
            "BASIC", 1,
            "INTERMEDIATE", 2,
            "ADVANCED", 3
        );
    }
    
    @Data
    public static class Security {
        private boolean encryptDocuments = true;
        private boolean watermarkDocuments = true;
        private String watermarkText = "WAQITI KYC VERIFIED";
        private boolean auditLogEnabled = true;
        private int downloadUrlExpiryMinutes = 15;
        private boolean ipWhitelistEnabled = false;
        private String[] whitelistedIps = {};
    }
    
    @Data
    public static class Compliance {
        private boolean gdprEnabled = true;
        private int dataRetentionDays = 2555; // 7 years
        private boolean rightToErasureEnabled = true;
        private String[] restrictedCountries = {"IR", "KP", "SY"};
        private boolean piiEncryptionEnabled = true;
        private boolean auditTrailEnabled = true;
    }
}