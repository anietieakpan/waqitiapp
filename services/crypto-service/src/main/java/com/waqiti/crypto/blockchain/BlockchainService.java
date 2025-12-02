/**
 * Blockchain Service
 * Core service for interacting with multiple blockchain networks
 */
package com.waqiti.crypto.blockchain;

import com.waqiti.crypto.dto.*;
import com.waqiti.crypto.entity.CryptoCurrency;
import com.waqiti.crypto.exception.UnsupportedCryptoCurrencyException;
import com.waqiti.crypto.repository.CryptoTransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.LocalDateTime;
import java.util.concurrent.CompletableFuture;

@Service
@RequiredArgsConstructor
@Slf4j
public class BlockchainService {

    private final BitcoinService bitcoinService;
    private final EthereumService ethereumService;
    private final LitecoinService litecoinService;
    private final BlockchainNodeManager nodeManager;
    private final CryptoTransactionRepository transactionRepository;

    /**
     * Broadcast signed transaction to appropriate blockchain network
     */
    public String broadcastTransaction(SignedCryptoTransaction signedTx) {
        log.info("Broadcasting transaction for currency: {} amount: {}", 
                signedTx.getCurrency(), signedTx.getAmount());
        
        try {
            String txHash = switch (signedTx.getCurrency()) {
                case BITCOIN -> bitcoinService.broadcastTransaction(signedTx);
                case ETHEREUM, USDC, USDT -> ethereumService.broadcastTransaction(signedTx);
                case LITECOIN -> litecoinService.broadcastTransaction(signedTx);
            };
            
            log.info("Transaction broadcasted successfully: {} hash: {}", 
                    signedTx.getCurrency(), txHash);
            
            return txHash;
            
        } catch (Exception e) {
            log.error("Failed to broadcast transaction for currency: {}", signedTx.getCurrency(), e);
            throw new TransactionBroadcastException("Failed to broadcast transaction", e);
        }
    }

    /**
     * Get current balance for an address
     */
    public BigDecimal getAddressBalance(String address, CryptoCurrency currency) {
        log.debug("Getting balance for address: {} currency: {}", address, currency);
        
        try {
            return switch (currency) {
                case BITCOIN -> bitcoinService.getAddressBalance(address);
                case ETHEREUM -> ethereumService.getEtherBalance(address);
                case USDC -> ethereumService.getTokenBalance(address, getTokenContractAddress(currency));
                case USDT -> ethereumService.getTokenBalance(address, getTokenContractAddress(currency));
                case LITECOIN -> litecoinService.getAddressBalance(address);
            };
        } catch (Exception e) {
            log.error("Failed to get balance for address: {} currency: {}", address, currency, e);
            throw new BalanceRetrievalException("Failed to retrieve balance", e);
        }
    }

    /**
     * Get transaction details from blockchain
     */
    public BlockchainTransaction getTransaction(String txHash, CryptoCurrency currency) {
        log.debug("Getting transaction: {} for currency: {}", txHash, currency);
        
        try {
            return switch (currency) {
                case BITCOIN -> bitcoinService.getTransaction(txHash);
                case ETHEREUM, USDC, USDT -> ethereumService.getTransaction(txHash);
                case LITECOIN -> litecoinService.getTransaction(txHash);
            };
        } catch (Exception e) {
            log.error("Failed to get transaction: {} for currency: {}", txHash, currency, e);
            throw new TransactionRetrievalException("Failed to retrieve transaction", e);
        }
    }

    /**
     * Get current network fees for transaction
     */
    public NetworkFees getNetworkFees(CryptoCurrency currency) {
        log.debug("Getting network fees for currency: {}", currency);
        
        try {
            return switch (currency) {
                case BITCOIN -> bitcoinService.getNetworkFees();
                case ETHEREUM, USDC, USDT -> ethereumService.getNetworkFees();
                case LITECOIN -> litecoinService.getNetworkFees();
            };
        } catch (Exception e) {
            log.error("Failed to get network fees for currency: {}", currency, e);
            throw new NetworkFeesException("Failed to retrieve network fees", e);
        }
    }

    /**
     * Estimate transaction fee for given parameters
     */
    public BigDecimal estimateTransactionFee(
            CryptoCurrency currency, 
            String fromAddress, 
            String toAddress, 
            BigDecimal amount,
            FeeSpeed feeSpeed) {
        
        log.debug("Estimating transaction fee for currency: {} amount: {} speed: {}", 
                currency, amount, feeSpeed);
        
        try {
            return switch (currency) {
                case BITCOIN -> bitcoinService.estimateTransactionFee(fromAddress, toAddress, amount, feeSpeed);
                case ETHEREUM -> ethereumService.estimateEtherTransactionFee(toAddress, amount, feeSpeed);
                case USDC, USDT -> ethereumService.estimateTokenTransactionFee(
                    toAddress, amount, getTokenContractAddress(currency), feeSpeed);
                case LITECOIN -> litecoinService.estimateTransactionFee(fromAddress, toAddress, amount, feeSpeed);
            };
        } catch (Exception e) {
            log.error("Failed to estimate transaction fee for currency: {}", currency, e);
            throw new FeeEstimationException("Failed to estimate transaction fee", e);
        }
    }

    /**
     * Validate address format for specific currency
     */
    public boolean validateAddress(String address, CryptoCurrency currency) {
        log.debug("Validating address: {} for currency: {}", address, currency);
        
        try {
            return switch (currency) {
                case BITCOIN -> bitcoinService.validateAddress(address);
                case ETHEREUM, USDC, USDT -> ethereumService.validateAddress(address);
                case LITECOIN -> litecoinService.validateAddress(address);
            };
        } catch (Exception e) {
            log.warn("Address validation failed for: {} currency: {}", address, currency, e);
            return false;
        }
    }

    /**
     * Monitor transaction confirmations asynchronously
     */
    @Async("cryptoTaskExecutor")
    public CompletableFuture<TransactionConfirmation> monitorTransactionConfirmations(
            String txHash, 
            CryptoCurrency currency,
            UUID transactionId) {
        
        log.info("Starting confirmation monitoring for transaction: {} currency: {}", txHash, currency);
        
        return CompletableFuture.supplyAsync(() -> {
            try {
                int requiredConfirmations = getRequiredConfirmations(currency);
                int currentConfirmations = 0;
                
                while (currentConfirmations < requiredConfirmations) {
                    // Wait before checking again
                    Thread.sleep(getConfirmationCheckInterval(currency));
                    
                    // Get current confirmation count
                    currentConfirmations = getConfirmationCount(txHash, currency);
                    
                    // Update transaction status in database
                    updateTransactionConfirmations(transactionId, currentConfirmations);
                    
                    log.debug("Transaction {} confirmations: {}/{}", 
                            txHash, currentConfirmations, requiredConfirmations);
                }
                
                // Mark transaction as confirmed
                markTransactionConfirmed(transactionId);
                
                return TransactionConfirmation.builder()
                    .txHash(txHash)
                    .confirmations(currentConfirmations)
                    .isConfirmed(true)
                    .confirmedAt(LocalDateTime.now())
                    .build();
                    
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Transaction monitoring interrupted", e);
            } catch (Exception e) {
                log.error("Error monitoring transaction confirmations: {}", txHash, e);
                throw new RuntimeException("Transaction monitoring failed", e);
            }
        });
    }

    /**
     * Get current confirmation count for transaction
     */
    public int getConfirmationCount(String txHash, CryptoCurrency currency) {
        try {
            return switch (currency) {
                case BITCOIN -> bitcoinService.getConfirmationCount(txHash);
                case ETHEREUM, USDC, USDT -> ethereumService.getConfirmationCount(txHash);
                case LITECOIN -> litecoinService.getConfirmationCount(txHash);
            };
        } catch (Exception e) {
            log.error("Failed to get confirmation count for transaction: {}", txHash, e);
            return 0;
        }
    }

    /**
     * Get block height for specific currency
     */
    public long getCurrentBlockHeight(CryptoCurrency currency) {
        try {
            return switch (currency) {
                case BITCOIN -> bitcoinService.getCurrentBlockHeight();
                case ETHEREUM, USDC, USDT -> ethereumService.getCurrentBlockHeight();
                case LITECOIN -> litecoinService.getCurrentBlockHeight();
            };
        } catch (Exception e) {
            log.error("Failed to get current block height for currency: {}", currency, e);
            throw new BlockchainException("Failed to get block height", e);
        }
    }

    /**
     * Check if node is synchronized with network
     */
    public boolean isNodeSynchronized(CryptoCurrency currency) {
        try {
            return switch (currency) {
                case BITCOIN -> bitcoinService.isNodeSynchronized();
                case ETHEREUM, USDC, USDT -> ethereumService.isNodeSynchronized();
                case LITECOIN -> litecoinService.isNodeSynchronized();
            };
        } catch (Exception e) {
            log.error("Failed to check node synchronization for currency: {}", currency, e);
            return false;
        }
    }

    /**
     * Get unspent transaction outputs (UTXOs) for Bitcoin-like currencies
     */
    public UTXOSet getUTXOs(String address, CryptoCurrency currency) {
        if (currency != CryptoCurrency.BITCOIN && currency != CryptoCurrency.LITECOIN) {
            throw new UnsupportedCryptoCurrencyException("UTXOs only supported for Bitcoin-like currencies. " +
                    "Currency " + currency + " does not support UTXO model.");
        }
        
        try {
            return switch (currency) {
                case BITCOIN -> bitcoinService.getUTXOs(address);
                case LITECOIN -> litecoinService.getUTXOs(address);
                default -> throw new UnsupportedCryptoCurrencyException("Unsupported currency for UTXO operations: " + currency);
            };
        } catch (UnsupportedCryptoCurrencyException e) {
            // Re-throw currency exceptions
            throw e;
        } catch (Exception e) {
            log.error("Failed to get UTXOs for address: {} currency: {}", address, currency, e);
            throw new UTXORetrievalException("Failed to retrieve UTXOs for " + currency + " address: " + address, e);
        }
    }

    /**
     * Get current gas price for Ethereum transactions
     */
    public BigInteger getCurrentGasPrice() {
        try {
            return ethereumService.getCurrentGasPrice();
        } catch (Exception e) {
            log.error("Failed to get current gas price", e);
            throw new GasPriceException("Failed to retrieve gas price", e);
        }
    }

    /**
     * Get optimal gas price based on desired confirmation speed
     */
    public BigInteger getOptimalGasPrice(FeeSpeed feeSpeed) {
        try {
            return ethereumService.getOptimalGasPrice(feeSpeed);
        } catch (Exception e) {
            log.error("Failed to get optimal gas price for speed: {}", feeSpeed, e);
            throw new GasPriceException("Failed to retrieve optimal gas price", e);
        }
    }

    // Private helper methods
    
    private String getTokenContractAddress(CryptoCurrency currency) {
        return switch (currency) {
            case USDC -> "0xA0b86a33E6441c4df7dD48C8e2c38b4F60c7c3b7"; // USDC contract address
            case USDT -> "0xdAC17F958D2ee523a2206206994597C13D831ec7"; // USDT contract address
            default -> throw new UnsupportedCurrencyException("No token contract for currency: " + currency);
        };
    }
    
    private int getRequiredConfirmations(CryptoCurrency currency) {
        return switch (currency) {
            case BITCOIN -> 6;       // ~1 hour
            case ETHEREUM -> 12;     // ~3 minutes
            case USDC, USDT -> 12;   // ~3 minutes (same as ETH)
            case LITECOIN -> 6;      // ~15 minutes
        };
    }
    
    private long getConfirmationCheckInterval(CryptoCurrency currency) {
        return switch (currency) {
            case BITCOIN -> 30000L;      // 30 seconds
            case ETHEREUM -> 15000L;     // 15 seconds
            case USDC, USDT -> 15000L;   // 15 seconds
            case LITECOIN -> 30000L;     // 30 seconds
        };
    }
    
    @org.springframework.transaction.annotation.Transactional
    private void updateTransactionConfirmations(UUID transactionId, int confirmations) {
        try {
            transactionRepository.updateConfirmations(transactionId, confirmations);
        } catch (Exception e) {
            log.error("Failed to update transaction confirmations: {} confirmations: {}", 
                    transactionId, confirmations, e);
        }
    }
    
    @org.springframework.transaction.annotation.Transactional
    private void markTransactionConfirmed(UUID transactionId) {
        try {
            transactionRepository.markAsConfirmed(transactionId, LocalDateTime.now());
        } catch (Exception e) {
            log.error("Failed to mark transaction as confirmed: {}", transactionId, e);
        }
    }

    /**
     * Get network status for currency
     */
    public NetworkStatus getNetworkStatus(CryptoCurrency currency) {
        try {
            boolean isHealthy = isNodeSynchronized(currency);
            long blockHeight = getCurrentBlockHeight(currency);
            NetworkFees fees = getNetworkFees(currency);
            
            return NetworkStatus.builder()
                .currency(currency)
                .isHealthy(isHealthy)
                .currentBlockHeight(blockHeight)
                .networkFees(fees)
                .lastChecked(LocalDateTime.now())
                .build();
                
        } catch (Exception e) {
            log.error("Failed to get network status for currency: {}", currency, e);
            return NetworkStatus.builder()
                .currency(currency)
                .isHealthy(false)
                .lastChecked(LocalDateTime.now())
                .build();
        }
    }

    /**
     * Batch process multiple transactions
     */
    @Async("cryptoTaskExecutor")
    public CompletableFuture<BatchTransactionResult> processBatchTransactions(
            BatchTransactionRequest batchRequest) {
        
        log.info("Processing batch of {} transactions", batchRequest.getTransactions().size());
        
        return CompletableFuture.supplyAsync(() -> {
            BatchTransactionResult.BatchTransactionResultBuilder resultBuilder = 
                BatchTransactionResult.builder();
            
            int successCount = 0;
            int failureCount = 0;
            
            for (SignedCryptoTransaction tx : batchRequest.getTransactions()) {
                try {
                    String txHash = broadcastTransaction(tx);
                    resultBuilder.successfulTransaction(
                        BatchTransactionItem.builder()
                            .transactionId(tx.getTransactionId())
                            .txHash(txHash)
                            .status("SUCCESS")
                            .build()
                    );
                    successCount++;
                    
                } catch (Exception e) {
                    log.error("Failed to process batch transaction: {}", tx.getTransactionId(), e);
                    resultBuilder.failedTransaction(
                        BatchTransactionItem.builder()
                            .transactionId(tx.getTransactionId())
                            .status("FAILED")
                            .errorMessage(e.getMessage())
                            .build()
                    );
                    failureCount++;
                }
            }
            
            return resultBuilder
                .totalTransactions(batchRequest.getTransactions().size())
                .successCount(successCount)
                .failureCount(failureCount)
                .processedAt(LocalDateTime.now())
                .build();
        });
    }
    
    // Exception classes for BlockchainService
    public static class UTXORetrievalException extends RuntimeException {
        public UTXORetrievalException(String message, Throwable cause) {
            super(message, cause);
        }
    }
    
    public static class UnsupportedCurrencyException extends RuntimeException {
        public UnsupportedCurrencyException(String message) {
            super(message);
        }
    }
    
    public static class GasPriceException extends RuntimeException {
        public GasPriceException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}