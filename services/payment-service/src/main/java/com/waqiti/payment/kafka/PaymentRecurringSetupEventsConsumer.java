package com.waqiti.payment.kafka;

import com.waqiti.common.events.PaymentRecurringSetupEvent;
import com.waqiti.common.events.MandateCreationEvent;
import com.waqiti.payment.domain.RecurringPaymentSetup;
import com.waqiti.payment.domain.RecurringPaymentStatus;
import com.waqiti.payment.domain.PaymentMandate;
import com.waqiti.payment.domain.MandateStatus;
import com.waqiti.payment.repository.RecurringPaymentSetupRepository;
import com.waqiti.payment.repository.PaymentMandateRepository;
import com.waqiti.payment.service.RecurringPaymentService;
import com.waqiti.payment.service.MandateService;
import com.waqiti.payment.service.ComplianceService;
import com.waqiti.payment.service.DocumentService;
import com.waqiti.payment.exception.RecurringSetupNotFoundException;
import com.waqiti.payment.metrics.RecurringPaymentMetricsService;
import com.waqiti.common.audit.AuditService;
import com.waqiti.common.notification.NotificationService;
import com.waqiti.common.security.SecurityContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.retrytopic.TopicSuffixingStrategy;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;

import java.time.LocalDateTime;
import java.time.Instant;
import java.util.*;

@Component
@Slf4j
@RequiredArgsConstructor
public class PaymentRecurringSetupEventsConsumer {
    
    private final RecurringPaymentSetupRepository recurringSetupRepository;
    private final PaymentMandateRepository mandateRepository;
    private final RecurringPaymentService recurringPaymentService;
    private final MandateService mandateService;
    private final ComplianceService complianceService;
    private final DocumentService documentService;
    private final RecurringPaymentMetricsService metricsService;
    private final AuditService auditService;
    private final NotificationService notificationService;
    private final SecurityContext securityContext;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    
    private static final int MANDATE_SIGNATURE_EXPIRY_HOURS = 48;
    private static final String[] SUPPORTED_MANDATE_TYPES = {"SEPA", "BACS", "ACH", "PAD"};
    
    @KafkaListener(
        topics = {"recurring-payment-setup-events", "mandate-creation-events", "sepa-mandate-events"},
        groupId = "recurring-setup-service-group",
        containerFactory = "criticalFinancialKafkaListenerContainerFactory",
        concurrency = "4"
    )
    @RetryableTopic(
        attempts = "4",
        backoff = @Backoff(delay = 2000, multiplier = 2.0, maxDelay = 25000),
        dltStrategy = org.springframework.kafka.retrytopic.DltStrategy.FAIL_ON_ERROR,
        topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE,
        include = {Exception.class}
    )
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public void handleRecurringSetupEvent(
            @Payload PaymentRecurringSetupEvent event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            @Header(value = KafkaHeaders.RECEIVED_TOPIC, required = false) String topic,
            Acknowledgment acknowledgment) {
        
        String correlationId = String.format("recurring-%s-p%d-o%d", 
            event.getSetupId(), partition, offset);
        
        log.info("Processing recurring setup event: setupId={}, eventType={}, correlation={}",
            event.getSetupId(), event.getEventType(), correlationId);
        
        try {
            securityContext.validateFinancialOperation(event.getSetupId(), "RECURRING_SETUP");
            validateRecurringSetupEvent(event);
            
            switch (event.getEventType()) {
                case SETUP_INITIATED:
                    processSetupInitiated(event, correlationId);
                    break;
                case MANDATE_CREATED:
                    processMandateCreated(event, correlationId);
                    break;
                case MANDATE_SIGNED:
                    processMandateSigned(event, correlationId);
                    break;
                case MANDATE_CANCELLED:
                    processMandateCancelled(event, correlationId);
                    break;
                case AUTHORIZATION_COMPLETED:
                    processAuthorizationCompleted(event, correlationId);
                    break;
                case SETUP_FAILED:
                    processSetupFailed(event, correlationId);
                    break;
                case COMPLIANCE_CHECK:
                    processComplianceCheck(event, correlationId);
                    break;
                case MANDATE_EXPIRED:
                    processMandateExpired(event, correlationId);
                    break;
                default:
                    log.warn("Unknown recurring setup event type: {}", event.getEventType());
                    break;
            }
            
            auditService.logFinancialEvent(
                "RECURRING_SETUP_EVENT_PROCESSED",
                event.getSetupId(),
                Map.of(
                    "eventType", event.getEventType(),
                    "userId", event.getUserId(),
                    "mandateType", event.getMandateType(),
                    "correlationId", correlationId,
                    "timestamp", Instant.now()
                )
            );
            
            acknowledgment.acknowledge();
            
        } catch (Exception e) {
            log.error("Failed to process recurring setup event: setupId={}, error={}",
                event.getSetupId(), e.getMessage(), e);
            
            handleRecurringSetupEventError(event, e, correlationId);
            acknowledgment.acknowledge();
        }
    }
    
    private void processSetupInitiated(PaymentRecurringSetupEvent event, String correlationId) {
        log.info("Processing setup initiated: setupId={}, userId={}, mandateType={}", 
            event.getSetupId(), event.getUserId(), event.getMandateType());
        
        RecurringPaymentSetup setup = RecurringPaymentSetup.builder()
            .id(event.getSetupId())
            .userId(event.getUserId())
            .mandateType(event.getMandateType())
            .paymentMethodId(event.getPaymentMethodId())
            .merchantId(event.getMerchantId())
            .status(RecurringPaymentStatus.INITIATED)
            .initiatedAt(LocalDateTime.now())
            .expiresAt(LocalDateTime.now().plusHours(MANDATE_SIGNATURE_EXPIRY_HOURS))
            .correlationId(correlationId)
            .build();
        
        recurringSetupRepository.save(setup);
        
        complianceService.performPreSetupChecks(
            event.getUserId(),
            event.getMandateType(),
            event.getPaymentMethodId()
        );
        
        notificationService.sendNotification(
            event.getUserId(),
            "Recurring Payment Setup Started",
            String.format("Please complete your %s mandate setup within 48 hours.",
                event.getMandateType()),
            event.getSetupId()
        );
        
        metricsService.recordSetupInitiated(event.getMandateType());
        
        log.info("Recurring setup initiated: setupId={}, expiresAt={}", 
            setup.getId(), setup.getExpiresAt());
    }
    
    private void processMandateCreated(PaymentRecurringSetupEvent event, String correlationId) {
        RecurringPaymentSetup setup = getRecurringSetup(event.getSetupId());
        
        log.info("Processing mandate created: setupId={}, mandateId={}, type={}", 
            setup.getId(), event.getMandateId(), event.getMandateType());
        
        PaymentMandate mandate = PaymentMandate.builder()
            .id(event.getMandateId())
            .setupId(setup.getId())
            .userId(setup.getUserId())
            .mandateType(event.getMandateType())
            .mandateReference(generateMandateReference(event))
            .creditorId(event.getCreditorId())
            .creditorName(event.getCreditorName())
            .debtorAccountNumber(maskAccountNumber(event.getDebtorAccountNumber()))
            .debtorName(event.getDebtorName())
            .debtorIban(event.getDebtorIban())
            .debtorBic(event.getDebtorBic())
            .maxAmount(event.getMaxAmount())
            .currency(event.getCurrency())
            .frequency(event.getFrequency())
            .status(MandateStatus.PENDING_SIGNATURE)
            .createdAt(LocalDateTime.now())
            .expiresAt(LocalDateTime.now().plusHours(MANDATE_SIGNATURE_EXPIRY_HOURS))
            .build();
        
        mandateRepository.save(mandate);
        
        setup.setMandateId(mandate.getId());
        setup.setMandateReference(mandate.getMandateReference());
        setup.setStatus(RecurringPaymentStatus.MANDATE_CREATED);
        recurringSetupRepository.save(setup);
        
        String signatureUrl = mandateService.generateSignatureUrl(mandate);
        
        notificationService.sendMandateSignatureRequest(
            setup.getUserId(),
            mandate,
            signatureUrl
        );
        
        metricsService.recordMandateCreated(event.getMandateType());
        
        log.info("Mandate created: mandateId={}, reference={}, signatureUrl={}", 
            mandate.getId(), mandate.getMandateReference(), signatureUrl);
    }
    
    private void processMandateSigned(PaymentRecurringSetupEvent event, String correlationId) {
        RecurringPaymentSetup setup = getRecurringSetup(event.getSetupId());
        PaymentMandate mandate = getMandate(setup.getMandateId());
        
        log.info("Processing mandate signed: setupId={}, mandateId={}, signatureMethod={}", 
            setup.getId(), mandate.getId(), event.getSignatureMethod());
        
        mandate.setStatus(MandateStatus.ACTIVE);
        mandate.setSignedAt(LocalDateTime.now());
        mandate.setSignatureMethod(event.getSignatureMethod());
        mandate.setSignatureIpAddress(event.getSignatureIpAddress());
        mandate.setSignatureUserAgent(event.getSignatureUserAgent());
        mandate.setActivatedAt(LocalDateTime.now());
        mandateRepository.save(mandate);
        
        String documentId = documentService.generateMandateDocument(mandate);
        mandate.setDocumentId(documentId);
        mandateRepository.save(mandate);
        
        setup.setStatus(RecurringPaymentStatus.ACTIVE);
        setup.setCompletedAt(LocalDateTime.now());
        recurringSetupRepository.save(setup);
        
        complianceService.recordMandateActivation(
            mandate.getId(),
            event.getUserId(),
            event.getSignatureIpAddress()
        );
        
        recurringPaymentService.enableRecurringPayments(
            setup.getUserId(),
            mandate.getId(),
            setup.getPaymentMethodId()
        );
        
        notificationService.sendNotification(
            setup.getUserId(),
            "Recurring Payment Setup Complete",
            String.format("Your %s mandate (%s) is now active. Document ID: %s",
                mandate.getMandateType(), mandate.getMandateReference(), documentId),
            setup.getId()
        );
        
        metricsService.recordMandateSigned(mandate.getMandateType());
        
        log.info("Mandate signed and activated: mandateId={}, reference={}, documentId={}", 
            mandate.getId(), mandate.getMandateReference(), documentId);
    }
    
    private void processMandateCancelled(PaymentRecurringSetupEvent event, String correlationId) {
        RecurringPaymentSetup setup = getRecurringSetup(event.getSetupId());
        
        log.info("Processing mandate cancellation: setupId={}, reason={}", 
            setup.getId(), event.getCancellationReason());
        
        if (setup.getMandateId() != null) {
            PaymentMandate mandate = getMandate(setup.getMandateId());
            mandate.setStatus(MandateStatus.CANCELLED);
            mandate.setCancellationReason(event.getCancellationReason());
            mandate.setCancelledAt(LocalDateTime.now());
            mandate.setCancelledBy(event.getCancelledBy());
            mandateRepository.save(mandate);
            
            recurringPaymentService.cancelPendingRecurringPayments(mandate.getId());
        }
        
        setup.setStatus(RecurringPaymentStatus.CANCELLED);
        setup.setCancellationReason(event.getCancellationReason());
        recurringSetupRepository.save(setup);
        
        notificationService.sendNotification(
            setup.getUserId(),
            "Recurring Payment Mandate Cancelled",
            String.format("Your mandate has been cancelled. Reason: %s",
                event.getCancellationReason()),
            setup.getId()
        );
        
        metricsService.recordMandateCancelled(setup.getMandateType(), event.getCancellationReason());
    }
    
    private void processAuthorizationCompleted(PaymentRecurringSetupEvent event, String correlationId) {
        RecurringPaymentSetup setup = getRecurringSetup(event.getSetupId());
        
        log.info("Processing authorization completed: setupId={}, authorizationId={}", 
            setup.getId(), event.getAuthorizationId());
        
        setup.setAuthorizationId(event.getAuthorizationId());
        setup.setAuthorizationCompletedAt(LocalDateTime.now());
        setup.setAuthorizationMethod(event.getAuthorizationMethod());
        
        if (setup.getMandateId() != null) {
            PaymentMandate mandate = getMandate(setup.getMandateId());
            mandate.setAuthorizationId(event.getAuthorizationId());
            mandate.setAuthorizationCompletedAt(LocalDateTime.now());
            mandateRepository.save(mandate);
        }
        
        recurringSetupRepository.save(setup);
        
        metricsService.recordAuthorizationCompleted(setup.getMandateType());
    }
    
    private void processSetupFailed(PaymentRecurringSetupEvent event, String correlationId) {
        RecurringPaymentSetup setup = getRecurringSetup(event.getSetupId());
        
        log.error("Processing setup failure: setupId={}, reason={}", 
            setup.getId(), event.getFailureReason());
        
        setup.setStatus(RecurringPaymentStatus.FAILED);
        setup.setFailureReason(event.getFailureReason());
        setup.setFailureCode(event.getFailureCode());
        setup.setFailedAt(LocalDateTime.now());
        recurringSetupRepository.save(setup);
        
        if (setup.getMandateId() != null) {
            PaymentMandate mandate = getMandate(setup.getMandateId());
            mandate.setStatus(MandateStatus.FAILED);
            mandate.setFailureReason(event.getFailureReason());
            mandateRepository.save(mandate);
        }
        
        notificationService.sendNotification(
            setup.getUserId(),
            "Recurring Payment Setup Failed",
            String.format("Your recurring payment setup failed. Reason: %s. Please try again.",
                event.getFailureReason()),
            setup.getId()
        );
        
        metricsService.recordSetupFailed(setup.getMandateType(), event.getFailureReason());
    }
    
    private void processComplianceCheck(PaymentRecurringSetupEvent event, String correlationId) {
        RecurringPaymentSetup setup = getRecurringSetup(event.getSetupId());
        
        log.info("Processing compliance check: setupId={}, checkType={}", 
            setup.getId(), event.getComplianceCheckType());
        
        boolean compliancePassed = complianceService.performComplianceCheck(
            setup.getUserId(),
            setup.getMandateType(),
            event.getComplianceCheckType(),
            setup.getPaymentMethodId()
        );
        
        setup.setComplianceCheckCompleted(true);
        setup.setComplianceCheckPassed(compliancePassed);
        setup.setComplianceCheckDate(LocalDateTime.now());
        
        if (!compliancePassed) {
            setup.setStatus(RecurringPaymentStatus.COMPLIANCE_FAILED);
            setup.setFailureReason("Compliance check failed: " + event.getComplianceCheckType());
            
            notificationService.sendNotification(
                setup.getUserId(),
                "Compliance Check Failed",
                "Your recurring payment setup requires additional verification.",
                setup.getId()
            );
        }
        
        recurringSetupRepository.save(setup);
        
        metricsService.recordComplianceCheck(
            setup.getMandateType(), 
            event.getComplianceCheckType(), 
            compliancePassed
        );
    }
    
    private void processMandateExpired(PaymentRecurringSetupEvent event, String correlationId) {
        RecurringPaymentSetup setup = getRecurringSetup(event.getSetupId());
        
        log.warn("Processing mandate expiry: setupId={}, mandateId={}", 
            setup.getId(), setup.getMandateId());
        
        if (setup.getMandateId() != null) {
            PaymentMandate mandate = getMandate(setup.getMandateId());
            mandate.setStatus(MandateStatus.EXPIRED);
            mandate.setExpiredAt(LocalDateTime.now());
            mandateRepository.save(mandate);
            
            recurringPaymentService.disableRecurringPayments(mandate.getId());
        }
        
        setup.setStatus(RecurringPaymentStatus.EXPIRED);
        recurringSetupRepository.save(setup);
        
        notificationService.sendNotification(
            setup.getUserId(),
            "Mandate Expired",
            "Your payment mandate has expired. Please create a new mandate to continue recurring payments.",
            setup.getId()
        );
        
        metricsService.recordMandateExpired(setup.getMandateType());
    }
    
    private String generateMandateReference(PaymentRecurringSetupEvent event) {
        String prefix = switch (event.getMandateType()) {
            case "SEPA" -> "SEPA";
            case "BACS" -> "BACS";
            case "ACH" -> "ACH";
            case "PAD" -> "PAD";
            default -> "MAND";
        };
        
        return String.format("%s-%s-%d", 
            prefix, 
            UUID.randomUUID().toString().substring(0, 8).toUpperCase(),
            System.currentTimeMillis() % 1000000
        );
    }
    
    private String maskAccountNumber(String accountNumber) {
        if (accountNumber == null || accountNumber.length() < 4) {
            return accountNumber;
        }
        
        int visibleChars = 4;
        String masked = "*".repeat(accountNumber.length() - visibleChars);
        String lastDigits = accountNumber.substring(accountNumber.length() - visibleChars);
        
        return masked + lastDigits;
    }
    
    private void validateRecurringSetupEvent(PaymentRecurringSetupEvent event) {
        if (event.getSetupId() == null || event.getSetupId().trim().isEmpty()) {
            throw new IllegalArgumentException("Setup ID is required");
        }
        
        if (event.getUserId() == null || event.getUserId().trim().isEmpty()) {
            throw new IllegalArgumentException("User ID is required");
        }
        
        if (event.getEventType() == null || event.getEventType().trim().isEmpty()) {
            throw new IllegalArgumentException("Event type is required");
        }
        
        if (event.getMandateType() != null && 
            !Arrays.asList(SUPPORTED_MANDATE_TYPES).contains(event.getMandateType())) {
            throw new IllegalArgumentException("Unsupported mandate type: " + event.getMandateType());
        }
        
        if (event.getTimestamp() == null) {
            throw new IllegalArgumentException("Timestamp is required");
        }
    }
    
    private RecurringPaymentSetup getRecurringSetup(String setupId) {
        return recurringSetupRepository.findById(setupId)
            .orElseThrow(() -> new RecurringSetupNotFoundException(
                "Recurring setup not found: " + setupId));
    }
    
    private PaymentMandate getMandate(String mandateId) {
        return mandateRepository.findById(mandateId)
            .orElseThrow(() -> new IllegalStateException(
                "Mandate not found: " + mandateId));
    }
    
    private void handleRecurringSetupEventError(PaymentRecurringSetupEvent event, Exception error, 
            String correlationId) {
        
        Map<String, Object> dlqPayload = Map.of(
            "originalEvent", event,
            "error", error.getMessage(),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        );
        
        kafkaTemplate.send("recurring-payment-setup-events-dlq", dlqPayload);
        
        notificationService.sendOperationalAlert(
            "Recurring Setup Event Processing Failed",
            String.format("Failed to process recurring setup event for setup %s: %s",
                event.getSetupId(), error.getMessage()),
            NotificationService.Priority.HIGH
        );
        
        metricsService.incrementSetupEventError(event.getEventType());
    }
}