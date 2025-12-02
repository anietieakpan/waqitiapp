package com.waqiti.crypto.lightning.service;

import com.waqiti.common.exception.BusinessException;
import com.waqiti.common.exception.ErrorCode;
import com.waqiti.crypto.lightning.LightningNetworkService;
import com.waqiti.crypto.lightning.LightningNetworkService.ChannelInfo;
import com.waqiti.crypto.lightning.entity.ChannelEntity;
import com.waqiti.crypto.lightning.entity.ChannelStatus;
import com.waqiti.crypto.lightning.repository.ChannelRepository;
import com.waqiti.crypto.dto.lightning.OpenChannelRequest;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.context.annotation.Lazy;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Production-grade service for managing Lightning channels
 * Handles channel lifecycle, monitoring, and optimization
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class LightningChannelService {
    
    @org.springframework.context.annotation.Lazy
    private final LightningChannelService self;

    private final ChannelRepository channelRepository;
    private final LightningNetworkService lightningService;
    private final MeterRegistry meterRegistry;
    
    private final ConcurrentHashMap<String, ChannelMetrics> channelMetricsCache = new ConcurrentHashMap<>();
    private Counter channelOpenedCounter;
    private Counter channelClosedCounter;
    private Gauge totalChannelCapacity;
    private Gauge activeChannelCount;

    @jakarta.annotation.PostConstruct
    public void init() {
        channelOpenedCounter = Counter.builder("lightning.channel.opened")
            .description("Number of Lightning channels opened")
            .register(meterRegistry);
            
        channelClosedCounter = Counter.builder("lightning.channel.closed")
            .description("Number of Lightning channels closed")
            .register(meterRegistry);
            
        totalChannelCapacity = Gauge.builder("lightning.channel.capacity.total", this, 
            LightningChannelService::calculateTotalCapacity)
            .description("Total Lightning channel capacity in satoshis")
            .register(meterRegistry);
            
        activeChannelCount = Gauge.builder("lightning.channel.active.count", this,
            LightningChannelService::countActiveChannels)
            .description("Number of active Lightning channels")
            .register(meterRegistry);
    }

    /**
     * Save a new channel
     */
    public ChannelEntity saveChannel(String channelId, OpenChannelRequest request, String userId) {
        log.info("Saving new channel: {} for user: {}", channelId, userId);
        
        // Check for duplicate
        if (channelRepository.existsById(channelId)) {
            throw new BusinessException(ErrorCode.PAYMENT_DUPLICATE_REQUEST, "Channel already exists");
        }
        
        ChannelEntity channel = ChannelEntity.builder()
            .id(channelId)
            .userId(userId)
            .remotePubkey(request.getNodePubkey())
            .capacity(request.getLocalFundingAmount())
            .localBalance(request.getLocalFundingAmount() - (request.getPushSat() != null ? request.getPushSat() : 0))
            .remoteBalance(request.getPushSat() != null ? request.getPushSat() : 0)
            .status(ChannelStatus.PENDING_OPEN)
            .isPrivate(request.isPrivate())
            .openedAt(Instant.now())
            .build();
        
        channel = channelRepository.save(channel);
        
        // Update metrics
        channelOpenedCounter.increment();
        updateChannelMetrics(channel);
        
        log.info("Channel {} saved successfully", channelId);
        return channel;
    }

    /**
     * Get channel by ID
     */
    @Cacheable(value = "channels", key = "#channelId")
    public ChannelEntity getChannel(String channelId) {
        return channelRepository.findById(channelId)
            .orElseThrow(() -> new BusinessException(ErrorCode.RES_NOT_FOUND, "Channel not found"));
    }

    /**
     * Get detailed channel information
     */
    public ChannelInfo getChannelInfo(String channelId) {
        // Get from Lightning node
        List<ChannelInfo> channels = lightningService.listChannels(false);
        
        return channels.stream()
            .filter(c -> c.getChannelId().equals(channelId))
            .findFirst()
            .orElseThrow(() -> new BusinessException(ErrorCode.RES_NOT_FOUND, "Channel not found on node"));
    }

    /**
     * Update channel status
     */
    @CacheEvict(value = "channels", key = "#channelId")
    public ChannelEntity updateChannelStatus(String channelId, ChannelStatus newStatus) {
        ChannelEntity channel = self.getChannel(channelId);
        ChannelStatus oldStatus = channel.getStatus();
        
        log.info("Updating channel {} status from {} to {}", channelId, oldStatus, newStatus);
        
        channel.setStatus(newStatus);
        channel.setUpdatedAt(Instant.now());
        
        if (newStatus == ChannelStatus.ACTIVE) {
            channel.setActiveAt(Instant.now());
        } else if (newStatus == ChannelStatus.CLOSED || newStatus == ChannelStatus.FORCE_CLOSED) {
            channel.setClosedAt(Instant.now());
            channelClosedCounter.increment();
        }
        
        channel = channelRepository.save(channel);
        updateChannelMetrics(channel);
        
        return channel;
    }

    /**
     * Filter channels by user
     */
    public List<ChannelInfo> filterUserChannels(List<ChannelInfo> channels, String userId) {
        Set<String> userChannelIds = channelRepository.findByUserId(userId).stream()
            .map(ChannelEntity::getId)
            .collect(Collectors.toSet());
        
        return channels.stream()
            .filter(c -> userChannelIds.contains(c.getChannelId()))
            .collect(Collectors.toList());
    }

    /**
     * Connect to a Lightning peer
     */
    public boolean connectToPeer(String pubkey, String host) {
        log.info("Connecting to peer: {} at {}", pubkey, host);
        
        try {
            // Attempt connection through Lightning node
            Map<String, Object> connectResult = lightningService.connectPeer(pubkey, host);
            
            boolean connected = (boolean) connectResult.getOrDefault("connected", false);
            
            if (connected) {
                log.info("Successfully connected to peer: {}", pubkey);
            } else {
                log.warn("Failed to connect to peer: {}", pubkey);
            }
            
            return connected;
            
        } catch (Exception e) {
            log.error("Error connecting to peer: {}", pubkey, e);
            return false;
        }
    }

    /**
     * Get user's channels
     */
    public Page<ChannelEntity> getUserChannels(String userId, ChannelStatus status, Pageable pageable) {
        if (status != null) {
            return channelRepository.findByUserIdAndStatus(userId, status, pageable);
        } else {
            return channelRepository.findByUserId(userId, pageable);
        }
    }

    /**
     * Analyze channel health and provide recommendations
     */
    public ChannelHealthAnalysis analyzeChannelHealth(String channelId) {
        ChannelEntity channel = self.getChannel(channelId);
        ChannelInfo nodeInfo = getChannelInfo(channelId);
        
        ChannelHealthAnalysis analysis = new ChannelHealthAnalysis();
        analysis.setChannelId(channelId);
        analysis.setHealthScore(calculateHealthScore(channel, nodeInfo));
        
        // Check balance ratio
        double balanceRatio = nodeInfo.getBalanceRatio();
        if (balanceRatio < 0.2 || balanceRatio > 0.8) {
            analysis.addRecommendation("Channel imbalanced. Consider rebalancing.");
            analysis.setNeedsRebalancing(true);
            analysis.setSuggestedRebalanceAmount(calculateOptimalRebalanceAmount(nodeInfo));
        }
        
        // Check activity
        if (channel.getLastActivityAt() != null) {
            long daysSinceActivity = ChronoUnit.DAYS.between(channel.getLastActivityAt(), Instant.now());
            if (daysSinceActivity > 30) {
                analysis.addRecommendation("Channel inactive for " + daysSinceActivity + " days.");
                analysis.setIsInactive(true);
            }
        }
        
        // Check fee earnings
        if (channel.getTotalFeesEarned() < channel.getCapacity() * 0.001) { // Less than 0.1% return
            analysis.addRecommendation("Low fee earnings. Consider adjusting fee policy.");
        }
        
        // Check peer reliability
        ChannelMetrics metrics = channelMetricsCache.get(channelId);
        if (metrics != null && metrics.getSuccessRate() < 0.95) {
            analysis.addRecommendation("Low routing success rate: " + 
                String.format("%.1f%%", metrics.getSuccessRate() * 100));
        }
        
        return analysis;
    }

    /**
     * Rebalance multiple channels automatically
     */
    public List<RebalanceResult> autoRebalanceChannels(String userId) {
        log.info("Starting auto-rebalance for user: {}", userId);
        
        List<ChannelEntity> channels = channelRepository.findByUserId(userId);
        List<RebalanceResult> results = new ArrayList<>();
        
        for (ChannelEntity channel : channels) {
            if (channel.getStatus() != ChannelStatus.ACTIVE) {
                continue;
            }
            
            try {
                ChannelInfo info = getChannelInfo(channel.getId());
                
                if (info.needsRebalancing(0.3)) { // 30% threshold
                    long rebalanceAmount = calculateOptimalRebalanceAmount(info);
                    
                    log.info("Rebalancing channel {} with amount: {} sats", 
                        channel.getId(), rebalanceAmount);
                    
                    var paymentResult = lightningService.rebalanceChannel(channel.getId(), rebalanceAmount);
                    
                    RebalanceResult result = new RebalanceResult();
                    result.setChannelId(channel.getId());
                    result.setAmount(rebalanceAmount);
                    result.setSuccess(paymentResult.isSuccess());
                    result.setFee(paymentResult.getFeeSat());
                    result.setError(paymentResult.getError());
                    
                    results.add(result);
                    
                    // Update channel metrics
                    if (paymentResult.isSuccess()) {
                        channel.setLastRebalanceAt(Instant.now());
                        channel.setRebalanceCount(channel.getRebalanceCount() + 1);
                        channelRepository.save(channel);
                    }
                }
            } catch (Exception e) {
                log.error("Error rebalancing channel: {}", channel.getId(), e);
                
                RebalanceResult result = new RebalanceResult();
                result.setChannelId(channel.getId());
                result.setSuccess(false);
                result.setError(e.getMessage());
                results.add(result);
            }
        }
        
        log.info("Auto-rebalance completed. Processed {} channels", results.size());
        return results;
    }

    /**
     * Update channel metrics from node
     */
    @Scheduled(fixedDelay = 60000) // Every minute
    public void updateChannelMetricsFromNode() {
        log.debug("Updating channel metrics from Lightning node");
        
        try {
            List<ChannelInfo> nodeChannels = lightningService.listChannels(false);
            
            for (ChannelInfo nodeChannel : nodeChannels) {
                try {
                    Optional<ChannelEntity> channelOpt = channelRepository.findById(nodeChannel.getChannelId());
                    
                    if (channelOpt.isPresent()) {
                        ChannelEntity channel = channelOpt.get();
                        
                        // Update balances
                        channel.setLocalBalance(nodeChannel.getLocalBalance());
                        channel.setRemoteBalance(nodeChannel.getRemoteBalance());
                        
                        // Update status
                        if (nodeChannel.isActive() && channel.getStatus() != ChannelStatus.ACTIVE) {
                            channel.setStatus(ChannelStatus.ACTIVE);
                            channel.setActiveAt(Instant.now());
                        } else if (!nodeChannel.isActive() && channel.getStatus() == ChannelStatus.ACTIVE) {
                            channel.setStatus(ChannelStatus.INACTIVE);
                        }
                        
                        // Update metrics cache
                        ChannelMetrics metrics = channelMetricsCache.computeIfAbsent(
                            channel.getId(), k -> new ChannelMetrics());
                        metrics.updateFromNodeInfo(nodeChannel);
                        
                        channel.setUpdatedAt(Instant.now());
                        channelRepository.save(channel);
                    }
                } catch (Exception e) {
                    log.error("Error updating channel: {}", nodeChannel.getChannelId(), e);
                }
            }
        } catch (Exception e) {
            log.error("Error fetching channels from node", e);
        }
    }

    /**
     * Optimize channel fee policies
     */
    public Map<String, FeePolicy> optimizeFeePolicies(String userId) {
        log.info("Optimizing fee policies for user: {}", userId);
        
        List<ChannelEntity> channels = channelRepository.findByUserId(userId);
        Map<String, FeePolicy> optimizedPolicies = new HashMap<>();
        
        for (ChannelEntity channel : channels) {
            try {
                ChannelMetrics metrics = channelMetricsCache.get(channel.getId());
                if (metrics == null) {
                    continue;
                }
                
                FeePolicy policy = calculateOptimalFeePolicy(channel, metrics);
                optimizedPolicies.put(channel.getId(), policy);
                
                // Apply the policy
                lightningService.updateChannelPolicy(
                    channel.getId(),
                    policy.getBaseFee(),
                    policy.getFeeRate(),
                    policy.getTimeLockDelta()
                );
                
                // Update channel record
                channel.setBaseFee(policy.getBaseFee());
                channel.setFeeRate(policy.getFeeRate());
                channel.setTimeLockDelta(policy.getTimeLockDelta());
                channel.setLastFeePolicyUpdate(Instant.now());
                channelRepository.save(channel);
                
            } catch (Exception e) {
                log.error("Error optimizing fee policy for channel: {}", channel.getId(), e);
            }
        }
        
        return optimizedPolicies;
    }

    // Helper methods

    private double calculateTotalCapacity() {
        return channelRepository.findAll().stream()
            .filter(c -> c.getStatus() == ChannelStatus.ACTIVE)
            .mapToLong(ChannelEntity::getCapacity)
            .sum();
    }

    private double countActiveChannels() {
        return channelRepository.countByStatus(ChannelStatus.ACTIVE);
    }

    private void updateChannelMetrics(ChannelEntity channel) {
        ChannelMetrics metrics = channelMetricsCache.computeIfAbsent(
            channel.getId(), k -> new ChannelMetrics());
        metrics.updateFromEntity(channel);
    }

    private double calculateHealthScore(ChannelEntity channel, ChannelInfo nodeInfo) {
        double score = 100.0;
        
        // Balance ratio (40 points)
        double balanceRatio = nodeInfo.getBalanceRatio();
        double balanceScore = 40.0 * (1.0 - Math.abs(0.5 - balanceRatio) * 2);
        score = Math.min(score, balanceScore);
        
        // Activity (30 points)
        if (channel.getLastActivityAt() != null) {
            long daysSinceActivity = ChronoUnit.DAYS.between(channel.getLastActivityAt(), Instant.now());
            double activityScore = Math.max(0, 30.0 - daysSinceActivity);
            score = Math.min(score, activityScore);
        }
        
        // Success rate (30 points)
        ChannelMetrics metrics = channelMetricsCache.get(channel.getId());
        if (metrics != null) {
            score = Math.min(score, 30.0 * metrics.getSuccessRate());
        }
        
        return Math.max(0, score);
    }

    private long calculateOptimalRebalanceAmount(ChannelInfo channel) {
        long targetBalance = channel.getCapacity() / 2;
        return Math.abs(channel.getLocalBalance() - targetBalance);
    }

    private FeePolicy calculateOptimalFeePolicy(ChannelEntity channel, ChannelMetrics metrics) {
        // Base fee calculation
        long baseFee = 1000; // 1 sat base
        
        // Fee rate calculation based on channel utilization
        double utilization = metrics.getUtilizationRate();
        long feeRate = 1; // Base rate per million
        
        if (utilization > 0.8) {
            // High demand - increase fees
            feeRate = 100;
        } else if (utilization > 0.5) {
            feeRate = 50;
        } else if (utilization < 0.2) {
            // Low utilization - decrease fees to attract routing
            baseFee = 0;
            feeRate = 1;
        }
        
        // Time lock delta
        int timeLockDelta = 144; // Default 1 day
        
        return new FeePolicy(baseFee, feeRate, timeLockDelta);
    }

    /**
     * Channel health analysis result
     */
    @lombok.Data
    public static class ChannelHealthAnalysis {
        private String channelId;
        private double healthScore;
        private List<String> recommendations = new ArrayList<>();
        private boolean needsRebalancing;
        private long suggestedRebalanceAmount;
        private boolean isInactive;
        
        public void addRecommendation(String recommendation) {
            recommendations.add(recommendation);
        }
    }

    /**
     * Rebalance result
     */
    @lombok.Data
    public static class RebalanceResult {
        private String channelId;
        private long amount;
        private boolean success;
        private long fee;
        private String error;
    }

    /**
     * Fee policy
     */
    @lombok.Data
    @lombok.AllArgsConstructor
    public static class FeePolicy {
        private long baseFee;
        private long feeRate;
        private int timeLockDelta;
    }

    /**
     * Channel metrics cache
     */
    private static class ChannelMetrics {
        private double successRate = 1.0;
        private double utilizationRate = 0.0;
        private long totalForwarded = 0;
        private long totalFees = 0;
        private Instant lastUpdated = Instant.now();
        
        public void updateFromNodeInfo(ChannelInfo info) {
            // Update metrics from node info
            lastUpdated = Instant.now();
        }
        
        public void updateFromEntity(ChannelEntity entity) {
            if (entity.getTotalForwarded() != null) {
                totalForwarded = entity.getTotalForwarded();
            }
            if (entity.getTotalFeesEarned() != null) {
                totalFees = entity.getTotalFeesEarned();
            }
            lastUpdated = Instant.now();
        }
        
        public double getSuccessRate() { return successRate; }
        public double getUtilizationRate() { return utilizationRate; }
    }
}