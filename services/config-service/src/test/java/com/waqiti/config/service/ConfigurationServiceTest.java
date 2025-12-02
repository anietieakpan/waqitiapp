package com.waqiti.config.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.config.client.NotificationServiceClient;
import com.waqiti.config.domain.Configuration;
import com.waqiti.config.domain.ConfigAudit;
import com.waqiti.config.domain.FeatureFlag;
import com.waqiti.config.dto.*;
import com.waqiti.config.enums.ConfigType;
import com.waqiti.config.repository.ConfigAuditRepository;
import com.waqiti.config.repository.ConfigurationRepository;
import com.waqiti.config.repository.FeatureFlagRepository;
import com.waqiti.config.repository.ServiceConfigRepository;
import com.waqiti.common.events.ConfigurationEvent;
import com.waqiti.common.events.EventPublisher;
import com.waqiti.common.security.SecurityContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.env.Environment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.vault.core.VaultTemplate;

import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Comprehensive unit tests for ConfigurationService
 * Tests CRUD operations, caching, transactions, feature flags, and bulk operations
 */
@ExtendWith(MockitoExtension.class)
class ConfigurationServiceTest {

    @Mock
    private ConfigurationRepository configRepository;

    @Mock
    private FeatureFlagRepository featureFlagRepository;

    @Mock
    private ConfigAuditRepository auditRepository;

    @Mock
    private ServiceConfigRepository serviceConfigRepository;

    @Mock
    private VaultTemplate vaultTemplate;

    @Mock
    private ConfigEncryptionService encryptionService;

    @Mock
    private NotificationServiceClient notificationServiceClient;

    @Mock
    private EventPublisher eventPublisher;

    @Mock
    private ApplicationEventPublisher applicationEventPublisher;

    @Mock
    private SecurityContext securityContext;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private Environment environment;

    private ConfigurationService configurationService;

    private static final String TEST_USER_ID = "user-123";
    private static final String TEST_CONFIG_KEY = "app.database.url";
    private static final String TEST_CONFIG_VALUE = "jdbc:postgresql://localhost:5432/testdb";
    private static final String TEST_SERVICE_NAME = "payment-service";

    @BeforeEach
    void setUp() {
        configurationService = new ConfigurationService(
            configRepository,
            featureFlagRepository,
            auditRepository,
            serviceConfigRepository,
            vaultTemplate,
            encryptionService,
            notificationServiceClient,
            eventPublisher,
            applicationEventPublisher,
            securityContext,
            objectMapper,
            environment
        );

        // Set configuration properties
        ReflectionTestUtils.setField(configurationService, "cacheTtlSeconds", 300);
        ReflectionTestUtils.setField(configurationService, "cacheMaxSize", 10000L);
        ReflectionTestUtils.setField(configurationService, "encryptionEnabled", true);
        ReflectionTestUtils.setField(configurationService, "strictValidation", true);

        // Mock security context
        when(securityContext.getCurrentUserId()).thenReturn(TEST_USER_ID);
    }

    // ==================== CRUD Operations Tests ====================

    @Test
    void testCreateConfig_ValidRequest_CreatesConfiguration() {
        // Given
        CreateConfigRequest request = CreateConfigRequest.builder()
            .key(TEST_CONFIG_KEY)
            .value(TEST_CONFIG_VALUE)
            .dataType("STRING")
            .service(TEST_SERVICE_NAME)
            .environment("production")
            .encrypted(false)
            .description("Database connection URL")
            .build();

        Configuration savedConfig = createTestConfiguration(TEST_CONFIG_KEY, TEST_CONFIG_VALUE);
        when(configRepository.findByKey(TEST_CONFIG_KEY)).thenReturn(Optional.empty());
        when(configRepository.save(any(Configuration.class))).thenReturn(savedConfig);

        // When
        ConfigValue result = configurationService.createConfig(request);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getKey()).isEqualTo(TEST_CONFIG_KEY);
        assertThat(result.getValue()).isEqualTo(TEST_CONFIG_VALUE);

        verify(configRepository).save(any(Configuration.class));
        verify(auditRepository).save(any(ConfigAudit.class));
        verify(eventPublisher).publish(any(ConfigurationEvent.class));
    }

    @Test
    void testCreateConfig_DuplicateKey_ThrowsException() {
        // Given
        CreateConfigRequest request = CreateConfigRequest.builder()
            .key(TEST_CONFIG_KEY)
            .value(TEST_CONFIG_VALUE)
            .dataType("STRING")
            .build();

        Configuration existingConfig = createTestConfiguration(TEST_CONFIG_KEY, "old-value");
        when(configRepository.findByKey(TEST_CONFIG_KEY)).thenReturn(Optional.of(existingConfig));

        // When/Then
        assertThatThrownBy(() -> configurationService.createConfig(request))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("already exists");

        verify(configRepository, never()).save(any());
    }

    @Test
    void testCreateConfig_EncryptedValue_EncryptsBeforeSaving() {
        // Given
        String plainValue = "secret-password-123";
        String encryptedValue = "ENC(encrypted-data)";

        CreateConfigRequest request = CreateConfigRequest.builder()
            .key("db.password")
            .value(plainValue)
            .dataType("STRING")
            .encrypted(true)
            .build();

        Configuration savedConfig = createTestConfiguration("db.password", encryptedValue);
        when(configRepository.findByKey("db.password")).thenReturn(Optional.empty());
        when(encryptionService.encrypt(plainValue)).thenReturn(encryptedValue);
        when(configRepository.save(any(Configuration.class))).thenReturn(savedConfig);

        // When
        ConfigValue result = configurationService.createConfig(request);

        // Then
        verify(encryptionService).encrypt(plainValue);
        verify(configRepository).save(argThat(config ->
            config.getValue().equals(encryptedValue) && config.isEncrypted()
        ));
    }

    @Test
    void testGetConfig_ExistingKey_ReturnsConfigValue() {
        // Given
        Configuration config = createTestConfiguration(TEST_CONFIG_KEY, TEST_CONFIG_VALUE);
        when(configRepository.findByKey(TEST_CONFIG_KEY)).thenReturn(Optional.of(config));

        // When
        ConfigValue result = configurationService.getConfig(TEST_CONFIG_KEY);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getKey()).isEqualTo(TEST_CONFIG_KEY);
        assertThat(result.getValue()).isEqualTo(TEST_CONFIG_VALUE);

        verify(configRepository).findByKey(TEST_CONFIG_KEY);
    }

    @Test
    void testGetConfig_NonExistentKey_ReturnsDefaultValue() {
        // Given
        String defaultValue = "default-value";
        when(configRepository.findByKey(TEST_CONFIG_KEY)).thenReturn(Optional.empty());

        // When
        ConfigValue result = configurationService.getConfig(TEST_CONFIG_KEY, defaultValue);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getKey()).isEqualTo(TEST_CONFIG_KEY);
        assertThat(result.getValue()).isEqualTo(defaultValue);
    }

    @Test
    void testGetConfig_EncryptedValue_DecryptsBeforeReturning() {
        // Given
        String encryptedValue = "ENC(encrypted-data)";
        String decryptedValue = "secret-password";

        Configuration config = createTestConfiguration("db.password", encryptedValue);
        config.setEncrypted(true);

        when(configRepository.findByKey("db.password")).thenReturn(Optional.of(config));
        when(encryptionService.decrypt(encryptedValue)).thenReturn(decryptedValue);

        // When
        ConfigValue result = configurationService.getConfig("db.password");

        // Then
        assertThat(result.getValue()).isEqualTo(decryptedValue);
        verify(encryptionService).decrypt(encryptedValue);
    }

    @Test
    void testUpdateConfig_ExistingKey_UpdatesSuccessfully() {
        // Given
        String newValue = "new-value";
        UpdateConfigRequest request = UpdateConfigRequest.builder()
            .value(newValue)
            .description("Updated description")
            .build();

        Configuration existingConfig = createTestConfiguration(TEST_CONFIG_KEY, "old-value");
        when(configRepository.findByKey(TEST_CONFIG_KEY)).thenReturn(Optional.of(existingConfig));
        when(configRepository.save(any(Configuration.class))).thenReturn(existingConfig);

        // When
        ConfigValue result = configurationService.updateConfig(TEST_CONFIG_KEY, request);

        // Then
        assertThat(result).isNotNull();
        verify(configRepository).save(any(Configuration.class));
        verify(auditRepository).save(any(ConfigAudit.class));
        verify(eventPublisher).publish(any(ConfigurationEvent.class));
    }

    @Test
    void testUpdateConfig_NonExistentKey_ThrowsException() {
        // Given
        UpdateConfigRequest request = UpdateConfigRequest.builder()
            .value("new-value")
            .build();

        when(configRepository.findByKey(TEST_CONFIG_KEY)).thenReturn(Optional.empty());

        // When/Then
        assertThatThrownBy(() -> configurationService.updateConfig(TEST_CONFIG_KEY, request))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("not found");
    }

    @Test
    void testDeleteConfig_ExistingKey_DeletesSuccessfully() {
        // Given
        Configuration config = createTestConfiguration(TEST_CONFIG_KEY, TEST_CONFIG_VALUE);
        when(configRepository.findByKey(TEST_CONFIG_KEY)).thenReturn(Optional.of(config));

        // When
        configurationService.deleteConfig(TEST_CONFIG_KEY);

        // Then
        verify(configRepository).delete(config);
        verify(auditRepository).save(any(ConfigAudit.class));
        verify(eventPublisher).publish(any(ConfigurationEvent.class));
    }

    // ==================== Feature Flag Tests ====================

    @Test
    void testCreateFeatureFlag_ValidRequest_CreatesFlag() {
        // Given
        CreateFeatureFlagRequest request = CreateFeatureFlagRequest.builder()
            .name("new-checkout-flow")
            .description("New checkout flow feature")
            .enabled(false)
            .rolloutPercentage(0)
            .build();

        FeatureFlag savedFlag = createTestFeatureFlag("new-checkout-flow", false);
        when(featureFlagRepository.findByName("new-checkout-flow")).thenReturn(Optional.empty());
        when(featureFlagRepository.save(any(FeatureFlag.class))).thenReturn(savedFlag);

        // When
        FeatureFlagDto result = configurationService.createFeatureFlag(request);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getName()).isEqualTo("new-checkout-flow");
        assertThat(result.isEnabled()).isFalse();

        verify(featureFlagRepository).save(any(FeatureFlag.class));
        verify(eventPublisher).publish(any());
    }

    @Test
    void testCreateFeatureFlag_DuplicateName_ThrowsException() {
        // Given
        CreateFeatureFlagRequest request = CreateFeatureFlagRequest.builder()
            .name("existing-flag")
            .build();

        FeatureFlag existingFlag = createTestFeatureFlag("existing-flag", true);
        when(featureFlagRepository.findByName("existing-flag")).thenReturn(Optional.of(existingFlag));

        // When/Then
        assertThatThrownBy(() -> configurationService.createFeatureFlag(request))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("already exists");
    }

    @Test
    void testIsFeatureEnabled_EnabledFlag_ReturnsTrue() {
        // Given
        FeatureFlag flag = createTestFeatureFlag("feature-x", true);
        flag.setRolloutPercentage(100);

        when(featureFlagRepository.findByName("feature-x")).thenReturn(Optional.of(flag));

        // When
        boolean result = configurationService.isFeatureEnabled("feature-x", "user-123", Map.of());

        // Then
        assertThat(result).isTrue();
    }

    @Test
    void testIsFeatureEnabled_DisabledFlag_ReturnsFalse() {
        // Given
        FeatureFlag flag = createTestFeatureFlag("feature-x", false);

        when(featureFlagRepository.findByName("feature-x")).thenReturn(Optional.of(flag));

        // When
        boolean result = configurationService.isFeatureEnabled("feature-x", "user-123", Map.of());

        // Then
        assertThat(result).isFalse();
    }

    @Test
    void testIsFeatureEnabled_NonExistentFlag_ReturnsFalse() {
        // Given
        when(featureFlagRepository.findByName("non-existent")).thenReturn(Optional.empty());

        // When
        boolean result = configurationService.isFeatureEnabled("non-existent", "user-123", Map.of());

        // Then
        assertThat(result).isFalse();
    }

    @Test
    void testUpdateFeatureFlag_ExistingFlag_UpdatesSuccessfully() {
        // Given
        UpdateFeatureFlagRequest request = UpdateFeatureFlagRequest.builder()
            .enabled(true)
            .rolloutPercentage(50)
            .description("Updated description")
            .build();

        FeatureFlag existingFlag = createTestFeatureFlag("feature-x", false);
        when(featureFlagRepository.findByName("feature-x")).thenReturn(Optional.of(existingFlag));
        when(featureFlagRepository.save(any(FeatureFlag.class))).thenReturn(existingFlag);

        // When
        FeatureFlagDto result = configurationService.updateFeatureFlag("feature-x", request);

        // Then
        assertThat(result).isNotNull();
        verify(featureFlagRepository).save(any(FeatureFlag.class));
        verify(eventPublisher).publish(any());
    }

    @Test
    void testGetAllFeatureFlags_ReturnsAllFlags() {
        // Given
        List<FeatureFlag> flags = Arrays.asList(
            createTestFeatureFlag("flag1", true),
            createTestFeatureFlag("flag2", false),
            createTestFeatureFlag("flag3", true)
        );

        when(featureFlagRepository.findAll()).thenReturn(flags);

        // When
        List<FeatureFlagDto> result = configurationService.getAllFeatureFlags();

        // Then
        assertThat(result).hasSize(3);
        assertThat(result).extracting(FeatureFlagDto::getName)
            .containsExactly("flag1", "flag2", "flag3");
    }

    @Test
    void testDeleteFeatureFlag_ExistingFlag_DeletesSuccessfully() {
        // Given
        FeatureFlag flag = createTestFeatureFlag("feature-x", true);
        when(featureFlagRepository.findByName("feature-x")).thenReturn(Optional.of(flag));

        // When
        configurationService.deleteFeatureFlag("feature-x");

        // Then
        verify(featureFlagRepository).delete(flag);
        verify(eventPublisher).publish(any());
    }

    // ==================== Service Configuration Tests ====================

    @Test
    void testGetServiceConfig_WithEncryptedValues_BatchDecryptsSuccessfully() {
        // Given
        Configuration config1 = createTestConfiguration("api.key", "ENC(encrypted1)");
        config1.setEncrypted(true);
        config1.setService(TEST_SERVICE_NAME);

        Configuration config2 = createTestConfiguration("db.password", "ENC(encrypted2)");
        config2.setEncrypted(true);
        config2.setService(TEST_SERVICE_NAME);

        Configuration config3 = createTestConfiguration("plain.config", "plain-value");
        config3.setEncrypted(false);
        config3.setService(TEST_SERVICE_NAME);

        List<Configuration> configs = Arrays.asList(config1, config2, config3);

        Map<String, String> toDecrypt = new HashMap<>();
        toDecrypt.put("api.key", "ENC(encrypted1)");
        toDecrypt.put("db.password", "ENC(encrypted2)");

        Map<String, String> decrypted = new HashMap<>();
        decrypted.put("api.key", "decrypted-api-key");
        decrypted.put("db.password", "decrypted-password");

        when(configRepository.findByServiceAndActiveTrue(TEST_SERVICE_NAME)).thenReturn(configs);
        when(encryptionService.decryptBatch(any())).thenReturn(decrypted);

        // When
        ServiceConfigDto result = configurationService.getServiceConfig(TEST_SERVICE_NAME);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getServiceName()).isEqualTo(TEST_SERVICE_NAME);
        assertThat(result.getConfigurations()).hasSize(3);
        assertThat(result.getConfigurations().get("api.key").getValue()).isEqualTo("decrypted-api-key");
        assertThat(result.getConfigurations().get("db.password").getValue()).isEqualTo("decrypted-password");
        assertThat(result.getConfigurations().get("plain.config").getValue()).isEqualTo("plain-value");

        verify(encryptionService).decryptBatch(any());
        verify(encryptionService, never()).decrypt(anyString()); // Should use batch, not individual
    }

    // ==================== Bulk Operations Tests ====================

    @Test
    void testBulkUpdateConfigs_MultipleUpdates_UpdatesAll() {
        // Given
        BulkConfigUpdate update1 = BulkConfigUpdate.builder()
            .key("config1")
            .value("value1")
            .build();

        BulkConfigUpdate update2 = BulkConfigUpdate.builder()
            .key("config2")
            .value("value2")
            .build();

        List<BulkConfigUpdate> updates = Arrays.asList(update1, update2);

        Configuration config1 = createTestConfiguration("config1", "old-value1");
        Configuration config2 = createTestConfiguration("config2", "old-value2");

        when(configRepository.findByKey("config1")).thenReturn(Optional.of(config1));
        when(configRepository.findByKey("config2")).thenReturn(Optional.of(config2));
        when(configRepository.save(any(Configuration.class))).thenAnswer(i -> i.getArgument(0));

        // When
        BulkUpdateResultDto result = configurationService.bulkUpdateConfigs(updates);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getSuccessCount()).isEqualTo(2);
        assertThat(result.getFailureCount()).isEqualTo(0);

        verify(configRepository, times(2)).save(any(Configuration.class));
    }

    @Test
    void testBulkUpdateConfigs_SomeFailures_ReturnsPartialSuccess() {
        // Given
        BulkConfigUpdate update1 = BulkConfigUpdate.builder()
            .key("existing-config")
            .value("value1")
            .build();

        BulkConfigUpdate update2 = BulkConfigUpdate.builder()
            .key("non-existent-config")
            .value("value2")
            .build();

        List<BulkConfigUpdate> updates = Arrays.asList(update1, update2);

        Configuration config1 = createTestConfiguration("existing-config", "old-value");

        when(configRepository.findByKey("existing-config")).thenReturn(Optional.of(config1));
        when(configRepository.findByKey("non-existent-config")).thenReturn(Optional.empty());
        when(configRepository.save(any(Configuration.class))).thenAnswer(i -> i.getArgument(0));

        // When
        BulkUpdateResultDto result = configurationService.bulkUpdateConfigs(updates);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getSuccessCount()).isEqualTo(1);
        assertThat(result.getFailureCount()).isEqualTo(1);
        assertThat(result.getErrors()).hasSize(1);
    }

    // ==================== Validation Tests ====================

    @Test
    void testValidateConfig_ValidConfiguration_ReturnsValid() {
        // Given
        ValidateConfigRequest request = ValidateConfigRequest.builder()
            .key("app.timeout")
            .value("30")
            .dataType("INTEGER")
            .build();

        // When
        ValidationResultDto result = configurationService.validateConfig(request);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.isValid()).isTrue();
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    void testValidateConfig_InvalidDataType_ReturnsInvalid() {
        // Given
        ValidateConfigRequest request = ValidateConfigRequest.builder()
            .key("app.timeout")
            .value("not-a-number")
            .dataType("INTEGER")
            .build();

        // When
        ValidationResultDto result = configurationService.validateConfig(request);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.isValid()).isFalse();
        assertThat(result.getErrors()).isNotEmpty();
    }

    // ==================== Metrics Tests ====================

    @Test
    void testGetConfigMetrics_ReturnsMetrics() {
        // Given
        when(configRepository.count()).thenReturn(100L);
        when(featureFlagRepository.count()).thenReturn(20L);

        // When
        ConfigMetricsDto result = configurationService.getConfigMetrics();

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getTotalConfigs()).isEqualTo(100L);
        assertThat(result.getTotalFeatureFlags()).isEqualTo(20L);
        assertThat(result.getCacheSize()).isGreaterThanOrEqualTo(0);
    }

    // ==================== History/Audit Tests ====================

    @Test
    void testGetConfigHistory_ReturnsAuditTrail() {
        // Given
        List<ConfigAudit> auditLogs = Arrays.asList(
            createTestAudit(TEST_CONFIG_KEY, "CREATE"),
            createTestAudit(TEST_CONFIG_KEY, "UPDATE"),
            createTestAudit(TEST_CONFIG_KEY, "UPDATE")
        );

        when(auditRepository.findByConfigKeyOrderByCreatedAtDesc(TEST_CONFIG_KEY)).thenReturn(auditLogs);

        // When
        List<ConfigAuditDto> result = configurationService.getConfigHistory(TEST_CONFIG_KEY);

        // Then
        assertThat(result).hasSize(3);
        assertThat(result).extracting(ConfigAuditDto::getAction)
            .containsExactly("CREATE", "UPDATE", "UPDATE");
    }

    // ==================== Search Tests ====================

    @Test
    void testSearchConfigurations_WithFilters_ReturnsMatchingConfigs() {
        // Given
        ConfigSearchRequest request = ConfigSearchRequest.builder()
            .service(TEST_SERVICE_NAME)
            .environment("production")
            .build();

        Configuration config1 = createTestConfiguration("config1", "value1");
        Configuration config2 = createTestConfiguration("config2", "value2");
        List<Configuration> configs = Arrays.asList(config1, config2);
        Page<Configuration> page = new PageImpl<>(configs);

        Pageable pageable = PageRequest.of(0, 10);
        when(configRepository.findAll(any(), eq(pageable))).thenReturn(page);

        // When
        Page<ConfigurationDto> result = configurationService.searchConfigurations(request, pageable);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getContent()).hasSize(2);
    }

    // ==================== Dependencies Tests ====================

    @Test
    void testGetConfigDependencies_ReturnsDependencies() {
        // Given
        Configuration config = createTestConfiguration(TEST_CONFIG_KEY, TEST_CONFIG_VALUE);
        when(configRepository.findByKey(TEST_CONFIG_KEY)).thenReturn(Optional.of(config));

        // When
        ConfigDependenciesDto result = configurationService.getConfigDependencies(TEST_CONFIG_KEY);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getConfigKey()).isEqualTo(TEST_CONFIG_KEY);
    }

    // ==================== Helper Methods ====================

    private Configuration createTestConfiguration(String key, String value) {
        Configuration config = new Configuration();
        config.setId(UUID.randomUUID());
        config.setKey(key);
        config.setValue(value);
        config.setService(TEST_SERVICE_NAME);
        config.setEnvironment("production");
        config.setDataType(ConfigType.STRING);
        config.setEncrypted(false);
        config.setActive(true);
        config.setCreatedAt(Instant.now());
        config.setUpdatedAt(Instant.now());
        config.setCreatedBy(TEST_USER_ID);
        return config;
    }

    private FeatureFlag createTestFeatureFlag(String name, boolean enabled) {
        FeatureFlag flag = new FeatureFlag();
        flag.setId(UUID.randomUUID());
        flag.setName(name);
        flag.setEnabled(enabled);
        flag.setRolloutPercentage(enabled ? 100 : 0);
        flag.setCreatedAt(Instant.now());
        flag.setUpdatedAt(Instant.now());
        flag.setCreatedBy(TEST_USER_ID);
        return flag;
    }

    private ConfigAudit createTestAudit(String configKey, String action) {
        ConfigAudit audit = new ConfigAudit();
        audit.setId(UUID.randomUUID());
        audit.setConfigKey(configKey);
        audit.setAction(action);
        audit.setUserId(TEST_USER_ID);
        audit.setCreatedAt(Instant.now());
        return audit;
    }
}
