package com.waqiti.business.controller;

import com.waqiti.business.dto.*;
import com.waqiti.business.entity.BusinessAccount;
import com.waqiti.business.entity.BusinessPayment;
import com.waqiti.business.service.BusinessService;
import com.waqiti.common.util.ResultWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Business Service REST API Controller
 * 
 * Provides endpoints for business account management and B2B payments
 */
@RestController
@RequestMapping("/api/v1/business")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = {"http://localhost:3000", "https://example.com"})
public class BusinessController {

    private final BusinessService businessService;

    /**
     * Create business account
     */
    @PostMapping("/accounts")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<ResultWrapper<BusinessAccount>> createBusinessAccount(
            @Valid @RequestBody CreateBusinessAccountRequest request) {
        
        log.info("Creating business account for user: {}", request.getOwnerId());
        
        ResultWrapper<BusinessAccount> result = businessService.createBusinessAccount(request);
        
        if (result.isSuccess()) {
            return ResponseEntity.ok(result);
        } else {
            return ResponseEntity.badRequest().body(result);
        }
    }

    /**
     * Get business account
     */
    @GetMapping("/accounts/{businessId}")
    @PreAuthorize("hasRole('BUSINESS_OWNER') or hasRole('BUSINESS_MEMBER')")
    public ResponseEntity<BusinessAccountDto> getBusinessAccount(
            @PathVariable String businessId) {
        
        log.info("Getting business account: {}", businessId);
        
        BusinessAccountDto account = businessService.getBusinessAccount(businessId);
        return ResponseEntity.ok(account);
    }

    /**
     * Add business member
     */
    @PostMapping("/accounts/{businessId}/members")
    @PreAuthorize("hasRole('BUSINESS_OWNER')")
    public ResponseEntity<ResultWrapper<BusinessMemberDto>> addBusinessMember(
            @PathVariable String businessId,
            @Valid @RequestBody AddBusinessMemberRequest request) {
        
        log.info("Adding member to business: {}", businessId);
        
        ResultWrapper<BusinessMemberDto> result = 
            businessService.addBusinessMember(businessId, request);
        
        if (result.isSuccess()) {
            return ResponseEntity.ok(result);
        } else {
            return ResponseEntity.badRequest().body(result);
        }
    }

    /**
     * Process business payment
     */
    @PostMapping("/payments")
    @PreAuthorize("hasRole('BUSINESS_MEMBER')")
    public CompletableFuture<ResponseEntity<ResultWrapper<BusinessPaymentResponse>>> processBusinessPayment(
            @Valid @RequestBody ProcessBusinessPaymentRequest request) {
        
        log.info("Processing business payment from: {} to: {} amount: {}", 
            request.getFromBusinessId(), request.getToBusinessId(), request.getAmount());
        
        return businessService.processBusinessPayment(request)
            .thenApply(result -> {
                if (result.isSuccess()) {
                    return ResponseEntity.ok(result);
                } else {
                    return ResponseEntity.badRequest().body(result);
                }
            });
    }

    /**
     * Get business payments
     */
    @GetMapping("/accounts/{businessId}/payments")
    @PreAuthorize("hasRole('BUSINESS_MEMBER')")
    public ResponseEntity<List<BusinessPaymentDto>> getBusinessPayments(
            @PathVariable String businessId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        
        log.info("Getting payments for business: {}", businessId);
        
        Pageable pageable = PageRequest.of(page, size);
        Page<BusinessPaymentDto> payments = 
            businessService.getBusinessPayments(businessId, pageable);
        
        return ResponseEntity.ok(payments.getContent());
    }

    /**
     * Get business analytics
     */
    @GetMapping("/accounts/{businessId}/analytics")
    @PreAuthorize("hasRole('BUSINESS_OWNER')")
    public ResponseEntity<BusinessAnalyticsDto> getBusinessAnalytics(
            @PathVariable String businessId,
            @RequestParam(defaultValue = "30") int days) {
        
        log.info("Getting analytics for business: {} days: {}", businessId, days);
        
        BusinessAnalyticsDto analytics = businessService.getBusinessAnalytics(businessId, days);
        return ResponseEntity.ok(analytics);
    }

    /**
     * Generate expense report
     */
    @PostMapping("/accounts/{businessId}/reports/expenses")
    @PreAuthorize("hasRole('BUSINESS_OWNER')")
    public ResponseEntity<ResultWrapper<ExpenseReportDto>> generateExpenseReport(
            @PathVariable String businessId,
            @Valid @RequestBody GenerateExpenseReportRequest request) {
        
        log.info("Generating expense report for business: {}", businessId);
        
        ResultWrapper<ExpenseReportDto> result = 
            businessService.generateExpenseReport(businessId, request);
        
        if (result.isSuccess()) {
            return ResponseEntity.ok(result);
        } else {
            return ResponseEntity.badRequest().body(result);
        }
    }

    /**
     * Health check endpoint
     */
    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("Business Service is healthy");
    }
}