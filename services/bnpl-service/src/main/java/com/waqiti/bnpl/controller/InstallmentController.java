/**
 * Installment Controller
 * REST API endpoints for BNPL installment management
 */
package com.waqiti.bnpl.controller;

import com.waqiti.bnpl.dto.request.ProcessPaymentRequest;
import com.waqiti.bnpl.dto.response.InstallmentResponse;
import com.waqiti.bnpl.dto.response.PaymentResponse;
import com.waqiti.bnpl.dto.response.PaymentScheduleResponse;
import com.waqiti.bnpl.entity.BnplInstallment.InstallmentStatus;
import com.waqiti.bnpl.service.InstallmentPaymentService;
import com.waqiti.bnpl.service.InstallmentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/installments")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "BNPL Installments", description = "BNPL installment payment management")
public class InstallmentController {
    
    private final InstallmentService installmentService;
    private final InstallmentPaymentService paymentService;
    
    @PostMapping("/{installmentId}/pay")
    @Operation(summary = "Make installment payment", description = "Processes payment for an installment")
    @PreAuthorize("hasRole('USER') and @installmentService.isInstallmentOwner(#installmentId, #userId)")
    public ResponseEntity<PaymentResponse> makePayment(
            @PathVariable UUID installmentId,
            @Valid @RequestBody ProcessPaymentRequest request,
            @RequestHeader("X-User-Id") UUID userId) {
        
        log.info("Processing payment for installment: {} by user: {}", installmentId, userId);
        PaymentResponse response = paymentService.processInstallmentPayment(installmentId, request);
        return ResponseEntity.ok(response);
    }
    
    @GetMapping("/{installmentId}")
    @Operation(summary = "Get installment details", description = "Retrieves installment information")
    @PreAuthorize("hasRole('USER') and @installmentService.isInstallmentOwner(#installmentId, #userId)")
    public ResponseEntity<InstallmentResponse> getInstallment(
            @PathVariable UUID installmentId,
            @RequestHeader("X-User-Id") UUID userId) {
        
        log.info("Fetching installment: {} for user: {}", installmentId, userId);
        InstallmentResponse response = installmentService.getInstallment(installmentId);
        return ResponseEntity.ok(response);
    }
    
    @GetMapping
    @Operation(summary = "List user installments", description = "Retrieves user's installments")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<Page<InstallmentResponse>> getUserInstallments(
            @RequestHeader("X-User-Id") UUID userId,
            @RequestParam(required = false) InstallmentStatus status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            Pageable pageable) {
        
        log.info("Listing installments for user: {} with status: {}", userId, status);
        Page<InstallmentResponse> installments = installmentService.getUserInstallments(
                userId, status, fromDate, toDate, pageable);
        return ResponseEntity.ok(installments);
    }
    
    @GetMapping("/application/{applicationId}")
    @Operation(summary = "Get payment schedule", description = "Retrieves payment schedule for an application")
    @PreAuthorize("hasRole('USER') and @applicationService.isApplicationOwner(#applicationId, #userId)")
    public ResponseEntity<PaymentScheduleResponse> getPaymentSchedule(
            @PathVariable UUID applicationId,
            @RequestHeader("X-User-Id") UUID userId) {
        
        log.info("Fetching payment schedule for application: {}", applicationId);
        PaymentScheduleResponse schedule = installmentService.getPaymentSchedule(applicationId);
        return ResponseEntity.ok(schedule);
    }
    
    @GetMapping("/upcoming")
    @Operation(summary = "Get upcoming payments", description = "Retrieves user's upcoming installment payments")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<List<InstallmentResponse>> getUpcomingPayments(
            @RequestHeader("X-User-Id") UUID userId,
            @RequestParam(defaultValue = "30") Integer days) {
        
        log.info("Fetching upcoming payments for user: {} within {} days", userId, days);
        List<InstallmentResponse> upcoming = installmentService.getUpcomingPayments(userId, days);
        return ResponseEntity.ok(upcoming);
    }
    
    @GetMapping("/overdue")
    @Operation(summary = "Get overdue installments", description = "Retrieves user's overdue installments")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<List<InstallmentResponse>> getOverdueInstallments(
            @RequestHeader("X-User-Id") UUID userId) {
        
        log.info("Fetching overdue installments for user: {}", userId);
        List<InstallmentResponse> overdue = installmentService.getOverdueInstallments(userId);
        return ResponseEntity.ok(overdue);
    }
    
    @PostMapping("/{installmentId}/autopay")
    @Operation(summary = "Setup autopay", description = "Enables automatic payment for installments")
    @PreAuthorize("hasRole('USER') and @installmentService.isInstallmentOwner(#installmentId, #userId)")
    public ResponseEntity<Void> setupAutopay(
            @PathVariable UUID installmentId,
            @RequestHeader("X-User-Id") UUID userId,
            @RequestParam String paymentMethodId) {
        
        log.info("Setting up autopay for installment: {} with method: {}", installmentId, paymentMethodId);
        installmentService.setupAutopay(installmentId, userId, paymentMethodId);
        return ResponseEntity.noContent().build();
    }
    
    @DeleteMapping("/{installmentId}/autopay")
    @Operation(summary = "Disable autopay", description = "Disables automatic payment for installments")
    @PreAuthorize("hasRole('USER') and @installmentService.isInstallmentOwner(#installmentId, #userId)")
    public ResponseEntity<Void> disableAutopay(
            @PathVariable UUID installmentId,
            @RequestHeader("X-User-Id") UUID userId) {
        
        log.info("Disabling autopay for installment: {}", installmentId);
        installmentService.disableAutopay(installmentId, userId);
        return ResponseEntity.noContent().build();
    }
    
    @GetMapping("/summary")
    @Operation(summary = "Get payment summary", description = "Retrieves user's BNPL payment summary")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<PaymentSummaryResponse> getPaymentSummary(
            @RequestHeader("X-User-Id") UUID userId) {
        
        log.info("Fetching payment summary for user: {}", userId);
        PaymentSummaryResponse summary = installmentService.getPaymentSummary(userId);
        return ResponseEntity.ok(summary);
    }
    
    @PostMapping("/bulk-pay")
    @Operation(summary = "Bulk payment", description = "Pays multiple installments at once")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<BulkPaymentResponse> makeBulkPayment(
            @Valid @RequestBody BulkPaymentRequest request,
            @RequestHeader("X-User-Id") UUID userId) {
        
        log.info("Processing bulk payment for user: {} installments: {}", userId, request.getInstallmentIds().size());
        BulkPaymentResponse response = paymentService.processBulkPayment(userId, request);
        return ResponseEntity.ok(response);
    }
    
    @Data
    @Builder
    public static class PaymentSummaryResponse {
        private BigDecimal totalOutstanding;
        private BigDecimal totalOverdue;
        private BigDecimal nextPaymentAmount;
        private LocalDate nextPaymentDate;
        private Integer activeInstallments;
        private Integer overdueInstallments;
        private Integer completedInstallments;
        private BigDecimal totalPaid;
        private BigDecimal totalFinanced;
    }
    
    @Data
    @Builder
    public static class BulkPaymentRequest {
        private List<UUID> installmentIds;
        private String paymentMethodId;
        private BigDecimal totalAmount;
    }
    
    @Data
    @Builder
    public static class BulkPaymentResponse {
        private boolean success;
        private Integer successfulPayments;
        private Integer failedPayments;
        private BigDecimal totalPaid;
        private List<PaymentResult> results;
    }
    
    @Data
    @Builder
    public static class PaymentResult {
        private UUID installmentId;
        private boolean success;
        private String transactionId;
        private String failureReason;
    }
}