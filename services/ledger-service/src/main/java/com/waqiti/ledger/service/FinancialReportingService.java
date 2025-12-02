package com.waqiti.ledger.service;

import com.waqiti.payment.dto.PaymentChargebackEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Set;

/**
 * Financial Reporting Service - Handles comprehensive financial reporting and compliance
 * 
 * Provides enterprise-grade financial reporting capabilities for:
 * - Chargeback loss reporting and revenue impact analysis
 * - Platform reserve reporting and capital adequacy compliance
 * - Regulatory compliance reporting and audit trail management
 * - Financial statement integration and accounting reconciliation
 * - Capital adequacy reporting and regulatory submissions
 * - Compliance monitoring and regulatory alert management
 * 
 * @author Waqiti Engineering Team
 * @version 2.0.0
 * @since 2024-01-01
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FinancialReportingService {

    private final LedgerServiceImpl ledgerService;
    private final RedisTemplate<String, Object> redisTemplate;

    @Value("${financial.reporting.enabled:true}")
    private boolean financialReportingEnabled;

    @Value("${compliance.reporting.enabled:true}")
    private boolean complianceReportingEnabled;

    @Value("${regulatory.reporting.enabled:true}")
    private boolean regulatoryReportingEnabled;

    /**
     * Records chargeback loss for financial reporting
     * 
     * @param chargebackId Chargeback identifier
     * @param merchantId Merchant identifier
     * @param chargebackAmount Chargeback amount
     * @param chargebackFee Associated fees
     * @param currency Currency code
     * @param cardNetwork Card network
     * @param reasonCode Chargeback reason code
     * @param fraudRelated Whether fraud-related
     * @param timestamp Event timestamp
     */
    public void recordChargebackLoss(
            String chargebackId,
            String merchantId,
            BigDecimal chargebackAmount,
            BigDecimal chargebackFee,
            String currency,
            PaymentChargebackEvent.CardNetwork cardNetwork,
            String reasonCode,
            Boolean fraudRelated,
            LocalDateTime timestamp) {

        if (!financialReportingEnabled) {
            log.debug("Financial reporting disabled, skipping chargeback loss recording");
            return;
        }

        try {
            log.info("Recording chargeback loss for financial reporting - Chargeback: {} Amount: {} {}", 
                chargebackId, chargebackAmount, currency);

            // Calculate total loss including fees
            BigDecimal totalLoss = chargebackAmount.add(chargebackFee != null ? chargebackFee : BigDecimal.ZERO);

            // Create financial reporting entry
            FinancialReportingEntry entry = FinancialReportingEntry.builder()
                .entryId(chargebackId + "_loss_report")
                .entryType(FinancialEntryType.CHARGEBACK_LOSS)
                .chargebackId(chargebackId)
                .merchantId(merchantId)
                .amount(totalLoss)
                .currency(currency)
                .cardNetwork(cardNetwork.toString())
                .reasonCode(reasonCode)
                .fraudRelated(Boolean.TRUE.equals(fraudRelated))
                .reportingPeriod(determineReportingPeriod(timestamp))
                .timestamp(timestamp)
                .build();

            // Store financial reporting entry
            storeFinancialReportingEntry(entry);

            // Update financial metrics
            updateChargebackLossMetrics(entry);

            // Update period totals
            updatePeriodTotals(entry);

            log.info("Recorded chargeback loss for financial reporting: {} - Total Loss: {} {}", 
                chargebackId, totalLoss, currency);

        } catch (Exception e) {
            log.error("Failed to record chargeback loss for financial reporting: {}", chargebackId, e);
        }
    }

    /**
     * Triggers compliance reporting for events requiring regulatory attention
     * 
     * @param chargebackId Chargeback identifier
     * @param reportingCategory Category of compliance reporting
     * @param complianceFlags Set of compliance flags
     * @param reportingDeadline Reporting deadline
     */
    public void triggerComplianceReporting(
            String chargebackId,
            String reportingCategory,
            Set<String> complianceFlags,
            LocalDateTime reportingDeadline) {

        if (!complianceReportingEnabled) {
            log.debug("Compliance reporting disabled, skipping compliance reporting trigger");
            return;
        }

        try {
            log.info("Triggering compliance reporting for chargeback: {} - Category: {}", 
                chargebackId, reportingCategory);

            // Create compliance reporting request
            ComplianceReportingRequest request = ComplianceReportingRequest.builder()
                .requestId(chargebackId + "_compliance")
                .chargebackId(chargebackId)
                .reportingCategory(reportingCategory != null ? reportingCategory : "CHARGEBACK_COMPLIANCE")
                .complianceFlags(complianceFlags)
                .reportingDeadline(reportingDeadline)
                .priority(determineCompliancePriority(complianceFlags))
                .createdAt(LocalDateTime.now())
                .build();

            // Store compliance reporting request
            storeComplianceReportingRequest(request);

            // Update compliance metrics
            updateComplianceReportingMetrics(request);

            log.info("Compliance reporting triggered for chargeback: {} - Priority: {}", 
                chargebackId, request.getPriority());

        } catch (Exception e) {
            log.error("Failed to trigger compliance reporting for chargeback: {}", chargebackId, e);
        }
    }

    /**
     * Reports platform reserve for regulatory compliance
     * 
     * @param reserveRequestId Reserve request identifier
     * @param amount Reserve amount
     * @param currency Currency code
     * @param reserveType Type of reserve
     * @param reason Reserve reason
     * @param regulatoryRequired Whether regulatory required
     */
    public void reportPlatformReserve(
            String reserveRequestId,
            BigDecimal amount,
            String currency,
            PlatformReserveService.ReserveType reserveType,
            String reason,
            boolean regulatoryRequired) {

        if (!financialReportingEnabled) {
            log.debug("Financial reporting disabled, skipping platform reserve reporting");
            return;
        }

        try {
            log.info("Reporting platform reserve - Reserve: {} Amount: {} {} Type: {}", 
                reserveRequestId, amount, currency, reserveType);

            // Create platform reserve reporting entry
            PlatformReserveReportingEntry entry = PlatformReserveReportingEntry.builder()
                .entryId(reserveRequestId + "_reserve_report")
                .reserveRequestId(reserveRequestId)
                .amount(amount)
                .currency(currency)
                .reserveType(reserveType)
                .reason(reason)
                .regulatoryRequired(regulatoryRequired)
                .reportingPeriod(determineReportingPeriod(LocalDateTime.now()))
                .timestamp(LocalDateTime.now())
                .build();

            // Store reserve reporting entry
            storePlatformReserveReportingEntry(entry);

            // Update reserve reporting metrics
            updateReserveReportingMetrics(entry);

            log.info("Platform reserve reported: {} - Amount: {} {}", 
                reserveRequestId, amount, currency);

        } catch (Exception e) {
            log.error("Failed to report platform reserve: {}", reserveRequestId, e);
        }
    }

    /**
     * Triggers capital adequacy reporting for regulatory compliance
     * 
     * @param reserveRequestId Reserve request identifier
     * @param amount Reserve amount
     * @param currency Currency code
     * @param reserveType Type of reserve
     * @param reason Reserve reason
     */
    public void triggerCapitalAdequacyReporting(
            String reserveRequestId,
            BigDecimal amount,
            String currency,
            PlatformReserveService.ReserveType reserveType,
            String reason) {

        if (!regulatoryReportingEnabled) {
            log.debug("Regulatory reporting disabled, skipping capital adequacy reporting");
            return;
        }

        try {
            log.info("Triggering capital adequacy reporting - Reserve: {} Amount: {} {}", 
                reserveRequestId, amount, currency);

            // Create capital adequacy reporting request
            CapitalAdequacyReportingRequest request = CapitalAdequacyReportingRequest.builder()
                .requestId(reserveRequestId + "_capital_adequacy")
                .reserveRequestId(reserveRequestId)
                .amount(amount)
                .currency(currency)
                .reserveType(reserveType)
                .reason(reason)
                .reportingType(CapitalAdequacyReportingType.RESERVE_ALLOCATION)
                .priority(determineCapitalAdequacyPriority(amount, reserveType))
                .createdAt(LocalDateTime.now())
                .build();

            // Store capital adequacy reporting request
            storeCapitalAdequacyReportingRequest(request);

            // Update capital adequacy metrics
            updateCapitalAdequacyMetrics(request);

            log.info("Capital adequacy reporting triggered: {} - Priority: {}", 
                reserveRequestId, request.getPriority());

        } catch (Exception e) {
            log.error("Failed to trigger capital adequacy reporting: {}", reserveRequestId, e);
        }
    }

    /**
     * Determines reporting period for timestamp
     */
    private String determineReportingPeriod(LocalDateTime timestamp) {
        return timestamp.getYear() + "-" + String.format("%02d", timestamp.getMonthValue());
    }

    /**
     * Determines compliance priority based on flags
     */
    private CompliancePriority determineCompliancePriority(Set<String> complianceFlags) {
        if (complianceFlags == null || complianceFlags.isEmpty()) {
            return CompliancePriority.NORMAL;
        }

        if (complianceFlags.contains("REGULATORY_VIOLATION") || 
            complianceFlags.contains("AML_ALERT") ||
            complianceFlags.contains("SANCTIONS_VIOLATION")) {
            return CompliancePriority.CRITICAL;
        }

        if (complianceFlags.contains("SUSPICIOUS_ACTIVITY") ||
            complianceFlags.contains("HIGH_RISK_MERCHANT") ||
            complianceFlags.contains("FRAUD_ALERT")) {
            return CompliancePriority.HIGH;
        }

        return CompliancePriority.NORMAL;
    }

    /**
     * Determines capital adequacy priority
     */
    private CapitalAdequacyPriority determineCapitalAdequacyPriority(
            BigDecimal amount, PlatformReserveService.ReserveType reserveType) {
        
        if (amount.compareTo(new BigDecimal("100000")) > 0 || 
            reserveType == PlatformReserveService.ReserveType.REGULATORY ||
            reserveType == PlatformReserveService.ReserveType.EMERGENCY) {
            return CapitalAdequacyPriority.HIGH;
        }

        if (amount.compareTo(new BigDecimal("50000")) > 0 ||
            reserveType == PlatformReserveService.ReserveType.CHARGEBACK) {
            return CapitalAdequacyPriority.MEDIUM;
        }

        return CapitalAdequacyPriority.LOW;
    }

    /**
     * Stores financial reporting entry
     */
    private void storeFinancialReportingEntry(FinancialReportingEntry entry) {
        try {
            String entryKey = "financial:reporting:entry:" + entry.getEntryId();
            Map<String, String> entryData = Map.of(
                "entry_id", entry.getEntryId(),
                "entry_type", entry.getEntryType().toString(),
                "chargeback_id", entry.getChargebackId(),
                "merchant_id", entry.getMerchantId(),
                "amount", entry.getAmount().toString(),
                "currency", entry.getCurrency(),
                "card_network", entry.getCardNetwork(),
                "reason_code", entry.getReasonCode(),
                "fraud_related", String.valueOf(entry.isFraudRelated()),
                "reporting_period", entry.getReportingPeriod(),
                "timestamp", entry.getTimestamp().toString()
            );

            redisTemplate.opsForHash().putAll(entryKey, entryData);
            redisTemplate.expire(entryKey, Duration.ofDays(365));

        } catch (Exception e) {
            log.error("Failed to store financial reporting entry", e);
        }
    }

    /**
     * Updates chargeback loss metrics
     */
    private void updateChargebackLossMetrics(FinancialReportingEntry entry) {
        try {
            // Update total loss by period
            String lossKey = "financial:metrics:chargeback_loss:" + entry.getReportingPeriod() + ":" + entry.getCurrency();
            redisTemplate.opsForValue().increment(lossKey, entry.getAmount().doubleValue());
            redisTemplate.expire(lossKey, Duration.ofDays(400));

            // Update fraud-related loss if applicable
            if (entry.isFraudRelated()) {
                String fraudLossKey = "financial:metrics:fraud_loss:" + entry.getReportingPeriod() + ":" + entry.getCurrency();
                redisTemplate.opsForValue().increment(fraudLossKey, entry.getAmount().doubleValue());
                redisTemplate.expire(fraudLossKey, Duration.ofDays(400));
            }

            // Update loss by card network
            String networkLossKey = "financial:metrics:loss_by_network:" + entry.getCardNetwork() + ":" + entry.getCurrency();
            redisTemplate.opsForValue().increment(networkLossKey, entry.getAmount().doubleValue());
            redisTemplate.expire(networkLossKey, Duration.ofDays(90));

        } catch (Exception e) {
            log.error("Failed to update chargeback loss metrics", e);
        }
    }

    /**
     * Updates period totals
     */
    private void updatePeriodTotals(FinancialReportingEntry entry) {
        try {
            // Update monthly totals
            String monthlyKey = "financial:totals:monthly:" + entry.getReportingPeriod() + ":" + entry.getCurrency();
            redisTemplate.opsForValue().increment(monthlyKey, entry.getAmount().doubleValue());
            redisTemplate.expire(monthlyKey, Duration.ofDays(400));

            // Update yearly totals
            String yearlyKey = "financial:totals:yearly:" + entry.getTimestamp().getYear() + ":" + entry.getCurrency();
            redisTemplate.opsForValue().increment(yearlyKey, entry.getAmount().doubleValue());
            redisTemplate.expire(yearlyKey, Duration.ofDays(400));

        } catch (Exception e) {
            log.error("Failed to update period totals", e);
        }
    }

    /**
     * Stores compliance reporting request
     */
    private void storeComplianceReportingRequest(ComplianceReportingRequest request) {
        try {
            String requestKey = "compliance:reporting:request:" + request.getRequestId();
            Map<String, String> requestData = Map.of(
                "request_id", request.getRequestId(),
                "chargeback_id", request.getChargebackId(),
                "reporting_category", request.getReportingCategory(),
                "compliance_flags", String.join(",", request.getComplianceFlags()),
                "reporting_deadline", request.getReportingDeadline() != null ? 
                    request.getReportingDeadline().toString() : "",
                "priority", request.getPriority().toString(),
                "created_at", request.getCreatedAt().toString()
            );

            redisTemplate.opsForHash().putAll(requestKey, requestData);
            redisTemplate.expire(requestKey, Duration.ofDays(90));

        } catch (Exception e) {
            log.error("Failed to store compliance reporting request", e);
        }
    }

    /**
     * Updates compliance reporting metrics
     */
    private void updateComplianceReportingMetrics(ComplianceReportingRequest request) {
        try {
            // Update compliance reporting count
            String countKey = "compliance:metrics:reports:count:" + request.getPriority().toString();
            redisTemplate.opsForValue().increment(countKey);
            redisTemplate.expire(countKey, Duration.ofDays(30));

            // Update by category
            String categoryKey = "compliance:metrics:reports:category:" + request.getReportingCategory();
            redisTemplate.opsForValue().increment(categoryKey);
            redisTemplate.expire(categoryKey, Duration.ofDays(30));

        } catch (Exception e) {
            log.error("Failed to update compliance reporting metrics", e);
        }
    }

    /**
     * Stores platform reserve reporting entry
     */
    private void storePlatformReserveReportingEntry(PlatformReserveReportingEntry entry) {
        try {
            String entryKey = "financial:reporting:reserve:" + entry.getEntryId();
            Map<String, String> entryData = Map.of(
                "entry_id", entry.getEntryId(),
                "reserve_id", entry.getReserveRequestId(),
                "amount", entry.getAmount().toString(),
                "currency", entry.getCurrency(),
                "reserve_type", entry.getReserveType().toString(),
                "reason", entry.getReason(),
                "regulatory_required", String.valueOf(entry.isRegulatoryRequired()),
                "reporting_period", entry.getReportingPeriod(),
                "timestamp", entry.getTimestamp().toString()
            );

            redisTemplate.opsForHash().putAll(entryKey, entryData);
            redisTemplate.expire(entryKey, Duration.ofDays(365));

        } catch (Exception e) {
            log.error("Failed to store platform reserve reporting entry", e);
        }
    }

    /**
     * Updates reserve reporting metrics
     */
    private void updateReserveReportingMetrics(PlatformReserveReportingEntry entry) {
        try {
            // Update reserve amount by type
            String typeKey = "financial:metrics:reserves:" + entry.getReserveType().toString().toLowerCase() + ":" + entry.getCurrency();
            redisTemplate.opsForValue().increment(typeKey, entry.getAmount().doubleValue());
            redisTemplate.expire(typeKey, Duration.ofDays(90));

            // Update regulatory reserves if applicable
            if (entry.isRegulatoryRequired()) {
                String regulatoryKey = "financial:metrics:regulatory_reserves:" + entry.getCurrency();
                redisTemplate.opsForValue().increment(regulatoryKey, entry.getAmount().doubleValue());
                redisTemplate.expire(regulatoryKey, Duration.ofDays(90));
            }

        } catch (Exception e) {
            log.error("Failed to update reserve reporting metrics", e);
        }
    }

    /**
     * Stores capital adequacy reporting request
     */
    private void storeCapitalAdequacyReportingRequest(CapitalAdequacyReportingRequest request) {
        try {
            String requestKey = "regulatory:capital_adequacy:request:" + request.getRequestId();
            Map<String, String> requestData = Map.of(
                "request_id", request.getRequestId(),
                "reserve_id", request.getReserveRequestId(),
                "amount", request.getAmount().toString(),
                "currency", request.getCurrency(),
                "reserve_type", request.getReserveType().toString(),
                "reason", request.getReason(),
                "reporting_type", request.getReportingType().toString(),
                "priority", request.getPriority().toString(),
                "created_at", request.getCreatedAt().toString()
            );

            redisTemplate.opsForHash().putAll(requestKey, requestData);
            redisTemplate.expire(requestKey, Duration.ofDays(365));

        } catch (Exception e) {
            log.error("Failed to store capital adequacy reporting request", e);
        }
    }

    /**
     * Updates capital adequacy metrics
     */
    private void updateCapitalAdequacyMetrics(CapitalAdequacyReportingRequest request) {
        try {
            // Update capital adequacy reporting count
            String countKey = "regulatory:metrics:capital_adequacy:count:" + request.getPriority().toString();
            redisTemplate.opsForValue().increment(countKey);
            redisTemplate.expire(countKey, Duration.ofDays(30));

            // Update by reporting type
            String typeKey = "regulatory:metrics:capital_adequacy:type:" + request.getReportingType().toString();
            redisTemplate.opsForValue().increment(typeKey);
            redisTemplate.expire(typeKey, Duration.ofDays(30));

        } catch (Exception e) {
            log.error("Failed to update capital adequacy metrics", e);
        }
    }

    // Data structures

    @lombok.Data
    @lombok.Builder
    @lombok.AllArgsConstructor
    @lombok.NoArgsConstructor
    private static class FinancialReportingEntry {
        private String entryId;
        private FinancialEntryType entryType;
        private String chargebackId;
        private String merchantId;
        private BigDecimal amount;
        private String currency;
        private String cardNetwork;
        private String reasonCode;
        private boolean fraudRelated;
        private String reportingPeriod;
        private LocalDateTime timestamp;
    }

    @lombok.Data
    @lombok.Builder
    @lombok.AllArgsConstructor
    @lombok.NoArgsConstructor
    private static class ComplianceReportingRequest {
        private String requestId;
        private String chargebackId;
        private String reportingCategory;
        private Set<String> complianceFlags;
        private LocalDateTime reportingDeadline;
        private CompliancePriority priority;
        private LocalDateTime createdAt;
    }

    @lombok.Data
    @lombok.Builder
    @lombok.AllArgsConstructor
    @lombok.NoArgsConstructor
    private static class PlatformReserveReportingEntry {
        private String entryId;
        private String reserveRequestId;
        private BigDecimal amount;
        private String currency;
        private PlatformReserveService.ReserveType reserveType;
        private String reason;
        private boolean regulatoryRequired;
        private String reportingPeriod;
        private LocalDateTime timestamp;
    }

    @lombok.Data
    @lombok.Builder
    @lombok.AllArgsConstructor
    @lombok.NoArgsConstructor
    private static class CapitalAdequacyReportingRequest {
        private String requestId;
        private String reserveRequestId;
        private BigDecimal amount;
        private String currency;
        private PlatformReserveService.ReserveType reserveType;
        private String reason;
        private CapitalAdequacyReportingType reportingType;
        private CapitalAdequacyPriority priority;
        private LocalDateTime createdAt;
    }

    // Enums

    public enum FinancialEntryType {
        CHARGEBACK_LOSS,
        FRAUD_LOSS,
        OPERATIONAL_LOSS,
        RESERVE_ALLOCATION,
        REGULATORY_RESERVE
    }

    public enum CompliancePriority {
        NORMAL,
        HIGH,
        CRITICAL
    }

    public enum CapitalAdequacyReportingType {
        RESERVE_ALLOCATION,
        LIQUIDITY_ASSESSMENT,
        RISK_EXPOSURE_REPORT,
        REGULATORY_SUBMISSION
    }

    public enum CapitalAdequacyPriority {
        LOW,
        MEDIUM,
        HIGH
    }
}