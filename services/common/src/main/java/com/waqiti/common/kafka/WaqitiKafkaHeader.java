package com.waqiti.common.kafka;

import org.apache.kafka.common.header.Header;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Objects;

/**
 * Represents a single Kafka message header key-value pair
 * for the Waqiti platform messaging system.
 * Implements the Kafka Header interface for full compatibility.
 */
public class WaqitiKafkaHeader implements Header {
    
    private final String key;
    private final byte[] value;
    
    /**
     * Create a new header with the given key and value
     * 
     * @param key The header key (cannot be null)
     * @param value The header value (can be null)
     */
    public WaqitiKafkaHeader(String key, byte[] value) {
        this.key = Objects.requireNonNull(key, "Header key cannot be null");
        this.value = value != null ? value.clone() : null;
        
        if (key.trim().isEmpty()) {
            throw new IllegalArgumentException("Header key cannot be empty");
        }
    }
    
    /**
     * Create a new header with the given key and string value
     * 
     * @param key The header key (cannot be null)
     * @param value The header value as string (can be null)
     */
    public WaqitiKafkaHeader(String key, String value) {
        this(key, value != null ? value.getBytes(StandardCharsets.UTF_8) : null);
    }
    
    /**
     * Get the header key
     */
    @Override
    public String key() {
        return key;
    }
    
    /**
     * Get the header value as byte array
     */
    @Override
    public byte[] value() {
        return value != null ? value.clone() : null;
    }
    
    /**
     * Get the header value as string using UTF-8 encoding
     */
    public String getValueAsString() {
        return value != null ? new String(value, StandardCharsets.UTF_8) : null;
    }
    
    /**
     * Get the header value as string using specified encoding
     */
    public String getValueAsString(java.nio.charset.Charset charset) {
        return value != null ? new String(value, charset) : null;
    }
    
    /**
     * Check if this header has a value
     */
    public boolean hasValue() {
        return value != null;
    }
    
    /**
     * Check if this header has an empty value
     */
    public boolean hasEmptyValue() {
        return value != null && value.length == 0;
    }
    
    /**
     * Get the size of the header value in bytes
     */
    public int getValueSize() {
        return value != null ? value.length : 0;
    }
    
    /**
     * Get the total size of this header (key + value) in bytes
     */
    public int estimateSize() {
        int keySize = key.getBytes(StandardCharsets.UTF_8).length;
        int valueSize = getValueSize();
        return keySize + valueSize + 8; // Add some overhead for metadata
    }
    
    /**
     * Check if the header value matches the given string
     */
    public boolean valueEquals(String other) {
        if (other == null) {
            return value == null;
        }
        if (value == null) {
            return false;
        }
        return other.equals(getValueAsString());
    }
    
    /**
     * Check if the header value matches the given byte array
     */
    public boolean valueEquals(byte[] other) {
        return Arrays.equals(value, other);
    }
    
    /**
     * Create a copy of this header
     */
    public WaqitiKafkaHeader copy() {
        return new WaqitiKafkaHeader(key, value);
    }
    
    /**
     * Create a new header with the same key but different value
     */
    public WaqitiKafkaHeader withValue(byte[] newValue) {
        return new WaqitiKafkaHeader(key, newValue);
    }
    
    /**
     * Create a new header with the same key but different string value
     */
    public WaqitiKafkaHeader withValue(String newValue) {
        return new WaqitiKafkaHeader(key, newValue);
    }
    
    /**
     * Get detailed information about this header for debugging
     */
    public String getDetailedInfo() {
        StringBuilder info = new StringBuilder();
        info.append("Header Details:\n");
        info.append("  Key: ").append(key).append("\n");
        info.append("  Has Value: ").append(hasValue()).append("\n");
        
        if (hasValue()) {
            info.append("  Value Size: ").append(getValueSize()).append(" bytes\n");
            info.append("  Value (String): ").append(getValueAsString()).append("\n");
            info.append("  Value (Hex): ").append(getValueAsHex()).append("\n");
        } else {
            info.append("  Value: null\n");
        }
        
        info.append("  Estimated Size: ").append(estimateSize()).append(" bytes");
        return info.toString();
    }
    
    /**
     * Get the header value as hexadecimal string
     */
    public String getValueAsHex() {
        if (value == null) {
            return null;
        }
        
        StringBuilder hex = new StringBuilder();
        for (byte b : value) {
            hex.append(String.format("%02X", b));
        }
        return hex.toString();
    }
    
    /**
     * Check if the header value is a valid UTF-8 string
     */
    public boolean isValidUtf8() {
        if (value == null) {
            return true; // null is considered valid
        }
        
        try {
            new String(value, StandardCharsets.UTF_8);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
    
    @Override
    public String toString() {
        return String.format("Header{key='%s', value='%s'}", key, getValueAsString());
    }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        
        WaqitiKafkaHeader header = (WaqitiKafkaHeader) obj;
        return Objects.equals(key, header.key) && Arrays.equals(value, header.value);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(key, Arrays.hashCode(value));
    }
    
    /**
     * Create a header from key-value string pair
     */
    public static WaqitiKafkaHeader of(String key, String value) {
        return new WaqitiKafkaHeader(key, value);
    }
    
    /**
     * Create a header from key-value byte array pair
     */
    public static WaqitiKafkaHeader of(String key, byte[] value) {
        return new WaqitiKafkaHeader(key, value);
    }
}