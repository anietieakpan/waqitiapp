package com.waqiti.common.data;

import com.waqiti.common.dto.RecentTransactionDTO;
import com.waqiti.common.dto.CardStatsDTO;
import com.waqiti.common.dto.SpendingCategoryDTO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import lombok.extern.slf4j.Slf4j;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service to optimize database queries and resolve N+1 problems.
 * Implements efficient data fetching strategies for financial operations.
 */
@Service
@Slf4j
@Transactional(readOnly = true)
public class QueryOptimizationService {

    @PersistenceContext
    private EntityManager entityManager;

    /**
     * Fetch transactions with all related data in a single query (resolves N+1)
     */
    public List<TransactionDTO> findTransactionsWithDetails(String userId, LocalDateTime from, LocalDateTime to) {
        String jpql = """
            SELECT new com.waqiti.common.data.TransactionDTO(
                t.id, t.amount, t.currency, t.status, t.createdAt,
                u.firstName, u.lastName, u.email,
                a.accountNumber, a.accountType,
                p.paymentMethod, p.lastFourDigits
            )
            FROM Transaction t
            JOIN t.user u
            LEFT JOIN t.account a
            LEFT JOIN t.paymentMethod p
            WHERE u.id = :userId
            AND t.createdAt BETWEEN :from AND :to
            ORDER BY t.createdAt DESC
        """;

        TypedQuery<TransactionDTO> query = entityManager.createQuery(jpql, TransactionDTO.class);
        query.setParameter("userId", userId);
        query.setParameter("from", from);
        query.setParameter("to", to);

        List<TransactionDTO> transactions = query.getResultList();
        log.debug("Fetched {} transactions with details in single query", transactions.size());
        
        return transactions;
    }

    /**
     * Fetch user wallets with balances and recent transactions
     */
    public List<WalletSummaryDTO> findWalletSummariesWithTransactions(String userId) {
        // First, get wallet summaries
        String walletJpql = """
            SELECT new com.waqiti.common.data.WalletSummaryDTO(
                w.id, w.currency, w.balance, w.walletType, w.status
            )
            FROM Wallet w
            WHERE w.userId = :userId AND w.status = 'ACTIVE'
            ORDER BY w.currency
        """;

        List<WalletSummaryDTO> wallets = entityManager.createQuery(walletJpql, WalletSummaryDTO.class)
            .setParameter("userId", userId)
            .getResultList();

        if (wallets.isEmpty()) {
            return wallets;
        }

        // Get wallet IDs for batch fetching
        List<String> walletIds = wallets.stream()
            .map(WalletSummaryDTO::getId)
            .collect(Collectors.toList());

        // Batch fetch recent transactions for all wallets
        String txJpql = """
            SELECT new com.waqiti.common.data.RecentTransactionDTO(
                t.walletId, t.id, t.amount, t.type, t.createdAt, t.description
            )
            FROM Transaction t
            WHERE t.walletId IN :walletIds
            AND t.createdAt >= :cutoffDate
            ORDER BY t.walletId, t.createdAt DESC
        """;

        List<RecentTransactionDTO> recentTransactions = entityManager
            .createQuery(txJpql, RecentTransactionDTO.class)
            .setParameter("walletIds", walletIds)
            .setParameter("cutoffDate", LocalDateTime.now().minusDays(30))
            .getResultList();

        // Group transactions by wallet ID
        Map<String, List<RecentTransactionDTO>> transactionsByWallet = recentTransactions.stream()
            .collect(Collectors.groupingBy(tx -> tx.getWalletId().toString()));

        // Attach transactions to wallets
        wallets.forEach(wallet -> {
            List<RecentTransactionDTO> walletTransactions = 
                transactionsByWallet.getOrDefault(wallet.getId(), Collections.emptyList());
            wallet.setRecentTransactions(walletTransactions.stream()
                .limit(10) // Last 10 transactions
                .collect(Collectors.toList()));
        });

        log.debug("Fetched {} wallets with recent transactions", wallets.size());
        return wallets;
    }

    /**
     * Fetch virtual cards with transaction statistics (batch processing)
     */
    public Page<VirtualCardDTO> findVirtualCardsWithStats(String userId, Pageable pageable) {
        // Count total cards for pagination
        String countJpql = "SELECT COUNT(vc) FROM VirtualCard vc WHERE vc.userId = :userId";
        Long totalCount = entityManager.createQuery(countJpql, Long.class)
            .setParameter("userId", userId)
            .getSingleResult();

        if (totalCount == 0) {
            return new PageImpl<>(Collections.emptyList(), pageable, 0);
        }

        // Fetch cards with basic info
        String cardJpql = """
            SELECT new com.waqiti.common.data.VirtualCardDTO(
                vc.id, vc.cardNumber, vc.expiryDate, vc.status,
                vc.spendingLimit, vc.dailyLimit, vc.monthlyLimit
            )
            FROM VirtualCard vc
            WHERE vc.userId = :userId
            ORDER BY vc.createdAt DESC
        """;

        List<VirtualCardDTO> cards = entityManager.createQuery(cardJpql, VirtualCardDTO.class)
            .setParameter("userId", userId)
            .setFirstResult((int) pageable.getOffset())
            .setMaxResults(pageable.getPageSize())
            .getResultList();

        if (cards.isEmpty()) {
            return new PageImpl<>(cards, pageable, totalCount);
        }

        // Get card IDs for batch statistics fetching
        List<String> cardIds = cards.stream()
            .map(VirtualCardDTO::getId)
            .collect(Collectors.toList());

        // Batch fetch transaction statistics
        String statsJpql = """
            SELECT 
                ct.cardId as cardId,
                COUNT(ct) as transactionCount,
                COALESCE(SUM(ct.amount), 0) as totalSpent,
                COALESCE(SUM(CASE WHEN ct.createdAt >= :monthStart THEN ct.amount ELSE 0 END), 0) as monthlySpent,
                COALESCE(SUM(CASE WHEN ct.createdAt >= :dayStart THEN ct.amount ELSE 0 END), 0) as dailySpent,
                MAX(ct.createdAt) as lastTransactionDate
            FROM CardTransaction ct
            WHERE ct.cardId IN :cardIds AND ct.status = 'COMPLETED'
            GROUP BY ct.cardId
        """;

        List<Object[]> statsResults = entityManager.createQuery(statsJpql)
            .setParameter("cardIds", cardIds)
            .setParameter("monthStart", LocalDateTime.now().withDayOfMonth(1).withHour(0).withMinute(0).withSecond(0))
            .setParameter("dayStart", LocalDateTime.now().withHour(0).withMinute(0).withSecond(0))
            .getResultList();

        // Map statistics to cards
        Map<String, CardStatsDTO> statsByCardId = statsResults.stream()
            .collect(Collectors.toMap(
                row -> (String) row[0],
                row -> CardStatsDTO.builder()
                    .totalTransactions(((Number) row[1]).longValue())
                    .totalSpent((BigDecimal) row[2])
                    .monthlySpent((BigDecimal) row[3])
                    .dailySpent((BigDecimal) row[4])
                    .lastUsedAt((LocalDateTime) row[5])
                    .build()
            ));

        // Attach statistics to cards
        cards.forEach(card -> {
            CardStatsDTO stats = statsByCardId.getOrDefault(card.getId(), 
                CardStatsDTO.builder()
                    .totalTransactions(0L)
                    .totalSpent(BigDecimal.ZERO)
                    .monthlySpent(BigDecimal.ZERO)
                    .dailySpent(BigDecimal.ZERO)
                    .lastUsedAt(null)
                    .build());
            card.setStatistics(stats);
        });

        log.debug("Fetched {} virtual cards with statistics", cards.size());
        return new PageImpl<>(cards, pageable, totalCount);
    }

    /**
     * Fetch merchant data with rating details and payment methods
     */
    public List<MerchantDetailDTO> findMerchantsWithDetails(String category, double latitude, double longitude, double radiusKm) {
        String jpql = """
            SELECT new com.waqiti.common.data.MerchantDetailDTO(
                m.id, m.name, m.category, m.description,
                m.latitude, m.longitude, m.address,
                m.rating, m.reviewCount, m.status,
                CASE WHEN m.latitude IS NOT NULL AND m.longitude IS NOT NULL 
                     THEN ROUND(CAST(6371 * acos(cos(radians(:lat)) * cos(radians(m.latitude)) * 
                          cos(radians(m.longitude) - radians(:lng)) + sin(radians(:lat)) * 
                          sin(radians(m.latitude))) AS numeric), 2)
                     ELSE 999999 END as distance
            )
            FROM Merchant m
            WHERE m.category = :category
            AND m.status = 'ACTIVE'
            AND (
                m.latitude IS NULL OR m.longitude IS NULL OR
                6371 * acos(cos(radians(:lat)) * cos(radians(m.latitude)) * 
                cos(radians(m.longitude) - radians(:lng)) + sin(radians(:lat)) * 
                sin(radians(m.latitude))) <= :radius
            )
            ORDER BY distance ASC, m.rating DESC
        """;

        List<MerchantDetailDTO> merchants = entityManager.createQuery(jpql, MerchantDetailDTO.class)
            .setParameter("category", category)
            .setParameter("lat", latitude)
            .setParameter("lng", longitude)
            .setParameter("radius", radiusKm)
            .setMaxResults(50) // Limit results
            .getResultList();

        if (merchants.isEmpty()) {
            return merchants;
        }

        // Batch fetch payment methods for all merchants
        List<String> merchantIds = merchants.stream()
            .map(MerchantDetailDTO::getId)
            .collect(Collectors.toList());

        String paymentMethodsJpql = """
            SELECT mpm.merchantId, mpm.paymentMethod, mpm.isEnabled
            FROM MerchantPaymentMethod mpm
            WHERE mpm.merchantId IN :merchantIds AND mpm.isEnabled = true
        """;

        List<Object[]> paymentMethodResults = entityManager.createQuery(paymentMethodsJpql)
            .setParameter("merchantIds", merchantIds)
            .getResultList();

        // Group payment methods by merchant
        Map<String, List<String>> paymentMethodsByMerchant = paymentMethodResults.stream()
            .collect(Collectors.groupingBy(
                row -> (String) row[0],
                Collectors.mapping(row -> (String) row[1], Collectors.toList())
            ));

        // Attach payment methods to merchants
        merchants.forEach(merchant -> {
            List<String> paymentMethods = paymentMethodsByMerchant.getOrDefault(
                merchant.getId(), Collections.emptyList());
            merchant.setAcceptedPaymentMethods(paymentMethods);
        });

        log.debug("Fetched {} merchants with payment methods", merchants.size());
        return merchants;
    }

    /**
     * Fetch user analytics with aggregated data
     */
    public com.waqiti.common.dto.UserAnalyticsDTO getUserAnalytics(String userId, LocalDateTime from, LocalDateTime to) {
        // Single query to get all analytics data
        String analyticsJpql = """
            SELECT 
                COUNT(t) as totalTransactions,
                COALESCE(SUM(CASE WHEN t.type = 'CREDIT' THEN t.amount ELSE 0 END), 0) as totalCredits,
                COALESCE(SUM(CASE WHEN t.type = 'DEBIT' THEN t.amount ELSE 0 END), 0) as totalDebits,
                COALESCE(AVG(t.amount), 0) as averageTransaction,
                COALESCE(MAX(t.amount), 0) as largestTransaction,
                COUNT(DISTINCT t.currency) as currenciesUsed,
                COUNT(DISTINCT CASE WHEN t.type = 'DEBIT' THEN t.recipientId ELSE NULL END) as uniqueRecipients,
                COUNT(DISTINCT DATE(t.createdAt)) as activeDays
            FROM Transaction t
            WHERE t.userId = :userId
            AND t.createdAt BETWEEN :from AND :to
            AND t.status = 'COMPLETED'
        """;

        Object[] result = (Object[]) entityManager.createQuery(analyticsJpql)
            .setParameter("userId", userId)
            .setParameter("from", from)
            .setParameter("to", to)
            .getSingleResult();

        com.waqiti.common.dto.UserAnalyticsDTO analytics = com.waqiti.common.dto.UserAnalyticsDTO.builder()
            .totalTransactions(((Number) result[0]).longValue())
            .totalSpent((BigDecimal) result[2])
            .totalRevenue((BigDecimal) result[1])
            .averageTransactionAmount((BigDecimal) result[3])
            .build();

        // Fetch top spending categories in a separate optimized query
        String categoriesJpql = """
            SELECT 
                mc.category,
                COUNT(t) as transactionCount,
                SUM(t.amount) as totalAmount
            FROM Transaction t
            JOIN Merchant m ON t.merchantId = m.id
            JOIN MerchantCategory mc ON m.categoryId = mc.id
            WHERE t.userId = :userId
            AND t.createdAt BETWEEN :from AND :to
            AND t.status = 'COMPLETED'
            AND t.type = 'DEBIT'
            GROUP BY mc.category
            ORDER BY totalAmount DESC
        """;

        List<Object[]> categoryResults = entityManager.createQuery(categoriesJpql)
            .setParameter("userId", userId)
            .setParameter("from", from)
            .setParameter("to", to)
            .setMaxResults(10)
            .getResultList();

        List<SpendingCategoryDTO> categories = categoryResults.stream()
            .map(row -> new SpendingCategoryDTO(
                (String) row[0],
                ((Number) row[1]).longValue(),
                (BigDecimal) row[2]
            ))
            .collect(Collectors.toList());

        analytics.setTopSpendingCategories(categories);

        log.debug("Generated analytics for user {} covering {} transactions", 
            userId, analytics.getTotalTransactions());
        
        return analytics;
    }

    /**
     * Batch fetch user preferences to avoid N+1 queries
     */
    public Map<String, UserPreferencesDTO> batchFetchUserPreferences(List<String> userIds) {
        if (userIds.isEmpty()) {
            return Collections.emptyMap();
        }

        String jpql = """
            SELECT new com.waqiti.common.data.UserPreferencesDTO(
                up.userId, up.language, up.timezone, up.currency,
                up.notificationEmail, up.notificationSms, up.notificationPush,
                up.twoFactorEnabled, up.biometricEnabled
            )
            FROM UserPreferences up
            WHERE up.userId IN :userIds
        """;

        List<UserPreferencesDTO> preferences = entityManager
            .createQuery(jpql, UserPreferencesDTO.class)
            .setParameter("userIds", userIds)
            .getResultList();

        Map<String, UserPreferencesDTO> result = preferences.stream()
            .collect(Collectors.toMap(
                UserPreferencesDTO::getUserId,
                pref -> pref
            ));

        log.debug("Batch fetched preferences for {} users", result.size());
        return result;
    }

    /**
     * Optimized query for dashboard data (prevents multiple queries)
     */
    public DashboardDataDTO getDashboardData(String userId) {
        // Single query to get all dashboard metrics
        String dashboardJpql = """
            SELECT 
                (SELECT COALESCE(SUM(w.balance), 0) FROM Wallet w WHERE w.userId = :userId AND w.status = 'ACTIVE'),
                (SELECT COUNT(t) FROM Transaction t WHERE t.userId = :userId AND t.createdAt >= :todayStart AND t.status = 'COMPLETED'),
                (SELECT COALESCE(SUM(t.amount), 0) FROM Transaction t WHERE t.userId = :userId AND t.createdAt >= :todayStart AND t.type = 'DEBIT' AND t.status = 'COMPLETED'),
                (SELECT COUNT(t) FROM Transaction t WHERE t.userId = :userId AND t.status = 'PENDING'),
                (SELECT COUNT(n) FROM Notification n WHERE n.userId = :userId AND n.isRead = false),
                (SELECT COUNT(vc) FROM VirtualCard vc WHERE vc.userId = :userId AND vc.status = 'ACTIVE'),
                (SELECT COALESCE(AVG(t.amount), 0) FROM Transaction t WHERE t.userId = :userId AND t.createdAt >= :monthStart AND t.status = 'COMPLETED')
        """;

        Object[] result = (Object[]) entityManager.createQuery(dashboardJpql)
            .setParameter("userId", userId)
            .setParameter("todayStart", LocalDateTime.now().withHour(0).withMinute(0).withSecond(0))
            .setParameter("monthStart", LocalDateTime.now().withDayOfMonth(1).withHour(0).withMinute(0).withSecond(0))
            .getSingleResult();

        DashboardDataDTO dashboard = DashboardDataDTO.builder()
            .totalBalance((BigDecimal) result[0])
            .transactionCount7Days(((Number) result[1]).intValue())
            .spendingLast7Days((BigDecimal) result[2])
            .pendingTransactions((BigDecimal) result[3])
            .build();

        log.debug("Generated dashboard data for user {}", userId);
        return dashboard;
    }

    // DTO classes would be defined here or in separate files
    // [DTO class definitions omitted for brevity but would include all the referenced DTOs]
}