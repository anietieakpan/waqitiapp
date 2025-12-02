package com.waqiti.crypto.repository;

import com.waqiti.crypto.entity.HighRiskAddress;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for managing high-risk cryptocurrency addresses
 */
@Repository
public interface HighRiskAddressRepository extends JpaRepository<HighRiskAddress, Long> {
    
    /**
     * Find a high-risk address by address string
     */
    Optional<HighRiskAddress> findByAddress(String address);
    
    /**
     * Find all addresses by category
     */
    List<HighRiskAddress> findByCategory(String category);
    
    /**
     * Find all active high-risk addresses
     */
    List<HighRiskAddress> findByActiveTrue();
    
    /**
     * Check if an address exists and is active
     */
    boolean existsByAddressAndActiveTrue(String address);
    
    /**
     * Find addresses by risk level
     */
    @Query("SELECT h FROM HighRiskAddress h WHERE h.riskLevel >= :minRiskLevel AND h.active = true")
    List<HighRiskAddress> findByMinimumRiskLevel(@Param("minRiskLevel") Integer minRiskLevel);
    
    /**
     * Find recently added addresses
     */
    @Query("SELECT h FROM HighRiskAddress h WHERE h.createdAt >= CURRENT_TIMESTAMP - INTERVAL '7' DAY ORDER BY h.createdAt DESC")
    List<HighRiskAddress> findRecentlyAdded();
    
    /**
     * Check if address is high risk
     */
    default boolean isHighRiskAddress(String address) {
        return existsByAddressAndActiveTrue(address);
    }
    
    /**
     * Get address risk information
     */
    default HighRiskAddress getAddressRiskInfo(String address) {
        return findByAddress(address).orElse(null);
    }
}