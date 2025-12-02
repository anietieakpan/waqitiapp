package com.waqiti.payment.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.common.events.CrossBorderTransactionEvent;
import com.waqiti.common.kafka.BaseKafkaConsumer;
import com.waqiti.payment.service.CrossBorderPaymentService;
import com.waqiti.payment.service.ComplianceService;
import com.waqiti.payment.service.ExchangeRateService;
import com.waqiti.payment.model.CrossBorderTransaction;
import com.waqiti.payment.model.ComplianceCheck;
import com.waqiti.payment.repository.CrossBorderTransactionRepository;
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
import java.util.List;
import java.util.Set;

/**
 * Consumer for processing cross-border transaction events.
 * Handles international payment processing, compliance checks, and regulatory requirements.
 */
@Slf4j
@Component
public class CrossBorderTransactionConsumer extends BaseKafkaConsumer<CrossBorderTransactionEvent> {

    private static final String TOPIC = "cross-border-transaction-events";
    private static final Set<String> HIGH_RISK_COUNTRIES = Set.of(
        "IR", "KP", "SY", "CU", "MM", "BY", "VE", "AF"
    );
    private static final Set<String> RESTRICTED_CURRENCIES = Set.of(
        "IRR", "KPW", "SYP", "CUP", "MMK", "BYN", "VES", "AFN"
    );

    private final CrossBorderPaymentService crossBorderPaymentService;
    private final ComplianceService complianceService;
    private final ExchangeRateService exchangeRateService;
    private final CrossBorderTransactionRepository crossBorderTransactionRepository;

    // Metrics
    private final Counter processedCounter;
    private final Counter complianceFailedCounter;
    private final Counter highRiskCountryCounter;
    private final Timer processingTimer;

    @Autowired
    public CrossBorderTransactionConsumer(
            ObjectMapper objectMapper,
            MeterRegistry meterRegistry,
            CrossBorderPaymentService crossBorderPaymentService,
            ComplianceService complianceService,
            ExchangeRateService exchangeRateService,
            CrossBorderTransactionRepository crossBorderTransactionRepository) {
        super(objectMapper, TOPIC);
        this.crossBorderPaymentService = crossBorderPaymentService;
        this.complianceService = complianceService;
        this.exchangeRateService = exchangeRateService;
        this.crossBorderTransactionRepository = crossBorderTransactionRepository;

        this.processedCounter = Counter.builder("cross_border_transaction_processed_total")
                .description("Total cross-border transactions processed")
                .register(meterRegistry);
        this.complianceFailedCounter = Counter.builder("cross_border_compliance_failed_total")
                .description("Total cross-border transactions failed compliance")
                .register(meterRegistry);
        this.highRiskCountryCounter = Counter.builder("high_risk_country_transaction_total")
                .description("Total transactions to high risk countries")
                .register(meterRegistry);
        this.processingTimer = Timer.builder("cross_border_transaction_processing_duration")
                .description("Time taken to process cross-border transactions")
                .register(meterRegistry);
    }

    @RetryableTopic(
        attempts = "3",
        backoff = @Backoff(delay = 1000, multiplier = 2.0),
        dltStrategy = org.springframework.kafka.retrytopic.DltStrategy.FAIL_ON_ERROR,
        include = {Exception.class}
    )
    @KafkaListener(topics = TOPIC, groupId = "payment-service-cross-border-group")
    @Transactional
    public void consume(
            ConsumerRecord<String, String> record,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.RECEIVED_MESSAGE_KEY) String key,
            Acknowledgment ack) {

        Timer.Sample sample = Timer.start();

        try {
            log.info("Processing cross-border transaction event - Key: {}, Partition: {}, Offset: {}",
                    key, partition, record.offset());

            CrossBorderTransactionEvent event = deserializeEvent(record.value(), CrossBorderTransactionEvent.class);

            // Validate required fields
            validateEvent(event);

            // Check for duplicate processing
            if (isAlreadyProcessed(event.getTransactionId(), event.getEventId())) {
                log.info("Cross-border transaction already processed: {}", event.getTransactionId());
                ack.acknowledge();
                return;
            }

            // Process the cross-border transaction
            processCrossBorderTransaction(event);

            processedCounter.increment();
            log.info("Successfully processed cross-border transaction: {}", event.getTransactionId());

            ack.acknowledge();

        } catch (Exception e) {
            log.error("Error processing cross-border transaction event: {}", record.value(), e);
            throw new RuntimeException("Failed to process cross-border transaction event", e);
        } finally {
            processingTimer.stop(sample);
        }
    }

    private void processCrossBorderTransaction(CrossBorderTransactionEvent event) {
        try {
            // Create cross-border transaction record
            CrossBorderTransaction transaction = createCrossBorderTransaction(event);

            // Perform compliance checks
            ComplianceCheck complianceCheck = performComplianceChecks(event);
            transaction.setComplianceStatus(complianceCheck.getStatus());
            transaction.setComplianceNotes(complianceCheck.getNotes());

            if (!complianceCheck.isPassed()) {
                handleComplianceFailure(event, transaction, complianceCheck);
                complianceFailedCounter.increment();
                return;
            }

            // Check for high-risk countries
            if (isHighRiskCountry(event)) {
                handleHighRiskCountryTransaction(event, transaction);
                highRiskCountryCounter.increment();
            }

            // Get current exchange rates
            BigDecimal exchangeRate = exchangeRateService.getExchangeRate(
                event.getSourceCurrency(), event.getTargetCurrency()
            );
            transaction.setExchangeRate(exchangeRate);
            transaction.setTargetAmount(event.getSourceAmount().multiply(exchangeRate));

            // Calculate fees and charges
            calculateFeesAndCharges(event, transaction);

            // Process the payment
            processPayment(event, transaction);

            // Save the transaction
            crossBorderTransactionRepository.save(transaction);

            // Send notifications
            sendNotifications(event, transaction);

            log.info("Processed cross-border transaction: {} from {} to {}",
                    event.getTransactionId(), event.getSourceCountry(), event.getTargetCountry());

        } catch (Exception e) {
            log.error("Error processing cross-border transaction: {}", event.getTransactionId(), e);
            throw new RuntimeException("Failed to process cross-border transaction", e);
        }
    }

    private CrossBorderTransaction createCrossBorderTransaction(CrossBorderTransactionEvent event) {
        return CrossBorderTransaction.builder()
                .transactionId(event.getTransactionId())
                .customerId(event.getCustomerId())
                .sourceAmount(event.getSourceAmount())
                .sourceCurrency(event.getSourceCurrency())
                .targetCurrency(event.getTargetCurrency())
                .sourceCountry(event.getSourceCountry())
                .targetCountry(event.getTargetCountry())
                .beneficiaryName(event.getBeneficiaryName())
                .beneficiaryAccount(event.getBeneficiaryAccount())
                .beneficiaryBank(event.getBeneficiaryBank())
                .swiftCode(event.getSwiftCode())
                .purpose(event.getPurpose())
                .paymentMethod(event.getPaymentMethod())
                .urgencyLevel(event.getUrgencyLevel())
                .regulatoryReporting(event.getRequiredReporting())
                .createdAt(LocalDateTime.now())
                .status("PROCESSING")
                .build();
    }

    private ComplianceCheck performComplianceChecks(CrossBorderTransactionEvent event) {
        try {
            ComplianceCheck check = new ComplianceCheck();

            // OFAC sanctions screening
            boolean ofacClear = complianceService.checkOfacSanctions(
                event.getCustomerId(), event.getBeneficiaryName(), event.getTargetCountry()
            );

            // AML checks
            boolean amlClear = complianceService.performAmlCheck(
                event.getCustomerId(), event.getSourceAmount(), event.getTargetCountry()
            );

            // CTF (Counter Terrorism Financing) checks
            boolean ctfClear = complianceService.performCtfCheck(
                event.getCustomerId(), event.getPurpose(), event.getTargetCountry()
            );

            // Regulatory limits check
            boolean limitsOk = complianceService.checkRegulatoryLimits(
                event.getCustomerId(), event.getSourceAmount(), event.getSourceCurrency(),
                event.getTargetCountry()
            );

            // Currency restrictions check
            boolean currencyAllowed = !RESTRICTED_CURRENCIES.contains(event.getTargetCurrency());

            boolean allPassed = ofacClear && amlClear && ctfClear && limitsOk && currencyAllowed;

            check.setPassed(allPassed);
            check.setStatus(allPassed ? "PASSED" : "FAILED");
            check.setOfacClear(ofacClear);
            check.setAmlClear(amlClear);
            check.setCtfClear(ctfClear);
            check.setLimitsOk(limitsOk);
            check.setCurrencyAllowed(currencyAllowed);

            if (!allPassed) {
                StringBuilder notes = new StringBuilder();
                if (!ofacClear) notes.append("OFAC sanctions hit; ");
                if (!amlClear) notes.append("AML concerns; ");
                if (!ctfClear) notes.append("CTF concerns; ");
                if (!limitsOk) notes.append("Regulatory limits exceeded; ");
                if (!currencyAllowed) notes.append("Restricted currency; ");
                check.setNotes(notes.toString());
            }

            return check;

        } catch (Exception e) {
            log.error("Error performing compliance checks for transaction: {}", event.getTransactionId(), e);
            ComplianceCheck failedCheck = new ComplianceCheck();
            failedCheck.setPassed(false);
            failedCheck.setStatus("ERROR");
            failedCheck.setNotes("Compliance check failed due to system error");
            return failedCheck;
        }
    }

    private void handleComplianceFailure(CrossBorderTransactionEvent event,
                                       CrossBorderTransaction transaction,
                                       ComplianceCheck complianceCheck) {
        try {
            transaction.setStatus("COMPLIANCE_FAILED");

            // Block the transaction
            crossBorderPaymentService.blockTransaction(event.getTransactionId(),
                "COMPLIANCE_FAILURE: " + complianceCheck.getNotes());

            // Create compliance case
            complianceService.createComplianceCase(event.getCustomerId(),
                event.getTransactionId(), complianceCheck.getNotes());

            // Send alerts
            complianceService.sendComplianceAlert(event.getCustomerId(),
                event.getTransactionId(), "CROSS_BORDER_COMPLIANCE_FAILURE");

            // Notify customer
            crossBorderPaymentService.sendCustomerNotification(event.getCustomerId(),
                "TRANSACTION_BLOCKED", "Your cross-border transaction has been blocked due to compliance requirements.");

            log.warn("Cross-border transaction failed compliance: {} - {}",
                    event.getTransactionId(), complianceCheck.getNotes());

        } catch (Exception e) {
            log.error("Error handling compliance failure for transaction: {}", event.getTransactionId(), e);
            throw new RuntimeException("Failed to handle compliance failure", e);
        }
    }

    private boolean isHighRiskCountry(CrossBorderTransactionEvent event) {
        return HIGH_RISK_COUNTRIES.contains(event.getTargetCountry()) ||
               HIGH_RISK_COUNTRIES.contains(event.getSourceCountry());
    }

    private void handleHighRiskCountryTransaction(CrossBorderTransactionEvent event,
                                                CrossBorderTransaction transaction) {
        try {
            // Enhanced due diligence required
            transaction.setRequiresEdd(true);
            transaction.setPriority("HIGH");

            // Create investigation case
            complianceService.createInvestigationCase(event.getCustomerId(),
                event.getTransactionId(), "HIGH_RISK_COUNTRY", "HIGH");

            // Additional documentation may be required
            crossBorderPaymentService.requestAdditionalDocumentation(
                event.getCustomerId(), event.getTransactionId());

            log.info("High-risk country transaction flagged: {} to {}",
                    event.getTransactionId(), event.getTargetCountry());

        } catch (Exception e) {
            log.error("Error handling high-risk country transaction: {}", event.getTransactionId(), e);
            throw new RuntimeException("Failed to handle high-risk country transaction", e);
        }
    }

    private void calculateFeesAndCharges(CrossBorderTransactionEvent event,
                                       CrossBorderTransaction transaction) {
        try {
            BigDecimal baseFee = crossBorderPaymentService.calculateBaseFee(
                event.getSourceAmount(), event.getTargetCountry());

            BigDecimal exchangeFee = crossBorderPaymentService.calculateExchangeFee(
                event.getSourceAmount(), event.getSourceCurrency(), event.getTargetCurrency());

            BigDecimal urgencyFee = BigDecimal.ZERO;
            if ("URGENT".equals(event.getUrgencyLevel())) {
                urgencyFee = crossBorderPaymentService.calculateUrgencyFee(event.getSourceAmount());
            }

            BigDecimal totalFees = baseFee.add(exchangeFee).add(urgencyFee);

            transaction.setBaseFee(baseFee);
            transaction.setExchangeFee(exchangeFee);
            transaction.setUrgencyFee(urgencyFee);
            transaction.setTotalFees(totalFees);

        } catch (Exception e) {
            log.error("Error calculating fees for transaction: {}", event.getTransactionId(), e);
            throw new RuntimeException("Failed to calculate fees", e);
        }
    }

    private void processPayment(CrossBorderTransactionEvent event, CrossBorderTransaction transaction) {
        try {
            if (transaction.getRequiresEdd() != null && transaction.getRequiresEdd()) {
                // Put on hold for enhanced due diligence
                transaction.setStatus("PENDING_EDD");
                crossBorderPaymentService.putOnHold(event.getTransactionId(), "ENHANCED_DUE_DILIGENCE");
            } else {
                // Process the payment
                String paymentResult = crossBorderPaymentService.initiatePayment(
                    event.getTransactionId(),
                    event.getSourceAmount(),
                    transaction.getExchangeRate(),
                    event.getBeneficiaryAccount(),
                    event.getSwiftCode(),
                    event.getUrgencyLevel()
                );

                transaction.setPaymentReference(paymentResult);
                transaction.setStatus("SENT");
                transaction.setProcessedAt(LocalDateTime.now());
            }

        } catch (Exception e) {
            log.error("Error processing payment for transaction: {}", event.getTransactionId(), e);
            transaction.setStatus("FAILED");
            transaction.setErrorMessage(e.getMessage());
            throw new RuntimeException("Failed to process payment", e);
        }
    }

    private void sendNotifications(CrossBorderTransactionEvent event, CrossBorderTransaction transaction) {
        try {
            // Send customer notification
            String notificationType = transaction.getStatus().equals("SENT") ?
                "CROSS_BORDER_PAYMENT_SENT" : "CROSS_BORDER_PAYMENT_PENDING";

            crossBorderPaymentService.sendCustomerNotification(
                event.getCustomerId(), notificationType, transaction);

            // Send regulatory reporting if required
            if (event.getRequiredReporting() != null && !event.getRequiredReporting().isEmpty()) {
                for (String reportType : event.getRequiredReporting()) {
                    complianceService.submitRegulatoryReport(
                        reportType, event.getTransactionId(), transaction);
                }
            }

        } catch (Exception e) {
            log.error("Error sending notifications for transaction: {}", event.getTransactionId(), e);
            // Don't fail the transaction for notification errors
        }
    }

    private boolean isAlreadyProcessed(String transactionId, String eventId) {
        return crossBorderTransactionRepository.existsByTransactionIdAndEventId(transactionId, eventId);
    }

    private void validateEvent(CrossBorderTransactionEvent event) {
        if (event.getTransactionId() == null || event.getTransactionId().trim().isEmpty()) {
            throw new IllegalArgumentException("Transaction ID cannot be null or empty");
        }
        if (event.getCustomerId() == null || event.getCustomerId().trim().isEmpty()) {
            throw new IllegalArgumentException("Customer ID cannot be null or empty");
        }
        if (event.getSourceAmount() == null || event.getSourceAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Source amount must be positive");
        }
        if (event.getSourceCurrency() == null || event.getTargetCurrency() == null) {
            throw new IllegalArgumentException("Source and target currencies cannot be null");
        }
        if (event.getSourceCountry() == null || event.getTargetCountry() == null) {
            throw new IllegalArgumentException("Source and target countries cannot be null");
        }
        if (event.getBeneficiaryName() == null || event.getBeneficiaryName().trim().isEmpty()) {
            throw new IllegalArgumentException("Beneficiary name cannot be null or empty");
        }
    }

    @Override
    protected void handleProcessingError(String message, Exception error) {
        log.error("Cross-border transaction processing error: {}", message, error);
        // Additional error handling logic
    }

    @Override
    protected void logProcessingMetrics(String key, long processingTime) {
        log.debug("Processed cross-border transaction event - Key: {}, Time: {}ms", key, processingTime);
    }
}