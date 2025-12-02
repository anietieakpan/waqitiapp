package com.waqiti.merchant.controller;

import com.waqiti.merchant.dto.*;
import com.waqiti.merchant.service.MerchantPaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * REST controller for merchant payment operations
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/merchant")
@RequiredArgsConstructor
@Validated
@Tag(name = "Merchant Payment", description = "Merchant payment management APIs")
@SecurityRequirement(name = "bearer-jwt")
public class MerchantPaymentController {

    private final MerchantPaymentService merchantPaymentService;

    // ============== Merchant Registration and Onboarding ==============

    @PostMapping("/register")
    @Operation(summary = "Register a new merchant", description = "Public endpoint for merchant registration")
    public ResponseEntity<MerchantRegistrationResponse> registerMerchant(
            @Valid @RequestBody MerchantRegistrationRequest request) {
        
        log.info("Registering new merchant: {}", request.getBusinessName());
        MerchantRegistrationResponse response = merchantPaymentService.registerMerchant(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/verify-email")
    @Operation(summary = "Verify merchant email")
    public ResponseEntity<VerificationResponse> verifyEmail(
            @RequestParam @NotBlank String token) {
        
        log.info("Verifying merchant email with token");
        VerificationResponse response = merchantPaymentService.verifyEmail(token);
        return ResponseEntity.ok(response);
    }

    // ============== Merchant Profile Management ==============

    @GetMapping("/profile")
    @PreAuthorize("hasAuthority('SCOPE_merchant:profile-read')")
    @Operation(summary = "Get merchant profile")
    public ResponseEntity<MerchantProfileResponse> getMerchantProfile(
            @AuthenticationPrincipal Jwt jwt) {
        
        String merchantId = jwt.getClaim("merchant_id");
        log.debug("Fetching profile for merchant: {}", merchantId);
        
        MerchantProfileResponse profile = merchantPaymentService.getMerchantProfile(merchantId);
        return ResponseEntity.ok(profile);
    }

    @PutMapping("/profile")
    @PreAuthorize("hasAuthority('SCOPE_merchant:profile-update')")
    @Operation(summary = "Update merchant profile")
    public ResponseEntity<MerchantProfileResponse> updateMerchantProfile(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody UpdateMerchantProfileRequest request) {
        
        String merchantId = jwt.getClaim("merchant_id");
        log.info("Updating profile for merchant: {}", merchantId);
        
        MerchantProfileResponse profile = merchantPaymentService.updateMerchantProfile(merchantId, request);
        return ResponseEntity.ok(profile);
    }

    // ============== KYC and Document Management ==============

    @PostMapping("/kyc/submit")
    @PreAuthorize("hasAuthority('SCOPE_merchant:kyc-submit')")
    @Operation(summary = "Submit KYC documents")
    public ResponseEntity<KYCSubmissionResponse> submitKYC(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody KYCSubmissionRequest request) {
        
        String merchantId = jwt.getClaim("merchant_id");
        log.info("Submitting KYC for merchant: {}", merchantId);
        
        KYCSubmissionResponse response = merchantPaymentService.submitKYC(merchantId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping(value = "/documents/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('SCOPE_merchant:document-upload')")
    @Operation(summary = "Upload merchant documents")
    public ResponseEntity<DocumentUploadResponse> uploadDocument(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam("file") MultipartFile file,
            @RequestParam("type") String documentType) {
        
        String merchantId = jwt.getClaim("merchant_id");
        log.info("Uploading {} document for merchant: {}", documentType, merchantId);
        
        DocumentUploadResponse response = merchantPaymentService.uploadDocument(merchantId, file, documentType);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // ============== Payment Processing ==============

    @PostMapping("/payments/process")
    @PreAuthorize("hasAuthority('SCOPE_merchant:payment-process')")
    @Operation(summary = "Process a payment")
    public ResponseEntity<PaymentProcessResponse> processPayment(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody ProcessPaymentRequest request) {
        
        String merchantId = jwt.getClaim("merchant_id");
        log.info("Processing payment for merchant: {} - amount: {}", merchantId, request.getAmount());
        
        PaymentProcessResponse response = merchantPaymentService.processPayment(merchantId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/payments/refund")
    @PreAuthorize("hasAuthority('SCOPE_merchant:payment-refund')")
    @Operation(summary = "Refund a payment")
    public ResponseEntity<RefundResponse> refundPayment(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody RefundRequest request) {
        
        String merchantId = jwt.getClaim("merchant_id");
        log.info("Processing refund for merchant: {} - payment: {}", merchantId, request.getPaymentId());
        
        RefundResponse response = merchantPaymentService.refundPayment(merchantId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/payments")
    @PreAuthorize("hasAuthority('SCOPE_merchant:payment-read')")
    @Operation(summary = "Get payment history")
    public ResponseEntity<Page<PaymentHistoryResponse>> getPaymentHistory(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(required = false) String status,
            Pageable pageable) {
        
        String merchantId = jwt.getClaim("merchant_id");
        log.debug("Fetching payment history for merchant: {}", merchantId);
        
        Page<PaymentHistoryResponse> payments = merchantPaymentService.getPaymentHistory(
            merchantId, fromDate, toDate, status, pageable);
        return ResponseEntity.ok(payments);
    }

    // ============== QR Code Payments ==============

    @PostMapping("/qr/generate")
    @PreAuthorize("hasAuthority('SCOPE_merchant:qr-generate')")
    @Operation(summary = "Generate QR code for payment")
    public ResponseEntity<QRCodeResponse> generateQRCode(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody GenerateQRCodeRequest request) {
        
        String merchantId = jwt.getClaim("merchant_id");
        log.info("Generating QR code for merchant: {} - amount: {}", merchantId, request.getAmount());
        
        QRCodeResponse response = merchantPaymentService.generateQRCode(merchantId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/qr/static")
    @PreAuthorize("hasAuthority('SCOPE_merchant:qr-static')")
    @Operation(summary = "Generate static QR code")
    public ResponseEntity<StaticQRCodeResponse> generateStaticQRCode(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody StaticQRCodeRequest request) {
        
        String merchantId = jwt.getClaim("merchant_id");
        log.info("Generating static QR code for merchant: {}", merchantId);
        
        StaticQRCodeResponse response = merchantPaymentService.generateStaticQRCode(merchantId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/qr/{qrCodeId}/status")
    @PreAuthorize("hasAuthority('SCOPE_merchant:qr-status')")
    @Operation(summary = "Get QR code payment status")
    public ResponseEntity<QRCodeStatusResponse> getQRCodeStatus(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String qrCodeId) {
        
        String merchantId = jwt.getClaim("merchant_id");
        log.debug("Checking QR code status {} for merchant: {}", qrCodeId, merchantId);
        
        QRCodeStatusResponse status = merchantPaymentService.getQRCodeStatus(merchantId, qrCodeId);
        return ResponseEntity.ok(status);
    }

    // ============== POS Terminal Management ==============

    @PostMapping("/pos/register")
    @PreAuthorize("hasAuthority('SCOPE_merchant:pos-register')")
    @Operation(summary = "Register a new POS terminal")
    public ResponseEntity<POSTerminalResponse> registerPOSTerminal(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody RegisterPOSTerminalRequest request) {
        
        String merchantId = jwt.getClaim("merchant_id");
        log.info("Registering POS terminal for merchant: {}", merchantId);
        
        POSTerminalResponse response = merchantPaymentService.registerPOSTerminal(merchantId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/pos/terminals")
    @PreAuthorize("hasAuthority('SCOPE_merchant:pos-read')")
    @Operation(summary = "Get all POS terminals")
    public ResponseEntity<List<POSTerminalResponse>> getPOSTerminals(
            @AuthenticationPrincipal Jwt jwt) {
        
        String merchantId = jwt.getClaim("merchant_id");
        log.debug("Fetching POS terminals for merchant: {}", merchantId);
        
        List<POSTerminalResponse> terminals = merchantPaymentService.getPOSTerminals(merchantId);
        return ResponseEntity.ok(terminals);
    }

    @PostMapping("/pos/terminals/{terminalId}/activate")
    @PreAuthorize("hasAuthority('SCOPE_merchant:pos-activate')")
    @Operation(summary = "Activate POS terminal")
    public ResponseEntity<POSTerminalResponse> activatePOSTerminal(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String terminalId) {
        
        String merchantId = jwt.getClaim("merchant_id");
        log.info("Activating POS terminal {} for merchant: {}", terminalId, merchantId);
        
        POSTerminalResponse response = merchantPaymentService.activatePOSTerminal(merchantId, terminalId);
        return ResponseEntity.ok(response);
    }

    // ============== Settlement and Payouts ==============

    @GetMapping("/settlements")
    @PreAuthorize("hasAuthority('SCOPE_merchant:settlement-read')")
    @Operation(summary = "Get settlement history")
    public ResponseEntity<Page<SettlementResponse>> getSettlements(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            Pageable pageable) {
        
        String merchantId = jwt.getClaim("merchant_id");
        log.debug("Fetching settlements for merchant: {}", merchantId);
        
        Page<SettlementResponse> settlements = merchantPaymentService.getSettlements(
            merchantId, fromDate, toDate, pageable);
        return ResponseEntity.ok(settlements);
    }

    @PostMapping("/settlements/request")
    @PreAuthorize("hasAuthority('SCOPE_merchant:settlement-request')")
    @Operation(summary = "Request immediate settlement")
    public ResponseEntity<SettlementRequestResponse> requestSettlement(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody SettlementRequest request) {
        
        String merchantId = jwt.getClaim("merchant_id");
        log.info("Requesting settlement for merchant: {} - amount: {}", merchantId, request.getAmount());
        
        SettlementRequestResponse response = merchantPaymentService.requestSettlement(merchantId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/balance")
    @PreAuthorize("hasAuthority('SCOPE_merchant:balance-read')")
    @Operation(summary = "Get merchant balance")
    public ResponseEntity<MerchantBalanceResponse> getMerchantBalance(
            @AuthenticationPrincipal Jwt jwt) {
        
        String merchantId = jwt.getClaim("merchant_id");
        log.debug("Fetching balance for merchant: {}", merchantId);
        
        MerchantBalanceResponse balance = merchantPaymentService.getMerchantBalance(merchantId);
        return ResponseEntity.ok(balance);
    }

    // ============== Analytics and Reporting ==============

    @GetMapping("/analytics/dashboard")
    @PreAuthorize("hasAuthority('SCOPE_merchant:analytics-read')")
    @Operation(summary = "Get merchant analytics dashboard")
    public ResponseEntity<DashboardAnalyticsResponse> getDashboardAnalytics(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "30") int days) {
        
        String merchantId = jwt.getClaim("merchant_id");
        log.debug("Fetching dashboard analytics for merchant: {} - days: {}", merchantId, days);
        
        DashboardAnalyticsResponse analytics = merchantPaymentService.getDashboardAnalytics(merchantId, days);
        return ResponseEntity.ok(analytics);
    }

    @GetMapping("/analytics/revenue")
    @PreAuthorize("hasAuthority('SCOPE_merchant:analytics-revenue')")
    @Operation(summary = "Get revenue analytics")
    public ResponseEntity<RevenueAnalyticsResponse> getRevenueAnalytics(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(defaultValue = "DAILY") String groupBy) {
        
        String merchantId = jwt.getClaim("merchant_id");
        log.debug("Fetching revenue analytics for merchant: {} from {} to {}", merchantId, fromDate, toDate);
        
        RevenueAnalyticsResponse analytics = merchantPaymentService.getRevenueAnalytics(
            merchantId, fromDate, toDate, groupBy);
        return ResponseEntity.ok(analytics);
    }

    // ============== Webhook Management ==============

    @GetMapping("/webhooks")
    @PreAuthorize("hasAuthority('SCOPE_merchant:webhook-read')")
    @Operation(summary = "Get configured webhooks")
    public ResponseEntity<List<WebhookConfigResponse>> getWebhooks(
            @AuthenticationPrincipal Jwt jwt) {
        
        String merchantId = jwt.getClaim("merchant_id");
        log.debug("Fetching webhooks for merchant: {}", merchantId);
        
        List<WebhookConfigResponse> webhooks = merchantPaymentService.getWebhooks(merchantId);
        return ResponseEntity.ok(webhooks);
    }

    @PostMapping("/webhooks")
    @PreAuthorize("hasAuthority('SCOPE_merchant:webhook-create')")
    @Operation(summary = "Create webhook configuration")
    public ResponseEntity<WebhookConfigResponse> createWebhook(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CreateWebhookRequest request) {
        
        String merchantId = jwt.getClaim("merchant_id");
        log.info("Creating webhook for merchant: {} - url: {}", merchantId, request.getUrl());
        
        WebhookConfigResponse response = merchantPaymentService.createWebhook(merchantId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/webhooks/test")
    @PreAuthorize("hasAuthority('SCOPE_merchant:webhook-test')")
    @Operation(summary = "Test webhook configuration")
    public ResponseEntity<WebhookTestResponse> testWebhook(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam UUID webhookId) {
        
        String merchantId = jwt.getClaim("merchant_id");
        log.info("Testing webhook {} for merchant: {}", webhookId, merchantId);
        
        WebhookTestResponse response = merchantPaymentService.testWebhook(merchantId, webhookId);
        return ResponseEntity.ok(response);
    }

    // ============== Settings Management ==============

    @GetMapping("/settings")
    @PreAuthorize("hasAuthority('SCOPE_merchant:settings-read')")
    @Operation(summary = "Get merchant settings")
    public ResponseEntity<MerchantSettingsResponse> getMerchantSettings(
            @AuthenticationPrincipal Jwt jwt) {
        
        String merchantId = jwt.getClaim("merchant_id");
        log.debug("Fetching settings for merchant: {}", merchantId);
        
        MerchantSettingsResponse settings = merchantPaymentService.getMerchantSettings(merchantId);
        return ResponseEntity.ok(settings);
    }

    @PutMapping("/settings")
    @PreAuthorize("hasAuthority('SCOPE_merchant:settings-update')")
    @Operation(summary = "Update merchant settings")
    public ResponseEntity<MerchantSettingsResponse> updateMerchantSettings(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody UpdateSettingsRequest request) {
        
        String merchantId = jwt.getClaim("merchant_id");
        log.info("Updating settings for merchant: {}", merchantId);
        
        MerchantSettingsResponse settings = merchantPaymentService.updateMerchantSettings(merchantId, request);
        return ResponseEntity.ok(settings);
    }
}