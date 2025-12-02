package com.waqiti.notification.client.fallback;

import com.waqiti.notification.client.PaymentServiceClient;
import com.waqiti.notification.client.dto.PendingPaymentResponse;
import com.waqiti.notification.client.dto.PaymentReminderData;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Slf4j
@Component
public class PaymentServiceClientFallback implements PaymentServiceClient {
    
    @Override
    public List<PendingPaymentResponse> getUsersWithPendingPayments() {
        log.warn("Fallback: Unable to fetch users with pending payments from payment service");
        return Collections.emptyList();
    }
    
    @Override
    public List<PaymentReminderData> getUserPendingPayments(UUID userId) {
        log.warn("Fallback: Unable to fetch pending payments for user {} from payment service", userId);
        return Collections.emptyList();
    }
    
    @Override
    public List<PendingPaymentResponse> getScheduledPayments(LocalDate date) {
        log.warn("Fallback: Unable to fetch scheduled payments for date {} from payment service", date);
        return Collections.emptyList();
    }
    
    @Override
    public List<PendingPaymentResponse> getRecurringPaymentsDue() {
        log.warn("Fallback: Unable to fetch recurring payments due from payment service");
        return Collections.emptyList();
    }
    
    @Override
    public List<PaymentReminderData> getUserRecurringPayments(UUID userId) {
        log.warn("Fallback: Unable to fetch recurring payments for user {} from payment service", userId);
        return Collections.emptyList();
    }
}