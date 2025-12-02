package com.waqiti.payment.client;

import com.waqiti.payment.client.dto.crypto.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.UUID;

/**
 * Fallback implementation for CryptoServiceClient
 *
 * Provides graceful degradation when crypto-service is unavailable.
 *
 * Behavior:
 * - Returns SERVICE_UNAVAILABLE (503) HTTP status
 * - Logs all fallback invocations for monitoring
 * - Preserves API contract for resilience
 * - Triggers alerts for DevOps team
 *
 * @author Waqiti Engineering Team
 * @version 2.0.0
 */
@Slf4j
@Component
public class CryptoServiceClientFallback implements CryptoServiceClient {

    private static final String SERVICE_UNAVAILABLE_MESSAGE =
            "Crypto service temporarily unavailable. Please try again later.";

    @Override
    public ResponseEntity<CryptoWalletResponse> createWallet(CreateCryptoWalletRequest request) {
        log.error("FALLBACK: createWallet called - crypto-service unavailable | currency={}",
                request != null ? request.getCurrency() : "null");
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(CryptoWalletResponse.unavailable(SERVICE_UNAVAILABLE_MESSAGE));
    }

    @Override
    public ResponseEntity<CryptoWalletResponse> getWallet(UUID walletId) {
        log.error("FALLBACK: getWallet called - crypto-service unavailable | walletId={}", walletId);
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(CryptoWalletResponse.unavailable(SERVICE_UNAVAILABLE_MESSAGE));
    }

    @Override
    public ResponseEntity<Page<CryptoWalletResponse>> getUserWallets(Pageable pageable) {
        log.error("FALLBACK: getUserWallets called - crypto-service unavailable");
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(new PageImpl<>(Collections.emptyList(), pageable, 0));
    }

    @Override
    public ResponseEntity<CryptoBalanceResponse> getTotalBalance() {
        log.error("FALLBACK: getTotalBalance called - crypto-service unavailable");
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(CryptoBalanceResponse.unavailable(SERVICE_UNAVAILABLE_MESSAGE));
    }

    @Override
    public ResponseEntity<CryptoAddressResponse> generateNewAddress(UUID walletId) {
        log.error("FALLBACK: generateNewAddress called - crypto-service unavailable | walletId={}", walletId);
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(CryptoAddressResponse.unavailable(SERVICE_UNAVAILABLE_MESSAGE));
    }

    @Override
    public ResponseEntity<CryptoTransactionInitiationResponse> initiateSendCrypto(
            SendCryptocurrencyRequest request) {
        log.error("FALLBACK: initiateSendCrypto called - crypto-service unavailable | toAddress={} | amount={}",
                request != null ? request.getToAddress() : "null",
                request != null ? request.getAmount() : "null");
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(CryptoTransactionInitiationResponse.unavailable(SERVICE_UNAVAILABLE_MESSAGE));
    }

    @Override
    public ResponseEntity<CryptoTransactionResponse> confirmSendCrypto(
            UUID transactionId, MfaConfirmationRequest request) {
        log.error("FALLBACK: confirmSendCrypto called - crypto-service unavailable | transactionId={}",
                transactionId);
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(CryptoTransactionResponse.unavailable(SERVICE_UNAVAILABLE_MESSAGE));
    }

    @Override
    public ResponseEntity<CryptoTransactionResponse> getTransaction(UUID transactionId) {
        log.error("FALLBACK: getTransaction called - crypto-service unavailable | transactionId={}",
                transactionId);
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(CryptoTransactionResponse.unavailable(SERVICE_UNAVAILABLE_MESSAGE));
    }

    @Override
    public ResponseEntity<Page<CryptoTransactionResponse>> getTransactions(
            Pageable pageable, String status, String currency) {
        log.error("FALLBACK: getTransactions called - crypto-service unavailable | status={} | currency={}",
                status, currency);
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(new PageImpl<>(Collections.emptyList(), pageable, 0));
    }

    @Override
    public ResponseEntity<CryptoTransactionResponse> buyCryptocurrency(BuyCryptocurrencyRequest request) {
        log.error("FALLBACK: buyCryptocurrency called - crypto-service unavailable | currency={} | amount={}",
                request != null ? request.getCurrency() : "null",
                request != null ? request.getAmount() : "null");
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(CryptoTransactionResponse.unavailable(SERVICE_UNAVAILABLE_MESSAGE));
    }

    @Override
    public ResponseEntity<CryptoTransactionResponse> sellCryptocurrency(SellCryptocurrencyRequest request) {
        log.error("FALLBACK: sellCryptocurrency called - crypto-service unavailable | currency={} | amount={}",
                request != null ? request.getCurrency() : "null",
                request != null ? request.getAmount() : "null");
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(CryptoTransactionResponse.unavailable(SERVICE_UNAVAILABLE_MESSAGE));
    }

    @Override
    public ResponseEntity<CryptoPriceResponse> getCurrentPrice(String currency, String fiatCurrency) {
        log.error("FALLBACK: getCurrentPrice called - crypto-service unavailable | currency={} | fiat={}",
                currency, fiatCurrency);
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(CryptoPriceResponse.unavailable(SERVICE_UNAVAILABLE_MESSAGE));
    }

    @Override
    public ResponseEntity<CryptoBalanceResponse> getWalletBalance(UUID walletId) {
        log.error("FALLBACK: getWalletBalance called - crypto-service unavailable | walletId={}", walletId);
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(CryptoBalanceResponse.unavailable(SERVICE_UNAVAILABLE_MESSAGE));
    }
}
