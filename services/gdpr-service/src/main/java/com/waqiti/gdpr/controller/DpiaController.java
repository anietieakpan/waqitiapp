package com.waqiti.gdpr.controller;

import com.waqiti.gdpr.domain.DataPrivacyImpactAssessment;
import com.waqiti.gdpr.domain.DpiaStatus;
import com.waqiti.gdpr.repository.DataPrivacyImpactAssessmentRepository;
import com.waqiti.gdpr.service.DpiaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Data Privacy Impact Assessment Controller
 *
 * REST API for GDPR Article 35 DPIA compliance.
 *
 * Endpoints:
 * - POST /api/v1/privacy/dpia - Initiate DPIA
 * - GET /api/v1/privacy/dpia - List DPIAs
 * - GET /api/v1/privacy/dpia/{id} - Get DPIA details
 * - POST /api/v1/privacy/dpia/{id}/risk-assessment - Conduct risk assessment
 * - POST /api/v1/privacy/dpia/{id}/dpo-consultation - Record DPO consultation
 * - POST /api/v1/privacy/dpia/{id}/subject-consultation - Record data subject consultation
 * - POST /api/v1/privacy/dpia/{id}/authority-consultation - Record supervisory authority consultation
 * - POST /api/v1/privacy/dpia/{id}/complete - Complete DPIA
 * - POST /api/v1/privacy/dpia/{id}/approve - Approve DPIA
 * - POST /api/v1/privacy/dpia/{id}/reject - Reject DPIA
 */
@RestController
@RequestMapping("/api/v1/privacy/dpia")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Data Privacy Impact Assessment", description = "GDPR Article 35 - DPIA Management")
public class DpiaController {

    private final DpiaService dpiaService;
    private final DataPrivacyImpactAssessmentRepository dpiaRepository;

    @PostMapping
    @PreAuthorize("hasAnyRole('DPO', 'PRIVACY_OFFICER', 'ADMIN')")
    @Operation(summary = "Initiate DPIA", description = "Start a new Data Privacy Impact Assessment (Article 35)")
    public ResponseEntity<DataPrivacyImpactAssessment> initiateDpia(
            @Valid @RequestBody DpiaService.DpiaInitiationRequest request) {

        log.info("API: Initiating DPIA: title={}", request.getTitle());

        DataPrivacyImpactAssessment dpia = dpiaService.initiateDpia(request);

        return ResponseEntity.status(HttpStatus.CREATED).body(dpia);
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('DPO', 'PRIVACY_OFFICER', 'ADMIN', 'AUDITOR')")
    @Operation(summary = "List DPIAs")
    public ResponseEntity<List<DataPrivacyImpactAssessment>> listDpias(
            @RequestParam(required = false) DpiaStatus status) {

        List<DataPrivacyImpactAssessment> dpias;

        if (status != null) {
            dpias = dpiaRepository.findByStatus(status);
        } else {
            dpias = dpiaRepository.findAll();
        }

        return ResponseEntity.ok(dpias);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('DPO', 'PRIVACY_OFFICER', 'ADMIN', 'AUDITOR')")
    @Operation(summary = "Get DPIA details")
    public ResponseEntity<DataPrivacyImpactAssessment> getDpia(@PathVariable String id) {
        return dpiaRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/{id}/risk-assessment")
    @PreAuthorize("hasAnyRole('DPO', 'PRIVACY_OFFICER', 'ADMIN')")
    @Operation(summary = "Conduct risk assessment")
    public ResponseEntity<DataPrivacyImpactAssessment> conductRiskAssessment(
            @PathVariable String id,
            @Valid @RequestBody DpiaService.RiskAssessmentInput input) {

        log.info("API: Conducting risk assessment for DPIA: id={}", id);

        DataPrivacyImpactAssessment dpia = dpiaService.conductRiskAssessment(id, input);

        return ResponseEntity.ok(dpia);
    }

    @PostMapping("/{id}/dpo-consultation")
    @PreAuthorize("hasAnyRole('DPO', 'PRIVACY_OFFICER', 'ADMIN')")
    @Operation(summary = "Record DPO consultation")
    public ResponseEntity<DataPrivacyImpactAssessment> consultDpo(
            @PathVariable String id,
            @RequestParam String dpoName,
            @RequestBody String opinion) {

        log.info("API: Recording DPO consultation for DPIA: id={}, dpo={}", id, dpoName);

        DataPrivacyImpactAssessment dpia = dpiaService.consultDpo(id, dpoName, opinion);

        return ResponseEntity.ok(dpia);
    }

    @PostMapping("/{id}/subject-consultation")
    @PreAuthorize("hasAnyRole('DPO', 'PRIVACY_OFFICER', 'ADMIN')")
    @Operation(summary = "Record data subject consultation")
    public ResponseEntity<DataPrivacyImpactAssessment> consultSubjects(
            @PathVariable String id,
            @RequestBody String consultationSummary) {

        log.info("API: Recording data subject consultation for DPIA: id={}", id);

        DataPrivacyImpactAssessment dpia = dpiaService.consultDataSubjects(id, consultationSummary);

        return ResponseEntity.ok(dpia);
    }

    @PostMapping("/{id}/authority-consultation")
    @PreAuthorize("hasAnyRole('DPO', 'ADMIN')")
    @Operation(summary = "Record supervisory authority consultation", description = "Article 36 - Prior consultation")
    public ResponseEntity<DataPrivacyImpactAssessment> consultAuthority(
            @PathVariable String id,
            @RequestParam String reference) {

        log.info("API: Recording supervisory authority consultation for DPIA: id={}, reference={}",
                id, reference);

        DataPrivacyImpactAssessment dpia = dpiaService.consultSupervisoryAuthority(id, reference);

        return ResponseEntity.ok(dpia);
    }

    @PostMapping("/{id}/complete")
    @PreAuthorize("hasAnyRole('DPO', 'PRIVACY_OFFICER', 'ADMIN')")
    @Operation(summary = "Complete DPIA")
    public ResponseEntity<DataPrivacyImpactAssessment> completeDpia(
            @PathVariable String id,
            @Valid @RequestBody DpiaService.DpiaCompletionInput input) {

        log.info("API: Completing DPIA: id={}", id);

        DataPrivacyImpactAssessment dpia = dpiaService.completeDpia(id, input);

        return ResponseEntity.ok(dpia);
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasAnyRole('DPO', 'ADMIN')")
    @Operation(summary = "Approve DPIA")
    public ResponseEntity<DataPrivacyImpactAssessment> approveDpia(
            @PathVariable String id,
            @RequestParam String approver) {

        log.info("API: Approving DPIA: id={}, approver={}", id, approver);

        DataPrivacyImpactAssessment dpia = dpiaService.approveDpia(id, approver);

        return ResponseEntity.ok(dpia);
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize("hasAnyRole('DPO', 'ADMIN')")
    @Operation(summary = "Reject DPIA")
    public ResponseEntity<DataPrivacyImpactAssessment> rejectDpia(
            @PathVariable String id,
            @RequestParam String reviewer,
            @RequestBody String reason) {

        log.info("API: Rejecting DPIA: id={}, reviewer={}", id, reviewer);

        DataPrivacyImpactAssessment dpia = dpiaService.rejectDpia(id, reviewer, reason);

        return ResponseEntity.ok(dpia);
    }

    @GetMapping("/high-risk")
    @PreAuthorize("hasAnyRole('DPO', 'PRIVACY_OFFICER', 'ADMIN')")
    @Operation(summary = "Get high-risk DPIAs")
    public ResponseEntity<List<DataPrivacyImpactAssessment>> getHighRiskDpias() {
        List<DataPrivacyImpactAssessment> highRisk = dpiaService.getHighRiskDpias();
        return ResponseEntity.ok(highRisk);
    }

    @GetMapping("/requiring-authority")
    @PreAuthorize("hasAnyRole('DPO', 'ADMIN')")
    @Operation(summary = "Get DPIAs requiring supervisory authority consultation")
    public ResponseEntity<List<DataPrivacyImpactAssessment>> getRequiringAuthority() {
        List<DataPrivacyImpactAssessment> requiring = dpiaService.getDpiasRequiringAuthority();
        return ResponseEntity.ok(requiring);
    }

    @GetMapping("/review-due")
    @PreAuthorize("hasAnyRole('DPO', 'PRIVACY_OFFICER', 'ADMIN')")
    @Operation(summary = "Get DPIAs requiring periodic review")
    public ResponseEntity<List<DataPrivacyImpactAssessment>> getReviewDue() {
        List<DataPrivacyImpactAssessment> reviewDue =
                dpiaRepository.findDpiasWithReviewDue(LocalDateTime.now());
        return ResponseEntity.ok(reviewDue);
    }
}
