package com.waqiti.payment.client;

import com.waqiti.payment.client.dto.*;
import com.waqiti.payment.client.fallback.RealTimePaymentNetworkFallback;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Client for Real-Time Payment Networks (FedNow, RTP, Zelle)
 * Handles instant payment processing through various payment rails
 */
@FeignClient(
    name = "rtp-network-service",
    path = "/api/v1/rtp",
    fallback = RealTimePaymentNetworkFallback.class,
    configuration = RTPNetworkClientConfiguration.class
)
public interface RealTimePaymentNetworkClient {

    // Network Availability
    
    @GetMapping("/availability/fednow")
    ResponseEntity<Boolean> isFedNowAvailable();
    
    @GetMapping("/availability/rtp")
    ResponseEntity<Boolean> isRTPAvailable();
    
    @GetMapping("/availability/zelle")
    ResponseEntity<Boolean> isZelleAvailable();
    
    @GetMapping("/availability/all")
    ResponseEntity<NetworkAvailabilityStatus> getAllNetworkStatus();

    // FedNow Operations
    
    @PostMapping("/fednow/transfer")
    ResponseEntity<FedNowResponse> sendFedNowTransfer(@RequestBody Object fedNowRequest);
    
    @GetMapping("/fednow/status/{transactionId}")
    ResponseEntity<FedNowTransactionStatus> getFedNowStatus(@PathVariable String transactionId);
    
    @PostMapping("/fednow/cancel/{transactionId}")
    ResponseEntity<FedNowCancellationResponse> cancelFedNowTransfer(@PathVariable String transactionId);
    
    @GetMapping("/fednow/limits")
    ResponseEntity<FedNowLimits> getFedNowLimits();

    // RTP (Real-Time Payments) Operations
    
    @PostMapping("/rtp/transfer")
    ResponseEntity<RTPResponse> sendRTPTransfer(@RequestBody Object rtpRequest);
    
    @GetMapping("/rtp/status/{transactionId}")
    ResponseEntity<RTPTransactionStatus> getRTPStatus(@PathVariable String transactionId);
    
    @PostMapping("/rtp/request-for-payment")
    ResponseEntity<RTPRequestForPaymentResponse> sendRequestForPayment(@RequestBody RTPRequestForPayment request);
    
    @GetMapping("/rtp/limits")
    ResponseEntity<RTPLimits> getRTPLimits();

    // Zelle Operations
    
    @PostMapping("/zelle/transfer")
    ResponseEntity<ZelleResponse> sendZelleTransfer(@RequestBody Object zelleRequest);
    
    @GetMapping("/zelle/status/{transactionId}")
    ResponseEntity<ZelleTransactionStatus> getZelleStatus(@PathVariable String transactionId);
    
    @PostMapping("/zelle/enrollment/check")
    ResponseEntity<ZelleEnrollmentStatus> checkZelleEnrollment(@RequestBody ZelleEnrollmentCheck request);
    
    @GetMapping("/zelle/limits")
    ResponseEntity<ZelleLimits> getZelleLimits();

    // Bank Verification
    
    @PostMapping("/bank/verify")
    ResponseEntity<BankVerificationResponse> verifyBankAccount(@RequestBody BankVerificationRequest request);
    
    @GetMapping("/bank/routing/{routingNumber}")
    ResponseEntity<BankInfo> getBankInfo(@PathVariable String routingNumber);
    
    @PostMapping("/bank/capabilities")
    ResponseEntity<BankCapabilities> checkBankCapabilities(@RequestBody BankCapabilityRequest request);

    // Network Routing
    
    @PostMapping("/routing/optimal")
    ResponseEntity<OptimalRoutingResponse> determineOptimalRoute(@RequestBody RoutingRequest request);
    
    @PostMapping("/routing/cost")
    ResponseEntity<RoutingCostAnalysis> analyzeRoutingCosts(@RequestBody RoutingCostRequest request);
    
    @GetMapping("/routing/preferences/{userId}")
    ResponseEntity<RoutingPreferences> getUserRoutingPreferences(@PathVariable UUID userId);

    // Settlement and Clearing
    
    @PostMapping("/settlement/initiate")
    ResponseEntity<SettlementResponse> initiateSettlement(@RequestBody SettlementRequest request);
    
    @GetMapping("/settlement/status/{settlementId}")
    ResponseEntity<SettlementStatus> getSettlementStatus(@PathVariable String settlementId);
    
    @PostMapping("/clearing/batch")
    ResponseEntity<ClearingBatchResponse> processClearingBatch(@RequestBody ClearingBatchRequest request);

    // Compliance and Reporting
    
    @PostMapping("/compliance/report")
    ResponseEntity<ComplianceReportResponse> generateComplianceReport(@RequestBody ComplianceReportRequest request);
    
    @GetMapping("/compliance/requirements/{networkType}")
    ResponseEntity<NetworkComplianceRequirements> getComplianceRequirements(@PathVariable String networkType);
    
    @PostMapping("/reporting/transaction")
    ResponseEntity<TransactionReportResponse> reportTransaction(@RequestBody TransactionReportRequest request);

    // Network Health and Monitoring
    
    @GetMapping("/health/all")
    ResponseEntity<NetworkHealthStatus> getAllNetworkHealth();
    
    @GetMapping("/metrics/performance")
    ResponseEntity<NetworkPerformanceMetrics> getPerformanceMetrics();
    
    @GetMapping("/incidents/active")
    ResponseEntity<List<NetworkIncident>> getActiveIncidents();
}