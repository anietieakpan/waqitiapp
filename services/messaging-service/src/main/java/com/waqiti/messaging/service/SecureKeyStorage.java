package com.waqiti.messaging.service;

public interface SecureKeyStorage {
    
    /**
     * Store a key securely
     * @param key The storage key identifier
     * @param value The value to store (already encrypted if needed)
     */
    void store(String key, String value);
    
    /**
     * Retrieve a key securely
     * @param key The storage key identifier
     * @return The stored value or null if not found
     */
    String retrieve(String key);
    
    /**
     * Delete a key
     * @param key The storage key identifier
     */
    void delete(String key);
    
    /**
     * Check if a key exists
     * @param key The storage key identifier
     * @return true if the key exists
     */
    boolean exists(String key);
}