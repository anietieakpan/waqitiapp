package com.waqiti.analytics.engine;

import com.waqiti.analytics.dto.Alert;
import com.waqiti.analytics.dto.AlertRule;
import com.waqiti.analytics.dto.MetricEvent;
import com.waqiti.analytics.service.AlertingService;
import com.waqiti.analytics.repository.AlertRuleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * Real-time alerting engine that processes metrics and triggers alerts based on rules
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RealTimeAlertingEngine {
    
    private final AlertingService alertingService;
    private final AlertRuleRepository alertRuleRepository;
    
    // Rule engine components
    private final ExpressionParser expressionParser = new SpelExpressionParser();
    private final Map<String, CompiledAlertRule> activeRules = new ConcurrentHashMap<>();
    private final Map<String, AlertThrottle> alertThrottles = new ConcurrentHashMap<>();
    
    // Metric buffers for time-window based rules
    private final Map<String, MetricBuffer> metricBuffers = new ConcurrentHashMap<>();
    
    // Performance metrics
    private final AtomicLong rulesEvaluated = new AtomicLong(0);
    private final AtomicLong alertsTriggered = new AtomicLong(0);
    
    @PostConstruct
    public void initialize() {
        log.info("Initializing Real-Time Alerting Engine");
        loadAlertRules();
        initializeDefaultRules();
    }
    
    /**
     * Load alert rules from database
     */
    private void loadAlertRules() {
        try {
            List<AlertRule> rules = alertRuleRepository.findAllActive();
            log.info("Loading {} active alert rules", rules.size());
            
            for (AlertRule rule : rules) {
                try {
                    CompiledAlertRule compiled = compileRule(rule);
                    activeRules.put(rule.getRuleId(), compiled);
                    log.debug("Loaded rule: {}", rule.getName());
                } catch (Exception e) {
                    log.error("Failed to compile rule: {}", rule.getName(), e);
                }
            }
        } catch (Exception e) {
            log.error("Failed to load alert rules", e);
        }
    }
    
    /**
     * Initialize default system alert rules
     */
    private void initializeDefaultRules() {
        // Transaction Volume Spike Rule
        addRule(AlertRule.builder()
            .ruleId("RULE_TXN_VOLUME_SPIKE")
            .name("Transaction Volume Spike")
            .description("Alert when transaction volume increases by more than 50% in 5 minutes")
            .category("TRANSACTION")
            .condition("currentVolume > averageVolume * 1.5")
            .severity(Alert.Severity.WARNING)
            .timeWindow(300) // 5 minutes
            .enabled(true)
            .build());
        
        // High Value Transaction Rule
        addRule(AlertRule.builder()
            .ruleId("RULE_HIGH_VALUE_TXN")
            .name("High Value Transaction")
            .description("Alert on transactions over $10,000")
            .category("TRANSACTION")
            .condition("amount > 10000")
            .severity(Alert.Severity.HIGH)
            .enabled(true)
            .build());
        
        // Failed Transaction Rate Rule
        addRule(AlertRule.builder()
            .ruleId("RULE_HIGH_FAILURE_RATE")
            .name("High Transaction Failure Rate")
            .description("Alert when failure rate exceeds 5% in 10 minutes")
            .category("TRANSACTION")
            .condition("failureRate > 0.05")
            .severity(Alert.Severity.CRITICAL)
            .timeWindow(600) // 10 minutes
            .enabled(true)
            .build());
        
        // API Response Time Rule
        addRule(AlertRule.builder()
            .ruleId("RULE_SLOW_API_RESPONSE")
            .name("Slow API Response Time")
            .description("Alert when average API response time exceeds 1000ms")
            .category("PERFORMANCE")
            .condition("averageResponseTime > 1000")
            .severity(Alert.Severity.WARNING)
            .timeWindow(300) // 5 minutes
            .enabled(true)
            .build());
        
        // Account Balance Threshold Rule
        addRule(AlertRule.builder()
            .ruleId("RULE_LOW_BALANCE")
            .name("Low Account Balance")
            .description("Alert when account balance falls below $100")
            .category("ACCOUNT")
            .condition("balance < 100")
            .severity(Alert.Severity.INFO)
            .enabled(true)
            .build());
        
        // Suspicious Activity Pattern Rule
        addRule(AlertRule.builder()
            .ruleId("RULE_SUSPICIOUS_PATTERN")
            .name("Suspicious Activity Pattern")
            .description("Alert on multiple failed login attempts")
            .category("SECURITY")
            .condition("failedAttempts >= 5")
            .severity(Alert.Severity.HIGH)
            .timeWindow(300) // 5 minutes
            .enabled(true)
            .build());
        
        // System Resource Usage Rule
        addRule(AlertRule.builder()
            .ruleId("RULE_HIGH_CPU_USAGE")
            .name("High CPU Usage")
            .description("Alert when CPU usage exceeds 80%")
            .category("SYSTEM")
            .condition("cpuUsage > 80")
            .severity(Alert.Severity.WARNING)
            .enabled(true)
            .build());
        
        // Database Connection Pool Rule
        addRule(AlertRule.builder()
            .ruleId("RULE_DB_POOL_EXHAUSTED")
            .name("Database Pool Exhaustion")
            .description("Alert when database connection pool usage exceeds 90%")
            .category("SYSTEM")
            .condition("poolUsage > 0.9")
            .severity(Alert.Severity.CRITICAL)
            .enabled(true)
            .build());
    }
    
    /**
     * Process incoming metric events
     */
    @KafkaListener(topics = "metric-events", groupId = "alerting-engine")
    public void processMetricEvent(MetricEvent event) {
        try {
            log.debug("Processing metric event: type={}, value={}", 
                event.getMetricType(), event.getValue());
            
            // Buffer metric for time-window calculations
            bufferMetric(event);
            
            // Evaluate all applicable rules
            evaluateRules(event);
            
        } catch (Exception e) {
            log.error("Failed to process metric event", e);
        }
    }
    
    /**
     * Evaluate all rules against the metric event
     */
    private void evaluateRules(MetricEvent event) {
        activeRules.values().stream()
            .filter(rule -> isRuleApplicable(rule, event))
            .forEach(rule -> evaluateRule(rule, event));
    }
    
    /**
     * Check if a rule is applicable to the given metric event
     */
    private boolean isRuleApplicable(CompiledAlertRule rule, MetricEvent event) {
        // Check if rule category matches metric type
        String metricCategory = getMetricCategory(event.getMetricType());
        return rule.getRule().getCategory().equalsIgnoreCase(metricCategory) ||
               rule.getRule().getCategory().equals("*");
    }
    
    /**
     * Evaluate a single rule
     */
    private void evaluateRule(CompiledAlertRule compiledRule, MetricEvent event) {
        try {
            rulesEvaluated.incrementAndGet();
            
            AlertRule rule = compiledRule.getRule();
            StandardEvaluationContext context = createEvaluationContext(rule, event);
            
            Boolean result = compiledRule.getExpression().getValue(context, Boolean.class);
            
            if (Boolean.TRUE.equals(result)) {
                triggerAlert(rule, event, context);
            }
            
        } catch (Exception e) {
            log.error("Failed to evaluate rule: {}", compiledRule.getRule().getName(), e);
        }
    }
    
    /**
     * Create evaluation context for rule expression
     */
    private StandardEvaluationContext createEvaluationContext(AlertRule rule, MetricEvent event) {
        StandardEvaluationContext context = new StandardEvaluationContext();
        
        // Add metric value
        context.setVariable("value", event.getValue());
        context.setVariable("metricType", event.getMetricType());
        context.setVariable("timestamp", event.getTimestamp());
        
        // Add metric-specific variables
        Map<String, Object> metadata = event.getMetadata();
        if (metadata != null) {
            metadata.forEach(context::setVariable);
        }
        
        // Add time-window aggregations if applicable
        if (rule.getTimeWindow() != null && rule.getTimeWindow() > 0) {
            addTimeWindowVariables(context, rule, event);
        }
        
        // Add helper functions
        context.registerFunction("abs", Math.class.getDeclaredMethod("abs", double.class));
        context.registerFunction("max", Math.class.getDeclaredMethod("max", double.class, double.class));
        context.registerFunction("min", Math.class.getDeclaredMethod("min", double.class, double.class));
        
        return context;
    }
    
    /**
     * Add time-window based variables to context
     */
    private void addTimeWindowVariables(StandardEvaluationContext context, 
                                      AlertRule rule, MetricEvent event) {
        String bufferKey = getBufferKey(rule, event);
        MetricBuffer buffer = metricBuffers.get(bufferKey);
        
        if (buffer != null) {
            List<MetricEvent> windowEvents = buffer.getEventsInWindow(rule.getTimeWindow());
            
            // Calculate aggregations
            if (!windowEvents.isEmpty()) {
                double sum = windowEvents.stream()
                    .mapToDouble(e -> e.getValue().doubleValue())
                    .sum();
                double average = sum / windowEvents.size();
                double max = windowEvents.stream()
                    .mapToDouble(e -> e.getValue().doubleValue())
                    .max().orElse(0);
                double min = windowEvents.stream()
                    .mapToDouble(e -> e.getValue().doubleValue())
                    .min().orElse(0);
                
                context.setVariable("sum", sum);
                context.setVariable("average", average);
                context.setVariable("max", max);
                context.setVariable("min", min);
                context.setVariable("count", windowEvents.size());
                
                // Calculate rate-based metrics
                if (event.getMetricType().contains("FAILURE") || 
                    event.getMetricType().contains("ERROR")) {
                    long totalCount = windowEvents.size();
                    long failureCount = windowEvents.stream()
                        .filter(e -> e.getValue().doubleValue() > 0)
                        .count();
                    double failureRate = totalCount > 0 ? 
                        (double) failureCount / totalCount : 0;
                    context.setVariable("failureRate", failureRate);
                }
            }
        }
    }
    
    /**
     * Trigger an alert
     */
    private void triggerAlert(AlertRule rule, MetricEvent event, 
                            StandardEvaluationContext context) {
        try {
            // Check throttling
            if (isThrottled(rule)) {
                log.debug("Alert throttled: {}", rule.getName());
                return;
            }
            
            alertsTriggered.incrementAndGet();
            
            // Build alert
            Alert alert = Alert.builder()
                .alertId(UUID.randomUUID())
                .type(mapToAlertType(rule.getCategory()))
                .severity(rule.getSeverity())
                .message(buildAlertMessage(rule, event, context))
                .source("ALERTING_ENGINE")
                .ruleId(rule.getRuleId())
                .ruleName(rule.getName())
                .timestamp(LocalDateTime.now())
                .metrics(extractMetrics(event, context))
                .metadata(buildMetadata(rule, event))
                .build();
            
            // Add user/transaction context if available
            if (event.getMetadata() != null) {
                if (event.getMetadata().containsKey("userId")) {
                    alert.setUserId(UUID.fromString(event.getMetadata().get("userId").toString()));
                }
                if (event.getMetadata().containsKey("transactionId")) {
                    alert.setTransactionId(UUID.fromString(event.getMetadata().get("transactionId").toString()));
                }
            }
            
            // Send alert
            alertingService.sendAlert(alert);
            
            // Update throttle
            updateThrottle(rule);
            
            log.info("Alert triggered: rule={}, severity={}, message={}", 
                rule.getName(), alert.getSeverity(), alert.getMessage());
            
        } catch (Exception e) {
            log.error("Failed to trigger alert for rule: {}", rule.getName(), e);
        }
    }
    
    /**
     * Build alert message with context
     */
    private String buildAlertMessage(AlertRule rule, MetricEvent event, 
                                   StandardEvaluationContext context) {
        String baseMessage = rule.getDescription();
        
        // Add context information
        StringBuilder message = new StringBuilder(baseMessage);
        message.append(" | Metric: ").append(event.getMetricType());
        message.append(" | Value: ").append(event.getValue());
        
        // Add time-window context if applicable
        if (context.lookupVariable("average") != null) {
            message.append(" | Average: ").append(context.lookupVariable("average"));
        }
        if (context.lookupVariable("count") != null) {
            message.append(" | Count: ").append(context.lookupVariable("count"));
        }
        
        return message.toString();
    }
    
    /**
     * Extract metrics for alert
     */
    private Map<String, Object> extractMetrics(MetricEvent event, 
                                              StandardEvaluationContext context) {
        Map<String, Object> metrics = new HashMap<>();
        metrics.put("value", event.getValue());
        metrics.put("metricType", event.getMetricType());
        
        // Add evaluated variables
        Arrays.asList("average", "sum", "max", "min", "count", "failureRate")
            .forEach(var -> {
                Object value = context.lookupVariable(var);
                if (value != null) {
                    metrics.put(var, value);
                }
            });
        
        return metrics;
    }
    
    /**
     * Build metadata for alert
     */
    private Map<String, Object> buildMetadata(AlertRule rule, MetricEvent event) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("ruleCondition", rule.getCondition());
        metadata.put("eventTimestamp", event.getTimestamp());
        
        if (event.getMetadata() != null) {
            metadata.putAll(event.getMetadata());
        }
        
        return metadata;
    }
    
    /**
     * Buffer metric for time-window calculations
     */
    private void bufferMetric(MetricEvent event) {
        // Only buffer if we have time-window rules
        boolean hasTimeWindowRules = activeRules.values().stream()
            .anyMatch(r -> r.getRule().getTimeWindow() != null && r.getRule().getTimeWindow() > 0);
        
        if (!hasTimeWindowRules) {
            return;
        }
        
        String bufferKey = event.getMetricType();
        metricBuffers.computeIfAbsent(bufferKey, k -> new MetricBuffer())
            .addEvent(event);
    }
    
    /**
     * Get buffer key for rule and event
     */
    private String getBufferKey(AlertRule rule, MetricEvent event) {
        return event.getMetricType();
    }
    
    /**
     * Check if alert is throttled
     */
    private boolean isThrottled(AlertRule rule) {
        AlertThrottle throttle = alertThrottles.get(rule.getRuleId());
        if (throttle == null) {
            return false;
        }
        
        return throttle.isThrottled();
    }
    
    /**
     * Update throttle for rule
     */
    private void updateThrottle(AlertRule rule) {
        alertThrottles.computeIfAbsent(rule.getRuleId(), 
            k -> new AlertThrottle(rule.getThrottleMinutes()))
            .recordAlert();
    }
    
    /**
     * Add or update a rule
     */
    public void addRule(AlertRule rule) {
        try {
            CompiledAlertRule compiled = compileRule(rule);
            activeRules.put(rule.getRuleId(), compiled);
            log.info("Added alert rule: {}", rule.getName());
        } catch (Exception e) {
            log.error("Failed to add rule: {}", rule.getName(), e);
        }
    }
    
    /**
     * Remove a rule
     */
    public void removeRule(String ruleId) {
        activeRules.remove(ruleId);
        alertThrottles.remove(ruleId);
        log.info("Removed alert rule: {}", ruleId);
    }
    
    /**
     * Compile alert rule
     */
    private CompiledAlertRule compileRule(AlertRule rule) {
        Expression expression = expressionParser.parseExpression(rule.getCondition());
        return new CompiledAlertRule(rule, expression);
    }
    
    /**
     * Get metric category from type
     */
    private String getMetricCategory(String metricType) {
        if (metricType.startsWith("TXN_")) return "TRANSACTION";
        if (metricType.startsWith("API_")) return "PERFORMANCE";
        if (metricType.startsWith("SYS_")) return "SYSTEM";
        if (metricType.startsWith("SEC_")) return "SECURITY";
        if (metricType.startsWith("ACC_")) return "ACCOUNT";
        return "OTHER";
    }
    
    /**
     * Map category to alert type
     */
    private Alert.AlertType mapToAlertType(String category) {
        switch (category.toUpperCase()) {
            case "TRANSACTION":
                return Alert.AlertType.TRANSACTION_ANOMALY;
            case "PERFORMANCE":
                return Alert.AlertType.PERFORMANCE_DEGRADATION;
            case "SYSTEM":
                return Alert.AlertType.SYSTEM_HEALTH;
            case "SECURITY":
                return Alert.AlertType.SECURITY_THREAT;
            case "ACCOUNT":
                return Alert.AlertType.ACCOUNT_ACTIVITY;
            default:
                return Alert.AlertType.CUSTOM;
        }
    }
    
    /**
     * Clean up old metric buffers periodically
     */
    @Scheduled(fixedDelay = 300000) // Every 5 minutes
    public void cleanupMetricBuffers() {
        log.debug("Cleaning up metric buffers");
        
        metricBuffers.forEach((key, buffer) -> {
            buffer.cleanup();
            if (buffer.isEmpty()) {
                metricBuffers.remove(key);
            }
        });
        
        log.debug("Metric buffers cleaned. Active buffers: {}", metricBuffers.size());
    }
    
    /**
     * Get alerting engine statistics
     */
    public Map<String, Object> getStatistics() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("activeRules", activeRules.size());
        stats.put("rulesEvaluated", rulesEvaluated.get());
        stats.put("alertsTriggered", alertsTriggered.get());
        stats.put("activeBuffers", metricBuffers.size());
        stats.put("throttledRules", alertThrottles.size());
        return stats;
    }
    
    /**
     * Compiled alert rule with parsed expression
     */
    private static class CompiledAlertRule {
        private final AlertRule rule;
        private final Expression expression;
        
        public CompiledAlertRule(AlertRule rule, Expression expression) {
            this.rule = rule;
            this.expression = expression;
        }
        
        public AlertRule getRule() {
            return rule;
        }
        
        public Expression getExpression() {
            return expression;
        }
    }
    
    /**
     * Metric buffer for time-window calculations
     */
    private static class MetricBuffer {
        private final List<MetricEvent> events = new ArrayList<>();
        private static final int MAX_BUFFER_SIZE = 10000;
        
        public synchronized void addEvent(MetricEvent event) {
            events.add(event);
            
            // Prevent unbounded growth
            if (events.size() > MAX_BUFFER_SIZE) {
                events.remove(0);
            }
        }
        
        public synchronized List<MetricEvent> getEventsInWindow(int windowSeconds) {
            LocalDateTime cutoff = LocalDateTime.now().minusSeconds(windowSeconds);
            return events.stream()
                .filter(e -> e.getTimestamp().isAfter(cutoff))
                .collect(Collectors.toList());
        }
        
        public synchronized void cleanup() {
            // Remove events older than 1 hour
            LocalDateTime cutoff = LocalDateTime.now().minusHours(1);
            events.removeIf(e -> e.getTimestamp().isBefore(cutoff));
        }
        
        public boolean isEmpty() {
            return events.isEmpty();
        }
    }
    
    /**
     * Alert throttle to prevent alert storms
     */
    private static class AlertThrottle {
        private final int throttleMinutes;
        private final AtomicInteger alertCount = new AtomicInteger(0);
        private LocalDateTime windowStart = LocalDateTime.now();
        
        public AlertThrottle(Integer throttleMinutes) {
            this.throttleMinutes = throttleMinutes != null ? throttleMinutes : 5;
        }
        
        public synchronized boolean isThrottled() {
            LocalDateTime now = LocalDateTime.now();
            
            // Reset window if expired
            if (now.isAfter(windowStart.plusMinutes(throttleMinutes))) {
                windowStart = now;
                alertCount.set(0);
            }
            
            // Allow first 3 alerts in window, then throttle
            return alertCount.get() >= 3;
        }
        
        public void recordAlert() {
            alertCount.incrementAndGet();
        }
    }
}