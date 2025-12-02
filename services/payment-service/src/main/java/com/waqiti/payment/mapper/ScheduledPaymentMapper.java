/**
 * Scheduled Payment Mapper
 * Maps between entity and DTOs
 */
package com.waqiti.payment.mapper;

import com.waqiti.payment.entity.ScheduledPayment;
import com.waqiti.payment.dto.CreateScheduledPaymentRequest;
import com.waqiti.payment.dto.ScheduledPaymentResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

import java.time.LocalDate;

@Mapper(componentModel = "spring")
public interface ScheduledPaymentMapper {
    
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "completedPayments", ignore = true)
    @Mapping(target = "failedPayments", ignore = true)
    @Mapping(target = "lastPaymentDate", ignore = true)
    @Mapping(target = "lastPaymentStatus", ignore = true)
    @Mapping(target = "lastPaymentId", ignore = true)
    @Mapping(target = "nextPaymentDate", ignore = true)
    @Mapping(target = "pauseReason", ignore = true)
    @Mapping(target = "cancellationReason", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "pausedAt", ignore = true)
    @Mapping(target = "cancelledAt", ignore = true)
    ScheduledPayment toEntity(CreateScheduledPaymentRequest request);
    
    @Mapping(target = "isActive", expression = "java(payment.isActive())")
    @Mapping(target = "isDue", expression = "java(payment.isDue())")
    @Mapping(target = "remainingPayments", expression = "java(calculateRemainingPayments(payment))")
    @Mapping(target = "nextReminderDate", expression = "java(calculateNextReminderDate(payment))")
    ScheduledPaymentResponse toResponse(ScheduledPayment payment);
    
    default Integer calculateRemainingPayments(ScheduledPayment payment) {
        if (payment.getTotalPayments() != null) {
            return Math.max(0, payment.getTotalPayments() - payment.getCompletedPayments());
        }
        // Return -1 to indicate unlimited/recurring payments
        return -1;
    }
    
    default LocalDate calculateNextReminderDate(ScheduledPayment payment) {
        if (payment.getSendReminder() && payment.getNextPaymentDate() != null 
                && payment.getReminderDaysBefore() != null) {
            return payment.getNextPaymentDate().minusDays(payment.getReminderDaysBefore());
        }
        // If no reminder configured, return the payment date itself as fallback
        return payment.getNextPaymentDate();
    }
}