package com.waqiti.kyc.workflow.delegates;

import com.waqiti.kyc.dto.AMLScreeningRequest;
import com.waqiti.kyc.dto.VerificationResult;
import com.waqiti.kyc.integration.KYCProvider;
import com.waqiti.kyc.integration.KYCProviderFactory;
import com.waqiti.kyc.model.AMLResult;
import com.waqiti.kyc.model.KYCApplication;
import com.waqiti.kyc.model.VerificationStatus;
import com.waqiti.kyc.repository.AMLResultRepository;
import com.waqiti.kyc.repository.KYCApplicationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.camunda.bpm.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Slf4j
@Component("amlScreeningDelegate")
@RequiredArgsConstructor
public class AMLScreeningDelegate implements JavaDelegate {

    private final KYCProviderFactory kycProviderFactory;
    private final KYCApplicationRepository kycApplicationRepository;
    private final AMLResultRepository amlResultRepository;

    @Override
    public void execute(DelegateExecution execution) throws Exception {
        log.info("Executing AML screening for process instance: {}", execution.getProcessInstanceId());

        try {
            String userId = (String) execution.getVariable("userId");
            String kycApplicationId = (String) execution.getVariable("kycApplicationId");
            String providerName = (String) execution.getVariable("amlProvider");

            if (providerName == null) {
                providerName = "complyadvantage"; // Default AML provider
            }

            // Get KYC application
            KYCApplication application = kycApplicationRepository.findById(kycApplicationId)
                    .orElseThrow(() -> new RuntimeException("KYC Application not found: " + kycApplicationId));

            // Get AML provider
            KYCProvider provider = kycProviderFactory.getProvider(providerName);

            // Prepare screening request
            AMLScreeningRequest request = AMLScreeningRequest.builder()
                    .userId(userId)
                    .firstName((String) execution.getVariable("firstName"))
                    .lastName((String) execution.getVariable("lastName"))
                    .dateOfBirth((String) execution.getVariable("dateOfBirth"))
                    .nationality((String) execution.getVariable("nationality"))
                    .country((String) execution.getVariable("country"))
                    .build();

            // Perform AML screening
            VerificationResult result = provider.performAMLCheck(request);

            // Create AML result record
            AMLResult amlResult = new AMLResult();
            amlResult.setKycApplicationId(kycApplicationId);
            amlResult.setUserId(userId);
            amlResult.setProvider(providerName);
            amlResult.setScreeningId(result.getVerificationId());
            amlResult.setStatus(result.isSuccess() ? VerificationStatus.VERIFIED : VerificationStatus.FAILED);
            amlResult.setRiskScore(result.getScore());
            amlResult.setScreenedAt(LocalDateTime.now());

            // Process screening results
            Map<String, Object> details = result.getDetails();
            if (details != null) {
                // Check for sanctions
                List<Map<String, Object>> sanctions = (List<Map<String, Object>>) details.get("sanctions");
                amlResult.setSanctionsMatch(sanctions != null && !sanctions.isEmpty());
                
                // Check for PEP status
                Boolean isPep = (Boolean) details.get("isPEP");
                amlResult.setPepMatch(isPep != null && isPep);
                
                // Check for adverse media
                List<Map<String, Object>> adverseMedia = (List<Map<String, Object>>) details.get("adverseMedia");
                amlResult.setAdverseMediaMatch(adverseMedia != null && !adverseMedia.isEmpty());
                
                // Store match details
                amlResult.setMatchDetails(details);
            }

            // Save AML result
            amlResultRepository.save(amlResult);

            // Update application
            application.setAmlScreeningStatus(amlResult.getStatus());
            application.setAmlRiskScore(amlResult.getRiskScore());
            application.setPepStatus(amlResult.isPepMatch());
            application.setLastUpdated(LocalDateTime.now());
            kycApplicationRepository.save(application);

            // Determine AML screening result
            String amlScreeningResult;
            if (amlResult.isSanctionsMatch()) {
                amlScreeningResult = "CONFIRMED_MATCH";
            } else if (amlResult.isPepMatch() || amlResult.isAdverseMediaMatch()) {
                amlScreeningResult = "POTENTIAL_MATCH";
            } else {
                amlScreeningResult = "CLEAR";
            }

            // Set process variables
            execution.setVariable("amlScreeningResult", amlScreeningResult);
            execution.setVariable("amlRiskScore", amlResult.getRiskScore());
            execution.setVariable("isPEP", amlResult.isPepMatch());
            execution.setVariable("hasSanctionsMatch", amlResult.isSanctionsMatch());
            execution.setVariable("hasAdverseMedia", amlResult.isAdverseMediaMatch());
            execution.setVariable("amlScreeningSuccess", result.isSuccess());
            execution.setVariable("amlScreeningDetails", result.getDetails());

            // Determine if manual review is required
            boolean manualReviewRequired = amlResult.isSanctionsMatch() || 
                                          amlResult.isPepMatch() || 
                                          amlResult.getRiskScore() > 70;
            execution.setVariable("manualReviewRequired", manualReviewRequired);

            log.info("AML screening completed for user: {} with result: {}, Risk Score: {}, PEP: {}, Sanctions: {}",
                    userId, amlScreeningResult, amlResult.getRiskScore(), 
                    amlResult.isPepMatch(), amlResult.isSanctionsMatch());

        } catch (Exception e) {
            log.error("Error during AML screening", e);
            
            // Set failure variables
            execution.setVariable("amlScreeningResult", "FAILED");
            execution.setVariable("amlRiskScore", 100); // High risk on failure
            execution.setVariable("amlScreeningSuccess", false);
            execution.setVariable("amlScreeningError", e.getMessage());
            execution.setVariable("manualReviewRequired", true);
            
            throw e;
        }
    }
}