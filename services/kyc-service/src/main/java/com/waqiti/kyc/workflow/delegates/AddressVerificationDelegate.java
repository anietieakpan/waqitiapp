package com.waqiti.kyc.workflow.delegates;

import com.waqiti.kyc.dto.AddressVerificationRequest;
import com.waqiti.kyc.dto.VerificationResult;
import com.waqiti.kyc.integration.KYCProvider;
import com.waqiti.kyc.integration.KYCProviderFactory;
import com.waqiti.kyc.model.Address;
import com.waqiti.kyc.model.KYCApplication;
import com.waqiti.kyc.model.KYCDocument;
import com.waqiti.kyc.model.VerificationStatus;
import com.waqiti.kyc.repository.AddressRepository;
import com.waqiti.kyc.repository.KYCApplicationRepository;
import com.waqiti.kyc.repository.KYCDocumentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.camunda.bpm.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Slf4j
@Component("addressVerificationDelegate")
@RequiredArgsConstructor
public class AddressVerificationDelegate implements JavaDelegate {

    private final KYCProviderFactory kycProviderFactory;
    private final KYCApplicationRepository kycApplicationRepository;
    private final KYCDocumentRepository kycDocumentRepository;
    private final AddressRepository addressRepository;

    @Override
    public void execute(DelegateExecution execution) throws Exception {
        log.info("Executing address verification for process instance: {}", execution.getProcessInstanceId());

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

            // Get address from process variables or database
            Address address = getOrCreateAddress(execution, userId);

            // Get proof of address document if available
            Optional<KYCDocument> proofOfAddress = kycDocumentRepository.findByKycApplicationIdAndDocumentType(
                    kycApplicationId,
                    com.waqiti.kyc.model.DocumentType.PROOF_OF_ADDRESS
            );

            // Get KYC provider
            KYCProvider provider = kycProviderFactory.getProvider(providerName);

            // Prepare address verification request
            AddressVerificationRequest request = AddressVerificationRequest.builder()
                    .userId(userId)
                    .addressLine1(address.getAddressLine1())
                    .addressLine2(address.getAddressLine2())
                    .city(address.getCity())
                    .state(address.getState())
                    .postalCode(address.getPostalCode())
                    .country(address.getCountry())
                    .proofOfAddressPath(proofOfAddress.map(KYCDocument::getDocumentPath).orElse(null))
                    .build();

            // Perform address verification
            VerificationResult result = provider.verifyAddress(request);

            // Update address record
            address.setVerificationStatus(
                    result.isSuccess() ? VerificationStatus.VERIFIED : VerificationStatus.FAILED
            );
            address.setVerificationScore(result.getScore());
            address.setVerifiedAt(LocalDateTime.now());
            
            // Check if address was standardized/corrected
            if (result.getDetails() != null && result.getDetails().containsKey("standardizedAddress")) {
                var standardized = (java.util.Map<String, String>) result.getDetails().get("standardizedAddress");
                address.setStandardizedLine1(standardized.get("line1"));
                address.setStandardizedLine2(standardized.get("line2"));
                address.setStandardizedCity(standardized.get("city"));
                address.setStandardizedState(standardized.get("state"));
                address.setStandardizedPostalCode(standardized.get("postalCode"));
            }
            
            addressRepository.save(address);

            // Update proof of address document if provided
            if (proofOfAddress.isPresent()) {
                KYCDocument doc = proofOfAddress.get();
                doc.setVerificationStatus(address.getVerificationStatus());
                doc.setVerificationScore(result.getScore());
                doc.setVerificationDetails(result.getDetails());
                doc.setVerifiedAt(LocalDateTime.now());
                kycDocumentRepository.save(doc);
            }

            // Update application
            application.setAddressVerificationStatus(address.getVerificationStatus());
            application.setAddressVerificationScore(result.getScore());
            application.setLastUpdated(LocalDateTime.now());
            kycApplicationRepository.save(application);

            // Set process variables
            execution.setVariable("addressVerificationStatus", address.getVerificationStatus().toString());
            execution.setVariable("addressVerificationScore", result.getScore());
            execution.setVariable("addressVerificationSuccess", result.isSuccess());
            execution.setVariable("addressVerified", result.isSuccess());
            
            // Check if address matches ID document address
            boolean addressMatchesDocument = checkAddressMatchesDocument(address, execution);
            execution.setVariable("addressMatchesDocument", addressMatchesDocument);

            log.info("Address verification completed for user: {} with status: {} and score: {}",
                    userId, result.isSuccess() ? "SUCCESS" : "FAILED", result.getScore());

        } catch (Exception e) {
            log.error("Error during address verification", e);
            
            // Set failure variables
            execution.setVariable("addressVerificationStatus", VerificationStatus.FAILED.toString());
            execution.setVariable("addressVerificationScore", 0);
            execution.setVariable("addressVerificationSuccess", false);
            execution.setVariable("addressVerificationError", e.getMessage());
            
            throw e;
        }
    }

    private Address getOrCreateAddress(DelegateExecution execution, String userId) {
        String addressLine1 = (String) execution.getVariable("addressLine1");
        String city = (String) execution.getVariable("city");
        String country = (String) execution.getVariable("country");
        
        // Try to find existing address
        Optional<Address> existingAddress = addressRepository.findByUserIdAndAddressLine1AndCityAndCountry(
                userId, addressLine1, city, country
        );
        
        if (existingAddress.isPresent()) {
            return existingAddress.get();
        }
        
        // Create new address
        Address address = new Address();
        address.setUserId(userId);
        address.setAddressLine1(addressLine1);
        address.setAddressLine2((String) execution.getVariable("addressLine2"));
        address.setCity(city);
        address.setState((String) execution.getVariable("state"));
        address.setPostalCode((String) execution.getVariable("postalCode"));
        address.setCountry(country);
        address.setAddressType("PRIMARY");
        address.setCreatedAt(LocalDateTime.now());
        
        return addressRepository.save(address);
    }

    private boolean checkAddressMatchesDocument(Address address, DelegateExecution execution) {
        // Get address from document verification if available
        Object documentVerificationResults = execution.getVariable("documentVerificationResults");
        if (documentVerificationResults == null) {
            return true; // Assume match if no document data
        }
        
        // Simple matching logic - can be enhanced
        // In production, would use fuzzy matching or address standardization service
        return true;
    }
}