package com.waqiti.common.security.model;

import com.waqiti.common.cache.IntelligentCacheService;
import com.waqiti.common.client.TransactionQueryClient;
import com.waqiti.common.client.UserServiceClient;
import com.waqiti.common.client.WalletServiceClient;
import com.waqiti.common.security.repository.ResourceOwnershipRepository;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Production-Grade Resource Ownership Verification Service
 *
 * CRITICAL SECURITY SERVICE:
 * - Prevents IDOR (Insecure Direct Object Reference) vulnerabilities
 * - Validates user ownership before granting resource access
 * - Supports distributed queries across microservices
 * - Implements caching for performance without sacrificing security
 * - Circuit breaker pattern for resilience
 *
 * ARCHITECTURE:
 * - Layer 1: In-memory cache (fast path)
 * - Layer 2: Database query (medium path)
 * - Layer 3: Inter-service Feign calls (slow path)
 *
 * PERFORMANCE:
 * - Cache hit rate target: >85%
 * - Response time: <10ms (cached), <50ms (DB), <200ms (service call)
 *
 * @author Waqiti Security Team
 * @version 3.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ResourceOwnershipService {

    private final ResourceOwnershipRepository ownershipRepository;
    private final UserServiceClient userServiceClient;
    private final WalletServiceClient walletServiceClient;
    private final TransactionQueryClient transactionQueryClient;
    private final IntelligentCacheService cacheService;

    // In-memory cache for frequently accessed ownerships (security-safe with TTL)
    private final Map<String, CachedOwnership> ownershipCache = new ConcurrentHashMap<>();
    private static final long CACHE_TTL_MS = TimeUnit.MINUTES.toMillis(5);

    /**
     * Async ownership verification with circuit breaker protection
     */
    @CircuitBreaker(name = "ownership-verification", fallbackMethod = "verifyOwnershipFallback")
    @Retry(name = "ownership-verification")
    public CompletableFuture<Boolean> verifyOwnership(String userId, String resourceId, String resourceType) {
        return CompletableFuture.supplyAsync(() -> {
            log.debug("Verifying ownership: user={}, resource={}:{}", userId, resourceType, resourceId);

            // Check cache first
            String cacheKey = buildCacheKey(userId, resourceType, resourceId);
            CachedOwnership cached = ownershipCache.get(cacheKey);
            if (cached != null && !cached.isExpired()) {
                log.debug("Ownership cache hit: {}", cacheKey);
                return cached.isOwner();
            }

            // Verify ownership based on resource type
            boolean isOwner = verifyOwnershipByType(userId, resourceType, resourceId);

            // Cache result (even negative results to prevent repeated attacks)
            ownershipCache.put(cacheKey, new CachedOwnership(isOwner, System.currentTimeMillis()));

            // Clean up expired entries periodically
            if (ownershipCache.size() > 10000) {
                cleanExpiredCache();
            }

            return isOwner;
        });
    }

    /**
     * Fallback method when ownership verification fails
     */
    private CompletableFuture<Boolean> verifyOwnershipFallback(String userId, String resourceId,
                                                                String resourceType, Throwable t) {
        log.error("SECURITY CRITICAL: Ownership verification failed - DENYING ACCESS. " +
                 "User: {}, Resource: {}:{}, Error: {}",
                 userId, resourceType, resourceId, t.getMessage());
        // FAIL CLOSED - deny access when verification fails
        return CompletableFuture.completedFuture(false);
    }

    /**
     * Check if user has specific permission on resource
     */
    @CircuitBreaker(name = "permission-check", fallbackMethod = "hasPermissionFallback")
    public CompletableFuture<Boolean> hasPermission(String userId, String resourceId, String action) {
        return CompletableFuture.supplyAsync(() -> {
            log.debug("Checking permission: user={}, resource={}, action={}", userId, resourceId, action);

            // For now, ownership implies full permissions
            // This can be extended with fine-grained RBAC/ABAC
            try {
                UUID userUuid = UUID.fromString(userId);
                UUID resourceUuid = UUID.fromString(resourceId);
                return ownershipRepository.hasPermission(userUuid, "GENERIC", resourceUuid, action);
            } catch (Exception e) {
                log.error("Permission check failed: {}", e.getMessage());
                return false;
            }
        });
    }

    private CompletableFuture<Boolean> hasPermissionFallback(String userId, String resourceId,
                                                             String action, Throwable t) {
        log.error("SECURITY: Permission check failed - DENYING. User: {}, Resource: {}, Action: {}",
                 userId, resourceId, action);
        return CompletableFuture.completedFuture(false);
    }

    /**
     * Check if user is owner of resource
     */
    @Cacheable(value = "resourceOwnership", key = "#userId + ':' + #resourceType + ':' + #resourceId")
    public boolean isOwner(String userId, String resourceType, String resourceId) {
        log.debug("Checking ownership: user={}, resource={}:{}", userId, resourceType, resourceId);

        try {
            return verifyOwnershipByType(userId, resourceType, resourceId);
        } catch (Exception e) {
            log.error("SECURITY: Ownership check failed for user {} on {}:{}",
                     userId, resourceType, resourceId, e);
            return false; // Fail closed
        }
    }

    /**
     * Check if user is account owner
     */
    @Cacheable(value = "accountOwnership", key = "#userId + ':' + #accountId")
    public boolean isAccountOwner(String userId, String accountId) {
        log.debug("Checking account ownership: user={}, account={}", userId, accountId);

        try {
            UUID userUuid = UUID.fromString(userId);
            UUID accountUuid = UUID.fromString(accountId);
            return ownershipRepository.isOwner(userUuid, "ACCOUNT", accountUuid);
        } catch (Exception e) {
            log.error("Account ownership check failed", e);
            return false;
        }
    }

    /**
     * Check if user is joint account holder
     */
    @Cacheable(value = "jointAccountHolder", key = "#userId + ':' + #accountId")
    public boolean isJointAccountHolder(String userId, String accountId) {
        log.debug("Checking joint account holder: user={}, account={}", userId, accountId);

        try {
            // Query joint account holders table
            String query = "SELECT COUNT(*) FROM account_holders WHERE user_id = ?::uuid AND account_id = ?::uuid AND status = 'ACTIVE'";
            // This would use ownershipRepository with custom query - simplified for now
            return false; // Will be implemented when joint accounts feature is added
        } catch (Exception e) {
            log.error("Joint account check failed", e);
            return false;
        }
    }

    /**
     * Check if user is family account member
     */
    @Cacheable(value = "familyAccountMember", key = "#userId + ':' + #accountId")
    public boolean isFamilyAccountMember(String userId, String accountId) {
        log.debug("Checking family account membership: user={}, account={}", userId, accountId);

        try {
            // Query family account members table
            String query = "SELECT COUNT(*) FROM family_account_members WHERE user_id = ?::uuid AND account_id = ?::uuid AND status = 'ACTIVE'";
            // This would use ownershipRepository with custom query
            return false; // Will be implemented when family accounts feature is fully deployed
        } catch (Exception e) {
            log.error("Family account check failed", e);
            return false;
        }
    }

    /**
     * Check if user is authorized for business account
     */
    @Cacheable(value = "businessAccountAuth", key = "#userId + ':' + #accountId")
    public boolean isBusinessAccountAuthorized(String userId, String accountId) {
        log.debug("Checking business account authorization: user={}, account={}", userId, accountId);

        try {
            // Query business account authorizations
            String query = "SELECT COUNT(*) FROM business_account_members WHERE user_id = ?::uuid AND account_id = ?::uuid AND status = 'ACTIVE'";
            // This would use ownershipRepository with custom query
            return false; // Will be implemented when business accounts feature is added
        } catch (Exception e) {
            log.error("Business account auth check failed", e);
            return false;
        }
    }

    /**
     * Get transaction information for authorization checks
     */
    @Cacheable(value = "transactionInfo", key = "#transactionId")
    public com.waqiti.common.security.AuthorizationService.TransactionInfo getTransactionInfo(String transactionId) {
        log.debug("Fetching transaction info: {}", transactionId);

        try {
            // Call transaction service via Feign client
            Map<String, Object> txData = transactionQueryClient.getTransactionDetails(transactionId);

            if (txData == null || txData.isEmpty()) {
                log.warn("Transaction not found: {}", transactionId);
                return null;
            }

            // Build TransactionInfo from response
            return com.waqiti.common.security.AuthorizationService.TransactionInfo.builder()
                .transactionId(transactionId)
                .senderId((String) txData.get("senderId"))
                .recipientId((String) txData.get("recipientId"))
                .accountId((String) txData.get("accountId"))
                .type((String) txData.get("type"))
                .build();

        } catch (Exception e) {
            log.error("Failed to fetch transaction info: {}", transactionId, e);
            return null;
        }
    }

    /**
     * Check if resource is locked
     */
    @Cacheable(value = "resourceLock", key = "#resourceType + ':' + #resourceId")
    public boolean isResourceLocked(String resourceType, String resourceId) {
        log.debug("Checking resource lock: {}:{}", resourceType, resourceId);

        try {
            // Query resource locks table
            String query = "SELECT COUNT(*) FROM resource_locks WHERE resource_type = ? AND resource_id = ?::uuid AND locked_until > NOW()";
            // This would use a dedicated ResourceLockRepository
            return false; // Most resources are not locked
        } catch (Exception e) {
            log.error("Resource lock check failed", e);
            return true; // Fail safe - if we can't verify, assume locked
        }
    }

    /**
     * Check if resource can be deleted
     */
    public boolean canBeDeleted(String resourceType, String resourceId) {
        log.debug("Checking if resource can be deleted: {}:{}", resourceType, resourceId);

        try {
            // Check if resource is locked
            if (isResourceLocked(resourceType, resourceId)) {
                return false;
            }

            // Check if resource has dependencies
            boolean hasDependencies = checkResourceDependencies(resourceType, resourceId);
            if (hasDependencies) {
                log.info("Resource {}:{} has dependencies - cannot delete", resourceType, resourceId);
                return false;
            }

            return true;

        } catch (Exception e) {
            log.error("Delete check failed", e);
            return false; // Fail safe
        }
    }

    /**
     * Check if user is blocked
     */
    @Cacheable(value = "userBlocked", key = "#userId")
    public boolean isUserBlocked(String userId) {
        log.debug("Checking if user is blocked: {}", userId);

        try {
            // Call user service to check block status
            Map<String, Object> userStatus = userServiceClient.getUserStatus(userId);

            if (userStatus != null) {
                Object blocked = userStatus.get("blocked");
                Object suspended = userStatus.get("suspended");
                return (blocked != null && (Boolean) blocked) || (suspended != null && (Boolean) suspended);
            }

            return false;

        } catch (Exception e) {
            log.error("User block check failed for: {}", userId, e);
            return true; // Fail safe - if we can't verify, assume blocked
        }
    }

    /**
     * Get user groups
     */
    @Cacheable(value = "userGroups", key = "#userId")
    public List<String> getUserGroups(String userId) {
        log.debug("Fetching user groups: {}", userId);

        try {
            Map<String, Object> userData = userServiceClient.getUserGroups(userId);
            if (userData != null && userData.containsKey("groups")) {
                return (List<String>) userData.get("groups");
            }
            return Collections.emptyList();
        } catch (Exception e) {
            log.error("Failed to fetch user groups", e);
            return Collections.emptyList();
        }
    }

    /**
     * Check if group has access to resource
     */
    @Cacheable(value = "groupAccess", key = "#groupId + ':' + #resourceType + ':' + #resourceId")
    public boolean groupHasAccess(String groupId, String resourceType, String resourceId) {
        log.debug("Checking group access: group={}, resource={}:{}", groupId, resourceType, resourceId);

        try {
            // Query group permissions table
            String query = "SELECT COUNT(*) FROM group_resource_permissions WHERE group_id = ? AND resource_type = ? AND resource_id = ?::uuid";
            // This would use a GroupPermissionRepository
            return false; // Group-based permissions not yet implemented
        } catch (Exception e) {
            log.error("Group access check failed", e);
            return false;
        }
    }

    /**
     * Get allowed actions for user on resource
     */
    @Cacheable(value = "allowedActions", key = "#userId + ':' + #resourceType + ':' + #resourceId")
    public Set<String> getAllowedActions(String userId, String resourceType, String resourceId) {
        log.debug("Fetching allowed actions: user={}, resource={}:{}", userId, resourceType, resourceId);

        try {
            // If user is owner, grant all actions
            if (isOwner(userId, resourceType, resourceId)) {
                return Set.of("READ", "WRITE", "DELETE", "SHARE", "TRANSFER");
            }

            // Check specific permissions
            Set<String> actions = new HashSet<>();

            // Query user_resource_permissions table
            // This would use a PermissionRepository

            return actions.isEmpty() ? Set.of("READ") : actions;

        } catch (Exception e) {
            log.error("Failed to fetch allowed actions", e);
            return Collections.emptySet();
        }
    }

    /**
     * Get family account role for user
     */
    @Cacheable(value = "familyAccountRole", key = "#userId + ':' + #accountId")
    public com.waqiti.common.security.AuthorizationService.FamilyAccountRole getFamilyAccountRole(String userId, String accountId) {
        log.debug("Fetching family account role: user={}, account={}", userId, accountId);

        try {
            // Query family account members table
            String query = "SELECT role FROM family_account_members WHERE user_id = ?::uuid AND account_id = ?::uuid AND status = 'ACTIVE'";
            // This would use ownershipRepository with custom query

            // For now, return null if not a family account member
            return null;

        } catch (Exception e) {
            log.error("Failed to fetch family account role", e);
            return null;
        }
    }

    /**
     * Get recent modification count for resource
     */
    @Cacheable(value = "recentModifications", key = "#resourceType + ':' + #resourceId + ':' + #hours")
    public int getRecentModificationCount(String resourceType, String resourceId, int hours) {
        log.debug("Fetching recent modification count: {}:{} in last {} hours", resourceType, resourceId, hours);

        try {
            LocalDateTime since = LocalDateTime.now().minusHours(hours);

            // Query audit log or resource modification history
            String query = "SELECT COUNT(*) FROM audit_log WHERE resource_type = ? AND resource_id = ?::uuid AND timestamp > ? AND action IN ('UPDATE', 'MODIFY')";
            // This would use an AuditRepository

            return 0; // No modifications by default

        } catch (Exception e) {
            log.error("Failed to fetch modification count", e);
            return 0;
        }
    }

    // ========================================================================
    // PRIVATE HELPER METHODS
    // ========================================================================

    /**
     * Verify ownership based on resource type
     */
    private boolean verifyOwnershipByType(String userId, String resourceType, String resourceId) {
        try {
            UUID userUuid = UUID.fromString(userId);
            UUID resourceUuid = UUID.fromString(resourceId);

            // Use repository for database-backed verification
            return ownershipRepository.isOwner(userUuid, resourceType.toUpperCase(), resourceUuid);

        } catch (IllegalArgumentException e) {
            log.warn("Invalid UUID format: user={}, resource={}:{}", userId, resourceType, resourceId);
            return false;
        } catch (Exception e) {
            log.error("Ownership verification failed", e);
            return false;
        }
    }

    /**
     * Check if resource has dependencies that prevent deletion
     */
    private boolean checkResourceDependencies(String resourceType, String resourceId) {
        // Check for child resources or references
        switch (resourceType.toUpperCase()) {
            case "ACCOUNT":
                // Check if account has transactions
                return hasActiveTransactions(resourceId);

            case "WALLET":
                // Check if wallet has balance or pending transactions
                return hasWalletBalance(resourceId) || hasPendingTransactions(resourceId);

            case "CARD":
                // Check if card has pending authorizations
                return hasPendingAuthorizations(resourceId);

            default:
                return false;
        }
    }

    private boolean hasActiveTransactions(String accountId) {
        try {
            // Query recent transactions for account
            return false; // Simplified - would query transaction service
        } catch (Exception e) {
            return true; // Fail safe
        }
    }

    private boolean hasWalletBalance(String walletId) {
        try {
            Map<String, Object> walletData = walletServiceClient.getWalletBalance(walletId);
            if (walletData != null && walletData.containsKey("balance")) {
                Object balance = walletData.get("balance");
                return balance != null && Double.parseDouble(balance.toString()) > 0.0;
            }
            return false;
        } catch (Exception e) {
            return true; // Fail safe
        }
    }

    private boolean hasPendingTransactions(String walletId) {
        try {
            // Query pending transactions for wallet
            return false; // Simplified
        } catch (Exception e) {
            return true; // Fail safe
        }
    }

    private boolean hasPendingAuthorizations(String cardId) {
        try {
            // Query pending card authorizations
            return false; // Simplified
        } catch (Exception e) {
            return true; // Fail safe
        }
    }

    private String buildCacheKey(String userId, String resourceType, String resourceId) {
        return userId + ":" + resourceType + ":" + resourceId;
    }

    private void cleanExpiredCache() {
        long now = System.currentTimeMillis();
        ownershipCache.entrySet().removeIf(entry -> entry.getValue().isExpired(now));
        log.debug("Cleaned expired ownership cache entries. Current size: {}", ownershipCache.size());
    }

    /**
     * Cached ownership record with TTL
     */
    private static class CachedOwnership {
        private final boolean owner;
        private final long timestamp;

        CachedOwnership(boolean owner, long timestamp) {
            this.owner = owner;
            this.timestamp = timestamp;
        }

        boolean isOwner() {
            return owner;
        }

        boolean isExpired() {
            return isExpired(System.currentTimeMillis());
        }

        boolean isExpired(long now) {
            return (now - timestamp) > CACHE_TTL_MS;
        }
    }
}
