package com.waqiti.ledger.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReconciliationDiscrepancyResponse {
    private UUID discrepancyId;
    private String discrepancyNumber;
    private UUID reconciliationId;
    private UUID accountId;
    private String accountName;
    private DiscrepancyType discrepancyType;
    private BigDecimal amount;
    private LocalDate transactionDate;
    private String description;
    private DiscrepancyStatus status;
    private DiscrepancyPriority priority;
    private String assignedTo;
    private LocalDateTime assignedDate;
    private LocalDateTime dueDate;
    private String resolution;
    private LocalDateTime resolvedDate;
    private String resolvedBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private List<DiscrepancyComment> comments;
    private List<DiscrepancyAttachment> attachments;
    private DiscrepancySource source;
}

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
class DiscrepancyComment {
    private UUID commentId;
    private String comment;
    private String commentedBy;
    private LocalDateTime commentedAt;
}

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
class DiscrepancyAttachment {
    private UUID attachmentId;
    private String fileName;
    private String fileType;
    private Long fileSize;
    private String uploadedBy;
    private LocalDateTime uploadedAt;
}

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
class DiscrepancySource {
    private String sourceType;
    private String sourceReference;
    private BigDecimal sourceAmount;
    private LocalDate sourceDate;
}