package com.waqiti.common.database;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Represents resource requirements for database operations
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResourceRequirements {
    private double cpuUsage;
    private long memoryUsageBytes;
    private double ioUsage;
    private int connectionCount;
    private long estimatedDurationMs;
    private QueryPriority priority;
    private boolean requiresTransaction;
    private boolean requiresIsolation;
    
    // Explicit getter for compilation issues
    public long getEstimatedDurationMs() { return estimatedDurationMs; }
    
    public static ResourceRequirements createDefault() {
        return ResourceRequirements.builder()
            .cpuUsage(0.1)
            .memoryUsageBytes(10_000_000L) // 10MB
            .ioUsage(0.05)
            .connectionCount(1)
            .estimatedDurationMs(100L)
            .priority(QueryPriority.MEDIUM)
            .requiresTransaction(false)
            .requiresIsolation(false)
            .build();
    }
    
    public static ResourceRequirements createHighLoad() {
        return ResourceRequirements.builder()
            .cpuUsage(0.5)
            .memoryUsageBytes(100_000_000L) // 100MB
            .ioUsage(0.3)
            .connectionCount(3)
            .estimatedDurationMs(5000L)
            .priority(QueryPriority.HIGH)
            .requiresTransaction(true)
            .requiresIsolation(true)
            .build();
    }
    
    public static ResourceRequirements createLowLoad() {
        return ResourceRequirements.builder()
            .cpuUsage(0.02)
            .memoryUsageBytes(1_000_000L) // 1MB
            .ioUsage(0.01)
            .connectionCount(1)
            .estimatedDurationMs(50L)
            .priority(QueryPriority.LOW)
            .requiresTransaction(false)
            .requiresIsolation(false)
            .build();
    }
    
    public ResourceRequirements scale(double factor) {
        return ResourceRequirements.builder()
            .cpuUsage(this.cpuUsage * factor)
            .memoryUsageBytes((long)(this.memoryUsageBytes * factor))
            .ioUsage(this.ioUsage * factor)
            .connectionCount(Math.max(1, (int)(this.connectionCount * factor)))
            .estimatedDurationMs((long)(this.estimatedDurationMs * factor))
            .priority(this.priority)
            .requiresTransaction(this.requiresTransaction)
            .requiresIsolation(this.requiresIsolation)
            .build();
    }
    
    public ResourceRequirements add(ResourceRequirements other) {
        return ResourceRequirements.builder()
            .cpuUsage(this.cpuUsage + other.cpuUsage)
            .memoryUsageBytes(this.memoryUsageBytes + other.memoryUsageBytes)
            .ioUsage(this.ioUsage + other.ioUsage)
            .connectionCount(this.connectionCount + other.connectionCount)
            .estimatedDurationMs(Math.max(this.estimatedDurationMs, other.estimatedDurationMs))
            .priority(this.priority.isHigherPriorityThan(other.priority) ? this.priority : other.priority)
            .requiresTransaction(this.requiresTransaction || other.requiresTransaction)
            .requiresIsolation(this.requiresIsolation || other.requiresIsolation)
            .build();
    }
    
    public boolean exceedsThreshold(ResourceRequirements threshold) {
        return this.cpuUsage > threshold.cpuUsage ||
               this.memoryUsageBytes > threshold.memoryUsageBytes ||
               this.ioUsage > threshold.ioUsage ||
               this.connectionCount > threshold.connectionCount ||
               this.estimatedDurationMs > threshold.estimatedDurationMs;
    }
    
    public static ResourceRequirements empty() {
        return ResourceRequirements.builder()
            .cpuUsage(0.0)
            .memoryUsageBytes(0L)
            .ioUsage(0.0)
            .connectionCount(0)
            .estimatedDurationMs(0L)
            .priority(QueryPriority.LOW)
            .requiresTransaction(false)
            .requiresIsolation(false)
            .build();
    }
    
    public double getCpuUnits() {
        return cpuUsage;
    }
    
    public long getMemoryMb() {
        return memoryUsageBytes / (1024 * 1024);
    }
    
    public long getIoOperations() {
        return (long)(ioUsage * 1000);
    }
    
    public String getResourceType() {
        if (cpuUsage > 0.5) return "CPU_INTENSIVE";
        if (memoryUsageBytes > 100_000_000L) return "MEMORY_INTENSIVE";
        if (ioUsage > 0.3) return "IO_INTENSIVE";
        if (estimatedDurationMs > 10000L) return "LONG_RUNNING";
        return "STANDARD";
    }
    
    public int getCpuCores() {
        return Math.max(1, (int)Math.ceil(cpuUsage));
    }
    
    public double getMemoryGb() {
        return memoryUsageBytes / (1024.0 * 1024.0 * 1024.0);
    }
}