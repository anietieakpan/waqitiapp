package com.waqiti.user.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.UUID;

/**
 * Repository stub for AccessToken management
 */
@Repository
public interface AccessTokenRepository extends JpaRepository<AccessTokenStub, UUID> {

    @Modifying
    @Query(value = "DELETE FROM access_tokens WHERE user_id = :userId", nativeQuery = true)
    void deleteByUserId(@Param("userId") String userId);

    @Query(value = "SELECT COUNT(*) FROM access_tokens WHERE user_id = :userId AND status = 'ACTIVE'", nativeQuery = true)
    long countActiveByUserId(@Param("userId") String userId);
    
    /**
     * Revoke all access tokens for a user
     */
    @Modifying
    @Query(value = "UPDATE access_tokens SET status = 'REVOKED' WHERE user_id = :userId AND status = 'ACTIVE'", nativeQuery = true)
    int revokeAllByUserId(@Param("userId") String userId);
    
    /**
     * Revoke all refresh tokens for a user
     */
    @Modifying
    @Query(value = "UPDATE refresh_tokens SET status = 'REVOKED' WHERE user_id = :userId AND status = 'ACTIVE'", nativeQuery = true)
    int revokeAllRefreshTokensByUserId(@Param("userId") String userId);
}

