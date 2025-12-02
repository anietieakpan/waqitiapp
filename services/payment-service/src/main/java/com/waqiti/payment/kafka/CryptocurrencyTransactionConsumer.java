package com.waqiti.payment.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.common.events.CryptocurrencyTransactionEvent;
import com.waqiti.common.kafka.BaseKafkaConsumer;
import com.waqiti.payment.service.CryptocurrencyService;
import com.waqiti.payment.service.BlockchainService;
import com.waqiti.payment.service.ComplianceService;
import com.waqiti.payment.model.CryptoTransaction;
import com.waqiti.payment.model.BlockchainVerification;
import com.waqiti.payment.repository.CryptoTransactionRepository;
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
 * Consumer for processing cryptocurrency transaction events.
 * Handles digital asset transactions, blockchain verification, and crypto compliance.
 */
@Slf4j
@Component
public class CryptocurrencyTransactionConsumer extends BaseKafkaConsumer<CryptocurrencyTransactionEvent> {

    private static final String TOPIC = "cryptocurrency-transaction-events";
    private static final Set<String> SUPPORTED_CRYPTOCURRENCIES = Set.of(
        "BTC", "ETH", "LTC", "BCH", "XRP", "ADA", "DOT", "LINK", "UNI", "AAVE"
    );
    private static final Set<String> HIGH_RISK_CRYPTOS = Set.of(
        "XMR", "ZEC", "DASH"  // Privacy coins
    );

    private final CryptocurrencyService cryptocurrencyService;
    private final BlockchainService blockchainService;
    private final ComplianceService complianceService;
    private final CryptoTransactionRepository cryptoTransactionRepository;

    // Metrics
    private final Counter processedCounter;
    private final Counter blockchainVerifiedCounter;
    private final Counter complianceFailedCounter;
    private final Timer processingTimer;

    @Autowired
    public CryptocurrencyTransactionConsumer(
            ObjectMapper objectMapper,
            MeterRegistry meterRegistry,
            CryptocurrencyService cryptocurrencyService,
            BlockchainService blockchainService,
            ComplianceService complianceService,
            CryptoTransactionRepository cryptoTransactionRepository) {
        super(objectMapper, TOPIC);
        this.cryptocurrencyService = cryptocurrencyService;
        this.blockchainService = blockchainService;
        this.complianceService = complianceService;
        this.cryptoTransactionRepository = cryptoTransactionRepository;

        this.processedCounter = Counter.builder("crypto_transaction_processed_total")
                .description("Total cryptocurrency transactions processed")
                .register(meterRegistry);
        this.blockchainVerifiedCounter = Counter.builder("blockchain_verified_total")
                .description("Total blockchain verifications completed")
                .register(meterRegistry);
        this.complianceFailedCounter = Counter.builder("crypto_compliance_failed_total")
                .description("Total crypto transactions failed compliance")
                .register(meterRegistry);
        this.processingTimer = Timer.builder("crypto_transaction_processing_duration")
                .description("Time taken to process crypto transactions")
                .register(meterRegistry);
    }

    @RetryableTopic(
        attempts = "3",
        backoff = @Backoff(delay = 1000, multiplier = 2.0),
        dltStrategy = org.springframework.kafka.retrytopic.DltStrategy.FAIL_ON_ERROR,
        include = {Exception.class}
    )
    @KafkaListener(topics = TOPIC, groupId = "payment-service-crypto-group")
    @Transactional
    public void consume(
            ConsumerRecord<String, String> record,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.RECEIVED_MESSAGE_KEY) String key,
            Acknowledgment ack) {

        Timer.Sample sample = Timer.start();

        try {
            log.info("Processing cryptocurrency transaction event - Key: {}, Partition: {}, Offset: {}",
                    key, partition, record.offset());

            CryptocurrencyTransactionEvent event = deserializeEvent(record.value(), CryptocurrencyTransactionEvent.class);

            // Validate required fields
            validateEvent(event);

            // Check for duplicate processing
            if (isAlreadyProcessed(event.getTransactionId(), event.getEventId())) {
                log.info("Crypto transaction already processed: {}", event.getTransactionId());
                ack.acknowledge();
                return;
            }

            // Process the cryptocurrency transaction
            processCryptocurrencyTransaction(event);

            processedCounter.increment();
            log.info("Successfully processed crypto transaction: {}", event.getTransactionId());

            ack.acknowledge();

        } catch (Exception e) {
            log.error("Error processing cryptocurrency transaction event: {}", record.value(), e);
            throw new RuntimeException("Failed to process cryptocurrency transaction event", e);
        } finally {
            processingTimer.stop(sample);
        }
    }

    private void processCryptocurrencyTransaction(CryptocurrencyTransactionEvent event) {
        try {
            // Create crypto transaction record
            CryptoTransaction transaction = createCryptoTransaction(event);

            // Verify cryptocurrency is supported
            if (!isSupportedCryptocurrency(event.getCryptocurrency())) {
                handleUnsupportedCrypto(event, transaction);
                return;
            }

            // Check for high-risk cryptocurrencies
            if (isHighRiskCrypto(event.getCryptocurrency())) {
                handleHighRiskCrypto(event, transaction);
            }

            // Perform blockchain verification
            BlockchainVerification verification = performBlockchainVerification(event);
            transaction.setBlockchainVerified(verification.isVerified());
            transaction.setBlockHash(verification.getBlockHash());
            transaction.setConfirmations(verification.getConfirmations());

            if (!verification.isVerified()) {
                handleVerificationFailure(event, transaction, verification);
                return;
            }

            blockchainVerifiedCounter.increment();

            // Perform crypto compliance checks
            boolean compliancePassed = performCryptoCompliance(event, transaction);
            if (!compliancePassed) {
                complianceFailedCounter.increment();
                return;
            }

            // Get current crypto rates
            BigDecimal cryptoRate = cryptocurrencyService.getCurrentRate(
                event.getCryptocurrency(), event.getFiatCurrency()
            );
            transaction.setCryptoRate(cryptoRate);
            transaction.setFiatEquivalent(event.getCryptoAmount().multiply(cryptoRate));

            // Calculate fees
            calculateCryptoFees(event, transaction);

            // Process the transaction
            processCryptoPayment(event, transaction);

            // Save the transaction
            cryptoTransactionRepository.save(transaction);

            // Send notifications
            sendCryptoNotifications(event, transaction);

            log.info("Processed crypto transaction: {} - {} {} (${} equivalent)",
                    event.getTransactionId(), event.getCryptoAmount(),
                    event.getCryptocurrency(), transaction.getFiatEquivalent());

        } catch (Exception e) {
            log.error("Error processing crypto transaction: {}", event.getTransactionId(), e);
            throw new RuntimeException("Failed to process crypto transaction", e);
        }
    }

    private CryptoTransaction createCryptoTransaction(CryptocurrencyTransactionEvent event) {
        return CryptoTransaction.builder()
                .transactionId(event.getTransactionId())
                .customerId(event.getCustomerId())
                .cryptocurrency(event.getCryptocurrency())
                .cryptoAmount(event.getCryptoAmount())
                .fiatCurrency(event.getFiatCurrency())
                .transactionType(event.getTransactionType())
                .sourceAddress(event.getSourceAddress())
                .destinationAddress(event.getDestinationAddress())
                .networkFee(event.getNetworkFee())
                .transactionHash(event.getTransactionHash())
                .blockHeight(event.getBlockHeight())
                .purpose(event.getPurpose())
                .createdAt(LocalDateTime.now())
                .status("PROCESSING")
                .build();
    }

    private boolean isSupportedCryptocurrency(String cryptocurrency) {
        return SUPPORTED_CRYPTOCURRENCIES.contains(cryptocurrency.toUpperCase());
    }

    private boolean isHighRiskCrypto(String cryptocurrency) {
        return HIGH_RISK_CRYPTOS.contains(cryptocurrency.toUpperCase());
    }

    private void handleUnsupportedCrypto(CryptocurrencyTransactionEvent event, CryptoTransaction transaction) {
        try {
            transaction.setStatus("REJECTED");
            transaction.setErrorMessage("Unsupported cryptocurrency: " + event.getCryptocurrency());

            // Send customer notification
            cryptocurrencyService.sendCustomerNotification(event.getCustomerId(),
                "CRYPTO_NOT_SUPPORTED",
                "The cryptocurrency " + event.getCryptocurrency() + " is not currently supported.");

            cryptoTransactionRepository.save(transaction);

            log.warn("Unsupported cryptocurrency transaction rejected: {} - {}",
                    event.getTransactionId(), event.getCryptocurrency());

        } catch (Exception e) {
            log.error("Error handling unsupported crypto: {}", event.getTransactionId(), e);
            throw new RuntimeException("Failed to handle unsupported crypto", e);
        }
    }

    private void handleHighRiskCrypto(CryptocurrencyTransactionEvent event, CryptoTransaction transaction) {
        try {
            transaction.setRiskLevel("HIGH");
            transaction.setRequiresApproval(true);

            // Create compliance case for privacy coins
            complianceService.createComplianceCase(event.getCustomerId(),
                event.getTransactionId(),
                "High-risk cryptocurrency transaction: " + event.getCryptocurrency());

            // Enhanced monitoring
            cryptocurrencyService.enableEnhancedMonitoring(event.getCustomerId());

            log.info("High-risk crypto transaction flagged: {} - {}",
                    event.getTransactionId(), event.getCryptocurrency());

        } catch (Exception e) {
            log.error("Error handling high-risk crypto: {}", event.getTransactionId(), e);
            throw new RuntimeException("Failed to handle high-risk crypto", e);
        }
    }

    private BlockchainVerification performBlockchainVerification(CryptocurrencyTransactionEvent event) {
        try {
            // Verify transaction on blockchain
            boolean isValid = blockchainService.verifyTransaction(
                event.getCryptocurrency(),
                event.getTransactionHash(),
                event.getSourceAddress(),
                event.getDestinationAddress(),
                event.getCryptoAmount()
            );

            // Get block information
            String blockHash = blockchainService.getBlockHash(
                event.getCryptocurrency(), event.getTransactionHash()
            );

            int confirmations = blockchainService.getConfirmations(
                event.getCryptocurrency(), event.getTransactionHash()
            );

            // Check if sufficient confirmations
            int requiredConfirmations = cryptocurrencyService.getRequiredConfirmations(event.getCryptocurrency());
            boolean sufficientConfirmations = confirmations >= requiredConfirmations;

            return BlockchainVerification.builder()
                    .verified(isValid && sufficientConfirmations)
                    .transactionValid(isValid)
                    .blockHash(blockHash)
                    .confirmations(confirmations)
                    .requiredConfirmations(requiredConfirmations)
                    .verificationTime(LocalDateTime.now())
                    .build();

        } catch (Exception e) {
            log.error("Error verifying blockchain transaction: {}", event.getTransactionId(), e);
            return BlockchainVerification.builder()
                    .verified(false)
                    .transactionValid(false)
                    .errorMessage("Blockchain verification failed: " + e.getMessage())
                    .verificationTime(LocalDateTime.now())
                    .build();
        }
    }

    private void handleVerificationFailure(CryptocurrencyTransactionEvent event,
                                         CryptoTransaction transaction,
                                         BlockchainVerification verification) {
        try {
            transaction.setStatus("VERIFICATION_FAILED");
            transaction.setErrorMessage("Blockchain verification failed: " + verification.getErrorMessage());

            // Create investigation case
            cryptocurrencyService.createInvestigationCase(event.getCustomerId(),
                event.getTransactionId(), "BLOCKCHAIN_VERIFICATION_FAILED");

            // Send alert
            cryptocurrencyService.sendSecurityAlert(event.getCustomerId(),
                event.getTransactionId(), "BLOCKCHAIN_VERIFICATION_FAILED");

            cryptoTransactionRepository.save(transaction);

            log.warn("Crypto transaction verification failed: {} - {}",
                    event.getTransactionId(), verification.getErrorMessage());

        } catch (Exception e) {
            log.error("Error handling verification failure: {}", event.getTransactionId(), e);
            throw new RuntimeException("Failed to handle verification failure", e);
        }
    }

    private boolean performCryptoCompliance(CryptocurrencyTransactionEvent event, CryptoTransaction transaction) {
        try {
            // Check address sanctions
            boolean sourceAddressClear = complianceService.checkCryptoAddressSanctions(
                event.getSourceAddress(), event.getCryptocurrency()
            );

            boolean destAddressClear = complianceService.checkCryptoAddressSanctions(
                event.getDestinationAddress(), event.getCryptocurrency()
            );

            // Check transaction limits
            boolean withinLimits = complianceService.checkCryptoTransactionLimits(
                event.getCustomerId(), event.getCryptoAmount(), event.getCryptocurrency()
            );

            // Check AML requirements
            boolean amlClear = complianceService.checkCryptoAml(
                event.getCustomerId(), transaction.getFiatEquivalent()
            );

            // Check travel rule compliance (for large amounts)
            boolean travelRuleCompliant = complianceService.checkTravelRuleCompliance(
                transaction.getFiatEquivalent(), event.getDestinationAddress()
            );

            boolean allPassed = sourceAddressClear && destAddressClear &&
                              withinLimits && amlClear && travelRuleCompliant;

            if (!allPassed) {
                transaction.setStatus("COMPLIANCE_FAILED");
                StringBuilder notes = new StringBuilder();
                if (!sourceAddressClear) notes.append("Source address sanctioned; ");
                if (!destAddressClear) notes.append("Destination address sanctioned; ");
                if (!withinLimits) notes.append("Transaction limits exceeded; ");
                if (!amlClear) notes.append("AML concerns; ");
                if (!travelRuleCompliant) notes.append("Travel rule violation; ");

                transaction.setComplianceNotes(notes.toString());

                // Handle compliance failure
                handleCryptoComplianceFailure(event, transaction);
            }

            return allPassed;

        } catch (Exception e) {
            log.error("Error performing crypto compliance checks: {}", event.getTransactionId(), e);
            transaction.setStatus("COMPLIANCE_ERROR");
            transaction.setComplianceNotes("Compliance check failed due to system error");
            return false;
        }
    }

    private void handleCryptoComplianceFailure(CryptocurrencyTransactionEvent event, CryptoTransaction transaction) {
        try {
            // Block the transaction
            cryptocurrencyService.blockTransaction(event.getTransactionId(),
                "CRYPTO_COMPLIANCE_FAILURE: " + transaction.getComplianceNotes());

            // Create compliance case
            complianceService.createComplianceCase(event.getCustomerId(),
                event.getTransactionId(), "Crypto compliance failure: " + transaction.getComplianceNotes());

            // Send alerts
            complianceService.sendComplianceAlert(event.getCustomerId(),
                event.getTransactionId(), "CRYPTO_COMPLIANCE_FAILURE");

            // Notify customer
            cryptocurrencyService.sendCustomerNotification(event.getCustomerId(),
                "CRYPTO_TRANSACTION_BLOCKED",
                "Your cryptocurrency transaction has been blocked due to compliance requirements.");

            cryptoTransactionRepository.save(transaction);

            log.warn("Crypto transaction failed compliance: {} - {}",
                    event.getTransactionId(), transaction.getComplianceNotes());

        } catch (Exception e) {
            log.error("Error handling crypto compliance failure: {}", event.getTransactionId(), e);
            throw new RuntimeException("Failed to handle crypto compliance failure", e);
        }
    }

    private void calculateCryptoFees(CryptocurrencyTransactionEvent event, CryptoTransaction transaction) {
        try {
            // Platform fee
            BigDecimal platformFee = cryptocurrencyService.calculatePlatformFee(
                transaction.getFiatEquivalent(), event.getCryptocurrency()
            );

            // Network fee (already included in event)
            BigDecimal networkFee = event.getNetworkFee();

            // Exchange fee (if crypto-to-fiat conversion)
            BigDecimal exchangeFee = BigDecimal.ZERO;
            if ("SELL".equals(event.getTransactionType())) {
                exchangeFee = cryptocurrencyService.calculateExchangeFee(
                    event.getCryptoAmount(), event.getCryptocurrency()
                );
            }

            BigDecimal totalFees = platformFee.add(networkFee).add(exchangeFee);

            transaction.setPlatformFee(platformFee);
            transaction.setExchangeFee(exchangeFee);
            transaction.setTotalFees(totalFees);

        } catch (Exception e) {
            log.error("Error calculating crypto fees: {}", event.getTransactionId(), e);
            throw new RuntimeException("Failed to calculate crypto fees", e);
        }
    }

    private void processCryptoPayment(CryptocurrencyTransactionEvent event, CryptoTransaction transaction) {
        try {
            if (transaction.getRequiresApproval() != null && transaction.getRequiresApproval()) {
                // Put on hold for approval
                transaction.setStatus("PENDING_APPROVAL");
                cryptocurrencyService.putOnHold(event.getTransactionId(), "REQUIRES_APPROVAL");
            } else {
                // Process the crypto transaction
                String result = cryptocurrencyService.processTransaction(
                    event.getTransactionId(),
                    event.getTransactionType(),
                    event.getCryptocurrency(),
                    event.getCryptoAmount(),
                    event.getSourceAddress(),
                    event.getDestinationAddress()
                );

                transaction.setProcessingResult(result);
                transaction.setStatus("COMPLETED");
                transaction.setProcessedAt(LocalDateTime.now());
            }

        } catch (Exception e) {
            log.error("Error processing crypto payment: {}", event.getTransactionId(), e);
            transaction.setStatus("FAILED");
            transaction.setErrorMessage(e.getMessage());
            throw new RuntimeException("Failed to process crypto payment", e);
        }
    }

    private void sendCryptoNotifications(CryptocurrencyTransactionEvent event, CryptoTransaction transaction) {
        try {
            // Send customer notification
            String notificationType = switch (transaction.getStatus()) {
                case "COMPLETED" -> "CRYPTO_TRANSACTION_COMPLETED";
                case "PENDING_APPROVAL" -> "CRYPTO_TRANSACTION_PENDING";
                case "COMPLIANCE_FAILED" -> "CRYPTO_TRANSACTION_BLOCKED";
                default -> "CRYPTO_TRANSACTION_UPDATE";
            };

            cryptocurrencyService.sendCustomerNotification(
                event.getCustomerId(), notificationType, transaction);

            // Send regulatory reporting if required for large amounts
            if (transaction.getFiatEquivalent().compareTo(BigDecimal.valueOf(10000)) > 0) {
                complianceService.submitCryptoReport(event.getTransactionId(), transaction);
            }

        } catch (Exception e) {
            log.error("Error sending crypto notifications: {}", event.getTransactionId(), e);
            // Don't fail the transaction for notification errors
        }
    }

    private boolean isAlreadyProcessed(String transactionId, String eventId) {
        return cryptoTransactionRepository.existsByTransactionIdAndEventId(transactionId, eventId);
    }

    private void validateEvent(CryptocurrencyTransactionEvent event) {
        if (event.getTransactionId() == null || event.getTransactionId().trim().isEmpty()) {
            throw new IllegalArgumentException("Transaction ID cannot be null or empty");
        }
        if (event.getCustomerId() == null || event.getCustomerId().trim().isEmpty()) {
            throw new IllegalArgumentException("Customer ID cannot be null or empty");
        }
        if (event.getCryptocurrency() == null || event.getCryptocurrency().trim().isEmpty()) {
            throw new IllegalArgumentException("Cryptocurrency cannot be null or empty");
        }
        if (event.getCryptoAmount() == null || event.getCryptoAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Crypto amount must be positive");
        }
        if (event.getTransactionHash() == null || event.getTransactionHash().trim().isEmpty()) {
            throw new IllegalArgumentException("Transaction hash cannot be null or empty");
        }
    }

    @Override
    protected void handleProcessingError(String message, Exception error) {
        log.error("Cryptocurrency transaction processing error: {}", message, error);
        // Additional error handling logic
    }

    @Override
    protected void logProcessingMetrics(String key, long processingTime) {
        log.debug("Processed cryptocurrency transaction event - Key: {}, Time: {}ms", key, processingTime);
    }
}