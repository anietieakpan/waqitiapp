package com.waqiti.webhook.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.validator.constraints.URL;

import java.util.List;
import java.util.Map;

/**
 * DTO for updating webhook subscription
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateWebhookSubscriptionRequest {

    private String subscriptionId;

    @URL(message = "Invalid URL format")
    @Size(max = 500, message = "URL must not exceed 500 characters")
    private String url;

    @Size(max = 500, message = "Secret must not exceed 500 characters")
    private String secret;

    private List<String> eventTypes;

    @Size(max = 500, message = "Description must not exceed 500 characters")
    private String description;

    private Map<String, String> headers;

    @Min(value = 1000, message = "Timeout must be at least 1000ms")
    private Integer timeout;

    @Min(value = 0, message = "Max retries must be at least 0")
    private Integer maxRetries;

    private Boolean isActive;
}
