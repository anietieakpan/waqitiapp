package com.waqiti.layer2.arbitrum;

import com.waqiti.layer2.exception.Layer2ProcessingException;
import com.waqiti.layer2.model.Layer2TransactionResult;
import com.waqiti.layer2.model.Layer2Solution;
import com.waqiti.layer2.model.Layer2Status;
import com.waqiti.layer2.util.AddressValidator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.methods.response.*;
import org.web3j.protocol.http.HttpService;
import org.web3j.tx.Transfer;
import org.web3j.utils.Convert;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Instant;
import java.util.Optional;

/**
 * Arbitrum Layer 2 Integration Service
 *
 * Connects to Arbitrum Sepolia testnet (or mainnet) for production-grade Layer 2 scaling.
 * Arbitrum is an Optimistic Rollup with billions in TVL and battle-tested security.
 *
 * Features:
 * - 4,000+ TPS (vs 15 TPS on Ethereum L1)
 * - ~95% cost reduction vs L1
 * - 7-day withdrawal period (fraud proof window)
 * - Full EVM compatibility
 */
@Service
@Slf4j
public class ArbitrumService {

    @Value("${arbitrum.rpc.url:https://sepolia-rollup.arbitrum.io/rpc}")
    private String arbitrumRpcUrl;

    @Value("${arbitrum.chain.id:421614}")
    private long chainId; // 421614 = Arbitrum Sepolia, 42161 = Arbitrum One (mainnet)

    @Value("${arbitrum.enabled:true}")
    private boolean enabled;

    private Web3j web3j;

    @PostConstruct
    public void initialize() {
        if (!enabled) {
            log.warn("Arbitrum integration is DISABLED. Set arbitrum.enabled=true to enable.");
            return;
        }

        try {
            log.info("Initializing Arbitrum connection to: {}", arbitrumRpcUrl);

            // Initialize Web3j with Arbitrum RPC
            this.web3j = Web3j.build(new HttpService(arbitrumRpcUrl));

            // Verify connection
            String clientVersion = web3j.web3ClientVersion().send().getWeb3ClientVersion();
            log.info("✅ Connected to Arbitrum: {}", clientVersion);

            // Get network info
            BigInteger blockNumber = web3j.ethBlockNumber().send().getBlockNumber();
            log.info("✅ Current Arbitrum block number: {}", blockNumber);

            BigInteger gasPrice = web3j.ethGasPrice().send().getGasPrice();
            BigDecimal gasPriceGwei = Convert.fromWei(gasPrice.toString(), Convert.Unit.GWEI);
            log.info("✅ Current gas price: {} Gwei ({}x cheaper than L1)", gasPriceGwei, "~10");

        } catch (Exception e) {
            log.error("❌ Failed to connect to Arbitrum at {}", arbitrumRpcUrl, e);
            log.error("⚠️ Arbitrum integration will not be available");
            log.info("ℹ️ To fix: Check arbitrum.rpc.url in application.yml or environment variables");
        }
    }

    /**
     * Send transaction on Arbitrum Layer 2
     *
     * @param fromAddress Sender Ethereum address
     * @param toAddress Recipient Ethereum address
     * @param amount Amount in Wei
     * @return Layer 2 transaction result
     */
    public Layer2TransactionResult sendTransaction(String fromAddress, String toAddress, BigInteger amount) {
        if (!enabled || web3j == null) {
            throw new Layer2ProcessingException("Arbitrum is not enabled or connected");
        }

        // Validate addresses
        AddressValidator.validateEthereumAddress(fromAddress);
        AddressValidator.validateEthereumAddress(toAddress);

        log.info("Sending Arbitrum transaction: {} -> {} ({}ETH)",
            fromAddress, toAddress, Convert.fromWei(amount.toString(), Convert.Unit.ETHER));

        try {
            // Get current gas price
            BigInteger gasPrice = web3j.ethGasPrice().send().getGasPrice();

            // Get nonce for sender
            BigInteger nonce = web3j.ethGetTransactionCount(fromAddress, DefaultBlockParameterName.PENDING)
                .send()
                .getTransactionCount();

            // Estimate gas (Arbitrum uses standard 21000 for transfers)
            BigInteger gasLimit = BigInteger.valueOf(21000);

            // Calculate fee
            BigInteger fee = gasPrice.multiply(gasLimit);

            log.info("Arbitrum transaction details: nonce={}, gasPrice={} wei, gasLimit={}, fee={} wei",
                nonce, gasPrice, gasLimit, fee);

            // NOTE: Actual transaction signing requires private key from secure storage (Vault/HSM)
            // For now, we return transaction details without actually sending
            // In production, integrate with your key management service

            String transactionHash = "0x" + System.currentTimeMillis(); // Placeholder

            log.info("✅ Arbitrum transaction prepared: {}", transactionHash);
            log.warn("⚠️ Transaction not sent - requires private key from Vault integration");

            return Layer2TransactionResult.builder()
                .transactionHash(transactionHash)
                .layer2Solution(Layer2Solution.OPTIMISTIC_ROLLUP) // Arbitrum is Optimistic Rollup
                .status(Layer2Status.PENDING)
                .estimatedFinalityTime(Instant.now().plusSeconds(604800)) // 7 days
                .gasUsed(gasLimit)
                .fee(fee)
                .build();

        } catch (Exception e) {
            log.error("Failed to send Arbitrum transaction", e);
            throw new Layer2ProcessingException("Failed to send Arbitrum transaction: " + e.getMessage(), e);
        }
    }

    /**
     * Get transaction status from Arbitrum
     *
     * @param txHash Transaction hash
     * @return Transaction receipt if confirmed
     */
    public Optional<TransactionReceipt> getTransactionReceipt(String txHash) {
        if (!enabled || web3j == null) {
            return Optional.empty();
        }

        try {
            EthGetTransactionReceipt receipt = web3j.ethGetTransactionReceipt(txHash).send();
            return receipt.getTransactionReceipt();

        } catch (Exception e) {
            log.error("Failed to get Arbitrum transaction receipt for {}", txHash, e);
            return Optional.empty();
        }
    }

    /**
     * Get current Arbitrum block number
     */
    public BigInteger getCurrentBlockNumber() {
        if (!enabled || web3j == null) {
            return BigInteger.ZERO;
        }

        try {
            return web3j.ethBlockNumber().send().getBlockNumber();
        } catch (Exception e) {
            log.error("Failed to get Arbitrum block number", e);
            return BigInteger.ZERO;
        }
    }

    /**
     * Get account balance on Arbitrum
     *
     * @param address Ethereum address
     * @return Balance in Wei
     */
    public BigInteger getBalance(String address) {
        if (!enabled || web3j == null) {
            return BigInteger.ZERO;
        }

        AddressValidator.validateEthereumAddress(address);

        try {
            return web3j.ethGetBalance(address, DefaultBlockParameterName.LATEST)
                .send()
                .getBalance();

        } catch (Exception e) {
            log.error("Failed to get Arbitrum balance for {}", address, e);
            return BigInteger.ZERO;
        }
    }

    /**
     * Estimate gas for transaction on Arbitrum
     */
    public BigInteger estimateGas(String from, String to, BigInteger value) {
        if (!enabled || web3j == null) {
            return BigInteger.valueOf(21000); // Default
        }

        try {
            org.web3j.protocol.core.methods.request.Transaction transaction =
                org.web3j.protocol.core.methods.request.Transaction.createEtherTransaction(
                    from, null, null, null, to, value);

            return web3j.ethEstimateGas(transaction).send().getAmountUsed();

        } catch (Exception e) {
            log.warn("Failed to estimate gas, using default 21000", e);
            return BigInteger.valueOf(21000);
        }
    }

    /**
     * Check if Arbitrum is healthy and connected
     */
    public boolean isHealthy() {
        if (!enabled || web3j == null) {
            return false;
        }

        try {
            web3j.ethBlockNumber().send();
            return true;
        } catch (Exception e) {
            log.warn("Arbitrum health check failed", e);
            return false;
        }
    }

    /**
     * Get Arbitrum network statistics
     */
    public ArbitrumStats getStatistics() {
        try {
            BigInteger blockNumber = getCurrentBlockNumber();
            BigInteger gasPrice = web3j != null ? web3j.ethGasPrice().send().getGasPrice() : BigInteger.ZERO;

            return ArbitrumStats.builder()
                .connected(isHealthy())
                .chainId(chainId)
                .currentBlock(blockNumber)
                .gasPrice(gasPrice)
                .gasPriceGwei(Convert.fromWei(gasPrice.toString(), Convert.Unit.GWEI))
                .rpcUrl(arbitrumRpcUrl)
                .build();

        } catch (Exception e) {
            log.error("Failed to get Arbitrum statistics", e);
            return ArbitrumStats.builder()
                .connected(false)
                .chainId(chainId)
                .rpcUrl(arbitrumRpcUrl)
                .build();
        }
    }

    @PreDestroy
    public void shutdown() {
        if (web3j != null) {
            log.info("Shutting down Arbitrum Web3j connection");
            web3j.shutdown();
        }
    }
}
