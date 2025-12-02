package com.waqiti.compliance.model.sanctions;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * Result of parsing a sanctions list
 *
 * Contains all parsed entities, aliases, and metadata
 *
 * @author Waqiti Engineering Team
 * @since 2025-11-19
 */
@Data
public class SanctionsListParseResult {

    private SanctionsListMetadata metadata;
    private List<SanctionedEntity> entities = new ArrayList<>();
    private List<SanctionedEntityAlias> aliases = new ArrayList<>();
    private List<SanctionsProgram> programs = new ArrayList<>();

    /**
     * Get total number of aliases across all entities
     */
    public int getTotalAliases() {
        return aliases.size();
    }

    /**
     * Check if parsing was successful
     */
    public boolean isSuccess() {
        return metadata != null &&
                "COMPLETED".equals(metadata.getProcessingStatus()) &&
                !entities.isEmpty();
    }

    /**
     * Get summary string
     */
    public String getSummary() {
        return String.format("%s %s List: %d entities, %d aliases, %d programs",
                metadata != null ? metadata.getListSource() : "Unknown",
                metadata != null ? metadata.getListType() : "Unknown",
                entities.size(),
                aliases.size(),
                programs.size());
    }
}
