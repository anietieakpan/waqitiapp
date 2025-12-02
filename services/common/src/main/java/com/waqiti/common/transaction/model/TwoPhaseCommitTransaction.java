package com.waqiti.common.transaction.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Enterprise-grade Two-Phase Commit (2PC) Transaction model.
 * Implements distributed transaction coordination using the two-phase commit protocol
 * for ensuring ACID properties across multiple resource managers.
 */
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class TwoPhaseCommitTransaction implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    /**
     * Unique transaction identifier
     */
    @NotNull
    @Builder.Default
    private String transactionId = UUID.randomUUID().toString();
    
    /**
     * Global transaction ID for distributed systems
     */
    private String globalTransactionId;
    
    /**
     * Transaction name/description
     */
    @NotNull(message = "Transaction name is required")
    @Size(min = 1, max = 200)
    private String name;
    
    /**
     * Current phase of the transaction
     */
    @Builder.Default
    private TransactionPhase phase = TransactionPhase.INITIAL;
    
    /**
     * Overall transaction state
     */
    @Builder.Default
    private TransactionState state = TransactionState.ACTIVE;
    
    /**
     * List of participating resource managers
     */
    @Builder.Default
    private List<Participant> participants = new ArrayList<>();
    
    /**
     * Coordinator information
     */
    @NotNull
    private Coordinator coordinator;
    
    /**
     * Transaction context data
     */
    @Builder.Default
    private Map<String, Object> context = new HashMap<>();
    
    /**
     * Metadata for the transaction
     */
    @Builder.Default
    private Map<String, Object> metadata = new HashMap<>();

    /**
     * Transaction result (for compatibility)
     */
    private Object result;

    public Object getResult() {
        return result;
    }
    
    /**
     * Vote results from participants
     */
    @Builder.Default
    private Map<String, Vote> votes = new ConcurrentHashMap<>();
    
    /**
     * Transaction creation timestamp
     */
    @NotNull
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
    
    /**
     * Prepare phase start timestamp
     */
    private LocalDateTime prepareStartedAt;
    
    /**
     * Prepare phase completion timestamp
     */
    private LocalDateTime prepareCompletedAt;
    
    /**
     * Commit/Abort decision timestamp
     */
    private LocalDateTime decisionAt;
    
    /**
     * Transaction completion timestamp
     */
    private LocalDateTime completedAt;
    
    /**
     * Timeout for prepare phase (in seconds)
     */
    @Builder.Default
    private long prepareTimeoutSeconds = 30;
    
    /**
     * Timeout for commit phase (in seconds)
     */
    @Builder.Default
    private long commitTimeoutSeconds = 60;
    
    /**
     * Maximum retry attempts
     */
    @Builder.Default
    private int maxRetries = 3;
    
    /**
     * Current retry count
     */
    @Builder.Default
    private int retryCount = 0;
    
    /**
     * Error message if transaction failed
     */
    private String errorMessage;
    
    /**
     * Decision made by coordinator
     */
    private Decision decision;
    
    /**
     * Transaction log for recovery
     */
    @Builder.Default
    private List<LogEntry> transactionLog = new ArrayList<>();
    
    /**
     * Performance metrics
     */
    @Builder.Default
    private TransactionMetrics metrics = new TransactionMetrics();
    
    /**
     * Transaction phases
     */
    public enum TransactionPhase {
        INITIAL("Transaction created but not started"),
        PREPARING("Prepare phase - asking participants to vote"),
        VOTING_COMPLETE("All votes received"),
        COMMITTING("Commit phase - instructing participants to commit"),
        ABORTING("Abort phase - instructing participants to abort"),
        COMPLETED("Transaction completed");
        
        private final String description;
        
        TransactionPhase(String description) {
            this.description = description;
        }
        
        public String getDescription() {
            return description;
        }
    }
    
    /**
     * Transaction states
     */
    public enum TransactionState {
        ACTIVE("Transaction is active"),
        PREPARED("Transaction is prepared"),
        COMMITTED("Transaction is committed"),
        ABORTED("Transaction is aborted"),
        IN_DOUBT("Transaction state is uncertain"),
        TIMEOUT("Transaction timed out"),
        FAILED("Transaction failed");
        
        private final String description;
        
        TransactionState(String description) {
            this.description = description;
        }
        
        public String getDescription() {
            return description;
        }
        
        public boolean isTerminal() {
            return this == COMMITTED || this == ABORTED || this == FAILED;
        }
    }
    
    /**
     * Coordinator decision
     */
    public enum Decision {
        COMMIT("Decision to commit the transaction"),
        ABORT("Decision to abort the transaction"),
        UNDECIDED("No decision made yet");
        
        private final String description;
        
        Decision(String description) {
            this.description = description;
        }
        
        public String getDescription() {
            return description;
        }
    }
    
    /**
     * Participant vote
     */
    public enum Vote {
        YES("Participant votes to commit"),
        NO("Participant votes to abort"),
        TIMEOUT("Participant did not respond in time"),
        ERROR("Error occurred during voting");
        
        private final String description;
        
        Vote(String description) {
            this.description = description;
        }
        
        public String getDescription() {
            return description;
        }
    }
    
    /**
     * Transaction participant (resource manager)
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Participant {
        @NotNull
        private String participantId;
        @NotNull
        private String name;
        private String resourceType;
        private String endpoint;
        @Builder.Default
        private ParticipantState state = ParticipantState.ACTIVE;
        private Vote vote;
        private LocalDateTime voteTimestamp;
        private LocalDateTime lastContactTimestamp;
        private String errorMessage;
        @Builder.Default
        private int retryCount = 0;
        @Builder.Default
        private Map<String, Object> metadata = new HashMap<>();
        
        public enum ParticipantState {
            ACTIVE,
            PREPARED,
            COMMITTED,
            ABORTED,
            FAILED,
            TIMEOUT
        }
    }
    
    /**
     * Transaction coordinator
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Coordinator {
        @NotNull
        private String coordinatorId;
        @NotNull
        private String name;
        private String endpoint;
        @Builder.Default
        private CoordinatorState state = CoordinatorState.ACTIVE;
        private LocalDateTime lastHeartbeat;
        @Builder.Default
        private Map<String, Object> metadata = new HashMap<>();
        
        public enum CoordinatorState {
            ACTIVE,
            PREPARING,
            DECIDING,
            EXECUTING,
            COMPLETED,
            FAILED
        }
    }
    
    /**
     * Transaction log entry for recovery
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LogEntry {
        @NotNull
        private LocalDateTime timestamp;
        @NotNull
        private String entryType;
        @NotNull
        private String message;
        private String participantId;
        private TransactionPhase phase;
        private TransactionState state;
        private Map<String, Object> data;
    }
    
    /**
     * Transaction performance metrics
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TransactionMetrics {
        @Builder.Default
        private long preparePhaseMs = 0;
        @Builder.Default
        private long commitPhaseMs = 0;
        @Builder.Default
        private long totalExecutionMs = 0;
        @Builder.Default
        private int participantCount = 0;
        @Builder.Default
        private int successfulVotes = 0;
        @Builder.Default
        private int failedVotes = 0;
        @Builder.Default
        private Map<String, Long> participantResponseTimes = new HashMap<>();
    }
    
    /**
     * Add a participant to the transaction
     */
    public void addParticipant(Participant participant) {
        if (participants == null) {
            participants = new ArrayList<>();
        }
        participants.add(participant);
        if (metrics == null) {
            metrics = new TransactionMetrics();
        }
        metrics.setParticipantCount(participants.size());
    }
    
    /**
     * Record a vote from a participant
     */
    public void recordVote(String participantId, Vote vote) {
        if (votes == null) {
            votes = new ConcurrentHashMap<>();
        }
        votes.put(participantId, vote);
        
        // Update participant state
        participants.stream()
                .filter(p -> p.getParticipantId().equals(participantId))
                .findFirst()
                .ifPresent(p -> {
                    p.setVote(vote);
                    p.setVoteTimestamp(LocalDateTime.now());
                    if (vote == Vote.YES) {
                        p.setState(Participant.ParticipantState.PREPARED);
                    }
                });
        
        // Update metrics
        if (vote == Vote.YES) {
            metrics.setSuccessfulVotes(metrics.getSuccessfulVotes() + 1);
        } else if (vote == Vote.NO || vote == Vote.ERROR) {
            metrics.setFailedVotes(metrics.getFailedVotes() + 1);
        }
    }
    
    /**
     * Check if all participants have voted
     */
    public boolean allVotesReceived() {
        return votes.size() == participants.size();
    }
    
    /**
     * Check if transaction can commit (all votes are YES)
     */
    public boolean canCommit() {
        if (!allVotesReceived()) {
            return false;
        }
        return votes.values().stream().allMatch(vote -> vote == Vote.YES);
    }
    
    /**
     * Check if transaction is in terminal state
     */
    public boolean isTerminal() {
        return state.isTerminal();
    }
    
    /**
     * Add a log entry
     */
    public void addLogEntry(LogEntry entry) {
        if (transactionLog == null) {
            transactionLog = new ArrayList<>();
        }
        transactionLog.add(entry);
    }
    
    /**
     * Create a log entry for current state
     */
    public void logState(String message) {
        addLogEntry(LogEntry.builder()
                .timestamp(LocalDateTime.now())
                .entryType("STATE_CHANGE")
                .message(message)
                .phase(phase)
                .state(state)
                .build());
    }
}