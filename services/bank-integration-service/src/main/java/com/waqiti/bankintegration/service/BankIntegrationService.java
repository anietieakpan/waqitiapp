package com.waqiti.bankintegration.service;

import com.waqiti.bankintegration.domain.*;
import com.waqiti.bankintegration.dto.*;
import com.waqiti.bankintegration.repository.*;
import com.waqiti.bankintegration.provider.PaymentProviderFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class BankIntegrationService {
    
    private final BankAccountRepository bankAccountRepository;
    private final PaymentTransactionRepository paymentTransactionRepository;
    private final PaymentProviderFactory paymentProviderFactory;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    
    /**
     * Link bank account for user
     */
    @Retryable(value = {Exception.class}, maxAttempts = 3, backoff = @Backoff(delay = 1000))
    public BankAccountDto linkBankAccount(LinkBankAccountRequest request) {
        log.info("Linking bank account for user: {}", request.getUserId());
        
        try {
            // Get payment provider
            var provider = paymentProviderFactory.getProvider(request.getProvider());
            
            // Validate account with provider
            AccountValidationResult validation = provider.validateAccount(
                request.getAccountNumber(), 
                request.getRoutingNumber(),
                request.getAccountType()
            );
            
            if (!validation.isValid()) {
                throw new RuntimeException("Invalid bank account: " + validation.getErrorMessage());
            }
            
            // Create bank account entity
            BankAccount bankAccount = BankAccount.builder()
                .userId(UUID.fromString(request.getUserId()))
                .provider(request.getProvider())
                .accountNumber(maskAccountNumber(request.getAccountNumber()))
                .routingNumber(request.getRoutingNumber())
                .accountType(BankAccountType.valueOf(request.getAccountType().toUpperCase()))
                .accountName(request.getAccountName())
                .isActive(true)
                .isVerified(validation.isInstantVerification())
                .lastVerifiedAt(validation.isInstantVerification() ? LocalDateTime.now() : null)
                .metadata(validation.getMetadata())
                .createdAt(LocalDateTime.now())
                .build();
            
            bankAccount = bankAccountRepository.save(bankAccount);
            
            // Send verification if required
            if (!validation.isInstantVerification()) {
                initiateAccountVerification(bankAccount);
            }
            
            // Publish event
            publishBankAccountLinkedEvent(bankAccount);
            
            log.info("Bank account linked successfully: {}", bankAccount.getId());
            
            return mapToBankAccountDto(bankAccount);
            
        } catch (Exception e) {
            log.error("Failed to link bank account for user: {}", request.getUserId(), e);
            throw new RuntimeException("Failed to link bank account: " + e.getMessage());
        }
    }
    
    /**
     * Process payment via bank integration
     */
    @Retryable(value = {Exception.class}, maxAttempts = 3, backoff = @Backoff(delay = 2000))
    public PaymentTransactionDto processPayment(ProcessPaymentRequest request) {
        log.info("Processing bank payment: {} for amount: {}", 
                request.getTransactionId(), request.getAmount());
        
        try {
            // Get bank account
            BankAccount bankAccount = bankAccountRepository.findById(request.getBankAccountId())
                .orElseThrow(() -> new RuntimeException("Bank account not found"));
            
            if (!bankAccount.isActive() || !bankAccount.isVerified()) {
                throw new RuntimeException("Bank account is not active or verified");
            }
            
            // Get payment provider
            var provider = paymentProviderFactory.getProvider(bankAccount.getProvider());
            
            // Create payment transaction record
            PaymentTransaction transaction = PaymentTransaction.builder()
                .transactionId(request.getTransactionId())
                .bankAccountId(bankAccount.getId())
                .userId(bankAccount.getUserId())
                .amount(request.getAmount())
                .currency(request.getCurrency())
                .description(request.getDescription())
                .paymentType(PaymentType.valueOf(request.getPaymentType().toUpperCase()))
                .status(PaymentStatus.PROCESSING)
                .createdAt(LocalDateTime.now())
                .metadata(request.getMetadata())
                .build();
            
            transaction = paymentTransactionRepository.save(transaction);
            
            // Process with provider
            PaymentResult result = provider.processPayment(PaymentProviderRequest.builder()
                .transactionId(transaction.getTransactionId())
                .bankAccount(bankAccount)
                .amount(request.getAmount())
                .currency(request.getCurrency())
                .description(request.getDescription())
                .paymentType(request.getPaymentType())
                .metadata(request.getMetadata())
                .build());
            
            // Update transaction with result
            transaction.setProviderTransactionId(result.getProviderTransactionId());
            transaction.setStatus(PaymentStatus.valueOf(result.getStatus().toUpperCase()));
            transaction.setProviderResponse(result.getProviderResponse());
            transaction.setProcessedAt(LocalDateTime.now());
            
            if (result.getEstimatedCompletionTime() != null) {
                transaction.setExpectedCompletionTime(result.getEstimatedCompletionTime());
            }
            
            transaction = paymentTransactionRepository.save(transaction);
            
            // Publish payment event
            publishPaymentProcessedEvent(transaction);
            
            log.info("Payment processed: {} with status: {}", 
                    transaction.getTransactionId(), transaction.getStatus());
            
            return mapToPaymentTransactionDto(transaction);
            
        } catch (Exception e) {
            log.error("Failed to process payment: {}", request.getTransactionId(), e);
            
            // Update transaction status to failed
            paymentTransactionRepository.findByTransactionId(request.getTransactionId())
                .ifPresent(tx -> {
                    tx.setStatus(PaymentStatus.FAILED);
                    tx.setErrorMessage(e.getMessage());
                    tx.setProcessedAt(LocalDateTime.now());
                    paymentTransactionRepository.save(tx);
                });
            
            throw new RuntimeException("Payment processing failed: " + e.getMessage());
        }
    }
    
    /**
     * Check payment status
     */
    public PaymentTransactionDto getPaymentStatus(String transactionId) {
        log.debug("Checking payment status: {}", transactionId);
        
        PaymentTransaction transaction = paymentTransactionRepository
            .findByTransactionId(transactionId)
            .orElseThrow(() -> new RuntimeException("Transaction not found: " + transactionId));
        
        // If still processing, check with provider
        if (transaction.getStatus() == PaymentStatus.PROCESSING) {
            updateTransactionStatus(transaction);
        }
        
        return mapToPaymentTransactionDto(transaction);
    }
    
    /**
     * Verify bank account with micro deposits
     */
    @Async
    public CompletableFuture<Void> verifyBankAccount(UUID bankAccountId, 
                                                    List<BigDecimal> microDepositAmounts) {
        log.info("Verifying bank account with micro deposits: {}", bankAccountId);
        
        try {
            BankAccount bankAccount = bankAccountRepository.findById(bankAccountId)
                .orElseThrow(() -> new RuntimeException("Bank account not found"));
            
            var provider = paymentProviderFactory.getProvider(bankAccount.getProvider());
            
            // Verify micro deposits
            boolean verified = provider.verifyMicroDeposits(bankAccount, microDepositAmounts);
            
            if (verified) {
                bankAccount.setIsVerified(true);
                bankAccount.setLastVerifiedAt(LocalDateTime.now());
                bankAccountRepository.save(bankAccount);
                
                // Publish verification event
                publishBankAccountVerifiedEvent(bankAccount);
                
                log.info("Bank account verified successfully: {}", bankAccountId);
            } else {
                log.warn("Bank account verification failed: {}", bankAccountId);
                // Could implement retry logic or notification here
            }
            
        } catch (Exception e) {
            log.error("Failed to verify bank account: {}", bankAccountId, e);
        }
        
        return CompletableFuture.completedFuture(null);
    }
    
    /**
     * Get user's linked bank accounts
     */
    public List<BankAccountDto> getUserBankAccounts(String userId) {
        log.debug("Getting bank accounts for user: {}", userId);
        
        List<BankAccount> accounts = bankAccountRepository
            .findByUserIdAndIsActiveTrue(UUID.fromString(userId));
        
        return accounts.stream()
            .map(this::mapToBankAccountDto)
            .toList();
    }
    
    /**
     * Remove bank account
     */
    public void removeBankAccount(UUID bankAccountId, String userId) {
        log.info("Removing bank account: {} for user: {}", bankAccountId, userId);
        
        BankAccount bankAccount = bankAccountRepository.findById(bankAccountId)
            .orElseThrow(() -> new RuntimeException("Bank account not found"));
        
        if (!bankAccount.getUserId().toString().equals(userId)) {
            throw new RuntimeException("Unauthorized access to bank account");
        }
        
        // Soft delete
        bankAccount.setIsActive(false);
        bankAccount.setUpdatedAt(LocalDateTime.now());
        bankAccountRepository.save(bankAccount);
        
        // Publish event
        publishBankAccountRemovedEvent(bankAccount);
        
        log.info("Bank account removed: {}", bankAccountId);
    }
    
    /**
     * Process refund
     */
    @Retryable(value = {Exception.class}, maxAttempts = 3, backoff = @Backoff(delay = 2000))
    public RefundTransactionDto processRefund(ProcessRefundRequest request) {
        log.info("Processing refund for transaction: {}", request.getOriginalTransactionId());
        
        try {
            // Get original transaction
            PaymentTransaction originalTransaction = paymentTransactionRepository
                .findByTransactionId(request.getOriginalTransactionId())
                .orElseThrow(() -> new RuntimeException("Original transaction not found"));
            
            if (originalTransaction.getStatus() != PaymentStatus.COMPLETED) {
                throw new RuntimeException("Cannot refund incomplete transaction");
            }
            
            // Get bank account and provider
            BankAccount bankAccount = bankAccountRepository.findById(originalTransaction.getBankAccountId())
                .orElseThrow(() -> new RuntimeException("Bank account not found"));
            
            var provider = paymentProviderFactory.getProvider(bankAccount.getProvider());
            
            // Create refund transaction
            PaymentTransaction refundTransaction = PaymentTransaction.builder()
                .transactionId(UUID.randomUUID().toString())
                .originalTransactionId(request.getOriginalTransactionId())
                .bankAccountId(bankAccount.getId())
                .userId(bankAccount.getUserId())
                .amount(request.getRefundAmount())
                .currency(originalTransaction.getCurrency())
                .description("Refund: " + request.getReason())
                .paymentType(PaymentType.REFUND)
                .status(PaymentStatus.PROCESSING)
                .createdAt(LocalDateTime.now())
                .build();
            
            refundTransaction = paymentTransactionRepository.save(refundTransaction);
            
            // Process refund with provider
            RefundResult result = provider.processRefund(RefundProviderRequest.builder()
                .originalProviderTransactionId(originalTransaction.getProviderTransactionId())
                .refundAmount(request.getRefundAmount())
                .reason(request.getReason())
                .build());
            
            // Update refund transaction
            refundTransaction.setProviderTransactionId(result.getProviderTransactionId());
            refundTransaction.setStatus(PaymentStatus.valueOf(result.getStatus().toUpperCase()));
            refundTransaction.setProviderResponse(result.getProviderResponse());
            refundTransaction.setProcessedAt(LocalDateTime.now());
            
            refundTransaction = paymentTransactionRepository.save(refundTransaction);
            
            // Publish refund event
            publishRefundProcessedEvent(refundTransaction, originalTransaction);
            
            log.info("Refund processed: {} with status: {}", 
                    refundTransaction.getTransactionId(), refundTransaction.getStatus());
            
            return mapToRefundTransactionDto(refundTransaction);
            
        } catch (Exception e) {
            log.error("Failed to process refund: {}", request.getOriginalTransactionId(), e);
            throw new RuntimeException("Refund processing failed: " + e.getMessage());
        }
    }
    
    /**
     * Get account balance from bank
     */
    public AccountBalanceDto getAccountBalance(UUID bankAccountId) {
        log.debug("Getting account balance for: {}", bankAccountId);
        
        try {
            BankAccount bankAccount = bankAccountRepository.findById(bankAccountId)
                .orElseThrow(() -> new RuntimeException("Bank account not found"));
            
            if (!bankAccount.isVerified()) {
                throw new RuntimeException("Account not verified");
            }
            
            var provider = paymentProviderFactory.getProvider(bankAccount.getProvider());
            
            BalanceInfo balance = provider.getAccountBalance(bankAccount);
            
            return AccountBalanceDto.builder()
                .bankAccountId(bankAccountId)
                .availableBalance(balance.getAvailableBalance())
                .currentBalance(balance.getCurrentBalance())
                .currency(balance.getCurrency())
                .lastUpdated(LocalDateTime.now())
                .build();
            
        } catch (Exception e) {
            log.error("Failed to get account balance: {}", bankAccountId, e);
            throw new RuntimeException("Failed to retrieve account balance: " + e.getMessage());
        }
    }
    
    // Private helper methods
    
    private void updateTransactionStatus(PaymentTransaction transaction) {
        try {
            BankAccount bankAccount = bankAccountRepository.findById(transaction.getBankAccountId())
                .orElse(null);
            
            if (bankAccount != null) {
                var provider = paymentProviderFactory.getProvider(bankAccount.getProvider());
                
                PaymentStatusInfo status = provider.getPaymentStatus(transaction.getProviderTransactionId());
                
                transaction.setStatus(PaymentStatus.valueOf(status.getStatus().toUpperCase()));
                transaction.setProviderResponse(status.getStatusDetails());
                
                if (status.getCompletedAt() != null) {
                    transaction.setCompletedAt(status.getCompletedAt());
                }
                
                paymentTransactionRepository.save(transaction);
                
                // Publish status update event
                publishPaymentStatusUpdatedEvent(transaction);
            }
            
        } catch (Exception e) {
            log.warn("Failed to update transaction status: {}", transaction.getTransactionId(), e);
        }
    }
    
    private void initiateAccountVerification(BankAccount bankAccount) {
        try {
            var provider = paymentProviderFactory.getProvider(bankAccount.getProvider());
            provider.initiateMicroDeposits(bankAccount);
            
            log.info("Micro deposit verification initiated for account: {}", bankAccount.getId());
            
        } catch (Exception e) {
            log.error("Failed to initiate account verification: {}", bankAccount.getId(), e);
        }
    }
    
    private String maskAccountNumber(String accountNumber) {
        if (accountNumber.length() <= 4) {
            return accountNumber;
        }
        return "*".repeat(accountNumber.length() - 4) + accountNumber.substring(accountNumber.length() - 4);
    }
    
    // Event publishing methods
    
    private void publishBankAccountLinkedEvent(BankAccount bankAccount) {
        try {
            Map<String, Object> event = Map.of(
                "eventType", "BANK_ACCOUNT_LINKED",
                "userId", bankAccount.getUserId().toString(),
                "bankAccountId", bankAccount.getId().toString(),
                "provider", bankAccount.getProvider(),
                "accountType", bankAccount.getAccountType().toString(),
                "timestamp", System.currentTimeMillis()
            );
            
            kafkaTemplate.send("bank-integration-events", event);
        } catch (Exception e) {
            log.warn("Failed to publish bank account linked event", e);
        }
    }
    
    private void publishPaymentProcessedEvent(PaymentTransaction transaction) {
        try {
            Map<String, Object> event = Map.of(
                "eventType", "PAYMENT_PROCESSED",
                "transactionId", transaction.getTransactionId(),
                "userId", transaction.getUserId().toString(),
                "amount", transaction.getAmount(),
                "currency", transaction.getCurrency(),
                "status", transaction.getStatus().toString(),
                "timestamp", System.currentTimeMillis()
            );
            
            kafkaTemplate.send("payment-events", event);
        } catch (Exception e) {
            log.warn("Failed to publish payment processed event", e);
        }
    }
    
    private void publishBankAccountVerifiedEvent(BankAccount bankAccount) {
        try {
            Map<String, Object> event = Map.of(
                "eventType", "BANK_ACCOUNT_VERIFIED",
                "userId", bankAccount.getUserId().toString(),
                "bankAccountId", bankAccount.getId().toString(),
                "timestamp", System.currentTimeMillis()
            );
            
            kafkaTemplate.send("bank-integration-events", event);
        } catch (Exception e) {
            log.warn("Failed to publish bank account verified event", e);
        }
    }
    
    private void publishBankAccountRemovedEvent(BankAccount bankAccount) {
        try {
            Map<String, Object> event = Map.of(
                "eventType", "BANK_ACCOUNT_REMOVED",
                "userId", bankAccount.getUserId().toString(),
                "bankAccountId", bankAccount.getId().toString(),
                "timestamp", System.currentTimeMillis()
            );
            
            kafkaTemplate.send("bank-integration-events", event);
        } catch (Exception e) {
            log.warn("Failed to publish bank account removed event", e);
        }
    }
    
    private void publishRefundProcessedEvent(PaymentTransaction refund, PaymentTransaction original) {
        try {
            Map<String, Object> event = Map.of(
                "eventType", "REFUND_PROCESSED",
                "refundTransactionId", refund.getTransactionId(),
                "originalTransactionId", original.getTransactionId(),
                "userId", refund.getUserId().toString(),
                "refundAmount", refund.getAmount(),
                "status", refund.getStatus().toString(),
                "timestamp", System.currentTimeMillis()
            );
            
            kafkaTemplate.send("payment-events", event);
        } catch (Exception e) {
            log.warn("Failed to publish refund processed event", e);
        }
    }
    
    private void publishPaymentStatusUpdatedEvent(PaymentTransaction transaction) {
        try {
            Map<String, Object> event = Map.of(
                "eventType", "PAYMENT_STATUS_UPDATED",
                "transactionId", transaction.getTransactionId(),
                "status", transaction.getStatus().toString(),
                "timestamp", System.currentTimeMillis()
            );
            
            kafkaTemplate.send("payment-events", event);
        } catch (Exception e) {
            log.warn("Failed to publish payment status updated event", e);
        }
    }
    
    // Mapping methods
    
    private BankAccountDto mapToBankAccountDto(BankAccount bankAccount) {
        return BankAccountDto.builder()
            .id(bankAccount.getId())
            .userId(bankAccount.getUserId().toString())
            .provider(bankAccount.getProvider())
            .accountName(bankAccount.getAccountName())
            .accountNumber(bankAccount.getAccountNumber()) // Already masked
            .routingNumber(bankAccount.getRoutingNumber())
            .accountType(bankAccount.getAccountType().toString())
            .isActive(bankAccount.isActive())
            .isVerified(bankAccount.isVerified())
            .lastVerifiedAt(bankAccount.getLastVerifiedAt())
            .createdAt(bankAccount.getCreatedAt())
            .build();
    }
    
    private PaymentTransactionDto mapToPaymentTransactionDto(PaymentTransaction transaction) {
        return PaymentTransactionDto.builder()
            .transactionId(transaction.getTransactionId())
            .bankAccountId(transaction.getBankAccountId())
            .amount(transaction.getAmount())
            .currency(transaction.getCurrency())
            .description(transaction.getDescription())
            .paymentType(transaction.getPaymentType().toString())
            .status(transaction.getStatus().toString())
            .providerTransactionId(transaction.getProviderTransactionId())
            .createdAt(transaction.getCreatedAt())
            .processedAt(transaction.getProcessedAt())
            .completedAt(transaction.getCompletedAt())
            .expectedCompletionTime(transaction.getExpectedCompletionTime())
            .errorMessage(transaction.getErrorMessage())
            .build();
    }
    
    private RefundTransactionDto mapToRefundTransactionDto(PaymentTransaction refund) {
        return RefundTransactionDto.builder()
            .refundTransactionId(refund.getTransactionId())
            .originalTransactionId(refund.getOriginalTransactionId())
            .refundAmount(refund.getAmount())
            .currency(refund.getCurrency())
            .status(refund.getStatus().toString())
            .processedAt(refund.getProcessedAt())
            .build();
    }
}