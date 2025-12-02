package com.waqiti.analytics.api;

import com.waqiti.analytics.service.DataAggregationService;
import com.waqiti.analytics.service.MachineLearningAnalyticsService;
import com.waqiti.analytics.service.MetricsCollectionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/analytics/users")
@RequiredArgsConstructor
@Slf4j
public class UserAnalyticsController {

    private final DataAggregationService dataAggregationService;
    private final MachineLearningAnalyticsService mlAnalyticsService;
    private final MetricsCollectionService metricsService;

    @GetMapping("/metrics")
    @PreAuthorize("hasAnyRole('ADMIN', 'ANALYST')")
    public ResponseEntity<Map<String, Object>> getUserMetrics(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        log.info("Getting user metrics from {} to {}", startDate, endDate);
        
        Map<String, Object> metrics = metricsService.getUserMetrics(startDate, endDate);
        return ResponseEntity.ok(metrics);
    }

    @GetMapping("/{userId}/profile")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN', 'ANALYST')")
    public ResponseEntity<Map<String, Object>> getUserProfile(@PathVariable String userId) {
        log.info("Getting user profile analytics for: {}", userId);
        
        Map<String, Object> profile = dataAggregationService.getUserAnalyticsProfile(userId);
        return ResponseEntity.ok(profile);
    }

    @GetMapping("/{userId}/spending-pattern")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN', 'ANALYST')")
    public ResponseEntity<Map<String, Object>> getSpendingPattern(
            @PathVariable String userId,
            @RequestParam(defaultValue = "30") int days) {
        log.info("Getting spending pattern for user: {} over {} days", userId, days);
        
        Map<String, Object> pattern = mlAnalyticsService.analyzeSpendingPattern(userId, days);
        return ResponseEntity.ok(pattern);
    }

    @GetMapping("/engagement")
    @PreAuthorize("hasAnyRole('ADMIN', 'ANALYST')")
    public ResponseEntity<Map<String, Object>> getUserEngagement(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(defaultValue = "daily") String granularity) {
        log.info("Getting user engagement from {} to {} with granularity: {}", startDate, endDate, granularity);
        
        Map<String, Object> engagement = dataAggregationService.analyzeUserEngagement(startDate, endDate, granularity);
        return ResponseEntity.ok(engagement);
    }

    @GetMapping("/retention")
    @PreAuthorize("hasAnyRole('ADMIN', 'ANALYST')")
    public ResponseEntity<Map<String, Object>> getUserRetention(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(defaultValue = "monthly") String period) {
        log.info("Getting user retention from {} to {} by {}", startDate, endDate, period);
        
        Map<String, Object> retention = mlAnalyticsService.analyzeUserRetention(startDate, endDate, period);
        return ResponseEntity.ok(retention);
    }

    @GetMapping("/acquisition")
    @PreAuthorize("hasAnyRole('ADMIN', 'ANALYST')")
    public ResponseEntity<Map<String, Object>> getUserAcquisition(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) String channel) {
        log.info("Getting user acquisition from {} to {} for channel: {}", startDate, endDate, channel);
        
        Map<String, Object> acquisition = dataAggregationService.analyzeUserAcquisition(startDate, endDate, channel);
        return ResponseEntity.ok(acquisition);
    }

    @GetMapping("/churn-prediction")
    @PreAuthorize("hasAnyRole('ADMIN', 'ANALYST')")
    public ResponseEntity<Map<String, Object>> getChurnPrediction(
            @RequestParam(defaultValue = "30") int predictionDays,
            @RequestParam(defaultValue = "0.7") double threshold) {
        log.info("Getting churn prediction for {} days with threshold: {}", predictionDays, threshold);
        
        Map<String, Object> churnPrediction = mlAnalyticsService.predictUserChurn(predictionDays, threshold);
        return ResponseEntity.ok(churnPrediction);
    }

    @GetMapping("/{userId}/lifetime-value")
    @PreAuthorize("hasAnyRole('ADMIN', 'ANALYST')")
    public ResponseEntity<Map<String, Object>> getUserLifetimeValue(@PathVariable String userId) {
        log.info("Calculating lifetime value for user: {}", userId);
        
        Map<String, Object> ltv = mlAnalyticsService.calculateUserLifetimeValue(userId);
        return ResponseEntity.ok(ltv);
    }

    @GetMapping("/top-users")
    @PreAuthorize("hasAnyRole('ADMIN', 'ANALYST')")
    public ResponseEntity<List<Map<String, Object>>> getTopUsers(
            @RequestParam(defaultValue = "transaction_volume") String metric,
            @RequestParam(defaultValue = "10") int limit,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        log.info("Getting top {} users by {} from {} to {}", limit, metric, startDate, endDate);
        
        List<Map<String, Object>> topUsers = dataAggregationService.getTopUsers(metric, limit, startDate, endDate);
        return ResponseEntity.ok(topUsers);
    }

    @GetMapping("/demographics")
    @PreAuthorize("hasAnyRole('ADMIN', 'ANALYST')")
    public ResponseEntity<Map<String, Object>> getUserDemographics(
            @RequestParam(required = false) String segmentBy,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        log.info("Getting user demographics segmented by: {} from {} to {}", segmentBy, startDate, endDate);
        
        Map<String, Object> demographics = dataAggregationService.analyzeUserDemographics(segmentBy, startDate, endDate);
        return ResponseEntity.ok(demographics);
    }

    @GetMapping("/behavior-analysis")
    @PreAuthorize("hasAnyRole('ADMIN', 'ANALYST')")
    public ResponseEntity<Map<String, Object>> getUserBehaviorAnalysis(
            @RequestParam(required = false) String behaviorType,
            @RequestParam(defaultValue = "30") int days) {
        log.info("Analyzing user behavior type: {} over {} days", behaviorType, days);
        
        Map<String, Object> behaviorAnalysis = mlAnalyticsService.analyzeUserBehavior(behaviorType, days);
        return ResponseEntity.ok(behaviorAnalysis);
    }

    @PostMapping("/custom-segment")
    @PreAuthorize("hasRole('ANALYST')")
    public ResponseEntity<Map<String, Object>> createCustomSegment(@RequestBody @Valid CustomSegmentRequest request) {
        log.info("Creating custom user segment: {}", request.getSegmentName());
        
        String segmentId = mlAnalyticsService.createCustomUserSegment(
            request.getSegmentName(),
            request.getCriteria(),
            request.getDescription()
        );
        
        return ResponseEntity.ok(Map.of(
            "segmentId", segmentId,
            "segmentName", request.getSegmentName(),
            "status", "created"
        ));
    }

    @GetMapping("/segments")
    @PreAuthorize("hasAnyRole('ADMIN', 'ANALYST')")
    public ResponseEntity<List<Map<String, Object>>> getUserSegments() {
        log.info("Getting all user segments");
        
        List<Map<String, Object>> segments = mlAnalyticsService.getAllUserSegments();
        return ResponseEntity.ok(segments);
    }

    @GetMapping("/segments/{segmentId}/users")
    @PreAuthorize("hasAnyRole('ADMIN', 'ANALYST')")
    public ResponseEntity<Map<String, Object>> getSegmentUsers(
            @PathVariable String segmentId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        log.info("Getting users in segment: {} (page: {}, size: {})", segmentId, page, size);
        
        Map<String, Object> segmentUsers = mlAnalyticsService.getSegmentUsers(segmentId, page, size);
        return ResponseEntity.ok(segmentUsers);
    }

    @GetMapping("/{userId}/recommendations")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    public ResponseEntity<Map<String, Object>> getUserRecommendations(@PathVariable String userId) {
        log.info("Getting recommendations for user: {}", userId);
        
        Map<String, Object> recommendations = mlAnalyticsService.generateUserRecommendations(userId);
        return ResponseEntity.ok(recommendations);
    }

    @GetMapping("/activity-heatmap")
    @PreAuthorize("hasAnyRole('ADMIN', 'ANALYST')")
    public ResponseEntity<Map<String, Object>> getActivityHeatmap(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(defaultValue = "hourly") String granularity) {
        log.info("Getting activity heatmap from {} to {} with granularity: {}", startDate, endDate, granularity);
        
        Map<String, Object> heatmap = dataAggregationService.generateActivityHeatmap(startDate, endDate, granularity);
        return ResponseEntity.ok(heatmap);
    }
}

