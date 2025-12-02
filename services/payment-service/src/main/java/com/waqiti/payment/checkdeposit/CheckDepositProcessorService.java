package com.waqiti.payment.checkdeposit;

import com.waqiti.payment.checkdeposit.dto.CheckDeposit;
import com.waqiti.payment.checkdeposit.dto.DepositStatus;
import com.waqiti.payment.checkdeposit.repository.CheckDepositRepository;
import com.waqiti.payment.wallet.WalletService;
import com.waqiti.payment.notification.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * Separate service for transactional check deposit processing operations
 * This ensures proper Spring AOP proxy behavior for @Transactional methods
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CheckDepositProcessorService {

    private final CheckDepositRepository depositRepository;
    private final WalletService walletService;
    private final NotificationService notificationService;

    @Transactional
    public void processDeposit(CheckDeposit deposit) {
        log.info("Processing deposit: {}", deposit.getId());
        
        try {
            // Calculate hold period
            int holdDays = calculateHoldPeriod(deposit);
            Instant availableAt = Instant.now().plus(holdDays, ChronoUnit.DAYS);
            
            deposit.setHoldDays(holdDays);
            deposit.setFundsAvailableAt(availableAt);
            
            // Credit wallet with hold
            walletService.creditWithHold(
                deposit.getWalletId(),
                deposit.getAmount(),
                availableAt,
                "Check deposit: " + deposit.getId()
            );
            
            // Update deposit status
            deposit.setStatus(DepositStatus.COMPLETED);
            deposit.setProcessedAt(Instant.now());
            depositRepository.save(deposit);
            
            // Send notification
            notificationService.sendCheckDepositNotification(
                deposit.getUserId(),
                deposit.getId(),
                deposit.getAmount(),
                availableAt
            );
            
            log.info("Deposit processed successfully: {}", deposit.getId());
            
        } catch (Exception e) {
            log.error("Error processing deposit: {}", deposit.getId(), e);
            
            // Update deposit status to failed
            deposit.setStatus(DepositStatus.FAILED);
            deposit.setFailureReason(e.getMessage());
            depositRepository.save(deposit);
            
            // Send failure notification
            notificationService.sendCheckDepositFailureNotification(
                deposit.getUserId(),
                deposit.getId(),
                e.getMessage()
            );
            
            throw e;
        }
    }

    private int calculateHoldPeriod(CheckDeposit deposit) {
        // Basic hold period calculation - can be enhanced with risk assessment
        BigDecimal amount = deposit.getAmount();
        
        if (amount.compareTo(new BigDecimal("5000")) > 0) {
            return 5; // 5 days for large amounts
        } else if (amount.compareTo(new BigDecimal("1000")) > 0) {
            return 3; // 3 days for medium amounts
        } else {
            return 1; // 1 day for small amounts
        }
    }
}