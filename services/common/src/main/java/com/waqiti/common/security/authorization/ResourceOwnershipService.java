package com.waqiti.common.security.authorization;

import com.waqiti.common.security.SecurityContext;
import com.waqiti.common.security.authorization.RBACService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * Enhanced Resource Ownership Service that provides comprehensive authorization
 * validation for financial operations with zero-trust security model.
 * 
 * Addresses Critical Security Vulnerability: Authorization Bypass
 * This service ensures users can only access resources they own or have explicit permissions to access.
 */
@Service
@Slf4j
public class ResourceOwnershipService {

    private final RBACService rbacService;
    private final SecurityContext securityContext;
    private final NamedParameterJdbcTemplate jdbcTemplate;
    
    public ResourceOwnershipService(
            RBACService rbacService,
            SecurityContext securityContext,
            NamedParameterJdbcTemplate jdbcTemplate) {
        this.rbacService = rbacService;
        this.securityContext = securityContext;
        this.jdbcTemplate = jdbcTemplate;
    }
    
    // Resource ownership validators registry
    private final Map<String, Function<OwnershipRequest, Boolean>> ownershipValidators = new ConcurrentHashMap<>();
    
    /**
     * Resource ownership validation request
     */
    public static class OwnershipRequest {
        private final UUID userId;
        private final String resourceType;
        private final String resourceId;
        private final String operation;
        private final Map<String, Object> context;
        
        public OwnershipRequest(UUID userId, String resourceType, String resourceId, String operation) {
            this(userId, resourceType, resourceId, operation, new HashMap<>());
        }
        
        public OwnershipRequest(UUID userId, String resourceType, String resourceId, 
                              String operation, Map<String, Object> context) {
            this.userId = userId;
            this.resourceType = resourceType;
            this.resourceId = resourceId;
            this.operation = operation;
            this.context = context != null ? context : new HashMap<>();
        }
        
        // Getters
        public UUID getUserId() { return userId; }
        public String getResourceType() { return resourceType; }
        public String getResourceId() { return resourceId; }
        public String getOperation() { return operation; }
        public Map<String, Object> getContext() { return context; }
    }
    
    /**
     * Validates if a user can access a specific resource.
     * Implements zero-trust security model with comprehensive validation.
     */
    @Cacheable(value = "resource-ownership", 
               key = "#request.userId + ':' + #request.resourceType + ':' + #request.resourceId + ':' + #request.operation")
    public boolean validateResourceAccess(OwnershipRequest request) {
        if (request == null || request.getUserId() == null || 
            !StringUtils.hasText(request.getResourceType()) || 
            !StringUtils.hasText(request.getResourceId())) {
            log.warn("Invalid ownership validation request: {}", request);
            return false;
        }
        
        try {
            log.debug("Validating resource access: userId={}, resourceType={}, resourceId={}, operation={}",
                    request.getUserId(), request.getResourceType(), request.getResourceId(), request.getOperation());
            
            // 1. Check for system administrator override
            if (isSystemAdministrator(request.getUserId())) {
                log.debug("System administrator access granted for user: {}", request.getUserId());
                return true;
            }
            
            // 2. Check specific resource-level permissions
            String resourcePermission = buildResourcePermission(request.getResourceType(), request.getOperation());
            if (rbacService.hasPermission(request.getUserId(), resourcePermission, request.getResourceId())) {
                log.debug("Resource-specific permission granted: {}", resourcePermission);
                return true;
            }
            
            // 3. Validate resource ownership using registered validators
            boolean hasOwnership = validateOwnershipInternal(request);
            
            // 4. Check delegation permissions if direct ownership fails
            if (!hasOwnership) {
                hasOwnership = validateDelegatedAccess(request);
            }
            
            // 5. Audit the access attempt
            auditResourceAccess(request, hasOwnership);
            
            log.debug("Resource access validation result: userId={}, resourceId={}, hasAccess={}",
                    request.getUserId(), request.getResourceId(), hasOwnership);
            
            return hasOwnership;
            
        } catch (Exception e) {
            log.error("Error validating resource access: userId={}, resourceType={}, resourceId={}",
                    request.getUserId(), request.getResourceType(), request.getResourceId(), e);
            // Fail securely
            return false;
        }
    }
    
    /**
     * Validates wallet ownership for financial operations
     */
    public boolean validateWalletOwnership(UUID userId, UUID walletId, String operation) {
        return validateResourceAccess(new OwnershipRequest(userId, "WALLET", walletId.toString(), operation));
    }
    
    /**
     * Validates payment access with enhanced security checks
     */
    public boolean validatePaymentAccess(UUID userId, UUID paymentId, String operation) {
        Map<String, Object> context = new HashMap<>();
        context.put("requiresEnhancedAuth", isHighRiskPaymentOperation(operation));
        return validateResourceAccess(new OwnershipRequest(userId, "PAYMENT", paymentId.toString(), operation, context));
    }
    
    /**
     * Validates transaction access with transaction-specific logic
     */
    public boolean validateTransactionAccess(UUID userId, UUID transactionId, String operation) {
        return validateResourceAccess(new OwnershipRequest(userId, "TRANSACTION", transactionId.toString(), operation));
    }
    
    /**
     * Validates account access with banking-specific validations
     */
    public boolean validateAccountAccess(UUID userId, String accountId, String operation) {
        Map<String, Object> context = new HashMap<>();
        context.put("accountType", getAccountType(accountId));
        return validateResourceAccess(new OwnershipRequest(userId, "ACCOUNT", accountId, operation, context));
    }
    
    /**
     * Validates notification access
     */
    public boolean validateNotificationAccess(UUID userId, UUID notificationId, String operation) {
        return validateResourceAccess(new OwnershipRequest(userId, "NOTIFICATION", notificationId.toString(), operation));
    }
    
    /**
     * Validates ledger entry access with accounting controls
     */
    public boolean validateLedgerAccess(UUID userId, UUID entryId, String operation) {
        // Ledger operations require additional compliance checks
        if (!hasCompliancePermission(userId, operation)) {
            return false;
        }
        return validateResourceAccess(new OwnershipRequest(userId, "LEDGER_ENTRY", entryId.toString(), operation));
    }
    
    /**
     * Registers a custom ownership validator for a resource type
     */
    public void registerOwnershipValidator(String resourceType, Function<OwnershipRequest, Boolean> validator) {
        if (StringUtils.hasText(resourceType) && validator != null) {
            ownershipValidators.put(resourceType.toUpperCase(), validator);
            log.info("Registered ownership validator for resource type: {}", resourceType);
        }
    }
    
    /**
     * Internal ownership validation using registered validators
     */
    private boolean validateOwnershipInternal(OwnershipRequest request) {
        String resourceType = request.getResourceType().toUpperCase();
        Function<OwnershipRequest, Boolean> validator = ownershipValidators.get(resourceType);
        
        if (validator != null) {
            try {
                return validator.apply(request);
            } catch (Exception e) {
                log.error("Error in ownership validator for resource type: {}", resourceType, e);
                return false;
            }
        }
        
        // Default ownership validation for common resource types
        return validateDefaultOwnership(request);
    }
    
    /**
     * Default ownership validation for common resource types
     */
    private boolean validateDefaultOwnership(OwnershipRequest request) {
        String resourceType = request.getResourceType().toUpperCase();
        
        switch (resourceType) {
            case "WALLET":
                return validateWalletOwnershipInternal(request);
            case "PAYMENT":
                return validatePaymentOwnershipInternal(request);
            case "TRANSACTION":
                return validateTransactionOwnershipInternal(request);
            case "ACCOUNT":
                return validateAccountOwnershipInternal(request);
            case "NOTIFICATION":
                return validateNotificationOwnershipInternal(request);
            case "LEDGER_ENTRY":
                return validateLedgerOwnershipInternal(request);
            default:
                log.warn("No ownership validator found for resource type: {}", resourceType);
                return false;
        }
    }
    
    /**
     * Validates delegated access through role hierarchy or explicit delegation
     */
    private boolean validateDelegatedAccess(OwnershipRequest request) {
        // Check if user has delegated permissions from a higher-level role
        String delegationPermission = "DELEGATE_ACCESS:" + request.getResourceType();
        return rbacService.hasPermission(request.getUserId(), delegationPermission, request.getResourceId());
    }
    
    /**
     * Builds resource-specific permission string
     */
    private String buildResourcePermission(String resourceType, String operation) {
        return String.format("%s:%s", resourceType.toUpperCase(), operation.toUpperCase());
    }
    
    /**
     * Checks if user is a system administrator
     */
    private boolean isSystemAdministrator(UUID userId) {
        return rbacService.hasPermission(userId, "SYSTEM:ADMIN", "*") ||
               rbacService.hasPermission(userId, "SUPER_ADMIN", "*");
    }
    
    /**
     * Checks if operation is high-risk and requires enhanced authentication
     */
    private boolean isHighRiskPaymentOperation(String operation) {
        return Arrays.asList("TRANSFER", "WITHDRAW", "INTERNATIONAL_TRANSFER", "BULK_PAYMENT")
                .contains(operation.toUpperCase());
    }
    
    /**
     * Gets account type for context-aware validation
     */
    private String getAccountType(String accountId) {
        // This would typically query the database
        // For now, return a default value
        return "STANDARD";
    }
    
    /**
     * Checks if user has compliance permissions for ledger operations
     */
    private boolean hasCompliancePermission(UUID userId, String operation) {
        return rbacService.hasPermission(userId, "COMPLIANCE:" + operation.toUpperCase(), "*") ||
               rbacService.hasPermission(userId, "FINANCIAL_CONTROLLER", "*");
    }
    
    /**
     * Audits resource access attempts
     */
    private void auditResourceAccess(OwnershipRequest request, boolean accessGranted) {
        try {
            log.info("SECURITY_AUDIT: ResourceAccess - userId={}, resourceType={}, resourceId={}, operation={}, granted={}",
                    request.getUserId(), request.getResourceType(), request.getResourceId(), 
                    request.getOperation(), accessGranted);
            
            // Additional audit logging for security events
            if (!accessGranted) {
                log.warn("SECURITY_EVENT: UNAUTHORIZED_RESOURCE_ACCESS_ATTEMPT - userId={}, resource={}:{}",
                        request.getUserId(), request.getResourceType(), request.getResourceId());
            }
        } catch (Exception e) {
            log.error("Error auditing resource access", e);
        }
    }
    
    // Internal validation methods for specific resource types

    /**
     * Safely parse UUID from string with validation.
     * SECURITY FIX (2025-10-18): Prevents SQL injection and information disclosure
     *
     * @param uuidString String to parse as UUID
     * @return Optional containing UUID if valid, empty otherwise
     */
    private Optional<UUID> safeParseUUID(String uuidString) {
        if (!StringUtils.hasText(uuidString)) {
            log.warn("Attempted to parse null or empty UUID string");
            return Optional.empty();
        }

        // Validate UUID format before parsing (prevents exception-based information disclosure)
        // UUID format: xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx (36 characters with dashes)
        if (!uuidString.matches("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")) {
            log.warn("Invalid UUID format detected: {}", uuidString.substring(0, Math.min(10, uuidString.length())) + "...");
            return Optional.empty();
        }

        try {
            return Optional.of(UUID.fromString(uuidString));
        } catch (IllegalArgumentException e) {
            log.warn("Failed to parse UUID: {}", uuidString.substring(0, Math.min(10, uuidString.length())) + "...", e);
            return Optional.empty();
        }
    }

    /**
     * Safely get UUID from resource ID or throw security exception.
     *
     * @param resourceId Resource ID string
     * @return Valid UUID
     * @throws InvalidResourceIdException if UUID is invalid
     */
    private UUID getValidatedResourceId(String resourceId) {
        return safeParseUUID(resourceId)
            .orElseThrow(() -> new InvalidResourceIdException("Invalid resource ID format: " +
                (resourceId != null ? resourceId.substring(0, Math.min(10, resourceId.length())) : "null")));
    }

    private boolean validateWalletOwnershipInternal(OwnershipRequest request) {
        try {
            // SECURITY FIX: Validate UUID before using in query
            UUID resourceUuid = getValidatedResourceId(request.getResourceId());

            // Query wallet service to validate ownership
            String query = "SELECT COUNT(*) > 0 FROM wallets WHERE id = :resourceId AND user_id = :userId AND status = 'ACTIVE'";

            Map<String, Object> params = new HashMap<>();
            params.put("resourceId", resourceUuid);
            params.put("userId", request.getUserId());
            
            // Check if user owns the wallet
            boolean isOwner = jdbcTemplate.queryForObject(query, params, Boolean.class);
            
            if (!isOwner && request.getOperation().equals("VIEW")) {
                // Check if user has shared access for viewing
                String sharedQuery = "SELECT COUNT(*) > 0 FROM wallet_shares WHERE wallet_id = :resourceId AND shared_with_user_id = :userId AND permission_level >= 'READ'";
                isOwner = jdbcTemplate.queryForObject(sharedQuery, params, Boolean.class);
            }
            
            return isOwner;
        } catch (Exception e) {
            log.error("Error validating wallet ownership for user {} and wallet {}", 
                request.getUserId(), request.getResourceId(), e);
            return false;
        }
    }
    
    private boolean validatePaymentOwnershipInternal(OwnershipRequest request) {
        try {
            // SECURITY FIX: Validate UUID before using in query
            UUID resourceUuid = getValidatedResourceId(request.getResourceId());

            // Query payment service to validate ownership
            String query = "SELECT COUNT(*) > 0 FROM payments p " +
                          "WHERE p.id = :resourceId AND (p.sender_id = :userId OR p.recipient_id = :userId)";

            Map<String, Object> params = new HashMap<>();
            params.put("resourceId", resourceUuid);
            params.put("userId", request.getUserId());
            
            // Check if user is sender or recipient
            boolean hasAccess = jdbcTemplate.queryForObject(query, params, Boolean.class);
            
            // For modification operations, only sender can modify before completion
            if (hasAccess && (request.getOperation().equals("CANCEL") || request.getOperation().equals("MODIFY"))) {
                String senderQuery = "SELECT COUNT(*) > 0 FROM payments WHERE id = :resourceId AND sender_id = :userId AND status IN ('PENDING', 'INITIATED')";
                hasAccess = jdbcTemplate.queryForObject(senderQuery, params, Boolean.class);
            }
            
            return hasAccess;
        } catch (Exception e) {
            log.error("Error validating payment ownership for user {} and payment {}", 
                request.getUserId(), request.getResourceId(), e);
            return false;
        }
    }
    
    private boolean validateTransactionOwnershipInternal(OwnershipRequest request) {
        try {
            // SECURITY FIX: Validate UUID before using in query
            UUID resourceUuid = getValidatedResourceId(request.getResourceId());

            // Query transaction service to validate ownership
            // Note: transactions table uses 'target_wallet_id' not 'destination_wallet_id'
            String query = "SELECT COUNT(*) > 0 FROM transactions t " +
                          "JOIN wallets w ON (t.source_wallet_id = w.id OR t.target_wallet_id = w.id) " +
                          "WHERE t.id = :resourceId AND w.user_id = :userId";

            Map<String, Object> params = new HashMap<>();
            params.put("resourceId", resourceUuid);
            params.put("userId", request.getUserId());
            
            // Check if user owns either source or destination wallet
            boolean hasAccess = jdbcTemplate.queryForObject(query, params, Boolean.class);
            
            // For dispute operations, check if user is authorized party
            if (!hasAccess && request.getOperation().equals("DISPUTE")) {
                String disputeQuery = "SELECT COUNT(*) > 0 FROM transaction_disputes WHERE transaction_id = :resourceId AND raised_by_user_id = :userId";
                hasAccess = jdbcTemplate.queryForObject(disputeQuery, params, Boolean.class);
            }
            
            return hasAccess;
        } catch (Exception e) {
            log.error("Error validating transaction ownership for user {} and transaction {}", 
                request.getUserId(), request.getResourceId(), e);
            return false;
        }
    }
    
    private boolean validateAccountOwnershipInternal(OwnershipRequest request) {
        try {
            // Query core banking service to validate ownership
            // Note: accounts table uses 'account_status' instead of 'status'
            String query = "SELECT COUNT(*) > 0 FROM accounts a " +
                          "WHERE a.account_number = :resourceId AND a.user_id = :userId AND a.account_status != 'CLOSED'";

            Map<String, Object> params = new HashMap<>();
            params.put("resourceId", request.getResourceId());
            params.put("userId", request.getUserId());

            // Check if user owns the account
            boolean isOwner = jdbcTemplate.queryForObject(query, params, Boolean.class);
            
            // For joint accounts, check co-ownership
            if (!isOwner && request.getContext().get("accountType") != null && 
                request.getContext().get("accountType").equals("JOINT")) {
                String jointQuery = "SELECT COUNT(*) > 0 FROM account_co_owners WHERE account_number = :resourceId AND co_owner_user_id = :userId";
                isOwner = jdbcTemplate.queryForObject(jointQuery, params, Boolean.class);
            }
            
            // For business accounts, check authorized signatories
            if (!isOwner && request.getContext().get("accountType") != null && 
                request.getContext().get("accountType").equals("BUSINESS")) {
                String signatoryQuery = "SELECT COUNT(*) > 0 FROM account_signatories WHERE account_number = :resourceId AND signatory_user_id = :userId AND is_active = true";
                isOwner = jdbcTemplate.queryForObject(signatoryQuery, params, Boolean.class);
            }
            
            return isOwner;
        } catch (Exception e) {
            log.error("Error validating account ownership for user {} and account {}", 
                request.getUserId(), request.getResourceId(), e);
            return false;
        }
    }
    
    private boolean validateNotificationOwnershipInternal(OwnershipRequest request) {
        try {
            // Query notification service to validate ownership
            // Note: notification table uses 'customer_id' instead of 'user_id'
            String query = "SELECT COUNT(*) > 0 FROM notification WHERE id = :resourceId AND customer_id = :userId";

            Map<String, Object> params = new HashMap<>();
            params.put("resourceId", UUID.fromString(request.getResourceId()));
            params.put("userId", request.getUserId());

            // Simple ownership check - notifications are user-specific
            boolean isOwner = jdbcTemplate.queryForObject(query, params, Boolean.class);

            // For marking as read/delete operations, ensure notification is not urgent (uses 'priority' not 'is_critical')
            if (isOwner && (request.getOperation().equals("DELETE") || request.getOperation().equals("MARK_READ"))) {
                String priorityQuery = "SELECT priority FROM notification WHERE id = :resourceId";
                params.clear();
                params.put("resourceId", UUID.fromString(request.getResourceId()));
                String priority = jdbcTemplate.queryForObject(priorityQuery, params, String.class);
                if ("URGENT".equals(priority) && request.getOperation().equals("DELETE")) {
                    return false; // Cannot delete urgent notifications
                }
            }

            return isOwner;
        } catch (Exception e) {
            log.error("Error validating notification ownership for user {} and notification {}",
                request.getUserId(), request.getResourceId(), e);
            return false;
        }
    }
    
    private boolean validateLedgerOwnershipInternal(OwnershipRequest request) {
        try {
            // Query ledger service with strict accounting rules
            // Ledger entries require special permissions due to compliance
            // Note: ledger_entries uses 'account_id' (single column), not debit_account_id/credit_account_id
            String query = "SELECT COUNT(*) > 0 FROM ledger_entries le " +
                          "WHERE le.id = :resourceId AND EXISTS (" +
                          "    SELECT 1 FROM accounts a " +
                          "    WHERE a.account_number = le.account_id AND a.user_id = :userId" +
                          ")";

            Map<String, Object> params = new HashMap<>();
            params.put("resourceId", UUID.fromString(request.getResourceId()));
            params.put("userId", request.getUserId());

            // Check if user has relationship with the ledger entry
            boolean hasRelationship = jdbcTemplate.queryForObject(query, params, Boolean.class);
            
            // For viewing, relationship is sufficient
            if (request.getOperation().equals("VIEW")) {
                return hasRelationship;
            }
            
            // For modifications, require financial controller or accountant role
            if (hasRelationship && (request.getOperation().equals("MODIFY") || 
                request.getOperation().equals("REVERSE") || request.getOperation().equals("APPROVE"))) {
                // Check for accounting permissions
                boolean hasAccountingRole = rbacService.hasPermission(
                    request.getUserId(), 
                    "LEDGER:MODIFY", 
                    request.getResourceId()
                );
                
                // Check if entry is locked for compliance
                // Note: ledger_entries uses 'locked_at' timestamp, not 'is_locked' boolean
                if (hasAccountingRole) {
                    String lockQuery = "SELECT (locked_at IS NOT NULL) as is_locked FROM ledger_entries WHERE id = :resourceId";
                    Boolean isLocked = jdbcTemplate.queryForObject(lockQuery, params, Boolean.class);
                    return !Boolean.TRUE.equals(isLocked);
                }
                
                return hasAccountingRole;
            }
            
            return false;
        } catch (Exception e) {
            log.error("Error validating ledger ownership for user {} and entry {}", 
                request.getUserId(), request.getResourceId(), e);
            return false;
        }
    }
}