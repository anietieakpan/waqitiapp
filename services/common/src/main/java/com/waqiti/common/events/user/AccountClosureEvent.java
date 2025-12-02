package com.waqiti.common.events.user;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Event representing an account closure request
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AccountClosureEvent {
    private String eventId;
    private String userId;
    private String closureReason;
    private String initiatedBy;
    private String initiationType;
    private String closureNotes;
    private String feedbackProvided;
    private LocalDateTime scheduledClosureDate;
    private boolean anonymizeData;
    private String withdrawalMethod;
    private Map<String, Object> withdrawalDetails;
    private String authenticationToken;
    private Map<String, String> securityAnswers;
    private LocalDateTime timestamp;
}