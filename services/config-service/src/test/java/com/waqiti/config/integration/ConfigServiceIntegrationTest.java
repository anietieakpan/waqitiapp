package com.waqiti.config.integration;

import com.waqiti.config.dto.*;
import com.waqiti.config.service.ConfigurationService;
import com.waqiti.config.repository.ConfigurationRepository;
import com.waqiti.config.repository.FeatureFlagRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.VaultContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration tests using TestContainers for PostgreSQL, Kafka, and Vault
 * Tests end-to-end scenarios with real infrastructure dependencies
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "config.encryption.enabled=true",
        "config.cache.enabled=true"
    }
)
@Testcontainers
class ConfigServiceIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
        DockerImageName.parse("postgres:15-alpine")
    )
    .withDatabaseName("configdb_test")
    .withUsername("test")
    .withPassword("test");

    @Container
    static KafkaContainer kafka = new KafkaContainer(
        DockerImageName.parse("confluentinc/cp-kafka:7.5.0")
    );

    @Container
    static VaultContainer<?> vault = new VaultContainer<>(
        DockerImageName.parse("vault:1.13")
    )
    .withVaultToken("test-token")
    .withSecretInVault(
        "secret/config",
        "encryption-key=dGVzdC1lbmNyeXB0aW9uLWtleS0zMi1ieXRlcy1sb25nISEh"
    );

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        // PostgreSQL
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");

        // Kafka
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);

        // Vault
        registry.add("spring.cloud.vault.host", vault::getHost);
        registry.add("spring.cloud.vault.port", () -> vault.getMappedPort(8200));
        registry.add("spring.cloud.vault.scheme", () -> "http");
        registry.add("spring.cloud.vault.token", () -> "test-token");
        registry.add("spring.cloud.vault.kv.enabled", () -> "true");

        // Disable security for testing
        registry.add("spring.security.enabled", () -> "false");
        registry.add("keycloak.enabled", () -> "false");
    }

    @Autowired
    private ConfigurationService configurationService;

    @Autowired
    private ConfigurationRepository configurationRepository;

    @Autowired
    private FeatureFlagRepository featureFlagRepository;

    @BeforeEach
    void setUp() {
        // Clean up database before each test
        configurationRepository.deleteAll();
        featureFlagRepository.deleteAll();
    }

    // ==================== Configuration CRUD Integration Tests ====================

    @Test
    void testCreateAndRetrieveConfiguration_EndToEnd() {
        // Given
        CreateConfigRequest request = CreateConfigRequest.builder()
            .key("app.database.url")
            .value("jdbc:postgresql://localhost:5432/testdb")
            .dataType("STRING")
            .service("payment-service")
            .environment("production")
            .encrypted(false)
            .description("Database connection URL")
            .build();

        // When - Create configuration
        ConfigValue createdConfig = configurationService.createConfig(request);

        // Then - Configuration is created
        assertThat(createdConfig).isNotNull();
        assertThat(createdConfig.getKey()).isEqualTo("app.database.url");
        assertThat(createdConfig.getValue()).isEqualTo("jdbc:postgresql://localhost:5432/testdb");

        // When - Retrieve configuration
        ConfigValue retrievedConfig = configurationService.getConfig("app.database.url");

        // Then - Configuration is retrieved correctly
        assertThat(retrievedConfig).isNotNull();
        assertThat(retrievedConfig.getKey()).isEqualTo("app.database.url");
        assertThat(retrievedConfig.getValue()).isEqualTo("jdbc:postgresql://localhost:5432/testdb");

        // Verify persistence
        assertThat(configurationRepository.count()).isEqualTo(1);
    }

    @Test
    void testUpdateConfiguration_PersistsChanges() {
        // Given - Create initial configuration
        CreateConfigRequest createRequest = CreateConfigRequest.builder()
            .key("app.timeout")
            .value("30")
            .dataType("INTEGER")
            .build();

        configurationService.createConfig(createRequest);

        // When - Update configuration
        UpdateConfigRequest updateRequest = UpdateConfigRequest.builder()
            .value("60")
            .description("Updated timeout value")
            .build();

        ConfigValue updatedConfig = configurationService.updateConfig("app.timeout", updateRequest);

        // Then - Changes are persisted
        assertThat(updatedConfig.getValue()).isEqualTo("60");

        ConfigValue retrievedConfig = configurationService.getConfig("app.timeout");
        assertThat(retrievedConfig.getValue()).isEqualTo("60");
    }

    @Test
    void testDeleteConfiguration_RemovesFromDatabase() {
        // Given - Create configuration
        CreateConfigRequest createRequest = CreateConfigRequest.builder()
            .key("temp.config")
            .value("temporary")
            .dataType("STRING")
            .build();

        configurationService.createConfig(createRequest);
        assertThat(configurationRepository.count()).isEqualTo(1);

        // When - Delete configuration
        configurationService.deleteConfig("temp.config");

        // Then - Configuration is removed
        assertThat(configurationRepository.count()).isEqualTo(0);

        ConfigValue retrievedConfig = configurationService.getConfig("temp.config", "default");
        assertThat(retrievedConfig.getValue()).isEqualTo("default");
    }

    // ==================== Encryption Integration Tests ====================

    @Test
    void testEncryptedConfiguration_VaultIntegration() {
        // Given - Create encrypted configuration
        CreateConfigRequest request = CreateConfigRequest.builder()
            .key("db.password")
            .value("super-secret-password-123")
            .dataType("STRING")
            .encrypted(true)
            .build();

        // When - Create configuration (should encrypt with Vault)
        ConfigValue createdConfig = configurationService.createConfig(request);

        // Then - Value is encrypted
        assertThat(createdConfig.getValue()).startsWith("ENC(");

        // When - Retrieve configuration (should decrypt with Vault)
        ConfigValue retrievedConfig = configurationService.getConfig("db.password");

        // Then - Value is decrypted
        assertThat(retrievedConfig.getValue()).isEqualTo("super-secret-password-123");
        assertThat(retrievedConfig.getValue()).doesNotContain("ENC(");
    }

    @Test
    void testBatchDecryption_MultipleEncryptedConfigs() {
        // Given - Create multiple encrypted configurations
        for (int i = 1; i <= 5; i++) {
            CreateConfigRequest request = CreateConfigRequest.builder()
                .key("secret.key." + i)
                .value("secret-value-" + i)
                .dataType("STRING")
                .service("test-service")
                .encrypted(true)
                .build();

            configurationService.createConfig(request);
        }

        // When - Retrieve service configuration (uses batch decryption)
        ServiceConfigDto serviceConfig = configurationService.getServiceConfig("test-service");

        // Then - All values are decrypted
        assertThat(serviceConfig.getConfigurations()).hasSize(5);

        for (int i = 1; i <= 5; i++) {
            ConfigValue configValue = serviceConfig.getConfigurations().get("secret.key." + i);
            assertThat(configValue).isNotNull();
            assertThat(configValue.getValue()).isEqualTo("secret-value-" + i);
            assertThat(configValue.getValue()).doesNotContain("ENC(");
        }
    }

    // ==================== Feature Flag Integration Tests ====================

    @Test
    void testCreateAndEvaluateFeatureFlag_EndToEnd() {
        // Given - Create feature flag
        CreateFeatureFlagRequest request = CreateFeatureFlagRequest.builder()
            .name("new-checkout-flow")
            .description("New checkout flow feature")
            .enabled(true)
            .rolloutPercentage(100)
            .build();

        // When - Create feature flag
        FeatureFlagDto createdFlag = configurationService.createFeatureFlag(request);

        // Then - Feature flag is created
        assertThat(createdFlag).isNotNull();
        assertThat(createdFlag.getName()).isEqualTo("new-checkout-flow");
        assertThat(createdFlag.isEnabled()).isTrue();

        // When - Evaluate feature flag
        boolean isEnabled = configurationService.isFeatureEnabled(
            "new-checkout-flow",
            "user-123",
            Map.of()
        );

        // Then - Feature flag is enabled
        assertThat(isEnabled).isTrue();

        // Verify persistence
        assertThat(featureFlagRepository.count()).isEqualTo(1);
    }

    @Test
    void testUpdateFeatureFlag_PersistsChanges() {
        // Given - Create feature flag
        CreateFeatureFlagRequest createRequest = CreateFeatureFlagRequest.builder()
            .name("feature-x")
            .enabled(false)
            .rolloutPercentage(0)
            .build();

        configurationService.createFeatureFlag(createRequest);

        // When - Update feature flag
        UpdateFeatureFlagRequest updateRequest = UpdateFeatureFlagRequest.builder()
            .enabled(true)
            .rolloutPercentage(100)
            .build();

        FeatureFlagDto updatedFlag = configurationService.updateFeatureFlag("feature-x", updateRequest);

        // Then - Changes are persisted
        assertThat(updatedFlag.isEnabled()).isTrue();
        assertThat(updatedFlag.getRolloutPercentage()).isEqualTo(100);

        boolean isEnabled = configurationService.isFeatureEnabled("feature-x", "user-123", Map.of());
        assertThat(isEnabled).isTrue();
    }

    @Test
    void testFeatureFlagRollout_PercentageBasedTargeting() {
        // Given - Create feature flag with 50% rollout
        CreateFeatureFlagRequest request = CreateFeatureFlagRequest.builder()
            .name("gradual-rollout")
            .enabled(true)
            .rolloutPercentage(50)
            .build();

        configurationService.createFeatureFlag(request);

        // When - Evaluate for multiple users
        int enabledCount = 0;
        int totalUsers = 1000;

        for (int i = 0; i < totalUsers; i++) {
            boolean isEnabled = configurationService.isFeatureEnabled(
                "gradual-rollout",
                "user-" + i,
                Map.of()
            );
            if (isEnabled) {
                enabledCount++;
            }
        }

        // Then - Approximately 50% of users should have the feature enabled
        double percentage = (enabledCount * 100.0) / totalUsers;
        assertThat(percentage).isBetween(45.0, 55.0); // Allow 5% margin of error
    }

    // ==================== Bulk Operations Integration Tests ====================

    @Test
    void testBulkUpdateConfigurations_PersistsAllChanges() {
        // Given - Create multiple configurations
        for (int i = 1; i <= 3; i++) {
            CreateConfigRequest request = CreateConfigRequest.builder()
                .key("bulk.config." + i)
                .value("value-" + i)
                .dataType("STRING")
                .build();

            configurationService.createConfig(request);
        }

        // When - Bulk update configurations
        List<BulkConfigUpdate> updates = Arrays.asList(
            BulkConfigUpdate.builder()
                .key("bulk.config.1")
                .value("updated-1")
                .build(),
            BulkConfigUpdate.builder()
                .key("bulk.config.2")
                .value("updated-2")
                .build(),
            BulkConfigUpdate.builder()
                .key("bulk.config.3")
                .value("updated-3")
                .build()
        );

        BulkUpdateResultDto result = configurationService.bulkUpdateConfigs(updates);

        // Then - All updates are successful
        assertThat(result.getSuccessCount()).isEqualTo(3);
        assertThat(result.getFailureCount()).isEqualTo(0);

        // Verify persisted values
        assertThat(configurationService.getConfig("bulk.config.1").getValue()).isEqualTo("updated-1");
        assertThat(configurationService.getConfig("bulk.config.2").getValue()).isEqualTo("updated-2");
        assertThat(configurationService.getConfig("bulk.config.3").getValue()).isEqualTo("updated-3");
    }

    // ==================== Caching Integration Tests ====================

    @Test
    void testConfigurationCaching_ReducesDatabaseQueries() {
        // Given - Create configuration
        CreateConfigRequest request = CreateConfigRequest.builder()
            .key("cached.config")
            .value("cached-value")
            .dataType("STRING")
            .build();

        configurationService.createConfig(request);

        // When - Retrieve configuration multiple times
        ConfigValue config1 = configurationService.getConfig("cached.config");
        ConfigValue config2 = configurationService.getConfig("cached.config");
        ConfigValue config3 = configurationService.getConfig("cached.config");

        // Then - All retrievals return the same value
        assertThat(config1.getValue()).isEqualTo("cached-value");
        assertThat(config2.getValue()).isEqualTo("cached-value");
        assertThat(config3.getValue()).isEqualTo("cached-value");

        // Note: In a real scenario, you would verify database query counts
        // using a query counter or metrics
    }

    // ==================== Validation Integration Tests ====================

    @Test
    void testConfigurationValidation_EnforcesDataTypes() {
        // Given - Invalid integer value
        ValidateConfigRequest request = ValidateConfigRequest.builder()
            .key("app.port")
            .value("not-a-number")
            .dataType("INTEGER")
            .build();

        // When - Validate configuration
        ValidationResultDto result = configurationService.validateConfig(request);

        // Then - Validation fails
        assertThat(result.isValid()).isFalse();
        assertThat(result.getErrors()).isNotEmpty();
    }

    @Test
    void testConfigurationValidation_ValidDataTypes() {
        // Given - Valid integer value
        ValidateConfigRequest request = ValidateConfigRequest.builder()
            .key("app.port")
            .value("8080")
            .dataType("INTEGER")
            .build();

        // When - Validate configuration
        ValidationResultDto result = configurationService.validateConfig(request);

        // Then - Validation succeeds
        assertThat(result.isValid()).isTrue();
        assertThat(result.getErrors()).isEmpty();
    }

    // ==================== Service Configuration Integration Tests ====================

    @Test
    void testGetServiceConfiguration_ReturnsAllServiceConfigs() {
        // Given - Create configurations for specific service
        CreateConfigRequest request1 = CreateConfigRequest.builder()
            .key("service.config.1")
            .value("value-1")
            .service("my-service")
            .dataType("STRING")
            .build();

        CreateConfigRequest request2 = CreateConfigRequest.builder()
            .key("service.config.2")
            .value("value-2")
            .service("my-service")
            .dataType("STRING")
            .build();

        CreateConfigRequest request3 = CreateConfigRequest.builder()
            .key("other.config")
            .value("value-3")
            .service("other-service")
            .dataType("STRING")
            .build();

        configurationService.createConfig(request1);
        configurationService.createConfig(request2);
        configurationService.createConfig(request3);

        // When - Retrieve service configuration
        ServiceConfigDto serviceConfig = configurationService.getServiceConfig("my-service");

        // Then - Only service-specific configurations are returned
        assertThat(serviceConfig.getServiceName()).isEqualTo("my-service");
        assertThat(serviceConfig.getConfigurations()).hasSize(2);
        assertThat(serviceConfig.getConfigurations()).containsKeys("service.config.1", "service.config.2");
        assertThat(serviceConfig.getConfigurations()).doesNotContainKey("other.config");
    }

    // ==================== Metrics Integration Tests ====================

    @Test
    void testGetConfigMetrics_ReturnsAccurateStatistics() {
        // Given - Create configurations and feature flags
        for (int i = 1; i <= 10; i++) {
            CreateConfigRequest configRequest = CreateConfigRequest.builder()
                .key("config." + i)
                .value("value-" + i)
                .dataType("STRING")
                .build();
            configurationService.createConfig(configRequest);
        }

        for (int i = 1; i <= 5; i++) {
            CreateFeatureFlagRequest flagRequest = CreateFeatureFlagRequest.builder()
                .name("flag-" + i)
                .enabled(true)
                .build();
            configurationService.createFeatureFlag(flagRequest);
        }

        // When - Get metrics
        ConfigMetricsDto metrics = configurationService.getConfigMetrics();

        // Then - Metrics reflect actual counts
        assertThat(metrics.getTotalConfigs()).isEqualTo(10);
        assertThat(metrics.getTotalFeatureFlags()).isEqualTo(5);
        assertThat(metrics.getCacheSize()).isGreaterThanOrEqualTo(0);
    }
}
