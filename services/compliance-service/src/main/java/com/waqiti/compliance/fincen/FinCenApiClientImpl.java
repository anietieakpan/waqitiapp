package com.waqiti.compliance.fincen;

import com.waqiti.common.vault.VaultSecretService;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import javax.annotation.PostConstruct;
import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "fincen.enabled", havingValue = "true")
public class FinCenApiClientImpl implements FinCenApiClient {

    private final RestTemplate restTemplate;
    private final VaultSecretService vaultSecretService;

    @Value("${fincen.api.url:https://bsaefiling1.fincen.treas.gov/main.html}")
    private String finCenApiUrl;

    // Secrets moved to Vault - fetched at runtime for security
    // @Value("${fincen.api.username}")
    // private String username;

    // @Value("${fincen.api.password}")
    // private String password;

    @Value("${fincen.filing-institution.tin}")
    private String filingInstitutionTin;

    @Value("${fincen.filing-institution.name}")
    private String filingInstitutionName;

    @PostConstruct
    public void initialize() {
        log.info("FinCEN API client initialized. URL: {}", finCenApiUrl);
    }

    @Override
    @CircuitBreaker(name = "fincen-api")
    @Retry(name = "fincen-api")
    public String submitSAR(SuspiciousActivityReport sar) {
        try {
            log.info("Submitting SAR to FinCEN. Subject: {}", sar.getSubjectName());

            Map<String, Object> bsaXml = buildSarXml(sar);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_XML);
            headers.setBasicAuth(
                vaultSecretService.getSecret("fincen/api/username", "compliance-service"),
                vaultSecretService.getSecret("fincen/api/password", "compliance-service")
            );

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(bsaXml, headers);

            ResponseEntity<Map> response = restTemplate.postForEntity(
                finCenApiUrl + "/submit",
                request,
                Map.class
            );

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                String sarId = (String) response.getBody().get("bsaId");
                log.info("SAR submitted successfully. SAR ID: {}", sarId);
                return sarId;
            }

            throw new FinCenException("SAR submission failed: " + response.getStatusCode());

        } catch (Exception e) {
            log.error("Failed to submit SAR to FinCEN", e);
            throw new FinCenException("SAR submission failed", e);
        }
    }

    @Override
    @CircuitBreaker(name = "fincen-api")
    @Retry(name = "fincen-api")
    public SarStatus getSARStatus(String sarId) {
        try {
            log.info("Retrieving SAR status from FinCEN. SAR ID: {}", sarId);

            HttpHeaders headers = new HttpHeaders();
            headers.setBasicAuth(
                vaultSecretService.getSecret("fincen/api/username", "compliance-service"),
                vaultSecretService.getSecret("fincen/api/password", "compliance-service")
            );

            HttpEntity<?> request = new HttpEntity<>(headers);

            ResponseEntity<Map> response = restTemplate.exchange(
                finCenApiUrl + "/status/" + sarId,
                HttpMethod.GET,
                request,
                Map.class
            );

            if (response.getBody() != null) {
                String status = (String) response.getBody().get("status");
                return SarStatus.valueOf(status);
            }

            return SarStatus.UNKNOWN;

        } catch (Exception e) {
            log.error("Failed to get SAR status from FinCEN. SAR ID: {}", sarId, e);
            return SarStatus.UNKNOWN;
        }
    }

    @Override
    @CircuitBreaker(name = "fincen-api")
    @Retry(name = "fincen-api")
    public String amendSAR(String originalSarId, SuspiciousActivityReport amendedSar) {
        try {
            log.info("Amending SAR in FinCEN. Original SAR ID: {}", originalSarId);

            Map<String, Object> bsaXml = buildSarXml(amendedSar);
            bsaXml.put("priorBsaId", originalSarId);
            bsaXml.put("amendmentFlag", true);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_XML);
            headers.setBasicAuth(
                vaultSecretService.getSecret("fincen/api/username", "compliance-service"),
                vaultSecretService.getSecret("fincen/api/password", "compliance-service")
            );

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(bsaXml, headers);

            ResponseEntity<Map> response = restTemplate.postForEntity(
                finCenApiUrl + "/amend",
                request,
                Map.class
            );

            if (response.getBody() != null) {
                String sarId = (String) response.getBody().get("bsaId");
                log.info("SAR amended successfully. New SAR ID: {}", sarId);
                return sarId;
            }

            throw new FinCenException("SAR amendment failed");

        } catch (Exception e) {
            log.error("Failed to amend SAR in FinCEN", e);
            throw new FinCenException("SAR amendment failed", e);
        }
    }

    private Map<String, Object> buildSarXml(SuspiciousActivityReport sar) {
        Map<String, Object> xml = new HashMap<>();
        
        xml.put("activityType", "SAR");
        xml.put("filingInstitution", buildFilingInstitution());
        xml.put("subjectInformation", buildSubjectInformation(sar));
        xml.put("suspiciousActivity", buildSuspiciousActivity(sar));
        xml.put("narrativeExplanation", sar.getNarrative());
        xml.put("filingDate", LocalDateTime.now().toString());

        return xml;
    }

    private Map<String, Object> buildFilingInstitution() {
        Map<String, Object> institution = new HashMap<>();
        institution.put("tin", filingInstitutionTin);
        institution.put("name", filingInstitutionName);
        institution.put("type", "31"); // Money services business
        return institution;
    }

    private Map<String, Object> buildSubjectInformation(SuspiciousActivityReport sar) {
        Map<String, Object> subject = new HashMap<>();
        subject.put("name", sar.getSubjectName());
        subject.put("address", sar.getSubjectAddress());
        subject.put("tin", sar.getSubjectTin());
        subject.put("dateOfBirth", sar.getSubjectDateOfBirth());
        subject.put("identification", sar.getSubjectIdentification());
        return subject;
    }

    private Map<String, Object> buildSuspiciousActivity(SuspiciousActivityReport sar) {
        Map<String, Object> activity = new HashMap<>();
        activity.put("activityDate", sar.getActivityDate());
        activity.put("amountInvolved", sar.getTotalAmount());
        activity.put("typeOfSuspiciousActivity", sar.getActivityTypes());
        activity.put("products", sar.getProductsInvolved());
        return activity;
    }

    public static class FinCenException extends RuntimeException {
        public FinCenException(String message) {
            super(message);
        }
        public FinCenException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    public enum SarStatus {
        SUBMITTED,
        ACCEPTED,
        REJECTED,
        PENDING,
        UNKNOWN
    }
}
