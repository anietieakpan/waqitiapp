package com.waqiti.webhook.dto;

import com.waqiti.webhook.model.WebhookEventType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateWebhookSubscriptionRequest {
    
    @NotBlank(message = "Webhook URL is required")
    @Pattern(regexp = "^https?://.*", message = "URL must be a valid HTTP or HTTPS URL")
    private String url;
    
    @NotEmpty(message = "At least one event type must be specified")
    private List<WebhookEventType> eventTypes;
    
    private String description;
    private String secret;
    private Map<String, String> headers;
    private Integer timeout;
    private Integer maxRetries;
    
    // These will be set by the controller
    private String userId;
    private String clientId;
}