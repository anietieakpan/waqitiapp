package com.waqiti.audit.config;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.annotation.JsonInclude;

import jakarta.validation.Validator;
import java.time.Duration;
import java.util.concurrent.Executor;
import java.util.Map;
import java.util.HashMap;

/**
 * Industrial-grade audit service configuration supporting high-volume operations
 * and regulatory compliance requirements.
 * 
 * This configuration provides:
 * - Performance optimization for 1M+ events per hour
 * - Comprehensive retention policy management
 * - Security and encryption configuration
 * - Real-time alerting and monitoring setup
 * - Batch processing configuration
 * - Compliance framework integration
 * - High-availability and fault tolerance settings
 */
@Configuration
@EnableConfigurationProperties({
    AuditConfiguration.AuditProperties.class,
    AuditConfiguration.PerformanceProperties.class,
    AuditConfiguration.ComplianceProperties.class,
    AuditConfiguration.SecurityProperties.class,
    AuditConfiguration.AlertingProperties.class
})
@EnableJpaRepositories(basePackages = "com.waqiti.audit.repository")
@EnableTransactionManagement
@EnableCaching
@EnableAsync
@EnableScheduling
@EnableKafka
@EnableRetry
@Slf4j
public class AuditConfiguration {

    /**
     * Primary audit service configuration properties
     */
    @Data
    @ConfigurationProperties(prefix = "waqiti.audit")
    public static class AuditProperties {
        
        /**
         * Enable/disable audit service globally
         */
        private boolean enabled = true;

        /**
         * Service version for compatibility tracking
         */
        private String version = "2.0.0";

        /**
         * Environment profile (dev, staging, prod)
         */
        private String environment = "prod";

        /**
         * Default event processing mode (sync/async)
         */
        private ProcessingMode defaultProcessingMode = ProcessingMode.ASYNC;

        /**
         * Enable/disable real-time processing
         */
        private boolean realTimeProcessing = true;

        /**
         * Enable/disable batch processing
         */
        private boolean batchProcessing = true;

        /**
         * Default batch size for bulk operations
         */
        private int defaultBatchSize = 1000;

        /**
         * Maximum batch size allowed
         */
        private int maxBatchSize = 10000;

        /**
         * Batch processing interval in seconds
         */
        private int batchProcessingIntervalSeconds = 30;

        /**
         * Enable/disable audit event validation
         */
        private boolean validationEnabled = true;

        /**
         * Enable/disable strict validation mode
         */
        private boolean strictValidation = true;

        /**
         * Enable/disable integrity verification
         */
        private boolean integrityVerificationEnabled = true;

        /**
         * Enable/disable chain integrity checks
         */
        private boolean chainIntegrityEnabled = true;

        /**
         * Enable/disable event compression
         */
        private boolean compressionEnabled = true;

        /**
         * Enable/disable event encryption for sensitive data
         */
        private boolean encryptionEnabled = true;

        /**
         * Default data classification for events without explicit classification
         */
        private String defaultDataClassification = "INTERNAL";

        /**
         * Maximum event size in bytes
         */
        private long maxEventSizeBytes = 1048576; // 1MB

        /**
         * Event processing timeout in milliseconds
         */
        private long processingTimeoutMs = 30000; // 30 seconds

        /**
         * Enable/disable metrics collection
         */
        private boolean metricsEnabled = true;

        /**
         * Metrics collection interval in seconds
         */
        private int metricsIntervalSeconds = 60;

        public enum ProcessingMode {
            SYNC, ASYNC, HYBRID
        }
    }

    /**
     * Performance optimization properties
     */
    @Data
    @ConfigurationProperties(prefix = "waqiti.audit.performance")
    public static class PerformanceProperties {
        
        /**
         * Thread pool configuration
         */
        private ThreadPool threadPool = new ThreadPool();

        /**
         * Database connection pool settings
         */
        private DatabasePool databasePool = new DatabasePool();

        /**
         * Cache configuration
         */
        private Cache cache = new Cache();

        /**
         * Indexing strategy configuration
         */
        private Indexing indexing = new Indexing();

        /**
         * Partitioning configuration
         */
        private Partitioning partitioning = new Partitioning();

        @Data
        public static class ThreadPool {
            private int corePoolSize = 20;
            private int maxPoolSize = 100;
            private int queueCapacity = 2000;
            private String threadNamePrefix = "audit-executor-";
            private int keepAliveSeconds = 300;
            private boolean allowCoreThreadTimeOut = true;
        }

        @Data
        public static class DatabasePool {
            private int minimumIdle = 10;
            private int maximumPoolSize = 50;
            private long connectionTimeoutMs = 30000;
            private long idleTimeoutMs = 600000;
            private long maxLifetimeMs = 1800000;
            private int leakDetectionThresholdMs = 60000;
            private String poolName = "AuditHikariPool";
        }

        @Data
        public static class Cache {
            private boolean enabled = true;
            private String cacheNames = "auditEvents,userSessions,complianceData";
            private long timeToLiveMinutes = 60;
            private int maximumSize = 10000;
            private String cacheType = "caffeine"; // caffeine, redis, hazelcast
        }

        @Data
        public static class Indexing {
            private boolean autoCreateIndexes = true;
            private String indexStrategy = "time_series"; // time_series, hash, btree
            private boolean partitionedIndexes = true;
            private int indexMaintenanceIntervalHours = 24;
        }

        @Data
        public static class Partitioning {
            private boolean enabled = true;
            private String strategy = "daily"; // daily, weekly, monthly
            private int retentionDays = 2555; // 7 years default
            private boolean autoPartitionCreation = true;
            private String partitionTablespace = "audit_partitions";
        }
    }

    /**
     * Compliance and regulatory properties
     */
    @Data
    @ConfigurationProperties(prefix = "waqiti.audit.compliance")
    public static class ComplianceProperties {
        
        /**
         * Enabled compliance frameworks
         */
        private Map<String, Boolean> frameworks = new HashMap<String, Boolean>() {{
            put("SOX", true);
            put("GDPR", true);
            put("PCI_DSS", true);
            put("FFIEC", true);
            put("BASEL", true);
            put("CCPA", false);
            put("HIPAA", false);
        }};

        /**
         * Retention policies by compliance framework (in days)
         */
        private Map<String, Integer> retentionPolicies = new HashMap<String, Integer>() {{
            put("SOX", 2555); // 7 years
            put("GDPR", 2190); // 6 years
            put("PCI_DSS", 1095); // 3 years
            put("FFIEC", 2555); // 7 years
            put("BASEL", 2555); // 7 years
            put("DEFAULT", 1825); // 5 years
        }};

        /**
         * Legal hold configuration
         */
        private LegalHold legalHold = new LegalHold();

        /**
         * Regulatory reporting configuration
         */
        private RegulatoryReporting regulatoryReporting = new RegulatoryReporting();

        @Data
        public static class LegalHold {
            private boolean enabled = true;
            private int defaultHoldDurationDays = 2555; // 7 years
            private String holdReasonRequired = "true";
            private String approvalWorkflowEnabled = "true";
        }

        @Data
        public static class RegulatoryReporting {
            private boolean enabled = true;
            private String reportingSchedule = "0 0 2 * * ?"; // Daily at 2 AM
            private String reportFormats = "PDF,CSV,JSON";
            private String deliveryMethods = "EMAIL,SFTP,S3";
            private boolean encryptedReports = true;
        }
    }

    /**
     * Security configuration properties
     */
    @Data
    @ConfigurationProperties(prefix = "waqiti.audit.security")
    public static class SecurityProperties {
        
        /**
         * Encryption configuration
         */
        private Encryption encryption = new Encryption();

        /**
         * Digital signature configuration
         */
        private DigitalSignature digitalSignature = new DigitalSignature();

        /**
         * Access control configuration
         */
        private AccessControl accessControl = new AccessControl();

        @Data
        public static class Encryption {
            private boolean enabled = true;
            private String algorithm = "AES-256-GCM";
            private String keyManagementService = "AWS_KMS"; // AWS_KMS, AZURE_KEY_VAULT, HASHICORP_VAULT
            private String keyRotationSchedule = "0 0 0 * * SUN"; // Weekly on Sunday
            private boolean fieldLevelEncryption = true;
            private String[] encryptedFields = {"beforeState", "afterState", "description", "metadata"};
        }

        @Data
        public static class DigitalSignature {
            private boolean enabled = true;
            private String algorithm = "RSA-PSS";
            private int keySize = 4096;
            private String hashAlgorithm = "SHA-256";
            private boolean timestampRequired = true;
            private String certificateValidityDays = "3650"; // 10 years
        }

        @Data
        public static class AccessControl {
            private boolean roleBasedAccess = true;
            private boolean attributeBasedAccess = true;
            private String[] adminRoles = {"AUDIT_ADMIN", "COMPLIANCE_OFFICER", "SYSTEM_ADMIN"};
            private String[] viewerRoles = {"AUDITOR", "SECURITY_ANALYST", "RISK_MANAGER"};
            private boolean auditLogAccess = true;
            private int sessionTimeoutMinutes = 480; // 8 hours
        }
    }

    /**
     * Alerting and monitoring properties
     */
    @Data
    @ConfigurationProperties(prefix = "waqiti.audit.alerting")
    public static class AlertingProperties {
        
        /**
         * Real-time alerting configuration
         */
        private RealTimeAlerts realTimeAlerts = new RealTimeAlerts();

        /**
         * Anomaly detection configuration
         */
        private AnomalyDetection anomalyDetection = new AnomalyDetection();

        /**
         * Notification channels
         */
        private Notifications notifications = new Notifications();

        @Data
        public static class RealTimeAlerts {
            private boolean enabled = true;
            private int processingDelayThresholdMs = 5000;
            private String[] criticalEventTypes = {"SECURITY_VIOLATION", "FRAUD_DETECTED", "COMPLIANCE_VIOLATION"};
            private Map<String, Integer> severityThresholds = new HashMap<String, Integer>() {{
                put("LOW", 1000); // events per hour
                put("MEDIUM", 500);
                put("HIGH", 100);
                put("CRITICAL", 10);
                put("FRAUD", 1);
            }};
        }

        @Data
        public static class AnomalyDetection {
            private boolean enabled = true;
            private String algorithm = "STATISTICAL"; // STATISTICAL, ML_BASED, RULE_BASED
            private double confidenceThreshold = 0.95;
            private int baselinePeriodDays = 30;
            private String[] monitoredMetrics = {"event_volume", "error_rate", "processing_time", "user_behavior"};
        }

        @Data
        public static class Notifications {
            private String[] channels = {"EMAIL", "SLACK", "PAGERDUTY", "SMS"};
            private Map<String, String> channelConfigurations = new HashMap<>();
            private String[] escalationLevels = {"L1_SUPPORT", "L2_SECURITY", "L3_MANAGEMENT", "L4_EXECUTIVE"};
            private Map<String, Integer> escalationTimeoutsMinutes = new HashMap<String, Integer>() {{
                put("L1_SUPPORT", 15);
                put("L2_SECURITY", 30);
                put("L3_MANAGEMENT", 60);
                put("L4_EXECUTIVE", 120);
            }};
        }
    }

    // Bean Configurations

    /**
     * High-performance thread pool executor for async audit processing
     */
    @Bean(name = "auditTaskExecutor")
    @Primary
    public Executor auditTaskExecutor(AuditProperties auditProperties, 
                                     PerformanceProperties performanceProperties) {
        
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        PerformanceProperties.ThreadPool threadPool = performanceProperties.getThreadPool();
        
        executor.setCorePoolSize(threadPool.getCorePoolSize());
        executor.setMaxPoolSize(threadPool.getMaxPoolSize());
        executor.setQueueCapacity(threadPool.getQueueCapacity());
        executor.setThreadNamePrefix(threadPool.getThreadNamePrefix());
        executor.setKeepAliveSeconds(threadPool.getKeepAliveSeconds());
        executor.setAllowCoreThreadTimeOut(threadPool.isAllowCoreThreadTimeOut());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);
        
        // Rejection policy for high-volume scenarios
        executor.setRejectedExecutionHandler(new java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy());
        
        executor.initialize();
        
        log.info("Initialized audit task executor with core pool size: {}, max pool size: {}, queue capacity: {}",
                threadPool.getCorePoolSize(), threadPool.getMaxPoolSize(), threadPool.getQueueCapacity());
        
        return executor;
    }

    /**
     * Optimized JSON ObjectMapper for audit event serialization
     */
    @Bean(name = "auditObjectMapper")
    public ObjectMapper auditObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        
        // Configure for optimal performance and compatibility
        mapper.registerModule(new JavaTimeModule());
        mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        mapper.configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        mapper.configure(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
        mapper.configure(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DURATIONS_AS_TIMESTAMPS, false);
        
        log.info("Configured audit ObjectMapper for optimal JSON processing");
        
        return mapper;
    }

    /**
     * Comprehensive validation factory for audit events
     */
    @Bean
    public Validator auditValidator() {
        LocalValidatorFactoryBean validatorFactory = new LocalValidatorFactoryBean();
        validatorFactory.afterPropertiesSet();
        
        log.info("Configured audit validator for comprehensive event validation");
        
        return validatorFactory;
    }

    /**
     * Performance monitoring and metrics configuration
     */
    @Bean
    public AuditMetricsConfiguration auditMetricsConfiguration(AuditProperties auditProperties) {
        return new AuditMetricsConfiguration(auditProperties.isMetricsEnabled(), 
                                           auditProperties.getMetricsIntervalSeconds());
    }

    /**
     * Retention policy manager configuration
     */
    @Bean
    public RetentionPolicyManager retentionPolicyManager(ComplianceProperties complianceProperties) {
        return new RetentionPolicyManager(complianceProperties.getRetentionPolicies(),
                                        complianceProperties.getLegalHold());
    }

    /**
     * Security manager configuration
     */
    @Bean
    public AuditSecurityManager auditSecurityManager(SecurityProperties securityProperties) {
        return new AuditSecurityManager(securityProperties.getEncryption(),
                                       securityProperties.getDigitalSignature(),
                                       securityProperties.getAccessControl());
    }

    /**
     * Alert manager configuration
     */
    @Bean
    public AuditAlertManager auditAlertManager(AlertingProperties alertingProperties) {
        return new AuditAlertManager(alertingProperties.getRealTimeAlerts(),
                                   alertingProperties.getAnomalyDetection(),
                                   alertingProperties.getNotifications());
    }

    // Inner classes for configuration support

    /**
     * Audit metrics configuration support
     */
    @Data
    public static class AuditMetricsConfiguration {
        private final boolean enabled;
        private final int intervalSeconds;

        public AuditMetricsConfiguration(boolean enabled, int intervalSeconds) {
            this.enabled = enabled;
            this.intervalSeconds = intervalSeconds;
        }
    }

    /**
     * Retention policy manager
     */
    @Data
    public static class RetentionPolicyManager {
        private final Map<String, Integer> retentionPolicies;
        private final ComplianceProperties.LegalHold legalHold;

        public RetentionPolicyManager(Map<String, Integer> retentionPolicies, 
                                    ComplianceProperties.LegalHold legalHold) {
            this.retentionPolicies = retentionPolicies;
            this.legalHold = legalHold;
        }

        public Duration getRetentionPeriod(String complianceFramework) {
            Integer days = retentionPolicies.getOrDefault(complianceFramework, 
                                                        retentionPolicies.get("DEFAULT"));
            return Duration.ofDays(days);
        }

        public boolean isLegalHoldEnabled() {
            return legalHold.isEnabled();
        }
    }

    /**
     * Audit security manager
     */
    @Data
    public static class AuditSecurityManager {
        private final SecurityProperties.Encryption encryption;
        private final SecurityProperties.DigitalSignature digitalSignature;
        private final SecurityProperties.AccessControl accessControl;

        public AuditSecurityManager(SecurityProperties.Encryption encryption,
                                  SecurityProperties.DigitalSignature digitalSignature,
                                  SecurityProperties.AccessControl accessControl) {
            this.encryption = encryption;
            this.digitalSignature = digitalSignature;
            this.accessControl = accessControl;
        }

        public boolean isEncryptionRequired(String dataClassification) {
            return encryption.isEnabled() && 
                   ("CONFIDENTIAL".equals(dataClassification) || "RESTRICTED".equals(dataClassification));
        }

        public boolean isDigitalSignatureRequired(String eventType, String severity) {
            return digitalSignature.isEnabled() && 
                   ("CRITICAL".equals(severity) || "REGULATORY".equals(severity) || "FRAUD".equals(severity));
        }
    }

    /**
     * Audit alert manager
     */
    @Data
    public static class AuditAlertManager {
        private final AlertingProperties.RealTimeAlerts realTimeAlerts;
        private final AlertingProperties.AnomalyDetection anomalyDetection;
        private final AlertingProperties.Notifications notifications;

        public AuditAlertManager(AlertingProperties.RealTimeAlerts realTimeAlerts,
                               AlertingProperties.AnomalyDetection anomalyDetection,
                               AlertingProperties.Notifications notifications) {
            this.realTimeAlerts = realTimeAlerts;
            this.anomalyDetection = anomalyDetection;
            this.notifications = notifications;
        }

        public boolean shouldTriggerRealTimeAlert(String eventType, String severity) {
            if (!realTimeAlerts.isEnabled()) {
                return false;
            }
            
            // Check if event type is in critical list
            for (String criticalType : realTimeAlerts.getCriticalEventTypes()) {
                if (criticalType.equals(eventType)) {
                    return true;
                }
            }
            
            // Check severity threshold
            return "CRITICAL".equals(severity) || "FRAUD".equals(severity) || "REGULATORY".equals(severity);
        }

        public int getEventThreshold(String severity) {
            return realTimeAlerts.getSeverityThresholds().getOrDefault(severity, 1000);
        }
    }

    // Configuration validation
    @Bean
    public AuditConfigurationValidator auditConfigurationValidator() {
        return new AuditConfigurationValidator();
    }

    /**
     * Configuration validator to ensure proper setup
     */
    public static class AuditConfigurationValidator {
        
        public void validateConfiguration(AuditProperties auditProperties,
                                        PerformanceProperties performanceProperties,
                                        ComplianceProperties complianceProperties,
                                        SecurityProperties securityProperties) {
            
            // Validate performance settings
            if (performanceProperties.getThreadPool().getMaxPoolSize() < 
                performanceProperties.getThreadPool().getCorePoolSize()) {
                throw new IllegalArgumentException("Thread pool max size must be >= core size");
            }
            
            // Validate retention policies
            if (!complianceProperties.getRetentionPolicies().containsKey("DEFAULT")) {
                throw new IllegalArgumentException("DEFAULT retention policy is required");
            }
            
            // Validate security settings
            if (securityProperties.getEncryption().isEnabled() && 
                securityProperties.getEncryption().getKeyManagementService() == null) {
                throw new IllegalArgumentException("Key management service is required when encryption is enabled");
            }
            
            log.info("Audit service configuration validation completed successfully");
        }
    }
}