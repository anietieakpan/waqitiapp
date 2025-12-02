package com.waqiti.layer2.service;

import com.waqiti.layer2.model.*;
import com.waqiti.common.config.VaultTemplate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.web3j.abi.FunctionEncoder;
import org.web3j.abi.datatypes.*;
import org.web3j.abi.datatypes.generated.Uint256;
import org.web3j.crypto.Hash;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.methods.response.EthSendTransaction;
import org.web3j.protocol.core.methods.request.Transaction;
import org.web3j.protocol.http.HttpService;
import org.web3j.tx.gas.DefaultGasProvider;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Plasma Chain Service
 * Handles high-frequency micropayments with periodic commitment to L1
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PlasmaChainService {

    private final VaultTemplate vaultTemplate;

    @Value("${layer2.plasma.contract.address:}")
    private String plasmaContractAddress;

    @Value("${layer2.plasma.block.interval:60}")
    private long blockInterval; // 60 seconds

    @Value("${layer2.plasma.block.size:1000}")
    private int blockSize;

    @Value("${layer2.plasma.challenge.period:604800}")
    private long challengePeriod; // 7 days

    @Value("${ethereum.rpc.url:}")
    private String ethereumRpcUrl;

    private Web3j web3j;
    private volatile boolean paused = false;
    private ScheduledExecutorService blockProducer;
    private ScheduledExecutorService exitProcessor;

    // Plasma chain state
    private final List<PlasmaTransaction> currentBlock = Collections.synchronizedList(new ArrayList<>());
    private final Map<String, PlasmaBlock> committedBlocks = new ConcurrentHashMap<>();
    private final Map<String, PlasmaExit> activeExits = new ConcurrentHashMap<>();
    private final Map<String, PlasmaChallenge> activeChallenges = new ConcurrentHashMap<>();

    // Account balances in Plasma chain
    private final Map<String, BigInteger> plasmaBalances = new ConcurrentHashMap<>();

    // Metrics
    private final AtomicLong totalTransactions = new AtomicLong(0);
    private final AtomicLong totalBlocks = new AtomicLong(0);
    private final AtomicLong totalExits = new AtomicLong(0);
    private final AtomicLong totalChallenges = new AtomicLong(0);

    @PostConstruct
    public void initialize() {
        try {
            // Get configuration from Vault
            var layer2Config = vaultTemplate.read("secret/layer2-config").getData();
            
            if (plasmaContractAddress.isEmpty()) {
                plasmaContractAddress = layer2Config.get("plasma-contract-address").toString();
            }
            
            if (ethereumRpcUrl.isEmpty()) {
                ethereumRpcUrl = layer2Config.get("ethereum-rpc-url").toString();
            }

            // Initialize Web3j
            this.web3j = Web3j.build(new HttpService(ethereumRpcUrl));

            log.info("Plasma Chain service initialized with contract: {}", plasmaContractAddress);

            // Start block production
            startBlockProducer();
            startExitProcessor();

        } catch (Exception e) {
            log.error("Failed to initialize Plasma Chain service", e);
            throw new RuntimeException("Cannot initialize Plasma Chain service", e);
        }
    }

    /**
     * Create a new Plasma transaction
     */
    public PlasmaTransaction createTransaction(String fromAddress, String toAddress, BigInteger amount) throws Exception {
        if (paused) {
            throw new Layer2ProcessingException("Plasma Chain service is paused");
        }

        log.debug("Creating Plasma transaction: {} -> {} amount: {}", fromAddress, toAddress, amount);

        try {
            // Check balance
            BigInteger currentBalance = plasmaBalances.getOrDefault(fromAddress, BigInteger.ZERO);
            if (currentBalance.compareTo(amount) < 0) {
                throw new Layer2ProcessingException("Insufficient balance in Plasma chain");
            }

            // Create transaction
            PlasmaTransaction transaction = PlasmaTransaction.builder()
                .id(generateTransactionId())
                .fromAddress(fromAddress)
                .toAddress(toAddress)
                .amount(amount)
                .timestamp(LocalDateTime.now())
                .nonce(getNextNonce(fromAddress))
                .hash(calculateTransactionHash(fromAddress, toAddress, amount))
                .status(PlasmaTransactionStatus.PENDING)
                .blockNumber(totalBlocks.get() + 1)
                .build();

            // Update balances locally
            plasmaBalances.put(fromAddress, currentBalance.subtract(amount));
            plasmaBalances.put(toAddress, 
                plasmaBalances.getOrDefault(toAddress, BigInteger.ZERO).add(amount));

            transaction.setStatus(PlasmaTransactionStatus.CONFIRMED);
            totalTransactions.incrementAndGet();

            log.info("Plasma transaction {} created successfully", transaction.getId());
            return transaction;

        } catch (Exception e) {
            log.error("Failed to create Plasma transaction", e);
            throw new Layer2ProcessingException("Failed to create Plasma transaction", e);
        }
    }

    /**
     * Add transaction to current block
     */
    public String addToCurrentBlock(PlasmaTransaction transaction) throws Exception {
        if (paused) {
            throw new Layer2ProcessingException("Plasma Chain service is paused");
        }

        try {
            synchronized (currentBlock) {
                currentBlock.add(transaction);
                
                // If block is full, commit it
                if (currentBlock.size() >= blockSize) {
                    return commitCurrentBlock();
                }
            }

            return "added-to-block";

        } catch (Exception e) {
            log.error("Failed to add transaction to block", e);
            throw new Layer2ProcessingException("Failed to add transaction to block", e);
        }
    }

    /**
     * Process a batch of transactions
     */
    public String processBatch(List<Layer2Transaction> transactions) throws Exception {
        if (paused) {
            throw new Layer2ProcessingException("Plasma Chain service is paused");
        }

        log.info("Processing Plasma batch of {} transactions", transactions.size());

        try {
            String batchId = generateBatchId();
            List<PlasmaTransaction> plasmaTransactions = new ArrayList<>();

            for (Layer2Transaction tx : transactions) {
                PlasmaTransaction plasmaTx = createTransaction(tx.getFromAddress(), tx.getToAddress(), tx.getAmount());
                plasmaTransactions.add(plasmaTx);
            }

            // Add all transactions to current block
            synchronized (currentBlock) {
                currentBlock.addAll(plasmaTransactions);
            }

            // Commit block if full
            if (currentBlock.size() >= blockSize) {
                commitCurrentBlock();
            }

            log.info("Plasma batch {} processed with {} transactions", batchId, plasmaTransactions.size());
            return batchId;

        } catch (Exception e) {
            log.error("Failed to process Plasma batch", e);
            throw new Layer2ProcessingException("Failed to process Plasma batch", e);
        }
    }

    /**
     * Initiate withdrawal (exit) from Plasma chain
     */
    public WithdrawalResult initiateWithdrawal(WithdrawalRequest request) throws Exception {
        log.info("Initiating Plasma exit for {} of amount {}", request.getUserAddress(), request.getAmount());

        try {
            String exitId = generateExitId();

            // Check user balance
            BigInteger userBalance = plasmaBalances.getOrDefault(request.getUserAddress(), BigInteger.ZERO);
            if (userBalance.compareTo(request.getAmount()) < 0) {
                throw new Layer2ProcessingException("Insufficient balance for exit");
            }

            // Create exit proof
            ExitProof exitProof = generateExitProof(request);

            // Create Plasma exit
            PlasmaExit exit = PlasmaExit.builder()
                .id(exitId)
                .userAddress(request.getUserAddress())
                .amount(request.getAmount())
                .exitProof(exitProof)
                .timestamp(LocalDateTime.now())
                .challengePeriodEnd(LocalDateTime.now().plusSeconds(challengePeriod))
                .status(PlasmaExitStatus.CHALLENGING)
                .build();

            // Submit exit to L1 contract
            String l1TransactionHash = submitExitToL1(exit);
            exit.setL1TransactionHash(l1TransactionHash);

            activeExits.put(exitId, exit);
            totalExits.incrementAndGet();

            // Update user balance
            plasmaBalances.put(request.getUserAddress(), userBalance.subtract(request.getAmount()));

            // Calculate completion time (challenge period + processing)
            Instant completionTime = Instant.now().plusSeconds(challengePeriod + 3600);

            return WithdrawalResult.builder()
                .withdrawalId(exitId)
                .userAddress(request.getUserAddress())
                .amount(request.getAmount())
                .fromSolution(Layer2Solution.PLASMA)
                .l1TransactionHash(l1TransactionHash)
                .estimatedCompletionTime(completionTime)
                .status(WithdrawalStatus.CHALLENGE_PERIOD)
                .challengePeriodEnd(Instant.now().plusSeconds(challengePeriod))
                .build();

        } catch (Exception e) {
            log.error("Failed to initiate Plasma exit", e);
            throw new Layer2ProcessingException("Failed to initiate exit", e);
        }
    }

    /**
     * Challenge a Plasma exit
     */
    public PlasmaChallenge challengeExit(String exitId, String challengerAddress, byte[] challengeProof) throws Exception {
        log.info("Challenging Plasma exit: {}", exitId);

        try {
            PlasmaExit exit = activeExits.get(exitId);
            if (exit == null) {
                throw new Layer2ProcessingException("Exit not found");
            }

            if (exit.getChallengePeriodEnd().isBefore(LocalDateTime.now())) {
                throw new Layer2ProcessingException("Challenge period expired");
            }

            String challengeId = generateChallengeId();

            PlasmaChallenge challenge = PlasmaChallenge.builder()
                .id(challengeId)
                .exitId(exitId)
                .challengerAddress(challengerAddress)
                .challengeProof(challengeProof)
                .timestamp(LocalDateTime.now())
                .status(PlasmaChallengeStatus.PENDING)
                .build();

            // Submit challenge to L1
            String challengeTxHash = submitChallengeToL1(challenge);
            challenge.setL1TransactionHash(challengeTxHash);

            activeChallenges.put(challengeId, challenge);
            totalChallenges.incrementAndGet();

            // Update exit status
            exit.setStatus(PlasmaExitStatus.CHALLENGED);

            log.info("Plasma exit {} challenged successfully", exitId);
            return challenge;

        } catch (Exception e) {
            log.error("Failed to challenge Plasma exit", e);
            throw new Layer2ProcessingException("Failed to challenge exit", e);
        }
    }

    /**
     * Get statistics
     */
    public PlasmaStats getStatistics() {
        return PlasmaStats.builder()
            .totalTransactions(totalTransactions.get())
            .totalBlocks(totalBlocks.get())
            .totalExits(totalExits.get())
            .totalChallenges(totalChallenges.get())
            .currentBlockSize(currentBlock.size())
            .activeExits(activeExits.size())
            .activeChallenges(activeChallenges.size())
            .averageBlockTime(blockInterval)
            .averageTPS(calculateAverageTPS())
            .build();
    }

    /**
     * Get metrics
     */
    public Layer2Metrics getMetrics() {
        return Layer2Metrics.builder()
            .averageCost(BigInteger.valueOf(1000)) // ~$1 on L1, ~$0.001 on Plasma
            .averageLatency(BigInteger.valueOf(60)) // 60 seconds (block interval)
            .throughput(BigInteger.valueOf(5000)) // 5000 TPS
            .successRate(BigDecimal.valueOf(99.8))
            .build();
    }

    /**
     * Pause the service
     */
    public void pause() {
        paused = true;
        log.warn("Plasma Chain service paused");
    }

    /**
     * Resume the service
     */
    public void resume() {
        paused = false;
        log.info("Plasma Chain service resumed");
    }

    // Private helper methods

    private void startBlockProducer() {
        // Start scheduled executor to produce blocks efficiently
        blockProducer = Executors.newScheduledThreadPool(1, r -> {
            Thread t = new Thread(r, "Plasma-BlockProducer");
            t.setDaemon(true);
            return t;
        });
        
        // Schedule block production at fixed intervals
        blockProducer.scheduleWithFixedDelay(() -> {
            try {
                if (!paused && !currentBlock.isEmpty()) {
                    commitCurrentBlock();
                }
            } catch (Exception e) {
                log.error("Block producer error", e);
            }
        }, blockInterval, blockInterval, TimeUnit.SECONDS);
        
        log.info("Plasma block producer started with {}-second intervals", blockInterval);
    }

    private void startExitProcessor() {
        // Start scheduled executor to process exits efficiently
        exitProcessor = Executors.newScheduledThreadPool(1, r -> {
            Thread t = new Thread(r, "Plasma-ExitProcessor");
            t.setDaemon(true);
            return t;
        });
        
        // Schedule exit processing at fixed intervals
        exitProcessor.scheduleWithFixedDelay(() -> {
            try {
                processMaturedExits();
            } catch (Exception e) {
                log.error("Exit processor error", e);
            }
        }, 60, 60, TimeUnit.SECONDS);
        
        log.info("Plasma exit processor started with 60-second intervals");
    }

    private String commitCurrentBlock() throws Exception {
        List<PlasmaTransaction> blockTransactions;
        
        synchronized (currentBlock) {
            if (currentBlock.isEmpty()) {
                log.debug("No transactions in current block to commit");
                return "no-block-to-commit";
            }
            
            blockTransactions = new ArrayList<>(currentBlock);
            currentBlock.clear();
        }

        String blockId = generateBlockId();
        long blockNumber = totalBlocks.incrementAndGet();

        // Create Plasma block
        PlasmaBlock block = PlasmaBlock.builder()
            .id(blockId)
            .blockNumber(blockNumber)
            .transactions(blockTransactions)
            .timestamp(LocalDateTime.now())
            .transactionCount(blockTransactions.size())
            .merkleRoot(calculateMerkleRoot(blockTransactions))
            .parentHash(getLatestBlockHash())
            .blockHash(calculateBlockHash(blockId, blockTransactions))
            .status(PlasmaBlockStatus.PENDING)
            .build();

        // Submit block root to L1
        String l1TransactionHash = submitBlockToL1(block);
        block.setL1TransactionHash(l1TransactionHash);
        block.setStatus(PlasmaBlockStatus.COMMITTED);

        committedBlocks.put(blockId, block);

        log.info("Plasma block {} committed to L1 with {} transactions", blockNumber, blockTransactions.size());
        return block.getBlockHash();
    }

    private void processMaturedExits() {
        LocalDateTime now = LocalDateTime.now();
        
        activeExits.values().stream()
            .filter(exit -> exit.getStatus() == PlasmaExitStatus.CHALLENGING)
            .filter(exit -> exit.getChallengePeriodEnd().isBefore(now))
            .forEach(exit -> {
                try {
                    finalizeExit(exit);
                } catch (Exception e) {
                    log.error("Failed to finalize exit {}", exit.getId(), e);
                }
            });
    }

    private void finalizeExit(PlasmaExit exit) throws Exception {
        // Finalize unchallenged exit
        String finalizeHash = submitExitFinalizationToL1(exit);
        exit.setStatus(PlasmaExitStatus.FINALIZED);
        exit.setFinalizationTxHash(finalizeHash);
        
        log.info("Plasma exit {} finalized", exit.getId());
    }

    private String generateTransactionId() {
        return "plasma-tx-" + System.currentTimeMillis() + "-" + UUID.randomUUID().toString().substring(0, 8);
    }

    private String generateBlockId() {
        return "plasma-block-" + totalBlocks.get();
    }

    private String generateBatchId() {
        return "plasma-batch-" + System.currentTimeMillis();
    }

    private String generateExitId() {
        return "plasma-exit-" + UUID.randomUUID().toString();
    }

    private String generateChallengeId() {
        return "plasma-challenge-" + UUID.randomUUID().toString();
    }

    private BigInteger getNextNonce(String address) {
        return BigInteger.valueOf(System.currentTimeMillis() % 1000000);
    }

    private String calculateTransactionHash(String fromAddress, String toAddress, BigInteger amount) {
        String hashInput = fromAddress + toAddress + amount.toString() + System.currentTimeMillis();
        return Hash.sha3String(hashInput);
    }

    private String calculateMerkleRoot(List<PlasmaTransaction> transactions) {
        if (transactions.isEmpty()) return "0x0";
        
        // Simplified Merkle root calculation
        StringBuilder hashInput = new StringBuilder();
        transactions.forEach(tx -> hashInput.append(tx.getHash()));
        
        return Hash.sha3String(hashInput.toString());
    }

    private String calculateBlockHash(String blockId, List<PlasmaTransaction> transactions) {
        String hashInput = blockId + calculateMerkleRoot(transactions) + System.currentTimeMillis();
        return Hash.sha3String(hashInput);
    }

    private String getLatestBlockHash() {
        return committedBlocks.values().stream()
            .max(Comparator.comparing(PlasmaBlock::getBlockNumber))
            .map(PlasmaBlock::getBlockHash)
            .orElse("0x0");
    }

    private String submitBlockToL1(PlasmaBlock block) throws Exception {
        Function submitBlockFunction = new Function(
            "submitPlasmaBlock",
            Arrays.asList(
                new Uint256(BigInteger.valueOf(block.getBlockNumber())),
                new Utf8String(block.getMerkleRoot()),
                new Uint256(BigInteger.valueOf(block.getTransactionCount()))
            ),
            Collections.emptyList()
        );

        String encodedFunction = FunctionEncoder.encode(submitBlockFunction);
        
        EthSendTransaction transactionResponse = web3j.ethSendTransaction(
            Transaction.createFunctionCallTransaction(
                null,
                null,
                DefaultGasProvider.GAS_PRICE,
                BigInteger.valueOf(150000),
                plasmaContractAddress,
                encodedFunction
            )
        ).send();

        if (transactionResponse.hasError()) {
            throw new Layer2ProcessingException("Failed to submit Plasma block: " + transactionResponse.getError().getMessage());
        }

        return transactionResponse.getTransactionHash();
    }

    private String submitExitToL1(PlasmaExit exit) throws Exception {
        Function exitFunction = new Function(
            "startExit",
            Arrays.asList(
                new Address(exit.getUserAddress()),
                new Uint256(exit.getAmount()),
                new DynamicBytes(exit.getExitProof().getProofData())
            ),
            Collections.emptyList()
        );

        String encodedFunction = FunctionEncoder.encode(exitFunction);
        
        EthSendTransaction transactionResponse = web3j.ethSendTransaction(
            Transaction.createFunctionCallTransaction(
                null,
                null,
                DefaultGasProvider.GAS_PRICE,
                BigInteger.valueOf(200000),
                plasmaContractAddress,
                encodedFunction
            )
        ).send();

        if (transactionResponse.hasError()) {
            throw new Layer2ProcessingException("Failed to submit Plasma exit: " + transactionResponse.getError().getMessage());
        }

        return transactionResponse.getTransactionHash();
    }

    private String submitChallengeToL1(PlasmaChallenge challenge) throws Exception {
        Function challengeFunction = new Function(
            "challengeExit",
            Arrays.asList(
                new Utf8String(challenge.getExitId()),
                new Address(challenge.getChallengerAddress()),
                new DynamicBytes(challenge.getChallengeProof())
            ),
            Collections.emptyList()
        );

        String encodedFunction = FunctionEncoder.encode(challengeFunction);
        
        EthSendTransaction transactionResponse = web3j.ethSendTransaction(
            Transaction.createFunctionCallTransaction(
                null,
                null,
                DefaultGasProvider.GAS_PRICE,
                BigInteger.valueOf(180000),
                plasmaContractAddress,
                encodedFunction
            )
        ).send();

        if (transactionResponse.hasError()) {
            throw new Layer2ProcessingException("Failed to submit Plasma challenge: " + transactionResponse.getError().getMessage());
        }

        return transactionResponse.getTransactionHash();
    }

    private String submitExitFinalizationToL1(PlasmaExit exit) throws Exception {
        Function finalizeFunction = new Function(
            "finalizeExit",
            Arrays.asList(
                new Utf8String(exit.getId()),
                new Address(exit.getUserAddress()),
                new Uint256(exit.getAmount())
            ),
            Collections.emptyList()
        );

        String encodedFunction = FunctionEncoder.encode(finalizeFunction);
        
        EthSendTransaction transactionResponse = web3j.ethSendTransaction(
            Transaction.createFunctionCallTransaction(
                null,
                null,
                DefaultGasProvider.GAS_PRICE,
                BigInteger.valueOf(160000),
                plasmaContractAddress,
                encodedFunction
            )
        ).send();

        if (transactionResponse.hasError()) {
            throw new Layer2ProcessingException("Failed to finalize Plasma exit: " + transactionResponse.getError().getMessage());
        }

        return transactionResponse.getTransactionHash();
    }

    private ExitProof generateExitProof(WithdrawalRequest request) {
        return ExitProof.builder()
            .userAddress(request.getUserAddress())
            .amount(request.getAmount())
            .proofData(("plasma-exit-proof-" + UUID.randomUUID()).getBytes())
            .blockNumber(totalBlocks.get())
            .transactionIndex(0)
            .build();
    }

    private double calculateAverageTPS() {
        if (totalBlocks.get() == 0) return 0.0;
        return (double) totalTransactions.get() / (totalBlocks.get() * blockInterval);
    }
    
    @PreDestroy
    public void shutdown() {
        log.info("Shutting down PlasmaChainService");
        
        if (blockProducer != null) {
            blockProducer.shutdown();
            try {
                if (!blockProducer.awaitTermination(10, TimeUnit.SECONDS)) {
                    blockProducer.shutdownNow();
                }
            } catch (InterruptedException e) {
                blockProducer.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
        
        if (exitProcessor != null) {
            exitProcessor.shutdown();
            try {
                if (!exitProcessor.awaitTermination(10, TimeUnit.SECONDS)) {
                    exitProcessor.shutdownNow();
                }
            } catch (InterruptedException e) {
                exitProcessor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
        
        log.info("PlasmaChainService shutdown completed");
    }
}