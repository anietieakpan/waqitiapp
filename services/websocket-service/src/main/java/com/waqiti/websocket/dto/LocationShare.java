package com.waqiti.websocket.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LocationShare {
    private String userId;
    
    @NotNull
    private Double latitude;
    
    @NotNull
    private Double longitude;
    
    private String address;
    private String placeName;
    private Instant timestamp;
    private Long expiresAt;
    private List<String> recipientIds;
    private String accuracy;
    private String shareType;
    
    public enum ShareType {
        TEMPORARY,
        LIVE,
        STATIC
    }
}