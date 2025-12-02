package com.waqiti.legal.controller;

import com.waqiti.legal.dto.request.CreateComplianceAssessmentRequest;
import com.waqiti.legal.dto.response.ComplianceAssessmentResponse;
import com.waqiti.legal.domain.ComplianceAssessment;
import com.waqiti.legal.repository.ComplianceAssessmentRepository;
import com.waqiti.legal.repository.ComplianceRequirementRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

/**
 * REST API Controller for Compliance Management
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/legal/compliance")
@RequiredArgsConstructor
public class ComplianceController {

    private final ComplianceAssessmentRepository assessmentRepository;
    private final ComplianceRequirementRepository requirementRepository;

    @PostMapping("/assessments")
    @PreAuthorize("hasAnyRole('LEGAL_ADMIN', 'LEGAL_OFFICER', 'AUDITOR')")
    public ResponseEntity<ComplianceAssessmentResponse> createAssessment(
            @Valid @RequestBody CreateComplianceAssessmentRequest request) {
        log.info("Creating compliance assessment for requirement: {}", request.getRequirementId());

        ComplianceAssessment assessment = ComplianceAssessment.builder()
                .assessmentId(java.util.UUID.randomUUID().toString())
                .requirementId(request.getRequirementId())
                .assessmentType(request.getAssessmentType())
                .assessmentDate(request.getAssessmentDate())
                .assessorName(request.getAssessorName())
                .scope(request.getScope())
                .methodology(request.getMethodology())
                .findings(request.getFindings())
                .recommendations(request.getRecommendations())
                .status(request.getStatus())
                .dueDate(request.getDueDate())
                .createdBy("API_USER")
                .build();

        ComplianceAssessment saved = assessmentRepository.save(assessment);
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(saved));
    }

    @GetMapping("/assessments/{assessmentId}")
    @PreAuthorize("hasAnyRole('LEGAL_ADMIN', 'LEGAL_OFFICER', 'LEGAL_VIEWER', 'AUDITOR')")
    public ResponseEntity<ComplianceAssessmentResponse> getAssessment(@PathVariable String assessmentId) {
        return assessmentRepository.findByAssessmentId(assessmentId)
                .map(this::toResponse)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/assessments")
    @PreAuthorize("hasAnyRole('LEGAL_ADMIN', 'LEGAL_OFFICER', 'LEGAL_VIEWER', 'AUDITOR')")
    public ResponseEntity<List<ComplianceAssessmentResponse>> getAllAssessments() {
        List<ComplianceAssessmentResponse> assessments = assessmentRepository.findAll()
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
        return ResponseEntity.ok(assessments);
    }

    @GetMapping("/assessments/requirement/{requirementId}")
    @PreAuthorize("hasAnyRole('LEGAL_ADMIN', 'LEGAL_OFFICER', 'LEGAL_VIEWER', 'AUDITOR')")
    public ResponseEntity<List<ComplianceAssessmentResponse>> getAssessmentsByRequirement(
            @PathVariable String requirementId) {
        List<ComplianceAssessmentResponse> assessments = assessmentRepository.findByRequirementId(requirementId)
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
        return ResponseEntity.ok(assessments);
    }

    @GetMapping("/requirements/active")
    @PreAuthorize("hasAnyRole('LEGAL_ADMIN', 'LEGAL_OFFICER', 'LEGAL_VIEWER', 'AUDITOR')")
    public ResponseEntity<List<?>> getActiveRequirements() {
        return ResponseEntity.ok(requirementRepository.findByIsActiveTrue());
    }

    @DeleteMapping("/assessments/{assessmentId}")
    @PreAuthorize("hasRole('LEGAL_ADMIN')")
    public ResponseEntity<Void> deleteAssessment(@PathVariable String assessmentId) {
        return assessmentRepository.findByAssessmentId(assessmentId)
                .map(assessment -> {
                    assessmentRepository.delete(assessment);
                    return ResponseEntity.noContent().<Void>build();
                })
                .orElse(ResponseEntity.notFound().build());
    }

    private ComplianceAssessmentResponse toResponse(ComplianceAssessment assessment) {
        return ComplianceAssessmentResponse.builder()
                .assessmentId(assessment.getAssessmentId())
                .requirementId(assessment.getRequirementId())
                .assessmentType(assessment.getAssessmentType())
                .assessmentDate(assessment.getAssessmentDate())
                .assessorName(assessment.getAssessorName())
                .scope(assessment.getScope())
                .methodology(assessment.getMethodology())
                .findings(assessment.getFindings())
                .recommendations(assessment.getRecommendations())
                .status(assessment.getStatus())
                .dueDate(assessment.getDueDate())
                .completionDate(assessment.getCompletionDate())
                .complianceScore(assessment.getComplianceScore())
                .compliant(assessment.isCompliant())
                .createdAt(assessment.getCreatedAt())
                .updatedAt(assessment.getUpdatedAt())
                .createdBy(assessment.getCreatedBy())
                .updatedBy(assessment.getUpdatedBy())
                .build();
    }
}
