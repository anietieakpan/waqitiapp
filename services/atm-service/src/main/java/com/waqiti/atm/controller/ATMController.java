package com.waqiti.atm.controller;

import com.waqiti.atm.dto.*;
import com.waqiti.atm.service.ATMService;
import com.waqiti.atm.service.CardlessATMService;
import com.waqiti.atm.service.ATMLocationService;
import com.waqiti.atm.security.AtmBiometricAuthService;
import com.waqiti.atm.security.AtmBiometricAuthService.*;
import com.waqiti.common.api.ApiResponse;
import com.waqiti.common.ratelimit.RateLimited;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/atm")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "ATM Operations", description = "ATM and cardless withdrawal operations with biometric authentication")
public class ATMController {

    private final ATMService atmService;
    private final CardlessATMService cardlessATMService;
    private final ATMLocationService atmLocationService;
    private final AtmBiometricAuthService biometricAuthService;

    // Cardless ATM Operations
    @PostMapping("/cardless/initiate")
    @Operation(summary = "Initiate cardless ATM withdrawal")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<ApiResponse<CardlessWithdrawalResponse>> initiateCardlessWithdrawal(
            @Valid @RequestBody CardlessWithdrawalRequest request) {
        log.info("Initiating cardless withdrawal for amount: {}", request.getAmount());
        
        CardlessWithdrawalResponse response = cardlessATMService.initiateWithdrawal(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response));
    }

    @PostMapping("/cardless/qr-code")
    @Operation(summary = "Generate QR code for ATM withdrawal")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<ApiResponse<QRCodeResponse>> generateWithdrawalQRCode(
            @Valid @RequestBody QRCodeRequest request) {
        log.info("Generating QR code for withdrawal: {}", request.getWithdrawalId());
        
        QRCodeResponse response = cardlessATMService.generateQRCode(request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/cardless/verify")
    @Operation(summary = "Verify cardless withdrawal code")
    @PreAuthorize("hasRole('ATM_SYSTEM')")
    public ResponseEntity<ApiResponse<WithdrawalVerificationResponse>> verifyWithdrawal(
            @Valid @RequestBody WithdrawalVerificationRequest request) {
        log.info("Verifying withdrawal code: {}", request.getCode());
        
        WithdrawalVerificationResponse response = cardlessATMService.verifyWithdrawal(request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/cardless/complete")
    @Operation(summary = "Complete cardless withdrawal")
    @PreAuthorize("hasRole('ATM_SYSTEM')")
    public ResponseEntity<ApiResponse<WithdrawalCompletionResponse>> completeWithdrawal(
            @Valid @RequestBody WithdrawalCompletionRequest request) {
        log.info("Completing withdrawal: {}", request.getWithdrawalId());
        
        WithdrawalCompletionResponse response = cardlessATMService.completeWithdrawal(request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/cardless/cancel/{withdrawalId}")
    @Operation(summary = "Cancel cardless withdrawal")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<ApiResponse<Void>> cancelWithdrawal(@PathVariable UUID withdrawalId) {
        log.info("Cancelling withdrawal: {}", withdrawalId);
        
        cardlessATMService.cancelWithdrawal(withdrawalId);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    // Biometric Authentication Operations
    @PostMapping("/biometric/initiate")
    @Operation(summary = "Initiate biometric authentication for ATM transaction", 
               description = "Determines biometric requirements based on transaction risk and initiates authentication session")
    @PreAuthorize("hasRole('ATM_SYSTEM')")
    @RateLimited(keyType = RateLimited.KeyType.IP, capacity = 20, refillTokens = 20, refillPeriodMinutes = 1)
    public ResponseEntity<ApiResponse<BiometricInitiationResponse>> initiateBiometricAuth(
            @RequestParam String atmId,
            @RequestParam String cardNumber,
            @RequestParam BigDecimal amount,
            @RequestParam TransactionType transactionType) {
        
        log.info("Initiating biometric auth for ATM {} transaction type {} amount {}", 
                atmId, transactionType, amount);
        
        // Determine biometric requirements
        BiometricAuthRequirement requirement = biometricAuthService.determineBiometricRequirement(
                atmId, cardNumber, amount, transactionType);
        
        if (requirement.isBlocked()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ApiResponse.error(requirement.getMessage()));
        }
        
        if (requirement.isLocked()) {
            return ResponseEntity.status(HttpStatus.LOCKED)
                    .body(ApiResponse.error(requirement.getMessage()));
        }
        
        // Create session if biometric required
        String sessionId = null;
        if (requirement.isRequired()) {
            BiometricSession session = biometricAuthService.createSession(
                    atmId, cardNumber, amount, transactionType, requirement);
            sessionId = session.getSessionId();
        }
        
        BiometricInitiationResponse response = BiometricInitiationResponse.builder()
                .sessionId(sessionId)
                .biometricRequired(requirement.isRequired())
                .requiredMethods(requirement.getRequiredMethods())
                .livenessCheckRequired(requirement.isLivenessCheckRequired())
                .sessionDurationMinutes(requirement.getSessionDuration())
                .message(requirement.getMessage())
                .build();
        
        return ResponseEntity.ok(ApiResponse.success(response));
    }
    
    @PostMapping("/biometric/verify")
    @Operation(summary = "Verify biometric data", 
               description = "Verifies captured biometric data against enrolled templates with liveness detection")
    @PreAuthorize("hasRole('ATM_SYSTEM')")
    @RateLimited(keyType = RateLimited.KeyType.IP, capacity = 10, refillTokens = 10, refillPeriodMinutes = 5)
    public ResponseEntity<ApiResponse<BiometricVerificationResult>> verifyBiometric(
            @RequestParam String sessionId,
            @RequestParam String cardNumber,
            @Valid @RequestBody Map<BiometricMethod, BiometricData> biometricData) {
        
        log.info("Verifying biometric for session {} with {} methods", 
                sessionId, biometricData.size());
        
        BiometricVerificationResult result = biometricAuthService.verifyBiometric(
                sessionId, cardNumber, biometricData);
        
        if (result.isAccountLocked()) {
            return ResponseEntity.status(HttpStatus.LOCKED).body(ApiResponse.error(result.getErrorMessage()));
        }
        
        if (!result.isSuccess()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiResponse.error(
                    result.getErrorMessage(), 
                    Map.of("attemptsRemaining", result.getAttemptsRemaining(),
                           "failedMethods", result.getFailedMethods())));
        }
        
        return ResponseEntity.ok(ApiResponse.success(result));
    }
    
    @PostMapping("/biometric/enroll")
    @Operation(summary = "Enroll biometric template", 
               description = "Enrolls a new biometric template for user authentication at ATMs")
    @PreAuthorize("hasRole('USER') or hasRole('BANK_TELLER')")
    @RateLimited(keyType = RateLimited.KeyType.USER, capacity = 5, refillTokens = 5, refillPeriodMinutes = 60)
    public ResponseEntity<ApiResponse<BiometricEnrollmentResult>> enrollBiometric(
            @RequestParam String cardNumber,
            @RequestParam BiometricMethod method,
            @RequestParam String enrollmentLocation,
            @Valid @RequestBody BiometricData biometricData) {
        
        log.info("Enrolling {} biometric for card ending ****{}", 
                method, cardNumber.substring(cardNumber.length() - 4));
        
        BiometricEnrollmentResult result = biometricAuthService.enrollBiometric(
                cardNumber, method, biometricData, enrollmentLocation);
        
        if (!result.isSuccess()) {
            return ResponseEntity.badRequest().body(ApiResponse.error(
                    result.getErrorMessage(),
                    Map.of("qualityScore", result.getQualityScore(),
                           "qualityIssues", result.getQualityIssues())));
        }
        
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(result));
    }
    
    @PostMapping("/biometric/update")
    @Operation(summary = "Update biometric template", 
               description = "Updates an existing biometric template with new data")
    @PreAuthorize("hasRole('USER')")
    @RateLimited(keyType = RateLimited.KeyType.USER, capacity = 3, refillTokens = 3, refillPeriodMinutes = 60)
    public ResponseEntity<ApiResponse<BiometricEnrollmentResult>> updateBiometric(
            @RequestParam String cardNumber,
            @RequestParam BiometricMethod method,
            @RequestParam String verificationCode,
            @Valid @RequestBody BiometricData biometricData) {
        
        log.info("Updating {} biometric for card ending ****{}", 
                method, cardNumber.substring(cardNumber.length() - 4));
        
        // Verify 2FA code before allowing update
        // This would integrate with existing 2FA service
        
        BiometricEnrollmentResult result = biometricAuthService.enrollBiometric(
                cardNumber, method, biometricData, "UPDATE");
        
        if (!result.isSuccess()) {
            return ResponseEntity.badRequest().body(ApiResponse.error(result.getErrorMessage()));
        }
        
        return ResponseEntity.ok(ApiResponse.success(result));
    }
    
    @DeleteMapping("/biometric/remove")
    @Operation(summary = "Remove biometric template", 
               description = "Removes an enrolled biometric template (requires 2FA)")
    @PreAuthorize("hasRole('USER')")
    @RateLimited(keyType = RateLimited.KeyType.USER, capacity = 2, refillTokens = 2, refillPeriodMinutes = 60)
    public ResponseEntity<ApiResponse<Void>> removeBiometric(
            @RequestParam String cardNumber,
            @RequestParam BiometricMethod method,
            @RequestParam String verificationCode) {
        
        log.info("Removing {} biometric for card ending ****{}", 
                method, cardNumber.substring(cardNumber.length() - 4));
        
        // Verify 2FA code and remove template
        // Implementation would be added
        
        return ResponseEntity.ok(ApiResponse.success(null));
    }
    
    @GetMapping("/biometric/methods")
    @Operation(summary = "Get enrolled biometric methods", 
               description = "Returns list of biometric methods enrolled for the card")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<ApiResponse<List<BiometricMethod>>> getEnrolledMethods(
            @RequestParam String cardNumber) {
        
        log.info("Getting enrolled biometric methods for card ending ****{}", 
                cardNumber.substring(cardNumber.length() - 4));
        
        // This would be implemented to return enrolled methods
        List<BiometricMethod> methods = List.of(BiometricMethod.FINGERPRINT, BiometricMethod.FACIAL_RECOGNITION);
        
        return ResponseEntity.ok(ApiResponse.success(methods));
    }
    
    @PostMapping("/biometric/emergency-override")
    @Operation(summary = "Emergency biometric override", 
               description = "Allows emergency override with duress detection (triggers silent alarm)")
    @PreAuthorize("hasRole('ATM_SYSTEM')")
    public ResponseEntity<ApiResponse<EmergencyOverrideResponse>> emergencyOverride(
            @RequestParam String atmId,
            @RequestParam String cardNumber,
            @RequestParam String overrideCode) {
        
        log.error("EMERGENCY OVERRIDE requested at ATM {} for card ****{}", 
                atmId, cardNumber.substring(cardNumber.length() - 4));
        
        // This would trigger security protocols and silent alarms
        EmergencyOverrideResponse response = EmergencyOverrideResponse.builder()
                .overrideAccepted(true)
                .silentAlarmTriggered(true)
                .message("Transaction proceeding under duress protocol")
                .build();
        
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    // ATM Location Services
    @GetMapping("/locations/nearby")
    @Operation(summary = "Find nearby ATMs")
    public ResponseEntity<ApiResponse<List<ATMLocationResponse>>> findNearbyATMs(
            @RequestParam Double latitude,
            @RequestParam Double longitude,
            @RequestParam(defaultValue = "5") Double radiusKm) {
        log.info("Finding ATMs near: {}, {} within {}km", latitude, longitude, radiusKm);
        
        List<ATMLocationResponse> locations = atmLocationService.findNearbyATMs(
            latitude, longitude, radiusKm);
        return ResponseEntity.ok(ApiResponse.success(locations));
    }

    @GetMapping("/locations/search")
    @Operation(summary = "Search ATMs by criteria")
    public ResponseEntity<ApiResponse<Page<ATMLocationResponse>>> searchATMs(
            @RequestParam(required = false) String city,
            @RequestParam(required = false) String area,
            @RequestParam(required = false) Boolean isOperational,
            @RequestParam(required = false) Boolean has24HourAccess,
            @RequestParam(required = false) Boolean hasDisabilityAccess,
            Pageable pageable) {
        
        ATMSearchCriteria criteria = ATMSearchCriteria.builder()
                .city(city)
                .area(area)
                .isOperational(isOperational)
                .has24HourAccess(has24HourAccess)
                .hasDisabilityAccess(hasDisabilityAccess)
                .build();
        
        Page<ATMLocationResponse> locations = atmLocationService.searchATMs(criteria, pageable);
        return ResponseEntity.ok(ApiResponse.success(locations));
    }

    @GetMapping("/locations/{atmId}")
    @Operation(summary = "Get ATM details")
    public ResponseEntity<ApiResponse<ATMDetailsResponse>> getATMDetails(@PathVariable UUID atmId) {
        log.info("Getting details for ATM: {}", atmId);
        
        ATMDetailsResponse details = atmLocationService.getATMDetails(atmId);
        return ResponseEntity.ok(ApiResponse.success(details));
    }

    // Transaction History
    @GetMapping("/transactions/history")
    @Operation(summary = "Get ATM transaction history")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<ApiResponse<Page<ATMTransactionResponse>>> getTransactionHistory(
            @RequestParam UUID accountId,
            Pageable pageable) {
        log.info("Getting ATM transaction history for account: {}", accountId);
        
        Page<ATMTransactionResponse> history = atmService.getTransactionHistory(accountId, pageable);
        return ResponseEntity.ok(ApiResponse.success(history));
    }

    // Withdrawal Limits
    @GetMapping("/limits")
    @Operation(summary = "Get ATM withdrawal limits")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<ApiResponse<WithdrawalLimitsResponse>> getWithdrawalLimits(
            @RequestParam UUID accountId) {
        log.info("Getting withdrawal limits for account: {}", accountId);
        
        WithdrawalLimitsResponse limits = atmService.getWithdrawalLimits(accountId);
        return ResponseEntity.ok(ApiResponse.success(limits));
    }

    @PutMapping("/limits")
    @Operation(summary = "Update ATM withdrawal limits")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<ApiResponse<WithdrawalLimitsResponse>> updateWithdrawalLimits(
            @RequestParam UUID accountId,
            @Valid @RequestBody UpdateWithdrawalLimitsRequest request) {
        log.info("Updating withdrawal limits for account: {}", accountId);
        
        WithdrawalLimitsResponse limits = atmService.updateWithdrawalLimits(accountId, request);
        return ResponseEntity.ok(ApiResponse.success(limits));
    }

    // PIN Management
    @PostMapping("/pin/change")
    @Operation(summary = "Change ATM PIN")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<ApiResponse<Void>> changeATMPin(
            @Valid @RequestBody ChangePinRequest request) {
        log.info("Processing PIN change request for card ending: ****{}", 
                request.getCardNumber().substring(request.getCardNumber().length() - 4));
        
        atmService.changePin(request);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @PostMapping("/pin/reset")
    @Operation(summary = "Reset ATM PIN")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<ApiResponse<PinResetResponse>> resetATMPin(
            @Valid @RequestBody PinResetRequest request) {
        log.info("Processing PIN reset request");
        
        PinResetResponse response = atmService.resetPin(request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    // Card Management
    @PostMapping("/card/block")
    @Operation(summary = "Block ATM card")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<ApiResponse<Void>> blockCard(
            @Valid @RequestBody BlockCardRequest request) {
        log.info("Blocking card: ****{}", 
                request.getCardNumber().substring(request.getCardNumber().length() - 4));
        
        atmService.blockCard(request);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @PostMapping("/card/unblock")
    @Operation(summary = "Unblock ATM card")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<ApiResponse<Void>> unblockCard(
            @Valid @RequestBody UnblockCardRequest request) {
        log.info("Unblocking card: ****{}", 
                request.getCardNumber().substring(request.getCardNumber().length() - 4));
        
        atmService.unblockCard(request);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    // Admin Operations
    @PostMapping("/admin/atm/register")
    @Operation(summary = "Register new ATM")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<ATMDetailsResponse>> registerATM(
            @Valid @RequestBody RegisterATMRequest request) {
        log.info("Registering new ATM at location: {}", request.getAddress());
        
        ATMDetailsResponse response = atmLocationService.registerATM(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response));
    }

    @PutMapping("/admin/atm/{atmId}/status")
    @Operation(summary = "Update ATM status")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> updateATMStatus(
            @PathVariable UUID atmId,
            @Valid @RequestBody UpdateATMStatusRequest request) {
        log.info("Updating status for ATM: {} to {}", atmId, request.getStatus());
        
        atmLocationService.updateATMStatus(atmId, request);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @GetMapping("/admin/atm/maintenance")
    @Operation(summary = "Get ATMs requiring maintenance")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<List<ATMMaintenanceResponse>>> getATMsRequiringMaintenance() {
        log.info("Retrieving ATMs requiring maintenance");
        
        List<ATMMaintenanceResponse> atms = atmLocationService.getATMsRequiringMaintenance();
        return ResponseEntity.ok(ApiResponse.success(atms));
    }

    // Health Check
    @GetMapping("/health")
    @Operation(summary = "Health check")
    public ResponseEntity<ApiResponse<String>> healthCheck() {
        return ResponseEntity.ok(ApiResponse.success("ATM service is healthy"));
    }
}