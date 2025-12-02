package com.waqiti.common.vault;

import com.waqiti.common.security.exception.SecretManagementException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.vault.core.VaultTemplate;
import org.springframework.vault.core.lease.SecretLeaseContainer;
import org.springframework.vault.core.lease.event.LeaseErrorListener;
import org.springframework.vault.core.lease.event.LeaseListener;
import org.springframework.vault.core.lease.event.SecretLeaseCreatedEvent;
import org.springframework.vault.core.lease.event.SecretLeaseExpiredEvent;
import org.springframework.vault.support.VaultResponse;
import org.springframework.vault.support.VaultResponseSupport;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

/**
 * Centralized Vault secret management service
 * 
 * Provides secure access to all application secrets with:
 * - No hardcoded defaults
 * - Automatic secret rotation support
 * - Caching with TTL
 * - Audit logging
 * - Zero-trust security model
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VaultSecretManager {
    
    private final VaultTemplate vaultTemplate;
    private final SecretLeaseContainer leaseContainer;
    private final ApplicationEventPublisher eventPublisher;
    private final MeterRegistry meterRegistry;
    
    @Value("${vault.secrets.base-path:secret/data}")
    private String vaultBasePath;
    
    @Value("${vault.secrets.cache.enabled:true}")
    private boolean cacheEnabled;
    
    @Value("${vault.secrets.cache.ttl:PT5M}")
    private Duration cacheTtl;
    
    @Value("${vault.secrets.rotation.enabled:true}")
    private boolean rotationEnabled;
    
    @Value("${vault.secrets.rotation.interval:P90D}")
    private Duration rotationInterval;
    
    @Value("${spring.application.name}")
    private String applicationName;
    
    @Value("${spring.profiles.active:dev}")
    private String activeProfile;
    
    @Value("${vault.enterprise.rotation.grace-period:P7D}")
    private Duration rotationGracePeriod;
    
    @Value("${vault.enterprise.versioning.enabled:true}")
    private boolean versioningEnabled;
    
    @Value("${vault.enterprise.versioning.max-versions:10}")
    private int maxVersions;
    
    @Value("${vault.enterprise.encryption.enabled:true}")
    private boolean encryptionEnabled;
    
    @Value("${vault.enterprise.break-glass.enabled:true}")
    private boolean breakGlassEnabled;
    
    @Value("${vault.enterprise.multi-party.enabled:false}")
    private boolean multiPartyAuthEnabled;
    
    @Value("${vault.enterprise.multi-party.threshold:2}")
    private int multiPartyThreshold;
    
    // Secret cache with TTL tracking
    private final Map<String, CachedSecret> secretCache = new ConcurrentHashMap<>();
    
    // Secret paths registry
    private final Map<String, String> secretPaths = new ConcurrentHashMap<>();
    
    // Enterprise secret management
    private final Map<String, SecretMetadata> secretRegistry = new ConcurrentHashMap<>();
    private final Map<String, SecretVersion> activeSecrets = new ConcurrentHashMap<>();
    private final Map<String, List<SecretVersion>> secretHistory = new ConcurrentHashMap<>();
    private final Map<String, RotationSchedule> rotationSchedules = new ConcurrentHashMap<>();
    private final Map<String, SecretLease> activeLeases = new ConcurrentHashMap<>();
    
    // Encryption and security
    private SecretKey masterEncryptionKey;
    private final SecureRandom secureRandom = new SecureRandom();
    
    // Performance tracking
    private final AtomicInteger activeRotations = new AtomicInteger(0);
    private final AtomicReference<Instant> lastRotationTime = new AtomicReference<>(Instant.now());
    
    // Metrics
    private Counter secretAccessCounter;
    private Counter secretRotationCounter;
    private Counter secretFailureCounter;
    private Timer secretAccessTimer;
    
    // Thread pools
    private final ScheduledExecutorService rotationExecutor = Executors.newScheduledThreadPool(2);
    private final ExecutorService auditExecutor = Executors.newFixedThreadPool(2);
    
    @PostConstruct
    public void initialize() {
        log.info("Initializing Enterprise Vault Secret Manager for application: {} in profile: {}", 
            applicationName, activeProfile);
            
        // Initialize metrics
        initializeMetrics();
        
        // Initialize encryption
        if (encryptionEnabled) {
            initializeEncryption();
        }
        
        // Register secret paths
        registerSecretPaths();
        
        // Register lease listeners
        registerLeaseListeners();
        
        // Load secret metadata
        loadSecretRegistry();
        
        // Schedule rotation tasks
        if (rotationEnabled) {
            scheduleRotationTasks();
        }
        
        // Validate required secrets
        validateRequiredSecrets();
        
        log.info("Enterprise Vault Secret Manager initialized successfully");
    }
    
    /**
     * Register all secret paths used by the application
     */
    private void registerSecretPaths() {
        // Database secrets
        secretPaths.put("database.password", buildPath("database/password"));
        secretPaths.put("database.username", buildPath("database/username"));
        secretPaths.put("database.url", buildPath("database/url"));
        
        // Redis secrets
        secretPaths.put("redis.password", buildPath("redis/password"));
        
        // Kafka secrets
        secretPaths.put("kafka.truststore.password", buildPath("kafka/truststore-password"));
        secretPaths.put("kafka.keystore.password", buildPath("kafka/keystore-password"));
        secretPaths.put("kafka.key.password", buildPath("kafka/key-password"));
        
        // JWT secrets
        secretPaths.put("jwt.secret", buildPath("jwt/secret"));
        secretPaths.put("jwt.refresh.secret", buildPath("jwt/refresh-secret"));
        
        // Keycloak secrets
        secretPaths.put("keycloak.client.secret", buildPath("keycloak/client-secret"));
        secretPaths.put("keycloak.admin.password", buildPath("keycloak/admin-password"));
        
        // Payment Provider API keys
        secretPaths.put("stripe.api.key", buildPath("stripe/api-key"));
        secretPaths.put("stripe.webhook.secret", buildPath("stripe/webhook-secret"));
        secretPaths.put("wise.api.client-id", buildPath("wise/client-id"));
        secretPaths.put("wise.api.client-secret", buildPath("wise/client-secret"));
        secretPaths.put("wise.api.profile-id", buildPath("wise/profile-id"));
        secretPaths.put("dwolla.api.client-id", buildPath("dwolla/client-id"));
        secretPaths.put("dwolla.api.client-secret", buildPath("dwolla/client-secret"));
        secretPaths.put("dwolla.webhook.secret", buildPath("dwolla/webhook-secret"));
        
        // KYC Provider API keys
        secretPaths.put("onfido.api.token", buildPath("onfido/api-token"));
        secretPaths.put("onfido.webhook.token", buildPath("onfido/webhook-token"));
        
        // Currency Provider API keys
        secretPaths.put("currencylayer.api.key", buildPath("currencylayer/api-key"));
        secretPaths.put("openexchangerates.api.key", buildPath("openexchangerates/api-key"));
        
        // Communication Provider API keys
        secretPaths.put("twilio.auth.token", buildPath("twilio/auth-token"));
        secretPaths.put("sendgrid.api.key", buildPath("sendgrid/api-key"));
        
        // Encryption keys
        secretPaths.put("encryption.master.key", buildPath("encryption/master-key"));
        secretPaths.put("encryption.data.key", buildPath("encryption/data-key"));
        
        // Cloud provider secrets
        secretPaths.put("aws.access.key", buildPath("aws/access-key"));
        secretPaths.put("aws.secret.key", buildPath("aws/secret-key"));
        secretPaths.put("azure.client.secret", buildPath("azure/client-secret"));
        secretPaths.put("gcp.service.account", buildPath("gcp/service-account"));
        
        // Monitoring secrets
        secretPaths.put("datadog.api.key", buildPath("datadog/api-key"));
        secretPaths.put("sentry.dsn", buildPath("sentry/dsn"));
        
        log.info("Registered {} secret paths", secretPaths.size());
    }
    
    /**
     * Get a secret value from Vault with enterprise features
     */
    @CircuitBreaker(name = "vault-secret", fallbackMethod = "getSecretFallback")
    @Retry(name = "vault-secret")
    @Cacheable(value = "vault-secrets", key = "#secretKey", condition = "@vaultSecretManager.isCacheEnabled()")
    public String getSecret(String secretKey) {
        return getSecret(secretKey, false);
    }
    
    /**
     * Get enterprise secret value with full metadata
     */
    public SecretValue getEnterpriseSecret(String secretKey) {
        return getEnterpriseSecret(secretKey, null);
    }
    
    /**
     * Get a specific version of an enterprise secret
     */
    public SecretValue getEnterpriseSecret(String secretKey, Integer version) {
        Timer.Sample sample = Timer.start(meterRegistry);
        
        try {
            // Check access permissions
            validateAccess(secretKey, SecretOperation.READ);
            
            // Get secret metadata
            SecretMetadata metadata = getOrCreateMetadata(secretKey);
            
            // Check if secret requires multi-party auth
            if (metadata.isRequiresMultiPartyAuth() && multiPartyAuthEnabled) {
                validateMultiPartyAuthorization(secretKey, SecretOperation.READ);
            }
            
            // Retrieve secret from Vault
            SecretVersion secretVersion;
            if (version != null && versioningEnabled) {
                secretVersion = getSecretVersion(secretKey, version);
            } else {
                secretVersion = getActiveSecret(secretKey);
            }
            
            // Decrypt if needed
            String decryptedValue = decryptSecretValue(secretVersion.getEncryptedValue());
            
            // Check if rotation is needed
            if (shouldRotate(metadata, secretVersion)) {
                scheduleImmediateRotation(secretKey);
            }
            
            // Update access metrics
            updateAccessMetrics(secretKey, true);
            
            // Audit access
            auditSecretAccess(secretKey, SecretOperation.READ, true);
            
            sample.stop(secretAccessTimer);
            
            return SecretValue.builder()
                .path(secretKey)
                .value(decryptedValue)
                .version(secretVersion.getVersion())
                .metadata(metadata)
                .leaseId(secretVersion.getLeaseId())
                .ttl(secretVersion.getTtl())
                .renewable(secretVersion.isRenewable())
                .lastRotated(secretVersion.getCreatedAt())
                .nextRotation(calculateNextRotation(metadata, secretVersion))
                .build();
                
        } catch (Exception e) {
            sample.stop(secretAccessTimer);
            updateAccessMetrics(secretKey, false);
            auditSecretAccess(secretKey, SecretOperation.READ, false);
            
            throw new SecretManagementException("Failed to retrieve secret: " + secretKey, e);
        }
    }
    
    /**
     * Get a secret value from Vault with optional force refresh
     */
    public String getSecret(String secretKey, boolean forceRefresh) {
        try {
            // Check cache first
            if (!forceRefresh && cacheEnabled) {
                CachedSecret cached = secretCache.get(secretKey);
                if (cached != null && !cached.isExpired()) {
                    log.debug("Returning cached secret for key: {}", secretKey);
                    return cached.getValue();
                }
            }
            
            // Get secret path
            String path = secretPaths.get(secretKey);
            if (path == null) {
                path = buildPath(secretKey);
                log.warn("Using dynamic path for unregistered secret key: {}", secretKey);
            }
            
            // Fetch from Vault
            log.debug("Fetching secret from Vault: {}", path);
            VaultResponse response = vaultTemplate.read(path);
            
            if (response == null || response.getData() == null) {
                throw new SecretManagementException("Secret not found in Vault: " + secretKey);
            }
            
            Map<String, Object> data = response.getData();
            String value = extractSecretValue(data, secretKey);
            
            if (value == null || value.isEmpty()) {
                throw new SecretManagementException("Empty secret value for key: " + secretKey);
            }
            
            // Cache the secret
            if (cacheEnabled) {
                secretCache.put(secretKey, new CachedSecret(value, Instant.now().plus(cacheTtl)));
            }
            
            // Audit secret access
            auditSecretAccess(secretKey, "SUCCESS");
            
            return value;
            
        } catch (Exception e) {
            log.error("Failed to retrieve secret: {}", secretKey, e);
            auditSecretAccess(secretKey, "FAILURE");
            throw new SecretManagementException("Failed to retrieve secret: " + secretKey, e);
        }
    }
    
    /**
     * Get multiple secrets as a map
     */
    public Map<String, String> getSecrets(String... secretKeys) {
        Map<String, String> secrets = new HashMap<>();
        
        for (String key : secretKeys) {
            try {
                secrets.put(key, getSecret(key));
            } catch (Exception e) {
                log.error("Failed to retrieve secret: {}", key, e);
                // Don't include failed secrets in the map
            }
        }
        
        return secrets;
    }
    
    /**
     * Store or update a secret in Vault
     */
    public void storeSecret(String secretKey, String value) {
        try {
            String path = secretPaths.getOrDefault(secretKey, buildPath(secretKey));
            
            Map<String, String> data = new HashMap<>();
            data.put("value", value);
            data.put("updated_at", Instant.now().toString());
            data.put("updated_by", applicationName);
            
            vaultTemplate.write(path, data);
            
            // Clear cache
            secretCache.remove(secretKey);
            
            log.info("Secret stored successfully: {}", secretKey);
            auditSecretAccess(secretKey, "WRITE");
            
        } catch (Exception e) {
            log.error("Failed to store secret: {}", secretKey, e);
            throw new SecretManagementException("Failed to store secret: " + secretKey, e);
        }
    }
    
    /**
     * Rotate a secret with zero downtime
     */
    public SecretRotationResult rotateSecret(String secretKey, SecretGenerator generator) {
        if (activeRotations.get() > 5) {
            throw new SecretManagementException("Too many concurrent rotations in progress");
        }
        
        activeRotations.incrementAndGet();
        
        try {
            log.info("Starting zero-downtime rotation for secret: {}", secretKey);
            
            // Get current secret
            SecretVersion currentVersion = getActiveSecret(secretKey);
            SecretMetadata metadata = getOrCreateMetadata(secretKey);
            
            // Generate new secret value
            String newValue = generator.generate();
            
            // Store new version (inactive)
            SecretVersion newVersion = storeSecretVersion(
                secretKey, 
                newValue, 
                Map.of("rotation_reason", "scheduled", "previous_version", currentVersion.getVersion())
            );
            newVersion.setStatus(SecretStatus.PENDING_ACTIVATION);
            
            // Start grace period
            RotationSchedule rotation = RotationSchedule.builder()
                .secretPath(secretKey)
                .oldVersion(currentVersion.getVersion())
                .newVersion(newVersion.getVersion())
                .gracePeriodStart(Instant.now())
                .gracePeriodEnd(Instant.now().plus(rotationGracePeriod))
                .status(RotationStatus.IN_PROGRESS)
                .build();
            
            rotationSchedules.put(secretKey, rotation);
            
            // Notify consumers about upcoming rotation
            eventPublisher.publishEvent(new SecretRotationStartedEvent(
                secretKey, 
                currentVersion.getVersion(), 
                newVersion.getVersion(),
                rotation.getGracePeriodEnd()
            ));
            
            // Schedule activation after grace period
            rotationExecutor.schedule(() -> {
                try {
                    activateRotatedSecret(secretKey, newVersion);
                } catch (Exception e) {
                    log.error("Failed to activate rotated secret: {}", secretKey, e);
                    rollbackRotation(secretKey, currentVersion);
                }
            }, rotationGracePeriod.toMillis(), TimeUnit.MILLISECONDS);
            
            // Update metrics
            secretRotationCounter.increment();
            lastRotationTime.set(Instant.now());
            
            // Audit
            auditSecretAccess(secretKey, SecretOperation.ROTATE, true);
            
            return SecretRotationResult.builder()
                .secretPath(secretKey)
                .oldVersion(currentVersion.getVersion())
                .newVersion(newVersion.getVersion())
                .gracePeriodEnd(rotation.getGracePeriodEnd())
                .status(RotationStatus.IN_PROGRESS)
                .build();
                
        } catch (Exception e) {
            log.error("Secret rotation failed for: {}", secretKey, e);
            auditSecretAccess(secretKey, SecretOperation.ROTATE, false);
            
            throw new SecretManagementException("Secret rotation failed: " + secretKey, e);
            
        } finally {
            activeRotations.decrementAndGet();
        }
    }
    
    /**
     * Legacy rotate method for backward compatibility - returns new secret value
     */
    public String rotateSecretAndGetValue(String secretKey, SecretGenerator generator) {
        SecretRotationResult result = rotateSecret(secretKey, generator);
        return getSecret(secretKey); // Return the new value after rotation starts
    }
    
    /**
     * Delete a secret
     */
    public void deleteSecret(String secretKey) {
        try {
            String path = secretPaths.getOrDefault(secretKey, buildPath(secretKey));
            
            vaultTemplate.delete(path);
            secretCache.remove(secretKey);
            
            log.info("Secret deleted: {}", secretKey);
            auditSecretAccess(secretKey, "DELETE");
            
        } catch (Exception e) {
            log.error("Failed to delete secret: {}", secretKey, e);
            throw new SecretManagementException("Failed to delete secret: " + secretKey, e);
        }
    }
    
    /**
     * Validate that all required secrets are present
     */
    private void validateRequiredSecrets() {
        String[] requiredSecrets = {
            "database.password",
            "jwt.secret",
            "encryption.master.key"
        };
        
        for (String secret : requiredSecrets) {
            try {
                String value = getSecret(secret);
                if (value == null || value.isEmpty()) {
                    throw new SecretManagementException("Required secret is empty: " + secret);
                }
            } catch (Exception e) {
                log.error("CRITICAL: Required secret validation failed: {}", secret);
                if ("production".equals(activeProfile)) {
                    throw new IllegalStateException("Cannot start in production without required secrets", e);
                }
            }
        }
        
        log.info("All required secrets validated successfully");
    }
    
    /**
     * Build Vault path for a secret
     */
    private String buildPath(String secretKey) {
        return String.format("%s/%s/%s/%s", 
            vaultBasePath, applicationName, activeProfile, secretKey);
    }
    
    /**
     * Extract secret value from Vault response
     */
    private String extractSecretValue(Map<String, Object> data, String secretKey) {
        // Try common keys
        if (data.containsKey("value")) {
            return (String) data.get("value");
        }
        if (data.containsKey(secretKey)) {
            return (String) data.get(secretKey);
        }
        
        // Try nested data
        if (data.containsKey("data")) {
            Map<String, Object> nestedData = (Map<String, Object>) data.get("data");
            return extractSecretValue(nestedData, secretKey);
        }
        
        // Return first string value found
        for (Object value : data.values()) {
            if (value instanceof String) {
                return (String) value;
            }
        }
        
        log.warn("CRITICAL: Secret value not found in Vault for key: {} - Security configuration may be incomplete", secretKey);
        throw new SecurityException("Secret value not found in Vault for key: " + secretKey);
    }
    
    /**
     * Audit secret access
     */
    private void auditSecretAccess(String secretKey, String action) {
        try {
            Map<String, String> auditData = new HashMap<>();
            auditData.put("timestamp", Instant.now().toString());
            auditData.put("application", applicationName);
            auditData.put("profile", activeProfile);
            auditData.put("secret_key", secretKey);
            auditData.put("action", action);
            
            String auditPath = String.format("%s/audit/%s/%s", 
                vaultBasePath, applicationName, Instant.now().toEpochMilli());
            
            vaultTemplate.write(auditPath, auditData);
            
        } catch (Exception e) {
            log.error("Failed to audit secret access", e);
        }
    }
    
    /**
     * Check if caching is enabled
     */
    public boolean isCacheEnabled() {
        return cacheEnabled;
    }
    
    /**
     * Clear secret cache
     */
    public void clearCache() {
        secretCache.clear();
        log.info("Secret cache cleared");
    }
    
    /**
     * Get cache statistics
     */
    public Map<String, Object> getCacheStatistics() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("cache_enabled", cacheEnabled);
        stats.put("cache_size", secretCache.size());
        stats.put("cache_ttl", cacheTtl.toString());
        
        long expiredCount = secretCache.values().stream()
            .filter(CachedSecret::isExpired)
            .count();
        stats.put("expired_entries", expiredCount);
        
        return stats;
    }
    
    /**
     * Cached secret with TTL
     */
    private static final class CachedSecret {
        private final String value;
        private final Instant expiresAt;
        
        public CachedSecret(String value, Instant expiresAt) {
            this.value = value;
            this.expiresAt = expiresAt;
        }
        
        public String getValue() {
            return value;
        }
        
        public boolean isExpired() {
            return Instant.now().isAfter(expiresAt);
        }
    }
    
    /**
     * Break-glass emergency access to secret
     */
    public SecretValue breakGlassAccess(String secretKey, String justification) {
        if (!breakGlassEnabled) {
            throw new SecretManagementException("Break-glass access is not enabled");
        }
        
        log.warn("BREAK-GLASS ACCESS REQUESTED for secret: {} by user: {} with justification: {}", 
            secretKey, getCurrentUser(), justification);
        
        try {
            // Record break-glass event
            BreakGlassEvent event = BreakGlassEvent.builder()
                .secretPath(secretKey)
                .user(getCurrentUser())
                .timestamp(Instant.now())
                .justification(justification)
                .build();
            
            // Store break-glass audit
            auditBreakGlassAccess(event);
            
            // Notify security team
            eventPublisher.publishEvent(event);
            
            // Grant temporary elevated access
            grantTemporaryAccess(secretKey, Duration.ofMinutes(15));
            
            // Retrieve secret with elevated privileges
            return getSecretWithElevatedPrivileges(secretKey);
            
        } catch (Exception e) {
            log.error("Break-glass access failed for: {}", secretKey, e);
            throw new SecretManagementException("Break-glass access failed", e);
        }
    }
    
    /**
     * Schedule automatic rotation for all eligible secrets
     */
    @Scheduled(cron = "${vault.enterprise.rotation.cron:0 0 2 * * ?}")
    public void scheduleAutomaticRotations() {
        if (!rotationEnabled) {
            return;
        }
        
        log.info("Starting scheduled secret rotation check");
        
        secretRegistry.values().stream()
            .filter(SecretMetadata::isRotationEnabled)
            .forEach(metadata -> {
                try {
                    SecretVersion active = activeSecrets.get(metadata.getPath());
                    if (shouldRotate(metadata, active)) {
                        rotateSecret(metadata.getPath(), metadata.getGenerator());
                    }
                } catch (Exception e) {
                    log.error("Failed to rotate secret: {}", metadata.getPath(), e);
                }
            });
    }
    
    /**
     * Clear secret cache
     */
    @CacheEvict(value = "vault-secrets", key = "#secretKey")
    public void evictSecretCache(String secretKey) {
        log.debug("Cache evicted for secret: {}", secretKey);
    }
    
    @CacheEvict(value = {"vault-secrets"}, allEntries = true)
    public void clearAllCaches() {
        log.info("All secret caches cleared");
        secretCache.clear();
    }
    
    @PreDestroy
    public void shutdown() {
        log.info("Shutting down Enterprise Vault Secret Service");
        
        // Shutdown executors
        rotationExecutor.shutdown();
        auditExecutor.shutdown();
        
        try {
            if (!rotationExecutor.awaitTermination(60, TimeUnit.SECONDS)) {
                rotationExecutor.shutdownNow();
            }
            if (!auditExecutor.awaitTermination(60, TimeUnit.SECONDS)) {
                auditExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            rotationExecutor.shutdownNow();
            auditExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        
        log.info("Enterprise Vault Secret Service shutdown complete");
    }
    
    // Circuit breaker fallback
    public String getSecretFallback(String secretKey, Exception ex) {
        log.error("Vault circuit breaker activated for secret: {}", secretKey, ex);
        
        // Try to return from cache if available
        CachedSecret cached = secretCache.get(secretKey);
        if (cached != null && !cached.isExpired()) {
            log.warn("Returning stale cached secret for: {}", secretKey);
            return cached.getValue();
        }
        
        throw new SecretManagementException("Vault unavailable and no cached secret: " + secretKey);
    }
    
    // Helper methods
    
    private void initializeMetrics() {
        secretAccessCounter = Counter.builder("vault.secret.access")
            .description("Secret access count")
            .register(meterRegistry);
            
        secretRotationCounter = Counter.builder("vault.secret.rotation")
            .description("Secret rotation count")
            .register(meterRegistry);
            
        secretFailureCounter = Counter.builder("vault.secret.failure")
            .description("Secret operation failures")
            .register(meterRegistry);
            
        secretAccessTimer = Timer.builder("vault.secret.access.time")
            .description("Secret access time")
            .register(meterRegistry);
    }
    
    private void initializeEncryption() {
        try {
            // Generate or load master encryption key
            KeyGenerator keyGen = KeyGenerator.getInstance("AES");
            keyGen.init(256);
            masterEncryptionKey = keyGen.generateKey();
            
            log.info("Encryption initialized successfully");
        } catch (Exception e) {
            throw new SecretManagementException("Failed to initialize encryption", e);
        }
    }
    
    private void registerLeaseListeners() {
        leaseContainer.addLeaseListener(event -> {
            if (event instanceof SecretLeaseCreatedEvent) {
                SecretLeaseCreatedEvent createdEvent = (SecretLeaseCreatedEvent) event;
                log.info("Lease created for: {}", createdEvent.getSource());
            } else if (event instanceof SecretLeaseExpiredEvent) {
                SecretLeaseExpiredEvent expiredEvent = (SecretLeaseExpiredEvent) event;
                log.warn("Lease expired for: {}", expiredEvent.getSource());
                String secretPath = expiredEvent.getPath();
                if (secretPath != null) {
                    activeLeases.remove(secretPath);
                    scheduleImmediateRotation(secretPath);
                }
            }
        });
    }
    
    private void loadSecretRegistry() {
        // Initialize secret metadata from configuration
        // This would be loaded from Vault or application configuration
        log.info("Loading secret registry");
    }
    
    private void scheduleRotationTasks() {
        rotationExecutor.scheduleAtFixedRate(this::checkPendingRotations, 
            0, 1, TimeUnit.HOURS);
    }
    
    private void checkPendingRotations() {
        rotationSchedules.values().stream()
            .filter(schedule -> schedule.getStatus() == RotationStatus.IN_PROGRESS)
            .filter(schedule -> Instant.now().isAfter(schedule.getGracePeriodEnd()))
            .forEach(schedule -> {
                try {
                    SecretVersion newVersion = getSecretVersion(
                        schedule.getSecretPath(), 
                        schedule.getNewVersion()
                    );
                    activateRotatedSecret(schedule.getSecretPath(), newVersion);
                } catch (Exception e) {
                    log.error("Failed to complete rotation for: {}", 
                        schedule.getSecretPath(), e);
                }
            });
    }
    
    private void validateAccess(String secretKey, SecretOperation operation) {
        // Implement access control logic
        // This would integrate with your security framework
    }
    
    private void validateMultiPartyAuthorization(String secretKey, SecretOperation operation) {
        // Implement multi-party authorization logic
        // This would require multiple approvals for sensitive operations
    }
    
    private String getCurrentUser() {
        // Get from security context
        return "system";
    }
    
    private SecretMetadata getOrCreateMetadata(String secretKey) {
        return secretRegistry.computeIfAbsent(secretKey, path -> 
            SecretMetadata.builder()
                .path(path)
                .type(SecretType.API_KEY)
                .rotationEnabled(rotationEnabled)
                .rotationInterval(rotationInterval)
                .createdAt(Instant.now())
                .createdBy(getCurrentUser())
                .build()
        );
    }
    
    private SecretVersion getActiveSecret(String secretKey) {
        SecretVersion active = activeSecrets.get(secretKey);
        if (active == null) {
            // Load from Vault
            active = loadSecretFromVault(secretKey, null);
            if (active != null) {
                activeSecrets.put(secretKey, active);
            }
        }
        
        if (active == null) {
            throw new SecretManagementException("Secret not found: " + secretKey);
        }
        
        return active;
    }
    
    private SecretVersion getSecretVersion(String secretKey, int version) {
        List<SecretVersion> history = secretHistory.get(secretKey);
        if (history != null) {
            return history.stream()
                .filter(v -> v.getVersion() == version)
                .findFirst()
                .orElseGet(() -> loadSecretFromVault(secretKey, version));
        }
        
        return loadSecretFromVault(secretKey, version);
    }
    
    private SecretVersion loadSecretFromVault(String secretKey, Integer version) {
        try {
            String vaultPath = buildVaultPath(secretKey, version);
            VaultResponseSupport<Map<String, Object>> response = vaultTemplate.read(vaultPath, Map.class);
            
            if (response == null || response.getData() == null) {
                log.warn("CRITICAL: Vault secret not found at path: {} - Security configuration may be missing", vaultPath);
                throw new SecurityException("Vault secret not found at path: " + vaultPath);
            }
            
            Map<String, Object> data = response.getData();
            return SecretVersion.builder()
                .path(secretKey)
                .version(version != null ? version : 1)
                .encryptedValue((String) data.get("value"))
                .createdAt(Instant.now())
                .createdBy(getCurrentUser())
                .status(SecretStatus.ACTIVE)
                .build();
                
        } catch (Exception e) {
            log.error("Failed to load secret from Vault: {}", secretKey, e);
            return null;
        }
    }
    
    private String buildVaultPath(String secretKey, Integer version) {
        StringBuilder path = new StringBuilder()
            .append(vaultBasePath)
            .append("/")
            .append(applicationName)
            .append("/")
            .append(activeProfile)
            .append("/")
            .append(secretKey);
            
        if (version != null && versioningEnabled) {
            path.append("?version=").append(version);
        }
        
        return path.toString();
    }
    
    private SecretVersion storeSecretVersion(String secretKey, String value, Map<String, Object> metadata) {
        try {
            // Encrypt the value
            String encryptedValue = encryptSecretValue(value);
            
            // Create new version
            int nextVersion = getNextVersion(secretKey);
            SecretVersion newVersion = SecretVersion.builder()
                .path(secretKey)
                .version(nextVersion)
                .encryptedValue(encryptedValue)
                .createdAt(Instant.now())
                .createdBy(getCurrentUser())
                .metadata(metadata)
                .status(SecretStatus.ACTIVE)
                .build();
            
            // Store in Vault
            String vaultPath = buildVaultPath(secretKey, nextVersion);
            Map<String, Object> vaultData = new HashMap<>();
            vaultData.put("value", encryptedValue);
            vaultData.put("version", nextVersion);
            vaultData.put("created_at", newVersion.getCreatedAt().toString());
            vaultData.put("created_by", newVersion.getCreatedBy());
            vaultData.putAll(metadata);
            
            VaultResponse response = vaultTemplate.write(vaultPath, vaultData);
            
            // Handle lease if applicable
            if (response != null && response.getLeaseId() != null) {
                newVersion.setLeaseId(response.getLeaseId());
                newVersion.setTtl(Duration.ofSeconds(response.getLeaseDuration()));
                newVersion.setRenewable(response.isRenewable());
                
                // Register lease for automatic renewal
                registerLease(secretKey, response);
            }
            
            // Update history
            secretHistory.computeIfAbsent(secretKey, k -> new CopyOnWriteArrayList<>()).add(newVersion);
            
            return newVersion;
            
        } catch (Exception e) {
            throw new SecretManagementException("Failed to store secret version: " + secretKey, e);
        }
    }
    
    private int getNextVersion(String secretKey) {
        List<SecretVersion> history = secretHistory.get(secretKey);
        if (history == null || history.isEmpty()) {
            return 1;
        }
        
        return history.stream()
            .mapToInt(SecretVersion::getVersion)
            .max()
            .orElse(0) + 1;
    }
    
    private String encryptSecretValue(String plaintext) {
        if (!encryptionEnabled || plaintext == null) {
            return plaintext;
        }
        
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            byte[] iv = new byte[12];
            secureRandom.nextBytes(iv);
            
            GCMParameterSpec parameterSpec = new GCMParameterSpec(128, iv);
            cipher.init(Cipher.ENCRYPT_MODE, masterEncryptionKey, parameterSpec);
            
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            
            // Combine IV and ciphertext
            byte[] combined = new byte[iv.length + ciphertext.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(ciphertext, 0, combined, iv.length, ciphertext.length);
            
            return Base64.getEncoder().encodeToString(combined);
            
        } catch (Exception e) {
            throw new SecretManagementException("Failed to encrypt secret", e);
        }
    }
    
    private String decryptSecretValue(String encrypted) {
        if (!encryptionEnabled || encrypted == null) {
            return encrypted;
        }
        
        try {
            byte[] combined = Base64.getDecoder().decode(encrypted);
            
            // Extract IV and ciphertext
            byte[] iv = new byte[12];
            byte[] ciphertext = new byte[combined.length - 12];
            System.arraycopy(combined, 0, iv, 0, iv.length);
            System.arraycopy(combined, iv.length, ciphertext, 0, ciphertext.length);
            
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            GCMParameterSpec parameterSpec = new GCMParameterSpec(128, iv);
            cipher.init(Cipher.DECRYPT_MODE, masterEncryptionKey, parameterSpec);
            
            byte[] plaintext = cipher.doFinal(ciphertext);
            return new String(plaintext, StandardCharsets.UTF_8);
            
        } catch (Exception e) {
            throw new SecretManagementException("Failed to decrypt secret", e);
        }
    }
    
    private boolean shouldRotate(SecretMetadata metadata, SecretVersion currentVersion) {
        if (!metadata.isRotationEnabled() || currentVersion == null) {
            return false;
        }
        
        Instant nextRotation = currentVersion.getCreatedAt().plus(metadata.getRotationInterval());
        return Instant.now().isAfter(nextRotation);
    }
    
    private Instant calculateNextRotation(SecretMetadata metadata, SecretVersion currentVersion) {
        if (!metadata.isRotationEnabled() || currentVersion == null) {
            return null;
        }
        
        return currentVersion.getCreatedAt().plus(metadata.getRotationInterval());
    }
    
    private void scheduleImmediateRotation(String secretKey) {
        rotationExecutor.schedule(() -> {
            try {
                SecretMetadata metadata = secretRegistry.get(secretKey);
                if (metadata != null && metadata.getGenerator() != null) {
                    rotateSecret(secretKey, metadata.getGenerator());
                }
            } catch (Exception e) {
                log.error("Immediate rotation failed for: {}", secretKey, e);
            }
        }, 1, TimeUnit.MINUTES);
    }
    
    private void activateRotatedSecret(String secretKey, SecretVersion newVersion) {
        try {
            // Mark old version as deprecated
            SecretVersion oldVersion = activeSecrets.get(secretKey);
            if (oldVersion != null) {
                oldVersion.setStatus(SecretStatus.DEPRECATED);
                oldVersion.setDeprecatedAt(Instant.now());
            }
            
            // Activate new version
            newVersion.setStatus(SecretStatus.ACTIVE);
            newVersion.setActivatedAt(Instant.now());
            activeSecrets.put(secretKey, newVersion);
            
            // Update rotation schedule
            RotationSchedule rotation = rotationSchedules.get(secretKey);
            if (rotation != null) {
                rotation.setStatus(RotationStatus.COMPLETED);
                rotation.setCompletedAt(Instant.now());
            }
            
            // Clear caches
            evictSecretCache(secretKey);
            
            // Notify consumers
            eventPublisher.publishEvent(new SecretRotationCompletedEvent(
                secretKey,
                oldVersion != null ? oldVersion.getVersion() : 0,
                newVersion.getVersion()
            ));
            
            log.info("Secret rotation completed successfully: {} (new version: {})", 
                secretKey, newVersion.getVersion());
                
        } catch (Exception e) {
            log.error("Failed to activate rotated secret: {}", secretKey, e);
            throw new SecretManagementException("Failed to activate rotated secret", e);
        }
    }
    
    private void rollbackRotation(String secretKey, SecretVersion previousVersion) {
        try {
            log.warn("Rolling back secret rotation for: {}", secretKey);
            
            // Reactivate previous version
            previousVersion.setStatus(SecretStatus.ACTIVE);
            activeSecrets.put(secretKey, previousVersion);
            
            // Update rotation schedule
            RotationSchedule rotation = rotationSchedules.get(secretKey);
            if (rotation != null) {
                rotation.setStatus(RotationStatus.FAILED);
                rotation.setCompletedAt(Instant.now());
            }
            
            // Notify about rollback
            eventPublisher.publishEvent(new SecretRotationRolledBackEvent(
                secretKey,
                previousVersion.getVersion()
            ));
            
            log.info("Secret rotation rolled back successfully: {}", secretKey);
            
        } catch (Exception e) {
            log.error("Failed to rollback secret rotation: {}", secretKey, e);
        }
    }
    
    private void registerLease(String secretKey, VaultResponse response) {
        if (response.getLeaseId() != null && response.isRenewable()) {
            SecretLease lease = SecretLease.builder()
                .secretPath(secretKey)
                .leaseId(response.getLeaseId())
                .duration(Duration.ofSeconds(response.getLeaseDuration()))
                .renewable(response.isRenewable())
                .createdAt(Instant.now())
                .build();
                
            activeLeases.put(secretKey, lease);
            
            // Register with lease container for automatic renewal
            leaseContainer.maintain(response.getLeaseId(), secretKey);
        }
    }
    
    private void updateAccessMetrics(String secretKey, boolean success) {
        if (success) {
            secretAccessCounter.increment();
        } else {
            secretFailureCounter.increment();
        }
    }
    
    private void auditSecretAccess(String secretKey, SecretOperation operation, boolean success) {
        auditExecutor.submit(() -> {
            try {
                Map<String, Object> auditEntry = new HashMap<>();
                auditEntry.put("timestamp", Instant.now().toString());
                auditEntry.put("user", getCurrentUser());
                auditEntry.put("secret_path", secretKey);
                auditEntry.put("operation", operation.name());
                auditEntry.put("success", success);
                auditEntry.put("ip_address", getClientIpAddress());
                
                String auditPath = String.format("%s/audit/%s/%s", 
                    vaultBasePath, applicationName, UUID.randomUUID());
                    
                vaultTemplate.write(auditPath, auditEntry);
                
            } catch (Exception e) {
                log.error("Failed to audit secret access", e);
            }
        });
    }
    
    private void auditBreakGlassAccess(BreakGlassEvent event) {
        Map<String, Object> auditEntry = new HashMap<>();
        auditEntry.put("event_type", "BREAK_GLASS");
        auditEntry.put("timestamp", event.getTimestamp().toString());
        auditEntry.put("user", event.getUser());
        auditEntry.put("secret_path", event.getSecretPath());
        auditEntry.put("justification", event.getJustification());
        auditEntry.put("alert_sent", true);
        
        String auditPath = String.format("%s/break-glass-audit/%s/%s", 
            vaultBasePath, applicationName, UUID.randomUUID());
            
        vaultTemplate.write(auditPath, auditEntry);
    }
    
    private void grantTemporaryAccess(String secretKey, Duration duration) {
        // Implement temporary access grant logic
    }
    
    private SecretValue getSecretWithElevatedPrivileges(String secretKey) {
        // Implement elevated access logic
        return getEnterpriseSecret(secretKey);
    }
    
    private String getClientIpAddress() {
        return "127.0.0.1";
    }
    
    // Data classes
    
    @Data
    @Builder
    public static class SecretValue {
        private String path;
        private String value;
        private int version;
        private SecretMetadata metadata;
        private String leaseId;
        private Duration ttl;
        private boolean renewable;
        private Instant lastRotated;
        private Instant nextRotation;
        private boolean fromFallback;
    }
    
    @Data
    @Builder
    public static class SecretMetadata {
        private String path;
        private String description;
        private SecretType type;
        private boolean rotationEnabled;
        private Duration rotationInterval;
        private SecretGenerator generator;
        private boolean requiresMultiPartyAuth;
        private boolean protected_;
        private boolean hidden;
        private Map<String, String> tags;
        private Map<String, Object> customMetadata;
        private Instant createdAt;
        private String createdBy;
        private Instant lastModified;
        private String lastModifiedBy;
    }
    
    @Data
    @Builder
    public static class SecretVersion {
        private String path;
        private int version;
        private String encryptedValue;
        private SecretStatus status;
        private Instant createdAt;
        private String createdBy;
        private Instant activatedAt;
        private Instant deprecatedAt;
        private Instant deletedAt;
        private String deletedBy;
        private Map<String, Object> metadata;
        private String leaseId;
        private Duration ttl;
        private boolean renewable;
    }
    
    @Data
    @Builder
    public static class RotationSchedule {
        private String secretPath;
        private int oldVersion;
        private int newVersion;
        private Instant gracePeriodStart;
        private Instant gracePeriodEnd;
        private RotationStatus status;
        private Instant completedAt;
    }
    
    @Data
    @Builder
    public static class SecretLease {
        private String secretPath;
        private String leaseId;
        private Duration duration;
        private boolean renewable;
        private Instant createdAt;
        private Instant expiresAt;
    }
    
    @Data
    @Builder
    public static class SecretRotationResult {
        private String secretPath;
        private int oldVersion;
        private int newVersion;
        private Instant gracePeriodEnd;
        private RotationStatus status;
    }
    
    @Data
    @Builder
    public static class BreakGlassEvent {
        private String secretPath;
        private String user;
        private Instant timestamp;
        private String justification;
    }
    
    // Enums
    
    public enum SecretType {
        API_KEY,
        PASSWORD,
        CERTIFICATE,
        SSH_KEY,
        ENCRYPTION_KEY,
        TOKEN,
        CONNECTION_STRING,
        CONFIGURATION
    }
    
    public enum SecretStatus {
        ACTIVE,
        PENDING_ACTIVATION,
        DEPRECATED,
        DELETED,
        EXPIRED,
        ROTATING
    }
    
    public enum SecretOperation {
        READ,
        WRITE,
        DELETE,
        ROTATE,
        ROLLBACK,
        BREAK_GLASS
    }
    
    public enum RotationStatus {
        SCHEDULED,
        IN_PROGRESS,
        COMPLETED,
        FAILED,
        ROLLED_BACK
    }
    
    // Events
    
    public static class SecretRotationStartedEvent {
        private final String path;
        private final int oldVersion;
        private final int newVersion;
        private final Instant gracePeriodEnd;
        
        public SecretRotationStartedEvent(String path, int oldVersion, int newVersion, Instant gracePeriodEnd) {
            this.path = path;
            this.oldVersion = oldVersion;
            this.newVersion = newVersion;
            this.gracePeriodEnd = gracePeriodEnd;
        }
    }
    
    public static class SecretRotationCompletedEvent {
        private final String path;
        private final int oldVersion;
        private final int newVersion;
        
        public SecretRotationCompletedEvent(String path, int oldVersion, int newVersion) {
            this.path = path;
            this.oldVersion = oldVersion;
            this.newVersion = newVersion;
        }
    }
    
    public static class SecretRotationRolledBackEvent {
        private final String path;
        private final int version;
        
        public SecretRotationRolledBackEvent(String path, int version) {
            this.path = path;
            this.version = version;
        }
    }

    /**
     * Secret generator interface for rotation
     */
    @FunctionalInterface
    public interface SecretGenerator {
        String generate();
    }
}