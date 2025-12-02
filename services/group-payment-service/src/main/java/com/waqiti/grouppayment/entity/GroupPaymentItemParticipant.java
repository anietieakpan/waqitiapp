package com.waqiti.grouppayment.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity
@Table(name = "group_payment_item_participants")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GroupPaymentItemParticipant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "item_id", nullable = false)
    private GroupPaymentItem item;

    @Column(nullable = false)
    private String userId;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal share;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ShareType shareType;
    
    // CRITICAL SECURITY FIX: Add optimistic locking to prevent concurrent modifications
    @Version
    private Long version;

    public enum ShareType {
        AMOUNT,
        PERCENTAGE,
        QUANTITY
    }
}