package com.waqiti.card.repository;

import com.waqiti.card.entity.CardProduct;
import com.waqiti.card.enums.CardBrand;
import com.waqiti.card.enums.CardType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * CardProductRepository - Spring Data JPA repository for CardProduct entity
 *
 * Provides data access methods for card product management including:
 * - Product lookup and queries
 * - Active product queries
 * - Network and issuer queries
 * - BIN range queries
 *
 * @author Waqiti Engineering Team
 * @version 2.0 (Consolidated)
 * @since 2025-11-09
 */
@Repository
public interface CardProductRepository extends JpaRepository<CardProduct, String>, JpaSpecificationExecutor<CardProduct> {

    // ========================================================================
    // BASIC LOOKUPS
    // ========================================================================

    /**
     * Find product by product ID
     */
    Optional<CardProduct> findByProductId(String productId);

    /**
     * Find product by product name
     */
    Optional<CardProduct> findByProductName(String productName);

    /**
     * Check if product exists by product ID
     */
    boolean existsByProductId(String productId);

    /**
     * Check if product exists by product name
     */
    boolean existsByProductName(String productName);

    // ========================================================================
    // ACTIVE PRODUCT QUERIES
    // ========================================================================

    /**
     * Find all active products
     */
    @Query("SELECT p FROM CardProduct p WHERE p.isActive = true AND p.deletedAt IS NULL")
    List<CardProduct> findAllActiveProducts();

    /**
     * Find active products by type
     */
    @Query("SELECT p FROM CardProduct p WHERE p.productType = :type AND p.isActive = true AND p.deletedAt IS NULL")
    List<CardProduct> findActiveProductsByType(@Param("type") CardType type);

    /**
     * Find active products by network
     */
    @Query("SELECT p FROM CardProduct p WHERE p.cardNetwork = :network AND p.isActive = true AND p.deletedAt IS NULL")
    List<CardProduct> findActiveProductsByNetwork(@Param("network") CardBrand network);

    /**
     * Find currently effective products (active and within date range)
     */
    @Query("SELECT p FROM CardProduct p WHERE p.isActive = true AND " +
           "(p.activationDate IS NULL OR p.activationDate <= :currentDate) AND " +
           "(p.expirationDate IS NULL OR p.expirationDate >= :currentDate) AND " +
           "p.deletedAt IS NULL")
    List<CardProduct> findCurrentlyEffectiveProducts(@Param("currentDate") LocalDate currentDate);

    // ========================================================================
    // TYPE & NETWORK QUERIES
    // ========================================================================

    /**
     * Find products by type
     */
    List<CardProduct> findByProductType(CardType productType);

    /**
     * Find products by card network
     */
    List<CardProduct> findByCardNetwork(CardBrand cardNetwork);

    /**
     * Find products by type and network
     */
    List<CardProduct> findByProductTypeAndCardNetwork(CardType productType, CardBrand cardNetwork);

    // ========================================================================
    // ISSUER & PROCESSOR QUERIES
    // ========================================================================

    /**
     * Find products by issuer ID
     */
    List<CardProduct> findByIssuerId(String issuerId);

    /**
     * Find active products by issuer ID
     */
    @Query("SELECT p FROM CardProduct p WHERE p.issuerId = :issuerId AND p.isActive = true AND p.deletedAt IS NULL")
    List<CardProduct> findActiveProductsByIssuerId(@Param("issuerId") String issuerId);

    /**
     * Find products by processor
     */
    List<CardProduct> findByProcessor(String processor);

    /**
     * Find products by program manager
     */
    List<CardProduct> findByProgramManager(String programManager);

    // ========================================================================
    // BIN RANGE QUERIES
    // ========================================================================

    /**
     * Find product by BIN (first 6 digits of card number)
     */
    @Query("SELECT p FROM CardProduct p WHERE " +
           "p.binRangeStart IS NOT NULL AND p.binRangeEnd IS NOT NULL AND " +
           ":bin BETWEEN p.binRangeStart AND p.binRangeEnd AND " +
           "p.deletedAt IS NULL")
    Optional<CardProduct> findByBin(@Param("bin") String bin);

    /**
     * Find products with BIN range overlapping specified range
     */
    @Query("SELECT p FROM CardProduct p WHERE " +
           "p.binRangeStart IS NOT NULL AND p.binRangeEnd IS NOT NULL AND " +
           "((p.binRangeStart <= :endBin AND p.binRangeEnd >= :startBin)) AND " +
           "p.deletedAt IS NULL")
    List<CardProduct> findProductsWithOverlappingBinRange(
        @Param("startBin") String startBin,
        @Param("endBin") String endBin
    );

    // ========================================================================
    // FEATURE QUERIES
    // ========================================================================

    /**
     * Find products with contactless enabled
     */
    @Query("SELECT p FROM CardProduct p WHERE p.contactlessEnabled = true AND p.isActive = true AND p.deletedAt IS NULL")
    List<CardProduct> findProductsWithContactless();

    /**
     * Find products with virtual card enabled
     */
    @Query("SELECT p FROM CardProduct p WHERE p.virtualCardEnabled = true AND p.isActive = true AND p.deletedAt IS NULL")
    List<CardProduct> findProductsWithVirtualCard();

    /**
     * Find products with international transactions enabled
     */
    @Query("SELECT p FROM CardProduct p WHERE p.internationalEnabled = true AND p.isActive = true AND p.deletedAt IS NULL")
    List<CardProduct> findProductsWithInternational();

    /**
     * Find products with rewards enabled
     */
    @Query("SELECT p FROM CardProduct p WHERE p.rewardsEnabled = true AND p.isActive = true AND p.deletedAt IS NULL")
    List<CardProduct> findProductsWithRewards();

    // ========================================================================
    // LIFECYCLE QUERIES
    // ========================================================================

    /**
     * Find products expiring soon
     */
    @Query("SELECT p FROM CardProduct p WHERE " +
           "p.expirationDate IS NOT NULL AND " +
           "p.expirationDate BETWEEN :currentDate AND :futureDate AND " +
           "p.isActive = true AND p.deletedAt IS NULL")
    List<CardProduct> findProductsExpiringSoon(
        @Param("currentDate") LocalDate currentDate,
        @Param("futureDate") LocalDate futureDate
    );

    /**
     * Find expired products
     */
    @Query("SELECT p FROM CardProduct p WHERE " +
           "p.expirationDate IS NOT NULL AND " +
           "p.expirationDate < :currentDate AND " +
           "p.isActive = true AND p.deletedAt IS NULL")
    List<CardProduct> findExpiredProducts(@Param("currentDate") LocalDate currentDate);

    /**
     * Find products pending activation
     */
    @Query("SELECT p FROM CardProduct p WHERE " +
           "p.activationDate IS NOT NULL AND " +
           "p.activationDate > :currentDate AND " +
           "p.deletedAt IS NULL")
    List<CardProduct> findProductsPendingActivation(@Param("currentDate") LocalDate currentDate);

    // ========================================================================
    // STATISTICAL QUERIES
    // ========================================================================

    /**
     * Count active products
     */
    @Query("SELECT COUNT(p) FROM CardProduct p WHERE p.isActive = true AND p.deletedAt IS NULL")
    long countActiveProducts();

    /**
     * Count products by type
     */
    long countByProductType(CardType productType);

    /**
     * Count products by network
     */
    long countByCardNetwork(CardBrand cardNetwork);

    /**
     * Get product statistics by type
     */
    @Query("SELECT p.productType, COUNT(p) FROM CardProduct p WHERE p.isActive = true AND p.deletedAt IS NULL GROUP BY p.productType")
    List<Object[]> getProductStatisticsByType();

    /**
     * Get product statistics by network
     */
    @Query("SELECT p.cardNetwork, COUNT(p) FROM CardProduct p WHERE p.isActive = true AND p.deletedAt IS NULL GROUP BY p.cardNetwork")
    List<Object[]> getProductStatisticsByNetwork();

    // ========================================================================
    // SOFT DELETE QUERIES
    // ========================================================================

    /**
     * Find all products including deleted
     */
    @Query("SELECT p FROM CardProduct p")
    List<CardProduct> findAllIncludingDeleted();

    /**
     * Find deleted products
     */
    @Query("SELECT p FROM CardProduct p WHERE p.deletedAt IS NOT NULL")
    List<CardProduct> findDeletedProducts();
}
