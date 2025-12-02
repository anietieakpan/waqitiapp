package com.waqiti.wallet.service;

import com.waqiti.common.kyc.annotation.RequireKYCVerification;
import com.waqiti.common.kyc.service.KYCClientService;
import com.waqiti.wallet.domain.Wallet;
import com.waqiti.wallet.dto.*;
import com.waqiti.wallet.client.dto.TransferResponse;
import com.waqiti.wallet.exception.InsufficientKYCLevelException;
import com.waqiti.wallet.exception.TransferLimitExceededException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

/**
 * Wallet service with integrated KYC verification checks
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class KYCIntegratedWalletService {

    private final WalletService walletService;
    private final KYCClientService kycClientService;

    // Transfer limits based on KYC level
    private static final BigDecimal BASIC_KYC_DAILY_LIMIT = new BigDecimal("1000");
    private static final BigDecimal BASIC_KYC_SINGLE_LIMIT = new BigDecimal("200");
    
    private static final BigDecimal INTERMEDIATE_KYC_DAILY_LIMIT = new BigDecimal("10000");
    private static final BigDecimal INTERMEDIATE_KYC_SINGLE_LIMIT = new BigDecimal("2000");
    
    private static final BigDecimal ADVANCED_KYC_DAILY_LIMIT = new BigDecimal("100000");
    private static final BigDecimal ADVANCED_KYC_SINGLE_LIMIT = new BigDecimal("25000");
    
    private static final BigDecimal UNVERIFIED_DAILY_LIMIT = new BigDecimal("100");
    private static final BigDecimal UNVERIFIED_SINGLE_LIMIT = new BigDecimal("50");

    /**
     * Transfer funds with KYC verification
     */
    @Transactional
    @RequireKYCVerification(level = RequireKYCVerification.VerificationLevel.BASIC,
                           message = "Basic KYC verification required for transfers")
    public TransferResponse transferWithKYC(TransferRequest request) {
        UUID senderId = request.getSourceWalletId();
        
        // Get sender's user ID from wallet
        WalletResponse senderWallet = walletService.getWallet(senderId);
        String senderUserId = senderWallet.getUserId().toString();
        
        // Validate transfer limits based on KYC level
        validateTransferLimits(senderUserId, request.getAmount());
        
        // For international transfers, require intermediate KYC
        if (request.isInternational()) {
            if (!kycClientService.canUserMakeInternationalTransfer(senderUserId)) {
                throw new InsufficientKYCLevelException(
                    "Intermediate KYC verification required for international transfers"
                );
            }
        }
        
        // For crypto transfers, require advanced KYC
        if (request.getCurrency().startsWith("CRYPTO_")) {
            if (!kycClientService.canUserPurchaseCrypto(senderUserId)) {
                throw new InsufficientKYCLevelException(
                    "Advanced KYC verification required for cryptocurrency transfers"
                );
            }
        }
        
        // Proceed with transfer
        TransactionResponse transactionResponse = walletService.transfer(request);
        
        // Convert to TransferResponse
        return TransferResponse.builder()
                .externalId(transactionResponse.getExternalId())
                .status(transactionResponse.getStatus())
                .build();
    }

    /**
     * Create wallet with KYC verification
     */
    @RequireKYCVerification(level = RequireKYCVerification.VerificationLevel.BASIC,
                           message = "KYC verification required to create wallets")
    public WalletResponse createWalletWithKYC(CreateWalletRequest request) {
        log.info("Creating wallet with KYC verification for user: {}", request.getUserId());
        
        // Crypto wallets require advanced KYC
        if (request.getCurrency().startsWith("CRYPTO_")) {
            if (!kycClientService.isUserAdvancedVerified(request.getUserId().toString())) {
                throw new InsufficientKYCLevelException(
                    "Advanced KYC verification required for cryptocurrency wallets"
                );
            }
        }
        
        return walletService.createWallet(request);
    }

    /**
     * Add funds to wallet with KYC verification
     */
    @RequireKYCVerification(level = RequireKYCVerification.VerificationLevel.INTERMEDIATE,
                           message = "Intermediate KYC verification required to add funds")
    public TransactionResponse addFundsWithKYC(UUID walletId, BigDecimal amount, String source) {
        log.info("Adding funds with KYC verification to wallet: {} amount: {}", walletId, amount);
        
        // Get wallet owner's user ID
        WalletResponse wallet = walletService.getWallet(walletId);
        String userId = wallet.getUserId().toString();
        
        // Large deposits require advanced KYC
        if (amount.compareTo(new BigDecimal("5000")) > 0) {
            if (!kycClientService.isUserAdvancedVerified(userId)) {
                throw new InsufficientKYCLevelException(
                    "Advanced KYC verification required for deposits over $5000"
                );
            }
        }
        
        DepositRequest depositRequest = DepositRequest.builder()
                .walletId(walletId)
                .amount(amount)
                .description(source)
                .build();
        return walletService.deposit(depositRequest);
    }

    /**
     * Withdraw funds with KYC verification
     */
    @RequireKYCVerification(level = RequireKYCVerification.VerificationLevel.INTERMEDIATE,
                           message = "Intermediate KYC verification required for withdrawals")
    public TransactionResponse withdrawWithKYC(UUID walletId, BigDecimal amount, String destination) {
        log.info("Withdrawing funds with KYC verification from wallet: {} amount: {}", walletId, amount);
        
        // Get wallet owner's user ID
        WalletResponse wallet = walletService.getWallet(walletId);
        String userId = wallet.getUserId().toString();
        
        // Validate withdrawal limits based on KYC level
        validateWithdrawalLimits(userId, amount);
        
        WithdrawalRequest withdrawalRequest = WithdrawalRequest.builder()
                .walletId(walletId)
                .amount(amount)
                .description(destination)
                .build();
        return walletService.withdraw(withdrawalRequest);
    }

    /**
     * Get wallet limits based on KYC level
     */
    public WalletLimits getWalletLimits(UUID userId) {
        String userIdStr = userId.toString();
        
        WalletLimits limits = new WalletLimits();
        
        if (kycClientService.isUserAdvancedVerified(userIdStr)) {
            limits.setDailyTransferLimit(ADVANCED_KYC_DAILY_LIMIT);
            limits.setSingleTransferLimit(ADVANCED_KYC_SINGLE_LIMIT);
            limits.setKycLevel("ADVANCED");
            limits.setInternationalTransfersEnabled(true);
            limits.setCryptoEnabled(true);
        } else if (kycClientService.isUserIntermediateVerified(userIdStr)) {
            limits.setDailyTransferLimit(INTERMEDIATE_KYC_DAILY_LIMIT);
            limits.setSingleTransferLimit(INTERMEDIATE_KYC_SINGLE_LIMIT);
            limits.setKycLevel("INTERMEDIATE");
            limits.setInternationalTransfersEnabled(true);
            limits.setCryptoEnabled(false);
        } else if (kycClientService.isUserBasicVerified(userIdStr)) {
            limits.setDailyTransferLimit(BASIC_KYC_DAILY_LIMIT);
            limits.setSingleTransferLimit(BASIC_KYC_SINGLE_LIMIT);
            limits.setKycLevel("BASIC");
            limits.setInternationalTransfersEnabled(false);
            limits.setCryptoEnabled(false);
        } else {
            limits.setDailyTransferLimit(UNVERIFIED_DAILY_LIMIT);
            limits.setSingleTransferLimit(UNVERIFIED_SINGLE_LIMIT);
            limits.setKycLevel("UNVERIFIED");
            limits.setInternationalTransfersEnabled(false);
            limits.setCryptoEnabled(false);
        }
        
        return limits;
    }

    /**
     * Enable instant transfers for KYC verified users
     */
    @RequireKYCVerification(level = RequireKYCVerification.VerificationLevel.INTERMEDIATE,
                           message = "Intermediate KYC required for instant transfers")
    public void enableInstantTransfers(UUID walletId) {
        log.info("Enabling instant transfers for wallet: {}", walletId);
        
        WalletResponse wallet = walletService.getWallet(walletId);
        String userId = wallet.getUserId().toString();
        
        // Verify user has at least intermediate KYC
        if (!kycClientService.isUserIntermediateVerified(userId)) {
            throw new InsufficientKYCLevelException(
                "Intermediate KYC verification required to enable instant transfers"
            );
        }
        
        // For advanced features, require advanced KYC
        if (kycClientService.isUserAdvancedVerified(userId)) {
            log.info("User {} has advanced KYC - enabling premium instant transfer features", userId);
            // Enable premium instant transfer features
            walletService.enablePremiumFeatures(walletId, "INSTANT_TRANSFERS_PREMIUM");
        } else {
            // Enable standard instant transfers
            walletService.enableStandardFeatures(walletId, "INSTANT_TRANSFERS_STANDARD");
        }
        
        // Update wallet metadata to reflect instant transfer capability
        try {
            walletService.updateWalletMetadata(walletId, Map.of(
                "instantTransfersEnabled", true,
                "enabledAt", java.time.LocalDateTime.now().toString(),
                "kycLevel", kycClientService.getUserKYCLevel(userId)
            ));
            
            log.info("Successfully enabled instant transfers for wallet: {} (user: {})", walletId, userId);
        } catch (Exception e) {
            log.error("Failed to update wallet metadata for instant transfers: {}", walletId, e);
            throw new RuntimeException("Failed to enable instant transfers", e);
        }
    }

    /**
     * Validate transfer limits based on KYC level
     */
    private void validateTransferLimits(String userId, BigDecimal amount) {
        WalletLimits limits = getWalletLimits(UUID.fromString(userId));
        
        if (amount.compareTo(limits.getSingleTransferLimit()) > 0) {
            throw new TransferLimitExceededException(
                String.format("Transfer amount exceeds your limit of %s. Current KYC level: %s",
                    limits.getSingleTransferLimit(), limits.getKycLevel())
            );
        }
        
        // Check daily limit
        BigDecimal dailyTotal = calculateDailyTransferTotal(userId);
        if (dailyTotal.add(amount).compareTo(limits.getDailyTransferLimit()) > 0) {
            throw new TransferLimitExceededException(
                String.format("Transfer would exceed your daily limit of %s. Current daily total: %s",
                    limits.getDailyTransferLimit(), dailyTotal)
            );
        }
    }

    /**
     * Validate withdrawal limits based on KYC level
     */
    private void validateWithdrawalLimits(String userId, BigDecimal amount) {
        WalletLimits limits = getWalletLimits(UUID.fromString(userId));
        
        // Withdrawals have same limits as transfers but could have different rules
        if (amount.compareTo(limits.getSingleTransferLimit()) > 0) {
            throw new TransferLimitExceededException(
                String.format("Withdrawal amount exceeds your limit of %s. Current KYC level: %s",
                    limits.getSingleTransferLimit(), limits.getKycLevel())
            );
        }
    }

    /**
     * Calculate total transfers for the day
     */
    private BigDecimal calculateDailyTransferTotal(String userId) {
        try {
            log.debug("Calculating daily transfer total for user: {}", userId);
            
            // Get all user's wallets
            var userWallets = walletService.getUserWallets(UUID.fromString(userId));
            
            BigDecimal totalTransferred = BigDecimal.ZERO;
            java.time.LocalDate today = java.time.LocalDate.now();
            
            // Sum up all outgoing transfers for today across all wallets
            for (var walletResponse : userWallets) {
                try {
                    // Get today's transactions for this wallet
                    var transactions = walletService.getWalletTransactions(
                        UUID.fromString(walletResponse.getWalletId()), 
                        today.atStartOfDay(), 
                        today.plusDays(1).atStartOfDay()
                    );
                    
                    // Sum outgoing transfers only
                    BigDecimal walletTotal = transactions.stream()
                        .filter(tx -> "TRANSFER_OUT".equals(tx.getType()) || "WITHDRAWAL".equals(tx.getType()))
                        .filter(tx -> "COMPLETED".equals(tx.getStatus()) || "PENDING".equals(tx.getStatus()))
                        .map(tx -> tx.getAmount())
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
                    
                    totalTransferred = totalTransferred.add(walletTotal);
                    log.debug("Wallet {} contributed {} to daily total", walletResponse.getWalletId(), walletTotal);
                    
                } catch (Exception e) {
                    log.warn("Failed to calculate daily total for wallet: {} - skipping", walletResponse.getWalletId(), e);
                }
            }
            
            log.debug("Total daily transfers for user {}: {}", userId, totalTransferred);
            return totalTransferred;
            
        } catch (Exception e) {
            log.error("Failed to calculate daily transfer total for user: {}", userId, e);
            // Return conservative estimate in case of error
            return new BigDecimal("999999"); // High value to trigger limits if there's an error
        }
    }
}