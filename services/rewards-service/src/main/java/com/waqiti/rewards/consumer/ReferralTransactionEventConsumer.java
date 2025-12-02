package com.waqiti.rewards.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.common.audit.AuditService;
import com.waqiti.rewards.domain.ReferralProgram;
import com.waqiti.rewards.domain.ReferralReward;
import com.waqiti.rewards.enums.RewardType;
import com.waqiti.rewards.service.*;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;

/**
 * Kafka consumer for payment/transaction events that qualify referrals
 * Processes first transactions to award referral rewards
 *
 * @author Waqiti Platform Team
 * @version 2.0
 * @since 2025-11-09
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ReferralTransactionEventConsumer {

    private final ReferralProgramService programService;
    private final ReferralRewardService rewardService;
    private final ReferralValidationService validationService;
    private final ReferralFraudDetectionService fraudService;
    private final ReferralStatisticsService statisticsService;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;

    private Counter processedCounter;
    private Counter rewardsIssuedCounter;
    private Counter fraudDetectedCounter;
    private Counter failedCounter;
    private Timer processingTimer;

    @jakarta.annotation.PostConstruct
    public void initMetrics() {
        processedCounter = Counter.builder("referral.transaction.processed")
                .description("Referral transactions processed")
                .register(meterRegistry);

        rewardsIssuedCounter = Counter.builder("referral.transaction.rewards.issued")
                .description("Referral rewards issued from transactions")
                .register(meterRegistry);

        fraudDetectedCounter = Counter.builder("referral.transaction.fraud.detected")
                .description("Fraudulent referral transactions detected")
                .register(meterRegistry);

        failedCounter = Counter.builder("referral.transaction.failed")
                .description("Referral transaction processing failures")
                .register(meterRegistry);

        processingTimer = Timer.builder("referral.transaction.processing.time")
                .description("Time to process referral transaction")
                .register(meterRegistry);
    }

    @KafkaListener(
            topics = "payment-completed",
            groupId = "rewards-service-referral-transaction-group",
            containerFactory = "criticalFinancialKafkaListenerContainerFactory"
    )
    @Transactional(isolation = Isolation.REPEATABLE_READ)
    public void handleTransactionEvent(
            @Payload String eventJson,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            @Header(value = "correlation_id", required = false) byte[] correlationIdBytes,
            Acknowledgment acknowledgment) {

        Timer.Sample sample = Timer.start(meterRegistry);
        String correlationId = correlationIdBytes != null
                ? new String(correlationIdBytes, StandardCharsets.UTF_8)
                : UUID.randomUUID().toString();

        MDC.put("correlation_id", correlationId);
        MDC.put("topic", topic);
        MDC.put("partition", String.valueOf(partition));
        MDC.put("offset", String.valueOf(offset));

        String userId = null;
        String transactionId = null;

        try {
            Map<String, Object> event = objectMapper.readValue(eventJson, Map.class);

            userId = (String) event.get("userId");
            transactionId = (String) event.get("transactionId");
            BigDecimal amount = new BigDecimal(event.get("amount").toString());
            String currency = (String) event.getOrDefault("currency", "USD");
            Boolean isFirstTransaction = (Boolean) event.getOrDefault("isFirstTransaction", false);
            String referralCode = (String) event.get("referralCode");
            String ipAddress = (String) event.get("ipAddress");

            MDC.put("user_id", userId);
            MDC.put("transaction_id", transactionId);
            MDC.put("amount", amount.toString());

            log.info("Processing transaction event: userId={}, txnId={}, amount={}, isFirst={}, referralCode={}",
                    userId, transactionId, amount, isFirstTransaction, referralCode);

            // Only process if this is a first transaction with a referral
            if (Boolean.TRUE.equals(isFirstTransaction) && referralCode != null && !referralCode.isBlank()) {
                processReferralTransaction(
                        userId, transactionId, amount, currency,
                        referralCode, ipAddress, correlationId
                );
            } else {
                log.debug("Transaction does not qualify for referral processing");
            }

            acknowledgment.acknowledge();
            processedCounter.increment();

        } catch (Exception e) {
            log.error("Failed to process transaction event: userId={}, txnId={}, error={}",
                    userId, transactionId, e.getMessage(), e);
            failedCounter.increment();

            // Acknowledge to prevent blocking (DLQ handles retries)
            acknowledgment.acknowledge();

        } finally {
            sample.stop(processingTimer);
            MDC.clear();
        }
    }

    private void processReferralTransaction(String userId, String transactionId,
                                            BigDecimal amount, String currency,
                                            String referralCode, String ipAddress,
                                            String correlationId) {
        UUID refereeId = UUID.fromString(userId);

        try {
            // Get referral link to find referrer
            com.waqiti.rewards.domain.ReferralLink link =
                    getReferralLinkByCode(referralCode);

            if (link == null) {
                log.warn("Referral code not found: {}", referralCode);
                return;
            }

            UUID referrerId = link.getUserId();
            String programId = link.getProgram().getProgramId();
            ReferralProgram program = programService.getProgramByProgramId(programId);

            // Check minimum transaction amount
            if (program.getMinTransactionAmount() != null &&
                amount.compareTo(program.getMinTransactionAmount()) < 0) {
                log.info("Transaction below minimum for referral: required={}, actual={}",
                        program.getMinTransactionAmount(), amount);
                return;
            }

            // Perform fraud check
            var fraudCheck = fraudService.performFraudCheck(
                    link.getLinkId(),
                    referrerId,
                    refereeId,
                    ipAddress
            );

            if ("FAILED".equals(fraudCheck.getCheckStatus())) {
                log.warn("Fraud check failed for referral: referrer={}, referee={}, riskScore={}",
                        referrerId, refereeId, fraudCheck.getRiskScore());
                fraudDetectedCounter.increment();
                return;
            }

            // Issue rewards to referrer
            if (program.getReferrerRewardType() != null) {
                ReferralReward referrerReward = createReward(
                        link.getLinkId(),
                        programId,
                        referrerId,
                        "REFERRER",
                        program.getReferrerRewardType(),
                        program.getReferrerRewardPoints(),
                        program.getReferrerRewardAmount()
                );
                log.info("Referrer reward created: rewardId={}, recipientId={}, type={}, amount={}",
                        referrerReward.getRewardId(), referrerId,
                        referrerReward.getRewardType(), referrerReward.getCashbackAmount());
            }

            // Issue rewards to referee
            if (program.getRefereeRewardType() != null) {
                ReferralReward refereeReward = createReward(
                        link.getLinkId(),
                        programId,
                        refereeId,
                        "REFEREE",
                        program.getRefereeRewardType(),
                        program.getRefereeRewardPoints(),
                        program.getRefereeRewardAmount()
                );
                log.info("Referee reward created: rewardId={}, recipientId={}, type={}, amount={}",
                        refereeReward.getRewardId(), refereeId,
                        refereeReward.getRewardType(), refereeReward.getCashbackAmount());
            }

            // Update statistics
            statisticsService.recordReferralConversion(programId, referrerId, refereeId, amount);

            // Update program counters
            programService.incrementSuccessfulReferrals(programId);

            rewardsIssuedCounter.increment();

            log.info("Referral transaction processed successfully: referrer={}, referee={}, amount={}",
                    referrerId, refereeId, amount);

            auditService.auditFinancialEvent(
                    "REFERRAL_TRANSACTION_PROCESSED",
                    userId,
                    String.format("Referral rewards issued for transaction: %s", transactionId),
                    Map.of(
                            "transaction_id", transactionId,
                            "referral_code", referralCode,
                            "referrer_id", referrerId.toString(),
                            "referee_id", refereeId.toString(),
                            "program_id", programId,
                            "amount", amount.toString(),
                            "currency", currency,
                            "fraud_risk_score", fraudCheck.getRiskScore().toString(),
                            "correlation_id", correlationId
                    )
            );

        } catch (Exception e) {
            log.error("Error processing referral transaction: userId={}, txnId={}, code={}, error={}",
                    userId, transactionId, referralCode, e.getMessage(), e);
            throw e;
        }
    }

    private com.waqiti.rewards.domain.ReferralLink getReferralLinkByCode(String code) {
        try {
            // This would be a repository method we need to add
            return null; // TODO: Implement getLinkByCode in ReferralLinkService
        } catch (Exception e) {
            log.error("Failed to get referral link by code: {}", code, e);
            return null;
        }
    }

    private ReferralReward createReward(String linkId, String programId,
                                        UUID recipientUserId, String recipientType,
                                        RewardType rewardType,
                                        Long pointsAmount, BigDecimal cashbackAmount) {
        return rewardService.createReward(
                linkId,
                programId,
                recipientUserId,
                recipientType,
                rewardType,
                pointsAmount,
                cashbackAmount
        );
    }
}
