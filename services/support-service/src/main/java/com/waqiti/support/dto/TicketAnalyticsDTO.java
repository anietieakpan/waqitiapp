package com.waqiti.support.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TicketAnalyticsDTO {
    
    // Time period
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
    private LocalDateTime periodStart;
    
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
    private LocalDateTime periodEnd;
    
    // Volume metrics
    private VolumeMetrics volumeMetrics;
    
    // Performance metrics  
    private PerformanceMetrics performanceMetrics;
    
    // SLA metrics
    private SLAMetrics slaMetrics;
    
    // Satisfaction metrics
    private SatisfactionMetrics satisfactionMetrics;
    
    // Category and trend analysis
    private List<CategoryBreakdown> categoryBreakdowns;
    private List<TrendDataPoint> volumeTrends;
    private List<TrendDataPoint> resolutionTimeTrends;
    
    // Agent performance
    private List<AgentPerformance> agentPerformance;
    
    // Channel analysis
    private Map<String, ChannelMetrics> channelMetrics;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class VolumeMetrics {
        private Long totalTickets;
        private Long newTickets;
        private Long resolvedTickets;
        private Long closedTickets;
        private Long reopenedTickets;
        private Long escalatedTickets;
        private Double resolutionRate; // percentage
        private Double reopenRate; // percentage
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PerformanceMetrics {
        private Double averageFirstResponseTimeHours;
        private Double averageResolutionTimeHours;
        private Double medianFirstResponseTimeHours;
        private Double medianResolutionTimeHours;
        private Long totalResponseTime;
        private Long totalResolutionTime;
        private Integer backlogCount;
        private Integer overdueCount;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SLAMetrics {
        private Double firstResponseSLACompliance; // percentage
        private Double resolutionSLACompliance; // percentage
        private Long totalSLABreaches;
        private Long firstResponseBreaches;
        private Long resolutionBreaches;
        private Double averageBreachTimeHours;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SatisfactionMetrics {
        private Double averageRating;
        private Long totalRatings;
        private Map<Integer, Long> ratingDistribution; // rating -> count
        private Double positiveRatingPercentage; // 4-5 stars
        private Double negativeRatingPercentage; // 1-2 stars
        private Long totalFeedbackComments;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CategoryBreakdown {
        private String category;
        private String subcategory;
        private Long ticketCount;
        private Double percentage;
        private Double averageResolutionTimeHours;
        private Double satisfactionRating;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TrendDataPoint {
        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
        private LocalDateTime timestamp;
        private String period; // hour, day, week, month
        private Double value;
        private Long count;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AgentPerformance {
        private String agentId;
        private String agentName;
        private Long ticketsAssigned;
        private Long ticketsResolved;
        private Double resolutionRate;
        private Double averageResolutionTimeHours;
        private Double averageSatisfactionRating;
        private Long totalResponseTime;
        private Integer currentWorkload;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ChannelMetrics {
        private String channel;
        private Long ticketCount;
        private Double percentage;
        private Double averageResolutionTimeHours;
        private Double satisfactionRating;
        private Double firstResponseSLA;
        private Double resolutionSLA;
    }
}