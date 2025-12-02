package com.waqiti.notification.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List; /**
 * Response for notification list operations
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class NotificationListResponse {
    private List<NotificationResponse> notifications;
    private long unreadCount;
    private int totalPages;
    private long totalElements;
    private int page;
    private int size;
}
