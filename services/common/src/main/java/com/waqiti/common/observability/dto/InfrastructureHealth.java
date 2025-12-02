package com.waqiti.common.observability.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class InfrastructureHealth {
    private ContainerStatus containerStatus;
    private NetworkStatus networkStatus;
    private StorageStatus storageStatus;
    private String kubernetesCluster;
    private String loadBalancer;
    private boolean isHealthy;
    
    public ContainerStatus getContainerStatus() { return containerStatus; }
    public NetworkStatus getNetworkStatus() { return networkStatus; }
    public StorageStatus getStorageStatus() { return storageStatus; }
}