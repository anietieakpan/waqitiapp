package com.waqiti.payment.controller;

import com.waqiti.payment.dto.*;
import com.waqiti.payment.service.DirectDepositService;
import com.waqiti.common.security.SecurityContext;
import com.waqiti.common.validation.ValidUUID;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.List;

/**
 * REST API controller for direct deposit and ACH transfers
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/direct-deposit")
@RequiredArgsConstructor
@Validated
@Tag(name = "Direct Deposit", description = "Direct deposit and ACH transfer management")
public class DirectDepositController {

    private final DirectDepositService directDepositService;
    private final SecurityContext securityContext;

    @Operation(
        summary = "Generate direct deposit account",
        description = "Generate a unique direct deposit account for receiving payroll and benefits"
    )
    @ApiResponse(responseCode = "201", description = "Direct deposit account created successfully")
    @ApiResponse(responseCode = "409", description = "Direct deposit account already exists")
    @ApiResponse(responseCode = "403", description = "Full KYC verification required")
    @PostMapping("/account")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<DirectDepositAccountDto> generateDirectDepositAccount() {
        String userId = securityContext.getCurrentUserId();
        log.info("Generating direct deposit account for user: {}", userId);
        
        DirectDepositAccountDto account = directDepositService.generateDirectDepositAccount(userId);
        return ResponseEntity.status(HttpStatus.CREATED).body(account);
    }

    @Operation(
        summary = "Get direct deposit account",
        description = "Get direct deposit account details for the authenticated user"
    )
    @ApiResponse(responseCode = "200", description = "Account details retrieved successfully")
    @ApiResponse(responseCode = "404", description = "Direct deposit account not found")
    @GetMapping("/account")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<DirectDepositAccountDto> getDirectDepositAccount() {
        String userId = securityContext.getCurrentUserId();
        log.debug("Getting direct deposit account for user: {}", userId);
        
        DirectDepositAccountDto account = directDepositService.getDirectDepositAccount(userId);
        return ResponseEntity.ok(account);
    }

    @Operation(
        summary = "Get direct deposit instructions",
        description = "Get detailed instructions for setting up direct deposit"
    )
    @ApiResponse(responseCode = "200", description = "Instructions retrieved successfully")
    @GetMapping("/instructions")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<DirectDepositInstructionsDto> getDirectDepositInstructions() {
        String userId = securityContext.getCurrentUserId();
        log.debug("Getting direct deposit instructions for user: {}", userId);
        
        DirectDepositInstructionsDto instructions = directDepositService.getDirectDepositInstructions(userId);
        return ResponseEntity.ok(instructions);
    }

    @Operation(
        summary = "Add employer",
        description = "Add employer information for direct deposit setup"
    )
    @ApiResponse(responseCode = "201", description = "Employer added successfully")
    @ApiResponse(responseCode = "409", description = "Employer already exists")
    @PostMapping("/employers")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<EmployerInfoDto> addEmployer(
            @Valid @RequestBody AddEmployerRequest request) {
        String userId = securityContext.getCurrentUserId();
        log.info("Adding employer for user: {}, employer: {}", userId, request.getEmployerName());
        
        EmployerInfoDto employer = directDepositService.addEmployer(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(employer);
    }

    @Operation(
        summary = "Get employers",
        description = "Get list of employers added for direct deposit"
    )
    @ApiResponse(responseCode = "200", description = "Employers retrieved successfully")
    @GetMapping("/employers")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<List<EmployerInfoDto>> getEmployers() {
        String userId = securityContext.getCurrentUserId();
        log.debug("Getting employers for user: {}", userId);
        
        List<EmployerInfoDto> employers = directDepositService.getEmployers(userId);
        return ResponseEntity.ok(employers);
    }

    @Operation(
        summary = "Remove employer",
        description = "Remove an employer from direct deposit"
    )
    @ApiResponse(responseCode = "204", description = "Employer removed successfully")
    @DeleteMapping("/employers/{employerId}")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<Void> removeEmployer(
            @Parameter(description = "Employer ID") @PathVariable @ValidUUID String employerId) {
        String userId = securityContext.getCurrentUserId();
        log.info("Removing employer: {} for user: {}", employerId, userId);
        
        directDepositService.removeEmployer(userId, employerId);
        return ResponseEntity.noContent().build();
    }

    @Operation(
        summary = "Get direct deposit history",
        description = "Get paginated list of direct deposits received"
    )
    @ApiResponse(responseCode = "200", description = "Direct deposit history retrieved successfully")
    @GetMapping("/deposits")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<Page<DirectDepositDto>> getDirectDepositHistory(
            @PageableDefault(size = 20) Pageable pageable) {
        String userId = securityContext.getCurrentUserId();
        log.debug("Getting direct deposit history for user: {}", userId);
        
        Page<DirectDepositDto> deposits = directDepositService.getDirectDepositHistory(userId, pageable);
        return ResponseEntity.ok(deposits);
    }

    @Operation(
        summary = "Initiate ACH transfer",
        description = "Initiate an ACH transfer from linked bank account"
    )
    @ApiResponse(responseCode = "201", description = "ACH transfer initiated successfully")
    @ApiResponse(responseCode = "400", description = "Invalid request or limits exceeded")
    @PostMapping("/ach-transfers")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<ACHTransferDto> initiateACHTransfer(
            @Valid @RequestBody InitiateACHRequest request) {
        String userId = securityContext.getCurrentUserId();
        log.info("Initiating ACH transfer for user: {}, amount: {}, instant: {}", 
            userId, request.getAmount(), request.isInstant());
        
        ACHTransferDto transfer = directDepositService.initiateACHTransfer(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(transfer);
    }

    @Operation(
        summary = "Get ACH transfer history",
        description = "Get paginated list of ACH transfers"
    )
    @ApiResponse(responseCode = "200", description = "ACH transfer history retrieved successfully")
    @GetMapping("/ach-transfers")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<Page<ACHTransferDto>> getACHTransferHistory(
            @PageableDefault(size = 20) Pageable pageable) {
        String userId = securityContext.getCurrentUserId();
        log.debug("Getting ACH transfer history for user: {}", userId);
        
        Page<ACHTransferDto> transfers = directDepositService.getACHTransferHistory(userId, pageable);
        return ResponseEntity.ok(transfers);
    }

    @Operation(
        summary = "Get ACH transfer details",
        description = "Get detailed information about a specific ACH transfer"
    )
    @ApiResponse(responseCode = "200", description = "Transfer details retrieved successfully")
    @GetMapping("/ach-transfers/{transferId}")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<ACHTransferDto> getACHTransferDetails(
            @Parameter(description = "Transfer ID") @PathVariable @ValidUUID String transferId) {
        String userId = securityContext.getCurrentUserId();
        log.debug("Getting ACH transfer details: {}, user: {}", transferId, userId);
        
        ACHTransferDto transfer = directDepositService.getACHTransferDetails(transferId, userId);
        return ResponseEntity.ok(transfer);
    }

    @Operation(
        summary = "Cancel ACH transfer",
        description = "Cancel a pending ACH transfer"
    )
    @ApiResponse(responseCode = "204", description = "Transfer cancelled successfully")
    @ApiResponse(responseCode = "400", description = "Transfer cannot be cancelled")
    @DeleteMapping("/ach-transfers/{transferId}")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<Void> cancelACHTransfer(
            @Parameter(description = "Transfer ID") @PathVariable @ValidUUID String transferId) {
        String userId = securityContext.getCurrentUserId();
        log.info("Cancelling ACH transfer: {} for user: {}", transferId, userId);
        
        directDepositService.cancelACHTransfer(transferId, userId);
        return ResponseEntity.noContent().build();
    }

    @Operation(
        summary = "Update direct deposit settings",
        description = "Update direct deposit auto-transfer and notification settings"
    )
    @ApiResponse(responseCode = "200", description = "Settings updated successfully")
    @PutMapping("/settings")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<DirectDepositAccountDto> updateDirectDepositSettings(
            @Valid @RequestBody UpdateDirectDepositRequest request) {
        String userId = securityContext.getCurrentUserId();
        log.info("Updating direct deposit settings for user: {}", userId);
        
        DirectDepositAccountDto account = directDepositService.updateDirectDepositSettings(userId, request);
        return ResponseEntity.ok(account);
    }

    @Operation(
        summary = "Get deposit analytics",
        description = "Get analytics about direct deposits and ACH transfers"
    )
    @ApiResponse(responseCode = "200", description = "Analytics retrieved successfully")
    @GetMapping("/analytics")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<DirectDepositAnalyticsDto> getDepositAnalytics(
            @RequestParam(defaultValue = "30") int days) {
        String userId = securityContext.getCurrentUserId();
        log.debug("Getting direct deposit analytics for user: {}, days: {}", userId, days);
        
        DirectDepositAnalyticsDto analytics = directDepositService.getDepositAnalytics(userId, days);
        return ResponseEntity.ok(analytics);
    }

    @Operation(
        summary = "Process incoming ACH",
        description = "Process incoming ACH transaction from external source (webhook)"
    )
    @ApiResponse(responseCode = "200", description = "ACH processed successfully")
    @PostMapping("/webhook/incoming-ach")
    @PreAuthorize("hasRole('ACH_PROVIDER')")
    public ResponseEntity<Void> processIncomingACH(
            @Valid @RequestBody IncomingACHRequest request) {
        log.info("Processing incoming ACH for account: {}, amount: {}, originator: {}", 
            request.getAccountNumber(), request.getAmount(), request.getOriginatorName());
        
        directDepositService.processIncomingDirectDeposit(request);
        return ResponseEntity.ok().build();
    }

    @Operation(
        summary = "ACH status update",
        description = "Receive ACH status updates from provider (webhook)"
    )
    @ApiResponse(responseCode = "200", description = "Status update processed successfully")
    @PostMapping("/webhook/ach-status")
    @PreAuthorize("hasRole('ACH_PROVIDER')")
    public ResponseEntity<Void> updateACHStatus(
            @Valid @RequestBody ACHStatusUpdateRequest request) {
        log.info("ACH status update for transfer: {}, status: {}", 
            request.getTransferId(), request.getStatus());
        
        directDepositService.updateACHStatus(request);
        return ResponseEntity.ok().build();
    }

    @Operation(
        summary = "Get ACH limits",
        description = "Get current ACH transfer limits and usage"
    )
    @ApiResponse(responseCode = "200", description = "Limits retrieved successfully")
    @GetMapping("/ach-limits")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<ACHLimitsDto> getACHLimits() {
        String userId = securityContext.getCurrentUserId();
        log.debug("Getting ACH limits for user: {}", userId);
        
        ACHLimitsDto limits = directDepositService.getACHLimits(userId);
        return ResponseEntity.ok(limits);
    }

    @Operation(
        summary = "Verify micro deposits",
        description = "Verify bank account ownership through micro deposit verification"
    )
    @ApiResponse(responseCode = "200", description = "Micro deposits verified successfully")
    @ApiResponse(responseCode = "400", description = "Invalid amounts")
    @PostMapping("/verify-micro-deposits")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<VerificationResultDto> verifyMicroDeposits(
            @Valid @RequestBody VerifyMicroDepositsRequest request) {
        String userId = securityContext.getCurrentUserId();
        log.info("Verifying micro deposits for user: {}, bank account: {}", 
            userId, request.getBankAccountId());
        
        VerificationResultDto result = directDepositService.verifyMicroDeposits(userId, request);
        return ResponseEntity.ok(result);
    }

    @Operation(
        summary = "Admin: Get all direct deposits",
        description = "Get all direct deposits in the system (admin only)"
    )
    @ApiResponse(responseCode = "200", description = "Direct deposits retrieved successfully")
    @GetMapping("/admin/deposits")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Page<DirectDepositDto>> getAllDirectDeposits(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @PageableDefault(size = 50) Pageable pageable) {
        log.info("Admin getting all direct deposits, status: {}, type: {}", status, type);
        
        Page<DirectDepositDto> deposits = directDepositService.getAllDirectDeposits(
            status, type, startDate, endDate, pageable);
        return ResponseEntity.ok(deposits);
    }

    @Operation(
        summary = "Admin: Get system metrics",
        description = "Get system-wide direct deposit and ACH metrics (admin only)"
    )
    @ApiResponse(responseCode = "200", description = "System metrics retrieved successfully")
    @GetMapping("/admin/metrics")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<DirectDepositSystemMetricsDto> getSystemMetrics() {
        log.info("Getting direct deposit system metrics");
        
        DirectDepositSystemMetricsDto metrics = directDepositService.getSystemMetrics();
        return ResponseEntity.ok(metrics);
    }
}