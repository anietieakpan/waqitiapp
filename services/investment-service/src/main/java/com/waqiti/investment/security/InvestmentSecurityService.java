package com.waqiti.investment.security;

import com.waqiti.investment.domain.AutoInvest;
import com.waqiti.investment.domain.InvestmentAccount;
import com.waqiti.investment.domain.InvestmentOrder;
import com.waqiti.investment.repository.AutoInvestRepository;
import com.waqiti.investment.repository.InvestmentAccountRepository;
import com.waqiti.investment.repository.InvestmentOrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * Security service for investment authorization checks
 */
@Service("investmentSecurityService")
@RequiredArgsConstructor
@Slf4j
public class InvestmentSecurityService {

    private final InvestmentAccountRepository accountRepository;
    private final InvestmentOrderRepository orderRepository;
    private final AutoInvestRepository autoInvestRepository;

    /**
     * Check if current user can access the investment account
     */
    public boolean canAccessAccount(Long accountId) {
        if (accountId == null) {
            return false;
        }

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return false;
        }

        String currentUserId = authentication.getName();
        
        Optional<InvestmentAccount> account = accountRepository.findById(accountId);
        if (account.isEmpty()) {
            log.warn("Investment account {} not found", accountId);
            return false;
        }

        // Check if the account belongs to the current user
        boolean hasAccess = account.get().getUserId().equals(currentUserId);
        
        if (!hasAccess) {
            log.warn("User {} attempted to access investment account {} without permission", 
                    currentUserId, accountId);
        }
        
        return hasAccess;
    }

    /**
     * Check if current user can access the order
     */
    public boolean canAccessOrder(Long orderId) {
        if (orderId == null) {
            return false;
        }

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return false;
        }

        Optional<InvestmentOrder> order = orderRepository.findById(orderId);
        if (order.isEmpty()) {
            log.warn("Investment order {} not found", orderId);
            return false;
        }

        // Check through the account
        return canAccessAccount(order.get().getAccount().getId());
    }

    /**
     * Check if current user can access the auto-invest plan
     */
    public boolean canAccessAutoInvestPlan(Long planId) {
        if (planId == null) {
            return false;
        }

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return false;
        }

        Optional<AutoInvest> plan = autoInvestRepository.findById(planId);
        if (plan.isEmpty()) {
            log.warn("Auto-invest plan {} not found", planId);
            return false;
        }

        // Check through the account
        return canAccessAccount(plan.get().getAccount().getId());
    }

    /**
     * Check if current user has admin role
     */
    public boolean isAdmin() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return false;
        }

        return authentication.getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals("ROLE_ADMIN"));
    }

    /**
     * Get current authenticated user ID
     */
    public String getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            log.error("SECURITY VIOLATION: Attempt to access investment service without valid authentication");
            throw new SecurityException("User not authenticated - cannot access investment services");
        }
        return authentication.getName();
    }
}