package com.waqiti.ledger.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Represents an individual item in the period close checklist.
 * This DTO tracks specific tasks and validations that must be 
 * completed as part of the period closing process.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PeriodCloseChecklistItem {
    
    /**
     * Unique identifier for this checklist item
     */
    private UUID checklistItemId;
    
    /**
     * Period close process this item belongs to
     */
    private UUID periodCloseId;
    
    /**
     * Accounting period being closed
     */
    private UUID periodId;
    
    /**
     * Item sequence number for ordering
     */
    private Integer sequenceNumber;
    
    /**
     * Category of the checklist item
     */
    private String category;
    
    /**
     * Subcategory for more detailed grouping
     */
    private String subcategory;
    
    /**
     * Item title or name
     */
    private String itemTitle;
    
    /**
     * Detailed description of the item
     */
    private String itemDescription;
    
    /**
     * Instructions for completing the item
     */
    private String instructions;
    
    /**
     * Type of checklist item (VALIDATION, TASK, APPROVAL, REVIEW)
     */
    private String itemType;
    
    /**
     * Priority level (CRITICAL, HIGH, MEDIUM, LOW)
     */
    private String priority;
    
    /**
     * Current status of the item
     */
    private String status;
    
    /**
     * Whether this item is mandatory for period close
     */
    private boolean isMandatory;
    
    /**
     * Whether this item can be automated
     */
    private boolean isAutomated;
    
    /**
     * Whether this item was completed automatically
     */
    private boolean wasAutoCompleted;
    
    /**
     * Whether this item requires manual intervention
     */
    private boolean requiresManualIntervention;
    
    /**
     * Whether this item requires approval
     */
    private boolean requiresApproval;
    
    /**
     * User or role assigned to complete this item
     */
    private String assignedTo;
    
    /**
     * User or role responsible for this item
     */
    private String responsibleParty;
    
    /**
     * When the item was started
     */
    private LocalDateTime startedAt;
    
    /**
     * When the item was completed
     */
    private LocalDateTime completedAt;
    
    /**
     * Who completed the item
     */
    private String completedBy;
    
    /**
     * When the item was approved (if applicable)
     */
    private LocalDateTime approvedAt;
    
    /**
     * Who approved the item
     */
    private String approvedBy;
    
    /**
     * Approval comments or notes
     */
    private String approvalComments;
    
    /**
     * Due date for completing this item
     */
    private LocalDateTime dueDate;
    
    /**
     * Estimated time to complete (in minutes)
     */
    private Integer estimatedCompletionMinutes;
    
    /**
     * Actual time taken to complete (in minutes)
     */
    private Integer actualCompletionMinutes;
    
    /**
     * Progress percentage (0-100)
     */
    private Integer progressPercentage;
    
    /**
     * Dependencies that must be completed before this item
     */
    private List<String> dependencies;
    
    /**
     * Items that depend on this item being completed
     */
    private List<String> dependentItems;
    
    /**
     * Validation criteria for this item
     */
    private ChecklistItemValidation validationCriteria;
    
    /**
     * Result or outcome of completing this item
     */
    private ChecklistItemResult result;
    
    /**
     * Any issues or exceptions encountered
     */
    private List<ChecklistItemIssue> issues;
    
    /**
     * Comments or notes about this item
     */
    private String comments;
    
    /**
     * Supporting documents or evidence
     */
    private List<String> supportingDocuments;
    
    /**
     * Related accounts or transactions
     */
    private List<UUID> relatedAccounts;
    
    /**
     * Related journal entries
     */
    private List<UUID> relatedJournalEntries;
    
    /**
     * Financial impact or amounts involved
     */
    private BigDecimal financialImpact;
    
    /**
     * Currency for financial amounts
     */
    private String currency;
    
    /**
     * Risk level associated with this item
     */
    private String riskLevel;
    
    /**
     * Mitigation actions for identified risks
     */
    private List<String> mitigationActions;
    
    /**
     * Whether this item can be skipped
     */
    private boolean canBeSkipped;
    
    /**
     * Reason for skipping (if applicable)
     */
    private String skipReason;
    
    /**
     * Who authorized skipping this item
     */
    private String skipAuthorizedBy;
    
    /**
     * When the skip was authorized
     */
    private LocalDateTime skipAuthorizedAt;
    
    /**
     * Tags for categorization and filtering
     */
    private List<String> tags;
    
    /**
     * Custom metadata for this item
     */
    private String metadata;
    
    /**
     * When this item record was created
     */
    private LocalDateTime createdAt;
    
    /**
     * When this item record was last updated
     */
    private LocalDateTime lastUpdatedAt;
    
    /**
     * Who created this item record
     */
    private String createdBy;
    
    /**
     * Who last updated this item record
     */
    private String lastUpdatedBy;
    
    /**
     * Number of times this item was retried
     */
    private Integer retryCount;
    
    /**
     * Maximum allowed retries
     */
    private Integer maxRetries;
    
    /**
     * Whether this item is currently locked for editing
     */
    private boolean isLocked;
    
    /**
     * Who locked this item
     */
    private String lockedBy;
    
    /**
     * When this item was locked
     */
    private LocalDateTime lockedAt;
}

/**
 * Validation criteria for a checklist item
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
class ChecklistItemValidation {
    
    /**
     * Validation rules that must be met
     */
    private List<ValidationRule> validationRules;
    
    /**
     * Acceptance criteria for completion
     */
    private List<String> acceptanceCriteria;
    
    /**
     * Minimum required evidence or documentation
     */
    private List<String> requiredEvidence;
    
    /**
     * Quality standards that must be met
     */
    private List<String> qualityStandards;
    
    /**
     * Thresholds that must be satisfied
     */
    private List<ValidationThreshold> thresholds;
    
    /**
     * Whether automated validation passed
     */
    private Boolean automatedValidationPassed;
    
    /**
     * Whether manual validation passed
     */
    private Boolean manualValidationPassed;
    
    /**
     * Overall validation status
     */
    private String validationStatus;
    
    /**
     * Validation comments or notes
     */
    private String validationComments;
}

/**
 * Result of completing a checklist item
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
class ChecklistItemResult {
    
    /**
     * Whether the item was successfully completed
     */
    private boolean wasSuccessful;
    
    /**
     * Outcome or result summary
     */
    private String outcomeSummary;
    
    /**
     * Detailed result description
     */
    private String resultDescription;
    
    /**
     * Quantitative results (if applicable)
     */
    private BigDecimal quantitativeResult;
    
    /**
     * Unit of measure for quantitative results
     */
    private String resultUnit;
    
    /**
     * Files or documents generated as result
     */
    private List<String> generatedDocuments;
    
    /**
     * Actions taken to complete the item
     */
    private List<String> actionsTaken;
    
    /**
     * Key findings or discoveries
     */
    private List<String> keyFindings;
    
    /**
     * Recommendations based on the result
     */
    private List<String> recommendations;
    
    /**
     * Follow-up actions required
     */
    private List<String> followUpActions;
    
    /**
     * Impact assessment
     */
    private String impactAssessment;
}

/**
 * Issue encountered with a checklist item
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
class ChecklistItemIssue {
    
    /**
     * Issue ID
     */
    private String issueId;
    
    /**
     * Issue type
     */
    private String issueType;
    
    /**
     * Issue severity (CRITICAL, HIGH, MEDIUM, LOW)
     */
    private String severity;
    
    /**
     * Issue title
     */
    private String issueTitle;
    
    /**
     * Detailed issue description
     */
    private String issueDescription;
    
    /**
     * When the issue was identified
     */
    private LocalDateTime identifiedAt;
    
    /**
     * Who identified the issue
     */
    private String identifiedBy;
    
    /**
     * Current status of the issue
     */
    private String issueStatus;
    
    /**
     * Resolution action taken
     */
    private String resolutionAction;
    
    /**
     * When the issue was resolved
     */
    private LocalDateTime resolvedAt;
    
    /**
     * Who resolved the issue
     */
    private String resolvedBy;
    
    /**
     * Root cause analysis
     */
    private String rootCause;
    
    /**
     * Preventive measures implemented
     */
    private String preventiveMeasures;
}

/**
 * Validation rule for checklist items
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
class ValidationRule {
    
    /**
     * Rule ID
     */
    private String ruleId;
    
    /**
     * Rule name
     */
    private String ruleName;
    
    /**
     * Rule description
     */
    private String ruleDescription;
    
    /**
     * Rule type
     */
    private String ruleType;
    
    /**
     * Expected value or condition
     */
    private String expectedCondition;
    
    /**
     * Actual value found
     */
    private String actualValue;
    
    /**
     * Whether the rule passed
     */
    private boolean rulePassed;
    
    /**
     * Failure reason (if rule failed)
     */
    private String failureReason;
    
    /**
     * Rule evaluation timestamp
     */
    private LocalDateTime evaluatedAt;
}

/**
 * Validation threshold
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
class ValidationThreshold {
    
    /**
     * Threshold name
     */
    private String thresholdName;
    
    /**
     * Threshold type (MINIMUM, MAXIMUM, EXACT, RANGE)
     */
    private String thresholdType;
    
    /**
     * Minimum value (if applicable)
     */
    private BigDecimal minValue;
    
    /**
     * Maximum value (if applicable)
     */
    private BigDecimal maxValue;
    
    /**
     * Exact value (if applicable)
     */
    private BigDecimal exactValue;
    
    /**
     * Actual value measured
     */
    private BigDecimal actualValue;
    
    /**
     * Whether the threshold was met
     */
    private boolean thresholdMet;
    
    /**
     * Unit of measure
     */
    private String unit;
    
    /**
     * Threshold evaluation timestamp
     */
    private LocalDateTime evaluatedAt;
}