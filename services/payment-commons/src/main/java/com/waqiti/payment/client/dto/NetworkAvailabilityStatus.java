package com.waqiti.payment.client.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NetworkAvailabilityStatus {
    private Map<String, Boolean> networkStatus;
    private LocalDateTime lastChecked;
    private String overallStatus;
    private Map<String, String> networkMessages;
    
    public boolean isAllNetworksAvailable() {
        return networkStatus.values().stream().allMatch(Boolean::booleanValue);
    }
    
    public int getAvailableNetworkCount() {
        return (int) networkStatus.values().stream().filter(Boolean::booleanValue).count();
    }
}