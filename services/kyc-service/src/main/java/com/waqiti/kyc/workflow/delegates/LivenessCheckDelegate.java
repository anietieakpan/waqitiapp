package com.waqiti.kyc.workflow.delegates;

import com.waqiti.kyc.dto.VerificationResult;
import com.waqiti.kyc.integration.KYCProvider;
import com.waqiti.kyc.integration.KYCProviderFactory;
import com.waqiti.kyc.model.KYCApplication;
import com.waqiti.kyc.model.KYCDocument;
import com.waqiti.kyc.model.VerificationStatus;
import com.waqiti.kyc.repository.KYCApplicationRepository;
import com.waqiti.kyc.repository.KYCDocumentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.camunda.bpm.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Map;

@Slf4j
@Component("livenessCheckDelegate")
@RequiredArgsConstructor
public class LivenessCheckDelegate implements JavaDelegate {

    private final KYCProviderFactory kycProviderFactory;
    private final KYCApplicationRepository kycApplicationRepository;
    private final KYCDocumentRepository kycDocumentRepository;

    @Override
    public void execute(DelegateExecution execution) throws Exception {
        log.info("Executing liveness check for process instance: {}", execution.getProcessInstanceId());

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

            // Get liveness check video/image
            KYCDocument livenessDocument = kycDocumentRepository.findByKycApplicationIdAndDocumentType(
                    kycApplicationId,
                    com.waqiti.kyc.model.DocumentType.LIVENESS_VIDEO
            ).or(() -> kycDocumentRepository.findByKycApplicationIdAndDocumentType(
                    kycApplicationId,
                    com.waqiti.kyc.model.DocumentType.SELFIE
            )).orElseThrow(() -> new RuntimeException("No liveness document found"));

            // Get KYC provider
            KYCProvider provider = kycProviderFactory.getProvider(providerName);

            // Prepare liveness check data
            Map<String, Object> livenessData = Map.of(
                    "userId", userId,
                    "documentPath", livenessDocument.getDocumentPath(),
                    "documentType", livenessDocument.getDocumentType().toString(),
                    "challengeType", "MOVEMENT", // HEAD_MOVEMENT, EYE_BLINK, SMILE
                    "qualityChecks", true,
                    "spoofingDetection", true
            );

            // Perform liveness check
            VerificationResult result = provider.performLivenessCheck(livenessData);

            // Update liveness document
            livenessDocument.setVerificationStatus(
                    result.isSuccess() ? VerificationStatus.VERIFIED : VerificationStatus.FAILED
            );
            livenessDocument.setVerificationScore(result.getScore());
            livenessDocument.setVerificationDetails(result.getDetails());
            livenessDocument.setVerifiedAt(LocalDateTime.now());
            kycDocumentRepository.save(livenessDocument);

            // Update application
            application.setLivenessCheckStatus(livenessDocument.getVerificationStatus());
            application.setLivenessScore(result.getScore());
            application.setLastUpdated(LocalDateTime.now());

            // Extract detailed liveness results
            Map<String, Object> details = result.getDetails();
            if (details != null) {
                Boolean isLive = (Boolean) details.get("isLive");
                Integer qualityScore = (Integer) details.get("qualityScore");
                String failureReason = (String) details.get("failureReason");
                
                execution.setVariable("isLive", isLive != null ? isLive : false);
                execution.setVariable("livenessQualityScore", qualityScore != null ? qualityScore : 0);
                if (failureReason != null) {
                    execution.setVariable("livenessFailureReason", failureReason);
                }
            }

            kycApplicationRepository.save(application);

            // Set process variables
            execution.setVariable("livenessCheckStatus", livenessDocument.getVerificationStatus().toString());
            execution.setVariable("livenessScore", result.getScore());
            execution.setVariable("livenessCheckSuccess", result.isSuccess());
            execution.setVariable("livenessCheckDetails", result.getDetails());

            log.info("Liveness check completed for user: {} with status: {} and score: {}",
                    userId, result.isSuccess() ? "SUCCESS" : "FAILED", result.getScore());

        } catch (Exception e) {
            log.error("Error during liveness check", e);
            
            // Set failure variables
            execution.setVariable("livenessCheckStatus", VerificationStatus.FAILED.toString());
            execution.setVariable("livenessScore", 0);
            execution.setVariable("livenessCheckSuccess", false);
            execution.setVariable("livenessCheckError", e.getMessage());
            execution.setVariable("isLive", false);
            
            throw e;
        }
    }
}