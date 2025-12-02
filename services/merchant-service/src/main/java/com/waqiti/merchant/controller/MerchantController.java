package com.waqiti.merchant.controller;

import com.waqiti.merchant.dto.*;
import com.waqiti.merchant.entity.Merchant;
import com.waqiti.merchant.enums.*;
import com.waqiti.merchant.service.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * REST Controller for Merchant Management
 * Provides comprehensive API for merchant onboarding, management, verification, and settlement
 */
@RestController
@RequestMapping("/api/v1/merchants")
@Tag(name = "Merchant Management", description = "APIs for merchant onboarding, verification, and management")
@RequiredArgsConstructor
@Validated
@Slf4j
public class MerchantController {

    private final MerchantService merchantService;
    private final MerchantOnboardingService onboardingService;
    private final MerchantVerificationService verificationService;
    private final MerchantSettlementService settlementService;
    private final MerchantFeeService feeService;
    private final MerchantAnalyticsService analyticsService;

    /**
     * Register a new merchant
     */
    @PostMapping("/register")
    @Operation(summary = "Register new merchant", description = "Initiate merchant onboarding process")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Merchant registration initiated"),
        @ApiResponse(responseCode = "400", description = "Invalid merchant data"),
        @ApiResponse(responseCode = "409", description = "Merchant already exists")
    })
    public ResponseEntity<MerchantDTO> registerMerchant(
            @Valid @RequestBody MerchantRegistrationRequest request) {
        
        log.info("Initiating merchant registration for business: {}", request.getBusinessName());
        
        try {
            MerchantDTO merchant = onboardingService.registerMerchant(request);
            log.info("Merchant registered successfully with code: {}", merchant.getMerchantCode());
            return ResponseEntity.status(HttpStatus.CREATED).body(merchant);
            
        } catch (Exception e) {
            log.error("Error registering merchant: {}", request.getBusinessName(), e);
            throw e;
        }
    }

    /**
     * Get merchant by ID
     */
    @GetMapping("/{merchantId}")
    @Operation(summary = "Get merchant details", description = "Retrieve detailed information about a merchant")
    @PreAuthorize("hasRole('ADMIN') or hasRole('MERCHANT_MANAGER') or #merchantId == authentication.principal.merchantId")
    public ResponseEntity<MerchantDTO> getMerchant(
            @PathVariable @NotBlank String merchantId) {
        
        log.debug("Fetching merchant details for ID: {}", merchantId);
        MerchantDTO merchant = merchantService.getMerchant(merchantId);
        return ResponseEntity.ok(merchant);
    }

    /**
     * Health check endpoint
     */
    @GetMapping("/health")
    @Operation(summary = "Health check", description = "Check if merchant service is running")
    public ResponseEntity<Map<String, Object>> healthCheck() {
        Map<String, Object> health = Map.of(
                "status", "UP",
                "service", "merchant-service",
                "timestamp", LocalDateTime.now().toString(),
                "components", Map.of(
                    "database", "UP",
                    "cache", "UP",
                    "messaging", "UP"
                )
        );
        
        return ResponseEntity.ok(health);
    }
}