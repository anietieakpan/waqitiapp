package com.waqiti.kyc.service;

import com.waqiti.kyc.dto.request.DocumentUploadRequest;
import com.waqiti.kyc.dto.response.DocumentResponse;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

public interface DocumentService {
    
    // Document Operations
    DocumentResponse uploadDocument(String verificationId, DocumentUploadRequest request);
    
    DocumentResponse uploadDocument(String verificationId, MultipartFile file, String documentType);
    
    DocumentResponse getDocument(String documentId);
    
    List<DocumentResponse> getVerificationDocuments(String verificationId);
    
    void deleteDocument(String documentId);
    
    // Document Verification
    DocumentResponse verifyDocument(String documentId);
    
    DocumentResponse rejectDocument(String documentId, String reason);
    
    Map<String, String> extractDocumentData(String documentId);
    
    // Document Storage
    String getDocumentDownloadUrl(String documentId);
    
    byte[] getDocumentContent(String documentId);
    
    // Document Validation
    boolean validateDocument(MultipartFile file, String documentType);
    
    boolean isDocumentExpired(String documentId);
    
    void markDocumentAsExpired(String documentId);
    
    // Batch Operations
    List<DocumentResponse> uploadMultipleDocuments(String verificationId, List<MultipartFile> files);
    
    void deleteVerificationDocuments(String verificationId);
    
    // Analytics
    Map<String, Object> getDocumentStatistics(String verificationId);
}