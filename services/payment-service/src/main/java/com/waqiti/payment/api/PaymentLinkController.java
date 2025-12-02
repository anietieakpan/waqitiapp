package com.waqiti.payment.api;

import com.waqiti.payment.dto.*;
import com.waqiti.payment.service.PaymentLinkService;
import io.micrometer.core.annotation.Timed;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * REST Controller for Payment Links API
 * Provides endpoints for creating and managing shareable payment links
 */
@RestController
@RequestMapping("/api/v1/payment-links")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Payment Links", description = "API for managing shareable payment links")
public class PaymentLinkController {
    
    private final PaymentLinkService paymentLinkService;
    
    /**
     * Create a new payment link
     */
    @PostMapping
    @Timed(name = "payment.link.create", description = "Time to create payment link")
    @Operation(summary = "Create Payment Link", 
               description = "Create a shareable payment link for collecting payments")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "201", description = "Payment link created successfully",
                    content = @Content(schema = @Schema(implementation = PaymentLinkResponse.class))),
        @ApiResponse(responseCode = "400", description = "Invalid request data"),
        @ApiResponse(responseCode = "401", description = "Unauthorized"),
        @ApiResponse(responseCode = "403", description = "KYC verification required"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @PreAuthorize("hasAuthority('SCOPE_payment:write')")
    public ResponseEntity<PaymentLinkResponse> createPaymentLink(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CreatePaymentLinkRequest request) {
        
        UUID userId = UUID.fromString(jwt.getSubject());
        log.info("Creating payment link for user: {}", userId);
        
        PaymentLinkResponse response = paymentLinkService.createPaymentLink(userId, request);
        
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
    
    /**
     * Get payment link details by link ID
     */
    @GetMapping("/{linkId}")
    @Timed(name = "payment.link.get", description = "Time to get payment link")
    @Operation(summary = "Get Payment Link", 
               description = "Retrieve payment link details by link ID")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Payment link retrieved successfully",
                    content = @Content(schema = @Schema(implementation = PaymentLinkResponse.class))),
        @ApiResponse(responseCode = "404", description = "Payment link not found"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<PaymentLinkResponse> getPaymentLink(
            @Parameter(description = "Payment link ID", required = true)
            @PathVariable String linkId) {
        
        log.debug("Getting payment link: {}", linkId);
        
        PaymentLinkResponse response = paymentLinkService.getPaymentLink(linkId);
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * Process payment through a payment link
     */
    @PostMapping("/{linkId}/pay")
    @Timed(name = "payment.link.pay", description = "Time to process payment link payment")
    @Operation(summary = "Process Payment Link Payment", 
               description = "Process a payment through a payment link")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Payment processed successfully",
                    content = @Content(schema = @Schema(implementation = PaymentLinkTransactionResponse.class))),
        @ApiResponse(responseCode = "400", description = "Invalid payment data"),
        @ApiResponse(responseCode = "401", description = "Unauthorized (for non-anonymous payments)"),
        @ApiResponse(responseCode = "403", description = "Payment not allowed"),
        @ApiResponse(responseCode = "404", description = "Payment link not found"),
        @ApiResponse(responseCode = "409", description = "Payment link cannot accept payments"),
        @ApiResponse(responseCode = "500", description = "Payment processing failed")
    })
    public ResponseEntity<PaymentLinkTransactionResponse> processPaymentLink(
            @Parameter(description = "Payment link ID", required = true)
            @PathVariable String linkId,
            @Valid @RequestBody ProcessPaymentLinkRequest request,
            HttpServletRequest httpRequest) {
        
        log.info("Processing payment for link: {} amount: {}", linkId, request.getAmount());
        
        // Set link ID and request metadata
        request.setLinkId(linkId);
        request.setIpAddress(getClientIpAddress(httpRequest));
        request.setUserAgent(httpRequest.getHeader("User-Agent"));
        
        PaymentLinkTransactionResponse response = paymentLinkService.processPaymentLink(request);
        
        // Return appropriate status based on transaction result
        HttpStatus status = response.getIsCompleted() ? HttpStatus.OK : 
                           response.getIsFailed() ? HttpStatus.BAD_REQUEST : 
                           HttpStatus.ACCEPTED;
        
        return ResponseEntity.status(status).body(response);
    }
    
    /**
     * Get user's payment links
     */
    @GetMapping("/my-links")
    @Timed(name = "payment.link.list", description = "Time to list user payment links")
    @Operation(summary = "Get My Payment Links", 
               description = "Get payment links created by the authenticated user")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Payment links retrieved successfully"),
        @ApiResponse(responseCode = "401", description = "Unauthorized"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @PreAuthorize("hasAuthority('SCOPE_payment:read')")
    public ResponseEntity<Page<PaymentLinkResponse>> getMyPaymentLinks(
            @AuthenticationPrincipal Jwt jwt,
            @PageableDefault(size = 20) Pageable pageable) {
        
        UUID userId = UUID.fromString(jwt.getSubject());
        log.debug("Getting payment links for user: {}", userId);
        
        Page<PaymentLinkResponse> response = paymentLinkService.getUserPaymentLinks(userId, pageable);
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * Get transactions for a payment link
     */
    @GetMapping("/my-links/{paymentLinkId}/transactions")
    @Timed(name = "payment.link.transactions", description = "Time to get payment link transactions")
    @Operation(summary = "Get Payment Link Transactions", 
               description = "Get transactions for a specific payment link")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Transactions retrieved successfully"),
        @ApiResponse(responseCode = "401", description = "Unauthorized"),
        @ApiResponse(responseCode = "403", description = "Access denied - not the owner"),
        @ApiResponse(responseCode = "404", description = "Payment link not found"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @PreAuthorize("hasAuthority('SCOPE_payment:read')")
    public ResponseEntity<Page<PaymentLinkTransactionResponse>> getPaymentLinkTransactions(
            @AuthenticationPrincipal Jwt jwt,
            @Parameter(description = "Payment link UUID", required = true)
            @PathVariable UUID paymentLinkId,
            @PageableDefault(size = 20) Pageable pageable) {
        
        UUID userId = UUID.fromString(jwt.getSubject());
        log.debug("Getting transactions for payment link: {} by user: {}", paymentLinkId, userId);
        
        Page<PaymentLinkTransactionResponse> response = paymentLinkService
                .getPaymentLinkTransactions(userId, paymentLinkId, pageable);
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * Update payment link
     */
    @PutMapping("/my-links/{paymentLinkId}")
    @Timed(name = "payment.link.update", description = "Time to update payment link")
    @Operation(summary = "Update Payment Link", 
               description = "Update an existing payment link")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Payment link updated successfully",
                    content = @Content(schema = @Schema(implementation = PaymentLinkResponse.class))),
        @ApiResponse(responseCode = "400", description = "Invalid request data"),
        @ApiResponse(responseCode = "401", description = "Unauthorized"),
        @ApiResponse(responseCode = "403", description = "Access denied - not the owner"),
        @ApiResponse(responseCode = "404", description = "Payment link not found"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @PreAuthorize("hasAuthority('SCOPE_payment:write')")
    public ResponseEntity<PaymentLinkResponse> updatePaymentLink(
            @AuthenticationPrincipal Jwt jwt,
            @Parameter(description = "Payment link UUID", required = true)
            @PathVariable UUID paymentLinkId,
            @Valid @RequestBody UpdatePaymentLinkRequest request) {
        
        UUID userId = UUID.fromString(jwt.getSubject());
        log.info("Updating payment link: {} by user: {}", paymentLinkId, userId);
        
        PaymentLinkResponse response = paymentLinkService.updatePaymentLink(userId, paymentLinkId, request);
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * Deactivate payment link
     */
    @PostMapping("/my-links/{paymentLinkId}/deactivate")
    @Timed(name = "payment.link.deactivate", description = "Time to deactivate payment link")
    @Operation(summary = "Deactivate Payment Link", 
               description = "Deactivate an existing payment link")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "204", description = "Payment link deactivated successfully"),
        @ApiResponse(responseCode = "401", description = "Unauthorized"),
        @ApiResponse(responseCode = "403", description = "Access denied - not the owner"),
        @ApiResponse(responseCode = "404", description = "Payment link not found"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @PreAuthorize("hasAuthority('SCOPE_payment:write')")
    public ResponseEntity<Void> deactivatePaymentLink(
            @AuthenticationPrincipal Jwt jwt,
            @Parameter(description = "Payment link UUID", required = true)
            @PathVariable UUID paymentLinkId) {
        
        UUID userId = UUID.fromString(jwt.getSubject());
        log.info("Deactivating payment link: {} by user: {}", paymentLinkId, userId);
        
        paymentLinkService.deactivatePaymentLink(userId, paymentLinkId);
        
        return ResponseEntity.noContent().build();
    }
    
    /**
     * Health check endpoint for payment links service
     */
    @GetMapping("/health")
    @Operation(summary = "Payment Links Health Check", 
               description = "Check if payment links service is healthy")
    @ApiResponse(responseCode = "200", description = "Service is healthy")
    public ResponseEntity<HealthResponse> health() {
        return ResponseEntity.ok(HealthResponse.builder()
                .status("UP")
                .service("payment-links")
                .timestamp(java.time.LocalDateTime.now())
                .build());
    }
    
    // Helper methods
    
    private String getClientIpAddress(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        
        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isEmpty()) {
            return xRealIp;
        }
        
        return request.getRemoteAddr();
    }
}

