package com.waqiti.common.client;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Map;

@Component
@Slf4j
public class AuditServiceClient {

    private final WebClient webClient;

    public AuditServiceClient(WebClient.Builder webClientBuilder,
                             @Value("${audit-service.url:http://localhost:8087}") String baseUrl) {
        this.webClient = webClientBuilder
                .baseUrl(baseUrl)
                .build();
    }

    @CircuitBreaker(name = "audit-service", fallbackMethod = "createAuditEventFallback")
    @Retry(name = "audit-service")
    public void createAuditEvent(String entityType, String entityId, String action, Map<String, Object> metadata) {
        log.debug("Creating audit event: {} {} {}", entityType, entityId, action);

        try {
            Map<String, Object> request = Map.of(
                "entityType", entityType,
                "entityId", entityId,
                "action", action,
                "metadata", metadata != null ? metadata : Map.of()
            );

            webClient.post()
                    .uri("/api/v1/audit/events")
                    .bodyValue(request)
                    .retrieve()
                    .onStatus(statusCode -> statusCode.isError(), response -> {
                        return response.bodyToMono(String.class)
                                .flatMap(errorBody -> {
                                    log.error("Audit service error: {} - {}", response.statusCode(), errorBody);
                                    return Mono.error(new RuntimeException("Audit event creation failed: " + errorBody));
                                });
                    })
                    .bodyToMono(Void.class)
                    .timeout(Duration.ofSeconds(15))
                    .block();

            log.debug("Successfully created audit event: {} {} {}", entityType, entityId, action);

        } catch (Exception e) {
            log.error("Failed to create audit event: {} {} {}", entityType, entityId, action, e);
            throw new RuntimeException("Audit service call failed", e);
        }
    }

    @CircuitBreaker(name = "audit-service", fallbackMethod = "logUserActivityFallback")
    @Retry(name = "audit-service")
    public void logUserActivity(String userId, String activity, String details, Map<String, Object> context) {
        log.debug("Logging user activity: {} - {}", userId, activity);

        try {
            Map<String, Object> request = Map.of(
                "userId", userId,
                "activity", activity,
                "details", details != null ? details : "",
                "context", context != null ? context : Map.of()
            );

            webClient.post()
                    .uri("/api/v1/audit/user-activity")
                    .bodyValue(request)
                    .retrieve()
                    .onStatus(statusCode -> statusCode.isError(), response -> {
                        return response.bodyToMono(String.class)
                                .flatMap(errorBody -> Mono.error(new RuntimeException(
                                        "User activity logging failed: " + errorBody)));
                    })
                    .bodyToMono(Void.class)
                    .timeout(Duration.ofSeconds(15))
                    .block();

            log.debug("Successfully logged user activity: {} - {}", userId, activity);

        } catch (Exception e) {
            log.error("Failed to log user activity: {} - {}", userId, activity, e);
            throw new RuntimeException("User activity logging failed", e);
        }
    }

    @CircuitBreaker(name = "audit-service", fallbackMethod = "logComplianceEventFallback")
    @Retry(name = "audit-service")
    public void logComplianceEvent(String eventType, String transactionId, String status, Map<String, Object> details) {
        log.debug("Logging compliance event: {} for transaction {}", eventType, transactionId);

        try {
            Map<String, Object> request = Map.of(
                "eventType", eventType,
                "transactionId", transactionId,
                "status", status,
                "details", details != null ? details : Map.of()
            );

            webClient.post()
                    .uri("/api/v1/audit/compliance-events")
                    .bodyValue(request)
                    .retrieve()
                    .onStatus(statusCode -> statusCode.isError(), response -> {
                        return response.bodyToMono(String.class)
                                .flatMap(errorBody -> Mono.error(new RuntimeException(
                                        "Compliance event logging failed: " + errorBody)));
                    })
                    .bodyToMono(Void.class)
                    .timeout(Duration.ofSeconds(15))
                    .block();

            log.debug("Successfully logged compliance event: {} for transaction {}", eventType, transactionId);

        } catch (Exception e) {
            log.error("Failed to log compliance event: {} for transaction {}", eventType, transactionId, e);
            throw new RuntimeException("Compliance event logging failed", e);
        }
    }

    // Fallback methods

    private void createAuditEventFallback(String entityType, String entityId, String action, 
                                         Map<String, Object> metadata, Exception ex) {
        log.warn("Audit service unavailable - audit event not recorded: {} {} {} (fallback executed)", 
                entityType, entityId, action, ex);
        // In production, you might want to queue this for retry or log to a local file
    }

    private void logUserActivityFallback(String userId, String activity, String details, 
                                       Map<String, Object> context, Exception ex) {
        log.warn("Audit service unavailable - user activity not logged: {} - {} (fallback executed)", 
                userId, activity, ex);
        // In production, you might want to queue this for retry or log to a local file
    }

    private void logComplianceEventFallback(String eventType, String transactionId, String status, 
                                          Map<String, Object> details, Exception ex) {
        log.warn("Audit service unavailable - compliance event not logged: {} for {} (fallback executed)", 
                eventType, transactionId, ex);
        // In production, you might want to queue this for retry or log to a local file
    }
}