package com.waqiti.kyc.service.impl;

import com.waqiti.kyc.config.KYCProperties;
import com.waqiti.kyc.domain.VerificationDocument;
import com.waqiti.kyc.dto.request.DocumentUploadRequest;
import com.waqiti.kyc.dto.response.DocumentResponse;
import com.waqiti.kyc.exception.DocumentException;
import com.waqiti.kyc.exception.KYCVerificationException;
import com.waqiti.kyc.repository.VerificationDocumentRepository;
import com.waqiti.kyc.service.DocumentService;
import com.waqiti.kyc.service.KYCProviderService;
import com.waqiti.kyc.service.StorageService;
import com.waqiti.common.event.EventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.time.LocalDateTime;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Implementation of DocumentService for managing KYC verification documents
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class DocumentServiceImpl implements DocumentService {
    
    private final VerificationDocumentRepository documentRepository;
    private final StorageService storageService;
    private final KYCProviderService providerService;
    private final EventPublisher eventPublisher;
    private final KYCProperties kycProperties;
    
    private static final Set<String> ALLOWED_FILE_TYPES = Set.of(
        "image/jpeg", "image/jpg", "image/png", "application/pdf"
    );
    
    private static final Map<String, Long> MAX_FILE_SIZES = Map.of(
        "image/jpeg", 10L * 1024 * 1024, // 10MB
        "image/jpg", 10L * 1024 * 1024,  // 10MB
        "image/png", 10L * 1024 * 1024,  // 10MB
        "application/pdf", 25L * 1024 * 1024 // 25MB
    );
    
    @Override
    public DocumentResponse uploadDocument(String verificationId, DocumentUploadRequest request) {
        log.info("Uploading document via base64 for verification: {}, type: {}", verificationId, request.getDocumentType());
        
        try {
            // Decode base64 document data
            byte[] documentData = Base64.getDecoder().decode(request.getDocumentData());
            
            // Validate document size
            if (documentData.length > MAX_FILE_SIZE_BYTES) {
                throw new DocumentValidationException("Document size exceeds maximum allowed: " + MAX_FILE_SIZE_BYTES + " bytes");
            }
            
            // Validate document type
            if (!SUPPORTED_DOCUMENT_TYPES.contains(request.getDocumentType())) {
                throw new DocumentValidationException("Unsupported document type: " + request.getDocumentType());
            }
            
            // Get verification session
            VerificationSession session = verificationRepository.findById(verificationId)
                .orElseThrow(() -> new VerificationNotFoundException("Verification session not found: " + verificationId));
            
            // Get appropriate KYC provider
            KYCProvider provider = providerFactory.getProvider(session.getProviderId());
            
            // Upload document through provider
            String documentId = provider.uploadDocument(session.getProviderSessionId(), 
                documentData, request.getDocumentType());
            
            // Create document record
            Document document = Document.builder()
                .verificationId(verificationId)
                .providerId(session.getProviderId())
                .providerDocumentId(documentId)
                .documentType(request.getDocumentType())
                .status(DocumentStatus.UPLOADED)
                .filename(request.getFilename())
                .contentType(detectContentType(documentData))
                .fileSize((long) documentData.length)
                .uploadedAt(Instant.now())
                .build();
            
            document = documentRepository.save(document);
            
            log.info("Successfully uploaded document: {} for verification: {}", document.getId(), verificationId);
            
            return DocumentResponse.builder()
                .documentId(document.getId())
                .verificationId(verificationId)
                .documentType(document.getDocumentType())
                .status(document.getStatus())
                .filename(document.getFilename())
                .fileSize(document.getFileSize())
                .uploadedAt(document.getUploadedAt())
                .build();
                
        } catch (IllegalArgumentException e) {
            log.error("Invalid base64 document data for verification: {}", verificationId, e);
            throw new DocumentValidationException("Invalid base64 document data", e);
        } catch (Exception e) {
            log.error("Failed to upload document for verification: {}", verificationId, e);
            throw new DocumentUploadException("Failed to upload document", e);
        }
    }
    
    @Override
    public DocumentResponse uploadDocument(String verificationId, MultipartFile file, String documentType) {
        log.info("Uploading document for verification: {}, type: {}", verificationId, documentType);
        
        // Validate file
        if (!validateDocument(file, documentType)) {
            throw new DocumentException("Invalid document file");
        }
        
        try {
            // Create document entity
            VerificationDocument document = new VerificationDocument();
            document.setVerificationId(verificationId);
            document.setDocumentType(VerificationDocument.DocumentType.valueOf(documentType.toUpperCase()));
            document.setFileName(file.getOriginalFilename());
            document.setFileSize(file.getSize());
            document.setMimeType(file.getContentType());
            document.setStatus(VerificationDocument.DocumentStatus.PENDING);
            document.setUploadedAt(LocalDateTime.now());
            
            // Read file content
            byte[] fileContent = file.getBytes();
            
            // Encrypt if configured
            if (kycProperties.getSecurity().isEncryptDocuments()) {
                fileContent = encryptDocument(fileContent);
                document.setEncrypted(true);
            }
            
            // Apply watermark if configured
            if (kycProperties.getSecurity().isWatermarkDocuments() && isImage(file.getContentType())) {
                fileContent = applyWatermark(fileContent, kycProperties.getSecurity().getWatermarkText());
                document.setWatermarked(true);
            }
            
            // Upload to storage
            String storagePath = generateStoragePath(verificationId, document.getId().toString(), file.getOriginalFilename());
            String storageUrl = storageService.uploadFile(storagePath, fileContent, file.getContentType());
            
            document.setStorageUrl(storageUrl);
            document.setStoragePath(storagePath);
            
            // Save document
            document = documentRepository.save(document);
            
            // Upload to KYC provider if needed
            try {
                String providerDocumentId = providerService.uploadDocumentToProvider(
                    verificationId, fileContent, documentType
                );
                document.setProviderReference(providerDocumentId);
                documentRepository.save(document);
            } catch (Exception e) {
                log.warn("Failed to upload document to provider: {}", e.getMessage());
            }
            
            log.info("Document uploaded successfully: {}", document.getId());
            return mapToResponse(document);
            
        } catch (Exception e) {
            log.error("Failed to upload document", e);
            throw new DocumentException("Failed to upload document: " + e.getMessage());
        }
    }
    
    @Override
    public DocumentResponse getDocument(String documentId) {
        VerificationDocument document = documentRepository.findById(UUID.fromString(documentId))
                .orElseThrow(() -> new DocumentException("Document not found: " + documentId));
        
        return mapToResponse(document);
    }
    
    @Override
    public List<DocumentResponse> getVerificationDocuments(String verificationId) {
        List<VerificationDocument> documents = documentRepository.findByVerificationId(verificationId);
        return documents.stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }
    
    @Override
    public void deleteDocument(String documentId) {
        VerificationDocument document = documentRepository.findById(UUID.fromString(documentId))
                .orElseThrow(() -> new DocumentException("Document not found: " + documentId));
        
        // Delete from storage
        try {
            storageService.deleteFile(document.getStoragePath());
        } catch (Exception e) {
            log.error("Failed to delete document from storage", e);
        }
        
        // Delete from database
        documentRepository.delete(document);
        
        log.info("Document deleted: {}", documentId);
    }
    
    @Override
    public DocumentResponse verifyDocument(String documentId) {
        VerificationDocument document = documentRepository.findById(UUID.fromString(documentId))
                .orElseThrow(() -> new DocumentException("Document not found: " + documentId));
        
        document.setStatus(VerificationDocument.DocumentStatus.VERIFIED);
        document.setVerifiedAt(LocalDateTime.now());
        document = documentRepository.save(document);
        
        return mapToResponse(document);
    }
    
    @Override
    public DocumentResponse rejectDocument(String documentId, String reason) {
        VerificationDocument document = documentRepository.findById(UUID.fromString(documentId))
                .orElseThrow(() -> new DocumentException("Document not found: " + documentId));
        
        document.setStatus(VerificationDocument.DocumentStatus.REJECTED);
        document.setRejectionReason(reason);
        document.setVerifiedAt(LocalDateTime.now());
        document = documentRepository.save(document);
        
        return mapToResponse(document);
    }
    
    @Override
    public Map<String, String> extractDocumentData(String documentId) {
        VerificationDocument document = documentRepository.findById(UUID.fromString(documentId))
                .orElseThrow(() -> new DocumentException("Document not found: " + documentId));
        
        if (document.getProviderReference() != null) {
            try {
                return providerService.extractDocumentDataFromProvider(document.getProviderReference());
            } catch (Exception e) {
                log.error("Failed to extract document data from provider", e);
            }
        }
        
        // Return existing extracted data if available
        return document.getExtractedData() != null ? document.getExtractedData() : Map.of();
    }
    
    @Override
    public String getDocumentDownloadUrl(String documentId) {
        VerificationDocument document = documentRepository.findById(UUID.fromString(documentId))
                .orElseThrow(() -> new DocumentException("Document not found: " + documentId));
        
        // Generate presigned URL with expiry
        int expiryMinutes = kycProperties.getSecurity().getDownloadUrlExpiryMinutes();
        return storageService.generatePresignedUrl(document.getStoragePath(), expiryMinutes);
    }
    
    @Override
    public byte[] getDocumentContent(String documentId) {
        VerificationDocument document = documentRepository.findById(UUID.fromString(documentId))
                .orElseThrow(() -> new DocumentException("Document not found: " + documentId));
        
        try {
            byte[] content = storageService.downloadFile(document.getStoragePath());
            
            // Decrypt if encrypted
            if (document.isEncrypted()) {
                content = decryptDocument(content);
            }
            
            return content;
        } catch (Exception e) {
            log.error("Failed to download document content", e);
            throw new DocumentException("Failed to download document: " + e.getMessage());
        }
    }
    
    @Override
    public boolean validateDocument(MultipartFile file, String documentType) {
        // Check file size
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_FILE_TYPES.contains(contentType)) {
            log.warn("Invalid file type: {}", contentType);
            return false;
        }
        
        Long maxSize = MAX_FILE_SIZES.get(contentType);
        if (maxSize != null && file.getSize() > maxSize) {
            log.warn("File too large: {} bytes (max: {} bytes)", file.getSize(), maxSize);
            return false;
        }
        
        // Additional validation based on document type
        try {
            switch (documentType.toUpperCase()) {
                case "PASSPORT":
                case "DRIVER_LICENSE":
                case "ID_CARD":
                    // These should be images
                    return isImage(contentType);
                    
                case "PROOF_OF_ADDRESS":
                case "BANK_STATEMENT":
                    // These can be images or PDFs
                    return true;
                    
                default:
                    return true;
            }
        } catch (Exception e) {
            log.error("Error validating document", e);
            return false;
        }
    }
    
    @Override
    public boolean isDocumentExpired(String documentId) {
        VerificationDocument document = documentRepository.findById(UUID.fromString(documentId))
                .orElseThrow(() -> new DocumentException("Document not found: " + documentId));
        
        if (document.getExpiryDate() != null) {
            return document.getExpiryDate().isBefore(LocalDateTime.now());
        }
        
        // Check based on document type and age
        int expiryDays = kycProperties.getVerification().getDocumentExpiryDays();
        LocalDateTime expiryThreshold = LocalDateTime.now().minusDays(expiryDays);
        
        return document.getUploadedAt().isBefore(expiryThreshold);
    }
    
    @Override
    public void markDocumentAsExpired(String documentId) {
        VerificationDocument document = documentRepository.findById(UUID.fromString(documentId))
                .orElseThrow(() -> new DocumentException("Document not found: " + documentId));
        
        document.setStatus(VerificationDocument.DocumentStatus.EXPIRED);
        document.setExpiryDate(LocalDateTime.now());
        documentRepository.save(document);
    }
    
    @Override
    public List<DocumentResponse> uploadMultipleDocuments(String verificationId, List<MultipartFile> files) {
        return files.stream()
                .map(file -> {
                    try {
                        String documentType = inferDocumentType(file.getOriginalFilename());
                        return uploadDocument(verificationId, file, documentType);
                    } catch (Exception e) {
                        log.error("KYC_DOCUMENT_UPLOAD_FAILED: Failed to upload file: {} for verification: {}. Error: {}", 
                                 file.getOriginalFilename(), verificationId, e.getMessage(), e);
                        
                        // Create a failed document response instead of returning null
                        // This helps track failed uploads in the KYC process
                        return DocumentResponse.builder()
                            .verificationId(verificationId)
                            .fileName(file.getOriginalFilename())
                            .documentType(inferDocumentType(file.getOriginalFilename()))
                            .uploadStatus("FAILED")
                            .uploadedAt(LocalDateTime.now())
                            .errorMessage(e.getMessage())
                            .requiresManualReview(true)
                            .build();
                    }
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }
    
    @Override
    public void deleteVerificationDocuments(String verificationId) {
        List<VerificationDocument> documents = documentRepository.findByVerificationId(verificationId);
        
        for (VerificationDocument document : documents) {
            try {
                storageService.deleteFile(document.getStoragePath());
            } catch (Exception e) {
                log.error("Failed to delete document from storage: {}", document.getId(), e);
            }
        }
        
        documentRepository.deleteAll(documents);
        log.info("Deleted {} documents for verification: {}", documents.size(), verificationId);
    }
    
    @Override
    public Map<String, Object> getDocumentStatistics(String verificationId) {
        List<VerificationDocument> documents = documentRepository.findByVerificationId(verificationId);
        
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalDocuments", documents.size());
        stats.put("verifiedDocuments", documents.stream()
                .filter(d -> d.getStatus() == VerificationDocument.DocumentStatus.VERIFIED)
                .count());
        stats.put("pendingDocuments", documents.stream()
                .filter(d -> d.getStatus() == VerificationDocument.DocumentStatus.PENDING)
                .count());
        stats.put("rejectedDocuments", documents.stream()
                .filter(d -> d.getStatus() == VerificationDocument.DocumentStatus.REJECTED)
                .count());
        stats.put("totalSize", documents.stream()
                .mapToLong(VerificationDocument::getFileSize)
                .sum());
        
        // Group by document type
        Map<String, Long> byType = documents.stream()
                .collect(Collectors.groupingBy(
                        d -> d.getDocumentType().toString(),
                        Collectors.counting()
                ));
        stats.put("documentsByType", byType);
        
        return stats;
    }
    
    // Helper methods
    
    private DocumentResponse mapToResponse(VerificationDocument document) {
        return DocumentResponse.builder()
                .id(document.getId().toString())
                .verificationId(document.getVerificationId())
                .documentType(document.getDocumentType().toString())
                .fileName(document.getFileName())
                .fileSize(document.getFileSize())
                .mimeType(document.getMimeType())
                .status(document.getStatus().toString())
                .uploadedAt(document.getUploadedAt())
                .verifiedAt(document.getVerifiedAt())
                .expiryDate(document.getExpiryDate())
                .rejectionReason(document.getRejectionReason())
                .extractedData(document.getExtractedData())
                .downloadUrl(document.getStatus() == VerificationDocument.DocumentStatus.VERIFIED ? 
                        getDocumentDownloadUrl(document.getId().toString()) : null)
                .build();
    }
    
    private String generateStoragePath(String verificationId, String documentId, String fileName) {
        String extension = "";
        int lastDot = fileName.lastIndexOf('.');
        if (lastDot > 0) {
            extension = fileName.substring(lastDot);
        }
        
        return String.format("kyc/%s/%s%s", verificationId, documentId, extension);
    }
    
    private boolean isImage(String contentType) {
        return contentType != null && contentType.startsWith("image/");
    }
    
    private String inferDocumentType(String fileName) {
        String lowerName = fileName.toLowerCase();
        
        if (lowerName.contains("passport")) {
            return "PASSPORT";
        } else if (lowerName.contains("driver") || lowerName.contains("license")) {
            return "DRIVER_LICENSE";
        } else if (lowerName.contains("id") || lowerName.contains("identity")) {
            return "ID_CARD";
        } else if (lowerName.contains("address") || lowerName.contains("utility") || lowerName.contains("bill")) {
            return "PROOF_OF_ADDRESS";
        } else if (lowerName.contains("bank") || lowerName.contains("statement")) {
            return "BANK_STATEMENT";
        } else {
            return "OTHER";
        }
    }
    
    private byte[] encryptDocument(byte[] data) {
        try {
            String encryptionKey = kycProperties.getStorage().getEncryptionKey();
            SecretKeySpec keySpec = new SecretKeySpec(encryptionKey.getBytes(), "AES");
            Cipher cipher = Cipher.getInstance("AES");
            cipher.init(Cipher.ENCRYPT_MODE, keySpec);
            return cipher.doFinal(data);
        } catch (Exception e) {
            log.error("Failed to encrypt document", e);
            throw new DocumentException("Failed to encrypt document");
        }
    }
    
    private byte[] decryptDocument(byte[] encryptedData) {
        try {
            String encryptionKey = kycProperties.getStorage().getEncryptionKey();
            SecretKeySpec keySpec = new SecretKeySpec(encryptionKey.getBytes(), "AES");
            Cipher cipher = Cipher.getInstance("AES");
            cipher.init(Cipher.DECRYPT_MODE, keySpec);
            return cipher.doFinal(encryptedData);
        } catch (Exception e) {
            log.error("Failed to decrypt document", e);
            throw new DocumentException("Failed to decrypt document");
        }
    }
    
    private byte[] applyWatermark(byte[] imageData, String watermarkText) {
        try {
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(imageData));
            
            Graphics2D g2d = (Graphics2D) image.getGraphics();
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            
            // Configure watermark
            AlphaComposite alphaChannel = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.3f);
            g2d.setComposite(alphaChannel);
            g2d.setColor(Color.GRAY);
            g2d.setFont(new Font("Arial", Font.BOLD, 64));
            
            // Calculate position
            FontMetrics fontMetrics = g2d.getFontMetrics();
            Rectangle2D rect = fontMetrics.getStringBounds(watermarkText, g2d);
            
            int centerX = (image.getWidth() - (int) rect.getWidth()) / 2;
            int centerY = image.getHeight() / 2;
            
            // Draw watermark
            g2d.drawString(watermarkText, centerX, centerY);
            g2d.dispose();
            
            // Convert back to bytes
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(image, "png", baos);
            return baos.toByteArray();
            
        } catch (Exception e) {
            log.error("Failed to apply watermark", e);
            return imageData; // Return original if watermarking fails
        }
    }
}