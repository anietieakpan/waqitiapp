package com.waqiti.bankintegration.strategy;

import com.paypal.core.PayPalEnvironment;
import com.paypal.core.PayPalHttpClient;
import com.paypal.http.HttpResponse;
import com.paypal.orders.*;
import com.paypal.payments.CapturesGetRequest;
import com.paypal.payments.CapturesRefundRequest;
import com.paypal.payments.Refund;
import com.paypal.payments.RefundRequest;
import com.waqiti.bankintegration.domain.PaymentProvider;
import com.waqiti.bankintegration.dto.*;
import com.waqiti.bankintegration.exception.PaymentProcessingException;
import com.waqiti.common.exception.BusinessException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * PayPal Payment Strategy Implementation
 * 
 * Handles payment processing through PayPal's Orders API v2.
 * Supports order creation, capture, refunds, and status checks.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PayPalPaymentStrategy implements PaymentStrategy {
    
    @Value("${waqiti.frontend.base-url:https://app.example.com}")
    private String frontendBaseUrl;

    @Override
    @CircuitBreaker(name = "paypalApi", fallbackMethod = "processPaymentFallback")
    @Retry(name = "paypalApi")
    public PaymentResponse processPayment(PaymentProvider provider, PaymentRequest request) {
        try {
            PayPalHttpClient client = createPayPalClient(provider);
            
            log.debug("Processing PayPal payment for amount: {} {}", 
                request.getAmount(), request.getCurrency());

            // Create PayPal order
            Order order = createPayPalOrder(request, client);
            
            PaymentResponse response = new PaymentResponse();
            response.setRequestId(request.getRequestId());
            response.setProviderId(provider.getId());
            response.setTransactionId(order.id());
            response.setProviderTransactionId(order.id());
            response.setStatus(mapPayPalStatus(order.status()));
            response.setAmount(request.getAmount());
            response.setCurrency(request.getCurrency());
            response.setProcessedAt(Instant.parse(order.createTime()));

            // Extract approval URL for redirect flow
            Map<String, Object> additionalData = new HashMap<>();
            for (LinkDescription link : order.links()) {
                if ("approve".equals(link.rel())) {
                    additionalData.put("approval_url", link.href());
                    response.setAuthenticationRequired(PaymentResponse.AuthenticationRequired.builder()
                        .type("redirect")
                        .redirectUrl(link.href())
                        .build());
                    break;
                }
            }
            response.setAdditionalData(additionalData);

            // Auto-capture if requested and approved
            if (Boolean.TRUE.equals(request.getAutoConfirm()) && 
                "APPROVED".equals(order.status())) {
                Order capturedOrder = capturePayPalOrder(order.id(), client);
                response.setStatus(mapPayPalStatus(capturedOrder.status()));
                
                // Extract capture details
                if (capturedOrder.purchaseUnits() != null && 
                    !capturedOrder.purchaseUnits().isEmpty()) {
                    PurchaseUnit purchaseUnit = capturedOrder.purchaseUnits().get(0);
                    if (purchaseUnit.payments() != null && 
                        purchaseUnit.payments().captures() != null &&
                        !purchaseUnit.payments().captures().isEmpty()) {
                        Capture capture = purchaseUnit.payments().captures().get(0);
                        additionalData.put("capture_id", capture.id());
                        additionalData.put("capture_status", capture.status());
                    }
                }
            }

            log.info("PayPal payment processed successfully: {}", response.getTransactionId());
            return response;

        } catch (Exception e) {
            log.error("PayPal payment failed: {}", e.getMessage(), e);
            throw new PaymentProcessingException("PayPal payment processing failed: " + e.getMessage(), e);
        }
    }

    @Override
    @CircuitBreaker(name = "paypalApi", fallbackMethod = "processRefundFallback")
    @Retry(name = "paypalApi")
    public RefundResponse processRefund(PaymentProvider provider, RefundRequest request) {
        try {
            PayPalHttpClient client = createPayPalClient(provider);
            
            log.debug("Processing PayPal refund for transaction: {}", request.getOriginalTransactionId());

            // First, get the capture ID from the order
            String captureId = getCaptureIdFromOrder(request.getOriginalTransactionId(), client);
            
            if (captureId == null) {
                throw new BusinessException("Cannot find capture for order: " + request.getOriginalTransactionId());
            }

            // Create refund request
            RefundRequest refundReq = new RefundRequest();
            refundReq.amount(new Money()
                .currencyCode(request.getCurrency())
                .value(request.getAmount().toString()));
            
            if (request.getReasonDetails() != null) {
                refundReq.noteToPayer(request.getReasonDetails());
            }

            CapturesRefundRequest refundRequest = new CapturesRefundRequest(captureId);
            refundRequest.requestBody(refundReq);

            HttpResponse<Refund> response = client.execute(refundRequest);
            Refund refund = response.result();

            RefundResponse refundResponse = new RefundResponse();
            refundResponse.setRefundId(refund.id());
            refundResponse.setProviderRefundId(refund.id());
            refundResponse.setOriginalTransactionId(request.getOriginalTransactionId());
            refundResponse.setStatus(mapRefundStatus(refund.status()));
            refundResponse.setAmount(new BigDecimal(refund.amount().value()));
            refundResponse.setCurrency(refund.amount().currencyCode());
            refundResponse.setProcessedAt(Instant.parse(refund.createTime()));

            if ("FAILED".equals(refund.status())) {
                refundResponse.setErrorCode("PAYPAL_REFUND_FAILED");
                refundResponse.setErrorMessage("PayPal refund failed");
            }

            log.info("PayPal refund processed successfully: {}", refundResponse.getRefundId());
            return refundResponse;

        } catch (Exception e) {
            log.error("PayPal refund failed: {}", e.getMessage(), e);
            throw new PaymentProcessingException("PayPal refund processing failed: " + e.getMessage(), e);
        }
    }

    @Override
    @CircuitBreaker(name = "paypalApi")
    public PaymentResponse checkPaymentStatus(PaymentProvider provider, String transactionId) {
        try {
            PayPalHttpClient client = createPayPalClient(provider);
            
            OrdersGetRequest request = new OrdersGetRequest(transactionId);
            HttpResponse<Order> response = client.execute(request);
            Order order = response.result();

            PaymentResponse paymentResponse = new PaymentResponse();
            paymentResponse.setTransactionId(transactionId);
            paymentResponse.setProviderId(provider.getId());
            paymentResponse.setProviderTransactionId(order.id());
            paymentResponse.setStatus(mapPayPalStatus(order.status()));
            paymentResponse.setProcessedAt(Instant.parse(order.createTime()));

            // Extract amount and currency from purchase units
            if (order.purchaseUnits() != null && !order.purchaseUnits().isEmpty()) {
                PurchaseUnit purchaseUnit = order.purchaseUnits().get(0);
                if (purchaseUnit.amountWithBreakdown() != null) {
                    paymentResponse.setAmount(new BigDecimal(purchaseUnit.amountWithBreakdown().value()));
                    paymentResponse.setCurrency(purchaseUnit.amountWithBreakdown().currencyCode());
                }
            }

            return paymentResponse;

        } catch (Exception e) {
            log.error("Failed to check PayPal payment status: {}", e.getMessage(), e);
            throw new PaymentProcessingException("Failed to check payment status: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean canHandle(PaymentProvider provider, PaymentRequest request) {
        return provider.getProviderType() == ProviderType.PAYPAL &&
               provider.supportsFeature("paypal_checkout");
    }

    @Override
    public boolean isProviderHealthy(PaymentProvider provider) {
        try {
            PayPalHttpClient client = createPayPalClient(provider);
            
            // Make a lightweight API call to check connectivity
            OrdersGetRequest request = new OrdersGetRequest("dummy-order-id");
            client.execute(request);
            return true;
        } catch (Exception e) {
            // Expected to fail with 404 for dummy order, but validates connectivity
            if (e.getMessage() != null && e.getMessage().contains("404")) {
                return true;
            }
            log.warn("PayPal health check failed: {}", e.getMessage());
            return false;
        }
    }

    @Override
    @CircuitBreaker(name = "paypalApi")
    public PaymentResponse cancelPayment(PaymentProvider provider, String transactionId) {
        try {
            PayPalHttpClient client = createPayPalClient(provider);
            
            // PayPal doesn't have a direct cancel API for orders
            // We need to check the status and handle accordingly
            OrdersGetRequest getRequest = new OrdersGetRequest(transactionId);
            HttpResponse<Order> response = client.execute(getRequest);
            Order order = response.result();
            
            PaymentResponse paymentResponse = new PaymentResponse();
            paymentResponse.setTransactionId(transactionId);
            paymentResponse.setProviderId(provider.getId());
            paymentResponse.setProviderTransactionId(order.id());
            
            if ("CREATED".equals(order.status()) || "SAVED".equals(order.status()) || 
                "APPROVED".equals(order.status())) {
                // Order can be considered cancelled if not captured
                paymentResponse.setStatus(PaymentStatus.CANCELLED);
            } else {
                paymentResponse.setStatus(mapPayPalStatus(order.status()));
            }
            
            log.info("PayPal payment cancellation handled: {}", transactionId);
            return paymentResponse;

        } catch (Exception e) {
            log.error("Failed to cancel PayPal payment: {}", e.getMessage(), e);
            throw new PaymentProcessingException("Failed to cancel payment: " + e.getMessage(), e);
        }
    }

    private PayPalHttpClient createPayPalClient(PaymentProvider provider) {
        PayPalEnvironment environment;
        
        if (provider.isSandboxMode()) {
            environment = new PayPalEnvironment.Sandbox(
                provider.getApiKey(),
                provider.getApiSecret()
            );
        } else {
            environment = new PayPalEnvironment.Live(
                provider.getApiKey(),
                provider.getApiSecret()
            );
        }
        
        return new PayPalHttpClient(environment);
    }

    private Order createPayPalOrder(PaymentRequest request, PayPalHttpClient client) 
            throws IOException {
        
        OrderRequest orderRequest = new OrderRequest();
        orderRequest.checkoutPaymentIntent("CAPTURE");

        ApplicationContext applicationContext = new ApplicationContext()
            .brandName("Waqiti")
            .landingPage("BILLING")
            .userAction("PAY_NOW")
            .returnUrl(request.getReturnUrl() != null ? request.getReturnUrl() : (frontendBaseUrl + "/payment/success"))
            .cancelUrl(frontendBaseUrl + "/payment/cancel");
        
        orderRequest.applicationContext(applicationContext);

        List<PurchaseUnitRequest> purchaseUnits = new ArrayList<>();
        PurchaseUnitRequest purchaseUnitRequest = new PurchaseUnitRequest()
            .referenceId(request.getRequestId())
            .description(request.getDescription() != null ? request.getDescription() : "Waqiti Payment")
            .amountWithBreakdown(new AmountWithBreakdown()
                .currencyCode(request.getCurrency())
                .value(request.getAmount().toString()));

        // Add billing details if available
        if (request.getBillingDetails() != null) {
            Payee payee = new Payee();
            if (request.getBillingDetails().getEmail() != null) {
                payee.emailAddress(request.getBillingDetails().getEmail());
            }
            purchaseUnitRequest.payee(payee);
        }

        purchaseUnits.add(purchaseUnitRequest);
        orderRequest.purchaseUnits(purchaseUnits);

        OrdersCreateRequest orderCreateRequest = new OrdersCreateRequest();
        orderCreateRequest.requestBody(orderRequest);

        HttpResponse<Order> response = client.execute(orderCreateRequest);
        return response.result();
    }

    private Order capturePayPalOrder(String orderId, PayPalHttpClient client) throws IOException {
        OrdersCaptureRequest request = new OrdersCaptureRequest(orderId);
        request.requestBody(new OrderActionRequest());

        HttpResponse<Order> response = client.execute(request);
        return response.result();
    }

    private String getCaptureIdFromOrder(String orderId, PayPalHttpClient client) throws IOException {
        OrdersGetRequest request = new OrdersGetRequest(orderId);
        HttpResponse<Order> response = client.execute(request);
        Order order = response.result();

        if (order.purchaseUnits() != null && !order.purchaseUnits().isEmpty()) {
            PurchaseUnit purchaseUnit = order.purchaseUnits().get(0);
            if (purchaseUnit.payments() != null && 
                purchaseUnit.payments().captures() != null &&
                !purchaseUnit.payments().captures().isEmpty()) {
                return purchaseUnit.payments().captures().get(0).id();
            }
        }
        
        throw new PaymentProcessingException("No capture ID found in PayPal order response");
    }

    private PaymentStatus mapPayPalStatus(String paypalStatus) {
        return switch (paypalStatus) {
            case "COMPLETED" -> PaymentStatus.COMPLETED;
            case "APPROVED" -> PaymentStatus.AUTHORIZED;
            case "CREATED", "SAVED" -> PaymentStatus.PENDING;
            case "CANCELLED" -> PaymentStatus.CANCELLED;
            case "VOIDED" -> PaymentStatus.CANCELLED;
            case "PARTIALLY_CAPTURED" -> PaymentStatus.PARTIALLY_REFUNDED;
            default -> PaymentStatus.FAILED;
        };
    }

    private RefundStatus mapRefundStatus(String paypalStatus) {
        return switch (paypalStatus) {
            case "COMPLETED" -> RefundStatus.COMPLETED;
            case "PENDING" -> RefundStatus.PENDING;
            case "FAILED" -> RefundStatus.FAILED;
            case "CANCELLED" -> RefundStatus.CANCELLED;
            default -> RefundStatus.FAILED;
        };
    }

    // Fallback methods
    public PaymentResponse processPaymentFallback(PaymentProvider provider, PaymentRequest request, Exception ex) {
        log.error("PayPal payment fallback triggered for request: {}", request.getRequestId(), ex);
        PaymentResponse response = new PaymentResponse();
        response.setRequestId(request.getRequestId());
        response.setStatus(PaymentStatus.FAILED);
        response.setErrorCode("PAYPAL_UNAVAILABLE");
        response.setErrorMessage("PayPal service is temporarily unavailable");
        return response;
    }

    public RefundResponse processRefundFallback(PaymentProvider provider, RefundRequest request, Exception ex) {
        log.error("PayPal refund fallback triggered for request: {}", request.getRequestId(), ex);
        RefundResponse response = new RefundResponse();
        response.setRefundId(request.getRequestId());
        response.setStatus(RefundStatus.FAILED);
        response.setErrorCode("PAYPAL_UNAVAILABLE");
        response.setErrorMessage("PayPal service is temporarily unavailable");
        return response;
    }
}