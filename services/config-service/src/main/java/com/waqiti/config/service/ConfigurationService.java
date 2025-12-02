package com.waqiti.config.service;

import com.waqiti.config.client.NotificationServiceClient;
import com.waqiti.config.domain.*;
import com.waqiti.config.dto.*;
import com.waqiti.config.repository.*;
import com.waqiti.common.events.EventPublisher;
import com.waqiti.common.events.ConfigurationEvent;
import com.waqiti.common.security.SecurityContext;
import com.waqiti.common.exception.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cloud.bus.event.RefreshRemoteApplicationEvent;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.vault.core.VaultTemplate;
import org.springframework.core.env.Environment;
import com.fasterxml.jackson.databind.ObjectMapper;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.stats.CacheStats;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Configuration Service - Manages centralized configuration, feature flags, and dynamic properties
 */
@Slf4j
@Service
@RefreshScope
@RequiredArgsConstructor
public class ConfigurationService {

    private final ConfigurationRepository configRepository;
    private final FeatureFlagRepository featureFlagRepository;
    private final ConfigAuditRepository auditRepository;
    private final ServiceConfigRepository serviceConfigRepository;
    private final VaultTemplate vaultTemplate;
    private final ConfigEncryptionService encryptionService;
    private final NotificationServiceClient notificationServiceClient;
    private final EventPublisher eventPublisher;
    private final ApplicationEventPublisher applicationEventPublisher;
    private final SecurityContext securityContext;
    private final ObjectMapper objectMapper;
    private final Environment environment;

    @Value("${config.cache.ttl:300}")
    private int cacheTtlSeconds;

    @Value("${config.cache.max-size:10000}")
    private long cacheMaxSize;

    @Value("${config.encryption.enabled:true}")
    private boolean encryptionEnabled;

    @Value("${config.validation.strict:true}")
    private boolean strictValidation;

    // High-performance local cache with size limits and TTL (Caffeine)
    // Prevents OutOfMemoryError with bounded size and automatic eviction
    private final Cache<String, ConfigValue> configCache = Caffeine.newBuilder()
            .maximumSize(10000) // Maximum 10,000 cached configs
            .expireAfterWrite(5, TimeUnit.MINUTES) // TTL: 5 minutes
            .recordStats() // Enable statistics for monitoring
            .build();

    /**
     * Get configuration value by key
     */
    @Cacheable(value = "configurations", key = "#key")
    public ConfigValue getConfig(String key) {
        return getConfig(key, null);
    }

    /**
     * Get configuration value by key with default
     */
    @Cacheable(value = "configurations", key = "#key + ':' + #defaultValue")
    public ConfigValue getConfig(String key, String defaultValue) {
        try {
            // Check cache first
            ConfigValue cached = configCache.get(key);
            if (cached != null && !cached.isExpired()) {
                return cached;
            }
            
            // Load from database
            Configuration config = configRepository.findByKeyAndActiveTrue(key)
                .orElse(null);
            
            if (config == null) {
                if (defaultValue != null) {
                    return ConfigValue.of(key, defaultValue, ConfigType.STRING);
                }
                throw new ConfigurationNotFoundException("Configuration not found: " + key);
            }
            
            // Decrypt if necessary
            String value = config.getValue();
            if (config.isEncrypted() && encryptionEnabled) {
                value = encryptionService.decrypt(value);
            }
            
            ConfigValue configValue = ConfigValue.builder()
                .key(config.getKey())
                .value(value)
                .type(config.getType())
                .description(config.getDescription())
                .metadata(config.getMetadata())
                .lastModified(config.getLastModified())
                .expiresAt(Instant.now().plusSeconds(cacheTtlSeconds))
                .build();
            
            // Update cache
            configCache.put(key, configValue);
            
            return configValue;
            
        } catch (Exception e) {
            log.error("Failed to get configuration for key: {}", key, e);
            if (defaultValue != null) {
                return ConfigValue.of(key, defaultValue, ConfigType.STRING);
            }
            throw new ConfigurationException("Failed to retrieve configuration", e);
        }
    }

    /**
     * Get typed configuration value
     */
    @SuppressWarnings("unchecked")
    public <T> T getConfigValue(String key, Class<T> type, T defaultValue) {
        ConfigValue config = getConfig(key, defaultValue != null ? defaultValue.toString() : null);
        
        try {
            String value = config.getValue();
            
            if (type == String.class) {
                return (T) value;
            } else if (type == Integer.class) {
                return (T) Integer.valueOf(value);
            } else if (type == Long.class) {
                return (T) Long.valueOf(value);
            } else if (type == Double.class) {
                return (T) Double.valueOf(value);
            } else if (type == Boolean.class) {
                return (T) Boolean.valueOf(value);
            } else if (type == List.class) {
                return (T) Arrays.asList(value.split(","));
            } else {
                // Use JSON deserialization for complex types
                return objectMapper.readValue(value, type);
            }
        } catch (Exception e) {
            log.warn("Failed to convert config value for key: {} to type: {}", key, type.getName());
            return defaultValue;
        }
    }

    /**
     * Update configuration value
     */
    @Transactional
    @CacheEvict(value = "configurations", key = "#key")
    public ConfigValue updateConfig(String key, UpdateConfigRequest request) {
        String userId = securityContext.getCurrentUserId();
        
        Configuration config = configRepository.findByKey(key)
            .orElseThrow(() -> new ConfigurationNotFoundException("Configuration not found: " + key));
        
        // Validate update
        validateConfigUpdate(config, request);
        
        // Store old value for audit
        String oldValue = config.getValue();
        boolean wasEncrypted = config.isEncrypted();
        
        // Update configuration
        config.setValue(request.getValue());
        config.setType(request.getType() != null ? request.getType() : config.getType());
        config.setDescription(request.getDescription() != null ? 
            request.getDescription() : config.getDescription());
        config.setMetadata(request.getMetadata() != null ? 
            request.getMetadata() : config.getMetadata());
        config.setLastModified(Instant.now());
        config.setLastModifiedBy(userId);
        
        // Encrypt if sensitive
        if (request.isSensitive() || config.isSensitive()) {
            config.setValue(encryptionService.encrypt(request.getValue()));
            config.setEncrypted(true);
        }
        
        config = configRepository.save(config);
        
        // Create audit entry
        createAuditEntry(config, oldValue, wasEncrypted, userId, "UPDATE");
        
        // Clear cache
        configCache.remove(key);
        
        // Notify services of configuration change
        notifyConfigurationChange(config);
        
        // Publish event
        eventPublisher.publish(ConfigurationEvent.configUpdated(config, oldValue));
        
        log.info("Updated configuration {} by user {}", key, userId);
        
        return getConfig(key);
    }

    /**
     * Create new configuration
     */
    @Transactional
    public ConfigValue createConfig(CreateConfigRequest request) {
        String userId = securityContext.getCurrentUserId();
        
        // Check if already exists
        if (configRepository.existsByKey(request.getKey())) {
            throw new DuplicateConfigurationException("Configuration already exists: " + request.getKey());
        }
        
        // Validate configuration
        validateNewConfig(request);
        
        Configuration config = Configuration.builder()
            .key(request.getKey())
            .value(request.getValue())
            .type(request.getType())
            .description(request.getDescription())
            .service(request.getService())
            .environment(request.getEnvironment())
            .sensitive(request.isSensitive())
            .encrypted(false)
            .active(true)
            .metadata(request.getMetadata())
            .createdAt(Instant.now())
            .createdBy(userId)
            .lastModified(Instant.now())
            .lastModifiedBy(userId)
            .build();
        
        // Encrypt if sensitive
        if (request.isSensitive() && encryptionEnabled) {
            config.setValue(encryptionService.encrypt(request.getValue()));
            config.setEncrypted(true);
        }
        
        config = configRepository.save(config);
        
        // Create audit entry
        createAuditEntry(config, null, false, userId, "CREATE");
        
        // Publish event
        eventPublisher.publish(ConfigurationEvent.configCreated(config));
        
        log.info("Created configuration {} by user {}", request.getKey(), userId);
        
        return getConfig(request.getKey());
    }

    /**
     * Delete configuration
     */
    @Transactional
    @CacheEvict(value = "configurations", key = "#key")
    public void deleteConfig(String key) {
        String userId = securityContext.getCurrentUserId();
        
        Configuration config = configRepository.findByKey(key)
            .orElseThrow(() -> new ConfigurationNotFoundException("Configuration not found: " + key));
        
        // Soft delete
        config.setActive(false);
        config.setDeletedAt(Instant.now());
        config.setDeletedBy(userId);
        configRepository.save(config);
        
        // Create audit entry
        createAuditEntry(config, config.getValue(), config.isEncrypted(), userId, "DELETE");
        
        // Clear cache
        configCache.remove(key);
        
        // Notify services
        notifyConfigurationChange(config);
        
        // Publish event
        eventPublisher.publish(ConfigurationEvent.configDeleted(config));
        
        log.info("Deleted configuration {} by user {}", key, userId);
    }

    /**
     * Get feature flag
     */
    @Cacheable(value = "feature-flags", key = "#flagName")
    public boolean isFeatureEnabled(String flagName) {
        return isFeatureEnabled(flagName, null, new HashMap<>());
    }

    /**
     * Get feature flag with context
     */
    @Cacheable(value = "feature-flags", key = "#flagName + ':' + #userId")
    public boolean isFeatureEnabled(String flagName, String userId, Map<String, Object> context) {
        try {
            FeatureFlag flag = featureFlagRepository.findByNameAndActiveTrue(flagName)
                .orElse(null);
            
            if (flag == null) {
                return false;
            }
            
            // Check if globally enabled
            if (!flag.isEnabled()) {
                return false;
            }
            
            // Check environment
            if (flag.getEnvironments() != null && !flag.getEnvironments().isEmpty()) {
                String currentEnv = getCurrentEnvironment();
                if (!flag.getEnvironments().contains(currentEnv)) {
                    return false;
                }
            }
            
            // Check user targeting
            if (userId != null && flag.getTargetUsers() != null && !flag.getTargetUsers().isEmpty()) {
                if (flag.getTargetUsers().contains(userId)) {
                    return true;
                }
            }
            
            // Check percentage rollout
            if (flag.getPercentageRollout() != null && flag.getPercentageRollout() > 0) {
                if (userId != null) {
                    int hash = Math.abs(userId.hashCode());
                    return (hash % 100) < flag.getPercentageRollout();
                }
            }
            
            // Check custom rules
            if (flag.getRules() != null && !flag.getRules().isEmpty()) {
                return evaluateFeatureFlagRules(flag.getRules(), context);
            }
            
            return flag.isEnabled();
            
        } catch (Exception e) {
            log.error("Failed to evaluate feature flag: {}", flagName, e);
            return false;
        }
    }

    /**
     * Update feature flag
     */
    @Transactional
    @CacheEvict(value = "feature-flags", allEntries = true)
    public FeatureFlagDto updateFeatureFlag(String flagName, UpdateFeatureFlagRequest request) {
        String userId = securityContext.getCurrentUserId();
        
        FeatureFlag flag = featureFlagRepository.findByName(flagName)
            .orElseThrow(() -> new FeatureFlagNotFoundException("Feature flag not found: " + flagName));
        
        // Store old state for audit
        boolean oldEnabled = flag.isEnabled();
        
        // Update flag
        if (request.getEnabled() != null) {
            flag.setEnabled(request.getEnabled());
        }
        if (request.getDescription() != null) {
            flag.setDescription(request.getDescription());
        }
        if (request.getEnvironments() != null) {
            flag.setEnvironments(request.getEnvironments());
        }
        if (request.getTargetUsers() != null) {
            flag.setTargetUsers(request.getTargetUsers());
        }
        if (request.getPercentageRollout() != null) {
            flag.setPercentageRollout(request.getPercentageRollout());
        }
        if (request.getRules() != null) {
            flag.setRules(request.getRules());
        }
        
        flag.setLastModified(Instant.now());
        flag.setLastModifiedBy(userId);
        
        flag = featureFlagRepository.save(flag);
        
        // Create audit entry
        createFeatureFlagAudit(flag, oldEnabled, userId, "UPDATE");
        
        // Notify services
        notifyFeatureFlagChange(flag);
        
        // Publish event
        eventPublisher.publish(ConfigurationEvent.featureFlagUpdated(flag, oldEnabled));
        
        log.info("Updated feature flag {} by user {}, enabled: {}", 
            flagName, userId, flag.isEnabled());
        
        return toFeatureFlagDto(flag);
    }

    /**
     * Get service configuration with batch decryption to avoid N+1 query problem
     * Reduces operations from 1 query + N decrypts to 1 query + 1 batch decrypt
     */
    @Transactional(readOnly = true)
    public ServiceConfigDto getServiceConfig(String serviceName) {
        List<Configuration> configs = configRepository.findByServiceAndActiveTrue(serviceName);

        // Fast path: if encryption is disabled, process all configs directly
        if (!encryptionEnabled) {
            Map<String, ConfigValue> configMap = configs.stream()
                .collect(Collectors.toMap(
                    Configuration::getKey,
                    config -> ConfigValue.of(config.getKey(), config.getValue(), config.getType())
                ));

            return ServiceConfigDto.builder()
                .serviceName(serviceName)
                .configurations(configMap)
                .lastRefresh(Instant.now())
                .build();
        }

        // Separate encrypted and non-encrypted configs
        Map<String, Configuration> encryptedConfigs = new HashMap<>();
        Map<String, ConfigValue> resultMap = new HashMap<>();

        for (Configuration config : configs) {
            if (config.isEncrypted()) {
                encryptedConfigs.put(config.getKey(), config);
            } else {
                resultMap.put(
                    config.getKey(),
                    ConfigValue.of(config.getKey(), config.getValue(), config.getType())
                );
            }
        }

        // Batch decrypt all encrypted values in a single operation
        if (!encryptedConfigs.isEmpty()) {
            Map<String, String> toDecrypt = encryptedConfigs.entrySet().stream()
                .collect(Collectors.toMap(
                    Map.Entry::getKey,
                    entry -> entry.getValue().getValue()
                ));

            try {
                log.debug("Batch decrypting {} encrypted configurations for service: {}",
                    toDecrypt.size(), serviceName);

                Map<String, String> decrypted = encryptionService.decryptBatch(toDecrypt);

                // Add decrypted values to result
                decrypted.forEach((key, value) -> {
                    Configuration config = encryptedConfigs.get(key);
                    resultMap.put(key, ConfigValue.of(key, value, config.getType()));
                });

                log.debug("Successfully batch decrypted {} configurations", decrypted.size());

            } catch (Exception e) {
                log.error("Failed to batch decrypt configurations for service: {}, falling back to individual decryption",
                    serviceName, e);

                // Fallback: try individual decryption for each config
                encryptedConfigs.forEach((key, config) -> {
                    try {
                        String decrypted = encryptionService.decrypt(config.getValue());
                        resultMap.put(key, ConfigValue.of(key, decrypted, config.getType()));
                    } catch (Exception ex) {
                        log.error("Failed to decrypt config: {}, using encrypted value", key, ex);
                        // Add encrypted value as-is (better than failing completely)
                        resultMap.put(key, ConfigValue.of(key, config.getValue(), config.getType()));
                    }
                });
            }
        }

        return ServiceConfigDto.builder()
            .serviceName(serviceName)
            .configurations(resultMap)
            .lastRefresh(Instant.now())
            .build();
    }

    /**
     * Refresh configuration for all services
     */
    @Transactional
    public void refreshAllConfigurations() {
        String userId = securityContext.getCurrentUserId();
        
        // Clear all caches
        configCache.clear();
        
        // Send refresh event through Spring Cloud Bus
        applicationEventPublisher.publishEvent(
            new RefreshRemoteApplicationEvent(this, "config-service", null)
        );
        
        // Create audit entry
        ConfigAudit audit = ConfigAudit.builder()
            .action("REFRESH_ALL")
            .performedBy(userId)
            .timestamp(Instant.now())
            .details(Map.of("source", "manual_refresh"))
            .build();
        
        auditRepository.save(audit);
        
        // Publish event
        eventPublisher.publish(ConfigurationEvent.allConfigurationsRefreshed());
        
        log.info("Refreshed all configurations by user {}", userId);
    }

    /**
     * Search configurations
     */
    @Transactional(readOnly = true)
    public Page<ConfigurationDto> searchConfigurations(ConfigSearchRequest request, Pageable pageable) {
        Page<Configuration> configs = configRepository.searchConfigurations(
            request.getKey(),
            request.getService(),
            request.getEnvironment(),
            request.getActive(),
            pageable
        );
        
        return configs.map(this::toConfigurationDto);
    }

    /**
     * Get configuration history
     */
    @Transactional(readOnly = true)
    public List<ConfigAuditDto> getConfigHistory(String key) {
        List<ConfigAudit> audits = auditRepository.findByConfigKeyOrderByTimestampDesc(key);
        return audits.stream()
            .map(this::toConfigAuditDto)
            .collect(Collectors.toList());
    }

    /**
     * Bulk update configurations
     */
    @Transactional
    @CacheEvict(value = "configurations", allEntries = true)
    public BulkUpdateResultDto bulkUpdateConfigs(List<BulkConfigUpdate> updates) {
        String userId = securityContext.getCurrentUserId();
        List<String> successful = new ArrayList<>();
        List<String> failed = new ArrayList<>();
        
        for (BulkConfigUpdate update : updates) {
            try {
                updateConfig(update.getKey(), UpdateConfigRequest.builder()
                    .value(update.getValue())
                    .type(update.getType())
                    .build());
                successful.add(update.getKey());
            } catch (Exception e) {
                log.error("Failed to update config: {}", update.getKey(), e);
                failed.add(update.getKey() + ": " + e.getMessage());
            }
        }
        
        // Refresh configurations
        if (!successful.isEmpty()) {
            refreshAllConfigurations();
        }
        
        return BulkUpdateResultDto.builder()
            .successful(successful)
            .failed(failed)
            .totalProcessed(updates.size())
            .build();
    }

    /**
     * Export configurations
     */
    @Transactional(readOnly = true)
    public ConfigExportDto exportConfigurations(ExportConfigRequest request) {
        List<Configuration> configs;
        
        if (request.getService() != null) {
            configs = configRepository.findByServiceAndActiveTrue(request.getService());
        } else if (request.getEnvironment() != null) {
            configs = configRepository.findByEnvironmentAndActiveTrue(request.getEnvironment());
        } else {
            configs = configRepository.findByActiveTrue();
        }
        
        // Filter sensitive configs if requested
        if (!request.isIncludeSensitive()) {
            configs = configs.stream()
                .filter(config -> !config.isSensitive())
                .collect(Collectors.toList());
        }
        
        Map<String, Object> exportData = new HashMap<>();
        for (Configuration config : configs) {
            String value = config.getValue();
            if (config.isEncrypted() && request.isDecrypt()) {
                try {
                    value = encryptionService.decrypt(value);
                } catch (Exception e) {
                    value = "**ENCRYPTED**";
                }
            }
            exportData.put(config.getKey(), value);
        }
        
        return ConfigExportDto.builder()
            .configurations(exportData)
            .exportedAt(Instant.now())
            .exportedBy(securityContext.getCurrentUserId())
            .format(request.getFormat())
            .build();
    }

    /**
     * Import configurations
     */
    @Transactional
    @CacheEvict(value = "configurations", allEntries = true)
    public ConfigImportResultDto importConfigurations(ConfigImportRequest request) {
        String userId = securityContext.getCurrentUserId();
        List<String> imported = new ArrayList<>();
        List<String> skipped = new ArrayList<>();
        List<String> failed = new ArrayList<>();
        
        for (Map.Entry<String, Object> entry : request.getConfigurations().entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue().toString();
            
            try {
                boolean exists = configRepository.existsByKey(key);
                
                if (exists && !request.isOverwrite()) {
                    skipped.add(key + ": already exists");
                    continue;
                }
                
                if (exists) {
                    updateConfig(key, UpdateConfigRequest.builder()
                        .value(value)
                        .build());
                } else {
                    createConfig(CreateConfigRequest.builder()
                        .key(key)
                        .value(value)
                        .type(determineConfigType(value))
                        .service(request.getTargetService())
                        .environment(request.getTargetEnvironment())
                        .build());
                }
                
                imported.add(key);
                
            } catch (Exception e) {
                log.error("Failed to import config: {}", key, e);
                failed.add(key + ": " + e.getMessage());
            }
        }
        
        // Create audit entry
        ConfigAudit audit = ConfigAudit.builder()
            .action("BULK_IMPORT")
            .performedBy(userId)
            .timestamp(Instant.now())
            .details(Map.of(
                "imported", imported.size(),
                "skipped", skipped.size(),
                "failed", failed.size()
            ))
            .build();
        
        auditRepository.save(audit);
        
        return ConfigImportResultDto.builder()
            .imported(imported)
            .skipped(skipped)
            .failed(failed)
            .totalProcessed(request.getConfigurations().size())
            .build();
    }

    private void validateConfigUpdate(Configuration config, UpdateConfigRequest request) {
        if (strictValidation) {
            // Validate type compatibility
            if (request.getType() != null && request.getType() != config.getType()) {
                validateTypeChange(config.getType(), request.getType(), request.getValue());
            }
            
            // Validate value format
            validateConfigValue(request.getValue(), request.getType() != null ? 
                request.getType() : config.getType());
        }
    }

    private void validateNewConfig(CreateConfigRequest request) {
        // Validate key format
        if (!request.getKey().matches("^[a-zA-Z0-9._-]+$")) {
            throw new IllegalArgumentException("Invalid configuration key format");
        }
        
        // Validate value
        validateConfigValue(request.getValue(), request.getType());
    }

    private void validateConfigValue(String value, ConfigType type) {
        try {
            switch (type) {
                case INTEGER:
                    Integer.parseInt(value);
                    break;
                case LONG:
                    Long.parseLong(value);
                    break;
                case DOUBLE:
                    Double.parseDouble(value);
                    break;
                case BOOLEAN:
                    if (!value.equalsIgnoreCase("true") && !value.equalsIgnoreCase("false")) {
                        throw new IllegalArgumentException("Invalid boolean value");
                    }
                    break;
                case JSON:
                    objectMapper.readTree(value);
                    break;
                case LIST:
                    // Check if comma-separated values
                    if (value.isEmpty()) {
                        throw new IllegalArgumentException("Empty list value");
                    }
                    break;
            }
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid value for type " + type + ": " + e.getMessage());
        }
    }

    private void validateTypeChange(ConfigType oldType, ConfigType newType, String value) {
        // Define allowed type transitions
        Set<ConfigType> compatibleTypes = new HashSet<>();
        
        switch (oldType) {
            case STRING:
                compatibleTypes.addAll(Arrays.asList(ConfigType.values()));
                break;
            case INTEGER:
                compatibleTypes.addAll(Arrays.asList(ConfigType.LONG, ConfigType.DOUBLE, ConfigType.STRING));
                break;
            case LONG:
                compatibleTypes.addAll(Arrays.asList(ConfigType.DOUBLE, ConfigType.STRING));
                break;
            case DOUBLE:
                compatibleTypes.add(ConfigType.STRING);
                break;
            case BOOLEAN:
                compatibleTypes.add(ConfigType.STRING);
                break;
            case JSON:
                compatibleTypes.add(ConfigType.STRING);
                break;
            case LIST:
                compatibleTypes.addAll(Arrays.asList(ConfigType.JSON, ConfigType.STRING));
                break;
        }
        
        if (!compatibleTypes.contains(newType)) {
            throw new IllegalArgumentException(
                String.format("Cannot change type from %s to %s", oldType, newType));
        }
    }

    private boolean evaluateFeatureFlagRules(List<FeatureFlagRule> rules, Map<String, Object> context) {
        for (FeatureFlagRule rule : rules) {
            if (!evaluateRule(rule, context)) {
                return false;
            }
        }
        return true;
    }

    private boolean evaluateRule(FeatureFlagRule rule, Map<String, Object> context) {
        Object contextValue = context.get(rule.getAttribute());
        if (contextValue == null) {
            return false;
        }
        
        switch (rule.getOperator()) {
            case EQUALS:
                return contextValue.toString().equals(rule.getValue());
            case NOT_EQUALS:
                return !contextValue.toString().equals(rule.getValue());
            case CONTAINS:
                return contextValue.toString().contains(rule.getValue());
            case GREATER_THAN:
                return compareNumeric(contextValue, rule.getValue()) > 0;
            case LESS_THAN:
                return compareNumeric(contextValue, rule.getValue()) < 0;
            case IN:
                return Arrays.asList(rule.getValue().split(",")).contains(contextValue.toString());
            default:
                return false;
        }
    }

    private int compareNumeric(Object value1, String value2) {
        try {
            Double d1 = Double.parseDouble(value1.toString());
            Double d2 = Double.parseDouble(value2);
            return d1.compareTo(d2);
        } catch (NumberFormatException e) {
            return value1.toString().compareTo(value2);
        }
    }

    private void notifyConfigurationChange(Configuration config) {
        // Notify specific service if applicable
        if (config.getService() != null) {
            applicationEventPublisher.publishEvent(
                new RefreshRemoteApplicationEvent(this, "config-service", config.getService())
            );
        } else {
            // Notify all services
            refreshAllConfigurations();
        }
    }

    private void notifyFeatureFlagChange(FeatureFlag flag) {
        // Send feature flag update event
        applicationEventPublisher.publishEvent(
            new FeatureFlagChangeEvent(flag.getName(), flag.isEnabled())
        );
    }

    private void createAuditEntry(Configuration config, String oldValue, boolean wasEncrypted, 
                                 String userId, String action) {
        ConfigAudit audit = ConfigAudit.builder()
            .configKey(config.getKey())
            .action(action)
            .oldValue(wasEncrypted ? "**ENCRYPTED**" : oldValue)
            .newValue(config.isEncrypted() ? "**ENCRYPTED**" : config.getValue())
            .performedBy(userId)
            .timestamp(Instant.now())
            .service(config.getService())
            .environment(config.getEnvironment())
            .metadata(config.getMetadata())
            .build();
        
        auditRepository.save(audit);
    }

    private void createFeatureFlagAudit(FeatureFlag flag, boolean oldEnabled, String userId, String action) {
        ConfigAudit audit = ConfigAudit.builder()
            .configKey("feature:" + flag.getName())
            .action(action)
            .oldValue(String.valueOf(oldEnabled))
            .newValue(String.valueOf(flag.isEnabled()))
            .performedBy(userId)
            .timestamp(Instant.now())
            .details(Map.of(
                "percentageRollout", flag.getPercentageRollout() != null ? flag.getPercentageRollout() : 0,
                "targetUsers", flag.getTargetUsers() != null ? flag.getTargetUsers().size() : 0,
                "environments", flag.getEnvironments() != null ? flag.getEnvironments() : Collections.emptySet()
            ))
            .build();
        
        auditRepository.save(audit);
    }

    private ConfigType determineConfigType(String value) {
        // Try to determine type from value
        try {
            Integer.parseInt(value);
            return ConfigType.INTEGER;
        } catch (NumberFormatException e) {
            // Not an integer
        }
        
        try {
            Double.parseDouble(value);
            return ConfigType.DOUBLE;
        } catch (NumberFormatException e) {
            // Not a double
        }
        
        if (value.equalsIgnoreCase("true") || value.equalsIgnoreCase("false")) {
            return ConfigType.BOOLEAN;
        }
        
        if (value.startsWith("{") || value.startsWith("[")) {
            try {
                objectMapper.readTree(value);
                return ConfigType.JSON;
            } catch (Exception e) {
                // Not valid JSON
            }
        }
        
        if (value.contains(",")) {
            return ConfigType.LIST;
        }
        
        return ConfigType.STRING;
    }

    private String getCurrentEnvironment() {
        return environment.getActiveProfiles()[0];
    }

    // DTO conversion methods
    private ConfigurationDto toConfigurationDto(Configuration config) {
        return ConfigurationDto.builder()
            .key(config.getKey())
            .value(config.isEncrypted() ? "**ENCRYPTED**" : config.getValue())
            .type(config.getType())
            .description(config.getDescription())
            .service(config.getService())
            .environment(config.getEnvironment())
            .sensitive(config.isSensitive())
            .encrypted(config.isEncrypted())
            .active(config.isActive())
            .createdAt(config.getCreatedAt())
            .lastModified(config.getLastModified())
            .build();
    }

    private FeatureFlagDto toFeatureFlagDto(FeatureFlag flag) {
        return FeatureFlagDto.builder()
            .name(flag.getName())
            .description(flag.getDescription())
            .enabled(flag.isEnabled())
            .environments(flag.getEnvironments())
            .targetUsers(flag.getTargetUsers())
            .percentageRollout(flag.getPercentageRollout())
            .rules(flag.getRules())
            .active(flag.isActive())
            .createdAt(flag.getCreatedAt())
            .lastModified(flag.getLastModified())
            .build();
    }

    private ConfigAuditDto toConfigAuditDto(ConfigAudit audit) {
        return ConfigAuditDto.builder()
            .id(audit.getId())
            .configKey(audit.getConfigKey())
            .action(audit.getAction())
            .oldValue(audit.getOldValue())
            .newValue(audit.getNewValue())
            .performedBy(audit.getPerformedBy())
            .timestamp(audit.getTimestamp())
            .service(audit.getService())
            .environment(audit.getEnvironment())
            .build();
    }

    /**
     * Create a new feature flag
     */
    @Transactional
    public FeatureFlagDto createFeatureFlag(CreateFeatureFlagRequest request) {
        log.info("Creating feature flag: {}", request.getName());

        // Check if feature flag already exists
        if (featureFlagRepository.findByName(request.getName()).isPresent()) {
            throw new IllegalArgumentException("Feature flag already exists: " + request.getName());
        }

        String userId = securityContext.getCurrentUserId();

        // Create feature flag entity
        FeatureFlag flag = FeatureFlag.builder()
                .name(request.getName())
                .description(request.getDescription())
                .enabled(request.isEnabled() != null ? request.isEnabled() : false)
                .environment(request.getEnvironment())
                .rules(request.getRules())
                .targetUsers(request.getTargetUsers())
                .targetGroups(request.getTargetGroups())
                .rolloutPercentage(request.getRolloutPercentage() != null ? request.getRolloutPercentage() : 0)
                .startDate(request.getStartDate())
                .endDate(request.getEndDate())
                .createdBy(userId)
                .modifiedBy(userId)
                .build();

        // Save feature flag
        flag = featureFlagRepository.save(flag);

        // Create audit entry
        createFeatureFlagAudit(flag, false, userId, "CREATE");

        // Publish event
        publishFeatureFlagChangeEvent(flag, FeatureFlagChangeEvent.ChangeType.CREATED);

        log.info("Feature flag created successfully: {}", flag.getName());

        return toFeatureFlagDto(flag);
    }

    /**
     * Get all feature flags
     */
    public List<FeatureFlagDto> getAllFeatureFlags() {
        log.debug("Retrieving all feature flags");

        List<FeatureFlag> flags = featureFlagRepository.findAll();

        return flags.stream()
                .map(this::toFeatureFlagDto)
                .collect(Collectors.toList());
    }

    /**
     * Delete a feature flag by name
     */
    @Transactional
    public void deleteFeatureFlag(String flagName) {
        log.warn("Deleting feature flag: {}", flagName);

        FeatureFlag flag = featureFlagRepository.findByName(flagName)
                .orElseThrow(() -> new NotFoundException("Feature flag not found: " + flagName));

        String userId = securityContext.getCurrentUserId();

        // Create audit entry before deletion
        createFeatureFlagAudit(flag, flag.isEnabled(), userId, "DELETE");

        // Publish event
        publishFeatureFlagChangeEvent(flag, FeatureFlagChangeEvent.ChangeType.DELETED);

        // Delete feature flag
        featureFlagRepository.delete(flag);

        log.info("Feature flag deleted successfully: {}", flagName);
    }

    /**
     * Get configuration metrics
     */
    public ConfigMetricsDto getConfigMetrics() {
        log.debug("Calculating configuration metrics");

        long totalConfigs = configRepository.count();
        long activeConfigs = configRepository.countByActiveTrue();
        long sensitiveConfigs = configRepository.countBySensitiveTrue();
        long encryptedConfigs = configRepository.countByEncryptedTrue();

        long totalFeatureFlags = featureFlagRepository.count();
        long enabledFeatureFlags = featureFlagRepository.countByEnabledTrue();

        // Calculate by environment
        Map<String, Long> configsByEnvironment = configRepository.findAll().stream()
                .filter(c -> c.getEnvironment() != null)
                .collect(Collectors.groupingBy(
                        Configuration::getEnvironment,
                        Collectors.counting()
                ));

        // Calculate by service
        Map<String, Long> configsByService = configRepository.findAll().stream()
                .filter(c -> c.getService() != null)
                .collect(Collectors.groupingBy(
                        Configuration::getService,
                        Collectors.counting()
                ));

        // Calculate cache statistics from Caffeine
        CacheStats cacheStats = configCache.stats();
        long cacheSize = configCache.estimatedSize();
        long cacheHits = cacheStats.hitCount();
        long cacheMisses = cacheStats.missCount();
        double cacheHitRate = cacheStats.hitRate();
        long evictionCount = cacheStats.evictionCount();

        return ConfigMetricsDto.builder()
                .totalConfigurations(totalConfigs)
                .activeConfigurations(activeConfigs)
                .sensitiveConfigurations(sensitiveConfigs)
                .encryptedConfigurations(encryptedConfigs)
                .totalFeatureFlags(totalFeatureFlags)
                .enabledFeatureFlags(enabledFeatureFlags)
                .configurationsByEnvironment(configsByEnvironment)
                .configurationsByService(configsByService)
                .cacheSize(cacheSize)
                .cacheHitRate(cacheHitRate)
                .cacheHits(cacheHits)
                .cacheMisses(cacheMisses)
                .cacheEvictions(evictionCount)
                .lastUpdated(Instant.now())
                .build();
    }

    /**
     * Validate configuration without saving
     */
    public ValidationResultDto validateConfig(ValidateConfigRequest request) {
        log.debug("Validating configuration: key={}, type={}", request.getKey(), request.getType());

        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        // Validate key format
        if (!request.getKey().matches("^[a-zA-Z0-9._-]+$")) {
            errors.add("Invalid configuration key format. Only alphanumeric characters, dots, underscores, and hyphens are allowed.");
        }

        // Validate value for type
        try {
            validateConfigValue(request.getValue(), request.getType());
        } catch (Exception e) {
            errors.add("Invalid value for type " + request.getType() + ": " + e.getMessage());
        }

        // Check if key already exists (warning only)
        if (configRepository.findByKeyAndActiveTrue(request.getKey()).isPresent()) {
            warnings.add("Configuration key already exists and will be overwritten if you save.");
        }

        // Additional validations
        if (request.isSensitive() && !request.isEncrypted()) {
            warnings.add("Sensitive configuration is not encrypted. Consider enabling encryption.");
        }

        // Type-specific validations
        if (request.getType() == ConfigType.URL) {
            try {
                new java.net.URL(request.getValue());
            } catch (Exception e) {
                errors.add("Invalid URL format: " + e.getMessage());
            }
        }

        if (request.getType() == ConfigType.EMAIL) {
            if (!request.getValue().matches("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$")) {
                errors.add("Invalid email format");
            }
        }

        if (request.getType() == ConfigType.JSON) {
            try {
                objectMapper.readTree(request.getValue());
            } catch (Exception e) {
                errors.add("Invalid JSON format: " + e.getMessage());
            }
        }

        boolean isValid = errors.isEmpty();

        return ValidationResultDto.builder()
                .valid(isValid)
                .errors(errors)
                .warnings(warnings)
                .validatedAt(Instant.now())
                .build();
    }

    /**
     * Get configuration dependencies
     */
    public ConfigDependenciesDto getConfigDependencies(String key) {
        log.debug("Getting dependencies for configuration: {}", key);

        Configuration config = configRepository.findByKeyAndActiveTrue(key)
                .orElseThrow(() -> new NotFoundException("Configuration not found: " + key));

        // Find configurations that reference this key in their value
        // This is a simple implementation - could be enhanced with AST parsing for complex expressions
        List<Configuration> dependentConfigs = configRepository.findAll().stream()
                .filter(c -> c.getValue() != null && c.getValue().contains(key))
                .collect(Collectors.toList());

        List<String> dependentKeys = dependentConfigs.stream()
                .map(Configuration::getKey)
                .collect(Collectors.toList());

        // Find services that use this configuration
        List<String> dependentServices = new ArrayList<>();
        if (config.getService() != null) {
            dependentServices.add(config.getService());
        }

        // Check service configs that might reference this key
        serviceConfigRepository.findAll().stream()
                .filter(sc -> sc.getConfigData() != null && sc.getConfigData().contains(key))
                .map(ServiceConfig::getServiceName)
                .forEach(dependentServices::add);

        // Find feature flags that might depend on this config
        List<String> affectedFeatureFlags = featureFlagRepository.findAll().stream()
                .filter(ff -> ff.getRules() != null && ff.getRules().contains(key))
                .map(FeatureFlag::getName)
                .collect(Collectors.toList());

        return ConfigDependenciesDto.builder()
                .configurationKey(key)
                .dependentConfigurations(dependentKeys)
                .dependentServices(dependentServices)
                .affectedFeatureFlags(affectedFeatureFlags)
                .totalDependencies(dependentKeys.size() + dependentServices.size() + affectedFeatureFlags.size())
                .impactLevel(calculateImpactLevel(dependentKeys.size(), dependentServices.size(), affectedFeatureFlags.size()))
                .queriedAt(Instant.now())
                .build();
    }

    /**
     * Calculate impact level based on dependency counts
     */
    private String calculateImpactLevel(int configCount, int serviceCount, int featureFlagCount) {
        int totalDependencies = configCount + serviceCount + featureFlagCount;

        if (totalDependencies == 0) return "NONE";
        if (totalDependencies <= 2) return "LOW";
        if (totalDependencies <= 5) return "MEDIUM";
        if (totalDependencies <= 10) return "HIGH";
        return "CRITICAL";
    }

    /**
     * Publish feature flag change event
     */
    private void publishFeatureFlagChangeEvent(FeatureFlag flag, FeatureFlagChangeEvent.ChangeType changeType) {
        try {
            FeatureFlagChangeEvent event = FeatureFlagChangeEvent.builder()
                    .eventId(UUID.randomUUID().toString())
                    .changeType(changeType)
                    .flagName(flag.getName())
                    .environment(flag.getEnvironment())
                    .newEnabled(flag.isEnabled())
                    .newRolloutPercentage(flag.getRolloutPercentage())
                    .changedBy(securityContext.getCurrentUserId())
                    .timestamp(Instant.now())
                    .build();

            eventPublisher.publish("feature-flag-changes", event);

            log.debug("Published feature flag change event: {}", event.getEventId());
        } catch (Exception e) {
            log.error("Failed to publish feature flag change event", e);
            // Don't fail the operation if event publishing fails
        }
    }
}