package com.waqiti.payment.controller;

import com.waqiti.payment.dto.*;
import com.waqiti.payment.service.NFCPaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * NFC Payment Controller
 * Handles all NFC-related payment operations including merchant payments,
 * peer-to-peer transfers, and contact exchange.
 */
@RestController
@RequestMapping("/api/v1/payments/nfc")
@RequiredArgsConstructor
@Validated
@Slf4j
@Tag(name = "NFC Payment", description = "NFC payment operations")
@SecurityRequirement(name = "bearer-jwt")
public class NFCPaymentController {

    private final NFCPaymentService nfcPaymentService;

    /**
     * Process NFC merchant payment
     */
    @PostMapping("/merchant/payment")
    @Operation(
        summary = "Process NFC merchant payment",
        description = "Processes a payment from customer to merchant via NFC",
        responses = {
            @ApiResponse(responseCode = "200", description = "Payment processed successfully",
                content = @Content(schema = @Schema(implementation = NFCPaymentResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid payment data"),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "402", description = "Insufficient funds"),
            @ApiResponse(responseCode = "409", description = "Duplicate payment"),
            @ApiResponse(responseCode = "422", description = "Invalid NFC signature"),
            @ApiResponse(responseCode = "429", description = "Rate limit exceeded"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
        }
    )
    @PreAuthorize("hasRole('USER') or hasRole('MERCHANT')")
    public ResponseEntity<NFCPaymentResponse> processMerchantPayment(
            @Valid @RequestBody NFCMerchantPaymentRequest request,
            HttpServletRequest httpRequest) {
        
        log.info("Processing NFC merchant payment: paymentId={}, merchantId={}, amount={}", 
                request.getPaymentId(), request.getMerchantId(), request.getAmount());
        
        try {
            // Extract client IP and user agent for fraud detection
            String clientIp = getClientIpAddress(httpRequest);
            String userAgent = httpRequest.getHeader("User-Agent");
            
            NFCPaymentResponse response = nfcPaymentService.processMerchantPayment(
                request, clientIp, userAgent);
            
            log.info("NFC merchant payment processed successfully: transactionId={}", 
                    response.getTransactionId());
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Error processing NFC merchant payment: paymentId={}", 
                    request.getPaymentId(), e);
            throw e;
        }
    }

    /**
     * Initialize merchant NFC payment session
     */
    @PostMapping("/merchant/session")
    @Operation(
        summary = "Initialize merchant NFC payment session",
        description = "Creates a new NFC payment session for merchant to accept payments"
    )
    @PreAuthorize("hasRole('MERCHANT')")
    public ResponseEntity<NFCSessionResponse> initializeMerchantSession(
            @Valid @RequestBody NFCMerchantSessionRequest request) {
        
        log.info("Initializing NFC merchant session: merchantId={}, amount={}", 
                request.getMerchantId(), request.getAmount());
        
        NFCSessionResponse response = nfcPaymentService.initializeMerchantSession(request);
        
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Process P2P NFC transfer
     */
    @PostMapping("/p2p/transfer")
    @Operation(
        summary = "Process P2P NFC transfer",
        description = "Processes a peer-to-peer money transfer via NFC"
    )
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<NFCP2PResponse> processP2PTransfer(
            @Valid @RequestBody NFCP2PTransferRequest request,
            HttpServletRequest httpRequest) {
        
        log.info("Processing NFC P2P transfer: transferId={}, senderId={}, recipientId={}, amount={}", 
                request.getTransferId(), request.getSenderId(), request.getRecipientId(), request.getAmount());
        
        String clientIp = getClientIpAddress(httpRequest);
        String userAgent = httpRequest.getHeader("User-Agent");
        
        NFCP2PResponse response = nfcPaymentService.processP2PTransfer(
            request, clientIp, userAgent);
        
        log.info("NFC P2P transfer processed successfully: transactionId={}", 
                response.getTransactionId());
        
        return ResponseEntity.ok(response);
    }

    /**
     * Initialize P2P NFC session
     */
    @PostMapping("/p2p/session")
    @Operation(
        summary = "Initialize P2P NFC session",
        description = "Creates a new NFC session for peer-to-peer transfers"
    )
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<NFCSessionResponse> initializeP2PSession(
            @Valid @RequestBody NFCP2PSessionRequest request) {
        
        log.info("Initializing NFC P2P session: userId={}", request.getUserId());
        
        NFCSessionResponse response = nfcPaymentService.initializeP2PSession(request);
        
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Process contact exchange via NFC
     */
    @PostMapping("/contact/exchange")
    @Operation(
        summary = "Process NFC contact exchange",
        description = "Exchanges contact information between users via NFC"
    )
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<NFCContactExchangeResponse> processContactExchange(
            @Valid @RequestBody NFCContactExchangeRequest request) {
        
        log.info("Processing NFC contact exchange: userId={}, contactUserId={}", 
                request.getUserId(), request.getContactUserId());
        
        NFCContactExchangeResponse response = nfcPaymentService.processContactExchange(request);
        
        log.info("NFC contact exchange processed successfully: connectionId={}", 
                response.getConnectionId());
        
        return ResponseEntity.ok(response);
    }

    /**
     * Validate NFC data signature
     */
    @PostMapping("/validate/signature")
    @Operation(
        summary = "Validate NFC data signature",
        description = "Validates the cryptographic signature of NFC data"
    )
    @PreAuthorize("hasRole('USER') or hasRole('MERCHANT')")
    public ResponseEntity<NFCSignatureValidationResponse> validateSignature(
            @Valid @RequestBody NFCSignatureValidationRequest request) {
        
        log.debug("Validating NFC signature: dataType={}", request.getDataType());
        
        NFCSignatureValidationResponse response = nfcPaymentService.validateNFCSignature(request);
        
        return ResponseEntity.ok(response);
    }

    /**
     * Get NFC transaction status
     */
    @GetMapping("/transaction/{transactionId}/status")
    @Operation(
        summary = "Get NFC transaction status",
        description = "Retrieves the current status of an NFC transaction"
    )
    @PreAuthorize("hasRole('USER') or hasRole('MERCHANT')")
    public ResponseEntity<NFCTransactionStatusResponse> getTransactionStatus(
            @Parameter(description = "Transaction ID") 
            @PathVariable @NotBlank String transactionId) {
        
        log.debug("Getting NFC transaction status: transactionId={}", transactionId);
        
        NFCTransactionStatusResponse response = nfcPaymentService.getTransactionStatus(transactionId);
        
        return ResponseEntity.ok(response);
    }

    /**
     * Get user's NFC payment history
     */
    @GetMapping("/history")
    @Operation(
        summary = "Get NFC payment history",
        description = "Retrieves user's NFC payment transaction history"
    )
    @PreAuthorize("hasRole('USER') or hasRole('MERCHANT')")
    public ResponseEntity<List<NFCTransactionHistoryResponse>> getPaymentHistory(
            @Parameter(description = "User ID") 
            @RequestParam @NotBlank String userId,
            @Parameter(description = "Page number (0-based)") 
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size") 
            @RequestParam(defaultValue = "20") int size,
            @Parameter(description = "Transaction type filter") 
            @RequestParam(required = false) String transactionType) {
        
        log.debug("Getting NFC payment history: userId={}, page={}, size={}", userId, page, size);
        
        List<NFCTransactionHistoryResponse> response = nfcPaymentService.getPaymentHistory(
            userId, page, size, transactionType);
        
        return ResponseEntity.ok(response);
    }

    /**
     * Cancel pending NFC transaction
     */
    @PatchMapping("/transaction/{transactionId}/cancel")
    @Operation(
        summary = "Cancel NFC transaction",
        description = "Cancels a pending NFC transaction"
    )
    @PreAuthorize("hasRole('USER') or hasRole('MERCHANT')")
    public ResponseEntity<NFCTransactionCancelResponse> cancelTransaction(
            @Parameter(description = "Transaction ID") 
            @PathVariable @NotBlank String transactionId,
            @Valid @RequestBody NFCTransactionCancelRequest request) {
        
        log.info("Cancelling NFC transaction: transactionId={}, reason={}", 
                transactionId, request.getReason());
        
        NFCTransactionCancelResponse response = nfcPaymentService.cancelTransaction(
            transactionId, request);
        
        return ResponseEntity.ok(response);
    }

    /**
     * Get NFC device capabilities
     */
    @GetMapping("/device/capabilities")
    @Operation(
        summary = "Get NFC device capabilities",
        description = "Retrieves NFC capabilities for the user's device"
    )
    @PreAuthorize("hasRole('USER') or hasRole('MERCHANT')")
    public ResponseEntity<NFCDeviceCapabilitiesResponse> getDeviceCapabilities(
            @Parameter(description = "Device ID") 
            @RequestParam @NotBlank String deviceId,
            @Parameter(description = "Platform (iOS/Android)") 
            @RequestParam @NotBlank String platform) {
        
        log.debug("Getting NFC device capabilities: deviceId={}, platform={}", deviceId, platform);
        
        NFCDeviceCapabilitiesResponse response = nfcPaymentService.getDeviceCapabilities(
            deviceId, platform);
        
        return ResponseEntity.ok(response);
    }

    /**
     * Process NFC payment webhook
     *
     * SECURITY: This endpoint validates webhook signatures cryptographically.
     * The service layer (nfcPaymentService.processPaymentWebhook) performs
     * signature validation to ensure webhooks are from trusted sources.
     * This is intentionally permitAll() as authentication is signature-based.
     */
    @PostMapping("/webhook/payment")
    @Operation(
        summary = "Process NFC payment webhook",
        description = "Handles webhook notifications for NFC payment events with signature validation"
    )
    @PreAuthorize("permitAll()")
    public ResponseEntity<Void> processPaymentWebhook(
            @RequestBody String payload,
            @RequestHeader("X-Webhook-Signature") String signature,
            HttpServletRequest request) {

        log.info("Processing NFC payment webhook from: {}", request.getRemoteAddr());

        // Process webhook asynchronously
        CompletableFuture.runAsync(() -> {
            try {
                nfcPaymentService.processPaymentWebhook(payload, signature);
            } catch (Exception e) {
                log.error("Error processing NFC payment webhook", e);
            }
        });

        return ResponseEntity.ok().build();
    }

    /**
     * Health check endpoint
     *
     * SECURITY: Intentionally public for monitoring systems
     */
    @GetMapping("/health")
    @Operation(
        summary = "NFC service health check",
        description = "Checks the health status of NFC payment service"
    )
    @PreAuthorize("permitAll()")
    public ResponseEntity<NFCHealthResponse> healthCheck() {
        NFCHealthResponse response = nfcPaymentService.performHealthCheck();

        HttpStatus status = response.isHealthy() ? HttpStatus.OK : HttpStatus.SERVICE_UNAVAILABLE;
        return ResponseEntity.status(status).body(response);
    }

    // Utility methods

    private String getClientIpAddress(HttpServletRequest request) {
        String clientIp = request.getHeader("X-Forwarded-For");
        if (clientIp == null || clientIp.isEmpty() || "unknown".equalsIgnoreCase(clientIp)) {
            clientIp = request.getHeader("X-Real-IP");
        }
        if (clientIp == null || clientIp.isEmpty() || "unknown".equalsIgnoreCase(clientIp)) {
            clientIp = request.getRemoteAddr();
        }
        
        // Handle multiple IPs in X-Forwarded-For header
        if (clientIp != null && clientIp.contains(",")) {
            clientIp = clientIp.split(",")[0].trim();
        }
        
        return clientIp;
    }
}