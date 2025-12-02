package com.waqiti.common.servicemesh;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Request model for creating CORS policies
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CorsRequest {
    private String policyName;
    private String serviceName;
    private List<String> allowedOrigins;
    private List<String> allowedMethods;
    private List<String> allowedHeaders;
    private List<String> exposedHeaders;
    private boolean allowCredentials;
    private long maxAge;
}