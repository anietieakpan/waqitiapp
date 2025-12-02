package com.waqiti.common.database;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * Represents database resource requirements for query optimization
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class DatabaseResourceRequirements {
    private int estimatedRows;
    private long estimatedMemoryBytes;
    private double estimatedCpuUnits;
    private long estimatedIoOperations;
    private int requiredConnections;
    private boolean requiresTransaction;
    private boolean requiresLocking;
    private int estimatedDurationMs;
    private int cpuCores;
    private int memoryGb;
    private int storageIops;
    private int connectionCount;
    
    public int getCpuCores() {
        return cpuCores > 0 ? cpuCores : (int)Math.ceil(estimatedCpuUnits);
    }
    
    public int getMemoryGb() {
        return memoryGb > 0 ? memoryGb : (int)Math.ceil(estimatedMemoryBytes / (1024.0 * 1024.0 * 1024.0));
    }
    
    public int getStorageIops() {
        return storageIops > 0 ? storageIops : (int)estimatedIoOperations;
    }
    
    public int getConnectionCount() {
        return connectionCount > 0 ? connectionCount : requiredConnections;
    }
}