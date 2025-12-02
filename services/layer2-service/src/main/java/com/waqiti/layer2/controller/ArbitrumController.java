package com.waqiti.layer2.controller;

import com.waqiti.layer2.arbitrum.ArbitrumService;
import com.waqiti.layer2.arbitrum.ArbitrumStats;
import com.waqiti.layer2.model.Layer2TransactionResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.web3j.protocol.core.methods.response.TransactionReceipt;
import org.web3j.utils.Convert;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Optional;

/**
 * REST API for Arbitrum Layer 2 operations
 */
@RestController
@RequestMapping("/api/v1/arbitrum")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Arbitrum", description = "Arbitrum Layer 2 integration endpoints")
public class ArbitrumController {

    private final ArbitrumService arbitrumService;

    @GetMapping("/stats")
    @Operation(summary = "Get Arbitrum network statistics")
    public ResponseEntity<ArbitrumStats> getStatistics() {
        ArbitrumStats stats = arbitrumService.getStatistics();
        return ResponseEntity.ok(stats);
    }

    @GetMapping("/health")
    @Operation(summary = "Check Arbitrum connection health")
    public ResponseEntity<HealthResponse> healthCheck() {
        boolean healthy = arbitrumService.isHealthy();
        return ResponseEntity.ok(new HealthResponse(healthy,
            healthy ? "Arbitrum connection is healthy" : "Arbitrum connection is down"));
    }

    @GetMapping("/balance/{address}")
    @Operation(summary = "Get account balance on Arbitrum")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<BalanceResponse> getBalance(@PathVariable String address) {
        BigInteger balanceWei = arbitrumService.getBalance(address);
        BigDecimal balanceEth = Convert.fromWei(balanceWei.toString(), Convert.Unit.ETHER);

        return ResponseEntity.ok(BalanceResponse.builder()
            .address(address)
            .balanceWei(balanceWei.toString())
            .balanceEth(balanceEth)
            .build());
    }

    @PostMapping("/transaction")
    @Operation(summary = "Send transaction on Arbitrum")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<Layer2TransactionResult> sendTransaction(@RequestBody TransactionRequest request) {
        BigInteger amountWei = Convert.toWei(request.getAmountEth(), Convert.Unit.ETHER).toBigInteger();

        Layer2TransactionResult result = arbitrumService.sendTransaction(
            request.getFromAddress(),
            request.getToAddress(),
            amountWei
        );

        return ResponseEntity.ok(result);
    }

    @GetMapping("/transaction/{txHash}")
    @Operation(summary = "Get transaction receipt from Arbitrum")
    public ResponseEntity<?> getTransactionReceipt(@PathVariable String txHash) {
        Optional<TransactionReceipt> receipt = arbitrumService.getTransactionReceipt(txHash);

        return receipt.map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/block-number")
    @Operation(summary = "Get current Arbitrum block number")
    public ResponseEntity<BlockNumberResponse> getBlockNumber() {
        BigInteger blockNumber = arbitrumService.getCurrentBlockNumber();
        return ResponseEntity.ok(new BlockNumberResponse(blockNumber));
    }

    // DTOs
    record HealthResponse(boolean healthy, String message) {}
    record BlockNumberResponse(BigInteger blockNumber) {}

    @lombok.Data
    @lombok.Builder
    static class BalanceResponse {
        private String address;
        private String balanceWei;
        private BigDecimal balanceEth;
    }

    @lombok.Data
    static class TransactionRequest {
        private String fromAddress;
        private String toAddress;
        private BigDecimal amountEth;
    }
}
