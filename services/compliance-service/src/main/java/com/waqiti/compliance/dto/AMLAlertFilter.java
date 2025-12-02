package com.waqiti.compliance.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AMLAlertFilter {
    private String status;
    private String severity;
    private String alertType;
    private LocalDate startDate;
    private LocalDate endDate;
}