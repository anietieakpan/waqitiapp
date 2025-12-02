package com.waqiti.reconciliation.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AccountAlert {
    private UUID alertId;
    private UUID accountId;
    private String alertType;
    private String severity;
    private String message;
    private LocalDateTime createdAt;
    private Boolean acknowledged;
}
