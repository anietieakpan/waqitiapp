package com.waqiti.payment.checkdeposit.service;

import java.math.BigDecimal;

/**
 * Bank Verification Service Interface
 *
 * Defines contract for bank account verification operations including:
 * - Account verification via routing and account numbers
 * - Account ownership validation
 * - Balance verification
 * - Account details retrieval
 * - Link token creation for Plaid Link
 * - Public token exchange
 * - Webhook handling for account updates
 *
 * Implementations should provide production-ready integrations with
 * bank verification providers (Plaid, Yodlee, etc.)
 */
public interface BankVerificationService {

    /**
     * Verify bank account using routing and account numbers
     *
     * @param accountNumber Bank account number
     * @param routingNumber Bank routing number (9 digits)
     * @param userId User ID requesting verification
     * @return Verification result with account details and status
     * @throws BankAccountLinkException if verification fails
     */
    BankAccountVerificationResult verifyBankAccount(
        String accountNumber,
        String routingNumber,
        String userId
    );

    /**
     * Validate account ownership using Plaid access token
     *
     * @param plaidAccessToken Plaid access token for the account
     * @param accountId Plaid account ID
     * @param userId User ID to validate ownership
     * @return Ownership validation result with owner details
     * @throws BankAccountLinkException if validation fails
     */
    AccountOwnershipResult validateAccountOwnership(
        String plaidAccessToken,
        String accountId,
        String userId
    );

    /**
     * Check account balance and verify minimum balance requirement
     *
     * @param accountId Plaid account ID
     * @param minimumBalance Minimum required balance
     * @return Balance check result with current balance and verification status
     * @throws BankAccountLinkException if balance check fails
     */
    AccountBalanceCheckResult checkAccountBalance(
        String accountId,
        BigDecimal minimumBalance
    );

    /**
     * Get detailed account information
     *
     * @param accountId Plaid account ID
     * @return Comprehensive account details including institution, balance, and type
     * @throws BankAccountLinkException if retrieval fails
     */
    BankAccountDetails getAccountDetails(String accountId);

    /**
     * Handle Plaid webhook notifications for account updates
     *
     * @param payload Webhook payload from Plaid
     * @return Processing result with status and details
     */
    WebhookProcessingResult handlePlaidWebhook(PlaidWebhookPayload payload);

    /**
     * Create Link token for Plaid Link initialization
     *
     * @param userId User ID requesting link token
     * @return Link token result with token and expiration
     * @throws BankAccountLinkException if token creation fails
     */
    LinkTokenResult createLinkToken(String userId);

    /**
     * Exchange public token for access token
     *
     * @param publicToken Public token from Plaid Link
     * @return Access token result with item details
     * @throws BankAccountLinkException if exchange fails
     */
    AccessTokenResult exchangePublicToken(String publicToken);
}
