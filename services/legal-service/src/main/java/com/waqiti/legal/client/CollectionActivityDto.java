package com.waqiti.legal.client;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Collection Activity DTO
 *
 * @author Waqiti Legal Team
 * @version 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CollectionActivityDto implements Serializable {

    private static final long serialVersionUID = 1L;

    private String activityId;
    private String customerId;
    private String accountId;
    private String activityType; // PHONE_CALL, LETTER, EMAIL, LEGAL_ACTION
    private String status; // ACTIVE, STOPPED, COMPLETED
    private LocalDateTime scheduledDate;
    private LocalDateTime completedDate;
    private String stoppedReason;
    private String stoppedByReferenceId; // Bankruptcy case ID if stopped due to stay
}
