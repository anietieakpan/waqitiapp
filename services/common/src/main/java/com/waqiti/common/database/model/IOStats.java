package com.waqiti.common.database.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Database I/O statistics
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IOStats {
    
    /**
     * Read statistics
     */
    private ReadStats readStats;
    
    /**
     * Write statistics
     */
    private WriteStats writeStats;
    
    /**
     * File statistics
     */
    private Map<String, FileStats> fileStats;
    
    /**
     * Overall I/O metrics
     */
    private OverallIOMetrics overallMetrics;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ReadStats {
        private long totalReads;
        private long dataReads;
        private long indexReads;
        private long tempReads;
        private double averageReadTimeMs;
        private double maxReadTimeMs;
        private long bytesRead;
        private double readThroughputMBps;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class WriteStats {
        private long totalWrites;
        private long dataWrites;
        private long indexWrites;
        private long logWrites;
        private double averageWriteTimeMs;
        private double maxWriteTimeMs;
        private long bytesWritten;
        private double writeThroughputMBps;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FileStats {
        private String fileName;
        private String fileType;
        private long sizeBytes;
        private long reads;
        private long writes;
        private double averageIOTimeMs;
        private double utilizationPercentage;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OverallIOMetrics {
        private double ioUtilizationPercentage;
        private long pendingIORequests;
        private double averageQueueLength;
        private double averageIOWaitTimeMs;
        private Map<String, Double> ioDistribution;
    }
}