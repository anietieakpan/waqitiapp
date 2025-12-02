package com.waqiti.saga.builder;

import com.waqiti.saga.dto.InternationalTransferRequest;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Test data builder for InternationalTransferRequest
 *
 * Provides fluent API for creating test data with sensible defaults.
 * Includes preset scenarios for compliance testing.
 *
 * Usage:
 * <pre>
 * InternationalTransferRequest request = InternationalTransferRequestBuilder
 *     .anInternationalTransferRequest()
 *     .withSanctionsHit()  // Preset scenario
 *     .build();
 * </pre>
 */
public class InternationalTransferRequestBuilder {

    private String transferId;
    private String senderId;
    private String recipientId;
    private String senderName;
    private String recipientName;
    private BigDecimal sourceAmount;
    private String sourceCurrency;
    private BigDecimal destinationAmount;
    private String destinationCurrency;
    private String sourceCountry;
    private String destinationCountry;
    private String purpose;
    private String swiftCode;
    private String iban;
    private String idempotencyKey;

    private InternationalTransferRequestBuilder() {
        // Set sensible defaults (US to UK transfer)
        this.transferId = UUID.randomUUID().toString();
        this.senderId = "user-" + UUID.randomUUID().toString().substring(0, 8);
        this.recipientId = "user-" + UUID.randomUUID().toString().substring(0, 8);
        this.senderName = "John Smith";
        this.recipientName = "Jane Doe";
        this.sourceAmount = new BigDecimal("1000.00");
        this.sourceCurrency = "USD";
        this.destinationAmount = new BigDecimal("785.00"); // ~0.785 exchange rate
        this.destinationCurrency = "GBP";
        this.sourceCountry = "US";
        this.destinationCountry = "GB";
        this.purpose = "Personal transfer";
        this.swiftCode = "BARCGB22";
        this.iban = "GB82WEST12345698765432";
        this.idempotencyKey = UUID.randomUUID().toString();
    }

    public static InternationalTransferRequestBuilder anInternationalTransferRequest() {
        return new InternationalTransferRequestBuilder();
    }

    public InternationalTransferRequestBuilder withTransferId(String transferId) {
        this.transferId = transferId;
        return this;
    }

    public InternationalTransferRequestBuilder withSenderId(String senderId) {
        this.senderId = senderId;
        return this;
    }

    public InternationalTransferRequestBuilder withRecipientId(String recipientId) {
        this.recipientId = recipientId;
        return this;
    }

    public InternationalTransferRequestBuilder withSenderName(String senderName) {
        this.senderName = senderName;
        return this;
    }

    public InternationalTransferRequestBuilder withRecipientName(String recipientName) {
        this.recipientName = recipientName;
        return this;
    }

    public InternationalTransferRequestBuilder withSourceAmount(BigDecimal sourceAmount) {
        this.sourceAmount = sourceAmount;
        return this;
    }

    public InternationalTransferRequestBuilder withSourceCurrency(String sourceCurrency) {
        this.sourceCurrency = sourceCurrency;
        return this;
    }

    public InternationalTransferRequestBuilder withDestinationAmount(BigDecimal destinationAmount) {
        this.destinationAmount = destinationAmount;
        return this;
    }

    public InternationalTransferRequestBuilder withDestinationCurrency(String destinationCurrency) {
        this.destinationCurrency = destinationCurrency;
        return this;
    }

    public InternationalTransferRequestBuilder withSourceCountry(String sourceCountry) {
        this.sourceCountry = sourceCountry;
        return this;
    }

    public InternationalTransferRequestBuilder withDestinationCountry(String destinationCountry) {
        this.destinationCountry = destinationCountry;
        return this;
    }

    public InternationalTransferRequestBuilder withPurpose(String purpose) {
        this.purpose = purpose;
        return this;
    }

    public InternationalTransferRequestBuilder withSwiftCode(String swiftCode) {
        this.swiftCode = swiftCode;
        return this;
    }

    public InternationalTransferRequestBuilder withIban(String iban) {
        this.iban = iban;
        return this;
    }

    public InternationalTransferRequestBuilder withIdempotencyKey(String idempotencyKey) {
        this.idempotencyKey = idempotencyKey;
        return this;
    }

    // ========== PRESET COMPLIANCE SCENARIOS ==========

    /**
     * Create transfer to sanctioned country (OFAC)
     */
    public InternationalTransferRequestBuilder withSanctionsHit() {
        this.destinationCountry = "IR"; // Iran (sanctioned)
        this.recipientName = "Tehran Bank";
        this.purpose = "Business payment";
        return this;
    }

    /**
     * Create transfer requiring CTR filing (>= $10,000)
     */
    public InternationalTransferRequestBuilder withCTRRequired() {
        this.sourceAmount = new BigDecimal("12000.00");
        this.destinationAmount = new BigDecimal("9420.00"); // ~0.785 exchange rate
        this.purpose = "Business payment - large amount";
        return this;
    }

    /**
     * Create suspicious transfer requiring SAR filing
     */
    public InternationalTransferRequestBuilder withSuspiciousActivity() {
        this.sourceAmount = new BigDecimal("9999.00"); // Just below CTR (structuring)
        this.destinationAmount = new BigDecimal("7849.00");
        this.purpose = "Cash payment";
        this.destinationCountry = "RU"; // Russia (high-risk country)
        return this;
    }

    /**
     * Create transfer to high-risk country
     */
    public InternationalTransferRequestBuilder withHighRiskCountry() {
        this.destinationCountry = "AF"; // Afghanistan
        this.purpose = "Humanitarian aid";
        return this;
    }

    /**
     * Create Euro to USD transfer
     */
    public InternationalTransferRequestBuilder withEuroToUSD() {
        this.sourceCurrency = "EUR";
        this.sourceCountry = "DE";
        this.sourceAmount = new BigDecimal("1000.00");
        this.destinationCurrency = "USD";
        this.destinationCountry = "US";
        this.destinationAmount = new BigDecimal("1080.00"); // ~1.08 exchange rate
        this.swiftCode = "CHASUS33";
        this.iban = "US64SVBKUS6S3300958879";
        return this;
    }

    /**
     * Create transfer with complex currency conversion (USD to JPY)
     */
    public InternationalTransferRequestBuilder withComplexCurrency() {
        this.sourceCurrency = "USD";
        this.destinationCurrency = "JPY";
        this.sourceAmount = new BigDecimal("1000.00");
        this.destinationAmount = new BigDecimal("150000.00"); // ~150 exchange rate
        this.destinationCountry = "JP";
        this.swiftCode = "BOTKJPJT";
        return this;
    }

    /**
     * Create GDPR-compliant EU transfer
     */
    public InternationalTransferRequestBuilder withGDPRTransfer() {
        this.sourceCountry = "FR";
        this.destinationCountry = "DE";
        this.sourceCurrency = "EUR";
        this.destinationCurrency = "EUR";
        this.sourceAmount = new BigDecimal("500.00");
        this.destinationAmount = new BigDecimal("500.00"); // Same currency
        this.purpose = "GDPR-compliant personal transfer";
        return this;
    }

    public InternationalTransferRequest build() {
        InternationalTransferRequest request = new InternationalTransferRequest();
        request.setTransferId(transferId);
        request.setSenderId(senderId);
        request.setRecipientId(recipientId);
        request.setSenderName(senderName);
        request.setRecipientName(recipientName);
        request.setSourceAmount(sourceAmount);
        request.setSourceCurrency(sourceCurrency);
        request.setDestinationAmount(destinationAmount);
        request.setDestinationCurrency(destinationCurrency);
        request.setSourceCountry(sourceCountry);
        request.setDestinationCountry(destinationCountry);
        request.setPurpose(purpose);
        request.setSwiftCode(swiftCode);
        request.setIban(iban);
        request.setIdempotencyKey(idempotencyKey);
        return request;
    }
}
