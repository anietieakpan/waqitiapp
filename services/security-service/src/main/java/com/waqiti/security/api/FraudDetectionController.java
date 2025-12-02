package com.waqiti.security.api;

import com.waqiti.security.domain.FraudDetectionResult;
import com.waqiti.security.domain.SecurityAction;
import com.waqiti.security.service.AdvancedFraudDetectionService;
import com.waqiti.security.service.FraudDetectionService;
import com.waqiti.common.ratelimit.RateLimited;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/security/fraud")
@RequiredArgsConstructor
@Slf4j
public class FraudDetectionController {

    private final FraudDetectionService fraudDetectionService;
    private final AdvancedFraudDetectionService advancedFraudDetectionService;

    @PostMapping("/check-transaction")
    @RateLimited(keyType = RateLimited.KeyType.USER, capacity = 100, refillTokens = 100, refillPeriodMinutes = 1)
    @PreAuthorize("hasAnyRole('USER', 'SYSTEM')")
    public ResponseEntity<FraudDetectionResult> checkTransaction(@RequestBody @Valid TransactionCheckRequest request) {
        log.info("Checking transaction for fraud: userId={}, amount={}", request.getUserId(), request.getAmount());
        
        FraudDetectionResult result = fraudDetectionService.analyzeTransaction(
            request.getUserId(),
            request.getAmount(),
            request.getCurrency(),
            request.getRecipientId(),
            request.getTransactionType(),
            request.getMetadata()
        );
        
        return ResponseEntity.ok(result);
    }

    @PostMapping("/advanced-check")
    @RateLimited(keyType = RateLimited.KeyType.USER, capacity = 50, refillTokens = 50, refillPeriodMinutes = 1)
    @PreAuthorize("hasAnyRole('USER', 'SYSTEM')")
    public ResponseEntity<FraudDetectionResult> advancedCheck(@RequestBody @Valid AdvancedCheckRequest request) {
        log.info("Performing advanced fraud check for transaction: {}", request.getTransactionId());
        
        FraudDetectionResult result = advancedFraudDetectionService.analyzeTransaction(
            request.getTransactionId(),
            request.getUserId(),
            request.getAmount(),
            request.getCurrency(),
            request.getRecipientId(),
            request.getTransactionType()
        );
        
        return ResponseEntity.ok(result);
    }

    @GetMapping("/risk-score/{userId}")
    @RateLimited(keyType = RateLimited.KeyType.USER, capacity = 30, refillTokens = 30, refillPeriodMinutes = 1)
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    public ResponseEntity<RiskScoreResponse> getUserRiskScore(@PathVariable String userId) {
        log.info("Getting risk score for user: {}", userId);
        
        double riskScore = fraudDetectionService.calculateUserRiskScore(userId);
        
        return ResponseEntity.ok(RiskScoreResponse.builder()
            .userId(userId)
            .riskScore(riskScore)
            .riskLevel(getRiskLevel(riskScore))
            .timestamp(System.currentTimeMillis())
            .build());
    }

    @GetMapping("/alerts/{userId}")
    @RateLimited(keyType = RateLimited.KeyType.USER, capacity = 50, refillTokens = 50, refillPeriodMinutes = 1)
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    public ResponseEntity<List<FraudAlert>> getUserFraudAlerts(
            @PathVariable String userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        log.info("Getting fraud alerts for user: {}", userId);
        
        List<FraudAlert> alerts = fraudDetectionService.getUserFraudAlerts(userId, page, size);
        return ResponseEntity.ok(alerts);
    }

    @PostMapping("/report-suspicious")
    @RateLimited(keyType = RateLimited.KeyType.USER, capacity = 5, refillTokens = 5, refillPeriodMinutes = 60)
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    public ResponseEntity<Map<String, Object>> reportSuspiciousActivity(@RequestBody @Valid SuspiciousActivityRequest request) {
        log.info("Reporting suspicious activity: type={}, userId={}", request.getActivityType(), request.getUserId());
        
        String reportId = fraudDetectionService.reportSuspiciousActivity(
            request.getUserId(),
            request.getActivityType(),
            request.getDescription(),
            request.getEvidence()
        );
        
        return ResponseEntity.ok(Map.of(
            "reportId", reportId,
            "status", "submitted",
            "message", "Suspicious activity report has been submitted for review"
        ));
    }

    @PostMapping("/whitelist")
    @RateLimited(keyType = RateLimited.KeyType.USER, capacity = 10, refillTokens = 10, refillPeriodMinutes = 5)
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> addToWhitelist(@RequestBody @Valid WhitelistRequest request) {
        log.info("Adding to whitelist: userId={}, recipientId={}", request.getUserId(), request.getRecipientId());
        
        fraudDetectionService.addToWhitelist(request.getUserId(), request.getRecipientId());
        
        return ResponseEntity.ok(Map.of(
            "status", "success",
            "message", "Recipient added to whitelist"
        ));
    }

    @DeleteMapping("/whitelist")
    @RateLimited(keyType = RateLimited.KeyType.USER, capacity = 10, refillTokens = 10, refillPeriodMinutes = 5)
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> removeFromWhitelist(@RequestBody @Valid WhitelistRequest request) {
        log.info("Removing from whitelist: userId={}, recipientId={}", request.getUserId(), request.getRecipientId());
        
        fraudDetectionService.removeFromWhitelist(request.getUserId(), request.getRecipientId());
        
        return ResponseEntity.ok(Map.of(
            "status", "success",
            "message", "Recipient removed from whitelist"
        ));
    }

    @GetMapping("/patterns/{userId}")
    @RateLimited(keyType = RateLimited.KeyType.USER, capacity = 20, refillTokens = 20, refillPeriodMinutes = 5)
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<TransactionPatterns> getUserTransactionPatterns(@PathVariable String userId) {
        log.info("Getting transaction patterns for user: {}", userId);
        
        TransactionPatterns patterns = advancedFraudDetectionService.analyzeUserPatterns(userId);
        return ResponseEntity.ok(patterns);
    }

    @PostMapping("/actions")
    @RateLimited(keyType = RateLimited.KeyType.USER, capacity = 10, refillTokens = 10, refillPeriodMinutes = 15)
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> takeSecurityAction(@RequestBody @Valid SecurityActionRequest request) {
        log.info("Taking security action: {} for user: {}", request.getAction(), request.getUserId());
        
        SecurityAction action = SecurityAction.builder()
            .userId(request.getUserId())
            .action(request.getAction())
            .reason(request.getReason())
            .performedBy(request.getPerformedBy())
            .build();
        
        String actionId = fraudDetectionService.executeSecurityAction(action);
        
        return ResponseEntity.ok(Map.of(
            "actionId", actionId,
            "status", "executed",
            "action", request.getAction()
        ));
    }

    private String getRiskLevel(double riskScore) {
        if (riskScore < 30) return "LOW";
        if (riskScore < 70) return "MEDIUM";
        return "HIGH";
    }
}

@lombok.Data
@lombok.Builder
class TransactionCheckRequest {
    private String userId;
    private BigDecimal amount;
    private String currency;
    private String recipientId;
    private String transactionType;
    private Map<String, Object> metadata;
}

@lombok.Data
@lombok.Builder
class AdvancedCheckRequest {
    private String transactionId;
    private String userId;
    private BigDecimal amount;
    private String currency;
    private String recipientId;
    private String transactionType;
}

@lombok.Data
@lombok.Builder
class RiskScoreResponse {
    private String userId;
    private double riskScore;
    private String riskLevel;
    private long timestamp;
}

@lombok.Data
@lombok.Builder
class FraudAlert {
    private String alertId;
    private String userId;
    private String alertType;
    private String severity;
    private String description;
    private long timestamp;
    private String status;
}

@lombok.Data
@lombok.Builder
class SuspiciousActivityRequest {
    private String userId;
    private String activityType;
    private String description;
    private Map<String, Object> evidence;
}

@lombok.Data
@lombok.Builder
class WhitelistRequest {
    private String userId;
    private String recipientId;
}

@lombok.Data
@lombok.Builder
class TransactionPatterns {
    private String userId;
    private Map<String, Object> dailyPatterns;
    private Map<String, Object> weeklyPatterns;
    private Map<String, Object> monthlyPatterns;
    private List<String> anomalies;
    private long analyzedAt;
}

@lombok.Data
@lombok.Builder
class SecurityActionRequest {
    private String userId;
    private String action;
    private String reason;
    private String performedBy;
}