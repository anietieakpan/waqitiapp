package com.waqiti.wallet.service;

import com.waqiti.wallet.domain.RegulatoryIncident;
import com.waqiti.wallet.repository.RegulatoryIncidentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Regulatory Incident Service
 *
 * Manages regulatory incidents requiring reporting to government authorities
 * such as FinCEN, IRS, OFAC, SEC, and other financial regulators.
 *
 * REGULATORY REQUIREMENTS:
 * - FinCEN SAR filing failures: Must be documented and reported
 * - OFAC sanctions violations: Must be reported within 10 days
 * - IRS Form 8300 failures: Must be corrected and refiled
 * - Account freeze failures: May require regulatory explanation
 *
 * INCIDENT LIFECYCLE:
 * 1. Creation: Incident detected and logged
 * 2. Assessment: Severity and regulatory impact determined
 * 3. Reporting Decision: Determine if regulatory notification required
 * 4. Filing: Submit required regulatory reports
 * 5. Resolution: Document actions taken and outcomes
 * 6. Archival: Maintain records per regulatory retention requirements
 *
 * RETENTION PERIODS:
 * - SAR-related: 5 years from filing date (FinCEN requirement)
 * - OFAC-related: 5 years from transaction date (OFAC requirement)
 * - IRS-related: 7 years (standard tax retention)
 * - General compliance: 5 years minimum
 *
 * @author Waqiti Compliance Team
 * @since 1.0
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class RegulatoryIncidentService {

    private final RegulatoryIncidentRepository regulatoryIncidentRepository;

    /**
     * Create a new regulatory incident
     *
     * @param incident Regulatory incident to create
     * @return Created incident with generated ID
     */
    public RegulatoryIncident createIncident(RegulatoryIncident incident) {
        try {
            // Set creation timestamp
            incident.setCreatedAt(LocalDateTime.now());

            // Set default status if not provided
            if (incident.getStatus() == null) {
                incident.setStatus("CREATED");
            }

            // Calculate reporting deadline based on incident type
            if (incident.getReportingDeadline() == null) {
                incident.setReportingDeadline(calculateReportingDeadline(incident));
            }

            // Save incident
            RegulatoryIncident savedIncident = regulatoryIncidentRepository.save(incident);

            log.error("REGULATORY_INCIDENT: Created regulatory incident - ID: {}, Type: {}, Severity: {}, " +
                            "User: {}, RequiresNotification: {}, Deadline: {}",
                    savedIncident.getId(), savedIncident.getIncidentType(), savedIncident.getSeverity(),
                    savedIncident.getUserId(), savedIncident.isRequiresRegulatorNotification(),
                    savedIncident.getReportingDeadline());

            return savedIncident;

        } catch (Exception e) {
            log.error("CRITICAL: Failed to create regulatory incident - Type: {}, User: {}",
                    incident.getIncidentType(), incident.getUserId(), e);
            throw new RuntimeException("Failed to create regulatory incident", e);
        }
    }

    /**
     * Get incident by ID
     */
    @Transactional(readOnly = true)
    public RegulatoryIncident getIncident(String incidentId) {
        return regulatoryIncidentRepository.findById(incidentId)
                .orElseThrow(() -> new IllegalArgumentException("Regulatory incident not found: " + incidentId));
    }

    /**
     * Get all incidents for a user
     */
    @Transactional(readOnly = true)
    public List<RegulatoryIncident> getIncidentsByUser(UUID userId) {
        return regulatoryIncidentRepository.findByUserId(userId);
    }

    /**
     * Get incidents requiring regulator notification
     */
    @Transactional(readOnly = true)
    public List<RegulatoryIncident> getIncidentsRequiringNotification() {
        return regulatoryIncidentRepository.findByRequiresRegulatorNotificationTrue();
    }

    /**
     * Get overdue incidents (past reporting deadline)
     */
    @Transactional(readOnly = true)
    public List<RegulatoryIncident> getOverdueIncidents() {
        return regulatoryIncidentRepository.findOverdueIncidents(LocalDateTime.now());
    }

    /**
     * Update incident status
     */
    public RegulatoryIncident updateIncidentStatus(String incidentId, String newStatus, String updatedBy) {
        try {
            RegulatoryIncident incident = getIncident(incidentId);

            String oldStatus = incident.getStatus();
            incident.setStatus(newStatus);
            incident.setUpdatedAt(LocalDateTime.now());
            incident.setUpdatedBy(updatedBy);

            // If marking as reported, set reported timestamp
            if ("REPORTED".equals(newStatus) && incident.getReportedAt() == null) {
                incident.setReportedAt(LocalDateTime.now());
            }

            // If marking as resolved, set resolved timestamp
            if ("RESOLVED".equals(newStatus) && incident.getResolvedAt() == null) {
                incident.setResolvedAt(LocalDateTime.now());
            }

            RegulatoryIncident updated = regulatoryIncidentRepository.save(incident);

            log.info("REGULATORY_INCIDENT: Updated incident status - ID: {}, OldStatus: {}, NewStatus: {}, UpdatedBy: {}",
                    incidentId, oldStatus, newStatus, updatedBy);

            return updated;

        } catch (Exception e) {
            log.error("Failed to update regulatory incident status - ID: {}, NewStatus: {}",
                    incidentId, newStatus, e);
            throw new RuntimeException("Failed to update incident status", e);
        }
    }

    /**
     * Mark incident as reported to regulator
     */
    public RegulatoryIncident markAsReported(String incidentId, String reportingReference, String reportedBy) {
        try {
            RegulatoryIncident incident = getIncident(incidentId);

            incident.setStatus("REPORTED");
            incident.setReportedAt(LocalDateTime.now());
            incident.setReportingReference(reportingReference);
            incident.setUpdatedAt(LocalDateTime.now());
            incident.setUpdatedBy(reportedBy);

            RegulatoryIncident updated = regulatoryIncidentRepository.save(incident);

            log.warn("REGULATORY_INCIDENT: Incident reported to regulator - ID: {}, Reference: {}, ReportedBy: {}",
                    incidentId, reportingReference, reportedBy);

            return updated;

        } catch (Exception e) {
            log.error("Failed to mark incident as reported - ID: {}", incidentId, e);
            throw new RuntimeException("Failed to mark incident as reported", e);
        }
    }

    /**
     * Resolve regulatory incident
     */
    public RegulatoryIncident resolveIncident(String incidentId, String resolution, String resolvedBy) {
        try {
            RegulatoryIncident incident = getIncident(incidentId);

            incident.setStatus("RESOLVED");
            incident.setResolvedAt(LocalDateTime.now());
            incident.setResolution(resolution);
            incident.setUpdatedAt(LocalDateTime.now());
            incident.setUpdatedBy(resolvedBy);

            RegulatoryIncident updated = regulatoryIncidentRepository.save(incident);

            log.info("REGULATORY_INCIDENT: Incident resolved - ID: {}, Resolution: {}, ResolvedBy: {}",
                    incidentId, resolution, resolvedBy);

            return updated;

        } catch (Exception e) {
            log.error("Failed to resolve regulatory incident - ID: {}", incidentId, e);
            throw new RuntimeException("Failed to resolve incident", e);
        }
    }

    /**
     * Calculate reporting deadline based on incident type
     */
    private LocalDateTime calculateReportingDeadline(RegulatoryIncident incident) {
        LocalDateTime now = LocalDateTime.now();

        switch (incident.getIncidentType().toUpperCase()) {
            case "SANCTIONS_FREEZE_FAILURE":
            case "OFAC_VIOLATION":
                // OFAC violations: 10 business days
                return now.plusDays(10);

            case "SAR_FILING_FAILURE":
            case "AML_SUSPICIOUS_ACTIVITY":
                // SAR filing: 30 days from detection
                return now.plusDays(30);

            case "IRS_FORM_8300_FAILURE":
                // Form 8300: 15 days from transaction
                return now.plusDays(15);

            case "CRITICAL_COMPLIANCE_VIOLATION":
                // Critical violations: 24 hours
                return now.plusHours(24);

            default:
                // Default: 30 days for standard regulatory reporting
                return now.plusDays(30);
        }
    }

    /**
     * Get count of active incidents
     */
    @Transactional(readOnly = true)
    public long getActiveIncidentCount() {
        return regulatoryIncidentRepository.countByStatusIn(List.of("CREATED", "UNDER_INVESTIGATION", "PENDING_REPORT"));
    }

    /**
     * Get count of overdue incidents
     */
    @Transactional(readOnly = true)
    public long getOverdueIncidentCount() {
        return regulatoryIncidentRepository.countOverdueIncidents(LocalDateTime.now());
    }
}
