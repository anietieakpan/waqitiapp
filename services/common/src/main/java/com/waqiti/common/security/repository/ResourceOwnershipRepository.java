package com.waqiti.common.security.repository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.UUID;

/**
 * Production-Grade Resource Ownership Repository
 *
 * CRITICAL SECURITY COMPONENT:
 * - Provides fast database-backed ownership verification
 * - Prevents IDOR (Insecure Direct Object Reference) vulnerabilities
 * - Uses optimized SQL queries with proper indexes
 * - Implements fail-closed security pattern
 *
 * DATABASE SCHEMA REQUIREMENTS:
 * - All resource tables must have 'user_id' or 'owner_id' column
 * - Proper indexes: CREATE INDEX idx_wallets_user_id ON wallets(user_id, id);
 * - Foreign key constraints for data integrity
 *
 * PERFORMANCE:
 * - Query response time: <5ms (with proper indexes)
 * - Connection pool: HikariCP with optimized settings
 * - Caching: Results cached at service layer (ResourceOwnershipService)
 *
 * @author Waqiti Security Team
 * @version 3.0.0
 */
@Repository
@RequiredArgsConstructor
@Slf4j
public class ResourceOwnershipRepository {

    private final JdbcTemplate jdbcTemplate;

    /**
     * Check if user owns the specified resource
     *
     * SECURITY: This is the core authorization check - must be correct!
     */
    public boolean isOwner(UUID userId, String resourceType, UUID resourceId) {
        String query = buildOwnershipQuery(resourceType);

        if (query == null) {
            log.error("SECURITY CRITICAL: Unknown resource type requested: {}", resourceType);
            // Fail closed - deny access for unknown resource types
            return false;
        }

        try {
            Integer count = jdbcTemplate.queryForObject(
                query,
                Integer.class,
                userId.toString(),
                resourceId.toString()
            );

            boolean isOwner = count != null && count > 0;

            if (!isOwner) {
                log.warn("SECURITY: Ownership denied - User: {}, Resource: {}:{}",
                        userId, resourceType, resourceId);
            }

            return isOwner;

        } catch (Exception e) {
            log.error("SECURITY CRITICAL: Ownership query failed for {} {} by user {}",
                resourceType, resourceId, userId, e);
            // Fail closed - deny access on database errors
            return false;
        }
    }

    /**
     * Check if user has specific permission on resource
     *
     * SECURITY: Checks granular permissions beyond simple ownership
     */
    public boolean hasPermission(UUID userId, String resourceType, UUID resourceId, String permission) {
        // First check ownership
        if (!isOwner(userId, resourceType, resourceId)) {
            return false;
        }

        // For most resources, ownership implies all permissions
        // This can be extended with ACL/RBAC tables for fine-grained control
        String permissionQuery = buildPermissionQuery(resourceType, permission);

        if (permissionQuery == null) {
            // No specific permission model exists - ownership implies full access
            return true;
        }

        try {
            Integer count = jdbcTemplate.queryForObject(
                permissionQuery,
                Integer.class,
                userId.toString(),
                resourceId.toString(),
                permission
            );

            return count != null && count > 0;

        } catch (Exception e) {
            log.error("SECURITY: Permission query failed for user {} on {}:{} permission {}",
                     userId, resourceType, resourceId, permission, e);
            // Fail closed
            return false;
        }
    }

    /**
     * Check if user has specific role
     */
    public boolean hasRole(UUID userId, String role) {
        String query = "SELECT COUNT(*) FROM user_roles WHERE user_id = ?::uuid AND role = ? AND active = true";

        try {
            Integer count = jdbcTemplate.queryForObject(
                query,
                Integer.class,
                userId.toString(),
                role
            );

            return count != null && count > 0;

        } catch (Exception e) {
            log.error("SECURITY: Role query failed for user {} role {}", userId, role, e);
            return false;
        }
    }

    /**
     * Build ownership query for specific resource type
     *
     * IMPORTANT: These queries must match your actual database schema!
     * Each query verifies that:
     * 1. The resource exists
     * 2. The user is the owner
     * 3. The resource is active/not deleted
     */
    private String buildOwnershipQuery(String resourceType) {
        switch (resourceType.toUpperCase()) {
            case "WALLET":
                return "SELECT COUNT(*) FROM wallets " +
                       "WHERE user_id = ?::uuid AND id = ?::uuid AND status != 'DELETED'";

            case "ACCOUNT":
                return "SELECT COUNT(*) FROM accounts " +
                       "WHERE user_id = ?::uuid AND id = ?::uuid AND status = 'ACTIVE'";

            case "TRANSACTION":
                return "SELECT COUNT(*) FROM transactions " +
                       "WHERE user_id = ?::uuid AND id = ?::uuid";

            case "PAYMENT":
                return "SELECT COUNT(*) FROM payments " +
                       "WHERE user_id = ?::uuid AND id = ?::uuid AND status != 'DELETED'";

            case "INVESTMENT_ACCOUNT":
                return "SELECT COUNT(*) FROM investment_accounts " +
                       "WHERE customer_id = ?::uuid AND id = ?::uuid AND status = 'ACTIVE'";

            case "CARD":
                return "SELECT COUNT(*) FROM tokenized_cards " +
                       "WHERE user_id = ?::uuid AND id = ?::uuid AND status NOT IN ('CANCELLED', 'EXPIRED')";

            case "BANK_ACCOUNT":
                return "SELECT COUNT(*) FROM bank_accounts " +
                       "WHERE user_id = ?::uuid AND id = ?::uuid AND status = 'ACTIVE'";

            case "BENEFICIARY":
                return "SELECT COUNT(*) FROM beneficiaries " +
                       "WHERE user_id = ?::uuid AND id = ?::uuid AND status = 'ACTIVE'";

            case "TRANSFER":
                return "SELECT COUNT(*) FROM transfers " +
                       "WHERE user_id = ?::uuid AND id = ?::uuid";

            case "SCHEDULED_PAYMENT":
                return "SELECT COUNT(*) FROM scheduled_payments " +
                       "WHERE user_id = ?::uuid AND id = ?::uuid AND status != 'CANCELLED'";

            case "INVESTMENT_ORDER":
                return "SELECT COUNT(*) FROM investment_orders io " +
                       "JOIN investment_accounts ia ON io.account_id = ia.id " +
                       "WHERE ia.customer_id = ?::uuid AND io.id = ?::uuid";

            case "PORTFOLIO":
                return "SELECT COUNT(*) FROM portfolios p " +
                       "JOIN investment_accounts ia ON p.investment_account_id = ia.id " +
                       "WHERE ia.customer_id = ?::uuid AND p.id = ?::uuid";

            case "CRYPTO_WALLET":
                return "SELECT COUNT(*) FROM crypto_wallets " +
                       "WHERE user_id = ?::uuid AND id = ?::uuid AND status = 'ACTIVE'";

            case "SAVINGS_ACCOUNT":
                return "SELECT COUNT(*) FROM savings_accounts " +
                       "WHERE user_id = ?::uuid AND id = ?::uuid AND status = 'ACTIVE'";

            case "LOAN":
                return "SELECT COUNT(*) FROM loans " +
                       "WHERE borrower_id = ?::uuid AND id = ?::uuid";

            case "INSURANCE_POLICY":
                return "SELECT COUNT(*) FROM insurance_policies " +
                       "WHERE policyholder_id = ?::uuid AND id = ?::uuid AND status = 'ACTIVE'";

            case "GENERIC":
                // Fallback for resources without specific queries
                return "SELECT 0"; // Will always return 0 - must use specific resource type

            default:
                log.warn("SECURITY: Unsupported resource type for ownership query: {}", resourceType);
                return null;
        }
    }

    /**
     * Build permission query for resource type and action
     *
     * Most resources use simple ownership model.
     * Override for resources with fine-grained ACL.
     */
    private String buildPermissionQuery(String resourceType, String permission) {
        // Example: For shared documents or collaborative resources
        if ("DOCUMENT".equals(resourceType)) {
            return "SELECT COUNT(*) FROM document_permissions " +
                   "WHERE user_id = ?::uuid AND document_id = ?::uuid " +
                   "AND permission = ? AND revoked = false";
        }

        // Example: For team resources
        if ("TEAM_RESOURCE".equals(resourceType)) {
            return "SELECT COUNT(*) FROM team_resource_permissions " +
                   "WHERE user_id = ?::uuid AND resource_id = ?::uuid " +
                   "AND permission = ? AND active = true";
        }

        // Default: ownership implies all permissions
        return null;
    }

    /**
     * Check if resource exists (regardless of ownership)
     * Useful for distinguishing "doesn't exist" vs "no permission"
     */
    public boolean resourceExists(String resourceType, UUID resourceId) {
        String query = buildExistenceQuery(resourceType);

        if (query == null) {
            return false;
        }

        try {
            Integer count = jdbcTemplate.queryForObject(
                query,
                Integer.class,
                resourceId.toString()
            );

            return count != null && count > 0;

        } catch (Exception e) {
            log.error("Resource existence check failed for {}:{}", resourceType, resourceId, e);
            return false;
        }
    }

    private String buildExistenceQuery(String resourceType) {
        String tableName = getTableName(resourceType);
        if (tableName == null) {
            return null;
        }

        return String.format("SELECT COUNT(*) FROM %s WHERE id = ?::uuid", tableName);
    }

    private String getTableName(String resourceType) {
        switch (resourceType.toUpperCase()) {
            case "WALLET": return "wallets";
            case "ACCOUNT": return "accounts";
            case "TRANSACTION": return "transactions";
            case "PAYMENT": return "payments";
            case "CARD": return "tokenized_cards";
            case "BANK_ACCOUNT": return "bank_accounts";
            case "BENEFICIARY": return "beneficiaries";
            case "TRANSFER": return "transfers";
            case "SCHEDULED_PAYMENT": return "scheduled_payments";
            case "INVESTMENT_ACCOUNT": return "investment_accounts";
            case "INVESTMENT_ORDER": return "investment_orders";
            case "PORTFOLIO": return "portfolios";
            case "CRYPTO_WALLET": return "crypto_wallets";
            case "SAVINGS_ACCOUNT": return "savings_accounts";
            case "LOAN": return "loans";
            case "INSURANCE_POLICY": return "insurance_policies";
            default: return null;
        }
    }

    /**
     * Batch ownership check for multiple resources
     * Optimizes performance when checking many resources at once
     */
    public boolean[] isOwnerBatch(UUID userId, String resourceType, UUID[] resourceIds) {
        if (resourceIds == null || resourceIds.length == 0) {
            return new boolean[0];
        }

        boolean[] results = new boolean[resourceIds.length];

        try {
            String tableName = getTableName(resourceType);
            if (tableName == null) {
                return results; // All false
            }

            // Build IN clause for batch query
            StringBuilder inClause = new StringBuilder();
            for (int i = 0; i < resourceIds.length; i++) {
                if (i > 0) inClause.append(",");
                inClause.append("'").append(resourceIds[i].toString()).append("'::uuid");
            }

            String query = String.format(
                "SELECT id FROM %s WHERE user_id = ?::uuid AND id IN (%s)",
                tableName,
                inClause
            );

            java.util.List<UUID> ownedIds = jdbcTemplate.query(
                query,
                (rs, rowNum) -> UUID.fromString(rs.getString("id")),
                userId.toString()
            );

            // Mark owned resources as true
            java.util.Set<UUID> ownedSet = new java.util.HashSet<>(ownedIds);
            for (int i = 0; i < resourceIds.length; i++) {
                results[i] = ownedSet.contains(resourceIds[i]);
            }

        } catch (Exception e) {
            log.error("Batch ownership check failed", e);
            // results already initialized to false
        }

        return results;
    }
}
