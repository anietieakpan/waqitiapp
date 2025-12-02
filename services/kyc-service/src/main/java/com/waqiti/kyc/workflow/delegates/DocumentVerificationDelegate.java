package com.waqiti.kyc.workflow.delegates;

import com.waqiti.kyc.dto.DocumentVerificationRequest;
import com.waqiti.kyc.dto.VerificationResult;
import com.waqiti.kyc.integration.KYCProvider;
import com.waqiti.kyc.integration.KYCProviderFactory;
import com.waqiti.kyc.model.DocumentType;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Component("documentVerificationDelegate")
@RequiredArgsConstructor
public class DocumentVerificationDelegate implements JavaDelegate {

    private final KYCProviderFactory kycProviderFactory;
    private final KYCApplicationRepository kycApplicationRepository;
    private final KYCDocumentRepository kycDocumentRepository;
    private final DocumentStorageService documentStorageService;

    @Override
    public void execute(DelegateExecution execution) throws Exception {
        log.info("Executing document verification for process instance: {}", execution.getProcessInstanceId());

        try {
            String userId = (String) execution.getVariable("userId");
            String kycApplicationId = (String) execution.getVariable("kycApplicationId");
            String providerName = (String) execution.getVariable("kycProvider");

            if (providerName == null) {
                providerName = "jumio"; // Default provider for documents
            }

            // Get KYC application
            KYCApplication application = kycApplicationRepository.findById(kycApplicationId)
                    .orElseThrow(() -> new RuntimeException("KYC Application not found: " + kycApplicationId));

            // Get KYC provider
            KYCProvider provider = kycProviderFactory.getProvider(providerName);

            // Get uploaded documents
            List<KYCDocument> documents = kycDocumentRepository.findByKycApplicationIdAndStatus(
                    kycApplicationId, 
                    VerificationStatus.PENDING
            );

            if (documents.isEmpty()) {
                throw new RuntimeException("No documents found for verification");
            }

            List<VerificationResult> results = new ArrayList<>();
            boolean allDocumentsVerified = true;
            int totalScore = 0;

            // Verify each document
            for (KYCDocument document : documents) {
                DocumentVerificationRequest request = DocumentVerificationRequest.builder()
                        .userId(userId)
                        .documentType(document.getDocumentType())
                        .documentPath(document.getDocumentPath())
                        .documentNumber(document.getDocumentNumber())
                        .issuingCountry(document.getIssuingCountry())
                        .expiryDate(document.getExpiryDate())
                        .build();

                VerificationResult result = provider.verifyDocument(request);
                results.add(result);

                // Update document status
                document.setVerificationStatus(
                        result.isSuccess() ? VerificationStatus.VERIFIED : VerificationStatus.FAILED
                );
                document.setVerificationScore(result.getScore());
                document.setVerificationDetails(result.getDetails());
                document.setVerifiedAt(LocalDateTime.now());
                kycDocumentRepository.save(document);

                if (!result.isSuccess()) {
                    allDocumentsVerified = false;
                }
                totalScore += result.getScore();
            }

            // Calculate average score
            int averageScore = results.isEmpty() ? 0 : totalScore / results.size();

            // Update application status
            application.setDocumentVerificationStatus(
                    allDocumentsVerified ? VerificationStatus.VERIFIED : VerificationStatus.FAILED
            );
            application.setDocumentVerificationScore(averageScore);
            application.setLastUpdated(LocalDateTime.now());
            kycApplicationRepository.save(application);

            // Set process variables
            execution.setVariable("documentVerificationStatus", 
                    allDocumentsVerified ? "VERIFIED" : documents.stream()
                            .anyMatch(d -> d.getVerificationScore() < 50) ? "FAILED" : "SUSPICIOUS");
            execution.setVariable("documentVerificationScore", averageScore);
            execution.setVariable("documentVerificationSuccess", allDocumentsVerified);
            execution.setVariable("verifiedDocumentCount", results.size());

            // Store combined verification report
            Map<String, Object> combinedResults = Map.of(
                    "documents", documents,
                    "results", results,
                    "overallStatus", allDocumentsVerified ? "VERIFIED" : "FAILED",
                    "averageScore", averageScore
            );
            execution.setVariable("documentVerificationResults", combinedResults);

            log.info("Document verification completed for user: {} with {} documents. Status: {}, Average Score: {}",
                    userId, results.size(), allDocumentsVerified ? "SUCCESS" : "FAILED", averageScore);

        } catch (Exception e) {
            log.error("Error during document verification", e);
            
            // Set failure variables
            execution.setVariable("documentVerificationStatus", "FAILED");
            execution.setVariable("documentVerificationScore", 0);
            execution.setVariable("documentVerificationSuccess", false);
            execution.setVariable("documentVerificationError", e.getMessage());
            
            throw e;
        }
    }
}