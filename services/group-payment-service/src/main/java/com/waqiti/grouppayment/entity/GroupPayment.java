package com.waqiti.grouppayment.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Entity
@Table(name = "group_payments")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GroupPayment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String groupPaymentId;

    @Column(nullable = false)
    private String createdBy;

    @Column(nullable = false)
    private String title;

    private String description;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal totalAmount;

    @Column(nullable = false)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private GroupPaymentStatus status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SplitType splitType;

    @Column
    private String receiptImageUrl;

    @Column
    private String category;

    @Column
    private Instant dueDate;

    @OneToMany(mappedBy = "groupPayment", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<GroupPaymentParticipant> participants;

    @OneToMany(mappedBy = "groupPayment", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<GroupPaymentItem> items;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private Instant updatedAt;

    @Version
    private Long version;

    public enum GroupPaymentStatus {
        DRAFT,
        ACTIVE,
        PARTIALLY_PAID,
        COMPLETED,
        CANCELLED,
        EXPIRED
    }

    public enum SplitType {
        EQUAL,
        CUSTOM_AMOUNTS,
        PERCENTAGE,
        BY_ITEM,
        BY_WEIGHT
    }
}