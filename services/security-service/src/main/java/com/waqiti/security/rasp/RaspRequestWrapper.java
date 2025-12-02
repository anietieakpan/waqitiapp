package com.waqiti.security.rasp;

import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import lombok.extern.slf4j.Slf4j;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Request wrapper for RASP analysis
 * Captures request body and parameters for security analysis
 */
@Slf4j
public class RaspRequestWrapper extends HttpServletRequestWrapper {

    private final String body;
    private final Map<String, String[]> parameterMap;
    private final Map<String, String> headerMap;

    public RaspRequestWrapper(HttpServletRequest request) throws IOException {
        super(request);
        
        // Capture request body
        this.body = captureRequestBody(request);
        
        // Capture parameters
        this.parameterMap = new HashMap<>(request.getParameterMap());
        
        // Capture headers
        this.headerMap = captureHeaders(request);
    }

    private String captureRequestBody(HttpServletRequest request) throws IOException {
        if (request.getContentLength() <= 0) {
            return "";
        }
        
        StringBuilder stringBuilder = new StringBuilder();
        try (BufferedReader reader = request.getReader()) {
            String line;
            while ((line = reader.readLine()) != null) {
                stringBuilder.append(line);
            }
        } catch (Exception e) {
            log.debug("Could not read request body: {}", e.getMessage());
            return "";
        }
        
        return stringBuilder.toString();
    }

    private Map<String, String> captureHeaders(HttpServletRequest request) {
        Map<String, String> headers = new HashMap<>();
        Enumeration<String> headerNames = request.getHeaderNames();
        
        while (headerNames.hasMoreElements()) {
            String headerName = headerNames.nextElement();
            String headerValue = request.getHeader(headerName);
            headers.put(headerName.toLowerCase(), headerValue);
        }
        
        return headers;
    }

    @Override
    public ServletInputStream getInputStream() throws IOException {
        return new CachedBodyServletInputStream(body.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public BufferedReader getReader() throws IOException {
        return new BufferedReader(new StringReader(body));
    }

    public String getBody() {
        return body;
    }

    public Map<String, String> getHeaderMap() {
        return Collections.unmodifiableMap(headerMap);
    }

    public String getHeaderValue(String headerName) {
        return headerMap.get(headerName.toLowerCase());
    }

    /**
     * Get all parameter values as a concatenated string for analysis
     */
    public String getAllParametersAsString() {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String[]> entry : parameterMap.entrySet()) {
            for (String value : entry.getValue()) {
                sb.append(entry.getKey()).append("=").append(value).append("&");
            }
        }
        return sb.toString();
    }

    /**
     * Get request URI with query string
     */
    public String getFullRequestURI() {
        String uri = getRequestURI();
        String queryString = getQueryString();
        return queryString != null ? uri + "?" + queryString : uri;
    }

    /**
     * Check if request contains multipart data
     */
    public boolean isMultipartRequest() {
        String contentType = getContentType();
        return contentType != null && contentType.toLowerCase().startsWith("multipart/");
    }

    /**
     * Get request size in bytes
     */
    public long getRequestSize() {
        return body.getBytes(StandardCharsets.UTF_8).length + 
               getAllParametersAsString().getBytes(StandardCharsets.UTF_8).length;
    }

    /**
     * Custom ServletInputStream implementation
     */
    private static class CachedBodyServletInputStream extends ServletInputStream {
        private final ByteArrayInputStream byteArrayInputStream;

        public CachedBodyServletInputStream(byte[] cachedBody) {
            this.byteArrayInputStream = new ByteArrayInputStream(cachedBody);
        }

        @Override
        public boolean isFinished() {
            return byteArrayInputStream.available() == 0;
        }

        @Override
        public boolean isReady() {
            return true;
        }

        @Override
        public void setReadListener(ReadListener readListener) {
            // Not implemented for this use case
        }

        @Override
        public int read() throws IOException {
            return byteArrayInputStream.read();
        }
    }
}