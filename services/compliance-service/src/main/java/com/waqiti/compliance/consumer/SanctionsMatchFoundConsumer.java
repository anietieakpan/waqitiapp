package com.waqiti.compliance.consumer;

import com.waqiti.common.alerting.UnifiedAlertingService;
import com.waqiti.common.events.SanctionsMatchFoundEvent;
import com.waqiti.common.idempotency.IdempotencyService;
import com.waqiti.compliance.exception.ComplianceException;
import com.waqiti.compliance.model.ComplianceCase;
import com.waqiti.compliance.model.SanctionMatch;
import com.waqiti.compliance.service.ComplianceAlertService;
import com.waqiti.compliance.service.RegulatoryReportingService;
import com.waqiti.compliance.service.UserFreezeService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Production-grade consumer for sanctions match events.
 *
 * This consumer handles the MOST CRITICAL compliance scenario: detection of a potential
 * match against OFAC, EU, or UN sanctions lists.
 *
 * LEGAL REQUIREMENTS:
 * - Immediate account freeze (31 CFR § 501.603)
 * - OFAC reporting within 10 days (31 CFR § 501.603)
 * - No customer notification ("tipping off" is illegal - 31 CFR § 501.604)
 * - Suspicious Activity Report (SAR) filing (31 CFR § 1020.320)
 * - Asset freeze and blocking (31 CFR § 501.606)
 *
 * CRITICAL ACTIONS:
 * 1. IMMEDIATE freeze of ALL user accounts
 * 2. P0 compliance case creation
 * 3. URGENT compliance team alert (24/7)
 * 4. Senior management notification (CLO, CCO, CEO)
 * 5. Regulatory report preparation
 * 6. DO NOT notify customer (federal requirement)
 *
 * PENALTIES FOR NON-COMPLIANCE:
 * - Civil: $330,947 per violation (adjusted annually)
 * - Criminal: Up to $20 million and 30 years imprisonment
 * - License revocation
 *
 * @author Waqiti Platform Engineering
 * @since 1.0
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class SanctionsMatchFoundConsumer {

    private final IdempotencyService idempotencyService;
    private final UserFreezeService userFreezeService;
    private final ComplianceAlertService complianceAlertService;
    private final RegulatoryReportingService reportingService;
    private final UnifiedAlertingService alertingService;
    private final MeterRegistry meterRegistry;

    private static final String METRIC_PREFIX = "compliance.sanctions.consumer";

    /**
     * Handles sanctions match events.
     *
     * This is the HIGHEST PRIORITY event in the entire platform.
     * Failure to process correctly can result in criminal prosecution.
     *
     * Idempotency Key: "sanctions-match:{userId}:{timestamp}"
     * TTL: PERMANENT (regulatory requirement - 5+ years)
     *
     * @param event Sanctions match detection event
     * @param acknowledgment Kafka acknowledgment
     */
    @KafkaListener(
        topics = "${kafka.topics.sanctions-match-found:sanctions-match-found}",
        groupId = "${kafka.consumer-groups.sanctions-match:sanctions-match-group}",
        containerFactory = "kafkaListenerContainerFactory",
        concurrency = "${kafka.consumers.sanctions-match.concurrency:1}" // SINGLE THREAD - too critical for parallelism
    )
    @Transactional(isolation = Isolation.SERIALIZABLE, timeout = 120) // Highest isolation level
    public void handleSanctionsMatch(
            @Payload SanctionsMatchFoundEvent event,
            Acknowledgment acknowledgment) {

        Timer.Sample timer = Timer.start(meterRegistry);
        String idempotencyKey = String.format("sanctions-match:%s:%s",
                event.getUserId(), event.getDetectedAt());
        UUID operationId = UUID.randomUUID();

        // DO NOT log customer name or PII - compliance requirement
        log.error("🚨🚨🚨 CRITICAL: SANCTIONS MATCH DETECTED - userId={}, matchCount={}, " +
                "highestScore={}, sources={}, IMMEDIATE ACTION REQUIRED",
                maskUserId(event.getUserId()),
                event.getMatches().size(),
                getHighestMatchScore(event.getMatches()),
                getSanctionsSources(event.getMatches()),
                operationId);

        try {
            // Check idempotency (critical - we can't double-freeze)
            if (!idempotencyService.startOperation(idempotencyKey, operationId, Duration.ofDays(365 * 10))) {
                log.warn("⚠️ DUPLICATE - Sanctions match already processed: userId={}",
                        maskUserId(event.getUserId()));
                recordMetric("duplicate", event);
                acknowledgment.acknowledge();
                return;
            }

            // Validate event
            validateEvent(event);

            // Process the sanctions match
            processSanctionsMatch(event, operationId);

            // Mark operation complete
            idempotencyService.completeOperation(
                idempotencyKey,
                operationId,
                Map.of(
                    "status", "PROCESSED",
                    "userId", event.getUserId().toString(),
                    "matchCount", String.valueOf(event.getMatches().size()),
                    "frozen", "true",
                    "complianceCaseCreated", "true",
                    "sarInitiated", "true"
                ),
                Duration.ofDays(365 * 10) // 10-year retention
            );

            acknowledgment.acknowledge();
            recordMetric("success", event);
            timer.stop(meterRegistry.timer(METRIC_PREFIX + ".processing.time", "result", "success"));

            log.info("✅ Sanctions match processed and accounts frozen: userId={}, operationId={}",
                    maskUserId(event.getUserId()), operationId);

        } catch (Exception e) {
            log.error("❌ CRITICAL: Failed to process sanctions match (REGULATORY VIOLATION RISK): " +
                    "userId={}, operationId={}, error={}",
                    maskUserId(event.getUserId()), operationId, e.getMessage(), e);

            idempotencyService.failOperation(idempotencyKey, operationId, e.getMessage());
            recordMetric("failure", event);
            timer.stop(meterRegistry.timer(METRIC_PREFIX + ".processing.time", "result", "failure"));

            // CRITICAL: Still acknowledge to prevent infinite retry loop
            // Manual escalation will be triggered by the failure alert
            acknowledgment.acknowledge();

            // Send URGENT alert about processing failure
            alertingService.sendPagerDutyAlert(
                "critical",
                "SANCTIONS_MATCH_PROCESSING_FAILED",
                "CRITICAL: Failed to process sanctions match - MANUAL INTERVENTION REQUIRED IMMEDIATELY",
                Map.of(
                    "userId", maskUserId(event.getUserId()),
                    "operationId", operationId.toString(),
                    "error", e.getMessage()
                )
            );
        }
    }

    /**
     * Core processing logic for sanctions matches.
     *
     * This orchestrates the legally-mandated response to sanctions detection:
     * 1. IMMEDIATE account freeze (all accounts, all products)
     * 2. Create P0 compliance case
     * 3. Alert compliance team (CCO, CLO)
     * 4. Alert senior management (CEO, CFO)
     * 5. Initiate SAR filing process
     * 6. Prepare OFAC/EU/UN report
     * 7. Block all pending transactions
     * 8. DO NOT notify customer
     */
    private void processSanctionsMatch(SanctionsMatchFoundEvent event, UUID operationId) {

        double highestScore = getHighestMatchScore(event.getMatches());
        String matchLevel = getMatchLevel(highestScore);
        List<String> sources = getSanctionsSources(event.getMatches());

        log.info("Processing sanctions match: userId={}, level={}, score={}, sources={}, operationId={}",
                maskUserId(event.getUserId()), matchLevel, highestScore, sources, operationId);

        // STEP 1: IMMEDIATE FREEZE OF ALL ACCOUNTS
        log.info("Step 1/8: FREEZING ALL ACCOUNTS (federal requirement): userId={}",
                maskUserId(event.getUserId()));

        String freezeReason = String.format(
            "Account frozen per 31 CFR § 501.603 - Sanctions screening match detected. " +
            "Match level: %s, Score: %.2f, Sources: %s. " +
            "DO NOT DISCUSS WITH CUSTOMER - 31 CFR § 501.604 prohibition on tipping off.",
            matchLevel,
            highestScore,
            String.join(", ", sources)
        );

        UUID freezeId = userFreezeService.freezeAllAccounts(
            event.getUserId(),
            "SANCTIONS_MATCH",
            freezeReason,
            Duration.ofDays(365 * 10), // Indefinite freeze
            true // blockPendingTransactions
        );

        log.info("✅ All accounts frozen: userId={}, freezeId={}", maskUserId(event.getUserId()), freezeId);

        // STEP 2: CREATE P0 COMPLIANCE CASE
        log.info("Step 2/8: Creating P0 compliance case: userId={}", maskUserId(event.getUserId()));

        ComplianceCase complianceCase = complianceAlertService.createP0Case(
            "SANCTIONS_MATCH",
            event.getUserId(),
            event.getMatches(),
            Map.of(
                "matchLevel", matchLevel,
                "highestScore", String.valueOf(highestScore),
                "sources", String.join(", ", sources),
                "freezeId", freezeId.toString(),
                "operationId", operationId.toString(),
                "detectedAt", event.getDetectedAt().toString()
            )
        );

        log.info("✅ P0 compliance case created: caseId={}, userId={}",
                complianceCase.getId(), maskUserId(event.getUserId()));

        // STEP 3: SEND CRITICAL ALERTS TO COMPLIANCE TEAM
        log.info("Step 3/8: Alerting compliance team (CCO, CLO): userId={}", maskUserId(event.getUserId()));

        Map<String, Object> alertContext = Map.of(
            "userId", maskUserId(event.getUserId()),
            "caseId", complianceCase.getId(),
            "freezeId", freezeId.toString(),
            "matchLevel", matchLevel,
            "highestScore", String.valueOf(highestScore),
            "matchCount", String.valueOf(event.getMatches().size()),
            "sources", String.join(", ", sources),
            "operationId", operationId.toString()
        );

        // PagerDuty - page compliance on-call
        alertingService.sendPagerDutyAlert(
            "critical",
            "SANCTIONS_MATCH_DETECTED",
            String.format("SANCTIONS MATCH - User: %s | Level: %s | Score: %.2f | Case: %s",
                maskUserId(event.getUserId()), matchLevel, highestScore, complianceCase.getId()),
            alertContext
        );

        // Slack - compliance channel
        alertingService.sendSlackAlert(
            "compliance",
            "🚨🚨🚨 SANCTIONS MATCH DETECTED",
            buildComplianceAlert(event, complianceCase, freezeId, matchLevel, highestScore),
            alertContext
        );

        log.info("✅ Compliance team alerted: userId={}", maskUserId(event.getUserId()));

        // STEP 4: ALERT SENIOR MANAGEMENT
        log.info("Step 4/8: Alerting senior management (CEO, CFO, CLO): userId={}",
                maskUserId(event.getUserId()));

        alertingService.sendExecutiveAlert(
            "SANCTIONS_MATCH",
            buildExecutiveAlert(event, complianceCase, matchLevel, highestScore),
            alertContext,
            List.of("CEO", "CFO", "CLO", "CCO") // Chief Compliance Officer, Chief Legal Officer
        );

        log.info("✅ Senior management alerted: userId={}", maskUserId(event.getUserId()));

        // STEP 5: INITIATE SAR FILING
        log.info("Step 5/8: Initiating SAR (Suspicious Activity Report) filing: userId={}",
                maskUserId(event.getUserId()));

        String sarId = reportingService.initiateSARFiling(
            event.getUserId(),
            "SANCTIONS_MATCH",
            "Potential match against OFAC/EU/UN sanctions lists detected",
            event.getMatches(),
            complianceCase.getId()
        );

        log.info("✅ SAR filing initiated: sarId={}, userId={}", sarId, maskUserId(event.getUserId()));

        // STEP 6: PREPARE REGULATORY REPORTS
        log.info("Step 6/8: Preparing OFAC/EU/UN reports: userId={}", maskUserId(event.getUserId()));

        // OFAC report (if OFAC match)
        if (sources.contains("OFAC")) {
            String ofacReportId = reportingService.prepareOFACReport(
                event.getUserId(),
                event.getMatches().stream()
                    .filter(m -> "OFAC".equals(m.getSource()))
                    .collect(Collectors.toList()),
                complianceCase.getId()
            );
            log.info("✅ OFAC report prepared: reportId={}", ofacReportId);
        }

        // EU report (if EU match)
        if (sources.contains("EU")) {
            String euReportId = reportingService.prepareEUReport(
                event.getUserId(),
                event.getMatches().stream()
                    .filter(m -> "EU".equals(m.getSource()))
                    .collect(Collectors.toList()),
                complianceCase.getId()
            );
            log.info("✅ EU report prepared: reportId={}", euReportId);
        }

        // UN report (if UN match)
        if (sources.contains("UN")) {
            String unReportId = reportingService.prepareUNReport(
                event.getUserId(),
                event.getMatches().stream()
                    .filter(m -> "UN".equals(m.getSource()))
                    .collect(Collectors.toList()),
                complianceCase.getId()
            );
            log.info("✅ UN report prepared: reportId={}", unReportId);
        }

        // STEP 7: BLOCK ALL PENDING TRANSACTIONS
        log.info("Step 7/8: Blocking all pending transactions: userId={}", maskUserId(event.getUserId()));

        int blockedCount = userFreezeService.blockPendingTransactions(
            event.getUserId(),
            "SANCTIONS_MATCH",
            complianceCase.getId()
        );

        log.info("✅ Pending transactions blocked: count={}, userId={}", blockedCount,
                maskUserId(event.getUserId()));

        // STEP 8: CREATE AUDIT TRAIL (DO NOT NOTIFY CUSTOMER)
        log.info("Step 8/8: Creating compliance audit trail: userId={}", maskUserId(event.getUserId()));

        complianceAlertService.createAuditTrail(
            event.getUserId(),
            "SANCTIONS_MATCH_PROCESSED",
            Map.of(
                "caseId", complianceCase.getId(),
                "freezeId", freezeId.toString(),
                "sarId", sarId,
                "matchLevel", matchLevel,
                "highestScore", String.valueOf(highestScore),
                "matchCount", String.valueOf(event.getMatches().size()),
                "sources", String.join(", ", sources),
                "blockedTransactions", String.valueOf(blockedCount),
                "operationId", operationId.toString(),
                "legalBasis", "31 CFR § 501.603, 31 CFR § 1020.320"
            )
        );

        // CRITICAL: DO NOT send customer notification
        // 31 CFR § 501.604 prohibits "tipping off" sanctioned persons
        log.info("⚠️ Customer notification SKIPPED per 31 CFR § 501.604 (tipping off prohibition)");

        log.info("✅ All sanctions match processing steps completed: userId={}, caseId={}, operationId={}",
                maskUserId(event.getUserId()), complianceCase.getId(), operationId);
    }

    /**
     * Builds detailed alert for compliance team.
     */
    private String buildComplianceAlert(
            SanctionsMatchFoundEvent event,
            ComplianceCase complianceCase,
            UUID freezeId,
            String matchLevel,
            double highestScore) {

        return String.format("""
            *SANCTIONS MATCH DETECTED* 🚨🚨🚨

            *IMMEDIATE ACTION REQUIRED - FEDERAL COMPLIANCE*

            *Case ID:* `%s`
            *User ID:* `%s` (MASKED)
            *Match Level:* *%s*
            *Highest Score:* `%.2f`

            *Match Details:*
            • Total Matches: `%d`
            • Sources: `%s`
            • Detection Time: `%s`

            *Actions Taken:*
            ✅ ALL accounts frozen (Freeze ID: `%s`)
            ✅ P0 compliance case created
            ✅ SAR filing initiated
            ✅ Regulatory reports prepared
            ✅ Pending transactions blocked
            ✅ Senior management notified

            *LEGAL REQUIREMENTS:*
            • OFAC report due within 10 days (31 CFR § 501.603)
            • SAR filing required (31 CFR § 1020.320)
            • Asset blocking mandatory (31 CFR § 501.606)
            • **DO NOT contact customer** (31 CFR § 501.604)

            *Next Steps:*
            1. Review case immediately
            2. Verify match accuracy
            3. Complete regulatory filings
            4. Coordinate with legal counsel
            5. Document all actions

            *Penalties for Non-Compliance:*
            • Civil: $330,947 per violation
            • Criminal: Up to $20M + 30 years
            • License revocation

            *Priority:* P0 - CRITICAL
            *Assigned To:* Compliance Team
            *Escalation:* CCO, CLO, CEO
            """,
            complianceCase.getId(),
            maskUserId(event.getUserId()),
            matchLevel,
            highestScore,
            event.getMatches().size(),
            getSanctionsSources(event.getMatches()).stream().collect(Collectors.joining(", ")),
            event.getDetectedAt(),
            freezeId
        );
    }

    /**
     * Builds executive alert message.
     */
    private String buildExecutiveAlert(
            SanctionsMatchFoundEvent event,
            ComplianceCase complianceCase,
            String matchLevel,
            double highestScore) {

        return String.format("""
            SANCTIONS MATCH ALERT

            A potential sanctions match has been detected and all required actions have been taken.

            Case ID: %s
            Match Level: %s (Score: %.2f)
            Matches: %d
            Sources: %s

            Actions Completed:
            • All accounts frozen
            • SAR filing initiated
            • Regulatory reports prepared
            • Compliance team engaged

            No customer action required at this time.
            Compliance team will provide updates.

            Legal Basis: 31 CFR § 501.603, 31 CFR § 1020.320
            """,
            complianceCase.getId(),
            matchLevel,
            highestScore,
            event.getMatches().size(),
            String.join(", ", getSanctionsSources(event.getMatches()))
        );
    }

    /**
     * Validates the sanctions match event.
     */
    private void validateEvent(SanctionsMatchFoundEvent event) {
        if (event.getUserId() == null) {
            throw new IllegalArgumentException("User ID cannot be null");
        }
        if (event.getMatches() == null || event.getMatches().isEmpty()) {
            throw new IllegalArgumentException("Matches list cannot be null or empty");
        }
        if (event.getDetectedAt() == null) {
            throw new IllegalArgumentException("Detection timestamp cannot be null");
        }
    }

    /**
     * Gets the highest match score from all matches.
     */
    private double getHighestMatchScore(List<SanctionMatch> matches) {
        return matches.stream()
            .mapToDouble(SanctionMatch::getMatchScore)
            .max()
            .orElse(0.0);
    }

    /**
     * Gets unique sanctions sources.
     */
    private List<String> getSanctionsSources(List<SanctionMatch> matches) {
        return matches.stream()
            .map(SanctionMatch::getSource)
            .distinct()
            .sorted()
            .collect(Collectors.toList());
    }

    /**
     * Determines match level based on score.
     */
    private String getMatchLevel(double score) {
        if (score >= 1.0) return "EXACT";
        if (score >= 0.95) return "CRITICAL";
        if (score >= 0.90) return "HIGH";
        if (score >= 0.85) return "MEDIUM";
        return "LOW";
    }

    /**
     * Masks user ID for privacy (log only partial ID).
     */
    private String maskUserId(UUID userId) {
        String id = userId.toString();
        return id.substring(0, 8) + "****";
    }

    /**
     * Records Prometheus metrics.
     */
    private void recordMetric(String result, SanctionsMatchFoundEvent event) {
        Counter.builder(METRIC_PREFIX + ".processed")
            .tag("result", result)
            .tag("matchLevel", getMatchLevel(getHighestMatchScore(event.getMatches())))
            .tag("sources", String.join(",", getSanctionsSources(event.getMatches())))
            .description("Sanctions match events processed")
            .register(meterRegistry)
            .increment();
    }
}
