package com.waqiti.frauddetection.kafka;

import com.waqiti.common.dlq.BaseDlqConsumer;
import com.waqiti.common.dlq.DlqHandler;
import com.waqiti.common.audit.AuditService;
import com.waqiti.common.notification.NotificationService;
import com.waqiti.frauddetection.service.FraudReportingService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.micrometer.core.instrument.MeterRegistry;

import java.time.Instant;
import java.util.Map;

/**
 * DLQ Consumer for fraud reporting failures.
 * Handles critical fraud reporting processing errors with immediate compliance escalation.
 */
@Component
@Slf4j
public class FraudReportingDlqConsumer extends BaseDlqConsumer {

    private final FraudReportingService fraudReportingService;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    public FraudReportingDlqConsumer(DlqHandler dlqHandler,
                                   AuditService auditService,
                                   NotificationService notificationService,
                                   MeterRegistry meterRegistry,
                                   FraudReportingService fraudReportingService,
                                   KafkaTemplate<String, Object> kafkaTemplate) {
        super(dlqHandler, auditService, notificationService, meterRegistry);
        this.fraudReportingService = fraudReportingService;
        this.kafkaTemplate = kafkaTemplate;
    }

    @KafkaListener(
        topics = {"fraud-reporting.DLQ"},
        groupId = "fraud-reporting-dlq-consumer-group",
        containerFactory = "criticalFraudKafkaListenerContainerFactory"
    )
    @DltHandler
    @Transactional
    @CircuitBreaker(name = "fraud-reporting-dlq", fallbackMethod = "handleFraudReportingDlqFallback")
    public void handleFraudReportingDlq(@Payload Object originalMessage,
                                      @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
                                      @Header(KafkaHeaders.EXCEPTION_MESSAGE) String exceptionMessage,
                                      @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
                                      @Header(KafkaHeaders.OFFSET) long offset,
                                      Acknowledgment acknowledgment,
                                      @Header Map<String, Object> headers) {

        super.handleDlqMessage(originalMessage, topic, exceptionMessage, partition, offset, acknowledgment, headers);
    }

    @Override
    protected void processDomainSpecificLogic(Object originalMessage, String topic,
                                            String exceptionMessage, String messageId) {
        try {
            String reportId = extractReportId(originalMessage);
            String reportType = extractReportType(originalMessage);
            String reportingPeriod = extractReportingPeriod(originalMessage);

            generateDomainSpecificAlerts(originalMessage, topic, exceptionMessage, messageId);

            kafkaTemplate.send("manual-fraud-reporting-review-queue", Map.of(
                "reportId", reportId != null ? reportId : "unknown",
                "reportType", reportType,
                "reportingPeriod", reportingPeriod,
                "reviewReason", "FRAUD_REPORTING_DLQ_FAILURE",
                "priority", "HIGH",
                "messageId", messageId,
                "timestamp", Instant.now()
            ));

        } catch (Exception e) {
            log.error("Error in fraud reporting DLQ processing: messageId={}, error={}", messageId, e.getMessage(), e);
            throw e;
        }
    }

    @Override
    protected String getConsumerName() {
        return "fraud-reporting-dlq";
    }

    @Override
    protected String getBusinessDomain() {
        return "FRAUD_REPORTING";
    }

    @Override
    protected boolean isCriticalBusinessImpact(Object originalMessage, String topic) {
        String reportType = extractReportType(originalMessage);
        return reportType != null && (reportType.contains("SAR") || reportType.contains("REGULATORY"));
    }

    @Override
    protected void generateDomainSpecificAlerts(Object originalMessage, String topic,
                                              String exceptionMessage, String messageId) {
        String reportId = extractReportId(originalMessage);
        String reportType = extractReportType(originalMessage);
        String reportingPeriod = extractReportingPeriod(originalMessage);

        try {
            String alertMessage = String.format(
                "🚨 FRAUD REPORTING FAILURE 🚨\n\n" +
                "Report ID: %s\nType: %s\nPeriod: %s\nError: %s\n\n" +
                "IMMEDIATE fraud reporting and compliance escalation required.",
                reportId != null ? reportId : "unknown",
                reportType != null ? reportType : "unknown",
                reportingPeriod != null ? reportingPeriod : "unknown",
                exceptionMessage
            );

            notificationService.sendFraudAlert("CRITICAL: Fraud Reporting Failed", alertMessage, "CRITICAL");
            notificationService.sendComplianceAlert("Fraud Reporting Compliance Risk", alertMessage, "HIGH");

            if (reportType != null && reportType.contains("SAR")) {
                notificationService.sendRegulatoryAlert("SAR Reporting Failed", alertMessage, "CRITICAL");
            }

        } catch (Exception e) {
            log.error("Failed to send fraud reporting DLQ notifications: {}", e.getMessage());
        }
    }

    public void handleFraudReportingDlqFallback(Object originalMessage, String topic, String exceptionMessage,
                                              int partition, long offset, Acknowledgment acknowledgment, Exception ex) {
        super.handleDlqFallback(originalMessage, topic, exceptionMessage, partition, offset, acknowledgment, ex);

        notificationService.sendExecutiveAlert(
            "EMERGENCY: Fraud Reporting DLQ Circuit Breaker",
            "CRITICAL: Fraud reporting systems circuit breaker triggered. IMMEDIATE COMPLIANCE ESCALATION REQUIRED."
        );
    }

    // Data extraction methods
    private String extractReportId(Object originalMessage) {
        try {
            if (originalMessage instanceof Map) {
                Map<?, ?> messageMap = (Map<?, ?>) originalMessage;
                Object id = messageMap.get("reportId");
                if (id == null) id = messageMap.get("id");
                return id != null ? id.toString() : null;
            }
        } catch (Exception e) {
            log.debug("Could not extract reportId: {}", e.getMessage());
        }
        return null;
    }

    private String extractReportType(Object originalMessage) {
        try {
            if (originalMessage instanceof Map) {
                Map<?, ?> messageMap = (Map<?, ?>) originalMessage;
                Object type = messageMap.get("reportType");
                if (type == null) type = messageMap.get("type");
                return type != null ? type.toString() : null;
            }
        } catch (Exception e) {
            log.debug("Could not extract reportType: {}", e.getMessage());
        }
        return null;
    }

    private String extractReportingPeriod(Object originalMessage) {
        try {
            if (originalMessage instanceof Map) {
                Map<?, ?> messageMap = (Map<?, ?>) originalMessage;
                Object period = messageMap.get("reportingPeriod");
                if (period == null) period = messageMap.get("period");
                return period != null ? period.toString() : null;
            }
        } catch (Exception e) {
            log.debug("Could not extract reportingPeriod: {}", e.getMessage());
        }
        return null;
    }
}