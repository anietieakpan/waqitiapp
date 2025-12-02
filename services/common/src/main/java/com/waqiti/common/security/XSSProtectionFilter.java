package com.waqiti.common.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

/**
 * XSS Protection Filter
 * 
 * Intercepts all incoming requests and sanitizes potentially dangerous input
 * Applied globally to all endpoints
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@RequiredArgsConstructor
@Slf4j
public class XSSProtectionFilter implements Filter {

    private final XSSProtectionService xssProtectionService;
    private final ObjectMapper objectMapper;
    
    // Endpoints that should skip XSS filtering (e.g., file uploads)
    private static final Set<String> EXCLUDED_PATHS = Set.of(
        "/api/v1/files/upload",
        "/api/v1/documents/upload",
        "/health",
        "/metrics",
        "/actuator"
    );

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) 
            throws IOException, ServletException {
        
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;
        
        // Check if path should be excluded
        String path = httpRequest.getRequestURI();
        if (shouldExcludePath(path)) {
            chain.doFilter(request, response);
            return;
        }
        
        // Add security headers
        addSecurityHeaders(httpResponse);
        
        // Wrap request to sanitize input
        XSSRequestWrapper wrappedRequest = new XSSRequestWrapper(httpRequest, xssProtectionService);
        
        try {
            chain.doFilter(wrappedRequest, response);
        } catch (XSSProtectionService.XSSValidationException e) {
            log.warn("XSS validation failed for request: {} {}", httpRequest.getMethod(), path);
            httpResponse.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            httpResponse.setContentType("application/json");
            
            Map<String, Object> error = new HashMap<>();
            error.put("error", "Invalid input detected");
            error.put("message", "Request contains potentially harmful content");
            error.put("timestamp", System.currentTimeMillis());
            
            httpResponse.getWriter().write(objectMapper.writeValueAsString(error));
        }
    }
    
    private boolean shouldExcludePath(String path) {
        return EXCLUDED_PATHS.stream().anyMatch(path::startsWith);
    }
    
    private void addSecurityHeaders(HttpServletResponse response) {
        // Prevent XSS
        response.setHeader("X-XSS-Protection", "1; mode=block");
        
        // Prevent MIME type sniffing
        response.setHeader("X-Content-Type-Options", "nosniff");
        
        // Prevent clickjacking
        response.setHeader("X-Frame-Options", "DENY");
        
        // Enable HSTS
        response.setHeader("Strict-Transport-Security", "max-age=31536000; includeSubDomains; preload");
        
        // Content Security Policy
        XSSProtectionService.CSPConfig cspConfig = new XSSProtectionService.CSPConfig()
            .setDefaultSrc("'self'")
            .setScriptSrc("'self' 'unsafe-inline' https://cdn.jsdelivr.net")
            .setStyleSrc("'self' 'unsafe-inline' https://fonts.googleapis.com")
            .setImgSrc("'self' data: https:")
            .setConnectSrc("'self' https://api.example.com wss://ws.waqiti.com")
            .setFormAction("'self'")
            .setUpgradeInsecureRequests(true)
            .setBlockAllMixedContent(true);
        
        response.setHeader("Content-Security-Policy", xssProtectionService.generateCSPHeader(cspConfig));
        
        // Referrer Policy
        response.setHeader("Referrer-Policy", "strict-origin-when-cross-origin");
        
        // Permissions Policy (formerly Feature Policy)
        response.setHeader("Permissions-Policy", 
            "geolocation=(), microphone=(), camera=(), payment=(self), usb=()");
    }

    /**
     * Custom HttpServletRequest wrapper that sanitizes input
     */
    private static class XSSRequestWrapper extends HttpServletRequestWrapper {
        
        private final XSSProtectionService xssProtectionService;
        private byte[] cachedBody;
        
        public XSSRequestWrapper(HttpServletRequest request, XSSProtectionService xssProtectionService) 
                throws IOException {
            super(request);
            this.xssProtectionService = xssProtectionService;
            
            // Cache request body for multiple reads
            if (isJsonRequest()) {
                InputStream requestInputStream = request.getInputStream();
                this.cachedBody = StreamUtils.copyToByteArray(requestInputStream);
            }
        }
        
        @Override
        public String getParameter(String name) {
            String value = super.getParameter(name);
            return sanitizeValue(value, name);
        }
        
        @Override
        public String[] getParameterValues(String name) {
            String[] values = super.getParameterValues(name);
            if (values == null) {
                return null;
            }
            
            return Arrays.stream(values)
                .map(value -> sanitizeValue(value, name))
                .toArray(String[]::new);
        }
        
        @Override
        public Map<String, String[]> getParameterMap() {
            Map<String, String[]> originalMap = super.getParameterMap();
            Map<String, String[]> sanitizedMap = new HashMap<>();
            
            for (Map.Entry<String, String[]> entry : originalMap.entrySet()) {
                String[] sanitizedValues = Arrays.stream(entry.getValue())
                    .map(value -> sanitizeValue(value, entry.getKey()))
                    .toArray(String[]::new);
                sanitizedMap.put(entry.getKey(), sanitizedValues);
            }
            
            return sanitizedMap;
        }
        
        @Override
        public String getHeader(String name) {
            String value = super.getHeader(name);
            
            // Don't sanitize certain headers
            if (isExcludedHeader(name)) {
                return value;
            }
            
            return sanitizeValue(value, name);
        }
        
        @Override
        public Enumeration<String> getHeaders(String name) {
            List<String> sanitizedHeaders = Collections.list(super.getHeaders(name))
                .stream()
                .map(value -> isExcludedHeader(name) ? value : sanitizeValue(value, name))
                .collect(Collectors.toList());
            
            return Collections.enumeration(sanitizedHeaders);
        }
        
        @Override
        public ServletInputStream getInputStream() throws IOException {
            if (!isJsonRequest() || cachedBody == null) {
                return super.getInputStream();
            }
            
            // Sanitize JSON body
            String body = new String(cachedBody, StandardCharsets.UTF_8);
            String sanitizedBody = xssProtectionService.sanitizeJson(body);
            byte[] sanitizedBytes = sanitizedBody.getBytes(StandardCharsets.UTF_8);
            
            return new ServletInputStream() {
                private final ByteArrayInputStream inputStream = new ByteArrayInputStream(sanitizedBytes);
                
                @Override
                public int read() {
                    return inputStream.read();
                }
                
                @Override
                public boolean isFinished() {
                    return inputStream.available() == 0;
                }
                
                @Override
                public boolean isReady() {
                    return true;
                }
                
                @Override
                public void setReadListener(ReadListener listener) {
                    // Not implemented for simplicity
                }
            };
        }
        
        @Override
        public BufferedReader getReader() throws IOException {
            return new BufferedReader(new InputStreamReader(getInputStream(), StandardCharsets.UTF_8));
        }
        
        private String sanitizeValue(String value, String paramName) {
            if (value == null) {
                return null;
            }
            
            // Special handling for specific parameter types
            if (isEmailParameter(paramName)) {
                return xssProtectionService.sanitizeEmail(value);
            } else if (isPhoneParameter(paramName)) {
                return xssProtectionService.sanitizePhoneNumber(value);
            } else if (isUrlParameter(paramName)) {
                return xssProtectionService.sanitizeURL(value);
            } else if (isFileNameParameter(paramName)) {
                return xssProtectionService.sanitizeFileName(value);
            }
            
            // Default sanitization
            return xssProtectionService.sanitizeInput(value);
        }
        
        private boolean isJsonRequest() {
            String contentType = getContentType();
            return contentType != null && contentType.contains("application/json");
        }
        
        private boolean isExcludedHeader(String headerName) {
            Set<String> excluded = Set.of(
                "Authorization",
                "Content-Type",
                "Accept",
                "User-Agent",
                "Cookie",
                "X-Requested-With",
                "X-Correlation-Id"
            );
            return excluded.contains(headerName);
        }
        
        private boolean isEmailParameter(String paramName) {
            return paramName != null && 
                (paramName.toLowerCase().contains("email") || 
                 paramName.toLowerCase().contains("mail"));
        }
        
        private boolean isPhoneParameter(String paramName) {
            return paramName != null && 
                (paramName.toLowerCase().contains("phone") || 
                 paramName.toLowerCase().contains("mobile") ||
                 paramName.toLowerCase().contains("tel"));
        }
        
        private boolean isUrlParameter(String paramName) {
            return paramName != null && 
                (paramName.toLowerCase().contains("url") || 
                 paramName.toLowerCase().contains("link") ||
                 paramName.toLowerCase().contains("href"));
        }
        
        private boolean isFileNameParameter(String paramName) {
            return paramName != null && 
                (paramName.toLowerCase().contains("filename") || 
                 paramName.toLowerCase().contains("file_name") ||
                 paramName.toLowerCase().contains("name"));
        }
    }
}