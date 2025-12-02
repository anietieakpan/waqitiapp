package com.waqiti.legal.controller;

import com.waqiti.legal.dto.request.CreateLegalCaseRequest;
import com.waqiti.legal.dto.response.LegalCaseResponse;
import com.waqiti.legal.domain.LegalCase;
import com.waqiti.legal.repository.LegalCaseRepository;
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
 * REST API Controller for Legal Case Management (Litigation)
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/legal/cases")
@RequiredArgsConstructor
public class LegalCaseController {

    private final LegalCaseRepository caseRepository;

    @PostMapping
    @PreAuthorize("hasAnyRole('LEGAL_ADMIN', 'LEGAL_OFFICER')")
    public ResponseEntity<LegalCaseResponse> createCase(@Valid @RequestBody CreateLegalCaseRequest request) {
        log.info("Creating legal case: {}", request.getCaseTitle());

        LegalCase legalCase = LegalCase.builder()
                .caseId(java.util.UUID.randomUUID().toString())
                .caseNumber(request.getCaseNumber())
                .caseType(request.getCaseType())
                .caseTitle(request.getCaseTitle())
                .filingDate(request.getFilingDate())
                .court(request.getCourt())
                .jurisdiction(request.getJurisdiction())
                .plaintiff(request.getPlaintiff())
                .defendant(request.getDefendant())
                .assignedAttorneyId(request.getAssignedAttorneyId())
                .claimAmount(request.getClaimAmount())
                .currencyCode(request.getCurrencyCode())
                .caseDescription(request.getCaseDescription())
                .createdBy("API_USER")
                .build();

        LegalCase saved = caseRepository.save(legalCase);
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(saved));
    }

    @GetMapping("/{caseId}")
    @PreAuthorize("hasAnyRole('LEGAL_ADMIN', 'LEGAL_OFFICER', 'LEGAL_VIEWER')")
    public ResponseEntity<LegalCaseResponse> getCase(@PathVariable String caseId) {
        return caseRepository.findByCaseId(caseId)
                .map(this::toResponse)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/number/{caseNumber}")
    @PreAuthorize("hasAnyRole('LEGAL_ADMIN', 'LEGAL_OFFICER', 'LEGAL_VIEWER')")
    public ResponseEntity<LegalCaseResponse> getCaseByCaseNumber(@PathVariable String caseNumber) {
        return caseRepository.findByCaseNumber(caseNumber)
                .map(this::toResponse)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('LEGAL_ADMIN', 'LEGAL_OFFICER', 'LEGAL_VIEWER')")
    public ResponseEntity<List<LegalCaseResponse>> getAllCases() {
        List<LegalCaseResponse> cases = caseRepository.findAll()
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
        return ResponseEntity.ok(cases);
    }

    @DeleteMapping("/{caseId}")
    @PreAuthorize("hasRole('LEGAL_ADMIN')")
    public ResponseEntity<Void> deleteCase(@PathVariable String caseId) {
        return caseRepository.findByCaseId(caseId)
                .map(legalCase -> {
                    caseRepository.delete(legalCase);
                    return ResponseEntity.noContent().<Void>build();
                })
                .orElse(ResponseEntity.notFound().build());
    }

    private LegalCaseResponse toResponse(LegalCase legalCase) {
        return LegalCaseResponse.builder()
                .caseId(legalCase.getCaseId())
                .caseNumber(legalCase.getCaseNumber())
                .caseType(legalCase.getCaseType())
                .caseTitle(legalCase.getCaseTitle())
                .caseStatus(legalCase.getCaseStatus())
                .filingDate(legalCase.getFilingDate())
                .court(legalCase.getCourt())
                .jurisdiction(legalCase.getJurisdiction())
                .plaintiff(legalCase.getPlaintiff())
                .defendant(legalCase.getDefendant())
                .assignedAttorneyId(legalCase.getAssignedAttorneyId())
                .claimAmount(legalCase.getClaimAmount())
                .currencyCode(legalCase.getCurrencyCode())
                .caseDescription(legalCase.getCaseDescription())
                .hearingDate(legalCase.getHearingDate())
                .settlementDate(legalCase.getSettlementDate())
                .settlementAmount(legalCase.getSettlementAmount())
                .outcome(legalCase.getOutcome())
                .createdAt(legalCase.getCreatedAt())
                .updatedAt(legalCase.getUpdatedAt())
                .createdBy(legalCase.getCreatedBy())
                .updatedBy(legalCase.getUpdatedBy())
                .build();
    }
}
