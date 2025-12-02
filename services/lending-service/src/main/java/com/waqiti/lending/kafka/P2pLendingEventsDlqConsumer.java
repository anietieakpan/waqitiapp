package com.waqiti.lending.kafka;

import com.waqiti.common.dlq.BaseDlqConsumer;
import com.waqiti.common.dlq.DlqHandler;
import com.waqiti.common.audit.AuditService;
import com.waqiti.common.notification.NotificationService;
import com.waqiti.lending.service.P2pLendingService;
import com.waqiti.lending.service.LoanService;
import com.waqiti.lending.repository.P2pLoanRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.micrometer.core.instrument.MeterRegistry;

import java.time.Instant;
import java.util.Map;

/**
 * DLQ Consumer for peer-to-peer lending events that failed to process.
 * Handles critical P2P lending platform failures affecting borrowers and lenders.
 */
@Component
@Slf4j
public class P2pLendingEventsDlqConsumer extends BaseDlqConsumer {

    private final P2pLendingService p2pLendingService;
    private final LoanService loanService;
    private final P2pLoanRepository p2pLoanRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    public P2pLendingEventsDlqConsumer(DlqHandler dlqHandler,
                                      AuditService auditService,
                                      NotificationService notificationService,
                                      MeterRegistry meterRegistry,
                                      P2pLendingService p2pLendingService,
                                      LoanService loanService,
                                      P2pLoanRepository p2pLoanRepository,
                                      KafkaTemplate<String, Object> kafkaTemplate) {
        super(dlqHandler, auditService, notificationService, meterRegistry);
        this.p2pLendingService = p2pLendingService;
        this.loanService = loanService;
        this.p2pLoanRepository = p2pLoanRepository;
        this.kafkaTemplate = kafkaTemplate;
    }

    @RetryableTopic(
        attempts = "3",
        backoff = @Backoff(delay = 5000, multiplier = 2.0),
        include = {Exception.class}
    )
    @KafkaListener(
        topics = {"p2p-lending-events-dlq"},
        groupId = "p2p-lending-dlq-consumer-group",
        containerFactory = "criticalFinancialKafkaListenerContainerFactory",
        properties = {
            "spring.kafka.consumer.isolation.level=read_committed",
            "spring.kafka.consumer.enable.auto.commit=false"
        }
    )
    @DltHandler
    @Transactional
    @CircuitBreaker(name = "p2p-lending-dlq", fallbackMethod = "handleP2pLendingDlqFallback")
    public void handleP2pLendingDlq(@Payload Object originalMessage,
                                   @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
                                   @Header(KafkaHeaders.EXCEPTION_MESSAGE) String exceptionMessage,
                                   @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
                                   @Header(KafkaHeaders.OFFSET) long offset,
                                   Acknowledgment acknowledgment,
                                   @Header Map<String, Object> headers) {

        String correlationId = extractCorrelationId(headers, originalMessage);
        log.info("Processing P2P lending DLQ message: topic={}, partition={}, offset={}, correlationId={}",
            topic, partition, offset, correlationId);

        super.handleDlqMessage(originalMessage, topic, exceptionMessage, partition, offset, acknowledgment, headers);
    }

    @Override
    protected void processDomainSpecificLogic(Object originalMessage, String topic,
                                            String exceptionMessage, String messageId) {
        try {
            String loanId = extractLoanId(originalMessage);
            String borrowerId = extractBorrowerId(originalMessage);
            String lenderId = extractLenderId(originalMessage);
            String eventType = extractEventType(originalMessage);
            String platformId = extractPlatformId(originalMessage);
            String correlationId = extractCorrelationId(null, originalMessage);

            log.info("Processing P2P lending DLQ: loanId={}, borrowerId={}, lenderId={}, eventType={}, platformId={}, messageId={}",
                loanId, borrowerId, lenderId, eventType, platformId, messageId);

            // Validate P2P loan and participant status
            if (loanId != null) {
                validateP2pLoanStatus(loanId, messageId);
                assessParticipantImpact(loanId, borrowerId, lenderId, originalMessage, messageId);
                handlePlatformFeesAndDistribution(loanId, originalMessage, exceptionMessage, messageId);
            }

            // Generate domain-specific alerts
            generateDomainSpecificAlerts(originalMessage, topic, exceptionMessage, messageId);

            // Handle platform-specific issues
            if (platformId != null) {
                handlePlatformOperationalIssues(platformId, eventType, originalMessage, messageId);
            }

            // Check for regulatory and compliance implications
            assessP2pRegulatoryImpact(loanId, eventType, originalMessage, messageId);

            // Handle specific P2P lending event failures
            handleSpecificP2pEventFailure(eventType, loanId, originalMessage, messageId);

        } catch (Exception e) {
            log.error("Error in P2P lending DLQ domain-specific processing: messageId={}, error={}",
                messageId, e.getMessage(), e);
            throw e;
        }
    }

    @Override
    protected String getConsumerName() {
        return "p2p-lending-events-dlq";
    }

    @Override
    protected String getBusinessDomain() {
        return "FINANCIAL_P2P_LENDING";
    }

    @Override
    protected boolean isCriticalBusinessImpact(Object originalMessage, String topic) {
        String eventType = extractEventType(originalMessage);
        Double amount = extractAmount(originalMessage);

        // Critical events affecting P2P participants
        if ("FUNDING_FAILED".equals(eventType) || "PAYMENT_DISTRIBUTION_FAILED".equals(eventType) ||
            "BORROWER_DEFAULT".equals(eventType) || "LENDER_WITHDRAWAL_FAILED".equals(eventType) ||
            "PLATFORM_SETTLEMENT_FAILED".equals(eventType)) {
            return true;
        }

        // Large P2P loan amounts are always critical
        return amount != null && amount > 50000;
    }

    @Override
    protected void generateDomainSpecificAlerts(Object originalMessage, String topic,
                                              String exceptionMessage, String messageId) {
        String loanId = extractLoanId(originalMessage);
        String borrowerId = extractBorrowerId(originalMessage);
        String lenderId = extractLenderId(originalMessage);
        String eventType = extractEventType(originalMessage);
        Double amount = extractAmount(originalMessage);

        try {
            // Send immediate alert to P2P lending operations team
            String alertTitle = String.format("CRITICAL: P2P Lending Event Failed - %s", eventType);
            String alertMessage = String.format(
                "P2P lending event processing failed:\n" +
                "Loan ID: %s\n" +
                "Borrower ID: %s\n" +
                "Lender ID: %s\n" +
                "Event Type: %s\n" +
                "Amount: %s\n" +
                "Error: %s\n" +
                "IMMEDIATE ACTION REQUIRED - P2P lending affects multiple participants.",
                loanId != null ? loanId : "unknown",
                borrowerId != null ? borrowerId : "unknown",
                lenderId != null ? lenderId : "unknown",
                eventType != null ? eventType : "unknown",
                amount != null ? String.format("$%.2f", amount) : "unknown",
                exceptionMessage
            );

            notificationService.sendCriticalAlert(alertTitle, alertMessage,
                Map.of("loanId", loanId != null ? loanId : "unknown",
                       "borrowerId", borrowerId != null ? borrowerId : "unknown",
                       "lenderId", lenderId != null ? lenderId : "unknown",
                       "eventType", eventType != null ? eventType : "unknown",
                       "messageId", messageId,
                       "businessImpact", "CRITICAL_P2P_PARTICIPANT_RISK"));

            // Send participant-specific notifications
            if (isParticipantImpactingEvent(eventType)) {
                if (borrowerId != null) {
                    String borrowerMessage = getBorrowerNotificationMessage(eventType);
                    notificationService.sendNotification(borrowerId,
                        "P2P Lending Update",
                        borrowerMessage,
                        messageId);
                }

                if (lenderId != null) {
                    String lenderMessage = getLenderNotificationMessage(eventType);
                    notificationService.sendNotification(lenderId,
                        "P2P Lending Investment Update",
                        lenderMessage,
                        messageId);
                }
            }

            // Alert platform operations for system-wide issues
            if (isPlatformEvent(eventType)) {
                notificationService.sendOperationalAlert(
                    "P2P Platform Event Failed",
                    String.format("P2P platform event %s failed for loan %s. " +
                        "Review platform stability and participant communications.", eventType, loanId),
                    "HIGH"
                );
            }

            // Alert compliance team for regulatory events
            if (isRegulatoryEvent(eventType)) {
                notificationService.sendComplianceAlert(
                    "P2P Regulatory Event Failed",
                    String.format("Regulatory event %s failed for P2P loan %s. " +
                        "Review compliance requirements and reporting obligations.", eventType, loanId),
                    "URGENT"
                );
            }

            // Alert finance team for payment distribution issues
            if (isPaymentDistributionEvent(eventType)) {
                notificationService.sendFinanceAlert(
                    "P2P Payment Distribution Failed",
                    String.format("Payment distribution event %s failed for loan %s. " +
                        "Verify participant account balances and payment processing.", eventType, loanId),
                    "HIGH"
                );
            }

        } catch (Exception e) {
            log.error("Failed to send P2P lending DLQ alerts: {}", e.getMessage());
        }
    }

    private void validateP2pLoanStatus(String loanId, String messageId) {
        try {
            var p2pLoan = p2pLoanRepository.findById(loanId);
            if (p2pLoan.isPresent()) {
                String status = p2pLoan.get().getStatus();
                String fundingStatus = p2pLoan.get().getFundingStatus();
                Integer participantCount = p2pLoan.get().getParticipantCount();

                log.info("P2P loan status validation for DLQ: loanId={}, status={}, fundingStatus={}, participants={}, messageId={}",
                    loanId, status, fundingStatus, participantCount, messageId);

                // Check for funding issues
                if ("FUNDING_FAILED".equals(fundingStatus) || "PARTIALLY_FUNDED".equals(fundingStatus)) {
                    log.warn("P2P loan DLQ for loan with funding issues: loanId={}, fundingStatus={}",
                        loanId, fundingStatus);

                    notificationService.sendCriticalAlert(
                        "High-Risk P2P Loan DLQ",
                        String.format("P2P loan %s with funding status %s has failed event processing. " +
                            "Multiple participants may be affected.", loanId, fundingStatus),
                        Map.of("loanId", loanId, "fundingStatus", fundingStatus, "riskLevel", "HIGH")
                    );
                }

                // Check participant impact
                if (participantCount != null && participantCount > 10) {
                    notificationService.sendOperationalAlert(
                        "High-Participant P2P Loan DLQ",
                        String.format("P2P loan %s with %d participants has failed event processing. " +
                            "Review participant communications and platform notifications.",
                            loanId, participantCount),
                        "HIGH"
                    );
                }
            } else {
                log.error("P2P loan not found for DLQ: loanId={}, messageId={}",
                    loanId, messageId);
            }
        } catch (Exception e) {
            log.error("Error validating P2P loan status for DLQ: loanId={}, error={}",
                loanId, e.getMessage());
        }
    }

    private void assessParticipantImpact(String loanId, String borrowerId, String lenderId,
                                       Object originalMessage, String messageId) {
        try {
            String eventType = extractEventType(originalMessage);
            Double amount = extractAmount(originalMessage);

            // Assess borrower impact
            if (borrowerId != null && isBorrowerImpactingEvent(eventType)) {
                log.info("Assessing borrower impact for P2P DLQ: borrowerId={}, eventType={}, amount=${}",
                    borrowerId, eventType, amount);

                // Check borrower's other P2P loans
                int activeLoanCount = p2pLendingService.getActiveLoanCount(borrowerId);
                if (activeLoanCount > 1) {
                    notificationService.sendRiskManagementAlert(
                        "Multi-Loan P2P Borrower DLQ",
                        String.format("Borrower %s with %d active P2P loans has failed event processing. " +
                            "Review borrower risk profile and cross-loan impacts.", borrowerId, activeLoanCount),
                        "MEDIUM"
                    );
                }
            }

            // Assess lender impact
            if (lenderId != null && isLenderImpactingEvent(eventType)) {
                log.info("Assessing lender impact for P2P DLQ: lenderId={}, eventType={}, amount=${}",
                    lenderId, eventType, amount);

                // Check lender's portfolio exposure
                Double totalExposure = p2pLendingService.getLenderTotalExposure(lenderId);
                if (totalExposure != null && totalExposure > 100000) {
                    notificationService.sendRiskManagementAlert(
                        "High-Exposure P2P Lender DLQ",
                        String.format("Lender %s with $%.2f total exposure has failed event processing. " +
                            "Review portfolio concentration and diversification risks.", lenderId, totalExposure),
                        "HIGH"
                    );
                }
            }

        } catch (Exception e) {
            log.error("Error assessing participant impact: loanId={}, error={}", loanId, e.getMessage());
        }
    }

    private void handlePlatformFeesAndDistribution(String loanId, Object originalMessage,
                                                 String exceptionMessage, String messageId) {
        try {
            String eventType = extractEventType(originalMessage);

            if (isPaymentDistributionEvent(eventType)) {
                // Record distribution failure for reconciliation
                p2pLendingService.recordDistributionFailure(loanId, Map.of(
                    "failureType", "P2P_DLQ",
                    "eventType", eventType,
                    "errorMessage", exceptionMessage,
                    "messageId", messageId,
                    "timestamp", Instant.now(),
                    "requiresReconciliation", true
                ));

                // Check for platform fee calculation issues
                Double platformFee = extractPlatformFee(originalMessage);
                if (platformFee != null && platformFee > 0) {
                    log.warn("P2P DLQ with platform fee implications: loanId={}, fee=${}", loanId, platformFee);

                    // Trigger fee reconciliation
                    kafkaTemplate.send("p2p-fee-reconciliation-queue", Map.of(
                        "loanId", loanId,
                        "platformFee", platformFee,
                        "triggerReason", "DLQ_DISTRIBUTION_FAILURE",
                        "messageId", messageId,
                        "timestamp", Instant.now()
                    ));
                }
            }
        } catch (Exception e) {
            log.error("Error handling platform fees and distribution: loanId={}, error={}",
                loanId, e.getMessage());
        }
    }

    private void handlePlatformOperationalIssues(String platformId, String eventType,
                                                Object originalMessage, String messageId) {
        try {
            // Handle platform-specific events
            if ("PLATFORM_MAINTENANCE".equals(eventType)) {
                notificationService.sendOperationalAlert(
                    "P2P Platform Maintenance Failed",
                    String.format("Platform maintenance processing failed for platform %s. " +
                        "Verify system availability and participant communications.", platformId),
                    "HIGH"
                );
            } else if ("PLATFORM_SETTLEMENT".equals(eventType)) {
                notificationService.sendFinanceAlert(
                    "P2P Platform Settlement Failed",
                    String.format("Platform settlement failed for %s. " +
                        "Review daily settlement procedures and participant payouts.", platformId),
                    "URGENT"
                );
            } else if ("PLATFORM_REPORTING".equals(eventType)) {
                notificationService.sendComplianceAlert(
                    "P2P Platform Reporting Failed",
                    String.format("Platform reporting failed for %s. " +
                        "Review regulatory reporting requirements and deadlines.", platformId),
                    "HIGH"
                );
            }

            // Update platform status for failed events
            p2pLendingService.recordPlatformEventFailure(platformId, eventType, messageId);

        } catch (Exception e) {
            log.error("Error handling platform operational issues: platformId={}, error={}",
                platformId, e.getMessage());
        }
    }

    private void assessP2pRegulatoryImpact(String loanId, String eventType,
                                         Object originalMessage, String messageId) {
        try {
            if (isRegulatoryEvent(eventType)) {
                // P2P lending has specific regulatory requirements
                notificationService.sendComplianceAlert(
                    "P2P Regulatory Event Failed",
                    String.format("Regulatory event %s failed for P2P loan %s. " +
                        "Review compliance with P2P lending regulations and investor protection requirements.",
                        eventType, loanId),
                    "HIGH"
                );

                // Trigger regulatory review for certain events
                if ("INVESTOR_PROTECTION_REPORT".equals(eventType) || "PLATFORM_DISCLOSURE".equals(eventType)) {
                    kafkaTemplate.send("p2p-regulatory-review-queue", Map.of(
                        "loanId", loanId,
                        "eventType", eventType,
                        "reviewReason", "REGULATORY_DLQ",
                        "messageId", messageId,
                        "timestamp", Instant.now()
                    ));
                }
            }
        } catch (Exception e) {
            log.error("Error assessing P2P regulatory impact: loanId={}, error={}",
                loanId, e.getMessage());
        }
    }

    private void handleSpecificP2pEventFailure(String eventType, String loanId,
                                             Object originalMessage, String messageId) {
        try {
            switch (eventType) {
                case "LOAN_FUNDING":
                    handleFundingFailure(loanId, originalMessage, messageId);
                    break;
                case "PAYMENT_DISTRIBUTION":
                    handleDistributionFailure(loanId, originalMessage, messageId);
                    break;
                case "BORROWER_APPLICATION":
                    handleApplicationFailure(loanId, originalMessage, messageId);
                    break;
                case "LENDER_INVESTMENT":
                    handleInvestmentFailure(loanId, originalMessage, messageId);
                    break;
                case "PLATFORM_SETTLEMENT":
                    handleSettlementFailure(loanId, originalMessage, messageId);
                    break;
                default:
                    log.info("No specific handling for P2P event type: {}", eventType);
                    break;
            }
        } catch (Exception e) {
            log.error("Error handling specific P2P event failure: eventType={}, loanId={}, error={}",
                eventType, loanId, e.getMessage());
        }
    }

    private void handleFundingFailure(String loanId, Object originalMessage, String messageId) {
        String borrowerId = extractBorrowerId(originalMessage);
        notificationService.sendCriticalAlert(
            "P2P Loan Funding Failed",
            String.format("P2P loan %s funding failed. Borrower %s may be waiting for loan proceeds.",
                loanId, borrowerId),
            Map.of("loanId", loanId, "borrowerId", borrowerId, "urgency", "HIGH", "customerImpact", "HIGH")
        );
    }

    private void handleDistributionFailure(String loanId, Object originalMessage, String messageId) {
        Double amount = extractAmount(originalMessage);
        notificationService.sendCriticalAlert(
            "P2P Payment Distribution Failed",
            String.format("Payment distribution of $%.2f failed for P2P loan %s. " +
                "Lenders may not receive expected payments.", amount != null ? amount : 0.0, loanId),
            Map.of("loanId", loanId, "amount", amount, "urgency", "HIGH")
        );
    }

    private void handleApplicationFailure(String loanId, Object originalMessage, String messageId) {
        String borrowerId = extractBorrowerId(originalMessage);
        notificationService.sendOperationalAlert(
            "P2P Loan Application Failed",
            String.format("P2P loan application %s failed for borrower %s. " +
                "Review application processing and borrower communications.", loanId, borrowerId),
            "HIGH"
        );
    }

    private void handleInvestmentFailure(String loanId, Object originalMessage, String messageId) {
        String lenderId = extractLenderId(originalMessage);
        Double amount = extractAmount(originalMessage);
        notificationService.sendCriticalAlert(
            "P2P Investment Failed",
            String.format("Investment of $%.2f failed for loan %s by lender %s. " +
                "Review lender account and funding sources.", amount != null ? amount : 0.0, loanId, lenderId),
            Map.of("loanId", loanId, "lenderId", lenderId, "amount", amount, "urgency", "HIGH")
        );
    }

    private void handleSettlementFailure(String loanId, Object originalMessage, String messageId) {
        notificationService.sendExecutiveAlert(
            "CRITICAL: P2P Platform Settlement Failed",
            String.format("Platform settlement failed for P2P loan %s. " +
                "Daily settlement process may be affected, impacting all participants.", loanId)
        );
    }

    // Circuit breaker fallback method
    public void handleP2pLendingDlqFallback(Object originalMessage, String topic, String exceptionMessage,
                                           int partition, long offset, Acknowledgment acknowledgment, Exception ex) {
        super.handleDlqFallback(originalMessage, topic, exceptionMessage, partition, offset, acknowledgment, ex);

        String loanId = extractLoanId(originalMessage);
        String borrowerId = extractBorrowerId(originalMessage);
        String lenderId = extractLenderId(originalMessage);

        if (loanId != null) {
            try {
                // Mark P2P loan for urgent review
                p2pLendingService.markForUrgentReview(loanId, "P2P_LENDING_DLQ_CIRCUIT_BREAKER");

                // Send executive notification with participant impact
                notificationService.sendExecutiveAlert(
                    "Critical: P2P Lending DLQ Circuit Breaker Triggered",
                    String.format("Circuit breaker triggered for P2P lending DLQ processing on loan %s " +
                        "(Borrower: %s, Lender: %s). " +
                        "This indicates a systemic issue affecting peer-to-peer lending operations.",
                        loanId, borrowerId, lenderId)
                );
            } catch (Exception e) {
                log.error("Error in P2P lending DLQ fallback: {}", e.getMessage());
            }
        }
    }

    // Helper methods for event classification
    private boolean isParticipantImpactingEvent(String eventType) {
        return eventType != null && (eventType.contains("PAYMENT") || eventType.contains("FUNDING") ||
                                   eventType.contains("DISTRIBUTION") || eventType.contains("APPLICATION"));
    }

    private boolean isPlatformEvent(String eventType) {
        return eventType != null && eventType.startsWith("PLATFORM_");
    }

    private boolean isRegulatoryEvent(String eventType) {
        return eventType != null && (eventType.contains("REGULATORY") || eventType.contains("COMPLIANCE") ||
                                   eventType.contains("DISCLOSURE") || eventType.contains("PROTECTION"));
    }

    private boolean isPaymentDistributionEvent(String eventType) {
        return eventType != null && (eventType.contains("DISTRIBUTION") || eventType.contains("PAYMENT") ||
                                   eventType.contains("SETTLEMENT"));
    }

    private boolean isBorrowerImpactingEvent(String eventType) {
        return eventType != null && (eventType.contains("FUNDING") || eventType.contains("APPLICATION") ||
                                   eventType.contains("BORROWER"));
    }

    private boolean isLenderImpactingEvent(String eventType) {
        return eventType != null && (eventType.contains("INVESTMENT") || eventType.contains("DISTRIBUTION") ||
                                   eventType.contains("LENDER"));
    }

    private String getBorrowerNotificationMessage(String eventType) {
        switch (eventType) {
            case "LOAN_FUNDING":
                return "We're experiencing a temporary delay with your P2P loan funding. " +
                       "Your loan will be processed shortly and you'll be notified once funds are available.";
            case "PAYMENT_PROCESSING":
                return "We're processing your P2P loan payment. " +
                       "Your payment will be applied to your account once processing is complete.";
            default:
                return "We're experiencing a temporary delay with your P2P loan service. " +
                       "Our team has been notified and will resolve this shortly.";
        }
    }

    private String getLenderNotificationMessage(String eventType) {
        switch (eventType) {
            case "LENDER_INVESTMENT":
                return "We're processing your P2P lending investment. " +
                       "Your investment will be confirmed once processing is complete.";
            case "PAYMENT_DISTRIBUTION":
                return "We're experiencing a temporary delay with your P2P investment payment distribution. " +
                       "Your payments will be processed and distributed according to schedule.";
            default:
                return "We're experiencing a temporary delay with your P2P lending investment. " +
                       "Our team has been notified and will resolve this shortly.";
        }
    }

    // Data extraction helper methods
    private String extractLoanId(Object originalMessage) {
        try {
            if (originalMessage instanceof Map) {
                Map<?, ?> messageMap = (Map<?, ?>) originalMessage;
                Object loanId = messageMap.get("loanId");
                if (loanId == null) loanId = messageMap.get("p2pLoanId");
                return loanId != null ? loanId.toString() : null;
            }
        } catch (Exception e) {
            log.debug("Could not extract loanId from message: {}", e.getMessage());
        }
        return null;
    }

    private String extractBorrowerId(Object originalMessage) {
        try {
            if (originalMessage instanceof Map) {
                Map<?, ?> messageMap = (Map<?, ?>) originalMessage;
                Object borrowerId = messageMap.get("borrowerId");
                return borrowerId != null ? borrowerId.toString() : null;
            }
        } catch (Exception e) {
            log.debug("Could not extract borrowerId from message: {}", e.getMessage());
        }
        return null;
    }

    private String extractLenderId(Object originalMessage) {
        try {
            if (originalMessage instanceof Map) {
                Map<?, ?> messageMap = (Map<?, ?>) originalMessage;
                Object lenderId = messageMap.get("lenderId");
                return lenderId != null ? lenderId.toString() : null;
            }
        } catch (Exception e) {
            log.debug("Could not extract lenderId from message: {}", e.getMessage());
        }
        return null;
    }

    private String extractEventType(Object originalMessage) {
        try {
            if (originalMessage instanceof Map) {
                Map<?, ?> messageMap = (Map<?, ?>) originalMessage;
                Object eventType = messageMap.get("eventType");
                return eventType != null ? eventType.toString() : null;
            }
        } catch (Exception e) {
            log.debug("Could not extract eventType from message: {}", e.getMessage());
        }
        return null;
    }

    private String extractPlatformId(Object originalMessage) {
        try {
            if (originalMessage instanceof Map) {
                Map<?, ?> messageMap = (Map<?, ?>) originalMessage;
                Object platformId = messageMap.get("platformId");
                return platformId != null ? platformId.toString() : null;
            }
        } catch (Exception e) {
            log.debug("Could not extract platformId from message: {}", e.getMessage());
        }
        return null;
    }

    private Double extractAmount(Object originalMessage) {
        try {
            if (originalMessage instanceof Map) {
                Map<?, ?> messageMap = (Map<?, ?>) originalMessage;
                Object amount = messageMap.get("amount");
                if (amount == null) amount = messageMap.get("investmentAmount");
                if (amount instanceof Number) {
                    return ((Number) amount).doubleValue();
                }
            }
        } catch (Exception e) {
            log.debug("Could not extract amount from message: {}", e.getMessage());
        }
        return null;
    }

    private Double extractPlatformFee(Object originalMessage) {
        try {
            if (originalMessage instanceof Map) {
                Map<?, ?> messageMap = (Map<?, ?>) originalMessage;
                Object fee = messageMap.get("platformFee");
                if (fee instanceof Number) {
                    return ((Number) fee).doubleValue();
                }
            }
        } catch (Exception e) {
            log.debug("Could not extract platformFee from message: {}", e.getMessage());
        }
        return null;
    }
}