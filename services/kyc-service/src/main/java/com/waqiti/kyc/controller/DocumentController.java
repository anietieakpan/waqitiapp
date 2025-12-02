package com.waqiti.kyc.controller;

import com.waqiti.common.security.SecurityContextUtil;
import com.waqiti.common.security.FileUploadValidator;
import com.waqiti.common.idempotency.IdempotencyService;
import com.waqiti.kyc.dto.request.DocumentUploadRequest;
import com.waqiti.kyc.dto.response.DocumentResponse;
import com.waqiti.kyc.service.DocumentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/kyc")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "KYC Documents", description = "Document management for KYC verification")
@SecurityRequirement(name = "bearerAuth")
public class DocumentController {

    private final DocumentService documentService;
    private final FileUploadValidator fileUploadValidator;
    private final IdempotencyService idempotencyService;

    @Operation(summary = "Upload document for verification")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "201", description = "Document uploaded successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid document"),
        @ApiResponse(responseCode = "413", description = "Document too large")
    })
    @PostMapping(value = "/verifications/{verificationId}/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('USER') or hasRole('ADMIN')")
    public ResponseEntity<DocumentResponse> uploadDocument(
            @PathVariable String verificationId,
            @RequestParam("file") MultipartFile file,
            @RequestParam("documentType") String documentType,
            @RequestParam(required = false) String documentNumber,
            @RequestParam(required = false) String issuingCountry,
            @RequestParam(required = false) String expiryDate,
            @RequestParam(required = false, defaultValue = "true") boolean isFront,
            @RequestParam(required = false) String description,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        
        // SECURITY FIX: Get authenticated user
        UUID authenticatedUserId = SecurityContextUtil.getAuthenticatedUserId();
        
        log.info("Uploading document for verification: {}, type: {} by user: {}", 
                verificationId, documentType, authenticatedUserId);
        
        // SECURITY FIX: Validate file upload
        FileUploadValidator.ValidationResult validationResult = fileUploadValidator.validateKYCDocument(file);
        if (!validationResult.isValid()) {
            log.warn("File validation failed for user {}: {}", authenticatedUserId, validationResult.getErrors());
            return ResponseEntity.badRequest()
                    .header("X-Validation-Errors", String.join(", ", validationResult.getErrors()))
                    .build();
        }
        
        // Generate idempotency key if not provided
        if (idempotencyKey == null || idempotencyKey.trim().isEmpty()) {
            idempotencyKey = IdempotencyService.FinancialIdempotencyKeys.documentUpload(
                    authenticatedUserId.toString(), verificationId, documentType
            );
        }
        
        final String finalIdempotencyKey = idempotencyKey;
        
        // Use idempotency service to prevent duplicate uploads
        DocumentResponse response = idempotencyService.executeIdempotent(idempotencyKey, () -> {
            DocumentUploadRequest request = DocumentUploadRequest.builder()
                    .file(file)
                    .documentType(com.waqiti.kyc.domain.VerificationDocument.DocumentType.valueOf(documentType))
                    .documentNumber(documentNumber)
                    .issuingCountry(issuingCountry)
                    .expiryDate(expiryDate)
                    .isFront(isFront)
                    .description(description)
                    .userId(authenticatedUserId) // SECURITY FIX: Set authenticated user
                    .build();
                    
            return documentService.uploadDocument(verificationId, request);
        });
        
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Operation(summary = "Upload multiple documents")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "201", description = "Documents uploaded successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid documents")
    })
    @PostMapping(value = "/verifications/{verificationId}/documents/batch", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('USER') or hasRole('ADMIN')")
    public ResponseEntity<List<DocumentResponse>> uploadMultipleDocuments(
            @PathVariable String verificationId,
            @RequestParam("files") List<MultipartFile> files,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        
        // SECURITY FIX: Get authenticated user
        UUID authenticatedUserId = SecurityContextUtil.getAuthenticatedUserId();
        
        log.info("Uploading {} documents for verification: {} by user: {}", 
                files.size(), verificationId, authenticatedUserId);
        
        // SECURITY FIX: Validate all files before processing
        for (MultipartFile file : files) {
            FileUploadValidator.ValidationResult validationResult = fileUploadValidator.validateKYCDocument(file);
            if (!validationResult.isValid()) {
                log.warn("Batch upload failed - file validation error for user {}: {}", 
                        authenticatedUserId, validationResult.getErrors());
                return ResponseEntity.badRequest()
                        .header("X-Validation-Errors", String.join(", ", validationResult.getErrors()))
                        .build();
            }
        }
        
        // Generate idempotency key if not provided
        if (idempotencyKey == null || idempotencyKey.trim().isEmpty()) {
            idempotencyKey = IdempotencyService.FinancialIdempotencyKeys.documentUpload(
                    authenticatedUserId.toString(), verificationId, "BATCH_" + files.size()
            );
        }
        
        // Use idempotency service
        List<DocumentResponse> responses = idempotencyService.executeIdempotent(idempotencyKey, () -> {
            return documentService.uploadMultipleDocuments(verificationId, files, authenticatedUserId);
        });
        
        return ResponseEntity.status(HttpStatus.CREATED).body(responses);
    }

    @Operation(summary = "Get document by ID")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Document found"),
        @ApiResponse(responseCode = "404", description = "Document not found")
    })
    @GetMapping("/documents/{documentId}")
    @PreAuthorize("hasRole('USER') or hasRole('ADMIN') or hasRole('COMPLIANCE')")
    public ResponseEntity<DocumentResponse> getDocument(
            @PathVariable String documentId) {
        
        // SECURITY FIX: Get authenticated user and validate access
        UUID authenticatedUserId = SecurityContextUtil.getAuthenticatedUserId();
        
        log.info("Fetching document: {} by user: {}", documentId, authenticatedUserId);
        
        // SECURITY FIX: Verify document ownership unless admin/compliance
        if (!SecurityContextUtil.hasAnyRole("ADMIN", "COMPLIANCE")) {
            documentService.verifyDocumentOwnership(documentId, authenticatedUserId);
        }
        
        DocumentResponse response = documentService.getDocument(documentId);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Get all documents for verification")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Documents retrieved successfully")
    })
    @GetMapping("/verifications/{verificationId}/documents")
    @PreAuthorize("hasRole('USER') or hasRole('ADMIN') or hasRole('COMPLIANCE')")
    public ResponseEntity<List<DocumentResponse>> getVerificationDocuments(
            @PathVariable String verificationId) {
        log.info("Fetching documents for verification: {}", verificationId);
        List<DocumentResponse> documents = documentService.getVerificationDocuments(verificationId);
        return ResponseEntity.ok(documents);
    }

    @Operation(summary = "Download document")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Document content returned"),
        @ApiResponse(responseCode = "404", description = "Document not found")
    })
    @GetMapping("/documents/{documentId}/download")
    @PreAuthorize("hasRole('USER') or hasRole('ADMIN') or hasRole('COMPLIANCE')")
    public ResponseEntity<Resource> downloadDocument(
            @PathVariable String documentId) {
        log.info("Downloading document: {}", documentId);
        
        DocumentResponse document = documentService.getDocument(documentId);
        byte[] content = documentService.getDocumentContent(documentId);
        
        ByteArrayResource resource = new ByteArrayResource(content);
        
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + document.getFileName() + "\"")
                .contentType(MediaType.parseMediaType(document.getContentType()))
                .contentLength(content.length)
                .body(resource);
    }

    @Operation(summary = "Get document download URL")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Download URL generated"),
        @ApiResponse(responseCode = "404", description = "Document not found")
    })
    @GetMapping("/documents/{documentId}/download-url")
    @PreAuthorize("hasRole('USER') or hasRole('ADMIN')")
    public ResponseEntity<Map<String, String>> getDocumentDownloadUrl(
            @PathVariable String documentId) {
        log.info("Generating download URL for document: {}", documentId);
        String downloadUrl = documentService.getDocumentDownloadUrl(documentId);
        return ResponseEntity.ok(Map.of("downloadUrl", downloadUrl));
    }

    @Operation(summary = "Verify document")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Document verified successfully"),
        @ApiResponse(responseCode = "400", description = "Document verification failed")
    })
    @PostMapping("/documents/{documentId}/verify")
    @PreAuthorize("hasRole('COMPLIANCE') or hasRole('ADMIN')")
    public ResponseEntity<DocumentResponse> verifyDocument(
            @PathVariable String documentId) {
        log.info("Verifying document: {}", documentId);
        DocumentResponse response = documentService.verifyDocument(documentId);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Reject document")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Document rejected successfully")
    })
    @PostMapping("/documents/{documentId}/reject")
    @PreAuthorize("hasRole('COMPLIANCE') or hasRole('ADMIN')")
    public ResponseEntity<DocumentResponse> rejectDocument(
            @PathVariable String documentId,
            @RequestParam String reason) {
        log.info("Rejecting document: {} with reason: {}", documentId, reason);
        DocumentResponse response = documentService.rejectDocument(documentId, reason);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Extract document data")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Data extracted successfully"),
        @ApiResponse(responseCode = "404", description = "Document not found")
    })
    @GetMapping("/documents/{documentId}/extract")
    @PreAuthorize("hasRole('COMPLIANCE') or hasRole('ADMIN')")
    public ResponseEntity<Map<String, String>> extractDocumentData(
            @PathVariable String documentId) {
        log.info("Extracting data from document: {}", documentId);
        Map<String, String> extractedData = documentService.extractDocumentData(documentId);
        return ResponseEntity.ok(extractedData);
    }

    @Operation(summary = "Delete document")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "204", description = "Document deleted successfully"),
        @ApiResponse(responseCode = "404", description = "Document not found")
    })
    @DeleteMapping("/documents/{documentId}")
    @PreAuthorize("hasRole('USER') or hasRole('ADMIN')")
    public ResponseEntity<Void> deleteDocument(
            @PathVariable String documentId) {
        log.info("Deleting document: {}", documentId);
        documentService.deleteDocument(documentId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Get document statistics")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Statistics retrieved successfully")
    })
    @GetMapping("/verifications/{verificationId}/documents/statistics")
    @PreAuthorize("hasRole('COMPLIANCE') or hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> getDocumentStatistics(
            @PathVariable String verificationId) {
        log.info("Fetching document statistics for verification: {}", verificationId);
        Map<String, Object> statistics = documentService.getDocumentStatistics(verificationId);
        return ResponseEntity.ok(statistics);
    }
}