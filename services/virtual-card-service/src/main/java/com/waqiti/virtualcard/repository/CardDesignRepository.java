package com.waqiti.virtualcard.repository;

import com.waqiti.virtualcard.domain.CardDesign;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Repository for Card Design operations
 */
@Repository
public interface CardDesignRepository extends JpaRepository<CardDesign, String> {
    
    /**
     * Find design by design code
     */
    Optional<CardDesign> findByDesignCode(String designCode);
    
    /**
     * Find all active designs
     */
    List<CardDesign> findByActiveTrue();
    
    /**
     * Find active designs ordered by sort order
     */
    List<CardDesign> findByActiveTrueOrderBySortOrderAscNameAsc();
    
    /**
     * Find designs by category
     */
    List<CardDesign> findByCategoryAndActiveTrue(String category);
    
    /**
     * Find premium designs
     */
    List<CardDesign> findByIsPremiumTrueAndActiveTrue();
    
    /**
     * Find free designs (non-premium)
     */
    List<CardDesign> findByIsPremiumFalseAndActiveTrue();
    
    /**
     * Find default design
     */
    Optional<CardDesign> findByIsDefaultTrueAndActiveTrue();
    
    /**
     * Find available designs (considering availability dates and order limits)
     */
    @Query("SELECT d FROM CardDesign d WHERE d.active = true " +
           "AND (d.availableFrom IS NULL OR d.availableFrom <= :now) " +
           "AND (d.availableUntil IS NULL OR d.availableUntil >= :now) " +
           "AND (d.maxOrders IS NULL OR d.currentOrders < d.maxOrders) " +
           "ORDER BY d.sortOrder ASC, d.name ASC")
    List<CardDesign> findAvailableDesigns(@Param("now") Instant now);
    
    /**
     * Find designs by category with availability check
     */
    @Query("SELECT d FROM CardDesign d WHERE d.category = :category AND d.active = true " +
           "AND (d.availableFrom IS NULL OR d.availableFrom <= :now) " +
           "AND (d.availableUntil IS NULL OR d.availableUntil >= :now) " +
           "AND (d.maxOrders IS NULL OR d.currentOrders < d.maxOrders) " +
           "ORDER BY d.sortOrder ASC, d.name ASC")
    List<CardDesign> findAvailableDesignsByCategory(@Param("category") String category, 
                                                     @Param("now") Instant now);
    
    /**
     * Find designs that are running low on availability
     */
    @Query("SELECT d FROM CardDesign d WHERE d.active = true AND d.maxOrders IS NOT NULL " +
           "AND (d.maxOrders - d.currentOrders) <= :threshold")
    List<CardDesign> findDesignsRunningLow(@Param("threshold") int threshold);
    
    /**
     * Find expired designs
     */
    @Query("SELECT d FROM CardDesign d WHERE d.active = true " +
           "AND d.availableUntil IS NOT NULL AND d.availableUntil < :now")
    List<CardDesign> findExpiredDesigns(@Param("now") Instant now);
    
    /**
     * Find designs that will expire soon
     */
    @Query("SELECT d FROM CardDesign d WHERE d.active = true " +
           "AND d.availableUntil IS NOT NULL AND d.availableUntil BETWEEN :now AND :threshold")
    List<CardDesign> findDesignsExpiringSoon(@Param("now") Instant now, 
                                             @Param("threshold") Instant threshold);
    
    /**
     * Find all categories
     */
    @Query("SELECT DISTINCT d.category FROM CardDesign d WHERE d.active = true AND d.category IS NOT NULL")
    List<String> findAllCategories();
    
    /**
     * Find designs with pagination
     */
    Page<CardDesign> findByActiveTrueOrderBySortOrderAscNameAsc(Pageable pageable);
    
    /**
     * Find designs by name containing (search)
     */
    @Query("SELECT d FROM CardDesign d WHERE d.active = true " +
           "AND (LOWER(d.name) LIKE LOWER(CONCAT('%', :searchTerm, '%')) " +
           "OR LOWER(d.description) LIKE LOWER(CONCAT('%', :searchTerm, '%'))) " +
           "ORDER BY d.sortOrder ASC, d.name ASC")
    List<CardDesign> searchDesigns(@Param("searchTerm") String searchTerm);
    
    /**
     * Count designs by category
     */
    @Query("SELECT d.category, COUNT(d) FROM CardDesign d WHERE d.active = true " +
           "AND d.category IS NOT NULL GROUP BY d.category")
    List<Object[]> countDesignsByCategory();
    
    /**
     * Count premium vs free designs
     */
    @Query("SELECT d.isPremium, COUNT(d) FROM CardDesign d WHERE d.active = true GROUP BY d.isPremium")
    List<Object[]> countDesignsByPremiumStatus();
}