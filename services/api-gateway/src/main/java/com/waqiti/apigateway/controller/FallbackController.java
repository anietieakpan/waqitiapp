package com.waqiti.apigateway.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Fallback controller for circuit breaker failures
 * Provides graceful degradation when services are unavailable
 */
@RestController
@RequestMapping("/fallback")
@Slf4j
public class FallbackController {

    @GetMapping("/user-service")
    @PostMapping("/user-service")
    public Mono<ResponseEntity<Map<String, Object>>> userServiceFallback() {
        log.warn("User service fallback triggered");
        return Mono.just(ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
            .body(Map.of(
                "error", "User service is temporarily unavailable",
                "message", "Please try again later",
                "timestamp", LocalDateTime.now(),
                "service", "user-service"
            )));
    }

    @GetMapping("/payment-service")
    @PostMapping("/payment-service")
    public Mono<ResponseEntity<Map<String, Object>>> paymentServiceFallback() {
        log.warn("Payment service fallback triggered");
        return Mono.just(ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
            .body(Map.of(
                "error", "Payment service is temporarily unavailable",
                "message", "Your payment request could not be processed. Please try again later.",
                "timestamp", LocalDateTime.now(),
                "service", "payment-service"
            )));
    }

    @GetMapping("/wallet-service")
    @PostMapping("/wallet-service")
    public Mono<ResponseEntity<Map<String, Object>>> walletServiceFallback() {
        log.warn("Wallet service fallback triggered");
        return Mono.just(ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
            .body(Map.of(
                "error", "Wallet service is temporarily unavailable",
                "message", "Wallet operations are currently disabled. Please try again later.",
                "timestamp", LocalDateTime.now(),
                "service", "wallet-service"
            )));
    }

    @GetMapping("/notification-service")
    @PostMapping("/notification-service")
    public Mono<ResponseEntity<Map<String, Object>>> notificationServiceFallback() {
        log.warn("Notification service fallback triggered");
        return Mono.just(ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
            .body(Map.of(
                "error", "Notification service is temporarily unavailable",
                "message", "Notifications may be delayed",
                "timestamp", LocalDateTime.now(),
                "service", "notification-service"
            )));
    }

    @GetMapping("/integration-service")
    @PostMapping("/integration-service")
    public Mono<ResponseEntity<Map<String, Object>>> integrationServiceFallback() {
        log.warn("Integration service fallback triggered");
        return Mono.just(ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
            .body(Map.of(
                "error", "Integration service is temporarily unavailable",
                "message", "External integrations are disabled. Please try again later.",
                "timestamp", LocalDateTime.now(),
                "service", "integration-service"
            )));
    }

    @GetMapping("/analytics-service")
    @PostMapping("/analytics-service")
    public Mono<ResponseEntity<Map<String, Object>>> analyticsServiceFallback() {
        log.warn("Analytics service fallback triggered");
        return Mono.just(ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
            .body(Map.of(
                "error", "Analytics service is temporarily unavailable",
                "message", "Reports and analytics are currently disabled",
                "timestamp", LocalDateTime.now(),
                "service", "analytics-service"
            )));
    }

    @GetMapping("/security-service")
    @PostMapping("/security-service")
    public Mono<ResponseEntity<Map<String, Object>>> securityServiceFallback() {
        log.warn("Security service fallback triggered");
        return Mono.just(ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
            .body(Map.of(
                "error", "Security service is temporarily unavailable",
                "message", "Security operations are disabled for maintenance",
                "timestamp", LocalDateTime.now(),
                "service", "security-service"
            )));
    }

    @GetMapping("/admin-service")
    @PostMapping("/admin-service")
    public Mono<ResponseEntity<Map<String, Object>>> adminServiceFallback() {
        log.warn("Admin service fallback triggered");
        return Mono.just(ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
            .body(Map.of(
                "error", "Admin service is temporarily unavailable",
                "message", "Administrative operations are currently disabled",
                "timestamp", LocalDateTime.now(),
                "service", "admin-service"
            )));
    }
}