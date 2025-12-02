package com.waqiti.payment.integration.paypal;

import com.paypal.core.PayPalEnvironment;
import com.paypal.core.PayPalHttpClient;
import com.paypal.http.HttpResponse;
import com.paypal.orders.*;
import com.paypal.payments.*;
import com.paypal.subscriptions.*;
import com.paypal.payouts.*;
import com.waqiti.payment.domain.PaymentMethod;
import com.waqiti.payment.domain.Transaction;
import com.waqiti.payment.dto.request.PaymentRequest;
import com.waqiti.payment.dto.response.PaymentResponse;
import com.waqiti.payment.exception.PaymentProcessingException;
import com.waqiti.payment.integration.PaymentProvider;
import com.waqiti.payment.core.model.UnifiedPaymentRequest;
import com.waqiti.payment.core.model.PaymentResult;
import com.waqiti.payment.integration.paypal.dto.*;
import com.waqiti.payment.vault.PaymentProviderSecretsManager;
import com.waqiti.common.cache.CacheService;
import com.waqiti.common.security.EncryptionService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.annotation.PostConstruct;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * Comprehensive PayPal Payment Integration Service
 * 
 * Provides payment processing, checkout sessions, subscription management,
 * and payout capabilities using PayPal's REST API v2.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PayPalPaymentProvider implements PaymentProvider {

    private final PaymentProviderSecretsManager secretsManager;
    private final CacheService cacheService;
    private final EncryptionService encryptionService;
    private final PayPalWebhookHandler webhookHandler;
    private final PayPalCustomerRepository customerRepository;
    private final PayPalTransactionRepository transactionRepository;

    @Value("${paypal.environment:sandbox}")
    private String environment;

    @Value("${paypal.return-url}")
    private String returnUrl;

    @Value("${paypal.cancel-url}")
    private String cancelUrl;

    @Value("${app.base-url}")
    private String baseUrl;

    // Lazy-loaded credentials from Vault
    private String clientId;
    private String clientSecret;
    private String webhookId;

    private PayPalHttpClient payPalClient;
    private PayPalEnvironment payPalEnvironment;

    @PostConstruct
    public void init() {
        try {
            log.info("SECURITY: Loading PayPal credentials from Vault...");

            // Load credentials from Vault
            this.clientId = secretsManager.getPayPalClientId();
            this.clientSecret = secretsManager.getPayPalClientSecret();
            this.webhookId = secretsManager.getPayPalWebhookId();

            // Initialize PayPal SDK with Vault-loaded credentials
            if ("production".equalsIgnoreCase(environment)) {
                this.payPalEnvironment = new PayPalEnvironment.Live(clientId, clientSecret);
            } else {
                this.payPalEnvironment = new PayPalEnvironment.Sandbox(clientId, clientSecret);
            }

            this.payPalClient = new PayPalHttpClient(payPalEnvironment);

            log.info("SECURITY: PayPal payment provider initialized with Vault-secured credentials (environment: {})", environment);

        } catch (Exception e) {
            log.error("CRITICAL: Failed to load PayPal credentials from Vault", e);
            throw new RuntimeException("Failed to initialize PayPal provider - Vault credentials unavailable", e);
        }
    }

    @Override
    public String getProviderName() {
        return "PAYPAL";
    }

    @Override
    public boolean isAvailable() {
        try {
            // Test connectivity with a simple API call
            return payPalClient != null;
        } catch (Exception e) {
            log.error("PayPal availability check failed: {}", e.getMessage());
            return false;
        }
    }

    @Override
    public PaymentResponse processPayment(PaymentRequest request) {
        log.info("Processing PayPal payment for amount: {} {}", request.getAmount(), request.getCurrency());
        
        try {
            // Create PayPal order
            Order order = createPayPalOrder(request);
            
            // Process based on payment method
            if (request.getPaymentMethodId() != null) {
                // Direct payment with stored payment method
                return processDirectPayment(order, request);
            } else {
                // Redirect payment (PayPal checkout)
                return processRedirectPayment(order, request);
            }
            
        } catch (Exception e) {
            log.error("PayPal payment processing failed: {}", e.getMessage());
            throw new PaymentProcessingException("Payment processing failed: " + e.getMessage());
        }
    }

    @Override
    public PaymentResponse capturePayment(String transactionId, BigDecimal amount) {
        log.info("Capturing PayPal payment: {} for amount: {}", transactionId, amount);
        
        try {
            OrdersCaptureRequest request = new OrdersCaptureRequest(transactionId);
            
            HttpResponse<Order> response = payPalClient.execute(request);
            Order capturedOrder = response.result();
            
            return mapToPaymentResponse(capturedOrder, null);
            
        } catch (Exception e) {
            log.error("Failed to capture PayPal payment: {}", e.getMessage());
            throw new PaymentProcessingException("Failed to capture payment: " + e.getMessage());
        }
    }

    @Override
    public PaymentResponse refundPayment(String transactionId, BigDecimal amount, String reason) {
        log.info("Processing PayPal refund for transaction: {} amount: {}", transactionId, amount);
        
        try {
            // Get the capture ID from the order
            String captureId = getCaptureIdFromOrder(transactionId);
            
            RefundRequest refundRequest = new RefundRequest();
            refundRequest.amount(new Money()
                    .currencyCode("USD") // Should be dynamic based on original transaction
                    .value(amount.toString()));
            refundRequest.reason(reason);
            
            CapturesRefundRequest request = new CapturesRefundRequest(captureId);
            request.requestBody(refundRequest);
            
            HttpResponse<Refund> response = payPalClient.execute(request);
            Refund refund = response.result();
            
            return PaymentResponse.builder()
                    .transactionId(refund.id())
                    .originalTransactionId(transactionId)
                    .status(mapRefundStatus(refund.status()))
                    .amount(amount.negate())
                    .currency(refund.amount().currencyCode())
                    .processedAt(LocalDateTime.now())
                    .providerResponse(refund.toString())
                    .build();
                    
        } catch (Exception e) {
            log.error("Failed to process PayPal refund: {}", e.getMessage());
            throw new PaymentProcessingException("Failed to process refund: " + e.getMessage());
        }
    }

    @Override
    public PaymentResponse cancelPayment(String transactionId) {
        log.info("Cancelling PayPal payment: {}", transactionId);
        
        try {
            // PayPal doesn't support direct cancellation, we void the authorization
            String authorizationId = getAuthorizationIdFromOrder(transactionId);
            
            AuthorizationsVoidRequest request = new AuthorizationsVoidRequest(authorizationId);
            
            HttpResponse<Authorization> response = payPalClient.execute(request);
            Authorization voidedAuth = response.result();
            
            return PaymentResponse.builder()
                    .transactionId(voidedAuth.id())
                    .status(Transaction.Status.CANCELLED)
                    .processedAt(LocalDateTime.now())
                    .build();
                    
        } catch (Exception e) {
            log.error("Failed to cancel PayPal payment: {}", e.getMessage());
            throw new PaymentProcessingException("Failed to cancel payment: " + e.getMessage());
        }
    }

    /**
     * Create PayPal checkout session for redirect payments
     */
    public CompletableFuture<PayPalCheckoutSession> createCheckoutSession(PaymentRequest request) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                Order order = createPayPalOrder(request);
                
                // Get approval URL from order links
                String approvalUrl = order.links().stream()
                        .filter(link -> "approve".equals(link.rel()))
                        .map(LinkDescription::href)
                        .findFirst()
                        .orElseThrow(() -> new PaymentProcessingException("No approval URL found"));

                return PayPalCheckoutSession.builder()
                        .orderId(order.id())
                        .approvalUrl(approvalUrl)
                        .status(order.status())
                        .createdAt(LocalDateTime.now())
                        .expiresAt(LocalDateTime.now().plusHours(3)) // PayPal orders expire after 3 hours
                        .build();
                
            } catch (Exception e) {
                log.error("Failed to create PayPal checkout session: {}", e.getMessage());
                throw new PaymentProcessingException("Failed to create checkout session: " + e.getMessage());
            }
        });
    }

    /**
     * Create subscription for recurring payments
     */
    public CompletableFuture<PayPalSubscription> createSubscription(PayPalSubscriptionRequest subscriptionRequest) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                SubscriptionRequest request = new SubscriptionRequest();
                request.planId(subscriptionRequest.getPlanId());
                
                // Set subscriber information
                Subscriber subscriber = new Subscriber();
                subscriber.name(new Name().givenName(subscriptionRequest.getCustomerName()));
                subscriber.emailAddress(subscriptionRequest.getCustomerEmail());
                request.subscriber(subscriber);
                
                // Set application context
                ApplicationContext applicationContext = new ApplicationContext();
                applicationContext.brandName("Waqiti");
                applicationContext.locale("en-US");
                applicationContext.shippingPreference("NO_SHIPPING");
                applicationContext.userAction("SUBSCRIBE_NOW");
                applicationContext.returnUrl(returnUrl);
                applicationContext.cancelUrl(cancelUrl);
                request.applicationContext(applicationContext);
                
                SubscriptionsCreateRequest createRequest = new SubscriptionsCreateRequest();
                createRequest.requestBody(request);
                
                HttpResponse<Subscription> response = payPalClient.execute(createRequest);
                Subscription subscription = response.result();
                
                return PayPalSubscription.builder()
                        .subscriptionId(subscription.id())
                        .status(subscription.status())
                        .planId(subscription.planId())
                        .customerId(subscriptionRequest.getCustomerId())
                        .startTime(subscription.startTime())
                        .approvalUrl(getApprovalUrl(subscription.links()))
                        .createdAt(LocalDateTime.now())
                        .build();
                
            } catch (Exception e) {
                log.error("Failed to create PayPal subscription: {}", e.getMessage());
                throw new PaymentProcessingException("Failed to create subscription: " + e.getMessage());
            }
        });
    }

    /**
     * Process payout to external PayPal account
     */
    public CompletableFuture<PayPalPayout> createPayout(PayPalPayoutRequest payoutRequest) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                CreatePayoutRequest request = new CreatePayoutRequest();
                
                PayoutHeader payoutHeader = new PayoutHeader();
                payoutHeader.senderBatchId(UUID.randomUUID().toString());
                payoutHeader.emailSubject("You have a payment from Waqiti");
                payoutHeader.emailMessage("You have received a payment. Thanks for using our service!");
                request.payoutHeader(payoutHeader);
                
                // Add payout items
                List<PayoutItem> items = new ArrayList<>();
                PayoutItem item = new PayoutItem();
                item.recipientType("EMAIL");
                item.amount(new Currency()
                        .value(payoutRequest.getAmount().toString())
                        .currency(payoutRequest.getCurrency()));
                item.receiver(payoutRequest.getRecipientEmail());
                item.senderItemId(payoutRequest.getReference());
                item.note(payoutRequest.getNote());
                items.add(item);
                
                request.payoutItems(items);
                
                PayoutsPostRequest payoutPostRequest = new PayoutsPostRequest();
                payoutPostRequest.requestBody(request);
                
                HttpResponse<CreatePayoutResponse> response = payPalClient.execute(payoutPostRequest);
                CreatePayoutResponse payoutResponse = response.result();
                
                return PayPalPayout.builder()
                        .payoutBatchId(payoutResponse.batchHeader().payoutBatchId())
                        .batchStatus(payoutResponse.batchHeader().batchStatus())
                        .amount(payoutRequest.getAmount())
                        .currency(payoutRequest.getCurrency())
                        .recipientEmail(payoutRequest.getRecipientEmail())
                        .reference(payoutRequest.getReference())
                        .createdAt(LocalDateTime.now())
                        .build();
                
            } catch (Exception e) {
                log.error("Failed to create PayPal payout: {}", e.getMessage());
                throw new PaymentProcessingException("Failed to create payout: " + e.getMessage());
            }
        });
    }

    /**
     * Verify webhook signature
     */
    public boolean verifyWebhookSignature(String payload, Map<String, String> headers) {
        try {
            return webhookHandler.verifySignature(payload, headers, webhookId);
        } catch (Exception e) {
            log.error("Failed to verify PayPal webhook signature: {}", e.getMessage());
            return false;
        }
    }

    // Private helper methods

    private Order createPayPalOrder(PaymentRequest request) throws Exception {
        OrderRequest orderRequest = new OrderRequest();
        orderRequest.checkoutPaymentIntent("CAPTURE");
        
        // Set purchase units
        List<PurchaseUnitRequest> purchaseUnits = new ArrayList<>();
        PurchaseUnitRequest purchaseUnit = new PurchaseUnitRequest();
        purchaseUnit.amount(new AmountWithBreakdown()
                .currencyCode(request.getCurrency())
                .value(request.getAmount().toString()));
        purchaseUnit.description(request.getDescription());
        purchaseUnit.customId(request.getTransactionId());
        purchaseUnits.add(purchaseUnit);
        orderRequest.purchaseUnits(purchaseUnits);
        
        // Set application context
        ApplicationContext applicationContext = new ApplicationContext();
        applicationContext.returnUrl(returnUrl);
        applicationContext.cancelUrl(cancelUrl);
        applicationContext.brandName("Waqiti");
        applicationContext.locale("en-US");
        applicationContext.landingPage("BILLING");
        applicationContext.shippingPreference("NO_SHIPPING");
        applicationContext.userAction("PAY_NOW");
        orderRequest.applicationContext(applicationContext);
        
        OrdersCreateRequest createRequest = new OrdersCreateRequest();
        createRequest.requestBody(orderRequest);
        
        HttpResponse<Order> response = payPalClient.execute(createRequest);
        return response.result();
    }

    private PaymentResponse processDirectPayment(Order order, PaymentRequest request) throws Exception {
        // For direct payments, we would need to use stored payment method
        // This would require PayPal's vault API or reference transactions
        OrdersCaptureRequest captureRequest = new OrdersCaptureRequest(order.id());
        
        HttpResponse<Order> response = payPalClient.execute(captureRequest);
        Order capturedOrder = response.result();
        
        return mapToPaymentResponse(capturedOrder, request);
    }

    private PaymentResponse processRedirectPayment(Order order, PaymentRequest request) {
        // Return response with approval URL for redirect
        String approvalUrl = order.links().stream()
                .filter(link -> "approve".equals(link.rel()))
                .map(LinkDescription::href)
                .findFirst()
                .orElse("");

        return PaymentResponse.builder()
                .transactionId(order.id())
                .status(mapOrderStatus(order.status()))
                .amount(request.getAmount())
                .currency(request.getCurrency())
                .requires3DSecure(false)
                .requiresAction(true)
                .redirectUrl(approvalUrl)
                .processedAt(LocalDateTime.now())
                .providerResponse(order.toString())
                .metadata(Map.of(
                        "approval_url", approvalUrl,
                        "order_status", order.status()
                ))
                .build();
    }

    private PaymentResponse mapToPaymentResponse(Order order, PaymentRequest request) {
        Transaction.Status status = mapOrderStatus(order.status());
        
        return PaymentResponse.builder()
                .transactionId(order.id())
                .status(status)
                .amount(request != null ? request.getAmount() : extractAmountFromOrder(order))
                .currency(extractCurrencyFromOrder(order))
                .providerTransactionId(order.id())
                .providerResponse(order.toString())
                .requires3DSecure(false)
                .processedAt(LocalDateTime.now())
                .metadata(Map.of(
                        "order_status", order.status(),
                        "capture_id", extractCaptureId(order)
                ))
                .build();
    }

    private Transaction.Status mapOrderStatus(String paypalStatus) {
        switch (paypalStatus) {
            case "COMPLETED":
                return Transaction.Status.COMPLETED;
            case "APPROVED":
                return Transaction.Status.AUTHORIZED;
            case "CREATED":
            case "SAVED":
                return Transaction.Status.PENDING;
            case "VOIDED":
                return Transaction.Status.CANCELLED;
            case "PAYER_ACTION_REQUIRED":
                return Transaction.Status.PENDING;
            default:
                return Transaction.Status.PENDING;
        }
    }

    private Transaction.Status mapRefundStatus(String refundStatus) {
        switch (refundStatus) {
            case "COMPLETED":
                return Transaction.Status.COMPLETED;
            case "PENDING":
                return Transaction.Status.PROCESSING;
            case "FAILED":
                return Transaction.Status.FAILED;
            case "CANCELLED":
                return Transaction.Status.CANCELLED;
            default:
                return Transaction.Status.PENDING;
        }
    }

    private String getCaptureIdFromOrder(String orderId) throws Exception {
        OrdersGetRequest request = new OrdersGetRequest(orderId);
        HttpResponse<Order> response = payPalClient.execute(request);
        
        return extractCaptureId(response.result());
    }

    private String getAuthorizationIdFromOrder(String orderId) throws Exception {
        OrdersGetRequest request = new OrdersGetRequest(orderId);
        HttpResponse<Order> response = payPalClient.execute(request);
        
        return extractAuthorizationId(response.result());
    }

    private String extractCaptureId(Order order) {
        return order.purchaseUnits().stream()
                .flatMap(unit -> unit.payments().captures().stream())
                .map(Capture::id)
                .findFirst()
                .orElse("");
    }

    private String extractAuthorizationId(Order order) {
        return order.purchaseUnits().stream()
                .flatMap(unit -> unit.payments().authorizations().stream())
                .map(Authorization::id)
                .findFirst()
                .orElse("");
    }

    private BigDecimal extractAmountFromOrder(Order order) {
        return order.purchaseUnits().stream()
                .map(unit -> new BigDecimal(unit.amount().value()))
                .findFirst()
                .orElse(BigDecimal.ZERO);
    }

    private String extractCurrencyFromOrder(Order order) {
        return order.purchaseUnits().stream()
                .map(unit -> unit.amount().currencyCode())
                .findFirst()
                .orElse("USD");
    }

    private String getApprovalUrl(List<LinkDescription> links) {
        return links.stream()
                .filter(link -> "approve".equals(link.rel()))
                .map(LinkDescription::href)
                .findFirst()
                .orElse("");
    }
    
    /**
     * Process P2P payment through PayPal
     */
    public PaymentResult processP2PPayment(UnifiedPaymentRequest request) {
        log.info("Processing P2P payment through PayPal for amount: {}", request.getAmount());
        
        try {
            // Create payout for P2P transfer
            PayoutsPostRequest payoutRequest = new PayoutsPostRequest();
            CreatePayoutRequest createPayoutRequest = new CreatePayoutRequest()
                    .senderBatchHeader(new SenderBatchHeader()
                            .senderBatchId(UUID.randomUUID().toString())
                            .recipientType("EMAIL")
                            .emailSubject("Payment from Waqiti user")
                            .emailMessage(request.getDescription()))
                    .items(Collections.singletonList(new PayoutItem()
                            .recipientType("EMAIL")
                            .receiver((String) request.getMetadata().get("recipientEmail"))
                            .amount(new Currency()
                                    .value(String.format("%.2f", request.getAmount()))
                                    .currency(request.getCurrency()))
                            .senderItemId(request.getRequestId())
                            .note(request.getDescription())));
            
            payoutRequest.requestBody(createPayoutRequest);
            HttpResponse<CreatePayoutResponse> response = payPalClient.execute(payoutRequest);
            
            return PaymentResult.builder()
                    .paymentId(response.result().batchHeader().payoutBatchId())
                    .requestId(request.getRequestId())
                    .status(mapPayoutStatus(response.result().batchHeader().batchStatus()))
                    .amount(request.getAmount())
                    .currency(request.getCurrency())
                    .transactionId(response.result().batchHeader().payoutBatchId())
                    .provider("PAYPAL")
                    .processedAt(LocalDateTime.now())
                    .confirmationNumber(response.result().batchHeader().payoutBatchId())
                    .build();
                    
        } catch (Exception e) {
            log.error("PayPal P2P payment failed", e);
            return createFailureResult(request, e);
        }
    }
    
    /**
     * Process merchant payment through PayPal
     */
    public PaymentResult processMerchantPayment(UnifiedPaymentRequest request) {
        log.info("Processing merchant payment through PayPal for amount: {}", request.getAmount());
        
        try {
            // Create standard PayPal order
            OrderRequest orderRequest = new OrderRequest()
                    .checkoutPaymentIntent("CAPTURE")
                    .purchaseUnits(Collections.singletonList(
                            new PurchaseUnitRequest()
                                    .referenceId(request.getRequestId())
                                    .description(request.getDescription())
                                    .amount(new AmountWithBreakdown()
                                            .currencyCode(request.getCurrency())
                                            .value(String.format("%.2f", request.getAmount())))
                                    .payee(new Payee()
                                            .merchantId(request.getRecipientId()))));
            
            OrdersCreateRequest createRequest = new OrdersCreateRequest()
                    .requestBody(orderRequest);
            
            HttpResponse<Order> response = payPalClient.execute(createRequest);
            Order order = response.result();
            
            return PaymentResult.builder()
                    .paymentId(order.id())
                    .requestId(request.getRequestId())
                    .status(mapOrderStatus(order.status()))
                    .amount(request.getAmount())
                    .currency(request.getCurrency())
                    .transactionId(order.id())
                    .provider("PAYPAL")
                    .processedAt(LocalDateTime.now())
                    .metadata(Map.of(
                            "approvalUrl", getApprovalUrl(order.links()),
                            "status", order.status()
                    ))
                    .build();
                    
        } catch (Exception e) {
            log.error("PayPal merchant payment failed", e);
            return createFailureResult(request, e);
        }
    }
    
    /**
     * Process international payment through PayPal
     */
    public PaymentResult processInternationalPayment(UnifiedPaymentRequest request) {
        log.info("Processing international payment through PayPal for amount: {}", request.getAmount());
        
        try {
            // PayPal handles currency conversion automatically
            PayoutsPostRequest payoutRequest = new PayoutsPostRequest();
            CreatePayoutRequest createPayoutRequest = new CreatePayoutRequest()
                    .senderBatchHeader(new SenderBatchHeader()
                            .senderBatchId(UUID.randomUUID().toString())
                            .recipientType("EMAIL")
                            .emailSubject("International payment from Waqiti")
                            .emailMessage(request.getDescription()))
                    .items(Collections.singletonList(new PayoutItem()
                            .recipientType("EMAIL")
                            .receiver((String) request.getMetadata().get("recipientEmail"))
                            .amount(new Currency()
                                    .value(String.format("%.2f", request.getAmount()))
                                    .currency((String) request.getMetadata().get("targetCurrency")))
                            .senderItemId(request.getRequestId())
                            .note("International transfer: " + request.getDescription())));
            
            payoutRequest.requestBody(createPayoutRequest);
            HttpResponse<CreatePayoutResponse> response = payPalClient.execute(payoutRequest);
            
            return PaymentResult.builder()
                    .paymentId(response.result().batchHeader().payoutBatchId())
                    .requestId(request.getRequestId())
                    .status(PaymentResult.PaymentStatus.PROCESSING)
                    .amount(request.getAmount())
                    .currency(request.getCurrency())
                    .transactionId(response.result().batchHeader().payoutBatchId())
                    .provider("PAYPAL")
                    .processedAt(LocalDateTime.now())
                    .metadata(Map.of(
                            "targetCurrency", request.getMetadata().get("targetCurrency"),
                            "destinationCountry", request.getMetadata().get("destinationCountry")
                    ))
                    .build();
                    
        } catch (Exception e) {
            log.error("PayPal international payment failed", e);
            return createFailureResult(request, e);
        }
    }
    
    /**
     * Process refund through PayPal
     */
    public PaymentResult processRefund(String transactionId, BigDecimal amount) {
        log.info("Processing PayPal refund for transaction: {} amount: {}", transactionId, amount);
        
        try {
            CapturesRefundRequest refundRequest = new CapturesRefundRequest(transactionId);
            refundRequest.requestBody(new RefundRequest()
                    .amount(new Money()
                            .currencyCode("USD")
                            .value(String.format("%.2f", amount)))
                    .note("Customer requested refund"));
            
            HttpResponse<Refund> response = payPalClient.execute(refundRequest);
            Refund refund = response.result();
            
            return PaymentResult.builder()
                    .paymentId(refund.id())
                    .status(mapRefundStatus(refund.status()))
                    .amount(amount.doubleValue())
                    .transactionId(refund.id())
                    .provider("PAYPAL")
                    .processedAt(LocalDateTime.now())
                    .confirmationNumber(refund.id())
                    .build();
                    
        } catch (Exception e) {
            log.error("PayPal refund failed", e);
            throw new PaymentProcessingException("Refund failed: " + e.getMessage());
        }
    }
    
    /**
     * Submit evidence for PayPal dispute
     * CRITICAL: Required for defending against chargebacks and reducing losses
     */
    public DisputeEvidenceResult submitDisputeEvidence(String disputeId, DisputeEvidenceRequest evidenceRequest) {
        log.info("Submitting evidence for PayPal dispute: {}", disputeId);

        try {
            // PayPal Disputes API endpoint
            String url = payPalEnvironment.baseUrl() + "/v1/customer/disputes/" + disputeId + "/provide-evidence";

            Map<String, Object> evidencePayload = new HashMap<>();
            List<Map<String, Object>> evidences = new ArrayList<>();

            // Build evidence documents
            if (evidenceRequest.getTrackingInfo() != null) {
                evidences.add(Map.of(
                        "evidence_type", "PROOF_OF_FULFILLMENT",
                        "evidence_info", evidenceRequest.getTrackingInfo()
                ));
            }

            if (evidenceRequest.getRefundPolicy() != null) {
                evidences.add(Map.of(
                        "evidence_type", "REFUND_POLICY",
                        "evidence_info", Map.of("refund_policy", evidenceRequest.getRefundPolicy())
                ));
            }

            if (evidenceRequest.getCustomerCommunication() != null) {
                evidences.add(Map.of(
                        "evidence_type", "PROOF_OF_DELIVERY_SIGNATURE",
                        "evidence_info", evidenceRequest.getCustomerCommunication()
                ));
            }

            evidencePayload.put("evidences", evidences);
            evidencePayload.put("note", evidenceRequest.getNotes());

            HttpResponse response = payPalClient.execute(
                    new com.paypal.http.HttpRequest(url, "POST")
                            .requestBody(evidencePayload)
            );

            log.info("Successfully submitted evidence for PayPal dispute: {}", disputeId);

            return DisputeEvidenceResult.builder()
                    .disputeId(disputeId)
                    .status("SUBMITTED")
                    .evidenceSubmitted(true)
                    .submittedAt(LocalDateTime.now())
                    .build();

        } catch (Exception e) {
            log.error("Failed to submit PayPal dispute evidence: {}", disputeId, e);
            throw new PaymentProcessingException("Dispute evidence submission failed: " + e.getMessage());
        }
    }

    /**
     * Retrieve PayPal dispute details
     */
    public DisputeDetails getDisputeDetails(String disputeId) {
        log.info("Retrieving PayPal dispute details: {}", disputeId);

        try {
            String url = payPalEnvironment.baseUrl() + "/v1/customer/disputes/" + disputeId;

            HttpResponse<Map> response = payPalClient.execute(
                    new com.paypal.http.HttpRequest<Map>(url, "GET")
                            .responseType(Map.class)
            );

            Map<String, Object> dispute = response.result();

            return DisputeDetails.builder()
                    .disputeId((String) dispute.get("dispute_id"))
                    .reason((String) dispute.get("reason"))
                    .status((String) dispute.get("status"))
                    .amount(new BigDecimal((String) ((Map) dispute.get("dispute_amount")).get("value")))
                    .currency((String) ((Map) dispute.get("dispute_amount")).get("currency_code"))
                    .created(LocalDateTime.parse((String) dispute.get("create_time")))
                    .build();

        } catch (Exception e) {
            log.error("Failed to retrieve PayPal dispute details: {}", disputeId, e);
            throw new PaymentProcessingException("Failed to retrieve dispute: " + e.getMessage());
        }
    }

    /**
     * Check if PayPal service is healthy
     */
    public boolean isHealthy() {
        return isAvailable();
    }

    // DTO classes for dispute evidence
    @lombok.Data
    @lombok.Builder
    public static class DisputeEvidenceRequest {
        private Map<String, Object> trackingInfo;
        private String refundPolicy;
        private Map<String, Object> customerCommunication;
        private String notes;
    }

    @lombok.Data
    @lombok.Builder
    public static class DisputeEvidenceResult {
        private String disputeId;
        private String status;
        private boolean evidenceSubmitted;
        private LocalDateTime submittedAt;
        private String errorMessage;
    }

    @lombok.Data
    @lombok.Builder
    public static class DisputeDetails {
        private String disputeId;
        private String reason;
        private String status;
        private BigDecimal amount;
        private String currency;
        private LocalDateTime created;
    }
    
    // Helper methods for new functionality
    
    private PaymentResult.PaymentStatus mapPayoutStatus(String status) {
        switch (status) {
            case "SUCCESS":
                return PaymentResult.PaymentStatus.COMPLETED;
            case "PROCESSING":
                return PaymentResult.PaymentStatus.PROCESSING;
            case "PENDING":
                return PaymentResult.PaymentStatus.PENDING;
            case "CANCELED":
                return PaymentResult.PaymentStatus.CANCELLED;
            case "DENIED":
            case "FAILED":
                return PaymentResult.PaymentStatus.FAILED;
            default:
                return PaymentResult.PaymentStatus.PENDING;
        }
    }
    
    private PaymentResult.PaymentStatus mapOrderStatus(String status) {
        switch (status) {
            case "COMPLETED":
                return PaymentResult.PaymentStatus.COMPLETED;
            case "APPROVED":
                return PaymentResult.PaymentStatus.PROCESSING;
            case "CREATED":
            case "SAVED":
                return PaymentResult.PaymentStatus.PENDING;
            case "VOIDED":
                return PaymentResult.PaymentStatus.CANCELLED;
            default:
                return PaymentResult.PaymentStatus.FAILED;
        }
    }
    
    private PaymentResult.PaymentStatus mapRefundStatus(String status) {
        switch (status) {
            case "COMPLETED":
                return PaymentResult.PaymentStatus.REFUNDED;
            case "PENDING":
                return PaymentResult.PaymentStatus.PROCESSING;
            case "FAILED":
                return PaymentResult.PaymentStatus.FAILED;
            default:
                return PaymentResult.PaymentStatus.PENDING;
        }
    }
    
    private PaymentResult createFailureResult(UnifiedPaymentRequest request, Exception e) {
        return PaymentResult.builder()
                .paymentId(UUID.randomUUID().toString())
                .requestId(request.getRequestId())
                .status(PaymentResult.PaymentStatus.FAILED)
                .amount(request.getAmount())
                .currency(request.getCurrency())
                .errorMessage(e.getMessage())
                .errorCode("PAYPAL_ERROR")
                .provider("PAYPAL")
                .processedAt(LocalDateTime.now())
                .build();
    }
}