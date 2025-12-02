package com.waqiti.common.kafka;

import org.apache.kafka.common.header.Header;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.internals.RecordHeader;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Represents Kafka message headers for the Waqiti platform.
 * Provides a comprehensive implementation for managing key-value header pairs.
 */
public class Headers implements Iterable<Header> {
    
    private final Map<String, List<Header>> headers;
    
    public Headers() {
        this.headers = new ConcurrentHashMap<>();
    }
    
    public Headers(Iterable<Header> headers) {
        this();
        if (headers != null) {
            for (Header header : headers) {
                add(header);
            }
        }
    }
    
    /**
     * Add a header with the given key and value
     */
    public Headers add(String key, byte[] value) {
        Objects.requireNonNull(key, "Header key cannot be null");
        Header header = new RecordHeader(key, value);
        return add(header);
    }
    
    /**
     * Add a header with the given key and string value
     */
    public Headers add(String key, String value) {
        byte[] valueBytes = value != null ? value.getBytes(StandardCharsets.UTF_8) : null;
        return add(key, valueBytes);
    }
    
    /**
     * Add a header
     */
    public Headers add(Header header) {
        Objects.requireNonNull(header, "Header cannot be null");
        headers.computeIfAbsent(header.key(), k -> new ArrayList<>()).add(header);
        return this;
    }
    
    /**
     * Remove all headers with the given key
     */
    public Headers remove(String key) {
        headers.remove(key);
        return this;
    }
    
    /**
     * Get the last header with the given key
     */
    public Header lastHeader(String key) {
        List<Header> headerList = headers.get(key);
        if (headerList == null || headerList.isEmpty()) {
            return null;
        }
        return headerList.get(headerList.size() - 1);
    }
    
    /**
     * Get all headers with the given key
     */
    public Iterable<Header> headers(String key) {
        List<Header> headerList = headers.get(key);
        return headerList != null ? new ArrayList<>(headerList) : Collections.emptyList();
    }
    
    /**
     * Get all header keys
     */
    public Set<String> getKeys() {
        return new HashSet<>(headers.keySet());
    }
    
    /**
     * Check if headers contain the given key
     */
    public boolean containsKey(String key) {
        return headers.containsKey(key);
    }
    
    /**
     * Get the number of headers
     */
    public int size() {
        return headers.values().stream().mapToInt(List::size).sum();
    }
    
    /**
     * Check if headers are empty
     */
    public boolean isEmpty() {
        return headers.isEmpty() || headers.values().stream().allMatch(List::isEmpty);
    }
    
    /**
     * Clear all headers
     */
    public void clear() {
        headers.clear();
    }
    
    /**
     * Get all headers as a flat list
     */
    public List<Header> toList() {
        List<Header> allHeaders = new ArrayList<>();
        for (List<Header> headerList : headers.values()) {
            allHeaders.addAll(headerList);
        }
        return allHeaders;
    }
    
    /**
     * Get headers as a map (only last value for each key)
     */
    public Map<String, String> toMap() {
        Map<String, String> headerMap = new HashMap<>();
        for (Map.Entry<String, List<Header>> entry : headers.entrySet()) {
            List<Header> headerList = entry.getValue();
            if (!headerList.isEmpty()) {
                Header lastHeader = headerList.get(headerList.size() - 1);
                headerMap.put(entry.getKey(), headerValueAsString(lastHeader));
            }
        }
        return headerMap;
    }
    
    /**
     * Estimate the size of headers in bytes
     */
    public int estimateSize() {
        int size = 0;
        for (List<Header> headerList : headers.values()) {
            for (Header header : headerList) {
                // Estimate: key length + value length + some overhead
                size += header.key().length() + (header.value() != null ? header.value().length : 0) + 8;
            }
        }
        return size;
    }
    
    /**
     * Create an iterator over all headers
     */
    @Override
    public Iterator<Header> iterator() {
        return toList().iterator();
    }
    
    /**
     * Create headers from a map
     */
    public static Headers from(Map<String, String> headerMap) {
        Headers headers = new Headers();
        if (headerMap != null) {
            for (Map.Entry<String, String> entry : headerMap.entrySet()) {
                headers.add(entry.getKey(), entry.getValue());
            }
        }
        return headers;
    }
    
    /**
     * Create headers from key-value pairs
     */
    public static Headers of(String... keyValuePairs) {
        if (keyValuePairs.length % 2 != 0) {
            throw new IllegalArgumentException("Key-value pairs must have even number of elements");
        }
        
        Headers headers = new Headers();
        for (int i = 0; i < keyValuePairs.length; i += 2) {
            headers.add(keyValuePairs[i], keyValuePairs[i + 1]);
        }
        return headers;
    }
    
    @Override
    public String toString() {
        if (isEmpty()) {
            return "Headers{}";
        }
        
        StringBuilder sb = new StringBuilder("Headers{");
        boolean first = true;
        for (Map.Entry<String, List<Header>> entry : headers.entrySet()) {
            if (!first) {
                sb.append(", ");
            }
            sb.append(entry.getKey()).append("=").append(entry.getValue().size()).append(" values");
            first = false;
        }
        sb.append("}");
        return sb.toString();
    }
    
    /**
     * Get detailed string representation for debugging
     */
    public String toDetailedString() {
        if (isEmpty()) {
            return "Headers: None";
        }
        
        StringBuilder sb = new StringBuilder("Headers:\n");
        for (Map.Entry<String, List<Header>> entry : headers.entrySet()) {
            String key = entry.getKey();
            List<Header> headerList = entry.getValue();
            
            for (int i = 0; i < headerList.size(); i++) {
                Header header = headerList.get(i);
                sb.append("  ").append(key);
                if (headerList.size() > 1) {
                    sb.append("[").append(i).append("]");
                }
                sb.append(": ").append(headerValueAsString(header)).append("\n");
            }
        }
        return sb.toString().trim();
    }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        
        Headers that = (Headers) obj;
        return Objects.equals(headers, that.headers);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(headers);
    }
    
    /**
     * Helper method to convert header value to string
     */
    private String headerValueAsString(Header header) {
        if (header == null || header.value() == null) {
            return null;
        }
        return new String(header.value(), StandardCharsets.UTF_8);
    }
}