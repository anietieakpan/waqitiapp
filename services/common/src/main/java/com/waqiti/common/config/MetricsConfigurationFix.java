package com.waqiti.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;

/**
 * Fixed MetricsConfiguration to resolve @ConstructorBinding and autowiring issues
 * Separates configuration properties from component configuration
 */
@Configuration
@EnableConfigurationProperties(MetricsConfigurationFix.MetricsProperties.class)
public class MetricsConfigurationFix {

    /**
     * Configuration properties class - separate from @Component
     * This fixes the @ConstructorBinding annotation issue
     */
    @ConfigurationProperties(prefix = "metrics")
    @Validated
    public static class MetricsProperties {
        
        @NotNull
        private boolean enabled = true;
        
        @Min(1)
        private int reportingInterval = 60;
        
        @NotNull
        private String exportFormat = "json";
        
        @Min(0)
        private double samplingRate = 1.0;
        
        @NotNull
        private Duration flushInterval = Duration.ofMinutes(1);
        
        private boolean enableJvmMetrics = true;
        
        private boolean enableSystemMetrics = true;
        
        private boolean enableCustomMetrics = true;
        
        private boolean enableHttpMetrics = true;
        
        private int histogramBuckets = 10;
        
        private boolean enablePrometheusExport = true;
        
        private boolean enableGraphiteExport = false;
        
        private String graphiteHost = "localhost";
        
        private int graphitePort = 2003;
        
        private Duration metricsRetention = Duration.ofDays(7);
        
        // Default constructor
        public MetricsProperties() {}
        
        // Getters and setters
        public boolean isEnabled() {
            return enabled;
        }
        
        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
        
        public int getReportingInterval() {
            return reportingInterval;
        }
        
        public void setReportingInterval(int reportingInterval) {
            this.reportingInterval = reportingInterval;
        }
        
        public String getExportFormat() {
            return exportFormat;
        }
        
        public void setExportFormat(String exportFormat) {
            this.exportFormat = exportFormat;
        }
        
        public double getSamplingRate() {
            return samplingRate;
        }
        
        public void setSamplingRate(double samplingRate) {
            this.samplingRate = samplingRate;
        }
        
        public Duration getFlushInterval() {
            return flushInterval;
        }
        
        public void setFlushInterval(Duration flushInterval) {
            this.flushInterval = flushInterval;
        }
        
        public boolean isEnableJvmMetrics() {
            return enableJvmMetrics;
        }
        
        public void setEnableJvmMetrics(boolean enableJvmMetrics) {
            this.enableJvmMetrics = enableJvmMetrics;
        }
        
        public boolean isEnableSystemMetrics() {
            return enableSystemMetrics;
        }
        
        public void setEnableSystemMetrics(boolean enableSystemMetrics) {
            this.enableSystemMetrics = enableSystemMetrics;
        }
        
        public boolean isEnableCustomMetrics() {
            return enableCustomMetrics;
        }
        
        public void setEnableCustomMetrics(boolean enableCustomMetrics) {
            this.enableCustomMetrics = enableCustomMetrics;
        }
        
        public boolean isEnableHttpMetrics() {
            return enableHttpMetrics;
        }
        
        public void setEnableHttpMetrics(boolean enableHttpMetrics) {
            this.enableHttpMetrics = enableHttpMetrics;
        }
        
        public int getHistogramBuckets() {
            return histogramBuckets;
        }
        
        public void setHistogramBuckets(int histogramBuckets) {
            this.histogramBuckets = histogramBuckets;
        }
        
        public boolean isEnablePrometheusExport() {
            return enablePrometheusExport;
        }
        
        public void setEnablePrometheusExport(boolean enablePrometheusExport) {
            this.enablePrometheusExport = enablePrometheusExport;
        }
        
        public boolean isEnableGraphiteExport() {
            return enableGraphiteExport;
        }
        
        public void setEnableGraphiteExport(boolean enableGraphiteExport) {
            this.enableGraphiteExport = enableGraphiteExport;
        }
        
        public String getGraphiteHost() {
            return graphiteHost;
        }
        
        public void setGraphiteHost(String graphiteHost) {
            this.graphiteHost = graphiteHost;
        }
        
        public int getGraphitePort() {
            return graphitePort;
        }
        
        public void setGraphitePort(int graphitePort) {
            this.graphitePort = graphitePort;
        }
        
        public Duration getMetricsRetention() {
            return metricsRetention;
        }
        
        public void setMetricsRetention(Duration metricsRetention) {
            this.metricsRetention = metricsRetention;
        }
    }
    
    /**
     * Bean configuration that uses the properties
     * This fixes the primitive autowiring issues
     */
    @Bean
    public MetricsConfigurationValues metricsConfigurationValues(MetricsProperties properties) {
        return MetricsConfigurationValues.builder()
                .enabled(properties.isEnabled())
                .reportingInterval(properties.getReportingInterval())
                .exportFormat(properties.getExportFormat())
                .samplingRate(properties.getSamplingRate())
                .flushInterval(properties.getFlushInterval())
                .enableJvmMetrics(properties.isEnableJvmMetrics())
                .enableSystemMetrics(properties.isEnableSystemMetrics())
                .enableCustomMetrics(properties.isEnableCustomMetrics())
                .enableHttpMetrics(properties.isEnableHttpMetrics())
                .histogramBuckets(properties.getHistogramBuckets())
                .enablePrometheusExport(properties.isEnablePrometheusExport())
                .enableGraphiteExport(properties.isEnableGraphiteExport())
                .graphiteHost(properties.getGraphiteHost())
                .graphitePort(properties.getGraphitePort())
                .metricsRetention(properties.getMetricsRetention())
                .build();
    }
    
    /**
     * Metrics configuration values holder
     * This provides autowirable non-primitive values
     */
    public static class MetricsConfigurationValues {
        private final boolean enabled;
        private final int reportingInterval;
        private final String exportFormat;
        private final double samplingRate;
        private final Duration flushInterval;
        private final boolean enableJvmMetrics;
        private final boolean enableSystemMetrics;
        private final boolean enableCustomMetrics;
        private final boolean enableHttpMetrics;
        private final int histogramBuckets;
        private final boolean enablePrometheusExport;
        private final boolean enableGraphiteExport;
        private final String graphiteHost;
        private final int graphitePort;
        private final Duration metricsRetention;
        
        private MetricsConfigurationValues(Builder builder) {
            this.enabled = builder.enabled;
            this.reportingInterval = builder.reportingInterval;
            this.exportFormat = builder.exportFormat;
            this.samplingRate = builder.samplingRate;
            this.flushInterval = builder.flushInterval;
            this.enableJvmMetrics = builder.enableJvmMetrics;
            this.enableSystemMetrics = builder.enableSystemMetrics;
            this.enableCustomMetrics = builder.enableCustomMetrics;
            this.enableHttpMetrics = builder.enableHttpMetrics;
            this.histogramBuckets = builder.histogramBuckets;
            this.enablePrometheusExport = builder.enablePrometheusExport;
            this.enableGraphiteExport = builder.enableGraphiteExport;
            this.graphiteHost = builder.graphiteHost;
            this.graphitePort = builder.graphitePort;
            this.metricsRetention = builder.metricsRetention;
        }
        
        public static Builder builder() {
            return new Builder();
        }
        
        // Getters
        public boolean isEnabled() { return enabled; }
        public int getReportingInterval() { return reportingInterval; }
        public String getExportFormat() { return exportFormat; }
        public double getSamplingRate() { return samplingRate; }
        public Duration getFlushInterval() { return flushInterval; }
        public boolean isEnableJvmMetrics() { return enableJvmMetrics; }
        public boolean isEnableSystemMetrics() { return enableSystemMetrics; }
        public boolean isEnableCustomMetrics() { return enableCustomMetrics; }
        public boolean isEnableHttpMetrics() { return enableHttpMetrics; }
        public int getHistogramBuckets() { return histogramBuckets; }
        public boolean isEnablePrometheusExport() { return enablePrometheusExport; }
        public boolean isEnableGraphiteExport() { return enableGraphiteExport; }
        public String getGraphiteHost() { return graphiteHost; }
        public int getGraphitePort() { return graphitePort; }
        public Duration getMetricsRetention() { return metricsRetention; }
        
        public static class Builder {
            private boolean enabled = true;
            private int reportingInterval = 60;
            private String exportFormat = "json";
            private double samplingRate = 1.0;
            private Duration flushInterval = Duration.ofMinutes(1);
            private boolean enableJvmMetrics = true;
            private boolean enableSystemMetrics = true;
            private boolean enableCustomMetrics = true;
            private boolean enableHttpMetrics = true;
            private int histogramBuckets = 10;
            private boolean enablePrometheusExport = true;
            private boolean enableGraphiteExport = false;
            private String graphiteHost = "localhost";
            private int graphitePort = 2003;
            private Duration metricsRetention = Duration.ofDays(7);
            
            public Builder enabled(boolean enabled) {
                this.enabled = enabled;
                return this;
            }
            
            public Builder reportingInterval(int reportingInterval) {
                this.reportingInterval = reportingInterval;
                return this;
            }
            
            public Builder exportFormat(String exportFormat) {
                this.exportFormat = exportFormat;
                return this;
            }
            
            public Builder samplingRate(double samplingRate) {
                this.samplingRate = samplingRate;
                return this;
            }
            
            public Builder flushInterval(Duration flushInterval) {
                this.flushInterval = flushInterval;
                return this;
            }
            
            public Builder enableJvmMetrics(boolean enableJvmMetrics) {
                this.enableJvmMetrics = enableJvmMetrics;
                return this;
            }
            
            public Builder enableSystemMetrics(boolean enableSystemMetrics) {
                this.enableSystemMetrics = enableSystemMetrics;
                return this;
            }
            
            public Builder enableCustomMetrics(boolean enableCustomMetrics) {
                this.enableCustomMetrics = enableCustomMetrics;
                return this;
            }
            
            public Builder enableHttpMetrics(boolean enableHttpMetrics) {
                this.enableHttpMetrics = enableHttpMetrics;
                return this;
            }
            
            public Builder histogramBuckets(int histogramBuckets) {
                this.histogramBuckets = histogramBuckets;
                return this;
            }
            
            public Builder enablePrometheusExport(boolean enablePrometheusExport) {
                this.enablePrometheusExport = enablePrometheusExport;
                return this;
            }
            
            public Builder enableGraphiteExport(boolean enableGraphiteExport) {
                this.enableGraphiteExport = enableGraphiteExport;
                return this;
            }
            
            public Builder graphiteHost(String graphiteHost) {
                this.graphiteHost = graphiteHost;
                return this;
            }
            
            public Builder graphitePort(int graphitePort) {
                this.graphitePort = graphitePort;
                return this;
            }
            
            public Builder metricsRetention(Duration metricsRetention) {
                this.metricsRetention = metricsRetention;
                return this;
            }
            
            public MetricsConfigurationValues build() {
                return new MetricsConfigurationValues(this);
            }
        }
    }
}