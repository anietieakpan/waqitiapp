/**
 * CRITICAL SECURITY FIX - LoanApplicationMapper
 * Secure mapping between Entity and DTO
 * Implements data masking and authorization-based field filtering
 */
package com.waqiti.bnpl.mapper;

import com.waqiti.common.dto.LoanApplicationDTO;
import com.waqiti.bnpl.entity.LoanApplication;
import org.springframework.stereotype.Component;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.Authentication;

import java.util.List;
import java.util.stream.Collectors;

@Component
public class LoanApplicationMapper {
    
    /**
     * Convert Entity to DTO with security-aware field mapping
     */
    public LoanApplicationDTO toDTO(LoanApplication entity) {
        if (entity == null) {
            return null;
        }
        
        boolean isAuthorized = isUserAuthorizedForSensitiveData();
        
        LoanApplicationDTO.LoanApplicationDTOBuilder builder = LoanApplicationDTO.builder()
            .id(entity.getId())
            .createdAt(entity.getCreatedAt())
            .updatedAt(entity.getUpdatedAt())
            .loanNumber(entity.getLoanNumber())
            .loanType(entity.getLoanType() != null ? entity.getLoanType().name() : null)
            .status(entity.getStatus() != null ? entity.getStatus().name() : null)
            .requestedAmount(entity.getRequestedAmount())
            .approvedAmount(entity.getApprovedAmount())
            .disbursedAmount(entity.getDisbursedAmount())
            .outstandingBalance(entity.getOutstandingBalance())
            .currency(entity.getCurrency())
            .interestRate(entity.getInterestRate())
            .interestType(entity.getInterestType() != null ? entity.getInterestType().name() : null)
            .loanTermMonths(entity.getLoanTermMonths())
            .repaymentFrequency(entity.getRepaymentFrequency() != null ? entity.getRepaymentFrequency().name() : null)
            .monthlyPayment(entity.getMonthlyPayment())
            .totalInterest(entity.getTotalInterest())
            .totalRepayment(entity.getTotalRepayment())
            .applicationDate(entity.getApplicationDate())
            .approvalDate(entity.getApprovalDate())
            .disbursementDate(entity.getDisbursementDate())
            .firstPaymentDate(entity.getFirstPaymentDate())
            .maturityDate(entity.getMaturityDate())
            .purpose(entity.getPurpose())
            .riskGrade(entity.getRiskGrade())
            .decision(entity.getDecision())
            .decisionReason(entity.getDecisionReason())
            .decisionDate(entity.getDecisionDate())
            .loanOfficerId(entity.getLoanOfficerId())
            .branchId(entity.getBranchId())
            .productId(entity.getProductId());
        
        // CRITICAL SECURITY: Mask sensitive financial data
        if (isAuthorized) {
            // Authorized users get masked but readable sensitive data
            builder.maskedCreditScore(maskCreditScore(entity.getCreditScore(), true))
                  .maskedAnnualIncome(maskAnnualIncome(entity.getAnnualIncome(), true))
                  .maskedDebtRatio(maskDebtRatio(entity.getDebtToIncomeRatio(), true));
        } else {
            // Unauthorized users get fully masked data
            builder.maskedCreditScore("***")
                  .maskedAnnualIncome("***.**")
                  .maskedDebtRatio("*.**");
        }
        
        return builder.build();
    }
    
    /**
     * Convert DTO to Entity for updates (limited fields)
     */
    public LoanApplication updateEntityFromDTO(LoanApplication entity, LoanApplicationDTO dto) {
        if (entity == null || dto == null) {
            return entity;
        }
        
        // Only allow updates to specific non-sensitive fields
        if (dto.getPurpose() != null) {
            entity.setPurpose(dto.getPurpose());
        }
        if (dto.getDecision() != null) {
            entity.setDecision(dto.getDecision());
        }
        if (dto.getDecisionReason() != null) {
            entity.setDecisionReason(dto.getDecisionReason());
        }
        if (dto.getDecisionDate() != null) {
            entity.setDecisionDate(dto.getDecisionDate());
        }
        
        // CRITICAL: NEVER allow DTO to update sensitive fields like:
        // - creditScore, annualIncome, debtToIncomeRatio
        // - Financial amounts (must go through proper business logic)
        
        return entity;
    }
    
    /**
     * Convert list of entities to DTOs
     */
    public List<LoanApplicationDTO> toDTOList(List<LoanApplication> entities) {
        if (entities == null) {
            return null;
        }
        return entities.stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }
    
    /**
     * Check if current user is authorized to view sensitive financial data
     */
    private boolean isUserAuthorizedForSensitiveData() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return false;
        }
        
        return auth.getAuthorities().stream()
                .anyMatch(authority -> 
                    authority.getAuthority().equals("ROLE_ADMIN") ||
                    authority.getAuthority().equals("ROLE_LOAN_OFFICER") ||
                    authority.getAuthority().equals("ROLE_FINANCIAL_ANALYST")
                );
    }
    
    /**
     * Mask credit score based on authorization level
     */
    private String maskCreditScore(Integer score, boolean isAuthorized) {
        if (score == null) return null;
        
        if (isAuthorized) {
            // Show range for authorized users: 750-800
            int range = (score / 50) * 50;
            return range + "-" + (range + 50);
        }
        return "***";
    }
    
    /**
     * Mask annual income based on authorization level
     */
    private String maskAnnualIncome(java.math.BigDecimal income, boolean isAuthorized) {
        if (income == null) return null;
        
        if (isAuthorized) {
            // Show income bracket for authorized users: $75K-$100K
            int bracket = income.intValue() / 25000 * 25000;
            return "$" + (bracket / 1000) + "K-$" + ((bracket + 25000) / 1000) + "K";
        }
        return "$***K";
    }
    
    /**
     * Mask debt-to-income ratio based on authorization level
     */
    private String maskDebtRatio(java.math.BigDecimal ratio, boolean isAuthorized) {
        if (ratio == null) return null;
        
        if (isAuthorized) {
            // Show category for authorized users: LOW, MEDIUM, HIGH
            if (ratio.compareTo(java.math.BigDecimal.valueOf(0.30)) <= 0) {
                return "LOW";
            } else if (ratio.compareTo(java.math.BigDecimal.valueOf(0.45)) <= 0) {
                return "MEDIUM";
            } else {
                return "HIGH";
            }
        }
        return "***";
    }
}