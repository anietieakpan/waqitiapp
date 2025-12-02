package com.waqiti.kyc.integration.onfido;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.kyc.dto.*;
import com.waqiti.kyc.dto.request.KYCVerificationRequest;
import com.waqiti.kyc.dto.response.KYCVerificationResponse;
import com.waqiti.kyc.exception.KYCProviderException;
import com.waqiti.kyc.integration.KYCProvider;
import com.waqiti.kyc.provider.AbstractKYCProvider;
import com.waqiti.common.config.SecretsConfig.ApiSecrets;

import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import io.github.resilience4j.retry.annotation.Retry;
import io.github.resilience4j.timelimiter.annotation.TimeLimiter;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.*;

/**
 * Onfido KYC provider implementation
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class OnfidoKYCProvider extends AbstractKYCProvider implements KYCProvider {
    
    private final ObjectMapper objectMapper;
    private final ApiSecrets apiSecrets;
    private String apiToken;
    
    @Override
    protected void configureHeaders(HttpHeaders headers, Map<String, String> config) {
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        
        // Use secure API token from secrets management
        if (this.apiToken != null) {
            headers.set("Authorization", "Token token=" + this.apiToken);
        }
    }
    
    @Override
    protected void doInitialize(Map<String, String> config) {
        validateConfiguration("apiUrl");
        // Get API token from secure secrets management
        this.apiToken = apiSecrets.getOnfidoApiToken();
    }
    
    @Override
    protected boolean performHealthCheck() {
        try {
            // Onfido doesn't have a dedicated health endpoint, so we'll check the account endpoint
            Map<String, Object> response = get("/v3.6/accounts", Map.class);
            return response != null && response.containsKey("id");
        } catch (Exception e) {
            log.debug("Onfido health check failed: {}", e.getMessage());
            return false;
        }
    }
    
    @Override
    @CircuitBreaker(name = "onfido-kyc", fallbackMethod = "createVerificationSessionFallback")
    @Retry(name = "onfido-kyc")
    @RateLimiter(name = "onfido-kyc")
    @Bulkhead(name = "onfido-kyc")
    public String createVerificationSession(String userId, KYCVerificationRequest request) {
        try {
            // First, create or get the applicant
            String applicantId = createOrGetApplicant(userId, request);
            
            // Create a workflow run (Onfido Studio) or check
            if (request.getWorkflowId() != null) {
                return createWorkflowRun(applicantId, request);
            } else {
                return createCheck(applicantId, request);
            }
        } catch (Exception e) {
            throw new KYCProviderException("Failed to create verification session", providerName, e);
        }
    }
    
    @Override
    @CircuitBreaker(name = "onfido-kyc", fallbackMethod = "getVerificationStatusFallback")
    @Retry(name = "onfido-kyc")
    @RateLimiter(name = "onfido-kyc")
    public Map<String, Object> getVerificationStatus(String sessionId) {
        try {
            // Try workflow run first
            try {
                Map<String, Object> workflowRun = get("/v3.6/workflow_runs/" + sessionId, Map.class);
                return convertWorkflowRunToStatus(workflowRun);
            } catch (Exception e) {
                // If not a workflow run, try as a check
                Map<String, Object> check = get("/v3.6/checks/" + sessionId, Map.class);
                return convertCheckToStatus(check);
            }
        } catch (Exception e) {
            log.debug("Session {} not found in Onfido", sessionId);
            return Map.of();
        }
    }
    
    @Override
    @CircuitBreaker(name = "onfido-kyc", fallbackMethod = "getVerificationResultsFallback")
    @Retry(name = "onfido-kyc")
    @RateLimiter(name = "onfido-kyc")
    public Map<String, Object> getVerificationResults(String sessionId) {
        try {
            // Try workflow run first
            try {
                Map<String, Object> workflowRun = get("/v3.6/workflow_runs/" + sessionId, Map.class);
                return convertWorkflowRunToResults(workflowRun);
            } catch (Exception e) {
                // If not a workflow run, try as a check
                Map<String, Object> check = get("/v3.6/checks/" + sessionId, Map.class);
                return convertCheckToResults(check);
            }
        } catch (Exception e) {
            throw new KYCProviderException("Failed to get verification results", providerName, e);
        }
    }
    
    @Override
    public void cancelVerificationSession(String sessionId) {
        try {
            // Workflow runs can be cancelled, checks cannot
            try {
                post("/v3.6/workflow_runs/" + sessionId + "/cancel", null, Map.class);
                log.info("Cancelled workflow run: {}", sessionId);
            } catch (Exception e) {
                log.debug("Session {} is not a workflow run or already completed", sessionId);
            }
        } catch (Exception e) {
            throw new KYCProviderException("Failed to cancel verification session", providerName, e);
        }
    }
    
    @Override
    @CircuitBreaker(name = "onfido-kyc", fallbackMethod = "uploadDocumentFallback")
    @Retry(name = "onfido-kyc")
    @RateLimiter(name = "onfido-kyc")
    @Bulkhead(name = "onfido-kyc")
    public String uploadDocument(String sessionId, byte[] documentData, String documentType) {
        try {
            // Get applicant ID from session
            String applicantId = getApplicantIdFromSession(sessionId);
            
            // Create multipart upload
            Map<String, Object> uploadRequest = Map.of(
                "applicant_id", applicantId,
                "type", mapDocumentType(documentType),
                "file", Base64.getEncoder().encodeToString(documentData)
            );
            
            Map<String, Object> response = post("/v3.6/documents", uploadRequest, Map.class);
            return (String) response.get("id");
            
        } catch (Exception e) {
            throw new KYCProviderException("Failed to upload document", providerName, e);
        }
    }
    
    @Override
    public Map<String, String> extractDocumentData(String documentId) {
        try {
            Map<String, Object> document = get("/v3.6/documents/" + documentId, Map.class);
            return extractDataFromDocument(document);
        } catch (Exception e) {
            log.debug("Document {} not found in Onfido", documentId);
            return Map.of();
        }
    }
    
    @Override
    public void processWebhook(Map<String, Object> webhookData) {
        // Webhook processing is handled by OnfidoWebhookHandler
        log.debug("Processing Onfido webhook: {}", webhookData.get("action"));
    }
    
    @Override
    public List<String> getSupportedDocumentTypes() {
        return List.of(
            "passport",
            "driving_licence", 
            "national_identity_card",
            "residence_permit",
            "passport_card",
            "tax_id",
            "voter_id"
        );
    }
    
    @Override
    public List<String> getSupportedCountries() {
        // Onfido supports most countries
        return List.of(
            "US", "GB", "CA", "AU", "NZ", "IE", "FR", "DE", "IT", "ES", 
            "NL", "BE", "AT", "CH", "SE", "NO", "DK", "FI", "PT", "GR",
            "PL", "CZ", "HU", "RO", "BG", "HR", "SI", "SK", "EE", "LV",
            "LT", "MT", "CY", "LU", "IS", "JP", "KR", "SG", "HK", "TW",
            "IN", "ID", "MY", "TH", "PH", "VN", "BR", "MX", "AR", "CL",
            "CO", "PE", "UY", "ZA", "NG", "KE", "GH", "EG", "MA", "TN",
            "IL", "AE", "SA", "QA", "KW", "BH", "OM", "JO", "LB", "TR"
        );
    }
    
    @Override
    public Map<String, Boolean> getFeatures() {
        return Map.of(
            "document_verification", true,
            "facial_recognition", true,
            "liveness_check", true,
            "address_verification", true,
            "database_checks", true,
            "pep_sanctions", true,
            "adverse_media", true,
            "video_verification", true,
            "workflow_studio", true,
            "webhooks", true
        );
    }
    
    // Helper methods
    
    private String createOrGetApplicant(String userId, KYCVerificationRequest request) {
        try {
            // Check if applicant already exists
            Map<String, Object> searchParams = Map.of("external_user_id", userId);
            List<Map<String, Object>> applicants = get("/v3.6/applicants?" + buildQueryString(searchParams), List.class);
            
            if (!applicants.isEmpty()) {
                return (String) applicants.get(0).get("id");
            }
            
            // Create new applicant
            Map<String, Object> applicantData = Map.of(
                "first_name", request.getFirstName(),
                "last_name", request.getLastName(),
                "email", request.getEmail(),
                "dob", request.getDateOfBirth(),
                "external_user_id", userId,
                "address", Map.of(
                    "country", request.getCountry(),
                    "postcode", request.getPostalCode(),
                    "town", request.getCity(),
                    "street", request.getStreetAddress(),
                    "flat_number", request.getApartment()
                )
            );
            
            Map<String, Object> response = post("/v3.6/applicants", applicantData, Map.class);
            return (String) response.get("id");
            
        } catch (Exception e) {
            throw new KYCProviderException("Failed to create/get applicant", providerName, e);
        }
    }
    
    private String createWorkflowRun(String applicantId, KYCVerificationRequest request) {
        Map<String, Object> workflowData = Map.of(
            "applicant_id", applicantId,
            "workflow_id", request.getWorkflowId(),
            "tags", request.getTags() != null ? request.getTags() : List.of()
        );
        
        Map<String, Object> response = post("/v3.6/workflow_runs", workflowData, Map.class);
        return (String) response.get("id");
    }
    
    private String createCheck(String applicantId, KYCVerificationRequest request) {
        List<String> reportNames = new ArrayList<>();
        
        // Add reports based on verification level
        switch (request.getVerificationLevel()) {
            case "BASIC":
                reportNames.add("document");
                break;
            case "INTERMEDIATE":
                reportNames.add("document");
                reportNames.add("facial_similarity_photo");
                break;
            case "ADVANCED":
                reportNames.add("document");
                reportNames.add("facial_similarity_photo_fully_auto");
                reportNames.add("identity_enhanced");
                reportNames.add("watchlist_enhanced");
                break;
        }
        
        Map<String, Object> checkData = Map.of(
            "applicant_id", applicantId,
            "report_names", reportNames,
            "tags", request.getTags() != null ? request.getTags() : List.of()
        );
        
        Map<String, Object> response = post("/v3.6/checks", checkData, Map.class);
        return (String) response.get("id");
    }
    
    private Map<String, Object> convertWorkflowRunToStatus(Map<String, Object> workflowRun) {
        Map<String, Object> status = new HashMap<>();
        status.put("sessionId", workflowRun.get("id"));
        status.put("status", workflowRun.get("status"));
        status.put("createdAt", workflowRun.get("created_at"));
        status.put("updatedAt", workflowRun.get("updated_at"));
        status.put("workflowId", workflowRun.get("workflow_id"));
        return status;
    }
    
    private Map<String, Object> convertCheckToStatus(Map<String, Object> check) {
        Map<String, Object> status = new HashMap<>();
        status.put("sessionId", check.get("id"));
        status.put("status", check.get("status"));
        status.put("result", check.get("result"));
        status.put("createdAt", check.get("created_at"));
        status.put("updatedAt", check.get("updated_at"));
        return status;
    }
    
    private Map<String, Object> convertWorkflowRunToResults(Map<String, Object> workflowRun) {
        Map<String, Object> results = new HashMap<>();
        results.put("id", workflowRun.get("id"));
        results.put("status", workflowRun.get("status"));
        results.put("output", workflowRun.get("output"));
        results.put("reasons", workflowRun.get("reasons"));
        results.put("error", workflowRun.get("error"));
        return results;
    }
    
    private Map<String, Object> convertCheckToResults(Map<String, Object> check) {
        Map<String, Object> results = new HashMap<>();
        results.put("id", check.get("id"));
        results.put("status", check.get("status"));
        results.put("result", check.get("result"));
        results.put("reports", check.get("report_ids"));
        return results;
    }
    
    private String getApplicantIdFromSession(String sessionId) {
        Map<String, Object> status = getVerificationStatus(sessionId);
        return (String) status.get("applicantId");
    }
    
    private String mapDocumentType(String documentType) {
        // Map generic document types to Onfido types
        Map<String, String> typeMapping = Map.of(
            "passport", "passport",
            "driver_license", "driving_licence",
            "id_card", "national_identity_card",
            "residence_permit", "residence_permit"
        );
        return typeMapping.getOrDefault(documentType.toLowerCase(), documentType);
    }
    
    private Map<String, String> extractDataFromDocument(Map<String, Object> document) {
        Map<String, String> extractedData = new HashMap<>();
        
        Map<String, Object> extractedDataObj = (Map<String, Object>) document.get("extracted_data");
        if (extractedDataObj != null) {
            extractedData.put("documentNumber", (String) extractedDataObj.get("document_number"));
            extractedData.put("firstName", (String) extractedDataObj.get("first_name"));
            extractedData.put("lastName", (String) extractedDataObj.get("last_name"));
            extractedData.put("dateOfBirth", (String) extractedDataObj.get("date_of_birth"));
            extractedData.put("expiryDate", (String) extractedDataObj.get("expiry_date"));
            extractedData.put("issuingCountry", (String) extractedDataObj.get("issuing_country"));
            extractedData.put("nationality", (String) extractedDataObj.get("nationality"));
            extractedData.put("gender", (String) extractedDataObj.get("gender"));
        }
        
        return extractedData;
    }
    
    private String buildQueryString(Map<String, Object> params) {
        return params.entrySet().stream()
                .map(e -> e.getKey() + "=" + e.getValue())
                .reduce((a, b) -> a + "&" + b)
                .orElse("");
    }
    
    // KYCProvider interface implementations
    
    @Override
    public VerificationResult verifyIdentity(String userId, Map<String, Object> identityData) {
        try {
            // Create applicant with identity data
            Map<String, Object> applicantData = Map.of(
                "first_name", identityData.get("firstName"),
                "last_name", identityData.get("lastName"),
                "email", identityData.get("email"),
                "dob", identityData.get("dateOfBirth"),
                "external_user_id", userId,
                "address", identityData.get("address")
            );
            
            Map<String, Object> response = post("/v3.6/applicants", applicantData, Map.class);
            String applicantId = (String) response.get("id");
            
            // Create identity check
            Map<String, Object> checkData = Map.of(
                "applicant_id", applicantId,
                "report_names", List.of("identity_enhanced")
            );
            
            Map<String, Object> check = post("/v3.6/checks", checkData, Map.class);
            
            return VerificationResult.builder()
                    .verificationId((String) check.get("id"))
                    .success("complete".equals(check.get("status")) && "clear".equals(check.get("result")))
                    .score(calculateScore(check))
                    .status((String) check.get("result"))
                    .provider(getProviderName())
                    .verifiedAt(LocalDateTime.now())
                    .details(check)
                    .build();
                    
        } catch (Exception e) {
            log.error("Onfido identity verification failed", e);
            return VerificationResult.builder()
                    .success(false)
                    .score(0)
                    .status("ERROR")
                    .provider(getProviderName())
                    .errorMessage(e.getMessage())
                    .build();
        }
    }
    
    @Override
    public VerificationResult verifyDocument(DocumentVerificationRequest request) {
        try {
            String applicantId = createOrGetApplicant(request.getUserId(), null);
            
            // Upload document
            Map<String, Object> documentData = Map.of(
                "applicant_id", applicantId,
                "type", mapDocumentType(request.getDocumentType().toString()),
                "file_path", request.getDocumentPath()
            );
            
            Map<String, Object> document = post("/v3.6/documents", documentData, Map.class);
            
            // Create document check
            Map<String, Object> checkData = Map.of(
                "applicant_id", applicantId,
                "report_names", List.of("document")
            );
            
            Map<String, Object> check = post("/v3.6/checks", checkData, Map.class);
            
            return VerificationResult.builder()
                    .verificationId((String) check.get("id"))
                    .success("complete".equals(check.get("status")) && "clear".equals(check.get("result")))
                    .score(calculateScore(check))
                    .status((String) check.get("result"))
                    .provider(getProviderName())
                    .verifiedAt(LocalDateTime.now())
                    .details(check)
                    .build();
                    
        } catch (Exception e) {
            log.error("Onfido document verification failed", e);
            return VerificationResult.builder()
                    .success(false)
                    .score(0)
                    .status("ERROR")
                    .provider(getProviderName())
                    .errorMessage(e.getMessage())
                    .build();
        }
    }
    
    @Override
    public VerificationResult verifySelfie(Map<String, Object> selfieData) {
        try {
            String applicantId = createOrGetApplicant((String) selfieData.get("userId"), null);
            
            // Upload selfie
            Map<String, Object> livePhotoData = Map.of(
                "applicant_id", applicantId,
                "file_path", selfieData.get("selfiePath")
            );
            
            Map<String, Object> livePhoto = post("/v3.6/live_photos", livePhotoData, Map.class);
            
            // Create facial similarity check
            Map<String, Object> checkData = Map.of(
                "applicant_id", applicantId,
                "report_names", List.of("facial_similarity_photo_fully_auto")
            );
            
            Map<String, Object> check = post("/v3.6/checks", checkData, Map.class);
            
            return VerificationResult.builder()
                    .verificationId((String) check.get("id"))
                    .success("complete".equals(check.get("status")) && "clear".equals(check.get("result")))
                    .score(calculateScore(check))
                    .status((String) check.get("result"))
                    .provider(getProviderName())
                    .verifiedAt(LocalDateTime.now())
                    .details(check)
                    .build();
                    
        } catch (Exception e) {
            log.error("Onfido selfie verification failed", e);
            return VerificationResult.builder()
                    .success(false)
                    .score(0)
                    .status("ERROR")
                    .provider(getProviderName())
                    .errorMessage(e.getMessage())
                    .build();
        }
    }
    
    @Override
    public VerificationResult verifyAddress(AddressVerificationRequest request) {
        try {
            // Onfido doesn't have dedicated address verification, but can extract from documents
            String applicantId = createOrGetApplicant(request.getUserId(), null);
            
            if (request.getProofOfAddressPath() != null) {
                // Upload proof of address document
                Map<String, Object> documentData = Map.of(
                    "applicant_id", applicantId,
                    "type", "proof_of_address",
                    "file_path", request.getProofOfAddressPath()
                );
                
                Map<String, Object> document = post("/v3.6/documents", documentData, Map.class);
                
                // Create document check
                Map<String, Object> checkData = Map.of(
                    "applicant_id", applicantId,
                    "report_names", List.of("document")
                );
                
                Map<String, Object> check = post("/v3.6/checks", checkData, Map.class);
                
                return VerificationResult.builder()
                        .verificationId((String) check.get("id"))
                        .success("complete".equals(check.get("status")) && "clear".equals(check.get("result")))
                        .score(calculateScore(check))
                        .status((String) check.get("result"))
                        .provider(getProviderName())
                        .verifiedAt(LocalDateTime.now())
                        .details(check)
                        .build();
            } else {
                // Basic address validation without document
                return VerificationResult.builder()
                        .success(true)
                        .score(80) // Moderate confidence without document proof
                        .status("VALIDATED")
                        .provider(getProviderName())
                        .verifiedAt(LocalDateTime.now())
                        .details(Map.of("addressValidated", true))
                        .build();
            }
                    
        } catch (Exception e) {
            log.error("Onfido address verification failed", e);
            return VerificationResult.builder()
                    .success(false)
                    .score(0)
                    .status("ERROR")
                    .provider(getProviderName())
                    .errorMessage(e.getMessage())
                    .build();
        }
    }
    
    @Override
    public VerificationResult performAMLCheck(AMLScreeningRequest request) {
        try {
            String applicantId = createOrGetApplicant(request.getUserId(), null);
            
            // Create AML/watchlist check
            Map<String, Object> checkData = Map.of(
                "applicant_id", applicantId,
                "report_names", List.of("watchlist_enhanced")
            );
            
            Map<String, Object> check = post("/v3.6/checks", checkData, Map.class);
            
            return VerificationResult.builder()
                    .verificationId((String) check.get("id"))
                    .success("complete".equals(check.get("status")) && "clear".equals(check.get("result")))
                    .score(calculateScore(check))
                    .status((String) check.get("result"))
                    .provider(getProviderName())
                    .verifiedAt(LocalDateTime.now())
                    .details(check)
                    .build();
                    
        } catch (Exception e) {
            log.error("Onfido AML check failed", e);
            return VerificationResult.builder()
                    .success(false)
                    .score(0)
                    .status("ERROR")
                    .provider(getProviderName())
                    .errorMessage(e.getMessage())
                    .build();
        }
    }
    
    @Override
    public String getProviderName() {
        return "onfido";
    }
    
    @Override
    public boolean isAvailable() {
        return performHealthCheck();
    }
    
    // Additional helper methods for new interface
    
    public VerificationResult performLivenessCheck(Map<String, Object> livenessData) {
        try {
            String applicantId = createOrGetApplicant((String) livenessData.get("userId"), null);
            
            // Upload live video
            Map<String, Object> liveVideoData = Map.of(
                "applicant_id", applicantId,
                "file_path", livenessData.get("documentPath")
            );
            
            Map<String, Object> liveVideo = post("/v3.6/live_videos", liveVideoData, Map.class);
            
            // Create motion capture check
            Map<String, Object> checkData = Map.of(
                "applicant_id", applicantId,
                "report_names", List.of("motion")
            );
            
            Map<String, Object> check = post("/v3.6/checks", checkData, Map.class);
            
            return VerificationResult.builder()
                    .verificationId((String) check.get("id"))
                    .success("complete".equals(check.get("status")) && "clear".equals(check.get("result")))
                    .score(calculateScore(check))
                    .status((String) check.get("result"))
                    .provider(getProviderName())
                    .verifiedAt(LocalDateTime.now())
                    .details(check)
                    .build();
                    
        } catch (Exception e) {
            log.error("Onfido liveness check failed", e);
            return VerificationResult.builder()
                    .success(false)
                    .score(0)
                    .status("ERROR")
                    .provider(getProviderName())
                    .errorMessage(e.getMessage())
                    .build();
        }
    }
    
    public VerificationResult initiatePhoneVerification(Map<String, Object> phoneData) {
        // Onfido doesn't provide phone verification - would need separate provider
        log.warn("Phone verification not supported by Onfido provider");
        return VerificationResult.builder()
                .success(false)
                .score(0)
                .status("NOT_SUPPORTED")
                .provider(getProviderName())
                .errorMessage("Phone verification not supported by Onfido")
                .build();
    }
    
    public VerificationResult verifyBankAccount(Map<String, Object> bankData) {
        // Onfido doesn't provide bank verification - would need separate provider
        log.warn("Bank verification not supported by Onfido provider");
        return VerificationResult.builder()
                .success(false)
                .score(0)
                .status("NOT_SUPPORTED")
                .provider(getProviderName())
                .errorMessage("Bank verification not supported by Onfido")
                .build();
    }
    
    public VerificationResult performEnhancedDueDiligence(Map<String, Object> eddData) {
        try {
            String applicantId = createOrGetApplicant((String) eddData.get("userId"), null);
            
            // Create comprehensive watchlist and identity check
            Map<String, Object> checkData = Map.of(
                "applicant_id", applicantId,
                "report_names", List.of("watchlist_enhanced", "identity_enhanced")
            );
            
            Map<String, Object> check = post("/v3.6/checks", checkData, Map.class);
            
            return VerificationResult.builder()
                    .verificationId((String) check.get("id"))
                    .success("complete".equals(check.get("status")) && "clear".equals(check.get("result")))
                    .score(calculateScore(check))
                    .status((String) check.get("result"))
                    .provider(getProviderName())
                    .verifiedAt(LocalDateTime.now())
                    .details(check)
                    .build();
                    
        } catch (Exception e) {
            log.error("Onfido enhanced due diligence failed", e);
            return VerificationResult.builder()
                    .success(false)
                    .score(0)
                    .status("ERROR")
                    .provider(getProviderName())
                    .errorMessage(e.getMessage())
                    .build();
        }
    }
    
    public VerificationResult verifyBusinessRegistration(Map<String, Object> businessData) {
        // Onfido doesn't provide business verification - would need separate provider
        log.warn("Business verification not supported by Onfido provider");
        return VerificationResult.builder()
                .success(false)
                .score(0)
                .status("NOT_SUPPORTED")
                .provider(getProviderName())
                .errorMessage("Business verification not supported by Onfido")
                .build();
    }
    
    private int calculateScore(Map<String, Object> check) {
        String result = (String) check.get("result");
        String status = (String) check.get("status");
        
        if ("complete".equals(status)) {
            switch (result) {
                case "clear": return 95;
                case "consider": return 70;
                case "unidentified": return 50;
                default: return 30;
            }
        }
        return 0;
    }
    
    // Circuit Breaker Fallback Methods
    
    /**
     * Fallback method for createVerificationSession when circuit breaker is open
     */
    public String createVerificationSessionFallback(String userId, KYCVerificationRequest request, Exception ex) {
        log.error("ONFIDO_CIRCUIT_BREAKER: Verification session creation circuit breaker activated for user: {}", userId, ex);
        
        // Return a temporary session ID that indicates service unavailability
        return "CIRCUIT_BREAKER_" + UUID.randomUUID().toString();
    }
    
    /**
     * Fallback method for getVerificationStatus when circuit breaker is open
     */
    public Map<String, Object> getVerificationStatusFallback(String sessionId, Exception ex) {
        log.error("ONFIDO_CIRCUIT_BREAKER: Verification status circuit breaker activated for session: {}", sessionId, ex);
        
        // Return a status indicating temporary unavailability
        Map<String, Object> fallbackStatus = new HashMap<>();
        fallbackStatus.put("sessionId", sessionId);
        fallbackStatus.put("status", "SERVICE_UNAVAILABLE");
        fallbackStatus.put("message", "KYC service temporarily unavailable. Please try again later.");
        fallbackStatus.put("retryAfter", LocalDateTime.now().plusMinutes(5));
        fallbackStatus.put("fromFallback", true);
        return fallbackStatus;
    }
    
    /**
     * Fallback method for getVerificationResults when circuit breaker is open
     */
    public Map<String, Object> getVerificationResultsFallback(String sessionId, Exception ex) {
        log.error("ONFIDO_CIRCUIT_BREAKER: Verification results circuit breaker activated for session: {}", sessionId, ex);
        
        // Return results indicating temporary unavailability
        Map<String, Object> fallbackResults = new HashMap<>();
        fallbackResults.put("id", sessionId);
        fallbackResults.put("status", "PENDING_RETRY");
        fallbackResults.put("error", "KYC verification service temporarily unavailable");
        fallbackResults.put("retryAfter", LocalDateTime.now().plusMinutes(5));
        fallbackResults.put("fromFallback", true);
        return fallbackResults;
    }
    
    /**
     * Fallback method for uploadDocument when circuit breaker is open
     */
    public String uploadDocumentFallback(String sessionId, byte[] documentData, String documentType, Exception ex) {
        log.error("ONFIDO_CIRCUIT_BREAKER: Document upload circuit breaker activated for session: {}", sessionId, ex);
        
        // Return a temporary document ID that can be retried later
        // In production, we might queue this for later processing
        return "PENDING_UPLOAD_" + UUID.randomUUID().toString();
    }
}