package com.waqiti.common.alerting;

import com.waqiti.common.alerting.client.PagerDutyClient;
import com.waqiti.common.alerting.client.SlackClient;
import com.waqiti.common.alerting.model.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Comprehensive unit tests for AlertingService
 *
 * Test Coverage:
 * - Alert sending (all severity levels)
 * - Multi-channel delivery
 * - Alert deduplication
 * - Maintenance mode
 * - Alert suppression
 * - Error handling
 * - Convenience methods
 * - Statistics tracking
 *
 * @author Waqiti Platform Team
 * @version 1.0.0
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AlertingService Tests")
class AlertingServiceTest {

    @Mock
    private PagerDutyClient pagerDutyClient;

    @Mock
    private SlackClient slackClient;

    @Mock
    private AlertingConfiguration config;

    @Mock
    private AlertDeduplicationService deduplicationService;

    @Mock
    private AlertAuditService auditService;

    @InjectMocks
    private AlertingService alertingService;

    @Captor
    private ArgumentCaptor<Alert> alertCaptor;

    private Alert testAlert;

    @BeforeEach
    void setUp() {
        // Configure default mock behavior
        when(config.isPagerDutyEnabled()).thenReturn(true);
        when(config.isSlackEnabled()).thenReturn(true);
        when(config.getDeduplicationWindowSeconds()).thenReturn(300);

        // Create test alert
        testAlert = Alert.builder()
            .id("test-alert-123")
            .severity(AlertSeverity.ERROR)
            .source("test-service")
            .title("Test Alert")
            .message("This is a test alert")
            .metadata(Map.of("testKey", "testValue"))
            .createdAt(Instant.now())
            .build();

        // Mock successful delivery
        when(pagerDutyClient.createIncident(any(Alert.class)))
            .thenReturn(CompletableFuture.completedFuture("incident-123"));
        when(slackClient.sendAlert(any(Alert.class)))
            .thenReturn(CompletableFuture.completedFuture("message-123"));
    }

    @Nested
    @DisplayName("Critical Alert Tests")
    class CriticalAlertTests {

        @Test
        @DisplayName("Should send critical alert to PagerDuty and Slack")
        void shouldSendCriticalAlertToBothChannels() throws Exception {
            // Arrange
            when(deduplicationService.shouldDeduplicate(any())).thenReturn(false);

            // Act
            CompletableFuture<String> result = alertingService.sendCriticalAlert(
                "Database Down",
                "PostgreSQL is unavailable",
                "payment-service",
                Map.of("database", "payments-db")
            );

            // Assert
            assertNotNull(result);
            String alertId = result.get();
            assertNotNull(alertId);

            // Verify PagerDuty called
            verify(pagerDutyClient, times(1)).createIncident(alertCaptor.capture());
            Alert sentAlert = alertCaptor.getValue();
            assertEquals(AlertSeverity.CRITICAL, sentAlert.getSeverity());
            assertEquals("Database Down", sentAlert.getTitle());

            // Verify Slack called
            verify(slackClient, times(1)).sendAlert(any(Alert.class));

            // Verify audit logged
            verify(auditService, times(1)).logAlertDelivered(any(), eq("pagerduty"), anyString());
            verify(auditService, times(1)).logAlertDelivered(any(), eq("slack"), anyString());
        }

        @Test
        @DisplayName("Should handle PagerDuty failure gracefully")
        void shouldHandlePagerDutyFailure() throws Exception {
            // Arrange
            when(deduplicationService.shouldDeduplicate(any())).thenReturn(false);
            when(pagerDutyClient.createIncident(any()))
                .thenReturn(CompletableFuture.failedFuture(new RuntimeException("PagerDuty unavailable")));

            // Act
            CompletableFuture<String> result = alertingService.sendCriticalAlert(
                "Test Alert",
                "Test Description",
                "test-service",
                Map.of()
            );

            // Assert
            assertNotNull(result);
            String alertId = result.get();
            assertNotNull(alertId);

            // Verify Slack still called (fallback)
            verify(slackClient, times(1)).sendAlert(any(Alert.class));

            // Verify failure logged
            verify(auditService, times(1)).logAlertDeliveryFailed(any(), eq("pagerduty"), anyString());
        }
    }

    @Nested
    @DisplayName("Error Alert Tests")
    class ErrorAlertTests {

        @Test
        @DisplayName("Should send error alert with proper severity")
        void shouldSendErrorAlert() throws Exception {
            // Arrange
            when(deduplicationService.shouldDeduplicate(any())).thenReturn(false);

            // Act
            CompletableFuture<String> result = alertingService.sendErrorAlert(
                "High Error Rate",
                "Error rate is 5%",
                "api-gateway",
                Map.of("errorRate", 0.05)
            );

            // Assert
            assertNotNull(result);
            verify(slackClient, times(1)).sendAlert(alertCaptor.capture());
            Alert sentAlert = alertCaptor.getValue();
            assertEquals(AlertSeverity.ERROR, sentAlert.getSeverity());
        }
    }

    @Nested
    @DisplayName("Warning Alert Tests")
    class WarningAlertTests {

        @Test
        @DisplayName("Should send warning alert to Slack only")
        void shouldSendWarningAlertToSlackOnly() throws Exception {
            // Arrange
            when(deduplicationService.shouldDeduplicate(any())).thenReturn(false);

            // Act
            CompletableFuture<String> result = alertingService.sendWarningAlert(
                "High Latency",
                "P99 latency is 500ms",
                "payment-service",
                Map.of("p99", 500)
            );

            // Assert
            assertNotNull(result);
            verify(slackClient, times(1)).sendAlert(any(Alert.class));
            verify(pagerDutyClient, never()).createIncident(any());
        }
    }

    @Nested
    @DisplayName("Deduplication Tests")
    class DeduplicationTests {

        @Test
        @DisplayName("Should deduplicate identical alerts")
        void shouldDeduplicateIdenticalAlerts() throws Exception {
            // Arrange
            when(deduplicationService.shouldDeduplicate(any())).thenReturn(true);

            // Act
            CompletableFuture<String> result = alertingService.sendCriticalAlert(
                "Duplicate Alert",
                "This alert is duplicate",
                "test-service",
                Map.of()
            );

            // Assert
            assertNotNull(result);
            verify(deduplicationService, times(1)).isDuplicate(any());
            verify(auditService, times(1)).logAlertDeduplicated(any());
            verify(pagerDutyClient, never()).createIncident(any());
            verify(slackClient, never()).sendAlert(any());
        }

        @Test
        @DisplayName("Should send alert when not duplicate")
        void shouldSendAlertWhenNotDuplicate() throws Exception {
            // Arrange
            when(deduplicationService.shouldDeduplicate(any())).thenReturn(false);

            // Act
            alertingService.sendCriticalAlert(
                "Unique Alert",
                "This is unique",
                "test-service",
                Map.of()
            );

            // Assert
            verify(deduplicationService, times(1)).isDuplicate(any());
            verify(slackClient, times(1)).sendAlert(any());
        }
    }

    @Nested
    @DisplayName("Maintenance Mode Tests")
    class MaintenanceModeTests {

        @Test
        @DisplayName("Should suppress non-critical alerts in maintenance mode")
        void shouldSuppressNonCriticalAlertsInMaintenanceMode() {
            // Arrange
            alertingService.enableMaintenanceMode("Scheduled maintenance", 60);

            // Act
            alertingService.sendWarningAlert(
                "Test Warning",
                "Should be suppressed",
                "test-service",
                Map.of()
            );

            // Assert
            verify(slackClient, never()).sendAlert(any());
            verify(auditService, times(1)).logAlertSuppressed(any(), eq("maintenance_mode"));
        }

        @Test
        @DisplayName("Should allow critical alerts in maintenance mode")
        void shouldAllowCriticalAlertsInMaintenanceMode() throws Exception {
            // Arrange
            when(deduplicationService.shouldDeduplicate(any())).thenReturn(false);
            alertingService.enableMaintenanceMode("Scheduled maintenance", 60);

            // Act
            alertingService.sendCriticalAlert(
                "Critical Issue",
                "Even in maintenance",
                "test-service",
                Map.of()
            );

            // Assert
            verify(slackClient, times(1)).sendAlert(any());
        }

        @Test
        @DisplayName("Should disable maintenance mode")
        void shouldDisableMaintenanceMode() throws Exception {
            // Arrange
            when(deduplicationService.shouldDeduplicate(any())).thenReturn(false);
            alertingService.enableMaintenanceMode("Test", 60);
            alertingService.disableMaintenanceMode("Test complete");

            // Act
            alertingService.sendWarningAlert(
                "Test Warning",
                "Should be sent",
                "test-service",
                Map.of()
            );

            // Assert
            verify(slackClient, times(1)).sendAlert(any());
        }
    }

    @Nested
    @DisplayName("Alert Suppression Tests")
    class AlertSuppressionTests {

        @Test
        @DisplayName("Should suppress alerts by type")
        void shouldSuppressAlertsByType() {
            // Arrange
            alertingService.suppressAlertType("test-service", 60);

            // Act
            alertingService.sendWarningAlert(
                "Test Alert",
                "Should be suppressed",
                "test-service",
                Map.of()
            );

            // Assert
            verify(slackClient, never()).sendAlert(any());
            verify(auditService, times(1)).logAlertSuppressed(any(), eq("type_suppressed"));
        }

        @Test
        @DisplayName("Should remove alert type suppression")
        void shouldRemoveAlertTypeSuppression() throws Exception {
            // Arrange
            when(deduplicationService.shouldDeduplicate(any())).thenReturn(false);
            alertingService.suppressAlertType("test-service", 60);
            alertingService.unsuppressAlertType("test-service");

            // Act
            alertingService.sendWarningAlert(
                "Test Alert",
                "Should be sent",
                "test-service",
                Map.of()
            );

            // Assert
            verify(slackClient, times(1)).sendAlert(any());
        }
    }

    @Nested
    @DisplayName("Convenience Method Tests")
    class ConvenienceMethodTests {

        @Test
        @DisplayName("Should alert on payment failure")
        void shouldAlertOnPaymentFailure() throws Exception {
            // Arrange
            when(deduplicationService.shouldDeduplicate(any())).thenReturn(false);

            // Act
            CompletableFuture<String> result = alertingService.alertPaymentFailure(
                "pay-123",
                "Insufficient funds",
                100.00,
                "USD"
            );

            // Assert
            assertNotNull(result);
            verify(slackClient, times(1)).sendAlert(alertCaptor.capture());
            Alert alert = alertCaptor.getValue();
            assertTrue(alert.getTitle().contains("Payment"));
            assertEquals("payment-service", alert.getSource());
        }

        @Test
        @DisplayName("Should alert on fraud detection")
        void shouldAlertOnFraudDetection() throws Exception {
            // Arrange
            when(deduplicationService.shouldDeduplicate(any())).thenReturn(false);

            // Act
            CompletableFuture<String> result = alertingService.alertFraudDetected(
                "txn-456",
                "user-789",
                "VELOCITY_ABUSE",
                0.95
            );

            // Assert
            assertNotNull(result);
            verify(pagerDutyClient, times(1)).createIncident(alertCaptor.capture());
            Alert alert = alertCaptor.getValue();
            assertEquals(AlertSeverity.CRITICAL, alert.getSeverity());
            assertTrue(alert.getTitle().contains("Fraud"));
        }

        @Test
        @DisplayName("Should alert on compliance violation")
        void shouldAlertOnComplianceViolation() throws Exception {
            // Arrange
            when(deduplicationService.shouldDeduplicate(any())).thenReturn(false);

            // Act
            alertingService.alertComplianceViolation(
                "AML_THRESHOLD_EXCEEDED",
                "user-123",
                "Transaction exceeds daily limit"
            );

            // Assert
            verify(pagerDutyClient, times(1)).createIncident(alertCaptor.capture());
            Alert alert = alertCaptor.getValue();
            assertEquals(AlertSeverity.CRITICAL, alert.getSeverity());
        }

        @Test
        @DisplayName("Should alert on database failure")
        void shouldAlertOnDatabaseFailure() throws Exception {
            // Arrange
            when(deduplicationService.shouldDeduplicate(any())).thenReturn(false);

            // Act
            alertingService.alertDatabaseFailure(
                "payments-db",
                "Connection timeout"
            );

            // Assert
            verify(pagerDutyClient, times(1)).createIncident(any());
        }

        @Test
        @DisplayName("Should alert on Kafka lag")
        void shouldAlertOnKafkaLag() throws Exception {
            // Arrange
            when(deduplicationService.shouldDeduplicate(any())).thenReturn(false);

            // Act
            alertingService.alertKafkaLag(
                "payment-processing-group",
                "payment-events",
                5000L,
                1000L
            );

            // Assert
            verify(slackClient, times(1)).sendAlert(alertCaptor.capture());
            Alert alert = alertCaptor.getValue();
            assertTrue(alert.getTitle().contains("Kafka"));
        }
    }

    @Nested
    @DisplayName("Statistics Tests")
    class StatisticsTests {

        @Test
        @DisplayName("Should return alert statistics")
        void shouldReturnAlertStatistics() {
            // Arrange
            when(auditService.getTotalAlertsSent()).thenReturn(100L);
            when(auditService.getAlertCountBySeverity(AlertSeverity.CRITICAL)).thenReturn(10L);
            when(auditService.getAlertCountBySeverity(AlertSeverity.ERROR)).thenReturn(30L);
            when(auditService.getAlertCountBySeverity(AlertSeverity.WARNING)).thenReturn(40L);
            when(auditService.getAlertCountBySeverity(AlertSeverity.INFO)).thenReturn(20L);
            when(auditService.getDeduplicatedCount()).thenReturn(25L);
            when(auditService.getSuppressedCount()).thenReturn(10L);
            when(auditService.getFailedDeliveryCount()).thenReturn(5L);

            // Act
            AlertStatistics stats = alertingService.getStatistics();

            // Assert
            assertNotNull(stats);
            assertEquals(100L, stats.getTotalAlertsSent());
            assertEquals(10L, stats.getCriticalAlerts());
            assertEquals(30L, stats.getErrorAlerts());
            assertEquals(40L, stats.getWarningAlerts());
            assertEquals(20L, stats.getInfoAlerts());
            assertEquals(25L, stats.getDeduplicatedAlerts());
            assertEquals(10L, stats.getSuppressedAlerts());
            assertEquals(5L, stats.getFailedDeliveries());
        }
    }

    @Nested
    @DisplayName("Configuration Tests")
    class ConfigurationTests {

        @Test
        @DisplayName("Should skip alerting when PagerDuty disabled")
        void shouldSkipAlertingWhenPagerDutyDisabled() {
            // Arrange
            when(config.isPagerDutyEnabled()).thenReturn(false);
            when(deduplicationService.shouldDeduplicate(any())).thenReturn(false);

            // Act
            alertingService.sendCriticalAlert(
                "Test",
                "Description",
                "source",
                Map.of()
            );

            // Assert
            verify(pagerDutyClient, never()).createIncident(any());
            verify(slackClient, times(1)).sendAlert(any());
        }

        @Test
        @DisplayName("Should test configuration")
        void shouldTestConfiguration() {
            // Arrange
            when(pagerDutyClient.testConnection())
                .thenReturn(CompletableFuture.completedFuture(true));
            when(slackClient.testConnection())
                .thenReturn(CompletableFuture.completedFuture(true));

            // Act
            CompletableFuture<Map<String, Boolean>> result = alertingService.testConfiguration();

            // Assert
            assertNotNull(result);
            Map<String, Boolean> resultMap = result.join();
            assertTrue(resultMap.get("pagerduty"));
            assertTrue(resultMap.get("slack"));
            assertTrue(resultMap.get("overall"));
        }
    }

    @Nested
    @DisplayName("Error Handling Tests")
    class ErrorHandlingTests {

        @Test
        @DisplayName("Should handle null alert gracefully")
        void shouldHandleNullAlertGracefully() {
            // Act & Assert
            assertThrows(Exception.class, () -> {
                alertingService.sendAlert(null);
            });
        }

        @Test
        @DisplayName("Should handle both channels failing")
        void shouldHandleBothChannelsFailing() {
            // Arrange
            when(deduplicationService.shouldDeduplicate(any())).thenReturn(false);
            when(pagerDutyClient.createIncident(any()))
                .thenReturn(CompletableFuture.failedFuture(new RuntimeException("PagerDuty failed")));
            when(slackClient.sendAlert(any()))
                .thenReturn(CompletableFuture.failedFuture(new RuntimeException("Slack failed")));

            // Act
            CompletableFuture<String> result = alertingService.sendCriticalAlert(
                "Test",
                "Description",
                "source",
                Map.of()
            );

            // Assert - Should still return alert ID (fail open)
            assertNotNull(result);
            assertDoesNotThrow(() -> result.get());
        }
    }

    @Nested
    @DisplayName("Incident Resolution Tests")
    class IncidentResolutionTests {

        @Test
        @DisplayName("Should resolve alert")
        void shouldResolveAlert() {
            // Arrange
            when(pagerDutyClient.resolveIncident(anyString(), anyString()))
                .thenReturn(CompletableFuture.completedFuture(null));

            // Act
            CompletableFuture<Void> result = alertingService.resolveAlert(
                "alert-123",
                "Issue fixed"
            );

            // Assert
            assertNotNull(result);
            assertDoesNotThrow(() -> result.get());
            verify(pagerDutyClient, times(1)).resolveIncident("alert-123", "Issue fixed");
            verify(auditService, times(1)).logAlertResolved("alert-123", "Issue fixed");
        }

        @Test
        @DisplayName("Should acknowledge alert")
        void shouldAcknowledgeAlert() {
            // Arrange
            when(pagerDutyClient.acknowledgeIncident(anyString(), anyString()))
                .thenReturn(CompletableFuture.completedFuture(null));

            // Act
            CompletableFuture<Void> result = alertingService.acknowledgeAlert(
                "alert-123",
                "engineer@example.com"
            );

            // Assert
            assertNotNull(result);
            assertDoesNotThrow(() -> result.get());
            verify(pagerDutyClient, times(1))
                .acknowledgeIncident("alert-123", "engineer@example.com");
            verify(auditService, times(1))
                .logAlertAcknowledged("alert-123", "engineer@example.com");
        }
    }
}
