package com.waqiti.common.observability.dto;

import com.waqiti.common.enums.TrendDirection;
import com.waqiti.common.observability.dto.DataPoint;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class ComplianceTrend {
    private TrendDirection direction;
    private double changePercentage;
    private String trendDescription;
    private List<DataPoint> historicalData;
    private String forecast;
}