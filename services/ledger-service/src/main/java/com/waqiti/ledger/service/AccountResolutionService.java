package com.waqiti.ledger.service;

import com.waqiti.ledger.dto.CreateAccountRequest;
import com.waqiti.ledger.dto.CreateAccountResponse;
import com.waqiti.ledger.entity.WalletAccountMappingEntity;
import com.waqiti.ledger.entity.WalletAccountMappingEntity.MappingType;
import com.waqiti.ledger.exception.AccountNotFoundException;
import com.waqiti.ledger.repository.AccountRepository;
import com.waqiti.ledger.repository.WalletAccountMappingRepository;
import com.waqiti.ledger.domain.Account;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * CRITICAL P0 FIX: Account Resolution Service
 *
 * Replaces hardcoded UUID.nameUUIDFromBytes() with proper chart of accounts integration.
 * Provides account ID resolution for wallet operations with full audit trail and compliance.
 *
 * This service ensures:
 * - Wallet transactions map to proper ledger accounts
 * - Audit trail for all account assignments
 * - Multi-currency support
 * - Compliance with GAAP, SOX, PCI DSS
 *
 * Migration Strategy:
 * 1. New wallets: Create mappings on wallet creation
 * 2. Existing wallets: Lazy creation on first transaction (with account lookup/creation)
 * 3. System accounts: Pre-created during deployment
 *
 * @author Waqiti Platform
 * @version 1.0.0
 * @since 2025-10-05
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AccountResolutionService {

    private final WalletAccountMappingRepository mappingRepository;
    private final AccountRepository accountRepository;
    private final ChartOfAccountsService chartOfAccountsService;

    /**
     * CRITICAL: Resolve wallet liability account ID
     *
     * Returns the ledger account ID for a wallet's liability account.
     * If no mapping exists, creates one automatically (lazy initialization).
     *
     * @param walletId Wallet UUID from wallet-service
     * @param currency Currency code (ISO 4217)
     * @return Ledger account UUID from chart of accounts
     * @throws AccountNotFoundException if account cannot be resolved or created
     */
    @Cacheable(value = "walletLiabilityAccounts", key = "#walletId + ':' + #currency")
    @Transactional
    public UUID resolveWalletLiabilityAccountId(UUID walletId, String currency) {
        log.debug("Resolving wallet liability account for wallet: {}, currency: {}", walletId, currency);

        try {
            // Step 1: Look up existing mapping
            return mappingRepository
                .findByWalletIdAndCurrencyAndMappingTypeAndIsActiveTrue(
                    walletId, currency, MappingType.WALLET_LIABILITY)
                .map(WalletAccountMappingEntity::getAccountId)
                .orElseGet(() -> {
                    // Step 2: No mapping exists - create one (lazy initialization)
                    log.warn("COMPLIANCE: No wallet-account mapping found for wallet: {}, currency: {}. Creating mapping...",
                        walletId, currency);
                    return createWalletLiabilityMapping(walletId, currency);
                });

        } catch (Exception e) {
            log.error("CRITICAL: Failed to resolve wallet liability account for wallet: {}, currency: {}",
                walletId, currency, e);
            throw new AccountNotFoundException(
                "Failed to resolve wallet liability account for wallet: " + walletId + ", currency: " + currency, e);
        }
    }

    /**
     * CRITICAL: Resolve cash clearing account ID
     *
     * Returns the ledger account ID for cash clearing (cash in transit).
     * Cash clearing accounts are system-wide per currency, not per-wallet.
     *
     * @param currency Currency code (ISO 4217)
     * @return Ledger account UUID from chart of accounts
     * @throws AccountNotFoundException if account cannot be resolved
     */
    @Cacheable(value = "cashClearingAccounts", key = "#currency")
    public UUID resolveCashClearingAccountId(String currency) {
        log.debug("Resolving cash clearing account for currency: {}", currency);

        try {
            return mappingRepository
                .findCashClearingAccountByCurrency(currency)
                .map(WalletAccountMappingEntity::getAccountId)
                .orElseThrow(() -> {
                    log.error("CRITICAL: No cash clearing account found for currency: {}. " +
                        "Cash clearing accounts must be pre-created during system setup!", currency);
                    return new AccountNotFoundException(
                        "Cash clearing account not found for currency: " + currency + ". " +
                        "Please create cash clearing account in chart of accounts and mapping table.");
                });

        } catch (AccountNotFoundException e) {
            throw e;
        } catch (Exception e) {
            log.error("CRITICAL: Failed to resolve cash clearing account for currency: {}", currency, e);
            throw new AccountNotFoundException(
                "Failed to resolve cash clearing account for currency: " + currency, e);
        }
    }

    /**
     * CRITICAL: Resolve fee revenue account ID
     *
     * Returns the ledger account ID for transaction fee revenue.
     *
     * @param currency Currency code (ISO 4217)
     * @return Ledger account UUID from chart of accounts
     * @throws AccountNotFoundException if account cannot be resolved
     */
    @Cacheable(value = "feeRevenueAccounts", key = "#currency")
    public UUID resolveFeeRevenueAccountId(String currency) {
        log.debug("Resolving fee revenue account for currency: {}", currency);

        try {
            return mappingRepository
                .findFeeRevenueAccountByCurrency(currency)
                .map(WalletAccountMappingEntity::getAccountId)
                .orElseThrow(() -> {
                    log.error("CRITICAL: No fee revenue account found for currency: {}", currency);
                    return new AccountNotFoundException(
                        "Fee revenue account not found for currency: " + currency);
                });

        } catch (AccountNotFoundException e) {
            throw e;
        } catch (Exception e) {
            log.error("CRITICAL: Failed to resolve fee revenue account for currency: {}", currency, e);
            throw new AccountNotFoundException(
                "Failed to resolve fee revenue account for currency: " + currency, e);
        }
    }

    /**
     * Create wallet liability account mapping
     *
     * Called when no mapping exists for a wallet (lazy initialization).
     * Creates both the account in chart of accounts and the mapping.
     */
    @Transactional
    private UUID createWalletLiabilityMapping(UUID walletId, String currency) {
        log.info("COMPLIANCE: Creating wallet liability account mapping for wallet: {}, currency: {}",
            walletId, currency);

        try {
            // Step 1: Find or create parent liability account
            UUID parentLiabilityAccountId = findParentLiabilityAccount(currency);

            // Step 2: Create account in chart of accounts
            String accountCode = generateWalletAccountCode(walletId, currency);
            String accountName = String.format("Wallet Liability - %s - %s", walletId, currency);

            CreateAccountRequest accountRequest = CreateAccountRequest.builder()
                .accountCode(accountCode)
                .accountName(accountName)
                .accountType(Account.AccountType.CURRENT_LIABILITY.name())
                .parentAccountId(parentLiabilityAccountId)
                .description(String.format("Customer wallet balance liability for wallet %s in %s", walletId, currency))
                .currency(currency)
                .normalBalance(Account.NormalBalance.CREDIT.name())
                .allowsTransactions(true)
                .build();

            CreateAccountResponse accountResponse = chartOfAccountsService.createAccount(accountRequest);
            UUID accountId = accountResponse.getAccountId();

            // Step 3: Create mapping
            WalletAccountMappingEntity mapping = WalletAccountMappingEntity.builder()
                .walletId(walletId)
                .currency(currency)
                .accountId(accountId)
                .mappingType(MappingType.WALLET_LIABILITY)
                .isActive(true)
                .createdAt(LocalDateTime.now())
                .lastUpdated(LocalDateTime.now())
                .createdBy("system-auto-provision")
                .notes(String.format("Auto-created during first transaction for wallet %s", walletId))
                .build();

            WalletAccountMappingEntity savedMapping = mappingRepository.save(mapping);

            log.info("COMPLIANCE: Successfully created wallet liability mapping - Mapping ID: {}, Account ID: {}, Wallet: {}, Currency: {}",
                savedMapping.getMappingId(), accountId, walletId, currency);

            return accountId;

        } catch (Exception e) {
            log.error("CRITICAL: Failed to create wallet liability mapping for wallet: {}, currency: {}",
                walletId, currency, e);
            throw new AccountNotFoundException(
                "Failed to create wallet liability account mapping for wallet: " + walletId, e);
        }
    }

    /**
     * Find parent liability account for customer wallets
     */
    private UUID findParentLiabilityAccount(String currency) {
        // Look for "Customer Wallet Liabilities - {CURRENCY}" account
        String parentAccountCode = "2200-" + currency; // e.g., 2200-USD

        return accountRepository.findByAccountCode(parentAccountCode)
            .map(Account::getAccountId)
            .orElseThrow(() -> {
                log.error("CRITICAL: Parent liability account not found: {}. " +
                    "Please create parent account '{}' in chart of accounts!", parentAccountCode, parentAccountCode);
                return new AccountNotFoundException(
                    "Parent liability account not found: " + parentAccountCode + ". " +
                    "Create parent account in chart of accounts first.");
            });
    }

    /**
     * Generate unique account code for wallet liability account
     * Format: 2200-{CURRENCY}-{WALLET_ID_SHORT}
     * Example: 2200-USD-A1B2C3D4
     */
    private String generateWalletAccountCode(UUID walletId, String currency) {
        // Use first 8 characters of wallet UUID for uniqueness
        String walletIdShort = walletId.toString().replace("-", "").substring(0, 8).toUpperCase();
        return String.format("2200-%s-%s", currency, walletIdShort);
    }

    /**
     * Create wallet-account mapping manually (for admin operations)
     */
    @Transactional
    public WalletAccountMappingEntity createMapping(
        UUID walletId,
        UUID accountId,
        String currency,
        MappingType mappingType,
        UUID userId,
        String notes
    ) {
        log.info("Creating wallet-account mapping: wallet={}, account={}, currency={}, type={}",
            walletId, accountId, currency, mappingType);

        // Validate account exists
        Account account = accountRepository.findById(accountId)
            .orElseThrow(() -> new AccountNotFoundException("Account not found: " + accountId));

        // Validate account is active and allows transactions
        chartOfAccountsService.validateAccountForTransaction(accountId);

        // Check for existing mapping
        if (mappingRepository.existsByWalletIdAndCurrencyAndMappingTypeAndIsActiveTrue(
            walletId, currency, mappingType)) {
            throw new IllegalStateException(
                "Active mapping already exists for wallet: " + walletId + ", currency: " + currency + ", type: " + mappingType);
        }

        WalletAccountMappingEntity mapping = WalletAccountMappingEntity.builder()
            .walletId(walletId)
            .currency(currency)
            .accountId(accountId)
            .mappingType(mappingType)
            .userId(userId)
            .isActive(true)
            .createdBy("admin")
            .notes(notes)
            .build();

        WalletAccountMappingEntity savedMapping = mappingRepository.save(mapping);

        log.info("Successfully created wallet-account mapping: {}", savedMapping.getMappingId());
        return savedMapping;
    }

    /**
     * P2 QUICK WIN: Get wallet currency from mapping
     *
     * Returns the currency for a wallet by looking up its account mapping.
     * This provides dynamic currency resolution instead of hardcoding to USD.
     *
     * @param walletId Wallet UUID
     * @return Currency code (e.g., "USD", "EUR", "GBP") or null if no mapping exists
     */
    @Cacheable(value = "walletCurrency", key = "#walletId")
    public String getWalletCurrency(UUID walletId) {
        log.debug("P2: Resolving currency for wallet: {}", walletId);

        try {
            return mappingRepository
                .findFirstByWalletIdAndMappingTypeAndIsActiveTrue(walletId, MappingType.WALLET_LIABILITY)
                .map(WalletAccountMappingEntity::getCurrency)
                .orElse(null);

        } catch (Exception e) {
            log.warn("P2: Failed to resolve currency for wallet: {} - will use default", walletId, e);
            return null;
        }
    }

    /**
     * Deactivate wallet-account mapping (soft delete)
     */
    @Transactional
    public void deactivateMapping(UUID mappingId, String reason) {
        log.info("Deactivating wallet-account mapping: {}, reason: {}", mappingId, reason);

        WalletAccountMappingEntity mapping = mappingRepository.findById(mappingId)
            .orElseThrow(() -> new AccountNotFoundException("Mapping not found: " + mappingId));

        mapping.setIsActive(false);
        mapping.setUpdatedBy("admin");
        mapping.setNotes(mapping.getNotes() + " | Deactivated: " + reason);
        mapping.setLastUpdated(LocalDateTime.now());

        mappingRepository.save(mapping);

        log.info("Successfully deactivated wallet-account mapping: {}", mappingId);
    }
}
