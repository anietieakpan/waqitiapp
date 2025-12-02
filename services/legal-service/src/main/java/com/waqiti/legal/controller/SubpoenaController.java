package com.waqiti.legal.controller;

import com.waqiti.legal.dto.request.CreateSubpoenaRequest;
import com.waqiti.legal.dto.request.UpdateSubpoenaStatusRequest;
import com.waqiti.legal.dto.request.ProcessSubpoenaRequest;
import com.waqiti.legal.dto.response.SubpoenaResponse;
import com.waqiti.legal.domain.Subpoena;
import com.waqiti.legal.service.SubpoenaProcessingService;
import com.waqiti.legal.repository.SubpoenaRepository;
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
 * REST API Controller for Subpoena Management
 *
 * Provides endpoints for creating, retrieving, updating, and processing subpoenas
 * with Right to Financial Privacy Act (RFPA) compliance
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/legal/subpoenas")
@RequiredArgsConstructor
public class SubpoenaController {

    private final SubpoenaProcessingService subpoenaProcessingService;
    private final SubpoenaRepository subpoenaRepository;

    /**
     * Create a new subpoena
     */
    @PostMapping
    @PreAuthorize("hasAnyRole('LEGAL_ADMIN', 'LEGAL_OFFICER')")
    public ResponseEntity<SubpoenaResponse> createSubpoena(@Valid @RequestBody CreateSubpoenaRequest request) {
        log.info("Creating subpoena for customer: {}, case: {}", request.getCustomerId(), request.getCaseNumber());

        Subpoena subpoena = subpoenaProcessingService.createSubpoenaRecord(
                java.util.UUID.randomUUID().toString(),
                request.getCustomerId(),
                request.getCaseNumber(),
                request.getIssuingCourt(),
                request.getIssuanceDate(),
                request.getResponseDeadline(),
                request.getSubpoenaType(),
                request.getRequestedRecords(),
                java.time.LocalDateTime.now()
        );

        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(subpoena));
    }

    /**
     * Get subpoena by ID
     */
    @GetMapping("/{subpoenaId}")
    @PreAuthorize("hasAnyRole('LEGAL_ADMIN', 'LEGAL_OFFICER', 'LEGAL_VIEWER')")
    public ResponseEntity<SubpoenaResponse> getSubpoena(@PathVariable String subpoenaId) {
        log.info("Retrieving subpoena: {}", subpoenaId);

        return subpoenaRepository.findBySubpoenaId(subpoenaId)
                .map(this::toResponse)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Get all subpoenas
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('LEGAL_ADMIN', 'LEGAL_OFFICER', 'LEGAL_VIEWER')")
    public ResponseEntity<List<SubpoenaResponse>> getAllSubpoenas() {
        log.info("Retrieving all subpoenas");

        List<SubpoenaResponse> subpoenas = subpoenaRepository.findAll()
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());

        return ResponseEntity.ok(subpoenas);
    }

    /**
     * Get subpoenas by customer ID
     */
    @GetMapping("/customer/{customerId}")
    @PreAuthorize("hasAnyRole('LEGAL_ADMIN', 'LEGAL_OFFICER', 'LEGAL_VIEWER')")
    public ResponseEntity<List<SubpoenaResponse>> getSubpoenasByCustomer(@PathVariable String customerId) {
        log.info("Retrieving subpoenas for customer: {}", customerId);

        List<SubpoenaResponse> subpoenas = subpoenaRepository.findByCustomerId(customerId)
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());

        return ResponseEntity.ok(subpoenas);
    }

    /**
     * Get subpoenas by case number
     */
    @GetMapping("/case/{caseNumber}")
    @PreAuthorize("hasAnyRole('LEGAL_ADMIN', 'LEGAL_OFFICER', 'LEGAL_VIEWER')")
    public ResponseEntity<List<SubpoenaResponse>> getSubpoenasByCase(@PathVariable String caseNumber) {
        log.info("Retrieving subpoenas for case: {}", caseNumber);

        List<SubpoenaResponse> subpoenas = subpoenaRepository.findByCaseNumber(caseNumber)
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());

        return ResponseEntity.ok(subpoenas);
    }

    /**
     * Get incomplete subpoenas
     */
    @GetMapping("/incomplete")
    @PreAuthorize("hasAnyRole('LEGAL_ADMIN', 'LEGAL_OFFICER')")
    public ResponseEntity<List<SubpoenaResponse>> getIncompleteSubpoenas() {
        log.info("Retrieving incomplete subpoenas");

        List<SubpoenaResponse> subpoenas = subpoenaRepository.findByCompletedFalse()
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());

        return ResponseEntity.ok(subpoenas);
    }

    /**
     * Update subpoena status
     */
    @PatchMapping("/{subpoenaId}/status")
    @PreAuthorize("hasAnyRole('LEGAL_ADMIN', 'LEGAL_OFFICER')")
    public ResponseEntity<SubpoenaResponse> updateSubpoenaStatus(
            @PathVariable String subpoenaId,
            @Valid @RequestBody UpdateSubpoenaStatusRequest request) {
        log.info("Updating status for subpoena: {} to {}", subpoenaId, request.getStatus());

        return subpoenaRepository.findBySubpoenaId(subpoenaId)
                .map(subpoena -> {
                    subpoena.setStatus(Subpoena.SubpoenaStatus.valueOf(request.getStatus()));
                    subpoena.setUpdatedBy(request.getUpdatedBy());
                    subpoena.setUpdatedAt(java.time.LocalDateTime.now());
                    Subpoena updated = subpoenaRepository.save(subpoena);
                    return ResponseEntity.ok(toResponse(updated));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Process subpoena (gather records)
     */
    @PostMapping("/{subpoenaId}/process")
    @PreAuthorize("hasAnyRole('LEGAL_ADMIN', 'LEGAL_OFFICER')")
    public ResponseEntity<SubpoenaResponse> processSubpoena(
            @PathVariable String subpoenaId,
            @Valid @RequestBody ProcessSubpoenaRequest request) {
        log.info("Processing subpoena: {}", subpoenaId);

        return subpoenaRepository.findBySubpoenaId(subpoenaId)
                .map(subpoena -> {
                    // Process subpoena logic here
                    subpoena.setStatus(Subpoena.SubpoenaStatus.PROCESSING);
                    subpoena.setUpdatedAt(java.time.LocalDateTime.now());
                    Subpoena updated = subpoenaRepository.save(subpoena);
                    return ResponseEntity.ok(toResponse(updated));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Mark subpoena as complete
     */
    @PostMapping("/{subpoenaId}/complete")
    @PreAuthorize("hasAnyRole('LEGAL_ADMIN', 'LEGAL_OFFICER')")
    public ResponseEntity<SubpoenaResponse> completeSubpoena(@PathVariable String subpoenaId) {
        log.info("Marking subpoena as complete: {}", subpoenaId);

        return subpoenaRepository.findBySubpoenaId(subpoenaId)
                .map(subpoena -> {
                    subpoena.markAsCompleted(java.time.LocalDateTime.now());
                    Subpoena updated = subpoenaRepository.save(subpoena);
                    return ResponseEntity.ok(toResponse(updated));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Delete subpoena (soft delete)
     */
    @DeleteMapping("/{subpoenaId}")
    @PreAuthorize("hasRole('LEGAL_ADMIN')")
    public ResponseEntity<Void> deleteSubpoena(@PathVariable String subpoenaId) {
        log.info("Deleting subpoena: {}", subpoenaId);

        return subpoenaRepository.findBySubpoenaId(subpoenaId)
                .map(subpoena -> {
                    subpoenaRepository.delete(subpoena);
                    return ResponseEntity.noContent().<Void>build();
                })
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Convert Subpoena entity to Response DTO
     */
    private SubpoenaResponse toResponse(Subpoena subpoena) {
        return SubpoenaResponse.builder()
                .subpoenaId(subpoena.getSubpoenaId())
                .customerId(subpoena.getCustomerId())
                .caseNumber(subpoena.getCaseNumber())
                .issuingCourt(subpoena.getIssuingCourt())
                .issuanceDate(subpoena.getIssuanceDate())
                .responseDeadline(subpoena.getResponseDeadline())
                .subpoenaType(subpoena.getSubpoenaType().name())
                .requestedRecords(subpoena.getRequestedRecords())
                .status(subpoena.getStatus().name())
                .completed(subpoena.isCompleted())
                .customerNotified(subpoena.isCustomerNotified())
                .customerNotificationDate(subpoena.getCustomerNotificationDate())
                .servingParty(subpoena.getServingParty())
                .courtJurisdiction(subpoena.getCourtJurisdiction())
                .attorneyName(subpoena.getAttorneyName())
                .attorneyContact(subpoena.getAttorneyContact())
                .totalRecordsProduced(subpoena.getTotalRecordsProduced())
                .batesRange(subpoena.getBatesRange())
                .createdAt(subpoena.getCreatedAt())
                .updatedAt(subpoena.getUpdatedAt())
                .createdBy(subpoena.getCreatedBy())
                .updatedBy(subpoena.getUpdatedBy())
                .build();
    }
}
