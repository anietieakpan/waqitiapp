package com.waqiti.business.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class EmployeeFilter {
    private String department;
    private String role;
    private String status;
}