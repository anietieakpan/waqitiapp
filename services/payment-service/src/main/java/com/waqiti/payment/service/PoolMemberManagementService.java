package com.waqiti.payment.service;

import com.waqiti.payment.dto.*;
import com.waqiti.payment.entity.*;
import com.waqiti.payment.repository.*;
import com.waqiti.user.service.UserService;
import com.waqiti.notification.service.NotificationService;
import com.waqiti.common.exception.ValidationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Separate service for pool member management to avoid transactional self-invocation
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PoolMemberManagementService {

    private final PoolMemberRepository memberRepository;
    private final GroupPaymentPoolRepository poolRepository;
    private final UserService userService;
    private final NotificationService notificationService;

    /**
     * Add member to pool (separate transactional method)
     */
    @Transactional
    public PoolMember addMemberToPool(
            GroupPaymentPool pool, 
            String userId, 
            PoolMemberRole role,
            String invitedBy) {
        
        log.info("Adding member {} to pool {} with role {}", userId, pool.getPoolId(), role);
        
        // Check if already a member
        if (memberRepository.existsByPoolIdAndUserId(pool.getId(), userId)) {
            throw new ValidationException("User is already a member of this pool");
        }
        
        // Verify user exists
        userService.getUserById(userId);
        
        PoolMember member = PoolMember.builder()
            .pool(pool)
            .userId(userId)
            .role(role)
            .contributionAmount(BigDecimal.ZERO)
            .contributionCount(0)
            .lastContributionAt(null)
            .invitedBy(invitedBy)
            .joinedAt(Instant.now())
            .status(MemberStatus.ACTIVE)
            .build();
        
        member = memberRepository.save(member);
        
        // Update pool member count
        pool.setMemberCount(pool.getMemberCount() + 1);
        poolRepository.save(pool);
        
        // Send notification
        notificationService.sendPoolInvitationNotification(userId, pool.getName(), invitedBy);
        
        return member;
    }

    /**
     * Remove member from pool
     */
    @Transactional
    public void removeMemberFromPool(String poolId, String userId, String removedBy) {
        log.info("Removing member {} from pool {}", userId, poolId);
        
        GroupPaymentPool pool = poolRepository.findByPoolId(poolId)
            .orElseThrow(() -> new ValidationException("Pool not found"));
            
        PoolMember member = memberRepository.findByPoolIdAndUserId(pool.getId(), userId)
            .orElseThrow(() -> new ValidationException("Member not found in pool"));
        
        // Update member status instead of deleting for audit trail
        member.setStatus(MemberStatus.REMOVED);
        member.setRemovedAt(Instant.now());
        member.setRemovedBy(removedBy);
        memberRepository.save(member);
        
        // Update pool member count
        pool.setMemberCount(pool.getMemberCount() - 1);
        poolRepository.save(pool);
        
        // Send notification
        notificationService.sendPoolRemovalNotification(userId, pool.getName(), removedBy);
    }

    /**
     * Update member role
     */
    @Transactional
    public PoolMember updateMemberRole(String poolId, String userId, PoolMemberRole newRole, String updatedBy) {
        log.info("Updating member {} role to {} in pool {}", userId, newRole, poolId);
        
        GroupPaymentPool pool = poolRepository.findByPoolId(poolId)
            .orElseThrow(() -> new ValidationException("Pool not found"));
            
        PoolMember member = memberRepository.findByPoolIdAndUserId(pool.getId(), userId)
            .orElseThrow(() -> new ValidationException("Member not found in pool"));
        
        PoolMemberRole oldRole = member.getRole();
        member.setRole(newRole);
        member.setUpdatedAt(Instant.now());
        member.setUpdatedBy(updatedBy);
        
        member = memberRepository.save(member);
        
        // Send notification about role change
        notificationService.sendRoleChangeNotification(userId, pool.getName(), oldRole, newRole, updatedBy);
        
        return member;
    }
}