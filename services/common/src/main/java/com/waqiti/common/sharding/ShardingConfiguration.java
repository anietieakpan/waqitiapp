package com.waqiti.common.sharding;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

/**
 * Database sharding configuration for high-volume tables
 */
@Configuration
@Slf4j
public class ShardingConfiguration {
    
    @Value("${sharding.enabled:false}")
    private boolean shardingEnabled;
    
    @Value("${sharding.shard-count:4}")
    private int shardCount;
    
    @Value("${sharding.strategy:HASH}")
    private ShardingStrategy shardingStrategy;
    
    @Bean
    public ShardManager shardManager() {
        if (!shardingEnabled) {
            log.info("Sharding is disabled");
            return new SingleShardManager(createDataSource(0));
        }
        
        log.info("Initializing sharding with {} shards using {} strategy", 
                shardCount, shardingStrategy);
        
        Map<Integer, DataSource> shardDataSources = new HashMap<>();
        for (int i = 0; i < shardCount; i++) {
            shardDataSources.put(i, createDataSource(i));
        }
        
        return new MultiShardManager(shardDataSources, shardingStrategy);
    }
    
    private DataSource createDataSource(int shardId) {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(getShardJdbcUrl(shardId));
        config.setUsername(System.getenv("DB_USERNAME"));
        config.setPassword(System.getenv("DB_PASSWORD"));
        config.setDriverClassName("org.postgresql.Driver");
        
        // Connection pool settings
        config.setMaximumPoolSize(25);
        config.setMinimumIdle(5);
        config.setConnectionTimeout(30000);
        config.setIdleTimeout(600000);
        config.setMaxLifetime(1800000);
        
        // Performance optimizations
        config.addDataSourceProperty("cachePrepStmts", "true");
        config.addDataSourceProperty("prepStmtCacheSize", "250");
        config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
        config.addDataSourceProperty("useServerPrepStmts", "true");
        config.addDataSourceProperty("useLocalSessionState", "true");
        config.addDataSourceProperty("rewriteBatchedStatements", "true");
        config.addDataSourceProperty("cacheResultSetMetadata", "true");
        config.addDataSourceProperty("cacheServerConfiguration", "true");
        config.addDataSourceProperty("elideSetAutoCommits", "true");
        config.addDataSourceProperty("maintainTimeStats", "false");
        
        config.setPoolName("shard-" + shardId + "-pool");
        
        return new HikariDataSource(config);
    }
    
    private String getShardJdbcUrl(int shardId) {
        String baseUrl = System.getenv("DB_BASE_URL");
        if (baseUrl == null) {
            baseUrl = "jdbc:postgresql://localhost:5432";
        }
        return baseUrl + "/waqiti_shard_" + shardId;
    }
    
    /**
     * Shard manager interface
     */
    public interface ShardManager {
        DataSource getShardForKey(String key);
        DataSource getShardById(int shardId);
        int getShardCount();
        void executeOnAllShards(ShardOperation operation);
        <T> T executeOnShard(String key, ShardFunction<T> function);
        <T> Map<Integer, T> executeOnAllShardsWithResult(ShardFunction<T> function);
    }
    
    /**
     * Single shard implementation (when sharding is disabled)
     */
    public static class SingleShardManager implements ShardManager {
        private final DataSource dataSource;
        
        public SingleShardManager(DataSource dataSource) {
            this.dataSource = dataSource;
        }
        
        @Override
        public DataSource getShardForKey(String key) {
            return dataSource;
        }
        
        @Override
        public DataSource getShardById(int shardId) {
            return dataSource;
        }
        
        @Override
        public int getShardCount() {
            return 1;
        }
        
        @Override
        public void executeOnAllShards(ShardOperation operation) {
            try {
                operation.execute(new JdbcTemplate(dataSource));
            } catch (Exception e) {
                throw new ShardingException("Failed to execute operation", e);
            }
        }
        
        @Override
        public <T> T executeOnShard(String key, ShardFunction<T> function) {
            try {
                return function.apply(new JdbcTemplate(dataSource));
            } catch (Exception e) {
                throw new ShardingException("Failed to execute function", e);
            }
        }
        
        @Override
        public <T> Map<Integer, T> executeOnAllShardsWithResult(ShardFunction<T> function) {
            Map<Integer, T> results = new HashMap<>();
            try {
                results.put(0, function.apply(new JdbcTemplate(dataSource)));
            } catch (Exception e) {
                throw new ShardingException("Failed to execute function", e);
            }
            return results;
        }
    }
    
    /**
     * Multi-shard implementation
     */
    public static class MultiShardManager implements ShardManager {
        private final Map<Integer, DataSource> shardDataSources;
        private final ShardingStrategy strategy;
        private final ShardKeyGenerator keyGenerator;
        
        public MultiShardManager(Map<Integer, DataSource> shardDataSources, 
                               ShardingStrategy strategy) {
            this.shardDataSources = shardDataSources;
            this.strategy = strategy;
            this.keyGenerator = createKeyGenerator(strategy);
        }
        
        @Override
        public DataSource getShardForKey(String key) {
            int shardId = keyGenerator.getShardId(key, shardDataSources.size());
            return shardDataSources.get(shardId);
        }
        
        @Override
        public DataSource getShardById(int shardId) {
            return shardDataSources.get(shardId);
        }
        
        @Override
        public int getShardCount() {
            return shardDataSources.size();
        }
        
        @Override
        public void executeOnAllShards(ShardOperation operation) {
            shardDataSources.values().parallelStream().forEach(dataSource -> {
                try {
                    operation.execute(new JdbcTemplate(dataSource));
                } catch (Exception e) {
                    log.error("Failed to execute operation on shard", e);
                    throw new ShardingException("Failed to execute operation on shard", e);
                }
            });
        }
        
        @Override
        public <T> T executeOnShard(String key, ShardFunction<T> function) {
            DataSource dataSource = getShardForKey(key);
            try {
                return function.apply(new JdbcTemplate(dataSource));
            } catch (Exception e) {
                throw new ShardingException("Failed to execute function on shard", e);
            }
        }
        
        @Override
        public <T> Map<Integer, T> executeOnAllShardsWithResult(ShardFunction<T> function) {
            Map<Integer, T> results = new HashMap<>();
            shardDataSources.forEach((shardId, dataSource) -> {
                try {
                    T result = function.apply(new JdbcTemplate(dataSource));
                    results.put(shardId, result);
                } catch (Exception e) {
                    log.error("Failed to execute function on shard {}", shardId, e);
                    throw new ShardingException("Failed to execute function on shard " + shardId, e);
                }
            });
            return results;
        }
        
        private ShardKeyGenerator createKeyGenerator(ShardingStrategy strategy) {
            switch (strategy) {
                case HASH:
                    return new HashShardKeyGenerator();
                case RANGE:
                    return new RangeShardKeyGenerator();
                case CONSISTENT_HASH:
                    return new ConsistentHashShardKeyGenerator();
                default:
                    throw new IllegalArgumentException("Unknown sharding strategy: " + strategy);
            }
        }
    }
    
    /**
     * Sharding strategies
     */
    public enum ShardingStrategy {
        HASH,
        RANGE,
        CONSISTENT_HASH
    }
    
    /**
     * Shard key generator interface
     */
    public interface ShardKeyGenerator {
        int getShardId(String key, int shardCount);
    }
    
    /**
     * Hash-based shard key generator
     */
    public static class HashShardKeyGenerator implements ShardKeyGenerator {
        @Override
        public int getShardId(String key, int shardCount) {
            return Math.abs(key.hashCode()) % shardCount;
        }
    }
    
    /**
     * Range-based shard key generator
     */
    public static class RangeShardKeyGenerator implements ShardKeyGenerator {
        @Override
        public int getShardId(String key, int shardCount) {
            // Simple range based on first character
            char firstChar = key.charAt(0);
            int range = 26 / shardCount;
            return Math.min((firstChar - 'A') / range, shardCount - 1);
        }
    }
    
    /**
     * Consistent hash shard key generator
     */
    public static class ConsistentHashShardKeyGenerator implements ShardKeyGenerator {
        private final ConsistentHash<Integer> consistentHash;
        
        public ConsistentHashShardKeyGenerator() {
            this.consistentHash = new ConsistentHash<>(100); // 100 virtual nodes per shard
        }
        
        @Override
        public int getShardId(String key, int shardCount) {
            // Initialize consistent hash if needed
            if (consistentHash.isEmpty()) {
                for (int i = 0; i < shardCount; i++) {
                    consistentHash.add(i);
                }
            }
            return consistentHash.get(key);
        }
    }
    
    /**
     * Functional interfaces for shard operations
     */
    @FunctionalInterface
    public interface ShardOperation {
        void execute(JdbcTemplate jdbcTemplate) throws Exception;
    }
    
    @FunctionalInterface
    public interface ShardFunction<T> {
        T apply(JdbcTemplate jdbcTemplate) throws Exception;
    }
    
    /**
     * Sharding exception
     */
    public static class ShardingException extends RuntimeException {
        public ShardingException(String message) {
            super(message);
        }
        
        public ShardingException(String message, Throwable cause) {
            super(message, cause);
        }
    }
    
    /**
     * Simple consistent hash implementation
     */
    private static class ConsistentHash<T> {
        private final int numberOfReplicas;
        private final java.util.SortedMap<Integer, T> circle = new java.util.TreeMap<>();
        
        public ConsistentHash(int numberOfReplicas) {
            this.numberOfReplicas = numberOfReplicas;
        }
        
        public void add(T node) {
            for (int i = 0; i < numberOfReplicas; i++) {
                circle.put(hash(node.toString() + i), node);
            }
        }
        
        public T get(Object key) {
            if (circle.isEmpty()) {
                return null;
            }
            int hash = hash(key);
            if (!circle.containsKey(hash)) {
                java.util.SortedMap<Integer, T> tailMap = circle.tailMap(hash);
                hash = tailMap.isEmpty() ? circle.firstKey() : tailMap.firstKey();
            }
            return circle.get(hash);
        }
        
        public boolean isEmpty() {
            return circle.isEmpty();
        }
        
        private int hash(Object key) {
            return Math.abs(key.hashCode());
        }
    }
}