package com.waqiti.tax.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.tax.dto.TaxCalculationRequest;
import com.waqiti.tax.dto.TaxCalculationResponse;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Avalara AvaTax API Client for Real-Time Tax Calculation
 *
 * INTEGRATION DETAILS:
 * - API: Avalara AvaTax REST API v2
 * - Authentication: HTTP Basic Auth (AccountID + LicenseKey)
 * - Endpoint: https://rest.avalara.com/api/v2/transactions/create
 * - Documentation: https://developer.avalara.com/
 *
 * FEATURES:
 * - Real-time sales tax calculation for 19,000+ US jurisdictions
 * - Address validation and geocoding
 * - Product taxability determination
 * - Multi-jurisdiction tax calculation
 * - Nexus validation
 * - Tax exemption certificates management
 * - Multi-currency support
 *
 * COMPLIANCE:
 * - Certified for PCI DSS compliance
 * - SOC 1 Type II and SOC 2 Type II certified
 * - Compliant with Streamlined Sales Tax (SST)
 * - GDPR compliant
 *
 * PERFORMANCE:
 * - Circuit breaker for fault tolerance
 * - Automatic retry with exponential backoff
 * - 99.99% uptime SLA
 * - < 500ms average response time
 *
 * @author Waqiti Tax Team
 * @version 3.0.0
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AvalaraTaxClient {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${avalara.api.url:https://rest.avalara.com}")
    private String avalaraApiUrl;

    @Value("${avalara.account.id}")
    private String avalaraAccountId;

    @Value("${avalara.license.key}")
    private String avalaraLicenseKey;

    @Value("${avalara.company.code:DEFAULT}")
    private String companyCode;

    @Value("${avalara.api.timeout:10000}")
    private int timeout;

    @Value("${avalara.environment:sandbox}")
    private String environment;

    /**
     * Calculate tax using Avalara AvaTax API
     *
     * USAGE:
     * <pre>
     * TaxCalculationRequest request = TaxCalculationRequest.builder()
     *     .transactionId(UUID.randomUUID())
     *     .amount(new BigDecimal("100.00"))
     *     .currency("USD")
     *     .transactionType("SALE")
     *     .sourceAddress(sourceAddress)
     *     .destinationAddress(destinationAddress)
     *     .build();
     *
     * TaxCalculationResponse response = avalaraTaxClient.calculateTax(request);
     * </pre>
     */
    @CircuitBreaker(name = "avalara", fallbackMethod = "calculateTaxFallback")
    @Retry(name = "avalara")
    public TaxCalculationResponse calculateTax(TaxCalculationRequest request) {
        log.info("Calculating tax via Avalara for transaction: {}, amount: ${}",
            request.getTransactionId(), request.getAmount());

        try {
            // Build Avalara transaction request
            Map<String, Object> avalaraRequest = buildAvalaraTransactionRequest(request);

            // Call Avalara API
            String apiUrl = String.format("%s/api/v2/transactions/create", avalaraApiUrl);
            HttpHeaders headers = createHeaders();
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(avalaraRequest, headers);

            log.debug("Calling Avalara API: {}", apiUrl);
            ResponseEntity<String> response = restTemplate.exchange(
                apiUrl, HttpMethod.POST, entity, String.class);

            if (response.getStatusCode() == HttpStatus.CREATED ||
                response.getStatusCode() == HttpStatus.OK) {

                TaxCalculationResponse taxResponse = parseAvalaraResponse(
                    response.getBody(), request);

                log.info("Avalara tax calculation successful - Total tax: ${}",
                    taxResponse.getTotalTaxAmount());

                return taxResponse;
            } else {
                log.error("Avalara API returned unexpected status: {}", response.getStatusCode());
                throw new AvalaraTaxException("Unexpected response from Avalara API");
            }

        } catch (HttpClientErrorException e) {
            log.error("Avalara API client error: {} - {}",
                e.getStatusCode(), e.getResponseBodyAsString());
            throw new AvalaraTaxException("Avalara API client error: " + e.getMessage(), e);

        } catch (HttpServerErrorException e) {
            log.error("Avalara API server error: {} - {}",
                e.getStatusCode(), e.getResponseBodyAsString());
            throw new AvalaraTaxException("Avalara API server error: " + e.getMessage(), e);

        } catch (Exception e) {
            log.error("Failed to calculate tax via Avalara", e);
            throw new AvalaraTaxException("Tax calculation failed: " + e.getMessage(), e);
        }
    }

    /**
     * Validate address using Avalara address validation API
     *
     * CRITICAL: Address validation improves tax calculation accuracy
     * - Corrects address formatting
     * - Validates ZIP+4 codes
     * - Geocodes to precise tax jurisdiction
     */
    @CircuitBreaker(name = "avalara", fallbackMethod = "validateAddressFallback")
    @Retry(name = "avalara")
    public Map<String, Object> validateAddress(Map<String, String> address) {
        log.debug("Validating address via Avalara: {}", address);

        try {
            String apiUrl = String.format("%s/api/v2/addresses/resolve", avalaraApiUrl);
            HttpHeaders headers = createHeaders();
            HttpEntity<Map<String, String>> entity = new HttpEntity<>(address, headers);

            ResponseEntity<String> response = restTemplate.exchange(
                apiUrl, HttpMethod.POST, entity, String.class);

            if (response.getStatusCode() == HttpStatus.OK) {
                JsonNode rootNode = objectMapper.readTree(response.getBody());

                Map<String, Object> validatedAddress = new HashMap<>();
                JsonNode addressNode = rootNode.path("validatedAddresses").get(0);

                validatedAddress.put("line1", addressNode.path("line1").asText());
                validatedAddress.put("line2", addressNode.path("line2").asText());
                validatedAddress.put("city", addressNode.path("city").asText());
                validatedAddress.put("region", addressNode.path("region").asText());
                validatedAddress.put("postalCode", addressNode.path("postalCode").asText());
                validatedAddress.put("country", addressNode.path("country").asText());
                validatedAddress.put("latitude", addressNode.path("latitude").asDouble());
                validatedAddress.put("longitude", addressNode.path("longitude").asDouble());

                log.debug("Address validated successfully");
                return validatedAddress;
            }

        } catch (Exception e) {
            log.error("Address validation failed", e);
        }

        // Fallback: return original address
        return new HashMap<>(address);
    }

    /**
     * Get tax rates for specific address
     *
     * USAGE: Quick tax rate lookup without full transaction
     */
    @CircuitBreaker(name = "avalara", fallbackMethod = "getTaxRatesFallback")
    @Retry(name = "avalara")
    public Map<String, BigDecimal> getTaxRates(String address, String postalCode,
                                                String city, String region, String country) {

        log.debug("Getting tax rates for: {}, {}, {}", city, region, postalCode);

        try {
            String apiUrl = String.format("%s/api/v2/taxrates/byaddress", avalaraApiUrl);

            String queryParams = String.format("?line1=%s&city=%s&region=%s&postalCode=%s&country=%s",
                encodeParam(address), encodeParam(city), encodeParam(region),
                encodeParam(postalCode), encodeParam(country));

            HttpHeaders headers = createHeaders();
            HttpEntity<Void> entity = new HttpEntity<>(headers);

            ResponseEntity<String> response = restTemplate.exchange(
                apiUrl + queryParams, HttpMethod.GET, entity, String.class);

            if (response.getStatusCode() == HttpStatus.OK) {
                return parseTaxRatesResponse(response.getBody());
            }

        } catch (Exception e) {
            log.error("Failed to get tax rates", e);
        }

        // Fallback: return empty rates
        return new HashMap<>();
    }

    /**
     * Commit transaction to Avalara (finalize tax calculation)
     *
     * CRITICAL: Must be called after payment confirmation
     * - Records transaction in Avalara for reporting
     * - Cannot be undone (only voided)
     */
    @CircuitBreaker(name = "avalara", fallbackMethod = "commitTransactionFallback")
    @Retry(name = "avalara")
    public void commitTransaction(String transactionCode) {
        log.info("Committing transaction to Avalara: {}", transactionCode);

        try {
            String apiUrl = String.format("%s/api/v2/companies/%s/transactions/%s/commit",
                avalaraApiUrl, companyCode, transactionCode);

            HttpHeaders headers = createHeaders();

            Map<String, Object> commitRequest = new HashMap<>();
            commitRequest.put("commit", true);

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(commitRequest, headers);

            ResponseEntity<String> response = restTemplate.exchange(
                apiUrl, HttpMethod.POST, entity, String.class);

            if (response.getStatusCode() == HttpStatus.OK) {
                log.info("Transaction committed successfully: {}", transactionCode);
            } else {
                log.error("Failed to commit transaction: {}", response.getStatusCode());
            }

        } catch (Exception e) {
            log.error("Error committing transaction: {}", transactionCode, e);
            throw new AvalaraTaxException("Failed to commit transaction", e);
        }
    }

    /**
     * Void transaction in Avalara (cancel/refund)
     *
     * USAGE: Call when order is cancelled or refunded
     */
    @CircuitBreaker(name = "avalara", fallbackMethod = "voidTransactionFallback")
    @Retry(name = "avalara")
    public void voidTransaction(String transactionCode, String reason) {
        log.info("Voiding transaction in Avalara: {}, reason: {}", transactionCode, reason);

        try {
            String apiUrl = String.format("%s/api/v2/companies/%s/transactions/%s/void",
                avalaraApiUrl, companyCode, transactionCode);

            HttpHeaders headers = createHeaders();

            Map<String, Object> voidRequest = new HashMap<>();
            voidRequest.put("code", "DocVoided");
            voidRequest.put("reason", reason);

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(voidRequest, headers);

            ResponseEntity<String> response = restTemplate.exchange(
                apiUrl, HttpMethod.POST, entity, String.class);

            if (response.getStatusCode() == HttpStatus.OK) {
                log.info("Transaction voided successfully: {}", transactionCode);
            }

        } catch (Exception e) {
            log.error("Error voiding transaction: {}", transactionCode, e);
            throw new AvalaraTaxException("Failed to void transaction", e);
        }
    }

    /**
     * Build Avalara transaction request payload
     *
     * AVALARA TRANSACTION STRUCTURE:
     * - companyCode: Your Avalara company identifier
     * - type: SalesInvoice, SalesOrder, ReturnInvoice, etc.
     * - customerCode: Unique customer identifier
     * - date: Transaction date (YYYY-MM-DD)
     * - lines: Array of transaction line items
     * - addresses: Ship-from and ship-to addresses
     */
    private Map<String, Object> buildAvalaraTransactionRequest(TaxCalculationRequest request) {
        Map<String, Object> transaction = new HashMap<>();

        // Company and transaction metadata
        transaction.put("companyCode", companyCode);
        transaction.put("type", mapTransactionType(request.getTransactionType()));
        transaction.put("customerCode", request.getUserId() != null ?
            request.getUserId().toString() : "GUEST");
        transaction.put("date", request.getTransactionDate().format(
            DateTimeFormatter.ISO_LOCAL_DATE));
        transaction.put("code", request.getTransactionId().toString());
        transaction.put("currencyCode", request.getCurrency() != null ?
            request.getCurrency() : "USD");

        // Addresses
        Map<String, Object> addresses = new HashMap<>();

        if (request.getSourceAddress() != null) {
            addresses.put("shipFrom", buildAddressObject(request.getSourceAddress()));
        }

        if (request.getDestinationAddress() != null) {
            addresses.put("shipTo", buildAddressObject(request.getDestinationAddress()));
        }

        transaction.put("addresses", addresses);

        // Transaction lines
        List<Map<String, Object>> lines = new ArrayList<>();

        Map<String, Object> line = new HashMap<>();
        line.put("number", "1");
        line.put("quantity", 1);
        line.put("amount", request.getAmount());
        line.put("taxCode", request.getTaxCode() != null ? request.getTaxCode() : "P0000000");
        line.put("itemCode", request.getProductCode() != null ?
            request.getProductCode() : "GENERAL");
        line.put("description", request.getDescription() != null ?
            request.getDescription() : "Transaction");

        // Addresses for this line
        line.put("addresses", addresses);

        lines.add(line);
        transaction.put("lines", lines);

        // Optional: Tax exemption certificate
        if (request.getTaxExemptionCertificateId() != null) {
            transaction.put("exemptionNo", request.getTaxExemptionCertificateId());
        }

        // Commit behavior (false = estimate only, true = record transaction)
        transaction.put("commit", request.isCommit() != null ? request.isCommit() : false);

        log.debug("Built Avalara transaction request: {}", transaction);
        return transaction;
    }

    /**
     * Build address object for Avalara API
     */
    private Map<String, Object> buildAddressObject(Map<String, String> address) {
        Map<String, Object> addressObj = new HashMap<>();

        addressObj.put("line1", address.getOrDefault("line1", ""));
        addressObj.put("line2", address.getOrDefault("line2", ""));
        addressObj.put("line3", address.getOrDefault("line3", ""));
        addressObj.put("city", address.getOrDefault("city", ""));
        addressObj.put("region", address.getOrDefault("region", ""));
        addressObj.put("postalCode", address.getOrDefault("postalCode", ""));
        addressObj.put("country", address.getOrDefault("country", "US"));

        return addressObj;
    }

    /**
     * Map internal transaction types to Avalara types
     */
    private String mapTransactionType(String transactionType) {
        if (transactionType == null) {
            return "SalesOrder";
        }

        switch (transactionType.toUpperCase()) {
            case "SALE":
            case "SALES_ORDER":
                return "SalesOrder";
            case "INVOICE":
            case "SALES_INVOICE":
                return "SalesInvoice";
            case "RETURN":
            case "REFUND":
                return "ReturnInvoice";
            case "PURCHASE":
            case "PURCHASE_ORDER":
                return "PurchaseOrder";
            case "PURCHASE_INVOICE":
                return "PurchaseInvoice";
            case "INVENTORY_TRANSFER":
                return "InventoryTransferOrder";
            default:
                return "SalesOrder";
        }
    }

    /**
     * Parse Avalara API response to TaxCalculationResponse
     */
    private TaxCalculationResponse parseAvalaraResponse(String responseBody,
                                                         TaxCalculationRequest request) throws Exception {

        JsonNode rootNode = objectMapper.readTree(responseBody);

        BigDecimal totalTax = new BigDecimal(rootNode.path("totalTax").asText("0"));
        BigDecimal taxableAmount = new BigDecimal(rootNode.path("totalTaxable").asText("0"));
        BigDecimal totalAmount = new BigDecimal(rootNode.path("totalAmount").asText("0"));

        // Parse tax breakdown by jurisdiction
        Map<String, BigDecimal> taxBreakdown = new HashMap<>();
        JsonNode linesNode = rootNode.path("lines");

        for (JsonNode lineNode : linesNode) {
            JsonNode detailsNode = lineNode.path("details");

            for (JsonNode detailNode : detailsNode) {
                String jurisdictionName = detailNode.path("jurisName").asText();
                BigDecimal jurisdictionTax = new BigDecimal(detailNode.path("tax").asText("0"));

                taxBreakdown.merge(jurisdictionName, jurisdictionTax, BigDecimal::add);
            }
        }

        // Calculate effective tax rate
        BigDecimal effectiveRate = totalAmount.compareTo(BigDecimal.ZERO) > 0 ?
            totalTax.divide(totalAmount, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100)) :
            BigDecimal.ZERO;

        // Build response
        TaxCalculationResponse response = TaxCalculationResponse.builder()
            .transactionId(request.getTransactionId())
            .totalTaxAmount(totalTax)
            .taxableAmount(taxableAmount)
            .totalAmount(totalAmount)
            .effectiveTaxRate(effectiveRate)
            .taxBreakdown(taxBreakdown)
            .calculationDate(LocalDateTime.now())
            .jurisdiction(extractJurisdiction(rootNode))
            .currency(request.getCurrency() != null ? request.getCurrency() : "USD")
            .status("CALCULATED")
            .source("AVALARA")
            .metadata(extractMetadata(rootNode))
            .build();

        log.debug("Parsed Avalara response: Total tax={}, Effective rate={}%",
            totalTax, effectiveRate);

        return response;
    }

    /**
     * Extract jurisdiction information from Avalara response
     */
    private String extractJurisdiction(JsonNode rootNode) {
        JsonNode linesNode = rootNode.path("lines");

        if (linesNode.size() > 0) {
            JsonNode firstLine = linesNode.get(0);
            JsonNode detailsNode = firstLine.path("details");

            if (detailsNode.size() > 0) {
                JsonNode firstDetail = detailsNode.get(0);
                String state = firstDetail.path("stateFIPS").asText();
                String county = firstDetail.path("countyFIPS").asText();
                String city = firstDetail.path("cityFIPS").asText();

                return String.format("US-%s-%s-%s", state, county, city);
            }
        }

        return "US-UNKNOWN";
    }

    /**
     * Extract metadata from Avalara response
     */
    private Map<String, String> extractMetadata(JsonNode rootNode) {
        Map<String, String> metadata = new HashMap<>();

        metadata.put("avalaraTransactionId", rootNode.path("id").asText());
        metadata.put("avalaraCode", rootNode.path("code").asText());
        metadata.put("avalaraStatus", rootNode.path("status").asText());
        metadata.put("avalaraType", rootNode.path("type").asText());
        metadata.put("avalaraDate", rootNode.path("date").asText());

        return metadata;
    }

    /**
     * Parse tax rates API response
     */
    private Map<String, BigDecimal> parseTaxRatesResponse(String responseBody) throws Exception {
        JsonNode rootNode = objectMapper.readTree(responseBody);
        Map<String, BigDecimal> rates = new HashMap<>();

        rates.put("totalRate", new BigDecimal(rootNode.path("totalRate").asText("0")));

        JsonNode ratesNode = rootNode.path("rates");
        for (JsonNode rateNode : ratesNode) {
            String type = rateNode.path("type").asText();
            BigDecimal rate = new BigDecimal(rateNode.path("rate").asText("0"));
            rates.put(type, rate);
        }

        return rates;
    }

    /**
     * Create HTTP headers for Avalara API
     *
     * AUTHENTICATION: HTTP Basic Auth
     * - Username: AccountID
     * - Password: LicenseKey
     */
    private HttpHeaders createHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Accept", "application/json");

        // HTTP Basic Auth
        String auth = avalaraAccountId + ":" + avalaraLicenseKey;
        byte[] encodedAuth = Base64.getEncoder().encode(
            auth.getBytes(StandardCharsets.UTF_8));
        String authHeader = "Basic " + new String(encodedAuth);

        headers.set("Authorization", authHeader);

        // Client identification
        headers.set("X-Avalara-Client", "Waqiti;3.0;Java;JDK17");

        return headers;
    }

    /**
     * URL-encode parameter
     */
    private String encodeParam(String param) {
        return java.net.URLEncoder.encode(param != null ? param : "",
            StandardCharsets.UTF_8);
    }

    /**
     * Fallback method for calculateTax when Avalara is unavailable
     */
    private TaxCalculationResponse calculateTaxFallback(TaxCalculationRequest request, Exception e) {
        log.warn("Avalara API unavailable, using fallback calculation for transaction: {}",
            request.getTransactionId());

        // Simple fallback: use default tax rate
        BigDecimal defaultTaxRate = new BigDecimal("0.08"); // 8% default
        BigDecimal taxAmount = request.getAmount().multiply(defaultTaxRate)
            .setScale(2, RoundingMode.HALF_UP);

        Map<String, BigDecimal> taxBreakdown = new HashMap<>();
        taxBreakdown.put("FALLBACK", taxAmount);

        return TaxCalculationResponse.builder()
            .transactionId(request.getTransactionId())
            .totalTaxAmount(taxAmount)
            .taxableAmount(request.getAmount())
            .totalAmount(request.getAmount().add(taxAmount))
            .effectiveTaxRate(new BigDecimal("8.00"))
            .taxBreakdown(taxBreakdown)
            .calculationDate(LocalDateTime.now())
            .currency(request.getCurrency())
            .status("FALLBACK")
            .source("INTERNAL")
            .build();
    }

    /**
     * Fallback methods for other operations
     */
    private Map<String, Object> validateAddressFallback(Map<String, String> address, Exception e) {
        log.warn("Avalara address validation unavailable, returning original address");
        return new HashMap<>(address);
    }

    private Map<String, BigDecimal> getTaxRatesFallback(String address, String postalCode,
                                                         String city, String region,
                                                         String country, Exception e) {
        log.warn("Avalara tax rates API unavailable, returning default rates");
        Map<String, BigDecimal> fallbackRates = new HashMap<>();
        fallbackRates.put("totalRate", new BigDecimal("0.08"));
        return fallbackRates;
    }

    private void commitTransactionFallback(String transactionCode, Exception e) {
        log.error("Failed to commit transaction to Avalara: {}", transactionCode, e);
        // Store for retry later
    }

    private void voidTransactionFallback(String transactionCode, String reason, Exception e) {
        log.error("Failed to void transaction in Avalara: {}", transactionCode, e);
        // Store for retry later
    }

    /**
     * Custom exception for Avalara operations
     */
    public static class AvalaraTaxException extends RuntimeException {
        public AvalaraTaxException(String message) {
            super(message);
        }

        public AvalaraTaxException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
