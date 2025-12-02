package com.waqiti.common.database;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Routes database connections based on query characteristics and load
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ConnectionRouter {
    private final ConnectionSelector connectionSelector;
    private final Map<String, DataSource> dataSources = new ConcurrentHashMap<>();
    private final Map<String, ConnectionPoolMetrics> poolMetrics = new ConcurrentHashMap<>();
    
    public Connection routeConnection(String queryPattern, QueryPriority priority) throws SQLException {
        log.debug("Routing connection for query pattern: {} with priority: {}", queryPattern, priority);
        
        String selectedPool = connectionSelector.selectOptimalPool(queryPattern, priority);
        DataSource dataSource = dataSources.get(selectedPool);
        
        if (dataSource == null) {
            log.warn("DataSource not found for pool: {}, using default", selectedPool);
            dataSource = dataSources.get("default");
        }
        
        Connection connection = dataSource.getConnection();
        updateMetrics(selectedPool);
        
        return connection;
    }
    
    public String routeQuery(QueryContext queryContext, java.util.Set<String> availablePools) {
        log.debug("Routing query based on context: {}", queryContext);
        
        // Extract query pattern and priority
        String queryPattern = queryContext.getQueryType();
        QueryPriority priority = queryContext.getPriority();
        
        // Use connection selector to find optimal pool
        String selectedPool = connectionSelector.selectOptimalPool(queryPattern, priority);
        
        // Validate pool is available
        if (!availablePools.contains(selectedPool)) {
            // Fall back to primary-write if selected pool not available
            if (availablePools.contains("primary-write")) {
                selectedPool = "primary-write";
            } else if (availablePools.contains("primary-read")) {
                selectedPool = "primary-read";
            } else {
                // Use first available pool
                selectedPool = availablePools.iterator().next();
            }
        }
        
        log.debug("Selected pool: {} for query", selectedPool);
        return selectedPool;
    }
    
    public void registerDataSource(String poolName, DataSource dataSource) {
        dataSources.put(poolName, dataSource);
        poolMetrics.put(poolName, new ConnectionPoolMetrics(poolName));
        log.info("Registered data source: {}", poolName);
    }
    
    public ConnectionPoolMetrics getPoolMetrics(String poolName) {
        return poolMetrics.get(poolName);
    }
    
    public List<String> getAvailablePools() {
        return List.copyOf(dataSources.keySet());
    }
    
    public void updateConnectionHealth(String poolName, ConnectionHealthStatus status) {
        ConnectionPoolMetrics metrics = poolMetrics.get(poolName);
        if (metrics != null) {
            metrics.setHealthStatus(status);
            log.debug("Updated health status for pool {}: {}", poolName, status);
        }
    }
    
    private void updateMetrics(String poolName) {
        ConnectionPoolMetrics metrics = poolMetrics.get(poolName);
        if (metrics != null) {
            metrics.incrementConnectionRequests();
        }
    }
}