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
@Component("enhancedDueDiligenceDelegate")
@RequiredArgsConstructor
public class EnhancedDueDiligenceDelegate implements JavaDelegate {

    private final KYCProviderFactory kycProviderFactory;
    private final KYCApplicationRepository kycApplicationRepository;

    @Override
    public void execute(DelegateExecution execution) throws Exception {
        log.info("Executing enhanced due diligence for process instance: {}", execution.getProcessInstanceId());

        try {
            String userId = (String) execution.getVariable("userId");
            String kycApplicationId = (String) execution.getVariable("kycApplicationId");
            String providerName = (String) execution.getVariable("eddProvider");

            if (providerName == null) {
                providerName = "complyadvantage"; // Default EDD provider
            }

            // Get KYC application
            KYCApplication application = kycApplicationRepository.findById(kycApplicationId)
                    .orElseThrow(() -> new RuntimeException("KYC Application not found: " + kycApplicationId));

            // Get EDD provider
            KYCProvider provider = kycProviderFactory.getProvider(providerName);

            // Prepare enhanced due diligence data
            Map<String, Object> eddData = Map.of(
                    "userId", userId,
                    "firstName", execution.getVariable("firstName"),
                    "lastName", execution.getVariable("lastName"),
                    "dateOfBirth", execution.getVariable("dateOfBirth"),
                    "nationality", execution.getVariable("nationality"),
                    "country", execution.getVariable("country"),
                    "address", execution.getVariable("addressLine1"),
                    "city", execution.getVariable("city"),
                    "expectedTransactionVolume", execution.getVariable("expectedTransactionVolume"),
                    "sourceOfFunds", execution.getVariable("sourceOfFunds"),
                    "employmentStatus", execution.getVariable("employmentStatus"),
                    "purposeOfAccount", execution.getVariable("purposeOfAccount"),
                    
                    // Enhanced screening parameters
                    "includeAssociates", true,
                    "includeFamilyMembers", true,
                    "includeBusinessConnections", true,
                    "searchDepth", "DEEP", // SHALLOW, MEDIUM, DEEP
                    "includeHistoricalData", true,
                    "includeSocialMedia", true,
                    "includeNewsArticles", true,
                    "includeCourtRecords", true,
                    "includeBankruptcy", true,
                    "includeLitigation", true
            );

            // Perform enhanced due diligence
            VerificationResult result = provider.performEnhancedDueDiligence(eddData);

            // Update application
            application.setEddStatus(
                    result.isSuccess() ? VerificationStatus.VERIFIED : VerificationStatus.FAILED
            );
            application.setEddScore(result.getScore());
            application.setLastUpdated(LocalDateTime.now());

            // Process EDD results
            Map<String, Object> details = result.getDetails();
            if (details != null) {
                // Risk indicators
                Boolean highRiskAssociations = (Boolean) details.get("highRiskAssociations");
                Boolean adverseMediaFound = (Boolean) details.get("adverseMediaFound");
                Boolean litigationHistory = (Boolean) details.get("litigationHistory");
                Boolean criminalHistory = (Boolean) details.get("criminalHistory");
                Boolean bankruptcyHistory = (Boolean) details.get("bankruptcyHistory");
                
                // Financial behavior analysis
                Integer transactionPatternScore = (Integer) details.get("transactionPatternScore");
                Boolean unusualFinancialBehavior = (Boolean) details.get("unusualFinancialBehavior");
                
                // Reputation score
                Integer reputationScore = (Integer) details.get("reputationScore");
                
                // Set process variables
                execution.setVariable("highRiskAssociations", highRiskAssociations != null ? highRiskAssociations : false);
                execution.setVariable("adverseMediaFound", adverseMediaFound != null ? adverseMediaFound : false);
                execution.setVariable("litigationHistory", litigationHistory != null ? litigationHistory : false);
                execution.setVariable("criminalHistory", criminalHistory != null ? criminalHistory : false);
                execution.setVariable("bankruptcyHistory", bankruptcyHistory != null ? bankruptcyHistory : false);
                execution.setVariable("transactionPatternScore", transactionPatternScore != null ? transactionPatternScore : 0);
                execution.setVariable("unusualFinancialBehavior", unusualFinancialBehavior != null ? unusualFinancialBehavior : false);
                execution.setVariable("reputationScore", reputationScore != null ? reputationScore : 0);
            }

            kycApplicationRepository.save(application);

            // Set process variables
            execution.setVariable("eddStatus", application.getEddStatus().toString());
            execution.setVariable("eddScore", result.getScore());
            execution.setVariable("eddSuccess", result.isSuccess());
            execution.setVariable("eddDetails", result.getDetails());

            // Determine if manual review is required based on EDD findings
            boolean manualReviewRequired = determineManualReviewRequired(execution, result);
            execution.setVariable("manualReviewRequired", manualReviewRequired);

            // Set review priority
            String reviewPriority = determineReviewPriority(execution, result);
            execution.setVariable("reviewPriority", reviewPriority);

            log.info("Enhanced due diligence completed for user: {} with status: {} and score: {}. Manual review required: {}",
                    userId, result.isSuccess() ? "SUCCESS" : "FAILED", result.getScore(), manualReviewRequired);

        } catch (Exception e) {
            log.error("Error during enhanced due diligence", e);
            
            // Set failure variables
            execution.setVariable("eddStatus", VerificationStatus.FAILED.toString());
            execution.setVariable("eddScore", 0);
            execution.setVariable("eddSuccess", false);
            execution.setVariable("eddError", e.getMessage());
            execution.setVariable("manualReviewRequired", true);
            execution.setVariable("reviewPriority", "HIGH");
            
            throw e;
        }
    }

    private boolean determineManualReviewRequired(DelegateExecution execution, VerificationResult result) {
        // Always require manual review if EDD failed
        if (!result.isSuccess()) {
            return true;
        }

        // Check various risk indicators
        Boolean highRiskAssociations = (Boolean) execution.getVariable("highRiskAssociations");
        Boolean adverseMediaFound = (Boolean) execution.getVariable("adverseMediaFound");
        Boolean litigationHistory = (Boolean) execution.getVariable("litigationHistory");
        Boolean criminalHistory = (Boolean) execution.getVariable("criminalHistory");
        Boolean isPEP = (Boolean) execution.getVariable("isPEP");
        Boolean hasSanctionsMatch = (Boolean) execution.getVariable("hasSanctionsMatch");

        // Require manual review for high-risk indicators
        if (Boolean.TRUE.equals(highRiskAssociations) ||
            Boolean.TRUE.equals(adverseMediaFound) ||
            Boolean.TRUE.equals(litigationHistory) ||
            Boolean.TRUE.equals(criminalHistory) ||
            Boolean.TRUE.equals(isPEP) ||
            Boolean.TRUE.equals(hasSanctionsMatch)) {
            return true;
        }

        // Check scores
        Integer eddScore = result.getScore();
        Integer reputationScore = (Integer) execution.getVariable("reputationScore");
        
        // Require manual review for low scores
        if (eddScore < 70 || (reputationScore != null && reputationScore < 60)) {
            return true;
        }

        return false;
    }

    private String determineReviewPriority(DelegateExecution execution, VerificationResult result) {
        Boolean hasSanctionsMatch = (Boolean) execution.getVariable("hasSanctionsMatch");
        Boolean criminalHistory = (Boolean) execution.getVariable("criminalHistory");
        Boolean isPEP = (Boolean) execution.getVariable("isPEP");

        // Critical priority for sanctions or criminal history
        if (Boolean.TRUE.equals(hasSanctionsMatch) || Boolean.TRUE.equals(criminalHistory)) {
            return "CRITICAL";
        }

        // High priority for PEPs or very low scores
        if (Boolean.TRUE.equals(isPEP) || result.getScore() < 50) {
            return "HIGH";
        }

        // Medium priority for other risk indicators
        Boolean adverseMediaFound = (Boolean) execution.getVariable("adverseMediaFound");
        Boolean litigationHistory = (Boolean) execution.getVariable("litigationHistory");
        
        if (Boolean.TRUE.equals(adverseMediaFound) || Boolean.TRUE.equals(litigationHistory)) {
            return "MEDIUM";
        }

        return "LOW";
    }
}