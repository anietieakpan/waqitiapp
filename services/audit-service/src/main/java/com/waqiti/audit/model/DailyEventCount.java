package com.waqiti.audit.model;

import lombok.Builder;
import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDate;

/**
 * Daily event count for statistics
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DailyEventCount {
    
    @JsonProperty("date")
    private LocalDate date;
    
    @JsonProperty("count")
    private Long count;
    
    @JsonProperty("unique_users")
    private Long uniqueUsers;
    
    @JsonProperty("unique_services")
    private Long uniqueServices;
}