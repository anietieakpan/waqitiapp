package com.waqiti.business.dto;

import lombok.Builder;
import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InvoiceFilter {
    private String status;
    private String customerName;
    private String customerEmail;
    private LocalDate startDate;
    private LocalDate endDate;
    private LocalDate dueDateFrom;
    private LocalDate dueDateTo;
    private Double minAmount;
    private Double maxAmount;
    private List<String> tags;
    private String project;
    private Boolean isPaid;
    private Boolean isOverdue;
    private String invoiceNumberPrefix;
    private String sortBy;
    private String sortDirection;
}