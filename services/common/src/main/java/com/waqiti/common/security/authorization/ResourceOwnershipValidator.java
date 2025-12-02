package com.waqiti.common.security.authorization;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Enhanced Resource Ownership Validator that queries actual database tables
 * to validate resource ownership for financial operations.
 * 
 * This service addresses the critical authorization bypass vulnerability by
 * implementing proper database-level ownership validation.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ResourceOwnershipValidator {

    private final JdbcTemplate jdbcTemplate;
    
    /**
     * Validates wallet ownership
     */
    @Cacheable(value = "wallet-ownership", key = "#userId + ':' + #walletId")
    public boolean isWalletOwner(UUID userId, UUID walletId) {
        try {
            String sql = "SELECT COUNT(*) FROM wallets WHERE id = ? AND user_id = ? AND status != 'CLOSED'";
            Integer count = jdbcTemplate.queryForObject(sql, Integer.class, walletId, userId);
            return count != null && count > 0;
        } catch (Exception e) {
            log.error("Error checking wallet ownership: userId={}, walletId={}", userId, walletId, e);
            return false;
        }
    }
    
    /**
     * Validates payment ownership (either as sender or recipient)
     */
    @Cacheable(value = "payment-ownership", key = "#userId + ':' + #paymentId")
    public boolean isPaymentParticipant(UUID userId, UUID paymentId) {
        try {
            String sql = """
                SELECT COUNT(*) FROM payments p
                LEFT JOIN wallets sw ON p.source_wallet_id = sw.id
                LEFT JOIN wallets tw ON p.target_wallet_id = tw.id
                WHERE p.id = ? 
                AND (sw.user_id = ? OR tw.user_id = ? OR p.requestor_id = ? OR p.recipient_id = ?)
                """;
            Integer count = jdbcTemplate.queryForObject(sql, Integer.class, 
                    paymentId, userId, userId, userId, userId);
            return count != null && count > 0;
        } catch (Exception e) {
            log.error("Error checking payment ownership: userId={}, paymentId={}", userId, paymentId, e);
            return false;
        }
    }
    
    /**
     * Validates transaction ownership (either as source or target wallet owner)
     */
    @Cacheable(value = "transaction-ownership", key = "#userId + ':' + #transactionId")
    public boolean isTransactionParticipant(UUID userId, UUID transactionId) {
        try {
            String sql = """
                SELECT COUNT(*) FROM transactions t
                LEFT JOIN wallets sw ON t.source_wallet_id = sw.id
                LEFT JOIN wallets tw ON t.target_wallet_id = tw.id
                WHERE t.id = ? AND (sw.user_id = ? OR tw.user_id = ?)
                """;
            Integer count = jdbcTemplate.queryForObject(sql, Integer.class, 
                    transactionId, userId, userId);
            return count != null && count > 0;
        } catch (Exception e) {
            log.error("Error checking transaction ownership: userId={}, transactionId={}", userId, transactionId, e);
            return false;
        }
    }
    
    /**
     * Validates account ownership in core banking
     */
    @Cacheable(value = "account-ownership", key = "#userId + ':' + #accountId")
    public boolean isAccountOwner(UUID userId, String accountId) {
        try {
            String sql = """
                SELECT COUNT(*) FROM accounts 
                WHERE account_id = ? AND user_id = ? AND status IN ('ACTIVE', 'DORMANT')
                """;
            Integer count = jdbcTemplate.queryForObject(sql, Integer.class, accountId, userId);
            return count != null && count > 0;
        } catch (Exception e) {
            log.error("Error checking account ownership: userId={}, accountId={}", userId, accountId, e);
            return false;
        }
    }
    
    /**
     * Validates notification ownership
     */
    @Cacheable(value = "notification-ownership", key = "#userId + ':' + #notificationId")
    public boolean isNotificationRecipient(UUID userId, UUID notificationId) {
        try {
            String sql = "SELECT COUNT(*) FROM notifications WHERE id = ? AND user_id = ?";
            Integer count = jdbcTemplate.queryForObject(sql, Integer.class, notificationId, userId);
            return count != null && count > 0;
        } catch (Exception e) {
            log.error("Error checking notification ownership: userId={}, notificationId={}", userId, notificationId, e);
            return false;
        }
    }
    
    /**
     * Validates ledger entry access (checks if user has accounting role and proper scope)
     */
    @Cacheable(value = "ledger-access", key = "#userId + ':' + #entryId")
    public boolean hasLedgerAccess(UUID userId, UUID entryId) {
        try {
            // First check if user has any accounting role
            String roleSql = """
                SELECT COUNT(*) FROM user_roles ur
                JOIN roles r ON ur.role_id = r.id
                WHERE ur.user_id = ? AND ur.status = 'ACTIVE'
                AND r.name IN ('ACCOUNTANT', 'FINANCIAL_CONTROLLER', 'AUDITOR', 'ADMIN')
                """;
            Integer roleCount = jdbcTemplate.queryForObject(roleSql, Integer.class, userId);
            
            if (roleCount == null || roleCount == 0) {
                return false;
            }
            
            // Then check if the ledger entry is within user's scope
            String scopeSql = """
                SELECT COUNT(*) FROM ledger_entries le
                JOIN accounts a ON le.account_id = a.id
                WHERE le.id = ? AND (
                    a.visibility = 'PUBLIC' OR
                    a.department_id IN (
                        SELECT department_id FROM user_departments WHERE user_id = ?
                    )
                )
                """;
            Integer scopeCount = jdbcTemplate.queryForObject(scopeSql, Integer.class, entryId, userId);
            return scopeCount != null && scopeCount > 0;
            
        } catch (Exception e) {
            log.error("Error checking ledger access: userId={}, entryId={}", userId, entryId, e);
            return false;
        }
    }
    
    /**
     * Validates if user can perform high-value operations (checks limits and permissions)
     */
    public boolean canPerformHighValueOperation(UUID userId, String operation, double amount) {
        try {
            String sql = """
                SELECT COUNT(*) FROM user_limits ul
                WHERE ul.user_id = ? 
                AND ul.operation_type = ?
                AND ul.max_amount >= ?
                AND ul.status = 'ACTIVE'
                AND (ul.expires_at IS NULL OR ul.expires_at > CURRENT_TIMESTAMP)
                """;
            Integer count = jdbcTemplate.queryForObject(sql, Integer.class, userId, operation, amount);
            return count != null && count > 0;
        } catch (Exception e) {
            log.error("Error checking high-value operation permission: userId={}, operation={}, amount={}", 
                    userId, operation, amount, e);
            return false;
        }
    }
    
    /**
     * Validates if user has delegated access to a resource
     */
    @Cacheable(value = "delegated-access", key = "#userId + ':' + #resourceType + ':' + #resourceId")
    public boolean hasDelegatedAccess(UUID userId, String resourceType, String resourceId) {
        try {
            String sql = """
                SELECT COUNT(*) FROM resource_delegations
                WHERE delegate_user_id = ?
                AND resource_type = ?
                AND resource_id = ?
                AND status = 'ACTIVE'
                AND (expires_at IS NULL OR expires_at > CURRENT_TIMESTAMP)
                """;
            Integer count = jdbcTemplate.queryForObject(sql, Integer.class, userId, resourceType, resourceId);
            return count != null && count > 0;
        } catch (Exception e) {
            log.error("Error checking delegated access: userId={}, resourceType={}, resourceId={}", 
                    userId, resourceType, resourceId, e);
            return false;
        }
    }
    
    /**
     * Validates family account membership for shared resources
     */
    @Cacheable(value = "family-account-membership", key = "#userId + ':' + #familyAccountId")
    public boolean isFamilyAccountMember(UUID userId, UUID familyAccountId) {
        try {
            String sql = """
                SELECT COUNT(*) FROM family_account_members
                WHERE family_account_id = ? 
                AND user_id = ?
                AND status = 'ACTIVE'
                """;
            Integer count = jdbcTemplate.queryForObject(sql, Integer.class, familyAccountId, userId);
            return count != null && count > 0;
        } catch (Exception e) {
            log.error("Error checking family account membership: userId={}, familyAccountId={}", 
                    userId, familyAccountId, e);
            return false;
        }
    }
    
    /**
     * Validates business account access
     */
    @Cacheable(value = "business-account-access", key = "#userId + ':' + #businessId")
    public boolean hasBusinessAccountAccess(UUID userId, UUID businessId, String requiredRole) {
        try {
            String sql = """
                SELECT COUNT(*) FROM business_users
                WHERE business_id = ?
                AND user_id = ?
                AND role IN (?, 'OWNER', 'ADMIN')
                AND status = 'ACTIVE'
                """;
            Integer count = jdbcTemplate.queryForObject(sql, Integer.class, businessId, userId, requiredRole);
            return count != null && count > 0;
        } catch (Exception e) {
            log.error("Error checking business account access: userId={}, businessId={}, role={}", 
                    userId, businessId, requiredRole, e);
            return false;
        }
    }
}