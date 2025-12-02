package com.waqiti.payment.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.common.events.AtmTransactionEvent;
import com.waqiti.common.kafka.BaseKafkaConsumer;
import com.waqiti.payment.service.AtmService;
import com.waqiti.payment.service.FraudDetectionService;
import com.waqiti.payment.service.SecurityService;
import com.waqiti.payment.model.AtmTransaction;
import com.waqiti.payment.model.AtmLocation;
import com.waqiti.payment.repository.AtmTransactionRepository;
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
import java.time.LocalTime;
import java.util.Set;

/**
 * Consumer for processing ATM transaction events.
 * Handles cash withdrawals, deposits, balance inquiries, and ATM-specific fraud detection.
 */
@Slf4j
@Component
public class AtmTransactionConsumer extends BaseKafkaConsumer<AtmTransactionEvent> {

    private static final String TOPIC = "atm-transaction-events";
    private static final Set<String> CASH_TRANSACTIONS = Set.of("WITHDRAWAL", "DEPOSIT");
    private static final Set<String> HIGH_RISK_LOCATIONS = Set.of(
        "AIRPORT", "CASINO", "BAR", "ADULT_ENTERTAINMENT", "FOREIGN_COUNTRY"
    );
    private static final BigDecimal LARGE_WITHDRAWAL_THRESHOLD = BigDecimal.valueOf(1000);
    private static final LocalTime NIGHT_START = LocalTime.of(22, 0);
    private static final LocalTime NIGHT_END = LocalTime.of(6, 0);

    private final AtmService atmService;
    private final FraudDetectionService fraudDetectionService;
    private final SecurityService securityService;
    private final AtmTransactionRepository atmTransactionRepository;

    // Metrics
    private final Counter processedCounter;
    private final Counter fraudDetectedCounter;
    private final Counter highRiskLocationCounter;
    private final Counter largeWithdrawalCounter;
    private final Timer processingTimer;

    @Autowired
    public AtmTransactionConsumer(
            ObjectMapper objectMapper,
            MeterRegistry meterRegistry,
            AtmService atmService,
            FraudDetectionService fraudDetectionService,
            SecurityService securityService,
            AtmTransactionRepository atmTransactionRepository) {
        super(objectMapper, TOPIC);
        this.atmService = atmService;
        this.fraudDetectionService = fraudDetectionService;
        this.securityService = securityService;
        this.atmTransactionRepository = atmTransactionRepository;

        this.processedCounter = Counter.builder("atm_transaction_processed_total")
                .description("Total ATM transactions processed")
                .register(meterRegistry);
        this.fraudDetectedCounter = Counter.builder("atm_fraud_detected_total")
                .description("Total ATM fraud cases detected")
                .register(meterRegistry);
        this.highRiskLocationCounter = Counter.builder("atm_high_risk_location_total")
                .description("Total ATM transactions at high-risk locations")
                .register(meterRegistry);
        this.largeWithdrawalCounter = Counter.builder("atm_large_withdrawal_total")
                .description("Total large ATM withdrawals")
                .register(meterRegistry);
        this.processingTimer = Timer.builder("atm_transaction_processing_duration")
                .description("Time taken to process ATM transactions")
                .register(meterRegistry);
    }

    @RetryableTopic(
        attempts = "3",
        backoff = @Backoff(delay = 1000, multiplier = 2.0),
        dltStrategy = org.springframework.kafka.retrytopic.DltStrategy.FAIL_ON_ERROR,
        include = {Exception.class}
    )
    @KafkaListener(topics = TOPIC, groupId = "payment-service-atm-group")
    @Transactional(isolation = Isolation.SERIALIZABLE, rollbackFor = Exception.class)
    public void consume(
            ConsumerRecord<String, String> record,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.RECEIVED_MESSAGE_KEY) String key,
            Acknowledgment ack) {

        Timer.Sample sample = Timer.start();

        try {
            log.info("Processing ATM transaction event - Key: {}, Partition: {}, Offset: {}",
                    key, partition, record.offset());

            AtmTransactionEvent event = deserializeEvent(record.value(), AtmTransactionEvent.class);

            // Validate required fields
            validateEvent(event);

            // Check for duplicate processing
            if (isAlreadyProcessed(event.getTransactionId(), event.getEventId())) {
                log.info("ATM transaction already processed: {}", event.getTransactionId());
                ack.acknowledge();
                return;
            }

            // Process the ATM transaction
            processAtmTransaction(event);

            processedCounter.increment();
            log.info("Successfully processed ATM transaction: {}", event.getTransactionId());

            ack.acknowledge();

        } catch (Exception e) {
            log.error("Error processing ATM transaction event: {}", record.value(), e);
            throw new RuntimeException("Failed to process ATM transaction event", e);
        } finally {
            processingTimer.stop(sample);
        }
    }

    private void processAtmTransaction(AtmTransactionEvent event) {
        try {
            // Create ATM transaction record
            AtmTransaction transaction = createAtmTransaction(event);

            // Get ATM location information
            AtmLocation atmLocation = atmService.getAtmLocation(event.getAtmId());
            transaction.setAtmLocation(atmLocation);

            // Perform fraud detection checks
            boolean fraudCheckPassed = performAtmFraudChecks(event, transaction, atmLocation);
            if (!fraudCheckPassed) {
                fraudDetectedCounter.increment();
                return; // Transaction blocked
            }

            // Check for high-risk locations
            if (isHighRiskLocation(atmLocation)) {
                handleHighRiskLocation(event, transaction, atmLocation);
                highRiskLocationCounter.increment();
            }

            // Check for large withdrawals
            if (isLargeWithdrawal(event)) {
                handleLargeWithdrawal(event, transaction);
                largeWithdrawalCounter.increment();
            }

            // Check for unusual time patterns
            if (isUnusualTime(event)) {
                handleUnusualTimeTransaction(event, transaction);
            }

            // Calculate fees
            calculateAtmFees(event, transaction, atmLocation);

            // Process the ATM transaction
            processAtmPayment(event, transaction);

            // Update account balance
            updateAccountBalance(event, transaction);

            // Save the transaction
            atmTransactionRepository.save(transaction);

            // Send notifications
            sendAtmNotifications(event, transaction);

            // Update ATM metrics
            updateAtmMetrics(event, transaction, atmLocation);

            log.info("Processed ATM transaction: {} at {} - {} ${}",
                    event.getTransactionId(), event.getAtmId(),
                    event.getTransactionType(), event.getAmount());

        } catch (Exception e) {
            log.error("Error processing ATM transaction: {}", event.getTransactionId(), e);
            throw new RuntimeException("Failed to process ATM transaction", e);
        }
    }

    private AtmTransaction createAtmTransaction(AtmTransactionEvent event) {
        return AtmTransaction.builder()
                .transactionId(event.getTransactionId())
                .customerId(event.getCustomerId())
                .accountId(event.getAccountId())
                .atmId(event.getAtmId())
                .amount(event.getAmount())
                .currency(event.getCurrency())
                .transactionType(event.getTransactionType())
                .cardNumber(event.getCardNumber())
                .pinVerified(event.isPinVerified())
                .receiptRequested(event.isReceiptRequested())
                .transactionTime(event.getTransactionTime())
                .createdAt(LocalDateTime.now())
                .status("PROCESSING")
                .build();
    }

    private boolean performAtmFraudChecks(AtmTransactionEvent event,
                                        AtmTransaction transaction,
                                        AtmLocation atmLocation) {
        try {
            // Check for card skimming indicators
            boolean noSkimming = fraudDetectionService.checkCardSkimming(
                event.getAtmId(), event.getCardNumber(), event.getTransactionTime()
            );

            // Check for PIN try attacks
            boolean noPinAttack = fraudDetectionService.checkPinTryAttack(
                event.getCardNumber(), event.getAtmId()
            );

            // Check for geographic anomalies
            boolean geoOk = fraudDetectionService.checkGeographicAnomaly(
                event.getCustomerId(), atmLocation.getLatitude(),
                atmLocation.getLongitude(), event.getTransactionTime()
            );

            // Check for velocity fraud
            boolean velocityOk = fraudDetectionService.checkAtmVelocity(
                event.getCustomerId(), event.getAmount(), event.getTransactionTime()
            );

            // Check for stolen card patterns
            boolean noStolenCard = fraudDetectionService.checkStolenCardPattern(
                event.getCardNumber(), event.getTransactionTime()
            );

            // Check for ATM compromise
            boolean atmSecure = securityService.checkAtmCompromise(event.getAtmId());

            boolean allPassed = noSkimming && noPinAttack && geoOk &&
                              velocityOk && noStolenCard && atmSecure;

            if (!allPassed) {
                handleAtmFraudDetection(event, transaction, noSkimming, noPinAttack,
                    geoOk, velocityOk, noStolenCard, atmSecure);
            }

            return allPassed;

        } catch (Exception e) {
            log.error("Error performing ATM fraud checks: {}", event.getTransactionId(), e);
            transaction.setStatus("FRAUD_CHECK_ERROR");
            return false;
        }
    }

    private void handleAtmFraudDetection(AtmTransactionEvent event,
                                       AtmTransaction transaction,
                                       boolean noSkimming,
                                       boolean noPinAttack,
                                       boolean geoOk,
                                       boolean velocityOk,
                                       boolean noStolenCard,
                                       boolean atmSecure) {
        try {
            transaction.setStatus("FRAUD_DETECTED");
            StringBuilder reasons = new StringBuilder();
            if (!noSkimming) reasons.append("Card skimming detected; ");
            if (!noPinAttack) reasons.append("PIN try attack; ");
            if (!geoOk) reasons.append("Geographic anomaly; ");
            if (!velocityOk) reasons.append("Velocity breach; ");
            if (!noStolenCard) reasons.append("Stolen card pattern; ");
            if (!atmSecure) reasons.append("ATM compromise; ");

            transaction.setFraudReasons(reasons.toString());

            // Block the transaction immediately
            atmService.blockTransaction(event.getTransactionId(),
                "ATM_FRAUD_DETECTED: " + reasons.toString());

            // Block the card if high-risk fraud detected
            if (!noStolenCard || !atmSecure) {
                atmService.blockCard(event.getCardNumber(), "ATM_FRAUD_BLOCK");
            }

            // Create fraud investigation
            fraudDetectionService.createAtmFraudCase(event.getCustomerId(),
                event.getTransactionId(), event.getAtmId(), reasons.toString());

            // Send immediate fraud alerts
            fraudDetectionService.sendAtmFraudAlert(event.getCustomerId(),
                event.getTransactionId(), "ATM_FRAUD_DETECTED");

            // Alert ATM security if ATM compromise detected
            if (!atmSecure) {
                securityService.alertAtmSecurity(event.getAtmId(),
                    "ATM_COMPROMISE_DETECTED", event.getTransactionId());
            }

            atmTransactionRepository.save(transaction);

            log.warn("ATM fraud detected: {} at ATM {} - {}",
                    event.getTransactionId(), event.getAtmId(), reasons.toString());

        } catch (Exception e) {
            log.error("Error handling ATM fraud detection: {}", event.getTransactionId(), e);
            throw new RuntimeException("Failed to handle ATM fraud detection", e);
        }
    }

    private boolean isHighRiskLocation(AtmLocation atmLocation) {
        return HIGH_RISK_LOCATIONS.contains(atmLocation.getLocationType().toUpperCase()) ||
               atmLocation.isHighCrimeArea() ||
               atmLocation.isUnattended();
    }

    private void handleHighRiskLocation(AtmTransactionEvent event,
                                      AtmTransaction transaction,
                                      AtmLocation atmLocation) {
        try {
            transaction.setRiskLevel("HIGH");
            transaction.setLocationRisk(true);

            // Enhanced monitoring for high-risk locations
            atmService.enableEnhancedMonitoring(event.getCustomerId(),
                event.getAtmId(), "HIGH_RISK_LOCATION");

            // Additional verification for withdrawals
            if ("WITHDRAWAL".equals(event.getTransactionType()) &&
                event.getAmount().compareTo(BigDecimal.valueOf(500)) > 0) {

                transaction.setRequiresVerification(true);
                atmService.requestAdditionalVerification(event.getTransactionId(),
                    "HIGH_RISK_LOCATION_WITHDRAWAL");
            }

            log.info("High-risk location ATM transaction: {} at {} ({})",
                    event.getTransactionId(), event.getAtmId(),
                    atmLocation.getLocationType());

        } catch (Exception e) {
            log.error("Error handling high-risk location: {}", event.getTransactionId(), e);
            throw new RuntimeException("Failed to handle high-risk location", e);
        }
    }

    private boolean isLargeWithdrawal(AtmTransactionEvent event) {
        return "WITHDRAWAL".equals(event.getTransactionType()) &&
               event.getAmount().compareTo(LARGE_WITHDRAWAL_THRESHOLD) > 0;
    }

    private void handleLargeWithdrawal(AtmTransactionEvent event, AtmTransaction transaction) {
        try {
            transaction.setLargeWithdrawal(true);

            // Check daily withdrawal limits
            boolean withinLimits = atmService.checkDailyWithdrawalLimits(
                event.getCustomerId(), event.getAmount()
            );

            if (!withinLimits) {
                transaction.setStatus("LIMIT_EXCEEDED");
                atmService.blockTransaction(event.getTransactionId(),
                    "DAILY_WITHDRAWAL_LIMIT_EXCEEDED");

                atmService.sendCustomerNotification(event.getCustomerId(),
                    "WITHDRAWAL_LIMIT_EXCEEDED", transaction);
                return;
            }

            // Notify customer of large withdrawal
            atmService.sendSecurityNotification(event.getCustomerId(),
                "LARGE_WITHDRAWAL_ALERT", transaction);

            // Enhanced monitoring for large withdrawals
            atmService.enableEnhancedMonitoring(event.getCustomerId(),
                event.getAtmId(), "LARGE_WITHDRAWAL");

            log.info("Large withdrawal processed: {} - ${} at ATM {}",
                    event.getTransactionId(), event.getAmount(), event.getAtmId());

        } catch (Exception e) {
            log.error("Error handling large withdrawal: {}", event.getTransactionId(), e);
            throw new RuntimeException("Failed to handle large withdrawal", e);
        }
    }

    private boolean isUnusualTime(AtmTransactionEvent event) {
        LocalTime transactionTime = event.getTransactionTime().toLocalTime();
        return transactionTime.isAfter(NIGHT_START) || transactionTime.isBefore(NIGHT_END);
    }

    private void handleUnusualTimeTransaction(AtmTransactionEvent event, AtmTransaction transaction) {
        try {
            transaction.setUnusualTime(true);

            // Enhanced fraud checks for night transactions
            if ("WITHDRAWAL".equals(event.getTransactionType())) {
                boolean additionalChecks = fraudDetectionService.performNightTransactionChecks(
                    event.getCustomerId(), event.getAmount(), event.getAtmId()
                );

                if (!additionalChecks) {
                    transaction.setRequiresVerification(true);
                    atmService.requestTimeVerification(event.getTransactionId(),
                        "UNUSUAL_TIME_TRANSACTION");
                }
            }

            log.info("Unusual time ATM transaction: {} at {} ({})",
                    event.getTransactionId(), event.getTransactionTime(), event.getAtmId());

        } catch (Exception e) {
            log.error("Error handling unusual time transaction: {}", event.getTransactionId(), e);
            // Don't fail transaction for unusual time handling errors
        }
    }

    private void calculateAtmFees(AtmTransactionEvent event,
                                AtmTransaction transaction,
                                AtmLocation atmLocation) {
        try {
            if (!CASH_TRANSACTIONS.contains(event.getTransactionType())) {
                // No fees for balance inquiries, etc.
                transaction.setAtmFee(BigDecimal.ZERO);
                return;
            }

            // Base ATM fee
            BigDecimal baseFee = atmService.getBaseFee(event.getTransactionType());

            // Network fee (if using another bank's ATM)
            BigDecimal networkFee = BigDecimal.ZERO;
            if (!atmService.isOwnAtm(event.getAtmId())) {
                networkFee = atmService.getNetworkFee(event.getTransactionType());
            }

            // International fee
            BigDecimal internationalFee = BigDecimal.ZERO;
            if (atmLocation.isInternational()) {
                internationalFee = atmService.getInternationalFee(event.getAmount());
            }

            BigDecimal totalFee = baseFee.add(networkFee).add(internationalFee);

            transaction.setBaseFee(baseFee);
            transaction.setNetworkFee(networkFee);
            transaction.setInternationalFee(internationalFee);
            transaction.setAtmFee(totalFee);

        } catch (Exception e) {
            log.error("Error calculating ATM fees: {}", event.getTransactionId(), e);
            throw new RuntimeException("Failed to calculate ATM fees", e);
        }
    }

    private void processAtmPayment(AtmTransactionEvent event, AtmTransaction transaction) {
        try {
            if (transaction.getRequiresVerification() != null && transaction.getRequiresVerification()) {
                // Put on hold for verification
                transaction.setStatus("PENDING_VERIFICATION");
                atmService.putOnHold(event.getTransactionId(), "VERIFICATION_REQUIRED");
            } else {
                // Process the ATM transaction
                String result = atmService.processTransaction(
                    event.getTransactionId(),
                    event.getTransactionType(),
                    event.getAmount(),
                    event.getAccountId(),
                    event.getAtmId()
                );

                transaction.setProcessingResult(result);
                transaction.setStatus("COMPLETED");
                transaction.setProcessedAt(LocalDateTime.now());
            }

        } catch (Exception e) {
            log.error("Error processing ATM payment: {}", event.getTransactionId(), e);
            transaction.setStatus("FAILED");
            transaction.setErrorMessage(e.getMessage());
            throw new RuntimeException("Failed to process ATM payment", e);
        }
    }

    private void updateAccountBalance(AtmTransactionEvent event, AtmTransaction transaction) {
        try {
            if ("COMPLETED".equals(transaction.getStatus())) {
                if ("WITHDRAWAL".equals(event.getTransactionType())) {
                    // Deduct amount and fees
                    BigDecimal totalDeduction = event.getAmount().add(transaction.getAtmFee());
                    atmService.debitAccount(event.getAccountId(), totalDeduction);
                } else if ("DEPOSIT".equals(event.getTransactionType())) {
                    // Credit the amount (fees may be deducted separately)
                    atmService.creditAccount(event.getAccountId(), event.getAmount());
                    if (transaction.getAtmFee().compareTo(BigDecimal.ZERO) > 0) {
                        atmService.debitAccount(event.getAccountId(), transaction.getAtmFee());
                    }
                }

                // Update balance in transaction record
                BigDecimal newBalance = atmService.getAccountBalance(event.getAccountId());
                transaction.setAccountBalance(newBalance);
            }

        } catch (Exception e) {
            log.error("Error updating account balance: {}", event.getTransactionId(), e);
            throw new RuntimeException("Failed to update account balance", e);
        }
    }

    private void sendAtmNotifications(AtmTransactionEvent event, AtmTransaction transaction) {
        try {
            // Send customer notification
            String notificationType = switch (transaction.getStatus()) {
                case "COMPLETED" -> "ATM_TRANSACTION_SUCCESS";
                case "PENDING_VERIFICATION" -> "ATM_TRANSACTION_PENDING";
                case "FRAUD_DETECTED" -> "ATM_FRAUD_ALERT";
                case "LIMIT_EXCEEDED" -> "ATM_LIMIT_EXCEEDED";
                default -> "ATM_TRANSACTION_UPDATE";
            };

            atmService.sendCustomerNotification(
                event.getCustomerId(), notificationType, transaction);

            // Send receipt if requested
            if (event.isReceiptRequested() && "COMPLETED".equals(transaction.getStatus())) {
                atmService.sendElectronicReceipt(event.getCustomerId(), transaction);
            }

        } catch (Exception e) {
            log.error("Error sending ATM notifications: {}", event.getTransactionId(), e);
            // Don't fail the transaction for notification errors
        }
    }

    private void updateAtmMetrics(AtmTransactionEvent event,
                                AtmTransaction transaction,
                                AtmLocation atmLocation) {
        try {
            // Update ATM usage statistics
            atmService.updateAtmStatistics(event.getAtmId(),
                event.getTransactionType(), transaction.getStatus());

            // Update location metrics
            atmService.updateLocationMetrics(atmLocation.getLocationId(),
                event.getTransactionType(), event.getAmount());

            // Update cash levels for withdrawals
            if ("WITHDRAWAL".equals(event.getTransactionType()) &&
                "COMPLETED".equals(transaction.getStatus())) {
                atmService.updateCashLevels(event.getAtmId(), event.getAmount());
            }

        } catch (Exception e) {
            log.error("Error updating ATM metrics: {}", event.getTransactionId(), e);
            // Don't fail the transaction for metrics update errors
        }
    }

    private boolean isAlreadyProcessed(String transactionId, String eventId) {
        return atmTransactionRepository.existsByTransactionIdAndEventId(transactionId, eventId);
    }

    private void validateEvent(AtmTransactionEvent event) {
        if (event.getTransactionId() == null || event.getTransactionId().trim().isEmpty()) {
            throw new IllegalArgumentException("Transaction ID cannot be null or empty");
        }
        if (event.getCustomerId() == null || event.getCustomerId().trim().isEmpty()) {
            throw new IllegalArgumentException("Customer ID cannot be null or empty");
        }
        if (event.getAccountId() == null || event.getAccountId().trim().isEmpty()) {
            throw new IllegalArgumentException("Account ID cannot be null or empty");
        }
        if (event.getAtmId() == null || event.getAtmId().trim().isEmpty()) {
            throw new IllegalArgumentException("ATM ID cannot be null or empty");
        }
        if (event.getAmount() == null || event.getAmount().compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Amount cannot be negative");
        }
        if (event.getTransactionType() == null || event.getTransactionType().trim().isEmpty()) {
            throw new IllegalArgumentException("Transaction type cannot be null or empty");
        }
    }

    @Override
    protected void handleProcessingError(String message, Exception error) {
        log.error("ATM transaction processing error: {}", message, error);
        // Additional error handling logic
    }

    @Override
    protected void logProcessingMetrics(String key, long processingTime) {
        log.debug("Processed ATM transaction event - Key: {}, Time: {}ms", key, processingTime);
    }
}