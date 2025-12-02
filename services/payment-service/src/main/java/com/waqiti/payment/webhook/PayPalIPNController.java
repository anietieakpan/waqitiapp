package com.waqiti.payment.webhook;

import com.waqiti.common.audit.TransactionAuditService;
import com.waqiti.common.idempotency.IdempotencyService;
import com.waqiti.payment.service.PaymentService;
import com.waqiti.payment.service.PayPalPaymentProcessor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import jakarta.servlet.http.HttpServletRequest;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.net.HttpURLConnection;
import java.net.URL;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * CRITICAL SECURITY: PayPal IPN (Instant Payment Notification) Controller
 * Handles PayPal webhook notifications with comprehensive security validation
 * Implements IPN verification, idempotency, and audit trail
 */
@RestController
@RequestMapping("/webhooks/paypal")
@RequiredArgsConstructor
@Slf4j
public class PayPalIPNController {
    
    private static final Set<String> PAYPAL_WEBHOOK_IPS = Set.of(
        "173.0.80.0", "173.0.81.0", "173.0.82.0", "173.0.83.0",
        "64.4.240.0", "64.4.241.0", "64.4.242.0", "64.4.243.0",
        "64.4.244.0", "64.4.245.0", "64.4.246.0", "64.4.247.0",
        "66.211.168.0", "66.211.169.0", "66.211.170.0", "66.211.171.0",
        "66.211.172.0", "66.211.173.0", "66.211.174.0", "66.211.175.0",
        "173.0.84.0", "173.0.85.0", "173.0.86.0", "173.0.87.0",
        "173.0.88.0", "173.0.89.0", "173.0.90.0", "173.0.91.0"
    );
    
    private final PaymentService paymentService;
    private final PayPalPaymentProcessor paypalProcessor;
    private final TransactionAuditService auditService;
    private final IdempotencyService idempotencyService;
    private final PaymentProviderFallbackService fallbackService;
    private final RestTemplate restTemplate;

    @Value("${paypal.ipn.verify-url:https://ipnpb.paypal.com/cgi-bin/webscr}")
    private String paypalIPNVerifyUrl;

    @Value("${paypal.sandbox:false}")
    private boolean paypalSandbox;

    @Value("${paypal.ipn.fallback-enabled:true}")
    private boolean fallbackEnabled;
    
    /**
     * CRITICAL SECURITY: PayPal IPN endpoint with comprehensive validation
     */
    @PostMapping("/ipn")
    @Transactional
    public ResponseEntity<String> handlePayPalIPN(
            HttpServletRequest request,
            @RequestBody String rawPayload) {
        
        try {
            // 1. CRITICAL SECURITY: Verify source IP address
            String clientIp = getClientIpAddress(request);
            if (!isValidPayPalIP(clientIp)) {
                log.error("SECURITY VIOLATION: PayPal IPN from unauthorized IP: {}", clientIp);
                
                auditService.auditSecurityEvent(
                    "system",
                    "PAYPAL_IP_VIOLATION",
                    "REJECTED",
                    clientIp,
                    request.getHeader("User-Agent"),
                    Map.of(
                        "provider", "PAYPAL",
                        "expectedIPRange", "64.4.240.0-66.211.175.0",
                        "actualIP", clientIp
                    )
                );
                
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Unauthorized source IP");
            }
            
            log.info("SECURITY: PayPal IPN IP validation passed: {}", clientIp);
            
            // 2. Extract IPN parameters from raw payload
            Map<String, String> ipnParams = parseIPNParameters(rawPayload);
            String transactionId = ipnParams.get("txn_id");
            String paymentStatus = ipnParams.get("payment_status");
            String idempotencyKey = "paypal-ipn-" + transactionId;
            
            log.info("SECURITY: Processing PayPal IPN: txnId={}, status={}, from IP={}", 
                    transactionId, paymentStatus, clientIp);
            
            // 3. CRITICAL SECURITY: Verify IPN authenticity with PayPal
            if (!verifyIPNWithPayPal(rawPayload)) {
                log.error("SECURITY VIOLATION: Invalid PayPal IPN verification from IP: {}", 
                        clientIp);
                
                // Audit security violation
                auditService.auditSecurityEvent(
                    "system",
                    "PAYPAL_IPN_VERIFICATION_FAILED",
                    "REJECTED",
                    clientIp,
                    request.getHeader("User-Agent"),
                    Map.of(
                        "provider", "PAYPAL",
                        "transactionId", transactionId != null ? transactionId : "unknown",
                        "payloadHash", Integer.toString(rawPayload.hashCode())
                    )
                );
                
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("IPN verification failed");
            }
            
            // 4. CRITICAL: Use idempotency to prevent duplicate processing
            return idempotencyService.executeIdempotentWithPersistence(
                "paypal-ipn-service",
                "process-ipn",
                idempotencyKey,
                () -> processPayPalIPNEvent(ipnParams, request),
                Duration.ofHours(24)
            );
            
        } catch (Exception e) {
            log.error("CRITICAL: Error processing PayPal IPN", e);
            
            // Audit IPN processing failure
            auditService.auditSecurityEvent(
                "system",
                "PAYPAL_IPN_PROCESSING_ERROR",
                "FAILED",
                request.getRemoteAddr(),
                request.getHeader("User-Agent"),
                Map.of(
                    "provider", "PAYPAL",
                    "error", e.getMessage(),
                    "payloadLength", rawPayload.length()
                )
            );
            
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("IPN processing failed");
        }
    }
    
    private String getClientIpAddress(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        
        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isEmpty()) {
            return xRealIp.trim();
        }
        
        return request.getRemoteAddr();
    }
    
    private boolean isValidPayPalIP(String clientIp) {
        if (clientIp == null || clientIp.isEmpty()) {
            return false;
        }
        
        return PAYPAL_WEBHOOK_IPS.stream()
                .anyMatch(allowedIp -> clientIp.startsWith(allowedIp.substring(0, allowedIp.lastIndexOf('.'))));
    }
    
    /**
     * Process individual PayPal IPN events with comprehensive handling
     */
    private ResponseEntity<String> processPayPalIPNEvent(Map<String, String> ipnParams, 
                                                        HttpServletRequest request) {
        
        String paymentStatus = ipnParams.get("payment_status");
        String transactionId = ipnParams.get("txn_id");
        String transactionType = ipnParams.get("txn_type");
        
        try {
            // Validate required parameters
            validateIPNParameters(ipnParams);
            
            switch (paymentStatus.toLowerCase()) {
                case "completed":
                    handlePaymentCompleted(ipnParams);
                    break;
                    
                case "pending":
                    handlePaymentPending(ipnParams);
                    break;
                    
                case "failed":
                    handlePaymentFailed(ipnParams);
                    break;
                    
                case "denied":
                    handlePaymentDenied(ipnParams);
                    break;
                    
                case "expired":
                    handlePaymentExpired(ipnParams);
                    break;
                    
                case "refunded":
                    handlePaymentRefunded(ipnParams);
                    break;
                    
                case "partially_refunded":
                    handlePaymentPartiallyRefunded(ipnParams);
                    break;
                    
                case "reversed":
                    handlePaymentReversed(ipnParams);
                    break;
                    
                case "canceled_reversal":
                    handleCanceledReversal(ipnParams);
                    break;
                    
                default:
                    log.warn("PAYPAL: Unhandled payment status: {} for transaction: {}", 
                            paymentStatus, transactionId);
                    return ResponseEntity.ok("Payment status not handled");
            }
            
            // Handle transaction type specific logic
            handleTransactionType(transactionType, ipnParams);
            
            log.info("SECURITY: Successfully processed PayPal IPN: txnId={}, status={}", 
                    transactionId, paymentStatus);
            
            return ResponseEntity.ok("IPN processed successfully");
            
        } catch (Exception e) {
            log.error("CRITICAL: Error processing PayPal IPN event: txnId={}, status={}", 
                    transactionId, paymentStatus, e);
            throw new RuntimeException("IPN event processing failed", e);
        }
    }
    
    /**
     * CRITICAL SECURITY: Verify IPN authenticity with PayPal servers
     */
    private boolean verifyIPNWithPayPal(String rawPayload) {
        try {
            // Prepare verification request
            String verifyUrl = paypalSandbox ? 
                "https://ipnpb.sandbox.paypal.com/cgi-bin/webscr" : 
                paypalIPNVerifyUrl;
            
            // Add cmd=_notify-validate to the original payload
            String verificationPayload = "cmd=_notify-validate&" + rawPayload;
            
            // Send POST request to PayPal for verification
            URL url = new URL(verifyUrl);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
            connection.setRequestProperty("Content-Length", String.valueOf(verificationPayload.length()));
            
            // Send verification payload
            connection.getOutputStream().write(verificationPayload.getBytes("UTF-8"));
            
            // Read response
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(connection.getInputStream()))) {
                String response = reader.lines().collect(Collectors.joining("\n"));
                
                // Check verification response
                boolean verified = "VERIFIED".equals(response);
                
                if (!verified) {
                    log.error("SECURITY: PayPal IPN verification failed. Response: {}", response);
                }
            }
            
            return verified;
            
        } catch (Exception e) {
            log.error("CRITICAL: Error verifying PayPal IPN", e);
            return false;
        }
    }
    
    /**
     * Parse IPN parameters from raw URL-encoded payload
     */
    private Map<String, String> parseIPNParameters(String rawPayload) {
        Map<String, String> params = new HashMap<>();
        
        if (rawPayload != null && !rawPayload.isEmpty()) {
            String[] pairs = rawPayload.split("&");
            for (String pair : pairs) {
                String[] keyValue = pair.split("=", 2);
                if (keyValue.length == 2) {
                    try {
                        String key = java.net.URLDecoder.decode(keyValue[0], "UTF-8");
                        String value = java.net.URLDecoder.decode(keyValue[1], "UTF-8");
                        params.put(key, value);
                    } catch (Exception e) {
                        log.warn("Failed to decode IPN parameter: {}", pair, e);
                    }
                }
            }
        }
        
        return params;
    }
    
    /**
     * Validate required IPN parameters
     */
    private void validateIPNParameters(Map<String, String> ipnParams) {
        String[] requiredParams = {
            "payment_status", "txn_id", "receiver_email", "payer_email"
        };
        
        for (String param : requiredParams) {
            if (!ipnParams.containsKey(param) || ipnParams.get(param).isEmpty()) {
                throw new IllegalArgumentException("Missing required IPN parameter: " + param);
            }
        }
    }
    
    /**
     * Handle completed payment
     */
    private void handlePaymentCompleted(Map<String, String> ipnParams) {
        String transactionId = ipnParams.get("txn_id");
        String parentTransactionId = ipnParams.get("parent_txn_id");
        String grossAmount = ipnParams.get("mc_gross");
        String currency = ipnParams.get("mc_currency");
        
        log.info("PAYPAL: Payment completed: txnId={}, amount={} {}", 
                transactionId, grossAmount, currency);
        
        // Update payment status
        paymentService.updatePaymentStatus(transactionId, "COMPLETED");
        
        // Audit successful payment
        auditService.auditPaymentOperation(
            transactionId,
            "PAYPAL",
            "PAYMENT_COMPLETED",
            new BigDecimal(grossAmount),
            currency,
            "COMPLETED",
            "paypal",
            createAuditMetadata(ipnParams)
        );
    }
    
    /**
     * Handle pending payment
     */
    private void handlePaymentPending(Map<String, String> ipnParams) {
        String transactionId = ipnParams.get("txn_id");
        String pendingReason = ipnParams.get("pending_reason");
        String grossAmount = ipnParams.get("mc_gross");
        String currency = ipnParams.get("mc_currency");
        
        log.info("PAYPAL: Payment pending: txnId={}, reason={}", transactionId, pendingReason);
        
        paymentService.updatePaymentStatus(transactionId, "PENDING");
        
        // Audit pending payment
        auditService.auditPaymentOperation(
            transactionId,
            "PAYPAL",
            "PAYMENT_PENDING",
            new BigDecimal(grossAmount),
            currency,
            "PENDING",
            "paypal",
            createAuditMetadata(ipnParams, Map.of("pendingReason", pendingReason))
        );
    }
    
    /**
     * Handle failed payment with intelligent fallback
     */
    private void handlePaymentFailed(Map<String, String> ipnParams) {
        String transactionId = ipnParams.get("txn_id");
        String reasonCode = ipnParams.get("reason_code");
        String grossAmount = ipnParams.get("mc_gross");
        String currency = ipnParams.get("mc_currency");

        log.warn("PAYPAL: Payment failed: txnId={}, reason={}", transactionId, reasonCode);

        // Attempt fallback to alternative provider if enabled and failure is retryable
        if (fallbackEnabled && isRetryablePayPalFailure(reasonCode)) {
            try {
                log.info("Attempting fallback for failed PayPal payment: {}", transactionId);

                // Create payment request from failed PayPal payment
                PaymentRequest fallbackRequest = createPayPalFallbackRequest(ipnParams);

                // Attempt payment with fallback service (excluding PayPal)
                PaymentResult fallbackResult = fallbackService.processPaymentWithFallback(fallbackRequest);

                if (fallbackResult.isSuccessful()) {
                    log.info("FALLBACK SUCCESS: Payment recovered with provider {} after PayPal failure",
                            fallbackResult.getProvider());

                    // Update payment with fallback provider details
                    paymentService.updatePaymentWithFallbackProvider(
                            transactionId,
                            fallbackResult.getProvider(),
                            fallbackResult.getTransactionId());

                    // Audit successful fallback
                    auditService.auditPaymentOperation(
                        transactionId,
                        fallbackResult.getProvider(),
                        "PAYMENT_FALLBACK_SUCCESS",
                        new BigDecimal(grossAmount),
                        currency,
                        "COMPLETED",
                        "system",
                        createAuditMetadata(ipnParams, Map.of(
                            "originalProvider", "PAYPAL",
                            "fallbackProvider", fallbackResult.getProvider(),
                            "paypalFailureReason", reasonCode
                        ))
                    );

                    return; // Success - no need to mark as failed
                }
            } catch (Exception fallbackEx) {
                log.error("Fallback failed for PayPal payment: {}", transactionId, fallbackEx);
                // Continue to mark as failed
            }
        }

        // Update payment status as failed (either fallback disabled or failed)
        paymentService.markPaymentFailed(transactionId, "PayPal payment failed: " + reasonCode);

        // Audit failed payment
        auditService.auditPaymentOperation(
            transactionId,
            "PAYPAL",
            "PAYMENT_FAILED",
            new BigDecimal(grossAmount),
            currency,
            "FAILED",
            "paypal",
            createAuditMetadata(ipnParams, Map.of(
                "reasonCode", reasonCode,
                "fallbackAttempted", fallbackEnabled
            ))
        );
    }

    /**
     * Check if PayPal failure is retryable with different provider
     */
    private boolean isRetryablePayPalFailure(String reasonCode) {
        if (reasonCode == null) return false;

        // Non-retryable failures (customer/payment method issues)
        Set<String> nonRetryableReasons = Set.of(
            "insufficient_funds",
            "invalid_account",
            "account_closed",
            "fraud",
            "buyer_complaint"
        );

        // Retryable failures (system/provider issues)
        Set<String> retryableReasons = Set.of(
            "processing_error",
            "temporary_hold",
            "regulatory_review",
            "system_error"
        );

        return retryableReasons.contains(reasonCode) &&
               !nonRetryableReasons.contains(reasonCode);
    }

    /**
     * Create fallback payment request from failed PayPal payment
     */
    private PaymentRequest createPayPalFallbackRequest(Map<String, String> ipnParams) {
        return PaymentRequest.builder()
                .paymentId(ipnParams.get("txn_id"))
                .amount(new BigDecimal(ipnParams.get("mc_gross")))
                .currency(ipnParams.get("mc_currency"))
                .customerId(ipnParams.get("payer_id"))
                .paymentType("DIGITAL_WALLET")
                .description(ipnParams.get("item_name"))
                .metadata(Map.of(
                    "original_provider", "paypal",
                    "payer_email", ipnParams.get("payer_email"),
                    "receiver_email", ipnParams.get("receiver_email")
                ))
                .excludeProviders(Set.of("paypal")) // Exclude PayPal from fallback
                .build();
    }
    
    /**
     * Handle denied payment
     */
    private void handlePaymentDenied(Map<String, String> ipnParams) {
        String transactionId = ipnParams.get("txn_id");
        String reasonCode = ipnParams.get("reason_code");
        String grossAmount = ipnParams.get("mc_gross");
        String currency = ipnParams.get("mc_currency");
        
        log.warn("PAYPAL: Payment denied: txnId={}, reason={}", transactionId, reasonCode);
        
        paymentService.markPaymentFailed(transactionId, "PayPal payment denied: " + reasonCode);
        
        // Audit denied payment
        auditService.auditPaymentOperation(
            transactionId,
            "PAYPAL",
            "PAYMENT_DENIED",
            new BigDecimal(grossAmount),
            currency,
            "DENIED",
            "paypal",
            createAuditMetadata(ipnParams, Map.of("reasonCode", reasonCode))
        );
    }
    
    /**
     * Handle expired payment
     */
    private void handlePaymentExpired(Map<String, String> ipnParams) {
        String transactionId = ipnParams.get("txn_id");
        String grossAmount = ipnParams.get("mc_gross");
        String currency = ipnParams.get("mc_currency");
        
        log.info("PAYPAL: Payment expired: txnId={}", transactionId);
        
        paymentService.updatePaymentStatus(transactionId, "EXPIRED");
        
        // Audit expired payment
        auditService.auditPaymentOperation(
            transactionId,
            "PAYPAL",
            "PAYMENT_EXPIRED",
            new BigDecimal(grossAmount),
            currency,
            "EXPIRED",
            "paypal",
            createAuditMetadata(ipnParams)
        );
    }
    
    /**
     * Handle refunded payment
     */
    private void handlePaymentRefunded(Map<String, String> ipnParams) {
        String transactionId = ipnParams.get("txn_id");
        String parentTransactionId = ipnParams.get("parent_txn_id");
        String grossAmount = ipnParams.get("mc_gross");
        String currency = ipnParams.get("mc_currency");
        
        log.info("PAYPAL: Payment refunded: txnId={}, parentTxnId={}", transactionId, parentTransactionId);
        
        paymentService.updateRefundStatus(transactionId, parentTransactionId, "COMPLETED");
        
        // Audit refund
        auditService.auditPaymentOperation(
            parentTransactionId != null ? parentTransactionId : transactionId,
            "PAYPAL",
            "PAYMENT_REFUNDED",
            new BigDecimal(grossAmount).abs(), // Refund amounts are negative, make positive for audit
            currency,
            "REFUNDED",
            "paypal",
            createAuditMetadata(ipnParams, Map.of("refundTransactionId", transactionId))
        );
    }
    
    /**
     * Handle partially refunded payment
     */
    private void handlePaymentPartiallyRefunded(Map<String, String> ipnParams) {
        String transactionId = ipnParams.get("txn_id");
        String parentTransactionId = ipnParams.get("parent_txn_id");
        String grossAmount = ipnParams.get("mc_gross");
        String currency = ipnParams.get("mc_currency");
        
        log.info("PAYPAL: Payment partially refunded: txnId={}, parentTxnId={}", 
                transactionId, parentTransactionId);
        
        paymentService.updateRefundStatus(transactionId, parentTransactionId, "PARTIAL");
        
        // Audit partial refund
        auditService.auditPaymentOperation(
            parentTransactionId != null ? parentTransactionId : transactionId,
            "PAYPAL",
            "PAYMENT_PARTIALLY_REFUNDED",
            new BigDecimal(grossAmount).abs(),
            currency,
            "PARTIALLY_REFUNDED",
            "paypal",
            createAuditMetadata(ipnParams, Map.of("refundTransactionId", transactionId))
        );
    }
    
    /**
     * Handle payment reversal (chargeback)
     */
    private void handlePaymentReversed(Map<String, String> ipnParams) {
        String transactionId = ipnParams.get("txn_id");
        String parentTransactionId = ipnParams.get("parent_txn_id");
        String reasonCode = ipnParams.get("reason_code");
        String grossAmount = ipnParams.get("mc_gross");
        String currency = ipnParams.get("mc_currency");
        
        log.warn("PAYPAL: Payment reversed (chargeback): txnId={}, parentTxnId={}, reason={}", 
                transactionId, parentTransactionId, reasonCode);
        
        paymentService.createChargeback(transactionId, parentTransactionId);
        
        // Audit chargeback
        auditService.auditPaymentOperation(
            parentTransactionId != null ? parentTransactionId : transactionId,
            "PAYPAL",
            "PAYMENT_REVERSED",
            new BigDecimal(grossAmount).abs(),
            currency,
            "REVERSED",
            "paypal",
            createAuditMetadata(ipnParams, Map.of(
                "reasonCode", reasonCode,
                "chargebackTransactionId", transactionId
            ))
        );
    }
    
    /**
     * Handle canceled reversal
     */
    private void handleCanceledReversal(Map<String, String> ipnParams) {
        String transactionId = ipnParams.get("txn_id");
        String parentTransactionId = ipnParams.get("parent_txn_id");
        String grossAmount = ipnParams.get("mc_gross");
        String currency = ipnParams.get("mc_currency");
        
        log.info("PAYPAL: Reversal canceled: txnId={}, parentTxnId={}", 
                transactionId, parentTransactionId);
        
        // Update chargeback status
        paymentService.updateDisputeStatus(transactionId, "CANCELED");
        
        // Audit canceled reversal
        auditService.auditPaymentOperation(
            parentTransactionId != null ? parentTransactionId : transactionId,
            "PAYPAL",
            "REVERSAL_CANCELED",
            new BigDecimal(grossAmount),
            currency,
            "REVERSAL_CANCELED",
            "paypal",
            createAuditMetadata(ipnParams)
        );
    }
    
    /**
     * Handle transaction type specific logic
     */
    private void handleTransactionType(String transactionType, Map<String, String> ipnParams) {
        if (transactionType == null) return;
        
        switch (transactionType.toLowerCase()) {
            case "recurring_payment":
                handleRecurringPayment(ipnParams);
                break;
                
            case "subscr_signup":
                handleSubscriptionSignup(ipnParams);
                break;
                
            case "subscr_payment":
                handleSubscriptionPayment(ipnParams);
                break;
                
            case "subscr_cancel":
                handleSubscriptionCancel(ipnParams);
                break;
                
            case "subscr_eot":
                handleSubscriptionEndOfTerm(ipnParams);
                break;
                
            case "mp_cancel":
                handleMassPayCancel(ipnParams);
                break;
                
            default:
                log.debug("PAYPAL: Transaction type not specifically handled: {}", transactionType);
        }
    }
    
    // Transaction type handlers
    
    private void handleRecurringPayment(Map<String, String> ipnParams) {
        String recurringPaymentId = ipnParams.get("recurring_payment_id");
        log.info("PAYPAL: Recurring payment: {}", recurringPaymentId);
        // Handle recurring payment logic
    }
    
    private void handleSubscriptionSignup(Map<String, String> ipnParams) {
        String subscriptionId = ipnParams.get("subscr_id");
        log.info("PAYPAL: Subscription signup: {}", subscriptionId);
        // Handle subscription signup logic
    }
    
    private void handleSubscriptionPayment(Map<String, String> ipnParams) {
        String subscriptionId = ipnParams.get("subscr_id");
        log.info("PAYPAL: Subscription payment: {}", subscriptionId);
        // Handle subscription payment logic
    }
    
    private void handleSubscriptionCancel(Map<String, String> ipnParams) {
        String subscriptionId = ipnParams.get("subscr_id");
        log.info("PAYPAL: Subscription canceled: {}", subscriptionId);
        // Handle subscription cancellation logic
    }
    
    private void handleSubscriptionEndOfTerm(Map<String, String> ipnParams) {
        String subscriptionId = ipnParams.get("subscr_id");
        log.info("PAYPAL: Subscription end of term: {}", subscriptionId);
        // Handle subscription end of term logic
    }
    
    private void handleMassPayCancel(Map<String, String> ipnParams) {
        String massPayId = ipnParams.get("masspay_txn_id");
        log.info("PAYPAL: Mass pay canceled: {}", massPayId);
        paymentService.cancelMassPayment(massPayId);
    }
    
    /**
     * Create audit metadata from IPN parameters
     */
    private Map<String, Object> createAuditMetadata(Map<String, String> ipnParams) {
        return createAuditMetadata(ipnParams, new HashMap<>());
    }
    
    private Map<String, Object> createAuditMetadata(Map<String, String> ipnParams, 
                                                   Map<String, String> additionalData) {
        Map<String, Object> metadata = new HashMap<>(additionalData);
        
        // Add key PayPal parameters to metadata
        String[] auditParams = {
            "payer_email", "receiver_email", "txn_type", "payment_type",
            "payment_date", "first_name", "last_name", "address_status",
            "payer_status", "protection_eligibility", "item_name", "item_number",
            "custom", "invoice", "notify_version", "verify_sign"
        };
        
        for (String param : auditParams) {
            if (ipnParams.containsKey(param)) {
                metadata.put(param, ipnParams.get(param));
            }
        }
        
        return metadata;
    }