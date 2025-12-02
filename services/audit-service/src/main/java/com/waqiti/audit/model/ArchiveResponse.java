package com.waqiti.audit.model;

import lombok.Builder;
import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Response for audit record archiving operations
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ArchiveResponse {
    
    @JsonProperty("archived_count")
    private Integer archivedCount;
    
    @JsonProperty("archive_date")
    private LocalDate archiveDate;
    
    @JsonProperty("archive_location")
    private String archiveLocation;
    
    @JsonProperty("status")
    private String status;
    
    @JsonProperty("completion_time")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime completionTime;
    
    @JsonProperty("archive_size_bytes")
    private Long archiveSizeBytes;
    
    @JsonProperty("compression_ratio")
    private Double compressionRatio;
    
    @JsonProperty("error_message")
    private String errorMessage;
}