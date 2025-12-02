package com.waqiti.social.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "payment_comments")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentComment {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    
    private UUID paymentId;
    private UUID userId;
    private String comment;
    private LocalDateTime createdAt;
}