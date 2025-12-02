package com.waqiti.payment.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

/**
 * Balance Alert Entity
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "balance_alerts")
public class BalanceAlert {

    @Id
    private String id;
    private String accountId;
    private String alertType;
    private Double currentBalance;
    private Double threshold;
    private String severity;
    private LocalDateTime alertTime;
    private String correlationId;
    private Boolean resolved;
    private LocalDateTime resolvedAt;
    private String resolvedBy;
}
