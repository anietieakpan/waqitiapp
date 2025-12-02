package com.waqiti.gdpr.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ErasureTransaction {
    private String userId;
    private String requestId;
    private String backupId;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
    private ErasureStatus status;
    private Map<String, Integer> erasedCategories = new HashMap<>();
    private Map<String, Integer> pseudonymizedCategories = new HashMap<>();
    private List<String> notifiedSystems;

    public void addErasedCategory(String category, int count) {
        erasedCategories.put(category, count);
    }

    public void addPseudonymizedCategory(String category, int count) {
        pseudonymizedCategories.put(category, count);
    }
}