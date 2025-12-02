package com.waqiti.discovery.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Instance DTO
 * Information about a service instance
 *
 * @author Discovery Service Team
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InstanceDto {
    private String instanceId;
    private String hostName;
    private String ipAddress;
    private Integer port;
    private Integer securePort;
    private String status;
    private String homePageUrl;
    private String healthCheckUrl;
    private Map<String, String> metadata;
    private Long lastUpdatedTimestamp;
}
