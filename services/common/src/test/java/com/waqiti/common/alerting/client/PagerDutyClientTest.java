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
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Comprehensive unit tests for PagerDutyClient
 *
 * Tests all incident lifecycle operations:
 * - Creating incidents
 * - Acknowledging incidents
 * - Resolving incidents
 * - Updating incidents
 * - Deduplication logic
 * - Connection testing
 * - Error handling
 * - Legacy method compatibility
 *
 * @author Waqiti Platform Team
 * @version 1.0.0
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("PagerDutyClient Tests")
class PagerDutyClientTest {

    @Mock
    private RestTemplate restTemplate;

    private PagerDutyClient pagerDutyClient;

    @Captor
    private ArgumentCaptor<HttpEntity<Map<String, Object>>> requestCaptor;

    private static final String TEST_INTEGRATION_KEY = "test-integration-key-12345";
    private static final String TEST_API_KEY = "test-api-key-67890";
    private static final String EVENTS_URL = "https://events.pagerduty.com/v2/enqueue";

    @BeforeEach
    void setUp() {
        pagerDutyClient = new PagerDutyClient(restTemplate);

        // Set properties using reflection
        ReflectionTestUtils.setField(pagerDutyClient, "enabled", true);
        ReflectionTestUtils.setField(pagerDutyClient, "integrationKey", TEST_INTEGRATION_KEY);
        ReflectionTestUtils.setField(pagerDutyClient, "apiKey", TEST_API_KEY);
    }

    @Nested
    @DisplayName("Incident Creation Tests")
    class IncidentCreationTests {

        @Test
        @DisplayName("Should create incident successfully")
        void shouldCreateIncidentSuccessfully() throws Exception {
            // Given
            Alert alert = Alert.builder()
                    .id("alert-123")
                    .severity(AlertSeverity.CRITICAL)
                    .message("Critical system failure")
                    .source("payment-service")
                    .type("SystemFailure")
                    .createdAt(Instant.now())
                    .build();

            Map<String, Object> responseBody = new HashMap<>();
            responseBody.put("dedup_key", "pd-incident-abc123");
            responseBody.put("status", "success");

            ResponseEntity<Map> response = new ResponseEntity<>(responseBody, HttpStatus.OK);
            when(restTemplate.postForEntity(eq(EVENTS_URL), any(HttpEntity.class), eq(Map.class)))
                    .thenReturn(response);

            // When
            CompletableFuture<String> result = pagerDutyClient.createIncident(alert);
            String incidentId = result.get();

            // Then
            assertThat(incidentId).isEqualTo("pd-incident-abc123");

            verify(restTemplate).postForEntity(eq(EVENTS_URL), requestCaptor.capture(), eq(Map.class));
            Map<String, Object> sentPayload = requestCaptor.getValue().getBody();

            assertThat(sentPayload).containsEntry("routing_key", TEST_INTEGRATION_KEY);
            assertThat(sentPayload).containsEntry("event_action", "trigger");

            @SuppressWarnings("unchecked")
            Map<String, Object> payload = (Map<String, Object>) sentPayload.get("payload");
            assertThat(payload.get("summary")).isEqualTo("Critical system failure");
            assertThat(payload.get("source")).isEqualTo("payment-service");
            assertThat(payload.get("severity")).isEqualTo("critical");
        }

        @Test
        @DisplayName("Should not create incident when disabled")
        void shouldNotCreateIncidentWhenDisabled() throws Exception {
            // Given
            ReflectionTestUtils.setField(pagerDutyClient, "enabled", false);
            Alert alert = Alert.builder()
                    .id("alert-123")
                    .severity(AlertSeverity.CRITICAL)
                    .message("Critical system failure")
                    .build();

            // When
            CompletableFuture<String> result = pagerDutyClient.createIncident(alert);
            String incidentId = result.get();

            // Then
            assertThat(incidentId).isNull();
            verify(restTemplate, never()).postForEntity(anyString(), any(), any());
        }

        @Test
        @DisplayName("Should update existing incident instead of creating duplicate")
        void shouldUpdateExistingIncident() throws Exception {
            // Given
            Alert alert = Alert.builder()
                    .id("alert-123")
                    .severity(AlertSeverity.CRITICAL)
                    .message("Critical system failure")
                    .source("payment-service")
                    .build();

            // First incident creation
            Map<String, Object> firstResponse = new HashMap<>();
            firstResponse.put("dedup_key", "pd-incident-abc123");
            ResponseEntity<Map> response1 = new ResponseEntity<>(firstResponse, HttpStatus.OK);

            when(restTemplate.postForEntity(eq(EVENTS_URL), any(HttpEntity.class), eq(Map.class)))
                    .thenReturn(response1);

            pagerDutyClient.createIncident(alert).get();

            // When - try to create same incident again
            CompletableFuture<String> result = pagerDutyClient.createIncident(alert);
            String incidentId = result.get();

            // Then - should update instead of create
            assertThat(incidentId).isEqualTo("pd-incident-abc123");

            // Should have been called twice (once for create, once for update)
            verify(restTemplate, times(2)).postForEntity(eq(EVENTS_URL), any(), eq(Map.class));
        }

        @Test
        @DisplayName("Should handle failed incident creation")
        void shouldHandleFailedIncidentCreation() {
            // Given
            Alert alert = Alert.builder()
                    .id("alert-123")
                    .severity(AlertSeverity.CRITICAL)
                    .message("Critical system failure")
                    .build();

            when(restTemplate.postForEntity(eq(EVENTS_URL), any(HttpEntity.class), eq(Map.class)))
                    .thenThrow(new RestClientException("Network error"));

            // When/Then
            CompletableFuture<String> result = pagerDutyClient.createIncident(alert);

            assertThatThrownBy(result::get)
                    .hasCauseInstanceOf(RestClientException.class)
                    .hasMessageContaining("Network error");
        }

        @Test
        @DisplayName("Should handle response without dedup_key")
        void shouldHandleResponseWithoutDedupKey() {
            // Given
            Alert alert = Alert.builder()
                    .id("alert-123")
                    .severity(AlertSeverity.CRITICAL)
                    .message("Critical system failure")
                    .build();

            Map<String, Object> responseBody = new HashMap<>();
            responseBody.put("status", "success");
            // Missing dedup_key

            ResponseEntity<Map> response = new ResponseEntity<>(responseBody, HttpStatus.OK);
            when(restTemplate.postForEntity(eq(EVENTS_URL), any(HttpEntity.class), eq(Map.class)))
                    .thenReturn(response);

            // When/Then
            CompletableFuture<String> result = pagerDutyClient.createIncident(alert);

            assertThatThrownBy(result::get)
                    .hasCauseInstanceOf(RuntimeException.class);
        }

        @Test
        @DisplayName("Should map all severity levels correctly")
        void shouldMapSeverityLevelsCorrectly() throws Exception {
            // Test each severity level
            testSeverityMapping(AlertSeverity.CRITICAL, "critical");
            testSeverityMapping(AlertSeverity.ERROR, "error");
            testSeverityMapping(AlertSeverity.WARNING, "warning");
            testSeverityMapping(AlertSeverity.INFO, "info");
        }

        private void testSeverityMapping(AlertSeverity input, String expected) throws Exception {
            Alert alert = Alert.builder()
                    .id("alert-" + input.name())
                    .severity(input)
                    .message("Test message")
                    .source("test-source")
                    .build();

            Map<String, Object> responseBody = new HashMap<>();
            responseBody.put("dedup_key", "test-incident-" + input.name());
            ResponseEntity<Map> response = new ResponseEntity<>(responseBody, HttpStatus.OK);

            when(restTemplate.postForEntity(eq(EVENTS_URL), any(HttpEntity.class), eq(Map.class)))
                    .thenReturn(response);

            pagerDutyClient.clearCache(); // Clear cache between tests
            pagerDutyClient.createIncident(alert).get();

            verify(restTemplate, atLeastOnce()).postForEntity(eq(EVENTS_URL), requestCaptor.capture(), eq(Map.class));

            Map<String, Object> sentPayload = requestCaptor.getValue().getBody();
            @SuppressWarnings("unchecked")
            Map<String, Object> payload = (Map<String, Object>) sentPayload.get("payload");
            assertThat(payload.get("severity")).isEqualTo(expected);
        }
    }

    @Nested
    @DisplayName("Incident Acknowledgement Tests")
    class IncidentAcknowledgementTests {

        @Test
        @DisplayName("Should acknowledge incident successfully")
        void shouldAcknowledgeIncidentSuccessfully() throws Exception {
            // Given
            String incidentId = "pd-incident-abc123";
            String acknowledger = "john.doe@example.com";

            ResponseEntity<Map> response = new ResponseEntity<>(new HashMap<>(), HttpStatus.OK);
            when(restTemplate.postForEntity(eq(EVENTS_URL), any(HttpEntity.class), eq(Map.class)))
                    .thenReturn(response);

            // When
            CompletableFuture<Void> result = pagerDutyClient.acknowledgeIncident(incidentId, acknowledger);
            result.get();

            // Then
            verify(restTemplate).postForEntity(eq(EVENTS_URL), requestCaptor.capture(), eq(Map.class));
            Map<String, Object> sentPayload = requestCaptor.getValue().getBody();

            assertThat(sentPayload).containsEntry("routing_key", TEST_INTEGRATION_KEY);
            assertThat(sentPayload).containsEntry("dedup_key", incidentId);
            assertThat(sentPayload).containsEntry("event_action", "acknowledge");

            @SuppressWarnings("unchecked")
            Map<String, Object> payload = (Map<String, Object>) sentPayload.get("payload");
            assertThat(payload.get("summary")).asString().contains(acknowledger);
        }

        @Test
        @DisplayName("Should not acknowledge when disabled")
        void shouldNotAcknowledgeWhenDisabled() throws Exception {
            // Given
            ReflectionTestUtils.setField(pagerDutyClient, "enabled", false);

            // When
            CompletableFuture<Void> result = pagerDutyClient.acknowledgeIncident("incident-123", "user");
            result.get();

            // Then
            verify(restTemplate, never()).postForEntity(anyString(), any(), any());
        }

        @Test
        @DisplayName("Should handle acknowledgement failure")
        void shouldHandleAcknowledgementFailure() {
            // Given
            when(restTemplate.postForEntity(eq(EVENTS_URL), any(HttpEntity.class), eq(Map.class)))
                    .thenThrow(new RestClientException("Network error"));

            // When/Then
            CompletableFuture<Void> result = pagerDutyClient.acknowledgeIncident("incident-123", "user");

            assertThatThrownBy(result::get)
                    .hasCauseInstanceOf(RestClientException.class);
        }
    }

    @Nested
    @DisplayName("Incident Resolution Tests")
    class IncidentResolutionTests {

        @Test
        @DisplayName("Should resolve incident successfully")
        void shouldResolveIncidentSuccessfully() throws Exception {
            // Given
            String incidentId = "pd-incident-abc123";
            String resolution = "Issue fixed by restarting service";

            ResponseEntity<Map> response = new ResponseEntity<>(new HashMap<>(), HttpStatus.OK);
            when(restTemplate.postForEntity(eq(EVENTS_URL), any(HttpEntity.class), eq(Map.class)))
                    .thenReturn(response);

            // When
            CompletableFuture<Void> result = pagerDutyClient.resolveIncident(incidentId, resolution);
            result.get();

            // Then
            verify(restTemplate).postForEntity(eq(EVENTS_URL), requestCaptor.capture(), eq(Map.class));
            Map<String, Object> sentPayload = requestCaptor.getValue().getBody();

            assertThat(sentPayload).containsEntry("routing_key", TEST_INTEGRATION_KEY);
            assertThat(sentPayload).containsEntry("dedup_key", incidentId);
            assertThat(sentPayload).containsEntry("event_action", "resolve");

            @SuppressWarnings("unchecked")
            Map<String, Object> payload = (Map<String, Object>) sentPayload.get("payload");
            assertThat(payload.get("summary")).asString().contains(resolution);
        }

        @Test
        @DisplayName("Should not resolve when disabled")
        void shouldNotResolveWhenDisabled() throws Exception {
            // Given
            ReflectionTestUtils.setField(pagerDutyClient, "enabled", false);

            // When
            CompletableFuture<Void> result = pagerDutyClient.resolveIncident("incident-123", "Fixed");
            result.get();

            // Then
            verify(restTemplate, never()).postForEntity(anyString(), any(), any());
        }

        @Test
        @DisplayName("Should handle resolution failure")
        void shouldHandleResolutionFailure() {
            // Given
            when(restTemplate.postForEntity(eq(EVENTS_URL), any(HttpEntity.class), eq(Map.class)))
                    .thenThrow(new RestClientException("Network error"));

            // When/Then
            CompletableFuture<Void> result = pagerDutyClient.resolveIncident("incident-123", "Fixed");

            assertThatThrownBy(result::get)
                    .hasCauseInstanceOf(RestClientException.class);
        }
    }

    @Nested
    @DisplayName("Connection Testing Tests")
    class ConnectionTestingTests {

        @Test
        @DisplayName("Should test connection successfully")
        void shouldTestConnectionSuccessfully() throws Exception {
            // Given
            Map<String, Object> responseBody = new HashMap<>();
            responseBody.put("dedup_key", "test-connection-123");
            ResponseEntity<Map> response = new ResponseEntity<>(responseBody, HttpStatus.OK);

            when(restTemplate.postForEntity(eq(EVENTS_URL), any(HttpEntity.class), eq(Map.class)))
                    .thenReturn(response);

            // When
            CompletableFuture<Boolean> result = pagerDutyClient.testConnection();
            Boolean success = result.get();

            // Then
            assertThat(success).isTrue();

            // Should create and then resolve test incident
            verify(restTemplate, times(2)).postForEntity(eq(EVENTS_URL), any(), eq(Map.class));
        }

        @Test
        @DisplayName("Should return false when connection test fails")
        void shouldReturnFalseWhenConnectionTestFails() throws Exception {
            // Given
            when(restTemplate.postForEntity(eq(EVENTS_URL), any(HttpEntity.class), eq(Map.class)))
                    .thenThrow(new RestClientException("Connection failed"));

            // When
            CompletableFuture<Boolean> result = pagerDutyClient.testConnection();
            Boolean success = result.get();

            // Then
            assertThat(success).isFalse();
        }

        @Test
        @DisplayName("Should return false when disabled")
        void shouldReturnFalseWhenDisabled() throws Exception {
            // Given
            ReflectionTestUtils.setField(pagerDutyClient, "enabled", false);

            // When
            CompletableFuture<Boolean> result = pagerDutyClient.testConnection();
            Boolean success = result.get();

            // Then
            assertThat(success).isFalse();
            verify(restTemplate, never()).postForEntity(anyString(), any(), any());
        }
    }

    @Nested
    @DisplayName("Legacy Method Tests")
    class LegacyMethodTests {

        @Test
        @DisplayName("Should trigger incident using legacy method")
        void shouldTriggerIncidentUsingLegacyMethod() {
            // Given
            String dedupKey = "legacy-incident-123";
            String summary = "Legacy alert";
            Map<String, Object> customDetails = new HashMap<>();
            customDetails.put("key", "value");

            ResponseEntity<String> response = new ResponseEntity<>("OK", HttpStatus.OK);
            when(restTemplate.postForEntity(eq(EVENTS_URL), any(HttpEntity.class), eq(String.class)))
                    .thenReturn(response);

            // When
            pagerDutyClient.triggerIncident(dedupKey, summary, customDetails);

            // Then
            verify(restTemplate).postForEntity(eq(EVENTS_URL), requestCaptor.capture(), eq(String.class));
            Map<String, Object> sentPayload = requestCaptor.getValue().getBody();

            assertThat(sentPayload).containsEntry("routing_key", TEST_INTEGRATION_KEY);
            assertThat(sentPayload).containsEntry("dedup_key", dedupKey);
        }

        @Test
        @DisplayName("Should send alert using legacy method")
        void shouldSendAlertUsingLegacyMethod() {
            // Given
            Alert alert = Alert.builder()
                    .id("alert-legacy-123")
                    .severity(AlertSeverity.ERROR)
                    .message("Legacy alert message")
                    .source("legacy-service")
                    .build();

            ResponseEntity<String> response = new ResponseEntity<>("OK", HttpStatus.OK);
            when(restTemplate.postForEntity(eq(EVENTS_URL), any(HttpEntity.class), eq(String.class)))
                    .thenReturn(response);

            // When
            pagerDutyClient.sendAlert(alert);

            // Then
            verify(restTemplate).postForEntity(eq(EVENTS_URL), any(), eq(String.class));
        }

        @Test
        @DisplayName("Should handle legacy method failure")
        void shouldHandleLegacyMethodFailure() {
            // Given
            String dedupKey = "legacy-incident-123";
            String summary = "Legacy alert";
            Map<String, Object> customDetails = new HashMap<>();

            when(restTemplate.postForEntity(eq(EVENTS_URL), any(HttpEntity.class), eq(String.class)))
                    .thenThrow(new RestClientException("Network error"));

            // When/Then
            assertThatThrownBy(() -> pagerDutyClient.triggerIncident(dedupKey, summary, customDetails))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("PagerDuty incident trigger failed");
        }
    }

    @Nested
    @DisplayName("Cache Management Tests")
    class CacheManagementTests {

        @Test
        @DisplayName("Should cache incident IDs by dedup key")
        void shouldCacheIncidentIds() throws Exception {
            // Given
            Alert alert = Alert.builder()
                    .id("alert-123")
                    .severity(AlertSeverity.CRITICAL)
                    .message("Test message")
                    .source("test-source")
                    .build();

            Map<String, Object> responseBody = new HashMap<>();
            responseBody.put("dedup_key", "pd-incident-abc123");
            ResponseEntity<Map> response = new ResponseEntity<>(responseBody, HttpStatus.OK);

            when(restTemplate.postForEntity(eq(EVENTS_URL), any(HttpEntity.class), eq(Map.class)))
                    .thenReturn(response);

            // When - create incident twice
            pagerDutyClient.createIncident(alert).get();
            pagerDutyClient.createIncident(alert).get();

            // Then - should use cache for second call (update instead of create)
            verify(restTemplate, times(2)).postForEntity(eq(EVENTS_URL), any(), eq(Map.class));
        }

        @Test
        @DisplayName("Should clear cache manually")
        void shouldClearCacheManually() throws Exception {
            // Given
            Alert alert = Alert.builder()
                    .id("alert-123")
                    .severity(AlertSeverity.CRITICAL)
                    .message("Test message")
                    .source("test-source")
                    .build();

            Map<String, Object> responseBody = new HashMap<>();
            responseBody.put("dedup_key", "pd-incident-abc123");
            ResponseEntity<Map> response = new ResponseEntity<>(responseBody, HttpStatus.OK);

            when(restTemplate.postForEntity(eq(EVENTS_URL), any(HttpEntity.class), eq(Map.class)))
                    .thenReturn(response);

            // When
            pagerDutyClient.createIncident(alert).get();
            pagerDutyClient.clearCache();
            pagerDutyClient.createIncident(alert).get();

            // Then - should create new incident both times (no cache hit)
            verify(restTemplate, times(2)).postForEntity(eq(EVENTS_URL), any(), eq(Map.class));
        }
    }

    @Nested
    @DisplayName("Custom Details Tests")
    class CustomDetailsTests {

        @Test
        @DisplayName("Should include alert metadata in custom details")
        void shouldIncludeMetadataInCustomDetails() throws Exception {
            // Given
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("user_id", "user-123");
            metadata.put("transaction_id", "txn-456");
            metadata.put("amount", 1500.00);

            Alert alert = Alert.builder()
                    .id("alert-123")
                    .severity(AlertSeverity.CRITICAL)
                    .message("Transaction failed")
                    .source("payment-service")
                    .type("TransactionFailure")
                    .metadata(metadata)
                    .build();

            Map<String, Object> responseBody = new HashMap<>();
            responseBody.put("dedup_key", "pd-incident-abc123");
            ResponseEntity<Map> response = new ResponseEntity<>(responseBody, HttpStatus.OK);

            when(restTemplate.postForEntity(eq(EVENTS_URL), any(HttpEntity.class), eq(Map.class)))
                    .thenReturn(response);

            // When
            pagerDutyClient.createIncident(alert).get();

            // Then
            verify(restTemplate).postForEntity(eq(EVENTS_URL), requestCaptor.capture(), eq(Map.class));
            Map<String, Object> sentPayload = requestCaptor.getValue().getBody();

            @SuppressWarnings("unchecked")
            Map<String, Object> payload = (Map<String, Object>) sentPayload.get("payload");
            @SuppressWarnings("unchecked")
            Map<String, Object> customDetails = (Map<String, Object>) payload.get("custom_details");

            assertThat(customDetails).containsEntry("alert_id", "alert-123");
            assertThat(customDetails).containsEntry("type", "TransactionFailure");
            assertThat(customDetails).containsEntry("user_id", "user-123");
            assertThat(customDetails).containsEntry("transaction_id", "txn-456");
            assertThat(customDetails).containsEntry("amount", 1500.00);
        }

        @Test
        @DisplayName("Should handle alerts without metadata")
        void shouldHandleAlertsWithoutMetadata() throws Exception {
            // Given
            Alert alert = Alert.builder()
                    .id("alert-123")
                    .severity(AlertSeverity.WARNING)
                    .message("Simple alert")
                    .source("test-service")
                    .build();

            Map<String, Object> responseBody = new HashMap<>();
            responseBody.put("dedup_key", "pd-incident-abc123");
            ResponseEntity<Map> response = new ResponseEntity<>(responseBody, HttpStatus.OK);

            when(restTemplate.postForEntity(eq(EVENTS_URL), any(HttpEntity.class), eq(Map.class)))
                    .thenReturn(response);

            // When
            pagerDutyClient.createIncident(alert).get();

            // Then
            verify(restTemplate).postForEntity(eq(EVENTS_URL), requestCaptor.capture(), eq(Map.class));
            Map<String, Object> sentPayload = requestCaptor.getValue().getBody();

            @SuppressWarnings("unchecked")
            Map<String, Object> payload = (Map<String, Object>) sentPayload.get("payload");
            @SuppressWarnings("unchecked")
            Map<String, Object> customDetails = (Map<String, Object>) payload.get("custom_details");

            assertThat(customDetails).containsEntry("alert_id", "alert-123");
            assertThat(customDetails).doesNotContainKey("metadata");
        }
    }
}
