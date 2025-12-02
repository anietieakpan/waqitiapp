package com.waqiti.grouppayment.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

/**
 * Group payment pool entity for shared expense management and savings goals.
 *
 * @author Waqiti Platform Team
 * @since 1.0
 */
@Data
@Entity
@Table(name = "group_payment_pools", indexes = {
    @Index(name = "idx_pools_creator", columnList = "creator_id"),
    @Index(name = "idx_pools_status", columnList = "status"),
    @Index(name = "idx_pools_type", columnList = "type"),
    @Index(name = "idx_pools_created", columnList = "created_at")
})
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GroupPaymentPool {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;
    
    @Column(name = "name", nullable = false)
    private String name;
    
    @Column(name = "description", length = 1000)
    private String description;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false)
    private PoolType type;
    
    @Column(name = "creator_id", nullable = false)
    private String creatorId;
    
    @Column(name = "currency", nullable = false, length = 3)
    private String currency;
    
    @Column(name = "total_balance", nullable = false, precision = 19, scale = 4)
    @Builder.Default
    private BigDecimal totalBalance = BigDecimal.ZERO;
    
    @Column(name = "goal_amount", precision = 19, scale = 4)
    private BigDecimal goalAmount;
    
    @Column(name = "goal_deadline")
    private Instant goalDeadline;
    
    @Column(name = "goal_reached_at")
    private Instant goalReachedAt;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private PoolStatus status;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "visibility", nullable = false)
    @Builder.Default
    private PoolVisibility visibility = PoolVisibility.PRIVATE;
    
    @Column(name = "image_url")
    private String imageUrl;
    
    @OneToMany(mappedBy = "pool", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @Builder.Default
    private List<PoolMember> members = new ArrayList<>();
    
    @OneToMany(mappedBy = "pool", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @Builder.Default
    private List<PoolTransaction> transactions = new ArrayList<>();
    
    @Embedded
    private PoolSettings settings;
    
    @Embedded
    private ContributionSettings contributionSettings;
    
    @Column(name = "last_activity_at")
    private Instant lastActivityAt;
    
    @Column(name = "last_settlement_at")
    private Instant lastSettlementAt;
    
    @ElementCollection
    @CollectionTable(name = "pool_tags", joinColumns = @JoinColumn(name = "pool_id"))
    @Column(name = "tag")
    @Builder.Default
    private Set<String> tags = new HashSet<>();
    
    @ElementCollection
    @CollectionTable(name = "pool_metadata", joinColumns = @JoinColumn(name = "pool_id"))
    @MapKeyColumn(name = "key")
    @Column(name = "value")
    @Builder.Default
    private Map<String, String> metadata = new HashMap<>();
    
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    
    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;
    
    @Version
    @Column(name = "version")
    private Long version;
}

/**
 * Pool member entity representing a participant in a group payment pool.
 */
@Data
@Entity
@Table(name = "pool_members", indexes = {
    @Index(name = "idx_members_user", columnList = "user_id"),
    @Index(name = "idx_members_pool", columnList = "pool_id"),
    @Index(name = "idx_members_status", columnList = "status")
})
@Builder
@NoArgsConstructor
@AllArgsConstructor
class PoolMember {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;
    
    @Column(name = "user_id", nullable = false)
    private String userId;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pool_id", nullable = false)
    private GroupPaymentPool pool;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false)
    private MemberRole role;
    
    @Column(name = "nickname")
    private String nickname;
    
    @Column(name = "joined_at", nullable = false)
    private Instant joinedAt;
    
    @Column(name = "invited_by")
    private String invitedBy;
    
    @Column(name = "balance", nullable = false, precision = 19, scale = 4)
    @Builder.Default
    private BigDecimal balance = BigDecimal.ZERO;
    
    @Column(name = "total_contributed", nullable = false, precision = 19, scale = 4)
    @Builder.Default
    private BigDecimal totalContributed = BigDecimal.ZERO;
    
    @Column(name = "total_spent", nullable = false, precision = 19, scale = 4)
    @Builder.Default
    private BigDecimal totalSpent = BigDecimal.ZERO;
    
    @Column(name = "contribution_target", precision = 19, scale = 4)
    private BigDecimal contributionTarget;
    
    @Column(name = "last_contribution_at")
    private Instant lastContributionAt;
    
    @Column(name = "last_settled_at")
    private Instant lastSettledAt;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private MemberStatus status;
    
    @Embedded
    private MemberPreferences preferences;
}

/**
 * Pool transaction entity representing expenses, payments, and contributions.
 */
@Data
@Entity
@Table(name = "pool_transactions", indexes = {
    @Index(name = "idx_transactions_pool", columnList = "pool_id"),
    @Index(name = "idx_transactions_type", columnList = "type"),
    @Index(name = "idx_transactions_date", columnList = "transaction_date"),
    @Index(name = "idx_transactions_status", columnList = "status")
})
@Builder
@NoArgsConstructor
@AllArgsConstructor
class PoolTransaction {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pool_id", nullable = false)
    private GroupPaymentPool pool;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false)
    private TransactionType type;
    
    @Column(name = "amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;
    
    @Column(name = "currency", nullable = false, length = 3)
    private String currency;
    
    @Column(name = "description", nullable = false)
    private String description;
    
    @Column(name = "category")
    private String category;
    
    @Column(name = "paid_by_id", nullable = false)
    private String paidById;
    
    @Column(name = "created_by_id", nullable = false)
    private String createdById;
    
    @OneToMany(mappedBy = "transaction", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @Builder.Default
    private List<TransactionSplit> splits = new ArrayList<>();
    
    @Column(name = "receipt_url")
    private String receiptUrl;
    
    @ElementCollection
    @CollectionTable(name = "receipt_items", joinColumns = @JoinColumn(name = "transaction_id"))
    private List<String> receiptItems;
    
    @Column(name = "location")
    private String location;
    
    @Column(name = "merchant_name")
    private String merchantName;
    
    @Column(name = "transaction_date", nullable = false)
    private Instant transactionDate;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private TransactionStatus status;
    
    @Column(name = "payment_reference")
    private String paymentReference;
    
    @ElementCollection
    @CollectionTable(name = "transaction_metadata", joinColumns = @JoinColumn(name = "transaction_id"))
    @MapKeyColumn(name = "key")
    @Column(name = "value")
    @Builder.Default
    private Map<String, String> metadata = new HashMap<>();
    
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}

/**
 * Transaction split entity representing how an expense is divided among members.
 */
@Data
@Entity
@Table(name = "transaction_splits", indexes = {
    @Index(name = "idx_splits_transaction", columnList = "transaction_id"),
    @Index(name = "idx_splits_user", columnList = "user_id")
})
@Builder
@NoArgsConstructor
@AllArgsConstructor
class TransactionSplit {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "transaction_id", nullable = false)
    private PoolTransaction transaction;
    
    @Column(name = "user_id", nullable = false)
    private String userId;
    
    @Column(name = "amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;
    
    @Column(name = "percentage", precision = 5, scale = 2)
    private BigDecimal percentage;
    
    @Column(name = "paid", nullable = false)
    @Builder.Default
    private boolean paid = false;
    
    @Column(name = "paid_at")
    private Instant paidAt;
    
    @Column(name = "notes")
    private String notes;
}

/**
 * Pool settings for configuration and preferences.
 */
@Embeddable
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
class PoolSettings {
    
    @Column(name = "auto_settle")
    @Builder.Default
    private boolean autoSettle = false;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "settlement_frequency")
    private SettlementFrequency settlementFrequency;
    
    @Column(name = "custom_frequency_days")
    private Integer customFrequencyDays;
    
    @Column(name = "allow_guest_expenses")
    @Builder.Default
    private boolean allowGuestExpenses = false;
    
    @Column(name = "require_receipt_for_expenses")
    @Builder.Default
    private boolean requireReceiptForExpenses = false;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "default_split_method")
    @Builder.Default
    private SplitMethod defaultSplitMethod = SplitMethod.EQUAL;
    
    @Column(name = "min_expense_amount", precision = 19, scale = 4)
    private BigDecimal minExpenseAmount;
    
    @Column(name = "max_expense_amount", precision = 19, scale = 4)
    private BigDecimal maxExpenseAmount;
    
    @Embedded
    private NotificationPreferences notificationPreferences;
}

/**
 * Contribution settings for savings pools.
 */
@Embeddable
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
class ContributionSettings {
    
    @Column(name = "automatic_contributions")
    @Builder.Default
    private boolean automaticContributions = false;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "contribution_frequency")
    private ContributionFrequency contributionFrequency;
    
    @Column(name = "default_amount", precision = 19, scale = 4)
    private BigDecimal defaultAmount;
    
    @Column(name = "reminder_enabled")
    @Builder.Default
    private boolean reminderEnabled = true;
    
    @Column(name = "reminder_days_before")
    @Builder.Default
    private Integer reminderDaysBefore = 3;
    
    @Column(name = "penalty_for_missed_contribution", precision = 19, scale = 4)
    private BigDecimal penaltyAmount;
}

/**
 * Member preferences for pool participation.
 */
@Embeddable
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
class MemberPreferences {
    
    @Column(name = "notify_on_expense")
    @Builder.Default
    private boolean notifyOnExpense = true;
    
    @Column(name = "notify_on_payment")
    @Builder.Default
    private boolean notifyOnPayment = true;
    
    @Column(name = "notify_on_settlement")
    @Builder.Default
    private boolean notifyOnSettlement = true;
    
    @Column(name = "auto_approve_expenses")
    @Builder.Default
    private boolean autoApproveExpenses = true;
    
    @Column(name = "preferred_payment_method")
    private String preferredPaymentMethod;
}

/**
 * Notification preferences for pool.
 */
@Embeddable
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
class NotificationPreferences {
    
    @Column(name = "notify_expense_added")
    @Builder.Default
    private boolean expenseAdded = true;
    
    @Column(name = "notify_payment_received")
    @Builder.Default
    private boolean paymentReceived = true;
    
    @Column(name = "notify_settlement_reminder")
    @Builder.Default
    private boolean settlementReminder = true;
    
    @Column(name = "notify_goal_progress")
    @Builder.Default
    private boolean goalProgress = true;
    
    @Column(name = "notify_member_joined")
    @Builder.Default
    private boolean memberJoined = true;
    
    @Column(name = "notification_channels")
    private String notificationChannels; // Comma-separated: EMAIL,SMS,PUSH
}

// Enums

enum PoolType {
    SHARED_EXPENSES,  // Regular expense sharing (like Splitwise)
    SAVINGS_GOAL,     // Group savings for a goal
    EVENT,            // Event-specific (trip, party, etc.)
    RECURRING,        // Recurring group expenses (rent, utilities)
    INVESTMENT        // Group investment pool
}

enum PoolStatus {
    ACTIVE,
    PAUSED,
    GOAL_REACHED,
    SETTLED,
    ARCHIVED,
    DELETED
}

enum PoolVisibility {
    PRIVATE,          // Only members can see
    FRIENDS_ONLY,     // Friends of members can see
    PUBLIC            // Anyone can see (not join)
}

enum MemberRole {
    ADMIN,            // Full control
    MODERATOR,        // Can add expenses, invite members
    MEMBER,           // Can add own expenses
    VIEWER            // Read-only access
}

enum MemberStatus {
    ACTIVE,
    PENDING,          // Invited but not accepted
    INACTIVE,
    REMOVED,
    BLOCKED
}

enum TransactionType {
    EXPENSE,          // Shared expense
    PAYMENT,          // Settlement payment
    CONTRIBUTION,     // Contribution to savings goal
    WITHDRAWAL,       // Withdrawal from pool
    ADJUSTMENT        // Manual balance adjustment
}

enum TransactionStatus {
    PENDING,
    APPROVED,
    COMPLETED,
    REJECTED,
    CANCELLED
}

enum SplitMethod {
    EQUAL,            // Split equally among all
    PERCENTAGE,       // Split by percentage
    CUSTOM_AMOUNT,    // Custom amount per person
    BY_USAGE,         // Based on historical usage patterns
    PROPORTIONAL      // Based on income/contribution ratios
}

enum SettlementFrequency {
    DAILY,
    WEEKLY,
    MONTHLY,
    QUARTERLY,
    ON_DEMAND,
    CUSTOM
}

enum ContributionFrequency {
    WEEKLY,
    BIWEEKLY,
    MONTHLY,
    QUARTERLY,
    ONE_TIME
}

enum OptimizationMethod {
    MINIMUM_TRANSACTIONS,  // Minimize number of transactions
    ROUND_AMOUNTS,        // Round to nice amounts
    BATCH_PAYMENTS        // Batch similar payments
}

enum PoolPermission {
    VIEW,
    ADD_EXPENSE,
    INVITE_MEMBERS,
    MODIFY_SETTINGS,
    DELETE_POOL
}