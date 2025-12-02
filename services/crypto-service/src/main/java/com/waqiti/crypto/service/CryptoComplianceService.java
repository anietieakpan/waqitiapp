package com.waqiti.crypto.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.common.exception.RegulatoryException;
import com.waqiti.common.model.alert.CryptoComplianceRecoveryResult;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Production-grade crypto compliance service for AML, KYC, and sanctions screening.
 * Handles regulatory compliance verification for cryptocurrency transactions.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CryptoComplianceService {

    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;
    private final CryptoTransactionService cryptoTransactionService;

    private final Counter complianceChecksCounter;
    private final Counter compliancePassedCounter;
    private final Counter complianceFailedCounter;
    private final Counter sanctionsHitsCounter;
    private final Timer complianceCheckTimer;

    public CryptoComplianceService(ObjectMapper objectMapper,
                                   MeterRegistry meterRegistry,
                                   CryptoTransactionService cryptoTransactionService) {
        this.objectMapper = objectMapper;
        this.meterRegistry = meterRegistry;
        this.cryptoTransactionService = cryptoTransactionService;

        this.complianceChecksCounter = Counter.builder("crypto_compliance_checks_total")
                .description("Total crypto compliance checks performed")
                .register(meterRegistry);
        this.compliancePassedCounter = Counter.builder("crypto_compliance_passed_total")
                .description("Total crypto compliance checks passed")
                .register(meterRegistry);
        this.complianceFailedCounter = Counter.builder("crypto_compliance_failed_total")
                .description("Total crypto compliance checks failed")
                .register(meterRegistry);
        this.sanctionsHitsCounter = Counter.builder("crypto_sanctions_hits_total")
                .description("Total sanctions screening hits detected")
                .register(meterRegistry);
        this.complianceCheckTimer = Timer.builder("crypto_compliance_check_duration")
                .description("Duration of crypto compliance checks")
                .register(meterRegistry);
    }

    /**
     * Process crypto compliance DLQ event with full regulatory validation
     */
    @Transactional
    public CryptoComplianceRecoveryResult processCryptoComplianceCompletedDlq(
            String complianceData,
            String messageKey,
            String correlationId,
            String transactionId,
            String cryptoAsset,
            String complianceType,
            Instant timestamp) {

        Timer.Sample sample = Timer.start(meterRegistry);
        complianceChecksCounter.increment();

        log.info("Processing crypto compliance DLQ: transactionId={}, asset={}, type={}, correlationId={}",
                transactionId, cryptoAsset, complianceType, correlationId);

        try {
            // Parse compliance data
            JsonNode complianceNode = objectMapper.readTree(complianceData);

            // Extract compliance details
            String customerId = extractField(complianceNode, "customerId", "UNKNOWN");
            String walletAddress = extractField(complianceNode, "walletAddress", "");
            BigDecimal amount = extractAmount(complianceNode);
            String blockchain = extractField(complianceNode, "blockchainNetwork", "UNKNOWN");

            // Perform compliance checks
            boolean amlPassed = checkAmlCompliance(complianceNode, transactionId);
            boolean kycPassed = checkKycCompliance(complianceNode, customerId);
            boolean sanctionsPassed = checkSanctionsScreening(complianceNode, walletAddress);

            // Determine overall compliance status
            boolean compliancePassed = amlPassed && kycPassed && sanctionsPassed;

            // Build violation flags
            List<String> violationFlags = new ArrayList<>();
            if (!amlPassed) violationFlags.add("AML_FAILURE");
            if (!kycPassed) violationFlags.add("KYC_FAILURE");
            if (!sanctionsPassed) {
                violationFlags.add("SANCTIONS_HIT");
                sanctionsHitsCounter.increment();
            }

            // Determine risk score
            String riskScore = calculateRiskScore(amount, violationFlags, complianceNode);

            // Check if manual review required
            boolean requiresManualReview = determineManualReviewRequired(
                    amount, riskScore, violationFlags, complianceNode);

            // Build recovery result
            CryptoComplianceRecoveryResult result = CryptoComplianceRecoveryResult.builder()
                    .eventId(messageKey)
                    .correlationId(correlationId)
                    .transactionId(transactionId)
                    .walletAddress(walletAddress)
                    .cryptoCurrency(cryptoAsset)
                    .amount(amount)
                    .complianceCheckType(complianceType)
                    .compliancePassed(compliancePassed)
                    .violationFlags(violationFlags)
                    .riskScore(riskScore)
                    .requiresManualReview(requiresManualReview)
                    .transactionBlocked(!compliancePassed)
                    .checkCompletedTimestamp(Instant.now())
                    .blockchainNetwork(blockchain)
                    .kycStatus(kycPassed ? "VERIFIED" : "FAILED")
                    .sanctionScreenPassed(sanctionsPassed)
                    .recovered(true)
                    .processingStartTime(timestamp)
                    .processingEndTime(Instant.now())
                    .build();

            // Update metrics
            if (compliancePassed) {
                compliancePassedCounter.increment();
            } else {
                complianceFailedCounter.increment();
            }

            sample.stop(complianceCheckTimer);

            log.info("Crypto compliance check completed: transactionId={}, passed={}, violations={}, correlationId={}",
                    transactionId, compliancePassed, violationFlags, correlationId);

            return result;

        } catch (Exception e) {
            log.error("Failed to process crypto compliance DLQ: transactionId={}, correlationId={}",
                    transactionId, correlationId, e);

            sample.stop(complianceCheckTimer);

            return CryptoComplianceRecoveryResult.builder()
                    .eventId(messageKey)
                    .correlationId(correlationId)
                    .transactionId(transactionId)
                    .cryptoCurrency(cryptoAsset)
                    .complianceCheckType(complianceType)
                    .recovered(false)
                    .failureReason(e.getMessage())
                    .processingStartTime(timestamp)
                    .processingEndTime(Instant.now())
                    .build();
        }
    }

    /**
     * Update compliance status for a transaction
     */
    @Transactional
    public void updateComplianceStatus(String transactionId, String status,
                                       String details, String correlationId) {
        log.info("Updating compliance status: transactionId={}, status={}, correlationId={}",
                transactionId, status, correlationId);

        // Implementation would update compliance record in database
        // For now, log the status update
        Counter.builder("crypto_compliance_status_updates_total")
                .tag("status", status)
                .register(meterRegistry)
                .increment();
    }

    // Helper methods

    private boolean checkAmlCompliance(JsonNode data, String transactionId) {
        try {
            if (!data.has("amlVerification")) {
                log.warn("AML verification data missing for transaction: {}", transactionId);
                return false;
            }

            JsonNode aml = data.get("amlVerification");
            boolean passed = aml.has("passed") && aml.get("passed").asBoolean();

            // Check source of funds
            if (!data.has("sourceOfFunds")) {
                log.warn("Source of funds missing for transaction: {}", transactionId);
                return false;
            }

            return passed;

        } catch (Exception e) {
            log.error("AML compliance check failed for transaction: {}", transactionId, e);
            return false;
        }
    }

    private boolean checkKycCompliance(JsonNode data, String customerId) {
        try {
            if (!data.has("kycVerification")) {
                log.warn("KYC verification data missing for customer: {}", customerId);
                return false;
            }

            JsonNode kyc = data.get("kycVerification");
            boolean verified = kyc.has("verified") && kyc.get("verified").asBoolean();

            // Check KYC level
            if (kyc.has("kycLevel")) {
                String level = kyc.get("kycLevel").asText();
                // Require at least level 2 for crypto transactions
                return verified && (level.equals("2") || level.equals("3"));
            }

            return verified;

        } catch (Exception e) {
            log.error("KYC compliance check failed for customer: {}", customerId, e);
            return false;
        }
    }

    private boolean checkSanctionsScreening(JsonNode data, String walletAddress) {
        try {
            if (!data.has("sanctionsScreening")) {
                log.error("Sanctions screening data missing for wallet: {}", walletAddress);
                throw new RegulatoryException("Sanctions screening required for all crypto transactions");
            }

            JsonNode sanctions = data.get("sanctionsScreening");
            boolean cleared = sanctions.has("cleared") && sanctions.get("cleared").asBoolean();

            if (!cleared && sanctions.has("hits")) {
                int hits = sanctions.get("hits").asInt();
                if (hits > 0) {
                    log.error("SANCTIONS HIT detected: wallet={}, hits={}", walletAddress, hits);
                    return false;
                }
            }

            return cleared;

        } catch (Exception e) {
            log.error("Sanctions screening check failed for wallet: {}", walletAddress, e);
            return false;
        }
    }

    private String calculateRiskScore(BigDecimal amount, List<String> violations, JsonNode data) {
        // High risk if any violations
        if (!violations.isEmpty()) {
            return "HIGH";
        }

        // High risk for large transactions (>$10k)
        if (amount != null && amount.compareTo(BigDecimal.valueOf(10000)) > 0) {
            return "MEDIUM";
        }

        // Check for high-risk jurisdictions
        if (data.has("jurisdiction")) {
            String jurisdiction = data.get("jurisdiction").asText();
            List<String> highRiskCountries = List.of("KP", "IR", "SY"); // North Korea, Iran, Syria
            if (highRiskCountries.contains(jurisdiction)) {
                return "HIGH";
            }
        }

        return "LOW";
    }

    private boolean determineManualReviewRequired(BigDecimal amount, String riskScore,
                                                  List<String> violations, JsonNode data) {
        // Always review if there are violations
        if (!violations.isEmpty()) {
            return true;
        }

        // Review high-risk transactions
        if ("HIGH".equals(riskScore) || "CRITICAL".equals(riskScore)) {
            return true;
        }

        // Review large transactions (>$50k)
        if (amount != null && amount.compareTo(BigDecimal.valueOf(50000)) > 0) {
            return true;
        }

        // Review if politically exposed person (PEP)
        if (data.has("pepStatus") && data.get("pepStatus").asBoolean()) {
            return true;
        }

        return false;
    }

    private String extractField(JsonNode node, String fieldName, String defaultValue) {
        return node.has(fieldName) ? node.get(fieldName).asText() : defaultValue;
    }

    private BigDecimal extractAmount(JsonNode node) {
        try {
            if (node.has("transactionAmount")) {
                return new BigDecimal(node.get("transactionAmount").asText());
            }
            if (node.has("amount")) {
                return new BigDecimal(node.get("amount").asText());
            }
            return BigDecimal.ZERO;
        } catch (Exception e) {
            log.warn("Failed to extract transaction amount", e);
            return BigDecimal.ZERO;
        }
    }
}
