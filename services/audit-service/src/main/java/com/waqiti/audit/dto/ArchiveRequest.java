package com.waqiti.audit.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * Archive Request DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ArchiveRequest {
    @NotNull
    private LocalDate beforeDate;

    private String archiveType;
    private String storageLocation;
    private Boolean compress;
}
