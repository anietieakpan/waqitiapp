package com.waqiti.legal.client;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Garnishment Service Client
 *
 * Feign client for garnishment-service integration.
 * Handles wage garnishment proceedings for automatic stay enforcement.
 *
 * @author Waqiti Legal Team
 * @version 1.0.0
 * @since 2025-10-19
 */
@FeignClient(
    name = "garnishment-service",
    url = "${garnishment.service.url:http://garnishment-service:8080}",
    fallback = GarnishmentServiceClient.GarnishmentServiceFallback.class
)
public interface GarnishmentServiceClient {

    /**
     * Get active wage garnishments for customer
     */
    @GetMapping("/api/v1/garnishments/customer/{customerId}/active")
    List<GarnishmentDto> getActiveGarnishments(@PathVariable("customerId") String customerId);

    /**
     * Stop garnishment (automatic stay)
     */
    @PostMapping("/api/v1/garnishments/{garnishmentId}/stop")
    Map<String, Object> stopGarnishment(
        @PathVariable("garnishmentId") String garnishmentId,
        @RequestParam("reason") String reason,
        @RequestParam("bankruptcyId") String bankruptcyId
    );

    /**
     * Resume garnishment (stay lifted)
     */
    @PostMapping("/api/v1/garnishments/{garnishmentId}/resume")
    Map<String, Object> resumeGarnishment(
        @PathVariable("garnishmentId") String garnishmentId,
        @RequestParam("reason") String reason,
        @RequestParam("bankruptcyId") String bankruptcyId
    );

    /**
     * Get garnishments since date (for stay compliance check)
     */
    @GetMapping("/api/v1/garnishments/customer/{customerId}/since")
    List<GarnishmentDto> getGarnishmentsSinceDate(
        @PathVariable("customerId") String customerId,
        @RequestParam("sinceDate") LocalDate sinceDate
    );

    /**
     * Garnishment DTO
     */
    class GarnishmentDto {
        private String garnishmentId;
        private String customerId;
        private String employerName;
        private String courtOrderNumber;
        private BigDecimal garnishmentAmount;
        private String garnishmentType; // WAGE, BANK_ACCOUNT, TAX_REFUND
        private String status; // ACTIVE, STOPPED, COMPLETED, CANCELLED
        private LocalDate startDate;
        private LocalDate endDate;
        private String stopReason;

        // Getters and setters
        public String getGarnishmentId() { return garnishmentId; }
        public void setGarnishmentId(String garnishmentId) { this.garnishmentId = garnishmentId; }

        public String getCustomerId() { return customerId; }
        public void setCustomerId(String customerId) { this.customerId = customerId; }

        public String getEmployerName() { return employerName; }
        public void setEmployerName(String employerName) { this.employerName = employerName; }

        public String getCourtOrderNumber() { return courtOrderNumber; }
        public void setCourtOrderNumber(String courtOrderNumber) { this.courtOrderNumber = courtOrderNumber; }

        public BigDecimal getGarnishmentAmount() { return garnishmentAmount; }
        public void setGarnishmentAmount(BigDecimal garnishmentAmount) { this.garnishmentAmount = garnishmentAmount; }

        public String getGarnishmentType() { return garnishmentType; }
        public void setGarnishmentType(String garnishmentType) { this.garnishmentType = garnishmentType; }

        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }

        public LocalDate getStartDate() { return startDate; }
        public void setStartDate(LocalDate startDate) { this.startDate = startDate; }

        public LocalDate getEndDate() { return endDate; }
        public void setEndDate(LocalDate endDate) { this.endDate = endDate; }

        public String getStopReason() { return stopReason; }
        public void setStopReason(String stopReason) { this.stopReason = stopReason; }
    }

    /**
     * Fallback implementation for circuit breaker
     */
    @Component
    @Slf4j
    @RequiredArgsConstructor
    class GarnishmentServiceFallback implements GarnishmentServiceClient {

        @Override
        public List<GarnishmentDto> getActiveGarnishments(String customerId) {
            log.error("GARNISHMENT_SERVICE_UNAVAILABLE: Failed to get active garnishments for customer {}. " +
                "Circuit breaker open - garnishment service unavailable.", customerId);
            return Collections.emptyList();
        }

        @Override
        public Map<String, Object> stopGarnishment(
                String garnishmentId, String reason, String bankruptcyId) {

            log.error("CRITICAL_GARNISHMENT_STOP_FAILURE: Failed to stop garnishment {} for bankruptcy {}. " +
                "Circuit breaker open - MANUAL INTERVENTION REQUIRED to avoid stay violation.",
                garnishmentId, bankruptcyId);

            Map<String, Object> result = new HashMap<>();
            result.put("success", false);
            result.put("fallback", true);
            result.put("garnishmentId", garnishmentId);
            result.put("error", "Garnishment service unavailable - circuit breaker open");
            result.put("actionRequired", "MANUAL_STOP_REQUIRED");

            return result;
        }

        @Override
        public Map<String, Object> resumeGarnishment(
                String garnishmentId, String reason, String bankruptcyId) {

            log.warn("GARNISHMENT_SERVICE_UNAVAILABLE: Failed to resume garnishment {} for bankruptcy {}. " +
                "Circuit breaker open.", garnishmentId, bankruptcyId);

            Map<String, Object> result = new HashMap<>();
            result.put("success", false);
            result.put("fallback", true);
            result.put("garnishmentId", garnishmentId);
            result.put("error", "Garnishment service unavailable - circuit breaker open");

            return result;
        }

        @Override
        public List<GarnishmentDto> getGarnishmentsSinceDate(String customerId, LocalDate sinceDate) {
            log.error("GARNISHMENT_SERVICE_UNAVAILABLE: Failed to check garnishments since {} for customer {}. " +
                "Circuit breaker open - MANUAL COMPLIANCE CHECK REQUIRED.",
                sinceDate, customerId);
            return Collections.emptyList();
        }
    }
}
