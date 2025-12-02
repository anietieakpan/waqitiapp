package com.waqiti.payment.invoice;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Invoice entity for payment invoicing
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Invoice {

    private String id;
    private String invoiceNumber;
    private String businessProfileId;
    private String customerId;
    private String status;
    private String currency;
    private String paymentMethod;

    private BigDecimal totalAmount;
    private BigDecimal paidAmount;

    private LocalDate issueDate;
    private LocalDate dueDate;
    private LocalDate paidDate;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // Helper methods for analytics
    public boolean isPaid() {
        return "PAID".equalsIgnoreCase(status);
    }

    public boolean isOverdue() {
        return dueDate != null && LocalDate.now().isAfter(dueDate) && !isPaid();
    }

    public BigDecimal getRemainingAmount() {
        if (paidAmount == null) {
            return totalAmount;
        }
        return totalAmount.subtract(paidAmount);
    }
}
