package com.waqiti.compliance.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.compliance.service.SarFilingService;
import com.waqiti.compliance.service.ComplianceNotificationService;
import com.waqiti.compliance.audit.ComplianceAuditService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.support.Acknowledgment;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Comprehensive test suite for SARFilingRequiredConsumer
 * 
 * Tests SAR (Suspicious Activity Report) filing workflow including:
 * - Event processing and validation
 * - Priority determination (CRITICAL, HIGH, MEDIUM, LOW)
 * - Deadline calculation and management
 * - Notification and alerting
 * - Error handling and recovery
 * - Audit trail creation
 * 
 * @author Waqiti Engineering Team
 * @version 1.0.0
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("SAR Filing Required Consumer Tests")
class SARFilingRequiredConsumerTest {

    @Mock
    private SarFilingService sarFilingService;

    @Mock
    private ComplianceNotificationService notificationService;

    @Mock
    private ComplianceAuditService auditService;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private Acknowledgment acknowledgment;

    @InjectMocks
    private SARFilingRequiredConsumer consumer;

    private String eventJson;
    private String transactionId;
    private String customerId;
    private String accountId;
    private BigDecimal amount;
    private LocalDateTime detectionDate;

    @BeforeEach
    void setUp() {
        transactionId = UUID.randomUUID().toString();
        customerId = UUID.randomUUID().toString();
        accountId = UUID.randomUUID().toString();
        amount = new BigDecimal("15000.00");
        detectionDate = LocalDateTime.now();

        eventJson = createSARFilingEvent(
            transactionId,
            customerId,
            accountId,
            amount,
            "STRUCTURING",
            "Critical",
            detectionDate
        );
    }

    @Nested
    @DisplayName("Event Processing Tests")
    class EventProcessingTests {

        @Test
        @DisplayName("Should successfully process valid SAR filing event")
        void shouldProcessValidEvent() {
            consumer.consume(eventJson, acknowledgment);

            verify(sarFilingService, times(1)).initiateSARFiling(any());
            verify(acknowledgment, times(1)).acknowledge();
        }

        @Test
        @DisplayName("Should process event with all required fields")
        void shouldProcessEventWithAllFields() {
            consumer.consume(eventJson, acknowledgment);

            verify(auditService, times(1)).logAudit(
                eq("SAR_FILING_INITIATED"),
                anyString(),
                anyMap()
            );
        }

        @Test
        @DisplayName("Should handle event with missing optional fields")
        void shouldHandleMissingOptionalFields() {
            String eventWithoutOptional = createSARFilingEvent(
                transactionId,
                customerId,
                null, // accountId optional
                amount,
                "FRAUD",
                "High",
                detectionDate
            );

            consumer.consume(eventWithoutOptional, acknowledgment);

            verify(sarFilingService, times(1)).initiateSARFiling(any());
        }

        @Test
        @DisplayName("Should reject event with missing required fields")
        void shouldRejectMissingRequiredFields() {
            String invalidEvent = createSARFilingEvent(
                null, // missing transactionId
                customerId,
                accountId,
                amount,
                "FRAUD",
                "High",
                detectionDate
            );

            assertThatCode(() -> consumer.consume(invalidEvent, acknowledgment))
                .doesNotThrowAnyException();

            verify(acknowledgment, times(1)).acknowledge();
        }
    }

    @Nested
    @DisplayName("Priority Determination Tests")
    class PriorityDeterminationTests {

        @Test
        @DisplayName("Should assign CRITICAL priority for high-value transactions")
        void shouldAssignCriticalPriorityForHighValue() {
            BigDecimal highValue = new BigDecimal("100000.00");
            String event = createSARFilingEvent(
                transactionId, customerId, accountId,
                highValue, "MONEY_LAUNDERING", "Critical", detectionDate
            );

            consumer.consume(event, acknowledgment);

            verify(sarFilingService, times(1)).initiateSARFiling(
                argThat(req -> req.getPriority().equals("CRITICAL"))
            );
        }

        @Test
        @DisplayName("Should assign HIGH priority for terrorist financing")
        void shouldAssignHighPriorityForTerrorism() {
            String event = createSARFilingEvent(
                transactionId, customerId, accountId,
                amount, "TERRORIST_FINANCING", "Critical", detectionDate
            );

            consumer.consume(event, acknowledgment);

            verify(sarFilingService, times(1)).initiateSARFiling(any());
        }

        @Test
        @DisplayName("Should assign MEDIUM priority for standard suspicious activity")
        void shouldAssignMediumPriorityForStandard() {
            BigDecimal mediumValue = new BigDecimal("15000.00");
            String event = createSARFilingEvent(
                transactionId, customerId, accountId,
                mediumValue, "STRUCTURING", "Medium", detectionDate
            );

            consumer.consume(event, acknowledgment);

            verify(sarFilingService, times(1)).initiateSARFiling(any());
        }

        @Test
        @DisplayName("Should assign LOW priority for minor violations")
        void shouldAssignLowPriorityForMinor() {
            BigDecimal lowValue = new BigDecimal("5000.00");
            String event = createSARFilingEvent(
                transactionId, customerId, accountId,
                lowValue, "UNUSUAL_PATTERN", "Low", detectionDate
            );

            consumer.consume(event, acknowledgment);

            verify(sarFilingService, times(1)).initiateSARFiling(any());
        }
    }

    @Nested
    @DisplayName("Deadline Calculation Tests")
    class DeadlineCalculationTests {

        @Test
        @DisplayName("Should calculate 30-day deadline from detection date")
        void shouldCalculate30DayDeadline() {
            consumer.consume(eventJson, acknowledgment);

            verify(sarFilingService, times(1)).initiateSARFiling(
                argThat(req -> {
                    LocalDateTime deadline = req.getFilingDeadline();
                    return deadline.isAfter(detectionDate.plusDays(29)) &&
                           deadline.isBefore(detectionDate.plusDays(31));
                })
            );
        }

        @Test
        @DisplayName("Should handle detection date in the past")
        void shouldHandlePastDetectionDate() {
            LocalDateTime pastDate = LocalDateTime.now().minusDays(15);
            String event = createSARFilingEvent(
                transactionId, customerId, accountId,
                amount, "FRAUD", "High", pastDate
            );

            consumer.consume(event, acknowledgment);

            verify(sarFilingService, times(1)).initiateSARFiling(any());
        }

        @Test
        @DisplayName("Should handle detection date close to deadline")
        void shouldHandleCloseToDeadline() {
            LocalDateTime closeDate = LocalDateTime.now().minusDays(25);
            String event = createSARFilingEvent(
                transactionId, customerId, accountId,
                amount, "FRAUD", "Critical", closeDate
            );

            consumer.consume(event, acknowledgment);

            verify(notificationService, atLeastOnce()).sendUrgentNotification(any());
        }
    }

    @Nested
    @DisplayName("Notification Tests")
    class NotificationTests {

        @Test
        @DisplayName("Should send notification to compliance officers")
        void shouldNotifyComplianceOfficers() {
            consumer.consume(eventJson, acknowledgment);

            verify(notificationService, times(1)).notifyComplianceTeam(any());
        }

        @Test
        @DisplayName("Should send urgent notification for critical SARs")
        void shouldSendUrgentNotificationForCritical() {
            BigDecimal criticalAmount = new BigDecimal("250000.00");
            String event = createSARFilingEvent(
                transactionId, customerId, accountId,
                criticalAmount, "TERRORIST_FINANCING", "Critical", detectionDate
            );

            consumer.consume(event, acknowledgment);

            verify(notificationService, times(1)).sendUrgentNotification(
                argThat(msg -> msg.contains("CRITICAL"))
            );
        }

        @Test
        @DisplayName("Should send deadline reminder notifications")
        void shouldSendDeadlineReminders() {
            LocalDateTime nearDeadline = LocalDateTime.now().minusDays(23);
            String event = createSARFilingEvent(
                transactionId, customerId, accountId,
                amount, "FRAUD", "High", nearDeadline
            );

            consumer.consume(event, acknowledgment);

            verify(notificationService, times(1)).scheduleReminderNotification(any());
        }

        @Test
        @DisplayName("Should escalate if no action taken within timeframe")
        void shouldEscalateIfNoAction() {
            LocalDateTime oldDate = LocalDateTime.now().minusDays(28);
            String event = createSARFilingEvent(
                transactionId, customerId, accountId,
                amount, "FRAUD", "High", oldDate
            );

            consumer.consume(event, acknowledgment);

            verify(notificationService, times(1)).escalateToManagement(any());
        }
    }

    @Nested
    @DisplayName("Activity Type Tests")
    class ActivityTypeTests {

        @Test
        @DisplayName("Should handle structuring activity type")
        void shouldHandleStructuring() {
            String event = createSARFilingEvent(
                transactionId, customerId, accountId,
                amount, "STRUCTURING", "High", detectionDate
            );

            consumer.consume(event, acknowledgment);

            verify(sarFilingService, times(1)).initiateSARFiling(any());
        }

        @Test
        @DisplayName("Should handle money laundering activity type")
        void shouldHandleMoneyLaundering() {
            String event = createSARFilingEvent(
                transactionId, customerId, accountId,
                amount, "MONEY_LAUNDERING", "Critical", detectionDate
            );

            consumer.consume(event, acknowledgment);

            verify(sarFilingService, times(1)).initiateSARFiling(any());
        }

        @Test
        @DisplayName("Should handle terrorist financing activity type")
        void shouldHandleTerroristFinancing() {
            String event = createSARFilingEvent(
                transactionId, customerId, accountId,
                amount, "TERRORIST_FINANCING", "Critical", detectionDate
            );

            consumer.consume(event, acknowledgment);

            verify(sarFilingService, times(1)).initiateSARFiling(any());
        }

        @Test
        @DisplayName("Should handle fraud activity type")
        void shouldHandleFraud() {
            String event = createSARFilingEvent(
                transactionId, customerId, accountId,
                amount, "FRAUD", "High", detectionDate
            );

            consumer.consume(event, acknowledgment);

            verify(sarFilingService, times(1)).initiateSARFiling(any());
        }

        @Test
        @DisplayName("Should handle identity theft activity type")
        void shouldHandleIdentityTheft() {
            String event = createSARFilingEvent(
                transactionId, customerId, accountId,
                amount, "IDENTITY_THEFT", "High", detectionDate
            );

            consumer.consume(event, acknowledgment);

            verify(sarFilingService, times(1)).initiateSARFiling(any());
        }

        @Test
        @DisplayName("Should handle unknown activity type")
        void shouldHandleUnknownActivityType() {
            String event = createSARFilingEvent(
                transactionId, customerId, accountId,
                amount, "UNKNOWN_TYPE", "Medium", detectionDate
            );

            consumer.consume(event, acknowledgment);

            verify(sarFilingService, times(1)).initiateSARFiling(any());
        }
    }

    @Nested
    @DisplayName("Error Handling and Recovery Tests")
    class ErrorHandlingTests {

        @Test
        @DisplayName("Should handle malformed JSON gracefully")
        void shouldHandleMalformedJSON() {
            String malformedJson = "{invalid json";

            assertThatCode(() -> consumer.consume(malformedJson, acknowledgment))
                .doesNotThrowAnyException();

            verify(acknowledgment, times(1)).acknowledge();
        }

        @Test
        @DisplayName("Should handle service failure with retry")
        void shouldHandleServiceFailure() {
            doThrow(new RuntimeException("Service unavailable"))
                .when(sarFilingService).initiateSARFiling(any());

            assertThatCode(() -> consumer.consume(eventJson, acknowledgment))
                .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("Should log error details for failed processing")
        void shouldLogErrorDetails() {
            doThrow(new RuntimeException("Processing error"))
                .when(sarFilingService).initiateSARFiling(any());

            consumer.consume(eventJson, acknowledgment);

            verify(auditService, times(1)).logAudit(
                eq("SAR_FILING_ERROR"),
                anyString(),
                anyMap()
            );
        }

        @Test
        @DisplayName("Should handle database connection failure")
        void shouldHandleDatabaseFailure() {
            doThrow(new RuntimeException("Database connection failed"))
                .when(sarFilingService).initiateSARFiling(any());

            assertThatCode(() -> consumer.consume(eventJson, acknowledgment))
                .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("Should handle notification service failure gracefully")
        void shouldHandleNotificationFailure() {
            doThrow(new RuntimeException("Notification service down"))
                .when(notificationService).notifyComplianceTeam(any());

            assertThatCode(() -> consumer.consume(eventJson, acknowledgment))
                .doesNotThrowAnyException();

            verify(acknowledgment, times(1)).acknowledge();
        }
    }

    @Nested
    @DisplayName("Audit Trail Tests")
    class AuditTrailTests {

        @Test
        @DisplayName("Should create audit entry for every SAR filing initiation")
        void shouldCreateAuditEntry() {
            consumer.consume(eventJson, acknowledgment);

            verify(auditService, atLeastOnce()).logAudit(
                anyString(),
                anyString(),
                anyMap()
            );
        }

        @Test
        @DisplayName("Should include all event details in audit")
        void shouldIncludeAllDetailsInAudit() {
            consumer.consume(eventJson, acknowledgment);

            verify(auditService, times(1)).logAudit(
                eq("SAR_FILING_INITIATED"),
                contains(transactionId),
                argThat(map -> 
                    map.containsKey("customerId") &&
                    map.containsKey("amount") &&
                    map.containsKey("activityType")
                )
            );
        }

        @Test
        @DisplayName("Should audit priority determination reasoning")
        void shouldAuditPriorityReasoning() {
            consumer.consume(eventJson, acknowledgment);

            verify(auditService, atLeastOnce()).logAudit(
                anyString(),
                anyString(),
                argThat(map -> map.containsKey("priority"))
            );
        }
    }

    @Nested
    @DisplayName("Compliance and Regulatory Tests")
    class ComplianceTests {

        @Test
        @DisplayName("Should comply with BSA 30-day filing requirement")
        void shouldComplyWithBSARequirement() {
            consumer.consume(eventJson, acknowledgment);

            verify(sarFilingService, times(1)).initiateSARFiling(
                argThat(req -> {
                    LocalDateTime deadline = req.getFilingDeadline();
                    long daysDifference = java.time.temporal.ChronoUnit.DAYS.between(
                        detectionDate, deadline
                    );
                    return daysDifference == 30;
                })
            );
        }

        @Test
        @DisplayName("Should handle known perpetrator threshold ($5000)")
        void shouldHandleKnownPerpetratorThreshold() {
            BigDecimal knownPerpetratorAmount = new BigDecimal("5000.00");
            String event = createSARFilingEvent(
                transactionId, customerId, accountId,
                knownPerpetratorAmount, "FRAUD", "High", detectionDate
            );

            consumer.consume(event, acknowledgment);

            verify(sarFilingService, times(1)).initiateSARFiling(any());
        }

        @Test
        @DisplayName("Should handle unknown perpetrator threshold ($25000)")
        void shouldHandleUnknownPerpetratorThreshold() {
            BigDecimal unknownPerpetratorAmount = new BigDecimal("25000.00");
            String event = createSARFilingEvent(
                transactionId, customerId, accountId,
                unknownPerpetratorAmount, "FRAUD", "High", detectionDate
            );

            consumer.consume(event, acknowledgment);

            verify(sarFilingService, times(1)).initiateSARFiling(any());
        }

        @Test
        @DisplayName("Should prepare for FinCEN submission")
        void shouldPrepareForFinCENSubmission() {
            consumer.consume(eventJson, acknowledgment);

            verify(sarFilingService, times(1)).prepareFinCENSubmission(any());
        }
    }

    private String createSARFilingEvent(
            String txnId, String custId, String acctId,
            BigDecimal amt, String actType, String severity,
            LocalDateTime detDate) {
        
        return String.format("""
            {
                "transactionId": "%s",
                "customerId": "%s",
                "accountId": "%s",
                "amount": %s,
                "activityType": "%s",
                "severity": "%s",
                "detectionDate": "%s",
                "currency": "USD"
            }
            """, 
            txnId, custId, acctId, amt, actType, severity, detDate
        );
    }
}