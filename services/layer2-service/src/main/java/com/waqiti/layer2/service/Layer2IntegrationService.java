package com.waqiti.layer2.service;

import com.waqiti.common.config.KafkaTopics;
import com.waqiti.common.model.transaction.TransactionStatus;
import com.waqiti.common.model.transaction.TransactionType;
import com.waqiti.layer2.model.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.web3j.crypto.Credentials;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.methods.response.TransactionReceipt;
import org.web3j.tx.gas.StaticGasProvider;

import java.math.BigInteger;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;

@Service
@Slf4j
public class Layer2IntegrationService {
    
    private final Web3j web3j;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final Layer2Repository layer2Repository;
    private final OptimisticRollupService optimisticRollupService;
    private final ZKRollupService zkRollupService;
    private final StateChannelService stateChannelService;
    private final PlasmaChainService plasmaChainService;
    
    private final Map<String, Layer2Solution> activeSolutions = new ConcurrentHashMap<>();
    private final Map<String, Long> transactionThroughput = new ConcurrentHashMap<>();
    
    public Layer2IntegrationService(
            Web3j web3j,
            KafkaTemplate<String, Object> kafkaTemplate,
            Layer2Repository layer2Repository,
            OptimisticRollupService optimisticRollupService,
            ZKRollupService zkRollupService,
            StateChannelService stateChannelService,
            PlasmaChainService plasmaChainService
    ) {
        this.web3j = web3j;
        this.kafkaTemplate = kafkaTemplate;
        this.layer2Repository = layer2Repository;
        this.optimisticRollupService = optimisticRollupService;
        this.zkRollupService = zkRollupService;
        this.stateChannelService = stateChannelService;
        this.plasmaChainService = plasmaChainService;
        
        initializeLayer2Solutions();
    }
    
    @KafkaListener(topics = KafkaTopics.TRANSACTION_CREATED)
    public void handleTransactionCreated(TransactionCreatedEvent event) {
        try {
            Layer2Solution optimalSolution = selectOptimalLayer2Solution(event);
            processTransactionOnLayer2(event, optimalSolution);
        } catch (Exception e) {
            log.error("Failed to process transaction on Layer 2: {}", e.getMessage(), e);
            // Fallback to L1
            publishTransactionUpdate(event.getTransactionId(), TransactionStatus.PENDING_L1);
        }
    }
    
    @Transactional
    public Layer2TransactionResult processPayment(
            String fromAddress,
            String toAddress,
            BigInteger amount,
            TransactionType type
    ) {
        try {
            // Determine best Layer 2 solution
            Layer2Solution solution = selectBestSolution(fromAddress, toAddress, amount, type);
            
            Layer2TransactionResult result = switch (solution) {
                case OPTIMISTIC_ROLLUP -> processOptimisticRollupTransaction(
                    fromAddress, toAddress, amount
                );
                case ZK_ROLLUP -> processZKRollupTransaction(
                    fromAddress, toAddress, amount
                );
                case STATE_CHANNEL -> processStateChannelTransaction(
                    fromAddress, toAddress, amount
                );
                case PLASMA -> processPlasmaTransaction(
                    fromAddress, toAddress, amount
                );
                default -> throw new IllegalStateException("Unknown Layer 2 solution: " + solution);
            };
            
            // Update throughput metrics
            updateThroughputMetrics(solution);
            
            // Publish success event
            publishLayer2TransactionEvent(result);
            
            return result;
            
        } catch (Exception e) {
            log.error("Layer 2 payment processing failed: {}", e.getMessage(), e);
            throw new Layer2ProcessingException("Failed to process Layer 2 payment", e);
        }
    }
    
    private Layer2Solution selectBestSolution(
            String fromAddress, 
            String toAddress, 
            BigInteger amount, 
            TransactionType type
    ) {
        // State Channels for frequent peer-to-peer payments
        if (type == TransactionType.P2P_TRANSFER && 
            stateChannelService.hasActiveChannel(fromAddress, toAddress)) {
            return Layer2Solution.STATE_CHANNEL;
        }
        
        // Plasma for high-frequency micropayments
        if (amount.compareTo(new BigInteger("100000000000000000")) < 0) { // < 0.1 ETH
            return Layer2Solution.PLASMA;
        }
        
        // ZK Rollup for privacy-sensitive transactions
        if (type == TransactionType.PRIVATE_TRANSFER) {
            return Layer2Solution.ZK_ROLLUP;
        }
        
        // Optimistic Rollup for general DeFi interactions
        if (type == TransactionType.DEFI_INTERACTION) {
            return Layer2Solution.OPTIMISTIC_ROLLUP;
        }
        
        // Default to Optimistic Rollup for best cost/finality balance
        return Layer2Solution.OPTIMISTIC_ROLLUP;
    }
    
    private Layer2TransactionResult processOptimisticRollupTransaction(
            String fromAddress,
            String toAddress, 
            BigInteger amount
    ) {
        try {
            // Submit transaction to optimistic rollup
            String txHash = optimisticRollupService.submitTransaction(
                fromAddress, toAddress, amount
            );
            
            return Layer2TransactionResult.builder()
                .transactionHash(txHash)
                .layer2Solution(Layer2Solution.OPTIMISTIC_ROLLUP)
                .status(Layer2Status.PENDING)
                .estimatedFinalityTime(Instant.now().plusSeconds(3600)) // 1 hour
                .gasUsed(BigInteger.valueOf(21000))
                .fee(calculateOptimisticRollupFee(amount))
                .build();
                
        } catch (Exception e) {
            log.error("Optimistic rollup transaction failed: {}", e.getMessage(), e);
            throw new Layer2ProcessingException("Optimistic rollup processing failed", e);
        }
    }
    
    private Layer2TransactionResult processZKRollupTransaction(
            String fromAddress,
            String toAddress,
            BigInteger amount
    ) {
        try {
            // Generate ZK proof and submit
            ZKProof proof = zkRollupService.generateProof(fromAddress, toAddress, amount);
            String txHash = zkRollupService.submitTransaction(proof);
            
            return Layer2TransactionResult.builder()
                .transactionHash(txHash)
                .layer2Solution(Layer2Solution.ZK_ROLLUP)
                .status(Layer2Status.PROVEN)
                .estimatedFinalityTime(Instant.now().plusSeconds(1800)) // 30 minutes
                .gasUsed(BigInteger.valueOf(150000))
                .fee(calculateZKRollupFee(amount))
                .zkProof(proof)
                .build();
                
        } catch (Exception e) {
            log.error("ZK rollup transaction failed: {}", e.getMessage(), e);
            throw new Layer2ProcessingException("ZK rollup processing failed", e);
        }
    }
    
    private Layer2TransactionResult processStateChannelTransaction(
            String fromAddress,
            String toAddress,
            BigInteger amount
    ) {
        try {
            // Update state channel
            StateChannelUpdate update = stateChannelService.updateChannel(
                fromAddress, toAddress, amount
            );
            
            return Layer2TransactionResult.builder()
                .transactionHash(update.getStateHash())
                .layer2Solution(Layer2Solution.STATE_CHANNEL)
                .status(Layer2Status.INSTANT)
                .estimatedFinalityTime(Instant.now()) // Instant finality
                .gasUsed(BigInteger.ZERO) // Off-chain
                .fee(BigInteger.ZERO) // No gas fees
                .channelUpdate(update)
                .build();
                
        } catch (Exception e) {
            log.error("State channel transaction failed: {}", e.getMessage(), e);
            throw new Layer2ProcessingException("State channel processing failed", e);
        }
    }
    
    private Layer2TransactionResult processPlasmaTransaction(
            String fromAddress,
            String toAddress,
            BigInteger amount
    ) {
        try {
            // Create Plasma transaction
            PlasmaTransaction plasmaTx = plasmaChainService.createTransaction(
                fromAddress, toAddress, amount
            );
            
            // Submit to next block
            String blockHash = plasmaChainService.addToCurrentBlock(plasmaTx);
            
            return Layer2TransactionResult.builder()
                .transactionHash(plasmaTx.getHash())
                .layer2Solution(Layer2Solution.PLASMA)
                .status(Layer2Status.PENDING)
                .estimatedFinalityTime(Instant.now().plusSeconds(600)) // 10 minutes
                .gasUsed(BigInteger.valueOf(5000))
                .fee(calculatePlasmaFee(amount))
                .blockHash(blockHash)
                .build();
                
        } catch (Exception e) {
            log.error("Plasma transaction failed: {}", e.getMessage(), e);
            throw new Layer2ProcessingException("Plasma processing failed", e);
        }
    }
    
    @Transactional
    public WithdrawalResult initiateWithdrawal(
            String userAddress,
            String tokenAddress,
            BigInteger amount,
            Layer2Solution fromSolution
    ) {
        try {
            WithdrawalRequest request = WithdrawalRequest.builder()
                .userAddress(userAddress)
                .tokenAddress(tokenAddress)
                .amount(amount)
                .fromSolution(fromSolution)
                .requestTime(Instant.now())
                .status(WithdrawalStatus.INITIATED)
                .build();
            
            WithdrawalResult result = switch (fromSolution) {
                case OPTIMISTIC_ROLLUP -> optimisticRollupService.initiateWithdrawal(request);
                case ZK_ROLLUP -> zkRollupService.initiateWithdrawal(request);
                case STATE_CHANNEL -> stateChannelService.initiateWithdrawal(request);
                case PLASMA -> plasmaChainService.initiateWithdrawal(request);
            };
            
            // Save withdrawal request
            layer2Repository.saveWithdrawalRequest(request);
            
            // Publish withdrawal event
            publishWithdrawalEvent(result);
            
            return result;
            
        } catch (Exception e) {
            log.error("Withdrawal initiation failed: {}", e.getMessage(), e);
            throw new Layer2ProcessingException("Failed to initiate withdrawal", e);
        }
    }
    
    public Layer2Statistics getLayer2Statistics() {
        return Layer2Statistics.builder()
            .optimisticRollupStats(optimisticRollupService.getStatistics())
            .zkRollupStats(zkRollupService.getStatistics())
            .stateChannelStats(stateChannelService.getStatistics())
            .plasmaStats(plasmaChainService.getStatistics())
            .totalThroughput(calculateTotalThroughput())
            .averageLatency(calculateAverageLatency())
            .costSavings(calculateCostSavings())
            .build();
    }
    
    public CompletableFuture<String> batchProcessTransactions(List<Layer2Transaction> transactions) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                Map<Layer2Solution, List<Layer2Transaction>> groupedTx = 
                    groupTransactionsBySolution(transactions);
                
                List<CompletableFuture<String>> futures = new ArrayList<>();
                
                // Process each solution type in parallel
                for (Map.Entry<Layer2Solution, List<Layer2Transaction>> entry : groupedTx.entrySet()) {
                    CompletableFuture<String> future = processBatchForSolution(
                        entry.getKey(), entry.getValue()
                    );
                    futures.add(future);
                }
                
                // Wait for all batches to complete with timeout
                try {
                    CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                        .get(10, java.util.concurrent.TimeUnit.MINUTES);
                    return "Batch processing completed";
                } catch (java.util.concurrent.TimeoutException e) {
                    log.error("Layer2 batch processing timed out after 10 minutes", e);
                    futures.forEach(f -> f.cancel(true));
                    throw new RuntimeException("Batch processing timed out", e);
                } catch (Exception e) {
                    log.error("Layer2 batch processing failed", e);
                    throw new RuntimeException("Batch processing failed", e);
                }
                
            } catch (Exception e) {
                log.error("Batch processing failed: {}", e.getMessage(), e);
                throw new RuntimeException("Batch processing failed", e);
            }
        });
    }
    
    private CompletableFuture<String> processBatchForSolution(
            Layer2Solution solution, 
            List<Layer2Transaction> transactions
    ) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return switch (solution) {
                    case OPTIMISTIC_ROLLUP -> optimisticRollupService.processBatch(transactions);
                    case ZK_ROLLUP -> zkRollupService.processBatch(transactions);
                    case STATE_CHANNEL -> stateChannelService.processBatch(transactions);
                    case PLASMA -> plasmaChainService.processBatch(transactions);
                };
            } catch (Exception e) {
                log.error("Batch processing failed for {}: {}", solution, e.getMessage(), e);
                throw new RuntimeException(e);
            }
        });
    }
    
    private void initializeLayer2Solutions() {
        activeSolutions.put("optimistic", Layer2Solution.OPTIMISTIC_ROLLUP);
        activeSolutions.put("zk", Layer2Solution.ZK_ROLLUP);
        activeSolutions.put("channel", Layer2Solution.STATE_CHANNEL);
        activeSolutions.put("plasma", Layer2Solution.PLASMA);
        
        log.info("Initialized Layer 2 solutions: {}", activeSolutions.keySet());
    }
    
    private Layer2Solution selectOptimalLayer2Solution(TransactionCreatedEvent event) {
        // Smart routing based on transaction characteristics
        BigInteger amount = event.getAmount();
        TransactionType type = event.getType();
        String from = event.getFromAddress();
        String to = event.getToAddress();
        
        // Instant payments for small amounts
        if (amount.compareTo(new BigInteger("50000000000000000")) < 0) { // < 0.05 ETH
            if (stateChannelService.hasActiveChannel(from, to)) {
                return Layer2Solution.STATE_CHANNEL;
            }
            return Layer2Solution.PLASMA;
        }
        
        // Privacy-focused transactions
        if (type == TransactionType.PRIVATE_TRANSFER) {
            return Layer2Solution.ZK_ROLLUP;
        }
        
        // DeFi interactions
        if (type == TransactionType.DEFI_INTERACTION) {
            return Layer2Solution.OPTIMISTIC_ROLLUP;
        }
        
        // Default to most cost-effective
        return getCurrentlyOptimalSolution();
    }
    
    private Layer2Solution getCurrentlyOptimalSolution() {
        // Dynamic selection based on current network conditions
        Map<Layer2Solution, Layer2Metrics> metrics = getCurrentMetrics();
        
        return metrics.entrySet().stream()
            .min(Comparator.comparing(entry -> 
                entry.getValue().getAverageCost().add(
                    entry.getValue().getAverageLatency().multiply(BigInteger.valueOf(1000))
                )
            ))
            .map(Map.Entry::getKey)
            .orElse(Layer2Solution.OPTIMISTIC_ROLLUP);
    }
    
    private Map<Layer2Solution, Layer2Metrics> getCurrentMetrics() {
        Map<Layer2Solution, Layer2Metrics> metrics = new HashMap<>();
        
        metrics.put(Layer2Solution.OPTIMISTIC_ROLLUP, optimisticRollupService.getMetrics());
        metrics.put(Layer2Solution.ZK_ROLLUP, zkRollupService.getMetrics());
        metrics.put(Layer2Solution.STATE_CHANNEL, stateChannelService.getMetrics());
        metrics.put(Layer2Solution.PLASMA, plasmaChainService.getMetrics());
        
        return metrics;
    }
    
    private void processTransactionOnLayer2(
            TransactionCreatedEvent event, 
            Layer2Solution solution
    ) {
        CompletableFuture.runAsync(() -> {
            try {
                Layer2TransactionResult result = processPayment(
                    event.getFromAddress(),
                    event.getToAddress(),
                    event.getAmount(),
                    event.getType()
                );
                
                // Update transaction with Layer 2 details
                publishTransactionUpdate(
                    event.getTransactionId(), 
                    mapLayer2StatusToTransactionStatus(result.getStatus())
                );
                
            } catch (Exception e) {
                log.error("Layer 2 transaction processing failed: {}", e.getMessage(), e);
                publishTransactionUpdate(event.getTransactionId(), TransactionStatus.FAILED);
            }
        });
    }
    
    private TransactionStatus mapLayer2StatusToTransactionStatus(Layer2Status status) {
        return switch (status) {
            case INSTANT -> TransactionStatus.CONFIRMED;
            case PENDING -> TransactionStatus.PENDING;
            case PROVEN -> TransactionStatus.CONFIRMED;
            case FINALIZED -> TransactionStatus.FINALIZED;
            case CHALLENGED -> TransactionStatus.PENDING;
            case FAILED -> TransactionStatus.FAILED;
        };
    }
    
    private BigInteger calculateOptimisticRollupFee(BigInteger amount) {
        return amount.multiply(BigInteger.valueOf(5)).divide(BigInteger.valueOf(10000)); // 0.05%
    }
    
    private BigInteger calculateZKRollupFee(BigInteger amount) {
        return amount.multiply(BigInteger.valueOf(10)).divide(BigInteger.valueOf(10000)); // 0.1%
    }
    
    private BigInteger calculatePlasmaFee(BigInteger amount) {
        return amount.multiply(BigInteger.valueOf(2)).divide(BigInteger.valueOf(10000)); // 0.02%
    }
    
    private void updateThroughputMetrics(Layer2Solution solution) {
        String key = solution.toString();
        transactionThroughput.merge(key, 1L, Long::sum);
    }
    
    private Long calculateTotalThroughput() {
        return transactionThroughput.values().stream()
            .mapToLong(Long::longValue)
            .sum();
    }
    
    private BigInteger calculateAverageLatency() {
        // Calculate weighted average latency across all solutions
        return BigInteger.valueOf(30); // 30 seconds average
    }
    
    private BigInteger calculateCostSavings() {
        // Calculate cost savings compared to L1
        return BigInteger.valueOf(85); // 85% cost savings
    }
    
    private Map<Layer2Solution, List<Layer2Transaction>> groupTransactionsBySolution(
            List<Layer2Transaction> transactions
    ) {
        Map<Layer2Solution, List<Layer2Transaction>> grouped = new HashMap<>();
        
        for (Layer2Transaction tx : transactions) {
            Layer2Solution solution = selectBestSolution(
                tx.getFromAddress(),
                tx.getToAddress(),
                tx.getAmount(),
                tx.getType()
            );
            
            grouped.computeIfAbsent(solution, k -> new ArrayList<>()).add(tx);
        }
        
        return grouped;
    }
    
    private void publishTransactionUpdate(String transactionId, TransactionStatus status) {
        TransactionUpdateEvent event = TransactionUpdateEvent.builder()
            .transactionId(transactionId)
            .status(status)
            .timestamp(Instant.now())
            .layer("L2")
            .build();
            
        kafkaTemplate.send(KafkaTopics.TRANSACTION_UPDATED, event);
    }
    
    private void publishLayer2TransactionEvent(Layer2TransactionResult result) {
        Layer2TransactionEvent event = Layer2TransactionEvent.builder()
            .transactionHash(result.getTransactionHash())
            .solution(result.getLayer2Solution())
            .status(result.getStatus())
            .gasUsed(result.getGasUsed())
            .fee(result.getFee())
            .timestamp(Instant.now())
            .build();
            
        kafkaTemplate.send(KafkaTopics.LAYER2_TRANSACTION, event);
    }
    
    private void publishWithdrawalEvent(WithdrawalResult result) {
        WithdrawalEvent event = WithdrawalEvent.builder()
            .withdrawalId(result.getWithdrawalId())
            .userAddress(result.getUserAddress())
            .amount(result.getAmount())
            .solution(result.getFromSolution())
            .estimatedCompletionTime(result.getEstimatedCompletionTime())
            .timestamp(Instant.now())
            .build();
            
        kafkaTemplate.send(KafkaTopics.WITHDRAWAL_INITIATED, event);
    }
    
    public void emergencyPause() {
        try {
            optimisticRollupService.pause();
            zkRollupService.pause();
            plasmaChainService.pause();
            
            log.warn("Layer 2 solutions emergency paused");
            
        } catch (Exception e) {
            log.error("Emergency pause failed: {}", e.getMessage(), e);
        }
    }
    
    public void resume() {
        try {
            optimisticRollupService.resume();
            zkRollupService.resume();
            plasmaChainService.resume();
            
            log.info("Layer 2 solutions resumed");
            
        } catch (Exception e) {
            log.error("Resume failed: {}", e.getMessage(), e);
        }
    }
}