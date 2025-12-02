package com.waqiti.legal.client;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Foreclosure Service Client
 *
 * Feign client for foreclosure-service integration.
 * Handles foreclosure and repossession proceedings for automatic stay enforcement.
 *
 * @author Waqiti Legal Team
 * @version 1.0.0
 * @since 2025-10-19
 */
@FeignClient(
    name = "foreclosure-service",
    url = "${foreclosure.service.url:http://foreclosure-service:8080}",
    fallback = ForeclosureServiceClient.ForeclosureServiceFallback.class
)
public interface ForeclosureServiceClient {

    /**
     * Get active foreclosure proceedings for customer
     */
    @GetMapping("/api/v1/foreclosures/customer/{customerId}/active")
    List<ForeclosureDto> getActiveForeclosures(@PathVariable("customerId") String customerId);

    /**
     * Halt foreclosure proceeding (automatic stay)
     */
    @PostMapping("/api/v1/foreclosures/{foreclosureId}/halt")
    Map<String, Object> haltForeclosureProceeding(
        @PathVariable("foreclosureId") String foreclosureId,
        @RequestParam("reason") String reason,
        @RequestParam("bankruptcyId") String bankruptcyId
    );

    /**
     * Resume foreclosure proceeding (stay lifted)
     */
    @PostMapping("/api/v1/foreclosures/{foreclosureId}/resume")
    Map<String, Object> resumeForeclosureProceeding(
        @PathVariable("foreclosureId") String foreclosureId,
        @RequestParam("reason") String reason,
        @RequestParam("bankruptcyId") String bankruptcyId
    );

    /**
     * Get foreclosures since date (for stay compliance check)
     */
    @GetMapping("/api/v1/foreclosures/customer/{customerId}/since")
    List<ForeclosureDto> getForeclosuresSinceDate(
        @PathVariable("customerId") String customerId,
        @RequestParam("sinceDate") LocalDate sinceDate
    );

    /**
     * Foreclosure DTO
     */
    class ForeclosureDto {
        private String foreclosureId;
        private String customerId;
        private String propertyAddress;
        private String loanNumber;
        private String status; // ACTIVE, HALTED, COMPLETED, CANCELLED
        private LocalDate filingDate;
        private LocalDate saleDate;
        private String haltReason;

        // Getters and setters
        public String getForeclosureId() { return foreclosureId; }
        public void setForeclosureId(String foreclosureId) { this.foreclosureId = foreclosureId; }

        public String getCustomerId() { return customerId; }
        public void setCustomerId(String customerId) { this.customerId = customerId; }

        public String getPropertyAddress() { return propertyAddress; }
        public void setPropertyAddress(String propertyAddress) { this.propertyAddress = propertyAddress; }

        public String getLoanNumber() { return loanNumber; }
        public void setLoanNumber(String loanNumber) { this.loanNumber = loanNumber; }

        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }

        public LocalDate getFilingDate() { return filingDate; }
        public void setFilingDate(LocalDate filingDate) { this.filingDate = filingDate; }

        public LocalDate getSaleDate() { return saleDate; }
        public void setSaleDate(LocalDate saleDate) { this.saleDate = saleDate; }

        public String getHaltReason() { return haltReason; }
        public void setHaltReason(String haltReason) { this.haltReason = haltReason; }
    }

    /**
     * Fallback implementation for circuit breaker
     */
    @Component
    @Slf4j
    @RequiredArgsConstructor
    class ForeclosureServiceFallback implements ForeclosureServiceClient {

        @Override
        public List<ForeclosureDto> getActiveForeclosures(String customerId) {
            log.error("FORECLOSURE_SERVICE_UNAVAILABLE: Failed to get active foreclosures for customer {}. " +
                "Circuit breaker open - foreclosure service unavailable.", customerId);
            return Collections.emptyList();
        }

        @Override
        public Map<String, Object> haltForeclosureProceeding(
                String foreclosureId, String reason, String bankruptcyId) {

            log.error("CRITICAL_FORECLOSURE_HALT_FAILURE: Failed to halt foreclosure {} for bankruptcy {}. " +
                "Circuit breaker open - MANUAL INTERVENTION REQUIRED to avoid stay violation.",
                foreclosureId, bankruptcyId);

            Map<String, Object> result = new HashMap<>();
            result.put("success", false);
            result.put("fallback", true);
            result.put("foreclosureId", foreclosureId);
            result.put("error", "Foreclosure service unavailable - circuit breaker open");
            result.put("actionRequired", "MANUAL_HALT_REQUIRED");

            return result;
        }

        @Override
        public Map<String, Object> resumeForeclosureProceeding(
                String foreclosureId, String reason, String bankruptcyId) {

            log.warn("FORECLOSURE_SERVICE_UNAVAILABLE: Failed to resume foreclosure {} for bankruptcy {}. " +
                "Circuit breaker open.", foreclosureId, bankruptcyId);

            Map<String, Object> result = new HashMap<>();
            result.put("success", false);
            result.put("fallback", true);
            result.put("foreclosureId", foreclosureId);
            result.put("error", "Foreclosure service unavailable - circuit breaker open");

            return result;
        }

        @Override
        public List<ForeclosureDto> getForeclosuresSinceDate(String customerId, LocalDate sinceDate) {
            log.error("FORECLOSURE_SERVICE_UNAVAILABLE: Failed to check foreclosures since {} for customer {}. " +
                "Circuit breaker open - MANUAL COMPLIANCE CHECK REQUIRED.",
                sinceDate, customerId);
            return Collections.emptyList();
        }
    }
}
