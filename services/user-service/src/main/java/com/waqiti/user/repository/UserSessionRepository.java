package com.waqiti.user.repository;

import com.waqiti.user.domain.UserSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserSessionRepository extends JpaRepository<UserSession, UUID> {

    List<UserSession> findByUserId(String userId);

    List<UserSession> findByUserIdAndStatus(String userId, String status);

    Optional<UserSession> findBySessionToken(String sessionToken);

    @Modifying
    @Query("UPDATE UserSession s SET s.status = :status WHERE s.userId = :userId")
    int updateStatusByUserId(@Param("userId") String userId, @Param("status") String status);

    @Modifying
    @Query("DELETE FROM UserSession s WHERE s.userId = :userId")
    void deleteByUserId(@Param("userId") String userId);

    @Query("SELECT COUNT(s) FROM UserSession s WHERE s.userId = :userId AND s.status = 'ACTIVE'")
    long countActiveSessionsByUserId(@Param("userId") String userId);

    List<UserSession> findByExpiresAtBefore(LocalDateTime dateTime);
    
    /**
     * Find active sessions by user ID
     */
    @Query("SELECT s FROM UserSession s WHERE s.userId = :userId AND s.status = 'ACTIVE'")
    List<UserSession> findActiveByUserId(@Param("userId") String userId);
}
