package com.waqiti.common.gdpr.enums;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * GDPR Data Export Formats
 *
 * Supported formats for data portability per GDPR Article 20
 *
 * @author Waqiti Platform Team
 * @version 1.0
 * @since 2025-10-19
 */
public enum ExportFormat {
    /**
     * JSON format - Machine readable, structured
     * Recommended for Article 20 (Right to Data Portability)
     */
    JSON("application/json", ".json", true),

    /**
     * CSV format - Widely compatible, tabular data
     */
    CSV("text/csv", ".csv", true),

    /**
     * XML format - Structured, industry standard
     */
    XML("application/xml", ".xml", true),

    /**
     * PDF format - Human readable, archival
     */
    PDF("application/pdf", ".pdf", false),

    /**
     * ZIP archive - Multiple files, compressed
     */
    ZIP("application/zip", ".zip", true);

    private final String mimeType;
    private final String fileExtension;
    private final boolean machineReadable;

    ExportFormat(String mimeType, String fileExtension, boolean machineReadable) {
        this.mimeType = mimeType;
        this.fileExtension = fileExtension;
        this.machineReadable = machineReadable;
    }

    public String getMimeType() {
        return mimeType;
    }

    public String getFileExtension() {
        return fileExtension;
    }

    public boolean isMachineReadable() {
        return machineReadable;
    }
}
