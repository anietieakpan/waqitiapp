package com.waqiti.analytics.config.health;

import com.waqiti.analytics.config.properties.AnalyticsProperties;
import com.waqiti.analytics.config.properties.InfluxDBProperties;
import com.waqiti.analytics.config.properties.SparkProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Configuration Health Indicator
 *
 * <p>Spring Boot Actuator health indicator that monitors the health and
 * validity of configuration properties. Exposed via /actuator/health endpoint
 * for operational monitoring and alerting.
 *
 * <p>Health Checks:
 * <ul>
 *   <li>InfluxDB connection configuration</li>
 *   <li>Spark cluster configuration</li>
 *   <li>Analytics feature enablement status</li>
 *   <li>Resource allocation warnings</li>
 * </ul>
 *
 * @author Waqiti Analytics Team
 * @since 1.0.0
 * @version 1.0
 */
@Component("configurationHealth")
@RequiredArgsConstructor
public class ConfigurationHealthIndicator implements HealthIndicator {

    private final AnalyticsProperties analyticsProperties;
    private final InfluxDBProperties influxDBProperties;
    private final SparkProperties sparkProperties;

    @Override
    public Health health() {
        Map<String, Object> details = new HashMap<>();

        // Check InfluxDB configuration
        boolean influxConfigured = influxDBProperties.isConfigured();
        details.put("influxdb.configured", influxConfigured);
        details.put("influxdb.url", influxDBProperties.getUrl());
        details.put("influxdb.org", influxDBProperties.getOrg());
        details.put("influxdb.bucket", influxDBProperties.getBucket());
        details.put("influxdb.token", influxDBProperties.getMaskedToken());

        // Check Spark configuration
        boolean sparkMemoryValid = sparkProperties.isMemoryConfigurationValid();
        details.put("spark.memory.valid", sparkMemoryValid);
        details.put("spark.master", sparkProperties.getMaster());
        details.put("spark.executor.memory", sparkProperties.getExecutor().getMemory());
        details.put("spark.executor.cores", sparkProperties.getExecutor().getCores());
        details.put("spark.driver.memory", sparkProperties.getDriver().getMemory());

        // Check Analytics features
        details.put("analytics.realtime.enabled", analyticsProperties.getRealTime().isEnabled());
        details.put("analytics.ml.enabled", analyticsProperties.getMl().isEnabled());
        details.put("analytics.etl.enabled", analyticsProperties.getEtl().isEnabled());
        details.put("analytics.reporting.enabled", analyticsProperties.getReporting().isEnabled());

        // Check thread configuration
        int maxThreads = analyticsProperties.getProcessing().getMaxThreads();
        int cpuCores = Runtime.getRuntime().availableProcessors();
        boolean threadConfigWarning = maxThreads > cpuCores * 2;
        details.put("analytics.processing.max-threads", maxThreads);
        details.put("system.cpu.cores", cpuCores);
        details.put("analytics.processing.thread-config-warning", threadConfigWarning);

        // Check retention configuration
        int rawDataDays = analyticsProperties.getRetention().getRawDataDays();
        int aggregatedDataDays = analyticsProperties.getRetention().getAggregatedDataDays();
        boolean retentionWarning = rawDataDays > aggregatedDataDays;
        details.put("analytics.retention.raw-data-days", rawDataDays);
        details.put("analytics.retention.aggregated-data-days", aggregatedDataDays);
        details.put("analytics.retention.configuration-warning", retentionWarning);

        // Determine overall health status
        if (!influxConfigured && analyticsProperties.getRealTime().isEnabled()) {
            return Health.down()
                    .withDetails(details)
                    .withDetail("error", "InfluxDB not configured but real-time analytics is enabled")
                    .build();
        }

        if (!sparkMemoryValid) {
            return Health.down()
                    .withDetails(details)
                    .withDetail("error", "Invalid Spark memory configuration: executor memory must be >= driver memory")
                    .build();
        }

        if (threadConfigWarning || retentionWarning) {
            return Health.up()
                    .withDetails(details)
                    .withDetail("warning", "Configuration has non-critical warnings")
                    .build();
        }

        return Health.up()
                .withDetails(details)
                .withDetail("status", "All configuration properties are healthy")
                .build();
    }
}
