package com.waqiti.business.verification.address;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.common.vault.VaultSecretManager;
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

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * USPS Address Verification Service
 *
 * Verifies and standardizes business addresses using USPS Address Validation API.
 *
 * Features:
 * - Real-time address validation
 * - Address standardization (CASS certified)
 * - Deliverability confirmation
 * - DPV (Delivery Point Validation)
 * - Address correction and suggestions
 *
 * USPS APIs:
 * - Address Validation API
 * - ZIP Code Lookup API
 * - City/State Lookup API
 *
 * Compliance:
 * - CASS (Coding Accuracy Support System)
 * - NCOA (National Change of Address)
 * - BSA/AML address verification requirements
 *
 * @author Waqiti Platform
 * @version 1.0.0
 * @since 2025-10-02
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UspsAddressVerificationService {

    private final RestTemplate restTemplate;
    private final VaultSecretManager vaultSecretManager;
    private final ObjectMapper objectMapper;

    @Value("${usps.api.base-url:https://secure.shippingapis.com/ShippingAPI.dll}")
    private String uspsApiUrl;

    @Value("${usps.api.enabled:true}")
    private boolean uspsVerificationEnabled;

    /**
     * Verify and standardize address with USPS.
     *
     * @param address Street address
     * @param city City
     * @param state State
     * @param zipCode ZIP code
     * @return Address verification result
     */
    @CircuitBreaker(name = "usps-address-verification", fallbackMethod = "verifyAddressFallback")
    @Retry(name = "usps-address-verification")
    public AddressVerificationResult verifyAddress(String address, String city, String state, String zipCode) {
        log.info("COMPLIANCE: Verifying address with USPS - City: {}, State: {}, ZIP: {}",
                city, state, zipCode);

        long startTime = System.currentTimeMillis();

        try {
            if (!uspsVerificationEnabled) {
                return performBasicValidation(address, city, state, zipCode, startTime);
            }

            // Get USPS API credentials
            String userId = vaultSecretManager.getSecret("usps/api-user-id");

            // Build USPS XML request
            String xmlRequest = buildUspsXmlRequest(userId, address, city, state, zipCode);

            // Call USPS API
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_XML);

            String url = uspsApiUrl + "?API=Verify&XML=" + xmlRequest;

            ResponseEntity<String> response = restTemplate.exchange(
                url,
                HttpMethod.GET,
                new HttpEntity<>(headers),
                String.class
            );

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                return parseUspsResponse(response.getBody(), address, city, state, zipCode, startTime);
            } else {
                log.warn("COMPLIANCE: USPS API returned non-success status: {}", response.getStatusCode());
                return performBasicValidation(address, city, state, zipCode, startTime);
            }

        } catch (Exception e) {
            log.error("COMPLIANCE: USPS address verification failed", e);
            return performBasicValidation(address, city, state, zipCode, startTime);
        }
    }

    /**
     * Build USPS XML request.
     */
    private String buildUspsXmlRequest(String userId, String address, String city, String state, String zipCode) {
        return String.format(
            "<AddressValidateRequest USERID=\"%s\">" +
            "<Revision>1</Revision>" +
            "<Address ID=\"0\">" +
            "<Address1></Address1>" +
            "<Address2>%s</Address2>" +
            "<City>%s</City>" +
            "<State>%s</State>" +
            "<Zip5>%s</Zip5>" +
            "<Zip4></Zip4>" +
            "</Address>" +
            "</AddressValidateRequest>",
            userId,
            escapeXml(address),
            escapeXml(city),
            escapeXml(state),
            escapeXml(zipCode != null ? zipCode.substring(0, Math.min(5, zipCode.length())) : "")
        );
    }

    /**
     * Parse USPS XML response.
     */
    private AddressVerificationResult parseUspsResponse(String xmlResponse, String originalAddress,
                                                        String originalCity, String originalState,
                                                        String originalZip, long startTime) {
        try {
            // Check for error response
            if (xmlResponse.contains("<Error>")) {
                String errorMsg = extractXmlValue(xmlResponse, "Description");
                log.warn("COMPLIANCE: USPS validation error: {}", errorMsg);

                return AddressVerificationResult.builder()
                    .valid(false)
                    .message("USPS validation error: " + errorMsg)
                    .originalAddress(formatAddress(originalAddress, originalCity, originalState, originalZip))
                    .verificationMethod("USPS_API")
                    .verifiedAt(LocalDateTime.now())
                    .durationMs(System.currentTimeMillis() - startTime)
                    .build();
            }

            // Extract standardized address components
            String standardizedAddress = extractXmlValue(xmlResponse, "Address2");
            String standardizedCity = extractXmlValue(xmlResponse, "City");
            String standardizedState = extractXmlValue(xmlResponse, "State");
            String zip5 = extractXmlValue(xmlResponse, "Zip5");
            String zip4 = extractXmlValue(xmlResponse, "Zip4");
            String dpvConfirmation = extractXmlValue(xmlResponse, "DPVConfirmation");

            String standardizedZip = zip5 + (zip4 != null && !zip4.isEmpty() ? "-" + zip4 : "");
            String fullStandardizedAddress = formatAddress(standardizedAddress, standardizedCity,
                                                          standardizedState, standardizedZip);

            // Check if address was corrected
            boolean corrected = !standardizedAddress.equalsIgnoreCase(originalAddress) ||
                               !standardizedCity.equalsIgnoreCase(originalCity) ||
                               !standardizedState.equalsIgnoreCase(originalState);

            // DPV confirmation: Y = confirmed, N = not confirmed, D = missing secondary
            boolean dpvConfirmed = "Y".equals(dpvConfirmation);

            return AddressVerificationResult.builder()
                .valid(dpvConfirmed)
                .corrected(corrected)
                .originalAddress(formatAddress(originalAddress, originalCity, originalState, originalZip))
                .standardizedAddress(fullStandardizedAddress)
                .standardizedStreet(standardizedAddress)
                .standardizedCity(standardizedCity)
                .standardizedState(standardizedState)
                .standardizedZip(standardizedZip)
                .dpvConfirmed(dpvConfirmed)
                .message(dpvConfirmed ? "Address validated and standardized" :
                        "Address format valid but deliverability uncertain")
                .verificationMethod("USPS_API")
                .verifiedAt(LocalDateTime.now())
                .durationMs(System.currentTimeMillis() - startTime)
                .build();

        } catch (Exception e) {
            log.error("COMPLIANCE: Failed to parse USPS response", e);
            return performBasicValidation(originalAddress, originalCity, originalState, originalZip, startTime);
        }
    }

    /**
     * Perform basic format validation when USPS API is unavailable.
     */
    private AddressVerificationResult performBasicValidation(String address, String city, String state,
                                                             String zipCode, long startTime) {
        boolean valid = address != null && !address.trim().isEmpty() &&
                       city != null && !city.trim().isEmpty() &&
                       state != null && state.length() == 2 &&
                       zipCode != null && zipCode.matches("^\\d{5}(-\\d{4})?$");

        return AddressVerificationResult.builder()
            .valid(valid)
            .corrected(false)
            .originalAddress(formatAddress(address, city, state, zipCode))
            .standardizedAddress(formatAddress(address, city, state, zipCode))
            .message(valid ? "Basic format validation passed - USPS verification unavailable" :
                           "Address format invalid")
            .verificationMethod("BASIC_FORMAT")
            .verifiedAt(LocalDateTime.now())
            .durationMs(System.currentTimeMillis() - startTime)
            .build();
    }

    /**
     * Fallback method for circuit breaker.
     */
    @SuppressWarnings("unused")
    private AddressVerificationResult verifyAddressFallback(String address, String city, String state,
                                                            String zipCode, Exception e) {
        log.error("COMPLIANCE FALLBACK: USPS address verification circuit breaker activated", e);
        return performBasicValidation(address, city, state, zipCode, System.currentTimeMillis());
    }

    /**
     * Extract value from XML response.
     */
    private String extractXmlValue(String xml, String tagName) {
        try {
            String startTag = "<" + tagName + ">";
            String endTag = "</" + tagName + ">";
            int startIndex = xml.indexOf(startTag);
            int endIndex = xml.indexOf(endTag);

            if (startIndex != -1 && endIndex != -1) {
                return xml.substring(startIndex + startTag.length(), endIndex).trim();
            }
        } catch (Exception e) {
            log.debug("Could not extract XML value for tag: {}", tagName);
        }
        return null;
    }

    /**
     * Escape XML special characters.
     */
    private String escapeXml(String value) {
        if (value == null) return "";
        return value.replace("&", "&amp;")
                   .replace("<", "&lt;")
                   .replace(">", "&gt;")
                   .replace("\"", "&quot;")
                   .replace("'", "&apos;");
    }

    /**
     * Format complete address string.
     */
    private String formatAddress(String street, String city, String state, String zip) {
        StringBuilder sb = new StringBuilder();
        if (street != null) sb.append(street);
        if (city != null) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(city);
        }
        if (state != null) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(state);
        }
        if (zip != null) {
            if (sb.length() > 0) sb.append(" ");
            sb.append(zip);
        }
        return sb.toString();
    }

    // Supporting DTO

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AddressVerificationResult {
        private Boolean valid;
        private Boolean corrected;
        private String originalAddress;
        private String standardizedAddress;
        private String standardizedStreet;
        private String standardizedCity;
        private String standardizedState;
        private String standardizedZip;
        private Boolean dpvConfirmed;
        private String message;
        private String verificationMethod;
        private LocalDateTime verifiedAt;
        private Long durationMs;
    }
}
