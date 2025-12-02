package com.waqiti.common.messaging.recovery.model;

import lombok.*;
import java.time.LocalDateTime;
import java.util.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CompensationResult {
    private boolean success;
    private String message;
    private Map<String, Object> details;
}