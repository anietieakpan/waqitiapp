package com.waqiti.payment.client;

import com.waqiti.payment.dto.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@Slf4j
public class RiskServiceClientFallback implements RiskServiceClient {

    @Override
    public ResponseEntity<UpdateUserRiskScoreResponse> updateUserRiskScore(UpdateUserRiskScoreRequest request) {
        log.warn("Risk Service fallback: updateUserRiskScore - userId={}. Risk scores will be updated via batch reconciliation.",
            request.getUserId());
        
        UpdateUserRiskScoreResponse response = UpdateUserRiskScoreResponse.builder()
            .success(false)
            .message("Risk service unavailable - update queued for batch processing")
            .userId(request.getUserId())
            .build();
        
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(response);
    }

    @Override
    public ResponseEntity<UserRiskScoreResponse> getUserRiskScore(String userId) {
        log.warn("Risk Service fallback: getUserRiskScore - userId={}", userId);
        
        UserRiskScoreResponse response = UserRiskScoreResponse.builder()
            .userId(userId)
            .riskScore(0.5)
            .riskLevel("MEDIUM")
            .message("Risk service unavailable - returning default risk score")
            .build();
        
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(response);
    }

    @Override
    public ResponseEntity<TransactionRiskAssessmentResponse> assessTransactionRisk(TransactionRiskAssessmentRequest request) {
        log.warn("Risk Service fallback: assessTransactionRisk - transactionId={}", request.getTransactionId());
        
        TransactionRiskAssessmentResponse response = TransactionRiskAssessmentResponse.builder()
            .transactionId(request.getTransactionId())
            .riskScore(0.5)
            .riskLevel("MEDIUM")
            .approved(false)
            .requiresManualReview(true)
            .message("Risk service unavailable - transaction requires manual review")
            .build();
        
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(response);
    }

    @Override
    public ResponseEntity<UserRiskProfile> getUserRiskProfile(String userId) {
        log.warn("Risk Service fallback: getUserRiskProfile - userId={}", userId);
        
        UserRiskProfile profile = UserRiskProfile.builder()
            .userId(userId)
            .riskLevel("UNKNOWN")
            .message("Risk service unavailable")
            .build();
        
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(profile);
    }

    @Override
    public ResponseEntity<Void> enableEnhancedMonitoring(String userId, EnhancedMonitoringRequest request) {
        log.warn("Risk Service fallback: enableEnhancedMonitoring - userId={}. Enhanced monitoring will be enabled via batch job.",
            userId);
        
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
    }

    @Override
    public ResponseEntity<List<RiskAlert>> getUserRiskAlerts(String userId, int page, int size) {
        log.warn("Risk Service fallback: getUserRiskAlerts - userId={}", userId);
        
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(List.of());
    }
}