/**
 * Traditional Loan Controller
 * REST API endpoints for traditional loan management
 */
package com.waqiti.bnpl.controller;

import com.waqiti.bnpl.entity.LoanApplication;
import com.waqiti.bnpl.entity.LoanInstallment;
import com.waqiti.bnpl.entity.LoanTransaction;
import com.waqiti.bnpl.repository.LoanApplicationRepository;
import com.waqiti.bnpl.repository.LoanInstallmentRepository;
import com.waqiti.bnpl.repository.LoanTransactionRepository;
import com.waqiti.bnpl.service.TraditionalLoanService;
import com.waqiti.common.dto.LoanApplicationDTO;
import com.waqiti.bnpl.mapper.LoanApplicationMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/loans")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Traditional Loans", description = "Traditional loan management operations")
@SecurityRequirement(name = "bearer-token")
public class TraditionalLoanController {
    
    private final TraditionalLoanService loanService;
    private final LoanApplicationRepository loanApplicationRepository;
    private final LoanInstallmentRepository loanInstallmentRepository;
    private final LoanTransactionRepository loanTransactionRepository;
    private final LoanApplicationMapper loanApplicationMapper;
    
    @PostMapping("/applications")
    @Operation(summary = "Submit loan application", description = "Submit a new traditional loan application")
    @ApiResponse(responseCode = "201", description = "Loan application submitted successfully")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<LoanApplicationDTO> submitLoanApplication(
            @Valid @RequestBody LoanApplication application) {
        log.info("Submitting loan application for user: {}", application.getUserId());
        LoanApplication savedApplication = loanService.submitLoanApplication(application);
        LoanApplicationDTO dto = loanApplicationMapper.toDTO(savedApplication);
        return ResponseEntity.status(201).body(dto);
    }
    
    @GetMapping("/applications/{id}")
    @Operation(summary = "Get loan application", description = "Retrieve loan application by ID")
    @PreAuthorize("hasRole('USER') or hasRole('ADMIN')")
    public ResponseEntity<LoanApplicationDTO> getLoanApplication(
            @Parameter(description = "Loan application ID") @PathVariable UUID id) {
        return loanApplicationRepository.findById(id)
            .map(loanApplicationMapper::toDTO)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }
    
    @GetMapping("/applications")
    @Operation(summary = "Search loan applications", description = "Search and filter loan applications")
    @PreAuthorize("hasRole('ADMIN') or hasRole('LOAN_OFFICER')")
    public ResponseEntity<Page<LoanApplicationDTO>> searchLoanApplications(
            @Parameter(description = "Loan number filter") @RequestParam(required = false) String loanNumber,
            @Parameter(description = "User ID filter") @RequestParam(required = false) UUID userId,
            @Parameter(description = "Status filter") @RequestParam(required = false) LoanApplication.LoanStatus status,
            @Parameter(description = "Loan type filter") @RequestParam(required = false) LoanApplication.LoanType loanType,
            Pageable pageable) {
        
        Page<LoanApplication> applications = loanApplicationRepository.searchLoans(
            loanNumber, userId, status, loanType, pageable);
        Page<LoanApplicationDTO> dtoPage = applications.map(loanApplicationMapper::toDTO);
        return ResponseEntity.ok(dtoPage);
    }
    
    @GetMapping("/users/{userId}/applications")
    @Operation(summary = "Get user loan applications", description = "Get all loan applications for a specific user")
    @PreAuthorize("hasRole('USER') or hasRole('ADMIN')")
    public ResponseEntity<List<LoanApplicationDTO>> getUserLoanApplications(
            @Parameter(description = "User ID") @PathVariable UUID userId) {
        List<LoanApplication> applications = loanApplicationRepository.findByUserIdOrderByApplicationDateDesc(userId);
        List<LoanApplicationDTO> dtoList = loanApplicationMapper.toDTOList(applications);
        return ResponseEntity.ok(dtoList);
    }
    
    @PostMapping("/applications/{id}/approve")
    @Operation(summary = "Approve loan application", description = "Approve a pending loan application")
    @PreAuthorize("hasRole('LOAN_OFFICER') or hasRole('ADMIN')")
    public ResponseEntity<LoanApplicationDTO> approveLoan(
            @Parameter(description = "Loan application ID") @PathVariable UUID id,
            @Parameter(description = "Approver ID") @RequestParam UUID approverId,
            @Parameter(description = "Approval notes") @RequestParam(required = false) String notes) {
        
        LoanApplication approvedLoan = loanService.approveLoan(id, approverId, notes);
        LoanApplicationDTO dto = loanApplicationMapper.toDTO(approvedLoan);
        return ResponseEntity.ok(dto);
    }
    
    @PostMapping("/applications/{id}/reject")
    @Operation(summary = "Reject loan application", description = "Reject a pending loan application")
    @PreAuthorize("hasRole('LOAN_OFFICER') or hasRole('ADMIN')")
    public ResponseEntity<LoanApplicationDTO> rejectLoan(
            @Parameter(description = "Loan application ID") @PathVariable UUID id,
            @Parameter(description = "Rejection reason") @RequestParam String reason,
            @Parameter(description = "Reviewed by") @RequestParam UUID reviewedBy) {
        
        LoanApplication loan = loanApplicationRepository.findById(id)
            .orElse(null);
        if (loan == null) {
            return ResponseEntity.notFound().build();
        }
        
        loan.setStatus(LoanApplication.LoanStatus.REJECTED);
        loan.setDecision("REJECTED");
        loan.setDecisionReason(reason);
        loan.setDecisionBy(reviewedBy.toString());
        loan.setDecisionDate(java.time.LocalDateTime.now());
        
        LoanApplication rejectedLoan = loanApplicationRepository.save(loan);
        LoanApplicationDTO dto = loanApplicationMapper.toDTO(rejectedLoan);
        return ResponseEntity.ok(dto);
    }
    
    @PostMapping("/applications/{id}/disburse")
    @Operation(summary = "Disburse approved loan", description = "Disburse funds for an approved loan")
    @PreAuthorize("hasRole('LOAN_OFFICER') or hasRole('ADMIN')")
    public ResponseEntity<LoanApplicationDTO> disburseLoan(
            @Parameter(description = "Loan application ID") @PathVariable UUID id,
            @Parameter(description = "Disbursement amount") @RequestParam BigDecimal amount,
            @Parameter(description = "Disbursement method") @RequestParam String method) {
        
        LoanApplication disbursedLoan = loanService.disburseLoan(id, amount, method);
        LoanApplicationDTO dto = loanApplicationMapper.toDTO(disbursedLoan);
        return ResponseEntity.ok(dto);
    }
    
    @GetMapping("/applications/{id}/installments")
    @Operation(summary = "Get loan installments", description = "Get repayment schedule for a loan")
    @PreAuthorize("hasRole('USER') or hasRole('ADMIN')")
    public ResponseEntity<List<LoanInstallment>> getLoanInstallments(
            @Parameter(description = "Loan application ID") @PathVariable UUID id) {
        
        LoanApplication loan = loanApplicationRepository.findById(id).orElse(null);
        if (loan == null) {
            return ResponseEntity.notFound().build();
        }
        
        List<LoanInstallment> installments = loanInstallmentRepository
            .findByLoanApplicationOrderByInstallmentNumber(loan);
        return ResponseEntity.ok(installments);
    }
    
    @PostMapping("/applications/{id}/payments")
    @Operation(summary = "Process loan repayment", description = "Process a repayment for a loan")
    @PreAuthorize("hasRole('USER') or hasRole('ADMIN')")
    public ResponseEntity<LoanTransaction> processRepayment(
            @Parameter(description = "Loan application ID") @PathVariable UUID id,
            @Parameter(description = "Payment amount") @RequestParam BigDecimal amount,
            @Parameter(description = "Payment method") @RequestParam String paymentMethod,
            @Parameter(description = "Payment reference") @RequestParam String paymentReference) {
        
        LoanTransaction transaction = loanService.processRepayment(id, amount, paymentMethod, paymentReference);
        return ResponseEntity.ok(transaction);
    }
    
    @GetMapping("/applications/{id}/transactions")
    @Operation(summary = "Get loan transactions", description = "Get all transactions for a loan")
    @PreAuthorize("hasRole('USER') or hasRole('ADMIN')")
    public ResponseEntity<List<LoanTransaction>> getLoanTransactions(
            @Parameter(description = "Loan application ID") @PathVariable UUID id) {
        
        LoanApplication loan = loanApplicationRepository.findById(id).orElse(null);
        if (loan == null) {
            return ResponseEntity.notFound().build();
        }
        
        List<LoanTransaction> transactions = loanTransactionRepository
            .findByLoanApplicationOrderByTransactionDateDesc(loan);
        return ResponseEntity.ok(transactions);
    }
    
    @GetMapping("/portfolio/summary")
    @Operation(summary = "Get portfolio summary", description = "Get loan portfolio summary statistics")
    @PreAuthorize("hasRole('ADMIN') or hasRole('LOAN_OFFICER')")
    public ResponseEntity<Object> getPortfolioSummary() {
        BigDecimal totalActive = loanApplicationRepository.getTotalActivePortfolio();
        BigDecimal totalOutstanding = loanApplicationRepository.getTotalOutstandingBalance();
        BigDecimal avgInterestRate = loanApplicationRepository.getAverageInterestRate();
        Long pendingCount = loanApplicationRepository.countByStatus(LoanApplication.LoanStatus.PENDING);
        Long activeCount = loanApplicationRepository.countByStatus(LoanApplication.LoanStatus.ACTIVE);
        
        return ResponseEntity.ok(new Object() {
            public final BigDecimal totalActivePortfolio = totalActive;
            public final BigDecimal totalOutstandingBalance = totalOutstanding;
            public final BigDecimal averageInterestRate = avgInterestRate;
            public final Long pendingApplications = pendingCount;
            public final Long activeLoans = activeCount;
        });
    }
    
    @GetMapping("/overdue")
    @Operation(summary = "Get overdue loans", description = "Get all loans with overdue payments")
    @PreAuthorize("hasRole('ADMIN') or hasRole('LOAN_OFFICER')")
    public ResponseEntity<List<LoanApplicationDTO>> getOverdueLoans() {
        List<LoanApplication> overdueLoans = loanApplicationRepository.findOverdueLoans();
        List<LoanApplicationDTO> dtoList = loanApplicationMapper.toDTOList(overdueLoans);
        return ResponseEntity.ok(dtoList);
    }
    
    @GetMapping("/installments/due")
    @Operation(summary = "Get due installments", description = "Get installments due today")
    @PreAuthorize("hasRole('ADMIN') or hasRole('LOAN_OFFICER')")
    public ResponseEntity<List<LoanInstallment>> getInstallmentsDueToday() {
        List<LoanInstallment> dueInstallments = loanInstallmentRepository.findInstallmentsDueToday();
        return ResponseEntity.ok(dueInstallments);
    }
    
    @GetMapping("/installments/upcoming")
    @Operation(summary = "Get upcoming installments", description = "Get installments due in next N days")
    @PreAuthorize("hasRole('ADMIN') or hasRole('LOAN_OFFICER')")
    public ResponseEntity<List<LoanInstallment>> getUpcomingInstallments(
            @Parameter(description = "Number of days ahead") @RequestParam(defaultValue = "7") int days) {
        
        LocalDate endDate = LocalDate.now().plusDays(days);
        List<LoanInstallment> upcomingInstallments = loanInstallmentRepository
            .findInstallmentsDueInNextDays(endDate);
        return ResponseEntity.ok(upcomingInstallments);
    }
    
    @GetMapping("/transactions/search")
    @Operation(summary = "Search transactions", description = "Search loan transactions")
    @PreAuthorize("hasRole('ADMIN') or hasRole('LOAN_OFFICER')")
    public ResponseEntity<Page<LoanTransaction>> searchTransactions(
            @Parameter(description = "Transaction reference") @RequestParam(required = false) String reference,
            @Parameter(description = "Loan ID") @RequestParam(required = false) UUID loanId,
            @Parameter(description = "Transaction type") @RequestParam(required = false) LoanTransaction.TransactionType type,
            @Parameter(description = "Transaction status") @RequestParam(required = false) LoanTransaction.TransactionStatus status,
            Pageable pageable) {
        
        Page<LoanTransaction> transactions = loanTransactionRepository.searchTransactions(
            reference, loanId, type, status, pageable);
        return ResponseEntity.ok(transactions);
    }
}