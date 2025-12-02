package com.waqiti.common.database.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Database resource usage statistics
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResourceUsage {
    
    /**
     * CPU usage
     */
    private CpuUsage cpuUsage;
    
    /**
     * Memory usage
     */
    private MemoryUsage memoryUsage;
    
    /**
     * Disk usage
     */
    private DiskUsage diskUsage;
    
    /**
     * Network usage
     */
    private NetworkUsage networkUsage;
    
    /**
     * Session/Connection usage
     */
    private SessionUsage sessionUsage;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CpuUsage {
        private double totalCpuPercentage;
        private double userCpuPercentage;
        private double systemCpuPercentage;
        private double iowaitPercentage;
        private int coreCount;
        private Map<Integer, Double> perCoreUsage;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MemoryUsage {
        private long totalMemoryBytes;
        private long usedMemoryBytes;
        private long freeMemoryBytes;
        private long bufferPoolBytes;
        private long sharedMemoryBytes;
        private double memoryUtilizationPercentage;
        private Map<String, Long> memoryByComponent;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DiskUsage {
        private long totalDiskSpaceBytes;
        private long usedDiskSpaceBytes;
        private long freeDiskSpaceBytes;
        private double diskUtilizationPercentage;
        private Map<String, DiskPartitionUsage> partitionUsage;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DiskPartitionUsage {
        private String partitionName;
        private String mountPoint;
        private long totalBytes;
        private long usedBytes;
        private long freeBytes;
        private double utilizationPercentage;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class NetworkUsage {
        private double inboundBandwidthMbps;
        private double outboundBandwidthMbps;
        private long packetsReceived;
        private long packetsSent;
        private long bytesReceived;
        private long bytesSent;
        private double packetLossPercentage;
        private double averageLatencyMs;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SessionUsage {
        private int totalSessions;
        private int activeSessions;
        private int idleSessions;
        private int blockedSessions;
        private int maxSessions;
        private double sessionUtilizationPercentage;
        private Map<String, Integer> sessionsByUser;
        private Map<String, Integer> sessionsByApplication;
    }
}