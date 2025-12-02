package com.waqiti.vault.service;

import com.waqiti.vault.service.VaultSecretsService.DatabaseCredentials;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.time.LocalDateTime;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Vault Database Secrets Manager
 * 
 * Manages database credentials from Vault with automatic renewal
 * and connection pool integration for production database access.
 */
@Service
public class VaultDatabaseSecretsManager {

    private static final Logger logger = LoggerFactory.getLogger(VaultDatabaseSecretsManager.class);
    
    private final VaultSecretsService vaultSecretsService;
    private final MeterRegistry meterRegistry;
    
    // Thread-safe storage for database credentials
    private final ConcurrentHashMap<String, DatabaseCredentials> credentialsCache = new ConcurrentHashMap<>();
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    
    // Metrics
    private Counter credentialsRenewedCounter;
    private Counter credentialsExpiredCounter;
    private Counter credentialsErrorCounter;

    public VaultDatabaseSecretsManager(VaultSecretsService vaultSecretsService, MeterRegistry meterRegistry) {
        this.vaultSecretsService = vaultSecretsService;
        this.meterRegistry = meterRegistry;
    }

    @PostConstruct
    private void initializeMetrics() {
        this.credentialsRenewedCounter = Counter.builder("vault.database.credentials.renewed")
                .description("Number of database credentials renewed")
                .register(meterRegistry);
        
        this.credentialsExpiredCounter = Counter.builder("vault.database.credentials.expired")
                .description("Number of database credentials that expired")
                .register(meterRegistry);
        
        this.credentialsErrorCounter = Counter.builder("vault.database.credentials.errors")
                .description("Number of database credential errors")
                .register(meterRegistry);
    }

    /**
     * Get database credentials for a specific role
     * Automatically handles caching and renewal
     */
    public DatabaseCredentials getCredentials(String role) {
        lock.readLock().lock();
        try {
            DatabaseCredentials cached = credentialsCache.get(role);
            
            // Check if cached credentials are still valid
            if (cached != null && !cached.isExpired()) {
                logger.debug("Using cached database credentials for role: {}", role);
                return cached;
            }
        } finally {
            lock.readLock().unlock();
        }
        
        // Need to refresh credentials
        lock.writeLock().lock();
        try {
            // Double-check in case another thread updated while we were waiting
            DatabaseCredentials cached = credentialsCache.get(role);
            if (cached != null && !cached.isExpired()) {
                return cached;
            }
            
            // Fetch new credentials from Vault
            logger.info("Fetching new database credentials for role: {}", role);
            DatabaseCredentials newCredentials = vaultSecretsService.getDatabaseCredentials(role);
            
            if (newCredentials != null) {
                credentialsCache.put(role, newCredentials);
                logger.info("Updated database credentials for role: {} (expires: {})", 
                           role, newCredentials.getExpiresAt());
                return newCredentials;
            } else {
                logger.error("Failed to fetch database credentials for role: {}", role);
                credentialsErrorCounter.increment("role", role, "type", "fetch_failed");
                throw new VaultSecretsException("Failed to fetch database credentials for role: " + role);
            }
            
        } catch (VaultSecretsException e) {
            throw e; // Re-throw our own exceptions
        } catch (Exception e) {
            logger.error("Error fetching database credentials for role: {}", role, e);
            credentialsErrorCounter.increment("role", role, "type", "exception");
            throw new VaultSecretsException("Error fetching database credentials for role: " + role, e);
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Get connection URL with embedded credentials
     */
    public String getConnectionUrl(String role, String baseUrl) {
        DatabaseCredentials credentials = getCredentials(role);
        if (credentials == null) {
            throw new IllegalStateException("No database credentials available for role: " + role);
        }
        
        return baseUrl.replace("{username}", credentials.getUsername())
                     .replace("{password}", credentials.getPassword());
    }

    /**
     * Manually renew credentials for a role
     */
    public boolean renewCredentials(String role) {
        lock.writeLock().lock();
        try {
            DatabaseCredentials current = credentialsCache.get(role);
            if (current != null) {
                logger.info("Manually renewing database credentials for role: {}", role);
                
                try {
                    // Try to renew the existing lease first
                    vaultSecretsService.renewDatabaseCredentials(current.getLeaseId());
                    credentialsRenewedCounter.increment("role", role, "type", "lease_renewal");
                    logger.info("Successfully renewed lease for role: {}", role);
                    return true;
                    
                } catch (Exception e) {
                    logger.warn("Failed to renew lease for role: {}, fetching new credentials", role, e);
                    
                    // If renewal fails, get new credentials
                    DatabaseCredentials newCredentials = vaultSecretsService.getDatabaseCredentials(role);
                    if (newCredentials != null) {
                        credentialsCache.put(role, newCredentials);
                        credentialsRenewedCounter.increment("role", role, "type", "new_credentials");
                        logger.info("Fetched new database credentials for role: {}", role);
                        return true;
                    }
                }
            }
            
            // No current credentials, fetch new ones
            DatabaseCredentials newCredentials = vaultSecretsService.getDatabaseCredentials(role);
            if (newCredentials != null) {
                credentialsCache.put(role, newCredentials);
                credentialsRenewedCounter.increment("role", role, "type", "initial_fetch");
                logger.info("Fetched initial database credentials for role: {}", role);
                return true;
            }
            
            logger.error("Failed to renew/fetch database credentials for role: {}", role);
            credentialsErrorCounter.increment("role", role, "type", "renewal_failed");
            return false;
            
        } catch (Exception e) {
            logger.error("Error renewing database credentials for role: {}", role, e);
            credentialsErrorCounter.increment("role", role, "type", "renewal_exception");
            return false;
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Scheduled task to check and renew expiring credentials
     * Runs every 5 minutes
     */
    @Scheduled(fixedRate = 300000, initialDelay = 60000)
    public void checkExpiringCredentials() {
        logger.debug("Checking for expiring database credentials");
        
        for (String role : credentialsCache.keySet()) {
            try {
                DatabaseCredentials credentials = credentialsCache.get(role);
                
                if (credentials != null && credentials.isExpired()) {
                    logger.info("Database credentials expiring soon for role: {}, renewing", role);
                    
                    boolean renewed = renewCredentials(role);
                    if (renewed) {
                        logger.info("Successfully renewed expiring credentials for role: {}", role);
                    } else {
                        logger.error("Failed to renew expiring credentials for role: {}", role);
                        credentialsExpiredCounter.increment("role", role);
                    }
                }
                
            } catch (Exception e) {
                logger.error("Error checking expiring credentials for role: {}", role, e);
                credentialsErrorCounter.increment("role", role, "type", "expiry_check_error");
            }
        }
    }

    /**
     * Invalidate cached credentials for a role
     */
    public void invalidateCredentials(String role) {
        lock.writeLock().lock();
        try {
            DatabaseCredentials removed = credentialsCache.remove(role);
            if (removed != null) {
                logger.info("Invalidated cached database credentials for role: {}", role);
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Get all cached roles
     */
    public java.util.Set<String> getCachedRoles() {
        return credentialsCache.keySet();
    }

    /**
     * Get credentials status for a role
     */
    public CredentialsStatus getCredentialsStatus(String role) {
        lock.readLock().lock();
        try {
            DatabaseCredentials credentials = credentialsCache.get(role);
            
            if (credentials == null) {
                return new CredentialsStatus(role, false, null, false, "No credentials cached");
            }
            
            boolean expired = credentials.isExpired();
            return new CredentialsStatus(
                role, 
                true, 
                credentials.getExpiresAt(), 
                expired,
                expired ? "Credentials expired" : "Credentials valid"
            );
            
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Get status for all cached credentials
     */
    public java.util.Map<String, CredentialsStatus> getAllCredentialsStatus() {
        java.util.Map<String, CredentialsStatus> statusMap = new java.util.HashMap<>();
        
        for (String role : credentialsCache.keySet()) {
            statusMap.put(role, getCredentialsStatus(role));
        }
        
        return statusMap;
    }

    /**
     * Clear all cached credentials
     */
    public void clearAllCredentials() {
        lock.writeLock().lock();
        try {
            int count = credentialsCache.size();
            credentialsCache.clear();
            logger.info("Cleared {} cached database credentials", count);
        } finally {
            lock.writeLock().unlock();
        }
    }

    @PreDestroy
    public void cleanup() {
        logger.info("Cleaning up VaultDatabaseSecretsManager");
        clearAllCredentials();
    }

    /**
     * Credentials Status class
     */
    public static class CredentialsStatus {
        private final String role;
        private final boolean cached;
        private final LocalDateTime expiresAt;
        private final boolean expired;
        private final String status;

        public CredentialsStatus(String role, boolean cached, LocalDateTime expiresAt, 
                               boolean expired, String status) {
            this.role = role;
            this.cached = cached;
            this.expiresAt = expiresAt;
            this.expired = expired;
            this.status = status;
        }

        // Getters
        public String getRole() { return role; }
        public boolean isCached() { return cached; }
        public LocalDateTime getExpiresAt() { return expiresAt; }
        public boolean isExpired() { return expired; }
        public String getStatus() { return status; }
        
        public boolean isValid() {
            return cached && !expired;
        }

        @Override
        public String toString() {
            return "CredentialsStatus{" +
                    "role='" + role + '\'' +
                    ", cached=" + cached +
                    ", expiresAt=" + expiresAt +
                    ", expired=" + expired +
                    ", status='" + status + '\'' +
                    '}';
        }
    }
}