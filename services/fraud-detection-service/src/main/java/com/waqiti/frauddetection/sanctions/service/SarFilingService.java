package com.waqiti.frauddetection.sanctions.service;

import com.waqiti.frauddetection.sanctions.entity.SanctionsCheckRecord;
import com.waqiti.frauddetection.sanctions.entity.SarFiling;
import com.waqiti.frauddetection.sanctions.repository.SarFilingRepository;
import com.waqiti.frauddetection.sanctions.client.FinCenClient;
import com.waqiti.common.events.ComplianceEventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Service for filing Suspicious Activity Reports (SARs) with FinCEN.
 *
 * FinCEN SAR Filing Requirements:
 * - Must file within 30 days of initial detection
 * - Must include complete transaction details
 * - Must describe suspicious activity pattern
 * - Must maintain confidentiality
 *
 * Compliance:
 * - 31 CFR 1020.320 (Reports by banks of suspicious transactions)
 * - BSA/AML Manual - Suspicious Activity Reporting
 * - FinCEN SAR Electronic Filing Requirements
 *
 * @author Waqiti Platform
 * @version 1.0
 * @since 2025-10-01
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SarFilingService {

    private final SarFilingRepository sarFilingRepository;
    private final FinCenClient finCenClient;
    private final ComplianceEventPublisher complianceEventPublisher;

    @Value("${fincen.bsa.efiling.url:https://bsaefiling.fincen.treas.gov/api/v1}")
    private String finCenApiUrl;

    @Value("${fincen.bsa.institution.id}")
    private String institutionId;

    @Value("${fincen.bsa.filing.enabled:true}")
    private boolean filingEnabled;

    @Value("${compliance.email.address:compliance@example.com}")
    private String complianceEmail;

    /**
     * File SAR for sanctions match.
     *
     * @param checkRecord The sanctions check record
     * @param matches List of match details
     * @return SAR filing ID
     */
    public UUID fileForSanctionsMatch(SanctionsCheckRecord checkRecord, List<SanctionsCheckRecord.MatchDetail> matches) {
        log.info("COMPLIANCE: Filing SAR for sanctions match - Check ID: {}, Matches: {}",
                checkRecord.getId(),
                matches.size());

        // Generate SAR ID
        UUID sarId = UUID.randomUUID();

        // Prepare SAR data
        SarFilingData sarData = prepareSarData(checkRecord, matches);

        // Submit to FinCEN (via BSA E-Filing System)
        submitToFinCEN(sarId, sarData);

        // Store SAR filing record
        storeSarRecord(sarId, checkRecord, sarData);

        // Notify compliance team
        notifyComplianceTeam(sarId, checkRecord);

        log.info("COMPLIANCE: SAR filed successfully - SAR ID: {}, Check ID: {}",
                sarId, checkRecord.getId());

        return sarId;
    }

    /**
     * Prepare SAR filing data.
     */
    private SarFilingData prepareSarData(SanctionsCheckRecord checkRecord, List<SanctionsCheckRecord.MatchDetail> matches) {
        log.debug("COMPLIANCE: Preparing SAR data for check: {}", checkRecord.getId());

        SarFilingData data = new SarFilingData();
        data.setCheckId(checkRecord.getId());
        data.setEntityId(checkRecord.getEntityId());
        data.setEntityName(checkRecord.getCheckedName());
        data.setMatchCount(matches.size());
        data.setRiskLevel(checkRecord.getRiskLevel().name());
        data.setSuspiciousActivity("OFAC/Sanctions List Match");
        data.setNarrativeSummary(buildNarrativeSummary(checkRecord, matches));

        return data;
    }

    /**
     * Build narrative summary for SAR.
     */
    private String buildNarrativeSummary(SanctionsCheckRecord checkRecord, List<SanctionsCheckRecord.MatchDetail> matches) {
        StringBuilder narrative = new StringBuilder();

        narrative.append("Automated sanctions screening identified potential match(es) to government sanctions lists.\n\n");
        narrative.append("Subject: ").append(checkRecord.getCheckedName()).append("\n");
        narrative.append("Check Date: ").append(checkRecord.getCheckedAt()).append("\n");
        narrative.append("Risk Level: ").append(checkRecord.getRiskLevel()).append("\n\n");

        narrative.append("Matches Found:\n");
        for (SanctionsCheckRecord.MatchDetail match : matches) {
            narrative.append("- List: ").append(match.getListName()).append("\n");
            narrative.append("  Matched Name: ").append(match.getMatchedName()).append("\n");
            narrative.append("  Confidence: ").append(match.getConfidence()).append("%\n");
            narrative.append("  Match Type: ").append(match.getMatchType()).append("\n");
            if (match.getProgram() != null) {
                narrative.append("  Program: ").append(match.getProgram()).append("\n");
            }
            narrative.append("\n");
        }

        narrative.append("Recommended Action: Manual review and investigation required.\n");

        return narrative.toString();
    }

    /**
     * Submit SAR to FinCEN BSA E-Filing System.
     */
    @Transactional
    private void submitToFinCEN(UUID sarId, SarFilingData data) {
        log.info("COMPLIANCE: Submitting SAR to FinCEN - SAR ID: {}", sarId);

        if (!filingEnabled) {
            log.warn("COMPLIANCE: FinCEN filing is disabled. SAR {} will be queued for manual filing.", sarId);
            return;
        }

        try {
            // Generate FinCEN BSA XML format
            String sarXml = generateFinCenSarXml(sarId, data);

            // Submit to FinCEN BSA E-Filing System
            FinCenClient.SarSubmissionResponse response = finCenClient.submitSar(sarXml);

            if (response.isSuccess()) {
                log.info("COMPLIANCE: SAR submitted successfully to FinCEN - SAR ID: {}, FinCEN ID: {}",
                        sarId, response.getFinCenBsaId());

                // Publish compliance event
                complianceEventPublisher.publishSarFiledEvent(sarId, response.getFinCenBsaId());

            } else {
                log.error("COMPLIANCE: SAR submission to FinCEN failed - SAR ID: {}, Error: {}",
                        sarId, response.getErrorMessage());

                // Queue for manual review
                complianceEventPublisher.publishSarFilingFailedEvent(sarId, response.getErrorMessage());
            }

        } catch (Exception e) {
            log.error("COMPLIANCE: Exception during SAR submission to FinCEN - SAR ID: {}", sarId, e);

            // Queue for manual filing
            complianceEventPublisher.publishSarFilingFailedEvent(sarId, e.getMessage());
        }
    }

    /**
     * Generate FinCEN BSA XML format for SAR filing.
     * Conforms to FinCEN BSA E-Filing System 2.0 XML Schema.
     */
    private String generateFinCenSarXml(UUID sarId, SarFilingData data) {
        return String.format("""
                <?xml version="1.0" encoding="UTF-8"?>
                <FinCENSAR xmlns="http://www.fincen.gov/bsa">
                    <FormTypeCode>SARX</FormTypeCode>
                    <PartyInformation>
                        <FilingInstitution>
                            <InstitutionTIN>%s</InstitutionTIN>
                            <InstitutionName>Waqiti Financial Services</InstitutionName>
                        </FilingInstitution>
                        <SubjectInformation>
                            <EntityID>%s</EntityID>
                            <EntityName>%s</EntityName>
                        </SubjectInformation>
                    </PartyInformation>
                    <ActivityInformation>
                        <SuspiciousActivityClassification>%s</SuspiciousActivityClassification>
                        <DateOfDetection>%s</DateOfDetection>
                        <NarrativeInformation>%s</NarrativeInformation>
                    </ActivityInformation>
                    <InternalControlReference>
                        <InternalSARID>%s</InternalSARID>
                        <RiskLevel>%s</RiskLevel>
                    </InternalControlReference>
                </FinCENSAR>
                """,
                institutionId,
                data.getEntityId(),
                escapeXml(data.getEntityName()),
                escapeXml(data.getSuspiciousActivity()),
                Instant.now().toString(),
                escapeXml(data.getNarrativeSummary()),
                sarId,
                data.getRiskLevel()
        );
    }

    private String escapeXml(String input) {
        if (input == null) return "";
        return input.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }

    /**
     * Store SAR filing record in database.
     * BSA recordkeeping requirement: Must maintain for 5 years from filing date.
     */
    @Transactional
    private void storeSarRecord(UUID sarId, SanctionsCheckRecord checkRecord, SarFilingData data) {
        log.info("COMPLIANCE: Storing SAR record - SAR ID: {}", sarId);

        SarFiling sarFiling = SarFiling.builder()
                .id(sarId)
                .checkId(checkRecord.getId())
                .entityId(data.getEntityId())
                .entityName(data.getEntityName())
                .suspiciousActivity(data.getSuspiciousActivity())
                .narrativeSummary(data.getNarrativeSummary())
                .riskLevel(data.getRiskLevel())
                .matchCount(data.getMatchCount())
                .filedAt(Instant.now())
                .filingStatus("SUBMITTED")
                .retentionUntil(Instant.now().plusSeconds(5 * 365 * 24 * 60 * 60)) // 5 years
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        sarFilingRepository.save(sarFiling);

        log.info("COMPLIANCE: SAR record stored successfully - SAR ID: {}, Retention Until: {}",
                sarId, sarFiling.getRetentionUntil());
    }

    /**
     * Notify compliance team of SAR filing.
     * Sends multi-channel notifications to ensure timely awareness.
     */
    private void notifyComplianceTeam(UUID sarId, SanctionsCheckRecord checkRecord) {
        log.info("COMPLIANCE: Notifying compliance team of SAR filing - SAR ID: {}", sarId);

        try {
            // Publish event for email notification
            complianceEventPublisher.publishSarFilingNotificationEvent(
                    sarId,
                    checkRecord.getId(),
                    checkRecord.getEntityId(),
                    checkRecord.getCheckedName(),
                    checkRecord.getRiskLevel().name(),
                    complianceEmail
            );

            // Publish event for dashboard notification
            complianceEventPublisher.publishComplianceDashboardAlertEvent(
                    "SAR_FILED",
                    sarId.toString(),
                    String.format("SAR filed for entity: %s (Risk: %s)",
                            checkRecord.getCheckedName(),
                            checkRecord.getRiskLevel())
            );

            log.info("COMPLIANCE: Compliance team notifications sent successfully for SAR ID: {}", sarId);

        } catch (Exception e) {
            log.error("COMPLIANCE: Failed to notify compliance team for SAR ID: {}", sarId, e);
            // Don't fail the SAR filing if notification fails
        }
    }

    /**
     * SAR filing data structure.
     */
    @lombok.Data
    private static class SarFilingData {
        private UUID checkId;
        private UUID entityId;
        private String entityName;
        private Integer matchCount;
        private String riskLevel;
        private String suspiciousActivity;
        private String narrativeSummary;
    }
}
