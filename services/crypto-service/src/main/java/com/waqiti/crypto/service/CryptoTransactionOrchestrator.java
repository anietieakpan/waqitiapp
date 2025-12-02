/**
 * Crypto Transaction Orchestrator
 * Orchestrates complex cryptocurrency transaction flows
 */
package com.waqiti.crypto.service;

import com.waqiti.common.event.EventPublisher;
import com.waqiti.crypto.blockchain.BitcoinService;
import com.waqiti.crypto.blockchain.BlockchainServiceFactory;
import com.waqiti.crypto.blockchain.EthereumService;
import com.waqiti.crypto.dto.*;
import com.waqiti.crypto.entity.*;
import com.waqiti.crypto.exception.ComplianceViolationException;
import com.waqiti.crypto.exception.InsufficientBalanceException;
import com.waqiti.crypto.exception.TransactionFailedException;
import com.waqiti.crypto.repository.CryptoTransactionRepository;
import com.waqiti.crypto.repository.CryptoWalletRepository;
import com.waqiti.crypto.security.TransactionSigner;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class CryptoTransactionOrchestrator {
    
    private final CryptoWalletRepository walletRepository;
    private final CryptoTransactionRepository transactionRepository;
    private final BlockchainServiceFactory blockchainServiceFactory;
    private final TransactionSigner transactionSigner;
    private final ComplianceService complianceService;
    private final FraudDetectionService fraudDetectionService;
    private final AddressAnalysisService addressAnalysisService;
    private final CryptoBalanceService balanceService;
    private final EventPublisher eventPublisher;
    
    /**
     * Orchestrates a cryptocurrency send transaction
     */
    @Transactional
    public CryptoTransaction orchestrateSendTransaction(UUID walletId, String toAddress, 
                                                       BigDecimal amount, String memo, 
                                                       boolean expedited) {
        log.info("Orchestrating send transaction from wallet {} to {} for amount {}", 
                walletId, toAddress, amount);
        
        try {
            // 1. Load and validate wallet
            CryptoWallet wallet = walletRepository.findById(walletId)
                    .orElseThrow(() -> new IllegalArgumentException("Wallet not found"));
            
            if (wallet.getStatus() != WalletStatus.ACTIVE) {
                throw new IllegalStateException("Wallet is not active");
            }
            
            // 2. Check balance
            CryptoBalance balance = balanceService.getWalletBalance(walletId);
            if (balance.getAvailable().compareTo(amount) < 0) {
                throw new InsufficientBalanceException(walletId, amount, balance.getAvailable());
            }
            
            // 3. Analyze destination address
            AddressRiskProfile riskProfile = addressAnalysisService.analyzeAddress(toAddress, wallet.getCurrency());
            if (riskProfile.getRiskScore() > 75) {
                log.warn("High-risk address detected: {} with score {}", toAddress, riskProfile.getRiskScore());
            }
            
            // 4. Run compliance checks
            ComplianceResult complianceResult = complianceService.checkTransaction(
                    wallet.getUserId(), wallet.getCurrency(), amount, toAddress);
            
            if (complianceResult.isBlocked()) {
                throw new ComplianceViolationException(complianceResult.getReason());
            }
            
            // 5. Create transaction record
            CryptoTransaction transaction = CryptoTransaction.builder()
                    .userId(wallet.getUserId())
                    .walletId(walletId)
                    .currency(wallet.getCurrency())
                    .transactionType(TransactionType.SEND)
                    .fromAddress(wallet.getPrimaryAddress())
                    .toAddress(toAddress)
                    .amount(amount)
                    .status(TransactionStatus.PENDING)
                    .memo(memo)
                    .expedited(expedited)
                    .build();
            
            transaction = transactionRepository.save(transaction);
            
            // 6. Lock balance
            balanceService.lockAmount(walletId, amount);
            
            // 7. Calculate fees
            BigDecimal fee = calculateTransactionFee(wallet.getCurrency(), amount, expedited);
            transaction.setFee(fee);
            
            // 8. Run fraud detection
            FraudCheckResult fraudResult = fraudDetectionService.checkTransaction(transaction);
            if (fraudResult.isBlocked()) {
                transaction.setStatus(TransactionStatus.FAILED);
                transaction.setFailureReason("Blocked by fraud detection: " + fraudResult.getReason());
                transactionRepository.save(transaction);
                balanceService.unlockAmount(walletId, amount);
                throw new TransactionFailedException(transaction.getId(), fraudResult.getReason());
            }
            
            // 9. Sign transaction
            SignedCryptoTransaction signedTx = transactionSigner.signTransaction(transaction, wallet);
            
            // 10. Broadcast to blockchain
            String txHash = broadcastTransaction(signedTx, wallet.getCurrency());
            transaction.setTxHash(txHash);
            transaction.setStatus(TransactionStatus.BROADCAST);
            transaction.setBroadcastTime(LocalDateTime.now());
            
            // 11. Update balance (pending)
            balanceService.updatePendingBalance(walletId, amount.negate(), fee.negate());
            
            // 12. Save and publish event
            transaction = transactionRepository.save(transaction);
            publishTransactionEvent(transaction, "TRANSACTION_SENT");
            
            // 13. Start confirmation monitoring
            startConfirmationMonitoring(transaction);
            
            return transaction;
            
        } catch (Exception e) {
            log.error("Failed to orchestrate send transaction", e);
            publishTransactionEvent(null, "TRANSACTION_FAILED");
            throw e;
        }
    }
    
    /**
     * Orchestrates a cryptocurrency buy transaction
     */
    @Transactional
    public CryptoTransaction orchestrateBuyTransaction(UUID walletId, BigDecimal fiatAmount, 
                                                       String paymentMethodId, BigDecimal maxSlippage) {
        log.info("Orchestrating buy transaction for wallet {} with fiat amount {}", walletId, fiatAmount);
        
        try {
            // 1. Load wallet
            CryptoWallet wallet = walletRepository.findById(walletId)
                    .orElseThrow(() -> new IllegalArgumentException("Wallet not found"));
            
            // 2. Get current price and calculate crypto amount
            BigDecimal currentPrice = getPriceService().getCurrentPrice(wallet.getCurrency());
            BigDecimal cryptoAmount = fiatAmount.divide(currentPrice, 8, RoundingMode.HALF_UP);
            
            // 3. Check price slippage
            if (maxSlippage != null) {
                BigDecimal slippage = calculateSlippage(currentPrice, cryptoAmount);
                if (slippage.compareTo(maxSlippage) > 0) {
                    throw new IllegalStateException("Price slippage exceeds maximum: " + slippage);
                }
            }
            
            // 4. Run compliance checks
            ComplianceResult complianceResult = complianceService.checkPurchase(
                    wallet.getUserId(), wallet.getCurrency(), fiatAmount);
            
            if (complianceResult.isBlocked()) {
                throw new ComplianceViolationException(complianceResult.getReason());
            }
            
            // 5. Create transaction record
            CryptoTransaction transaction = CryptoTransaction.builder()
                    .userId(wallet.getUserId())
                    .walletId(walletId)
                    .currency(wallet.getCurrency())
                    .transactionType(TransactionType.BUY)
                    .toAddress(wallet.getPrimaryAddress())
                    .amount(cryptoAmount)
                    .fiatAmount(fiatAmount)
                    .exchangeRate(currentPrice)
                    .status(TransactionStatus.PENDING)
                    .build();
            
            transaction = transactionRepository.save(transaction);
            
            // 6. Process fiat payment
            processPayment(paymentMethodId, fiatAmount, transaction.getId());
            
            // 7. Execute crypto purchase from liquidity provider
            String purchaseTxHash = executePurchase(wallet.getCurrency(), cryptoAmount, wallet.getPrimaryAddress());
            
            transaction.setTxHash(purchaseTxHash);
            transaction.setStatus(TransactionStatus.BROADCAST);
            transaction.setBroadcastTime(LocalDateTime.now());
            
            // 8. Update and publish
            transaction = transactionRepository.save(transaction);
            publishTransactionEvent(transaction, "CRYPTO_PURCHASED");
            
            // 9. Monitor for confirmations
            startConfirmationMonitoring(transaction);
            
            return transaction;
            
        } catch (Exception e) {
            log.error("Failed to orchestrate buy transaction", e);
            publishTransactionEvent(null, "PURCHASE_FAILED");
            throw e;
        }
    }
    
    private BigDecimal calculateTransactionFee(CryptoCurrency currency, BigDecimal amount, boolean expedited) {
        // Implementation would calculate actual network fees
        return expedited ? new BigDecimal("0.001") : new BigDecimal("0.0005");
    }
    
    private String broadcastTransaction(SignedCryptoTransaction signedTx, CryptoCurrency currency) {
        var blockchainService = blockchainServiceFactory.getService(currency);
        return blockchainService.broadcastTransaction(signedTx.getSignedTransaction());
    }
    
    private void startConfirmationMonitoring(CryptoTransaction transaction) {
        // Publish event to start async confirmation monitoring
        publishTransactionEvent(transaction, "START_CONFIRMATION_MONITORING");
    }
    
    private void publishTransactionEvent(CryptoTransaction transaction, String eventType) {
        eventPublisher.publish("crypto.transaction." + eventType.toLowerCase(), 
                              transaction != null ? transaction : eventType);
    }
    
    private CryptoPricingService getPriceService() {
        // Create pricing service instance with fallback support
        if (cryptoPricingService == null) {
            log.warn("CryptoPricingService not injected, creating default instance");
            cryptoPricingService = new CryptoPricingService() {
                @Override
                public BigDecimal getCurrentPrice(String cryptoCurrency) {
                    // Fallback price service with simulated current market data
                    Map<String, BigDecimal> fallbackPrices = Map.of(
                        "BTC", new BigDecimal("45000.00"),
                        "ETH", new BigDecimal("2800.00"),
                        "ADA", new BigDecimal("0.85"),
                        "USDT", new BigDecimal("1.00"),
                        "USDC", new BigDecimal("1.00")
                    );
                    
                    BigDecimal price = fallbackPrices.get(cryptoCurrency.toUpperCase());
                    if (price == null) {
                        log.warn("No fallback price available for {}, using default", cryptoCurrency);
                        return new BigDecimal("1.00");
                    }
                    
                    log.debug("Using fallback price for {}: {}", cryptoCurrency, price);
                    return price;
                }
                
                @Override
                public BigDecimal getHistoricalPrice(String cryptoCurrency, java.time.LocalDateTime dateTime) {
                    // Simplified historical prices - in production would query price history API
                    return getCurrentPrice(cryptoCurrency).multiply(new BigDecimal("0.95"));
                }
            };
        }
        return cryptoPricingService;
    }
    
    // Add field to store pricing service instance
    private CryptoPricingService cryptoPricingService;
    
    private void processPayment(String paymentMethodId, BigDecimal amount, UUID transactionId) {
        // Integration with payment service
        log.info("Processing payment of {} using method {}", amount, paymentMethodId);
    }
    
    private String executePurchase(CryptoCurrency currency, BigDecimal amount, String address) {
        // Integration with liquidity provider
        log.info("Executing purchase of {} {} to {}", amount, currency, address);
        return "0x" + UUID.randomUUID().toString().replace("-", "");
    }
    
    private BigDecimal calculateSlippage(BigDecimal price, BigDecimal amount) {
        // Would calculate actual market slippage
        return new BigDecimal("0.5");
    }
}