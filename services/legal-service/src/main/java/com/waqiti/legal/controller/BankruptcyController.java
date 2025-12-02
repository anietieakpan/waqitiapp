package com.waqiti.legal.controller;

import com.waqiti.legal.dto.request.CreateBankruptcyRequest;
import com.waqiti.legal.dto.response.BankruptcyResponse;
import com.waqiti.legal.domain.BankruptcyCase;
import com.waqiti.legal.service.BankruptcyProcessingService;
import com.waqiti.legal.service.AutomaticStayService;
import com.waqiti.legal.repository.BankruptcyRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * REST API Controller for Bankruptcy Case Management
 *
 * Provides endpoints for managing bankruptcy cases with automatic stay enforcement
 * and U.S. Bankruptcy Code compliance
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/legal/bankruptcy")
@RequiredArgsConstructor
public class BankruptcyController {

    private final BankruptcyProcessingService bankruptcyProcessingService;
    private final AutomaticStayService automaticStayService;
    private final BankruptcyRepository bankruptcyRepository;

    /**
     * Create a new bankruptcy case
     */
    @PostMapping
    @PreAuthorize("hasAnyRole('LEGAL_ADMIN', 'LEGAL_OFFICER')")
    public ResponseEntity<BankruptcyResponse> createBankruptcyCase(@Valid @RequestBody CreateBankruptcyRequest request) {
        log.info("Creating bankruptcy case for customer: {}, chapter: {}", request.getCustomerId(), request.getBankruptcyChapter());

        Map<String, String> trusteeInfo = new HashMap<>();
        if (request.getTrusteeName() != null) {
            trusteeInfo.put("name", request.getTrusteeName());
            trusteeInfo.put("email", request.getTrusteeEmail());
            trusteeInfo.put("phone", request.getTrusteePhone());
        }

        BankruptcyCase bankruptcyCase = bankruptcyProcessingService.createBankruptcyRecord(
                request.getCustomerId(),
                request.getCustomerName(),
                request.getCaseNumber(),
                request.getBankruptcyChapter(),
                request.getFilingDate(),
                request.getCourtDistrict(),
                trusteeInfo,
                request.getTotalDebtAmount(),
                "API_USER"
        );

        // Enforce automatic stay
        automaticStayService.enforceAutomaticStay(
                bankruptcyCase.getBankruptcyId(),
                request.getCustomerId(),
                request.getFilingDate(),
                request.getFilingDate()
        );

        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(bankruptcyCase));
    }

    /**
     * Get bankruptcy case by ID
     */
    @GetMapping("/{bankruptcyId}")
    @PreAuthorize("hasAnyRole('LEGAL_ADMIN', 'LEGAL_OFFICER', 'LEGAL_VIEWER')")
    public ResponseEntity<BankruptcyResponse> getBankruptcyCase(@PathVariable String bankruptcyId) {
        log.info("Retrieving bankruptcy case: {}", bankruptcyId);

        return bankruptcyRepository.findByBankruptcyId(bankruptcyId)
                .map(this::toResponse)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Get bankruptcy case by case number
     */
    @GetMapping("/case/{caseNumber}")
    @PreAuthorize("hasAnyRole('LEGAL_ADMIN', 'LEGAL_OFFICER', 'LEGAL_VIEWER')")
    public ResponseEntity<BankruptcyResponse> getBankruptcyByCaseNumber(@PathVariable String caseNumber) {
        log.info("Retrieving bankruptcy case by case number: {}", caseNumber);

        return bankruptcyRepository.findByCaseNumber(caseNumber)
                .map(this::toResponse)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Get all bankruptcy cases for a customer
     */
    @GetMapping("/customer/{customerId}")
    @PreAuthorize("hasAnyRole('LEGAL_ADMIN', 'LEGAL_OFFICER', 'LEGAL_VIEWER')")
    public ResponseEntity<List<BankruptcyResponse>> getBankruptcyCasesByCustomer(@PathVariable String customerId) {
        log.info("Retrieving bankruptcy cases for customer: {}", customerId);

        List<BankruptcyResponse> cases = bankruptcyRepository.findByCustomerId(customerId)
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());

        return ResponseEntity.ok(cases);
    }

    /**
     * Get all bankruptcy cases by chapter
     */
    @GetMapping("/chapter/{chapter}")
    @PreAuthorize("hasAnyRole('LEGAL_ADMIN', 'LEGAL_OFFICER', 'LEGAL_VIEWER')")
    public ResponseEntity<List<BankruptcyResponse>> getBankruptcyCasesByChapter(@PathVariable String chapter) {
        log.info("Retrieving bankruptcy cases for chapter: {}", chapter);

        BankruptcyCase.BankruptcyChapter bankruptcyChapter = BankruptcyCase.BankruptcyChapter.valueOf(chapter);
        List<BankruptcyResponse> cases = bankruptcyRepository.findByBankruptcyChapter(bankruptcyChapter)
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());

        return ResponseEntity.ok(cases);
    }

    /**
     * Get all active bankruptcy cases (automatic stay active)
     */
    @GetMapping("/active-stay")
    @PreAuthorize("hasAnyRole('LEGAL_ADMIN', 'LEGAL_OFFICER', 'LEGAL_VIEWER')")
    public ResponseEntity<List<BankruptcyResponse>> getActiveBankruptcyCases() {
        log.info("Retrieving bankruptcy cases with active automatic stay");

        List<BankruptcyResponse> cases = bankruptcyRepository.findByAutomaticStayActiveTrue()
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());

        return ResponseEntity.ok(cases);
    }

    /**
     * File proof of claim
     */
    @PostMapping("/{bankruptcyId}/proof-of-claim")
    @PreAuthorize("hasAnyRole('LEGAL_ADMIN', 'LEGAL_OFFICER')")
    public ResponseEntity<Map<String, Object>> fileProofOfClaim(@PathVariable String bankruptcyId) {
        log.info("Filing proof of claim for bankruptcy case: {}", bankruptcyId);

        BankruptcyCase bankruptcyCase = bankruptcyRepository.findByBankruptcyId(bankruptcyId)
                .orElseThrow(() -> new IllegalArgumentException("Bankruptcy case not found"));

        java.math.BigDecimal claimAmount = bankruptcyProcessingService.calculateCreditorClaim(
                bankruptcyId, bankruptcyCase.getCustomerId());

        String classification = "UNSECURED_NONPRIORITY"; // Simplified for now

        Map<String, Object> result = bankruptcyProcessingService.fileProofOfClaim(
                bankruptcyId,
                claimAmount,
                classification,
                java.util.Collections.emptyList()
        );

        return ResponseEntity.ok(result);
    }

    /**
     * Prepare Chapter 13 repayment plan
     */
    @PostMapping("/{bankruptcyId}/repayment-plan")
    @PreAuthorize("hasAnyRole('LEGAL_ADMIN', 'LEGAL_OFFICER')")
    public ResponseEntity<Map<String, Object>> prepareRepaymentPlan(
            @PathVariable String bankruptcyId,
            @RequestParam(defaultValue = "60") int durationMonths) {
        log.info("Preparing Chapter 13 repayment plan for case: {}", bankruptcyId);

        Map<String, Object> plan = bankruptcyProcessingService.prepareRepaymentPlan(bankruptcyId, durationMonths);

        return ResponseEntity.ok(plan);
    }

    /**
     * Identify exempt assets (Chapter 7)
     */
    @PostMapping("/{bankruptcyId}/exempt-assets")
    @PreAuthorize("hasAnyRole('LEGAL_ADMIN', 'LEGAL_OFFICER')")
    public ResponseEntity<Map<String, Object>> identifyExemptAssets(@PathVariable String bankruptcyId) {
        log.info("Identifying exempt assets for case: {}", bankruptcyId);

        BankruptcyCase bankruptcyCase = bankruptcyRepository.findByBankruptcyId(bankruptcyId)
                .orElseThrow(() -> new IllegalArgumentException("Bankruptcy case not found"));

        Map<String, Object> assets = bankruptcyProcessingService.identifyExemptAssets(
                bankruptcyId, bankruptcyCase.getCustomerId());

        return ResponseEntity.ok(assets);
    }

    /**
     * Grant discharge
     */
    @PostMapping("/{bankruptcyId}/discharge")
    @PreAuthorize("hasRole('LEGAL_ADMIN')")
    public ResponseEntity<BankruptcyResponse> grantDischarge(@PathVariable String bankruptcyId) {
        log.info("Granting discharge for bankruptcy case: {}", bankruptcyId);

        return bankruptcyRepository.findByBankruptcyId(bankruptcyId)
                .map(bankruptcyCase -> {
                    bankruptcyCase.grantDischarge(java.time.LocalDate.now());
                    BankruptcyCase updated = bankruptcyRepository.save(bankruptcyCase);
                    return ResponseEntity.ok(toResponse(updated));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Dismiss bankruptcy case
     */
    @PostMapping("/{bankruptcyId}/dismiss")
    @PreAuthorize("hasRole('LEGAL_ADMIN')")
    public ResponseEntity<BankruptcyResponse> dismissCase(
            @PathVariable String bankruptcyId,
            @RequestParam String reason) {
        log.info("Dismissing bankruptcy case: {} for reason: {}", bankruptcyId, reason);

        return bankruptcyRepository.findByBankruptcyId(bankruptcyId)
                .map(bankruptcyCase -> {
                    bankruptcyCase.dismiss(java.time.LocalDate.now(), reason);
                    BankruptcyCase updated = bankruptcyRepository.save(bankruptcyCase);
                    return ResponseEntity.ok(toResponse(updated));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Convert BankruptcyCase entity to Response DTO
     */
    private BankruptcyResponse toResponse(BankruptcyCase bankruptcyCase) {
        return BankruptcyResponse.builder()
                .bankruptcyId(bankruptcyCase.getBankruptcyId())
                .customerId(bankruptcyCase.getCustomerId())
                .customerName(bankruptcyCase.getCustomerName())
                .caseNumber(bankruptcyCase.getCaseNumber())
                .bankruptcyChapter(bankruptcyCase.getBankruptcyChapter().name())
                .caseStatus(bankruptcyCase.getCaseStatus().name())
                .filingDate(bankruptcyCase.getFilingDate())
                .courtDistrict(bankruptcyCase.getCourtDistrict())
                .trusteeName(bankruptcyCase.getTrusteeName())
                .trusteeEmail(bankruptcyCase.getTrusteeEmail())
                .trusteePhone(bankruptcyCase.getTrusteePhone())
                .totalDebtAmount(bankruptcyCase.getTotalDebtAmount())
                .waqitiClaimAmount(bankruptcyCase.getWaqitiClaimAmount())
                .claimClassification(bankruptcyCase.getClaimClassification())
                .currencyCode(bankruptcyCase.getCurrencyCode())
                .automaticStayActive(bankruptcyCase.isAutomaticStayActive())
                .automaticStayDate(bankruptcyCase.getAutomaticStayDate())
                .accountsFrozen(bankruptcyCase.isAccountsFrozen())
                .pendingTransactionsCancelled(bankruptcyCase.isPendingTransactionsCancelled())
                .proofOfClaimFiled(bankruptcyCase.isProofOfClaimFiled())
                .proofOfClaimFilingDate(bankruptcyCase.getProofOfClaimFilingDate())
                .proofOfClaimBarDate(bankruptcyCase.getProofOfClaimBarDate())
                .dischargeGranted(bankruptcyCase.isDischargeGranted())
                .dischargeDate(bankruptcyCase.getDischargeDate())
                .dismissed(bankruptcyCase.isDismissed())
                .dismissalDate(bankruptcyCase.getDismissalDate())
                .dismissalReason(bankruptcyCase.getDismissalReason())
                .expectedRecoveryPercentage(bankruptcyCase.getExpectedRecoveryPercentage())
                .expectedRecoveryAmount(bankruptcyCase.calculateExpectedRecovery())
                .creditReportingFlagged(bankruptcyCase.isCreditReportingFlagged())
                .creditReportingFlagDate(bankruptcyCase.getCreditReportingFlagDate())
                .createdAt(bankruptcyCase.getCreatedAt())
                .updatedAt(bankruptcyCase.getUpdatedAt())
                .createdBy(bankruptcyCase.getCreatedBy())
                .build();
    }
}
