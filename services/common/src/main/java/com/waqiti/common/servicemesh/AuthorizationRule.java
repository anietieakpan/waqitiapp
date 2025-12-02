package com.waqiti.common.servicemesh;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * Authorization rule for service mesh policies
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthorizationRule {
    private String source;
    private String operation;
    private List<String> paths;
    private List<String> methods;
    private Map<String, String> headers;
    private Map<String, String> claims;
}