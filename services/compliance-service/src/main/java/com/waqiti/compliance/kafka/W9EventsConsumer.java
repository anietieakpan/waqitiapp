package com.waqiti.compliance.kafka;

import com.waqiti.common.events.W9Event;
import com.waqiti.compliance.domain.W9Form;
import com.waqiti.compliance.domain.TINValidationResult;
import com.waqiti.compliance.repository.W9FormRepository;
import com.waqiti.compliance.repository.TINValidationRepository;
import com.waqiti.compliance.service.W9ValidationService;
import com.waqiti.compliance.service.TINMatchingService;
import com.waqiti.compliance.service.BackupWithholdingService;
import com.waqiti.compliance.metrics.ComplianceMetricsService;
import com.waqiti.common.audit.AuditService;
import com.waqiti.common.notification.NotificationService;
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
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.Instant;
import java.math.BigDecimal;
import java.util.*;

@Component
@Slf4j
@RequiredArgsConstructor
public class W9EventsConsumer {
    
    private final W9FormRepository w9FormRepository;
    private final TINValidationRepository tinValidationRepository;
    private final W9ValidationService w9ValidationService;
    private final TINMatchingService tinMatchingService;
    private final BackupWithholdingService backupWithholdingService;
    private final ComplianceMetricsService metricsService;
    private final AuditService auditService;
    private final NotificationService notificationService;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    
    private static final BigDecimal BACKUP_WITHHOLDING_RATE = new BigDecimal("0.24");
    private static final int TIN_VALIDATION_RETRY_DAYS = 7;
    
    @KafkaListener(
        topics = {"w9-events", "tax-form-w9-events", "tin-certification-events"},
        groupId = "compliance-w9-service-group",
        containerFactory = "criticalFinancialKafkaListenerContainerFactory",
        concurrency = "3"
    )
    @RetryableTopic(
        attempts = "3",
        backoff = @Backoff(delay = 2500, multiplier = 2.0, maxDelay = 25000),
        dltStrategy = org.springframework.kafka.retrytopic.DltStrategy.FAIL_ON_ERROR,
        topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE
    )
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public void handleW9Event(
            @Payload W9Event event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {
        
        String correlationId = String.format("w9-%s-p%d-o%d", 
            event.getUserId(), partition, offset);
        
        log.info("Processing W9 event: userId={}, type={}", 
            event.getUserId(), event.getEventType());
        
        try {
            switch (event.getEventType()) {
                case FORM_SUBMITTED:
                    processFormSubmitted(event, correlationId);
                    break;
                case TIN_VALIDATION_REQUESTED:
                    processTINValidationRequested(event, correlationId);
                    break;
                case TIN_VALIDATION_COMPLETED:
                    processTINValidationCompleted(event, correlationId);
                    break;
                case TIN_MISMATCH_DETECTED:
                    processTINMismatchDetected(event, correlationId);
                    break;
                case BACKUP_WITHHOLDING_STARTED:
                    processBackupWithholdingStarted(event, correlationId);
                    break;
                case BACKUP_WITHHOLDING_STOPPED:
                    processBackupWithholdingStopped(event, correlationId);
                    break;
                case FORM_UPDATED:
                    processFormUpdated(event, correlationId);
                    break;
                case CERTIFICATION_REQUIRED:
                    processCertificationRequired(event, correlationId);
                    break;
                case CERTIFICATION_RECEIVED:
                    processCertificationReceived(event, correlationId);
                    break;
                case B_NOTICE_RECEIVED:
                    processBNoticeReceived(event, correlationId);
                    break;
                default:
                    log.warn("Unknown W9 event type: {}", event.getEventType());
                    break;
            }
            
            auditService.logComplianceEvent(
                "W9_EVENT_PROCESSED",
                event.getUserId(),
                Map.of(
                    "eventType", event.getEventType(),
                    "formId", event.getFormId() != null ? event.getFormId() : "N/A",
                    "correlationId", correlationId,
                    "timestamp", Instant.now()
                )
            );
            
            acknowledgment.acknowledge();
            
        } catch (Exception e) {
            log.error("Failed to process W9 event: {}", e.getMessage(), e);
            kafkaTemplate.send("w9-events-dlq", Map.of(
                "originalEvent", event,
                "error", e.getMessage(),
                "correlationId", correlationId,
                "timestamp", Instant.now()
            ));
            acknowledgment.acknowledge();
        }
    }
    
    private void processFormSubmitted(W9Event event, String correlationId) {
        log.info("W9 form submitted: userId={}, entityType={}", 
            event.getUserId(), event.getEntityType());
        
        W9Form w9Form = W9Form.builder()
            .id(UUID.randomUUID().toString())
            .userId(event.getUserId())
            .name(event.getName())
            .businessName(event.getBusinessName())
            .entityType(event.getEntityType())
            .taxClassification(event.getTaxClassification())
            .tinType(event.getTinType())
            .tin(event.getTin())
            .address(event.getAddress())
            .certificationDate(LocalDateTime.now())
            .certifiedUnderPenaltyOfPerjury(true)
            .status("SUBMITTED")
            .submittedAt(LocalDateTime.now())
            .validationRequired(true)
            .correlationId(correlationId)
            .build();
        
        w9FormRepository.save(w9Form);
        
        w9ValidationService.validateForm(w9Form.getId());
        tinMatchingService.scheduleTINValidation(w9Form.getId());
        
        notificationService.sendNotification(
            event.getUserId(),
            "W-9 Form Received",
            "Your W-9 form has been received and is being processed.",
            correlationId
        );
        
        metricsService.recordW9FormSubmitted(event.getEntityType());
    }
    
    private void processTINValidationRequested(W9Event event, String correlationId) {
        log.info("TIN validation requested: formId={}, tin=***", event.getFormId());
        
        W9Form w9Form = w9FormRepository.findById(event.getFormId())
            .orElseThrow();
        
        w9Form.setStatus("VALIDATION_IN_PROGRESS");
        w9Form.setValidationRequestedAt(LocalDateTime.now());
        w9FormRepository.save(w9Form);
        
        tinMatchingService.submitTINValidation(w9Form.getId());
        metricsService.recordTINValidationRequested();
    }
    
    private void processTINValidationCompleted(W9Event event, String correlationId) {
        log.info("TIN validation completed: formId={}, result={}", 
            event.getFormId(), event.getValidationResult());
        
        W9Form w9Form = w9FormRepository.findById(event.getFormId())
            .orElseThrow();
        
        TINValidationResult validationResult = TINValidationResult.builder()
            .id(UUID.randomUUID().toString())
            .formId(event.getFormId())
            .validationDate(LocalDateTime.now())
            .result(event.getValidationResult())
            .nameMatch(event.isNameMatch())
            .tinMatch(event.isTinMatch())
            .validationSource("IRS_TIN_MATCHING")
            .correlationId(correlationId)
            .build();
        
        tinValidationRepository.save(validationResult);
        
        if ("MATCH".equals(event.getValidationResult())) {
            w9Form.setStatus("VALIDATED");
            w9Form.setValidatedAt(LocalDateTime.now());
            w9Form.setBackupWithholdingRequired(false);
            
            notificationService.sendNotification(
                event.getUserId(),
                "W-9 Validated",
                "Your tax information has been successfully validated.",
                correlationId
            );
        } else if ("MISMATCH".equals(event.getValidationResult())) {
            w9Form.setStatus("VALIDATION_FAILED");
            w9Form.setValidationFailedAt(LocalDateTime.now());
            w9Form.setRetryValidationDeadline(LocalDateTime.now().plusDays(TIN_VALIDATION_RETRY_DAYS));
            
            notificationService.sendNotification(
                event.getUserId(),
                "W-9 Validation Issue",
                "There is an issue with your tax information. Please review and update your W-9 form.",
                correlationId
            );
        }
        
        w9FormRepository.save(w9Form);
        metricsService.recordTINValidationCompleted(event.getValidationResult());
    }
    
    private void processTINMismatchDetected(W9Event event, String correlationId) {
        log.warn("TIN mismatch detected: formId={}, userId={}", 
            event.getFormId(), event.getUserId());
        
        W9Form w9Form = w9FormRepository.findById(event.getFormId())
            .orElseThrow();
        
        w9Form.setStatus("MISMATCH");
        w9Form.setMismatchDetectedAt(LocalDateTime.now());
        w9Form.setMismatchReason(event.getMismatchReason());
        w9Form.setCorrectionDeadline(LocalDateTime.now().plusDays(30));
        w9FormRepository.save(w9Form);
        
        notificationService.sendNotification(
            event.getUserId(),
            "Action Required: W-9 Mismatch",
            String.format("Your W-9 information does not match IRS records. Reason: %s. Please submit a corrected W-9 within 30 days to avoid backup withholding.", 
                event.getMismatchReason()),
            correlationId
        );
        
        metricsService.recordTINMismatch();
    }
    
    private void processBackupWithholdingStarted(W9Event event, String correlationId) {
        log.warn("Backup withholding started: userId={}, rate={}", 
            event.getUserId(), BACKUP_WITHHOLDING_RATE);
        
        W9Form w9Form = w9FormRepository.findById(event.getFormId())
            .orElseThrow();
        
        w9Form.setBackupWithholdingRequired(true);
        w9Form.setBackupWithholdingStartedAt(LocalDateTime.now());
        w9Form.setBackupWithholdingRate(BACKUP_WITHHOLDING_RATE);
        w9Form.setBackupWithholdingReason(event.getWithholdingReason());
        w9FormRepository.save(w9Form);
        
        backupWithholdingService.enableBackupWithholding(event.getUserId(), BACKUP_WITHHOLDING_RATE);
        
        notificationService.sendNotification(
            event.getUserId(),
            "Backup Withholding Started",
            String.format("Backup withholding at %s%% has been applied to your account due to: %s. Submit a validated W-9 to stop withholding.", 
                BACKUP_WITHHOLDING_RATE.multiply(new BigDecimal("100")), event.getWithholdingReason()),
            correlationId
        );
        
        metricsService.recordBackupWithholdingStarted();
    }
    
    private void processBackupWithholdingStopped(W9Event event, String correlationId) {
        log.info("Backup withholding stopped: userId={}", event.getUserId());
        
        W9Form w9Form = w9FormRepository.findById(event.getFormId())
            .orElseThrow();
        
        w9Form.setBackupWithholdingRequired(false);
        w9Form.setBackupWithholdingStoppedAt(LocalDateTime.now());
        w9FormRepository.save(w9Form);
        
        backupWithholdingService.disableBackupWithholding(event.getUserId());
        
        notificationService.sendNotification(
            event.getUserId(),
            "Backup Withholding Stopped",
            "Backup withholding has been removed from your account.",
            correlationId
        );
        
        metricsService.recordBackupWithholdingStopped();
    }
    
    private void processFormUpdated(W9Event event, String correlationId) {
        log.info("W9 form updated: formId={}, userId={}", 
            event.getFormId(), event.getUserId());
        
        W9Form w9Form = w9FormRepository.findById(event.getFormId())
            .orElseThrow();
        
        w9Form.setName(event.getName());
        w9Form.setBusinessName(event.getBusinessName());
        w9Form.setTin(event.getTin());
        w9Form.setAddress(event.getAddress());
        w9Form.setUpdatedAt(LocalDateTime.now());
        w9Form.setStatus("SUBMITTED");
        w9Form.setValidationRequired(true);
        w9FormRepository.save(w9Form);
        
        tinMatchingService.scheduleTINValidation(w9Form.getId());
        metricsService.recordW9FormUpdated();
    }
    
    private void processCertificationRequired(W9Event event, String correlationId) {
        log.info("W9 certification required: userId={}", event.getUserId());
        
        W9Form w9Form = w9FormRepository.findByUserId(event.getUserId())
            .orElse(W9Form.builder()
                .id(UUID.randomUUID().toString())
                .userId(event.getUserId())
                .status("CERTIFICATION_REQUIRED")
                .build());
        
        w9Form.setCertificationRequired(true);
        w9Form.setCertificationDeadline(LocalDateTime.now().plusDays(30));
        w9FormRepository.save(w9Form);
        
        notificationService.sendNotification(
            event.getUserId(),
            "W-9 Certification Required",
            "Please submit a W-9 form to certify your taxpayer information. This is required for tax reporting.",
            correlationId
        );
        
        metricsService.recordCertificationRequired();
    }
    
    private void processCertificationReceived(W9Event event, String correlationId) {
        log.info("W9 certification received: formId={}", event.getFormId());
        
        W9Form w9Form = w9FormRepository.findById(event.getFormId())
            .orElseThrow();
        
        w9Form.setCertificationRequired(false);
        w9Form.setCertificationDate(LocalDateTime.now());
        w9Form.setCertifiedUnderPenaltyOfPerjury(true);
        w9FormRepository.save(w9Form);
        
        metricsService.recordCertificationReceived();
    }
    
    private void processBNoticeReceived(W9Event event, String correlationId) {
        log.warn("B Notice received from IRS: userId={}, reason={}", 
            event.getUserId(), event.getBNoticeReason());
        
        W9Form w9Form = w9FormRepository.findByUserId(event.getUserId())
            .orElseThrow();
        
        w9Form.setStatus("B_NOTICE_RECEIVED");
        w9Form.setBNoticeReceivedAt(LocalDateTime.now());
        w9Form.setBNoticeReason(event.getBNoticeReason());
        w9Form.setCorrectionDeadline(LocalDateTime.now().plusDays(30));
        w9Form.setBackupWithholdingWarning(true);
        w9FormRepository.save(w9Form);
        
        notificationService.sendNotification(
            event.getUserId(),
            "Urgent: IRS B Notice Received",
            String.format("The IRS has notified us of an issue with your taxpayer information: %s. Please submit a corrected W-9 within 30 days to avoid backup withholding.", 
                event.getBNoticeReason()),
            correlationId
        );
        
        metricsService.recordBNoticeReceived();
    }
}