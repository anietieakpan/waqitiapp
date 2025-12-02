package com.waqiti.expense.service;

import com.waqiti.expense.dto.ExpenseReportDto;
import com.waqiti.expense.dto.GenerateReportRequestDto;
import org.springframework.http.ResponseEntity;

import java.util.UUID;

/**
 * Service interface for expense report generation
 */
public interface ExpenseReportService {

    /**
     * Generate expense report in specified format
     *
     * @param request report generation request
     * @return generated report details
     */
    ExpenseReportDto generateReport(GenerateReportRequestDto request);

    /**
     * Download generated report file
     *
     * @param reportId report ID
     * @return report file as byte array with appropriate headers
     */
    ResponseEntity<byte[]> downloadReport(UUID reportId);

    /**
     * Get report generation status
     *
     * @param reportId report ID
     * @return report status
     */
    ExpenseReportDto getReportStatus(UUID reportId);

    /**
     * Delete generated report
     *
     * @param reportId report ID
     */
    void deleteReport(UUID reportId);
}
