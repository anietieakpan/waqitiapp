package com.waqiti.atm.service;

import com.waqiti.atm.domain.ATMCard;
import com.waqiti.atm.domain.ATMTransaction;
import com.waqiti.atm.domain.ATMWithdrawal;
import com.waqiti.atm.repository.ATMCardRepository;
import com.waqiti.atm.repository.ATMTransactionRepository;
import com.waqiti.atm.repository.ATMWithdrawalRepository;
import com.waqiti.atm.repository.WithdrawalLimitRepository;
import com.waqiti.atm.client.AccountServiceClient;
import com.waqiti.atm.client.ATMNetworkClient;
import com.waqiti.atm.exception.ATMException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

/**
 * ATM Transaction Service
 * Handles withdrawal, balance inquiry, and PIN validation operations
 * Implements production-grade transaction processing with regulatory compliance
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ATMTransactionService {

    private final ATMCardRepository atmCardRepository;
    private final ATMTransactionRepository atmTransactionRepository;
    private final ATMWithdrawalRepository atmWithdrawalRepository;
    private final WithdrawalLimitRepository withdrawalLimitRepository;
    private final AccountServiceClient accountServiceClient;
    private final ATMNetworkClient atmNetworkClient;
    private final PasswordEncoder passwordEncoder;

    // PIN Validation Constants
    private static final int MAX_FAILED_PIN_ATTEMPTS = 3;
    private static final int PIN_LOCKOUT_HOURS = 24;

    // Withdrawal Limit Constants
    private static final BigDecimal DEFAULT_DAILY_LIMIT = new BigDecimal("5000.00");
    private static final BigDecimal DEFAULT_PER_TRANSACTION_LIMIT = new BigDecimal("1000.00");
    private static final int DEFAULT_DAILY_TRANSACTION_COUNT_LIMIT = 10;

    /**
     * Validate PIN for ATM transaction
     * Implements PCI DSS compliant PIN validation with lockout mechanism
     */
    @Transactional(readOnly = true)
    public boolean validatePIN(String cardNumber, String pinHash, LocalDateTime timestamp) {
        log.debug("Validating PIN for card: {}", maskCardNumber(cardNumber));

        Optional<ATMCard> cardOpt = atmCardRepository.findByCardNumber(cardNumber);
        if (cardOpt.isEmpty()) {
            log.warn("Card not found: {}", maskCardNumber(cardNumber));
            return false;
        }

        ATMCard card = cardOpt.get();

        // Check if card is locked due to failed attempts
        if (card.getFailedPinAttempts() >= MAX_FAILED_PIN_ATTEMPTS) {
            log.error("Card locked due to failed PIN attempts: {}", maskCardNumber(cardNumber));
            return false;
        }

        // Check if card is blocked
        if (card.getStatus() == ATMCard.CardStatus.BLOCKED ||
            card.getStatus() == ATMCard.CardStatus.SUSPENDED) {
            log.error("Card is blocked/suspended: {}", maskCardNumber(cardNumber));
            return false;
        }

        // Check if card is expired
        if (card.getExpiryDate().isBefore(timestamp)) {
            log.error("Card expired: {}", maskCardNumber(cardNumber));
            return false;
        }

        // Validate PIN hash
        boolean pinValid = passwordEncoder.matches(pinHash, card.getPinHash());

        if (!pinValid) {
            log.warn("Invalid PIN for card: {}", maskCardNumber(cardNumber));
        }

        return pinValid;
    }

    /**
     * Increment failed PIN attempts and lock card if threshold exceeded
     */
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public void incrementFailedAttempts(String cardNumber, LocalDateTime timestamp) {
        log.info("Incrementing failed PIN attempts for card: {}", maskCardNumber(cardNumber));

        atmCardRepository.findByCardNumber(cardNumber).ifPresent(card -> {
            int attempts = card.getFailedPinAttempts() + 1;
            card.setFailedPinAttempts(attempts);

            if (attempts >= MAX_FAILED_PIN_ATTEMPTS) {
                card.setStatus(ATMCard.CardStatus.BLOCKED);
                card.setBlockedAt(timestamp);
                card.setBlockReason("Exceeded maximum failed PIN attempts");
                log.error("Card blocked due to {} failed PIN attempts: {}",
                        attempts, maskCardNumber(cardNumber));
            }

            atmCardRepository.save(card);
        });
    }

    /**
     * Validate account status for ATM transaction
     */
    @CircuitBreaker(name = "account-service")
    @Retry(name = "account-service")
    public boolean validateAccountStatus(String accountId, LocalDateTime timestamp) {
        log.debug("Validating account status: {}", accountId);

        try {
            return accountServiceClient.isAccountActive(UUID.fromString(accountId));
        } catch (Exception e) {
            log.error("Error validating account status: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * Validate withdrawal limits (daily limit, per-transaction limit, count limit)
     */
    @Transactional(readOnly = true)
    public boolean validateWithdrawalLimits(String cardNumber, String accountId,
                                           BigDecimal amount, LocalDateTime timestamp) {
        log.debug("Validating withdrawal limits for card: {}, amount: {}",
                maskCardNumber(cardNumber), amount);

        // Get card limits or use defaults
        BigDecimal dailyLimit = withdrawalLimitRepository
                .findDailyLimitByCardNumber(cardNumber)
                .orElse(DEFAULT_DAILY_LIMIT);

        BigDecimal perTransactionLimit = withdrawalLimitRepository
                .findPerTransactionLimitByCardNumber(cardNumber)
                .orElse(DEFAULT_PER_TRANSACTION_LIMIT);

        // Check per-transaction limit
        if (amount.compareTo(perTransactionLimit) > 0) {
            log.warn("Amount {} exceeds per-transaction limit {}", amount, perTransactionLimit);
            return false;
        }

        // Get today's withdrawals
        LocalDate today = timestamp.toLocalDate();
        BigDecimal todayTotal = atmWithdrawalRepository
                .sumWithdrawalAmountByCardNumberAndDate(cardNumber, today)
                .orElse(BigDecimal.ZERO);

        // Check daily limit
        BigDecimal newTotal = todayTotal.add(amount);
        if (newTotal.compareTo(dailyLimit) > 0) {
            log.warn("Total withdrawals {} would exceed daily limit {}", newTotal, dailyLimit);
            return false;
        }

        // Check daily transaction count
        long todayCount = atmWithdrawalRepository
                .countWithdrawalsByCardNumberAndDate(cardNumber, today);

        if (todayCount >= DEFAULT_DAILY_TRANSACTION_COUNT_LIMIT) {
            log.warn("Daily transaction count {} exceeds limit {}",
                    todayCount, DEFAULT_DAILY_TRANSACTION_COUNT_LIMIT);
            return false;
        }

        return true;
    }

    /**
     * Validate sufficient funds for withdrawal
     */
    @CircuitBreaker(name = "account-service")
    @Retry(name = "account-service")
    public boolean validateSufficientFunds(String accountId, BigDecimal amount, LocalDateTime timestamp) {
        log.debug("Validating sufficient funds for account: {}, amount: {}", accountId, amount);

        try {
            BigDecimal availableBalance = accountServiceClient
                    .getAvailableBalance(UUID.fromString(accountId));

            boolean sufficient = availableBalance.compareTo(amount) >= 0;

            if (!sufficient) {
                log.warn("Insufficient funds: available={}, requested={}", availableBalance, amount);
            }

            return sufficient;
        } catch (Exception e) {
            log.error("Error validating sufficient funds: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * Validate ATM cash availability
     */
    @CircuitBreaker(name = "atm-network")
    @Retry(name = "atm-network")
    public boolean validateATMCashAvailability(String atmId, BigDecimal amount, LocalDateTime timestamp) {
        log.debug("Validating ATM cash availability: atmId={}, amount={}", atmId, amount);

        try {
            BigDecimal atmCashBalance = atmNetworkClient.getATMCashBalance(UUID.fromString(atmId));

            boolean available = atmCashBalance.compareTo(amount) >= 0;

            if (!available) {
                log.warn("ATM cash unavailable: balance={}, requested={}", atmCashBalance, amount);
            }

            return available;
        } catch (Exception e) {
            log.error("Error validating ATM cash availability: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * Process ATM withdrawal
     */
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public ATMWithdrawal processWithdrawal(String withdrawalId, String atmId, String cardNumber,
                                          String accountId, BigDecimal amount,
                                          String authorizationCode, LocalDateTime timestamp) {
        log.info("Processing ATM withdrawal: id={}, amount={}", withdrawalId, amount);

        // Get card details
        ATMCard card = atmCardRepository.findByCardNumber(cardNumber)
                .orElseThrow(() -> new ATMException("Card not found: " + maskCardNumber(cardNumber)));

        // Create withdrawal record
        ATMWithdrawal withdrawal = ATMWithdrawal.builder()
                .id(UUID.fromString(withdrawalId))
                .atmId(UUID.fromString(atmId))
                .cardId(card.getId())
                .accountId(UUID.fromString(accountId))
                .amount(amount)
                .currency("USD")
                .authorizationCode(authorizationCode)
                .status(ATMWithdrawal.WithdrawalStatus.PROCESSING)
                .withdrawalDate(timestamp)
                .build();

        withdrawal = atmWithdrawalRepository.save(withdrawal);

        // Create transaction record
        ATMTransaction transaction = ATMTransaction.builder()
                .accountId(UUID.fromString(accountId))
                .cardId(card.getId())
                .atmId(UUID.fromString(atmId))
                .transactionType(ATMTransaction.TransactionType.WITHDRAWAL)
                .amount(amount)
                .currency("USD")
                .status(ATMTransaction.TransactionStatus.PENDING)
                .referenceNumber(authorizationCode)
                .authCode(authorizationCode)
                .isCardless(false)
                .transactionDate(timestamp)
                .build();

        atmTransactionRepository.save(transaction);

        log.info("Created withdrawal record: id={}", withdrawalId);
        return withdrawal;
    }

    /**
     * Decline withdrawal with reason
     */
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public void declineWithdrawal(String withdrawalId, String reason, LocalDateTime timestamp) {
        log.warn("Declining withdrawal: id={}, reason={}", withdrawalId, reason);

        atmWithdrawalRepository.findById(UUID.fromString(withdrawalId)).ifPresent(withdrawal -> {
            withdrawal.setStatus(ATMWithdrawal.WithdrawalStatus.FAILED);
            withdrawal.setFailureReason(reason);
            atmWithdrawalRepository.save(withdrawal);
        });
    }

    /**
     * Dispense cash via ATM network
     */
    @CircuitBreaker(name = "atm-network")
    @Retry(name = "atm-network")
    public boolean dispenseCash(String atmId, BigDecimal amount, LocalDateTime timestamp) {
        log.info("Dispensing cash: atmId={}, amount={}", atmId, amount);

        try {
            boolean dispensed = atmNetworkClient.dispenseCash(UUID.fromString(atmId), amount);

            if (dispensed) {
                log.info("Cash dispensed successfully: atmId={}, amount={}", atmId, amount);
            } else {
                log.error("Cash dispensing failed: atmId={}, amount={}", atmId, amount);
            }

            return dispensed;
        } catch (Exception e) {
            log.error("Error dispensing cash: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * Reverse withdrawal (in case of dispensing failure)
     */
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public void reverseWithdrawal(String withdrawalId, LocalDateTime timestamp) {
        log.warn("Reversing withdrawal: id={}", withdrawalId);

        atmWithdrawalRepository.findById(UUID.fromString(withdrawalId)).ifPresent(withdrawal -> {
            withdrawal.setStatus(ATMWithdrawal.WithdrawalStatus.REVERSED);
            atmWithdrawalRepository.save(withdrawal);

            // Reverse account debit via account service
            try {
                accountServiceClient.reverseDebit(withdrawal.getAccountId(), withdrawal.getAmount());
                log.info("Withdrawal reversed successfully: id={}", withdrawalId);
            } catch (Exception e) {
                log.error("Error reversing withdrawal in account service: {}", e.getMessage(), e);
            }
        });
    }

    /**
     * Update account balance after successful withdrawal
     */
    @CircuitBreaker(name = "account-service")
    @Retry(name = "account-service")
    public void updateAccountBalance(String accountId, BigDecimal amount, LocalDateTime timestamp) {
        log.info("Updating account balance: accountId={}, amount={}", accountId, amount);

        try {
            accountServiceClient.debitAccount(UUID.fromString(accountId), amount, "ATM_WITHDRAWAL");
            log.info("Account balance updated successfully");
        } catch (Exception e) {
            log.error("Error updating account balance: {}", e.getMessage(), e);
            throw new ATMException("Failed to update account balance", e);
        }
    }

    /**
     * Update daily withdrawal limits
     */
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public void updateDailyLimits(String cardNumber, BigDecimal amount, LocalDateTime timestamp) {
        log.debug("Updating daily limits for card: {}, amount={}", maskCardNumber(cardNumber), amount);
        // Limits are tracked via withdrawal records, no explicit update needed
    }

    /**
     * Update ATM cash balance after dispensing
     */
    @CircuitBreaker(name = "atm-network")
    @Retry(name = "atm-network")
    public void updateATMCashBalance(String atmId, BigDecimal amount, LocalDateTime timestamp) {
        log.info("Updating ATM cash balance: atmId={}, amount={}", atmId, amount);

        try {
            atmNetworkClient.updateCashBalance(UUID.fromString(atmId), amount.negate());
            log.info("ATM cash balance updated successfully");
        } catch (Exception e) {
            log.error("Error updating ATM cash balance: {}", e.getMessage(), e);
        }
    }

    /**
     * Mask card number for logging (PCI DSS compliance)
     */
    private String maskCardNumber(String cardNumber) {
        if (cardNumber == null || cardNumber.length() < 4) {
            return "****";
        }
        return "************" + cardNumber.substring(cardNumber.length() - 4);
    }
}
