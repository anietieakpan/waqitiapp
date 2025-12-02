package com.waqiti.websocket.pool;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * WebSocket connection pool for efficient connection management
 */
@Slf4j
@Component
public class WebSocketConnectionPool {
    
    // Pool configuration
    private static final int CORE_POOL_SIZE = 50;
    private static final int MAX_POOL_SIZE = 500;
    private static final int MAX_CONNECTIONS_PER_USER = 5;
    private static final long IDLE_TIMEOUT_MINUTES = 30;
    private static final long HEALTH_CHECK_INTERVAL_SECONDS = 60;
    
    // Connection storage
    private final Map<String, UserConnectionPool> userPools = new ConcurrentHashMap<>();
    private final Map<String, WebSocketConnection> allConnections = new ConcurrentHashMap<>();
    
    // Metrics
    private final AtomicInteger totalConnections = new AtomicInteger(0);
    private final AtomicInteger activeConnections = new AtomicInteger(0);
    private final AtomicInteger idleConnections = new AtomicInteger(0);
    
    // Thread pools
    private final ScheduledExecutorService healthCheckExecutor = Executors.newSingleThreadScheduledExecutor();
    private final ExecutorService messageExecutor = Executors.newFixedThreadPool(10);
    
    public WebSocketConnectionPool() {
        // Start health check task
        healthCheckExecutor.scheduleAtFixedRate(
            this::performHealthCheck, 
            HEALTH_CHECK_INTERVAL_SECONDS, 
            HEALTH_CHECK_INTERVAL_SECONDS, 
            TimeUnit.SECONDS
        );
        
        log.info("WebSocket connection pool initialized with core size: {}, max size: {}", 
            CORE_POOL_SIZE, MAX_POOL_SIZE);
    }
    
    /**
     * Add a new connection to the pool
     */
    public synchronized boolean addConnection(String userId, WebSocketSession session) {
        try {
            // Check total pool size
            if (totalConnections.get() >= MAX_POOL_SIZE) {
                log.warn("Connection pool is full. Max size: {}", MAX_POOL_SIZE);
                return false;
            }
            
            // Get or create user pool
            UserConnectionPool userPool = userPools.computeIfAbsent(userId, 
                k -> new UserConnectionPool(userId, MAX_CONNECTIONS_PER_USER));
            
            // Check user connection limit
            if (userPool.size() >= MAX_CONNECTIONS_PER_USER) {
                log.warn("User {} has reached max connections limit: {}", 
                    userId, MAX_CONNECTIONS_PER_USER);
                
                // Remove oldest idle connection if exists
                WebSocketConnection oldest = userPool.getOldestIdleConnection();
                if (oldest != null) {
                    removeConnection(oldest.getConnectionId());
                } else {
                    return false;
                }
            }
            
            // Create connection wrapper
            String connectionId = UUID.randomUUID().toString();
            WebSocketConnection connection = new WebSocketConnection(
                connectionId, userId, session
            );
            
            // Add to pools
            userPool.addConnection(connection);
            allConnections.put(connectionId, connection);
            
            // Update metrics
            totalConnections.incrementAndGet();
            activeConnections.incrementAndGet();
            
            log.info("Added connection {} for user {}. Total connections: {}", 
                connectionId, userId, totalConnections.get());
            
            return true;
            
        } catch (Exception e) {
            log.error("Failed to add connection for user: {}", userId, e);
            return false;
        }
    }
    
    /**
     * Remove a connection from the pool
     */
    public synchronized void removeConnection(String connectionId) {
        try {
            WebSocketConnection connection = allConnections.remove(connectionId);
            if (connection == null) {
                return;
            }
            
            // Remove from user pool
            UserConnectionPool userPool = userPools.get(connection.getUserId());
            if (userPool != null) {
                userPool.removeConnection(connectionId);
                
                // Remove empty user pool
                if (userPool.isEmpty()) {
                    userPools.remove(connection.getUserId());
                }
            }
            
            // Close session if still open
            if (connection.getSession().isOpen()) {
                try {
                    connection.getSession().close();
                } catch (IOException e) {
                    log.warn("Failed to close WebSocket session: {}", connectionId, e);
                }
            }
            
            // Update metrics
            totalConnections.decrementAndGet();
            if (connection.isActive()) {
                activeConnections.decrementAndGet();
            } else {
                idleConnections.decrementAndGet();
            }
            
            log.info("Removed connection {} for user {}. Total connections: {}", 
                connectionId, connection.getUserId(), totalConnections.get());
            
        } catch (Exception e) {
            log.error("Failed to remove connection: {}", connectionId, e);
        }
    }
    
    /**
     * Get connection by ID
     */
    public WebSocketConnection getConnection(String connectionId) {
        return allConnections.get(connectionId);
    }
    
    /**
     * Get all connections for a user
     */
    public List<WebSocketConnection> getUserConnections(String userId) {
        UserConnectionPool userPool = userPools.get(userId);
        return userPool != null ? userPool.getAllConnections() : Collections.emptyList();
    }
    
    /**
     * Get active connections for a user
     */
    public List<WebSocketConnection> getActiveUserConnections(String userId) {
        return getUserConnections(userId).stream()
            .filter(WebSocketConnection::isActive)
            .collect(Collectors.toList());
    }
    
    /**
     * Mark connection as active
     */
    public void markActive(String connectionId) {
        WebSocketConnection connection = allConnections.get(connectionId);
        if (connection != null) {
            boolean wasIdle = !connection.isActive();
            connection.markActive();
            
            if (wasIdle) {
                idleConnections.decrementAndGet();
                activeConnections.incrementAndGet();
            }
        }
    }
    
    /**
     * Mark connection as idle
     */
    public void markIdle(String connectionId) {
        WebSocketConnection connection = allConnections.get(connectionId);
        if (connection != null) {
            boolean wasActive = connection.isActive();
            connection.markIdle();
            
            if (wasActive) {
                activeConnections.decrementAndGet();
                idleConnections.incrementAndGet();
            }
        }
    }
    
    /**
     * Send message to all user connections
     */
    public CompletableFuture<Integer> broadcastToUser(String userId, String message) {
        return CompletableFuture.supplyAsync(() -> {
            List<WebSocketConnection> connections = getActiveUserConnections(userId);
            AtomicInteger successCount = new AtomicInteger(0);
            
            List<CompletableFuture<Void>> futures = connections.stream()
                .map(conn -> sendMessage(conn, message)
                    .thenAccept(success -> {
                        if (success) {
                            successCount.incrementAndGet();
                        }
                    }))
                .collect(Collectors.toList());
            
            try {
                CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                    .get(30, java.util.concurrent.TimeUnit.SECONDS);
            } catch (java.util.concurrent.TimeoutException e) {
                log.error("Broadcasting to user {} timed out after 30 seconds. Sent to {}/{} connections",
                    userId, successCount.get(), connections.size(), e);
                futures.forEach(f -> f.cancel(true));
            } catch (java.util.concurrent.ExecutionException e) {
                log.error("Broadcasting to user {} execution failed", userId, e.getCause());
            } catch (java.util.concurrent.InterruptedException e) {
                Thread.currentThread().interrupt();
                log.error("Broadcasting to user {} interrupted", userId, e);
            }

            return successCount.get();
        }, messageExecutor);
    }
    
    /**
     * Send message to specific connection
     */
    public CompletableFuture<Boolean> sendMessage(WebSocketConnection connection, String message) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                if (connection.getSession().isOpen()) {
                    connection.getSession().sendMessage(
                        new org.springframework.web.socket.TextMessage(message)
                    );
                    connection.updateLastActivity();
                    return true;
                } else {
                    log.warn("Attempted to send message to closed connection: {}", 
                        connection.getConnectionId());
                    removeConnection(connection.getConnectionId());
                    return false;
                }
            } catch (Exception e) {
                log.error("Failed to send message to connection: {}", 
                    connection.getConnectionId(), e);
                return false;
            }
        }, messageExecutor);
    }
    
    /**
     * Perform health check on all connections
     */
    private void performHealthCheck() {
        try {
            log.debug("Starting connection pool health check");
            
            List<String> toRemove = new ArrayList<>();
            LocalDateTime idleThreshold = LocalDateTime.now().minusMinutes(IDLE_TIMEOUT_MINUTES);
            
            for (WebSocketConnection connection : allConnections.values()) {
                // Check if session is still open
                if (!connection.getSession().isOpen()) {
                    toRemove.add(connection.getConnectionId());
                    continue;
                }
                
                // Check idle timeout
                if (!connection.isActive() && 
                    connection.getLastActivity().isBefore(idleThreshold)) {
                    log.info("Connection {} idle for too long, marking for removal", 
                        connection.getConnectionId());
                    toRemove.add(connection.getConnectionId());
                    continue;
                }
                
                // Send ping to check connection health
                try {
                    if (connection.getSession().isOpen()) {
                        connection.getSession().sendMessage(
                            new org.springframework.web.socket.PingMessage()
                        );
                    }
                } catch (Exception e) {
                    log.warn("Failed to ping connection {}, marking for removal", 
                        connection.getConnectionId());
                    toRemove.add(connection.getConnectionId());
                }
            }
            
            // Remove unhealthy connections
            toRemove.forEach(this::removeConnection);
            
            log.debug("Health check completed. Removed {} connections. Total: {}, Active: {}, Idle: {}", 
                toRemove.size(), totalConnections.get(), activeConnections.get(), idleConnections.get());
            
        } catch (Exception e) {
            log.error("Failed to perform health check", e);
        }
    }
    
    /**
     * Get pool statistics
     */
    public PoolStatistics getStatistics() {
        return PoolStatistics.builder()
            .totalConnections(totalConnections.get())
            .activeConnections(activeConnections.get())
            .idleConnections(idleConnections.get())
            .userCount(userPools.size())
            .corePoolSize(CORE_POOL_SIZE)
            .maxPoolSize(MAX_POOL_SIZE)
            .maxConnectionsPerUser(MAX_CONNECTIONS_PER_USER)
            .idleTimeoutMinutes(IDLE_TIMEOUT_MINUTES)
            .build();
    }
    
    /**
     * Shutdown the pool
     */
    @PreDestroy
    public void shutdown() {
        log.info("Shutting down WebSocket connection pool");
        
        // Stop health checks
        healthCheckExecutor.shutdown();
        
        // Close all connections
        allConnections.keySet().forEach(this::removeConnection);
        
        // Shutdown executors
        messageExecutor.shutdown();
        
        try {
            if (!healthCheckExecutor.awaitTermination(10, TimeUnit.SECONDS)) {
                healthCheckExecutor.shutdownNow();
            }
            if (!messageExecutor.awaitTermination(10, TimeUnit.SECONDS)) {
                messageExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Interrupted while shutting down", e);
        }
        
        log.info("WebSocket connection pool shutdown complete");
    }
    
    /**
     * User-specific connection pool
     */
    private static class UserConnectionPool {
        private final String userId;
        private final int maxConnections;
        private final Map<String, WebSocketConnection> connections = new ConcurrentHashMap<>();
        
        public UserConnectionPool(String userId, int maxConnections) {
            this.userId = userId;
            this.maxConnections = maxConnections;
        }
        
        public void addConnection(WebSocketConnection connection) {
            connections.put(connection.getConnectionId(), connection);
        }
        
        public void removeConnection(String connectionId) {
            connections.remove(connectionId);
        }
        
        public List<WebSocketConnection> getAllConnections() {
            return new ArrayList<>(connections.values());
        }
        
        public WebSocketConnection getOldestIdleConnection() {
            return connections.values().stream()
                .filter(conn -> !conn.isActive())
                .min(Comparator.comparing(WebSocketConnection::getLastActivity))
                .orElse(null); // Returning null is intentional here for "no idle connection found"
        }
        
        public int size() {
            return connections.size();
        }
        
        public boolean isEmpty() {
            return connections.isEmpty();
        }
    }
    
    /**
     * Pool statistics
     */
    @lombok.Data
    @lombok.Builder
    public static class PoolStatistics {
        private int totalConnections;
        private int activeConnections;
        private int idleConnections;
        private int userCount;
        private int corePoolSize;
        private int maxPoolSize;
        private int maxConnectionsPerUser;
        private long idleTimeoutMinutes;
    }
}