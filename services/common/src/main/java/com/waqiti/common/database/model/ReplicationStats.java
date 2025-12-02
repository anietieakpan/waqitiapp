package com.waqiti.common.database.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Database replication statistics
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReplicationStats {
    
    /**
     * Replication status
     */
    private ReplicationStatus status;
    
    /**
     * Primary/Master info
     */
    private NodeInfo primaryNode;
    
    /**
     * Replica nodes
     */
    private List<ReplicaInfo> replicas;
    
    /**
     * Replication lag
     */
    private ReplicationLag replicationLag;
    
    /**
     * Throughput metrics
     */
    private ThroughputMetrics throughput;
    
    /**
     * Conflict statistics
     */
    private ConflictStats conflictStats;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class NodeInfo {
        private String nodeId;
        private String hostName;
        private String ipAddress;
        private int port;
        private boolean healthy;
        private Instant lastHeartbeat;
    }
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode(callSuper = false)
    public static class ReplicaInfo extends NodeInfo {
        private ReplicaState state;
        private long lagBytes;
        private long lagSeconds;
        private double syncPercentage;
        private Instant lastSyncTime;
        private Map<String, Object> replicaMetrics;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ReplicationLag {
        private double averageLagSeconds;
        private double maxLagSeconds;
        private double minLagSeconds;
        private Map<String, Double> lagByReplica;
        private List<LagEvent> lagHistory;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LagEvent {
        private Instant timestamp;
        private String replicaId;
        private double lagSeconds;
        private String reason;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ThroughputMetrics {
        private double transactionsPerSecond;
        private double bytesPerSecond;
        private long totalTransactions;
        private long totalBytes;
        private Map<String, Double> throughputByReplica;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ConflictStats {
        private long totalConflicts;
        private Map<ConflictType, Long> conflictsByType;
        private List<ConflictEvent> recentConflicts;
        private double conflictRate;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ConflictEvent {
        private Instant timestamp;
        private ConflictType type;
        private String tableName;
        private String resolution;
        private Map<String, Object> details;
    }
    
    public enum ReplicationStatus {
        ACTIVE,
        PAUSED,
        STOPPED,
        ERROR,
        INITIALIZING,
        CATCHING_UP
    }
    
    public enum ReplicaState {
        ONLINE,
        OFFLINE,
        SYNCING,
        ERROR,
        MAINTENANCE
    }
    
    public enum ConflictType {
        INSERT_INSERT,
        UPDATE_UPDATE,
        UPDATE_DELETE,
        DELETE_DELETE,
        SCHEMA_MISMATCH
    }
}