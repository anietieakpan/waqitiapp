package com.waqiti.common.servicemesh;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Request model for creating authorization policies
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthorizationRequest {
    private String policyName;
    private String serviceName;
    private List<AuthorizationRule> rules;
    private PolicyAction action;
}