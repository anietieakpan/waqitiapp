package com.waqiti.payment.client.fallback;

import com.waqiti.payment.client.RealTimePaymentNetworkClient;
import com.waqiti.payment.client.dto.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Fallback implementation for RealTimePaymentNetworkClient
 * Ensures graceful degradation when payment networks are unavailable
 */
@Component
@Slf4j
public class RealTimePaymentNetworkFallback implements RealTimePaymentNetworkClient {

    @Override
    public ResponseEntity<Boolean> isFedNowAvailable() {
        log.warn("FALLBACK: FedNow availability check failed");
        return ResponseEntity.ok(false);
    }

    @Override
    public ResponseEntity<Boolean> isRTPAvailable() {
        log.warn("FALLBACK: RTP availability check failed");
        return ResponseEntity.ok(false);
    }

    @Override
    public ResponseEntity<Boolean> isZelleAvailable() {
        log.warn("FALLBACK: Zelle availability check failed");
        return ResponseEntity.ok(false);
    }

    @Override
    public ResponseEntity<NetworkAvailabilityStatus> getAllNetworkStatus() {
        log.warn("FALLBACK: Network status check failed - returning all networks as unavailable");
        
        NetworkAvailabilityStatus status = NetworkAvailabilityStatus.builder()
            .networkStatus(Map.of(
                "FEDNOW", false,
                "RTP", false,
                "ZELLE", false
            ))
            .overallStatus("UNAVAILABLE")
            .networkMessages(Map.of(
                "FEDNOW", "Service unavailable",
                "RTP", "Service unavailable", 
                "ZELLE", "Service unavailable"
            ))
            .build();
            
        return ResponseEntity.ok(status);
    }

    @Override
    public ResponseEntity<FedNowResponse> sendFedNowTransfer(Object fedNowRequest) {
        log.error("FALLBACK: FedNow transfer failed - service unavailable");
        
        FedNowResponse response = FedNowResponse.builder()
            .transactionId("FALLBACK-" + UUID.randomUUID().toString())
            .status("FAILED")
            .errorMessage("FedNow service temporarily unavailable")
            .errorCode("SERVICE_UNAVAILABLE")
            .build();
            
        return ResponseEntity.ok(response);
    }

    @Override
    public ResponseEntity<RTPResponse> sendRTPTransfer(Object rtpRequest) {
        log.error("FALLBACK: RTP transfer failed - service unavailable");
        
        RTPResponse response = RTPResponse.builder()
            .transactionId("FALLBACK-" + UUID.randomUUID().toString())
            .status("FAILED")
            .errorMessage("RTP service temporarily unavailable")
            .errorCode("SERVICE_UNAVAILABLE")
            .build();
            
        return ResponseEntity.ok(response);
    }

    @Override
    public ResponseEntity<ZelleResponse> sendZelleTransfer(Object zelleRequest) {
        log.error("FALLBACK: Zelle transfer failed - service unavailable");
        
        ZelleResponse response = ZelleResponse.builder()
            .transactionId("FALLBACK-" + UUID.randomUUID().toString())
            .status("FAILED")
            .errorMessage("Zelle service temporarily unavailable")
            .errorCode("SERVICE_UNAVAILABLE")
            .build();
            
        return ResponseEntity.ok(response);
    }

    // Default fallback implementations for all other methods
    @Override
    public ResponseEntity<Object> getFedNowStatus(String transactionId) {
        return ResponseEntity.ok(createFailureResponse(transactionId));
    }

    @Override
    public ResponseEntity<Object> cancelFedNowTransfer(String transactionId) {
        return ResponseEntity.ok(createFailureResponse(transactionId));
    }

    @Override
    public ResponseEntity<Object> getFedNowLimits() {
        return ResponseEntity.ok(Collections.emptyMap());
    }

    @Override
    public ResponseEntity<Object> getRTPStatus(String transactionId) {
        return ResponseEntity.ok(createFailureResponse(transactionId));
    }

    @Override
    public ResponseEntity<Object> sendRequestForPayment(Object request) {
        return ResponseEntity.ok(createGenericFailureResponse());
    }

    @Override
    public ResponseEntity<Object> getRTPLimits() {
        return ResponseEntity.ok(Collections.emptyMap());
    }

    @Override
    public ResponseEntity<Object> getZelleStatus(String transactionId) {
        return ResponseEntity.ok(createFailureResponse(transactionId));
    }

    @Override
    public ResponseEntity<Object> checkZelleEnrollment(Object request) {
        return ResponseEntity.ok(Map.of(
            "enrolled", false,
            "fallback", true
        ));
    }

    @Override
    public ResponseEntity<Object> getZelleLimits() {
        return ResponseEntity.ok(Collections.emptyMap());
    }

    @Override
    public ResponseEntity<Object> verifyBankAccount(Object request) {
        return ResponseEntity.ok(Map.of(
            "verified", false,
            "fallback", true
        ));
    }

    @Override
    public ResponseEntity<Object> getBankInfo(String routingNumber) {
        return ResponseEntity.ok(createGenericFailureResponse());
    }

    @Override
    public ResponseEntity<Object> checkBankCapabilities(Object request) {
        return ResponseEntity.ok(createGenericFailureResponse());
    }

    @Override
    public ResponseEntity<Object> determineOptimalRoute(Object request) {
        return ResponseEntity.ok(Map.of(
            "recommendedRoute", "INTERNAL",
            "reason", "External networks unavailable",
            "fallback", true
        ));
    }

    @Override
    public ResponseEntity<Object> analyzeRoutingCosts(Object request) {
        return ResponseEntity.ok(createGenericFailureResponse());
    }

    @Override
    public ResponseEntity<Object> getUserRoutingPreferences(UUID userId) {
        return ResponseEntity.ok(createGenericFailureResponse());
    }

    @Override
    public ResponseEntity<Object> initiateSettlement(Object request) {
        return ResponseEntity.ok(createGenericFailureResponse());
    }

    @Override
    public ResponseEntity<Object> getSettlementStatus(String settlementId) {
        return ResponseEntity.ok(createFailureResponse(settlementId));
    }

    @Override
    public ResponseEntity<Object> processClearingBatch(Object request) {
        return ResponseEntity.ok(createGenericFailureResponse());
    }

    @Override
    public ResponseEntity<Object> generateComplianceReport(Object request) {
        return ResponseEntity.ok(createGenericFailureResponse());
    }

    @Override
    public ResponseEntity<Object> getComplianceRequirements(String networkType) {
        return ResponseEntity.ok(createGenericFailureResponse());
    }

    @Override
    public ResponseEntity<Object> reportTransaction(Object request) {
        return ResponseEntity.ok(createGenericFailureResponse());
    }

    @Override
    public ResponseEntity<Object> getAllNetworkHealth() {
        return ResponseEntity.ok(Map.of(
            "overallHealth", "DEGRADED",
            "fallback", true
        ));
    }

    @Override
    public ResponseEntity<Object> getPerformanceMetrics() {
        return ResponseEntity.ok(createGenericFailureResponse());
    }

    @Override
    public ResponseEntity<List<Object>> getActiveIncidents() {
        return ResponseEntity.ok(Collections.emptyList());
    }

    // Helper methods
    private Map<String, Object> createFailureResponse(String transactionId) {
        return Map.of(
            "transactionId", transactionId,
            "status", "FAILED",
            "errorMessage", "Service temporarily unavailable",
            "fallback", true
        );
    }

    private Map<String, Object> createGenericFailureResponse() {
        return Map.of(
            "status", "FAILED",
            "errorMessage", "Service temporarily unavailable",
            "fallback", true
        );
    }
}