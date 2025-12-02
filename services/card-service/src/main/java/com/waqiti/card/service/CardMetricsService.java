package com.waqiti.card.service;

import com.waqiti.card.enums.CardStatus;
import com.waqiti.card.repository.CardAuthorizationRepository;
import com.waqiti.card.repository.CardRepository;
import com.waqiti.card.repository.CardTransactionRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * CardMetricsService - Monitoring and analytics for card operations
 *
 * Provides:
 * - Prometheus metrics for card operations
 * - Performance monitoring
 * - Business metrics (card counts, transaction volumes)
 * - Error rate tracking
 *
 * @author Waqiti Engineering Team
 * @version 2.0
 * @since 2025-11-19
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class CardMetricsService {

    private final CardRepository cardRepository;
    private final CardTransactionRepository transactionRepository;
    private final CardAuthorizationRepository authorizationRepository;
    private final MeterRegistry meterRegistry;

    /**
     * Record card creation metric
     */
    public void recordCardCreation(String cardType) {
        Counter.builder("card.created")
                .tag("type", cardType)
                .register(meterRegistry)
                .increment();
    }

    /**
     * Record card activation metric
     */
    public void recordCardActivation(UUID cardId) {
        Counter.builder("card.activated")
                .register(meterRegistry)
                .increment();
    }

    /**
     * Record card blocking metric
     */
    public void recordCardBlocked(String reason) {
        Counter.builder("card.blocked")
                .tag("reason", reason)
                .register(meterRegistry)
                .increment();
    }

    /**
     * Record authorization attempt
     */
    public void recordAuthorizationAttempt(boolean approved, BigDecimal amount) {
        Counter.builder("card.authorization")
                .tag("status", approved ? "approved" : "declined")
                .register(meterRegistry)
                .increment();

        meterRegistry.summary("card.authorization.amount")
                .record(amount.doubleValue());
    }

    /**
     * Record PIN verification attempt
     */
    public void recordPinVerification(boolean success) {
        Counter.builder("card.pin.verification")
                .tag("result", success ? "success" : "failure")
                .register(meterRegistry)
                .increment();
    }

    /**
     * Get total active cards count
     */
    public long getActiveCardsCount() {
        return cardRepository.countByCardStatusAndDeletedAtIsNull(CardStatus.ACTIVE);
    }

    /**
     * Get total cards count for user
     */
    public long getUserCardsCount(UUID userId) {
        return cardRepository.countByUserIdAndDeletedAtIsNull(userId);
    }

    /**
     * Get transaction volume for time period
     */
    public BigDecimal getTransactionVolume(LocalDateTime startTime, LocalDateTime endTime) {
        return transactionRepository.sumAmountByTimestampBetween(startTime, endTime);
    }

    /**
     * Get authorization approval rate
     */
    public double getAuthorizationApprovalRate(LocalDateTime startTime, LocalDateTime endTime) {
        long total = authorizationRepository.countByAuthorizationTimeBetween(startTime, endTime);
        if (total == 0) return 0.0;

        long approved = authorizationRepository.countApprovedAuthorizationsBetween(startTime, endTime);
        return (double) approved / total * 100.0;
    }
}
