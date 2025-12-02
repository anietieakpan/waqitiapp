package com.waqiti.payment.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.common.events.ContactlessPaymentEvent;
import com.waqiti.common.kafka.BaseKafkaConsumer;
import com.waqiti.payment.service.ContactlessPaymentService;
import com.waqiti.payment.service.NfcSecurityService;
import com.waqiti.payment.model.ContactlessPayment;
import com.waqiti.payment.repository.ContactlessPaymentRepository;
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

/**
 * Consumer for processing contactless payment events.
 * Handles NFC payments, tap-to-pay transactions, and contactless security.
 */
@Slf4j
@Component
public class ContactlessPaymentConsumer extends BaseKafkaConsumer<ContactlessPaymentEvent> {

    private static final String TOPIC = "contactless-payment-events";
    private static final BigDecimal CONTACTLESS_LIMIT = BigDecimal.valueOf(100); // Typical contactless limit

    private final ContactlessPaymentService contactlessPaymentService;
    private final NfcSecurityService nfcSecurityService;
    private final ContactlessPaymentRepository contactlessPaymentRepository;

    // Metrics
    private final Counter processedCounter;
    private final Counter limitExceededCounter;
    private final Counter nfcSecurityFailCounter;
    private final Timer processingTimer;

    @Autowired
    public ContactlessPaymentConsumer(
            ObjectMapper objectMapper,
            MeterRegistry meterRegistry,
            ContactlessPaymentService contactlessPaymentService,
            NfcSecurityService nfcSecurityService,
            ContactlessPaymentRepository contactlessPaymentRepository) {
        super(objectMapper, TOPIC);
        this.contactlessPaymentService = contactlessPaymentService;
        this.nfcSecurityService = nfcSecurityService;
        this.contactlessPaymentRepository = contactlessPaymentRepository;

        this.processedCounter = Counter.builder("contactless_payment_processed_total")
                .description("Total contactless payments processed")
                .register(meterRegistry);
        this.limitExceededCounter = Counter.builder("contactless_limit_exceeded_total")
                .description("Total contactless payments exceeding limits")
                .register(meterRegistry);
        this.nfcSecurityFailCounter = Counter.builder("nfc_security_fail_total")
                .description("Total NFC security failures")
                .register(meterRegistry);
        this.processingTimer = Timer.builder("contactless_payment_processing_duration")
                .description("Time taken to process contactless payments")
                .register(meterRegistry);
    }

    @RetryableTopic(
        attempts = "3",
        backoff = @Backoff(delay = 1000, multiplier = 2.0),
        dltStrategy = org.springframework.kafka.retrytopic.DltStrategy.FAIL_ON_ERROR,
        include = {Exception.class}
    )
    @KafkaListener(topics = TOPIC, groupId = "payment-service-contactless-group")
    @Transactional
    public void consume(
            ConsumerRecord<String, String> record,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.RECEIVED_MESSAGE_KEY) String key,
            Acknowledgment ack) {

        Timer.Sample sample = Timer.start();

        try {
            log.info("Processing contactless payment event - Key: {}, Partition: {}, Offset: {}",
                    key, partition, record.offset());

            ContactlessPaymentEvent event = deserializeEvent(record.value(), ContactlessPaymentEvent.class);

            // Validate required fields
            validateEvent(event);

            // Check for duplicate processing
            if (isAlreadyProcessed(event.getTransactionId(), event.getEventId())) {
                log.info("Contactless payment already processed: {}", event.getTransactionId());
                ack.acknowledge();
                return;
            }

            // Process the contactless payment
            processContactlessPayment(event);

            processedCounter.increment();
            log.info("Successfully processed contactless payment: {}", event.getTransactionId());

            ack.acknowledge();

        } catch (Exception e) {
            log.error("Error processing contactless payment event: {}", record.value(), e);
            throw new RuntimeException("Failed to process contactless payment event", e);
        } finally {
            processingTimer.stop(sample);
        }
    }

    private void processContactlessPayment(ContactlessPaymentEvent event) {
        try {
            // Create contactless payment record
            ContactlessPayment payment = createContactlessPayment(event);

            // Check contactless limits
            if (exceedsContactlessLimit(event)) {
                handleLimitExceeded(event, payment);
                limitExceededCounter.increment();
                return;
            }

            // Perform NFC security checks
            boolean nfcSecure = performNfcSecurityChecks(event, payment);
            if (!nfcSecure) {
                nfcSecurityFailCounter.increment();
                return;
            }

            // Process the payment
            contactlessPaymentService.processPayment(event.getTransactionId(),
                event.getAmount(), event.getMerchantId());

            payment.setStatus("COMPLETED");
            payment.setProcessedAt(LocalDateTime.now());

            // Save the payment
            contactlessPaymentRepository.save(payment);

            // Send notifications
            contactlessPaymentService.sendCustomerNotification(event.getCustomerId(),
                "CONTACTLESS_PAYMENT_SUCCESS", payment);

            log.info("Processed contactless payment: {} - ${} via {}",
                    event.getTransactionId(), event.getAmount(), event.getContactlessMethod());

        } catch (Exception e) {
            log.error("Error processing contactless payment: {}", event.getTransactionId(), e);
            throw new RuntimeException("Failed to process contactless payment", e);
        }
    }

    private ContactlessPayment createContactlessPayment(ContactlessPaymentEvent event) {
        return ContactlessPayment.builder()
                .transactionId(event.getTransactionId())
                .customerId(event.getCustomerId())
                .amount(event.getAmount())
                .currency(event.getCurrency())
                .contactlessMethod(event.getContactlessMethod())
                .nfcEnabled(event.isNfcEnabled())
                .terminalId(event.getTerminalId())
                .merchantId(event.getMerchantId())
                .transactionTime(event.getTransactionTime())
                .createdAt(LocalDateTime.now())
                .status("PROCESSING")
                .build();
    }

    private boolean exceedsContactlessLimit(ContactlessPaymentEvent event) {
        return event.getAmount().compareTo(CONTACTLESS_LIMIT) > 0;
    }

    private void handleLimitExceeded(ContactlessPaymentEvent event, ContactlessPayment payment) {
        try {
            payment.setStatus("LIMIT_EXCEEDED");
            payment.setErrorMessage("Amount exceeds contactless limit");

            contactlessPaymentService.blockTransaction(event.getTransactionId(),
                "CONTACTLESS_LIMIT_EXCEEDED");

            contactlessPaymentRepository.save(payment);

            log.warn("Contactless limit exceeded: {} - ${}",
                    event.getTransactionId(), event.getAmount());

        } catch (Exception e) {
            log.error("Error handling limit exceeded: {}", event.getTransactionId(), e);
            throw new RuntimeException("Failed to handle limit exceeded", e);
        }
    }

    private boolean performNfcSecurityChecks(ContactlessPaymentEvent event, ContactlessPayment payment) {
        try {
            boolean nfcSecure = nfcSecurityService.validateNfcTransaction(
                event.getTerminalId(), event.getTransactionId()
            );

            if (!nfcSecure) {
                payment.setStatus("NFC_SECURITY_FAILED");
                contactlessPaymentService.blockTransaction(event.getTransactionId(),
                    "NFC_SECURITY_FAILURE");
                contactlessPaymentRepository.save(payment);
            }

            return nfcSecure;

        } catch (Exception e) {
            log.error("Error performing NFC security checks: {}", event.getTransactionId(), e);
            return false;
        }
    }

    private boolean isAlreadyProcessed(String transactionId, String eventId) {
        return contactlessPaymentRepository.existsByTransactionIdAndEventId(transactionId, eventId);
    }

    private void validateEvent(ContactlessPaymentEvent event) {
        if (event.getTransactionId() == null || event.getTransactionId().trim().isEmpty()) {
            throw new IllegalArgumentException("Transaction ID cannot be null or empty");
        }
        if (event.getCustomerId() == null || event.getCustomerId().trim().isEmpty()) {
            throw new IllegalArgumentException("Customer ID cannot be null or empty");
        }
        if (event.getAmount() == null || event.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Amount must be positive");
        }
    }

    @Override
    protected void handleProcessingError(String message, Exception error) {
        log.error("Contactless payment processing error: {}", message, error);
    }

    @Override
    protected void logProcessingMetrics(String key, long processingTime) {
        log.debug("Processed contactless payment event - Key: {}, Time: {}ms", key, processingTime);
    }
}