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
@Component("bankVerificationDelegate")
@RequiredArgsConstructor
public class BankVerificationDelegate implements JavaDelegate {

    private final KYCProviderFactory kycProviderFactory;
    private final KYCApplicationRepository kycApplicationRepository;

    @Override
    public void execute(DelegateExecution execution) throws Exception {
        log.info("Executing bank account verification for process instance: {}", execution.getProcessInstanceId());

        try {
            String userId = (String) execution.getVariable("userId");
            String kycApplicationId = (String) execution.getVariable("kycApplicationId");
            String providerName = (String) execution.getVariable("bankVerificationProvider");

            if (providerName == null) {
                providerName = "plaid"; // Default bank verification provider
            }

            // Get KYC application
            KYCApplication application = kycApplicationRepository.findById(kycApplicationId)
                    .orElseThrow(() -> new RuntimeException("KYC Application not found: " + kycApplicationId));

            // Get bank verification provider
            KYCProvider provider = kycProviderFactory.getProvider(providerName);

            // Prepare bank verification data
            Map<String, Object> bankData = Map.of(
                    "userId", userId,
                    "accountNumber", execution.getVariable("bankAccountNumber"),
                    "routingNumber", execution.getVariable("bankRoutingNumber"),
                    "accountType", execution.getVariable("accountType") != null ? 
                                  execution.getVariable("accountType") : "CHECKING",
                    "bankName", execution.getVariable("bankName"),
                    "accountHolderName", execution.getVariable("firstName") + " " + 
                                        execution.getVariable("lastName"),
                    "verificationMethod", "MICRODEPOSIT" // MICRODEPOSIT, INSTANT, SAME_DAY_ACH
            );

            // Perform bank account verification
            VerificationResult result = provider.verifyBankAccount(bankData);

            // Update application
            application.setBankVerificationStatus(
                    result.isSuccess() ? VerificationStatus.VERIFIED : VerificationStatus.FAILED
            );
            application.setBankVerificationScore(result.getScore());
            application.setLastUpdated(LocalDateTime.now());

            // Process verification details
            Map<String, Object> details = result.getDetails();
            if (details != null) {
                Boolean accountExists = (Boolean) details.get("accountExists");
                Boolean nameMatch = (Boolean) details.get("nameMatch");
                String accountStatus = (String) details.get("accountStatus");
                
                execution.setVariable("bankAccountExists", accountExists != null ? accountExists : false);
                execution.setVariable("bankNameMatch", nameMatch != null ? nameMatch : false);
                execution.setVariable("bankAccountStatus", accountStatus != null ? accountStatus : "UNKNOWN");
                
                // Check for fraud indicators
                Boolean suspiciousActivity = (Boolean) details.get("suspiciousActivity");
                if (suspiciousActivity != null && suspiciousActivity) {
                    execution.setVariable("bankFraudRisk", true);
                    application.setBankVerificationStatus(VerificationStatus.MANUAL_REVIEW);
                }
            }

            kycApplicationRepository.save(application);

            // Set process variables
            execution.setVariable("bankVerificationStatus", application.getBankVerificationStatus().toString());
            execution.setVariable("bankVerificationScore", result.getScore());
            execution.setVariable("bankVerificationSuccess", result.isSuccess());
            execution.setVariable("bankVerificationDetails", result.getDetails());

            // Determine if manual review is needed
            boolean manualReviewRequired = !result.isSuccess() || 
                                         result.getScore() < 70 ||
                                         (Boolean.TRUE.equals(execution.getVariable("bankFraudRisk")));
            execution.setVariable("bankManualReviewRequired", manualReviewRequired);

            log.info("Bank verification completed for user: {} with status: {} and score: {}",
                    userId, result.isSuccess() ? "SUCCESS" : "FAILED", result.getScore());

        } catch (Exception e) {
            log.error("Error during bank account verification", e);
            
            // Set failure variables
            execution.setVariable("bankVerificationStatus", VerificationStatus.FAILED.toString());
            execution.setVariable("bankVerificationScore", 0);
            execution.setVariable("bankVerificationSuccess", false);
            execution.setVariable("bankVerificationError", e.getMessage());
            execution.setVariable("bankManualReviewRequired", true);
            
            throw e;
        }
    }
}