package com.waqiti.tax.service;

import com.waqiti.common.audit.AuditService;
import com.waqiti.common.exception.IRSFilingException;
import com.waqiti.tax.dto.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * IRS INTEGRATION SERVICE
 *
 * Interfaces with IRS FIRE (Filing Information Returns Electronically) system
 * for electronic filing of Form 1099 tax documents.
 *
 * REGULATORY REQUIREMENT: IRS requires electronic filing for payers with
 * 250 or more information returns in a calendar year.
 *
 * IRS FIRE SYSTEM:
 * - Production: https://fire.irs.gov/
 * - Test: https://testfire.irs.gov/
 *
 * FILING REQUIREMENTS:
 * - TLS 1.2 or higher
 * - XML format per IRS Publication 1220
 * - Transmitter Control Code (TCC) required
 * - Filing deadline: January 31st
 *
 * PENALTIES:
 * - Late filing: $50-$280 per form
 * - Intentional disregard: $570 per form (no maximum)
 *
 * IRS FIRE Documentation:
 * https://www.irs.gov/e-file-providers/filing-information-returns-electronically-fire
 *
 * @author Waqiti Tax Team
 * @version 2.0.0
 * @since 2025-01-01
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IRSIntegrationService {

    private final WebClient.Builder webClientBuilder;
    private final IRSXMLGeneratorService xmlGenerator;
    private final AuditService auditService;

    @Value("${irs.fire.enabled:false}")
    private boolean irsFireEnabled;

    @Value("${irs.fire.base-url:https://fire.irs.gov/fire}")
    private String irsFireUrl;

    @Value("${irs.fire.test-url:https://testfire.irs.gov/fire}")
    private String irsTestUrl;

    @Value("${irs.fire.environment:test}")
    private String environment; // "test" or "production"

    @Value("${irs.fire.tcc:#{null}}")
    private String transmitterControlCode; // Transmitter Control Code

    @Value("${irs.fire.tin:#{null}}")
    private String payerTin; // Waqiti's Tax Identification Number

    @Value("${irs.fire.timeout-seconds:60}")
    private int timeoutSeconds;

    @Value("${irs.fire.retry-attempts:3}")
    private int retryAttempts;

    /**
     * File 1099 forms with IRS FIRE system
     *
     * @param taxYear Tax year for filing
     * @param forms Collection of 1099 forms to file
     * @return Filing response with acknowledgment
     */
    public IRSFilingResponse file1099Forms(int taxYear, Form1099Collection forms) {
        log.info("IRS: Filing 1099 forms with IRS FIRE - TaxYear: {}, TotalForms: {}",
            taxYear, forms.getTotalCount());

        if (!irsFireEnabled) {
            String error = "CRITICAL: IRS FIRE integration is DISABLED. Enable irs.fire.enabled=true for production.";
            log.error("IRS: {}", error);
            throw new IllegalStateException(error);
        }

        validateConfiguration();

        long startTime = System.currentTimeMillis();
        String transmissionId = generateTransmissionId(taxYear);

        try {
            // Generate IRS-compliant XML
            String irsXml = xmlGenerator.generate1099XML(
                taxYear,
                transmitterControlCode,
                payerTin,
                forms
            );

            log.debug("IRS: Generated XML - Size: {} bytes, TransmissionId: {}",
                irsXml.length(), transmissionId);

            // Determine URL based on environment
            String filingUrl = "production".equalsIgnoreCase(environment) ? irsFireUrl : irsTestUrl;

            // Submit to IRS FIRE
            WebClient webClient = webClientBuilder
                .baseUrl(filingUrl)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, "application/xml")
                .defaultHeader(HttpHeaders.ACCEPT, "application/xml")
                .build();

            IRSFilingResponse response = webClient
                .post()
                .uri("/submit")
                .headers(headers -> {
                    headers.set("X-Transmission-ID", transmissionId);
                    headers.set("X-TCC", transmitterControlCode);
                    headers.set("X-Tax-Year", String.valueOf(taxYear));
                    headers.set("X-Environment", environment);
                })
                .bodyValue(irsXml)
                .retrieve()
                .bodyToMono(IRSFilingResponse.class)
                .timeout(Duration.ofSeconds(timeoutSeconds))
                .retryWhen(Retry.backoff(retryAttempts, Duration.ofSeconds(3))
                    .filter(throwable -> !(throwable instanceof WebClientResponseException.BadRequest)))
                .doOnSuccess(resp -> {
                    log.info("IRS: Filing submitted successfully - TransmissionId: {}, ReceiptId: {}",
                        transmissionId, resp.getReceiptId());
                })
                .doOnError(error -> {
                    log.error("IRS: Filing submission FAILED - TransmissionId: {}",
                        transmissionId, error);
                })
                .block();

            long responseTime = System.currentTimeMillis() - startTime;

            if (response != null && response.isAccepted()) {
                log.info("IRS: Filing ACCEPTED by IRS - ReceiptId: {}, Status: {}",
                    response.getReceiptId(), response.getStatus());

                // AUDIT: Log successful filing
                auditService.logTaxEvent(
                    "IRS_FILING_SUCCESS",
                    null,
                    Map.of(
                        "taxYear", String.valueOf(taxYear),
                        "transmissionId", transmissionId,
                        "receiptId", response.getReceiptId(),
                        "formsCount", String.valueOf(forms.getTotalCount()),
                        "responseTime", String.valueOf(responseTime)
                    )
                );

                return response;

            } else {
                log.error("IRS: Filing REJECTED by IRS - TransmissionId: {}, Errors: {}",
                    transmissionId, response != null ? response.getErrors() : "null response");

                // AUDIT: Log filing rejection
                auditService.logCriticalError(
                    "IRS_FILING_REJECTED",
                    "IRS_REJECTION",
                    "IRS rejected 1099 filing: " +
                        (response != null ? response.getMessage() : "No response"),
                    Map.of(
                        "taxYear", String.valueOf(taxYear),
                        "transmissionId", transmissionId
                    )
                );

                return response != null ? response :
                    createErrorResponse(transmissionId, "No response from IRS");
            }

        } catch (WebClientResponseException e) {
            log.error("IRS: HTTP error filing with IRS - TransmissionId: {}, Status: {}, Body: {}",
                transmissionId, e.getStatusCode(), e.getResponseBodyAsString(), e);

            // AUDIT: Log HTTP error
            auditService.logCriticalError(
                "IRS_FILING_HTTP_ERROR",
                "HTTP_" + e.getStatusCode(),
                "HTTP error filing with IRS: " + e.getMessage(),
                Map.of(
                    "transmissionId", transmissionId,
                    "statusCode", String.valueOf(e.getStatusCode().value())
                )
            );

            return createErrorResponse(transmissionId,
                "HTTP " + e.getStatusCode() + ": " + e.getMessage());

        } catch (Exception e) {
            log.error("IRS: Unexpected error filing with IRS - TransmissionId: {}",
                transmissionId, e);

            // AUDIT: Log unexpected error
            auditService.logCriticalError(
                "IRS_FILING_EXCEPTION",
                "EXCEPTION",
                "Unexpected error filing with IRS: " + e.getMessage(),
                Map.of("transmissionId", transmissionId)
            );

            return createErrorResponse(transmissionId,
                "Unexpected error: " + e.getMessage());
        }
    }

    /**
     * Check filing status with IRS
     *
     * @param receiptId IRS receipt ID from initial filing
     * @return Current filing status
     */
    public IRSFilingStatusResponse checkFilingStatus(String receiptId) {
        log.debug("IRS: Checking filing status - ReceiptId: {}", receiptId);

        if (!irsFireEnabled) {
            throw new IllegalStateException("IRS FIRE integration is disabled");
        }

        validateConfiguration();

        try {
            String filingUrl = "production".equalsIgnoreCase(environment) ? irsFireUrl : irsTestUrl;

            WebClient webClient = webClientBuilder
                .baseUrl(filingUrl)
                .build();

            IRSFilingStatusResponse response = webClient
                .get()
                .uri("/status/{receiptId}", receiptId)
                .headers(headers -> {
                    headers.set("X-TCC", transmitterControlCode);
                })
                .retrieve()
                .bodyToMono(IRSFilingStatusResponse.class)
                .timeout(Duration.ofSeconds(timeoutSeconds))
                .block();

            if (response != null) {
                log.info("IRS: Filing status retrieved - ReceiptId: {}, Status: {}",
                    receiptId, response.getStatus());

                // AUDIT: Log status check
                auditService.logTaxEvent(
                    "IRS_STATUS_CHECK",
                    null,
                    Map.of(
                        "receiptId", receiptId,
                        "status", response.getStatus()
                    )
                );
            }

            return response;

        } catch (Exception e) {
            log.error("IRS: Error checking filing status - ReceiptId: {}", receiptId, e);

            // AUDIT: Log status check failure
            auditService.logCriticalError(
                "IRS_STATUS_CHECK_FAILED",
                "STATUS_CHECK_ERROR",
                "Failed to check IRS filing status: " + e.getMessage(),
                Map.of("receiptId", receiptId)
            );

            return null;
        }
    }

    /**
     * Retrieve acknowledgment file from IRS
     *
     * @param receiptId IRS receipt ID
     * @return Acknowledgment details
     */
    public IRSAcknowledgmentResponse getAcknowledgment(String receiptId) {
        log.info("IRS: Retrieving acknowledgment - ReceiptId: {}", receiptId);

        if (!irsFireEnabled) {
            throw new IllegalStateException("IRS FIRE integration is disabled");
        }

        try {
            String filingUrl = "production".equalsIgnoreCase(environment) ? irsFireUrl : irsTestUrl;

            WebClient webClient = webClientBuilder
                .baseUrl(filingUrl)
                .build();

            IRSAcknowledgmentResponse response = webClient
                .get()
                .uri("/acknowledgment/{receiptId}", receiptId)
                .headers(headers -> {
                    headers.set("X-TCC", transmitterControlCode);
                })
                .retrieve()
                .bodyToMono(IRSAcknowledgmentResponse.class)
                .timeout(Duration.ofSeconds(timeoutSeconds))
                .block();

            if (response != null) {
                log.info("IRS: Acknowledgment retrieved - ReceiptId: {}, AcceptedCount: {}, RejectedCount: {}",
                    receiptId, response.getAcceptedCount(), response.getRejectedCount());

                // AUDIT: Log acknowledgment retrieval
                auditService.logTaxEvent(
                    "IRS_ACKNOWLEDGMENT_RECEIVED",
                    null,
                    Map.of(
                        "receiptId", receiptId,
                        "acceptedCount", String.valueOf(response.getAcceptedCount()),
                        "rejectedCount", String.valueOf(response.getRejectedCount())
                    )
                );
            }

            return response;

        } catch (Exception e) {
            log.error("IRS: Error retrieving acknowledgment - ReceiptId: {}", receiptId, e);

            // AUDIT: Log retrieval failure
            auditService.logCriticalError(
                "IRS_ACKNOWLEDGMENT_FAILED",
                "ACKNOWLEDGMENT_ERROR",
                "Failed to retrieve IRS acknowledgment: " + e.getMessage(),
                Map.of("receiptId", receiptId)
            );

            return null;
        }
    }

    /**
     * Correct and resubmit rejected forms
     *
     * @param originalReceiptId Original filing receipt ID
     * @param correctedForms Corrected forms
     * @return New filing response
     */
    public IRSFilingResponse correctAndResubmit(
        String originalReceiptId,
        int taxYear,
        Form1099Collection correctedForms) {

        log.info("IRS: Correcting and resubmitting forms - OriginalReceiptId: {}, FormsCount: {}",
            originalReceiptId, correctedForms.getTotalCount());

        // File corrected forms (similar to initial filing)
        IRSFilingResponse response = file1099Forms(taxYear, correctedForms);

        if (response.isAccepted()) {
            // AUDIT: Log successful correction
            auditService.logTaxEvent(
                "IRS_CORRECTION_SUCCESS",
                null,
                Map.of(
                    "originalReceiptId", originalReceiptId,
                    "newReceiptId", response.getReceiptId(),
                    "formsCount", String.valueOf(correctedForms.getTotalCount())
                )
            );
        }

        return response;
    }

    /**
     * Validate IRS FIRE configuration
     */
    private void validateConfiguration() {
        if (transmitterControlCode == null || transmitterControlCode.isEmpty()) {
            throw new IllegalStateException("IRS Transmitter Control Code (TCC) not configured");
        }
        if (payerTin == null || payerTin.isEmpty()) {
            throw new IllegalStateException("Payer TIN not configured");
        }
    }

    /**
     * Generate unique transmission ID
     */
    private String generateTransmissionId(int taxYear) {
        return String.format("WAQITI-%d-%s-%d",
            taxYear,
            LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMddHHmmss")),
            System.currentTimeMillis() % 10000
        );
    }

    /**
     * Create error response
     */
    private IRSFilingResponse createErrorResponse(String transmissionId, String errorMessage) {
        IRSFilingResponse response = new IRSFilingResponse();
        response.setAccepted(false);
        response.setStatus("ERROR");
        response.setMessage(errorMessage);
        response.setTransmissionId(transmissionId);
        response.setTimestamp(LocalDateTime.now());
        return response;
    }
}
