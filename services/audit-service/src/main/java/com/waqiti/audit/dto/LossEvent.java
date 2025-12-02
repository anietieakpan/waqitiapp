package com.waqiti.audit.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LossEvent {
    private LocalDateTime eventDate;
    private String eventType;
    private BigDecimal lossAmount;
    private BigDecimal recoveryAmount;
}
