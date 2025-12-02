package com.waqiti.compliance.sanctions;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.common.error.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Suspicious Activity Report (SAR) Filing Service for FinCEN.
 *
 * Implements automated SAR filing as required by:
 * - Bank Secrecy Act (BSA)
 * - USA PATRIOT Act
 * - 31 CFR 1020.320 (BSA Reporting Requirements)
 * - FinCEN SAR Electronic Filing Requirements
 *
 * SAR Filing Triggers:
 * - Transactions >= $5,000 with suspected criminal activity
 * - Sanctions screening matches (high-risk)
 * - Structuring / smurfing patterns
 * - Wire transfers to high-risk countries
 * - Unusual transaction patterns
 * - Money laundering indicators
 *
 * Features:
 * - Automated SAR generation in FinCEN BSA E-Filing XML format
 * - Secure transmission to FinCEN
 * - 30-day filing timeline tracking
 * - Comprehensive audit logging
 * - Confidentiality protection (no customer notification)
 * - Continuing activity tracking
 *
 * @author Waqiti Compliance Team
 * @version 2.0.0
 */
@Slf4j
@Service
public class SarFilingService {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${fincen.api.url:https://bsaefiling.fincen.treas.gov/}")
    private String fincenApiUrl;

    @Value("${fincen.api.key:#{null}}")
    private String fincenApiKey;

    @Value("${fincen.institution.id}")
    private String institutionId;

    @Value("${fincen.institution.tin}")
    private String institutionTin;

    @Value("${fincen.institution.name}")
    private String institutionName;

    @Value("${sar.auto.filing.enabled:false}")
    private boolean autoFilingEnabled;

    // Track pending SARs
    private final Map<String, PendingSAR> pendingSARs = new ConcurrentHashMap<>();

    // SAR filing statistics
    private int sarsFiled = 0;
    private int sarsQueued = 0;

    public SarFilingService(RestTemplate restTemplate, ObjectMapper objectMapper) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * File Suspicious Activity Report (SAR) with FinCEN.
     *
     * @param customerId Customer ID
     * @param activityType Type of suspicious activity
     * @param riskScore Risk score (0-100)
     * @param narrative Detailed narrative of suspicious activity
     * @return SAR filing confirmation
     */
    @Async
    public CompletableFuture<SARFilingResult> fileSAR(
            String customerId,
            String activityType,
            int riskScore,
            String narrative) {

        try {
            log.warn("SAR Filing Initiated | Customer: {} | Activity: {} | Risk Score: {}",
                    customerId, activityType, riskScore);

            // Generate SAR ID
            String sarId = generateSARId();

            // Create SAR record
            SARRecord sarRecord = createSARRecord(
                    sarId,
                    customerId,
                    activityType,
                    riskScore,
                    narrative
            );

            // Validate SAR before filing
            validateSAR(sarRecord);

            if (autoFilingEnabled) {
                // File immediately with FinCEN
                return CompletableFuture.completedFuture(fileWithFinCEN(sarRecord));
            } else {
                // Queue for manual review
                return CompletableFuture.completedFuture(queueForReview(sarRecord));
            }

        } catch (Exception e) {
            log.error("SAR filing failed for customer: {}", customerId, e);
            return CompletableFuture.completedFuture(SARFilingResult.failed(e.getMessage()));
        }
    }

    /**
     * Create SAR record.
     */
    private SARRecord createSARRecord(
            String sarId,
            String customerId,
            String activityType,
            int riskScore,
            String narrative) {

        SARRecord record = new SARRecord();
        record.setSarId(sarId);
        record.setCustomerId(customerId);
        record.setActivityType(activityType);
        record.setRiskScore(riskScore);
        record.setNarrative(narrative);
        record.setFilingDate(LocalDateTime.now());
        record.setReportingInstitution(institutionName);
        record.setReportingInstitutionTIN(institutionTin);
        record.setStatus(SARStatus.DRAFT);

        // Add filing deadline (30 days from detection)
        record.setFilingDeadline(LocalDateTime.now().plusDays(30));

        return record;
    }

    /**
     * Validate SAR before filing.
     */
    private void validateSAR(SARRecord record) {
        if (record.getNarrative() == null || record.getNarrative().length() < 100) {
            throw BusinessException.badRequest(
                    "SAR narrative must be at least 100 characters (detailed explanation required)");
        }

        if (record.getActivityType() == null) {
            throw BusinessException.badRequest("SAR activity type is required");
        }

        if (record.getCustomerId() == null) {
            throw BusinessException.badRequest("Customer ID is required for SAR");
        }

        log.debug("SAR validation passed: {}", record.getSarId());
    }

    /**
     * File SAR with FinCEN electronically.
     */
    private SARFilingResult fileWithFinCEN(SARRecord record) {
        try {
            log.info("Filing SAR with FinCEN | SAR ID: {} | Customer: {}",
                    record.getSarId(), record.getCustomerId());

            // Generate FinCEN BSA E-Filing XML format
            String xmlPayload = generateFinCENXML(record);

            // Submit to FinCEN API
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_XML);
            headers.set("X-FinCEN-API-Key", fincenApiKey);
            headers.set("X-Institution-ID", institutionId);

            HttpEntity<String> request = new HttpEntity<>(xmlPayload, headers);

            ResponseEntity<String> response = restTemplate.postForEntity(
                    fincenApiUrl + "/sar/submit",
                    request,
                    String.class
            );

            if (response.getStatusCode() == HttpStatus.OK) {
                record.setStatus(SARStatus.FILED);
                record.setFinCENAcknowledgement(response.getBody());
                record.setFiledTimestamp(Instant.now());

                sarsFiled++;

                log.info("SAR successfully filed with FinCEN | SAR ID: {} | Acknowledgement: {}",
                        record.getSarId(),
                        record.getFinCENAcknowledgement());

                // Audit log (CRITICAL: Must be retained for 5 years per BSA)
                auditSARFiling(record, true, null);

                return SARFilingResult.success(record.getSarId(), record.getFinCENAcknowledgement());
            } else {
                throw new RuntimeException("FinCEN returned non-OK status: " + response.getStatusCode());
            }

        } catch (Exception e) {
            log.error("Failed to file SAR with FinCEN: {}", record.getSarId(), e);

            record.setStatus(SARStatus.FAILED);
            record.setErrorMessage(e.getMessage());

            // Audit failure
            auditSARFiling(record, false, e.getMessage());

            // Queue for retry/manual review
            queueForReview(record);

            throw BusinessException.serviceUnavailable(
                    "Failed to file SAR with FinCEN: " + e.getMessage());
        }
    }

    /**
     * Queue SAR for manual compliance review.
     */
    private SARFilingResult queueForReview(SARRecord record) {
        record.setStatus(SARStatus.QUEUED_FOR_REVIEW);

        PendingSAR pending = new PendingSAR();
        pending.setSarRecord(record);
        pending.setQueuedTimestamp(Instant.now());
        pending.setReviewDeadline(record.getFilingDeadline());

        pendingSARs.put(record.getSarId(), pending);
        sarsQueued++;

        log.info("SAR queued for manual review | SAR ID: {} | Deadline: {}",
                record.getSarId(),
                record.getFilingDeadline());

        // Audit queueing
        auditSARFiling(record, true, "Queued for manual compliance review");

        return SARFilingResult.queued(record.getSarId(), record.getFilingDeadline());
    }

    /**
     * Generate FinCEN BSA E-Filing XML format.
     */
    private String generateFinCENXML(SARRecord record) {
        StringBuilder xml = new StringBuilder();

        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        xml.append("<EFilingBatchXML>\n");
        xml.append("  <FormTypeCode>SARX</FormTypeCode>\n");
        xml.append("  <ActivityCount>1</ActivityCount>\n");
        xml.append("  <Activity>\n");
        xml.append("    <EFilingPriorDocumentNumber>").append(record.getSarId()).append("</EFilingPriorDocumentNumber>\n");
        xml.append("    <ActivityAssociation>\n");
        xml.append("      <CorrectsAmendsPriorReportIndicator>N</CorrectsAmendsPriorReportIndicator>\n");
        xml.append("    </ActivityAssociation>\n");

        // Filing institution information
        xml.append("    <FilingInstitution>\n");
        xml.append("      <PartyName>\n");
        xml.append("        <PartyNameTypeCode>L</PartyNameTypeCode>\n");
        xml.append("        <RawPartyFullName>").append(escapeXml(institutionName)).append("</RawPartyFullName>\n");
        xml.append("      </PartyName>\n");
        xml.append("      <PartyIdentification>\n");
        xml.append("        <PartyIdentificationNumberText>").append(institutionTin).append("</PartyIdentificationNumberText>\n");
        xml.append("        <PartyIdentificationTypeCode>2</PartyIdentificationTypeCode>\n");
        xml.append("      </PartyIdentification>\n");
        xml.append("    </FilingInstitution>\n");

        // Suspicious activity information
        xml.append("    <SuspiciousActivity>\n");
        xml.append("      <SuspiciousActivityClassification>\n");
        xml.append("        <SuspiciousActivityClassificationTypeCode>")
                .append(mapActivityTypeToFinCENCode(record.getActivityType()))
                .append("</SuspiciousActivityClassificationTypeCode>\n");
        xml.append("      </SuspiciousActivityClassification>\n");
        xml.append("    </SuspiciousActivity>\n");

        // Narrative
        xml.append("    <CyberEventIndicators>\n");
        xml.append("      <NarrativeText>").append(escapeXml(record.getNarrative())).append("</NarrativeText>\n");
        xml.append("    </CyberEventIndicators>\n");

        xml.append("  </Activity>\n");
        xml.append("</EFilingBatchXML>\n");

        return xml.toString();
    }

    /**
     * Map internal activity type to FinCEN classification code.
     */
    private String mapActivityTypeToFinCENCode(String activityType) {
        Map<String, String> mapping = new HashMap<>();
        mapping.put("SANCTIONS_MATCH", "SANCTIONS");
        mapping.put("STRUCTURING", "STR");
        mapping.put("MONEY_LAUNDERING", "ML");
        mapping.put("WIRE_FRAUD", "WIRE");
        mapping.put("TERRORIST_FINANCING", "TF");
        mapping.put("UNUSUAL_TRANSACTION", "OTHER");

        return mapping.getOrDefault(activityType, "OTHER");
    }

    /**
     * Escape XML special characters.
     */
    private String escapeXml(String text) {
        if (text == null) {
            return "";
        }

        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }

    /**
     * Generate unique SAR ID.
     */
    private String generateSARId() {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        return "SAR-" + institutionId + "-" + timestamp + "-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    /**
     * Audit SAR filing operation.
     *
     * CRITICAL: BSA requires 5-year retention of all SAR records and audit logs.
     */
    private void auditSARFiling(SARRecord record, boolean success, String details) {
        log.info("SAR_FILING_AUDIT | SAR ID: {} | Customer: {} | Activity: {} | Status: {} | Success: {} | Details: {}",
                record.getSarId(),
                record.getCustomerId(),
                record.getActivityType(),
                record.getStatus(),
                success,
                details);

        // In production, this would also write to dedicated audit database/log
        // with tamper-evident properties and 5-year retention
    }

    /**
     * Get pending SARs requiring review.
     */
    public List<PendingSAR> getPendingSARs() {
        return new ArrayList<>(pendingSARs.values());
    }

    /**
     * Get SAR filing statistics.
     */
    public SARStatistics getStatistics() {
        SARStatistics stats = new SARStatistics();
        stats.setSarsFiled(sarsFiled);
        stats.setSarsQueued(sarsQueued);
        stats.setPendingReview(pendingSARs.size());
        return stats;
    }

    // Inner classes

    public static class SARRecord {
        private String sarId;
        private String customerId;
        private String activityType;
        private int riskScore;
        private String narrative;
        private LocalDateTime filingDate;
        private LocalDateTime filingDeadline;
        private String reportingInstitution;
        private String reportingInstitutionTIN;
        private SARStatus status;
        private String finCENAcknowledgement;
        private Instant filedTimestamp;
        private String errorMessage;

        // Getters and setters
        public String getSarId() { return sarId; }
        public void setSarId(String sarId) { this.sarId = sarId; }
        public String getCustomerId() { return customerId; }
        public void setCustomerId(String customerId) { this.customerId = customerId; }
        public String getActivityType() { return activityType; }
        public void setActivityType(String activityType) { this.activityType = activityType; }
        public int getRiskScore() { return riskScore; }
        public void setRiskScore(int riskScore) { this.riskScore = riskScore; }
        public String getNarrative() { return narrative; }
        public void setNarrative(String narrative) { this.narrative = narrative; }
        public LocalDateTime getFilingDate() { return filingDate; }
        public void setFilingDate(LocalDateTime filingDate) { this.filingDate = filingDate; }
        public LocalDateTime getFilingDeadline() { return filingDeadline; }
        public void setFilingDeadline(LocalDateTime deadline) { this.filingDeadline = deadline; }
        public String getReportingInstitution() { return reportingInstitution; }
        public void setReportingInstitution(String institution) { this.reportingInstitution = institution; }
        public String getReportingInstitutionTIN() { return reportingInstitutionTIN; }
        public void setReportingInstitutionTIN(String tin) { this.reportingInstitutionTIN = tin; }
        public SARStatus getStatus() { return status; }
        public void setStatus(SARStatus status) { this.status = status; }
        public String getFinCENAcknowledgement() { return finCENAcknowledgement; }
        public void setFinCENAcknowledgement(String ack) { this.finCENAcknowledgement = ack; }
        public Instant getFiledTimestamp() { return filedTimestamp; }
        public void setFiledTimestamp(Instant timestamp) { this.filedTimestamp = timestamp; }
        public String getErrorMessage() { return errorMessage; }
        public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    }

    public static class PendingSAR {
        private SARRecord sarRecord;
        private Instant queuedTimestamp;
        private LocalDateTime reviewDeadline;

        // Getters and setters
        public SARRecord getSarRecord() { return sarRecord; }
        public void setSarRecord(SARRecord record) { this.sarRecord = record; }
        public Instant getQueuedTimestamp() { return queuedTimestamp; }
        public void setQueuedTimestamp(Instant timestamp) { this.queuedTimestamp = timestamp; }
        public LocalDateTime getReviewDeadline() { return reviewDeadline; }
        public void setReviewDeadline(LocalDateTime deadline) { this.reviewDeadline = deadline; }
    }

    public static class SARFilingResult {
        private boolean success;
        private String sarId;
        private String acknowledgement;
        private String errorMessage;
        private LocalDateTime filingDeadline;

        public static SARFilingResult success(String sarId, String acknowledgement) {
            SARFilingResult result = new SARFilingResult();
            result.setSuccess(true);
            result.setSarId(sarId);
            result.setAcknowledgement(acknowledgement);
            return result;
        }

        public static SARFilingResult queued(String sarId, LocalDateTime deadline) {
            SARFilingResult result = new SARFilingResult();
            result.setSuccess(true);
            result.setSarId(sarId);
            result.setFilingDeadline(deadline);
            return result;
        }

        public static SARFilingResult failed(String errorMessage) {
            SARFilingResult result = new SARFilingResult();
            result.setSuccess(false);
            result.setErrorMessage(errorMessage);
            return result;
        }

        // Getters and setters
        public boolean isSuccess() { return success; }
        public void setSuccess(boolean success) { this.success = success; }
        public String getSarId() { return sarId; }
        public void setSarId(String sarId) { this.sarId = sarId; }
        public String getAcknowledgement() { return acknowledgement; }
        public void setAcknowledgement(String ack) { this.acknowledgement = ack; }
        public String getErrorMessage() { return errorMessage; }
        public void setErrorMessage(String message) { this.errorMessage = message; }
        public LocalDateTime getFilingDeadline() { return filingDeadline; }
        public void setFilingDeadline(LocalDateTime deadline) { this.filingDeadline = deadline; }
    }

    public static class SARStatistics {
        private int sarsFiled;
        private int sarsQueued;
        private int pendingReview;

        // Getters and setters
        public int getSarsFiled() { return sarsFiled; }
        public void setSarsFiled(int count) { this.sarsFiled = count; }
        public int getSarsQueued() { return sarsQueued; }
        public void setSarsQueued(int count) { this.sarsQueued = count; }
        public int getPendingReview() { return pendingReview; }
        public void setPendingReview(int count) { this.pendingReview = count; }
    }

    public enum SARStatus {
        DRAFT,
        QUEUED_FOR_REVIEW,
        FILED,
        FAILED,
        AMENDED
    }
}
