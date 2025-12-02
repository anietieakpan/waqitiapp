package com.waqiti.audit.model;

import lombok.Builder;
import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

/**
 * Request for archiving audit records
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ArchiveRequest {
    
    @NotNull
    @JsonProperty("archive_date")
    private LocalDate archiveDate;
    
    @JsonProperty("delete_after_archive")
    private boolean deleteAfterArchive;
    
    @JsonProperty("archive_format")
    private ArchiveFormat archiveFormat;
    
    @JsonProperty("compression_enabled")
    private Boolean compressionEnabled;
    
    @JsonProperty("encryption_enabled")
    private Boolean encryptionEnabled;
    
    @JsonProperty("include_metadata")
    private Boolean includeMetadata;
    
    public enum ArchiveFormat {
        JSON,
        CSV,
        PARQUET,
        AVRO
    }
}