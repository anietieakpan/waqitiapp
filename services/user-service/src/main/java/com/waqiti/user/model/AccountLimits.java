package com.waqiti.user.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Entity
@Table(name = "account_limits")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AccountLimits {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;
    
    private String userId;
    private BigDecimal dailyTransactionLimit;
    private BigDecimal monthlyTransactionLimit;
    private BigDecimal maxSingleTransactionAmount;
    private Boolean internationalTransferEnabled;
    private Boolean cryptoTradingEnabled;
}