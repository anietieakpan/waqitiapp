// File: services/user-service/src/main/java/com/waqiti/user/repository/MfaVerificationCodeRepository.java
package com.waqiti.user.repository;

import com.waqiti.user.domain.MfaMethod;
import com.waqiti.user.domain.MfaVerificationCode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface MfaVerificationCodeRepository extends JpaRepository<MfaVerificationCode, UUID> {

    /**
     * Functional interface for mocking in tests
     */
    @FunctionalInterface
    public interface FindLatestActiveCodeMethod {
        Optional<MfaVerificationCode> find(UUID userId, MfaMethod method, LocalDateTime now);
    }

    /**
     * Find the latest unused verification code
     */
    @Query("SELECT v FROM MfaVerificationCode v WHERE v.userId = :userId AND v.method = :method " +
            "AND v.used = false AND v.expiryDate > :now ORDER BY v.createdAt DESC")
    Optional<MfaVerificationCode> findLatestActiveCode(
            @Param("userId") UUID userId,
            @Param("method") MfaMethod method,
            @Param("now") LocalDateTime now);

    /**
     * Find verification codes by code for a given user and method
     */
    Optional<MfaVerificationCode> findByUserIdAndMethodAndCodeAndUsedFalseAndExpiryDateAfter(
            UUID userId, MfaMethod method, String code, LocalDateTime now);

    /**
     * Find expired but unused codes
     */
    List<MfaVerificationCode> findByUsedFalseAndExpiryDateBefore(LocalDateTime now);

    /**
     * Delete verification codes for a user
     */
    void deleteByUserId(UUID userId);


}