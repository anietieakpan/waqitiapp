package com.waqiti.discovery.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Service Discovery Event
 * Event for service registry changes
 *
 * @author Discovery Service Team
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ServiceDiscoveryEvent {
    private String serviceName;
    private String eventType;
    private String status;
    private Instant timestamp;

    public static ServiceDiscoveryEvent serviceStatusChanged(String serviceName, String newStatus) {
        return ServiceDiscoveryEvent.builder()
            .serviceName(serviceName)
            .eventType("STATUS_CHANGED")
            .status(newStatus)
            .timestamp(Instant.now())
            .build();
    }
}
