package com.waqiti.saga.client;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Client for communicating with the analytics service from saga steps
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class AnalyticsServiceClient {

    private final RestTemplate restTemplate;

    @Value("${services.analytics-service.url:http://analytics-service}")
    private String analyticsServiceUrl;

    @Value("${analytics.timeout:5000}")
    private int timeoutMs;

    /**
     * Record transaction analytics event
     */
    @Retryable(value = {Exception.class}, maxAttempts = 2, backoff = @Backoff(delay = 500))
    public CompletableFuture<Boolean> recordTransactionEvent(String transactionId, String userId, 
                                                           String eventType, String transactionType,
                                                           BigDecimal amount, String currency) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                log.debug("Recording transaction analytics: transactionId={}, eventType={}", 
                    transactionId, eventType);

                String url = analyticsServiceUrl + "/api/v1/analytics/transactions/events";
                
                Map<String, Object> event = Map.of(
                    "eventId", UUID.randomUUID().toString(),
                    "transactionId", transactionId,
                    "userId", userId,
                    "eventType", eventType,
                    "transactionType", transactionType,
                    "amount", amount,
                    "currency", currency,
                    "timestamp", LocalDateTime.now(),
                    "source", "saga-orchestration-service"
                );
                
                HttpHeaders headers = createHeaders();
                HttpEntity<Map<String, Object>> httpEntity = new HttpEntity<>(event, headers);
                
                ResponseEntity<Map> response = restTemplate.exchange(
                    url, HttpMethod.POST, httpEntity, Map.class);
                
                Map<String, Object> result = response.getBody();
                boolean success = result != null && Boolean.TRUE.equals(result.get("success"));
                
                log.debug("Transaction analytics recorded: transactionId={}, success={}", 
                    transactionId, success);
                return success;
                
            } catch (Exception e) {
                log.warn("Failed to record transaction analytics: transactionId={}", transactionId, e);
                return false; // Don't fail saga for analytics failures
            }
        });
    }

    /**
     * Update transaction metrics
     */
    public CompletableFuture<Boolean> updateTransactionMetrics(String transactionId, String status, 
                                                              long processingTimeMs) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                log.debug("Updating transaction metrics: transactionId={}, status={}, processingTime={}ms", 
                    transactionId, status, processingTimeMs);

                String url = analyticsServiceUrl + "/api/v1/analytics/transactions/metrics";
                
                Map<String, Object> metrics = Map.of(
                    "transactionId", transactionId,
                    "status", status,
                    "processingTimeMs", processingTimeMs,
                    "updatedAt", LocalDateTime.now(),
                    "source", "saga-orchestration-service"
                );
                
                HttpHeaders headers = createHeaders();
                HttpEntity<Map<String, Object>> httpEntity = new HttpEntity<>(metrics, headers);
                
                ResponseEntity<Map> response = restTemplate.exchange(
                    url, HttpMethod.POST, httpEntity, Map.class);
                
                Map<String, Object> result = response.getBody();
                boolean success = result != null && Boolean.TRUE.equals(result.get("success"));
                
                log.debug("Transaction metrics updated: transactionId={}, success={}", 
                    transactionId, success);
                return success;
                
            } catch (Exception e) {
                log.warn("Failed to update transaction metrics: transactionId={}", transactionId, e);
                return false;
            }
        });
    }

    /**
     * Record saga execution analytics
     */
    public CompletableFuture<Boolean> recordSagaExecution(String sagaId, String sagaType, 
                                                         String status, long executionTimeMs,
                                                         int stepsCompleted, int totalSteps) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                log.debug("Recording saga analytics: sagaId={}, type={}, status={}", 
                    sagaId, sagaType, status);

                String url = analyticsServiceUrl + "/api/v1/analytics/sagas/executions";
                
                Map<String, Object> sagaAnalytics = Map.of(
                    "sagaId", sagaId,
                    "sagaType", sagaType,
                    "status", status,
                    "executionTimeMs", executionTimeMs,
                    "stepsCompleted", stepsCompleted,
                    "totalSteps", totalSteps,
                    "completionRate", (double) stepsCompleted / totalSteps,
                    "timestamp", LocalDateTime.now(),
                    "source", "saga-orchestration-service"
                );
                
                HttpHeaders headers = createHeaders();
                HttpEntity<Map<String, Object>> httpEntity = new HttpEntity<>(sagaAnalytics, headers);
                
                ResponseEntity<Map> response = restTemplate.exchange(
                    url, HttpMethod.POST, httpEntity, Map.class);
                
                Map<String, Object> result = response.getBody();
                boolean success = result != null && Boolean.TRUE.equals(result.get("success"));
                
                log.debug("Saga analytics recorded: sagaId={}, success={}", sagaId, success);
                return success;
                
            } catch (Exception e) {
                log.warn("Failed to record saga analytics: sagaId={}", sagaId, e);
                return false;
            }
        });
    }

    /**
     * Reverse analytics data (compensation)
     */
    public CompletableFuture<Boolean> reverseTransactionAnalytics(String transactionId, String reason) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                log.debug("Reversing transaction analytics: transactionId={}, reason={}", 
                    transactionId, reason);

                String url = analyticsServiceUrl + "/api/v1/analytics/transactions/" + transactionId + "/reverse";
                
                Map<String, Object> reversal = Map.of(
                    "transactionId", transactionId,
                    "reason", reason,
                    "reversedAt", LocalDateTime.now(),
                    "source", "saga-orchestration-service"
                );
                
                HttpHeaders headers = createHeaders();
                HttpEntity<Map<String, Object>> httpEntity = new HttpEntity<>(reversal, headers);
                
                ResponseEntity<Map> response = restTemplate.exchange(
                    url, HttpMethod.POST, httpEntity, Map.class);
                
                Map<String, Object> result = response.getBody();
                boolean success = result != null && Boolean.TRUE.equals(result.get("success"));
                
                log.debug("Transaction analytics reversed: transactionId={}, success={}", 
                    transactionId, success);
                return success;
                
            } catch (Exception e) {
                log.warn("Failed to reverse transaction analytics: transactionId={}", transactionId, e);
                return false;
            }
        });
    }

    /**
     * Record user behavior analytics
     */
    public CompletableFuture<Boolean> recordUserBehavior(String userId, String action, 
                                                        Map<String, Object> context) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                log.debug("Recording user behavior analytics: userId={}, action={}", userId, action);

                String url = analyticsServiceUrl + "/api/v1/analytics/users/behavior";
                
                Map<String, Object> behavior = Map.of(
                    "userId", userId,
                    "action", action,
                    "context", context != null ? context : Map.of(),
                    "timestamp", LocalDateTime.now(),
                    "source", "saga-orchestration-service"
                );
                
                HttpHeaders headers = createHeaders();
                HttpEntity<Map<String, Object>> httpEntity = new HttpEntity<>(behavior, headers);
                
                ResponseEntity<Map> response = restTemplate.exchange(
                    url, HttpMethod.POST, httpEntity, Map.class);
                
                Map<String, Object> result = response.getBody();
                boolean success = result != null && Boolean.TRUE.equals(result.get("success"));
                
                log.debug("User behavior analytics recorded: userId={}, success={}", userId, success);
                return success;
                
            } catch (Exception e) {
                log.warn("Failed to record user behavior analytics: userId={}", userId, e);
                return false;
            }
        });
    }

    /**
     * Get transaction analytics summary
     */
    public CompletableFuture<TransactionAnalyticsSummary> getTransactionSummary(String transactionId) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                log.debug("Getting transaction analytics summary: transactionId={}", transactionId);

                String url = analyticsServiceUrl + "/api/v1/analytics/transactions/" + transactionId + "/summary";
                
                HttpHeaders headers = createHeaders();
                HttpEntity<?> httpEntity = new HttpEntity<>(headers);
                
                ResponseEntity<Map> response = restTemplate.exchange(
                    url, HttpMethod.GET, httpEntity, Map.class);
                
                Map<String, Object> result = response.getBody();
                if (result != null) {
                    return TransactionAnalyticsSummary.builder()
                        .transactionId(transactionId)
                        .totalEvents(((Number) result.getOrDefault("totalEvents", 0)).intValue())
                        .processingTimeMs(((Number) result.getOrDefault("processingTimeMs", 0)).longValue())
                        .status((String) result.get("status"))
                        .lastUpdated(LocalDateTime.now())
                        .build();
                }
                
                return createEmptySummary(transactionId);
                
            } catch (Exception e) {
                log.warn("Failed to get transaction analytics summary: transactionId={}", transactionId, e);
                return createEmptySummary(transactionId);
            }
        });
    }

    /**
     * Health check for analytics service
     */
    public boolean isAnalyticsServiceHealthy() {
        try {
            String url = analyticsServiceUrl + "/actuator/health";
            ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
            return response.getStatusCode() == HttpStatus.OK;
        } catch (Exception e) {
            log.debug("Analytics service health check failed", e);
            return false;
        }
    }

    private HttpHeaders createHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Service-Name", "saga-orchestration-service");
        headers.set("X-Request-ID", UUID.randomUUID().toString());
        return headers;
    }

    private TransactionAnalyticsSummary createEmptySummary(String transactionId) {
        return TransactionAnalyticsSummary.builder()
            .transactionId(transactionId)
            .totalEvents(0)
            .processingTimeMs(0L)
            .status("UNKNOWN")
            .lastUpdated(LocalDateTime.now())
            .build();
    }

    // DTOs
    @lombok.Data
    @lombok.Builder
    public static class TransactionAnalyticsSummary {
        private String transactionId;
        private int totalEvents;
        private long processingTimeMs;
        private String status;
        private LocalDateTime lastUpdated;
    }
}