package com.waqiti.compliance.kafka;

import com.waqiti.common.events.RegEComplianceEvent;
import com.waqiti.compliance.domain.RegEDispute;
import com.waqiti.compliance.domain.RegEError;
import com.waqiti.compliance.domain.UnauthorizedTransactionClaim;
import com.waqiti.compliance.repository.RegEDisputeRepository;
import com.waqiti.compliance.repository.RegEErrorRepository;
import com.waqiti.compliance.repository.UnauthorizedTransactionRepository;
import com.waqiti.compliance.service.RegEComplianceService;
import com.waqiti.compliance.service.DisputeResolutionService;
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
import java.util.*;

@Component
@Slf4j
@RequiredArgsConstructor
public class RegEComplianceEventsConsumer {
    
    private final RegEDisputeRepository disputeRepository;
    private final RegEErrorRepository errorRepository;
    private final UnauthorizedTransactionRepository unauthorizedTransactionRepository;
    private final RegEComplianceService regEService;
    private final DisputeResolutionService disputeResolutionService;
    private final ComplianceMetricsService metricsService;
    private final AuditService auditService;
    private final NotificationService notificationService;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    
    private static final int ERROR_NOTICE_DAYS = 3;
    private static final int INVESTIGATION_DAYS = 10;
    private static final int EXTENDED_INVESTIGATION_DAYS = 45;
    private static final int PROVISIONAL_CREDIT_DAYS = 10;
    
    @KafkaListener(
        topics = {"reg-e-compliance-events", "electronic-fund-transfer-disputes", "eft-error-resolution"},
        groupId = "compliance-rege-service-group",
        containerFactory = "criticalFinancialKafkaListenerContainerFactory",
        concurrency = "4"
    )
    @RetryableTopic(
        attempts = "4",
        backoff = @Backoff(delay = 2500, multiplier = 2.0, maxDelay = 25000),
        dltStrategy = org.springframework.kafka.retrytopic.DltStrategy.FAIL_ON_ERROR,
        topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE
    )
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public void handleRegEComplianceEvent(
            @Payload RegEComplianceEvent event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {
        
        String correlationId = String.format("rege-%s-p%d-o%d", 
            event.getEventId(), partition, offset);
        
        log.info("Processing Reg E compliance event: type={}, userId={}", 
            event.getEventType(), event.getUserId());
        
        try {
            switch (event.getEventType()) {
                case ERROR_REPORTED:
                    processErrorReported(event, correlationId);
                    break;
                case UNAUTHORIZED_TRANSACTION_REPORTED:
                    processUnauthorizedTransactionReported(event, correlationId);
                    break;
                case DISPUTE_INITIATED:
                    processDisputeInitiated(event, correlationId);
                    break;
                case INVESTIGATION_STARTED:
                    processInvestigationStarted(event, correlationId);
                    break;
                case PROVISIONAL_CREDIT_ISSUED:
                    processProvisionalCreditIssued(event, correlationId);
                    break;
                case INVESTIGATION_COMPLETED:
                    processInvestigationCompleted(event, correlationId);
                    break;
                case DISPUTE_RESOLVED:
                    processDisputeResolved(event, correlationId);
                    break;
                case ERROR_CORRECTED:
                    processErrorCorrected(event, correlationId);
                    break;
                case PERIODIC_STATEMENT_ERROR:
                    processPeriodicStatementError(event, correlationId);
                    break;
                case PREAUTHORIZED_TRANSFER_ERROR:
                    processPreauthorizedTransferError(event, correlationId);
                    break;
                default:
                    log.warn("Unknown Reg E event type: {}", event.getEventType());
                    break;
            }
            
            auditService.logComplianceEvent(
                "REG_E_EVENT_PROCESSED",
                event.getEventId(),
                Map.of(
                    "eventType", event.getEventType(),
                    "userId", event.getUserId(),
                    "correlationId", correlationId,
                    "timestamp", Instant.now()
                )
            );
            
            acknowledgment.acknowledge();
            
        } catch (Exception e) {
            log.error("Failed to process Reg E event: {}", e.getMessage(), e);
            kafkaTemplate.send("reg-e-compliance-events-dlq", Map.of(
                "originalEvent", event,
                "error", e.getMessage(),
                "correlationId", correlationId,
                "timestamp", Instant.now()
            ));
            acknowledgment.acknowledge();
        }
    }
    
    private void processErrorReported(RegEComplianceEvent event, String correlationId) {
        log.info("EFT error reported: userId={}, transactionId={}", 
            event.getUserId(), event.getTransactionId());
        
        RegEError error = RegEError.builder()
            .id(UUID.randomUUID().toString())
            .userId(event.getUserId())
            .transactionId(event.getTransactionId())
            .errorType(event.getErrorType())
            .errorDescription(event.getErrorDescription())
            .reportedAmount(event.getDisputedAmount())
            .reportedAt(LocalDateTime.now())
            .status("REPORTED")
            .noticeDeadline(LocalDateTime.now().plusDays(ERROR_NOTICE_DAYS))
            .correlationId(correlationId)
            .build();
        
        errorRepository.save(error);
        
        regEService.sendErrorNotice(error.getId());
        
        notificationService.sendNotification(
            event.getUserId(),
            "Error Report Received",
            String.format("We received your error report. We will investigate and respond within %d business days.", 
                INVESTIGATION_DAYS),
            correlationId
        );
        
        metricsService.recordRegEErrorReported(event.getErrorType());
    }
    
    private void processUnauthorizedTransactionReported(RegEComplianceEvent event, String correlationId) {
        log.warn("Unauthorized transaction reported: userId={}, amount={}", 
            event.getUserId(), event.getDisputedAmount());
        
        UnauthorizedTransactionClaim claim = UnauthorizedTransactionClaim.builder()
            .id(UUID.randomUUID().toString())
            .userId(event.getUserId())
            .transactionId(event.getTransactionId())
            .disputedAmount(event.getDisputedAmount())
            .transactionDate(event.getTransactionDate())
            .reportedAt(LocalDateTime.now())
            .status("REPORTED")
            .isTimely(isTimelyReported(event.getTransactionDate()))
            .liabilityLimit(calculateLiabilityLimit(event.getTransactionDate()))
            .provisionalCreditDeadline(LocalDateTime.now().plusDays(PROVISIONAL_CREDIT_DAYS))
            .investigationDeadline(calculateInvestigationDeadline(event))
            .correlationId(correlationId)
            .build();
        
        unauthorizedTransactionRepository.save(claim);
        
        if (claim.isTimely()) {
            regEService.scheduleProvisionalCredit(claim.getId());
        }
        
        notificationService.sendNotification(
            event.getUserId(),
            "Unauthorized Transaction Report Received",
            String.format("We received your report of an unauthorized transaction. Your liability is limited to %s.", 
                claim.getLiabilityLimit()),
            correlationId
        );
        
        metricsService.recordUnauthorizedTransactionReported(claim.isTimely());
    }
    
    private void processDisputeInitiated(RegEComplianceEvent event, String correlationId) {
        log.info("Reg E dispute initiated: disputeId={}, type={}", 
            event.getDisputeId(), event.getDisputeType());
        
        RegEDispute dispute = RegEDispute.builder()
            .id(event.getDisputeId())
            .userId(event.getUserId())
            .transactionId(event.getTransactionId())
            .disputeType(event.getDisputeType())
            .disputedAmount(event.getDisputedAmount())
            .initiatedAt(LocalDateTime.now())
            .status("INITIATED")
            .investigationDeadline(calculateInvestigationDeadline(event))
            .requiresProvisionalCredit(requiresProvisionalCredit(event))
            .correlationId(correlationId)
            .build();
        
        disputeRepository.save(dispute);
        disputeResolutionService.startInvestigation(dispute.getId());
        
        metricsService.recordRegEDisputeInitiated(event.getDisputeType());
    }
    
    private void processInvestigationStarted(RegEComplianceEvent event, String correlationId) {
        log.info("Investigation started: disputeId={}", event.getDisputeId());
        
        RegEDispute dispute = disputeRepository.findById(event.getDisputeId())
            .orElseThrow();
        
        dispute.setStatus("UNDER_INVESTIGATION");
        dispute.setInvestigationStartedAt(LocalDateTime.now());
        disputeRepository.save(dispute);
        
        notificationService.sendNotification(
            event.getUserId(),
            "Investigation Started",
            "We have started investigating your dispute.",
            correlationId
        );
        
        metricsService.recordRegEInvestigationStarted();
    }
    
    private void processProvisionalCreditIssued(RegEComplianceEvent event, String correlationId) {
        log.info("Provisional credit issued: disputeId={}, amount={}", 
            event.getDisputeId(), event.getCreditAmount());
        
        RegEDispute dispute = disputeRepository.findById(event.getDisputeId())
            .orElseThrow();
        
        dispute.setProvisionalCreditIssued(true);
        dispute.setProvisionalCreditAmount(event.getCreditAmount());
        dispute.setProvisionalCreditIssuedAt(LocalDateTime.now());
        disputeRepository.save(dispute);
        
        notificationService.sendNotification(
            event.getUserId(),
            "Provisional Credit Issued",
            String.format("A provisional credit of %s has been issued to your account while we investigate.", 
                event.getCreditAmount()),
            correlationId
        );
        
        metricsService.recordProvisionalCreditIssued(event.getCreditAmount());
    }
    
    private void processInvestigationCompleted(RegEComplianceEvent event, String correlationId) {
        log.info("Investigation completed: disputeId={}, finding={}", 
            event.getDisputeId(), event.getInvestigationFinding());
        
        RegEDispute dispute = disputeRepository.findById(event.getDisputeId())
            .orElseThrow();
        
        dispute.setStatus("INVESTIGATION_COMPLETED");
        dispute.setInvestigationCompletedAt(LocalDateTime.now());
        dispute.setInvestigationFinding(event.getInvestigationFinding());
        dispute.setResolutionAmount(event.getResolutionAmount());
        disputeRepository.save(dispute);
        
        regEService.prepareResolutionLetter(dispute.getId());
        metricsService.recordRegEInvestigationCompleted(event.getInvestigationFinding());
    }
    
    private void processDisputeResolved(RegEComplianceEvent event, String correlationId) {
        log.info("Dispute resolved: disputeId={}, resolution={}", 
            event.getDisputeId(), event.getResolutionType());
        
        RegEDispute dispute = disputeRepository.findById(event.getDisputeId())
            .orElseThrow();
        
        dispute.setStatus("RESOLVED");
        dispute.setResolvedAt(LocalDateTime.now());
        dispute.setResolutionType(event.getResolutionType());
        dispute.setFinalAmount(event.getFinalAmount());
        disputeRepository.save(dispute);
        
        String message = "CUSTOMER_FAVOR".equals(event.getResolutionType()) ?
            String.format("Your dispute has been resolved in your favor. Amount: %s", event.getFinalAmount()) :
            "Your dispute has been resolved. Please see the resolution letter for details.";
        
        notificationService.sendNotification(
            event.getUserId(),
            "Dispute Resolved",
            message,
            correlationId
        );
        
        metricsService.recordRegEDisputeResolved(event.getResolutionType());
    }
    
    private void processErrorCorrected(RegEComplianceEvent event, String correlationId) {
        log.info("Error corrected: errorId={}, correctionAmount={}", 
            event.getErrorId(), event.getCorrectionAmount());
        
        RegEError error = errorRepository.findById(event.getErrorId())
            .orElseThrow();
        
        error.setStatus("CORRECTED");
        error.setCorrectedAt(LocalDateTime.now());
        error.setCorrectionAmount(event.getCorrectionAmount());
        errorRepository.save(error);
        
        notificationService.sendNotification(
            event.getUserId(),
            "Error Corrected",
            String.format("The error has been corrected. Adjustment: %s", event.getCorrectionAmount()),
            correlationId
        );
        
        metricsService.recordRegEErrorCorrected();
    }
    
    private void processPeriodicStatementError(RegEComplianceEvent event, String correlationId) {
        log.info("Periodic statement error: userId={}, statementPeriod={}", 
            event.getUserId(), event.getStatementPeriod());
        
        RegEError error = RegEError.builder()
            .id(UUID.randomUUID().toString())
            .userId(event.getUserId())
            .errorType("PERIODIC_STATEMENT_ERROR")
            .errorDescription(event.getErrorDescription())
            .statementPeriod(event.getStatementPeriod())
            .reportedAt(LocalDateTime.now())
            .status("UNDER_REVIEW")
            .correlationId(correlationId)
            .build();
        
        errorRepository.save(error);
        regEService.reviewStatementError(error.getId());
        
        metricsService.recordPeriodicStatementError();
    }
    
    private void processPreauthorizedTransferError(RegEComplianceEvent event, String correlationId) {
        log.info("Preauthorized transfer error: userId={}, transferId={}", 
            event.getUserId(), event.getTransferId());
        
        RegEError error = RegEError.builder()
            .id(UUID.randomUUID().toString())
            .userId(event.getUserId())
            .transferId(event.getTransferId())
            .errorType("PREAUTHORIZED_TRANSFER_ERROR")
            .errorDescription(event.getErrorDescription())
            .reportedAmount(event.getDisputedAmount())
            .reportedAt(LocalDateTime.now())
            .status("UNDER_REVIEW")
            .correlationId(correlationId)
            .build();
        
        errorRepository.save(error);
        regEService.reviewPreauthorizedTransferError(error.getId());
        
        metricsService.recordPreauthorizedTransferError();
    }
    
    private boolean isTimelyReported(LocalDateTime transactionDate) {
        long daysSinceTransaction = java.time.Duration.between(transactionDate, LocalDateTime.now()).toDays();
        return daysSinceTransaction <= 60;
    }
    
    private String calculateLiabilityLimit(LocalDateTime transactionDate) {
        long daysSinceTransaction = java.time.Duration.between(transactionDate, LocalDateTime.now()).toDays();
        
        if (daysSinceTransaction <= 2) {
            return "$50";
        } else if (daysSinceTransaction <= 60) {
            return "$500";
        } else {
            return "Unlimited";
        }
    }
    
    private LocalDateTime calculateInvestigationDeadline(RegEComplianceEvent event) {
        boolean isNewAccount = event.isNewAccount() != null && event.isNewAccount();
        boolean isComplexCase = event.isComplexCase() != null && event.isComplexCase();
        
        if (isNewAccount || isComplexCase) {
            return LocalDateTime.now().plusDays(EXTENDED_INVESTIGATION_DAYS);
        }
        return LocalDateTime.now().plusDays(INVESTIGATION_DAYS);
    }
    
    private boolean requiresProvisionalCredit(RegEComplianceEvent event) {
        return "UNAUTHORIZED_TRANSACTION".equals(event.getDisputeType()) ||
               "ATM_WITHDRAWAL_ERROR".equals(event.getDisputeType());
    }
}