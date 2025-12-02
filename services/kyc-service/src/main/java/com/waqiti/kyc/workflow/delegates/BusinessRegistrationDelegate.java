package com.waqiti.kyc.workflow.delegates;

import com.waqiti.kyc.dto.VerificationResult;
import com.waqiti.kyc.integration.KYCProvider;
import com.waqiti.kyc.integration.KYCProviderFactory;
import com.waqiti.kyc.model.KYCApplication;
import com.waqiti.kyc.model.VerificationStatus;
import com.waqiti.kyc.repository.KYCApplicationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.camunda.bpm.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Map;

@Slf4j
@Component("businessRegistrationDelegate")
@RequiredArgsConstructor
public class BusinessRegistrationDelegate implements JavaDelegate {

    private final KYCProviderFactory kycProviderFactory;
    private final KYCApplicationRepository kycApplicationRepository;

    @Override
    public void execute(DelegateExecution execution) throws Exception {
        log.info("Executing business registration verification for process instance: {}", execution.getProcessInstanceId());

        try {
            String userId = (String) execution.getVariable("userId");
            String kycApplicationId = (String) execution.getVariable("kycApplicationId");
            String providerName = (String) execution.getVariable("businessVerificationProvider");

            if (providerName == null) {
                providerName = "lexisnexis"; // Default business verification provider
            }

            // Get KYC application
            KYCApplication application = kycApplicationRepository.findById(kycApplicationId)
                    .orElseThrow(() -> new RuntimeException("KYC Application not found: " + kycApplicationId));

            // Get business verification provider
            KYCProvider provider = kycProviderFactory.getProvider(providerName);

            // Prepare business registration verification data
            Map<String, Object> businessData = Map.of(
                    "userId", userId,
                    "businessName", execution.getVariable("businessName"),
                    "registrationNumber", execution.getVariable("businessRegistrationNumber"),
                    "incorporationCountry", execution.getVariable("businessIncorporationCountry"),
                    "businessType", execution.getVariable("businessType"), // LLC, CORPORATION, PARTNERSHIP, etc.
                    "industry", execution.getVariable("businessIndustry"),
                    "incorporationDate", execution.getVariable("businessIncorporationDate") != null ? 
                                       execution.getVariable("businessIncorporationDate") : null,
                    "registeredAddress", Map.of(
                        "line1", execution.getVariable("businessAddressLine1"),
                        "city", execution.getVariable("businessCity"),
                        "state", execution.getVariable("businessState"),
                        "postalCode", execution.getVariable("businessPostalCode"),
                        "country", execution.getVariable("businessCountry")
                    ),
                    "taxId", execution.getVariable("businessTaxId"),
                    "vatNumber", execution.getVariable("businessVatNumber")
            );

            // Perform business registration verification
            VerificationResult result = provider.verifyBusinessRegistration(businessData);

            // Update application
            application.setBusinessRegistrationStatus(
                    result.isSuccess() ? VerificationStatus.VERIFIED : VerificationStatus.FAILED
            );
            application.setBusinessRegistrationScore(result.getScore());
            application.setLastUpdated(LocalDateTime.now());

            // Process verification details
            Map<String, Object> details = result.getDetails();
            if (details != null) {
                Boolean businessExists = (Boolean) details.get("businessExists");
                Boolean registrationActive = (Boolean) details.get("registrationActive");
                Boolean nameMatch = (Boolean) details.get("nameMatch");
                Boolean addressMatch = (Boolean) details.get("addressMatch");
                String businessStatus = (String) details.get("businessStatus");
                String registrationType = (String) details.get("registrationType");
                
                execution.setVariable("businessExists", businessExists != null ? businessExists : false);
                execution.setVariable("businessRegistrationActive", registrationActive != null ? registrationActive : false);
                execution.setVariable("businessNameMatch", nameMatch != null ? nameMatch : false);
                execution.setVariable("businessAddressMatch", addressMatch != null ? addressMatch : false);
                execution.setVariable("businessStatus", businessStatus != null ? businessStatus : "UNKNOWN");
                execution.setVariable("businessRegistrationType", registrationType);
                
                // Check for red flags
                Boolean dissolved = (Boolean) details.get("dissolved");
                Boolean suspended = (Boolean) details.get("suspended");
                Boolean inDefault = (Boolean) details.get("inDefault");
                
                if (Boolean.TRUE.equals(dissolved) || Boolean.TRUE.equals(suspended) || Boolean.TRUE.equals(inDefault)) {
                    execution.setVariable("businessRedFlags", true);
                    application.setBusinessRegistrationStatus(VerificationStatus.FAILED);
                }
                
                // Extract officers/directors information if available
                @SuppressWarnings("unchecked")
                java.util.List<Map<String, Object>> officers = (java.util.List<Map<String, Object>>) details.get("officers");
                if (officers != null && !officers.isEmpty()) {
                    execution.setVariable("businessOfficers", officers);
                    execution.setVariable("businessOfficerCount", officers.size());
                }
            }

            kycApplicationRepository.save(application);

            // Set process variables
            execution.setVariable("businessRegistrationStatus", application.getBusinessRegistrationStatus().toString());
            execution.setVariable("businessRegistrationScore", result.getScore());
            execution.setVariable("businessRegistrationSuccess", result.isSuccess());
            execution.setVariable("businessRegistrationDetails", result.getDetails());

            // Determine if additional verification is needed
            boolean additionalVerificationRequired = determineAdditionalVerificationRequired(execution, result);
            execution.setVariable("additionalBusinessVerificationRequired", additionalVerificationRequired);

            log.info("Business registration verification completed for user: {} with status: {} and score: {}",
                    userId, result.isSuccess() ? "SUCCESS" : "FAILED", result.getScore());

        } catch (Exception e) {
            log.error("Error during business registration verification", e);
            
            // Set failure variables
            execution.setVariable("businessRegistrationStatus", VerificationStatus.FAILED.toString());
            execution.setVariable("businessRegistrationScore", 0);
            execution.setVariable("businessRegistrationSuccess", false);
            execution.setVariable("businessRegistrationError", e.getMessage());
            execution.setVariable("additionalBusinessVerificationRequired", true);
            
            throw e;
        }
    }

    private boolean determineAdditionalVerificationRequired(DelegateExecution execution, VerificationResult result) {
        // Require additional verification if basic checks failed
        if (!result.isSuccess() || result.getScore() < 70) {
            return true;
        }

        // Check for red flags
        Boolean businessRedFlags = (Boolean) execution.getVariable("businessRedFlags");
        if (Boolean.TRUE.equals(businessRedFlags)) {
            return true;
        }

        // Check if business is too new (less than 6 months old)
        Object incorporationDateObj = execution.getVariable("businessIncorporationDate");
        if (incorporationDateObj != null) {
            try {
                LocalDateTime incorporationDate = LocalDateTime.parse(incorporationDateObj.toString());
                LocalDateTime sixMonthsAgo = LocalDateTime.now().minusMonths(6);
                if (incorporationDate.isAfter(sixMonthsAgo)) {
                    return true;
                }
            } catch (Exception e) {
                log.warn("Could not parse incorporation date: {}", incorporationDateObj);
            }
        }

        // Check if high-risk industry
        String industry = (String) execution.getVariable("businessIndustry");
        if (isHighRiskIndustry(industry)) {
            return true;
        }

        return false;
    }

    private boolean isHighRiskIndustry(String industry) {
        if (industry == null) {
            return false;
        }
        
        String[] highRiskIndustries = {
            "CRYPTOCURRENCY", "GAMBLING", "ADULT_ENTERTAINMENT", "FIREARMS",
            "MONEY_SERVICES", "DEBT_COLLECTION", "TELEMARKETING", "PHARMACEUTICALS",
            "PRECIOUS_METALS", "CASH_INTENSIVE", "POLITICAL_ORGANIZATIONS"
        };
        
        for (String riskIndustry : highRiskIndustries) {
            if (riskIndustry.equalsIgnoreCase(industry)) {
                return true;
            }
        }
        
        return false;
    }
}