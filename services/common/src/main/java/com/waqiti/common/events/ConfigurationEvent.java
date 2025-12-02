package com.waqiti.common.events;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;
import lombok.extern.jackson.Jacksonized;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Configuration change event for tracking system configuration modifications.
 * Supports configuration auditing, rollback, and distributed configuration synchronization.
 * Integrates with Spring Cloud Config and Vault for configuration management.
 * 
 * @author Waqiti Platform Team
 * @since 1.0.0
 */
@Data
@Builder
@Jacksonized
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = {"eventId"})
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ConfigurationEvent implements DomainEvent {
    
    @Builder.Default
    private String eventId = UUID.randomUUID().toString();
    
    @Builder.Default
    private String eventType = "Configuration.Changed";
    
    @Builder.Default
    private Instant timestamp = Instant.now();
    
    @Builder.Default
    private String topic = "configuration-events";
    
    private String aggregateId; // Configuration key or identifier
    
    @Builder.Default
    private String aggregateType = "Configuration";
    
    private Long version;
    
    private String correlationId;
    
    private String userId;
    
    @Builder.Default
    private String sourceService = System.getProperty("spring.application.name", "unknown");
    
    @Builder.Default
    private Map<String, Object> metadata = new HashMap<>();
    
    // Configuration-specific fields
    private String configurationKey;
    private String configurationNamespace;
    private String environment;
    private String application;
    private String profile;
    
    // Change details
    private Object previousValue;
    private Object currentValue;
    private ChangeType changeType;
    private String changeReason;
    private String approvedBy;
    private Instant approvalTimestamp;
    
    // Configuration metadata
    private ConfigurationScope scope;
    private ConfigurationSource source;
    private boolean encrypted;
    private boolean sensitive;
    private String encryptionKeyId;
    
    // Validation and constraints
    private String validationSchema;
    private Map<String, String> validationErrors;
    private boolean validationPassed;
    
    // Rollback support
    private String previousVersionId;
    private boolean rollbackable;
    private Instant rollbackDeadline;
    private String rollbackRequestId;
    
    // Propagation control
    private PropagationStrategy propagationStrategy;
    private boolean requiresRestart;
    private boolean hotReloadable;
    private Long propagationDelayMs;
    
    // Audit trail
    private String auditId;
    private String changeRequestId;
    private String ticketReference;
    private Map<String, String> tags;
    
    // Impact analysis
    private String[] affectedServices;
    private String[] affectedFeatures;
    private RiskLevel riskLevel;
    private String impactAssessment;
    
    // Compliance
    private boolean complianceRelevant;
    private String[] complianceStandards;
    private String dataClassification;
    
    /**
     * Type of configuration change
     */
    public enum ChangeType {
        CREATE("New configuration created"),
        UPDATE("Configuration value updated"),
        DELETE("Configuration removed"),
        RENAME("Configuration key renamed"),
        ENCRYPT("Configuration encrypted"),
        DECRYPT("Configuration decrypted"),
        ROLLBACK("Configuration rolled back"),
        BULK_UPDATE("Multiple configurations updated"),
        SCHEMA_CHANGE("Configuration schema modified"),
        MIGRATION("Configuration migrated");
        
        private final String description;
        
        ChangeType(String description) {
            this.description = description;
        }
        
        public String getDescription() {
            return description;
        }
    }
    
    /**
     * Scope of configuration applicability
     */
    public enum ConfigurationScope {
        GLOBAL("Applies to entire platform"),
        SERVICE("Service-specific configuration"),
        INSTANCE("Instance-specific configuration"),
        USER("User-specific preference"),
        TENANT("Tenant-specific setting"),
        FEATURE("Feature flag configuration"),
        ENVIRONMENT("Environment-specific"),
        REGIONAL("Region-specific configuration");
        
        private final String description;
        
        ConfigurationScope(String description) {
            this.description = description;
        }
        
        public String getDescription() {
            return description;
        }
    }
    
    /**
     * Source of configuration
     */
    public enum ConfigurationSource {
        SPRING_CLOUD_CONFIG("Spring Cloud Config Server"),
        VAULT("HashiCorp Vault"),
        KUBERNETES_CONFIGMAP("Kubernetes ConfigMap"),
        KUBERNETES_SECRET("Kubernetes Secret"),
        DATABASE("Database configuration"),
        ENVIRONMENT_VARIABLE("Environment variable"),
        COMMAND_LINE("Command line argument"),
        LOCAL_FILE("Local configuration file"),
        REMOTE_FILE("Remote configuration file"),
        API("Configuration API"),
        CONSOLE("Management console"),
        AUTOMATION("Automated system");
        
        private final String description;
        
        ConfigurationSource(String description) {
            this.description = description;
        }
        
        public String getDescription() {
            return description;
        }
    }
    
    /**
     * Strategy for configuration propagation
     */
    public enum PropagationStrategy {
        IMMEDIATE("Apply immediately to all instances"),
        GRADUAL("Rolling update across instances"),
        SCHEDULED("Apply at scheduled time"),
        MANUAL("Requires manual trigger per instance"),
        CANARY("Apply to canary instances first"),
        BLUE_GREEN("Blue-green deployment strategy"),
        FEATURE_FLAG("Controlled by feature flag");
        
        private final String description;
        
        PropagationStrategy(String description) {
            this.description = description;
        }
        
        public String getDescription() {
            return description;
        }
    }
    
    /**
     * Risk level of configuration change
     */
    public enum RiskLevel {
        LOW("Minimal risk, cosmetic changes"),
        MEDIUM("Moderate risk, functional changes"),
        HIGH("High risk, critical path changes"),
        CRITICAL("Critical risk, security or payment impact");
        
        private final String description;
        
        RiskLevel(String description) {
            this.description = description;
        }
        
        public String getDescription() {
            return description;
        }
    }
    
    /**
     * Factory method for configuration creation events
     */
    public static ConfigurationEvent created(String key, Object value, String userId) {
        return ConfigurationEvent.builder()
                .eventType("Configuration.Created")
                .configurationKey(key)
                .aggregateId(key)
                .currentValue(value)
                .changeType(ChangeType.CREATE)
                .userId(userId)
                .timestamp(Instant.now())
                .build();
    }
    
    /**
     * Factory method for configuration update events
     */
    public static ConfigurationEvent updated(String key, Object oldValue, Object newValue, String userId) {
        return ConfigurationEvent.builder()
                .eventType("Configuration.Updated")
                .configurationKey(key)
                .aggregateId(key)
                .previousValue(oldValue)
                .currentValue(newValue)
                .changeType(ChangeType.UPDATE)
                .userId(userId)
                .timestamp(Instant.now())
                .build();
    }
    
    /**
     * Factory method for configuration deletion events
     */
    public static ConfigurationEvent deleted(String key, Object lastValue, String userId) {
        return ConfigurationEvent.builder()
                .eventType("Configuration.Deleted")
                .configurationKey(key)
                .aggregateId(key)
                .previousValue(lastValue)
                .changeType(ChangeType.DELETE)
                .userId(userId)
                .timestamp(Instant.now())
                .build();
    }
    
    /**
     * Factory method for configuration rollback events
     */
    public static ConfigurationEvent rolledBack(String key, Object currentValue, Object previousValue, 
                                                 String rollbackRequestId, String userId) {
        return ConfigurationEvent.builder()
                .eventType("Configuration.RolledBack")
                .configurationKey(key)
                .aggregateId(key)
                .previousValue(currentValue)
                .currentValue(previousValue)
                .changeType(ChangeType.ROLLBACK)
                .rollbackRequestId(rollbackRequestId)
                .userId(userId)
                .timestamp(Instant.now())
                .build();
    }
    
    /**
     * Checks if configuration change requires approval
     */
    public boolean requiresApproval() {
        return riskLevel == RiskLevel.HIGH || 
               riskLevel == RiskLevel.CRITICAL ||
               sensitive ||
               complianceRelevant;
    }
    
    /**
     * Checks if configuration can be auto-applied
     */
    public boolean canAutoApply() {
        return validationPassed &&
               !requiresApproval() &&
               propagationStrategy != PropagationStrategy.MANUAL &&
               riskLevel == RiskLevel.LOW;
    }
    
    /**
     * Gets estimated impact radius
     */
    public int getImpactRadius() {
        if (scope == ConfigurationScope.GLOBAL) return 100;
        if (scope == ConfigurationScope.SERVICE) return 50;
        if (scope == ConfigurationScope.INSTANCE) return 10;
        return 5;
    }
    
    /**
     * Validates configuration event integrity
     */
    @Override
    public boolean isValid() {
        return eventId != null &&
               configurationKey != null &&
               changeType != null &&
               timestamp != null &&
               (changeType == ChangeType.CREATE || previousValue != null || currentValue != null);
    }
    
    /**
     * Determines if this is a high-priority configuration event
     */
    @Override
    public Integer getPriority() {
        if (riskLevel == RiskLevel.CRITICAL) return 10;
        if (riskLevel == RiskLevel.HIGH) return 8;
        if (propagationStrategy == PropagationStrategy.IMMEDIATE) return 7;
        if (requiresRestart) return 6;
        return 5;
    }
    
    /**
     * Configuration events should be persistent for audit trail
     */
    @Override
    public boolean isPersistent() {
        return true;
    }
    
    /**
     * Sensitive configurations require special handling
     */
    @Override
    public boolean containsSensitiveData() {
        return sensitive || encrypted;
    }
    
    /**
     * Configuration events have longer TTL for compliance
     */
    @Override
    public Long getTtlSeconds() {
        return 2592000L; // 30 days for audit trail
    }
    
    @Override
    public String getAggregateName() {
        return "Configuration";
    }
}