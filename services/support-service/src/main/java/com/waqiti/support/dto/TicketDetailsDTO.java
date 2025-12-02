package com.waqiti.support.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TicketDetailsDTO {
    
    // Basic ticket information
    private TicketDTO ticket;
    
    // Messages and communication
    private List<TicketMessageDTO> messages;
    private List<TicketActivityDTO> activities;
    private List<TicketAttachmentDTO> attachments;
    
    // AI insights
    private TicketAIAnalysisDTO aiAnalysis;
    private List<TicketSuggestions> agentSuggestions;
    private List<SimilarTicketSummary> similarTickets;
    
    // Performance metrics
    private TicketMetricsDTO metrics;
    
    // Current user permissions
    private TicketPermissionsDTO permissions;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TicketActivityDTO {
        private String id;
        private String activityType;
        private String performedBy;
        private String performedByName;
        private String description;
        private String oldValue;
        private String newValue;
        
        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
        private LocalDateTime createdAt;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TicketAttachmentDTO {
        private String id;
        private String filename;
        private String originalFilename;
        private String contentType;
        private Long fileSize;
        private String downloadUrl;
        private boolean isPublic;
        private String virusScanStatus;
        private String uploadedBy;
        
        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
        private LocalDateTime uploadedAt;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TicketAIAnalysisDTO {
        private String detectedIntent;
        private Double intentConfidence;
        private String sentiment;
        private Double sentimentScore;
        private Double urgencyScore;
        private String suggestedCategory;
        private String suggestedPriority;
        private List<String> suggestedTags;
        private Integer estimatedResolutionTimeMinutes;
        private String modelVersion;
        
        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
        private LocalDateTime processedAt;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TicketMetricsDTO {
        private Long totalResponseTimeMinutes;
        private Long averageResponseTimeMinutes;
        private Long timeToFirstResponseMinutes;
        private Long timeToResolutionMinutes;
        private Integer reopenCount;
        private Integer escalationCount;
        private Double customerSatisfactionRating;
        
        // SLA metrics
        private boolean firstResponseSlaBreached;
        private boolean resolutionSlaBreached;
        private Long slaBreachTimeMinutes;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TicketPermissionsDTO {
        private boolean canView;
        private boolean canEdit;
        private boolean canComment;
        private boolean canReassign;
        private boolean canEscalate;
        private boolean canResolve;
        private boolean canClose;
        private boolean canReopen;
        private boolean canDelete;
        private boolean canViewInternal;
    }
}