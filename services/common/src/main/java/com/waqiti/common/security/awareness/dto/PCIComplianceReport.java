package com.waqiti.common.security.awareness.dto;

import lombok.*;
import jakarta.validation.constraints.*;
import com.waqiti.common.security.awareness.validation.ValidQuarter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PCIComplianceReport {
    private LocalDateTime reportDate;
    private Long totalEmployees;
    private Long compliantEmployees;
    private Long overdueEmployees;
    private BigDecimal overallComplianceRate;
    private String pciRequirement;
    private String complianceStatus;
}