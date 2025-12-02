package com.waqiti.common.observability.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class EscalationStatus {
    private boolean isEscalationRequired;
    private EscalationLevel level;
    private List<String> notifiedStakeholders;
    private LocalDateTime escalationTime;
    private String escalationReason;
    
    public boolean isEscalationRequired() {
        return isEscalationRequired;
    }
}

enum EscalationLevel {
    LEVEL_1, LEVEL_2, LEVEL_3, EXECUTIVE
}