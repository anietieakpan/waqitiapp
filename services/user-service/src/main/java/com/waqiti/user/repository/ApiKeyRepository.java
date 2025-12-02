package com.waqiti.user.repository;

import com.waqiti.user.domain.ApiKey;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ApiKeyRepository extends JpaRepository<ApiKey, UUID> {

    List<ApiKey> findByUserId(String userId);

    List<ApiKey> findByUserIdAndStatus(String userId, String status);

    Optional<ApiKey> findByKeyHash(String keyHash);

    @Modifying
    @Query("UPDATE ApiKey ak SET ak.status = :status WHERE ak.userId = :userId")
    int updateStatusByUserId(@Param("userId") String userId, @Param("status") String status);

    @Modifying
    @Query("DELETE FROM ApiKey ak WHERE ak.userId = :userId")
    void deleteByUserId(@Param("userId") String userId);

    @Query("SELECT COUNT(ak) FROM ApiKey ak WHERE ak.userId = :userId AND ak.status = 'ACTIVE'")
    long countActiveByUserId(@Param("userId") String userId);
    
    /**
     * Find active API keys by user ID
     */
    @Query("SELECT ak FROM ApiKey ak WHERE ak.userId = :userId AND ak.status = 'ACTIVE'")
    List<ApiKey> findActiveByUserId(@Param("userId") String userId);
}
