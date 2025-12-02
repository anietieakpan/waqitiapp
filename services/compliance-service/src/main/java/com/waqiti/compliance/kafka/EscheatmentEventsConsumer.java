package com.waqiti.compliance.kafka;

import com.waqiti.common.events.EscheatmentEvent;
import com.waqiti.compliance.domain.DormantAccount;
import com.waqiti.compliance.domain.EscheatmentReport;
import com.waqiti.compliance.repository.DormantAccountRepository;
import com.waqiti.compliance.repository.EscheatmentReportRepository;
import com.waqiti.compliance.service.EscheatmentService;
import com.waqiti.compliance.service.StateReportingService;
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
public class EscheatmentEventsConsumer {
    
    private final DormantAccountRepository dormantAccountRepository;
    private final EscheatmentReportRepository escheatmentReportRepository;
    private final EscheatmentService escheatmentService;
    private final StateReportingService stateReportingService;
    private final ComplianceMetricsService metricsService;
    private final AuditService auditService;
    private final NotificationService notificationService;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    
    private static final Map<String, Integer> STATE_DORMANCY_PERIODS = Map.ofEntries(
        Map.entry("CA", 3),
        Map.entry("NY", 3),
        Map.entry("TX", 3),
        Map.entry("FL", 5),
        Map.entry("IL", 5),
        Map.entry("DEFAULT", 5)
    );
    
    @KafkaListener(
        topics = {"escheatment-events", "unclaimed-property-events", "dormant-account-events"},
        groupId = "compliance-escheatment-service-group",
        containerFactory = "criticalFinancialKafkaListenerContainerFactory",
        concurrency = "3"
    )
    @RetryableTopic(
        attempts = "3",
        backoff = @Backoff(delay = 3000, multiplier = 2.0, maxDelay = 30000),
        dltStrategy = org.springframework.kafka.retrytopic.DltStrategy.FAIL_ON_ERROR,
        topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE
    )
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public void handleEscheatmentEvent(
            @Payload EscheatmentEvent event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {
        
        String correlationId = String.format("escheat-%s-p%d-o%d", 
            event.getAccountId(), partition, offset);
        
        log.info("Processing escheatment event: accountId={}, type={}", 
            event.getAccountId(), event.getEventType());
        
        try {
            switch (event.getEventType()) {
                case ACCOUNT_DORMANT:
                    processAccountDormant(event, correlationId);
                    break;
                case DUE_DILIGENCE_REQUIRED:
                    processDueDiligenceRequired(event, correlationId);
                    break;
                case DUE_DILIGENCE_LETTER_SENT:
                    processDueDiligenceLetterSent(event, correlationId);
                    break;
                case ACCOUNT_REACTIVATED:
                    processAccountReactivated(event, correlationId);
                    break;
                case PROPERTY_ESCHEATABLE:
                    processPropertyEscheatable(event, correlationId);
                    break;
                case STATE_REPORT_GENERATED:
                    processStateReportGenerated(event, correlationId);
                    break;
                case STATE_REPORT_FILED:
                    processStateReportFiled(event, correlationId);
                    break;
                case FUNDS_REMITTED_TO_STATE:
                    processFundsRemittedToState(event, correlationId);
                    break;
                case CLAIM_RECEIVED_FROM_STATE:
                    processClaimReceivedFromState(event, correlationId);
                    break;
                default:
                    log.warn("Unknown escheatment event type: {}", event.getEventType());
                    break;
            }
            
            auditService.logComplianceEvent(
                "ESCHEATMENT_EVENT_PROCESSED",
                event.getAccountId(),
                Map.of(
                    "eventType", event.getEventType(),
                    "state", event.getState(),
                    "correlationId", correlationId,
                    "timestamp", Instant.now()
                )
            );
            
            acknowledgment.acknowledge();
            
        } catch (Exception e) {
            log.error("Failed to process escheatment event: {}", e.getMessage(), e);
            kafkaTemplate.send("escheatment-events-dlq", Map.of(
                "originalEvent", event,
                "error", e.getMessage(),
                "correlationId", correlationId,
                "timestamp", Instant.now()
            ));
            acknowledgment.acknowledge();
        }
    }
    
    private void processAccountDormant(EscheatmentEvent event, String correlationId) {
        log.info("Account marked dormant: accountId={}, state={}, balance={}", 
            event.getAccountId(), event.getState(), event.getBalance());
        
        int dormancyPeriodYears = STATE_DORMANCY_PERIODS.getOrDefault(
            event.getState(), 
            STATE_DORMANCY_PERIODS.get("DEFAULT")
        );
        
        DormantAccount dormantAccount = DormantAccount.builder()
            .id(UUID.randomUUID().toString())
            .accountId(event.getAccountId())
            .userId(event.getUserId())
            .accountType(event.getAccountType())
            .balance(event.getBalance())
            .lastActivityDate(event.getLastActivityDate())
            .dormantSince(LocalDateTime.now())
            .ownerState(event.getState())
            .ownerAddress(event.getOwnerAddress())
            .dormancyPeriodYears(dormancyPeriodYears)
            .escheatable(false)
            .status("DORMANT")
            .dueDiligenceDeadline(calculateDueDiligenceDeadline(event.getLastActivityDate(), dormancyPeriodYears))
            .escheatmentDeadline(calculateEscheatmentDeadline(event.getLastActivityDate(), dormancyPeriodYears))
            .correlationId(correlationId)
            .build();
        
        dormantAccountRepository.save(dormantAccount);
        escheatmentService.scheduleDueDiligence(dormantAccount.getId());
        
        metricsService.recordDormantAccount(event.getState(), event.getBalance());
    }
    
    private void processDueDiligenceRequired(EscheatmentEvent event, String correlationId) {
        log.info("Due diligence required: accountId={}, state={}", 
            event.getAccountId(), event.getState());
        
        DormantAccount dormantAccount = dormantAccountRepository.findByAccountId(event.getAccountId())
            .orElseThrow();
        
        dormantAccount.setStatus("DUE_DILIGENCE_REQUIRED");
        dormantAccount.setDueDiligenceStartedAt(LocalDateTime.now());
        dormantAccountRepository.save(dormantAccount);
        
        escheatmentService.initiateDueDiligence(dormantAccount.getId());
        metricsService.recordDueDiligenceInitiated(event.getState());
    }
    
    private void processDueDiligenceLetterSent(EscheatmentEvent event, String correlationId) {
        log.info("Due diligence letter sent: accountId={}, method={}", 
            event.getAccountId(), event.getContactMethod());
        
        DormantAccount dormantAccount = dormantAccountRepository.findByAccountId(event.getAccountId())
            .orElseThrow();
        
        dormantAccount.setStatus("DUE_DILIGENCE_SENT");
        dormantAccount.setDueDiligenceLetterSentAt(LocalDateTime.now());
        dormantAccount.setContactMethod(event.getContactMethod());
        dormantAccount.setResponseDeadline(LocalDateTime.now().plusDays(30));
        dormantAccountRepository.save(dormantAccount);
        
        metricsService.recordDueDiligenceLetterSent(event.getContactMethod());
    }
    
    private void processAccountReactivated(EscheatmentEvent event, String correlationId) {
        log.info("Dormant account reactivated: accountId={}", event.getAccountId());
        
        DormantAccount dormantAccount = dormantAccountRepository.findByAccountId(event.getAccountId())
            .orElseThrow();
        
        dormantAccount.setStatus("REACTIVATED");
        dormantAccount.setReactivatedAt(LocalDateTime.now());
        dormantAccount.setEscheatable(false);
        dormantAccountRepository.save(dormantAccount);
        
        notificationService.sendNotification(
            event.getUserId(),
            "Account Reactivated",
            "Your account has been reactivated and will not be reported as unclaimed property.",
            correlationId
        );
        
        metricsService.recordDormantAccountReactivated(event.getState());
    }
    
    private void processPropertyEscheatable(EscheatmentEvent event, String correlationId) {
        log.warn("Property escheatable: accountId={}, state={}, amount={}", 
            event.getAccountId(), event.getState(), event.getBalance());
        
        DormantAccount dormantAccount = dormantAccountRepository.findByAccountId(event.getAccountId())
            .orElseThrow();
        
        dormantAccount.setStatus("ESCHEATABLE");
        dormantAccount.setEscheatable(true);
        dormantAccount.setEscheatableAsOf(LocalDateTime.now());
        dormantAccountRepository.save(dormantAccount);
        
        escheatmentService.prepareForEscheatment(dormantAccount.getId());
        
        notificationService.sendComplianceAlert(
            "Property Escheatable",
            String.format("Account %s (%.2f) is now escheatable to %s", 
                event.getAccountId(), event.getBalance(), event.getState()),
            NotificationService.Priority.HIGH
        );
        
        metricsService.recordPropertyEscheatable(event.getState(), event.getBalance());
    }
    
    private void processStateReportGenerated(EscheatmentEvent event, String correlationId) {
        log.info("State escheatment report generated: reportId={}, state={}, itemCount={}", 
            event.getReportId(), event.getState(), event.getItemCount());
        
        EscheatmentReport report = EscheatmentReport.builder()
            .id(event.getReportId())
            .state(event.getState())
            .reportingYear(event.getReportingYear())
            .itemCount(event.getItemCount())
            .totalAmount(event.getTotalAmount())
            .generatedAt(LocalDateTime.now())
            .status("GENERATED")
            .filingDeadline(calculateFilingDeadline(event.getState(), event.getReportingYear()))
            .correlationId(correlationId)
            .build();
        
        escheatmentReportRepository.save(report);
        stateReportingService.validateReport(report.getId());
        
        metricsService.recordEscheatmentReportGenerated(event.getState(), event.getItemCount(), event.getTotalAmount());
    }
    
    private void processStateReportFiled(EscheatmentEvent event, String correlationId) {
        log.info("State escheatment report filed: reportId={}, state={}, confirmationNumber={}", 
            event.getReportId(), event.getState(), event.getConfirmationNumber());
        
        EscheatmentReport report = escheatmentReportRepository.findById(event.getReportId())
            .orElseThrow();
        
        report.setStatus("FILED");
        report.setFiledAt(LocalDateTime.now());
        report.setConfirmationNumber(event.getConfirmationNumber());
        report.setRemittanceDeadline(LocalDateTime.now().plusDays(30));
        escheatmentReportRepository.save(report);
        
        escheatmentService.scheduleRemittance(report.getId());
        metricsService.recordEscheatmentReportFiled(event.getState());
    }
    
    private void processFundsRemittedToState(EscheatmentEvent event, String correlationId) {
        log.info("Funds remitted to state: reportId={}, state={}, amount={}", 
            event.getReportId(), event.getState(), event.getRemittedAmount());
        
        EscheatmentReport report = escheatmentReportRepository.findById(event.getReportId())
            .orElseThrow();
        
        report.setStatus("REMITTED");
        report.setRemittedAt(LocalDateTime.now());
        report.setRemittedAmount(event.getRemittedAmount());
        report.setPaymentMethod(event.getPaymentMethod());
        report.setPaymentReference(event.getPaymentReference());
        escheatmentReportRepository.save(report);
        
        List<DormantAccount> accounts = dormantAccountRepository.findByStateAndEscheatable(event.getState(), true);
        accounts.forEach(account -> {
            account.setStatus("ESCHEATED");
            account.setEscheatedAt(LocalDateTime.now());
            account.setEscheatmentReportId(report.getId());
            dormantAccountRepository.save(account);
        });
        
        metricsService.recordFundsRemittedToState(event.getState(), event.getRemittedAmount());
    }
    
    private void processClaimReceivedFromState(EscheatmentEvent event, String correlationId) {
        log.info("Claim received from state: accountId={}, claimId={}, amount={}", 
            event.getAccountId(), event.getClaimId(), event.getClaimAmount());
        
        DormantAccount dormantAccount = dormantAccountRepository.findByAccountId(event.getAccountId())
            .orElseThrow();
        
        dormantAccount.setStatus("CLAIM_RECEIVED");
        dormantAccount.setClaimReceivedAt(LocalDateTime.now());
        dormantAccount.setClaimId(event.getClaimId());
        dormantAccount.setClaimAmount(event.getClaimAmount());
        dormantAccountRepository.save(dormantAccount);
        
        escheatmentService.processClaim(dormantAccount.getId(), event.getClaimId());
        metricsService.recordClaimReceived(event.getState(), event.getClaimAmount());
    }
    
    private LocalDateTime calculateDueDiligenceDeadline(LocalDateTime lastActivityDate, int dormancyPeriodYears) {
        return lastActivityDate.plusYears(dormancyPeriodYears).minusMonths(6);
    }
    
    private LocalDateTime calculateEscheatmentDeadline(LocalDateTime lastActivityDate, int dormancyPeriodYears) {
        return lastActivityDate.plusYears(dormancyPeriodYears);
    }
    
    private LocalDateTime calculateFilingDeadline(String state, int reportingYear) {
        return switch (state) {
            case "CA" -> LocalDateTime.of(reportingYear + 1, 11, 1, 0, 0);
            case "NY", "TX" -> LocalDateTime.of(reportingYear + 1, 5, 1, 0, 0);
            case "FL" -> LocalDateTime.of(reportingYear + 1, 5, 1, 0, 0);
            default -> LocalDateTime.of(reportingYear + 1, 11, 1, 0, 0);
        };
    }
}