package com.waqiti.payment.events;

public class MonetaryAmountAvro {
    public static Builder newBuilder() { return new Builder(); }
    
    public static class Builder {
        public Builder setValue(String value) { return this; }
        public Builder setCurrency(String currency) { return this; }
        public MonetaryAmountAvro build() { return new MonetaryAmountAvro(); }
    }
}
