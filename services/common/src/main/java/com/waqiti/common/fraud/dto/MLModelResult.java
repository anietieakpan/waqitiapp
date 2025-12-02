package com.waqiti.common.fraud.dto;

import lombok.Builder;
import lombok.Data;
import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class MLModelResult {
    private double fraudProbability;
    private double confidence;
    private List<String> topFeatures;
    private String modelVersion;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime predictionTimestamp;
}