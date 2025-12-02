package com.waqiti.security.idor;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation for IDOR Protection via Ownership Validation
 *
 * USAGE EXAMPLES:
 *
 * <pre>
 * // Simple ownership check
 * {@literal @}GetMapping("/wallets/{walletId}")
 * {@literal @}ValidateOwnership(resourceType = "WALLET", resourceIdParam = "walletId")
 * public WalletResponse getWallet(@PathVariable UUID walletId) {
 *     return walletService.getWallet(walletId);
 * }
 *
 * // Ownership with specific permission
 * {@literal @}DeleteMapping("/wallets/{walletId}")
 * {@literal @}ValidateOwnership(
 *     resourceType = "WALLET",
 *     resourceIdParam = "walletId",
 *     requiredPermission = "DELETE"
 * )
 * public void deleteWallet(@PathVariable UUID walletId) {
 *     walletService.deleteWallet(walletId);
 * }
 *
 * // Allow admin bypass
 * {@literal @}GetMapping("/users/{userId}/transactions")
 * {@literal @}ValidateOwnership(
 *     resourceType = "USER",
 *     resourceIdParam = "userId",
 *     allowAdmin = true
 * )
 * public List<Transaction> getUserTransactions(@PathVariable UUID userId) {
 *     return transactionService.getTransactionsByUser(userId);
 * }
 * </pre>
 *
 * SUPPORTED RESOURCE TYPES:
 * - WALLET
 * - ACCOUNT
 * - TRANSACTION
 * - PAYMENT
 * - INVESTMENT_ACCOUNT
 * - CARD
 * - BANK_ACCOUNT
 * - BENEFICIARY
 *
 * @author Waqiti Security Team
 * @version 3.0.0
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidateOwnership {

    /**
     * Type of resource being protected (WALLET, ACCOUNT, TRANSACTION, etc.)
     */
    String resourceType();

    /**
     * Name of the method parameter containing the resource ID
     * Must be of type UUID or String
     */
    String resourceIdParam();

    /**
     * Optional: Required permission (READ, WRITE, DELETE)
     * If empty, only ownership is checked
     */
    String requiredPermission() default "";

    /**
     * Whether to allow admin users to bypass ownership check
     * Default: true
     */
    boolean allowAdmin() default true;
}
