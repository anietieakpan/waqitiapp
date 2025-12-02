package com.waqiti.discovery.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.net.URI;
import java.util.Map;

/**
 * Service Instance DTO
 *
 * @author Discovery Service Team
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ServiceInstanceDto {
    private String instanceId;
    private String serviceId;
    private String host;
    private Integer port;
    private Boolean secure;
    private URI uri;
    private Map<String, String> metadata;
}
