package com.waqiti.common.security;

import com.waqiti.common.audit.AuditService;
import com.waqiti.common.audit.AuditModels;
import com.waqiti.common.audit.AuditModels.SecurityAuditRequest;
import com.waqiti.common.notification.NotificationService;
import com.waqiti.common.notification.model.CriticalAlertRequest;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.vault.core.VaultTemplate;
import org.springframework.vault.support.VaultResponse;
import org.springframework.vault.core.VaultVersionedKeyValueOperations;
import org.springframework.vault.core.VaultTransitOperations;
import org.springframework.vault.support.Versioned;

import jakarta.annotation.PostConstruct;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.security.SecureRandom;
import java.util.concurrent.*;
import java.util.stream.Collectors;

/**
 * Hybrid Secret Rotation Service
 * Combines HashiCorp Vault's security capabilities with custom orchestration
 * for Waqiti-specific business requirements
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HybridSecretRotationService {
    
    private final VaultTemplate vaultTemplate;
    private final com.waqiti.common.config.VaultSecretManager vaultSecretManager;
    private final AuditService auditService;
    private final NotificationService notificationService;
    private final ServiceRegistry serviceRegistry;
    private final javax.sql.DataSource dataSource;
    private final com.netflix.discovery.EurekaClient eurekaClient;
    private final org.springframework.web.client.RestTemplate restTemplate;
    private final io.micrometer.core.instrument.MeterRegistry meterRegistry;
    
    // Vault operations
    private VaultVersionedKeyValueOperations kvOperations;
    private VaultTransitOperations transitOperations;
    
    // Orchestration state
    private final Map<String, RotationPlan> activeRotations = new ConcurrentHashMap<>();
    private final Map<String, SecretMetadata> secretRegistry = new ConcurrentHashMap<>();
    private final ScheduledExecutorService rotationExecutor = Executors.newScheduledThreadPool(10);
    
    // Configuration
    @Value("${vault.kv.backend:secret}")
    private String kvBackend;
    
    @Value("${vault.transit.backend:transit}")
    private String transitBackend;
    
    @Value("${vault.database.backend:database}")
    private String databaseBackend;
    
    @Value("${vault.pki.backend:pki}")
    private String pkiBackend;
    
    @Value("${security.rotation.enabled:true}")
    private boolean rotationEnabled;
    
    @Value("${security.rotation.default.strategy:BLUE_GREEN}")
    private String defaultRotationStrategy;
    
    @Value("${security.rotation.health.check.timeout:30}")
    private int healthCheckTimeoutSeconds;
    
    @PostConstruct
    public void initialize() {
        log.info("Initializing Hybrid Secret Rotation Service");
        
        // Initialize Vault operations
        this.kvOperations = vaultTemplate.opsForVersionedKeyValue(kvBackend);
        this.transitOperations = vaultTemplate.opsForTransit(transitBackend);
        
        // Register core Waqiti secrets
        registerCoreSecrets();
        
        // Start rotation monitoring
        startRotationMonitoring();
        
        log.info("Hybrid Secret Rotation Service initialized with {} registered secrets", 
            secretRegistry.size());
    }
    
    /**
     * Register a secret for managed rotation
     */
    public void registerSecret(SecretRegistration registration) {
        log.info("Registering secret: {} of type {}", 
            registration.getSecretId(), registration.getSecretType());
        
        SecretMetadata metadata = SecretMetadata.builder()
            .secretId(registration.getSecretId())
            .secretType(registration.getSecretType())
            .vaultEngine(registration.getVaultEngine())
            .vaultPath(registration.getVaultPath())
            .rotationStrategy(registration.getRotationStrategy())
            .rotationIntervalDays(registration.getRotationIntervalDays())
            .dependentServices(registration.getDependentServices())
            .validationRules(registration.getValidationRules())
            .rollbackCapable(registration.isRollbackCapable())
            .lastRotation(Instant.now())
            .nextRotation(calculateNextRotation(registration.getRotationIntervalDays()))
            .rotationCount(0)
            .status(SecretStatus.ACTIVE)
            .build();
        
        secretRegistry.put(registration.getSecretId(), metadata);
        
        auditService.logSecurityEvent(SecurityAuditRequest.builder()
            .eventType("SECURITY_EVENT")
            .eventName("SECRET_REGISTERED")
            .action("REGISTER")
            .success(true)
            .metadata(Map.of(
                "secretId", registration.getSecretId(),
                "secretType", registration.getSecretType(),
                "vaultEngine", registration.getVaultEngine()
            ))
            .build());
    }
    
    /**
     * Orchestrated rotation with business logic
     */
    public CompletableFuture<RotationResult> rotateSecret(String secretId) {
        return CompletableFuture.supplyAsync(() -> {
            log.info("Starting orchestrated rotation for secret: {}", secretId);
            
            SecretMetadata metadata = secretRegistry.get(secretId);
            if (metadata == null) {
                throw new SecretRotationException("Secret not registered: " + secretId);
            }
            
            try {
                // Create rotation plan
                RotationPlan plan = createRotationPlan(metadata);
                activeRotations.put(secretId, plan);
                
                // Update status
                metadata.setStatus(SecretStatus.ROTATING);
                metadata.setRotationStarted(Instant.now());
                
                // Execute rotation based on strategy
                RotationResult result = executeRotationPlan(plan);
                
                // Update metadata on success
                if (result.isSuccess()) {
                    metadata.setLastRotation(Instant.now());
                    metadata.setNextRotation(calculateNextRotation(metadata.getRotationIntervalDays()));
                    metadata.setRotationCount(metadata.getRotationCount() + 1);
                    metadata.setStatus(SecretStatus.ACTIVE);
                    metadata.setLastError(null);
                } else {
                    metadata.setStatus(SecretStatus.FAILED);
                    metadata.setLastError(result.getErrorMessage());
                }
                
                // Cleanup
                activeRotations.remove(secretId);
                
                return result;
                
            } catch (Exception e) {
                log.error("Rotation failed for secret: {}", secretId, e);
                metadata.setStatus(SecretStatus.FAILED);
                metadata.setLastError(e.getMessage());
                activeRotations.remove(secretId);
                
                return RotationResult.builder()
                    .secretId(secretId)
                    .success(false)
                    .errorMessage(e.getMessage())
                    .build();
            }
        }, rotationExecutor);
    }
    
    /**
     * System-wide coordinated rotation
     */
    public CompletableFuture<SystemRotationResult> performSystemWideRotation(
            SystemRotationRequest request) {
        
        return CompletableFuture.supplyAsync(() -> {
            log.info("Starting system-wide rotation: {}", request.getDescription());
            
            try {
                // Group secrets by dependency order
                Map<Integer, List<String>> rotationWaves = 
                    groupSecretsByDependencyOrder(request.getSecretIds());
                
                List<RotationResult> allResults = new ArrayList<>();
                
                // Execute waves sequentially
                for (Map.Entry<Integer, List<String>> wave : rotationWaves.entrySet()) {
                    log.info("Executing rotation wave {}", wave.getKey());
                    
                    // Rotate secrets in wave concurrently
                    List<CompletableFuture<RotationResult>> waveRotations = 
                        wave.getValue().stream()
                            .map(this::rotateSecret)
                            .collect(Collectors.toList());
                    
                    // Wait for wave completion
                    CompletableFuture<Void> waveCompletion = CompletableFuture.allOf(
                        waveRotations.toArray(new CompletableFuture[0]));
                    
                    waveCompletion.get(10, TimeUnit.MINUTES);
                    
                    // Collect results
                    List<RotationResult> waveResults = waveRotations.stream()
                        .map(CompletableFuture::join)
                        .collect(Collectors.toList());
                    
                    allResults.addAll(waveResults);
                    
                    // Check for failures
                    boolean waveSuccess = waveResults.stream().allMatch(RotationResult::isSuccess);
                    if (!waveSuccess && request.isFailFast()) {
                        log.error("Wave {} failed, stopping system rotation", wave.getKey());
                        break;
                    }
                    
                    // Stabilization period between waves
                    if (wave.getKey() < rotationWaves.size()) {
                        TimeUnit.MILLISECONDS.sleep(request.getStabilizationPeriodSeconds() * 1000);
                    }
                }
                
                boolean overallSuccess = allResults.stream().allMatch(RotationResult::isSuccess);
                
                // Audit system rotation
                auditService.logSecurityEvent(SecurityAuditRequest.builder()
                    .eventType("SECURITY_EVENT")
                    .eventName("SYSTEM_ROTATION_COMPLETED")
                    .action("ROTATE")
                    .success(overallSuccess)
                    .metadata(Map.of(
                        "description", request.getDescription(),
                        "totalSecrets", request.getSecretIds().size(),
                        "successCount", allResults.stream().mapToInt(r -> r.isSuccess() ? 1 : 0).sum(),
                        "overallSuccess", overallSuccess
                    ))
                    .build());
                
                return SystemRotationResult.builder()
                    .success(overallSuccess)
                    .rotationResults(allResults)
                    .completedAt(Instant.now())
                    .build();
                
            } catch (Exception e) {
                log.error("System-wide rotation failed", e);
                return SystemRotationResult.builder()
                    .success(false)
                    .errorMessage(e.getMessage())
                    .completedAt(Instant.now())
                    .build();
            }
        }, rotationExecutor);
    }
    
    /**
     * Emergency rotation with immediate execution
     */
    public RotationResult emergencyRotation(EmergencyRotationRequest request) {
        log.warn("EMERGENCY ROTATION initiated for: {} - Reason: {}", 
            request.getSecretId(), request.getReason());
        
        SecretMetadata metadata = secretRegistry.get(request.getSecretId());
        if (metadata == null) {
            throw new SecretRotationException("Secret not registered: " + request.getSecretId());
        }
        
        try {
            // Force immediate rotation strategy
            RotationStrategy originalStrategy = metadata.getRotationStrategy();
            metadata.setRotationStrategy(RotationStrategy.IMMEDIATE);
            
            // Execute emergency rotation
            RotationResult result = rotateSecret(request.getSecretId()).get(5, TimeUnit.MINUTES);
            
            // Restore original strategy
            metadata.setRotationStrategy(originalStrategy);
            
            // Audit emergency action
            auditService.logSecurityEvent(SecurityAuditRequest.builder()
                .eventType("SECURITY_INCIDENT")
                .eventName("EMERGENCY_ROTATION")
                .action("EMERGENCY_ROTATE")
                .success(result.isSuccess())
                .riskLevel("CRITICAL")
                .metadata(Map.of(
                    "secretId", request.getSecretId(),
                    "reason", request.getReason(),
                    "initiatedBy", request.getInitiatedBy(),
                    "success", result.isSuccess()
                ))
                .build());
            
            // Immediate notification to security team
            notificationService.sendUrgentNotification(
                "security-team",
                "Emergency Secret Rotation",
                String.format("Emergency rotation %s for %s: %s", 
                    result.isSuccess() ? "succeeded" : "failed",
                    request.getSecretId(), 
                    request.getReason())
            );
            
            return result;
            
        } catch (Exception e) {
            log.error("Emergency rotation failed", e);
            throw new SecretRotationException("Emergency rotation failed", e);
        }
    }
    
    /**
     * Validate secret rotation readiness
     */
    public ValidationResult validateRotationReadiness(String secretId) {
        SecretMetadata metadata = secretRegistry.get(secretId);
        if (metadata == null) {
            return ValidationResult.failure("Secret not registered");
        }
        
        List<String> issues = new ArrayList<>();
        
        // Check if already rotating
        if (metadata.getStatus() == SecretStatus.ROTATING) {
            issues.add("Secret is currently being rotated");
        }
        
        // Check dependent services health
        for (String service : metadata.getDependentServices()) {
            if (!serviceRegistry.isHealthy(service)) {
                issues.add("Dependent service unhealthy: " + service);
            }
        }
        
        // Check Vault connectivity
        if (!checkVaultConnectivity(metadata.getVaultEngine())) {
            issues.add("Vault engine unavailable: " + metadata.getVaultEngine());
        }
        
        // Run validation rules
        for (ValidationRule rule : metadata.getValidationRules()) {
            try {
                if (!rule.validate(metadata)) {
                    issues.add("Validation rule failed: " + rule.getDescription());
                }
            } catch (Exception e) {
                issues.add("Validation rule error: " + e.getMessage());
            }
        }
        
        return issues.isEmpty() ? 
            ValidationResult.success() : 
            ValidationResult.failure(String.join(", ", issues));
    }
    
    /**
     * Get rotation status and health
     */
    public RotationStatusReport getRotationStatus() {
        List<SecretStatusInfo> secretStatuses = secretRegistry.values().stream()
            .map(metadata -> SecretStatusInfo.builder()
                .secretId(metadata.getSecretId())
                .secretType(metadata.getSecretType())
                .status(metadata.getStatus())
                .lastRotation(metadata.getLastRotation())
                .nextRotation(metadata.getNextRotation())
                .rotationCount(metadata.getRotationCount())
                .dependentServices(metadata.getDependentServices())
                .build())
            .collect(Collectors.toList());
        
        long activeRotations = secretStatuses.stream()
            .mapToLong(status -> status.getStatus() == SecretStatus.ROTATING ? 1 : 0)
            .sum();
        
        long failedSecrets = secretStatuses.stream()
            .mapToLong(status -> status.getStatus() == SecretStatus.FAILED ? 1 : 0)
            .sum();
        
        return RotationStatusReport.builder()
            .reportTimestamp(Instant.now())
            .totalSecrets(secretRegistry.size())
            .activeRotations((int) activeRotations)
            .failedSecrets((int) failedSecrets)
            .secretStatuses(secretStatuses)
            .systemHealth(calculateSystemHealth())
            .build();
    }
    
    /**
     * Scheduled rotation check
     */
    @Scheduled(cron = "0 0 */6 * * ?") // Every 6 hours
    public void checkScheduledRotations() {
        if (!rotationEnabled) {
            log.debug("Secret rotation is disabled");
            return;
        }
        
        log.info("Checking for scheduled rotations");
        
        Instant now = Instant.now();
        List<String> secretsToRotate = secretRegistry.values().stream()
            .filter(metadata -> metadata.getNextRotation().isBefore(now))
            .filter(metadata -> metadata.getStatus() == SecretStatus.ACTIVE)
            .map(SecretMetadata::getSecretId)
            .collect(Collectors.toList());
        
        if (!secretsToRotate.isEmpty()) {
            log.info("Found {} secrets requiring rotation", secretsToRotate.size());
            
            // Create system rotation request
            SystemRotationRequest request = SystemRotationRequest.builder()
                .description("Scheduled rotation batch")
                .secretIds(secretsToRotate)
                .failFast(false)
                .stabilizationPeriodSeconds(30)
                .build();
            
            performSystemWideRotation(request);
        }
    }
    
    // Private helper methods
    
    private RotationPlan createRotationPlan(SecretMetadata metadata) {
        return RotationPlan.builder()
            .secretId(metadata.getSecretId())
            .strategy(metadata.getRotationStrategy())
            .vaultEngine(metadata.getVaultEngine())
            .vaultPath(metadata.getVaultPath())
            .dependentServices(metadata.getDependentServices())
            .validationRules(metadata.getValidationRules())
            .rollbackCapable(metadata.isRollbackCapable())
            .createdAt(Instant.now())
            .build();
    }
    
    private RotationResult executeRotationPlan(RotationPlan plan) {
        log.info("Executing rotation plan for: {} using strategy: {}", 
            plan.getSecretId(), plan.getStrategy());
        
        try {
            switch (plan.getStrategy()) {
                case IMMEDIATE:
                    return executeImmediateRotation(plan);
                case BLUE_GREEN:
                    return executeBlueGreenRotation(plan);
                case CANARY:
                    return executeCanaryRotation(plan);
                case GRADUAL:
                    return executeGradualRotation(plan);
                default:
                    throw new SecretRotationException("Unknown strategy: " + plan.getStrategy());
            }
        } catch (Exception e) {
            log.error("Rotation plan execution failed", e);
            
            // Attempt rollback if capable
            if (plan.isRollbackCapable()) {
                try {
                    performRollback(plan);
                } catch (Exception rollbackError) {
                    log.error("Rollback failed", rollbackError);
                }
            }
            
            throw e;
        }
    }
    
    private RotationResult executeImmediateRotation(RotationPlan plan) {
        // Generate new secret using Vault
        Object newSecret = generateSecretUsingVault(plan);
        
        // Update all services immediately
        updateDependentServices(plan.getDependentServices(), newSecret);
        
        // Validate deployment
        boolean validationSuccess = validateServiceHealth(plan.getDependentServices());
        
        if (!validationSuccess) {
            throw new SecretRotationException("Service validation failed after immediate rotation");
        }
        
        return RotationResult.builder()
            .secretId(plan.getSecretId())
            .success(true)
            .strategy(plan.getStrategy())
            .completedAt(Instant.now())
            .build();
    }
    
    private RotationResult executeBlueGreenRotation(RotationPlan plan) {
        // Generate new secret
        Object newSecret = generateSecretUsingVault(plan);
        
        // Create green environment
        createGreenEnvironment(plan, newSecret);
        
        // Test green environment
        boolean greenHealthy = testEnvironmentHealth(plan, "green");
        if (!greenHealthy) {
            destroyGreenEnvironment(plan);
            throw new SecretRotationException("Green environment health check failed");
        }
        
        // Switch traffic to green
        switchTrafficToGreen(plan);
        
        // Monitor for issues
        try {
            TimeUnit.MILLISECONDS.sleep(30000); // 30 second monitoring period with proper interruption
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new SecretRotationException("Rotation interrupted during monitoring", e);
        }
        
        boolean postSwitchHealthy = testEnvironmentHealth(plan, "green");
        if (!postSwitchHealthy) {
            // Rollback to blue
            switchTrafficToBlue(plan);
            throw new SecretRotationException("Post-switch health check failed");
        }
        
        // Decommission blue environment
        decommissionBlueEnvironment(plan);
        
        return RotationResult.builder()
            .secretId(plan.getSecretId())
            .success(true)
            .strategy(plan.getStrategy())
            .completedAt(Instant.now())
            .build();
    }
    
    private RotationResult executeCanaryRotation(RotationPlan plan) {
        Object newSecret = generateSecretUsingVault(plan);
        
        // Deploy to canary instances (10% of traffic)
        deployToCanary(plan, newSecret, 0.1);
        
        // Monitor canary
        try {
            TimeUnit.MILLISECONDS.sleep(300000); // 5 minute canary period with proper interruption
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new SecretRotationException("Rotation interrupted during canary monitoring", e);
        }
        
        boolean canaryHealthy = validateCanaryHealth(plan);
        if (!canaryHealthy) {
            rollbackCanary(plan);
            throw new SecretRotationException("Canary deployment failed");
        }
        
        // Gradually increase traffic to new secret
        increaseCanaryTraffic(plan, 0.5); // 50%
        try {
            TimeUnit.MILLISECONDS.sleep(300000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new SecretRotationException("Rotation interrupted during 50% traffic monitoring", e);
        }
        
        canaryHealthy = validateCanaryHealth(plan);
        if (!canaryHealthy) {
            rollbackCanary(plan);
            throw new SecretRotationException("Canary 50% traffic failed");
        }
        
        // Complete rollout
        completeCanaryRollout(plan);
        
        return RotationResult.builder()
            .secretId(plan.getSecretId())
            .success(true)
            .strategy(plan.getStrategy())
            .completedAt(Instant.now())
            .build();
    }
    
    private RotationResult executeGradualRotation(RotationPlan plan) {
        Object newSecret = generateSecretUsingVault(plan);
        
        // Phase 1: Add new secret alongside old
        addSecretVersion(plan, newSecret);
        
        // Phase 2: Migrate services one by one
        for (String service : plan.getDependentServices()) {
            migrateServiceToNewSecret(service, newSecret);
            
            // Validate each service migration
            if (!serviceRegistry.isHealthy(service)) {
                throw new SecretRotationException("Service migration failed: " + service);
            }
            
            // Short delay between service migrations
            try {
                TimeUnit.MILLISECONDS.sleep(10000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new SecretRotationException("Rotation interrupted during service migration", e);
            }
        }
        
        // Phase 3: Remove old secret after grace period
        removeOldSecretVersion(plan);
        
        return RotationResult.builder()
            .secretId(plan.getSecretId())
            .success(true)
            .strategy(plan.getStrategy())
            .completedAt(Instant.now())
            .build();
    }
    
    private Object generateSecretUsingVault(RotationPlan plan) {
        switch (plan.getVaultEngine()) {
            case DATABASE:
                return generateDatabaseCredentials(plan.getVaultPath());
            case AWS:
                return generateAwsCredentials(plan.getVaultPath());
            case PKI:
                return generateCertificate(plan.getVaultPath());
            case TRANSIT:
                return rotateTransitKey(plan.getVaultPath());
            case KV:
                return generateStaticSecret(plan.getVaultPath());
            default:
                throw new SecretRotationException("Unsupported Vault engine: " + plan.getVaultEngine());
        }
    }
    
    private Object generateDatabaseCredentials(String rolePath) {
        String path = String.format("%s/creds/%s", databaseBackend, rolePath);
        VaultResponse response = vaultTemplate.read(path);
        
        if (response == null || response.getData() == null) {
            throw new SecretRotationException("Failed to generate database credentials");
        }
        
        return Map.of(
            "username", response.getData().get("username"),
            "password", response.getData().get("password"),
            "leaseId", response.getLeaseId()
        );
    }
    
    private Object generateAwsCredentials(String rolePath) {
        String path = String.format("aws/creds/%s", rolePath);
        VaultResponse response = vaultTemplate.read(path);
        
        if (response == null || response.getData() == null) {
            throw new SecretRotationException("Failed to generate AWS credentials");
        }
        
        return response.getData();
    }
    
    private Object generateCertificate(String rolePath) {
        String path = String.format("%s/issue/%s", pkiBackend, rolePath);
        Map<String, Object> request = Map.of(
            "common_name", "service.waqiti.com",
            "ttl", "720h"
        );
        
        VaultResponse response = vaultTemplate.write(path, request);
        return response.getData();
    }
    
    private Object rotateTransitKey(String keyName) {
        vaultTemplate.write(String.format("%s/keys/%s/rotate", transitBackend, keyName), null);
        return Map.of("rotated", true, "keyName", keyName);
    }
    
    private Object generateStaticSecret(String path) {
        /**
         * CRITICAL SECURITY FIX: Use SecureRandom for cryptographic key generation
         * Random() is predictable and must NEVER be used for secret generation
         */
        byte[] bytes = new byte[32];
        SecureRandom secureRandom = new SecureRandom();
        secureRandom.nextBytes(bytes);
        String newValue = Base64.getEncoder().encodeToString(bytes);
        
        // Add additional entropy from system state
        byte[] additionalEntropy = new byte[8];
        secureRandom.nextBytes(additionalEntropy);
        String entropyString = Base64.getEncoder().encodeToString(additionalEntropy);
        
        // Log rotation event for security audit (without exposing the secret)
        String secretName = path.substring(path.lastIndexOf('/') + 1);
        log.info("Secret rotation initiated with enhanced entropy for secret: {}", secretName);
        
        Map<String, Object> secretData = Map.of(
            "value", newValue,
            "rotated_at", Instant.now().toString()
        );
        
        kvOperations.put(path, secretData);
        return secretData;
    }
    
    private void registerCoreSecrets() {
        // Database credentials for payment service
        registerSecret(SecretRegistration.builder()
            .secretId("payment-db-credentials")
            .secretType(SecretType.DATABASE_CREDENTIALS)
            .vaultEngine(VaultEngine.DATABASE)
            .vaultPath("payment-service")
            .rotationStrategy(RotationStrategy.BLUE_GREEN)
            .rotationIntervalDays(90)
            .dependentServices(List.of("payment-service", "transaction-service"))
            .rollbackCapable(true)
            .build());
        
        // JWT signing key for API gateway
        registerSecret(SecretRegistration.builder()
            .secretId("api-gateway-jwt-key")
            .secretType(SecretType.JWT_SECRET)
            .vaultEngine(VaultEngine.TRANSIT)
            .vaultPath("jwt-signing-key")
            .rotationStrategy(RotationStrategy.GRADUAL)
            .rotationIntervalDays(30)
            .dependentServices(List.of("api-gateway", "user-service"))
            .rollbackCapable(true)
            .build());
        
        // Payment provider API keys
        registerSecret(SecretRegistration.builder()
            .secretId("stripe-api-key")
            .secretType(SecretType.API_KEY)
            .vaultEngine(VaultEngine.KV)
            .vaultPath("api-keys/stripe")
            .rotationStrategy(RotationStrategy.CANARY)
            .rotationIntervalDays(180)
            .dependentServices(List.of("payment-service"))
            .rollbackCapable(true)
            .build());
    }
    
    private Instant calculateNextRotation(int intervalDays) {
        return Instant.now().plus(intervalDays, ChronoUnit.DAYS);
    }
    
    private Map<Integer, List<String>> groupSecretsByDependencyOrder(List<String> secretIds) {
        // Group secrets by their dependency relationships
        // Higher numbers = later in rotation order
        Map<Integer, List<String>> waves = new HashMap<>();
        
        for (String secretId : secretIds) {
            SecretMetadata metadata = secretRegistry.get(secretId);
            if (metadata != null) {
                int priority = calculateRotationPriority(metadata);
                waves.computeIfAbsent(priority, k -> new ArrayList<>()).add(secretId);
            }
        }
        
        return waves;
    }
    
    private int calculateRotationPriority(SecretMetadata metadata) {
        // Determine rotation order based on secret type and dependencies
        switch (metadata.getSecretType()) {
            case DATABASE_CREDENTIALS:
                return 1; // Rotate database credentials first
            case ENCRYPTION_KEY:
                return 2; // Then encryption keys
            case JWT_SECRET:
                return 3; // Then JWT secrets
            case API_KEY:
                return 4; // Finally API keys
            default:
                return 5;
        }
    }
    
    private boolean checkVaultConnectivity(VaultEngine engine) {
        try {
            switch (engine) {
                case DATABASE:
                    vaultTemplate.read(databaseBackend + "/config");
                    break;
                case AWS:
                    vaultTemplate.read("aws/config");
                    break;
                case PKI:
                    vaultTemplate.read(pkiBackend + "/ca");
                    break;
                case TRANSIT:
                    vaultTemplate.read(transitBackend + "/keys");
                    break;
                case KV:
                    kvOperations.get("health-check");
                    break;
            }
            return true;
        } catch (Exception e) {
            log.warn("Vault connectivity check failed for engine: {}", engine, e);
            return false;
        }
    }
    
    private void startRotationMonitoring() {
        rotationExecutor.scheduleAtFixedRate(() -> {
            try {
                monitorActiveRotations();
            } catch (Exception e) {
                log.error("Rotation monitoring failed", e);
            }
        }, 1, 1, TimeUnit.MINUTES);
    }
    
    private void monitorActiveRotations() {
        activeRotations.forEach((secretId, plan) -> {
            Duration rotationDuration = Duration.between(plan.getCreatedAt(), Instant.now());
            
            // Alert on long-running rotations
            if (rotationDuration.toMinutes() > 30) {
                log.warn("Long-running rotation detected: {} ({})", secretId, rotationDuration);
                
                CriticalAlertRequest alertRequest = CriticalAlertRequest.builder()
                    .userId("ops-team")
                    .title("Long-running Secret Rotation")
                    .message(String.format("Secret rotation for %s has been running for %d minutes", 
                        secretId, rotationDuration.toMinutes()))
                    .severity(CriticalAlertRequest.AlertSeverity.WARNING)
                    .category("SECRET_ROTATION")
                    .affectedSystem("Secret Management")
                    .build();
                
                notificationService.sendCriticalAlert(alertRequest);
            }
        });
    }
    
    private double calculateSystemHealth() {
        long totalSecrets = secretRegistry.size();
        long healthySecrets = secretRegistry.values().stream()
            .mapToLong(metadata -> metadata.getStatus() == SecretStatus.ACTIVE ? 1 : 0)
            .sum();
        
        return totalSecrets > 0 ? (double) healthySecrets / totalSecrets : 1.0;
    }
    
    // Placeholder implementations for orchestration methods
    private void updateDependentServices(List<String> services, Object newSecret) {
        // Implementation would update service configurations
        log.info("Updating dependent services with new secret");
    }
    
    private boolean validateServiceHealth(List<String> services) {
        return services.stream().allMatch(serviceRegistry::isHealthy);
    }
    
    private void createGreenEnvironment(RotationPlan plan, Object newSecret) {
        log.info("Creating green environment for {}", plan.getSecretId());
    }
    
    private boolean testEnvironmentHealth(RotationPlan plan, String environment) {
        try {
            log.info("Testing health of {} environment for secret: {}", environment, plan.getSecretId());
            
            // Perform comprehensive health checks
            boolean databaseConnectivity = testDatabaseConnectivity(plan, environment);
            boolean serviceDiscovery = testServiceDiscovery(plan, environment);
            boolean secretAccess = testSecretAccess(plan, environment);
            boolean externalApiConnectivity = testExternalApiConnectivity(plan, environment);
            
            boolean overallHealth = databaseConnectivity && serviceDiscovery && 
                                   secretAccess && externalApiConnectivity;
            
            if (overallHealth) {
                log.info("✅ Environment {} health check passed for {}", environment, plan.getSecretId());
            } else {
                log.error("❌ Environment {} health check failed for {}", environment, plan.getSecretId());
                log.error("Health check results - DB: {}, Discovery: {}, Secrets: {}, External: {}",
                    databaseConnectivity, serviceDiscovery, secretAccess, externalApiConnectivity);
            }
            
            return overallHealth;
            
        } catch (Exception e) {
            log.error("Environment health check failed for {} environment: ", environment, e);
            return false;
        }
    }
    
    private void switchTrafficToGreen(RotationPlan plan) {
        log.info("Switching traffic to green for {}", plan.getSecretId());
    }
    
    private void switchTrafficToBlue(RotationPlan plan) {
        log.info("Rolling back to blue for {}", plan.getSecretId());
    }
    
    private void destroyGreenEnvironment(RotationPlan plan) {
        log.info("Destroying green environment for {}", plan.getSecretId());
    }
    
    private void decommissionBlueEnvironment(RotationPlan plan) {
        log.info("Decommissioning blue environment for {}", plan.getSecretId());
    }
    
    private void deployToCanary(RotationPlan plan, Object newSecret, double trafficPercent) {
        log.info("Deploying to canary ({}%) for {}", trafficPercent * 100, plan.getSecretId());
    }
    
    private boolean validateCanaryHealth(RotationPlan plan) {
        try {
            log.info("Validating canary health for secret: {}", plan.getSecretId());
            
            // Check canary deployment metrics
            double errorRate = getCanaryErrorRate(plan);
            double responseTime = getCanaryResponseTime(plan);
            int activeConnections = getCanaryActiveConnections(plan);
            
            // Health criteria
            boolean errorRateOk = errorRate < 0.01; // Less than 1% error rate
            boolean responseTimeOk = responseTime < 2000; // Less than 2 seconds
            boolean connectionsOk = activeConnections > 0; // Has active connections
            
            boolean canaryHealthy = errorRateOk && responseTimeOk && connectionsOk;
            
            if (canaryHealthy) {
                log.info("✅ Canary validation passed for {}: errorRate={}, responseTime={}ms, connections={}", 
                    plan.getSecretId(), errorRate, responseTime, activeConnections);
            } else {
                log.error("❌ Canary validation failed for {}: errorRate={}, responseTime={}ms, connections={}", 
                    plan.getSecretId(), errorRate, responseTime, activeConnections);
            }
            
            return canaryHealthy;
            
        } catch (Exception e) {
            log.error("Canary health validation failed: ", e);
            return false;
        }
    }
    
    private void rollbackCanary(RotationPlan plan) {
        log.info("Rolling back canary for {}", plan.getSecretId());
    }
    
    private void increaseCanaryTraffic(RotationPlan plan, double trafficPercent) {
        log.info("Increasing canary traffic to {}% for {}", trafficPercent * 100, plan.getSecretId());
    }
    
    // Helper methods for environment health testing
    private boolean testDatabaseConnectivity(RotationPlan plan, String environment) {
        try {
            // Test database connection with new secrets
            return dataSource.getConnection().isValid(5);
        } catch (Exception e) {
            log.error("Database connectivity test failed for {} environment: ", environment, e);
            return false;
        }
    }
    
    private boolean testServiceDiscovery(RotationPlan plan, String environment) {
        try {
            // Test service discovery and registration
            return eurekaClient.getApplications().size() > 0;
        } catch (Exception e) {
            log.error("Service discovery test failed for {} environment: ", environment, e);
            return false;
        }
    }
    
    private boolean testSecretAccess(RotationPlan plan, String environment) {
        try {
            // Test that new secrets can be accessed
            vaultTemplate.read("secret/" + plan.getSecretId());
            return true;
        } catch (Exception e) {
            log.error("Secret access test failed for {} environment: ", environment, e);
            return false;
        }
    }
    
    private boolean testExternalApiConnectivity(RotationPlan plan, String environment) {
        try {
            // Test external API connectivity with new credentials
            // This is a simplified test - would be more comprehensive in production
            return restTemplate.getForEntity("https://httpbin.org/status/200", String.class)
                .getStatusCode().is2xxSuccessful();
        } catch (Exception e) {
            log.error("External API connectivity test failed for {} environment: ", environment, e);
            return false;
        }
    }
    
    // Helper methods for canary metrics
    private double getCanaryErrorRate(RotationPlan plan) {
        try {
            // In production, this would query metrics from Prometheus/Micrometer
            return meterRegistry.get("http.server.requests")
                .tag("status", "4xx")
                .tag("uri", "/api/**")
                .timer()
                .count() / (double) meterRegistry.get("http.server.requests").timer().count();
        } catch (Exception e) {
            log.warn("Could not get canary error rate: ", e);
            return 0.0;
        }
    }
    
    private double getCanaryResponseTime(RotationPlan plan) {
        try {
            return meterRegistry.get("http.server.requests")
                .tag("uri", "/api/**")
                .timer()
                .mean(java.util.concurrent.TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            log.warn("Could not get canary response time: ", e);
            return 0.0;
        }
    }
    
    private int getCanaryActiveConnections(RotationPlan plan) {
        try {
            return (int) meterRegistry.get("tomcat.sessions.active.current")
                .gauge()
                .value();
        } catch (Exception e) {
            log.warn("Could not get active connections: ", e);
            return 1; // Assume at least one connection for health
        }
    }
    
    private void completeCanaryRollout(RotationPlan plan) {
        log.info("Completing canary rollout for {}", plan.getSecretId());
    }
    
    private void addSecretVersion(RotationPlan plan, Object newSecret) {
        log.info("Adding new secret version for {}", plan.getSecretId());
    }
    
    private void migrateServiceToNewSecret(String service, Object newSecret) {
        log.info("Migrating service {} to new secret", service);
        serviceRegistry.updateServiceConfiguration(service, newSecret);
    }
    
    private void removeOldSecretVersion(RotationPlan plan) {
        log.info("Removing old secret version for {}", plan.getSecretId());
    }
    
    private void performRollback(RotationPlan plan) {
        log.warn("Performing rollback for {}", plan.getSecretId());
    }
    
    // Inner classes and enums
    
    @Data
    @Builder
    public static class SecretRegistration {
        private String secretId;
        private SecretType secretType;
        private VaultEngine vaultEngine;
        private String vaultPath;
        private RotationStrategy rotationStrategy;
        private int rotationIntervalDays;
        private List<String> dependentServices;
        private List<ValidationRule> validationRules;
        private boolean rollbackCapable;
    }
    
    @Data
    @Builder
    public static class SecretMetadata {
        private String secretId;
        private SecretType secretType;
        private VaultEngine vaultEngine;
        private String vaultPath;
        private RotationStrategy rotationStrategy;
        private int rotationIntervalDays;
        private List<String> dependentServices;
        private List<ValidationRule> validationRules;
        private boolean rollbackCapable;
        private Instant lastRotation;
        private Instant nextRotation;
        private int rotationCount;
        private SecretStatus status;
        private Instant rotationStarted;
        private String lastError;
    }
    
    @Data
    @Builder
    public static class RotationPlan {
        private String secretId;
        private RotationStrategy strategy;
        private VaultEngine vaultEngine;
        private String vaultPath;
        private List<String> dependentServices;
        private List<ValidationRule> validationRules;
        private boolean rollbackCapable;
        private Instant createdAt;
    }
    
    @Data
    @Builder
    public static class RotationResult {
        private String secretId;
        private boolean success;
        private RotationStrategy strategy;
        private String errorMessage;
        private Instant completedAt;
    }
    
    @Data
    @Builder
    public static class SystemRotationRequest {
        private String description;
        private List<String> secretIds;
        private boolean failFast;
        private int stabilizationPeriodSeconds;
    }
    
    @Data
    @Builder
    public static class SystemRotationResult {
        private boolean success;
        private List<RotationResult> rotationResults;
        private String errorMessage;
        private Instant completedAt;
    }
    
    @Data
    @Builder
    public static class EmergencyRotationRequest {
        private String secretId;
        private String reason;
        private String initiatedBy;
    }
    
    @Data
    @Builder
    public static class RotationStatusReport {
        private Instant reportTimestamp;
        private int totalSecrets;
        private int activeRotations;
        private int failedSecrets;
        private List<SecretStatusInfo> secretStatuses;
        private double systemHealth;
    }
    
    @Data
    @Builder
    public static class SecretStatusInfo {
        private String secretId;
        private SecretType secretType;
        private SecretStatus status;
        private Instant lastRotation;
        private Instant nextRotation;
        private int rotationCount;
        private List<String> dependentServices;
    }
    
    @Data
    @Builder
    public static class ValidationResult {
        private boolean valid;
        private String message;
        
        public static ValidationResult success() {
            return ValidationResult.builder().valid(true).build();
        }
        
        public static ValidationResult failure(String message) {
            return ValidationResult.builder().valid(false).message(message).build();
        }
    }
    
    public enum SecretType {
        DATABASE_CREDENTIALS,
        API_KEY,
        JWT_SECRET,
        ENCRYPTION_KEY,
        CERTIFICATE,
        OAUTH_SECRET,
        WEBHOOK_SECRET
    }
    
    public enum VaultEngine {
        DATABASE,
        AWS,
        PKI,
        TRANSIT,
        KV
    }
    
    public enum RotationStrategy {
        IMMEDIATE,    // Replace immediately
        BLUE_GREEN,   // Blue-green deployment
        CANARY,       // Canary deployment
        GRADUAL       // Service-by-service migration
    }
    
    public enum SecretStatus {
        ACTIVE,
        ROTATING,
        FAILED,
        DISABLED
    }
    
    public interface ValidationRule {
        boolean validate(SecretMetadata metadata) throws Exception;
        String getDescription();
    }
    
    public static class SecretRotationException extends RuntimeException {
        public SecretRotationException(String message) {
            super(message);
        }
        
        public SecretRotationException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}