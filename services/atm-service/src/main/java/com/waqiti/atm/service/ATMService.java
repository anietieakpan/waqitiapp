package com.waqiti.atm.service;

import com.waqiti.atm.domain.ATMCard;
import com.waqiti.atm.domain.ATMTransaction;
import com.waqiti.atm.domain.WithdrawalLimit;
import com.waqiti.atm.dto.*;
import com.waqiti.atm.exception.*;
import com.waqiti.atm.repository.ATMCardRepository;
import com.waqiti.atm.repository.ATMTransactionRepository;
import com.waqiti.atm.repository.WithdrawalLimitRepository;
import com.waqiti.common.security.EncryptionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;
import java.security.SecureRandom;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class ATMService {
    
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    @Lazy
    private final ATMService self;
    private final ATMCardRepository atmCardRepository;
    private final ATMTransactionRepository atmTransactionRepository;
    private final WithdrawalLimitRepository withdrawalLimitRepository;
    private final PasswordEncoder passwordEncoder;
    private final EncryptionService encryptionService;
    private final NotificationService notificationService;

    /**
     * Get ATM transaction history
     */
    @Transactional(readOnly = true)
    public Page<ATMTransactionResponse> getTransactionHistory(UUID accountId, Pageable pageable) {
        log.info("Fetching ATM transaction history for account: {}", accountId);
        
        Page<ATMTransaction> transactions = atmTransactionRepository
                .findByAccountIdOrderByTransactionDateDesc(accountId, pageable);
        
        return transactions.map(this::mapToTransactionResponse);
    }

    /**
     * Get withdrawal limits
     */
    @Transactional(readOnly = true)
    public WithdrawalLimitsResponse getWithdrawalLimits(UUID accountId) {
        log.info("Fetching withdrawal limits for account: {}", accountId);
        
        WithdrawalLimit limit = withdrawalLimitRepository.findByAccountId(accountId)
                .orElseGet(() -> createDefaultWithdrawalLimit(accountId));
        
        // Calculate used amounts for today
        BigDecimal dailyUsed = calculateDailyUsage(accountId);
        BigDecimal monthlyUsed = calculateMonthlyUsage(accountId);
        
        return WithdrawalLimitsResponse.builder()
                .accountId(accountId)
                .dailyLimit(limit.getDailyLimit())
                .dailyUsed(dailyUsed)
                .dailyRemaining(limit.getDailyLimit().subtract(dailyUsed))
                .monthlyLimit(limit.getMonthlyLimit())
                .monthlyUsed(monthlyUsed)
                .monthlyRemaining(limit.getMonthlyLimit().subtract(monthlyUsed))
                .singleTransactionLimit(limit.getSingleTransactionLimit())
                .lastUpdated(limit.getUpdatedAt())
                .build();
    }

    /**
     * Update withdrawal limits
     */
    public WithdrawalLimitsResponse updateWithdrawalLimits(UUID accountId, 
                                                          UpdateWithdrawalLimitsRequest request) {
        log.info("Updating withdrawal limits for account: {}", accountId);
        
        // Validate the new limits
        validateWithdrawalLimits(request);
        
        WithdrawalLimit limit = withdrawalLimitRepository.findByAccountId(accountId)
                .orElseGet(() -> WithdrawalLimit.builder()
                        .accountId(accountId)
                        .build());
        
        if (request.getDailyLimit() != null) {
            limit.setDailyLimit(request.getDailyLimit());
        }
        if (request.getMonthlyLimit() != null) {
            limit.setMonthlyLimit(request.getMonthlyLimit());
        }
        if (request.getSingleTransactionLimit() != null) {
            limit.setSingleTransactionLimit(request.getSingleTransactionLimit());
        }
        
        limit.setUpdatedAt(LocalDateTime.now());
        withdrawalLimitRepository.save(limit);
        
        // Send notification
        notificationService.sendLimitUpdateNotification(accountId, limit);
        
        return self.getWithdrawalLimits(accountId);
    }

    /**
     * Change ATM PIN
     */
    public void changePin(ChangePinRequest request) {
        log.info("Processing PIN change for card: ****{}", 
                request.getCardNumber().substring(request.getCardNumber().length() - 4));
        
        ATMCard card = atmCardRepository.findByCardNumber(request.getCardNumber())
                .orElseThrow(() -> new CardNotFoundException("Card not found"));
        
        // Verify current PIN
        if (!passwordEncoder.matches(request.getCurrentPin(), card.getPinHash())) {
            card.setFailedPinAttempts(card.getFailedPinAttempts() + 1);
            
            if (card.getFailedPinAttempts() >= 3) {
                card.setStatus(ATMCard.CardStatus.BLOCKED);
                card.setBlockedAt(LocalDateTime.now());
                atmCardRepository.save(card);
                throw new CardBlockedException("Card blocked due to multiple failed PIN attempts");
            }
            
            atmCardRepository.save(card);
            throw new InvalidPinException("Invalid current PIN");
        }
        
        // Validate new PIN
        validatePin(request.getNewPin());
        
        // Update PIN
        card.setPinHash(passwordEncoder.encode(request.getNewPin()));
        card.setFailedPinAttempts(0);
        card.setPinChangedAt(LocalDateTime.now());
        atmCardRepository.save(card);
        
        // Log the transaction
        logPinChange(card);
        
        // Send notification
        notificationService.sendPinChangeNotification(card.getAccountId());
        
        log.info("PIN changed successfully for card: ****{}", 
                card.getCardNumber().substring(card.getCardNumber().length() - 4));
    }

    /**
     * Reset ATM PIN
     */
    public PinResetResponse resetPin(PinResetRequest request) {
        log.info("Processing PIN reset request");
        
        ATMCard card = atmCardRepository.findByCardNumberAndAccountId(
                request.getCardNumber(), request.getAccountId())
                .orElseThrow(() -> new CardNotFoundException("Card not found"));
        
        // Verify identity (simplified - in production would use OTP or biometric)
        if (!verifyIdentity(request)) {
            throw new UnauthorizedException("Identity verification failed");
        }
        
        // Generate temporary PIN
        String temporaryPin = generateTemporaryPin();
        
        // Update card with temporary PIN
        card.setPinHash(passwordEncoder.encode(temporaryPin));
        card.setTemporaryPin(true);
        card.setPinResetAt(LocalDateTime.now());
        card.setFailedPinAttempts(0);
        card.setStatus(ATMCard.CardStatus.ACTIVE);
        atmCardRepository.save(card);
        
        // Send temporary PIN via secure channel (SMS/Email)
        notificationService.sendTemporaryPin(card.getAccountId(), temporaryPin);
        
        return PinResetResponse.builder()
                .success(true)
                .message("Temporary PIN sent to registered mobile/email")
                .mustChangePin(true)
                .build();
    }

    /**
     * Block ATM card
     */
    public void blockCard(BlockCardRequest request) {
        log.info("Blocking card: ****{}", 
                request.getCardNumber().substring(request.getCardNumber().length() - 4));
        
        ATMCard card = atmCardRepository.findByCardNumber(request.getCardNumber())
                .orElseThrow(() -> new CardNotFoundException("Card not found"));
        
        if (card.getStatus() == ATMCard.CardStatus.BLOCKED) {
            throw new CardAlreadyBlockedException("Card is already blocked");
        }
        
        card.setStatus(ATMCard.CardStatus.BLOCKED);
        card.setBlockedAt(LocalDateTime.now());
        card.setBlockReason(request.getReason());
        atmCardRepository.save(card);
        
        // Log the action
        logCardBlock(card, request.getReason());
        
        // Send notification
        notificationService.sendCardBlockedNotification(card.getAccountId(), request.getReason());
        
        log.info("Card blocked successfully: ****{}", 
                card.getCardNumber().substring(card.getCardNumber().length() - 4));
    }

    /**
     * Unblock ATM card
     */
    public void unblockCard(UnblockCardRequest request) {
        log.info("Unblocking card: ****{}", 
                request.getCardNumber().substring(request.getCardNumber().length() - 4));
        
        ATMCard card = atmCardRepository.findByCardNumber(request.getCardNumber())
                .orElseThrow(() -> new CardNotFoundException("Card not found"));
        
        if (card.getStatus() != ATMCard.CardStatus.BLOCKED) {
            throw new InvalidCardStatusException("Card is not blocked");
        }
        
        // Verify authorization
        if (!verifyUnblockAuthorization(request)) {
            throw new UnauthorizedException("Unblock authorization failed");
        }
        
        card.setStatus(ATMCard.CardStatus.ACTIVE);
        card.setBlockedAt(null);
        card.setBlockReason(null);
        card.setFailedPinAttempts(0);
        card.setUnblockedAt(LocalDateTime.now());
        atmCardRepository.save(card);
        
        // Log the action
        logCardUnblock(card);
        
        // Send notification
        notificationService.sendCardUnblockedNotification(card.getAccountId());
        
        log.info("Card unblocked successfully: ****{}", 
                card.getCardNumber().substring(card.getCardNumber().length() - 4));
    }

    // Helper methods
    
    private ATMTransactionResponse mapToTransactionResponse(ATMTransaction transaction) {
        return ATMTransactionResponse.builder()
                .transactionId(transaction.getId())
                .accountId(transaction.getAccountId())
                .atmId(transaction.getAtmId())
                .atmLocation(transaction.getAtmLocation())
                .transactionType(transaction.getTransactionType())
                .amount(transaction.getAmount())
                .currency(transaction.getCurrency())
                .status(transaction.getStatus())
                .transactionDate(transaction.getTransactionDate())
                .referenceNumber(transaction.getReferenceNumber())
                .build();
    }

    private WithdrawalLimit createDefaultWithdrawalLimit(UUID accountId) {
        WithdrawalLimit limit = WithdrawalLimit.builder()
                .accountId(accountId)
                .dailyLimit(new BigDecimal("1000.00"))
                .monthlyLimit(new BigDecimal("10000.00"))
                .singleTransactionLimit(new BigDecimal("500.00"))
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        
        return withdrawalLimitRepository.save(limit);
    }

    private BigDecimal calculateDailyUsage(UUID accountId) {
        LocalDateTime startOfDay = LocalDate.now().atStartOfDay();
        return atmTransactionRepository.sumWithdrawalsByAccountIdAndDateRange(
                accountId, startOfDay, LocalDateTime.now());
    }

    private BigDecimal calculateMonthlyUsage(UUID accountId) {
        LocalDateTime startOfMonth = LocalDate.now().withDayOfMonth(1).atStartOfDay();
        return atmTransactionRepository.sumWithdrawalsByAccountIdAndDateRange(
                accountId, startOfMonth, LocalDateTime.now());
    }

    private void validateWithdrawalLimits(UpdateWithdrawalLimitsRequest request) {
        if (request.getDailyLimit() != null && request.getDailyLimit().compareTo(BigDecimal.ZERO) <= 0) {
            throw new InvalidLimitException("Daily limit must be greater than zero");
        }
        
        if (request.getMonthlyLimit() != null && request.getMonthlyLimit().compareTo(BigDecimal.ZERO) <= 0) {
            throw new InvalidLimitException("Monthly limit must be greater than zero");
        }
        
        if (request.getSingleTransactionLimit() != null && 
            request.getSingleTransactionLimit().compareTo(BigDecimal.ZERO) <= 0) {
            throw new InvalidLimitException("Single transaction limit must be greater than zero");
        }
        
        // Ensure single transaction limit doesn't exceed daily limit
        if (request.getSingleTransactionLimit() != null && request.getDailyLimit() != null &&
            request.getSingleTransactionLimit().compareTo(request.getDailyLimit()) > 0) {
            throw new InvalidLimitException("Single transaction limit cannot exceed daily limit");
        }
    }

    private void validatePin(String pin) {
        if (pin == null || pin.length() != 4) {
            throw new InvalidPinException("PIN must be 4 digits");
        }
        
        if (!pin.matches("\\d{4}")) {
            throw new InvalidPinException("PIN must contain only digits");
        }
        
        // Check for simple patterns
        if (pin.matches("(\\d)\\1{3}")) {
            throw new InvalidPinException("PIN cannot be all same digits");
        }
        
        if ("1234".equals(pin) || "0000".equals(pin) || "1111".equals(pin)) {
            throw new InvalidPinException("PIN is too simple");
        }
    }

    private boolean verifyIdentity(PinResetRequest request) {
        // In production, this would verify OTP, security questions, or biometric
        return request.getVerificationCode() != null && 
               request.getVerificationCode().length() == 6;
    }

    private boolean verifyUnblockAuthorization(UnblockCardRequest request) {
        // In production, this would verify OTP or manager approval
        return request.getAuthorizationCode() != null && 
               request.getAuthorizationCode().length() > 0;
    }

    private String generateTemporaryPin() {
        // Generate secure random 4-digit PIN
        return String.format("%04d", SECURE_RANDOM.nextInt(10000));
    }

    private void logPinChange(ATMCard card) {
        // Audit log for PIN change
        log.info("PIN changed for card: {}, account: {}", 
                maskCardNumber(card.getCardNumber()), card.getAccountId());
    }

    private void logCardBlock(ATMCard card, String reason) {
        // Audit log for card block
        log.info("Card blocked: {}, account: {}, reason: {}", 
                maskCardNumber(card.getCardNumber()), card.getAccountId(), reason);
    }

    private void logCardUnblock(ATMCard card) {
        // Audit log for card unblock
        log.info("Card unblocked: {}, account: {}", 
                maskCardNumber(card.getCardNumber()), card.getAccountId());
    }

    private String maskCardNumber(String cardNumber) {
        if (cardNumber == null || cardNumber.length() < 8) {
            return "****";
        }
        return "****" + cardNumber.substring(cardNumber.length() - 4);
    }
}