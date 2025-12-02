package com.waqiti.payment.controller;

import com.waqiti.common.api.ApiResponse;
import com.waqiti.payment.dto.CreatePaymentRequest;
import com.waqiti.payment.dto.PaymentResponse;
import com.waqiti.payment.service.PaymentServiceEventSourcingExtension;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Event Sourcing enabled Payment Controller
 * This controller exposes endpoints for payments tracked with event sourcing
 */
@RestController
@RequestMapping("/api/v1/payments/event-sourced")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Event Sourced Payments", description = "Payment operations with event sourcing for audit and compliance")
@ConditionalOnProperty(name = "payment.event-sourcing.enabled", havingValue = "true")
public class EventSourcingPaymentController {
    
    private final PaymentServiceEventSourcingExtension eventSourcingExtension;
    
    @PostMapping
    @Operation(
        summary = "Create payment with event sourcing",
        description = "Creates a new payment that is tracked using event sourcing for complete audit trail"
    )
    @PreAuthorize("hasAuthority('CREATE_PAYMENT')")
    public ResponseEntity<ApiResponse<CompletableFuture<PaymentResponse>>> createEventSourcedPayment(
            @Valid @RequestBody CreatePaymentRequest request,
            Authentication authentication) {
        
        String userId = authentication.getName();
        log.info("Creating event-sourced payment for user: {}", userId);
        
        CompletableFuture<PaymentResponse> future = eventSourcingExtension.processPaymentWithEventSourcing(request, userId);
        
        return ResponseEntity.status(HttpStatus.ACCEPTED)
            .body(ApiResponse.success(future, "Payment processing initiated with event sourcing"));
    }
    
    @GetMapping("/{paymentId}")
    @Operation(
        summary = "Get payment from event store",
        description = "Retrieves payment information reconstructed from the event store"
    )
    @PreAuthorize("hasAuthority('READ_PAYMENT')")
    public ResponseEntity<ApiResponse<PaymentResponse>> getEventSourcedPayment(
            @PathVariable String paymentId,
            Authentication authentication) {
        
        String userId = authentication.getName();
        log.debug("Retrieving event-sourced payment: {} for user: {}", paymentId, userId);
        
        PaymentResponse response = eventSourcingExtension.getPaymentFromEventStore(paymentId, userId)
            .join(); // Block for synchronous response
        
        if (response == null) {
            return ResponseEntity.notFound().build();
        }
        
        return ResponseEntity.ok(ApiResponse.success(response));
    }
    
    @PostMapping("/{paymentId}/cancel")
    @Operation(
        summary = "Cancel payment with event sourcing",
        description = "Cancels a payment and records the cancellation event"
    )
    @PreAuthorize("hasAuthority('CANCEL_PAYMENT')")
    public ResponseEntity<ApiResponse<String>> cancelEventSourcedPayment(
            @PathVariable String paymentId,
            @RequestParam(required = false) String reason,
            @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId,
            Authentication authentication) {
        
        String userId = authentication.getName();
        String cancelReason = reason != null ? reason : "User requested cancellation";
        String corrId = correlationId != null ? correlationId : UUID.randomUUID().toString();
        
        log.info("Cancelling event-sourced payment: {} for user: {} with reason: {}", paymentId, userId, cancelReason);
        
        eventSourcingExtension.cancelPaymentWithEventSourcing(paymentId, cancelReason, userId, corrId)
            .join(); // Block for synchronous response
        
        return ResponseEntity.ok(ApiResponse.success("Payment cancelled successfully"));
    }
    
    @PostMapping("/{paymentId}/flag-for-review")
    @Operation(
        summary = "Flag payment for manual review",
        description = "Flags a payment for manual review and records the event"
    )
    @PreAuthorize("hasAuthority('FLAG_PAYMENT')")
    public ResponseEntity<ApiResponse<String>> flagPaymentForReview(
            @PathVariable String paymentId,
            @RequestParam String reason,
            @RequestParam(required = false) String riskScore,
            @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId,
            Authentication authentication) {
        
        String userId = authentication.getName();
        String score = riskScore != null ? riskScore : "MEDIUM";
        String corrId = correlationId != null ? correlationId : UUID.randomUUID().toString();
        
        log.info("Flagging payment for review: {} by user: {} with risk score: {}", paymentId, userId, score);
        
        eventSourcingExtension.flagPaymentForReview(paymentId, reason, score, userId, corrId)
            .join(); // Block for synchronous response
        
        return ResponseEntity.ok(ApiResponse.success("Payment flagged for review"));
    }
    
    @GetMapping("/status")
    @Operation(
        summary = "Check event sourcing status",
        description = "Returns whether event sourcing is enabled for payments"
    )
    public ResponseEntity<ApiResponse<Boolean>> getEventSourcingStatus() {
        return ResponseEntity.ok(ApiResponse.success(eventSourcingExtension.isEventSourcingEnabled()));
    }
}