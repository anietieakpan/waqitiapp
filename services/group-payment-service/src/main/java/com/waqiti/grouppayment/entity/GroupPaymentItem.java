package com.waqiti.grouppayment.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Entity
@Table(name = "group_payment_items")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GroupPaymentItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "group_payment_id", nullable = false)
    private GroupPayment groupPayment;

    @Column(nullable = false)
    private String name;

    @Column
    private String description;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false)
    private Integer quantity;

    @Column
    private String category;

    @OneToMany(mappedBy = "item", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<GroupPaymentItemParticipant> itemParticipants;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;
    
    // CRITICAL SECURITY FIX: Add optimistic locking to prevent concurrent modifications
    @Version
    private Long version;

    public BigDecimal getTotalAmount() {
        return amount.multiply(BigDecimal.valueOf(quantity));
    }
}