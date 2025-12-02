package com.waqiti.payment.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

/**
 * DTO for bulk user requests
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UserBulkRequest {
    
    @NotEmpty
    private List<String> userIds;
    
    private boolean includeInactive;
    private boolean includeUnverified;
    private List<String> fieldsToInclude;
    private List<String> fieldsToExclude;
}