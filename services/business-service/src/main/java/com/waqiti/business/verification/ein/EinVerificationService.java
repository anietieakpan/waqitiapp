package com.waqiti.business.verification.ein;

import com.waqiti.common.vault.VaultSecretManager;
import com.waqiti.security.logging.SecureLoggingService;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * EIN Verification Service
 *
 * Verifies Employer Identification Numbers (EIN) with IRS.
 *
 * Verification Methods:
 * 1. Format validation (9 digits, valid prefix)
 * 2. IRS EIN validation API (when available)
 * 3. Business name matching
 * 4. Status verification (active/inactive)
 *
 * Compliance:
 * - IRS Publication 1075 (Tax Information Security)
 * - BSA/AML business verification requirements
 * - FCRA business reporting standards
 *
 * @author Waqiti Platform
 * @version 1.0.0
 * @since 2025-10-02
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EinVerificationService {

    private final RestTemplate restTemplate;
    private final VaultSecretManager vaultSecretManager;
    private final SecureLoggingService secureLoggingService;

    @Value("${irs.ein.verification.api.url:https://api.irs.gov/ein/verify}")
    private String irsApiUrl;

    @Value("${irs.ein.verification.enabled:true}")
    private boolean einVerificationEnabled;

    /**
     * Verify EIN with IRS and business name matching.
     *
     * @param ein The EIN to verify
     * @param businessName The business name to match
     * @return Verification result
     */
    @CircuitBreaker(name = "ein-verification", fallbackMethod = "verifyEinFallback")
    @Retry(name = "ein-verification")
    public EinVerificationResult verifyEin(String ein, String businessName) {
        log.info("COMPLIANCE: Verifying EIN: {}", maskEin(ein));

        long startTime = System.currentTimeMillis();

        try {
            // Step 1: Format validation
            FormatValidationResult formatResult = validateEinFormat(ein);
            if (!formatResult.isValid()) {
                return EinVerificationResult.builder()
                    .valid(false)
                    .ein(ein)
                    .message("Invalid EIN format: " + formatResult.getMessage())
                    .verificationMethod("FORMAT_VALIDATION")
                    .verifiedAt(LocalDateTime.now())
                    .durationMs(System.currentTimeMillis() - startTime)
                    .build();
            }

            // Step 2: IRS API verification (if enabled and available)
            if (einVerificationEnabled) {
                IrsApiResult irsResult = verifyWithIrsApi(ein, businessName);

                if (irsResult != null) {
                    return EinVerificationResult.builder()
                        .valid(irsResult.isValid())
                        .ein(ein)
                        .businessName(irsResult.getBusinessName())
                        .businessNameMatch(irsResult.isBusinessNameMatch())
                        .einStatus(irsResult.getStatus())
                        .issuedDate(irsResult.getIssuedDate())
                        .message(irsResult.getMessage())
                        .verificationMethod("IRS_API")
                        .verifiedAt(LocalDateTime.now())
                        .durationMs(System.currentTimeMillis() - startTime)
                        .build();
                }
            }

            // Step 3: Fallback to format validation only
            log.warn("COMPLIANCE: IRS API verification unavailable, using format validation only for EIN: {}", maskEin(ein));

            List<String> warnings = new ArrayList<>();
            warnings.add("Full IRS verification unavailable - format validation only");
            warnings.add("Manual verification may be required");

            return EinVerificationResult.builder()
                .valid(true)
                .ein(ein)
                .businessName(businessName)
                .businessNameMatch(null) // Cannot verify without API
                .message("EIN format valid - IRS verification pending")
                .warnings(warnings)
                .verificationMethod("FORMAT_ONLY")
                .verifiedAt(LocalDateTime.now())
                .durationMs(System.currentTimeMillis() - startTime)
                .build();

        } catch (Exception e) {
            log.error("COMPLIANCE: EIN verification failed for EIN: {}", maskEin(ein), e);

            return EinVerificationResult.builder()
                .valid(false)
                .ein(ein)
                .message("EIN verification error: " + e.getMessage())
                .verificationMethod("ERROR")
                .verifiedAt(LocalDateTime.now())
                .durationMs(System.currentTimeMillis() - startTime)
                .build();
        }
    }

    /**
     * Validate EIN format.
     */
    private FormatValidationResult validateEinFormat(String ein) {
        // Remove hyphens and spaces
        String cleanEin = ein.replaceAll("[^0-9]", "");

        // Must be 9 digits
        if (cleanEin.length() != 9) {
            return new FormatValidationResult(false, "EIN must be 9 digits");
        }

        // Validate prefix (first 2 digits)
        int prefix = Integer.parseInt(cleanEin.substring(0, 2));

        boolean validPrefix =
            (prefix >= 1 && prefix <= 6) ||   // Northeast
            (prefix >= 10 && prefix <= 16) ||  // Southeast
            (prefix >= 20 && prefix <= 27) ||  // Central
            (prefix >= 30 && prefix <= 39) ||  // Southwest
            (prefix >= 40 && prefix <= 48) ||  // Mountain
            (prefix >= 50 && prefix <= 59) ||  // Pacific
            (prefix >= 60 && prefix <= 67) ||  // Philadelphia
            (prefix >= 68 && prefix <= 77) ||  // Internet
            (prefix >= 80 && prefix <= 88) ||  // Philadelphia Campus
            (prefix >= 90 && prefix <= 95) ||  // Memphis Campus
            (prefix >= 98 && prefix <= 99);    // International

        if (!validPrefix) {
            return new FormatValidationResult(false, "Invalid EIN prefix: " + prefix);
        }

        return new FormatValidationResult(true, "EIN format valid");
    }

    /**
     * Verify EIN with IRS API.
     *
     * Note: IRS does not provide a public real-time EIN verification API.
     * This method simulates integration with third-party services like:
     * - Melissa Data EIN verification
     * - LexisNexis Business Verification
     * - Dun & Bradstreet verification
     */
    private IrsApiResult verifyWithIrsApi(String ein, String businessName) {
        try {
            log.debug("COMPLIANCE: Calling IRS/third-party EIN verification API");

            // Get API credentials from Vault
            String apiKey = vaultSecretManager.getSecret("irs/ein-verification-api-key");

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Authorization", "Bearer " + apiKey);

            Map<String, String> requestBody = new HashMap<>();
            requestBody.put("ein", ein);
            requestBody.put("business_name", businessName);

            HttpEntity<Map<String, String>> request = new HttpEntity<>(requestBody, headers);

            ResponseEntity<Map> response = restTemplate.exchange(
                irsApiUrl,
                HttpMethod.POST,
                request,
                Map.class
            );

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Map<String, Object> result = response.getBody();

                return IrsApiResult.builder()
                    .valid((Boolean) result.get("valid"))
                    .businessName((String) result.get("business_name"))
                    .businessNameMatch((Boolean) result.get("name_match"))
                    .status((String) result.get("status"))
                    .issuedDate(result.get("issued_date") != null ?
                               LocalDate.parse((String) result.get("issued_date")) : null)
                    .message((String) result.get("message"))
                    .build();
            }

            return null;

        } catch (Exception e) {
            log.warn("COMPLIANCE: IRS API verification failed, will fallback to format validation", e);
            return null;
        }
    }

    /**
     * Fallback method when circuit breaker opens.
     */
    @SuppressWarnings("unused")
    private EinVerificationResult verifyEinFallback(String ein, String businessName, Exception e) {
        log.error("COMPLIANCE FALLBACK: EIN verification circuit breaker activated for EIN: {}", maskEin(ein), e);

        // Perform format validation only as fallback
        FormatValidationResult formatResult = validateEinFormat(ein);

        List<String> warnings = new ArrayList<>();
        warnings.add("Circuit breaker activated - using format validation only");
        warnings.add("Manual verification required");

        return EinVerificationResult.builder()
            .valid(formatResult.isValid())
            .ein(ein)
            .businessName(businessName)
            .message(formatResult.getMessage())
            .warnings(warnings)
            .verificationMethod("FALLBACK_FORMAT")
            .verifiedAt(LocalDateTime.now())
            .build();
    }

    /**
     * Mask EIN for logging.
     */
    private String maskEin(String ein) {
        if (ein == null || ein.length() < 4) {
            return "****";
        }
        String cleaned = ein.replaceAll("[^0-9]", "");
        return "**-***" + cleaned.substring(cleaned.length() - 4);
    }

    // Supporting DTOs

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EinVerificationResult {
        private Boolean valid;
        private String ein;
        private String businessName;
        private Boolean businessNameMatch;
        private String einStatus;
        private LocalDate issuedDate;
        private String message;
        private List<String> warnings;
        private String verificationMethod;
        private LocalDateTime verifiedAt;
        private Long durationMs;

        public boolean hasWarnings() {
            return warnings != null && !warnings.isEmpty();
        }

        public String getWarnings() {
            return warnings != null ? String.join("; ", warnings) : "";
        }
    }

    @Data
    @AllArgsConstructor
    private static class FormatValidationResult {
        private boolean valid;
        private String message;
    }

    @Data
    @Builder
    private static class IrsApiResult {
        private Boolean valid;
        private String businessName;
        private Boolean businessNameMatch;
        private String status;
        private LocalDate issuedDate;
        private String message;
    }
}
