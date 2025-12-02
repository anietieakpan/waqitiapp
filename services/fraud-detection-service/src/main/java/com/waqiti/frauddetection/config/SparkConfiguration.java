package com.waqiti.frauddetection.config;

import lombok.extern.slf4j.Slf4j;
import org.apache.spark.SparkConf;
import org.apache.spark.api.java.JavaSparkContext;
import org.apache.spark.sql.SparkSession;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import javax.annotation.PreDestroy;

/**
 * Production-grade Apache Spark configuration for fraud detection ML processing
 */
@Configuration
@Slf4j
public class SparkConfiguration {

    @Value("${fraud.spark.app.name:FraudDetectionML}")
    private String appName;

    @Value("${fraud.spark.master:local[*]}")
    private String sparkMaster;

    @Value("${fraud.spark.executor.memory:2g}")
    private String executorMemory;

    @Value("${fraud.spark.driver.memory:1g}")
    private String driverMemory;

    @Value("${fraud.spark.executor.cores:2}")
    private String executorCores;

    @Value("${fraud.spark.sql.adaptive.enabled:true}")
    private boolean adaptiveQueryEnabled;

    @Value("${fraud.spark.sql.adaptive.coalescePartitions.enabled:true}")
    private boolean coalescePartitionsEnabled;

    @Value("${fraud.spark.serializer:org.apache.spark.serializer.KryoSerializer}")
    private String serializer;

    @Value("${fraud.spark.sql.execution.arrow.pyspark.enabled:true}")
    private boolean arrowEnabled;

    @Value("${fraud.spark.dynamicAllocation.enabled:true}")
    private boolean dynamicAllocationEnabled;

    @Value("${fraud.spark.dynamicAllocation.minExecutors:1}")
    private String minExecutors;

    @Value("${fraud.spark.dynamicAllocation.maxExecutors:10}")
    private String maxExecutors;

    @Value("${fraud.spark.sql.warehouse.dir:/tmp/spark-warehouse}")
    private String warehouseDir;

    @Value("${fraud.spark.checkpointDir:/tmp/spark-checkpoints}")
    private String checkpointDir;

    private SparkSession sparkSession;
    private JavaSparkContext javaSparkContext;

    /**
     * Create and configure SparkSession for fraud detection ML workloads
     */
    @Bean
    @Profile("!test")
    public SparkSession sparkSession() {
        try {
            log.info("Initializing Spark session for fraud detection ML");

            SparkConf sparkConf = new SparkConf()
                .setAppName(appName)
                .setMaster(sparkMaster)
                
                // Memory configuration
                .set("spark.executor.memory", executorMemory)
                .set("spark.driver.memory", driverMemory)
                .set("spark.executor.cores", executorCores)
                
                // Performance optimizations
                .set("spark.serializer", serializer)
                .set("spark.sql.adaptive.enabled", String.valueOf(adaptiveQueryEnabled))
                .set("spark.sql.adaptive.coalescePartitions.enabled", String.valueOf(coalescePartitionsEnabled))
                .set("spark.sql.execution.arrow.pyspark.enabled", String.valueOf(arrowEnabled))
                
                // Dynamic allocation for resource efficiency
                .set("spark.dynamicAllocation.enabled", String.valueOf(dynamicAllocationEnabled))
                .set("spark.dynamicAllocation.minExecutors", minExecutors)
                .set("spark.dynamicAllocation.maxExecutors", maxExecutors)
                .set("spark.dynamicAllocation.initialExecutors", "2")
                .set("spark.dynamicAllocation.targetExecutors", "4")
                
                // Shuffle and networking
                .set("spark.shuffle.compress", "true")
                .set("spark.shuffle.spill.compress", "true")
                .set("spark.rdd.compress", "true")
                .set("spark.io.compression.codec", "org.apache.spark.io.SnappyCompressionCodec")
                
                // SQL optimizations for fraud detection queries
                .set("spark.sql.adaptive.advisoryPartitionSizeInBytes", "128MB")
                .set("spark.sql.adaptive.skewJoin.enabled", "true")
                .set("spark.sql.adaptive.localShuffleReader.enabled", "true")
                .set("spark.sql.cbo.enabled", "true")
                .set("spark.sql.statistics.histogram.enabled", "true")
                
                // Checkpointing for fault tolerance
                .set("spark.sql.streaming.checkpointLocation", checkpointDir)
                .set("spark.sql.warehouse.dir", warehouseDir)
                
                // Kryo serialization optimizations
                .set("spark.kryo.registrationRequired", "false")
                .set("spark.kryo.unsafe", "true")
                .set("spark.kryoserializer.buffer.max", "128m")
                
                // Memory fraction tuning for ML workloads
                .set("spark.executor.memoryFraction", "0.7")
                .set("spark.storage.memoryFraction", "0.3")
                
                // Garbage collection optimizations
                .set("spark.executor.extraJavaOptions", 
                     "-XX:+UseG1GC -XX:+UnlockDiagnosticVMOptions -XX:+G1PrintRegionRememberSetInfo")
                .set("spark.driver.extraJavaOptions", 
                     "-XX:+UseG1GC -XX:+UnlockDiagnosticVMOptions")
                
                // Network timeouts
                .set("spark.network.timeout", "300s")
                .set("spark.executor.heartbeatInterval", "20s")
                .set("spark.rpc.askTimeout", "300s")
                
                // ML-specific configurations
                .set("spark.ml.cache.enabled", "true")
                .set("spark.ml.tree.impl", "new")
                .set("spark.mllib.tree.impl", "new");

            // Add additional ML libraries to classpath if available
            addMLLibraries(sparkConf);

            sparkSession = SparkSession.builder()
                .config(sparkConf)
                .enableHiveSupport()  // Enable Hive support for advanced SQL features
                .getOrCreate();

            // Set checkpoint directory
            sparkSession.sparkContext().setCheckpointDir(checkpointDir);

            // Set log level to reduce noise
            sparkSession.sparkContext().setLogLevel("WARN");

            log.info("Spark session initialized successfully with master: {}", sparkMaster);
            log.info("Spark UI available at: {}", sparkSession.sparkContext().uiWebUrl().get());

            return sparkSession;

        } catch (Exception e) {
            log.error("Failed to initialize Spark session", e);
            throw new RuntimeException("Failed to initialize Spark session", e);
        }
    }

    /**
     * Create JavaSparkContext bean for low-level Spark operations
     */
    @Bean
    @Profile("!test")
    public JavaSparkContext javaSparkContext(SparkSession sparkSession) {
        javaSparkContext = new JavaSparkContext(sparkSession.sparkContext());
        log.info("Java Spark Context created successfully");
        return javaSparkContext;
    }

    /**
     * Test configuration for Spark (lightweight)
     */
    @Bean
    @Profile("test")
    public SparkSession testSparkSession() {
        log.info("Initializing test Spark session");
        
        SparkConf testConf = new SparkConf()
            .setAppName("FraudDetectionML-Test")
            .setMaster("local[2]")
            .set("spark.executor.memory", "512m")
            .set("spark.driver.memory", "512m")
            .set("spark.sql.warehouse.dir", "/tmp/test-spark-warehouse")
            .set("spark.ui.enabled", "false")
            .set("spark.sql.adaptive.enabled", "false");

        sparkSession = SparkSession.builder()
            .config(testConf)
            .getOrCreate();

        sparkSession.sparkContext().setLogLevel("ERROR");
        
        return sparkSession;
    }

    private void addMLLibraries(SparkConf sparkConf) {
        try {
            // Add additional ML libraries if they're available on the classpath
            StringBuilder jars = new StringBuilder();
            
            // Common ML libraries for fraud detection
            String[] mlLibraries = {
                "org.apache.spark:spark-mllib_2.12",
                "org.apache.spark:spark-sql-kafka-0-10_2.12",
                "com.github.fommil.netlib:all",
                "org.scalanlp:breeze_2.12"
            };
            
            // Note: In production, these would be resolved through dependency management
            // This is just for configuration completeness
            
            if (jars.length() > 0) {
                sparkConf.set("spark.jars", jars.toString());
            }
            
        } catch (Exception e) {
            log.warn("Could not add ML libraries to Spark configuration", e);
        }
    }

    /**
     * Health check for Spark session
     */
    public boolean isSparkHealthy() {
        try {
            if (sparkSession == null) {
                return false;
            }
            
            // Simple health check - create a small dataset
            return sparkSession.range(1).count() == 1;
            
        } catch (Exception e) {
            log.warn("Spark health check failed", e);
            return false;
        }
    }

    /**
     * Get Spark session statistics
     */
    public SparkStatistics getSparkStatistics() {
        if (sparkSession == null) {
            return SparkStatistics.builder()
                .healthy(false)
                .status("NOT_INITIALIZED")
                .build();
        }

        try {
            return SparkStatistics.builder()
                .healthy(true)
                .status("RUNNING")
                .appName(sparkSession.sparkContext().appName())
                .sparkVersion(sparkSession.version())
                .master(sparkSession.sparkContext().master())
                .executors(sparkSession.sparkContext().getExecutorInfos().size())
                .totalCores(sparkSession.sparkContext().defaultParallelism())
                .uiWebUrl(sparkSession.sparkContext().uiWebUrl().getOrElse("N/A"))
                .build();
        } catch (Exception e) {
            log.warn("Failed to get Spark statistics", e);
            return SparkStatistics.builder()
                .healthy(false)
                .status("ERROR")
                .error(e.getMessage())
                .build();
        }
    }

    /**
     * Restart Spark session (for maintenance)
     */
    public void restartSparkSession() {
        log.info("Restarting Spark session");
        
        try {
            if (sparkSession != null) {
                sparkSession.stop();
            }
            
            // Reinitialize
            sparkSession = sparkSession();
            
            log.info("Spark session restarted successfully");
            
        } catch (Exception e) {
            log.error("Failed to restart Spark session", e);
            throw new RuntimeException("Failed to restart Spark session", e);
        }
    }

    /**
     * Cleanup Spark resources on application shutdown
     */
    @PreDestroy
    public void cleanup() {
        log.info("Shutting down Spark session");
        
        try {
            if (javaSparkContext != null) {
                javaSparkContext.close();
            }
            
            if (sparkSession != null) {
                sparkSession.stop();
            }
            
            log.info("Spark session shutdown completed");
            
        } catch (Exception e) {
            log.error("Error during Spark cleanup", e);
        }
    }

    // Statistics DTO
    @lombok.Data
    @lombok.Builder
    public static class SparkStatistics {
        private boolean healthy;
        private String status;
        private String appName;
        private String sparkVersion;
        private String master;
        private int executors;
        private int totalCores;
        private String uiWebUrl;
        private String error;
    }
}