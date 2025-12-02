package com.waqiti.compliance.kyc;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
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
@ConditionalOnProperty(name = "kyc.provider", havingValue = "ONFIDO")
public class OnfidoKycProviderImpl {

    private final RestTemplate restTemplate;

    @Value("${onfido.api.token}")
    private String apiToken;

    @Value("${onfido.api.url:https://api.onfido.com/v3}")
    private String apiUrl;

    @PostConstruct
    public void initialize() {
        log.info("Onfido KYC provider initialized. API URL: {}", apiUrl);
    }

    @CircuitBreaker(name = "onfido-kyc")
    @Retry(name = "onfido-kyc")
    @RateLimiter(name = "onfido-kyc")
    public String createApplicant(Map<String, Object> applicantData) {
        try {
            log.info("Creating Onfido applicant");

            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(apiToken);
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(applicantData, headers);

            ResponseEntity<Map> response = restTemplate.postForEntity(
                apiUrl + "/applicants",
                request,
                Map.class
            );

            String applicantId = (String) response.getBody().get("id");
            log.info("Onfido applicant created. ID: {}", applicantId);
            return applicantId;

        } catch (Exception e) {
            log.error("Failed to create Onfido applicant", e);
            throw new RuntimeException("KYC applicant creation failed", e);
        }
    }

    @CircuitBreaker(name = "onfido-kyc")
    @Retry(name = "onfido-kyc")
    public String initiateCheck(String applicantId, List<String> reportTypes) {
        try {
            log.info("Initiating Onfido check for applicant: {}", applicantId);

            Map<String, Object> checkData = new HashMap<>();
            checkData.put("applicant_id", applicantId);
            checkData.put("report_names", reportTypes);

            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(apiToken);
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(checkData, headers);

            ResponseEntity<Map> response = restTemplate.postForEntity(
                apiUrl + "/checks",
                request,
                Map.class
            );

            String checkId = (String) response.getBody().get("id");
            log.info("Onfido check initiated. CheckId: {}", checkId);
            return checkId;

        } catch (Exception e) {
            log.error("Failed to initiate Onfido check", e);
            throw new RuntimeException("KYC check initiation failed", e);
        }
    }

    @CircuitBreaker(name = "onfido-kyc")
    public Map<String, Object> getCheckResult(String checkId) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(apiToken);

            HttpEntity<?> request = new HttpEntity<>(headers);

            ResponseEntity<Map> response = restTemplate.exchange(
                apiUrl + "/checks/" + checkId,
                HttpMethod.GET,
                request,
                Map.class
            );

            return response.getBody();

        } catch (Exception e) {
            log.error("Failed to get check result", e);
            throw new RuntimeException("KYC check retrieval failed", e);
        }
    }
}
