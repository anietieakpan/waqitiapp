package com.waqiti.payment.events;

/**
 * Placeholder classes for Avro generated objects
 * In a real implementation, these would be generated from the .avsc schema files
 */
public class PaymentEventAvro {
    public static Builder newBuilder() { return new Builder(); }
    
    public String getPaymentId() { return ""; }
    public String getEventType() { return ""; }
    
    public static class Builder {
        public Builder setEventId(String eventId) { return this; }
        public Builder setEventType(PaymentEventTypeAvro eventType) { return this; }
        public Builder setPaymentId(String paymentId) { return this; }
        public Builder setUserId(String userId) { return this; }
        public Builder setAmount(MonetaryAmountAvro amount) { return this; }
        public Builder setPaymentMethod(PaymentMethodAvro method) { return this; }
        public Builder setStatus(PaymentStatusAvro status) { return this; }
        public Builder setTimestamp(long timestamp) { return this; }
        public Builder setVersion(String version) { return this; }
        public PaymentEventAvro build() { return new PaymentEventAvro(); }
    }
}
