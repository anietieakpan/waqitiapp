package com.waqiti.crypto.lightning.monitoring;

import com.waqiti.crypto.lightning.service.LightningNetworkService;
import com.waqiti.crypto.lightning.entity.*;
import com.waqiti.crypto.lightning.repository.*;
import io.micrometer.core.instrument.*;
import io.micrometer.core.instrument.binder.MeterBinder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Comprehensive Lightning Network Monitoring Service
 * Provides real-time metrics, health monitoring, and alerting
 * Integrates with Prometheus, Grafana, and alerting systems
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class LightningMonitoringService implements HealthIndicator, MeterBinder {

    private final LightningNetworkService lightningService;
    private final InvoiceRepository invoiceRepository;
    private final PaymentRepository paymentRepository;
    private final ChannelRepository channelRepository;
    private final StreamRepository streamRepository;
    private final SwapRepository swapRepository;
    private final LightningAuditRepository auditRepository;
    private final LightningAlertService alertService;

    @Value("${waqiti.lightning.monitoring.enabled:true}")
    private boolean monitoringEnabled;

    @Value("${waqiti.lightning.monitoring.metrics-interval:PT30S}")
    private Duration metricsInterval;

    @Value("${waqiti.lightning.monitoring.health-check-interval:PT1M}")
    private Duration healthCheckInterval;

    @Value("${waqiti.lightning.monitoring.alert-thresholds.payment-failure-rate:0.05}")
    private double paymentFailureThreshold;

    @Value("${waqiti.lightning.monitoring.alert-thresholds.channel-balance-low:0.1}")
    private double channelBalanceLowThreshold;

    @Value("${waqiti.lightning.monitoring.alert-thresholds.node-sync-lag:10}")
    private int nodeSyncLagBlocksThreshold;

    @Value("${waqiti.lightning.monitoring.alert-thresholds.response-time-ms:5000}")
    private long responseTimeThreshold;

    // Metrics and gauges
    private MeterRegistry meterRegistry;
    private Gauge nodeBalanceGauge;
    private Gauge channelCountGauge;
    private Gauge activeChannelCountGauge;
    private Gauge totalCapacityGauge;
    private Gauge localBalanceGauge;
    private Gauge remoteBalanceGauge;
    private Gauge pendingChannelsGauge;
    private Gauge peerCountGauge;
    private Gauge blockHeightGauge;
    private Gauge syncProgressGauge;

    // Counters
    private Counter invoiceCreatedCounter;
    private Counter invoiceSettledCounter;
    private Counter invoiceExpiredCounter;
    private Counter paymentSentCounter;
    private Counter paymentFailedCounter;
    private Counter keysendSentCounter;
    private Counter channelOpenedCounter;
    private Counter channelClosedCounter;
    private Counter alertGeneratedCounter;

    // Timers
    private Timer invoiceCreationTimer;
    private Timer paymentSendingTimer;
    private Timer channelOperationTimer;
    private Timer apiResponseTimer;

    // Distribution summaries
    private DistributionSummary invoiceAmountSummary;
    private DistributionSummary paymentAmountSummary;
    private DistributionSummary paymentFeeSummary;
    private DistributionSummary channelCapacitySummary;

    // Real-time monitoring data
    private final Map<String, AtomicLong> realtimeMetrics = new ConcurrentHashMap<>();
    private final Map<String, AtomicReference<Object>> systemState = new ConcurrentHashMap<>();
    private final Queue<PerformanceSnapshot> performanceHistory = new LinkedList<>();
    private volatile Instant lastHealthCheck = Instant.now();
    private volatile HealthStatus currentHealthStatus = HealthStatus.UNKNOWN;

    @PostConstruct
    public void init() {
        if (!monitoringEnabled) {
            log.info("Lightning monitoring is disabled");
            return;
        }

        log.info("Initializing Lightning Network monitoring service");
        initializeRealtimeMetrics();
        log.info("Lightning Network monitoring service initialized successfully");
    }

    @Override
    public void bindTo(MeterRegistry registry) {
        this.meterRegistry = registry;
        initializeMetrics(registry);
        log.info("Lightning Network metrics bound to registry");
    }

    // ============ METRICS INITIALIZATION ============

    private void initializeMetrics(MeterRegistry registry) {
        // Gauge metrics
        nodeBalanceGauge = Gauge.builder("lightning.node.balance.total", this, LightningMonitoringService::getNodeBalance)
            .description("Total Lightning node balance in satoshis")
            .register(registry);

        channelCountGauge = Gauge.builder("lightning.channels.count.total", this, LightningMonitoringService::getChannelCount)
            .description("Total number of Lightning channels")
            .register(registry);

        activeChannelCountGauge = Gauge.builder("lightning.channels.count.active", this, LightningMonitoringService::getActiveChannelCount)
            .description("Number of active Lightning channels")
            .register(registry);

        totalCapacityGauge = Gauge.builder("lightning.channels.capacity.total", this, LightningMonitoringService::getTotalCapacity)
            .description("Total Lightning channel capacity in satoshis")
            .register(registry);

        localBalanceGauge = Gauge.builder("lightning.channels.balance.local", this, LightningMonitoringService::getLocalBalance)
            .description("Total local channel balance in satoshis")
            .register(registry);

        remoteBalanceGauge = Gauge.builder("lightning.channels.balance.remote", this, LightningMonitoringService::getRemoteBalance)
            .description("Total remote channel balance in satoshis")
            .register(registry);

        pendingChannelsGauge = Gauge.builder("lightning.channels.count.pending", this, LightningMonitoringService::getPendingChannelCount)
            .description("Number of pending Lightning channels")
            .register(registry);

        peerCountGauge = Gauge.builder("lightning.peers.count", this, LightningMonitoringService::getPeerCount)
            .description("Number of connected Lightning peers")
            .register(registry);

        blockHeightGauge = Gauge.builder("lightning.node.block_height", this, LightningMonitoringService::getBlockHeight)
            .description("Current block height known by Lightning node")
            .register(registry);

        syncProgressGauge = Gauge.builder("lightning.node.sync_progress", this, LightningMonitoringService::getSyncProgress)
            .description("Node synchronization progress (0-1)")
            .register(registry);

        // Counter metrics
        invoiceCreatedCounter = Counter.builder("lightning.invoices.created.total")
            .description("Total number of invoices created")
            .register(registry);

        invoiceSettledCounter = Counter.builder("lightning.invoices.settled.total")
            .description("Total number of invoices settled")
            .register(registry);

        invoiceExpiredCounter = Counter.builder("lightning.invoices.expired.total")
            .description("Total number of invoices expired")
            .register(registry);

        paymentSentCounter = Counter.builder("lightning.payments.sent.total")
            .description("Total number of payments sent")
            .register(registry);

        paymentFailedCounter = Counter.builder("lightning.payments.failed.total")
            .description("Total number of payments failed")
            .register(registry);

        keysendSentCounter = Counter.builder("lightning.keysend.sent.total")
            .description("Total number of keysend payments sent")
            .register(registry);

        channelOpenedCounter = Counter.builder("lightning.channels.opened.total")
            .description("Total number of channels opened")
            .register(registry);

        channelClosedCounter = Counter.builder("lightning.channels.closed.total")
            .description("Total number of channels closed")
            .register(registry);

        alertGeneratedCounter = Counter.builder("lightning.alerts.generated.total")
            .description("Total number of monitoring alerts generated")
            .register(registry);

        // Timer metrics
        invoiceCreationTimer = Timer.builder("lightning.operations.invoice_creation.duration")
            .description("Time taken to create Lightning invoices")
            .register(registry);

        paymentSendingTimer = Timer.builder("lightning.operations.payment_sending.duration")
            .description("Time taken to send Lightning payments")
            .register(registry);

        channelOperationTimer = Timer.builder("lightning.operations.channel.duration")
            .description("Time taken for channel operations")
            .register(registry);

        apiResponseTimer = Timer.builder("lightning.api.response_time")
            .description("Lightning API response time")
            .register(registry);

        // Distribution summary metrics
        invoiceAmountSummary = DistributionSummary.builder("lightning.invoices.amount")
            .description("Distribution of Lightning invoice amounts")
            .baseUnit("satoshis")
            .register(registry);

        paymentAmountSummary = DistributionSummary.builder("lightning.payments.amount")
            .description("Distribution of Lightning payment amounts")
            .baseUnit("satoshis")
            .register(registry);

        paymentFeeSummary = DistributionSummary.builder("lightning.payments.fee")
            .description("Distribution of Lightning payment fees")
            .baseUnit("satoshis")
            .register(registry);

        channelCapacitySummary = DistributionSummary.builder("lightning.channels.capacity")
            .description("Distribution of Lightning channel capacities")
            .baseUnit("satoshis")
            .register(registry);
    }

    private void initializeRealtimeMetrics() {
        realtimeMetrics.put("invoice_creation_rate", new AtomicLong(0));
        realtimeMetrics.put("payment_success_rate", new AtomicLong(0));
        realtimeMetrics.put("payment_failure_count", new AtomicLong(0));
        realtimeMetrics.put("channel_rebalance_count", new AtomicLong(0));
        realtimeMetrics.put("api_error_count", new AtomicLong(0));
        realtimeMetrics.put("audit_event_count", new AtomicLong(0));

        systemState.put("node_status", new AtomicReference<>("UNKNOWN"));
        systemState.put("wallet_status", new AtomicReference<>("UNKNOWN"));
        systemState.put("sync_status", new AtomicReference<>("UNKNOWN"));
        systemState.put("last_error", new AtomicReference<>(""));
    }

    // ============ REAL-TIME MONITORING ============

    @Scheduled(fixedDelayString = "#{T(java.time.Duration).parse('${waqiti.lightning.monitoring.metrics-interval:PT30S}').toMillis()}")
    public void collectMetrics() {
        if (!monitoringEnabled) {
            return;
        }

        try {
            log.debug("Collecting Lightning Network metrics");

            // Collect node metrics
            collectNodeMetrics();

            // Collect channel metrics
            collectChannelMetrics();

            // Collect payment metrics
            collectPaymentMetrics();

            // Collect performance metrics
            collectPerformanceMetrics();

            // Update system state
            updateSystemState();

            // Check alert conditions
            checkAlertConditions();

            log.debug("Lightning Network metrics collection completed");

        } catch (Exception e) {
            log.error("Failed to collect Lightning Network metrics", e);
            realtimeMetrics.get("api_error_count").incrementAndGet();
        }
    }

    @Scheduled(fixedDelayString = "#{T(java.time.Duration).parse('${waqiti.lightning.monitoring.health-check-interval:PT1M}').toMillis()}")
    public void performHealthCheck() {
        if (!monitoringEnabled) {
            return;
        }

        try {
            log.debug("Performing Lightning Network health check");

            HealthStatus previousStatus = currentHealthStatus;
            currentHealthStatus = determineHealthStatus();
            lastHealthCheck = Instant.now();

            // Alert on health status changes
            if (previousStatus != currentHealthStatus && previousStatus != HealthStatus.UNKNOWN) {
                alertService.sendHealthStatusAlert(previousStatus, currentHealthStatus);
                alertGeneratedCounter.increment();
            }

            log.debug("Lightning Network health check completed: {}", currentHealthStatus);

        } catch (Exception e) {
            log.error("Failed to perform Lightning Network health check", e);
            currentHealthStatus = HealthStatus.CRITICAL;
        }
    }

    // ============ METRIC COLLECTION METHODS ============

    private void collectNodeMetrics() {
        try {
            LightningNetworkService.WalletInfo walletInfo = lightningService.getWalletInfo();
            if (walletInfo != null) {
                systemState.get("node_status").set("ONLINE");
                systemState.get("wallet_status").set(walletInfo.isSyncedToChain() ? "SYNCED" : "SYNCING");
            }
        } catch (Exception e) {
            systemState.get("node_status").set("OFFLINE");
            log.debug("Failed to collect node metrics", e);
        }
    }

    private void collectChannelMetrics() {
        try {
            List<ChannelEntity> channels = channelRepository.findAll();
            
            long totalCapacity = channels.stream()
                .mapToLong(ChannelEntity::getCapacity)
                .sum();
                
            long localBalance = channels.stream()
                .mapToLong(ChannelEntity::getLocalBalance)
                .sum();
                
            long remoteBalance = channels.stream()
                .mapToLong(ChannelEntity::getRemoteBalance)
                .sum();
                
            long activeChannels = channels.stream()
                .filter(ChannelEntity::getActive)
                .count();
                
            // Update channel capacity distribution
            channels.forEach(channel -> 
                channelCapacitySummary.record(channel.getCapacity()));

        } catch (Exception e) {
            log.debug("Failed to collect channel metrics", e);
        }
    }

    private void collectPaymentMetrics() {
        try {
            Instant oneHourAgo = Instant.now().minus(Duration.ofHours(1));
            
            // Recent invoice metrics
            List<InvoiceEntity> recentInvoices = invoiceRepository
                .findByCreatedAtAfter(oneHourAgo);
            
            long settledInvoices = recentInvoices.stream()
                .filter(invoice -> invoice.getStatus() == InvoiceStatus.SETTLED)
                .count();
                
            long expiredInvoices = recentInvoices.stream()
                .filter(invoice -> invoice.getStatus() == InvoiceStatus.EXPIRED)
                .count();
                
            // Recent payment metrics
            List<PaymentEntity> recentPayments = paymentRepository
                .findByCreatedAtAfter(oneHourAgo);
                
            long successfulPayments = recentPayments.stream()
                .filter(payment -> payment.getStatus() == PaymentStatus.SUCCEEDED)
                .count();
                
            long failedPayments = recentPayments.stream()
                .filter(payment -> payment.getStatus() == PaymentStatus.FAILED)
                .count();
                
            // Update payment metrics
            realtimeMetrics.get("payment_failure_count").set(failedPayments);
            
            // Calculate success rate
            if (successfulPayments + failedPayments > 0) {
                double successRate = (double) successfulPayments / (successfulPayments + failedPayments);
                realtimeMetrics.get("payment_success_rate").set((long) (successRate * 100));
            }
            
            // Update distribution summaries
            recentInvoices.forEach(invoice -> 
                invoiceAmountSummary.record(invoice.getAmountSat()));
                
            recentPayments.forEach(payment -> {
                paymentAmountSummary.record(payment.getAmountSat());
                if (payment.getFeeSat() != null) {
                    paymentFeeSummary.record(payment.getFeeSat());
                }
            });

        } catch (Exception e) {
            log.debug("Failed to collect payment metrics", e);
        }
    }

    private void collectPerformanceMetrics() {
        try {
            // Collect performance snapshot
            PerformanceSnapshot snapshot = PerformanceSnapshot.builder()
                .timestamp(Instant.now())
                .nodeBalance(getNodeBalance())
                .channelCount(getChannelCount())
                .activeChannelCount(getActiveChannelCount())
                .totalCapacity(getTotalCapacity())
                .localBalance(getLocalBalance())
                .remoteBalance(getRemoteBalance())
                .peerCount(getPeerCount())
                .build();
                
            // Maintain performance history (keep last 100 snapshots)
            performanceHistory.offer(snapshot);
            if (performanceHistory.size() > 100) {
                performanceHistory.poll();
            }

        } catch (Exception e) {
            log.debug("Failed to collect performance metrics", e);
        }
    }

    private void updateSystemState() {
        try {
            systemState.get("sync_status").set(
                lightningService.isFullySynced() ? "SYNCED" : "SYNCING");
                
        } catch (Exception e) {
            systemState.get("sync_status").set("ERROR");
            systemState.get("last_error").set(e.getMessage());
        }
    }

    // ============ ALERT CONDITIONS ============

    private void checkAlertConditions() {
        // Check payment failure rate
        checkPaymentFailureRate();
        
        // Check channel balance levels
        checkChannelBalanceLevels();
        
        // Check node sync status
        checkNodeSyncStatus();
        
        // Check response times
        checkResponseTimes();
        
        // Check channel availability
        checkChannelAvailability();
        
        // Check audit anomalies
        checkAuditAnomalies();
    }

    private void checkPaymentFailureRate() {
        try {
            long successRate = realtimeMetrics.get("payment_success_rate").get();
            double failureRate = (100.0 - successRate) / 100.0;
            
            if (failureRate > paymentFailureThreshold) {
                alertService.sendAlert(
                    AlertSeverity.HIGH,
                    "HIGH_PAYMENT_FAILURE_RATE",
                    String.format("Payment failure rate is %.2f%% (threshold: %.2f%%)",
                        failureRate * 100, paymentFailureThreshold * 100),
                    Map.of(
                        "failure_rate", failureRate,
                        "threshold", paymentFailureThreshold,
                        "success_rate", successRate
                    )
                );
                alertGeneratedCounter.increment();
            }
        } catch (Exception e) {
            log.debug("Failed to check payment failure rate", e);
        }
    }

    private void checkChannelBalanceLevels() {
        try {
            List<ChannelEntity> channels = channelRepository.findAll();
            
            for (ChannelEntity channel : channels) {
                if (channel.getActive()) {
                    double balanceRatio = (double) channel.getLocalBalance() / channel.getCapacity();
                    
                    if (balanceRatio < channelBalanceLowThreshold) {
                        alertService.sendAlert(
                            AlertSeverity.MEDIUM,
                            "LOW_CHANNEL_BALANCE",
                            String.format("Channel %s has low local balance: %.1f%% (threshold: %.1f%%)",
                                channel.getId(), balanceRatio * 100, channelBalanceLowThreshold * 100),
                            Map.of(
                                "channel_id", channel.getId(),
                                "balance_ratio", balanceRatio,
                                "threshold", channelBalanceLowThreshold,
                                "local_balance", channel.getLocalBalance(),
                                "capacity", channel.getCapacity()
                            )
                        );
                        alertGeneratedCounter.increment();
                    }
                }
            }
        } catch (Exception e) {
            log.debug("Failed to check channel balance levels", e);
        }
    }

    private void checkNodeSyncStatus() {
        try {
            if (!lightningService.isFullySynced()) {
                long syncLag = lightningService.getSyncLagBlocks();
                
                if (syncLag > nodeSyncLagBlocksThreshold) {
                    alertService.sendAlert(
                        AlertSeverity.HIGH,
                        "NODE_SYNC_LAG",
                        String.format("Node is %d blocks behind (threshold: %d blocks)",
                            syncLag, nodeSyncLagBlocksThreshold),
                        Map.of(
                            "sync_lag", syncLag,
                            "threshold", nodeSyncLagBlocksThreshold
                        )
                    );
                    alertGeneratedCounter.increment();
                }
            }
        } catch (Exception e) {
            log.debug("Failed to check node sync status", e);
        }
    }

    private void checkResponseTimes() {
        // This would typically be done via API instrumentation
        // For now, we'll check average response time from timer metrics
        try {
            Timer.Sample sample = Timer.start(meterRegistry);
            // Simulate API call measurement
            sample.stop(apiResponseTimer);
            
            double avgResponseTime = apiResponseTimer.mean(java.util.concurrent.TimeUnit.MILLISECONDS);
            
            if (avgResponseTime > responseTimeThreshold) {
                alertService.sendAlert(
                    AlertSeverity.MEDIUM,
                    "HIGH_RESPONSE_TIME",
                    String.format("Average API response time is %.1fms (threshold: %dms)",
                        avgResponseTime, responseTimeThreshold),
                    Map.of(
                        "avg_response_time", avgResponseTime,
                        "threshold", responseTimeThreshold
                    )
                );
                alertGeneratedCounter.increment();
            }
        } catch (Exception e) {
            log.debug("Failed to check response times", e);
        }
    }

    private void checkChannelAvailability() {
        try {
            List<ChannelEntity> channels = channelRepository.findAll();
            long totalChannels = channels.size();
            long activeChannels = channels.stream()
                .filter(ChannelEntity::getActive)
                .count();
                
            if (totalChannels > 0) {
                double availabilityRatio = (double) activeChannels / totalChannels;
                
                if (availabilityRatio < 0.8) { // Alert if less than 80% channels are active
                    alertService.sendAlert(
                        AlertSeverity.HIGH,
                        "LOW_CHANNEL_AVAILABILITY",
                        String.format("Only %d/%d (%.1f%%) channels are active",
                            activeChannels, totalChannels, availabilityRatio * 100),
                        Map.of(
                            "active_channels", activeChannels,
                            "total_channels", totalChannels,
                            "availability_ratio", availabilityRatio
                        )
                    );
                    alertGeneratedCounter.increment();
                }
            }
        } catch (Exception e) {
            log.debug("Failed to check channel availability", e);
        }
    }

    private void checkAuditAnomalies() {
        try {
            Instant oneHourAgo = Instant.now().minus(Duration.ofHours(1));
            
            // Check for high number of security events
            long securityEvents = auditRepository.countByEventTypeSince(
                LightningAuditEventType.SECURITY_EVENT, oneHourAgo);
                
            if (securityEvents > 10) { // More than 10 security events per hour
                alertService.sendAlert(
                    AlertSeverity.CRITICAL,
                    "HIGH_SECURITY_EVENT_COUNT",
                    String.format("%d security events detected in the last hour", securityEvents),
                    Map.of(
                        "security_event_count", securityEvents,
                        "time_window", "1 hour"
                    )
                );
                alertGeneratedCounter.increment();
            }
            
            // Check for payment anomalies
            long failedPayments = auditRepository.countByEventTypeSince(
                LightningAuditEventType.PAYMENT_FAILED, oneHourAgo);
                
            if (failedPayments > 50) { // More than 50 failed payments per hour
                alertService.sendAlert(
                    AlertSeverity.HIGH,
                    "HIGH_PAYMENT_FAILURE_COUNT",
                    String.format("%d payment failures detected in the last hour", failedPayments),
                    Map.of(
                        "failed_payment_count", failedPayments,
                        "time_window", "1 hour"
                    )
                );
                alertGeneratedCounter.increment();
            }
        } catch (Exception e) {
            log.debug("Failed to check audit anomalies", e);
        }
    }

    // ============ HEALTH MONITORING ============

    @Override
    public Health health() {
        Health.Builder healthBuilder;
        
        switch (currentHealthStatus) {
            case HEALTHY:
                healthBuilder = Health.up();
                break;
            case DEGRADED:
                healthBuilder = Health.status("DEGRADED");
                break;
            case CRITICAL:
                healthBuilder = Health.down();
                break;
            default:
                healthBuilder = Health.unknown();
                break;
        }
        
        try {
            // Add detailed health information
            healthBuilder
                .withDetail("monitoring_enabled", monitoringEnabled)
                .withDetail("last_health_check", lastHealthCheck.toString())
                .withDetail("node_status", systemState.get("node_status").get())
                .withDetail("wallet_status", systemState.get("wallet_status").get())
                .withDetail("sync_status", systemState.get("sync_status").get())
                .withDetail("node_balance", getNodeBalance())
                .withDetail("channel_count", getChannelCount())
                .withDetail("active_channel_count", getActiveChannelCount())
                .withDetail("peer_count", getPeerCount())
                .withDetail("payment_success_rate", realtimeMetrics.get("payment_success_rate").get() + "%")
                .withDetail("last_error", systemState.get("last_error").get());
                
        } catch (Exception e) {
            healthBuilder
                .withDetail("error", "Failed to collect health details: " + e.getMessage())
                .down();
        }
        
        return healthBuilder.build();
    }

    private HealthStatus determineHealthStatus() {
        try {
            // Check critical components
            if (!lightningService.isHealthy()) {
                return HealthStatus.CRITICAL;
            }
            
            if (!lightningService.isWalletUnlocked()) {
                return HealthStatus.CRITICAL;
            }
            
            // Check degraded conditions
            long paymentFailures = realtimeMetrics.get("payment_failure_count").get();
            if (paymentFailures > 10) {
                return HealthStatus.DEGRADED;
            }
            
            if (!lightningService.isFullySynced()) {
                return HealthStatus.DEGRADED;
            }
            
            // Check channel availability
            long activeChannels = getActiveChannelCount();
            long totalChannels = getChannelCount();
            if (totalChannels > 0 && (double) activeChannels / totalChannels < 0.8) {
                return HealthStatus.DEGRADED;
            }
            
            return HealthStatus.HEALTHY;
            
        } catch (Exception e) {
            log.error("Failed to determine health status", e);
            return HealthStatus.CRITICAL;
        }
    }

    // ============ METRIC PROVIDER METHODS ============

    public double getNodeBalance() {
        try {
            LightningNetworkService.WalletInfo walletInfo = lightningService.getWalletInfo();
            return walletInfo != null ? walletInfo.getTotalBalance() : 0.0;
        } catch (Exception e) {
            return 0.0;
        }
    }

    public double getChannelCount() {
        try {
            return channelRepository.count();
        } catch (Exception e) {
            return 0.0;
        }
    }

    public double getActiveChannelCount() {
        try {
            return channelRepository.countByActiveTrue();
        } catch (Exception e) {
            return 0.0;
        }
    }

    public double getTotalCapacity() {
        try {
            return channelRepository.findAll().stream()
                .mapToLong(ChannelEntity::getCapacity)
                .sum();
        } catch (Exception e) {
            return 0.0;
        }
    }

    public double getLocalBalance() {
        try {
            return channelRepository.findAll().stream()
                .mapToLong(ChannelEntity::getLocalBalance)
                .sum();
        } catch (Exception e) {
            return 0.0;
        }
    }

    public double getRemoteBalance() {
        try {
            return channelRepository.findAll().stream()
                .mapToLong(ChannelEntity::getRemoteBalance)
                .sum();
        } catch (Exception e) {
            return 0.0;
        }
    }

    public double getPendingChannelCount() {
        try {
            return channelRepository.countByStatus(ChannelStatus.PENDING_OPEN) +
                   channelRepository.countByStatus(ChannelStatus.PENDING_CLOSE);
        } catch (Exception e) {
            return 0.0;
        }
    }

    public double getPeerCount() {
        try {
            return lightningService.getPeerCount();
        } catch (Exception e) {
            return 0.0;
        }
    }

    public double getBlockHeight() {
        try {
            return lightningService.getCurrentBlockHeight();
        } catch (Exception e) {
            return 0.0;
        }
    }

    public double getSyncProgress() {
        try {
            return lightningService.getSyncProgress();
        } catch (Exception e) {
            return 0.0;
        }
    }

    // ============ PUBLIC MONITORING METHODS ============

    public void recordInvoiceCreated(long amountSat) {
        if (monitoringEnabled) {
            invoiceCreatedCounter.increment();
            invoiceAmountSummary.record(amountSat);
            realtimeMetrics.get("invoice_creation_rate").incrementAndGet();
        }
    }

    public void recordInvoiceSettled(long amountSat) {
        if (monitoringEnabled) {
            invoiceSettledCounter.increment();
            invoiceAmountSummary.record(amountSat);
        }
    }

    public void recordInvoiceExpired() {
        if (monitoringEnabled) {
            invoiceExpiredCounter.increment();
        }
    }

    public void recordPaymentSent(long amountSat, long feeSat) {
        if (monitoringEnabled) {
            paymentSentCounter.increment();
            paymentAmountSummary.record(amountSat);
            paymentFeeSummary.record(feeSat);
        }
    }

    public void recordPaymentFailed() {
        if (monitoringEnabled) {
            paymentFailedCounter.increment();
            realtimeMetrics.get("payment_failure_count").incrementAndGet();
        }
    }

    public void recordKeysendSent(long amountSat) {
        if (monitoringEnabled) {
            keysendSentCounter.increment();
            paymentAmountSummary.record(amountSat);
        }
    }

    public void recordChannelOpened(long capacity) {
        if (monitoringEnabled) {
            channelOpenedCounter.increment();
            channelCapacitySummary.record(capacity);
        }
    }

    public void recordChannelClosed() {
        if (monitoringEnabled) {
            channelClosedCounter.increment();
        }
    }

    public Timer.Sample startTimer(String operation) {
        if (monitoringEnabled) {
            return Timer.start(meterRegistry);
        }
        return null;
    }

    public void stopTimer(Timer.Sample sample, String operation) {
        if (monitoringEnabled && sample != null) {
            switch (operation) {
                case "invoice_creation":
                    sample.stop(invoiceCreationTimer);
                    break;
                case "payment_sending":
                    sample.stop(paymentSendingTimer);
                    break;
                case "channel_operation":
                    sample.stop(channelOperationTimer);
                    break;
                default:
                    sample.stop(apiResponseTimer);
                    break;
            }
        }
    }

    // ============ MONITORING DATA ACCESS ============

    public Map<String, Object> getCurrentMetrics() {
        Map<String, Object> metrics = new HashMap<>();
        
        metrics.put("node_balance", getNodeBalance());
        metrics.put("channel_count", getChannelCount());
        metrics.put("active_channel_count", getActiveChannelCount());
        metrics.put("total_capacity", getTotalCapacity());
        metrics.put("local_balance", getLocalBalance());
        metrics.put("remote_balance", getRemoteBalance());
        metrics.put("peer_count", getPeerCount());
        metrics.put("block_height", getBlockHeight());
        metrics.put("sync_progress", getSyncProgress());
        
        realtimeMetrics.forEach((key, value) -> 
            metrics.put(key, value.get()));
            
        systemState.forEach((key, value) -> 
            metrics.put(key, value.get()));
            
        metrics.put("health_status", currentHealthStatus.toString());
        metrics.put("last_health_check", lastHealthCheck.toString());
        
        return metrics;
    }

    public List<PerformanceSnapshot> getPerformanceHistory() {
        return new ArrayList<>(performanceHistory);
    }

    // ============ INNER CLASSES ============

    public enum HealthStatus {
        HEALTHY, DEGRADED, CRITICAL, UNKNOWN
    }

    @lombok.Builder
    @lombok.Getter
    public static class PerformanceSnapshot {
        private final Instant timestamp;
        private final double nodeBalance;
        private final double channelCount;
        private final double activeChannelCount;
        private final double totalCapacity;
        private final double localBalance;
        private final double remoteBalance;
        private final double peerCount;
    }
}