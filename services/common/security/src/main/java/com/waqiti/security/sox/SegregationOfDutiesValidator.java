package com.waqiti.security.sox;

import com.waqiti.common.security.Roles;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * SOX Segregation of Duties (SoD) Validator
 *
 * CRITICAL COMPLIANCE:
 * - Sarbanes-Oxley Act (SOX) Section 404
 * - COSO Framework for Internal Controls
 * - COBIT 5 - Principle 12 (Risk Management)
 *
 * SEGREGATION OF DUTIES PRINCIPLE:
 * No single person should have control over ALL phases of a financial transaction:
 * 1. Authorization - Approve transaction
 * 2. Recording - Enter transaction into system
 * 3. Custody - Handle physical assets
 * 4. Reconciliation - Verify transaction accuracy
 *
 * EXAMPLE VIOLATIONS:
 * ❌ Same user CREATES and APPROVES a payment
 * ❌ Same user INITIATES and RECONCILES a transfer
 * ❌ Same user CREATES and VOIDS a transaction
 *
 * ENFORCEMENT RULES:
 * - Incompatible roles cannot be assigned to same user
 * - Incompatible actions cannot be performed by same user on same transaction
 * - Dual authorization required for high-value transactions
 * - Automated alerts for SoD violations
 *
 * @author Waqiti Compliance Team
 * @version 3.0.0
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SegregationOfDutiesValidator {

    private final SegregationOfDutiesRepository sodRepository;
    private final SegregationOfDutiesAuditLogger sodAuditLogger;

    /**
     * Incompatible role combinations
     * Users cannot have BOTH roles in any pair
     */
    private static final Map<String, Set<String>> INCOMPATIBLE_ROLES = Map.ofEntries(
        // Finance roles incompatible with operations
        Map.entry(Roles.ACCOUNTANT, Set.of(Roles.TREASURY, Roles.OPERATIONS)),
        Map.entry(Roles.TREASURY, Set.of(Roles.ACCOUNTANT, Roles.FINANCE_MANAGER)),

        // Compliance cannot approve what they audit
        Map.entry(Roles.AUDITOR, Set.of(Roles.ADMIN, Roles.FINANCE_MANAGER, Roles.TREASURY)),
        Map.entry(Roles.INTERNAL_AUDIT, Set.of(Roles.OPERATIONS, Roles.SUPPORT_MANAGER)),

        // Developer/admin separation
        Map.entry(Roles.ADMIN, Set.of(Roles.AUDITOR, Roles.EXTERNAL_AUDIT)),
        Map.entry(Roles.SUPER_ADMIN, Set.of(Roles.COMPLIANCE_OFFICER))
    );

    /**
     * Incompatible action combinations on same transaction
     * Same user cannot perform BOTH actions on the same transaction/resource
     */
    private static final Map<String, Set<String>> INCOMPATIBLE_ACTIONS = Map.ofEntries(
        // Payment lifecycle
        Map.entry("PAYMENT_CREATE", Set.of("PAYMENT_APPROVE", "PAYMENT_RECONCILE")),
        Map.entry("PAYMENT_APPROVE", Set.of("PAYMENT_CREATE", "PAYMENT_EXECUTE", "PAYMENT_RECONCILE")),
        Map.entry("PAYMENT_EXECUTE", Set.of("PAYMENT_APPROVE", "PAYMENT_RECONCILE")),
        Map.entry("PAYMENT_RECONCILE", Set.of("PAYMENT_CREATE", "PAYMENT_APPROVE", "PAYMENT_EXECUTE")),

        // Transfer lifecycle
        Map.entry("TRANSFER_INITIATE", Set.of("TRANSFER_APPROVE", "TRANSFER_RECONCILE")),
        Map.entry("TRANSFER_APPROVE", Set.of("TRANSFER_INITIATE", "TRANSFER_EXECUTE", "TRANSFER_RECONCILE")),
        Map.entry("TRANSFER_EXECUTE", Set.of("TRANSFER_APPROVE", "TRANSFER_RECONCILE")),
        Map.entry("TRANSFER_RECONCILE", Set.of("TRANSFER_INITIATE", "TRANSFER_APPROVE")),

        // Transaction management
        Map.entry("TRANSACTION_CREATE", Set.of("TRANSACTION_VOID", "TRANSACTION_REFUND")),
        Map.entry("TRANSACTION_VOID", Set.of("TRANSACTION_CREATE", "TRANSACTION_APPROVE")),
        Map.entry("TRANSACTION_REFUND", Set.of("TRANSACTION_CREATE", "TRANSACTION_APPROVE")),

        // User management (prevent self-service privilege escalation)
        Map.entry("USER_CREATE", Set.of("USER_APPROVE", "USER_GRANT_ROLE")),
        Map.entry("USER_GRANT_ROLE", Set.of("USER_CREATE", "USER_APPROVE")),

        // Limit adjustments
        Map.entry("LIMIT_INCREASE", Set.of("LIMIT_APPROVE", "LIMIT_EXECUTE")),
        Map.entry("LIMIT_APPROVE", Set.of("LIMIT_INCREASE", "LIMIT_EXECUTE"))
    );

    /**
     * Actions requiring dual authorization (two different users)
     */
    private static final Set<String> DUAL_AUTH_REQUIRED_ACTIONS = Set.of(
        "PAYMENT_APPROVE",
        "TRANSFER_APPROVE",
        "LIMIT_INCREASE",
        "ACCOUNT_FREEZE",
        "ACCOUNT_UNFREEZE",
        "TRANSACTION_VOID",
        "LARGE_WITHDRAWAL" // > $10,000
    );

    /**
     * Validate role assignment - prevent incompatible roles for same user
     *
     * USAGE:
     * <pre>
     * validateRoleAssignment(userId, "ROLE_ACCOUNTANT");
     * // Throws exception if user already has ROLE_TREASURY or ROLE_OPERATIONS
     * </pre>
     */
    public void validateRoleAssignment(UUID userId, String newRole) {
        log.debug("Validating role assignment: User={}, NewRole={}", userId, newRole);

        // Get user's current roles
        Set<String> currentRoles = sodRepository.getUserRoles(userId);

        // Check for incompatible roles
        Set<String> incompatibleWithNewRole = INCOMPATIBLE_ROLES.getOrDefault(newRole, Collections.emptySet());

        for (String currentRole : currentRoles) {
            if (incompatibleWithNewRole.contains(currentRole)) {
                String violation = String.format(
                    "SOX VIOLATION: Cannot assign role '%s' to user %s - " +
                    "incompatible with existing role '%s'",
                    newRole, userId, currentRole
                );

                log.error(violation);
                sodAuditLogger.logSoDViolation(
                    userId, "ROLE_ASSIGNMENT", newRole, currentRole, violation
                );

                throw new SegregationOfDutiesViolationException(violation);
            }
        }

        // Also check reverse incompatibility
        for (String currentRole : currentRoles) {
            Set<String> incompatibleWithCurrent = INCOMPATIBLE_ROLES.getOrDefault(
                currentRole, Collections.emptySet()
            );

            if (incompatibleWithCurrent.contains(newRole)) {
                String violation = String.format(
                    "SOX VIOLATION: Cannot assign role '%s' to user %s - " +
                    "role '%s' is incompatible with it",
                    newRole, userId, currentRole
                );

                log.error(violation);
                sodAuditLogger.logSoDViolation(
                    userId, "ROLE_ASSIGNMENT", newRole, currentRole, violation
                );

                throw new SegregationOfDutiesViolationException(violation);
            }
        }

        log.debug("Role assignment validation passed for user {} and role {}", userId, newRole);
    }

    /**
     * Validate action on transaction - prevent same user from performing incompatible actions
     *
     * USAGE:
     * <pre>
     * validateTransactionAction(userId, transactionId, "PAYMENT_APPROVE");
     * // Throws exception if same user already performed PAYMENT_CREATE
     * </pre>
     */
    public void validateTransactionAction(UUID userId, UUID transactionId, String action) {
        log.debug("Validating transaction action: User={}, Transaction={}, Action={}",
            userId, transactionId, action);

        // Get previous actions on this transaction
        List<TransactionAction> previousActions = sodRepository.getTransactionActions(transactionId);

        // Check for incompatible actions by same user
        Set<String> incompatibleActions = INCOMPATIBLE_ACTIONS.getOrDefault(
            action, Collections.emptySet()
        );

        for (TransactionAction previousAction : previousActions) {
            // Check if same user performed an incompatible action
            if (previousAction.getUserId().equals(userId) &&
                incompatibleActions.contains(previousAction.getAction())) {

                String violation = String.format(
                    "SOX VIOLATION: User %s cannot perform action '%s' - " +
                    "already performed incompatible action '%s' on transaction %s",
                    userId, action, previousAction.getAction(), transactionId
                );

                log.error(violation);
                sodAuditLogger.logSoDViolation(
                    userId, "TRANSACTION_ACTION", action,
                    previousAction.getAction(), violation
                );

                throw new SegregationOfDutiesViolationException(violation);
            }
        }

        log.debug("Transaction action validation passed");
    }

    /**
     * Validate dual authorization requirement
     *
     * CRITICAL: Ensures two different users approve high-risk operations
     */
    public void validateDualAuthorization(UUID initiatorUserId, UUID approverUserId,
                                          String action, UUID transactionId) {
        log.debug("Validating dual authorization: Initiator={}, Approver={}, Action={}",
            initiatorUserId, approverUserId, action);

        // Check if dual auth is required for this action
        if (!DUAL_AUTH_REQUIRED_ACTIONS.contains(action)) {
            log.debug("Dual authorization not required for action: {}", action);
            return;
        }

        // Verify two different users
        if (initiatorUserId.equals(approverUserId)) {
            String violation = String.format(
                "SOX VIOLATION: Action '%s' requires dual authorization - " +
                "initiator and approver must be different users (both are %s)",
                action, initiatorUserId
            );

            log.error(violation);
            sodAuditLogger.logSoDViolation(
                initiatorUserId, "DUAL_AUTHORIZATION", action, action, violation
            );

            throw new SegregationOfDutiesViolationException(violation);
        }

        // Verify approver has sufficient authority
        if (!hasApprovalAuthority(approverUserId, action)) {
            String violation = String.format(
                "SOX VIOLATION: User %s does not have approval authority for action '%s'",
                approverUserId, action
            );

            log.error(violation);
            sodAuditLogger.logSoDViolation(
                approverUserId, "APPROVAL_AUTHORITY", action, null, violation
            );

            throw new SegregationOfDutiesViolationException(violation);
        }

        // Record dual authorization for audit
        sodAuditLogger.logDualAuthorization(
            transactionId, initiatorUserId, approverUserId, action
        );

        log.info("Dual authorization validated: Transaction={}, Initiator={}, Approver={}",
            transactionId, initiatorUserId, approverUserId);
    }

    /**
     * Validate maker-checker pattern
     *
     * USAGE: Ensure transaction maker and checker are different users
     */
    public void validateMakerChecker(UUID makerId, UUID checkerId, UUID transactionId) {
        log.debug("Validating maker-checker: Maker={}, Checker={}, Transaction={}",
            makerId, checkerId, transactionId);

        if (makerId.equals(checkerId)) {
            String violation = String.format(
                "SOX VIOLATION: Maker-Checker violation - " +
                "maker and checker must be different users (both are %s) for transaction %s",
                makerId, transactionId
            );

            log.error(violation);
            sodAuditLogger.logSoDViolation(
                makerId, "MAKER_CHECKER", "SAME_USER", null, violation
            );

            throw new SegregationOfDutiesViolationException(violation);
        }

        // Record maker-checker for audit
        sodRepository.recordMakerChecker(transactionId, makerId, checkerId);

        log.debug("Maker-checker validation passed");
    }

    /**
     * Check if user has approval authority for specific action
     */
    private boolean hasApprovalAuthority(UUID userId, String action) {
        Set<String> userRoles = sodRepository.getUserRoles(userId);

        // Map actions to required approval roles
        return switch (action) {
            case "PAYMENT_APPROVE", "TRANSFER_APPROVE" ->
                userRoles.contains(Roles.FINANCE_MANAGER) ||
                userRoles.contains(Roles.TREASURY) ||
                userRoles.contains(Roles.ADMIN);

            case "LIMIT_INCREASE" ->
                userRoles.contains(Roles.RISK_ANALYST) ||
                userRoles.contains(Roles.COMPLIANCE_MANAGER) ||
                userRoles.contains(Roles.ADMIN);

            case "ACCOUNT_FREEZE", "ACCOUNT_UNFREEZE" ->
                userRoles.contains(Roles.COMPLIANCE_OFFICER) ||
                userRoles.contains(Roles.FRAUD_ANALYST) ||
                userRoles.contains(Roles.ADMIN);

            case "TRANSACTION_VOID" ->
                userRoles.contains(Roles.FINANCE_MANAGER) ||
                userRoles.contains(Roles.COMPLIANCE_OFFICER) ||
                userRoles.contains(Roles.ADMIN);

            case "LARGE_WITHDRAWAL" ->
                userRoles.contains(Roles.FINANCE_MANAGER) ||
                userRoles.contains(Roles.TREASURY) ||
                userRoles.contains(Roles.ADMIN);

            default -> false;
        };
    }

    /**
     * Get incompatible roles for a given role
     */
    public Set<String> getIncompatibleRoles(String role) {
        Set<String> incompatible = new HashSet<>(
            INCOMPATIBLE_ROLES.getOrDefault(role, Collections.emptySet())
        );

        // Add reverse incompatibilities
        for (Map.Entry<String, Set<String>> entry : INCOMPATIBLE_ROLES.entrySet()) {
            if (entry.getValue().contains(role)) {
                incompatible.add(entry.getKey());
            }
        }

        return incompatible;
    }

    /**
     * Get incompatible actions for a given action
     */
    public Set<String> getIncompatibleActions(String action) {
        return INCOMPATIBLE_ACTIONS.getOrDefault(action, Collections.emptySet());
    }

    /**
     * Check if action requires dual authorization
     */
    public boolean requiresDualAuthorization(String action) {
        return DUAL_AUTH_REQUIRED_ACTIONS.contains(action);
    }

    /**
     * Validate entire transaction workflow for SoD compliance
     *
     * COMPREHENSIVE CHECK: Reviews all actions on transaction
     */
    public SoDValidationResult validateTransactionWorkflow(UUID transactionId) {
        log.info("Performing comprehensive SoD validation for transaction: {}", transactionId);

        SoDValidationResult result = new SoDValidationResult();
        result.setTransactionId(transactionId);
        result.setValid(true);

        List<TransactionAction> actions = sodRepository.getTransactionActions(transactionId);

        // Group actions by user
        Map<UUID, List<String>> actionsByUser = new HashMap<>();
        for (TransactionAction action : actions) {
            actionsByUser.computeIfAbsent(action.getUserId(), k -> new ArrayList<>())
                .add(action.getAction());
        }

        // Check for SoD violations
        for (Map.Entry<UUID, List<String>> entry : actionsByUser.entrySet()) {
            UUID userId = entry.getKey();
            List<String> userActions = entry.getValue();

            // Check each pair of actions for incompatibility
            for (int i = 0; i < userActions.size(); i++) {
                for (int j = i + 1; j < userActions.size(); j++) {
                    String action1 = userActions.get(i);
                    String action2 = userActions.get(j);

                    Set<String> incompatible = INCOMPATIBLE_ACTIONS.getOrDefault(
                        action1, Collections.emptySet()
                    );

                    if (incompatible.contains(action2)) {
                        result.setValid(false);
                        result.addViolation(String.format(
                            "User %s performed incompatible actions: %s and %s",
                            userId, action1, action2
                        ));

                        sodAuditLogger.logSoDViolation(
                            userId, "WORKFLOW_VALIDATION", action1, action2,
                            "Incompatible actions on same transaction"
                        );
                    }
                }
            }
        }

        // Check dual authorization requirements
        for (TransactionAction action : actions) {
            if (DUAL_AUTH_REQUIRED_ACTIONS.contains(action.getAction())) {
                // Verify there was a different initiator
                boolean hasDifferentInitiator = actions.stream()
                    .anyMatch(a -> !a.getUserId().equals(action.getUserId()) &&
                                   a.getTimestamp().isBefore(action.getTimestamp()));

                if (!hasDifferentInitiator) {
                    result.setValid(false);
                    result.addViolation(String.format(
                        "Action '%s' requires dual authorization but was performed by single user",
                        action.getAction()
                    ));
                }
            }
        }

        log.info("SoD validation result for transaction {}: Valid={}, Violations={}",
            transactionId, result.isValid(), result.getViolations().size());

        return result;
    }

    /**
     * Exception for SoD violations
     */
    public static class SegregationOfDutiesViolationException extends RuntimeException {
        public SegregationOfDutiesViolationException(String message) {
            super(message);
        }

        public SegregationOfDutiesViolationException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /**
     * Result of SoD validation
     */
    public static class SoDValidationResult {
        private UUID transactionId;
        private boolean valid;
        private List<String> violations = new ArrayList<>();

        public UUID getTransactionId() { return transactionId; }
        public void setTransactionId(UUID transactionId) { this.transactionId = transactionId; }

        public boolean isValid() { return valid; }
        public void setValid(boolean valid) { this.valid = valid; }

        public List<String> getViolations() { return violations; }
        public void addViolation(String violation) { this.violations.add(violation); }
    }

    /**
     * Transaction action record
     */
    public static class TransactionAction {
        private final UUID userId;
        private final String action;
        private final java.time.LocalDateTime timestamp;

        public TransactionAction(UUID userId, String action, java.time.LocalDateTime timestamp) {
            this.userId = userId;
            this.action = action;
            this.timestamp = timestamp;
        }

        public UUID getUserId() { return userId; }
        public String getAction() { return action; }
        public java.time.LocalDateTime getTimestamp() { return timestamp; }
    }
}
