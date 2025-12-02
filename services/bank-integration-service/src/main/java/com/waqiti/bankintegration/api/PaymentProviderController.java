package com.waqiti.bankintegration.api;

import com.waqiti.bankintegration.strategy.PayPalPaymentStrategy;
import com.waqiti.bankintegration.strategy.PlaidPaymentStrategy;
import com.waqiti.bankintegration.strategy.StripePaymentStrategy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/banking/providers")
@RequiredArgsConstructor
@Slf4j
public class PaymentProviderController {

    private final StripePaymentStrategy stripeStrategy;
    private final PayPalPaymentStrategy paypalStrategy;
    private final PlaidPaymentStrategy plaidStrategy;

    @PostMapping("/stripe/payments")
    @PreAuthorize("hasAnyRole('USER', 'SYSTEM')")
    public ResponseEntity<Map<String, Object>> processStripePayment(@RequestBody @Valid StripePaymentRequest request) {
        log.info("Processing Stripe payment for amount: {} {}", request.getAmount(), request.getCurrency());
        
        try {
            var paymentProvider = createPaymentProvider("STRIPE", request.getApiKey());
            var paymentRequest = createPaymentRequest(request);
            
            var response = stripeStrategy.processPayment(paymentProvider, paymentRequest);
            
            return ResponseEntity.ok(Map.of(
                "success", response.isSuccess(),
                "transactionId", response.getTransactionId(),
                "providerTransactionId", response.getProviderTransactionId(),
                "status", response.getStatus(),
                "message", response.getMessage()
            ));
        } catch (Exception e) {
            log.error("Stripe payment failed", e);
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "error", e.getMessage()
            ));
        }
    }

    @PostMapping("/paypal/payments")
    @PreAuthorize("hasAnyRole('USER', 'SYSTEM')")
    public ResponseEntity<Map<String, Object>> processPayPalPayment(@RequestBody @Valid PayPalPaymentRequest request) {
        log.info("Processing PayPal payment for amount: {} {}", request.getAmount(), request.getCurrency());
        
        try {
            var paymentProvider = createPaymentProvider("PAYPAL", request.getClientId(), request.getClientSecret());
            var paymentRequest = createPaymentRequest(request);
            
            var response = paypalStrategy.processPayment(paymentProvider, paymentRequest);
            
            return ResponseEntity.ok(Map.of(
                "success", response.isSuccess(),
                "transactionId", response.getTransactionId(),
                "providerTransactionId", response.getProviderTransactionId(),
                "status", response.getStatus(),
                "approvalUrl", response.getApprovalUrl(),
                "message", response.getMessage()
            ));
        } catch (Exception e) {
            log.error("PayPal payment failed", e);
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "error", e.getMessage()
            ));
        }
    }

    @PostMapping("/plaid/accounts/link")
    @PreAuthorize("hasAnyRole('USER', 'SYSTEM')")
    public ResponseEntity<Map<String, Object>> linkPlaidAccount(@RequestBody @Valid PlaidLinkRequest request) {
        log.info("Linking Plaid account for user: {}", request.getUserId());
        
        try {
            var paymentProvider = createPaymentProvider("PLAID", request.getClientId(), request.getSecret());
            
            var response = plaidStrategy.linkAccount(paymentProvider, request.getUserId(), request.getPublicToken());
            
            return ResponseEntity.ok(Map.of(
                "success", true,
                "accessToken", response.getAccessToken(),
                "itemId", response.getItemId(),
                "accounts", response.getAccounts()
            ));
        } catch (Exception e) {
            log.error("Plaid account linking failed", e);
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "error", e.getMessage()
            ));
        }
    }

    @GetMapping("/plaid/accounts/{userId}")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    public ResponseEntity<List<Map<String, Object>>> getPlaidAccounts(@PathVariable String userId) {
        log.info("Getting Plaid accounts for user: {}", userId);
        
        try {
            var accounts = plaidStrategy.getAccounts(userId);
            return ResponseEntity.ok(accounts);
        } catch (Exception e) {
            log.error("Failed to get Plaid accounts", e);
            return ResponseEntity.badRequest().body(List.of());
        }
    }

    @PostMapping("/plaid/payments")
    @PreAuthorize("hasAnyRole('USER', 'SYSTEM')")
    public ResponseEntity<Map<String, Object>> processPlaidPayment(@RequestBody @Valid PlaidPaymentRequest request) {
        log.info("Processing Plaid payment for amount: {} {}", request.getAmount(), request.getCurrency());
        
        try {
            var paymentProvider = createPaymentProvider("PLAID", request.getClientId(), request.getSecret());
            var paymentRequest = createPaymentRequest(request);
            
            var response = plaidStrategy.processPayment(paymentProvider, paymentRequest);
            
            return ResponseEntity.ok(Map.of(
                "success", response.isSuccess(),
                "transactionId", response.getTransactionId(),
                "providerTransactionId", response.getProviderTransactionId(),
                "status", response.getStatus(),
                "message", response.getMessage()
            ));
        } catch (Exception e) {
            log.error("Plaid payment failed", e);
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "error", e.getMessage()
            ));
        }
    }

    @PostMapping("/stripe/refunds")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    public ResponseEntity<Map<String, Object>> processStripeRefund(@RequestBody @Valid RefundRequest request) {
        log.info("Processing Stripe refund for transaction: {}", request.getTransactionId());
        
        try {
            var paymentProvider = createPaymentProvider("STRIPE", request.getApiKey());
            
            var response = stripeStrategy.refund(paymentProvider, request.getTransactionId(), request.getAmount(), request.getReason());
            
            return ResponseEntity.ok(Map.of(
                "success", response.isSuccess(),
                "refundId", response.getRefundId(),
                "status", response.getStatus(),
                "amount", response.getRefundedAmount(),
                "message", response.getMessage()
            ));
        } catch (Exception e) {
            log.error("Stripe refund failed", e);
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "error", e.getMessage()
            ));
        }
    }

    @PostMapping("/paypal/refunds")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    public ResponseEntity<Map<String, Object>> processPayPalRefund(@RequestBody @Valid RefundRequest request) {
        log.info("Processing PayPal refund for transaction: {}", request.getTransactionId());
        
        try {
            var paymentProvider = createPaymentProvider("PAYPAL", request.getClientId(), request.getClientSecret());
            
            var response = paypalStrategy.refund(paymentProvider, request.getTransactionId(), request.getAmount(), request.getReason());
            
            return ResponseEntity.ok(Map.of(
                "success", response.isSuccess(),
                "refundId", response.getRefundId(),
                "status", response.getStatus(),
                "amount", response.getRefundedAmount(),
                "message", response.getMessage()
            ));
        } catch (Exception e) {
            log.error("PayPal refund failed", e);
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "error", e.getMessage()
            ));
        }
    }

    @GetMapping("/stripe/status/{transactionId}")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    public ResponseEntity<Map<String, Object>> getStripePaymentStatus(
            @PathVariable String transactionId,
            @RequestParam String apiKey) {
        log.info("Checking Stripe payment status for: {}", transactionId);
        
        try {
            var paymentProvider = createPaymentProvider("STRIPE", apiKey);
            var status = stripeStrategy.checkStatus(paymentProvider, transactionId);
            
            return ResponseEntity.ok(Map.of(
                "transactionId", transactionId,
                "status", status.getStatus(),
                "amount", status.getAmount(),
                "currency", status.getCurrency(),
                "created", status.getCreatedAt(),
                "description", status.getDescription()
            ));
        } catch (Exception e) {
            log.error("Failed to check Stripe payment status", e);
            return ResponseEntity.badRequest().body(Map.of(
                "error", e.getMessage()
            ));
        }
    }

    @GetMapping("/paypal/status/{transactionId}")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    public ResponseEntity<Map<String, Object>> getPayPalPaymentStatus(
            @PathVariable String transactionId,
            @RequestParam String clientId,
            @RequestParam String clientSecret) {
        log.info("Checking PayPal payment status for: {}", transactionId);
        
        try {
            var paymentProvider = createPaymentProvider("PAYPAL", clientId, clientSecret);
            var status = paypalStrategy.checkStatus(paymentProvider, transactionId);
            
            return ResponseEntity.ok(Map.of(
                "transactionId", transactionId,
                "status", status.getStatus(),
                "amount", status.getAmount(),
                "currency", status.getCurrency(),
                "created", status.getCreatedAt(),
                "description", status.getDescription()
            ));
        } catch (Exception e) {
            log.error("Failed to check PayPal payment status", e);
            return ResponseEntity.badRequest().body(Map.of(
                "error", e.getMessage()
            ));
        }
    }

    @PostMapping("/stripe/cancel/{transactionId}")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    public ResponseEntity<Map<String, Object>> cancelStripePayment(
            @PathVariable String transactionId,
            @RequestBody @Valid CancelRequest request) {
        log.info("Cancelling Stripe payment: {}", transactionId);
        
        try {
            var paymentProvider = createPaymentProvider("STRIPE", request.getApiKey());
            
            var response = stripeStrategy.cancelPayment(paymentProvider, transactionId, request.getReason());
            
            return ResponseEntity.ok(Map.of(
                "success", response.isSuccess(),
                "transactionId", transactionId,
                "status", response.getStatus(),
                "message", response.getMessage()
            ));
        } catch (Exception e) {
            log.error("Failed to cancel Stripe payment", e);
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "error", e.getMessage()
            ));
        }
    }

    @GetMapping("/health")
    @PreAuthorize("hasAnyRole('ADMIN', 'SYSTEM')")
    public ResponseEntity<Map<String, Object>> getProvidersHealth() {
        log.info("Checking payment providers health");
        
        Map<String, Object> health = Map.of(
            "stripe", stripeStrategy.checkHealth(),
            "paypal", paypalStrategy.checkHealth(),
            "plaid", plaidStrategy.checkHealth(),
            "timestamp", System.currentTimeMillis()
        );
        
        return ResponseEntity.ok(health);
    }

    @GetMapping("/capabilities")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    public ResponseEntity<Map<String, Object>> getProviderCapabilities() {
        log.info("Getting payment provider capabilities");
        
        Map<String, Object> capabilities = Map.of(
            "stripe", Map.of(
                "supportedCurrencies", List.of("USD", "EUR", "GBP", "CAD", "AUD"),
                "features", List.of("payments", "refunds", "subscriptions", "marketplace"),
                "countries", List.of("US", "CA", "GB", "AU", "EU")
            ),
            "paypal", Map.of(
                "supportedCurrencies", List.of("USD", "EUR", "GBP", "CAD", "AUD", "JPY"),
                "features", List.of("payments", "refunds", "subscriptions"),
                "countries", List.of("US", "CA", "GB", "AU", "EU", "JP")
            ),
            "plaid", Map.of(
                "supportedCurrencies", List.of("USD"),
                "features", List.of("account_linking", "balance_check", "ach_payments"),
                "countries", List.of("US", "CA")
            )
        );
        
        return ResponseEntity.ok(capabilities);
    }

    // Helper methods
    private PaymentProvider createPaymentProvider(String type, String... credentials) {
        return PaymentProvider.builder()
            .type(type)
            .credentials(Map.of(
                "key1", credentials.length > 0 ? credentials[0] : "",
                "key2", credentials.length > 1 ? credentials[1] : ""
            ))
            .build();
    }

    private PaymentRequest createPaymentRequest(Object request) {
        // Implementation would map the specific request to PaymentRequest
        return PaymentRequest.builder()
            .build();
    }
}

