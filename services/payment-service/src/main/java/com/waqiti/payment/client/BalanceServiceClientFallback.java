package com.waqiti.payment.client;

import com.waqiti.payment.dto.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

/**
 * Fallback implementation for BalanceServiceClient
 * 
 * NOTE: This client is now redirected to wallet-service.
 * Fallback provides safe defaults when wallet-service is unavailable.
 * 
 * @author Waqiti Infrastructure Team
 * @version 2.0.0 - Updated for wallet-service integration
 * @since 2025-09-27
 */
@Slf4j
@Component
public class BalanceServiceClientFallback implements BalanceServiceClient {

    @Override
    public ResponseEntity<BalanceInfo> getBalance(String customerId, String currency) {
        log.warn("WalletService (Balance) fallback: getBalance for customer: {} currency: {}", customerId, currency);
        return ResponseEntity.ok(BalanceInfo.builder()
                .customerId(customerId)
                .currency(currency)
                .availableBalance(BigDecimal.ZERO)
                .totalBalance(BigDecimal.ZERO)
                .status("UNAVAILABLE")
                .build());
    }

    @Override
    public ResponseEntity<List<BalanceInfo>> getAllBalances(String customerId) {
        log.warn("WalletService (Balance) fallback: getAllBalances for customer: {}", customerId);
        return ResponseEntity.ok(Collections.emptyList());
    }

    @Override
    public ResponseEntity<BalanceReservationResult> reserveBalance(BalanceReservationRequest request) {
        log.warn("WalletService (Balance) fallback: reserveBalance for customer: {}", request.getCustomerId());
        return ResponseEntity.ok(BalanceReservationResult.failed(request.getCustomerId(), "Balance service unavailable"));
    }

    @Override
    public ResponseEntity<Void> releaseReservation(String reservationId) {
        log.warn("BalanceService fallback: releaseReservation for reservationId: {}", reservationId);
        return ResponseEntity.ok().build();
    }

    @Override
    public ResponseEntity<Void> confirmReservation(String reservationId) {
        log.warn("BalanceService fallback: confirmReservation for reservationId: {}", reservationId);
        return ResponseEntity.ok().build();
    }

    @Override
    public ResponseEntity<BalanceTransactionResult> debitAccount(String walletId, BalanceDebitRequest request) {
        log.warn("BalanceService fallback: debitAccount for walletId: {}", walletId);
        return ResponseEntity.ok(BalanceTransactionResult.builder()
                .success(false)
                .errorMessage("Wallet service unavailable")
                .build());
    }

    @Override
    public ResponseEntity<BalanceTransactionResult> creditAccount(String walletId, AccountCreditRequest request) {
        log.warn("BalanceService fallback: creditAccount for walletId: {}", walletId);
        return ResponseEntity.ok(BalanceTransactionResult.builder()
                .success(false)
                .errorMessage("Wallet service unavailable")
                .build());
    }

    @Override
    public ResponseEntity<BalanceTransferResult> transferFunds(BalanceTransferRequest request) {
        log.warn("BalanceService fallback: transferFunds from: {} to: {}", request.getFromCustomerId(), request.getToCustomerId());
        return ResponseEntity.ok(BalanceTransferResult.builder()
                .success(false)
                .errorMessage("Wallet service unavailable")
                .build());
    }

    @Override
    public ResponseEntity<BalanceSufficientResult> checkSufficientBalance(BalanceSufficientRequest request) {
        log.warn("BalanceService fallback: checkSufficientBalance for customer: {}", request.getCustomerId());
        return ResponseEntity.ok(BalanceSufficientResult.builder()
                .sufficient(false)
                .reason("Balance service unavailable")
                .build());
    }

    @Override
    public ResponseEntity<List<BalanceTransaction>> getBalanceHistory(String customerId, String currency, int page, int size) {
        log.warn("BalanceService fallback: getBalanceHistory for customer: {} currency: {}", customerId, currency);
        return ResponseEntity.ok(Collections.emptyList());
    }

    @Override
    public ResponseEntity<List<BalanceReservation>> getPendingReservations(String customerId) {
        log.warn("BalanceService fallback: getPendingReservations for customer: {}", customerId);
        return ResponseEntity.ok(Collections.emptyList());
    }

    @Override
    public ResponseEntity<BalanceSummary> getBalanceSummary(String customerId) {
        log.warn("BalanceService fallback: getBalanceSummary for customer: {}", customerId);
        return ResponseEntity.ok(BalanceSummary.builder()
                .customerId(customerId)
                .totalBalance(BigDecimal.ZERO)
                .availableBalance(BigDecimal.ZERO)
                .status("UNAVAILABLE")
                .build());
    }

    @Override
    public ResponseEntity<Void> freezeBalance(String customerId, BalanceFreezeRequest request) {
        log.warn("BalanceService fallback: freezeBalance for customer: {}", customerId);
        return ResponseEntity.ok().build();
    }

    @Override
    public ResponseEntity<Void> unfreezeBalance(String customerId, BalanceUnfreezeRequest request) {
        log.warn("BalanceService fallback: unfreezeBalance for customer: {}", customerId);
        return ResponseEntity.ok().build();
    }

    @Override
    public ResponseEntity<BigDecimal> getFrozenAmount(String customerId, String currency) {
        log.warn("BalanceService fallback: getFrozenAmount for customer: {} currency: {}", customerId, currency);
        return ResponseEntity.ok(BigDecimal.ZERO);
    }

    @Override
    public ResponseEntity<BalanceValidationResult> validateAccount(BalanceValidationRequest request) {
        log.warn("BalanceService fallback: validateAccount for customer: {}", request.getCustomerId());
        return ResponseEntity.ok(BalanceValidationResult.builder()
                .valid(false)
                .errorMessage("Wallet service unavailable")
                .build());
    }
}