package com.waqiti.payment.client;

import com.waqiti.payment.dto.*;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import io.github.resilience4j.timelimiter.annotation.TimeLimiter;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.List;

@FeignClient(
    name = "risk-service",
    url = "${services.risk-service.url:http://risk-service:8090}",
    fallback = RiskServiceClientFallback.class
)
public interface RiskServiceClient {

    @PostMapping("/api/risk/user/update-score")
    @CircuitBreaker(name = "risk-service")
    @Retry(name = "risk-service")
    @TimeLimiter(name = "risk-service")
    ResponseEntity<UpdateUserRiskScoreResponse> updateUserRiskScore(@Valid @RequestBody UpdateUserRiskScoreRequest request);

    @GetMapping("/api/risk/user/{userId}/score")
    @CircuitBreaker(name = "risk-service")
    @Retry(name = "risk-service")
    @TimeLimiter(name = "risk-service")
    ResponseEntity<UserRiskScoreResponse> getUserRiskScore(@PathVariable String userId);

    @PostMapping("/api/risk/transaction/assess")
    @CircuitBreaker(name = "risk-service")
    @Retry(name = "risk-service")
    @TimeLimiter(name = "risk-service")
    ResponseEntity<TransactionRiskAssessmentResponse> assessTransactionRisk(@Valid @RequestBody TransactionRiskAssessmentRequest request);

    @GetMapping("/api/risk/user/{userId}/profile")
    @CircuitBreaker(name = "risk-service")
    @Retry(name = "risk-service")
    @TimeLimiter(name = "risk-service")
    ResponseEntity<UserRiskProfile> getUserRiskProfile(@PathVariable String userId);

    @PostMapping("/api/risk/user/{userId}/monitor")
    @CircuitBreaker(name = "risk-service")
    @Retry(name = "risk-service")
    @TimeLimiter(name = "risk-service")
    ResponseEntity<Void> enableEnhancedMonitoring(@PathVariable String userId, 
                                                   @RequestBody EnhancedMonitoringRequest request);

    @GetMapping("/api/risk/alerts/{userId}")
    @CircuitBreaker(name = "risk-service")
    @Retry(name = "risk-service")
    @TimeLimiter(name = "risk-service")
    ResponseEntity<List<RiskAlert>> getUserRiskAlerts(@PathVariable String userId,
                                                       @RequestParam(defaultValue = "0") int page,
                                                       @RequestParam(defaultValue = "20") int size);
}