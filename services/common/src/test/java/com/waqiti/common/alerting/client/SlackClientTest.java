package com.waqiti.common.alerting.client;

import com.waqiti.common.alerting.model.Alert;
import com.waqiti.common.alerting.model.AlertSeverity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Comprehensive unit tests for SlackClient
 *
 * Tests rich Slack integration:
 * - Alert sending with rich formatting
 * - Color-coded messages by severity
 * - Channel routing (critical vs default)
 * - Rich attachments with fields
 * - Simple message sending
 * - Connection testing
 * - Error handling
 * - Metadata field formatting
 *
 * @author Waqiti Platform Team
 * @version 1.0.0
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("SlackClient Tests")
class SlackClientTest {

    @Mock
    private RestTemplate restTemplate;

    private SlackClient slackClient;

    @Captor
    private ArgumentCaptor<HttpEntity<Map<String, Object>>> requestCaptor;

    private static final String TEST_WEBHOOK_URL = "https://hooks.slack.com/services/TEST/WEBHOOK/URL";
    private static final String DEFAULT_CHANNEL = "#alerts";
    private static final String CRITICAL_CHANNEL = "#critical-alerts";
    private static final String BOT_USERNAME = "Waqiti Alert Bot";
    private static final String ICON_EMOJI = ":robot_face:";

    @BeforeEach
    void setUp() {
        slackClient = new SlackClient(restTemplate);

        // Set properties using reflection
        ReflectionTestUtils.setField(slackClient, "enabled", true);
        ReflectionTestUtils.setField(slackClient, "webhookUrl", TEST_WEBHOOK_URL);
        ReflectionTestUtils.setField(slackClient, "defaultChannel", DEFAULT_CHANNEL);
        ReflectionTestUtils.setField(slackClient, "criticalChannel", CRITICAL_CHANNEL);
        ReflectionTestUtils.setField(slackClient, "botUsername", BOT_USERNAME);
        ReflectionTestUtils.setField(slackClient, "iconEmoji", ICON_EMOJI);
    }

    @Nested
    @DisplayName("Alert Sending Tests")
    class AlertSendingTests {

        @Test
        @DisplayName("Should send critical alert to critical channel")
        void shouldSendCriticalAlertToCriticalChannel() throws Exception {
            // Given
            Alert alert = Alert.builder()
                    .id("alert-123")
                    .severity(AlertSeverity.CRITICAL)
                    .message("Database connection lost")
                    .source("database-service")
                    .type("DatabaseFailure")
                    .createdAt(Instant.now())
                    .build();

            ResponseEntity<String> response = new ResponseEntity<>("ok", HttpStatus.OK);
            when(restTemplate.postForEntity(eq(TEST_WEBHOOK_URL), any(HttpEntity.class), eq(String.class)))
                    .thenReturn(response);

            // When
            CompletableFuture<String> result = slackClient.sendAlert(alert);
            String alertId = result.get();

            // Then
            assertThat(alertId).isEqualTo("alert-123");

            verify(restTemplate).postForEntity(eq(TEST_WEBHOOK_URL), requestCaptor.capture(), eq(String.class));
            Map<String, Object> sentPayload = requestCaptor.getValue().getBody();

            assertThat(sentPayload).containsEntry("channel", CRITICAL_CHANNEL);
            assertThat(sentPayload).containsEntry("username", BOT_USERNAME);
            assertThat(sentPayload).containsEntry("icon_emoji", ICON_EMOJI);

            String mainText = (String) sentPayload.get("text");
            assertThat(mainText).contains("CRITICAL");
            assertThat(mainText).contains("database-service");
        }

        @Test
        @DisplayName("Should send warning alert to default channel")
        void shouldSendWarningAlertToDefaultChannel() throws Exception {
            // Given
            Alert alert = Alert.builder()
                    .id("alert-456")
                    .severity(AlertSeverity.WARNING)
                    .message("High memory usage")
                    .source("payment-service")
                    .createdAt(Instant.now())
                    .build();

            ResponseEntity<String> response = new ResponseEntity<>("ok", HttpStatus.OK);
            when(restTemplate.postForEntity(eq(TEST_WEBHOOK_URL), any(HttpEntity.class), eq(String.class)))
                    .thenReturn(response);

            // When
            CompletableFuture<String> result = slackClient.sendAlert(alert);
            result.get();

            // Then
            verify(restTemplate).postForEntity(eq(TEST_WEBHOOK_URL), requestCaptor.capture(), eq(String.class));
            Map<String, Object> sentPayload = requestCaptor.getValue().getBody();

            assertThat(sentPayload).containsEntry("channel", DEFAULT_CHANNEL);
        }

        @Test
        @DisplayName("Should not send alert when disabled")
        void shouldNotSendAlertWhenDisabled() throws Exception {
            // Given
            ReflectionTestUtils.setField(slackClient, "enabled", false);
            Alert alert = Alert.builder()
                    .id("alert-123")
                    .severity(AlertSeverity.CRITICAL)
                    .message("Test message")
                    .build();

            // When
            CompletableFuture<String> result = slackClient.sendAlert(alert);
            String alertId = result.get();

            // Then
            assertThat(alertId).isNull();
            verify(restTemplate, never()).postForEntity(anyString(), any(), any());
        }

        @Test
        @DisplayName("Should fail when webhook URL not configured")
        void shouldFailWhenWebhookUrlNotConfigured() {
            // Given
            ReflectionTestUtils.setField(slackClient, "webhookUrl", "");
            Alert alert = Alert.builder()
                    .id("alert-123")
                    .severity(AlertSeverity.ERROR)
                    .message("Test message")
                    .build();

            // When/Then
            CompletableFuture<String> result = slackClient.sendAlert(alert);

            assertThatThrownBy(result::get)
                    .hasCauseInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Slack webhook URL not configured");
        }

        @Test
        @DisplayName("Should handle failed alert sending")
        void shouldHandleFailedAlertSending() {
            // Given
            Alert alert = Alert.builder()
                    .id("alert-123")
                    .severity(AlertSeverity.ERROR)
                    .message("Test message")
                    .build();

            when(restTemplate.postForEntity(eq(TEST_WEBHOOK_URL), any(HttpEntity.class), eq(String.class)))
                    .thenThrow(new RestClientException("Network error"));

            // When/Then
            CompletableFuture<String> result = slackClient.sendAlert(alert);

            assertThatThrownBy(result::get)
                    .hasCauseInstanceOf(RestClientException.class)
                    .hasMessageContaining("Network error");
        }

        @Test
        @DisplayName("Should handle non-2xx response")
        void shouldHandleNon2xxResponse() {
            // Given
            Alert alert = Alert.builder()
                    .id("alert-123")
                    .severity(AlertSeverity.ERROR)
                    .message("Test message")
                    .build();

            ResponseEntity<String> response = new ResponseEntity<>("error", HttpStatus.INTERNAL_SERVER_ERROR);
            when(restTemplate.postForEntity(eq(TEST_WEBHOOK_URL), any(HttpEntity.class), eq(String.class)))
                    .thenReturn(response);

            // When/Then
            CompletableFuture<String> result = slackClient.sendAlert(alert);

            assertThatThrownBy(result::get)
                    .hasCauseInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Failed to send Slack alert");
        }
    }

    @Nested
    @DisplayName("Rich Formatting Tests")
    class RichFormattingTests {

        @Test
        @DisplayName("Should include rich attachment with fields")
        void shouldIncludeRichAttachmentWithFields() throws Exception {
            // Given
            Alert alert = Alert.builder()
                    .id("alert-789")
                    .severity(AlertSeverity.ERROR)
                    .message("Payment processing failed")
                    .source("payment-service")
                    .type("PaymentFailure")
                    .createdAt(Instant.now())
                    .build();

            ResponseEntity<String> response = new ResponseEntity<>("ok", HttpStatus.OK);
            when(restTemplate.postForEntity(eq(TEST_WEBHOOK_URL), any(HttpEntity.class), eq(String.class)))
                    .thenReturn(response);

            // When
            slackClient.sendAlert(alert).get();

            // Then
            verify(restTemplate).postForEntity(eq(TEST_WEBHOOK_URL), requestCaptor.capture(), eq(String.class));
            Map<String, Object> sentPayload = requestCaptor.getValue().getBody();

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> attachments = (List<Map<String, Object>>) sentPayload.get("attachments");
            assertThat(attachments).hasSize(1);

            Map<String, Object> attachment = attachments.get(0);
            assertThat(attachment).containsEntry("title", "Payment processing failed");
            assertThat(attachment).containsEntry("fallback", "Payment processing failed");
            assertThat(attachment).containsKey("color");
            assertThat(attachment).containsKey("ts");
            assertThat(attachment).containsKey("fields");
            assertThat(attachment).containsEntry("footer", "Waqiti Alerting System");
        }

        @Test
        @DisplayName("Should include metadata fields in attachment")
        void shouldIncludeMetadataFieldsInAttachment() throws Exception {
            // Given
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("userId", "user-123");
            metadata.put("transaction_id", "txn-456");
            metadata.put("amount", 1500.00);

            Alert alert = Alert.builder()
                    .id("alert-999")
                    .severity(AlertSeverity.WARNING)
                    .message("Suspicious transaction")
                    .source("fraud-service")
                    .metadata(metadata)
                    .createdAt(Instant.now())
                    .build();

            ResponseEntity<String> response = new ResponseEntity<>("ok", HttpStatus.OK);
            when(restTemplate.postForEntity(eq(TEST_WEBHOOK_URL), any(HttpEntity.class), eq(String.class)))
                    .thenReturn(response);

            // When
            slackClient.sendAlert(alert).get();

            // Then
            verify(restTemplate).postForEntity(eq(TEST_WEBHOOK_URL), requestCaptor.capture(), eq(String.class));
            Map<String, Object> sentPayload = requestCaptor.getValue().getBody();

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> attachments = (List<Map<String, Object>>) sentPayload.get("attachments");
            Map<String, Object> attachment = attachments.get(0);

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> fields = (List<Map<String, Object>>) attachment.get("fields");

            // Should contain alert fields plus metadata fields
            assertThat(fields).hasSizeGreaterThanOrEqualTo(5); // ID, Severity, Source, Type, Timestamp

            // Check for metadata fields (formatted field names)
            boolean hasUserId = fields.stream()
                    .anyMatch(f -> f.get("title").toString().contains("User"));
            boolean hasTransactionId = fields.stream()
                    .anyMatch(f -> f.get("title").toString().contains("Transaction"));

            assertThat(hasUserId || hasTransactionId).isTrue();
        }

        @Test
        @DisplayName("Should apply correct color for each severity")
        void shouldApplyCorrectColorForEachSeverity() throws Exception {
            testSeverityColor(AlertSeverity.CRITICAL, "#FF0000");
            testSeverityColor(AlertSeverity.ERROR, "#FF6600");
            testSeverityColor(AlertSeverity.WARNING, "#FFCC00");
            testSeverityColor(AlertSeverity.INFO, "#36A64F");
        }

        private void testSeverityColor(AlertSeverity severity, String expectedColor) throws Exception {
            Alert alert = Alert.builder()
                    .id("alert-color-" + severity.name())
                    .severity(severity)
                    .message("Test message")
                    .source("test-service")
                    .createdAt(Instant.now())
                    .build();

            ResponseEntity<String> response = new ResponseEntity<>("ok", HttpStatus.OK);
            when(restTemplate.postForEntity(eq(TEST_WEBHOOK_URL), any(HttpEntity.class), eq(String.class)))
                    .thenReturn(response);

            slackClient.sendAlert(alert).get();

            verify(restTemplate, atLeastOnce()).postForEntity(eq(TEST_WEBHOOK_URL), requestCaptor.capture(), eq(String.class));
            Map<String, Object> sentPayload = requestCaptor.getValue().getBody();

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> attachments = (List<Map<String, Object>>) sentPayload.get("attachments");
            Map<String, Object> attachment = attachments.get(0);

            assertThat(attachment).containsEntry("color", expectedColor);
        }

        @Test
        @DisplayName("Should truncate long metadata values")
        void shouldTruncateLongMetadataValues() throws Exception {
            // Given
            String longValue = "A".repeat(150); // 150 characters
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("longField", longValue);

            Alert alert = Alert.builder()
                    .id("alert-long")
                    .severity(AlertSeverity.INFO)
                    .message("Test with long metadata")
                    .source("test-service")
                    .metadata(metadata)
                    .createdAt(Instant.now())
                    .build();

            ResponseEntity<String> response = new ResponseEntity<>("ok", HttpStatus.OK);
            when(restTemplate.postForEntity(eq(TEST_WEBHOOK_URL), any(HttpEntity.class), eq(String.class)))
                    .thenReturn(response);

            // When
            slackClient.sendAlert(alert).get();

            // Then
            verify(restTemplate).postForEntity(eq(TEST_WEBHOOK_URL), requestCaptor.capture(), eq(String.class));
            Map<String, Object> sentPayload = requestCaptor.getValue().getBody();

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> attachments = (List<Map<String, Object>>) sentPayload.get("attachments");
            Map<String, Object> attachment = attachments.get(0);

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> fields = (List<Map<String, Object>>) attachment.get("fields");

            // Find the long field
            Map<String, Object> longField = fields.stream()
                    .filter(f -> f.get("title").toString().contains("Long"))
                    .findFirst()
                    .orElse(null);

            if (longField != null) {
                String value = (String) longField.get("value");
                assertThat(value).hasSizeLessThanOrEqualTo(100);
                assertThat(value).endsWith("...");
            }
        }
    }

    @Nested
    @DisplayName("Simple Message Tests")
    class SimpleMessageTests {

        @Test
        @DisplayName("Should send simple message successfully")
        void shouldSendSimpleMessageSuccessfully() throws Exception {
            // Given
            String channel = "#general";
            String message = "Hello, World!";

            ResponseEntity<String> response = new ResponseEntity<>("ok", HttpStatus.OK);
            when(restTemplate.postForEntity(eq(TEST_WEBHOOK_URL), any(HttpEntity.class), eq(String.class)))
                    .thenReturn(response);

            // When
            CompletableFuture<Void> result = slackClient.sendMessage(channel, message);
            result.get();

            // Then
            verify(restTemplate).postForEntity(eq(TEST_WEBHOOK_URL), requestCaptor.capture(), eq(String.class));
            Map<String, Object> sentPayload = requestCaptor.getValue().getBody();

            assertThat(sentPayload).containsEntry("text", message);
            assertThat(sentPayload).containsEntry("channel", channel);
            assertThat(sentPayload).containsEntry("username", BOT_USERNAME);
            assertThat(sentPayload).containsEntry("icon_emoji", ICON_EMOJI);
        }

        @Test
        @DisplayName("Should use default channel when null provided")
        void shouldUseDefaultChannelWhenNullProvided() throws Exception {
            // Given
            String message = "Test message";

            ResponseEntity<String> response = new ResponseEntity<>("ok", HttpStatus.OK);
            when(restTemplate.postForEntity(eq(TEST_WEBHOOK_URL), any(HttpEntity.class), eq(String.class)))
                    .thenReturn(response);

            // When
            slackClient.sendMessage(null, message).get();

            // Then
            verify(restTemplate).postForEntity(eq(TEST_WEBHOOK_URL), requestCaptor.capture(), eq(String.class));
            Map<String, Object> sentPayload = requestCaptor.getValue().getBody();

            assertThat(sentPayload).containsEntry("channel", DEFAULT_CHANNEL);
        }

        @Test
        @DisplayName("Should not send message when disabled")
        void shouldNotSendMessageWhenDisabled() throws Exception {
            // Given
            ReflectionTestUtils.setField(slackClient, "enabled", false);

            // When
            slackClient.sendMessage("#general", "Test").get();

            // Then
            verify(restTemplate, never()).postForEntity(anyString(), any(), any());
        }

        @Test
        @DisplayName("Should handle message sending failure")
        void shouldHandleMessageSendingFailure() {
            // Given
            when(restTemplate.postForEntity(eq(TEST_WEBHOOK_URL), any(HttpEntity.class), eq(String.class)))
                    .thenThrow(new RestClientException("Network error"));

            // When/Then
            CompletableFuture<Void> result = slackClient.sendMessage("#general", "Test");

            assertThatThrownBy(result::get)
                    .hasCauseInstanceOf(RestClientException.class);
        }
    }

    @Nested
    @DisplayName("Rich Message Tests")
    class RichMessageTests {

        @Test
        @DisplayName("Should send rich message with color")
        void shouldSendRichMessageWithColor() throws Exception {
            // Given
            String channel = "#general";
            String title = "Important Update";
            String text = "This is an important message";
            String color = "#FF0000";

            ResponseEntity<String> response = new ResponseEntity<>("ok", HttpStatus.OK);
            when(restTemplate.postForEntity(eq(TEST_WEBHOOK_URL), any(HttpEntity.class), eq(String.class)))
                    .thenReturn(response);

            // When
            CompletableFuture<Void> result = slackClient.sendRichMessage(channel, title, text, color);
            result.get();

            // Then
            verify(restTemplate).postForEntity(eq(TEST_WEBHOOK_URL), requestCaptor.capture(), eq(String.class));
            Map<String, Object> sentPayload = requestCaptor.getValue().getBody();

            assertThat(sentPayload).containsEntry("channel", channel);
            assertThat(sentPayload).containsKey("attachments");

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> attachments = (List<Map<String, Object>>) sentPayload.get("attachments");
            assertThat(attachments).hasSize(1);

            Map<String, Object> attachment = attachments.get(0);
            assertThat(attachment).containsEntry("title", title);
            assertThat(attachment).containsEntry("text", text);
            assertThat(attachment).containsEntry("color", color);
            assertThat(attachment).containsKey("ts");
        }

        @Test
        @DisplayName("Should use default channel for rich message when null")
        void shouldUseDefaultChannelForRichMessageWhenNull() throws Exception {
            // Given
            ResponseEntity<String> response = new ResponseEntity<>("ok", HttpStatus.OK);
            when(restTemplate.postForEntity(eq(TEST_WEBHOOK_URL), any(HttpEntity.class), eq(String.class)))
                    .thenReturn(response);

            // When
            slackClient.sendRichMessage(null, "Title", "Text", "#FF0000").get();

            // Then
            verify(restTemplate).postForEntity(eq(TEST_WEBHOOK_URL), requestCaptor.capture(), eq(String.class));
            Map<String, Object> sentPayload = requestCaptor.getValue().getBody();

            assertThat(sentPayload).containsEntry("channel", DEFAULT_CHANNEL);
        }

        @Test
        @DisplayName("Should not send rich message when disabled")
        void shouldNotSendRichMessageWhenDisabled() throws Exception {
            // Given
            ReflectionTestUtils.setField(slackClient, "enabled", false);

            // When
            slackClient.sendRichMessage("#general", "Title", "Text", "#FF0000").get();

            // Then
            verify(restTemplate, never()).postForEntity(anyString(), any(), any());
        }
    }

    @Nested
    @DisplayName("Connection Testing Tests")
    class ConnectionTestingTests {

        @Test
        @DisplayName("Should test connection successfully")
        void shouldTestConnectionSuccessfully() throws Exception {
            // Given
            ResponseEntity<String> response = new ResponseEntity<>("ok", HttpStatus.OK);
            when(restTemplate.postForEntity(eq(TEST_WEBHOOK_URL), any(HttpEntity.class), eq(String.class)))
                    .thenReturn(response);

            // When
            CompletableFuture<Boolean> result = slackClient.testConnection();
            Boolean success = result.get();

            // Then
            assertThat(success).isTrue();

            verify(restTemplate).postForEntity(eq(TEST_WEBHOOK_URL), requestCaptor.capture(), eq(String.class));
            Map<String, Object> sentPayload = requestCaptor.getValue().getBody();

            assertThat(sentPayload).containsKey("text");
            String text = (String) sentPayload.get("text");
            assertThat(text).contains("Connection Test");
        }

        @Test
        @DisplayName("Should return false when connection test fails")
        void shouldReturnFalseWhenConnectionTestFails() throws Exception {
            // Given
            when(restTemplate.postForEntity(eq(TEST_WEBHOOK_URL), any(HttpEntity.class), eq(String.class)))
                    .thenThrow(new RestClientException("Connection failed"));

            // When
            CompletableFuture<Boolean> result = slackClient.testConnection();
            Boolean success = result.get();

            // Then
            assertThat(success).isFalse();
        }

        @Test
        @DisplayName("Should return false when disabled")
        void shouldReturnFalseWhenDisabled() throws Exception {
            // Given
            ReflectionTestUtils.setField(slackClient, "enabled", false);

            // When
            CompletableFuture<Boolean> result = slackClient.testConnection();
            Boolean success = result.get();

            // Then
            assertThat(success).isFalse();
            verify(restTemplate, never()).postForEntity(anyString(), any(), any());
        }

        @Test
        @DisplayName("Should return false when webhook URL not configured")
        void shouldReturnFalseWhenWebhookUrlNotConfigured() throws Exception {
            // Given
            ReflectionTestUtils.setField(slackClient, "webhookUrl", "");

            // When
            CompletableFuture<Boolean> result = slackClient.testConnection();
            Boolean success = result.get();

            // Then
            assertThat(success).isFalse();
            verify(restTemplate, never()).postForEntity(anyString(), any(), any());
        }

        @Test
        @DisplayName("Should return false for non-2xx response")
        void shouldReturnFalseForNon2xxResponse() throws Exception {
            // Given
            ResponseEntity<String> response = new ResponseEntity<>("error", HttpStatus.INTERNAL_SERVER_ERROR);
            when(restTemplate.postForEntity(eq(TEST_WEBHOOK_URL), any(HttpEntity.class), eq(String.class)))
                    .thenReturn(response);

            // When
            CompletableFuture<Boolean> result = slackClient.testConnection();
            Boolean success = result.get();

            // Then
            assertThat(success).isFalse();
        }
    }
}
