package com.waqiti.investment.exception;

public class OrderNotFoundException extends InvestmentException {
    
    public OrderNotFoundException(String orderId) {
        super("Investment order not found: " + orderId);
    }
}