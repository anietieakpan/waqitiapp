package com.waqiti.common.database.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Database lock statistics
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LockStats {
    
    /**
     * Total locks
     */
    private long totalLocks;
    
    /**
     * Active locks
     */
    private long activeLocks;
    
    /**
     * Waiting locks
     */
    private long waitingLocks;
    
    /**
     * Lock breakdown by type
     */
    private Map<LockType, Long> lockTypeBreakdown;
    
    /**
     * Lock wait statistics
     */
    private LockWaitStats lockWaitStats;
    
    /**
     * Deadlock information
     */
    private DeadlockInfo deadlockInfo;
    
    /**
     * Current locks
     */
    private List<CurrentLock> currentLocks;
    
    /**
     * Lock escalations
     */
    private LockEscalationStats escalationStats;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LockWaitStats {
        private double averageWaitTimeMs;
        private double maxWaitTimeMs;
        private long totalWaitTimeMs;
        private long waitCount;
        private Map<LockType, Double> averageWaitByType;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DeadlockInfo {
        private long deadlockCount;
        private Instant lastDeadlock;
        private List<DeadlockEvent> recentDeadlocks;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DeadlockEvent {
        private Instant timestamp;
        private List<String> involvedQueries;
        private List<String> involvedSessions;
        private String victimSession;
        private String deadlockGraph;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CurrentLock {
        private String lockId;
        private LockType lockType;
        private String objectName;
        private String sessionId;
        private String queryText;
        private Instant acquiredAt;
        private long holdTimeMs;
        private boolean blocking;
        private List<String> blockedSessions;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LockEscalationStats {
        private long escalationCount;
        private Map<String, Long> escalationByTable;
        private double escalationRate;
    }
    
    public enum LockType {
        SHARED,
        EXCLUSIVE,
        UPDATE,
        INTENT_SHARED,
        INTENT_EXCLUSIVE,
        SCHEMA,
        BULK_UPDATE,
        KEY_RANGE
    }
}