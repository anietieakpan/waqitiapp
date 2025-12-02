package com.waqiti.common.observability;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import io.opentelemetry.sdk.trace.ReadWriteSpan;
import io.opentelemetry.sdk.trace.ReadableSpan;
import io.opentelemetry.sdk.trace.SpanProcessor;
import lombok.extern.slf4j.Slf4j;

/**
 * Custom span processor for financial transaction tracing
 * Adds financial-specific attributes and security measures
 */
@Slf4j
public class FinancialTransactionSpanProcessor implements SpanProcessor {
    
    private static final AttributeKey<String> TRANSACTION_TYPE = AttributeKey.stringKey("transaction.type");
    private static final AttributeKey<String> TRANSACTION_ID = AttributeKey.stringKey("transaction.id");
    private static final AttributeKey<String> ACCOUNT_TYPE = AttributeKey.stringKey("account.type");
    private static final AttributeKey<Boolean> IS_HIGH_VALUE = AttributeKey.booleanKey("transaction.high_value");
    private static final AttributeKey<String> COMPLIANCE_LEVEL = AttributeKey.stringKey("compliance.level");
    
    @Override
    public void onStart(Context parentContext, ReadWriteSpan span) {
        try {
            // Add financial transaction context
            String spanName = span.getName();
            
            if (isFinancialOperation(spanName)) {
                span.setAttribute(TRANSACTION_TYPE, extractTransactionType(spanName));
                span.setAttribute(COMPLIANCE_LEVEL, "PCI_DSS");
                
                // Mark as financial transaction for special handling
                span.setAttribute("transaction.financial", true);
                
                log.debug("Enhanced financial transaction span: {}", spanName);
            }
        } catch (Exception e) {
            log.warn("Error processing financial transaction span", e);
        }
    }
    
    @Override
    public boolean isStartRequired() {
        return true;
    }
    
    @Override
    public void onEnd(ReadableSpan span) {
        try {
            if (span.getAttribute(AttributeKey.booleanKey("transaction.financial")) != null) {
                // Log completion of financial transaction
                log.debug("Financial transaction span completed: {} - Duration: {}ms", 
                    span.getName(), 
                    span.getLatencyNanos() / 1_000_000);
            }
        } catch (Exception e) {
            log.warn("Error finalizing financial transaction span", e);
        }
    }
    
    @Override
    public boolean isEndRequired() {
        return true;
    }
    
    @Override
    public void close() {
        log.info("FinancialTransactionSpanProcessor closed");
    }
    
    private boolean isFinancialOperation(String spanName) {
        return spanName != null && (
            spanName.contains("payment") ||
            spanName.contains("transfer") ||
            spanName.contains("transaction") ||
            spanName.contains("wallet") ||
            spanName.contains("balance") ||
            spanName.contains("withdraw") ||
            spanName.contains("deposit")
        );
    }
    
    private String extractTransactionType(String spanName) {
        if (spanName.contains("payment")) return "PAYMENT";
        if (spanName.contains("transfer")) return "TRANSFER";
        if (spanName.contains("withdraw")) return "WITHDRAWAL";
        if (spanName.contains("deposit")) return "DEPOSIT";
        return "FINANCIAL_OPERATION";
    }
}