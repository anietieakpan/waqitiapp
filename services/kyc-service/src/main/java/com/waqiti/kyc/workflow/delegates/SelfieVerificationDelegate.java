package com.waqiti.kyc.workflow.delegates;

import com.waqiti.kyc.dto.VerificationResult;
import com.waqiti.kyc.integration.KYCProvider;
import com.waqiti.kyc.integration.KYCProviderFactory;
import com.waqiti.kyc.model.KYCApplication;
import com.waqiti.kyc.model.KYCDocument;
import com.waqiti.kyc.model.VerificationStatus;
import com.waqiti.kyc.repository.KYCApplicationRepository;
import com.waqiti.kyc.repository.KYCDocumentRepository;
import com.waqiti.kyc.service.DocumentStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.camunda.bpm.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Map;

@Slf4j
@Component("selfieVerificationDelegate")
@RequiredArgsConstructor
public class SelfieVerificationDelegate implements JavaDelegate {

    private final KYCProviderFactory kycProviderFactory;
    private final KYCApplicationRepository kycApplicationRepository;
    private final KYCDocumentRepository kycDocumentRepository;
    private final DocumentStorageService documentStorageService;

    @Override
    public void execute(DelegateExecution execution) throws Exception {
        log.info("Executing selfie verification for process instance: {}", execution.getProcessInstanceId());

        try {
            String userId = (String) execution.getVariable("userId");
            String kycApplicationId = (String) execution.getVariable("kycApplicationId");
            String providerName = (String) execution.getVariable("kycProvider");

            if (providerName == null) {
                providerName = "onfido"; // Default provider
            }

            // Get KYC application
            KYCApplication application = kycApplicationRepository.findById(kycApplicationId)
                    .orElseThrow(() -> new RuntimeException("KYC Application not found: " + kycApplicationId));

            // Get selfie document
            KYCDocument selfieDocument = kycDocumentRepository.findByKycApplicationIdAndDocumentType(
                    kycApplicationId, 
                    com.waqiti.kyc.model.DocumentType.SELFIE
            ).orElseThrow(() -> new RuntimeException("Selfie document not found"));

            // Get primary ID document for comparison
            KYCDocument idDocument = kycDocumentRepository.findByKycApplicationIdAndDocumentType(
                    kycApplicationId,
                    com.waqiti.kyc.model.DocumentType.PASSPORT
            ).or(() -> kycDocumentRepository.findByKycApplicationIdAndDocumentType(
                    kycApplicationId,
                    com.waqiti.kyc.model.DocumentType.DRIVERS_LICENSE
            )).or(() -> kycDocumentRepository.findByKycApplicationIdAndDocumentType(
                    kycApplicationId,
                    com.waqiti.kyc.model.DocumentType.NATIONAL_ID
            )).orElseThrow(() -> new RuntimeException("No ID document found for selfie comparison"));

            // Get KYC provider
            KYCProvider provider = kycProviderFactory.getProvider(providerName);

            // Prepare selfie verification data
            Map<String, Object> selfieData = Map.of(
                    "userId", userId,
                    "selfiePath", selfieDocument.getDocumentPath(),
                    "documentPath", idDocument.getDocumentPath(),
                    "documentType", idDocument.getDocumentType().toString(),
                    "livenessCheck", true
            );

            // Perform selfie verification with face match
            VerificationResult result = provider.verifySelfie(selfieData);

            // Update selfie document
            selfieDocument.setVerificationStatus(
                    result.isSuccess() ? VerificationStatus.VERIFIED : VerificationStatus.FAILED
            );
            selfieDocument.setVerificationScore(result.getScore());
            selfieDocument.setVerificationDetails(result.getDetails());
            selfieDocument.setVerifiedAt(LocalDateTime.now());
            kycDocumentRepository.save(selfieDocument);

            // Update application
            application.setSelfieVerificationStatus(selfieDocument.getVerificationStatus());
            application.setSelfieMatchScore(result.getScore());
            
            // Extract liveness score if available
            Map<String, Object> details = result.getDetails();
            if (details != null && details.containsKey("livenessScore")) {
                Integer livenessScore = (Integer) details.get("livenessScore");
                application.setLivenessScore(livenessScore);
                execution.setVariable("livenessScore", livenessScore);
            }

            application.setLastUpdated(LocalDateTime.now());
            kycApplicationRepository.save(application);

            // Set process variables
            execution.setVariable("selfieVerificationStatus", selfieDocument.getVerificationStatus().toString());
            execution.setVariable("selfieMatchScore", result.getScore());
            execution.setVariable("selfieVerificationSuccess", result.isSuccess());
            execution.setVariable("selfieVerificationDetails", result.getDetails());

            // Store face match report if available
            if (result.getReportUrl() != null) {
                String reportPath = documentStorageService.storeDocument(
                        userId,
                        "selfie-verification-report",
                        result.getReportUrl(),
                        "application/pdf"
                );
                execution.setVariable("selfieVerificationReportPath", reportPath);
            }

            log.info("Selfie verification completed for user: {} with status: {} and score: {}",
                    userId, result.isSuccess() ? "SUCCESS" : "FAILED", result.getScore());

        } catch (Exception e) {
            log.error("Error during selfie verification", e);
            
            // Set failure variables
            execution.setVariable("selfieVerificationStatus", VerificationStatus.FAILED.toString());
            execution.setVariable("selfieMatchScore", 0);
            execution.setVariable("selfieVerificationSuccess", false);
            execution.setVariable("selfieVerificationError", e.getMessage());
            
            throw e;
        }
    }
}