package com.waqiti.common.fraud.dto;

import lombok.Builder;
import lombok.Data;
import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.LocalDateTime;

@Data
@Builder
public class AlertResolutionEvent {
    private String alertId;
    private String resolvedBy;
    private String resolution;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime timestamp;
}