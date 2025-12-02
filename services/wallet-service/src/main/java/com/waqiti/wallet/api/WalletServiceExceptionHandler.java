package com.waqiti.wallet.api;

import com.waqiti.common.exception.ErrorResponse;
import com.waqiti.common.exception.GlobalExceptionHandler;
import com.waqiti.wallet.domain.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;

/**
 * Wallet service specific exception handler extending the common global handler
 */
@RestControllerAdvice
@Slf4j
public class WalletServiceExceptionHandler extends GlobalExceptionHandler {

    @ExceptionHandler(WalletNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleWalletNotFound(WalletNotFoundException ex, WebRequest request) {
        log.error("Wallet not found", ex);
        return buildErrorResponse(HttpStatus.NOT_FOUND, ex.getMessage(), request);
    }

    @ExceptionHandler(InsufficientBalanceException.class)
    public ResponseEntity<ErrorResponse> handleInsufficientBalance(InsufficientBalanceException ex, WebRequest request) {
        log.error("Insufficient balance", ex);
        return buildErrorResponse(HttpStatus.BAD_REQUEST, ex.getMessage(), request);
    }

    @ExceptionHandler(WalletNotActiveException.class)
    public ResponseEntity<ErrorResponse> handleWalletNotActive(WalletNotActiveException ex, WebRequest request) {
        log.error("Wallet not active", ex);
        return buildErrorResponse(HttpStatus.BAD_REQUEST, ex.getMessage(), request);
    }

    @ExceptionHandler(TransactionFailedException.class)
    public ResponseEntity<ErrorResponse> handleTransactionFailed(TransactionFailedException ex, WebRequest request) {
        log.error("Transaction failed", ex);
        return buildErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR, ex.getMessage(), request);
    }

    @ExceptionHandler(ConcurrentModificationException.class)
    public ResponseEntity<ErrorResponse> handleConcurrentModification(ConcurrentModificationException ex, WebRequest request) {
        log.error("Concurrent modification", ex);
        return buildErrorResponse(HttpStatus.CONFLICT, ex.getMessage(), request);
    }
}