package com.waqiti.lending.kafka;

import com.waqiti.common.events.LoanOriginationEvent;
import com.waqiti.lending.domain.LoanApplication;
import com.waqiti.lending.repository.LoanApplicationRepository;
import com.waqiti.lending.service.LoanUnderwritingService;
import com.waqiti.lending.service.CreditCheckService;
import com.waqiti.lending.metrics.LoanMetricsService;
import com.waqiti.common.audit.AuditService;
import com.waqiti.common.notification.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.time.Instant;
import java.util.*;

@Component
@Slf4j
@RequiredArgsConstructor
public class LoanOriginationEventsConsumer {
    
    private final LoanApplicationRepository applicationRepository;
    private final LoanUnderwritingService underwritingService;
    private final CreditCheckService creditCheckService;
    private final LoanMetricsService metricsService;
    private final AuditService auditService;
    private final NotificationService notificationService;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    
    @KafkaListener(
        topics = {"loan-origination-events", "loan-application-events", "loan-approval-events"},
        groupId = "loan-origination-service-group",
        containerFactory = "criticalFinancialKafkaListenerContainerFactory",
        concurrency = "4"
    )
    @RetryableTopic(attempts = "3", backoff = @Backoff(delay = 3000, multiplier = 2.0, maxDelay = 30000))
    @Transactional
    public void handleLoanOriginationEvent(
            @Payload LoanOriginationEvent event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {
        
        String correlationId = String.format("loan-orig-%s-p%d-o%d", event.getApplicationId(), partition, offset);
        
        log.info("Processing loan origination event: appId={}, type={}", event.getApplicationId(), event.getEventType());
        
        try {
            switch (event.getEventType()) {
                case APPLICATION_SUBMITTED:
                    LoanApplication app = LoanApplication.builder()
                        .id(event.getApplicationId())
                        .userId(event.getUserId())
                        .loanAmount(event.getLoanAmount())
                        .loanPurpose(event.getLoanPurpose())
                        .status("SUBMITTED")
                        .submittedAt(LocalDateTime.now())
                        .correlationId(correlationId)
                        .build();
                    applicationRepository.save(app);
                    creditCheckService.initiateCreditCheck(event.getApplicationId());
                    notificationService.sendNotification(event.getUserId(), "Loan Application Received",
                        "Your loan application has been received and is being processed.", correlationId);
                    metricsService.recordApplicationSubmitted(event.getLoanAmount());
                    break;
                case CREDIT_CHECK_COMPLETED:
                    LoanApplication creditApp = applicationRepository.findById(event.getApplicationId()).orElseThrow();
                    creditApp.setCreditScore(event.getCreditScore());
                    creditApp.setCreditCheckCompletedAt(LocalDateTime.now());
                    applicationRepository.save(creditApp);
                    underwritingService.evaluateApplication(event.getApplicationId());
                    metricsService.recordCreditCheckCompleted(event.getCreditScore());
                    break;
                case UNDERWRITING_APPROVED:
                    LoanApplication approvedApp = applicationRepository.findById(event.getApplicationId()).orElseThrow();
                    approvedApp.setStatus("APPROVED");
                    approvedApp.setApprovedAt(LocalDateTime.now());
                    approvedApp.setApprovedAmount(event.getApprovedAmount());
                    approvedApp.setInterestRate(event.getInterestRate());
                    applicationRepository.save(approvedApp);
                    notificationService.sendNotification(event.getUserId(), "Loan Approved",
                        String.format("Congratulations! Your loan of %.2f has been approved at %.2f%% APR.", 
                            event.getApprovedAmount(), event.getInterestRate()), correlationId);
                    metricsService.recordLoanApproved(event.getApprovedAmount());
                    break;
                case UNDERWRITING_DENIED:
                    LoanApplication deniedApp = applicationRepository.findById(event.getApplicationId()).orElseThrow();
                    deniedApp.setStatus("DENIED");
                    deniedApp.setDeniedAt(LocalDateTime.now());
                    deniedApp.setDenialReason(event.getDenialReason());
                    applicationRepository.save(deniedApp);
                    notificationService.sendNotification(event.getUserId(), "Loan Application Update",
                        "We're unable to approve your loan application at this time.", correlationId);
                    metricsService.recordLoanDenied(event.getDenialReason());
                    break;
                case LOAN_FUNDED:
                    LoanApplication fundedApp = applicationRepository.findById(event.getApplicationId()).orElseThrow();
                    fundedApp.setStatus("FUNDED");
                    fundedApp.setFundedAt(LocalDateTime.now());
                    applicationRepository.save(fundedApp);
                    notificationService.sendNotification(event.getUserId(), "Loan Funded",
                        "Your loan has been funded! Funds will be available shortly.", correlationId);
                    metricsService.recordLoanFunded(event.getApprovedAmount());
                    break;
                default:
                    log.warn("Unknown loan origination event type: {}", event.getEventType());
                    break;
            }
            
            auditService.logLoanEvent("LOAN_ORIGINATION_EVENT_PROCESSED", event.getApplicationId(),
                Map.of("eventType", event.getEventType(), "correlationId", correlationId, "timestamp", Instant.now()));
            
            acknowledgment.acknowledge();
            
        } catch (Exception e) {
            log.error("Failed to process loan origination event: {}", e.getMessage(), e);
            kafkaTemplate.send("loan-origination-events-dlq", Map.of(
                "originalEvent", event, "error", e.getMessage(), "correlationId", correlationId, "timestamp", Instant.now()));
            acknowledgment.acknowledge();
        }
    }
}