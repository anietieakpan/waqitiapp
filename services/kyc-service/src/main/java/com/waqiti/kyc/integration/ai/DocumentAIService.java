package com.waqiti.kyc.integration.ai;

import com.waqiti.kyc.service.KYCDocumentVerificationService.DocumentType;
import com.waqiti.kyc.service.KYCDocumentVerificationService.AuthenticityResult;

public interface DocumentAIService {
    
    AuthenticityResult verifyAuthenticity(byte[] documentBytes, DocumentType documentType);
    
    boolean detectTampering(byte[] documentBytes);
    
    boolean isAvailable();
    
    String getProviderName();
}