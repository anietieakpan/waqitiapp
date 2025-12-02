package com.waqiti.chaos.experiments;

import com.waqiti.chaos.core.ChaosExperiment;
import com.waqiti.chaos.core.ChaosResult;
import com.waqiti.chaos.metrics.ChaosMetrics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

@Component
@Slf4j
@RequiredArgsConstructor
public class DatabaseChaosExperiment implements ChaosExperiment {
    
    private final DataSource dataSource;
    private final JdbcTemplate jdbcTemplate;
    private final ChaosMetrics chaosMetrics;
    
    @Value("${chaos.database.connection-pool-size:10}")
    private int connectionPoolSize;
    
    @Value("${chaos.database.duration:120}")
    private int durationSeconds;
    
    @Value("${chaos.database.load-threads:50}")
    private int loadThreads;
    
    private final ExecutorService executor = Executors.newCachedThreadPool();
    private final List<Connection> hijackedConnections = new CopyOnWriteArrayList<>();
    
    @Override
    public String getName() {
        return "Database Chaos Experiment";
    }
    
    @Override
    public String getDescription() {
        return "Tests database resilience through connection exhaustion, deadlocks, and slow queries";
    }
    
    @Override
    public ChaosResult execute() {
        log.info("Starting database chaos experiment");
        ChaosResult.Builder resultBuilder = ChaosResult.builder()
            .experimentName(getName())
            .startTime(System.currentTimeMillis());
        
        try {
            // Run different database chaos scenarios
            CompletableFuture<Map<String, Object>> connectionExhaustion = runConnectionExhaustionTest();
            CompletableFuture<Map<String, Object>> deadlockTest = runDeadlockTest();
            CompletableFuture<Map<String, Object>> slowQueryTest = runSlowQueryTest();
            CompletableFuture<Map<String, Object>> tableLocksTest = runTableLockTest();
            CompletableFuture<Map<String, Object>> diskSpaceTest = runDiskSpaceTest();
            
            // Collect results
            CompletableFuture.allOf(
                connectionExhaustion, deadlockTest, slowQueryTest, 
                tableLocksTest, diskSpaceTest
            ).get(durationSeconds * 2, TimeUnit.SECONDS);
            
            // Aggregate metrics
            Map<String, Object> aggregatedMetrics = new HashMap<>();
            aggregatedMetrics.putAll(connectionExhaustion.get());
            aggregatedMetrics.putAll(deadlockTest.get());
            aggregatedMetrics.putAll(slowQueryTest.get());
            aggregatedMetrics.putAll(tableLocksTest.get());
            aggregatedMetrics.putAll(diskSpaceTest.get());
            
            resultBuilder
                .success(true)
                .message("Database chaos experiment completed successfully")
                .metrics(aggregatedMetrics);
            
        } catch (Exception e) {
            log.error("Database chaos experiment failed", e);
            resultBuilder
                .success(false)
                .message("Database chaos experiment failed: " + e.getMessage())
                .error(e);
        }
        
        ChaosResult result = resultBuilder
            .endTime(System.currentTimeMillis())
            .build();
        
        chaosMetrics.recordExperiment(result);
        return result;
    }
    
    private CompletableFuture<Map<String, Object>> runConnectionExhaustionTest() {
        return CompletableFuture.supplyAsync(() -> {
            Map<String, Object> metrics = new HashMap<>();
            
            try {
                log.info("Starting connection exhaustion test");
                
                // Hijack connections
                int targetConnections = connectionPoolSize * 2; // Exceed pool size
                CountDownLatch latch = new CountDownLatch(targetConnections);
                AtomicInteger failedConnections = new AtomicInteger(0);
                AtomicLong totalConnectionTime = new AtomicLong(0);
                
                for (int i = 0; i < targetConnections; i++) {
                    executor.submit(() -> {
                        long startTime = System.currentTimeMillis();
                        try {
                            Connection conn = dataSource.getConnection();
                            hijackedConnections.add(conn);
                            
                            long connectionTime = System.currentTimeMillis() - startTime;
                            totalConnectionTime.addAndGet(connectionTime);
                            
                            // Hold connection
                            Thread.sleep(Duration.ofSeconds(30).toMillis());
                            
                        } catch (Exception e) {
                            failedConnections.incrementAndGet();
                            log.debug("Failed to acquire connection: {}", e.getMessage());
                        } finally {
                            latch.countDown();
                        }
                    });
                }
                
                // Wait for connections to be acquired
                latch.await(60, TimeUnit.SECONDS);
                
                // Test impact on new connections
                long impactStart = System.currentTimeMillis();
                int successfulRequests = 0;
                int failedRequests = 0;
                
                for (int i = 0; i < 10; i++) {
                    try {
                        jdbcTemplate.queryForObject("SELECT 1", Integer.class);
                        successfulRequests++;
                    } catch (Exception e) {
                        failedRequests++;
                    }
                    Thread.sleep(1000);
                }
                
                metrics.put("connections_hijacked", hijackedConnections.size());
                metrics.put("connection_failures", failedConnections.get());
                metrics.put("average_connection_time_ms", 
                    hijackedConnections.isEmpty() ? 0 : 
                    totalConnectionTime.get() / hijackedConnections.size());
                metrics.put("requests_during_exhaustion_success", successfulRequests);
                metrics.put("requests_during_exhaustion_failed", failedRequests);
                
                log.info("Connection exhaustion test completed. Hijacked: {}, Failed: {}", 
                    hijackedConnections.size(), failedConnections.get());
                
            } catch (Exception e) {
                log.error("Connection exhaustion test failed", e);
                metrics.put("connection_exhaustion_error", e.getMessage());
            } finally {
                releaseHijackedConnections();
            }
            
            return metrics;
        });
    }
    
    private CompletableFuture<Map<String, Object>> runDeadlockTest() {
        return CompletableFuture.supplyAsync(() -> {
            Map<String, Object> metrics = new HashMap<>();
            
            try {
                log.info("Starting deadlock simulation test");
                
                // Create test tables
                jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS chaos_table_a (id INT PRIMARY KEY, value VARCHAR(100))");
                jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS chaos_table_b (id INT PRIMARY KEY, value VARCHAR(100))");
                
                // Insert test data
                jdbcTemplate.execute("INSERT INTO chaos_table_a VALUES (1, 'test') ON CONFLICT DO NOTHING");
                jdbcTemplate.execute("INSERT INTO chaos_table_b VALUES (1, 'test') ON CONFLICT DO NOTHING");
                
                AtomicInteger deadlockCount = new AtomicInteger(0);
                CountDownLatch startLatch = new CountDownLatch(1);
                CountDownLatch completeLatch = new CountDownLatch(2);
                
                // Transaction 1: Lock A then B
                CompletableFuture<Void> transaction1 = CompletableFuture.runAsync(() -> {
                    try {
                        startLatch.await();
                        jdbcTemplate.execute("BEGIN");
                        jdbcTemplate.execute("UPDATE chaos_table_a SET value = 'tx1' WHERE id = 1");
                        Thread.sleep(100); // Give time for tx2 to lock B
                        jdbcTemplate.execute("UPDATE chaos_table_b SET value = 'tx1' WHERE id = 1");
                        jdbcTemplate.execute("COMMIT");
                    } catch (Exception e) {
                        if (e.getMessage().contains("deadlock")) {
                            deadlockCount.incrementAndGet();
                        }
                        try {
                            jdbcTemplate.execute("ROLLBACK");
                        } catch (Exception rollbackEx) {
                            log.debug("Rollback failed after deadlock: {}", rollbackEx.getMessage());
                        }
                    } finally {
                        completeLatch.countDown();
                    }
                });
                
                // Transaction 2: Lock B then A
                CompletableFuture<Void> transaction2 = CompletableFuture.runAsync(() -> {
                    try {
                        startLatch.await();
                        jdbcTemplate.execute("BEGIN");
                        jdbcTemplate.execute("UPDATE chaos_table_b SET value = 'tx2' WHERE id = 1");
                        Thread.sleep(100); // Give time for tx1 to lock A
                        jdbcTemplate.execute("UPDATE chaos_table_a SET value = 'tx2' WHERE id = 1");
                        jdbcTemplate.execute("COMMIT");
                    } catch (Exception e) {
                        if (e.getMessage().contains("deadlock")) {
                            deadlockCount.incrementAndGet();
                        }
                        try {
                            jdbcTemplate.execute("ROLLBACK");
                        } catch (Exception rollbackEx) {
                            log.debug("Rollback failed after deadlock: {}", rollbackEx.getMessage());
                        }
                    } finally {
                        completeLatch.countDown();
                    }
                });
                
                // Start transactions simultaneously
                startLatch.countDown();
                
                // Wait for completion
                completeLatch.await(30, TimeUnit.SECONDS);
                
                metrics.put("deadlocks_detected", deadlockCount.get());
                metrics.put("deadlock_test_completed", true);
                
                log.info("Deadlock test completed. Deadlocks detected: {}", deadlockCount.get());
                
            } catch (Exception e) {
                log.error("Deadlock test failed", e);
                metrics.put("deadlock_test_error", e.getMessage());
            } finally {
                // Cleanup
                try {
                    jdbcTemplate.execute("DROP TABLE IF EXISTS chaos_table_a");
                    jdbcTemplate.execute("DROP TABLE IF EXISTS chaos_table_b");
                } catch (Exception cleanupEx) {
                    log.debug("Cleanup of test tables failed: {}", cleanupEx.getMessage());
                }
            }
            
            return metrics;
        });
    }
    
    private CompletableFuture<Map<String, Object>> runSlowQueryTest() {
        return CompletableFuture.supplyAsync(() -> {
            Map<String, Object> metrics = new HashMap<>();
            
            try {
                log.info("Starting slow query test");
                
                // Create large test table
                jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS chaos_large_table (id SERIAL PRIMARY KEY, data TEXT)");
                
                // Insert large amount of data
                log.info("Inserting test data...");
                for (int i = 0; i < 1000; i++) {
                    jdbcTemplate.execute(String.format(
                        "INSERT INTO chaos_large_table (data) VALUES ('%s')",
                        "x".repeat(1000) // 1KB per row
                    ));
                }
                
                // Run slow queries concurrently with normal queries
                AtomicInteger slowQueries = new AtomicInteger(0);
                AtomicInteger normalQueries = new AtomicInteger(0);
                AtomicLong totalSlowQueryTime = new AtomicLong(0);
                AtomicLong totalNormalQueryTime = new AtomicLong(0);
                
                CountDownLatch latch = new CountDownLatch(loadThreads);
                
                for (int i = 0; i < loadThreads; i++) {
                    final int threadId = i;
                    executor.submit(() -> {
                        try {
                            if (threadId % 5 == 0) {
                                // Slow query (no index scan)
                                long startTime = System.currentTimeMillis();
                                jdbcTemplate.queryForObject(
                                    "SELECT COUNT(*) FROM chaos_large_table WHERE data LIKE '%xxx%'",
                                    Integer.class
                                );
                                long queryTime = System.currentTimeMillis() - startTime;
                                totalSlowQueryTime.addAndGet(queryTime);
                                slowQueries.incrementAndGet();
                            } else {
                                // Normal query
                                long startTime = System.currentTimeMillis();
                                jdbcTemplate.queryForObject("SELECT 1", Integer.class);
                                long queryTime = System.currentTimeMillis() - startTime;
                                totalNormalQueryTime.addAndGet(queryTime);
                                normalQueries.incrementAndGet();
                            }
                        } catch (Exception e) {
                            log.debug("Query failed: {}", e.getMessage());
                        } finally {
                            latch.countDown();
                        }
                    });
                }
                
                latch.await(60, TimeUnit.SECONDS);
                
                metrics.put("slow_queries_executed", slowQueries.get());
                metrics.put("normal_queries_executed", normalQueries.get());
                metrics.put("average_slow_query_time_ms", 
                    slowQueries.get() == 0 ? 0 : totalSlowQueryTime.get() / slowQueries.get());
                metrics.put("average_normal_query_time_ms", 
                    normalQueries.get() == 0 ? 0 : totalNormalQueryTime.get() / normalQueries.get());
                
                log.info("Slow query test completed. Slow queries: {}, Normal queries: {}", 
                    slowQueries.get(), normalQueries.get());
                
            } catch (Exception e) {
                log.error("Slow query test failed", e);
                metrics.put("slow_query_test_error", e.getMessage());
            } finally {
                // Cleanup
                try {
                    jdbcTemplate.execute("DROP TABLE IF EXISTS chaos_large_table");
                } catch (Exception cleanupEx) {
                    log.debug("Cleanup of chaos_large_table failed: {}", cleanupEx.getMessage());
                }
            }
            
            return metrics;
        });
    }
    
    private CompletableFuture<Map<String, Object>> runTableLockTest() {
        return CompletableFuture.supplyAsync(() -> {
            Map<String, Object> metrics = new HashMap<>();
            
            try {
                log.info("Starting table lock test");
                
                // Create test table
                jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS chaos_lock_table (id INT PRIMARY KEY, value VARCHAR(100))");
                jdbcTemplate.execute("INSERT INTO chaos_lock_table VALUES (1, 'test')");
                
                // Acquire exclusive lock
                CompletableFuture<Void> lockHolder = CompletableFuture.runAsync(() -> {
                    try {
                        jdbcTemplate.execute("BEGIN");
                        jdbcTemplate.execute("LOCK TABLE chaos_lock_table IN EXCLUSIVE MODE");
                        log.info("Acquired exclusive lock on table");
                        Thread.sleep(Duration.ofSeconds(30).toMillis());
                        jdbcTemplate.execute("COMMIT");
                        log.info("Released exclusive lock");
                    } catch (Exception e) {
                        log.error("Lock holder failed", e);
                        try {
                            jdbcTemplate.execute("ROLLBACK");
                        } catch (Exception rollbackEx) {
                            log.debug("Rollback failed after deadlock: {}", rollbackEx.getMessage());
                        }
                    }
                });
                
                // Wait for lock to be acquired
                Thread.sleep(1000);
                
                // Try concurrent access
                AtomicInteger blockedQueries = new AtomicInteger(0);
                AtomicLong totalWaitTime = new AtomicLong(0);
                CountDownLatch accessLatch = new CountDownLatch(10);
                
                for (int i = 0; i < 10; i++) {
                    executor.submit(() -> {
                        long startTime = System.currentTimeMillis();
                        try {
                            jdbcTemplate.queryForObject(
                                "SELECT value FROM chaos_lock_table WHERE id = 1",
                                String.class
                            );
                        } catch (Exception e) {
                            blockedQueries.incrementAndGet();
                        } finally {
                            long waitTime = System.currentTimeMillis() - startTime;
                            totalWaitTime.addAndGet(waitTime);
                            accessLatch.countDown();
                        }
                    });
                }
                
                accessLatch.await(45, TimeUnit.SECONDS);
                
                metrics.put("queries_blocked_by_lock", blockedQueries.get());
                metrics.put("average_wait_time_ms", totalWaitTime.get() / 10);
                
                log.info("Table lock test completed. Blocked queries: {}", blockedQueries.get());
                
            } catch (Exception e) {
                log.error("Table lock test failed", e);
                metrics.put("table_lock_test_error", e.getMessage());
            } finally {
                // Cleanup
                try {
                    jdbcTemplate.execute("DROP TABLE IF EXISTS chaos_lock_table");
                } catch (Exception cleanupEx) {
                    log.debug("Cleanup of chaos_lock_table failed: {}", cleanupEx.getMessage());
                }
            }
            
            return metrics;
        });
    }
    
    private CompletableFuture<Map<String, Object>> runDiskSpaceTest() {
        return CompletableFuture.supplyAsync(() -> {
            Map<String, Object> metrics = new HashMap<>();
            
            try {
                log.info("Starting disk space simulation test");
                
                // Create table with large data
                jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS chaos_disk_test (id SERIAL PRIMARY KEY, data TEXT)");
                
                // Try to fill disk (safely limited)
                AtomicLong dataInserted = new AtomicLong(0);
                AtomicInteger insertFailures = new AtomicInteger(0);
                
                for (int i = 0; i < 100; i++) {
                    try {
                        // Insert 10MB of data
                        String largeData = "x".repeat(10 * 1024 * 1024);
                        jdbcTemplate.execute(String.format(
                            "INSERT INTO chaos_disk_test (data) VALUES ('%s')",
                            largeData
                        ));
                        dataInserted.addAndGet(largeData.length());
                    } catch (Exception e) {
                        insertFailures.incrementAndGet();
                        if (e.getMessage().contains("disk") || e.getMessage().contains("space")) {
                            log.info("Disk space exhaustion detected");
                            break;
                        }
                    }
                }
                
                metrics.put("data_inserted_bytes", dataInserted.get());
                metrics.put("insert_failures", insertFailures.get());
                metrics.put("disk_test_completed", true);
                
                log.info("Disk space test completed. Data inserted: {} bytes", dataInserted.get());
                
            } catch (Exception e) {
                log.error("Disk space test failed", e);
                metrics.put("disk_space_test_error", e.getMessage());
            } finally {
                // Cleanup
                try {
                    jdbcTemplate.execute("DROP TABLE IF EXISTS chaos_disk_test");
                } catch (Exception e) {
                    log.debug("Failed to cleanup chaos_disk_test table: {}", e.getMessage());
                }
            }
            
            return metrics;
        });
    }
    
    private void releaseHijackedConnections() {
        log.info("Releasing {} hijacked connections", hijackedConnections.size());
        for (Connection conn : hijackedConnections) {
            try {
                if (!conn.isClosed()) {
                    conn.close();
                }
            } catch (SQLException e) {
                log.error("Failed to close connection", e);
            }
        }
        hijackedConnections.clear();
    }
    
    @Override
    public void cleanup() {
        releaseHijackedConnections();
        executor.shutdown();
        try {
            if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
        }
    }
}