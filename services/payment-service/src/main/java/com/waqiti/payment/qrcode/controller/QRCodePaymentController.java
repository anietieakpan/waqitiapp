package com.waqiti.payment.qrcode.controller;

import com.waqiti.common.ratelimit.RateLimit;
import com.waqiti.common.security.RequiresScope;
import com.waqiti.payment.qrcode.dto.*;
import com.waqiti.payment.qrcode.service.QRCodePaymentService;
import com.waqiti.payment.qrcode.service.QRCodeAnalyticsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * REST Controller for QR Code Payment operations
 * Provides comprehensive API for QR code generation, scanning, and payment processing
 */
@RestController
@RequestMapping("/api/v1/qr-payments")
@RequiredArgsConstructor
@Slf4j
@Validated
@Tag(name = "QR Code Payments", description = "QR Code payment generation, scanning and processing")
@SecurityRequirement(name = "Bearer Authentication")
public class QRCodePaymentController {
    
    private final QRCodePaymentService qrCodePaymentService;
    private final QRCodeAnalyticsService qrCodeAnalyticsService;
    
    /**
     * Generate a payment QR code
     */
    @PostMapping("/generate")
    @Operation(summary = "Generate a payment QR code", 
               description = "Creates a new QR code for receiving payments with customizable options")
    @ApiResponse(responseCode = "201", description = "QR code generated successfully")
    @ApiResponse(responseCode = "400", description = "Invalid request parameters")
    @ApiResponse(responseCode = "401", description = "Authentication required")
    @ApiResponse(responseCode = "403", description = "Insufficient permissions")
    @ApiResponse(responseCode = "429", description = "Rate limit exceeded")
    @RequiresScope("SCOPE_payment:qr-generate")
    @RateLimit(key = "qr-generate", limit = 10, window = 60) // 10 per minute
    public ResponseEntity<QRCodeGenerationResponse> generateQRCode(
            @Valid @RequestBody GenerateQRCodeRequest request,
            HttpServletRequest httpRequest) {
        
        log.info("Generating QR code for user: {} with type: {}", request.getUserId(), request.getType());
        
        // Add request context
        request.getMetadata().put("ip_address", getClientIP(httpRequest));
        request.getMetadata().put("user_agent", httpRequest.getHeader("User-Agent"));
        
        QRCodeGenerationResponse response = qrCodePaymentService.generatePaymentQRCode(request);
        
        return ResponseEntity.status(HttpStatus.CREATED)
                .header("X-QR-Code-ID", response.getQrCodeId())
                .body(response);
    }
    
    /**
     * Scan and validate a QR code
     */
    @PostMapping("/scan")
    @Operation(summary = "Scan and validate a QR code", 
               description = "Scans a QR code and returns payment details for confirmation")
    @ApiResponse(responseCode = "200", description = "QR code scanned successfully")
    @ApiResponse(responseCode = "404", description = "QR code not found")
    @ApiResponse(responseCode = "410", description = "QR code expired")
    @RequiresScope("SCOPE_payment:qr-scan")
    @RateLimit(key = "qr-scan", limit = 30, window = 60) // 30 per minute
    public ResponseEntity<QRCodeScanResponse> scanQRCode(
            @Valid @RequestBody ScanQRCodeRequest request,
            HttpServletRequest httpRequest) {
        
        log.info("Scanning QR code by user: {}", request.getScannerUserId());
        
        // Add security context
        request.getDeviceInfo().setIpAddress(getClientIP(httpRequest));
        
        QRCodeScanResponse response = qrCodePaymentService.scanQRCode(request);
        
        return ResponseEntity.ok()
                .header("X-Security-Level", response.getSecurityFeatures() != null ? "HIGH" : "STANDARD")
                .body(response);
    }
    
    /**
     * Process a QR code payment
     */
    @PostMapping("/process")
    @Operation(summary = "Process a QR code payment", 
               description = "Executes the actual payment after QR code scanning and confirmation")
    @ApiResponse(responseCode = "200", description = "Payment processed successfully")
    @ApiResponse(responseCode = "402", description = "Payment required - insufficient funds")
    @ApiResponse(responseCode = "409", description = "Payment conflict - QR code already used")
    @RequiresScope("SCOPE_payment:qr-process")
    @RateLimit(key = "qr-process", limit = 5, window = 60) // 5 per minute
    public ResponseEntity<QRCodePaymentResponse> processQRCodePayment(
            @Valid @RequestBody ProcessQRCodePaymentRequest request) {
        
        log.info("Processing QR code payment: {} by user: {}", request.getQrCodeId(), request.getPayerId());
        
        QRCodePaymentResponse response = qrCodePaymentService.processQRCodePayment(request);
        
        return ResponseEntity.ok()
                .header("X-Transaction-ID", response.getTransactionId())
                .body(response);
    }
    
    /**
     * Generate merchant static QR code
     */
    @PostMapping("/merchant/generate")
    @Operation(summary = "Generate merchant static QR code", 
               description = "Creates a reusable QR code for merchant payments")
    @RequiresScope("SCOPE_merchant:qr-generate")
    @PreAuthorize("hasRole('MERCHANT') or hasRole('ADMIN')")
    public ResponseEntity<MerchantQRCodeResponse> generateMerchantQRCode(
            @Valid @RequestBody GenerateMerchantQRCodeRequest request) {
        
        log.info("Generating merchant QR code for: {}", request.getMerchantId());
        
        MerchantQRCodeResponse response = qrCodePaymentService.generateMerchantQRCode(request);
        
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
    
    /**
     * Get QR code status
     */
    @GetMapping("/{qrCodeId}/status")
    @Operation(summary = "Get QR code status", 
               description = "Retrieves current status and details of a QR code")
    @RequiresScope("SCOPE_payment:qr-read")
    public ResponseEntity<QRCodeStatusResponse> getQRCodeStatus(
            @Parameter(description = "QR code identifier") 
            @PathVariable @NotBlank String qrCodeId) {
        
        QRCodeStatusResponse response = qrCodePaymentService.getQRCodeStatus(qrCodeId);
        
        return ResponseEntity.ok()
                .header("Cache-Control", "no-cache")
                .body(response);
    }
    
    /**
     * Cancel a QR code
     */
    @DeleteMapping("/{qrCodeId}")
    @Operation(summary = "Cancel a QR code", 
               description = "Cancels an active QR code, making it unavailable for payments")
    @RequiresScope("SCOPE_payment:qr-cancel")
    public ResponseEntity<Void> cancelQRCode(
            @Parameter(description = "QR code identifier") 
            @PathVariable @NotBlank String qrCodeId,
            @Parameter(description = "User ID requesting cancellation")
            @RequestParam @NotBlank String userId) {
        
        log.info("Cancelling QR code: {} by user: {}", qrCodeId, userId);
        
        qrCodePaymentService.cancelQRCode(qrCodeId, userId);
        
        return ResponseEntity.noContent().build();
    }
    
    /**
     * Get user's QR codes
     */
    @GetMapping("/user/{userId}")
    @Operation(summary = "Get user's QR codes", 
               description = "Retrieves paginated list of QR codes for a specific user")
    @RequiresScope("SCOPE_payment:qr-read")
    @PreAuthorize("#userId == authentication.principal.userId or hasRole('ADMIN')")
    public ResponseEntity<Page<QRCodeSummaryResponse>> getUserQRCodes(
            @Parameter(description = "User identifier") 
            @PathVariable @NotBlank String userId,
            @Parameter(description = "Filter by status")
            @RequestParam(required = false) String status,
            @PageableDefault(size = 20) Pageable pageable) {
        
        Page<QRCodeSummaryResponse> response = qrCodePaymentService.getUserQRCodes(userId, status, pageable);
        
        return ResponseEntity.ok()
                .header("X-Total-Count", String.valueOf(response.getTotalElements()))
                .body(response);
    }
    
    /**
     * Get merchant's QR codes
     */
    @GetMapping("/merchant/{merchantId}")
    @Operation(summary = "Get merchant's QR codes", 
               description = "Retrieves paginated list of QR codes for a specific merchant")
    @RequiresScope("SCOPE_merchant:qr-read")
    @PreAuthorize("@merchantService.isOwnerOrEmployee(#merchantId, authentication.principal.userId) or hasRole('ADMIN')")
    public ResponseEntity<Page<QRCodeSummaryResponse>> getMerchantQRCodes(
            @Parameter(description = "Merchant identifier") 
            @PathVariable @NotBlank String merchantId,
            @Parameter(description = "Filter by status")
            @RequestParam(required = false) String status,
            @Parameter(description = "Filter by type")
            @RequestParam(required = false) String type,
            @PageableDefault(size = 20) Pageable pageable) {
        
        Page<QRCodeSummaryResponse> response = qrCodePaymentService.getMerchantQRCodes(merchantId, status, type, pageable);
        
        return ResponseEntity.ok()
                .header("X-Total-Count", String.valueOf(response.getTotalElements()))
                .body(response);
    }
    
    /**
     * Bulk generate QR codes
     */
    @PostMapping("/bulk-generate")
    @Operation(summary = "Bulk generate QR codes", 
               description = "Generates multiple QR codes for campaigns or events")
    @RequiresScope("SCOPE_merchant:qr-bulk-generate")
    @PreAuthorize("hasRole('MERCHANT') or hasRole('ADMIN')")
    @RateLimit(key = "qr-bulk-generate", limit = 2, window = 300) // 2 per 5 minutes
    public ResponseEntity<BulkQRCodeGenerationResponse> bulkGenerateQRCodes(
            @Valid @RequestBody BulkQRCodeGenerationRequest request) {
        
        log.info("Bulk generating {} QR codes for merchant: {}", request.getCount(), request.getMerchantId());
        
        BulkQRCodeGenerationResponse response = qrCodePaymentService.bulkGenerateQRCodes(request);
        
        return ResponseEntity.status(HttpStatus.CREATED)
                .header("X-Bulk-Operation-ID", response.getOperationId())
                .body(response);
    }
    
    /**
     * Get QR code analytics
     */
    @GetMapping("/analytics")
    @Operation(summary = "Get QR code analytics", 
               description = "Retrieves analytics and statistics for QR code usage")
    @RequiresScope("SCOPE_analytics:qr-read")
    public ResponseEntity<QRCodeAnalyticsResponse> getQRCodeAnalytics(
            @Parameter(description = "Merchant ID (optional)")
            @RequestParam(required = false) String merchantId,
            @Parameter(description = "User ID (optional)")
            @RequestParam(required = false) String userId,
            @Parameter(description = "Start date")
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @Parameter(description = "End date")
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate,
            @Parameter(description = "Group by period")
            @RequestParam(defaultValue = "DAY") String groupBy) {
        
        QRCodeAnalyticsRequest analyticsRequest = QRCodeAnalyticsRequest.builder()
                .merchantId(merchantId)
                .userId(userId)
                .startDate(startDate)
                .endDate(endDate)
                .groupBy(groupBy)
                .build();
        
        QRCodeAnalyticsResponse response = qrCodeAnalyticsService.getAnalytics(analyticsRequest);
        
        return ResponseEntity.ok()
                .header("Cache-Control", "public, max-age=300") // 5 minutes cache
                .body(response);
    }
    
    /**
     * Download QR code image
     */
    @GetMapping("/{qrCodeId}/image")
    @Operation(summary = "Download QR code image", 
               description = "Downloads the QR code image in PNG format")
    public ResponseEntity<byte[]> downloadQRCodeImage(
            @Parameter(description = "QR code identifier") 
            @PathVariable @NotBlank String qrCodeId,
            @Parameter(description = "Image size in pixels")
            @RequestParam(defaultValue = "300") @Min(200) @Max(1000) int size,
            @Parameter(description = "Include logo")
            @RequestParam(defaultValue = "false") boolean includeLogo) {
        
        byte[] imageData = qrCodePaymentService.generateQRCodeImage(qrCodeId, size, includeLogo);
        
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_PNG)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"qr-code-" + qrCodeId + ".png\"")
                .header("Cache-Control", "public, max-age=3600") // 1 hour cache
                .body(imageData);
    }
    
    /**
     * Validate QR code data
     */
    @PostMapping("/validate")
    @Operation(summary = "Validate QR code data", 
               description = "Validates QR code data format and signature without processing payment")
    @RequiresScope("SCOPE_payment:qr-validate")
    public ResponseEntity<QRCodeValidationResponse> validateQRCode(
            @Valid @RequestBody QRCodeValidationRequest request) {
        
        QRCodeValidationResponse response = qrCodePaymentService.validateQRCodeData(request);
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * Get payment history for QR code
     */
    @GetMapping("/{qrCodeId}/history")
    @Operation(summary = "Get QR code payment history", 
               description = "Retrieves payment history and audit trail for a QR code")
    @RequiresScope("SCOPE_payment:qr-read")
    public ResponseEntity<List<QRCodePaymentHistoryResponse>> getQRCodeHistory(
            @Parameter(description = "QR code identifier") 
            @PathVariable @NotBlank String qrCodeId) {
        
        List<QRCodePaymentHistoryResponse> response = qrCodePaymentService.getQRCodeHistory(qrCodeId);
        
        return ResponseEntity.ok()
                .header("Cache-Control", "private, max-age=60") // 1 minute cache
                .body(response);
    }
    
    /**
     * Refresh QR code (extend expiry)
     */
    @PutMapping("/{qrCodeId}/refresh")
    @Operation(summary = "Refresh QR code", 
               description = "Extends the expiry time of an active QR code")
    @RequiresScope("SCOPE_payment:qr-manage")
    @RateLimit(key = "qr-refresh", limit = 5, window = 300) // 5 per 5 minutes
    public ResponseEntity<QRCodeGenerationResponse> refreshQRCode(
            @Parameter(description = "QR code identifier") 
            @PathVariable @NotBlank String qrCodeId,
            @Valid @RequestBody QRCodeRefreshRequest request) {
        
        log.info("Refreshing QR code: {} with new expiry: {}", qrCodeId, request.getNewExpiryMinutes());
        
        QRCodeGenerationResponse response = qrCodePaymentService.refreshQRCode(qrCodeId, request);
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * Get fraud detection alerts for QR codes
     */
    @GetMapping("/fraud-alerts")
    @Operation(summary = "Get fraud detection alerts", 
               description = "Retrieves fraud detection alerts for QR code transactions")
    @RequiresScope("SCOPE_security:fraud-read")
    @PreAuthorize("hasRole('ADMIN') or hasRole('COMPLIANCE_OFFICER')")
    public ResponseEntity<List<QRCodeFraudAlertResponse>> getFraudAlerts(
            @Parameter(description = "Alert severity level")
            @RequestParam(defaultValue = "MEDIUM") String severity,
            @Parameter(description = "Hours to look back")
            @RequestParam(defaultValue = "24") @Min(1) @Max(168) int hours) {
        
        List<QRCodeFraudAlertResponse> response = qrCodePaymentService.getFraudAlerts(severity, hours);
        
        return ResponseEntity.ok()
                .header("X-Alert-Count", String.valueOf(response.size()))
                .body(response);
    }
    
    // Helper methods
    
    private String getClientIP(HttpServletRequest request) {
        String xfHeader = request.getHeader("X-Forwarded-For");
        if (xfHeader == null) {
            return request.getRemoteAddr();
        }
        return xfHeader.split(",")[0].trim();
    }
}