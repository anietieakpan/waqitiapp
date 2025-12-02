package com.waqiti.billpayment.repository;

import com.waqiti.billpayment.entity.Biller;
import com.waqiti.billpayment.entity.BillerStatus;
import com.waqiti.billpayment.entity.BillCategory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for Biller entity operations
 */
@Repository
public interface BillerRepository extends JpaRepository<Biller, UUID> {

    /**
     * Find biller by external ID
     */
    Optional<Biller> findByExternalBillerId(String externalBillerId);

    /**
     * Find billers by status
     */
    List<Biller> findByStatus(BillerStatus status);

    /**
     * Find billers by category
     */
    Page<Biller> findByCategory(BillCategory category, Pageable pageable);

    /**
     * Find billers by category and status
     */
    List<Biller> findByCategoryAndStatus(BillCategory category, BillerStatus status);

    /**
     * Search billers by name (case-insensitive)
     */
    @Query("SELECT b FROM Biller b WHERE LOWER(b.name) LIKE LOWER(CONCAT('%', :searchTerm, '%')) " +
           "OR LOWER(b.displayName) LIKE LOWER(CONCAT('%', :searchTerm, '%'))")
    Page<Biller> searchByName(@Param("searchTerm") String searchTerm, Pageable pageable);

    /**
     * Find active billers
     */
    @Query("SELECT b FROM Biller b WHERE b.status = 'ACTIVE' AND b.deletedAt IS NULL")
    Page<Biller> findActiveBillers(Pageable pageable);

    /**
     * Find billers supporting specific feature
     */
    @Query("SELECT b FROM Biller b WHERE b.status = 'ACTIVE' " +
           "AND ((:feature = 'AUTO_PAY' AND b.supportsAutoPay = true) " +
           "OR (:feature = 'DIRECT_PAYMENT' AND b.supportsDirectPayment = true) " +
           "OR (:feature = 'BILL_IMPORT' AND b.supportsBillImport = true) " +
           "OR (:feature = 'EBILL' AND b.supportsEbill = true))")
    List<Biller> findBySupportedFeature(@Param("feature") String feature);

    /**
     * Find billers by location
     */
    List<Biller> findByCountryCodeAndStateCode(String countryCode, String stateCode);

    /**
     * Find billers by country
     */
    List<Biller> findByCountryCode(String countryCode);

    /**
     * Count billers by status
     */
    long countByStatus(BillerStatus status);

    /**
     * Check if biller exists by name
     */
    boolean existsByNameIgnoreCase(String name);

    /**
     * Find most popular billers (by number of connections)
     */
    @Query("SELECT b FROM Biller b " +
           "LEFT JOIN BillerConnection bc ON b.id = bc.billerId " +
           "WHERE b.status = 'ACTIVE' " +
           "GROUP BY b.id " +
           "ORDER BY COUNT(bc.id) DESC")
    List<Biller> findMostPopularBillers(Pageable pageable);

    /**
     * Soft delete biller
     */
    @Query("UPDATE Biller b SET b.deletedAt = :now WHERE b.id = :id")
    void softDelete(@Param("id") UUID id, @Param("now") LocalDateTime now);

    /**
     * Find biller by name
     */
    Optional<Biller> findByName(String name);

    /**
     * Check if user has connection to biller
     */
    @Query("SELECT CASE WHEN COUNT(bc) > 0 THEN true ELSE false END FROM BillerConnection bc " +
           "WHERE bc.userId = :userId AND bc.billerId = :billerId AND bc.isActive = true")
    boolean existsConnection(@Param("userId") String userId, @Param("billerId") UUID billerId);

    /**
     * Find billers by category and country
     */
    @Query("SELECT b FROM Biller b WHERE b.category = :category AND b.countryCode = :country " +
           "AND b.status = 'ACTIVE' AND b.deletedAt IS NULL")
    Page<Biller> findByCategoryAndCountry(
            @Param("category") String category,
            @Param("country") String country,
            Pageable pageable);

    /**
     * Find billers by category string
     */
    @Query("SELECT b FROM Biller b WHERE UPPER(b.category) = UPPER(:category) " +
           "AND b.status = 'ACTIVE' AND b.deletedAt IS NULL")
    Page<Biller> findByCategory(@Param("category") String category, Pageable pageable);

    /**
     * Find billers by country string
     */
    @Query("SELECT b FROM Biller b WHERE b.countryCode = :country " +
           "AND b.status = 'ACTIVE' AND b.deletedAt IS NULL")
    Page<Biller> findByCountry(@Param("country") String country, Pageable pageable);

    /**
     * Search billers by name or category
     */
    @Query("SELECT b FROM Biller b WHERE " +
           "(LOWER(b.name) LIKE LOWER(CONCAT('%', :query, '%')) " +
           "OR LOWER(b.displayName) LIKE LOWER(CONCAT('%', :query, '%')) " +
           "OR LOWER(b.category) LIKE LOWER(CONCAT('%', :query, '%'))) " +
           "AND b.status = 'ACTIVE' AND b.deletedAt IS NULL " +
           "ORDER BY b.name")
    List<Biller> searchByNameOrCategory(@Param("query") String query, Pageable pageable);
}
