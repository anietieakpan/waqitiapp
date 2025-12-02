package com.waqiti.payment.cash.provider;

// Custom exception classes
public class NetworkUnavailableException extends RuntimeException {
    public NetworkUnavailableException(String message) { super(message); }
}
