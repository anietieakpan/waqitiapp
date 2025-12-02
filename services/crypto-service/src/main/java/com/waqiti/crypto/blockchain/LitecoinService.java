/**
 * Litecoin Service
 * Production-grade service for Litecoin blockchain operations
 */
package com.waqiti.crypto.blockchain;

import com.waqiti.crypto.dto.*;
import com.waqiti.crypto.entity.FeeSpeed;
import com.waqiti.crypto.entity.CryptoCurrency;
import com.waqiti.crypto.exception.BlockchainException;
import com.waqiti.crypto.exception.InsufficientBalanceException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.bitcoinj.core.*;
import org.bitcoinj.core.listeners.DownloadProgressTracker;
import org.bitcoinj.crypto.TransactionSignature;
import org.bitcoinj.net.discovery.DnsDiscovery;
import org.bitcoinj.params.MainNetParams;
import org.bitcoinj.params.TestNet3Params;
import org.bitcoinj.script.Script;
import org.bitcoinj.script.ScriptBuilder;
import org.bitcoinj.store.BlockStore;
import org.bitcoinj.store.BlockStoreException;
import org.bitcoinj.store.SPVBlockStore;
import org.bitcoinj.wallet.Wallet;
import org.bitcoinj.wallet.WalletTransaction;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.io.File;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.InetAddress;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

@Service
@Slf4j
public class LitecoinService {

    @Lazy
    private final LitecoinService self;

    public LitecoinService(@Lazy LitecoinService self,
                          RedisTemplate<String, Object> redisTemplate, 
                          MeterRegistry meterRegistry) {
        this.self = self;
        this.redisTemplate = redisTemplate;
        this.meterRegistry = meterRegistry;
    }

    @Value("${litecoin.network:mainnet}")
    private String network;

    @Value("${litecoin.node.url:https://ltc.blockbook.api}")
    private String nodeUrl;
    
    @Value("${litecoin.node.backup.url:https://api.blockcypher.com/v1/ltc/main}")
    private String backupNodeUrl;
    
    @Value("${litecoin.node.port:9333}")
    private int nodePort;
    
    @Value("${litecoin.spv.enabled:true}")
    private boolean spvEnabled;
    
    @Value("${litecoin.fee.api.url:https://api.blockcypher.com/v1/ltc/main}")
    private String feeApiUrl;
    
    @Value("${litecoin.cache.ttl.minutes:5}")
    private int cacheTtlMinutes;
    
    @Value("${litecoin.confirmation.required:6}")
    private int requiredConfirmations;

    private NetworkParameters networkParams;
    private PeerGroup peerGroup;
    private BlockStore blockStore;
    private BlockChain blockChain;
    private WebClient webClient;
    private WebClient backupWebClient;
    
    private final RedisTemplate<String, Object> redisTemplate;
    private final MeterRegistry meterRegistry;
    
    // Metrics
    private Counter transactionBroadcastCounter;
    private Counter transactionFailureCounter;
    private Timer balanceCheckTimer;
    private Timer transactionBroadcastTimer;
    
    // Thread pool for async operations
    private final ExecutorService executorService = Executors.newFixedThreadPool(10);
    
    // Cache keys
    private static final String BALANCE_CACHE_PREFIX = "ltc:balance:";
    private static final String TX_CACHE_PREFIX = "ltc:tx:";
    private static final String FEE_CACHE_KEY = "ltc:fees";
    private static final String UTXO_CACHE_PREFIX = "ltc:utxo:";

    @PostConstruct
    public void initialize() {
        try {
            // Initialize network parameters
            networkParams = "mainnet".equals(network) ? 
                MainNetParams.get() : TestNet3Params.get();
            
            // Initialize web clients
            this.webClient = WebClient.builder()
                .baseUrl(nodeUrl)
                .defaultHeader("Accept", "application/json")
                .build();
                
            this.backupWebClient = WebClient.builder()
                .baseUrl(backupNodeUrl)
                .defaultHeader("Accept", "application/json")
                .build();
            
            // Initialize SPV components if enabled
            if (spvEnabled) {
                initializeSPV();
            }
            
            // Initialize metrics
            transactionBroadcastCounter = Counter.builder("litecoin.transaction.broadcast")
                .description("Number of Litecoin transactions broadcasted")
                .register(meterRegistry);
                
            transactionFailureCounter = Counter.builder("litecoin.transaction.failure")
                .description("Number of failed Litecoin transactions")
                .register(meterRegistry);
                
            balanceCheckTimer = Timer.builder("litecoin.balance.check")
                .description("Litecoin balance check duration")
                .register(meterRegistry);
                
            transactionBroadcastTimer = Timer.builder("litecoin.transaction.broadcast.time")
                .description("Litecoin transaction broadcast duration")
                .register(meterRegistry);
            
            log.info("LitecoinService initialized on {} network", network);
            
        } catch (Exception e) {
            log.error("Failed to initialize LitecoinService", e);
            throw new BlockchainException("Failed to initialize Litecoin service", e);
        }
    }
    
    @PreDestroy
    public void shutdown() {
        if (peerGroup != null) {
            peerGroup.stop();
        }
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(10, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
        }
    }
    
    /**
     * Initialize SPV (Simplified Payment Verification) components
     */
    private void initializeSPV() throws BlockStoreException {
        File blockStoreFile = new File("litecoin-" + network + ".spvchain");
        blockStore = new SPVBlockStore(networkParams, blockStoreFile);
        blockChain = new BlockChain(networkParams, blockStore);
        
        peerGroup = new PeerGroup(networkParams, blockChain);
        peerGroup.setMaxConnections(8);
        peerGroup.addPeerDiscovery(new DnsDiscovery(networkParams));
        
        // Add progress tracker
        peerGroup.addBlocksDownloadedEventListener(new DownloadProgressTracker() {
            @Override
            protected void progress(double pct, int blocksSoFar, Date date) {
                log.debug("Litecoin blockchain sync progress: {}%", String.format("%.2f", pct));
            }
        });
        
        // Start peer group asynchronously
        peerGroup.startAsync();
    }

    /**
     * Broadcast signed transaction to Litecoin network
     */
    @CircuitBreaker(name = "litecoin-broadcast", fallbackMethod = "broadcastTransactionFallback")
    @Retry(name = "litecoin-broadcast")
    public String broadcastTransaction(SignedCryptoTransaction signedTx) {
        Timer.Sample sample = Timer.start(meterRegistry);
        log.info("Broadcasting Litecoin transaction");

        try {
            validateSignedTransaction(signedTx);
            
            // Broadcast via primary node
            String txHash = broadcastViaNode(signedTx);
            
            // Cache transaction
            cacheTransaction(txHash, signedTx);
            
            // Update metrics
            transactionBroadcastCounter.increment();
            sample.stop(transactionBroadcastTimer);
            
            log.info("Litecoin transaction broadcasted successfully: {}", txHash);
            return txHash;

        } catch (Exception e) {
            transactionFailureCounter.increment();
            sample.stop(transactionBroadcastTimer);
            log.error("Failed to broadcast Litecoin transaction", e);
            throw new TransactionBroadcastException("Failed to broadcast transaction", e);
        }
    }
    
    /**
     * Broadcast transaction via node API
     */
    private String broadcastViaNode(SignedCryptoTransaction signedTx) {
        Map<String, Object> request = new HashMap<>();
        request.put("hex", signedTx.getSignedTransaction());
        
        BroadcastResponse response = webClient.post()
            .uri("/tx/send")
            .bodyValue(request)
            .retrieve()
            .bodyToMono(BroadcastResponse.class)
            .timeout(Duration.ofSeconds(30))
            .block();
        
        if (response == null || response.getTxid() == null) {
            throw new TransactionBroadcastException("Invalid response from node");
        }
        
        return response.getTxid();
    }
    
    /**
     * Fallback method for transaction broadcast
     */
    public String broadcastTransactionFallback(SignedCryptoTransaction signedTx, Exception ex) {
        log.warn("Primary broadcast failed, using backup node: {}", ex.getMessage());
        
        try {
            // Try backup node
            Map<String, Object> request = new HashMap<>();
            request.put("tx", signedTx.getSignedTransaction());
            
            BroadcastResponse response = backupWebClient.post()
                .uri("/txs/push")
                .bodyValue(request)
                .retrieve()
                .bodyToMono(BroadcastResponse.class)
                .timeout(Duration.ofSeconds(30))
                .block();
            
            if (response != null && response.getTxid() != null) {
                log.info("Transaction broadcasted via backup node: {}", response.getTxid());
                return response.getTxid();
            }
        } catch (Exception backupEx) {
            log.error("Backup broadcast also failed", backupEx);
        }
        
        throw new TransactionBroadcastException("All broadcast attempts failed");
    }

    /**
     * Get address balance with caching and fallback
     */
    @Cacheable(value = "litecoinBalance", key = "#address", unless = "#result == null")
    @CircuitBreaker(name = "litecoin-balance", fallbackMethod = "getAddressBalanceFallback")
    public BigDecimal getAddressBalance(String address) {
        Timer.Sample sample = Timer.start(meterRegistry);
        log.debug("Getting Litecoin balance for address: {}", address);

        try {
            // Check cache first
            String cacheKey = BALANCE_CACHE_PREFIX + address;
            BigDecimal cached = (BigDecimal) redisTemplate.opsForValue().get(cacheKey);
            if (cached != null) {
                sample.stop(balanceCheckTimer);
                return cached;
            }
            
            // Fetch from node
            AddressInfo addressInfo = webClient.get()
                .uri("/address/{address}", address)
                .retrieve()
                .bodyToMono(AddressInfo.class)
                .timeout(Duration.ofSeconds(10))
                .block();
            
            if (addressInfo == null) {
                throw new BalanceRetrievalException("Failed to get address info");
            }
            
            // Calculate confirmed balance
            BigDecimal confirmedBalance = new BigDecimal(addressInfo.getBalance())
                .divide(new BigDecimal("100000000"), 8, RoundingMode.HALF_UP);
            
            // Cache the balance
            redisTemplate.opsForValue().set(cacheKey, confirmedBalance, 
                Duration.ofMinutes(cacheTtlMinutes));
            
            sample.stop(balanceCheckTimer);
            log.debug("Litecoin balance for {}: {} LTC", address, confirmedBalance);
            return confirmedBalance;

        } catch (Exception e) {
            sample.stop(balanceCheckTimer);
            log.error("Failed to get Litecoin balance for address: {}", address, e);
            throw new BalanceRetrievalException("Failed to get balance", e);
        }
    }
    
    /**
     * Fallback method for balance retrieval
     */
    public BigDecimal getAddressBalanceFallback(String address, Exception ex) {
        log.warn("Primary balance check failed, using backup: {}", ex.getMessage());
        
        try {
            // Try backup API
            AddressInfo addressInfo = backupWebClient.get()
                .uri("/addrs/{address}/balance", address)
                .retrieve()
                .bodyToMono(AddressInfo.class)
                .timeout(Duration.ofSeconds(10))
                .block();
            
            if (addressInfo != null) {
                return new BigDecimal(addressInfo.getBalance())
                    .divide(new BigDecimal("100000000"), 8, RoundingMode.HALF_UP);
            }
        } catch (Exception backupEx) {
            log.error("Backup balance check also failed", backupEx);
        }
        
        // Return cached value if available
        String cacheKey = BALANCE_CACHE_PREFIX + address;
        BigDecimal cached = (BigDecimal) redisTemplate.opsForValue().get(cacheKey);
        if (cached != null) {
            log.info("Returning cached balance for {}", address);
            return cached;
        }
        
        throw new BalanceRetrievalException("All balance retrieval attempts failed");
    }

    /**
     * Get transaction details with full information
     */
    @Cacheable(value = "litecoinTransaction", key = "#txHash")
    @CircuitBreaker(name = "litecoin-tx")
    public BlockchainTransaction getTransaction(String txHash) {
        log.debug("Getting Litecoin transaction: {}", txHash);

        try {
            // Check cache
            String cacheKey = TX_CACHE_PREFIX + txHash;
            BlockchainTransaction cached = (BlockchainTransaction) redisTemplate.opsForValue().get(cacheKey);
            if (cached != null) {
                return cached;
            }
            
            // Fetch from node
            TransactionInfo txInfo = webClient.get()
                .uri("/tx/{txHash}", txHash)
                .retrieve()
                .bodyToMono(TransactionInfo.class)
                .timeout(Duration.ofSeconds(10))
                .block();
            
            if (txInfo == null) {
                throw new TransactionRetrievalException("Transaction not found: " + txHash);
            }
            
            // Build transaction object
            BlockchainTransaction transaction = BlockchainTransaction.builder()
                .txHash(txHash)
                .blockHeight(txInfo.getBlockHeight())
                .confirmations(txInfo.getConfirmations())
                .timestamp(txInfo.getTimestamp() * 1000) // Convert to milliseconds
                .fee(calculateTransactionFee(txInfo))
                .status(determineTransactionStatus(txInfo))
                .inputs(mapTransactionInputs(txInfo))
                .outputs(mapTransactionOutputs(txInfo))
                .size(txInfo.getSize())
                .weight(txInfo.getWeight())
                .build();
            
            // Cache if confirmed
            if (transaction.getConfirmations() >= requiredConfirmations) {
                redisTemplate.opsForValue().set(cacheKey, transaction, Duration.ofHours(24));
            }
            
            return transaction;

        } catch (Exception e) {
            log.error("Failed to get Litecoin transaction: {}", txHash, e);
            throw new TransactionRetrievalException("Failed to get transaction", e);
        }
    }

    /**
     * Get current network fees with dynamic calculation
     */
    @Cacheable(value = "litecoinFees", unless = "#result == null")
    public NetworkFees getNetworkFees() {
        log.debug("Getting Litecoin network fees");

        try {
            // Check cache
            NetworkFees cached = (NetworkFees) redisTemplate.opsForValue().get(FEE_CACHE_KEY);
            if (cached != null && !cached.isStale()) {
                return cached;
            }
            
            // Fetch fee estimates from multiple sources
            Map<FeeSpeed, BigDecimal> feeRates = fetchFeeEstimates();
            
            NetworkFees fees = new NetworkFees();
            fees.setCurrency(CryptoCurrency.LITECOIN);
            fees.setFastFee(feeRates.get(FeeSpeed.FAST));
            fees.setMediumFee(feeRates.get(FeeSpeed.MEDIUM));
            fees.setSlowFee(feeRates.get(FeeSpeed.SLOW));
            fees.setTimestamp(System.currentTimeMillis());
            
            // Cache for 5 minutes
            redisTemplate.opsForValue().set(FEE_CACHE_KEY, fees, Duration.ofMinutes(5));
            
            log.debug("Litecoin fees - Fast: {} Medium: {} Slow: {} LTC/byte", 
                fees.getFastFee(), fees.getMediumFee(), fees.getSlowFee());
            
            return fees;

        } catch (Exception e) {
            log.error("Failed to get Litecoin network fees", e);
            // Return default fees as fallback
            return getDefaultNetworkFees();
        }
    }
    
    /**
     * Estimate transaction fee for given parameters
     */
    public BigDecimal estimateTransactionFee(int inputs, int outputs, FeeSpeed speed) {
        try {
            // Calculate transaction size (approximate)
            // P2PKH: 180 bytes per input, 34 bytes per output, 10 bytes overhead
            int estimatedSize = (inputs * 180) + (outputs * 34) + 10;
            
            NetworkFees fees = self.getNetworkFees();
            BigDecimal feePerByte = switch (speed) {
                case FAST -> fees.getFastFee();
                case MEDIUM -> fees.getMediumFee();
                case SLOW -> fees.getSlowFee();
            };
            
            return feePerByte.multiply(new BigDecimal(estimatedSize))
                .setScale(8, RoundingMode.HALF_UP);
            
        } catch (Exception e) {
            log.error("Failed to estimate transaction fee", e);
            // Return conservative estimate
            return new BigDecimal("0.001");
        }
    }
    
    /**
     * Get unspent transaction outputs (UTXOs) for address
     */
    @CircuitBreaker(name = "litecoin-utxo")
    public List<UTXO> getUTXOs(String address) {
        log.debug("Getting UTXOs for address: {}", address);
        
        try {
            // Check cache
            String cacheKey = UTXO_CACHE_PREFIX + address;
            List<UTXO> cached = (List<UTXO>) redisTemplate.opsForValue().get(cacheKey);
            if (cached != null) {
                return cached;
            }
            
            // Fetch from node
            UTXOResponse response = webClient.get()
                .uri("/address/{address}/utxo", address)
                .retrieve()
                .bodyToMono(UTXOResponse.class)
                .timeout(Duration.ofSeconds(10))
                .block();
            
            if (response == null || response.getUtxos() == null) {
                return Collections.emptyList();
            }
            
            List<UTXO> utxos = response.getUtxos().stream()
                .map(this::mapToUTXO)
                .filter(utxo -> utxo.getConfirmations() >= requiredConfirmations)
                .sorted((a, b) -> b.getValue().compareTo(a.getValue())) // Sort by value descending
                .collect(Collectors.toList());
            
            // Cache for short duration
            redisTemplate.opsForValue().set(cacheKey, utxos, Duration.ofMinutes(1));
            
            return utxos;
            
        } catch (Exception e) {
            log.error("Failed to get UTXOs for address: {}", address, e);
            throw new BlockchainException("Failed to get UTXOs", e);
        }
    }
    
    /**
     * Build raw transaction
     */
    public String buildRawTransaction(
            String fromAddress, 
            String toAddress, 
            BigDecimal amount, 
            BigDecimal fee) {
        
        log.info("Building raw transaction from {} to {} amount: {} fee: {}", 
            fromAddress, toAddress, amount, fee);
        
        try {
            // Get UTXOs for the address
            List<UTXO> utxos = getUTXOs(fromAddress);
            
            // Select UTXOs for the transaction
            List<UTXO> selectedUTXOs = selectUTXOs(utxos, amount.add(fee));
            
            if (selectedUTXOs.isEmpty()) {
                throw new InsufficientBalanceException("Insufficient balance for transaction");
            }
            
            // Calculate total input and change
            BigDecimal totalInput = selectedUTXOs.stream()
                .map(UTXO::getValue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
            
            BigDecimal change = totalInput.subtract(amount).subtract(fee);
            
            // Build transaction
            Transaction tx = new Transaction(networkParams);
            
            // Add inputs
            for (UTXO utxo : selectedUTXOs) {
                TransactionOutPoint outPoint = new TransactionOutPoint(
                    networkParams, 
                    utxo.getVout(), 
                    Sha256Hash.wrap(utxo.getTxid())
                );
                tx.addInput(new TransactionInput(networkParams, tx, new byte[]{}, outPoint));
            }
            
            // Add outputs
            Address recipientAddress = Address.fromString(networkParams, toAddress);
            Coin sendAmount = Coin.parseCoin(amount.toString());
            tx.addOutput(sendAmount, recipientAddress);
            
            // Add change output if necessary
            if (change.compareTo(new BigDecimal("0.00000546")) > 0) { // Dust threshold
                Address changeAddress = Address.fromString(networkParams, fromAddress);
                Coin changeAmount = Coin.parseCoin(change.toString());
                tx.addOutput(changeAmount, changeAddress);
            }
            
            // Return hex-encoded transaction
            return Utils.HEX.encode(tx.bitcoinSerialize());
            
        } catch (Exception e) {
            log.error("Failed to build raw transaction", e);
            throw new BlockchainException("Failed to build transaction", e);
        }
    }
    
    /**
     * Validate Litecoin address
     */
    public boolean isValidAddress(String address) {
        try {
            Address.fromString(networkParams, address);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * Get blockchain info
     */
    @Cacheable(value = "litecoinBlockchainInfo", unless = "#result == null")
    public BlockchainInfo getBlockchainInfo() {
        try {
            return webClient.get()
                .uri("/")
                .retrieve()
                .bodyToMono(BlockchainInfo.class)
                .timeout(Duration.ofSeconds(10))
                .block();
        } catch (Exception e) {
            log.error("Failed to get blockchain info", e);
            throw new BlockchainException("Failed to get blockchain info", e);
        }
    }
    
    /**
     * Monitor transaction confirmation
     */
    @Async
    public CompletableFuture<TransactionStatus> monitorTransaction(String txHash) {
        return CompletableFuture.supplyAsync(() -> {
            int attempts = 0;
            int maxAttempts = 120; // 2 hours with 1-minute intervals
            
            while (attempts < maxAttempts) {
                try {
                    BlockchainTransaction tx = getTransaction(txHash);
                    
                    if (tx.getConfirmations() >= requiredConfirmations) {
                        log.info("Transaction {} confirmed with {} confirmations", 
                            txHash, tx.getConfirmations());
                        return TransactionStatus.CONFIRMED;
                    }
                    
                    // Non-blocking wait before next check
                    return CompletableFuture.delayedExecutor(1, TimeUnit.MINUTES)
                        .execute(() -> {}).thenApply(v -> TransactionStatus.PENDING);
                    attempts++;
                    
                } catch (Exception e) {
                    log.error("Error monitoring transaction {}: {}", txHash, e.getMessage());
                    if (attempts > 10) {
                        return TransactionStatus.FAILED;
                    }
                }
            }
            
            log.warn("Transaction {} not confirmed after {} attempts", txHash, maxAttempts);
            return TransactionStatus.PENDING;
            
        }, executorService);
    }
    
    // Helper methods
    
    private void validateSignedTransaction(SignedCryptoTransaction signedTx) {
        if (signedTx == null || signedTx.getSignedTransaction() == null) {
            throw new IllegalArgumentException("Invalid signed transaction");
        }
        
        // Validate hex format
        if (!signedTx.getSignedTransaction().matches("^[0-9a-fA-F]+$")) {
            throw new IllegalArgumentException("Transaction must be in hex format");
        }
    }
    
    private void cacheTransaction(String txHash, SignedCryptoTransaction signedTx) {
        try {
            String cacheKey = TX_CACHE_PREFIX + txHash;
            redisTemplate.opsForValue().set(cacheKey, signedTx, Duration.ofHours(1));
        } catch (Exception e) {
            log.warn("Failed to cache transaction: {}", e.getMessage());
        }
    }
    
    private BigDecimal calculateTransactionFee(TransactionInfo txInfo) {
        BigDecimal totalInput = txInfo.getInputs().stream()
            .map(input -> new BigDecimal(input.getValue()))
            .reduce(BigDecimal.ZERO, BigDecimal::add);
            
        BigDecimal totalOutput = txInfo.getOutputs().stream()
            .map(output -> new BigDecimal(output.getValue()))
            .reduce(BigDecimal.ZERO, BigDecimal::add);
            
        return totalInput.subtract(totalOutput)
            .divide(new BigDecimal("100000000"), 8, RoundingMode.HALF_UP);
    }
    
    private String determineTransactionStatus(TransactionInfo txInfo) {
        if (txInfo.getConfirmations() >= requiredConfirmations) {
            return "confirmed";
        } else if (txInfo.getConfirmations() > 0) {
            return "pending";
        } else {
            return "unconfirmed";
        }
    }
    
    private List<TransactionInput> mapTransactionInputs(TransactionInfo txInfo) {
        return txInfo.getInputs().stream()
            .map(input -> TransactionInput.builder()
                .address(input.getAddress())
                .amount(new BigDecimal(input.getValue())
                    .divide(new BigDecimal("100000000"), 8, RoundingMode.HALF_UP))
                .txid(input.getTxid())
                .vout(input.getVout())
                .build())
            .collect(Collectors.toList());
    }
    
    private List<TransactionOutput> mapTransactionOutputs(TransactionInfo txInfo) {
        return txInfo.getOutputs().stream()
            .map(output -> TransactionOutput.builder()
                .address(output.getAddress())
                .amount(new BigDecimal(output.getValue())
                    .divide(new BigDecimal("100000000"), 8, RoundingMode.HALF_UP))
                .index(output.getIndex())
                .spent(output.isSpent())
                .build())
            .collect(Collectors.toList());
    }
    
    private Map<FeeSpeed, BigDecimal> fetchFeeEstimates() {
        try {
            FeeEstimate estimate = webClient.get()
                .uri("/fee-estimates")
                .retrieve()
                .bodyToMono(FeeEstimate.class)
                .timeout(Duration.ofSeconds(5))
                .block();
            
            if (estimate != null) {
                Map<FeeSpeed, BigDecimal> fees = new HashMap<>();
                fees.put(FeeSpeed.FAST, new BigDecimal(estimate.getFastestFee()).divide(new BigDecimal("1000"), 8, RoundingMode.HALF_UP));
                fees.put(FeeSpeed.MEDIUM, new BigDecimal(estimate.getHalfHourFee()).divide(new BigDecimal("1000"), 8, RoundingMode.HALF_UP));
                fees.put(FeeSpeed.SLOW, new BigDecimal(estimate.getHourFee()).divide(new BigDecimal("1000"), 8, RoundingMode.HALF_UP));
                return fees;
            }
        } catch (Exception e) {
            log.warn("Failed to fetch fee estimates: {}", e.getMessage());
        }
        
        // Return default fees
        Map<FeeSpeed, BigDecimal> defaultFees = new HashMap<>();
        defaultFees.put(FeeSpeed.FAST, new BigDecimal("0.00001000"));
        defaultFees.put(FeeSpeed.MEDIUM, new BigDecimal("0.00000500"));
        defaultFees.put(FeeSpeed.SLOW, new BigDecimal("0.00000100"));
        return defaultFees;
    }
    
    private NetworkFees getDefaultNetworkFees() {
        NetworkFees fees = new NetworkFees();
        fees.setCurrency(CryptoCurrency.LITECOIN);
        fees.setFastFee(new BigDecimal("0.00001000"));
        fees.setMediumFee(new BigDecimal("0.00000500"));
        fees.setSlowFee(new BigDecimal("0.00000100"));
        fees.setTimestamp(System.currentTimeMillis());
        return fees;
    }
    
    private UTXO mapToUTXO(UTXOInfo info) {
        return UTXO.builder()
            .txid(info.getTxid())
            .vout(info.getVout())
            .address(info.getAddress())
            .scriptPubKey(info.getScriptPubKey())
            .value(new BigDecimal(info.getValue())
                .divide(new BigDecimal("100000000"), 8, RoundingMode.HALF_UP))
            .confirmations(info.getConfirmations())
            .spendable(info.isSpendable())
            .build();
    }
    
    private List<UTXO> selectUTXOs(List<UTXO> utxos, BigDecimal targetAmount) {
        List<UTXO> selected = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        
        // Use largest UTXOs first to minimize transaction size
        for (UTXO utxo : utxos) {
            if (!utxo.isSpendable()) continue;
            
            selected.add(utxo);
            total = total.add(utxo.getValue());
            
            if (total.compareTo(targetAmount) >= 0) {
                break;
            }
        }
        
        if (total.compareTo(targetAmount) < 0) {
            return Collections.emptyList();
        }
        
        return selected;
    }
    
    // Exception classes
    
    public static class TransactionBroadcastException extends RuntimeException {
        public TransactionBroadcastException(String message) {
            super(message);
        }
        
        public TransactionBroadcastException(String message, Throwable cause) {
            super(message, cause);
        }
    }
    
    public static class BalanceRetrievalException extends RuntimeException {
        public BalanceRetrievalException(String message) {
            super(message);
        }
        
        public BalanceRetrievalException(String message, Throwable cause) {
            super(message, cause);
        }
    }
    
    public static class TransactionRetrievalException extends RuntimeException {
        public TransactionRetrievalException(String message) {
            super(message);
        }
        
        public TransactionRetrievalException(String message, Throwable cause) {
            super(message, cause);
        }
    }
    
    // Response DTOs
    
    @lombok.Data
    private static class BroadcastResponse {
        private String txid;
        private String error;
    }
    
    @lombok.Data
    private static class AddressInfo {
        private String address;
        private String balance;
        private String unconfirmedBalance;
        private int txCount;
    }
    
    @lombok.Data
    private static class TransactionInfo {
        private String txid;
        private long blockHeight;
        private int confirmations;
        private long timestamp;
        private int size;
        private int weight;
        private List<InputInfo> inputs;
        private List<OutputInfo> outputs;
    }
    
    @lombok.Data
    private static class InputInfo {
        private String txid;
        private int vout;
        private String address;
        private String value;
    }
    
    @lombok.Data
    private static class OutputInfo {
        private int index;
        private String address;
        private String value;
        private boolean spent;
    }
    
    @lombok.Data
    private static class UTXOResponse {
        private List<UTXOInfo> utxos;
    }
    
    @lombok.Data
    private static class UTXOInfo {
        private String txid;
        private int vout;
        private String address;
        private String scriptPubKey;
        private String value;
        private int confirmations;
        private boolean spendable;
    }
    
    @lombok.Data
    private static class FeeEstimate {
        private int fastestFee;
        private int halfHourFee;
        private int hourFee;
    }
    
    @lombok.Data
    @lombok.Builder
    private static class TransactionInput {
        private String address;
        private BigDecimal amount;
        private String txid;
        private int vout;
    }
    
    @lombok.Data
    @lombok.Builder
    private static class TransactionOutput {
        private String address;
        private BigDecimal amount;
        private int index;
        private boolean spent;
    }
    
    @lombok.Data
    @lombok.Builder
    private static class UTXO {
        private String txid;
        private int vout;
        private String address;
        private String scriptPubKey;
        private BigDecimal value;
        private int confirmations;
        private boolean spendable;
    }
    
    @lombok.Data
    private static class BlockchainInfo {
        private String chain;
        private long blocks;
        private long headers;
        private String bestBlockHash;
        private double difficulty;
        private long medianTime;
        private double verificationProgress;
    }
    
    private enum TransactionStatus {
        PENDING, CONFIRMED, FAILED
    }
}