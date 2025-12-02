package com.waqiti.security.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.security.dto.SignedRequest;
import com.waqiti.security.dto.SignatureVerificationResult;
import com.waqiti.security.service.APISigningService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

@Component
@Order(2) // After CORS filter
@RequiredArgsConstructor
@Slf4j
public class APISignatureVerificationFilter extends OncePerRequestFilter {

    private final APISigningService apiSigningService;
    private final ObjectMapper objectMapper;
    
    @Value("${security.api-signing.enabled:true}")
    private boolean signatureVerificationEnabled;
    
    @Value("${security.api-signing.required-endpoints}")
    private List<String> requiredEndpoints;
    
    @Value("${security.api-signing.excluded-endpoints}")
    private List<String> excludedEndpoints;
    
    @Value("${security.api-signing.header-prefix:X-Waqiti-}")
    private String headerPrefix;
    
    private static final String SIGNATURE_HEADER = "X-Waqiti-Signature";
    private static final String TIMESTAMP_HEADER = "X-Waqiti-Timestamp";
    private static final String NONCE_HEADER = "X-Waqiti-Nonce";
    private static final String CLIENT_ID_HEADER = "X-Waqiti-Client-Id";
    private static final String KEY_ID_HEADER = "X-Waqiti-Key-Id";
    private static final String ALGORITHM_HEADER = "X-Waqiti-Algorithm";
    
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, 
                                  FilterChain filterChain) throws ServletException, IOException {
        
        if (!signatureVerificationEnabled) {
            filterChain.doFilter(request, response);
            return;
        }
        
        String requestURI = request.getRequestURI();
        String method = request.getMethod();
        
        // Skip verification for excluded endpoints
        if (isExcludedEndpoint(requestURI)) {
            filterChain.doFilter(request, response);
            return;
        }
        
        // Check if signature is required for this endpoint
        boolean requiresSignature = isRequiredEndpoint(requestURI) || 
                                   isCriticalOperation(method, requestURI);
        
        // Check if signature headers are present
        boolean hasSignatureHeaders = hasRequiredSignatureHeaders(request);
        
        if (requiresSignature && !hasSignatureHeaders) {
            log.warn("Missing signature headers for required endpoint: {} {}", method, requestURI);
            sendErrorResponse(response, HttpStatus.UNAUTHORIZED, 
                    "API signature required for this endpoint");
            return;
        }
        
        if (hasSignatureHeaders) {
            // Verify signature
            CachedBodyHttpServletRequest cachedRequest = new CachedBodyHttpServletRequest(request);
            
            try {
                SignedRequest signedRequest = extractSignedRequest(cachedRequest);
                SignatureVerificationResult result = apiSigningService.verifySignature(signedRequest);
                
                if (!result.isValid()) {
                    log.warn("Invalid API signature for {} {}: {}", 
                            method, requestURI, result.getErrorMessage());
                    sendErrorResponse(response, HttpStatus.UNAUTHORIZED, 
                            "Invalid API signature: " + result.getErrorMessage());
                    return;
                }
                
                // Add client ID to request attributes for downstream use
                cachedRequest.setAttribute("verified.client.id", result.getClientId());
                cachedRequest.setAttribute("signature.verified", true);
                
                log.debug("API signature verified successfully for client: {}", result.getClientId());
                
                filterChain.doFilter(cachedRequest, response);
                
            } catch (Exception e) {
                log.error("Error during signature verification", e);
                sendErrorResponse(response, HttpStatus.INTERNAL_SERVER_ERROR, 
                        "Signature verification failed");
            }
        } else {
            // No signature headers, proceed if not required
            filterChain.doFilter(request, response);
        }
    }
    
    private boolean isExcludedEndpoint(String requestURI) {
        if (excludedEndpoints == null) {
            return false;
        }
        
        return excludedEndpoints.stream()
                .anyMatch(pattern -> requestURI.matches(pattern.replace("*", ".*")));
    }
    
    private boolean isRequiredEndpoint(String requestURI) {
        if (requiredEndpoints == null) {
            return false;
        }
        
        return requiredEndpoints.stream()
                .anyMatch(pattern -> requestURI.matches(pattern.replace("*", ".*")));
    }
    
    private boolean isCriticalOperation(String method, String requestURI) {
        // Consider these operations as critical and requiring signatures
        return (method.equals("POST") && (requestURI.contains("/payments") || 
                                         requestURI.contains("/transfers") ||
                                         requestURI.contains("/withdrawals"))) ||
               (method.equals("PUT") && requestURI.contains("/accounts")) ||
               (method.equals("DELETE") && requestURI.contains("/payment-methods"));
    }
    
    private boolean hasRequiredSignatureHeaders(HttpServletRequest request) {
        return StringUtils.hasText(request.getHeader(SIGNATURE_HEADER)) &&
               StringUtils.hasText(request.getHeader(TIMESTAMP_HEADER)) &&
               StringUtils.hasText(request.getHeader(NONCE_HEADER)) &&
               StringUtils.hasText(request.getHeader(CLIENT_ID_HEADER));
    }
    
    private SignedRequest extractSignedRequest(CachedBodyHttpServletRequest request) throws IOException {
        String method = request.getMethod();
        String uri = request.getRequestURI();
        String body = request.getCachedBody();
        
        // Extract signature headers
        String signature = request.getHeader(SIGNATURE_HEADER);
        String timestamp = request.getHeader(TIMESTAMP_HEADER);
        String nonce = request.getHeader(NONCE_HEADER);
        String clientId = request.getHeader(CLIENT_ID_HEADER);
        String keyId = request.getHeader(KEY_ID_HEADER);
        String algorithm = request.getHeader(ALGORITHM_HEADER);
        
        // Extract relevant headers for signing
        Map<String, String> headers = extractRelevantHeaders(request);
        
        return SignedRequest.builder()
                .clientId(clientId)
                .method(method)
                .uri(uri)
                .body(body != null ? body : "")
                .headers(headers)
                .timestamp(timestamp)
                .nonce(nonce)
                .algorithm(algorithm != null ? algorithm : "RS256")
                .signature(signature)
                .keyId(keyId)
                .build();
    }
    
    private Map<String, String> extractRelevantHeaders(HttpServletRequest request) {
        Map<String, String> headers = new HashMap<>();
        
        // Extract standard headers that should be signed
        String[] standardHeaders = {"authorization", "content-type", "date", "host"};
        
        for (String headerName : standardHeaders) {
            String headerValue = request.getHeader(headerName);
            if (StringUtils.hasText(headerValue)) {
                headers.put(headerName.toLowerCase(), headerValue);
            }
        }
        
        // Extract custom Waqiti headers
        Enumeration<String> headerNames = request.getHeaderNames();
        while (headerNames.hasMoreElements()) {
            String headerName = headerNames.nextElement();
            if (headerName.toLowerCase().startsWith(headerPrefix.toLowerCase())) {
                headers.put(headerName.toLowerCase(), request.getHeader(headerName));
            }
        }
        
        return headers;
    }
    
    private void sendErrorResponse(HttpServletResponse response, HttpStatus status, String message) 
            throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        
        Map<String, Object> errorResponse = Map.of(
                "error", status.getReasonPhrase(),
                "message", message,
                "timestamp", System.currentTimeMillis(),
                "status", status.value()
        );
        
        response.getWriter().write(objectMapper.writeValueAsString(errorResponse));
    }
    
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        
        // Skip health checks, metrics, and static resources
        return path.startsWith("/actuator") ||
               path.startsWith("/health") ||
               path.startsWith("/metrics") ||
               path.startsWith("/static") ||
               path.startsWith("/webjars") ||
               path.equals("/") ||
               path.endsWith(".html") ||
               path.endsWith(".css") ||
               path.endsWith(".js") ||
               path.endsWith(".ico");
    }
    
    // Helper class to cache request body for signature verification
    private static class CachedBodyHttpServletRequest extends jakarta.servlet.http.HttpServletRequestWrapper {
        private final String cachedBody;
        
        public CachedBodyHttpServletRequest(HttpServletRequest request) throws IOException {
            super(request);
            
            // Read and cache the body
            StringBuilder body = new StringBuilder();
            String line;
            
            try (var reader = request.getReader()) {
                while ((line = reader.readLine()) != null) {
                    body.append(line);
                }
            } catch (Exception e) {
                // If we can't read the body, use empty string
                body = new StringBuilder();
            }
            
            this.cachedBody = body.toString();
        }
        
        public String getCachedBody() {
            return cachedBody;
        }
        
        @Override
        public java.io.BufferedReader getReader() throws IOException {
            return new java.io.BufferedReader(new java.io.StringReader(cachedBody));
        }
        
        @Override
        public jakarta.servlet.ServletInputStream getInputStream() throws IOException {
            final byte[] bytes = cachedBody.getBytes(StandardCharsets.UTF_8);
            return new jakarta.servlet.ServletInputStream() {
                private int index = 0;
                
                @Override
                public int read() {
                    return index >= bytes.length ? -1 : bytes[index++];
                }
                
                @Override
                public boolean isFinished() {
                    return index >= bytes.length;
                }
                
                @Override
                public boolean isReady() {
                    return true;
                }
                
                @Override
                public void setReadListener(jakarta.servlet.ReadListener readListener) {
                    // Not implemented for this use case
                }
            };
        }
    }
}