package com.waqiti.payment.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.common.events.MerchantTransactionEvent;
import com.waqiti.common.kafka.BaseKafkaConsumer;
import com.waqiti.common.idempotency.RedisIdempotencyService;
import com.waqiti.payment.service.MerchantPaymentService;
import com.waqiti.payment.service.FraudDetectionService;
import com.waqiti.payment.service.SettlementService;
import com.waqiti.payment.model.MerchantTransaction;
import com.waqiti.payment.model.MerchantRiskProfile;
import com.waqiti.payment.repository.MerchantTransactionRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Set;

/**
 * Consumer for processing merchant transaction events.
 * Handles merchant payments, fee calculations, settlement processing, and risk management.
 */
@Slf4j
@Component
public class MerchantTransactionConsumer extends BaseKafkaConsumer<MerchantTransactionEvent> {

    private static final String TOPIC = "merchant-transaction-events";
    private static final Set<String> HIGH_RISK_CATEGORIES = Set.of(
        "GAMBLING", "ADULT_ENTERTAINMENT", "CRYPTOCURRENCY", "CASH_ADVANCE",
        "MONEY_TRANSFER", "DEBT_COLLECTION", "PAWN_SHOPS"
    );
    private static final Set<String> RESTRICTED_CATEGORIES = Set.of(
        "ILLEGAL_DRUGS", "WEAPONS", "COUNTERFEIT_GOODS"
    );

    private final MerchantPaymentService merchantPaymentService;
    private final FraudDetectionService fraudDetectionService;
    private final SettlementService settlementService;
    private final MerchantTransactionRepository merchantTransactionRepository;
    private final RedisIdempotencyService idempotencyService;

    // Metrics
    private final Counter processedCounter;
    private final Counter highRiskMerchantCounter;
    private final Counter chargebackRiskCounter;
    private final Timer processingTimer;

    @Autowired
    public MerchantTransactionConsumer(
            ObjectMapper objectMapper,
            MeterRegistry meterRegistry,
            MerchantPaymentService merchantPaymentService,
            FraudDetectionService fraudDetectionService,
            SettlementService settlementService,
            MerchantTransactionRepository merchantTransactionRepository,
            RedisIdempotencyService idempotencyService) {
        super(objectMapper, TOPIC);
        this.merchantPaymentService = merchantPaymentService;
        this.fraudDetectionService = fraudDetectionService;
        this.settlementService = settlementService;
        this.merchantTransactionRepository = merchantTransactionRepository;
        this.idempotencyService = idempotencyService;

        this.processedCounter = Counter.builder("merchant_transaction_processed_total")
                .description("Total merchant transactions processed")
                .register(meterRegistry);
        this.highRiskMerchantCounter = Counter.builder("high_risk_merchant_transaction_total")
                .description("Total high-risk merchant transactions")
                .register(meterRegistry);
        this.chargebackRiskCounter = Counter.builder("chargeback_risk_transaction_total")
                .description("Total transactions with chargeback risk")
                .register(meterRegistry);
        this.processingTimer = Timer.builder("merchant_transaction_processing_duration")
                .description("Time taken to process merchant transactions")
                .register(meterRegistry);
    }

    @RetryableTopic(
        attempts = "3",
        backoff = @Backoff(delay = 1000, multiplier = 2.0),
        dltStrategy = org.springframework.kafka.retrytopic.DltStrategy.FAIL_ON_ERROR,
        include = {Exception.class}
    )
    @KafkaListener(topics = TOPIC, groupId = "payment-service-merchant-group")
    @Transactional(isolation = Isolation.SERIALIZABLE, rollbackFor = Exception.class)
    public void consume(
            ConsumerRecord<String, String> record,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.RECEIVED_MESSAGE_KEY) String key,
            Acknowledgment ack) {

        Timer.Sample sample = Timer.start();

        try {
            log.info("Processing merchant transaction event - Key: {}, Partition: {}, Offset: {}",
                    key, partition, record.offset());

            MerchantTransactionEvent event = deserializeEvent(record.value(), MerchantTransactionEvent.class);

            // Validate required fields
            validateEvent(event);

            // Build idempotency key
            String idempotencyKey = idempotencyService.buildIdempotencyKey(
                "payment-service",
                "MerchantTransactionEvent",
                event.getTransactionId()
            );

            // Universal idempotency check (30-day TTL for financial transactions)
            if (idempotencyService.isProcessed(idempotencyKey)) {
                log.info("⏭️ Merchant transaction already processed: {}", event.getTransactionId());
                ack.acknowledge();
                return;
            }

            // Process the merchant transaction
            processMerchantTransaction(event);

            // Mark as processed (30-day TTL for financial transactions)
            idempotencyService.markFinancialOperationProcessed(idempotencyKey);

            processedCounter.increment();
            log.info("Successfully processed merchant transaction: {}", event.getTransactionId());

            ack.acknowledge();

        } catch (Exception e) {
            log.error("Error processing merchant transaction event: {}", record.value(), e);
            throw new RuntimeException("Failed to process merchant transaction event", e);
        } finally {
            processingTimer.stop(sample);
        }
    }

    private void processMerchantTransaction(MerchantTransactionEvent event) {
        try {
            // Create merchant transaction record
            MerchantTransaction transaction = createMerchantTransaction(event);

            // Check if merchant category is restricted
            if (isRestrictedCategory(event.getMerchantCategory())) {
                handleRestrictedTransaction(event, transaction);
                return;
            }

            // Get merchant risk profile
            MerchantRiskProfile riskProfile = merchantPaymentService.getMerchantRiskProfile(event.getMerchantId());
            transaction.setMerchantRiskScore(riskProfile.getRiskScore());
            transaction.setMerchantTier(riskProfile.getTier());

            // Check for high-risk merchants
            if (isHighRiskMerchant(event, riskProfile)) {
                handleHighRiskMerchant(event, transaction, riskProfile);
                highRiskMerchantCounter.increment();
            }

            // Perform fraud checks
            boolean fraudCheckPassed = performMerchantFraudCheck(event, transaction);
            if (!fraudCheckPassed) {
                return; // Transaction blocked
            }

            // Calculate merchant fees and settlement
            calculateMerchantFees(event, transaction, riskProfile);

            // Check for chargeback risk
            assessChargebackRisk(event, transaction);

            // Process the payment
            processMerchantPayment(event, transaction);

            // Schedule settlement
            scheduleSettlement(event, transaction);

            // Save the transaction
            merchantTransactionRepository.save(transaction);

            // Send notifications
            sendMerchantNotifications(event, transaction);

            log.info("Processed merchant transaction: {} for merchant: {} (${} - Fee: ${})",
                    event.getTransactionId(), event.getMerchantId(),
                    event.getTransactionAmount(), transaction.getMerchantFee());

        } catch (Exception e) {
            log.error("Error processing merchant transaction: {}", event.getTransactionId(), e);
            throw new RuntimeException("Failed to process merchant transaction", e);
        }
    }

    private MerchantTransaction createMerchantTransaction(MerchantTransactionEvent event) {
        return MerchantTransaction.builder()
                .transactionId(event.getTransactionId())
                .merchantId(event.getMerchantId())
                .merchantName(event.getMerchantName())
                .customerId(event.getCustomerId())
                .amount(event.getTransactionAmount())
                .currency(event.getCurrency())
                .merchantCategory(event.getMerchantCategory())
                .merchantCountry(event.getMerchantCountry())
                .paymentMethod(event.getPaymentMethod())
                .cardType(event.getCardType())
                .acquirerReferenceNumber(event.getAcquirerReferenceNumber())
                .terminalId(event.getTerminalId())
                .merchantLocationId(event.getMerchantLocationId())
                .transactionTime(event.getTransactionTime())
                .createdAt(LocalDateTime.now())
                .status("PROCESSING")
                .build();
    }

    private boolean isRestrictedCategory(String merchantCategory) {
        return RESTRICTED_CATEGORIES.contains(merchantCategory.toUpperCase());
    }

    private void handleRestrictedTransaction(MerchantTransactionEvent event, MerchantTransaction transaction) {
        try {
            transaction.setStatus("BLOCKED");
            transaction.setBlockReason("RESTRICTED_MERCHANT_CATEGORY");

            // Block the transaction
            merchantPaymentService.blockTransaction(event.getTransactionId(),
                "Restricted merchant category: " + event.getMerchantCategory());

            // Create compliance case
            merchantPaymentService.createComplianceCase(event.getMerchantId(),
                event.getTransactionId(), "RESTRICTED_CATEGORY");

            // Send alerts
            merchantPaymentService.sendMerchantAlert(event.getMerchantId(),
                "RESTRICTED_TRANSACTION_ATTEMPTED");

            merchantTransactionRepository.save(transaction);

            log.warn("Restricted merchant transaction blocked: {} - Category: {}",
                    event.getTransactionId(), event.getMerchantCategory());

        } catch (Exception e) {
            log.error("Error handling restricted transaction: {}", event.getTransactionId(), e);
            throw new RuntimeException("Failed to handle restricted transaction", e);
        }
    }

    private boolean isHighRiskMerchant(MerchantTransactionEvent event, MerchantRiskProfile riskProfile) {
        return riskProfile.getRiskScore() > 70 ||
               HIGH_RISK_CATEGORIES.contains(event.getMerchantCategory().toUpperCase()) ||
               riskProfile.getChargebackRate() > 0.02 || // 2% chargeback rate
               merchantPaymentService.isBlacklistedMerchant(event.getMerchantId());
    }

    private void handleHighRiskMerchant(MerchantTransactionEvent event,
                                      MerchantTransaction transaction,
                                      MerchantRiskProfile riskProfile) {
        try {
            transaction.setRiskLevel("HIGH");
            transaction.setRequiresReview(true);

            // Enhanced monitoring for high-risk merchants
            merchantPaymentService.enableEnhancedMonitoring(event.getMerchantId());

            // Additional verification for large amounts
            if (event.getTransactionAmount().compareTo(BigDecimal.valueOf(5000)) > 0) {
                transaction.setRequiresManualApproval(true);
                merchantPaymentService.requestManualApproval(event.getTransactionId(),
                    "HIGH_RISK_MERCHANT_LARGE_AMOUNT");
            }

            // Create monitoring case
            merchantPaymentService.createMonitoringCase(event.getMerchantId(),
                event.getTransactionId(), "HIGH_RISK_MERCHANT");

            log.info("High-risk merchant transaction flagged: {} for merchant: {} (Risk: {})",
                    event.getTransactionId(), event.getMerchantId(), riskProfile.getRiskScore());

        } catch (Exception e) {
            log.error("Error handling high-risk merchant: {}", event.getTransactionId(), e);
            throw new RuntimeException("Failed to handle high-risk merchant", e);
        }
    }

    private boolean performMerchantFraudCheck(MerchantTransactionEvent event, MerchantTransaction transaction) {
        try {
            // Check for velocity fraud
            boolean velocityOk = fraudDetectionService.checkMerchantVelocity(
                event.getMerchantId(), event.getTransactionAmount()
            );

            // Check for duplicate transactions
            boolean noDuplicates = fraudDetectionService.checkDuplicateTransaction(
                event.getCustomerId(), event.getMerchantId(),
                event.getTransactionAmount(), event.getTransactionTime()
            );

            // Check card testing patterns
            boolean noCardTesting = fraudDetectionService.checkCardTestingPattern(
                event.getMerchantId(), event.getCardType()
            );

            // Check for suspicious patterns
            boolean noSuspiciousPatterns = fraudDetectionService.checkSuspiciousPatterns(
                event.getMerchantId(), event.getTransactionAmount(),
                event.getMerchantLocationId(), event.getTransactionTime()
            );

            boolean allPassed = velocityOk && noDuplicates && noCardTesting && noSuspiciousPatterns;

            if (!allPassed) {
                handleMerchantFraudDetection(event, transaction,
                    velocityOk, noDuplicates, noCardTesting, noSuspiciousPatterns);
            }

            return allPassed;

        } catch (Exception e) {
            log.error("Error performing merchant fraud checks: {}", event.getTransactionId(), e);
            transaction.setStatus("FRAUD_CHECK_ERROR");
            return false;
        }
    }

    private void handleMerchantFraudDetection(MerchantTransactionEvent event,
                                            MerchantTransaction transaction,
                                            boolean velocityOk,
                                            boolean noDuplicates,
                                            boolean noCardTesting,
                                            boolean noSuspiciousPatterns) {
        try {
            transaction.setStatus("FRAUD_DETECTED");
            StringBuilder reasons = new StringBuilder();
            if (!velocityOk) reasons.append("Velocity breach; ");
            if (!noDuplicates) reasons.append("Duplicate transaction; ");
            if (!noCardTesting) reasons.append("Card testing pattern; ");
            if (!noSuspiciousPatterns) reasons.append("Suspicious patterns; ");

            transaction.setFraudReasons(reasons.toString());

            // Block the transaction
            merchantPaymentService.blockTransaction(event.getTransactionId(),
                "MERCHANT_FRAUD_DETECTED: " + reasons.toString());

            // Create fraud investigation
            fraudDetectionService.createMerchantFraudCase(event.getMerchantId(),
                event.getTransactionId(), reasons.toString());

            // Send fraud alerts
            fraudDetectionService.sendMerchantFraudAlert(event.getMerchantId(),
                event.getTransactionId(), "MERCHANT_FRAUD_DETECTED");

            merchantTransactionRepository.save(transaction);

            log.warn("Merchant fraud detected: {} - {}",
                    event.getTransactionId(), reasons.toString());

        } catch (Exception e) {
            log.error("Error handling merchant fraud detection: {}", event.getTransactionId(), e);
            throw new RuntimeException("Failed to handle merchant fraud detection", e);
        }
    }

    private void calculateMerchantFees(MerchantTransactionEvent event,
                                     MerchantTransaction transaction,
                                     MerchantRiskProfile riskProfile) {
        try {
            // Base merchant fee
            BigDecimal baseFeeRate = merchantPaymentService.getBaseFeeRate(
                event.getMerchantId(), event.getPaymentMethod()
            );

            // Risk-based fee adjustment
            BigDecimal riskAdjustment = merchantPaymentService.getRiskAdjustment(
                riskProfile.getRiskScore()
            );

            // Volume-based discount
            BigDecimal volumeDiscount = merchantPaymentService.getVolumeDiscount(
                event.getMerchantId(), event.getTransactionAmount()
            );

            // Card type fees
            BigDecimal cardTypeFee = merchantPaymentService.getCardTypeFee(
                event.getCardType(), event.getTransactionAmount()
            );

            // Cross-border fees
            BigDecimal crossBorderFee = BigDecimal.ZERO;
            if (!event.getMerchantCountry().equals(event.getCardCountry())) {
                crossBorderFee = merchantPaymentService.getCrossBorderFee(
                    event.getTransactionAmount()
                );
            }

            BigDecimal totalFeeRate = baseFeeRate.add(riskAdjustment)
                    .subtract(volumeDiscount)
                    .add(cardTypeFee);

            BigDecimal merchantFee = event.getTransactionAmount()
                    .multiply(totalFeeRate)
                    .add(crossBorderFee);

            BigDecimal settlementAmount = event.getTransactionAmount().subtract(merchantFee);

            transaction.setBaseFeeRate(baseFeeRate);
            transaction.setRiskAdjustment(riskAdjustment);
            transaction.setVolumeDiscount(volumeDiscount);
            transaction.setCardTypeFee(cardTypeFee);
            transaction.setCrossBorderFee(crossBorderFee);
            transaction.setMerchantFee(merchantFee);
            transaction.setSettlementAmount(settlementAmount);

        } catch (Exception e) {
            log.error("Error calculating merchant fees: {}", event.getTransactionId(), e);
            throw new RuntimeException("Failed to calculate merchant fees", e);
        }
    }

    private void assessChargebackRisk(MerchantTransactionEvent event, MerchantTransaction transaction) {
        try {
            // Calculate chargeback risk score
            int chargebackRisk = merchantPaymentService.calculateChargebackRisk(
                event.getMerchantId(),
                event.getCustomerId(),
                event.getTransactionAmount(),
                event.getMerchantCategory(),
                event.getPaymentMethod()
            );

            transaction.setChargebackRisk(chargebackRisk);

            if (chargebackRisk > 70) {
                transaction.setHighChargebackRisk(true);

                // Set up chargeback monitoring
                merchantPaymentService.enableChargebackMonitoring(
                    event.getTransactionId(), event.getMerchantId()
                );

                chargebackRiskCounter.increment();

                log.info("High chargeback risk transaction: {} (Risk: {})",
                        event.getTransactionId(), chargebackRisk);
            }

        } catch (Exception e) {
            log.error("Error assessing chargeback risk: {}", event.getTransactionId(), e);
            // Don't fail transaction for chargeback risk assessment errors
        }
    }

    private void processMerchantPayment(MerchantTransactionEvent event, MerchantTransaction transaction) {
        try {
            if (transaction.getRequiresManualApproval() != null && transaction.getRequiresManualApproval()) {
                // Put on hold for manual approval
                transaction.setStatus("PENDING_APPROVAL");
                merchantPaymentService.putOnHold(event.getTransactionId(), "MANUAL_APPROVAL_REQUIRED");
            } else {
                // Process the payment
                String paymentResult = merchantPaymentService.processPayment(
                    event.getTransactionId(),
                    event.getTransactionAmount(),
                    event.getPaymentMethod(),
                    event.getMerchantId()
                );

                transaction.setPaymentResult(paymentResult);
                transaction.setStatus("COMPLETED");
                transaction.setProcessedAt(LocalDateTime.now());
            }

        } catch (Exception e) {
            log.error("Error processing merchant payment: {}", event.getTransactionId(), e);
            transaction.setStatus("FAILED");
            transaction.setErrorMessage(e.getMessage());
            throw new RuntimeException("Failed to process merchant payment", e);
        }
    }

    private void scheduleSettlement(MerchantTransactionEvent event, MerchantTransaction transaction) {
        try {
            if ("COMPLETED".equals(transaction.getStatus())) {
                // Get merchant settlement schedule
                String settlementSchedule = merchantPaymentService.getSettlementSchedule(event.getMerchantId());

                LocalDateTime settlementDate = settlementService.calculateSettlementDate(
                    event.getTransactionTime(), settlementSchedule
                );

                // Schedule the settlement
                settlementService.scheduleSettlement(
                    event.getMerchantId(),
                    event.getTransactionId(),
                    transaction.getSettlementAmount(),
                    settlementDate
                );

                transaction.setScheduledSettlementDate(settlementDate);
            }

        } catch (Exception e) {
            log.error("Error scheduling settlement: {}", event.getTransactionId(), e);
            // Don't fail transaction for settlement scheduling errors
        }
    }

    private void sendMerchantNotifications(MerchantTransactionEvent event, MerchantTransaction transaction) {
        try {
            // Send merchant notification
            String notificationType = switch (transaction.getStatus()) {
                case "COMPLETED" -> "MERCHANT_PAYMENT_SUCCESS";
                case "PENDING_APPROVAL" -> "MERCHANT_PAYMENT_PENDING";
                case "BLOCKED" -> "MERCHANT_PAYMENT_BLOCKED";
                case "FRAUD_DETECTED" -> "MERCHANT_FRAUD_ALERT";
                default -> "MERCHANT_PAYMENT_UPDATE";
            };

            merchantPaymentService.sendMerchantNotification(
                event.getMerchantId(), notificationType, transaction);

            // Send customer notification
            merchantPaymentService.sendCustomerNotification(
                event.getCustomerId(), "MERCHANT_PAYMENT_PROCESSED", transaction);

        } catch (Exception e) {
            log.error("Error sending merchant notifications: {}", event.getTransactionId(), e);
            // Don't fail the transaction for notification errors
        }
    }

    private void validateEvent(MerchantTransactionEvent event) {
        if (event.getTransactionId() == null || event.getTransactionId().trim().isEmpty()) {
            throw new IllegalArgumentException("Transaction ID cannot be null or empty");
        }
        if (event.getMerchantId() == null || event.getMerchantId().trim().isEmpty()) {
            throw new IllegalArgumentException("Merchant ID cannot be null or empty");
        }
        if (event.getCustomerId() == null || event.getCustomerId().trim().isEmpty()) {
            throw new IllegalArgumentException("Customer ID cannot be null or empty");
        }
        if (event.getTransactionAmount() == null || event.getTransactionAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Transaction amount must be positive");
        }
        if (event.getMerchantCategory() == null || event.getMerchantCategory().trim().isEmpty()) {
            throw new IllegalArgumentException("Merchant category cannot be null or empty");
        }
    }

    @Override
    protected void handleProcessingError(String message, Exception error) {
        log.error("Merchant transaction processing error: {}", message, error);
        // Additional error handling logic
    }

    @Override
    protected void logProcessingMetrics(String key, long processingTime) {
        log.debug("Processed merchant transaction event - Key: {}, Time: {}ms", key, processingTime);
    }
}