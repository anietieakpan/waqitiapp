package com.waqiti.atm.service;

import com.waqiti.atm.domain.ATMCard;
import com.waqiti.atm.domain.ATMInquiry;
import com.waqiti.atm.repository.ATMCardRepository;
import com.waqiti.atm.repository.ATMInquiryRepository;
import com.waqiti.atm.client.AccountServiceClient;
import com.waqiti.atm.exception.ATMException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
 * ATM Inquiry Service
 * Handles balance inquiries, mini-statements, and receipt generation
 * Implements secure inquiry processing with rate limiting
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ATMInquiryService {

    private final ATMCardRepository atmCardRepository;
    private final ATMInquiryRepository atmInquiryRepository;
    private final AccountServiceClient accountServiceClient;
    private final PasswordEncoder passwordEncoder;

    // Inquiry rate limiting
    private static final int MAX_INQUIRIES_PER_DAY = 50;
    private static final int MAX_FAILED_PIN_ATTEMPTS = 3;

    /**
     * Validate PIN for inquiry
     */
    @Transactional(readOnly = true)
    public boolean validatePIN(String cardNumber, String pinHash, LocalDateTime timestamp) {
        log.debug("Validating PIN for inquiry: {}", maskCardNumber(cardNumber));

        Optional<ATMCard> cardOpt = atmCardRepository.findByCardNumber(cardNumber);
        if (cardOpt.isEmpty()) {
            return false;
        }

        ATMCard card = cardOpt.get();

        if (card.getFailedPinAttempts() >= MAX_FAILED_PIN_ATTEMPTS) {
            log.error("Card locked due to failed PIN attempts: {}", maskCardNumber(cardNumber));
            return false;
        }

        if (card.getStatus() != ATMCard.CardStatus.ACTIVE) {
            return false;
        }

        if (card.getExpiryDate().isBefore(timestamp)) {
            return false;
        }

        return passwordEncoder.matches(pinHash, card.getPinHash());
    }

    /**
     * Increment failed PIN attempts
     */
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public void incrementFailedAttempts(String cardNumber, LocalDateTime timestamp) {
        atmCardRepository.findByCardNumber(cardNumber).ifPresent(card -> {
            int attempts = card.getFailedPinAttempts() + 1;
            card.setFailedPinAttempts(attempts);

            if (attempts >= MAX_FAILED_PIN_ATTEMPTS) {
                card.setStatus(ATMCard.CardStatus.BLOCKED);
                card.setBlockedAt(timestamp);
                card.setBlockReason("Exceeded maximum failed PIN attempts");
            }

            atmCardRepository.save(card);
        });
    }

    /**
     * Validate card status
     */
    @Transactional(readOnly = true)
    public boolean validateCardStatus(String cardNumber, LocalDateTime timestamp) {
        Optional<ATMCard> cardOpt = atmCardRepository.findByCardNumber(cardNumber);

        return cardOpt.isPresent() &&
               cardOpt.get().getStatus() == ATMCard.CardStatus.ACTIVE &&
               cardOpt.get().getExpiryDate().isAfter(timestamp);
    }

    /**
     * Validate account access
     */
    @CircuitBreaker(name = "account-service")
    @Retry(name = "account-service")
    public boolean validateAccountAccess(String cardNumber, String accountId, LocalDateTime timestamp) {
        ATMCard card = atmCardRepository.findByCardNumber(cardNumber).orElse(null);

        if (card == null) {
            return false;
        }

        // Verify card-account linkage
        if (!card.getAccountId().equals(UUID.fromString(accountId))) {
            log.error("Card-account mismatch");
            return false;
        }

        // Check account is active
        return accountServiceClient.isAccountActive(UUID.fromString(accountId));
    }

    /**
     * Validate inquiry rate limits
     */
    @Transactional(readOnly = true)
    public boolean validateInquiryLimits(String cardNumber, String inquiryType, LocalDateTime timestamp) {
        LocalDate today = timestamp.toLocalDate();

        long todayCount = atmInquiryRepository
                .countInquiriesByCardNumberAndDate(cardNumber, today);

        if (todayCount >= MAX_INQUIRIES_PER_DAY) {
            log.warn("Daily inquiry limit exceeded: card={}, count={}",
                    maskCardNumber(cardNumber), todayCount);
            return false;
        }

        return true;
    }

    /**
     * Create inquiry record
     */
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public ATMInquiry createInquiry(String inquiryId, String atmId, String cardNumber,
                                   String accountId, String inquiryType,
                                   boolean receiptRequested, LocalDateTime timestamp) {
        log.info("Creating inquiry record: id={}, type={}", inquiryId, inquiryType);

        ATMCard card = atmCardRepository.findByCardNumber(cardNumber)
                .orElseThrow(() -> new ATMException("Card not found"));

        ATMInquiry inquiry = ATMInquiry.builder()
                .id(UUID.fromString(inquiryId))
                .atmId(UUID.fromString(atmId))
                .cardId(card.getId())
                .accountId(UUID.fromString(accountId))
                .inquiryType(ATMInquiry.InquiryType.valueOf(inquiryType))
                .receiptRequested(receiptRequested)
                .status(ATMInquiry.InquiryStatus.PROCESSING)
                .inquiryDate(timestamp)
                .build();

        return atmInquiryRepository.save(inquiry);
    }

    /**
     * Reject inquiry
     */
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public void rejectInquiry(String inquiryId, String reason, LocalDateTime timestamp) {
        atmInquiryRepository.findById(UUID.fromString(inquiryId)).ifPresent(inquiry -> {
            inquiry.setStatus(ATMInquiry.InquiryStatus.FAILED);
            inquiry.setFailureReason(reason);
            atmInquiryRepository.save(inquiry);
        });
    }

    /**
     * Get available balance
     */
    @CircuitBreaker(name = "account-service")
    @Retry(name = "account-service")
    public BigDecimal getAvailableBalance(String accountId, LocalDateTime timestamp) {
        return accountServiceClient.getAvailableBalance(UUID.fromString(accountId));
    }

    /**
     * Get current balance
     */
    @CircuitBreaker(name = "account-service")
    @Retry(name = "account-service")
    public BigDecimal getCurrentBalance(String accountId, LocalDateTime timestamp) {
        return accountServiceClient.getCurrentBalance(UUID.fromString(accountId));
    }

    /**
     * Set balance information in inquiry
     */
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public void setBalanceInformation(String inquiryId, BigDecimal availableBalance,
                                     BigDecimal currentBalance, LocalDateTime timestamp) {
        atmInquiryRepository.findById(UUID.fromString(inquiryId)).ifPresent(inquiry -> {
            inquiry.setAvailableBalance(availableBalance);
            inquiry.setCurrentBalance(currentBalance);
            inquiry.setStatus(ATMInquiry.InquiryStatus.COMPLETED);
            atmInquiryRepository.save(inquiry);
        });
    }

    /**
     * Set mini-statement data
     */
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public void setMiniStatementData(String inquiryId, java.util.List<String> transactions,
                                    LocalDateTime timestamp) {
        atmInquiryRepository.findById(UUID.fromString(inquiryId)).ifPresent(inquiry -> {
            inquiry.setMiniStatementData(String.join("\n", transactions));
            inquiry.setStatus(ATMInquiry.InquiryStatus.COMPLETED);
            atmInquiryRepository.save(inquiry);
        });
    }

    /**
     * Generate response data for ATM display
     */
    @Transactional(readOnly = true)
    public String generateResponseData(String inquiryId, LocalDateTime timestamp) {
        ATMInquiry inquiry = atmInquiryRepository.findById(UUID.fromString(inquiryId))
                .orElseThrow(() -> new ATMException("Inquiry not found"));

        if (inquiry.getInquiryType() == ATMInquiry.InquiryType.BALANCE) {
            return String.format("Available: $%s\nCurrent: $%s",
                    inquiry.getAvailableBalance(), inquiry.getCurrentBalance());
        } else {
            return inquiry.getMiniStatementData();
        }
    }

    /**
     * Print receipt
     */
    public String printReceipt(String inquiryId, String atmId, LocalDateTime timestamp) {
        String receiptNumber = "INQ-" + inquiryId.substring(0, 8).toUpperCase();
        log.info("Printing receipt: {}", receiptNumber);
        return receiptNumber;
    }

    /**
     * Update inquiry counters
     */
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public void updateInquiryCounters(String cardNumber, String inquiryType, LocalDateTime timestamp) {
        log.debug("Updating inquiry counters: card={}, type={}",
                maskCardNumber(cardNumber), inquiryType);
        // Counters tracked via inquiry records
    }

    /**
     * Update ATM usage stats
     */
    public void updateATMUsageStats(String atmId, String inquiryType, LocalDateTime timestamp) {
        log.debug("Updating ATM usage stats: atmId={}, type={}", atmId, inquiryType);
        // Stats tracked via inquiry records
    }

    private String maskCardNumber(String cardNumber) {
        if (cardNumber == null || cardNumber.length() < 4) {
            return "****";
        }
        return "************" + cardNumber.substring(cardNumber.length() - 4);
    }
}
