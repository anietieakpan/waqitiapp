package com.waqiti.common.servicemesh;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Service Mesh Readiness Indicator
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ServiceMeshReadinessIndicator {

    private final ServiceMeshManager serviceMeshManager;

    public boolean isReady() {
        // Check if service mesh is ready
        return serviceMeshManager != null;
    }
}