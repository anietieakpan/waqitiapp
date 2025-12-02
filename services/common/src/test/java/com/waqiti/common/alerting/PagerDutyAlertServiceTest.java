package com.waqiti.common.alerting;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Comprehensive unit tests for PagerDutyAlertService
 *
 * @author Waqiti Operations Team
 * @version 3.0.0
 * @since 2025-10-11
 */
@ExtendWith(MockitoExtension.class)
class PagerDutyAlertServiceTest {

    @Mock
    private RestTemplate restTemplate;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private PagerDutyAlertService alertService;

    @Captor
    private ArgumentCaptor<HttpEntity<Map<String, Object>>> requestCaptor;

    @Captor
    private ArgumentCaptor<String> urlCaptor;

    private static final String TEST_INTEGRATION_KEY = "test-integration-key";
    private static final String PAGERDUTY_URL = "https://events.pagerduty.com/v2/enqueue";

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(alertService, "integrationKey", TEST_INTEGRATION_KEY);
        ReflectionTestUtils.setField(alertService, "pagerDutyUrl", PAGERDUTY_URL);
        ReflectionTestUtils.setField(alertService, "enabled", true);
    }

    @Test
    void testTriggerAlert_Success() {
        // Arrange
        String severity = "critical";
        String summary = "Test alert";
        Map<String, Object> details = new HashMap<>();
        details.put("error", "Test error");
        String source = "payment-service";

        ResponseEntity<String> response = new ResponseEntity<>(
            "{\"status\":\"success\"}", HttpStatus.OK);

        when(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(String.class)))
            .thenReturn(response);

        // Act
        alertService.triggerAlert(severity, summary, details, source);

        // Assert
        verify(restTemplate, times(1)).postForEntity(
            eq(PAGERDUTY_URL),
            requestCaptor.capture(),
            eq(String.class)
        );

        HttpEntity<Map<String, Object>> capturedRequest = requestCaptor.getValue();
        Map<String, Object> requestBody = capturedRequest.getBody();

        assertThat(requestBody).containsKey("routing_key");
        assertThat(requestBody.get("routing_key")).isEqualTo(TEST_INTEGRATION_KEY);
        assertThat(requestBody.get("event_action")).isEqualTo("trigger");
        assertThat(requestBody).containsKey("dedup_key");
        assertThat(requestBody).containsKey("payload");

        @SuppressWarnings("unchecked")
        Map<String, Object> payload = (Map<String, Object>) requestBody.get("payload");
        assertThat(payload.get("summary")).isEqualTo(summary);
        assertThat(payload.get("severity")).isEqualTo(severity);
        assertThat(payload.get("source")).isEqualTo(source);
        assertThat(payload).containsKey("custom_details");
    }

    @Test
    void testTriggerAlert_Disabled_NoAlert() {
        // Arrange
        ReflectionTestUtils.setField(alertService, "enabled", false);

        // Act
        alertService.triggerAlert("critical", "Test", new HashMap<>(), "test");

        // Assert
        verify(restTemplate, never()).postForEntity(anyString(), any(), any());
    }

    @Test
    void testTriggerAlert_Failure_DoesNotThrow() {
        // Arrange
        when(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(String.class)))
            .thenThrow(new RestClientException("Connection failed"));

        // Act & Assert - Should not throw exception
        assertThatCode(() -> {
            alertService.triggerAlert("critical", "Test", new HashMap<>(), "test");
        }).doesNotThrowAnyException();

        verify(restTemplate, times(1)).postForEntity(anyString(), any(), any());
    }

    @Test
    void testAlertDLQFailure_CorrectFormat() {
        // Arrange
        String service = "payment-service";
        String topic = "payment.events";
        String error = "Deserialization error";
        Map<String, Object> context = new HashMap<>();
        context.put("messageId", "12345");

        ResponseEntity<String> response = new ResponseEntity<>(
            "{\"status\":\"success\"}", HttpStatus.OK);

        when(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(String.class)))
            .thenReturn(response);

        // Act
        alertService.alertDLQFailure(service, topic, error, context);

        // Assert
        verify(restTemplate, times(1)).postForEntity(
            anyString(),
            requestCaptor.capture(),
            eq(String.class)
        );

        Map<String, Object> requestBody = requestCaptor.getValue().getBody();
        @SuppressWarnings("unchecked")
        Map<String, Object> payload = (Map<String, Object>) requestBody.get("payload");

        assertThat(payload.get("summary")).asString()
            .contains("DLQ Failure")
            .contains(service)
            .contains(topic);
        assertThat(payload.get("severity")).isEqualTo("critical");

        @SuppressWarnings("unchecked")
        Map<String, Object> customDetails = (Map<String, Object>) payload.get("custom_details");
        assertThat(customDetails.get("service")).isEqualTo(service);
        assertThat(customDetails.get("topic")).isEqualTo(topic);
        assertThat(customDetails.get("error")).isEqualTo(error);
        assertThat(customDetails.get("messageId")).isEqualTo("12345");
    }

    @Test
    void testAlertLockFailure_CorrectFormat() {
        // Arrange
        String service = "payment-service";
        String resource = "recurring-payment-123";
        String lockType = "RECURRING_PAYMENT";
        String error = "Failed to acquire lock after 3 attempts";

        ResponseEntity<String> response = new ResponseEntity<>(
            "{\"status\":\"success\"}", HttpStatus.OK);

        when(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(String.class)))
            .thenReturn(response);

        // Act
        alertService.alertLockFailure(service, resource, lockType, error);

        // Assert
        verify(restTemplate, times(1)).postForEntity(
            anyString(),
            requestCaptor.capture(),
            eq(String.class)
        );

        Map<String, Object> requestBody = requestCaptor.getValue().getBody();
        @SuppressWarnings("unchecked")
        Map<String, Object> payload = (Map<String, Object>) requestBody.get("payload");

        assertThat(payload.get("summary")).asString()
            .contains("Distributed Lock Failure")
            .contains(service)
            .contains(resource);
        assertThat(payload.get("severity")).isEqualTo("critical");

        @SuppressWarnings("unchecked")
        Map<String, Object> customDetails = (Map<String, Object>) payload.get("custom_details");
        assertThat(customDetails.get("service")).isEqualTo(service);
        assertThat(customDetails.get("resource")).isEqualTo(resource);
        assertThat(customDetails.get("lockType")).isEqualTo(lockType);
        assertThat(customDetails.get("error")).isEqualTo(error);
    }

    @Test
    void testAlertPaymentGatewayFailure_CorrectFormat() {
        // Arrange
        String gateway = "Stripe";
        String error = "API timeout";
        int failureCount = 5;

        ResponseEntity<String> response = new ResponseEntity<>(
            "{\"status\":\"success\"}", HttpStatus.OK);

        when(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(String.class)))
            .thenReturn(response);

        // Act
        alertService.alertPaymentGatewayFailure(gateway, error, failureCount);

        // Assert
        verify(restTemplate, times(1)).postForEntity(
            anyString(),
            requestCaptor.capture(),
            eq(String.class)
        );

        Map<String, Object> requestBody = requestCaptor.getValue().getBody();
        @SuppressWarnings("unchecked")
        Map<String, Object> payload = (Map<String, Object>) requestBody.get("payload");

        assertThat(payload.get("summary")).asString()
            .contains("Payment Gateway Failure")
            .contains(gateway)
            .contains(String.valueOf(failureCount));
        assertThat(payload.get("severity")).isEqualTo("critical");
        assertThat(payload.get("source")).isEqualTo("payment-service");

        @SuppressWarnings("unchecked")
        Map<String, Object> customDetails = (Map<String, Object>) payload.get("custom_details");
        assertThat(customDetails.get("gateway")).isEqualTo(gateway);
        assertThat(customDetails.get("error")).isEqualTo(error);
        assertThat(customDetails.get("failureCount")).isEqualTo(failureCount);
    }

    @Test
    void testDeduplicationKey_SameAlertsSameDedupKey() {
        // Arrange
        String summary1 = "DLQ Failure in payment-service";
        String source1 = "payment-service";

        ResponseEntity<String> response = new ResponseEntity<>(
            "{\"status\":\"success\"}", HttpStatus.OK);

        when(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(String.class)))
            .thenReturn(response);

        // Act
        alertService.triggerAlert("critical", summary1, new HashMap<>(), source1);
        alertService.triggerAlert("critical", summary1, new HashMap<>(), source1);

        // Assert
        verify(restTemplate, times(2)).postForEntity(
            anyString(),
            requestCaptor.capture(),
            eq(String.class)
        );

        Map<String, Object> request1 = requestCaptor.getAllValues().get(0).getBody();
        Map<String, Object> request2 = requestCaptor.getAllValues().get(1).getBody();

        // Both should have the same dedup_key to prevent alert storms
        assertThat(request1.get("dedup_key")).isEqualTo(request2.get("dedup_key"));
    }

    @Test
    void testDeduplicationKey_DifferentAlertsDifferentDedupKeys() {
        // Arrange
        ResponseEntity<String> response = new ResponseEntity<>(
            "{\"status\":\"success\"}", HttpStatus.OK);

        when(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(String.class)))
            .thenReturn(response);

        // Act
        alertService.triggerAlert("critical", "Alert 1", new HashMap<>(), "service-1");
        alertService.triggerAlert("critical", "Alert 2", new HashMap<>(), "service-2");

        // Assert
        verify(restTemplate, times(2)).postForEntity(
            anyString(),
            requestCaptor.capture(),
            eq(String.class)
        );

        Map<String, Object> request1 = requestCaptor.getAllValues().get(0).getBody();
        Map<String, Object> request2 = requestCaptor.getAllValues().get(1).getBody();

        // Different alerts should have different dedup_keys
        assertThat(request1.get("dedup_key")).isNotEqualTo(request2.get("dedup_key"));
    }

    @Test
    void testSeverityLevels_AllSupported() {
        // Arrange
        ResponseEntity<String> response = new ResponseEntity<>(
            "{\"status\":\"success\"}", HttpStatus.OK);

        when(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(String.class)))
            .thenReturn(response);

        // Act & Assert
        String[] severities = {"critical", "error", "warning", "info"};

        for (String severity : severities) {
            alertService.triggerAlert(severity, "Test " + severity, new HashMap<>(), "test");
        }

        verify(restTemplate, times(4)).postForEntity(
            anyString(),
            requestCaptor.capture(),
            eq(String.class)
        );

        for (int i = 0; i < severities.length; i++) {
            Map<String, Object> requestBody = requestCaptor.getAllValues().get(i).getBody();
            @SuppressWarnings("unchecked")
            Map<String, Object> payload = (Map<String, Object>) requestBody.get("payload");
            assertThat(payload.get("severity")).isEqualTo(severities[i]);
        }
    }

    @Test
    void testHttpHeaders_CorrectlySet() {
        // Arrange
        ResponseEntity<String> response = new ResponseEntity<>(
            "{\"status\":\"success\"}", HttpStatus.OK);

        when(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(String.class)))
            .thenReturn(response);

        // Act
        alertService.triggerAlert("critical", "Test", new HashMap<>(), "test");

        // Assert
        verify(restTemplate, times(1)).postForEntity(
            anyString(),
            requestCaptor.capture(),
            eq(String.class)
        );

        HttpEntity<Map<String, Object>> request = requestCaptor.getValue();
        assertThat(request.getHeaders().getContentType().toString())
            .isEqualTo("application/json");
    }

    @Test
    void testCircuitBreakerFallback_LogsError() {
        // Arrange
        when(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(String.class)))
            .thenThrow(new RuntimeException("PagerDuty service unavailable"));

        // Act
        alertService.triggerAlert("critical", "Test", new HashMap<>(), "test");

        // Assert - Should not throw exception (fallback activated)
        verify(restTemplate, times(1)).postForEntity(anyString(), any(), any());
        // Fallback should log error but not break the main flow
    }
}
