package com.waqiti.payment.controller;

import com.waqiti.common.dto.ApiResponse;
import com.waqiti.common.ratelimit.RateLimit;
import com.waqiti.common.ratelimit.RateLimit.KeyType;
import com.waqiti.common.ratelimit.RateLimit.Priority;
import com.waqiti.payment.dto.CreatePaymentMethodRequest;
import com.waqiti.payment.dto.PaymentMethod;
import com.waqiti.payment.dto.UpdatePaymentMethodRequest;
import com.waqiti.payment.service.PaymentMethodService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
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
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api/v1/payment-methods")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Payment Methods", description = "Payment method management endpoints")
@Validated
public class PaymentMethodController {
    
    private final PaymentMethodService paymentMethodService;
    
    @PostMapping
    @PreAuthorize("hasRole('USER')")
    @RateLimit(requests = 10, window = 1, unit = TimeUnit.HOURS, keyType = KeyType.USER, priority = Priority.HIGH, description = "Add payment method", errorMessage = "Payment method creation limit exceeded. Maximum 10 per hour.")
    @Operation(summary = "Add a new payment method")
    public ResponseEntity<ApiResponse<PaymentMethod>> createPaymentMethod(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CreatePaymentMethodRequest request) {
        
        UUID userId = UUID.fromString(jwt.getSubject());
        log.info("Creating payment method for user: {}", userId);
        
        PaymentMethod paymentMethod = paymentMethodService.createPaymentMethod(userId, request);
        
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(paymentMethod, "Payment method added successfully"));
    }
    
    @GetMapping
    @PreAuthorize("hasRole('USER')")
    @RateLimit(requests = 100, window = 1, unit = TimeUnit.MINUTES, keyType = KeyType.USER, priority = Priority.MEDIUM, description = "List payment methods", errorMessage = "Query limit exceeded. Maximum 100 requests per minute.")
    @Operation(summary = "Get user's payment methods")
    public ResponseEntity<ApiResponse<Page<PaymentMethod>>> getUserPaymentMethods(
            @AuthenticationPrincipal Jwt jwt,
            @PageableDefault(size = 20) Pageable pageable) {
        
        UUID userId = UUID.fromString(jwt.getSubject());
        Page<PaymentMethod> paymentMethods = paymentMethodService.getUserPaymentMethods(userId, pageable);
        
        return ResponseEntity.ok(ApiResponse.success(paymentMethods, "Payment methods retrieved successfully"));
    }
    
    @GetMapping("/active")
    @PreAuthorize("hasRole('USER')")
    @RateLimit(requests = 120, window = 1, unit = TimeUnit.MINUTES, keyType = KeyType.USER, priority = Priority.MEDIUM, description = "Get active payment methods", errorMessage = "Query limit exceeded. Maximum 120 requests per minute.")
    @Operation(summary = "Get user's active payment methods")
    public ResponseEntity<ApiResponse<List<PaymentMethod>>> getActivePaymentMethods(
            @AuthenticationPrincipal Jwt jwt) {
        
        UUID userId = UUID.fromString(jwt.getSubject());
        List<PaymentMethod> paymentMethods = paymentMethodService.getActivePaymentMethods(userId);
        
        return ResponseEntity.ok(ApiResponse.success(paymentMethods, "Active payment methods retrieved successfully"));
    }
    
    @GetMapping("/{paymentMethodId}")
    @PreAuthorize("hasRole('USER')")
    @RateLimit(requests = 120, window = 1, unit = TimeUnit.MINUTES, keyType = KeyType.USER, priority = Priority.MEDIUM, description = "Get payment method details", errorMessage = "Lookup limit exceeded. Maximum 120 requests per minute.")
    @Operation(summary = "Get a specific payment method")
    public ResponseEntity<ApiResponse<PaymentMethod>> getPaymentMethod(
            @AuthenticationPrincipal Jwt jwt,
            @Parameter(description = "Payment method ID") @PathVariable UUID paymentMethodId) {
        
        UUID userId = UUID.fromString(jwt.getSubject());
        PaymentMethod paymentMethod = paymentMethodService.getPaymentMethod(userId, paymentMethodId);
        
        return ResponseEntity.ok(ApiResponse.success(paymentMethod, "Payment method retrieved successfully"));
    }
    
    @PutMapping("/{paymentMethodId}")
    @PreAuthorize("hasRole('USER')")
    @RateLimit(requests = 20, window = 1, unit = TimeUnit.HOURS, keyType = KeyType.USER, priority = Priority.HIGH, description = "Update payment method", errorMessage = "Update limit exceeded. Maximum 20 updates per hour.")
    @Operation(summary = "Update a payment method")
    public ResponseEntity<ApiResponse<PaymentMethod>> updatePaymentMethod(
            @AuthenticationPrincipal Jwt jwt,
            @Parameter(description = "Payment method ID") @PathVariable UUID paymentMethodId,
            @Valid @RequestBody UpdatePaymentMethodRequest request) {
        
        UUID userId = UUID.fromString(jwt.getSubject());
        PaymentMethod updated = paymentMethodService.updatePaymentMethod(userId, paymentMethodId, request);
        
        return ResponseEntity.ok(ApiResponse.success(updated, "Payment method updated successfully"));
    }
    
    @PostMapping("/{paymentMethodId}/set-default")
    @PreAuthorize("hasRole('USER')")
    @RateLimit(requests = 30, window = 1, unit = TimeUnit.HOURS, keyType = KeyType.USER, priority = Priority.MEDIUM, description = "Set default payment method", errorMessage = "Default update limit exceeded. Maximum 30 per hour.")
    @Operation(summary = "Set a payment method as default")
    public ResponseEntity<ApiResponse<Void>> setDefaultPaymentMethod(
            @AuthenticationPrincipal Jwt jwt,
            @Parameter(description = "Payment method ID") @PathVariable UUID paymentMethodId) {
        
        UUID userId = UUID.fromString(jwt.getSubject());
        paymentMethodService.setDefaultPaymentMethod(userId, paymentMethodId);
        
        return ResponseEntity.ok(ApiResponse.success(null, "Default payment method updated successfully"));
    }
    
    @DeleteMapping("/{paymentMethodId}")
    @PreAuthorize("hasRole('USER')")
    @RateLimit(requests = 10, window = 1, unit = TimeUnit.HOURS, keyType = KeyType.USER, priority = Priority.HIGH, description = "Delete payment method", errorMessage = "Deletion limit exceeded. Maximum 10 per hour.")
    @Operation(summary = "Delete a payment method")
    public ResponseEntity<ApiResponse<Void>> deletePaymentMethod(
            @AuthenticationPrincipal Jwt jwt,
            @Parameter(description = "Payment method ID") @PathVariable UUID paymentMethodId) {
        
        UUID userId = UUID.fromString(jwt.getSubject());
        paymentMethodService.deletePaymentMethod(userId, paymentMethodId);
        
        return ResponseEntity.ok(ApiResponse.success(null, "Payment method deleted successfully"));
    }
    
    @PostMapping("/{paymentMethodId}/verify")
    @PreAuthorize("hasRole('USER')")
    @RateLimit(requests = 5, window = 1, unit = TimeUnit.HOURS, keyType = KeyType.USER, priority = Priority.HIGH, description = "Verify payment method", errorMessage = "Verification limit exceeded. Maximum 5 verifications per hour.")
    @Operation(summary = "Verify a payment method")
    public ResponseEntity<ApiResponse<Void>> verifyPaymentMethod(
            @AuthenticationPrincipal Jwt jwt,
            @Parameter(description = "Payment method ID") @PathVariable UUID paymentMethodId,
            @RequestBody Map<String, String> verificationData) {
        
        UUID userId = UUID.fromString(jwt.getSubject());
        paymentMethodService.verifyPaymentMethod(userId, paymentMethodId, verificationData);
        
        return ResponseEntity.ok(ApiResponse.success(null, "Payment method verification processed"));
    }
}