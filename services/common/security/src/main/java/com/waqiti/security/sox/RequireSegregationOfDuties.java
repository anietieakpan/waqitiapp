package com.waqiti.security.sox;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation to enforce Segregation of Duties (SoD) on financial operations
 *
 * USAGE EXAMPLES:
 *
 * <pre>
 * // Require dual authorization
 * {@literal @}PostMapping("/payments/{id}/approve")
 * {@literal @}RequireSegregationOfDuties(
 *     action = "PAYMENT_APPROVE",
 *     requireDualAuth = true,
 *     transactionIdParam = "id"
 * )
 * public PaymentResponse approvePayment(@PathVariable UUID id) {
 *     // Enforces that approver != initiator
 * }
 *
 * // Prevent same user from creating and approving
 * {@literal @}PostMapping("/transfers/{id}/execute")
 * {@literal @}RequireSegregationOfDuties(
 *     action = "TRANSFER_EXECUTE",
 *     incompatibleActions = {"TRANSFER_INITIATE", "TRANSFER_APPROVE"},
 *     transactionIdParam = "id"
 * )
 * public TransferResponse executeTransfer(@PathVariable UUID id) {
 *     // Throws exception if same user initiated or approved
 * }
 *
 * // Maker-checker pattern
 * {@literal @}PostMapping("/limits/increase")
 * {@literal @}RequireSegregationOfDuties(
 *     action = "LIMIT_INCREASE",
 *     requireMakerChecker = true,
 *     transactionIdParam = "requestId"
 * )
 * public LimitResponse increaseLimit(@RequestBody LimitRequest request) {
 *     // Enforces maker != checker
 * }
 * </pre>
 *
 * @author Waqiti Compliance Team
 * @version 3.0.0
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RequireSegregationOfDuties {

    /**
     * The action being performed (e.g., "PAYMENT_APPROVE", "TRANSFER_EXECUTE")
     */
    String action();

    /**
     * Name of the method parameter containing the transaction ID
     * Used to look up previous actions on this transaction
     */
    String transactionIdParam();

    /**
     * Whether dual authorization is required
     * Ensures initiator and approver are different users
     */
    boolean requireDualAuth() default false;

    /**
     * Whether maker-checker pattern is required
     * Ensures maker and checker are different users
     */
    boolean requireMakerChecker() default false;

    /**
     * Actions that are incompatible with this action
     * Same user cannot perform this action if they performed any incompatible action
     */
    String[] incompatibleActions() default {};

    /**
     * Optional: Name of parameter containing approver user ID
     * Used for dual authorization validation
     */
    String approverIdParam() default "";

    /**
     * Optional: Name of parameter containing checker user ID
     * Used for maker-checker validation
     */
    String checkerIdParam() default "";

    /**
     * Whether to allow admin bypass
     * Default: false (admins must also follow SoD rules)
     */
    boolean allowAdminBypass() default false;
}
