package com.waqiti.grouppayment.exception;

public class GroupPaymentNotFoundException extends RuntimeException {
    public GroupPaymentNotFoundException(String message) {
        super(message);
    }
}