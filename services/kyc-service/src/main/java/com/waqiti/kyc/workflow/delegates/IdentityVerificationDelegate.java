package com.waqiti.kyc.workflow.delegates;

import com.waqiti.kyc.dto.VerificationResult;
import com.waqiti.kyc.integration.KYCProvider;
import com.waqiti.kyc.integration.KYCProviderFactory;
import com.waqiti.kyc.model.KYCApplication;
import com.waqiti.kyc.model.VerificationStatus;
import com.waqiti.kyc.repository.KYCApplicationRepository;
import com.waqiti.kyc.service.DocumentStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.camunda.bpm.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Map;

@Slf4j
@Component("identityVerificationDelegate")
@RequiredArgsConstructor
public class IdentityVerificationDelegate implements JavaDelegate {

    private final KYCProviderFactory kycProviderFactory;
    private final KYCApplicationRepository kycApplicationRepository;
    private final DocumentStorageService documentStorageService;

    @Override
    public void execute(DelegateExecution execution) throws Exception {
        log.info("Executing identity verification for process instance: {}", execution.getProcessInstanceId());

        try {
            // Get variables from process
            String userId = (String) execution.getVariable("userId");
            String kycApplicationId = (String) execution.getVariable("kycApplicationId");
            String providerName = (String) execution.getVariable("kycProvider");

            if (providerName == null) {
                providerName = "onfido"; // Default provider
            }

            // Get KYC application
            KYCApplication application = kycApplicationRepository.findById(kycApplicationId)
                    .orElseThrow(() -> new RuntimeException("KYC Application not found: " + kycApplicationId));

            // Get KYC provider
            KYCProvider provider = kycProviderFactory.getProvider(providerName);

            // Prepare identity data
            Map<String, Object> identityData = Map.of(
                    "firstName", execution.getVariable("firstName"),
                    "lastName", execution.getVariable("lastName"),
                    "dateOfBirth", execution.getVariable("dateOfBirth"),
                    "email", execution.getVariable("email"),
                    "phone", execution.getVariable("phone"),
                    "address", Map.of(
                            "line1", execution.getVariable("addressLine1"),
                            "line2", execution.getVariable("addressLine2"),
                            "city", execution.getVariable("city"),
                            "state", execution.getVariable("state"),
                            "postalCode", execution.getVariable("postalCode"),
                            "country", execution.getVariable("country")
                    )
            );

            // Perform identity verification
            VerificationResult result = provider.verifyIdentity(userId, identityData);

            // Update application status
            application.setIdentityVerificationStatus(
                    result.isSuccess() ? VerificationStatus.VERIFIED : VerificationStatus.FAILED
            );
            application.setIdentityVerificationScore(result.getScore());
            application.setIdentityVerificationDetails(result.getDetails());
            application.setLastUpdated(LocalDateTime.now());

            // Save updated application
            kycApplicationRepository.save(application);

            // Set process variables for downstream tasks
            execution.setVariable("identityVerificationStatus", application.getIdentityVerificationStatus().toString());
            execution.setVariable("identityVerificationScore", result.getScore());
            execution.setVariable("identityVerificationDetails", result.getDetails());
            execution.setVariable("identityVerificationSuccess", result.isSuccess());

            // Store verification report if available
            if (result.getReportUrl() != null) {
                String reportPath = documentStorageService.storeDocument(
                        userId,
                        "identity-verification-report",
                        result.getReportUrl(),
                        "application/pdf"
                );
                execution.setVariable("identityVerificationReportPath", reportPath);
            }

            log.info("Identity verification completed for user: {} with status: {} and score: {}",
                    userId, result.isSuccess() ? "SUCCESS" : "FAILED", result.getScore());

        } catch (Exception e) {
            log.error("Error during identity verification", e);
            
            // Set failure variables
            execution.setVariable("identityVerificationStatus", VerificationStatus.FAILED.toString());
            execution.setVariable("identityVerificationScore", 0);
            execution.setVariable("identityVerificationSuccess", false);
            execution.setVariable("identityVerificationError", e.getMessage());
            
            throw e; // Re-throw to trigger BPMN error handling
        }
    }
}