package com.waqiti.common.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Token Migration Utility for seamless transition from legacy JWT to Keycloak
 * Handles live session migration without user disruption
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TokenMigrationUtility {
    
    private final JwtTokenProvider jwtTokenProvider;
    private final RestTemplate restTemplate;
    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;
    
    @Value("${keycloak.auth-server-url:http://localhost:8080}")
    private String keycloakServerUrl;
    
    @Value("${keycloak.realm:waqiti}")
    private String realm;
    
    @Value("${keycloak.client-id:token-migration-service}")
    private String clientId;
    
    @Value("${keycloak.client-secret:}")
    private String clientSecret;
    
    @Value("${token.migration.batch-size:100}")
    private int batchSize;
    
    @Value("${token.migration.parallel-threads:10}")
    private int parallelThreads;
    
    @Value("${token.migration.cache-ttl-hours:24}")
    private int cacheTtlHours;
    
    private static final String MIGRATION_CACHE_PREFIX = "token:migration:";
    private static final String MIGRATION_STATUS_KEY = "migration:status";
    private static final String MIGRATION_METRICS_KEY = "migration:metrics";
    
    private final ConcurrentHashMap<String, MigrationStatus> migrationStatusMap = new ConcurrentHashMap<>();
    
    public enum MigrationStatus {
        PENDING,
        IN_PROGRESS,
        COMPLETED,
        FAILED,
        ROLLED_BACK
    }
    
    /**
     * Migrate a single legacy JWT token to Keycloak token
     */
    public MigrationResult migrateSingleToken(String legacyToken) {
        String sessionId = UUID.randomUUID().toString();
        log.info("Starting token migration for session: {}", sessionId);
        
        try {
            // Validate legacy token
            if (!jwtTokenProvider.validateToken(legacyToken)) {
                log.warn("Invalid legacy token for migration");
                return MigrationResult.failed("Invalid legacy token");
            }
            
            // Extract user information from legacy token
            Claims claims = extractClaims(legacyToken);
            String username = claims.getSubject();
            List<String> authorities = extractAuthorities(claims);
            Map<String, Object> additionalClaims = extractAdditionalClaims(claims);
            
            // Check if already migrated
            String cacheKey = MIGRATION_CACHE_PREFIX + username;
            MigrationResult cachedResult = getCachedMigration(cacheKey);
            if (cachedResult != null && cachedResult.isSuccess()) {
                log.debug("Using cached migration for user: {}", username);
                return cachedResult;
            }
            
            // Create or update Keycloak user
            String keycloakUserId = createOrUpdateKeycloakUser(username, authorities, additionalClaims);
            
            // Generate Keycloak token
            KeycloakTokenResponse keycloakToken = generateKeycloakToken(username, authorities);
            
            // Store migration mapping
            storeMigrationMapping(legacyToken, keycloakToken, username);
            
            // Cache successful migration
            MigrationResult result = MigrationResult.success(
                keycloakToken.getAccessToken(),
                keycloakToken.getRefreshToken(),
                keycloakUserId,
                sessionId
            );
            
            cacheMigrationResult(cacheKey, result);
            updateMigrationMetrics(true);
            
            log.info("Successfully migrated token for user: {}", username);
            return result;
            
        } catch (Exception e) {
            log.error("Failed to migrate token for session: {}", sessionId, e);
            updateMigrationMetrics(false);
            return MigrationResult.failed(e.getMessage());
        }
    }
    
    /**
     * Batch migrate multiple tokens
     */
    public BatchMigrationResult migrateBatch(List<String> legacyTokens) {
        log.info("Starting batch migration for {} tokens", legacyTokens.size());
        
        BatchMigrationResult batchResult = new BatchMigrationResult();
        batchResult.setStartTime(Instant.now());
        batchResult.setTotalTokens(legacyTokens.size());
        
        // Process in parallel batches
        List<CompletableFuture<MigrationResult>> futures = legacyTokens.stream()
            .map(token -> CompletableFuture.supplyAsync(() -> migrateSingleToken(token)))
            .collect(Collectors.toList());
        
        // Wait for all migrations to complete
        try {
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .get(10, java.util.concurrent.TimeUnit.MINUTES);
        } catch (java.util.concurrent.TimeoutException e) {
            log.error("Token migration timed out after 10 minutes for {} tokens", legacyTokens.size(), e);
            futures.forEach(f -> f.cancel(true));
            throw new RuntimeException("Token migration timed out", e);
        } catch (java.util.concurrent.ExecutionException e) {
            log.error("Token migration execution failed", e.getCause());
            throw new RuntimeException("Token migration failed: " + e.getCause().getMessage(), e.getCause());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Token migration interrupted", e);
            throw new RuntimeException("Token migration interrupted", e);
        }

        // Collect results (safe to get with short timeout since allOf completed)
        List<MigrationResult> results = futures.stream()
            .map(f -> {
                try {
                    return f.get(1, java.util.concurrent.TimeUnit.SECONDS);
                } catch (Exception e) {
                    log.error("Failed to retrieve migration result", e);
                    return MigrationResult.failed("Failed to retrieve result: " + e.getMessage());
                }
            })
            .collect(Collectors.toList());
        
        // Calculate statistics
        long successCount = results.stream().filter(MigrationResult::isSuccess).count();
        long failureCount = results.size() - successCount;
        
        batchResult.setSuccessCount((int) successCount);
        batchResult.setFailureCount((int) failureCount);
        batchResult.setEndTime(Instant.now());
        batchResult.setResults(results);
        
        // Store batch result
        storeBatchResult(batchResult);
        
        log.info("Batch migration completed: {} success, {} failures", 
            successCount, failureCount);
        
        return batchResult;
    }
    
    /**
     * Migrate all active sessions
     */
    public CompletableFuture<BatchMigrationResult> migrateAllActiveSessions() {
        return CompletableFuture.supplyAsync(() -> {
            log.info("Starting migration of all active sessions");
            
            // Get all active sessions from Redis
            Set<String> sessionKeys = redisTemplate.keys("session:*");
            if (sessionKeys == null || sessionKeys.isEmpty()) {
                log.info("No active sessions found for migration");
                return new BatchMigrationResult();
            }
            
            List<String> tokens = new ArrayList<>();
            for (String key : sessionKeys) {
                Object sessionData = redisTemplate.opsForValue().get(key);
                if (sessionData instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> session = (Map<String, Object>) sessionData;
                    String token = (String) session.get("token");
                    if (token != null && isLegacyToken(token)) {
                        tokens.add(token);
                    }
                }
            }
            
            log.info("Found {} legacy tokens in active sessions", tokens.size());
            
            // Migrate in batches
            List<BatchMigrationResult> batchResults = new ArrayList<>();
            for (int i = 0; i < tokens.size(); i += batchSize) {
                int end = Math.min(i + batchSize, tokens.size());
                List<String> batch = tokens.subList(i, end);
                BatchMigrationResult result = migrateBatch(batch);
                batchResults.add(result);
                
                // Add delay between batches to avoid overwhelming Keycloak
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    log.warn("Migration interrupted");
                    break;
                }
            }
            
            // Aggregate results
            return aggregateBatchResults(batchResults);
        });
    }
    
    /**
     * Validate if migration was successful
     */
    public boolean validateMigration(String username) {
        try {
            // Check if user exists in Keycloak
            String keycloakUserId = getKeycloakUserId(username);
            if (keycloakUserId == null) {
                log.warn("User {} not found in Keycloak", username);
                return false;
            }
            
            // Check if user can authenticate with Keycloak
            KeycloakTokenResponse token = authenticateWithKeycloak(username);
            if (token == null || token.getAccessToken() == null) {
                log.warn("User {} cannot authenticate with Keycloak", username);
                return false;
            }
            
            // Validate token with Keycloak
            boolean isValid = validateKeycloakToken(token.getAccessToken());
            
            log.info("Migration validation for user {}: {}", username, isValid ? "SUCCESS" : "FAILED");
            return isValid;
            
        } catch (Exception e) {
            log.error("Error validating migration for user: {}", username, e);
            return false;
        }
    }
    
    /**
     * Rollback migration for a user
     */
    public boolean rollbackMigration(String username) {
        log.warn("Rolling back migration for user: {}", username);
        
        try {
            // Clear migration cache
            String cacheKey = MIGRATION_CACHE_PREFIX + username;
            redisTemplate.delete(cacheKey);
            
            // Mark user for legacy authentication
            redisTemplate.opsForValue().set(
                "rollback:" + username,
                true,
                Duration.ofDays(7)
            );
            
            // Update migration status
            migrationStatusMap.put(username, MigrationStatus.ROLLED_BACK);
            
            log.info("Successfully rolled back migration for user: {}", username);
            return true;
            
        } catch (Exception e) {
            log.error("Failed to rollback migration for user: {}", username, e);
            return false;
        }
    }
    
    /**
     * Get migration statistics
     */
    public MigrationStatistics getMigrationStatistics() {
        MigrationStatistics stats = new MigrationStatistics();
        
        // Get metrics from Redis
        Map<Object, Object> metrics = redisTemplate.opsForHash().entries(MIGRATION_METRICS_KEY);
        
        stats.setTotalMigrated(getLongValue(metrics.get("total_migrated")));
        stats.setSuccessfulMigrations(getLongValue(metrics.get("successful")));
        stats.setFailedMigrations(getLongValue(metrics.get("failed")));
        stats.setRolledBack(getLongValue(metrics.get("rolled_back")));
        stats.setAverageLatencyMs(getDoubleValue(metrics.get("avg_latency")));
        stats.setLastMigrationTime(getInstantValue(metrics.get("last_migration")));
        
        // Calculate success rate
        if (stats.getTotalMigrated() > 0) {
            double successRate = (double) stats.getSuccessfulMigrations() / stats.getTotalMigrated() * 100;
            stats.setSuccessRate(successRate);
        }
        
        return stats;
    }
    
    // Helper methods

    private Claims extractClaims(String token) {
        return jwtTokenProvider.extractClaims(token);
    }
    
    @SuppressWarnings("unchecked")
    private List<String> extractAuthorities(Claims claims) {
        Object authorities = claims.get("authorities");
        if (authorities instanceof List) {
            return (List<String>) authorities;
        }
        return Collections.emptyList();
    }
    
    private Map<String, Object> extractAdditionalClaims(Claims claims) {
        Map<String, Object> additionalClaims = new HashMap<>(claims);
        // Remove standard claims
        additionalClaims.remove("sub");
        additionalClaims.remove("iat");
        additionalClaims.remove("exp");
        additionalClaims.remove("authorities");
        return additionalClaims;
    }
    
    private String createOrUpdateKeycloakUser(String username, List<String> authorities, Map<String, Object> additionalClaims) {
        // Implementation would call Keycloak Admin REST API
        // This is a simplified version
        log.debug("Creating/updating Keycloak user: {}", username);
        
        String adminToken = getKeycloakAdminToken();
        String url = String.format("%s/admin/realms/%s/users", keycloakServerUrl, realm);
        
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(adminToken);
        headers.setContentType(MediaType.APPLICATION_JSON);
        
        Map<String, Object> userRepresentation = new HashMap<>();
        userRepresentation.put("username", username);
        userRepresentation.put("enabled", true);
        userRepresentation.put("attributes", additionalClaims);
        
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(userRepresentation, headers);
        
        try {
            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.POST, request, Map.class);
            return (String) response.getBody().get("id");
        } catch (Exception e) {
            log.warn("User might already exist, attempting to get existing user ID");
            return getKeycloakUserId(username);
        }
    }
    
    private KeycloakTokenResponse generateKeycloakToken(String username, List<String> authorities) {
        String tokenUrl = String.format("%s/realms/%s/protocol/openid-connect/token", keycloakServerUrl, realm);
        
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("grant_type", "password");
        params.add("client_id", clientId);
        params.add("client_secret", clientSecret);
        params.add("username", username);
        params.add("scope", String.join(" ", authorities));
        
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        
        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(params, headers);
        
        ResponseEntity<KeycloakTokenResponse> response = restTemplate.postForEntity(
            tokenUrl, request, KeycloakTokenResponse.class
        );
        
        return response.getBody();
    }
    
    private void storeMigrationMapping(String legacyToken, KeycloakTokenResponse keycloakToken, String username) {
        String mappingKey = "migration:mapping:" + username;
        Map<String, Object> mapping = new HashMap<>();
        mapping.put("legacy_token_hash", hashToken(legacyToken));
        mapping.put("keycloak_token", keycloakToken.getAccessToken());
        mapping.put("refresh_token", keycloakToken.getRefreshToken());
        mapping.put("migrated_at", Instant.now().toString());
        
        redisTemplate.opsForHash().putAll(mappingKey, mapping);
        redisTemplate.expire(mappingKey, Duration.ofDays(30));
    }
    
    private String hashToken(String token) {
        // Simple hash for security
        return org.apache.commons.codec.digest.DigestUtils.sha256Hex(token);
    }
    
    private boolean isLegacyToken(String token) {
        try {
            Claims claims = extractClaims(token);
            return !claims.containsKey("azp"); // Keycloak tokens have 'azp' claim
        } catch (Exception e) {
            return false;
        }
    }
    
    private MigrationResult getCachedMigration(String cacheKey) {
        Object cached = redisTemplate.opsForValue().get(cacheKey);
        if (cached != null) {
            return objectMapper.convertValue(cached, MigrationResult.class);
        }
        return null;
    }
    
    private void cacheMigrationResult(String cacheKey, MigrationResult result) {
        redisTemplate.opsForValue().set(cacheKey, result, Duration.ofHours(cacheTtlHours));
    }
    
    private void updateMigrationMetrics(boolean success) {
        String key = success ? "successful" : "failed";
        redisTemplate.opsForHash().increment(MIGRATION_METRICS_KEY, key, 1);
        redisTemplate.opsForHash().increment(MIGRATION_METRICS_KEY, "total_migrated", 1);
        redisTemplate.opsForHash().put(MIGRATION_METRICS_KEY, "last_migration", Instant.now().toString());
    }
    
    private void storeBatchResult(BatchMigrationResult result) {
        String key = "migration:batch:" + UUID.randomUUID();
        redisTemplate.opsForValue().set(key, result, Duration.ofDays(7));
    }
    
    private BatchMigrationResult aggregateBatchResults(List<BatchMigrationResult> results) {
        BatchMigrationResult aggregated = new BatchMigrationResult();
        aggregated.setStartTime(results.get(0).getStartTime());
        aggregated.setEndTime(results.get(results.size() - 1).getEndTime());
        
        int totalTokens = 0;
        int successCount = 0;
        int failureCount = 0;
        
        for (BatchMigrationResult result : results) {
            totalTokens += result.getTotalTokens();
            successCount += result.getSuccessCount();
            failureCount += result.getFailureCount();
        }
        
        aggregated.setTotalTokens(totalTokens);
        aggregated.setSuccessCount(successCount);
        aggregated.setFailureCount(failureCount);
        
        return aggregated;
    }
    
    private String getKeycloakAdminToken() {
        // Get admin token for Keycloak Admin API
        String tokenUrl = String.format("%s/realms/master/protocol/openid-connect/token", keycloakServerUrl);
        
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("grant_type", "client_credentials");
        params.add("client_id", "admin-cli");
        params.add("client_secret", clientSecret);
        
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        
        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(params, headers);
        
        ResponseEntity<Map> response = restTemplate.postForEntity(tokenUrl, request, Map.class);
        return (String) response.getBody().get("access_token");
    }
    
    private String getKeycloakUserId(String username) {
        String adminToken = getKeycloakAdminToken();
        String url = String.format("%s/admin/realms/%s/users?username=%s", keycloakServerUrl, realm, username);
        
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(adminToken);
        
        HttpEntity<Void> request = new HttpEntity<>(headers);
        
        ResponseEntity<List> response = restTemplate.exchange(url, HttpMethod.GET, request, List.class);
        List users = response.getBody();
        
        if (users != null && !users.isEmpty()) {
            Map user = (Map) users.get(0);
            return (String) user.get("id");
        }
        
        return null;
    }
    
    private KeycloakTokenResponse authenticateWithKeycloak(String username) {
        // Simplified - would need actual password or service account
        return generateKeycloakToken(username, Collections.emptyList());
    }
    
    private boolean validateKeycloakToken(String token) {
        String introspectUrl = String.format("%s/realms/%s/protocol/openid-connect/token/introspect", 
            keycloakServerUrl, realm);
        
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("token", token);
        params.add("client_id", clientId);
        params.add("client_secret", clientSecret);
        
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        
        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(params, headers);
        
        ResponseEntity<Map> response = restTemplate.postForEntity(introspectUrl, request, Map.class);
        Map result = response.getBody();
        
        return result != null && Boolean.TRUE.equals(result.get("active"));
    }
    
    private long getLongValue(Object value) {
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        return 0L;
    }
    
    private double getDoubleValue(Object value) {
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        return 0.0;
    }
    
    private Instant getInstantValue(Object value) {
        if (value instanceof String) {
            return Instant.parse((String) value);
        }
        return null;
    }
    
    // Inner classes for results
    
    @lombok.Data
    @lombok.Builder
    public static class MigrationResult {
        private boolean success;
        private String accessToken;
        private String refreshToken;
        private String keycloakUserId;
        private String sessionId;
        private String errorMessage;
        private Instant timestamp;
        
        public static MigrationResult success(String accessToken, String refreshToken, 
                                             String keycloakUserId, String sessionId) {
            return MigrationResult.builder()
                .success(true)
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .keycloakUserId(keycloakUserId)
                .sessionId(sessionId)
                .timestamp(Instant.now())
                .build();
        }
        
        public static MigrationResult failed(String errorMessage) {
            return MigrationResult.builder()
                .success(false)
                .errorMessage(errorMessage)
                .timestamp(Instant.now())
                .build();
        }
    }
    
    @lombok.Data
    public static class BatchMigrationResult {
        private Instant startTime;
        private Instant endTime;
        private int totalTokens;
        private int successCount;
        private int failureCount;
        private List<MigrationResult> results;
        
        public double getSuccessRate() {
            if (totalTokens == 0) return 0;
            return (double) successCount / totalTokens * 100;
        }
        
        public Duration getDuration() {
            if (startTime == null || endTime == null) return Duration.ZERO;
            return Duration.between(startTime, endTime);
        }
    }
    
    @lombok.Data
    public static class MigrationStatistics {
        private long totalMigrated;
        private long successfulMigrations;
        private long failedMigrations;
        private long rolledBack;
        private double successRate;
        private double averageLatencyMs;
        private Instant lastMigrationTime;
    }
    
    @lombok.Data
    static class KeycloakTokenResponse {
        private String accessToken;
        private String refreshToken;
        private String tokenType;
        private int expiresIn;
        private int refreshExpiresIn;
        private String scope;
    }
}