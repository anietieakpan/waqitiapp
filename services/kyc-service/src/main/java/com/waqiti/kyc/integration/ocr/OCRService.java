package com.waqiti.kyc.integration.ocr;

import com.waqiti.kyc.service.KYCDocumentVerificationService;
import com.waqiti.kyc.service.KYCDocumentVerificationService.DocumentType;
import com.waqiti.kyc.service.KYCDocumentVerificationService.OCRResult;

public interface OCRService {
    
    OCRResult extractText(byte[] documentBytes, DocumentType documentType);
    
    boolean isAvailable();
    
    String getProviderName();
}