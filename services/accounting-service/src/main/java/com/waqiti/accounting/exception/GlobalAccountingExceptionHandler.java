package com.waqiti.accounting.exception;

import com.waqiti.common.dto.response.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Global exception handler for accounting service
 */
@RestControllerAdvice
@Slf4j
public class GlobalAccountingExceptionHandler {

    @ExceptionHandler(AccountingException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccountingException(AccountingException ex) {
        log.error("Accounting error: {} - {}", ex.getErrorCode(), ex.getMessage(), ex);
        return ApiResponse.<Void>error(HttpStatus.BAD_REQUEST, ex.getMessage())
            .toResponseEntity();
    }

    @ExceptionHandler(InsufficientBalanceException.class)
    public ResponseEntity<ApiResponse<Void>> handleInsufficientBalance(InsufficientBalanceException ex) {
        log.warn("Insufficient balance: account={}, available={}, required={}",
            ex.getAccountCode(), ex.getAvailableBalance(), ex.getRequiredAmount());
        return ApiResponse.<Void>error(HttpStatus.PAYMENT_REQUIRED, ex.getMessage())
            .toResponseEntity();
    }

    @ExceptionHandler(JournalEntryNotBalancedException.class)
    public ResponseEntity<ApiResponse<Void>> handleJournalNotBalanced(JournalEntryNotBalancedException ex) {
        log.error("Journal entry not balanced: entry={}, debits={}, credits={}, diff={}",
            ex.getEntryId(), ex.getTotalDebits(), ex.getTotalCredits(), ex.getDifference());
        return ApiResponse.<Void>error(HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage())
            .toResponseEntity();
    }

    @ExceptionHandler(AccountNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccountNotFound(AccountNotFoundException ex) {
        log.warn("Account not found: {}", ex.getAccountCode());
        return ApiResponse.<Void>notFound(ex.getMessage())
            .toResponseEntity();
    }

    @ExceptionHandler(FinancialPeriodClosedException.class)
    public ResponseEntity<ApiResponse<Void>> handlePeriodClosed(FinancialPeriodClosedException ex) {
        log.warn("Attempt to post to closed period: {}", ex.getPeriodName());
        return ApiResponse.<Void>error(HttpStatus.CONFLICT, ex.getMessage())
            .toResponseEntity();
    }

    @ExceptionHandler(ReconciliationException.class)
    public ResponseEntity<ApiResponse<Void>> handleReconciliation(ReconciliationException ex) {
        log.error("Reconciliation error: account={}, message={}",
            ex.getAccountCode(), ex.getMessage(), ex);
        return ApiResponse.<Void>error(HttpStatus.INTERNAL_SERVER_ERROR, ex.getMessage())
            .toResponseEntity();
    }

    @ExceptionHandler(SettlementException.class)
    public ResponseEntity<ApiResponse<Void>> handleSettlement(SettlementException ex) {
        log.error("Settlement error: settlementId={}, message={}",
            ex.getSettlementId(), ex.getMessage(), ex);
        return ApiResponse.<Void>error(HttpStatus.INTERNAL_SERVER_ERROR, ex.getMessage())
            .toResponseEntity();
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidationException(MethodArgumentNotValidException ex) {
        Map<String, List<String>> errors = new HashMap<>();

        ex.getBindingResult().getFieldErrors().forEach(error -> {
            String fieldName = error.getField();
            String errorMessage = error.getDefaultMessage();
            errors.computeIfAbsent(fieldName, k -> new java.util.ArrayList<>()).add(errorMessage);
        });

        log.warn("Validation failed: {}", errors);
        return ApiResponse.<Void>validationError(errors)
            .toResponseEntity();
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> handleIllegalArgument(IllegalArgumentException ex) {
        log.warn("Invalid argument: {}", ex.getMessage());
        return ApiResponse.<Void>badRequest(ex.getMessage())
            .toResponseEntity();
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGenericException(Exception ex) {
        log.error("Unexpected error in accounting service", ex);
        return ApiResponse.<Void>internalError("An unexpected error occurred. Please contact support.")
            .toResponseEntity();
    }
}
