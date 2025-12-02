package com.waqiti.atm.service;

import com.waqiti.atm.domain.*;
import com.waqiti.atm.repository.*;
import com.waqiti.atm.client.AccountServiceClient;
import com.waqiti.atm.client.ATMNetworkClient;
import com.waqiti.atm.exception.ATMException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * ATM Deposit Service
 * Handles cash and check deposit operations via ATM
 * Implements regulatory-compliant deposit processing with holds and imaging
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ATMDepositService {

    private final ATMCardRepository atmCardRepository;
    private final ATMDepositRepository atmDepositRepository;
    private final DepositLimitRepository depositLimitRepository;
    private final CheckHoldRepository checkHoldRepository;
    private final ATMTransactionRepository atmTransactionRepository;
    private final AccountServiceClient accountServiceClient;
    private final ATMNetworkClient atmNetworkClient;
    private final KafkaTemplate<String, String> kafkaTemplate;

    // Deposit Limit Constants
    private static final BigDecimal DEFAULT_DAILY_CASH_LIMIT = new BigDecimal("10000.00");
    private static final BigDecimal DEFAULT_DAILY_CHECK_LIMIT = new BigDecimal("25000.00");
    private static final BigDecimal DEFAULT_PER_DEPOSIT_LIMIT = new BigDecimal("5000.00");
    private static final int DEFAULT_MAX_CHECKS_PER_DEPOSIT = 30;

    // Check Hold Policy Constants (per Regulation CC)
    private static final int HOLD_DAYS_LOCAL_CHECK = 2; // Next-day availability for local checks
    private static final int HOLD_DAYS_NONLOCAL_CHECK = 5; // 5 days for non-local checks
    private static final int HOLD_DAYS_NEW_ACCOUNT = 9; // 9 days for new accounts
    private static final BigDecimal HOLD_EXEMPT_AMOUNT = new BigDecimal("5000.00"); // First $5000

    /**
     * Validate card for deposit operation
     */
    @Transactional(readOnly = true)
    public boolean validateCardForDeposit(String cardNumber, String accountId, LocalDateTime timestamp) {
        log.debug("Validating card for deposit: {}", maskCardNumber(cardNumber));

        ATMCard card = atmCardRepository.findByCardNumber(cardNumber).orElse(null);

        if (card == null) {
            log.error("Card not found: {}", maskCardNumber(cardNumber));
            return false;
        }

        // Check card status
        if (card.getStatus() != ATMCard.CardStatus.ACTIVE) {
            log.error("Card not active: {}, status={}", maskCardNumber(cardNumber), card.getStatus());
            return false;
        }

        // Check card expiry
        if (card.getExpiryDate().isBefore(timestamp)) {
            log.error("Card expired: {}", maskCardNumber(cardNumber));
            return false;
        }

        // Verify card-account linkage
        if (!card.getAccountId().equals(UUID.fromString(accountId))) {
            log.error("Card-account mismatch: card={}, account={}",
                    maskCardNumber(cardNumber), accountId);
            return false;
        }

        return true;
    }

    /**
     * Validate deposit limits
     */
    @Transactional(readOnly = true)
    public boolean validateDepositLimits(String cardNumber, String accountId,
                                        BigDecimal totalAmount, String depositType,
                                        LocalDateTime timestamp) {
        log.debug("Validating deposit limits: card={}, amount={}, type={}",
                maskCardNumber(cardNumber), totalAmount, depositType);

        // Check per-deposit limit
        if (totalAmount.compareTo(DEFAULT_PER_DEPOSIT_LIMIT) > 0) {
            log.warn("Amount {} exceeds per-deposit limit {}", totalAmount, DEFAULT_PER_DEPOSIT_LIMIT);
            return false;
        }

        // Get today's deposits
        LocalDate today = timestamp.toLocalDate();

        BigDecimal todayCashTotal = atmDepositRepository
                .sumCashDepositsByAccountIdAndDate(UUID.fromString(accountId), today)
                .orElse(BigDecimal.ZERO);

        BigDecimal todayCheckTotal = atmDepositRepository
                .sumCheckDepositsByAccountIdAndDate(UUID.fromString(accountId), today)
                .orElse(BigDecimal.ZERO);

        // Validate based on deposit type
        if ("CASH".equals(depositType) || "MIXED".equals(depositType)) {
            if (todayCashTotal.add(totalAmount).compareTo(DEFAULT_DAILY_CASH_LIMIT) > 0) {
                log.warn("Cash deposit would exceed daily limit: current={}, new={}, limit={}",
                        todayCashTotal, totalAmount, DEFAULT_DAILY_CASH_LIMIT);
                return false;
            }
        }

        if ("CHECK".equals(depositType) || "MIXED".equals(depositType)) {
            if (todayCheckTotal.add(totalAmount).compareTo(DEFAULT_DAILY_CHECK_LIMIT) > 0) {
                log.warn("Check deposit would exceed daily limit: current={}, new={}, limit={}",
                        todayCheckTotal, totalAmount, DEFAULT_DAILY_CHECK_LIMIT);
                return false;
            }
        }

        return true;
    }

    /**
     * Create deposit record
     */
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public ATMDeposit createDeposit(String depositId, String atmId, String cardNumber,
                                   String accountId, String depositType,
                                   BigDecimal cashAmount, BigDecimal checkAmount,
                                   Integer numberOfChecks, LocalDateTime timestamp) {
        log.info("Creating deposit record: id={}, type={}, cash={}, checks={}",
                depositId, depositType, cashAmount, checkAmount);

        // Get card details
        ATMCard card = atmCardRepository.findByCardNumber(cardNumber)
                .orElseThrow(() -> new ATMException("Card not found: " + maskCardNumber(cardNumber)));

        // Create deposit
        ATMDeposit deposit = ATMDeposit.builder()
                .id(UUID.fromString(depositId))
                .atmId(UUID.fromString(atmId))
                .cardId(card.getId())
                .accountId(UUID.fromString(accountId))
                .depositType(ATMDeposit.DepositType.valueOf(depositType))
                .cashAmount(cashAmount)
                .checkAmount(checkAmount)
                .totalAmount(cashAmount.add(checkAmount))
                .numberOfChecks(numberOfChecks != null ? numberOfChecks : 0)
                .status(ATMDeposit.DepositStatus.PROCESSING)
                .depositDate(timestamp)
                .build();

        deposit = atmDepositRepository.save(deposit);

        // Create transaction record
        ATMTransaction transaction = ATMTransaction.builder()
                .accountId(UUID.fromString(accountId))
                .cardId(card.getId())
                .atmId(UUID.fromString(atmId))
                .transactionType(ATMTransaction.TransactionType.DEPOSIT)
                .amount(cashAmount.add(checkAmount))
                .currency("USD")
                .status(ATMTransaction.TransactionStatus.PENDING)
                .referenceNumber(depositId)
                .isCardless(false)
                .transactionDate(timestamp)
                .build();

        atmTransactionRepository.save(transaction);

        log.info("Created deposit record: id={}", depositId);
        return deposit;
    }

    /**
     * Reject deposit with reason
     */
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public void rejectDeposit(String depositId, String reason, LocalDateTime timestamp) {
        log.warn("Rejecting deposit: id={}, reason={}", depositId, reason);

        atmDepositRepository.findById(UUID.fromString(depositId)).ifPresent(deposit -> {
            deposit.setStatus(ATMDeposit.DepositStatus.REJECTED);
            deposit.setRejectionReason(reason);
            atmDepositRepository.save(deposit);
        });
    }

    /**
     * Hold deposit for review
     */
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public void holdDeposit(String depositId, String reason, LocalDateTime timestamp) {
        log.warn("Holding deposit for review: id={}, reason={}", depositId, reason);

        atmDepositRepository.findById(UUID.fromString(depositId)).ifPresent(deposit -> {
            deposit.setStatus(ATMDeposit.DepositStatus.ON_HOLD);
            deposit.setHoldReason(reason);
            atmDepositRepository.save(deposit);
        });
    }

    /**
     * Process cash portion of deposit
     */
    @Transactional(isolation = Isolation.SERIALIZABLE)
    @CircuitBreaker(name = "account-service")
    @Retry(name = "account-service")
    public void processCashDeposit(String depositId, BigDecimal cashAmount, LocalDateTime timestamp) {
        log.info("Processing cash deposit: id={}, amount={}", depositId, cashAmount);

        ATMDeposit deposit = atmDepositRepository.findById(UUID.fromString(depositId))
                .orElseThrow(() -> new ATMException("Deposit not found: " + depositId));

        // Update deposit status
        deposit.setCashProcessedAt(timestamp);
        atmDepositRepository.save(deposit);

        log.info("Cash deposit processed: id={}, amount={}", depositId, cashAmount);
    }

    /**
     * Update account balance for cash deposit (immediate availability)
     */
    @CircuitBreaker(name = "account-service")
    @Retry(name = "account-service")
    public void updateAccountBalance(String accountId, BigDecimal amount, LocalDateTime timestamp) {
        log.info("Updating account balance for cash deposit: accountId={}, amount={}", accountId, amount);

        try {
            accountServiceClient.creditAccount(UUID.fromString(accountId), amount, "ATM_CASH_DEPOSIT");
            log.info("Account balance updated successfully for cash deposit");
        } catch (Exception e) {
            log.error("Error updating account balance for cash deposit: {}", e.getMessage(), e);
            throw new ATMException("Failed to update account balance", e);
        }
    }

    /**
     * Apply check holds per Regulation CC
     */
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public void applyCheckHolds(String depositId, BigDecimal checkAmount, LocalDateTime timestamp) {
        log.info("Applying check holds: depositId={}, amount={}", depositId, checkAmount);

        ATMDeposit deposit = atmDepositRepository.findById(UUID.fromString(depositId))
                .orElseThrow(() -> new ATMException("Deposit not found: " + depositId));

        // Determine hold period based on account age and check type
        int holdDays = calculateHoldDays(deposit.getAccountId(), checkAmount);

        LocalDateTime releaseDate = timestamp.plusDays(holdDays);

        // Create check hold record
        CheckHold checkHold = CheckHold.builder()
                .depositId(deposit.getId())
                .accountId(deposit.getAccountId())
                .holdAmount(checkAmount)
                .holdType(CheckHold.HoldType.REGULATORY)
                .holdReason("Regulation CC - " + holdDays + " day hold")
                .holdPlacedAt(timestamp)
                .holdReleaseDate(releaseDate)
                .status(CheckHold.HoldStatus.ACTIVE)
                .build();

        checkHoldRepository.save(checkHold);

        log.info("Check hold applied: depositId={}, amount={}, releaseDays={}, releaseDate={}",
                depositId, checkAmount, holdDays, releaseDate);
    }

    /**
     * Calculate hold days based on account and check characteristics
     */
    private int calculateHoldDays(UUID accountId, BigDecimal checkAmount) {
        // Check if new account (< 30 days old)
        boolean isNewAccount = accountServiceClient.isNewAccount(accountId, 30);

        if (isNewAccount) {
            return HOLD_DAYS_NEW_ACCOUNT;
        }

        // For established accounts, use standard hold periods
        // In production, determine if check is local vs non-local
        // For now, use conservative non-local hold
        return HOLD_DAYS_NONLOCAL_CHECK;
    }

    /**
     * Schedule check processing (async clearing process)
     */
    @Async
    public void scheduleCheckProcessing(String depositId, LocalDateTime timestamp) {
        log.info("Scheduling check processing: depositId={}", depositId);

        // Publish to check clearing queue
        kafkaTemplate.send("check-clearing-queue", depositId);

        log.info("Check processing scheduled: depositId={}", depositId);
    }

    /**
     * Update ATM counters (cash cassette, check scanner)
     */
    @CircuitBreaker(name = "atm-network")
    @Retry(name = "atm-network")
    public void updateATMCounters(String atmId, BigDecimal cashAmount,
                                 Integer numberOfChecks, LocalDateTime timestamp) {
        log.info("Updating ATM counters: atmId={}, cash={}, checks={}",
                atmId, cashAmount, numberOfChecks);

        try {
            // Update cash cassette balance
            if (cashAmount.compareTo(BigDecimal.ZERO) > 0) {
                atmNetworkClient.updateCashBalance(UUID.fromString(atmId), cashAmount);
            }

            // Update check scanner counter
            if (numberOfChecks != null && numberOfChecks > 0) {
                atmNetworkClient.incrementCheckCount(UUID.fromString(atmId), numberOfChecks);
            }

            log.info("ATM counters updated successfully");
        } catch (Exception e) {
            log.error("Error updating ATM counters: {}", e.getMessage(), e);
        }
    }

    /**
     * Generate deposit receipt
     */
    @Transactional(readOnly = true)
    public String generateDepositReceipt(String depositId, LocalDateTime timestamp) {
        log.info("Generating deposit receipt: depositId={}", depositId);

        ATMDeposit deposit = atmDepositRepository.findById(UUID.fromString(depositId))
                .orElseThrow(() -> new ATMException("Deposit not found: " + depositId));

        // Generate receipt number
        String receiptNumber = "DEP-" + deposit.getId().toString().substring(0, 8).toUpperCase();

        // Update deposit with receipt number
        deposit.setReceiptNumber(receiptNumber);
        atmDepositRepository.save(deposit);

        log.info("Deposit receipt generated: {}", receiptNumber);
        return receiptNumber;
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
