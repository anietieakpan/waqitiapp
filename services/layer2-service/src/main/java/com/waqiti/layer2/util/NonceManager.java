package com.waqiti.layer2.util;

import org.springframework.stereotype.Component;

import java.math.BigInteger;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Thread-safe nonce management for preventing replay attacks
 */
@Component
public class NonceManager {

    private final ConcurrentHashMap<String, AtomicLong> nonces = new ConcurrentHashMap<>();

    /**
     * Get next nonce for address (thread-safe, sequential)
     *
     * @param address Ethereum address
     * @return Next nonce value
     */
    public BigInteger getNextNonce(String address) {
        return BigInteger.valueOf(
            nonces.computeIfAbsent(address, k -> new AtomicLong(0))
                  .incrementAndGet()
        );
    }

    /**
     * Get current nonce without incrementing
     *
     * @param address Ethereum address
     * @return Current nonce value
     */
    public BigInteger getCurrentNonce(String address) {
        AtomicLong nonce = nonces.get(address);
        return nonce == null ? BigInteger.ZERO : BigInteger.valueOf(nonce.get());
    }

    /**
     * Reset nonce for address (use with caution)
     *
     * @param address Ethereum address
     */
    public void resetNonce(String address) {
        nonces.remove(address);
    }

    /**
     * Set nonce to specific value (for synchronization with L1)
     *
     * @param address Ethereum address
     * @param nonce Nonce value to set
     */
    public void setNonce(String address, BigInteger nonce) {
        nonces.put(address, new AtomicLong(nonce.longValue()));
    }
}
