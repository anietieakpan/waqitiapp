package com.waqiti.payment.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.common.events.MobilePaymentEvent;
import com.waqiti.common.kafka.BaseKafkaConsumer;
import com.waqiti.payment.service.MobilePaymentService;
import com.waqiti.payment.service.BiometricService;
import com.waqiti.payment.service.DeviceSecurityService;
import com.waqiti.payment.model.MobilePayment;
import com.waqiti.payment.model.DeviceProfile;
import com.waqiti.payment.repository.MobilePaymentRepository;
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
 * Consumer for processing mobile payment events.
 * Handles mobile wallet transactions, biometric verification, and device security.
 */
@Slf4j
@Component
public class MobilePaymentConsumer extends BaseKafkaConsumer<MobilePaymentEvent> {

    private static final String TOPIC = "mobile-payment-events";
    private static final Set<String> SUPPORTED_WALLETS = Set.of(
        "APPLE_PAY", "GOOGLE_PAY", "SAMSUNG_PAY", "WAQITI_WALLET"
    );
    private static final Set<String> BIOMETRIC_METHODS = Set.of(
        "FINGERPRINT", "FACE_ID", "VOICE_RECOGNITION", "IRIS_SCAN"
    );
    private static final BigDecimal HIGH_VALUE_THRESHOLD = BigDecimal.valueOf(500);

    private final MobilePaymentService mobilePaymentService;
    private final BiometricService biometricService;
    private final DeviceSecurityService deviceSecurityService;
    private final MobilePaymentRepository mobilePaymentRepository;

    // Metrics
    private final Counter processedCounter;
    private final Counter biometricVerifiedCounter;
    private final Counter deviceCompromisedCounter;
    private final Counter highValueTransactionCounter;
    private final Timer processingTimer;

    @Autowired
    public MobilePaymentConsumer(
            ObjectMapper objectMapper,
            MeterRegistry meterRegistry,
            MobilePaymentService mobilePaymentService,
            BiometricService biometricService,
            DeviceSecurityService deviceSecurityService,
            MobilePaymentRepository mobilePaymentRepository) {
        super(objectMapper, TOPIC);
        this.mobilePaymentService = mobilePaymentService;
        this.biometricService = biometricService;
        this.deviceSecurityService = deviceSecurityService;
        this.mobilePaymentRepository = mobilePaymentRepository;

        this.processedCounter = Counter.builder("mobile_payment_processed_total")
                .description("Total mobile payments processed")
                .register(meterRegistry);
        this.biometricVerifiedCounter = Counter.builder("biometric_verified_total")
                .description("Total biometric verifications completed")
                .register(meterRegistry);
        this.deviceCompromisedCounter = Counter.builder("device_compromised_total")
                .description("Total compromised devices detected")
                .register(meterRegistry);
        this.highValueTransactionCounter = Counter.builder("mobile_high_value_transaction_total")
                .description("Total high-value mobile transactions")
                .register(meterRegistry);
        this.processingTimer = Timer.builder("mobile_payment_processing_duration")
                .description("Time taken to process mobile payments")
                .register(meterRegistry);
    }

    @RetryableTopic(
        attempts = "3",
        backoff = @Backoff(delay = 1000, multiplier = 2.0),
        dltStrategy = org.springframework.kafka.retrytopic.DltStrategy.FAIL_ON_ERROR,
        include = {Exception.class}
    )
    @KafkaListener(topics = TOPIC, groupId = "payment-service-mobile-group")
    @Transactional
    public void consume(
            ConsumerRecord<String, String> record,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.RECEIVED_MESSAGE_KEY) String key,
            Acknowledgment ack) {

        Timer.Sample sample = Timer.start();

        try {
            log.info("Processing mobile payment event - Key: {}, Partition: {}, Offset: {}",
                    key, partition, record.offset());

            MobilePaymentEvent event = deserializeEvent(record.value(), MobilePaymentEvent.class);

            // Validate required fields
            validateEvent(event);

            // Check for duplicate processing
            if (isAlreadyProcessed(event.getTransactionId(), event.getEventId())) {
                log.info("Mobile payment already processed: {}", event.getTransactionId());
                ack.acknowledge();
                return;
            }

            // Process the mobile payment
            processMobilePayment(event);

            processedCounter.increment();
            log.info("Successfully processed mobile payment: {}", event.getTransactionId());

            ack.acknowledge();

        } catch (Exception e) {
            log.error("Error processing mobile payment event: {}", record.value(), e);
            throw new RuntimeException("Failed to process mobile payment event", e);
        } finally {
            sample.stop(processingTimer);
        }
    }

    private void processMobilePayment(MobilePaymentEvent event) {
        try {
            // Create mobile payment record
            MobilePayment payment = createMobilePayment(event);

            // Verify wallet is supported
            if (!isSupportedWallet(event.getWalletType())) {
                handleUnsupportedWallet(event, payment);
                return;
            }

            // Get device profile
            DeviceProfile deviceProfile = deviceSecurityService.getDeviceProfile(event.getDeviceId());
            payment.setDeviceProfile(deviceProfile);

            // Perform device security checks
            boolean deviceSecure = performDeviceSecurityChecks(event, payment, deviceProfile);
            if (!deviceSecure) {
                deviceCompromisedCounter.increment();
                return; // Transaction blocked
            }

            // Perform biometric verification if required
            boolean biometricVerified = performBiometricVerification(event, payment);
            if (!biometricVerified) {
                return; // Verification failed
            }

            biometricVerifiedCounter.increment();

            // Check for high-value transactions
            if (isHighValueTransaction(event)) {
                handleHighValueTransaction(event, payment);
                highValueTransactionCounter.increment();
            }

            // Perform mobile-specific fraud checks
            boolean fraudCheckPassed = performMobileFraudChecks(event, payment);
            if (!fraudCheckPassed) {
                return; // Transaction blocked
            }

            // Calculate mobile payment fees
            calculateMobilePaymentFees(event, payment);

            // Process the mobile payment
            processMobileTransaction(event, payment);

            // Update wallet balance
            updateWalletBalance(event, payment);

            // Save the payment
            mobilePaymentRepository.save(payment);

            // Send notifications
            sendMobilePaymentNotifications(event, payment);

            // Update device metrics
            updateDeviceMetrics(event, payment, deviceProfile);

            log.info("Processed mobile payment: {} via {} - ${} (Biometric: {})",
                    event.getTransactionId(), event.getWalletType(),
                    event.getAmount(), event.getBiometricMethod());

        } catch (Exception e) {
            log.error("Error processing mobile payment: {}", event.getTransactionId(), e);
            throw new RuntimeException("Failed to process mobile payment", e);
        }
    }

    private MobilePayment createMobilePayment(MobilePaymentEvent event) {
        return MobilePayment.builder()
                .transactionId(event.getTransactionId())
                .customerId(event.getCustomerId())
                .amount(event.getAmount())
                .currency(event.getCurrency())
                .walletType(event.getWalletType())
                .deviceId(event.getDeviceId())
                .deviceType(event.getDeviceType())
                .osVersion(event.getOsVersion())
                .appVersion(event.getAppVersion())
                .merchantId(event.getMerchantId())
                .merchantName(event.getMerchantName())
                .biometricMethod(event.getBiometricMethod())
                .biometricVerified(event.isBiometricVerified())
                .tokenized(event.isTokenized())
                .paymentToken(event.getPaymentToken())
                .nfcEnabled(event.isNfcEnabled())
                .location(event.getLocation())
                .transactionTime(event.getTransactionTime())
                .createdAt(LocalDateTime.now())
                .status("PROCESSING")
                .build();
    }

    private boolean isSupportedWallet(String walletType) {
        return SUPPORTED_WALLETS.contains(walletType.toUpperCase());
    }

    private void handleUnsupportedWallet(MobilePaymentEvent event, MobilePayment payment) {
        try {
            payment.setStatus("REJECTED");
            payment.setErrorMessage("Unsupported wallet type: " + event.getWalletType());

            // Send customer notification
            mobilePaymentService.sendCustomerNotification(event.getCustomerId(),
                "WALLET_NOT_SUPPORTED",
                "The wallet type " + event.getWalletType() + " is not currently supported.");

            mobilePaymentRepository.save(payment);

            log.warn("Unsupported wallet type rejected: {} - {}",
                    event.getTransactionId(), event.getWalletType());

        } catch (Exception e) {
            log.error("Error handling unsupported wallet: {}", event.getTransactionId(), e);
            throw new RuntimeException("Failed to handle unsupported wallet", e);
        }
    }

    private boolean performDeviceSecurityChecks(MobilePaymentEvent event,
                                              MobilePayment payment,
                                              DeviceProfile deviceProfile) {
        try {
            // Check if device is compromised
            boolean deviceSecure = deviceSecurityService.isDeviceSecure(event.getDeviceId());

            // Check for jailbreak/root
            boolean notJailbroken = deviceSecurityService.checkJailbreakStatus(
                event.getDeviceId(), event.getDeviceType()
            );

            // Check app integrity
            boolean appIntegrityOk = deviceSecurityService.checkAppIntegrity(
                event.getDeviceId(), event.getAppVersion()
            );

            // Check device reputation
            boolean goodReputation = deviceSecurityService.checkDeviceReputation(
                event.getDeviceId(), deviceProfile
            );

            // Check for malware
            boolean noMalware = deviceSecurityService.checkMalware(event.getDeviceId());

            boolean allPassed = deviceSecure && notJailbroken && appIntegrityOk &&
                              goodReputation && noMalware;

            if (!allPassed) {
                handleDeviceSecurityFailure(event, payment, deviceSecure, notJailbroken,
                    appIntegrityOk, goodReputation, noMalware);
            }

            return allPassed;

        } catch (Exception e) {
            log.error("Error performing device security checks: {}", event.getTransactionId(), e);
            payment.setStatus("SECURITY_CHECK_ERROR");
            return false;
        }
    }

    private void handleDeviceSecurityFailure(MobilePaymentEvent event,
                                           MobilePayment payment,
                                           boolean deviceSecure,
                                           boolean notJailbroken,
                                           boolean appIntegrityOk,
                                           boolean goodReputation,
                                           boolean noMalware) {
        try {
            payment.setStatus("DEVICE_SECURITY_FAILED");
            StringBuilder reasons = new StringBuilder();
            if (!deviceSecure) reasons.append("Device compromised; ");
            if (!notJailbroken) reasons.append("Jailbroken/Rooted device; ");
            if (!appIntegrityOk) reasons.append("App integrity failed; ");
            if (!goodReputation) reasons.append("Poor device reputation; ");
            if (!noMalware) reasons.append("Malware detected; ");

            payment.setSecurityFailureReasons(reasons.toString());

            // Block the transaction
            mobilePaymentService.blockTransaction(event.getTransactionId(),
                "DEVICE_SECURITY_FAILURE: " + reasons.toString());

            // Block the device if severely compromised
            if (!deviceSecure || !noMalware) {
                deviceSecurityService.blockDevice(event.getDeviceId(),
                    "SECURITY_COMPROMISE");
            }

            // Create security incident
            deviceSecurityService.createSecurityIncident(event.getCustomerId(),
                event.getDeviceId(), event.getTransactionId(), reasons.toString());

            // Send security alerts
            deviceSecurityService.sendSecurityAlert(event.getCustomerId(),
                event.getTransactionId(), "DEVICE_SECURITY_FAILURE");

            mobilePaymentRepository.save(payment);

            log.warn("Device security failure: {} on device {} - {}",
                    event.getTransactionId(), event.getDeviceId(), reasons.toString());

        } catch (Exception e) {
            log.error("Error handling device security failure: {}", event.getTransactionId(), e);
            throw new RuntimeException("Failed to handle device security failure", e);
        }
    }

    private boolean performBiometricVerification(MobilePaymentEvent event, MobilePayment payment) {
        try {
            if (!event.isBiometricVerified() || event.getBiometricMethod() == null) {
                // No biometric verification attempted
                payment.setBiometricVerificationResult("NOT_ATTEMPTED");
                return true; // Allow non-biometric transactions for now
            }

            // Verify biometric method is supported
            if (!BIOMETRIC_METHODS.contains(event.getBiometricMethod().toUpperCase())) {
                payment.setBiometricVerificationResult("UNSUPPORTED_METHOD");
                handleBiometricFailure(event, payment, "Unsupported biometric method");
                return false;
            }

            // Perform biometric verification
            boolean verified = biometricService.verifyBiometric(
                event.getCustomerId(),
                event.getDeviceId(),
                event.getBiometricMethod(),
                event.getBiometricData()
            );

            payment.setBiometricVerificationResult(verified ? "VERIFIED" : "FAILED");

            if (!verified) {
                handleBiometricFailure(event, payment, "Biometric verification failed");
                return false;
            }

            // Update biometric profile
            biometricService.updateBiometricProfile(event.getCustomerId(),
                event.getDeviceId(), event.getBiometricMethod());

            return true;

        } catch (Exception e) {
            log.error("Error performing biometric verification: {}", event.getTransactionId(), e);
            payment.setBiometricVerificationResult("ERROR");
            handleBiometricFailure(event, payment, "Biometric verification error: " + e.getMessage());
            return false;
        }
    }

    private void handleBiometricFailure(MobilePaymentEvent event, MobilePayment payment, String reason) {
        try {
            payment.setStatus("BIOMETRIC_FAILED");
            payment.setBiometricFailureReason(reason);

            // Block the transaction
            mobilePaymentService.blockTransaction(event.getTransactionId(),
                "BIOMETRIC_VERIFICATION_FAILED: " + reason);

            // Create security incident for repeated failures
            if (biometricService.hasRepeatedFailures(event.getCustomerId(), event.getDeviceId())) {
                biometricService.createSecurityIncident(event.getCustomerId(),
                    event.getDeviceId(), "REPEATED_BIOMETRIC_FAILURES");

                // Temporarily block biometric payments
                biometricService.suspendBiometricPayments(event.getCustomerId(),
                    event.getDeviceId(), "REPEATED_FAILURES");
            }

            // Send customer notification
            mobilePaymentService.sendCustomerNotification(event.getCustomerId(),
                "BIOMETRIC_VERIFICATION_FAILED", payment);

            mobilePaymentRepository.save(payment);

            log.warn("Biometric verification failed: {} - {}",
                    event.getTransactionId(), reason);

        } catch (Exception e) {
            log.error("Error handling biometric failure: {}", event.getTransactionId(), e);
            throw new RuntimeException("Failed to handle biometric failure", e);
        }
    }

    private boolean isHighValueTransaction(MobilePaymentEvent event) {
        return event.getAmount().compareTo(HIGH_VALUE_THRESHOLD) > 0;
    }

    private void handleHighValueTransaction(MobilePaymentEvent event, MobilePayment payment) {
        try {
            payment.setHighValue(true);

            // Enhanced verification for high-value mobile payments
            payment.setRequiresAdditionalAuth(true);

            // Check daily mobile payment limits
            boolean withinLimits = mobilePaymentService.checkDailyMobilePaymentLimits(
                event.getCustomerId(), event.getAmount()
            );

            if (!withinLimits) {
                payment.setStatus("LIMIT_EXCEEDED");
                mobilePaymentService.blockTransaction(event.getTransactionId(),
                    "DAILY_MOBILE_PAYMENT_LIMIT_EXCEEDED");

                mobilePaymentService.sendCustomerNotification(event.getCustomerId(),
                    "MOBILE_PAYMENT_LIMIT_EXCEEDED", payment);
                return;
            }

            // Send high-value transaction alert
            mobilePaymentService.sendSecurityNotification(event.getCustomerId(),
                "HIGH_VALUE_MOBILE_PAYMENT", payment);

            log.info("High-value mobile payment processed: {} - ${} via {}",
                    event.getTransactionId(), event.getAmount(), event.getWalletType());

        } catch (Exception e) {
            log.error("Error handling high-value transaction: {}", event.getTransactionId(), e);
            throw new RuntimeException("Failed to handle high-value transaction", e);
        }
    }

    private boolean performMobileFraudChecks(MobilePaymentEvent event, MobilePayment payment) {
        try {
            // Check for device velocity fraud
            boolean velocityOk = mobilePaymentService.checkDeviceVelocity(
                event.getDeviceId(), event.getAmount()
            );

            // Check for location anomalies
            boolean locationOk = mobilePaymentService.checkLocationAnomaly(
                event.getCustomerId(), event.getLocation(), event.getTransactionTime()
            );

            // Check for duplicate transactions
            boolean noDuplicates = mobilePaymentService.checkDuplicateTransaction(
                event.getCustomerId(), event.getMerchantId(),
                event.getAmount(), event.getTransactionTime()
            );

            // Check for suspicious app behavior
            boolean appBehaviorOk = mobilePaymentService.checkAppBehavior(
                event.getDeviceId(), event.getAppVersion()
            );

            boolean allPassed = velocityOk && locationOk && noDuplicates && appBehaviorOk;

            if (!allPassed) {
                handleMobileFraudDetection(event, payment, velocityOk, locationOk,
                    noDuplicates, appBehaviorOk);
            }

            return allPassed;

        } catch (Exception e) {
            log.error("Error performing mobile fraud checks: {}", event.getTransactionId(), e);
            payment.setStatus("FRAUD_CHECK_ERROR");
            return false;
        }
    }

    private void handleMobileFraudDetection(MobilePaymentEvent event,
                                          MobilePayment payment,
                                          boolean velocityOk,
                                          boolean locationOk,
                                          boolean noDuplicates,
                                          boolean appBehaviorOk) {
        try {
            payment.setStatus("FRAUD_DETECTED");
            StringBuilder reasons = new StringBuilder();
            if (!velocityOk) reasons.append("Device velocity breach; ");
            if (!locationOk) reasons.append("Location anomaly; ");
            if (!noDuplicates) reasons.append("Duplicate transaction; ");
            if (!appBehaviorOk) reasons.append("Suspicious app behavior; ");

            payment.setFraudReasons(reasons.toString());

            // Block the transaction
            mobilePaymentService.blockTransaction(event.getTransactionId(),
                "MOBILE_FRAUD_DETECTED: " + reasons.toString());

            // Create fraud investigation
            mobilePaymentService.createMobileFraudCase(event.getCustomerId(),
                event.getTransactionId(), event.getDeviceId(), reasons.toString());

            // Send fraud alerts
            mobilePaymentService.sendMobileFraudAlert(event.getCustomerId(),
                event.getTransactionId(), "MOBILE_FRAUD_DETECTED");

            mobilePaymentRepository.save(payment);

            log.warn("Mobile fraud detected: {} on device {} - {}",
                    event.getTransactionId(), event.getDeviceId(), reasons.toString());

        } catch (Exception e) {
            log.error("Error handling mobile fraud detection: {}", event.getTransactionId(), e);
            throw new RuntimeException("Failed to handle mobile fraud detection", e);
        }
    }

    private void calculateMobilePaymentFees(MobilePaymentEvent event, MobilePayment payment) {
        try {
            // Base mobile payment fee
            BigDecimal baseFee = mobilePaymentService.getBaseMobileFee(event.getWalletType());

            // Biometric discount
            BigDecimal biometricDiscount = BigDecimal.ZERO;
            if (payment.isBiometricVerified()) {
                biometricDiscount = mobilePaymentService.getBiometricDiscount(event.getAmount());
            }

            // Tokenization fee/discount
            BigDecimal tokenizationAdjustment = BigDecimal.ZERO;
            if (event.isTokenized()) {
                tokenizationAdjustment = mobilePaymentService.getTokenizationDiscount(event.getAmount());
            }

            BigDecimal totalFee = baseFee.subtract(biometricDiscount).subtract(tokenizationAdjustment);
            if (totalFee.compareTo(BigDecimal.ZERO) < 0) {
                totalFee = BigDecimal.ZERO;
            }

            payment.setBaseFee(baseFee);
            payment.setBiometricDiscount(biometricDiscount);
            payment.setTokenizationAdjustment(tokenizationAdjustment);
            payment.setMobileFee(totalFee);

        } catch (Exception e) {
            log.error("Error calculating mobile payment fees: {}", event.getTransactionId(), e);
            throw new RuntimeException("Failed to calculate mobile payment fees", e);
        }
    }

    private void processMobileTransaction(MobilePaymentEvent event, MobilePayment payment) {
        try {
            if (payment.getRequiresAdditionalAuth() != null && payment.getRequiresAdditionalAuth()) {
                // Put on hold for additional authentication
                payment.setStatus("PENDING_AUTH");
                mobilePaymentService.putOnHold(event.getTransactionId(), "ADDITIONAL_AUTH_REQUIRED");
            } else {
                // Process the mobile payment
                String result = mobilePaymentService.processPayment(
                    event.getTransactionId(),
                    event.getWalletType(),
                    event.getAmount(),
                    event.getMerchantId(),
                    event.getPaymentToken()
                );

                payment.setProcessingResult(result);
                payment.setStatus("COMPLETED");
                payment.setProcessedAt(LocalDateTime.now());
            }

        } catch (Exception e) {
            log.error("Error processing mobile transaction: {}", event.getTransactionId(), e);
            payment.setStatus("FAILED");
            payment.setErrorMessage(e.getMessage());
            throw new RuntimeException("Failed to process mobile transaction", e);
        }
    }

    private void updateWalletBalance(MobilePaymentEvent event, MobilePayment payment) {
        try {
            if ("COMPLETED".equals(payment.getStatus())) {
                // Update wallet balance
                BigDecimal totalAmount = event.getAmount().add(payment.getMobileFee());
                mobilePaymentService.updateWalletBalance(
                    event.getCustomerId(), event.getWalletType(), totalAmount.negate()
                );

                // Update balance in payment record
                BigDecimal newBalance = mobilePaymentService.getWalletBalance(
                    event.getCustomerId(), event.getWalletType()
                );
                payment.setWalletBalance(newBalance);
            }

        } catch (Exception e) {
            log.error("Error updating wallet balance: {}", event.getTransactionId(), e);
            throw new RuntimeException("Failed to update wallet balance", e);
        }
    }

    private void sendMobilePaymentNotifications(MobilePaymentEvent event, MobilePayment payment) {
        try {
            // Send customer notification
            String notificationType = switch (payment.getStatus()) {
                case "COMPLETED" -> "MOBILE_PAYMENT_SUCCESS";
                case "PENDING_AUTH" -> "MOBILE_PAYMENT_PENDING";
                case "FRAUD_DETECTED" -> "MOBILE_FRAUD_ALERT";
                case "DEVICE_SECURITY_FAILED" -> "DEVICE_SECURITY_ALERT";
                case "BIOMETRIC_FAILED" -> "BIOMETRIC_FAILURE_ALERT";
                default -> "MOBILE_PAYMENT_UPDATE";
            };

            mobilePaymentService.sendCustomerNotification(
                event.getCustomerId(), notificationType, payment);

            // Send push notification to device
            if ("COMPLETED".equals(payment.getStatus())) {
                mobilePaymentService.sendPushNotification(event.getDeviceId(),
                    "Payment completed successfully", payment);
            }

        } catch (Exception e) {
            log.error("Error sending mobile payment notifications: {}", event.getTransactionId(), e);
            // Don't fail the transaction for notification errors
        }
    }

    private void updateDeviceMetrics(MobilePaymentEvent event,
                                   MobilePayment payment,
                                   DeviceProfile deviceProfile) {
        try {
            // Update device usage statistics
            deviceSecurityService.updateDeviceStatistics(event.getDeviceId(),
                payment.getStatus(), event.getWalletType());

            // Update biometric usage statistics
            if (payment.isBiometricVerified()) {
                biometricService.updateBiometricStatistics(event.getCustomerId(),
                    event.getDeviceId(), event.getBiometricMethod());
            }

            // Update device risk profile
            deviceSecurityService.updateDeviceRiskProfile(event.getDeviceId(),
                payment.getStatus(), event.getAmount());

        } catch (Exception e) {
            log.error("Error updating device metrics: {}", event.getTransactionId(), e);
            // Don't fail the transaction for metrics update errors
        }
    }

    private boolean isAlreadyProcessed(String transactionId, String eventId) {
        return mobilePaymentRepository.existsByTransactionIdAndEventId(transactionId, eventId);
    }

    private void validateEvent(MobilePaymentEvent event) {
        if (event.getTransactionId() == null || event.getTransactionId().trim().isEmpty()) {
            throw new IllegalArgumentException("Transaction ID cannot be null or empty");
        }
        if (event.getCustomerId() == null || event.getCustomerId().trim().isEmpty()) {
            throw new IllegalArgumentException("Customer ID cannot be null or empty");
        }
        if (event.getAmount() == null || event.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Amount must be positive");
        }
        if (event.getWalletType() == null || event.getWalletType().trim().isEmpty()) {
            throw new IllegalArgumentException("Wallet type cannot be null or empty");
        }
        if (event.getDeviceId() == null || event.getDeviceId().trim().isEmpty()) {
            throw new IllegalArgumentException("Device ID cannot be null or empty");
        }
    }

    @Override
    protected void handleProcessingError(String message, Exception error) {
        log.error("Mobile payment processing error: {}", message, error);
        // Additional error handling logic
    }

    @Override
    protected void logProcessingMetrics(String key, long processingTime) {
        log.debug("Processed mobile payment event - Key: {}, Time: {}ms", key, processingTime);
    }
}