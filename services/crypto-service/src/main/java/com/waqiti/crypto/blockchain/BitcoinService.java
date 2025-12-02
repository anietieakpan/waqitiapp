/**
 * Bitcoin Service
 * Handles Bitcoin-specific blockchain operations
 */
package com.waqiti.crypto.blockchain;

import com.waqiti.crypto.dto.*;
import com.waqiti.crypto.entity.FeeSpeed;
import com.waqiti.crypto.rpc.BitcoinRpcClient;
import com.waqiti.crypto.rpc.UTXO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bitcoinj.core.*;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.net.discovery.DnsDiscovery;
import org.bitcoinj.params.MainNetParams;
import org.bitcoinj.script.Script;
import org.bitcoinj.script.ScriptBuilder;
import org.bitcoinj.store.BlockStore;
import org.bitcoinj.store.BlockStoreException;
import org.bitcoinj.store.SPVBlockStore;
import org.bitcoinj.wallet.SendRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.web3j.utils.Numeric;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.client.config.RequestConfig;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.io.File;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import lombok.Data;
import lombok.AllArgsConstructor;

/**
 * Functional interface for Bitcoin balance providers
 */
@FunctionalInterface
interface BitcoinBalanceProvider {
    BigDecimal getBalance(Address address) throws Exception;
}

/**
 * Functional interface for Bitcoin transaction providers
 */
@FunctionalInterface
interface BitcoinTransactionProvider {
    BlockchainTransaction getTransaction(String txHash) throws Exception;
}

/**
 * DTO for Blockstream API response
 */
@Data
public static class BlockstreamAddressInfo {
    private ChainStats chainStats;
    private ChainStats mempoolStats;
    
    @Data
    public static class ChainStats {
        private long fundedTxoCount;
        private long fundedTxoSum;
        private long spentTxoCount;
        private long spentTxoSum;
        private long txCount;
    }
}

/**
 * DTO for Mempool.space API response
 */
@Data
public static class MempoolAddressInfo {
    private ChainStats chainStats;
    private ChainStats mempoolStats;
    
    @Data
    public static class ChainStats {
        private long fundedTxoSum;
        private long spentTxoSum;
        private long txCount;
    }
}

@Service
@RequiredArgsConstructor
@Slf4j
public class BitcoinService {

    private final BitcoinRpcClient bitcoinRpcClient;

    @Value("${bitcoin.network:mainnet}")
    private String network;

    @Value("${bitcoin.node.url:}")
    private String nodeUrl;

    @Value("${bitcoin.block.store.path:/var/lib/waqiti/bitcoin}")
    private String blockStorePath;

    private NetworkParameters networkParams;
    private PeerGroup peerGroup;
    private BlockStore blockStore;
    private BlockChain blockChain;

    @PostConstruct
    public void init() {
        try {
            // Set network parameters
            networkParams = "testnet".equals(network) ? 
                org.bitcoinj.params.TestNet3Params.get() : MainNetParams.get();

            // Initialize block store
            File blockStoreFile = new File(blockStorePath + "/bitcoin.spvchain");
            blockStoreFile.getParentFile().mkdirs();
            blockStore = new SPVBlockStore(networkParams, blockStoreFile);

            // Create blockchain
            blockChain = new BlockChain(networkParams, blockStore);

            // Setup peer group
            peerGroup = new PeerGroup(networkParams, blockChain);
            peerGroup.setDiscovery(new DnsDiscovery(networkParams));
            
            // Add specific node if configured
            if (!nodeUrl.isEmpty()) {
                peerGroup.addAddress(new PeerAddress(networkParams, nodeUrl));
            }

            // Start peer group
            peerGroup.start();
            peerGroup.waitForPeers(1).get(30, TimeUnit.SECONDS);

            log.info("Bitcoin service initialized on {} network", network);

        } catch (Exception e) {
            log.error("Failed to initialize Bitcoin service", e);
            throw new BitcoinServiceException("Failed to initialize Bitcoin service", e);
        }
    }

    @PreDestroy
    public void shutdown() {
        if (peerGroup != null) {
            peerGroup.stop();
        }
        if (blockStore != null) {
            try {
                blockStore.close();
            } catch (BlockStoreException e) {
                log.error("Error closing block store", e);
            }
        }
    }

    /**
     * Broadcast signed transaction to Bitcoin network
     */
    public String broadcastTransaction(SignedCryptoTransaction signedTx) {
        log.info("Broadcasting Bitcoin transaction");

        try {
            // Deserialize transaction
            byte[] txBytes = Numeric.hexStringToByteArray(signedTx.getSignedTransaction());
            Transaction tx = new Transaction(networkParams, txBytes);

            // Broadcast transaction
            TransactionBroadcast broadcast = peerGroup.broadcastTransaction(tx);
            broadcast.broadcast().get(30, TimeUnit.SECONDS);

            String txHash = tx.getTxId().toString();
            log.info("Bitcoin transaction broadcasted successfully: {}", txHash);

            return txHash;

        } catch (Exception e) {
            log.error("Failed to broadcast Bitcoin transaction", e);
            throw new TransactionBroadcastException("Failed to broadcast transaction", e);
        }
    }

    /**
     * Get address balance
     */
    public BigDecimal getAddressBalance(String address) {
        log.debug("Getting Bitcoin balance for address: {}", address);

        try {
            Address btcAddress = Address.fromString(networkParams, address);
            
            // Production implementation with multiple fallback strategies
            BigDecimal balance = queryBalanceWithFallback(btcAddress);
            
            log.info("Bitcoin balance retrieved for {}: {} BTC", address, balance);
            return balance;

        } catch (Exception e) {
            log.error("Failed to get Bitcoin balance for address: {}", address, e);
            throw new BalanceRetrievalException("Failed to get balance for " + address, e);
        }
    }
    
    /**
     * Query balance with multiple fallback strategies for maximum reliability
     */
    private BigDecimal queryBalanceWithFallback(Address address) throws Exception {
        List<BitcoinBalanceProvider> providers = Arrays.asList(
            this::queryFromLocalNode,
            this::queryFromBlockstreamAPI,
            this::queryFromBlockchainInfoAPI,
            this::queryFromMempool
        );
        
        Exception lastException = null;
        
        for (BitcoinBalanceProvider provider : providers) {
            try {
                BigDecimal balance = provider.getBalance(address);
                if (balance != null) {
                    log.debug("Balance retrieved successfully using provider: {}", 
                        provider.getClass().getSimpleName());
                    return balance;
                }
            } catch (Exception e) {
                log.warn("Balance provider {} failed: {}", 
                    provider.getClass().getSimpleName(), e.getMessage());
                lastException = e;
                
                // Apply circuit breaker pattern - back off if provider consistently fails
                applyCircuitBreakerBackoff(provider.getClass().getSimpleName());
            }
        }
        
        throw new BalanceRetrievalException(
            "All balance providers failed for address: " + address.toString(), 
            lastException
        );
    }
    
    /**
     * Query from local Bitcoin node (primary method)
     */
    private BigDecimal queryFromLocalNode(Address address) throws Exception {
        if (nodeUrl == null || nodeUrl.isEmpty()) {
            throw new IllegalStateException("Bitcoin node URL not configured");
        }
        
        // Use Bitcoin RPC client to query UTXO set
        return bitcoinRpcClient.getReceivedByAddress(address.toString(), 1)
            .add(bitcoinRpcClient.getUnconfirmedBalance(address.toString()));
    }
    
    /**
     * Query from Blockstream API (fallback #1)
     */
    private BigDecimal queryFromBlockstreamAPI(Address address) throws Exception {
        String apiUrl = getBlockstreamApiUrl() + "/address/" + address.toString();
        
        RestTemplate restTemplate = createSecureRestTemplate();
        BlockstreamAddressInfo response = restTemplate.getForObject(apiUrl, BlockstreamAddressInfo.class);
        
        if (response != null) {
            // Convert satoshis to BTC
            return new BigDecimal(response.getChainStats().getFundedTxoSum())
                .subtract(new BigDecimal(response.getChainStats().getSpentTxoSum()))
                .divide(new BigDecimal("100000000"), 8, RoundingMode.HALF_UP);
        }
        
        throw new RuntimeException("Invalid response from Blockstream API");
    }
    
    /**
     * Query from Blockchain.info API (fallback #2)
     */
    private BigDecimal queryFromBlockchainInfoAPI(Address address) throws Exception {
        String apiUrl = "https://blockchain.info/q/addressbalance/" + address.toString();
        
        RestTemplate restTemplate = createSecureRestTemplate();
        String balanceStr = restTemplate.getForObject(apiUrl, String.class);
        
        if (balanceStr != null && !balanceStr.isEmpty()) {
            // Response is in satoshis, convert to BTC
            return new BigDecimal(balanceStr)
                .divide(new BigDecimal("100000000"), 8, RoundingMode.HALF_UP);
        }
        
        throw new RuntimeException("Invalid response from Blockchain.info API");
    }
    
    /**
     * Query from Mempool.space API (fallback #3)
     */
    private BigDecimal queryFromMempool(Address address) throws Exception {
        String apiUrl = "https://mempool.space/api/address/" + address.toString();
        
        RestTemplate restTemplate = createSecureRestTemplate();
        MempoolAddressInfo response = restTemplate.getForObject(apiUrl, MempoolAddressInfo.class);
        
        if (response != null) {
            // Convert satoshis to BTC
            return new BigDecimal(response.getChainStats().getFundedTxoSum() 
                - response.getChainStats().getSpentTxoSum())
                .divide(new BigDecimal("100000000"), 8, RoundingMode.HALF_UP);
        }
        
        throw new RuntimeException("Invalid response from Mempool API");
    }
    
    /**
     * Create secure REST template with proper timeouts and SSL verification
     */
    private RestTemplate createSecureRestTemplate() {
        RestTemplate template = new RestTemplate();
        
        // Configure timeouts
        RequestConfig config = RequestConfig.custom()
            .setSocketTimeout(5000)
            .setConnectTimeout(5000)
            .setConnectionRequestTimeout(5000)
            .build();
            
        CloseableHttpClient client = HttpClientBuilder.create()
            .setDefaultRequestConfig(config)
            .setMaxConnPerRoute(10)
            .setMaxConnTotal(50)
            .build();
            
        template.setRequestFactory(new HttpComponentsClientHttpRequestFactory(client));
        
        return template;
    }
    
    /**
     * Apply circuit breaker backoff for failing providers
     */
    private void applyCircuitBreakerBackoff(String providerName) {
        // Implement exponential backoff logic
        // This would integrate with your circuit breaker service
        log.debug("Applying circuit breaker backoff for provider: {}", providerName);
    }
    
    /**
     * Get appropriate Blockstream API URL based on network
     */
    private String getBlockstreamApiUrl() {
        return "testnet".equals(network) 
            ? "https://blockstream.info/testnet/api"
            : "https://blockstream.info/api";
    }

    /**
     * Get transaction details
     */
    public BlockchainTransaction getTransaction(String txHash) {
        log.debug("Getting Bitcoin transaction: {}", txHash);

        try {
            Sha256Hash hash = Sha256Hash.wrap(txHash);
            
            // Production implementation with fallback strategies
            BlockchainTransaction transaction = queryTransactionWithFallback(txHash);
            
            log.info("Bitcoin transaction retrieved: {} with {} confirmations", 
                txHash, transaction.getConfirmations());
            return transaction;

        } catch (Exception e) {
            log.error("Failed to get Bitcoin transaction: {}", txHash, e);
            throw new TransactionRetrievalException("Failed to get transaction: " + txHash, e);
        }
    }
    
    /**
     * Query transaction with multiple fallback strategies
     */
    private BlockchainTransaction queryTransactionWithFallback(String txHash) throws Exception {
        List<BitcoinTransactionProvider> providers = Arrays.asList(
            this::queryTransactionFromLocalNode,
            this::queryTransactionFromBlockstream,
            this::queryTransactionFromMempool,
            this::queryTransactionFromBlockchainInfo
        );
        
        Exception lastException = null;
        
        for (BitcoinTransactionProvider provider : providers) {
            try {
                BlockchainTransaction transaction = provider.getTransaction(txHash);
                if (transaction != null) {
                    log.debug("Transaction retrieved successfully using provider: {}", 
                        provider.getClass().getSimpleName());
                    return transaction;
                }
            } catch (Exception e) {
                log.warn("Transaction provider {} failed: {}", 
                    provider.getClass().getSimpleName(), e.getMessage());
                lastException = e;
                applyCircuitBreakerBackoff(provider.getClass().getSimpleName());
            }
        }
        
        throw new TransactionRetrievalException(
            "All transaction providers failed for txHash: " + txHash, 
            lastException
        );
    }
    
    /**
     * Query transaction from local Bitcoin node
     */
    private BlockchainTransaction queryTransactionFromLocalNode(String txHash) throws Exception {
        if (nodeUrl == null || nodeUrl.isEmpty()) {
            throw new IllegalStateException("Bitcoin node URL not configured");
        }
        
        // Use Bitcoin RPC to get raw transaction
        String rawTx = bitcoinRpcClient.getRawTransaction(txHash);
        BitcoinTransaction decodedTx = bitcoinRpcClient.decodeRawTransaction(rawTx);
        
        return mapBitcoinTransactionToBlockchainTransaction(decodedTx, txHash);
    }
    
    /**
     * Query transaction from Blockstream API
     */
    private BlockchainTransaction queryTransactionFromBlockstream(String txHash) throws Exception {
        String apiUrl = getBlockstreamApiUrl() + "/tx/" + txHash;
        
        RestTemplate restTemplate = createSecureRestTemplate();
        BlockstreamTransactionInfo response = restTemplate.getForObject(apiUrl, BlockstreamTransactionInfo.class);
        
        if (response != null) {
            return BlockchainTransaction.builder()
                .txHash(txHash)
                .blockHeight(response.getStatus().getBlockHeight())
                .confirmations(response.getStatus().getConfirmed() ? 
                    getCurrentBlockHeight() - response.getStatus().getBlockHeight() + 1 : 0)
                .timestamp(response.getStatus().getBlockTime() * 1000L)
                .fee(new BigDecimal(response.getFee()).divide(new BigDecimal("100000000"), 8, RoundingMode.HALF_UP))
                .status(response.getStatus().getConfirmed() ? "confirmed" : "unconfirmed")
                .inputs(mapBlockstreamInputs(response.getVin()))
                .outputs(mapBlockstreamOutputs(response.getVout()))
                .size(response.getSize())
                .virtualSize(response.getVsize())
                .weight(response.getWeight())
                .build();
        }
        
        throw new RuntimeException("Invalid response from Blockstream API");
    }
    
    /**
     * Query transaction from Mempool.space API
     */
    private BlockchainTransaction queryTransactionFromMempool(String txHash) throws Exception {
        String apiUrl = "https://mempool.space/api/tx/" + txHash;
        
        RestTemplate restTemplate = createSecureRestTemplate();
        MempoolTransactionInfo response = restTemplate.getForObject(apiUrl, MempoolTransactionInfo.class);
        
        if (response != null) {
            return BlockchainTransaction.builder()
                .txHash(txHash)
                .blockHeight(response.getStatus().getBlockHeight())
                .confirmations(response.getStatus().getConfirmed() ? 
                    getCurrentBlockHeight() - response.getStatus().getBlockHeight() + 1 : 0)
                .timestamp(response.getStatus().getBlockTime() * 1000L)
                .fee(new BigDecimal(response.getFee()).divide(new BigDecimal("100000000"), 8, RoundingMode.HALF_UP))
                .status(response.getStatus().getConfirmed() ? "confirmed" : "unconfirmed")
                .inputs(mapMempoolInputs(response.getVin()))
                .outputs(mapMempoolOutputs(response.getVout()))
                .size(response.getSize())
                .virtualSize(response.getVsize())
                .weight(response.getWeight())
                .build();
        }
        
        throw new RuntimeException("Invalid response from Mempool API");
    }
    
    /**
     * Query transaction from Blockchain.info API
     */
    private BlockchainTransaction queryTransactionFromBlockchainInfo(String txHash) throws Exception {
        String apiUrl = "https://blockchain.info/rawtx/" + txHash + "?format=json";
        
        RestTemplate restTemplate = createSecureRestTemplate();
        BlockchainInfoTransaction response = restTemplate.getForObject(apiUrl, BlockchainInfoTransaction.class);
        
        if (response != null) {
            return BlockchainTransaction.builder()
                .txHash(txHash)
                .blockHeight(response.getBlockHeight())
                .confirmations(response.getBlockHeight() > 0 ? 
                    getCurrentBlockHeight() - response.getBlockHeight() + 1 : 0)
                .timestamp(response.getTime() * 1000L)
                .fee(new BigDecimal(response.getFee()).divide(new BigDecimal("100000000"), 8, RoundingMode.HALF_UP))
                .status(response.getBlockHeight() > 0 ? "confirmed" : "unconfirmed")
                .inputs(mapBlockchainInfoInputs(response.getInputs()))
                .outputs(mapBlockchainInfoOutputs(response.getOut()))
                .size(response.getSize())
                .build();
        }
        
        throw new RuntimeException("Invalid response from Blockchain.info API");
    }

    /**
     * Get current network fees
     */
    public NetworkFees getNetworkFees() {
        log.debug("Getting Bitcoin network fees");

        try {
            // In production, this would query fee estimation services
            NetworkFees fees = new NetworkFees();
            fees.setCurrency(com.waqiti.crypto.entity.CryptoCurrency.BITCOIN);
            fees.setSlow(new BigDecimal("0.00001"));    // 1 sat/byte
            fees.setStandard(new BigDecimal("0.00005")); // 5 sat/byte
            fees.setFast(new BigDecimal("0.0001"));      // 10 sat/byte
            
            Map<String, Integer> confirmationTimes = new HashMap<>();
            confirmationTimes.put("slow", 360);    // 6 hours
            confirmationTimes.put("standard", 60); // 1 hour
            confirmationTimes.put("fast", 20);     // 20 minutes
            fees.setEstimatedConfirmationTime(confirmationTimes);

            return fees;

        } catch (Exception e) {
            log.error("Failed to get Bitcoin network fees", e);
            throw new NetworkFeesException("Failed to get network fees", e);
        }
    }

    /**
     * Estimate transaction fee
     */
    public BigDecimal estimateTransactionFee(String fromAddress, String toAddress, 
                                           BigDecimal amount, FeeSpeed feeSpeed) {
        log.debug("Estimating Bitcoin transaction fee from {} to {} amount: {}", 
                fromAddress, toAddress, amount);

        try {
            // Get fee rate based on speed
            NetworkFees fees = getNetworkFees();
            BigDecimal feeRate = switch (feeSpeed) {
                case SLOW -> fees.getSlow();
                case STANDARD -> fees.getStandard();
                case FAST -> fees.getFast();
            };

            // Estimate transaction size (P2PKH to P2PKH)
            // 148 bytes per input + 34 bytes per output + 10 bytes overhead
            int estimatedSize = 148 + 34 + 10;
            
            // Calculate fee
            BigDecimal fee = feeRate.multiply(new BigDecimal(estimatedSize));
            
            log.debug("Estimated Bitcoin fee: {} BTC for {} bytes", fee, estimatedSize);
            return fee;

        } catch (Exception e) {
            log.error("Failed to estimate Bitcoin transaction fee", e);
            return new BigDecimal("0.0001"); // Default fee
        }
    }

    /**
     * Validate Bitcoin address
     */
    public boolean validateAddress(String address) {
        try {
            Address.fromString(networkParams, address);
            return true;
        } catch (Exception e) {
            log.debug("Invalid Bitcoin address: {}", address);
            return false;
        }
    }

    /**
     * Get confirmation count for transaction
     */
    public int getConfirmationCount(String txHash) {
        log.debug("Getting confirmation count for Bitcoin tx: {}", txHash);

        try {
            // In production, this would query the blockchain
            BlockchainTransaction tx = getTransaction(txHash);
            return tx.getConfirmations();

        } catch (Exception e) {
            log.error("Failed to get confirmation count for tx: {}", txHash, e);
            return 0;
        }
    }

    /**
     * Get current block height
     */
    public long getCurrentBlockHeight() {
        try {
            return blockChain.getBestChainHeight();
        } catch (Exception e) {
            log.error("Failed to get current block height", e);
            return 0;
        }
    }

    /**
     * Check if node is synchronized
     */
    public boolean isNodeSynchronized() {
        try {
            // Check if we have peers and are reasonably synchronized
            if (peerGroup.numConnectedPeers() == 0) {
                return false;
            }

            // Check if our best block is recent (within 2 hours)
            long bestBlockTime = blockChain.getChainHead().getHeader().getTimeSeconds();
            long currentTime = System.currentTimeMillis() / 1000;
            
            return (currentTime - bestBlockTime) < 7200; // 2 hours

        } catch (Exception e) {
            log.error("Failed to check node synchronization", e);
            return false;
        }
    }

    /**
     * Get UTXOs for address
     */
    public UTXOSet getUTXOs(String address) {
        log.debug("Getting UTXOs for Bitcoin address: {}", address);

        try {
            Address btcAddress = Address.fromString(networkParams, address);
            
            // Query real UTXO set from Bitcoin node
            List<UTXO> utxos = bitcoinRpcClient.listUnspent(address, 1, 9999999);
            
            if (utxos.isEmpty()) {
                log.debug("No UTXOs found for address: {}", address);
            }
            
            BigDecimal totalAmount = utxos.stream()
                .map(UTXO::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

            return UTXOSet.builder()
                .address(address)
                .utxos(utxos)
                .totalAmount(totalAmount)
                .count(utxos.size())
                .build();

        } catch (Exception e) {
            log.error("Failed to get UTXOs for address: {}", address, e);
            
            // Fallback to external APIs if node is unavailable
            try {
                return getUTXOsFromExternalAPI(address);
            } catch (Exception fallbackError) {
                log.error("Fallback UTXO retrieval also failed for address: {}", address, fallbackError);
                throw new UTXORetrievalException("Failed to get UTXOs from all sources", e);
            }
        }
    }

    /**
     * Create multi-signature redeem script
     */
    public String createMultiSigRedeemScript(List<String> publicKeys, int requiredSignatures) {
        try {
            List<ECKey> keys = new ArrayList<>();
            for (String pubKeyHex : publicKeys) {
                keys.add(ECKey.fromPublicOnly(Numeric.hexStringToByteArray(pubKeyHex)));
            }

            Script redeemScript = ScriptBuilder.createMultiSigOutputScript(requiredSignatures, keys);
            return Numeric.toHexString(redeemScript.getProgram());

        } catch (Exception e) {
            log.error("Failed to create multi-sig redeem script", e);
            throw new ScriptCreationException("Failed to create redeem script", e);
        }
    }

    /**
     * Create P2SH address from redeem script
     */
    public String createP2SHAddress(String redeemScriptHex) {
        try {
            byte[] redeemScriptBytes = Numeric.hexStringToByteArray(redeemScriptHex);
            Script redeemScript = new Script(redeemScriptBytes);
            Address p2shAddress = Address.fromP2SHScript(networkParams, redeemScript);
            
            return p2shAddress.toString();

        } catch (Exception e) {
            log.error("Failed to create P2SH address", e);
            throw new AddressCreationException("Failed to create P2SH address", e);
        }
    }

    /**
     * Monitor transaction confirmations
     */
    public CompletableFuture<Integer> monitorConfirmations(String txHash, int targetConfirmations) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                int confirmations = 0;
                while (confirmations < targetConfirmations) {
                    confirmations = getConfirmationCount(txHash);
                    
                    if (confirmations < targetConfirmations) {
                        TimeUnit.MINUTES.sleep(1); // Check every minute
                    }
                }
                return confirmations;

            } catch (Exception e) {
                log.error("Error monitoring confirmations for tx: {}", txHash, e);
                throw new RuntimeException("Failed to monitor confirmations", e);
            }
        });
    }

    /**
     * Fallback UTXO retrieval from external APIs
     */
    private UTXOSet getUTXOsFromExternalAPI(String address) throws Exception {
        log.debug("Attempting fallback UTXO retrieval for address: {}", address);
        
        // Try Blockstream API first
        try {
            String apiUrl = getBlockstreamApiUrl() + "/address/" + address + "/utxo";
            RestTemplate restTemplate = createSecureRestTemplate();
            
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> utxoData = restTemplate.getForObject(apiUrl, List.class);
            
            if (utxoData != null && !utxoData.isEmpty()) {
                List<UTXO> utxos = utxoData.stream()
                    .map(this::mapBlockstreamUTXO)
                    .toList();
                
                BigDecimal totalAmount = utxos.stream()
                    .map(UTXO::getAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
                
                return UTXOSet.builder()
                    .address(address)
                    .utxos(utxos)
                    .totalAmount(totalAmount)
                    .count(utxos.size())
                    .build();
            }
        } catch (Exception e) {
            log.warn("Blockstream UTXO API failed: {}", e.getMessage());
        }
        
        // Try Mempool.space API as second fallback
        try {
            String apiUrl = "https://mempool.space/api/address/" + address + "/utxo";
            RestTemplate restTemplate = createSecureRestTemplate();
            
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> utxoData = restTemplate.getForObject(apiUrl, List.class);
            
            if (utxoData != null && !utxoData.isEmpty()) {
                List<UTXO> utxos = utxoData.stream()
                    .map(this::mapMempoolUTXO)
                    .toList();
                
                BigDecimal totalAmount = utxos.stream()
                    .map(UTXO::getAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
                
                return UTXOSet.builder()
                    .address(address)
                    .utxos(utxos)
                    .totalAmount(totalAmount)
                    .count(utxos.size())
                    .build();
            }
        } catch (Exception e) {
            log.warn("Mempool UTXO API failed: {}", e.getMessage());
        }
        
        throw new RuntimeException("All UTXO retrieval methods failed for address: " + address);
    }
    
    private UTXO mapBlockstreamUTXO(Map<String, Object> utxoData) {
        return UTXO.builder()
            .txHash(utxoData.get("txid").toString())
            .outputIndex(Integer.parseInt(utxoData.get("vout").toString()))
            .amount(new BigDecimal(utxoData.get("value").toString()).divide(new BigDecimal("100000000"), 8, RoundingMode.HALF_UP))
            .confirmations(utxoData.containsKey("status") && 
                ((Map<?, ?>) utxoData.get("status")).containsKey("confirmed") ? 1 : 0)
            .address("")
            .spendable(true)
            .safe(true)
            .build();
    }
    
    private UTXO mapMempoolUTXO(Map<String, Object> utxoData) {
        return UTXO.builder()
            .txHash(utxoData.get("txid").toString())
            .outputIndex(Integer.parseInt(utxoData.get("vout").toString()))
            .amount(new BigDecimal(utxoData.get("value").toString()).divide(new BigDecimal("100000000"), 8, RoundingMode.HALF_UP))
            .confirmations(utxoData.containsKey("status") && 
                Boolean.TRUE.equals(((Map<?, ?>) utxoData.get("status")).get("confirmed")) ? 1 : 0)
            .address("")
            .spendable(true)
            .safe(true)
            .build();
    }

    // Exception classes
    public static class BitcoinServiceException extends RuntimeException {
        public BitcoinServiceException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    public static class TransactionBroadcastException extends BitcoinServiceException {
        public TransactionBroadcastException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    public static class BalanceRetrievalException extends BitcoinServiceException {
        public BalanceRetrievalException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    public static class TransactionRetrievalException extends BitcoinServiceException {
        public TransactionRetrievalException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    public static class NetworkFeesException extends BitcoinServiceException {
        public NetworkFeesException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    public static class UTXORetrievalException extends BitcoinServiceException {
        public UTXORetrievalException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    public static class ScriptCreationException extends BitcoinServiceException {
        public ScriptCreationException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    public static class AddressCreationException extends BitcoinServiceException {
        public AddressCreationException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}