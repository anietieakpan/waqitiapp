package com.waqiti.saga.builder;

import com.waqiti.saga.dto.P2PTransferRequest;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Test data builder for P2PTransferRequest
 *
 * Provides fluent API for creating test data with sensible defaults.
 * Follows the Test Data Builder pattern.
 *
 * Usage:
 * <pre>
 * P2PTransferRequest request = P2PTransferRequestBuilder.aP2PTransferRequest()
 *     .withAmount(new BigDecimal("100.00"))
 *     .withFromUserId("user-123")
 *     .withToUserId("user-456")
 *     .build();
 * </pre>
 */
public class P2PTransferRequestBuilder {

    private String transactionId;
    private String fromUserId;
    private String toUserId;
    private BigDecimal amount;
    private String currency;
    private String description;
    private String idempotencyKey;

    private P2PTransferRequestBuilder() {
        // Set sensible defaults
        this.transactionId = UUID.randomUUID().toString();
        this.fromUserId = "user-" + UUID.randomUUID().toString().substring(0, 8);
        this.toUserId = "user-" + UUID.randomUUID().toString().substring(0, 8);
        this.amount = new BigDecimal("100.00");
        this.currency = "USD";
        this.description = "Test P2P transfer";
        this.idempotencyKey = UUID.randomUUID().toString();
    }

    public static P2PTransferRequestBuilder aP2PTransferRequest() {
        return new P2PTransferRequestBuilder();
    }

    public P2PTransferRequestBuilder withTransactionId(String transactionId) {
        this.transactionId = transactionId;
        return this;
    }

    public P2PTransferRequestBuilder withFromUserId(String fromUserId) {
        this.fromUserId = fromUserId;
        return this;
    }

    public P2PTransferRequestBuilder withToUserId(String toUserId) {
        this.toUserId = toUserId;
        return this;
    }

    public P2PTransferRequestBuilder withAmount(BigDecimal amount) {
        this.amount = amount;
        return this;
    }

    public P2PTransferRequestBuilder withCurrency(String currency) {
        this.currency = currency;
        return this;
    }

    public P2PTransferRequestBuilder withDescription(String description) {
        this.description = description;
        return this;
    }

    public P2PTransferRequestBuilder withIdempotencyKey(String idempotencyKey) {
        this.idempotencyKey = idempotencyKey;
        return this;
    }

    /**
     * Create a request with insufficient funds scenario
     */
    public P2PTransferRequestBuilder withInsufficientFunds() {
        this.amount = new BigDecimal("999999.99");
        return this;
    }

    /**
     * Create a request with invalid amount (negative)
     */
    public P2PTransferRequestBuilder withInvalidAmount() {
        this.amount = new BigDecimal("-100.00");
        return this;
    }

    /**
     * Create a request with same source and destination (self-transfer)
     */
    public P2PTransferRequestBuilder withSelfTransfer() {
        this.toUserId = this.fromUserId;
        return this;
    }

    /**
     * Create a request with large amount requiring compliance checks
     */
    public P2PTransferRequestBuilder withLargeAmount() {
        this.amount = new BigDecimal("15000.00"); // Above CTR threshold
        return this;
    }

    public P2PTransferRequest build() {
        P2PTransferRequest request = new P2PTransferRequest();
        request.setTransactionId(transactionId);
        request.setFromUserId(fromUserId);
        request.setToUserId(toUserId);
        request.setAmount(amount);
        request.setCurrency(currency);
        request.setDescription(description);
        request.setIdempotencyKey(idempotencyKey);
        return request;
    }
}
