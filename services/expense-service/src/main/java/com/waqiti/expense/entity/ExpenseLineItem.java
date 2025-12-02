package com.waqiti.expense.entity;

import com.waqiti.expense.domain.ExpenseCategory;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Individual line item within an expense.
 * Represents detailed breakdown of purchases within a single expense.
 */
@Entity
@Table(name = "expense_line_items")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExpenseLineItem {
    
    @Id
    @GeneratedValue
    private UUID id;
    
    @Column(name = "expense_id", nullable = false)
    private UUID expenseId;
    
    @Column(name = "description", nullable = false, length = 500)
    private String description;
    
    @Column(name = "product_code")
    private String productCode;
    
    @Column(name = "quantity", precision = 10, scale = 3)
    private BigDecimal quantity;
    
    @Column(name = "unit_price", precision = 12, scale = 2)
    private BigDecimal unitPrice;
    
    @Column(name = "total_price", precision = 12, scale = 2, nullable = false)
    private BigDecimal totalPrice;
    
    @Column(name = "discount_amount", precision = 12, scale = 2)
    private BigDecimal discountAmount;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "category")
    private ExpenseCategory category;
    
    @Column(name = "is_taxable")
    private Boolean taxable;
    
    @Column(name = "tax_amount", precision = 12, scale = 2)
    private BigDecimal taxAmount;
    
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
    
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
    
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (quantity == null) {
            quantity = BigDecimal.ONE;
        }
        if (taxable == null) {
            taxable = true;
        }
    }
    
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}