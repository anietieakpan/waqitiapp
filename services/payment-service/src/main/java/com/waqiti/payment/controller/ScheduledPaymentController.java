/**
 * Scheduled Payment Controller
 * REST endpoints for scheduled payment management
 */
package com.waqiti.payment.controller;

import com.waqiti.payment.dto.CreateScheduledPaymentRequest;
import com.waqiti.payment.dto.UpdateScheduledPaymentRequest;
import com.waqiti.payment.dto.ScheduledPaymentResponse;
import com.waqiti.payment.entity.ScheduledPayment.ScheduledPaymentStatus;
import com.waqiti.payment.service.ScheduledPaymentService;
import com.waqiti.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/scheduled-payments")
@RequiredArgsConstructor
@Validated
@Slf4j
@Tag(name = "Scheduled Payments", description = "Manage recurring and scheduled payments")
public class ScheduledPaymentController {
    
    private final ScheduledPaymentService scheduledPaymentService;
    
    @PostMapping
    @Operation(summary = "Create scheduled payment")
    @PreAuthorize("hasRole('USER')")
    public ApiResponse<ScheduledPaymentResponse> createScheduledPayment(
            @Valid @RequestBody CreateScheduledPaymentRequest request
    ) {
        log.info("Creating scheduled payment for user: {}", request.getUserId());
        ScheduledPaymentResponse response = scheduledPaymentService.createScheduledPayment(request);
        return ApiResponse.success(response, "Scheduled payment created successfully");
    }
    
    @PutMapping("/{paymentId}")
    @Operation(summary = "Update scheduled payment")
    @PreAuthorize("hasRole('USER')")
    public ApiResponse<ScheduledPaymentResponse> updateScheduledPayment(
            @PathVariable UUID paymentId,
            @Valid @RequestBody UpdateScheduledPaymentRequest request
    ) {
        log.info("Updating scheduled payment: {}", paymentId);
        ScheduledPaymentResponse response = scheduledPaymentService.updateScheduledPayment(paymentId, request);
        return ApiResponse.success(response, "Scheduled payment updated successfully");
    }
    
    @PostMapping("/{paymentId}/pause")
    @Operation(summary = "Pause scheduled payment")
    @PreAuthorize("hasRole('USER')")
    public ApiResponse<ScheduledPaymentResponse> pauseScheduledPayment(
            @PathVariable UUID paymentId,
            @RequestParam UUID userId,
            @RequestParam @NotBlank String reason
    ) {
        log.info("Pausing scheduled payment: {} for user: {}", paymentId, userId);
        ScheduledPaymentResponse response = scheduledPaymentService.pauseScheduledPayment(paymentId, userId, reason);
        return ApiResponse.success(response, "Scheduled payment paused successfully");
    }
    
    @PostMapping("/{paymentId}/resume")
    @Operation(summary = "Resume scheduled payment")
    @PreAuthorize("hasRole('USER')")
    public ApiResponse<ScheduledPaymentResponse> resumeScheduledPayment(
            @PathVariable UUID paymentId,
            @RequestParam UUID userId
    ) {
        log.info("Resuming scheduled payment: {} for user: {}", paymentId, userId);
        ScheduledPaymentResponse response = scheduledPaymentService.resumeScheduledPayment(paymentId, userId);
        return ApiResponse.success(response, "Scheduled payment resumed successfully");
    }
    
    @DeleteMapping("/{paymentId}")
    @Operation(summary = "Cancel scheduled payment")
    @PreAuthorize("hasRole('USER')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancelScheduledPayment(
            @PathVariable UUID paymentId,
            @RequestParam UUID userId,
            @RequestParam @NotBlank String reason
    ) {
        log.info("Cancelling scheduled payment: {} for user: {}", paymentId, userId);
        scheduledPaymentService.cancelScheduledPayment(paymentId, userId, reason);
    }
    
    @GetMapping("/user/{userId}")
    @Operation(summary = "Get user scheduled payments")
    @PreAuthorize("hasRole('USER') and #userId == authentication.principal.id")
    public ApiResponse<Page<ScheduledPaymentResponse>> getUserScheduledPayments(
            @PathVariable UUID userId,
            @RequestParam(required = false) ScheduledPaymentStatus status,
            @PageableDefault(sort = "nextPaymentDate", direction = Sort.Direction.ASC) Pageable pageable
    ) {
        log.info("Getting scheduled payments for user: {} with status: {}", userId, status);
        Page<ScheduledPaymentResponse> payments = scheduledPaymentService.getUserScheduledPayments(userId, status, pageable);
        return ApiResponse.success(payments, "Scheduled payments retrieved successfully");
    }
    
    @GetMapping("/{paymentId}")
    @Operation(summary = "Get scheduled payment details")
    @PreAuthorize("hasRole('USER')")
    public ApiResponse<ScheduledPaymentResponse> getScheduledPayment(
            @PathVariable UUID paymentId,
            @RequestParam UUID userId
    ) {
        log.info("Getting scheduled payment: {} for user: {}", paymentId, userId);
        ScheduledPaymentResponse response = scheduledPaymentService.getScheduledPayment(paymentId, userId);
        return ApiResponse.success(response, "Scheduled payment retrieved successfully");
    }
}