package com.waqiti.analytics.config.properties;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

/**
 * Apache Spark Configuration Properties
 *
 * <p>Production-grade configuration for Apache Spark integration used in
 * large-scale data processing, machine learning, and analytics workloads.
 *
 * <p>Features:
 * <ul>
 *   <li>Spark application and cluster configuration</li>
 *   <li>Executor and driver memory/CPU settings with validation</li>
 *   <li>SQL optimization settings for adaptive query execution</li>
 *   <li>Resource allocation with safety constraints</li>
 * </ul>
 *
 * @author Waqiti Analytics Team
 * @since 1.0.0
 * @version 1.0
 */
@Data
@Component
@Validated
@ConfigurationProperties(prefix = "spark")
@Schema(description = "Apache Spark distributed computing configuration")
public class SparkProperties {

    @NotBlank(message = "Spark application name cannot be blank")
    @Schema(
        description = "Spark application name for identification",
        example = "Waqiti Analytics Service",
        required = true
    )
    private String appName = "Waqiti Analytics Service";

    @NotBlank(message = "Spark master URL cannot be blank")
    @Pattern(
        regexp = "^(local(\\[\\*?\\d*\\])?|spark://.*|yarn|mesos://.*|k8s://.*)",
        message = "Spark master must be local[*], spark://, yarn, mesos://, or k8s://"
    )
    @Schema(
        description = "Spark master URL (local, standalone, YARN, Mesos, or Kubernetes)",
        example = "local[*]",
        pattern = "^(local(\\[\\*?\\d*\\])?|spark://.*|yarn|mesos://.*|k8s://.*)",
        required = true
    )
    private String master = "local[*]";

    @Valid
    private SqlConfig sql = new SqlConfig();

    @Valid
    private ExecutorConfig executor = new ExecutorConfig();

    @Valid
    private DriverConfig driver = new DriverConfig();

    /**
     * Spark SQL Configuration
     */
    @Data
    @Schema(description = "Spark SQL configuration for query optimization")
    public static class SqlConfig {

        @Valid
        private AdaptiveConfig adaptive = new AdaptiveConfig();

        @Data
        @Schema(description = "Adaptive query execution configuration")
        public static class AdaptiveConfig {

            @Schema(description = "Enable adaptive query execution (AQE)", example = "true")
            private boolean enabled = true;

            @Valid
            private CoalescePartitionsConfig coalescePartitions = new CoalescePartitionsConfig();

            @Data
            @Schema(description = "Partition coalescing configuration for AQE")
            public static class CoalescePartitionsConfig {

                @Schema(description = "Enable automatic partition coalescing", example = "true")
                private boolean enabled = true;
            }
        }
    }

    /**
     * Spark Executor Configuration
     */
    @Data
    @Schema(description = "Spark executor resource configuration")
    public static class ExecutorConfig {

        @NotBlank(message = "Executor memory cannot be blank")
        @Pattern(
            regexp = "^\\d+[kKmMgGtT]$",
            message = "Executor memory must be in format: number + unit (k/m/g/t), e.g., '2g', '1024m'"
        )
        @Schema(
            description = "Executor memory allocation (format: number + unit k/m/g/t)",
            example = "2g",
            pattern = "^\\d+[kKmMgGtT]$",
            required = true
        )
        private String memory = "2g";

        @Min(value = 1, message = "Executor cores must be at least 1")
        @Schema(
            description = "Number of cores per executor",
            example = "2",
            minimum = "1",
            required = true
        )
        private int cores = 2;
    }

    /**
     * Spark Driver Configuration
     */
    @Data
    @Schema(description = "Spark driver resource configuration")
    public static class DriverConfig {

        @NotBlank(message = "Driver memory cannot be blank")
        @Pattern(
            regexp = "^\\d+[kKmMgGtT]$",
            message = "Driver memory must be in format: number + unit (k/m/g/t), e.g., '1g', '512m'"
        )
        @Schema(
            description = "Driver memory allocation (format: number + unit k/m/g/t)",
            example = "1g",
            pattern = "^\\d+[kKmMgGtT]$",
            required = true
        )
        private String memory = "1g";
    }

    /**
     * Validates that executor memory is greater than driver memory
     *
     * @return true if executor memory >= driver memory
     */
    public boolean isMemoryConfigurationValid() {
        long executorBytes = parseMemoryString(executor.getMemory());
        long driverBytes = parseMemoryString(driver.getMemory());
        return executorBytes >= driverBytes;
    }

    /**
     * Parses memory string to bytes
     *
     * @param memoryStr memory string (e.g., "2g", "1024m")
     * @return memory in bytes
     */
    private long parseMemoryString(String memoryStr) {
        if (memoryStr == null || memoryStr.isEmpty()) {
            return 0;
        }

        String digits = memoryStr.substring(0, memoryStr.length() - 1);
        char unit = Character.toLowerCase(memoryStr.charAt(memoryStr.length() - 1));

        long value = Long.parseLong(digits);

        return switch (unit) {
            case 'k' -> value * 1024L;
            case 'm' -> value * 1024L * 1024L;
            case 'g' -> value * 1024L * 1024L * 1024L;
            case 't' -> value * 1024L * 1024L * 1024L * 1024L;
            default -> value;
        };
    }
}
