package com.waqiti.common.messaging.recovery.model;

import lombok.*;
import java.time.LocalDateTime;
import java.util.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecoveryResult {
    private String eventId;
    private String status;
    private String message;
    private LocalDateTime timestamp;
    private LocalDateTime nextRetryAt;

    public static RecoveryResult failed(String eventId, String message) {
        return RecoveryResult.builder()
                .eventId(eventId)
                .status("FAILED")
                .message(message)
                .timestamp(LocalDateTime.now())
                .build();
    }
}