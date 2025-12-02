package com.waqiti.common.observability;

import com.waqiti.common.correlation.CorrelationIdService;
import feign.RequestInterceptor;
import feign.RequestTemplate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;

/**
 * CRITICAL FINANCIAL: Financial Correlation Propagation Interceptor
 * 
 * Ensures financial transaction correlation IDs and trace context are
 * properly propagated across all HTTP calls between microservices.
 * 
 * Implements multiple interceptor interfaces for comprehensive coverage:
 * - HandlerInterceptor: Incoming HTTP requests
 * - ClientHttpRequestInterceptor: Outgoing RestTemplate calls
 * - RequestInterceptor: Outgoing Feign client calls
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "waqiti.observability.correlation.enabled", havingValue = "true", matchIfMissing = true)
public class FinancialCorrelationInterceptor implements 
        HandlerInterceptor, 
        ClientHttpRequestInterceptor, 
        RequestInterceptor {

    private final CorrelationIdService correlationIdService;
    private final FinancialTransactionTracing financialTracing;
    private final DistributedTracingConfig tracingConfig;

    // Standard header names for financial tracing
    public static final String FINANCIAL_CORRELATION_ID_HEADER = "X-Financial-Correlation-ID";
    public static final String FINANCIAL_TRANSACTION_TYPE_HEADER = "X-Financial-Transaction-Type";
    public static final String FINANCIAL_USER_ID_HEADER = "X-Financial-User-ID";
    public static final String FINANCIAL_AMOUNT_HEADER = "X-Financial-Amount";
    public static final String FINANCIAL_CURRENCY_HEADER = "X-Financial-Currency";
    public static final String FINANCIAL_RISK_LEVEL_HEADER = "X-Financial-Risk-Level";
    public static final String FINANCIAL_COMPLIANCE_REQUIRED_HEADER = "X-Financial-Compliance-Required";

    /**
     * HandlerInterceptor implementation - Handle incoming HTTP requests
     */
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // Extract financial correlation context from headers
        String financialCorrelationId = request.getHeader(FINANCIAL_CORRELATION_ID_HEADER);
        String transactionType = request.getHeader(FINANCIAL_TRANSACTION_TYPE_HEADER);
        String userId = request.getHeader(FINANCIAL_USER_ID_HEADER);
        String traceId = request.getHeader(DistributedTracingConfig.TRACE_ID_HEADER);
        String correlationId = request.getHeader(DistributedTracingConfig.CORRELATION_ID_HEADER);

        // Set up correlation context
        if (correlationId != null) {
            correlationIdService.setCorrelationId(correlationId);
        } else if (financialCorrelationId != null) {
            correlationIdService.setCorrelationId(financialCorrelationId);
        }

        if (traceId != null) {
            String parentSpanId = request.getHeader(DistributedTracingConfig.SPAN_ID_HEADER);
            correlationIdService.setTraceContext(traceId, parentSpanId);
        }

        // Add financial context to current span if this is a financial operation
        if (financialCorrelationId != null && isFinancialEndpoint(request)) {
            addFinancialContextToSpan(request);
        }

        // Add correlation headers to response
        addCorrelationHeadersToResponse(response);

        log.debug("INCOMING_REQUEST: method={}, uri={}, financial_correlation={}, trace={}", 
                request.getMethod(), request.getRequestURI(), financialCorrelationId, traceId);

        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, 
                              Object handler, Exception ex) throws Exception {
        // Log completion with financial context
        String financialCorrelationId = correlationIdService.getCorrelationId().orElse(null);
        
        if (financialCorrelationId != null && financialCorrelationId.startsWith("FIN-")) {
            FinancialTransactionContext context = financialTracing.getCurrentFinancialContext();
            if (context != null) {
                log.info("FINANCIAL_REQUEST_COMPLETED: method={}, uri={}, status={}, " +
                        "financial_correlation={}, duration={}ms", 
                        request.getMethod(), request.getRequestURI(), response.getStatus(),
                        financialCorrelationId, context.getDurationMillis());
            }
        }

        // Clear correlation context
        correlationIdService.clearContext();
    }

    /**
     * ClientHttpRequestInterceptor implementation - Handle outgoing RestTemplate calls
     */
    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body, 
                                      ClientHttpRequestExecution execution) throws IOException {
        
        // Add correlation headers to outgoing request
        addCorrelationHeadersToRequest(request);
        
        // Add financial context headers if within financial transaction
        addFinancialContextHeaders(request);

        log.debug("OUTGOING_REQUEST: method={}, uri={}, correlation={}", 
                request.getMethod(), request.getURI(), 
                correlationIdService.getCorrelationId().orElse("none"));

        return execution.execute(request, body);
    }

    /**
     * RequestInterceptor implementation - Handle outgoing Feign client calls
     */
    @Override
    public void apply(RequestTemplate template) {
        // Add correlation headers to Feign request
        Map<String, String> correlationHeaders = tracingConfig.getCorrelationHeaders();
        correlationHeaders.forEach(template::header);

        // Add standard correlation headers
        correlationIdService.getCorrelationId().ifPresent(id -> 
            template.header(DistributedTracingConfig.CORRELATION_ID_HEADER, id));

        // Add financial context if applicable
        FinancialTransactionContext financialContext = financialTracing.getCurrentFinancialContext();
        if (financialContext != null) {
            template.header(FINANCIAL_CORRELATION_ID_HEADER, financialContext.getFinancialCorrelationId());
            template.header(FINANCIAL_TRANSACTION_TYPE_HEADER, financialContext.getTransactionType());
            template.header(FINANCIAL_USER_ID_HEADER, financialContext.getUserId());
            template.header(FINANCIAL_CURRENCY_HEADER, financialContext.getCurrency());
            
            if (financialContext.getAmount() != null) {
                template.header(FINANCIAL_AMOUNT_HEADER, financialContext.getAmount().toString());
            }
            
            template.header(FINANCIAL_RISK_LEVEL_HEADER, financialContext.getHighestFraudRiskLevel());
            template.header(FINANCIAL_COMPLIANCE_REQUIRED_HEADER, 
                String.valueOf(!financialContext.isCompliant()));
        }

        log.debug("FEIGN_REQUEST: method={}, url={}, correlation={}", 
                template.method(), template.url(), 
                correlationIdService.getCorrelationId().orElse("none"));
    }

    // Private helper methods

    private boolean isFinancialEndpoint(HttpServletRequest request) {
        String uri = request.getRequestURI().toLowerCase();
        return uri.contains("/payment") || uri.contains("/transaction") || 
               uri.contains("/wallet") || uri.contains("/transfer") ||
               uri.contains("/deposit") || uri.contains("/withdraw");
    }

    private void addFinancialContextToSpan(HttpServletRequest request) {
        // Add financial-specific attributes to current span
        tracingConfig.addTags(Map.of(
            "waqiti.financial.endpoint", "true",
            "waqiti.financial.correlation.id", 
                request.getHeader(FINANCIAL_CORRELATION_ID_HEADER),
            "waqiti.financial.transaction.type", 
                request.getHeader(FINANCIAL_TRANSACTION_TYPE_HEADER) != null ? 
                    request.getHeader(FINANCIAL_TRANSACTION_TYPE_HEADER) : "unknown",
            "waqiti.financial.user.id", 
                request.getHeader(FINANCIAL_USER_ID_HEADER) != null ? 
                    request.getHeader(FINANCIAL_USER_ID_HEADER) : "unknown"
        ));

        // Add compliance and risk context
        String complianceRequired = request.getHeader(FINANCIAL_COMPLIANCE_REQUIRED_HEADER);
        String riskLevel = request.getHeader(FINANCIAL_RISK_LEVEL_HEADER);
        
        if (complianceRequired != null) {
            tracingConfig.addTags(Map.of("waqiti.compliance.required", complianceRequired));
        }
        if (riskLevel != null) {
            tracingConfig.addTags(Map.of("waqiti.risk.level", riskLevel));
        }
    }

    private void addCorrelationHeadersToResponse(HttpServletResponse response) {
        // Add correlation headers to response for client tracing
        correlationIdService.getCorrelationId().ifPresent(id -> 
            response.setHeader(DistributedTracingConfig.CORRELATION_ID_HEADER, id));
        
        String traceId = tracingConfig.getCurrentTraceId();
        String spanId = tracingConfig.getCurrentSpanId();
        
        if (traceId != null) {
            response.setHeader(DistributedTracingConfig.TRACE_ID_HEADER, traceId);
        }
        if (spanId != null) {
            response.setHeader(DistributedTracingConfig.SPAN_ID_HEADER, spanId);
        }
    }

    private void addCorrelationHeadersToRequest(HttpRequest request) {
        // Add standard correlation headers
        correlationIdService.getCorrelationId().ifPresent(id -> 
            request.getHeaders().add(DistributedTracingConfig.CORRELATION_ID_HEADER, id));
        
        String traceId = tracingConfig.getCurrentTraceId();
        String spanId = tracingConfig.getCurrentSpanId();
        
        if (traceId != null) {
            request.getHeaders().add(DistributedTracingConfig.TRACE_ID_HEADER, traceId);
        }
        if (spanId != null) {
            request.getHeaders().add(DistributedTracingConfig.SPAN_ID_HEADER, spanId);
        }
    }

    private void addFinancialContextHeaders(HttpRequest request) {
        // Add financial context if within financial transaction
        FinancialTransactionContext financialContext = financialTracing.getCurrentFinancialContext();
        if (financialContext != null) {
            request.getHeaders().add(FINANCIAL_CORRELATION_ID_HEADER, 
                financialContext.getFinancialCorrelationId());
            request.getHeaders().add(FINANCIAL_TRANSACTION_TYPE_HEADER, 
                financialContext.getTransactionType());
            request.getHeaders().add(FINANCIAL_USER_ID_HEADER, 
                financialContext.getUserId());
            request.getHeaders().add(FINANCIAL_CURRENCY_HEADER, 
                financialContext.getCurrency());
            
            if (financialContext.getAmount() != null) {
                request.getHeaders().add(FINANCIAL_AMOUNT_HEADER, 
                    financialContext.getAmount().toString());
            }
            
            request.getHeaders().add(FINANCIAL_RISK_LEVEL_HEADER, 
                financialContext.getHighestFraudRiskLevel());
            request.getHeaders().add(FINANCIAL_COMPLIANCE_REQUIRED_HEADER, 
                String.valueOf(!financialContext.isCompliant()));
        }
    }
}