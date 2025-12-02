package com.waqiti.analytics.service;

import com.waqiti.analytics.client.NotificationServiceClient;
import com.waqiti.analytics.client.PagerDutyClient;
import com.waqiti.analytics.client.SlackClient;
import com.waqiti.analytics.dto.notification.NotificationRequest;
import com.waqiti.analytics.dto.pagerduty.PagerDutyEvent;
import com.waqiti.analytics.dto.slack.SlackMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit Tests for NotificationService
 *
 * Tests notification routing, channel selection, and fallback behavior.
 * Uses Mockito to verify interactions with external services.
 *
 * @author Waqiti Analytics Team
 * @version 1.0.0-PRODUCTION
 * @since 2025-11-15
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("NotificationService Unit Tests")
class NotificationServiceTest {

    @Mock
    private NotificationServiceClient notificationServiceClient;

    @Mock
    private PagerDutyClient pagerDutyClient;

    @Mock
    private SlackClient slackClient;

    @InjectMocks
    private NotificationService notificationService;

    @Captor
    private ArgumentCaptor<NotificationRequest> notificationRequestCaptor;

    @Captor
    private ArgumentCaptor<PagerDutyEvent> pagerDutyEventCaptor;

    @Captor
    private ArgumentCaptor<SlackMessage> slackMessageCaptor;

    @BeforeEach
    void setUp() {
        // Set configuration values using reflection
        ReflectionTestUtils.setField(notificationService, "pagerDutyEnabled", true);
        ReflectionTestUtils.setField(notificationService, "slackEnabled", true);
        ReflectionTestUtils.setField(notificationService, "pagerDutyRoutingKey", "test-routing-key");
        ReflectionTestUtils.setField(notificationService, "analyticsAlertsChannel", "#analytics-alerts");
        ReflectionTestUtils.setField(notificationService, "operationsChannel", "#operations");
        ReflectionTestUtils.setField(notificationService, "escalationsChannel", "#escalations");
    }

    @Test
    @DisplayName("Should send resolution notification via notification-service and Slack")
    void shouldSendResolutionNotification() {
        // Given
        UUID alertId = UUID.randomUUID();
        String resolutionMethod = "Automatic retry";
        String resolvedBy = "system";
        String correlationId = UUID.randomUUID().toString();

        // When
        notificationService.sendResolutionNotification(alertId, resolutionMethod, resolvedBy, correlationId);

        // Then
        verify(notificationServiceClient).sendNotification(notificationRequestCaptor.capture());
        NotificationRequest request = notificationRequestCaptor.getValue();

        assertThat(request.getCorrelationId()).isEqualTo(correlationId);
        assertThat(request.getType()).isEqualTo(NotificationRequest.NotificationType.ALERT_RESOLVED);
        assertThat(request.getPriority()).isEqualTo(NotificationRequest.NotificationPriority.MEDIUM);
        assertThat(request.getSubject()).contains(alertId.toString());

        verify(slackClient).postMessage(any(SlackMessage.class));
    }

    @Test
    @DisplayName("Should send escalation notification with PagerDuty for Tier 2+")
    void shouldSendEscalationNotificationWithPagerDuty() {
        // Given
        UUID alertId = UUID.randomUUID();
        Integer escalationTier = 2;
        String reason = "Persistent high-severity anomaly";
        String correlationId = UUID.randomUUID().toString();

        // When
        notificationService.notifyEscalationTeam(alertId, escalationTier, reason, correlationId);

        // Then
        verify(notificationServiceClient).sendNotification(notificationRequestCaptor.capture());
        NotificationRequest request = notificationRequestCaptor.getValue();

        assertThat(request.getType()).isEqualTo(NotificationRequest.NotificationType.ESCALATION);
        assertThat(request.getPriority()).isEqualTo(NotificationRequest.NotificationPriority.CRITICAL);
        assertThat(request.getChannels()).contains(
            NotificationRequest.NotificationChannel.EMAIL,
            NotificationRequest.NotificationChannel.SMS
        );

        // Verify PagerDuty incident created for Tier 2
        verify(pagerDutyClient).triggerEvent(pagerDutyEventCaptor.capture());
        PagerDutyEvent event = pagerDutyEventCaptor.getValue();

        assertThat(event.getRoutingKey()).isEqualTo("test-routing-key");
        assertThat(event.getEventAction()).isEqualTo("trigger");
        assertThat(event.getDedupKey()).isEqualTo(alertId.toString());
        assertThat(event.getPayload().getSeverity()).isEqualTo("critical");

        verify(slackClient).postMessage(any(SlackMessage.class));
    }

    @Test
    @DisplayName("Should NOT trigger PagerDuty for Tier 1 escalation")
    void shouldNotTriggerPagerDutyForTier1() {
        // Given
        UUID alertId = UUID.randomUUID();
        Integer escalationTier = 1;
        String reason = "Minor issue";
        String correlationId = UUID.randomUUID().toString();

        // When
        notificationService.notifyEscalationTeam(alertId, escalationTier, reason, correlationId);

        // Then
        verify(notificationServiceClient).sendNotification(any(NotificationRequest.class));
        verify(pagerDutyClient, never()).triggerEvent(any(PagerDutyEvent.class));
        verify(slackClient).postMessage(any(SlackMessage.class));
    }

    @Test
    @DisplayName("Should send DLQ alert with appropriate severity")
    void shouldSendDlqAlert() {
        // Given
        String topic = "analytics.events";
        String correlationId = UUID.randomUUID().toString();
        String failureReason = "Invalid JSON payload";
        int retryCount = 2;
        String severity = "HIGH";

        // When
        notificationService.sendDlqAlert(topic, correlationId, failureReason, retryCount, severity);

        // Then
        verify(notificationServiceClient).sendNotification(notificationRequestCaptor.capture());
        NotificationRequest request = notificationRequestCaptor.getValue();

        assertThat(request.getType()).isEqualTo(NotificationRequest.NotificationType.DLQ_ALERT);
        assertThat(request.getPriority()).isEqualTo(NotificationRequest.NotificationPriority.HIGH);
        assertThat(request.getSubject()).contains("HIGH");
        assertThat(request.getSubject()).contains(topic);

        // Verify Slack alert sent for HIGH severity
        verify(slackClient).postMessage(any(SlackMessage.class));
    }

    @Test
    @DisplayName("Should NOT send Slack alert for LOW severity DLQ")
    void shouldNotSendSlackForLowSeverityDlq() {
        // Given
        String topic = "analytics.events";
        String correlationId = UUID.randomUUID().toString();
        String failureReason = "Transient network error";
        int retryCount = 1;
        String severity = "LOW";

        // When
        notificationService.sendDlqAlert(topic, correlationId, failureReason, retryCount, severity);

        // Then
        verify(notificationServiceClient).sendNotification(any(NotificationRequest.class));
        verify(slackClient, never()).postMessage(any(SlackMessage.class));
    }

    @Test
    @DisplayName("Should send critical DLQ alert with PagerDuty")
    void shouldSendCriticalDlqAlert() {
        // Given
        String topic = "analytics.critical";
        String correlationId = UUID.randomUUID().toString();
        String error = "Database connection failed";

        // When
        notificationService.sendCriticalDlqAlert(topic, correlationId, error);

        // Then
        verify(notificationServiceClient).sendNotification(notificationRequestCaptor.capture());
        NotificationRequest request = notificationRequestCaptor.getValue();

        assertThat(request.getType()).isEqualTo(NotificationRequest.NotificationType.CRITICAL_ALERT);
        assertThat(request.getPriority()).isEqualTo(NotificationRequest.NotificationPriority.CRITICAL);
        assertThat(request.getChannels()).contains(
            NotificationRequest.NotificationChannel.EMAIL,
            NotificationRequest.NotificationChannel.SMS,
            NotificationRequest.NotificationChannel.PUSH
        );

        verify(pagerDutyClient).triggerEvent(any(PagerDutyEvent.class));
        verify(slackClient).postMessage(any(SlackMessage.class));
    }

    @Test
    @DisplayName("Should handle notification-service failure gracefully")
    void shouldHandleNotificationServiceFailure() {
        // Given
        UUID alertId = UUID.randomUUID();
        doThrow(new RuntimeException("Service unavailable"))
            .when(notificationServiceClient).sendNotification(any());

        // When/Then - should not throw exception
        notificationService.sendFailureNotification(
            alertId,
            "Test failure",
            UUID.randomUUID().toString()
        );

        verify(notificationServiceClient).sendNotification(any());
        // Slack should still be attempted
        verify(slackClient).postMessage(any(SlackMessage.class));
    }

    @Test
    @DisplayName("Should map severity to priority correctly")
    void shouldMapSeverityToPriority() {
        // This is tested indirectly through sendDlqAlert
        String[] severities = {"CRITICAL", "HIGH", "MEDIUM", "LOW"};
        NotificationRequest.NotificationPriority[] expectedPriorities = {
            NotificationRequest.NotificationPriority.CRITICAL,
            NotificationRequest.NotificationPriority.HIGH,
            NotificationRequest.NotificationPriority.MEDIUM,
            NotificationRequest.NotificationPriority.LOW
        };

        for (int i = 0; i < severities.length; i++) {
            reset(notificationServiceClient);

            notificationService.sendDlqAlert(
                "test.topic",
                UUID.randomUUID().toString(),
                "test",
                1,
                severities[i]
            );

            verify(notificationServiceClient).sendNotification(notificationRequestCaptor.capture());
            assertThat(notificationRequestCaptor.getValue().getPriority())
                .isEqualTo(expectedPriorities[i]);
        }
    }
}
