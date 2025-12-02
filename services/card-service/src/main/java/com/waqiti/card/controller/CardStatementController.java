package com.waqiti.card.controller;

import com.waqiti.card.dto.CardStatementResponse;
import com.waqiti.card.service.CardStatementService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * CardStatementController - REST API for statement management
 *
 * @author Waqiti Engineering Team
 * @version 2.0 (Consolidated)
 * @since 2025-11-09
 */
@RestController
@RequestMapping("/api/v1/statements")
@RequiredArgsConstructor
@Slf4j
public class CardStatementController {

    private final CardStatementService statementService;

    @PostMapping("/generate")
    @PreAuthorize("hasAnyRole('ADMIN', 'SYSTEM')")
    public ResponseEntity<CardStatementResponse> generateStatement(
        @RequestParam UUID cardId,
        @RequestParam int year,
        @RequestParam int month) {

        log.info("REST: Generate statement for card: {} - {}/{}", cardId, year, month);
        CardStatementResponse response = statementService.generateStatement(cardId, year, month);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{statementId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SYSTEM') or @cardOwnershipValidator.isStatementOwner(#statementId)")
    public ResponseEntity<CardStatementResponse> getStatementById(@PathVariable String statementId) {
        log.info("REST: Get statement: {}", statementId);
        CardStatementResponse response = statementService.getStatementById(statementId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/card/{cardId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SYSTEM') or @cardOwnershipValidator.isCardOwner(#cardId)")
    public ResponseEntity<Page<CardStatementResponse>> getStatementsByCardId(
        @PathVariable UUID cardId,
        @PageableDefault(size = 12, sort = "statementDate", direction = Sort.Direction.DESC) Pageable pageable) {

        log.info("REST: Get statements for card: {}", cardId);
        Page<CardStatementResponse> statements = statementService.getStatementsByCardId(cardId, pageable);
        return ResponseEntity.ok(statements);
    }

    @GetMapping("/card/{cardId}/current")
    @PreAuthorize("hasAnyRole('ADMIN', 'SYSTEM') or @cardOwnershipValidator.isCardOwner(#cardId)")
    public ResponseEntity<CardStatementResponse> getCurrentStatement(@PathVariable UUID cardId) {
        log.info("REST: Get current statement for card: {}", cardId);
        CardStatementResponse response = statementService.getCurrentStatement(cardId);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{statementId}/finalize")
    @PreAuthorize("hasAnyRole('ADMIN', 'SYSTEM')")
    public ResponseEntity<CardStatementResponse> finalizeStatement(@PathVariable String statementId) {
        log.info("REST: Finalize statement: {}", statementId);
        CardStatementResponse response = statementService.finalizeStatement(statementId);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{statementId}/payment")
    @PreAuthorize("hasAnyRole('ADMIN', 'SYSTEM') or @cardOwnershipValidator.isStatementOwner(#statementId)")
    public ResponseEntity<CardStatementResponse> recordPayment(
        @PathVariable String statementId,
        @RequestParam BigDecimal amount,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate paymentDate) {

        log.info("REST: Record payment for statement: {} - Amount: {}", statementId, amount);
        CardStatementResponse response = statementService.recordPayment(statementId, amount, paymentDate);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/overdue")
    @PreAuthorize("hasAnyRole('ADMIN', 'SYSTEM')")
    public ResponseEntity<List<CardStatementResponse>> getOverdueStatements() {
        log.info("REST: Get overdue statements");
        List<CardStatementResponse> statements = statementService.getOverdueStatements();
        return ResponseEntity.ok(statements);
    }

    @PostMapping("/{statementId}/send-email")
    @PreAuthorize("hasAnyRole('ADMIN', 'SYSTEM')")
    public ResponseEntity<Void> sendStatementEmail(@PathVariable String statementId) {
        log.info("REST: Send statement email: {}", statementId);
        statementService.sendStatementEmail(statementId);
        return ResponseEntity.noContent().build();
    }
}
