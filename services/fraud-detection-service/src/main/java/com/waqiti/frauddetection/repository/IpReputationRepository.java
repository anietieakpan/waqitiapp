package com.waqiti.frauddetection.repository;

import com.waqiti.frauddetection.entity.IpReputation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for IP address reputation tracking
 * Maintains blacklists, whitelists, and reputation scores for IP addresses
 */
@Repository
public interface IpReputationRepository extends JpaRepository<IpReputation, UUID> {

    /**
     * Find reputation record by IP address
     * Uses unique index: idx_ip_reputation_ip_address
     */
    Optional<IpReputation> findByIpAddress(String ipAddress);

    /**
     * Find blacklisted IP addresses
     */
    @Query("SELECT ir FROM IpReputation ir WHERE ir.blacklisted = true")
    List<IpReputation> findBlacklisted();

    /**
     * Find IPs with reputation score below threshold
     * Uses index: idx_ip_reputation_score
     */
    @Query("SELECT ir FROM IpReputation ir WHERE ir.reputationScore < :threshold " +
           "AND ir.blacklisted = false ORDER BY ir.reputationScore ASC")
    List<IpReputation> findLowReputationIps(@Param("threshold") Double threshold);

    /**
     * Find IPs associated with VPN/proxy services
     */
    @Query("SELECT ir FROM IpReputation ir WHERE ir.isVpn = true OR ir.isProxy = true " +
           "OR ir.isTor = true")
    List<IpReputation> findVpnProxyTorIps();

    /**
     * Find IPs with high fraud incident count
     */
    @Query("SELECT ir FROM IpReputation ir WHERE ir.fraudIncidentCount >= :threshold " +
           "ORDER BY ir.fraudIncidentCount DESC")
    List<IpReputation> findHighFraudIps(@Param("threshold") Integer threshold);

    /**
     * Find IPs from specific country
     */
    @Query("SELECT ir FROM IpReputation ir WHERE ir.country = :country")
    List<IpReputation> findByCountry(@Param("country") String country);

    /**
     * Find recently updated IP reputations
     */
    @Query("SELECT ir FROM IpReputation ir WHERE ir.lastUpdated >= :since " +
           "ORDER BY ir.lastUpdated DESC")
    List<IpReputation> findRecentlyUpdated(@Param("since") LocalDateTime since);

    /**
     * Count blacklisted IPs
     */
    @Query("SELECT COUNT(ir) FROM IpReputation ir WHERE ir.blacklisted = true")
    long countBlacklisted();

    /**
     * Find IPs with failed login attempts above threshold
     */
    @Query("SELECT ir FROM IpReputation ir WHERE ir.failedLoginAttempts >= :threshold " +
           "AND ir.lastFailedLogin >= :since ORDER BY ir.failedLoginAttempts DESC")
    List<IpReputation> findHighFailedLoginIps(
            @Param("threshold") Integer threshold,
            @Param("since") LocalDateTime since);

    /**
     * Check if IP is in whitelist
     */
    @Query("SELECT CASE WHEN COUNT(ir) > 0 THEN true ELSE false END " +
           "FROM IpReputation ir WHERE ir.ipAddress = :ipAddress AND ir.whitelisted = true")
    boolean isWhitelisted(@Param("ipAddress") String ipAddress);

    /**
     * Check if IP is blacklisted
     */
    @Query("SELECT CASE WHEN COUNT(ir) > 0 THEN true ELSE false END " +
           "FROM IpReputation ir WHERE ir.ipAddress = :ipAddress AND ir.blacklisted = true")
    boolean isBlacklisted(@Param("ipAddress") String ipAddress);
}
