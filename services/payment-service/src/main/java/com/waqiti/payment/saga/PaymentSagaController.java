package com.waqiti.payment.saga;

import com.waqiti.common.saga.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * REST controller for managing payment sagas
 */
@RestController
@RequestMapping("/api/v1/sagas")
@Slf4j
@RequiredArgsConstructor
public class PaymentSagaController {

    private final SagaOrchestrator sagaOrchestrator;
    private final PaymentSagaDefinitions sagaDefinitions;
    private final SagaMetricsService metricsService;

    /**
     * Start a P2P transfer saga
     */
    @PostMapping("/p2p-transfer")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN', 'SYSTEM')")
    public CompletableFuture<ResponseEntity<SagaResult>> startP2PTransfer(@RequestBody P2PTransferRequest request) {
        log.info("Starting P2P transfer saga: from={}, to={}, amount={}", 
            request.getFromUserId(), request.getToUserId(), request.getAmount());
        
        try {
            // Validate request
            validateP2PTransferRequest(request);
            
            // Create saga definition
            SagaDefinition sagaDefinition = sagaDefinitions.createP2PTransferSaga();
            
            // Prepare initial data
            Map<String, Object> initialData = Map.of(
                "fromUserId", request.getFromUserId(),
                "toUserId", request.getToUserId(),
                "amount", request.getAmount(),
                "currency", request.getCurrency(),
                "description", request.getDescription(),
                "reference", request.getReference(),
                "requestId", request.getRequestId()
            );
            
            // Start saga execution
            return sagaOrchestrator.executeSaga(sagaDefinition, initialData)
                .thenApply(result -> {
                    log.info("P2P transfer saga started: sagaId={}, status={}", 
                        result.getSagaId(), result.getStatus());
                    return ResponseEntity.ok(result);
                })
                .exceptionally(throwable -> {
                    log.error("Failed to start P2P transfer saga", throwable);
                    return ResponseEntity.internalServerError().build();
                });
                
        } catch (IllegalArgumentException e) {
            log.warn("Invalid P2P transfer request: {}", e.getMessage());
            return CompletableFuture.completedFuture(ResponseEntity.badRequest().build());
        } catch (Exception e) {
            log.error("Error starting P2P transfer saga", e);
            return CompletableFuture.completedFuture(ResponseEntity.internalServerError().build());
        }
    }

    /**
     * Start a virtual card payment saga
     */
    @PostMapping("/card-payment")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN', 'SYSTEM')")
    public CompletableFuture<ResponseEntity<SagaResult>> startCardPayment(@RequestBody CardPaymentRequest request) {
        log.info("Starting card payment saga: cardId={}, merchantId={}, amount={}", 
            request.getCardId(), request.getMerchantId(), request.getAmount());
        
        try {
            // Validate request
            validateCardPaymentRequest(request);
            
            // Create saga definition
            SagaDefinition sagaDefinition = sagaDefinitions.createVirtualCardPaymentSaga();
            
            // Prepare initial data
            Map<String, Object> initialData = Map.of(
                "cardId", request.getCardId(),
                "merchantId", request.getMerchantId(),
                "amount", request.getAmount(),
                "currency", request.getCurrency(),
                "description", request.getDescription(),
                "merchantReference", request.getMerchantReference(),
                "requestId", request.getRequestId()
            );
            
            // Start saga execution
            return sagaOrchestrator.executeSaga(sagaDefinition, initialData)
                .thenApply(result -> {
                    log.info("Card payment saga started: sagaId={}, status={}", 
                        result.getSagaId(), result.getStatus());
                    return ResponseEntity.ok(result);
                })
                .exceptionally(throwable -> {
                    log.error("Failed to start card payment saga", throwable);
                    return ResponseEntity.internalServerError().build();
                });
                
        } catch (IllegalArgumentException e) {
            log.warn("Invalid card payment request: {}", e.getMessage());
            return CompletableFuture.completedFuture(ResponseEntity.badRequest().build());
        } catch (Exception e) {
            log.error("Error starting card payment saga", e);
            return CompletableFuture.completedFuture(ResponseEntity.internalServerError().build());
        }
    }

    /**
     * Start an account top-up saga
     */
    @PostMapping("/account-topup")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN', 'SYSTEM')")
    public CompletableFuture<ResponseEntity<SagaResult>> startAccountTopUp(@RequestBody AccountTopUpRequest request) {
        log.info("Starting account top-up saga: userId={}, amount={}, source={}", 
            request.getUserId(), request.getAmount(), request.getPaymentSource());
        
        try {
            // Validate request
            validateAccountTopUpRequest(request);
            
            // Create saga definition
            SagaDefinition sagaDefinition = sagaDefinitions.createAccountTopUpSaga();
            
            // Prepare initial data
            Map<String, Object> initialData = Map.of(
                "userId", request.getUserId(),
                "amount", request.getAmount(),
                "currency", request.getCurrency(),
                "paymentSource", request.getPaymentSource(),
                "paymentMethodId", request.getPaymentMethodId(),
                "requestId", request.getRequestId()
            );
            
            // Start saga execution
            return sagaOrchestrator.executeSaga(sagaDefinition, initialData)
                .thenApply(result -> {
                    log.info("Account top-up saga started: sagaId={}, status={}", 
                        result.getSagaId(), result.getStatus());
                    return ResponseEntity.ok(result);
                })
                .exceptionally(throwable -> {
                    log.error("Failed to start account top-up saga", throwable);
                    return ResponseEntity.internalServerError().build();
                });
                
        } catch (IllegalArgumentException e) {
            log.warn("Invalid account top-up request: {}", e.getMessage());
            return CompletableFuture.completedFuture(ResponseEntity.badRequest().build());
        } catch (Exception e) {
            log.error("Error starting account top-up saga", e);
            return CompletableFuture.completedFuture(ResponseEntity.internalServerError().build());
        }
    }

    /**
     * Start a currency exchange saga
     */
    @PostMapping("/currency-exchange")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN', 'SYSTEM')")
    public CompletableFuture<ResponseEntity<SagaResult>> startCurrencyExchange(@RequestBody CurrencyExchangeRequest request) {
        log.info("Starting currency exchange saga: userId={}, from={}, to={}, amount={}", 
            request.getUserId(), request.getFromCurrency(), request.getToCurrency(), request.getAmount());
        
        try {
            // Validate request
            validateCurrencyExchangeRequest(request);
            
            // Create saga definition
            SagaDefinition sagaDefinition = sagaDefinitions.createCurrencyExchangeSaga();
            
            // Prepare initial data
            Map<String, Object> initialData = Map.of(
                "userId", request.getUserId(),
                "fromCurrency", request.getFromCurrency(),
                "toCurrency", request.getToCurrency(),
                "amount", request.getAmount(),
                "fromWalletId", request.getFromWalletId(),
                "toWalletId", request.getToWalletId(),
                "requestId", request.getRequestId()
            );
            
            // Start saga execution
            return sagaOrchestrator.executeSaga(sagaDefinition, initialData)
                .thenApply(result -> {
                    log.info("Currency exchange saga started: sagaId={}, status={}", 
                        result.getSagaId(), result.getStatus());
                    return ResponseEntity.ok(result);
                })
                .exceptionally(throwable -> {
                    log.error("Failed to start currency exchange saga", throwable);
                    return ResponseEntity.internalServerError().build();
                });
                
        } catch (IllegalArgumentException e) {
            log.warn("Invalid currency exchange request: {}", e.getMessage());
            return CompletableFuture.completedFuture(ResponseEntity.badRequest().build());
        } catch (Exception e) {
            log.error("Error starting currency exchange saga", e);
            return CompletableFuture.completedFuture(ResponseEntity.internalServerError().build());
        }
    }

    /**
     * Get saga status
     */
    @GetMapping("/{sagaId}/status")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN', 'SUPPORT', 'SYSTEM')")
    public ResponseEntity<SagaInfo> getSagaStatus(@PathVariable String sagaId) {
        try {
            Optional<SagaInfo> sagaInfo = sagaOrchestrator.getSagaInfo(sagaId);
            
            if (sagaInfo.isPresent()) {
                return ResponseEntity.ok(sagaInfo.get());
            } else {
                return ResponseEntity.notFound().build();
            }
            
        } catch (Exception e) {
            log.error("Error getting saga status: sagaId=" + sagaId, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Cancel a saga
     */
    @PostMapping("/{sagaId}/cancel")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN', 'SYSTEM')")
    public CompletableFuture<ResponseEntity<Void>> cancelSaga(@PathVariable String sagaId, 
                                                            @RequestBody(required = false) CancelSagaRequest request) {
        String reason = request != null ? request.getReason() : "User requested cancellation";
        
        log.info("Cancelling saga: sagaId={}, reason={}", sagaId, reason);
        
        return sagaOrchestrator.cancelSaga(sagaId, reason)
            .thenApply(result -> {
                log.info("Saga cancellation initiated: sagaId={}", sagaId);
                return ResponseEntity.ok().build();
            })
            .exceptionally(throwable -> {
                log.error("Failed to cancel saga: sagaId=" + sagaId, throwable);
                return ResponseEntity.internalServerError().build();
            });
    }

    /**
     * List active sagas
     */
    @GetMapping("/active")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPPORT', 'SYSTEM')")
    public ResponseEntity<List<SagaInfo>> getActiveSagas() {
        try {
            List<SagaInfo> activeSagas = sagaOrchestrator.getActiveSagas();
            return ResponseEntity.ok(activeSagas);
            
        } catch (Exception e) {
            log.error("Error getting active sagas", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Get saga metrics
     */
    @GetMapping("/metrics")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPPORT', 'SYSTEM')")
    public ResponseEntity<SagaMetricsSummary> getSagaMetrics() {
        try {
            SagaMetricsSummary metrics = metricsService.getMetricsSummary();
            return ResponseEntity.ok(metrics);
            
        } catch (Exception e) {
            log.error("Error getting saga metrics", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Resume a saga (for recovery)
     */
    @PostMapping("/{sagaId}/resume")
    @PreAuthorize("hasAnyRole('ADMIN', 'SYSTEM')")
    public CompletableFuture<ResponseEntity<SagaResult>> resumeSaga(@PathVariable String sagaId) {
        log.info("Resuming saga: sagaId={}", sagaId);
        
        return sagaOrchestrator.resumeSaga(sagaId)
            .thenApply(result -> {
                log.info("Saga resume initiated: sagaId={}, status={}", sagaId, result.getStatus());
                return ResponseEntity.ok(result);
            })
            .exceptionally(throwable -> {
                log.error("Failed to resume saga: sagaId=" + sagaId, throwable);
                return ResponseEntity.internalServerError().build();
            });
    }

    // Private validation methods

    private void validateP2PTransferRequest(P2PTransferRequest request) {
        if (request.getFromUserId() == null || request.getFromUserId().trim().isEmpty()) {
            throw new IllegalArgumentException("From user ID is required");
        }
        if (request.getToUserId() == null || request.getToUserId().trim().isEmpty()) {
            throw new IllegalArgumentException("To user ID is required");
        }
        if (request.getAmount() == null || request.getAmount().compareTo(java.math.BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Amount must be positive");
        }
        if (request.getCurrency() == null || request.getCurrency().trim().isEmpty()) {
            throw new IllegalArgumentException("Currency is required");
        }
        if (request.getFromUserId().equals(request.getToUserId())) {
            throw new IllegalArgumentException("Cannot transfer to same user");
        }
    }

    private void validateCardPaymentRequest(CardPaymentRequest request) {
        if (request.getCardId() == null || request.getCardId().trim().isEmpty()) {
            throw new IllegalArgumentException("Card ID is required");
        }
        if (request.getMerchantId() == null || request.getMerchantId().trim().isEmpty()) {
            throw new IllegalArgumentException("Merchant ID is required");
        }
        if (request.getAmount() == null || request.getAmount().compareTo(java.math.BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Amount must be positive");
        }
        if (request.getCurrency() == null || request.getCurrency().trim().isEmpty()) {
            throw new IllegalArgumentException("Currency is required");
        }
    }

    private void validateAccountTopUpRequest(AccountTopUpRequest request) {
        if (request.getUserId() == null || request.getUserId().trim().isEmpty()) {
            throw new IllegalArgumentException("User ID is required");
        }
        if (request.getAmount() == null || request.getAmount().compareTo(java.math.BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Amount must be positive");
        }
        if (request.getCurrency() == null || request.getCurrency().trim().isEmpty()) {
            throw new IllegalArgumentException("Currency is required");
        }
        if (request.getPaymentMethodId() == null || request.getPaymentMethodId().trim().isEmpty()) {
            throw new IllegalArgumentException("Payment method ID is required");
        }
    }

    private void validateCurrencyExchangeRequest(CurrencyExchangeRequest request) {
        if (request.getUserId() == null || request.getUserId().trim().isEmpty()) {
            throw new IllegalArgumentException("User ID is required");
        }
        if (request.getAmount() == null || request.getAmount().compareTo(java.math.BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Amount must be positive");
        }
        if (request.getFromCurrency() == null || request.getFromCurrency().trim().isEmpty()) {
            throw new IllegalArgumentException("From currency is required");
        }
        if (request.getToCurrency() == null || request.getToCurrency().trim().isEmpty()) {
            throw new IllegalArgumentException("To currency is required");
        }
        if (request.getFromCurrency().equals(request.getToCurrency())) {
            throw new IllegalArgumentException("From and to currencies cannot be the same");
        }
    }
}

