package com.waqiti.kyc.migration;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MigrationStats {
    
    private int totalCount;
    private int migratedCount;
    private int pendingCount;
    
    public double getProgressPercentage() {
        if (totalCount == 0) return 0.0;
        return (double) migratedCount / totalCount * 100.0;
    }
    
    public int getRemainingCount() {
        return pendingCount;
    }
    
    public boolean isComplete() {
        return pendingCount == 0;
    }
}