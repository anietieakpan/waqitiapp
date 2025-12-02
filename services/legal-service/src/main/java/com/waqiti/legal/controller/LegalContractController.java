package com.waqiti.legal.controller;

import com.waqiti.legal.dto.request.CreateLegalContractRequest;
import com.waqiti.legal.dto.response.LegalContractResponse;
import com.waqiti.legal.domain.LegalContract;
import com.waqiti.legal.repository.LegalContractRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

/**
 * REST API Controller for Legal Contract Management
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/legal/contracts")
@RequiredArgsConstructor
public class LegalContractController {

    private final LegalContractRepository contractRepository;

    @PostMapping
    @PreAuthorize("hasAnyRole('LEGAL_ADMIN', 'LEGAL_OFFICER')")
    public ResponseEntity<LegalContractResponse> createContract(@Valid @RequestBody CreateLegalContractRequest request) {
        log.info("Creating contract: {}", request.getContractTitle());

        LegalContract contract = LegalContract.builder()
                .contractId(java.util.UUID.randomUUID().toString())
                .contractType(LegalContract.ContractType.valueOf(request.getContractType()))
                .contractTitle(request.getContractTitle())
                .firstPartyName(request.getFirstPartyName())
                .secondPartyName(request.getSecondPartyName())
                .contractStartDate(request.getContractStartDate())
                .contractEndDate(request.getContractEndDate())
                .contractValue(request.getContractValue())
                .currencyCode(request.getCurrencyCode())
                .jurisdiction(request.getJurisdiction())
                .governingLaw(request.getGoverningLaw())
                .disputeResolutionMethod(request.getDisputeResolutionMethod())
                .renewalPeriodDays(request.getRenewalPeriodDays())
                .autoRenewal(request.getAutoRenewal())
                .createdBy(request.getCreatedBy())
                .build();

        LegalContract saved = contractRepository.save(contract);
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(saved));
    }

    @GetMapping("/{contractId}")
    @PreAuthorize("hasAnyRole('LEGAL_ADMIN', 'LEGAL_OFFICER', 'LEGAL_VIEWER')")
    public ResponseEntity<LegalContractResponse> getContract(@PathVariable String contractId) {
        return contractRepository.findByContractId(contractId)
                .map(this::toResponse)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('LEGAL_ADMIN', 'LEGAL_OFFICER', 'LEGAL_VIEWER')")
    public ResponseEntity<List<LegalContractResponse>> getAllContracts() {
        List<LegalContractResponse> contracts = contractRepository.findAll()
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
        return ResponseEntity.ok(contracts);
    }

    @GetMapping("/active")
    @PreAuthorize("hasAnyRole('LEGAL_ADMIN', 'LEGAL_OFFICER', 'LEGAL_VIEWER')")
    public ResponseEntity<List<LegalContractResponse>> getActiveContracts() {
        List<LegalContractResponse> contracts = contractRepository.findActiveContracts(LocalDate.now())
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
        return ResponseEntity.ok(contracts);
    }

    @GetMapping("/expiring")
    @PreAuthorize("hasAnyRole('LEGAL_ADMIN', 'LEGAL_OFFICER')")
    public ResponseEntity<List<LegalContractResponse>> getExpiringContracts(
            @RequestParam(defaultValue = "30") int days) {
        LocalDate startDate = LocalDate.now();
        LocalDate endDate = startDate.plusDays(days);

        List<LegalContractResponse> contracts = contractRepository.findContractsExpiringBetween(startDate, endDate)
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
        return ResponseEntity.ok(contracts);
    }

    @DeleteMapping("/{contractId}")
    @PreAuthorize("hasRole('LEGAL_ADMIN')")
    public ResponseEntity<Void> deleteContract(@PathVariable String contractId) {
        return contractRepository.findByContractId(contractId)
                .map(contract -> {
                    contractRepository.delete(contract);
                    return ResponseEntity.noContent().<Void>build();
                })
                .orElse(ResponseEntity.notFound().build());
    }

    private LegalContractResponse toResponse(LegalContract contract) {
        return LegalContractResponse.builder()
                .contractId(contract.getContractId())
                .contractType(contract.getContractType() != null ? contract.getContractType().name() : null)
                .contractTitle(contract.getContractTitle())
                .firstPartyName(contract.getFirstPartyName())
                .secondPartyName(contract.getSecondPartyName())
                .contractStatus(contract.getContractStatus() != null ? contract.getContractStatus().name() : null)
                .contractStartDate(contract.getContractStartDate())
                .contractEndDate(contract.getContractEndDate())
                .contractValue(contract.getContractValue())
                .currencyCode(contract.getCurrencyCode())
                .jurisdiction(contract.getJurisdiction())
                .governingLaw(contract.getGoverningLaw())
                .disputeResolutionMethod(contract.getDisputeResolutionMethod())
                .renewalPeriodDays(contract.getRenewalPeriodDays())
                .autoRenewal(contract.isAutoRenewal())
                .nextRenewalDate(contract.getNextRenewalDate())
                .renewalCount(contract.getRenewalCount())
                .requiresSignature(contract.isRequiresSignature())
                .fullyExecuted(contract.isFullyExecuted())
                .executedDate(contract.getExecutedDate())
                .createdAt(contract.getCreatedAt())
                .updatedAt(contract.getUpdatedAt())
                .createdBy(contract.getCreatedBy())
                .updatedBy(contract.getUpdatedBy())
                .build();
    }
}
