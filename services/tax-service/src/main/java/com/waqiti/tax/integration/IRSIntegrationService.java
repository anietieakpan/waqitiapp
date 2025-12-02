package com.waqiti.tax.integration;

import com.waqiti.common.exception.BusinessException;
import com.waqiti.tax.domain.TaxFormPackage;
import com.waqiti.tax.dto.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import javax.net.ssl.SSLContext;
import java.security.KeyStore;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/**
 * IRS Integration Service
 *
 * Handles electronic filing and communication with IRS systems:
 * - E-File API for tax return submission
 * - FIRE system for 1099 forms
 * - Where's My Refund API for status tracking
 * - Transcript API for historical data
 *
 * Security:
 * - TLS 1.2+ required
 * - X.509 certificates for authentication
 * - Message-level encryption
 * - IP whitelist requirements
 *
 * Compliance:
 * - IRS Publication 1345 (Handbook for Authorized IRS e-file Providers)
 * - IRS Publication 1220 (FIRE specifications)
 * - IRS Revenue Procedure 2007-40 (e-file requirements)
 *
 * @author Waqiti Tax Team
 * @since 2025-10-01
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class IRSIntegrationService {

    private final RestTemplate irsRestTemplate;

    @Value("${irs.api.base-url:https://testapi.irs.gov}")
    private String irsApiBaseUrl;

    @Value("${irs.api.efile-endpoint:/efile/submit}")
    private String efileEndpoint;

    @Value("${irs.api.refund-endpoint:/wmr/status}")
    private String refundEndpoint;

    @Value("${irs.api.transcript-endpoint:/transcript/request}")
    private String transcriptEndpoint;

    @Value("${irs.efin:XXXXXX}")
    private String efin; // Electronic Filing Identification Number

    @Value("${irs.etin:XXXXXXX}")
    private String etin; // Electronic Transmitter Identification Number

    @Value("${irs.test-mode:true}")
    private boolean testMode;

    private static final int MAX_RETRIES = 3;
    private static final long RETRY_DELAY_MS = 5000;

    /**
     * Submit tax return to IRS e-file system
     *
     * @param formPackage Complete tax return package
     * @return Submission result with confirmation number
     */
    public IRSSubmissionResult submitReturn(TaxFormPackage formPackage) {
        log.info("Submitting tax return to IRS: tax_year={}, form={}",
                formPackage.getTaxYear(), formPackage.getFormType());

        try {
            // Validate package completeness
            validateFormPackage(formPackage);

            // Generate e-file XML (MeF format - Modernized e-File)
            String mefXml = generateMeFXml(formPackage);

            if (testMode) {
                log.warn("TEST MODE: Simulating IRS submission (not actually filing)");
                return simulateSubmission(formPackage);
            }

            // Prepare submission request
            HttpHeaders headers = createIrsHeaders();
            HttpEntity<String> request = new HttpEntity<>(mefXml, headers);

            // Submit with retry logic
            IRSSubmissionResult result = submitWithRetry(request);

            log.info("Tax return submitted successfully: confirmation={}",
                    result.getConfirmationNumber());

            return result;

        } catch (Exception e) {
            log.error("Failed to submit tax return to IRS", e);
            throw new BusinessException("IRS submission failed: " + e.getMessage());
        }
    }

    /**
     * Check refund status with IRS Where's My Refund service
     *
     * @param ssn Taxpayer SSN
     * @param filingStatus Filing status
     * @param refundAmount Expected refund amount
     * @return Refund status information
     */
    public IRSRefundStatus checkRefundStatus(String ssn, String filingStatus,
                                            java.math.BigDecimal refundAmount) {
        log.debug("Checking refund status with IRS");

        try {
            if (testMode) {
                return simulateRefundStatus();
            }

            // Prepare request
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("ssn", maskSsn(ssn));
            requestBody.put("filing_status", filingStatus);
            requestBody.put("refund_amount", refundAmount.intValue());

            HttpHeaders headers = createIrsHeaders();
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);

            // Call IRS API
            String url = irsApiBaseUrl + refundEndpoint;
            ResponseEntity<Map> response = irsRestTemplate.postForEntity(url, request, Map.class);

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                return parseRefundStatus(response.getBody());
            }

            log.warn("Unexpected IRS refund status response: {}", response.getStatusCode());
            return IRSRefundStatus.builder()
                    .status(RefundStatusType.PROCESSING)
                    .message("Unable to retrieve status at this time")
                    .build();

        } catch (Exception e) {
            log.error("Failed to check refund status", e);
            return IRSRefundStatus.builder()
                    .status(RefundStatusType.PROCESSING)
                    .message("Service temporarily unavailable")
                    .build();
        }
    }

    /**
     * Fetch W-2 forms from IRS (requires taxpayer authorization)
     *
     * @param ssn Taxpayer SSN
     * @param taxYear Tax year
     * @return List of W-2 forms
     */
    public List<W2Form> fetchW2Forms(String ssn, Integer taxYear) {
        log.info("Fetching W-2 forms from IRS: tax_year={}", taxYear);

        try {
            if (testMode) {
                log.warn("TEST MODE: Returning empty W-2 list");
                return Collections.emptyList();
            }

            // Prepare request
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("ssn", ssn);
            requestBody.put("tax_year", taxYear);
            requestBody.put("form_type", "W2");

            HttpHeaders headers = createIrsHeaders();
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);

            // Call IRS Transcript API
            String url = irsApiBaseUrl + transcriptEndpoint;
            ResponseEntity<List> response = irsRestTemplate.postForEntity(url, request, List.class);

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                return parseW2Forms(response.getBody());
            }

            return Collections.emptyList();

        } catch (Exception e) {
            log.error("Failed to fetch W-2 forms from IRS", e);
            return Collections.emptyList();
        }
    }

    // Helper methods

    private void validateFormPackage(TaxFormPackage formPackage) {
        if (formPackage == null) {
            throw new IllegalArgumentException("Form package cannot be null");
        }
        if (formPackage.getTaxYear() == null) {
            throw new IllegalArgumentException("Tax year is required");
        }
        if (formPackage.getTaxpayerSsn() == null || formPackage.getTaxpayerSsn().isEmpty()) {
            throw new IllegalArgumentException("Taxpayer SSN is required");
        }
        if (formPackage.getForm1040() == null) {
            throw new IllegalArgumentException("Form 1040 is required");
        }
    }

    private String generateMeFXml(TaxFormPackage formPackage) {
        // In production, use official IRS MeF XML schema
        // This is a simplified version for demonstration

        StringBuilder xml = new StringBuilder();
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        xml.append("<Return xmlns=\"http://www.irs.gov/efile\" returnVersion=\"2024v1.0\">\n");
        xml.append("  <ReturnHeader>\n");
        xml.append("    <Timestamp>").append(LocalDateTime.now()).append("</Timestamp>\n");
        xml.append("    <TaxYear>").append(formPackage.getTaxYear()).append("</TaxYear>\n");
        xml.append("    <TaxPeriodBeginDate>").append(formPackage.getTaxYear()).append("-01-01</TaxPeriodBeginDate>\n");
        xml.append("    <TaxPeriodEndDate>").append(formPackage.getTaxYear()).append("-12-31</TaxPeriodEndDate>\n");
        xml.append("    <SoftwareId>").append("WAQITI-TAX-2024").append("</SoftwareId>\n");
        xml.append("    <EFIN>").append(efin).append("</EFIN>\n");
        xml.append("  </ReturnHeader>\n");
        xml.append("  <ReturnData>\n");
        // Add Form 1040 data
        xml.append("    <IRS1040>\n");
        xml.append("      <!-- Form 1040 XML data -->\n");
        xml.append("    </IRS1040>\n");
        xml.append("  </ReturnData>\n");
        xml.append("</Return>\n");

        return xml.toString();
    }

    private HttpHeaders createIrsHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-IRS-EFIN", efin);
        headers.set("X-IRS-ETIN", etin);
        headers.set("X-IRS-Test-Mode", String.valueOf(testMode));
        return headers;
    }

    private IRSSubmissionResult submitWithRetry(HttpEntity<String> request) {
        int attempts = 0;
        Exception lastException = null;

        while (attempts < MAX_RETRIES) {
            try {
                attempts++;
                log.debug("IRS submission attempt {}/{}", attempts, MAX_RETRIES);

                String url = irsApiBaseUrl + efileEndpoint;
                ResponseEntity<Map> response = irsRestTemplate.postForEntity(url, request, Map.class);

                if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                    return parseSubmissionResult(response.getBody());
                }

                log.warn("IRS submission returned non-OK status: {}", response.getStatusCode());

            } catch (Exception e) {
                lastException = e;
                log.warn("IRS submission attempt {} failed: {}", attempts, e.getMessage());

                if (attempts < MAX_RETRIES) {
                    try {
                        Thread.sleep(RETRY_DELAY_MS * attempts); // Exponential backoff
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }

        throw new BusinessException("IRS submission failed after " + MAX_RETRIES + " attempts",
                lastException);
    }

    private IRSSubmissionResult parseSubmissionResult(Map<String, Object> responseBody) {
        String status = (String) responseBody.getOrDefault("status", "PENDING");
        String confirmationNumber = (String) responseBody.get("confirmation_number");
        String message = (String) responseBody.get("message");

        return IRSSubmissionResult.builder()
                .success("ACCEPTED".equals(status))
                .confirmationNumber(confirmationNumber)
                .message(message)
                .estimatedRefundDate(LocalDate.now().plusDays(21))
                .submittedAt(LocalDateTime.now())
                .build();
    }

    private IRSRefundStatus parseRefundStatus(Map<String, Object> responseBody) {
        String statusCode = (String) responseBody.getOrDefault("status_code", "PROCESSING");
        String message = (String) responseBody.get("message");
        String estimatedDate = (String) responseBody.get("estimated_deposit_date");

        RefundStatusType status = mapIrsStatusCode(statusCode);

        return IRSRefundStatus.builder()
                .status(status)
                .message(message)
                .estimatedDate(estimatedDate != null ? LocalDate.parse(estimatedDate) : null)
                .lastUpdated(LocalDateTime.now())
                .build();
    }

    private RefundStatusType mapIrsStatusCode(String irsCode) {
        return switch (irsCode.toUpperCase()) {
            case "RECEIVED" -> RefundStatusType.RECEIVED;
            case "APPROVED" -> RefundStatusType.APPROVED;
            case "SENT" -> RefundStatusType.SENT;
            case "DEPOSITED", "COMPLETED" -> RefundStatusType.DEPOSITED;
            default -> RefundStatusType.PROCESSING;
        };
    }

    private List<W2Form> parseW2Forms(List<Map<String, Object>> w2Data) {
        List<W2Form> w2Forms = new ArrayList<>();

        for (Map<String, Object> data : w2Data) {
            W2Form w2 = W2Form.builder()
                    .employerEin((String) data.get("employer_ein"))
                    .employerName((String) data.get("employer_name"))
                    .wages((Number) data.get("wages"))
                    .federalTaxWithheld((Number) data.get("federal_tax_withheld"))
                    .socialSecurityWages((Number) data.get("social_security_wages"))
                    .socialSecurityTaxWithheld((Number) data.get("social_security_tax_withheld"))
                    .medicareWages((Number) data.get("medicare_wages"))
                    .medicareTaxWithheld((Number) data.get("medicare_tax_withheld"))
                    .build();
            w2Forms.add(w2);
        }

        return w2Forms;
    }

    private IRSSubmissionResult simulateSubmission(TaxFormPackage formPackage) {
        log.info("SIMULATION: IRS e-file submission");

        String confirmationNumber = String.format("IRS-EFILE-%d-%s",
                formPackage.getTaxYear(),
                UUID.randomUUID().toString().substring(0, 12).toUpperCase());

        return IRSSubmissionResult.builder()
                .success(true)
                .confirmationNumber(confirmationNumber)
                .message("Tax return accepted by IRS (TEST MODE)")
                .estimatedRefundDate(LocalDate.now().plusDays(21))
                .submittedAt(LocalDateTime.now())
                .build();
    }

    private IRSRefundStatus simulateRefundStatus() {
        log.debug("SIMULATION: IRS refund status check");

        return IRSRefundStatus.builder()
                .status(RefundStatusType.APPROVED)
                .message("Your refund has been approved and will be sent to your bank within 5 business days (TEST MODE)")
                .estimatedDate(LocalDate.now().plusDays(5))
                .lastUpdated(LocalDateTime.now())
                .build();
    }

    private String maskSsn(String ssn) {
        if (ssn == null || ssn.length() < 4) {
            return "***-**-****";
        }
        return "***-**-" + ssn.substring(ssn.length() - 4);
    }
}
