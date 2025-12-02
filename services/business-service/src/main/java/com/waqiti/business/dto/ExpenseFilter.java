package com.waqiti.business.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.util.UUID;

@Data
@Builder
public class ExpenseFilter {
    private String category;
    private String status;
    private UUID employeeId;
    private LocalDate startDate;
    private LocalDate endDate;
}