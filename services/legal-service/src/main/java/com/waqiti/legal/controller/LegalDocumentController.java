package com.waqiti.legal.controller;

import com.waqiti.legal.dto.request.CreateLegalDocumentRequest;
import com.waqiti.legal.dto.response.LegalDocumentResponse;
import com.waqiti.legal.domain.LegalDocument;
import com.waqiti.legal.repository.LegalDocumentRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/api/v1/legal/documents")
@RequiredArgsConstructor
public class LegalDocumentController {

    private final LegalDocumentRepository legalDocumentRepository;

    @PostMapping
    @PreAuthorize("hasAnyRole('LEGAL_ADMIN', 'LEGAL_OFFICER')")
    public ResponseEntity<LegalDocumentResponse> createDocument(@Valid @RequestBody CreateLegalDocumentRequest request) {
        log.info("Creating legal document: {}", request.getDocumentTitle());

        LegalDocument document = LegalDocument.builder()
                .documentId(java.util.UUID.randomUUID().toString())
                .documentType(LegalDocument.DocumentType.valueOf(request.getDocumentType()))
                .documentTitle(request.getDocumentTitle())
                .documentCategory(request.getDocumentCategory())
                .jurisdiction(request.getJurisdiction())
                .effectiveDate(request.getEffectiveDate())
                .expirationDate(request.getExpirationDate())
                .confidentialityLevel(request.getConfidentialityLevel() != null ?
                    LegalDocument.ConfidentialityLevel.valueOf(request.getConfidentialityLevel()) :
                    LegalDocument.ConfidentialityLevel.INTERNAL)
                .createdBy(request.getCreatedBy())
                .build();

        LegalDocument saved = legalDocumentRepository.save(document);
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(saved));
    }

    @GetMapping("/{documentId}")
    @PreAuthorize("hasAnyRole('LEGAL_ADMIN', 'LEGAL_OFFICER', 'LEGAL_VIEWER')")
    public ResponseEntity<LegalDocumentResponse> getDocument(@PathVariable String documentId) {
        return legalDocumentRepository.findByDocumentId(documentId)
                .map(this::toResponse)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('LEGAL_ADMIN', 'LEGAL_OFFICER', 'LEGAL_VIEWER')")
    public ResponseEntity<List<LegalDocumentResponse>> getAllDocuments() {
        List<LegalDocumentResponse> documents = legalDocumentRepository.findAll()
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
        return ResponseEntity.ok(documents);
    }

    @DeleteMapping("/{documentId}")
    @PreAuthorize("hasRole('LEGAL_ADMIN')")
    public ResponseEntity<Void> deleteDocument(@PathVariable String documentId) {
        return legalDocumentRepository.findByDocumentId(documentId)
                .map(doc -> {
                    legalDocumentRepository.delete(doc);
                    return ResponseEntity.noContent().<Void>build();
                })
                .orElse(ResponseEntity.notFound().build());
    }

    private LegalDocumentResponse toResponse(LegalDocument document) {
        return LegalDocumentResponse.builder()
                .documentId(document.getDocumentId())
                .documentType(document.getDocumentType().name())
                .documentTitle(document.getDocumentTitle())
                .documentCategory(document.getDocumentCategory())
                .jurisdiction(document.getJurisdiction())
                .documentStatus(document.getDocumentStatus() != null ? document.getDocumentStatus().name() : null)
                .effectiveDate(document.getEffectiveDate())
                .expirationDate(document.getExpirationDate())
                .confidentialityLevel(document.getConfidentialityLevel() != null ? document.getConfidentialityLevel().name() : null)
                .versionNumber(document.getVersionNumber())
                .createdAt(document.getCreatedAt())
                .updatedAt(document.getUpdatedAt())
                .createdBy(document.getCreatedBy())
                .updatedBy(document.getUpdatedBy())
                .build();
    }
}
