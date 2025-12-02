package com.waqiti.kyc.dto;

import com.waqiti.kyc.enums.CrimeCategory;
import com.waqiti.kyc.service.CriminalRecordService;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.Period;
import java.util.List;

/**
 * DTO representing criminal record check results
 * 
 * @author Waqiti Compliance Team
 * @version 2.0.0
 * @since 2025-01-27
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CriminalRecordResult {
    
    private String fullName;
    private LocalDate dateOfBirth;
    private boolean checkCompleted;
    private boolean consentObtained;
    private int recordsFound;
    private List<CrimeCategory> convictionCategories;
    private LocalDate mostRecentConvictionDate;
    private List<CriminalRecordService.CriminalRecord> records;
    private String errorMessage;
    
    public boolean hasRelevantConvictions() {
        return recordsFound > 0 && convictionCategories != null && !convictionCategories.isEmpty();
    }
    
    public boolean hasRecentFinancialCrimeHistory(int yearsBack) {
        if (mostRecentConvictionDate == null) {
            return false;
        }
        
        LocalDate cutoffDate = LocalDate.now().minusYears(yearsBack);
        return mostRecentConvictionDate.isAfter(cutoffDate);
    }
}