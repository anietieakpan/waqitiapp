package com.waqiti.social.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "payment_qr_codes")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentQRCode {
    
    @Id
    private String id;
    
    private UUID userId;
    private BigDecimal amount;
    private String currency;
    private Integer usageCount;
    private LocalDateTime lastUsedAt;
    private LocalDateTime createdAt;
}