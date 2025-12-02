package com.waqiti.payment.client.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmailNotificationRequest {
    private UUID userId;
    private String to;
    private String subject;
    private String body;
    private boolean isHtml;
    private List<String> cc;
    private List<String> bcc;
    private Map<String, String> attachments;
}