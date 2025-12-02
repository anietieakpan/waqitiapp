package com.waqiti.transaction.controller;

import com.waqiti.transaction.dto.ReceiptGenerationOptions;
import com.waqiti.transaction.dto.ReceiptMetadata;
import com.waqiti.transaction.dto.ReceiptAuditLog;
import com.waqiti.transaction.dto.ReceiptAccessTokenRequest;
import com.waqiti.transaction.entity.Transaction;
import com.waqiti.transaction.service.ReceiptService;
import com.waqiti.transaction.service.ReceiptAuditService;
import com.waqiti.transaction.service.ReceiptSecurityService;
import com.waqiti.transaction.service.TransactionService;
import com.waqiti.common.util.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
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
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Email;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * REST Controller for receipt generation, management, and audit operations.
 * Provides comprehensive receipt services with security and compliance features.
 * 
 * @author Waqiti Transaction Team
 * @since 2.0.0
 */
@RestController
@RequestMapping("/api/v1/receipts")
@RequiredArgsConstructor
@Slf4j
@Validated
@Tag(name = "Receipt Service", description = "Receipt generation, management, and audit operations")
public class ReceiptController {

    private final ReceiptService receiptService;
    private final ReceiptAuditService auditService;
    private final ReceiptSecurityService securityService;
    private final TransactionService transactionService;

    /**
     * Generate receipt for a transaction
     */
    @PostMapping("/transaction/{transactionId}")
    @Operation(summary = "Generate receipt for transaction", 
               description = "Generates a PDF receipt for the specified transaction")
    @ApiResponse(responseCode = "200", description = "Receipt generated successfully")
    @ApiResponse(responseCode = "404", description = "Transaction not found")
    @ApiResponse(responseCode = "403", description = "Access denied")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN') and @transactionAuthorizationService.canAccessTransaction(#transactionId, authentication.name)")
    public ResponseEntity<byte[]> generateReceipt(
            @PathVariable @NotNull UUID transactionId,
            @RequestParam(defaultValue = "STANDARD") ReceiptGenerationOptions.ReceiptFormat format,
            @RequestParam(defaultValue = "false") boolean includeQrCode,
            @RequestParam(defaultValue = "false") boolean includeWatermark,
            @RequestParam(defaultValue = "false") boolean includeDetailedFees,
            HttpServletRequest request) {

        log.info("Receipt generation request for transaction: {} by user: {}", 
                transactionId, SecurityUtils.getCurrentUserId());

        try {
            // Get transaction
            Optional<Transaction> transactionOpt = transactionService.findById(transactionId);
            if (transactionOpt.isEmpty()) {
                return ResponseEntity.notFound().build();
            }

            Transaction transaction = transactionOpt.get();

            // Build generation options
            ReceiptGenerationOptions options = ReceiptGenerationOptions.builder()
                    .format(format)
                    .includeQrCode(includeQrCode)
                    .includeWatermark(includeWatermark)
                    .includeDetailedFees(includeDetailedFees)
                    .includeTimeline(format == ReceiptGenerationOptions.ReceiptFormat.DETAILED)
                    .includeComplianceInfo(format == ReceiptGenerationOptions.ReceiptFormat.PROOF_OF_PAYMENT)
                    .build();

            // Generate receipt
            byte[] receiptData = receiptService.generateReceipt(transaction, options);

            // Log audit event
            auditService.logReceiptGenerated(
                    transactionId, 
                    null, // Will be assigned if stored
                    SecurityUtils.getCurrentUserId(),
                    format.name(),
                    SecurityUtils.getClientIp(request),
                    request.getHeader("User-Agent")
            );

            // Set headers
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_PDF);
            headers.setContentDispositionFormData("inline", "receipt-" + transactionId + ".pdf");
            headers.setContentLength(receiptData.length);

            return ResponseEntity.ok()
                    .headers(headers)
                    .body(receiptData);

        } catch (Exception e) {
            log.error("Failed to generate receipt for transaction: {}", transactionId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Generate and store receipt
     */
    @PostMapping("/transaction/{transactionId}/store")
    @Operation(summary = "Generate and store receipt", 
               description = "Generates receipt and stores it securely for future access")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN') and @transactionAuthorizationService.canAccessTransaction(#transactionId, authentication.name)")
    public ResponseEntity<ReceiptMetadata> generateAndStoreReceipt(
            @PathVariable @NotNull UUID transactionId,
            HttpServletRequest request) {

        log.info("Store receipt request for transaction: {}", transactionId);

        try {
            Optional<Transaction> transactionOpt = transactionService.findById(transactionId);
            if (transactionOpt.isEmpty()) {
                return ResponseEntity.notFound().build();
            }

            ReceiptMetadata metadata = receiptService.generateAndStoreReceipt(transactionOpt.get());

            // Log audit event
            auditService.logReceiptGenerated(
                    transactionId,
                    metadata.getReceiptId(),
                    SecurityUtils.getCurrentUserId(),
                    metadata.getFormat().name(),
                    SecurityUtils.getClientIp(request),
                    request.getHeader("User-Agent")
            );

            return ResponseEntity.ok(metadata);

        } catch (Exception e) {
            log.error("Failed to generate and store receipt for transaction: {}", transactionId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Download stored receipt
     */
    @GetMapping("/transaction/{transactionId}/download")
    @Operation(summary = "Download stored receipt", 
               description = "Downloads a previously stored receipt")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN') and @transactionAuthorizationService.canAccessTransaction(#transactionId, authentication.name)")
    public ResponseEntity<byte[]> downloadStoredReceipt(
            @PathVariable @NotNull UUID transactionId,
            HttpServletRequest request) {

        log.info("Download receipt request for transaction: {}", transactionId);

        try {
            byte[] receiptData = receiptService.getStoredReceipt(transactionId);
            
            if (receiptData == null) {
                return ResponseEntity.notFound().build();
            }

            // Log audit event
            auditService.logReceiptDownloaded(
                    transactionId,
                    null, // Could be enhanced to include receipt ID
                    SecurityUtils.getCurrentUserId(),
                    SecurityUtils.getClientIp(request),
                    request.getHeader("User-Agent")
            );

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_PDF);
            headers.setContentDispositionFormData("attachment", "receipt-" + transactionId + ".pdf");
            headers.setContentLength(receiptData.length);

            return ResponseEntity.ok()
                    .headers(headers)
                    .body(receiptData);

        } catch (Exception e) {
            log.error("Failed to download receipt for transaction: {}", transactionId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Email receipt to specified address
     */
    @PostMapping("/transaction/{transactionId}/email")
    @Operation(summary = "Email receipt", 
               description = "Sends receipt via email to specified address")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN') and @transactionAuthorizationService.canAccessTransaction(#transactionId, authentication.name)")
    public ResponseEntity<Void> emailReceipt(
            @PathVariable @NotNull UUID transactionId,
            @RequestParam @Email @NotBlank String recipientEmail,
            HttpServletRequest request) {

        log.info("Email receipt request for transaction: {} to: {}", transactionId, recipientEmail);

        try {
            boolean success = receiptService.emailReceipt(transactionId, recipientEmail);

            // Log audit event
            auditService.logReceiptEmailed(
                    transactionId,
                    null,
                    SecurityUtils.getCurrentUserId(),
                    recipientEmail,
                    success,
                    SecurityUtils.getClientIp(request)
            );

            if (success) {
                return ResponseEntity.ok().build();
            } else {
                return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
            }

        } catch (Exception e) {
            log.error("Failed to email receipt for transaction: {}", transactionId, e);
            
            // Log failed email attempt
            auditService.logReceiptEmailed(
                    transactionId,
                    null,
                    SecurityUtils.getCurrentUserId(),
                    recipientEmail,
                    false,
                    SecurityUtils.getClientIp(request)
            );
            
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Generate proof of payment document
     */
    @PostMapping("/transaction/{transactionId}/proof-of-payment")
    @Operation(summary = "Generate proof of payment", 
               description = "Generates official proof of payment document")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN') and @transactionAuthorizationService.canAccessTransaction(#transactionId, authentication.name)")
    public ResponseEntity<byte[]> generateProofOfPayment(
            @PathVariable @NotNull UUID transactionId,
            HttpServletRequest request) {

        log.info("Proof of payment request for transaction: {}", transactionId);

        try {
            Optional<Transaction> transactionOpt = transactionService.findById(transactionId);
            if (transactionOpt.isEmpty()) {
                return ResponseEntity.notFound().build();
            }

            byte[] proofData = receiptService.generateProofOfPayment(transactionOpt.get());

            // Log audit event
            auditService.logReceiptGenerated(
                    transactionId,
                    null,
                    SecurityUtils.getCurrentUserId(),
                    "PROOF_OF_PAYMENT",
                    SecurityUtils.getClientIp(request),
                    request.getHeader("User-Agent")
            );

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_PDF);
            headers.setContentDispositionFormData("inline", "proof-of-payment-" + transactionId + ".pdf");
            headers.setContentLength(proofData.length);

            return ResponseEntity.ok()
                    .headers(headers)
                    .body(proofData);

        } catch (Exception e) {
            log.error("Failed to generate proof of payment for transaction: {}", transactionId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Verify receipt integrity
     */
    @PostMapping("/verify")
    @Operation(summary = "Verify receipt integrity", 
               description = "Verifies the integrity and authenticity of a receipt")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    public ResponseEntity<Boolean> verifyReceipt(
            @RequestBody @Valid ReceiptVerificationRequest request,
            HttpServletRequest httpRequest) {

        log.info("Receipt verification request for transaction: {}", request.getTransactionId());

        try {
            boolean isValid = receiptService.verifyReceiptIntegrity(
                    request.getReceiptData(), 
                    request.getExpectedHash()
            );

            // Enhanced verification through security service
            int securityScore = securityService.calculateSecurityScore(
                    request.getReceiptData(),
                    request.getTransactionId(),
                    SecurityUtils.getCurrentUserId()
            );

            // Log verification attempt
            auditService.logReceiptVerified(
                    request.getTransactionId(),
                    null,
                    SecurityUtils.getCurrentUserId(),
                    isValid,
                    securityScore,
                    SecurityUtils.getClientIp(httpRequest)
            );

            return ResponseEntity.ok(isValid);

        } catch (Exception e) {
            log.error("Failed to verify receipt for transaction: {}", request.getTransactionId(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Get receipt audit trail
     */
    @GetMapping("/transaction/{transactionId}/audit")
    @Operation(summary = "Get receipt audit trail", 
               description = "Retrieves complete audit trail for receipt operations")
    @PreAuthorize("hasAnyRole('ADMIN', 'COMPLIANCE_OFFICER') or (hasRole('USER') and @transactionAuthorizationService.canAccessTransaction(#transactionId, authentication.name))")
    public ResponseEntity<List<ReceiptAuditLog>> getReceiptAuditTrail(
            @PathVariable @NotNull UUID transactionId) {

        log.info("Audit trail request for transaction: {}", transactionId);

        try {
            List<ReceiptAuditLog> auditTrail = auditService.getAuditTrail(transactionId);
            return ResponseEntity.ok(auditTrail);

        } catch (Exception e) {
            log.error("Failed to get audit trail for transaction: {}", transactionId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Get user receipt history
     */
    @GetMapping("/user/{userId}/history")
    @Operation(summary = "Get user receipt history", 
               description = "Retrieves receipt access history for a user")
    @PreAuthorize("hasRole('ADMIN') or (#userId == authentication.name)")
    public ResponseEntity<List<ReceiptAuditLog>> getUserReceiptHistory(
            @PathVariable @NotBlank String userId,
            @RequestParam(defaultValue = "50") @Positive int limit) {

        log.info("Receipt history request for user: {}", userId);

        try {
            List<ReceiptAuditLog> history = auditService.getUserAuditLogs(userId, limit);
            return ResponseEntity.ok(history);

        } catch (Exception e) {
            log.error("Failed to get receipt history for user: {}", userId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Generate secure access token for receipt
     */
    @PostMapping("/transaction/{transactionId}/access-token")
    @Operation(summary = "Generate secure access token", 
               description = "Generates secure access token for receipt sharing")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN') and @transactionAuthorizationService.canAccessTransaction(#transactionId, authentication.name)")
    public ResponseEntity<String> generateAccessToken(
            @PathVariable @NotNull UUID transactionId,
            @RequestBody @Valid ReceiptAccessTokenRequest tokenRequest,
            HttpServletRequest request) {

        log.info("Access token generation for transaction: {}", transactionId);

        try {
            String accessToken = securityService.generateSecureAccessToken(
                    transactionId,
                    SecurityUtils.getCurrentUserId(),
                    tokenRequest.getExpirationMinutes(),
                    tokenRequest.getAccessLevel()
            );

            // Log token generation
            auditService.logTokenAccess(
                    transactionId,
                    accessToken,
                    SecurityUtils.getCurrentUserId(),
                    true,
                    SecurityUtils.getClientIp(request)
            );

            return ResponseEntity.ok(accessToken);

        } catch (Exception e) {
            log.error("Failed to generate access token for transaction: {}", transactionId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Access receipt with secure token
     */
    @GetMapping("/access/{accessToken}")
    @Operation(summary = "Access receipt with token", 
               description = "Accesses receipt using secure access token")
    public ResponseEntity<byte[]> accessReceiptWithToken(
            @PathVariable @NotBlank String accessToken,
            HttpServletRequest request) {

        log.info("Token-based receipt access attempt");

        try {
            UUID transactionId = securityService.validateAndGetTransactionId(accessToken);
            
            if (transactionId == null) {
                // Log failed token access
                auditService.logTokenAccess(
                        null,
                        accessToken,
                        "ANONYMOUS",
                        false,
                        SecurityUtils.getClientIp(request)
                );
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
            }

            byte[] receiptData = receiptService.getStoredReceipt(transactionId);
            
            if (receiptData == null) {
                return ResponseEntity.notFound().build();
            }

            // Log successful token access
            auditService.logTokenAccess(
                    transactionId,
                    accessToken,
                    "TOKEN_HOLDER",
                    true,
                    SecurityUtils.getClientIp(request)
            );

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_PDF);
            headers.setContentDispositionFormData("inline", "receipt-" + transactionId + ".pdf");
            headers.setContentLength(receiptData.length);

            return ResponseEntity.ok()
                    .headers(headers)
                    .body(receiptData);

        } catch (Exception e) {
            log.error("Failed to access receipt with token", e);
            
            // Log failed token access
            auditService.logTokenAccess(
                    null,
                    accessToken,
                    "ANONYMOUS",
                    false,
                    SecurityUtils.getClientIp(request)
            );
            
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Delete receipt (GDPR compliance)
     */
    @DeleteMapping("/transaction/{transactionId}")
    @Operation(summary = "Delete receipt", 
               description = "Deletes stored receipt for privacy compliance")
    @PreAuthorize("hasRole('ADMIN') or (hasRole('USER') and @transactionAuthorizationService.canAccessTransaction(#transactionId, authentication.name))")
    public ResponseEntity<Void> deleteReceipt(
            @PathVariable @NotNull UUID transactionId,
            @RequestParam(defaultValue = "USER_REQUEST") String reason,
            HttpServletRequest request) {

        log.info("Receipt deletion request for transaction: {}, reason: {}", transactionId, reason);

        try {
            boolean deleted = receiptService.deleteStoredReceipt(transactionId);

            // Log deletion
            auditService.logReceiptDeleted(
                    transactionId,
                    null,
                    SecurityUtils.getCurrentUserId(),
                    reason,
                    SecurityUtils.getClientIp(request)
            );

            if (deleted) {
                return ResponseEntity.ok().build();
            } else {
                return ResponseEntity.notFound().build();
            }

        } catch (Exception e) {
            log.error("Failed to delete receipt for transaction: {}", transactionId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Get suspicious activities report
     */
    @GetMapping("/audit/suspicious")
    @Operation(summary = "Get suspicious activities", 
               description = "Retrieves recent suspicious receipt activities")
    @PreAuthorize("hasAnyRole('ADMIN', 'SECURITY_OFFICER')")
    public ResponseEntity<List<ReceiptAuditLog>> getSuspiciousActivities(
            @RequestParam(defaultValue = "24") @Positive int hours) {

        log.info("Suspicious activities report request for last {} hours", hours);

        try {
            List<ReceiptAuditLog> activities = auditService.getRecentSuspiciousActivities(hours);
            return ResponseEntity.ok(activities);

        } catch (Exception e) {
            log.error("Failed to get suspicious activities", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Generate compliance report
     */
    @PostMapping("/audit/compliance-report")
    @Operation(summary = "Generate compliance report", 
               description = "Generates comprehensive compliance audit report")
    @PreAuthorize("hasAnyRole('ADMIN', 'COMPLIANCE_OFFICER')")
    public ResponseEntity<byte[]> generateComplianceReport(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate,
            @RequestParam(defaultValue = "PDF") String format) {

        log.info("Compliance report generation from {} to {}", startDate, endDate);

        try {
            byte[] reportData = auditService.generateComplianceReport(startDate, endDate, format);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_PDF);
            headers.setContentDispositionFormData("attachment", 
                    "receipt-compliance-report-" + startDate.toLocalDate() + "-to-" + endDate.toLocalDate() + ".pdf");
            headers.setContentLength(reportData.length);

            return ResponseEntity.ok()
                    .headers(headers)
                    .body(reportData);

        } catch (Exception e) {
            log.error("Failed to generate compliance report", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Health check for receipt service
     */
    @GetMapping("/health")
    @Operation(summary = "Receipt service health check", 
               description = "Checks the health of receipt generation service")
    public ResponseEntity<String> healthCheck() {
        return ResponseEntity.ok("Receipt service is healthy");
    }

    /**
     * Receipt verification request DTO
     */
    public static class ReceiptVerificationRequest {
        private UUID transactionId;
        private byte[] receiptData;
        private String expectedHash;

        // Getters and setters
        public UUID getTransactionId() { return transactionId; }
        public void setTransactionId(UUID transactionId) { this.transactionId = transactionId; }
        public byte[] getReceiptData() { return receiptData; }
        public void setReceiptData(byte[] receiptData) { this.receiptData = receiptData; }
        public String getExpectedHash() { return expectedHash; }
        public void setExpectedHash(String expectedHash) { this.expectedHash = expectedHash; }
    }
}