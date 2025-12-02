package com.waqiti.payment.integration.dwolla.dto;

import lombok.Builder;
import lombok.Value;

import java.time.LocalDateTime;
import java.util.Map;

@Value
@Builder
public class DwollaTransferResponse {
    String id;
    String status;
    LocalDateTime created;
    Map<String, String> metadata;
    
    public Map<String, Object> toMap() {
        return Map.of(
            "id", id,
            "status", status,
            "created", created.toString(),
            "metadata", metadata != null ? metadata : Map.of()
        );
    }
}