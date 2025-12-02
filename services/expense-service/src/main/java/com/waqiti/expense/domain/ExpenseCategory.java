package com.waqiti.expense.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.GenericGenerator;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Expense Category Entity
 * Hierarchical categorization system for expenses with intelligent classification
 */
@Entity
@Table(name = "expense_categories", indexes = {
    @Index(name = "idx_user_id", columnList = "user_id"),
    @Index(name = "idx_parent_category", columnList = "parent_category_id"),
    @Index(name = "idx_category_type", columnList = "category_type"),
    @Index(name = "idx_is_active", columnList = "is_active"),
    @Index(name = "idx_user_parent", columnList = "user_id, parent_category_id")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@EntityListeners(AuditingEntityListener.class)
public class ExpenseCategory {

    @Id
    @GeneratedValue(generator = "uuid2")
    @GenericGenerator(name = "uuid2", strategy = "uuid2")
    @EqualsAndHashCode.Include
    private UUID id;

    @Column(name = "category_id", unique = true, nullable = false)
    private String categoryId; // Business key (e.g., "FOOD", "TRANSPORT")

    @Column(name = "user_id")
    private UUID userId; // null for system/default categories

    @Column(name = "category_name", nullable = false)
    private String name; // Display name

    @Column(name = "description")
    private String description;

    @Column(name = "icon")
    private String icon;

    @Column(name = "color")
    private String color;

    // Hierarchical Structure
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_category_id")
    private ExpenseCategory parentCategory;

    @OneToMany(mappedBy = "parentCategory", fetch = FetchType.LAZY, cascade = CascadeType.ALL)
    private List<ExpenseCategory> subCategories = new ArrayList<>();

    @Column(name = "level")
    private Integer level = 0; // 0 = top level, 1 = subcategory, etc.

    @Column(name = "category_path")
    private String categoryPath; // e.g., "Food > Restaurants > Fast Food"

    // Category Type and Properties
    @Enumerated(EnumType.STRING)
    @Column(name = "category_type", nullable = false)
    private CategoryType categoryType;

    @Column(name = "is_system_category")
    private Boolean isSystemCategory = false;

    @Column(name = "is_active")
    private Boolean isActive = true;

    @Column(name = "is_tax_deductible")
    private Boolean isTaxDeductible = false;

    @Column(name = "requires_receipt")
    private Boolean requiresReceipt = false;

    // Budgeting and Limits
    @Column(name = "default_budget_amount", precision = 19, scale = 2)
    private BigDecimal defaultBudgetAmount;

    @Column(name = "suggested_budget_percentage", precision = 5, scale = 2)
    private BigDecimal suggestedBudgetPercentage;

    @Column(name = "monthly_limit", precision = 19, scale = 2)
    private BigDecimal monthlyLimit;

    @Column(name = "warning_threshold", precision = 19, scale = 2)
    private BigDecimal warningThreshold;

    // ML and Classification
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "category_keywords", joinColumns = @JoinColumn(name = "category_id"))
    @Column(name = "keyword")
    private List<String> keywords = new ArrayList<>();

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "category_merchant_patterns", joinColumns = @JoinColumn(name = "category_id"))
    @Column(name = "pattern")
    private List<String> merchantPatterns = new ArrayList<>();

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "category_mcc_codes", joinColumns = @JoinColumn(name = "category_id"))
    @Column(name = "mcc_code")
    private List<String> mccCodes = new ArrayList<>();

    @Column(name = "classification_confidence", precision = 5, scale = 4)
    private BigDecimal classificationConfidence;

    // Usage Statistics
    @Column(name = "usage_count")
    private Long usageCount = 0L;

    @Column(name = "total_spent", precision = 19, scale = 2)
    private BigDecimal totalSpent = BigDecimal.ZERO;

    @Column(name = "average_transaction_amount", precision = 19, scale = 2)
    private BigDecimal averageTransactionAmount;

    @Column(name = "last_used_at")
    private LocalDateTime lastUsedAt;

    // Display and Sorting
    @Column(name = "display_order")
    private Integer displayOrder = 0;

    @Column(name = "is_favorite")
    private Boolean isFavorite = false;

    @Column(name = "is_hidden")
    private Boolean isHidden = false;

    // Metadata and Custom Fields
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "category_metadata", joinColumns = @JoinColumn(name = "category_id"))
    @MapKeyColumn(name = "metadata_key")
    @Column(name = "metadata_value")
    private Map<String, String> metadata;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Version
    private Long version;

    // Business Methods

    /**
     * Add subcategory
     */
    public void addSubCategory(ExpenseCategory subCategory) {
        if (subCategories == null) {
            subCategories = new ArrayList<>();
        }
        subCategory.setParentCategory(this);
        subCategory.setLevel(this.level + 1);
        subCategory.updateCategoryPath();
        subCategories.add(subCategory);
    }

    /**
     * Remove subcategory
     */
    public void removeSubCategory(ExpenseCategory subCategory) {
        if (subCategories != null) {
            subCategories.remove(subCategory);
            subCategory.setParentCategory(null);
        }
    }

    /**
     * Update category path based on hierarchy
     */
    public void updateCategoryPath() {
        if (parentCategory == null) {
            categoryPath = name;
        } else {
            categoryPath = parentCategory.getCategoryPath() + " > " + name;
        }
        
        // Update all subcategory paths
        if (subCategories != null) {
            subCategories.forEach(ExpenseCategory::updateCategoryPath);
        }
    }

    /**
     * Check if category matches transaction data
     */
    public boolean matchesTransaction(String merchantName, String description, String mccCode) {
        double score = 0.0;
        
        // Check MCC codes
        if (mccCode != null && mccCodes.contains(mccCode)) {
            score += 0.8;
        }
        
        // Check merchant patterns
        if (merchantName != null) {
            for (String pattern : merchantPatterns) {
                if (merchantName.toLowerCase().contains(pattern.toLowerCase())) {
                    score += 0.6;
                    break;
                }
            }
        }
        
        // Check keywords in description
        if (description != null) {
            String lowerDescription = description.toLowerCase();
            for (String keyword : keywords) {
                if (lowerDescription.contains(keyword.toLowerCase())) {
                    score += 0.4;
                }
            }
        }
        
        return score >= 0.5; // Threshold for match
    }

    /**
     * Calculate classification confidence for a transaction
     */
    public double calculateConfidence(String merchantName, String description, String mccCode) {
        double confidence = 0.0;
        int factors = 0;
        
        // MCC code match (highest confidence)
        if (mccCode != null && mccCodes.contains(mccCode)) {
            confidence += 0.8;
            factors++;
        }
        
        // Merchant pattern match
        if (merchantName != null) {
            for (String pattern : merchantPatterns) {
                if (merchantName.toLowerCase().contains(pattern.toLowerCase())) {
                    confidence += 0.6;
                    factors++;
                    break;
                }
            }
        }
        
        // Keyword match
        if (description != null) {
            String lowerDescription = description.toLowerCase();
            double keywordScore = 0.0;
            int keywordMatches = 0;
            for (String keyword : keywords) {
                if (lowerDescription.contains(keyword.toLowerCase())) {
                    keywordScore += 0.4;
                    keywordMatches++;
                }
            }
            if (keywordMatches > 0) {
                confidence += Math.min(0.6, keywordScore); // Cap keyword contribution
                factors++;
            }
        }
        
        // Average the confidence if multiple factors match
        return factors > 0 ? Math.min(1.0, confidence / factors) : 0.0;
    }

    /**
     * Record expense usage
     */
    public void recordUsage(BigDecimal amount) {
        usageCount++;
        totalSpent = totalSpent.add(amount);
        averageTransactionAmount = totalSpent.divide(BigDecimal.valueOf(usageCount), 2, RoundingMode.HALF_UP);
        lastUsedAt = LocalDateTime.now();
    }

    /**
     * Add keyword for classification
     */
    public void addKeyword(String keyword) {
        if (keywords == null) {
            keywords = new ArrayList<>();
        }
        if (!keywords.contains(keyword.toLowerCase())) {
            keywords.add(keyword.toLowerCase());
        }
    }

    /**
     * Add merchant pattern
     */
    public void addMerchantPattern(String pattern) {
        if (merchantPatterns == null) {
            merchantPatterns = new ArrayList<>();
        }
        if (!merchantPatterns.contains(pattern.toLowerCase())) {
            merchantPatterns.add(pattern.toLowerCase());
        }
    }

    /**
     * Add MCC code
     */
    public void addMccCode(String mccCode) {
        if (mccCodes == null) {
            mccCodes = new ArrayList<>();
        }
        if (!mccCodes.contains(mccCode)) {
            mccCodes.add(mccCode);
        }
    }

    /**
     * Check if category is a parent category
     */
    public boolean isParentCategory() {
        return subCategories != null && !subCategories.isEmpty();
    }

    /**
     * Check if category is a leaf category
     */
    public boolean isLeafCategory() {
        return subCategories == null || subCategories.isEmpty();
    }

    /**
     * Get all descendant categories
     */
    public List<ExpenseCategory> getAllDescendants() {
        List<ExpenseCategory> descendants = new ArrayList<>();
        if (subCategories != null) {
            for (ExpenseCategory subCategory : subCategories) {
                descendants.add(subCategory);
                descendants.addAll(subCategory.getAllDescendants());
            }
        }
        return descendants;
    }

    /**
     * Get top-level category
     */
    public ExpenseCategory getTopLevelCategory() {
        ExpenseCategory current = this;
        while (current.getParentCategory() != null) {
            current = current.getParentCategory();
        }
        return current;
    }

    /**
     * Check if this category is ancestor of another
     */
    public boolean isAncestorOf(ExpenseCategory other) {
        if (other == null) return false;
        
        ExpenseCategory current = other.getParentCategory();
        while (current != null) {
            if (current.equals(this)) {
                return true;
            }
            current = current.getParentCategory();
        }
        return false;
    }

    /**
     * Check if this category is descendant of another
     */
    public boolean isDescendantOf(ExpenseCategory other) {
        return other != null && other.isAncestorOf(this);
    }

    /**
     * Get full category hierarchy as list
     */
    public List<ExpenseCategory> getHierarchyPath() {
        List<ExpenseCategory> path = new ArrayList<>();
        ExpenseCategory current = this;
        while (current != null) {
            path.add(0, current); // Add to beginning to maintain order
            current = current.getParentCategory();
        }
        return path;
    }

    @PrePersist
    protected void onCreate() {
        updateCategoryPath();
        if (displayOrder == null) {
            displayOrder = 0;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updateCategoryPath();
    }

    /**
     * Category Type Enumeration
     */
    public enum CategoryType {
        INCOME,
        FIXED_EXPENSE,      // Rent, insurance, etc.
        VARIABLE_EXPENSE,   // Groceries, dining, etc.
        DISCRETIONARY,      // Entertainment, hobbies, etc.
        INVESTMENT,
        SAVINGS,
        DEBT_PAYMENT,
        TRANSFER,
        TAX,
        BUSINESS,
        PERSONAL,
        OTHER
    }
}