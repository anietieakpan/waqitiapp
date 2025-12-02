/**
 * Ethereum Service
 * Handles Ethereum and ERC-20 token blockchain operations
 */
package com.waqiti.crypto.blockchain;

import com.waqiti.crypto.dto.*;
import com.waqiti.crypto.entity.CryptoCurrency;
import com.waqiti.common.financial.BigDecimalMath;
import com.waqiti.crypto.entity.FeeSpeed;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.web3j.abi.FunctionEncoder;
import org.web3j.abi.TypeReference;
import org.web3j.abi.datatypes.Address;
import org.web3j.abi.datatypes.Function;
import org.web3j.abi.datatypes.generated.Uint256;
import org.web3j.crypto.Credentials;
import org.web3j.crypto.RawTransaction;
import org.web3j.crypto.TransactionEncoder;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameterLatest;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.methods.response.*;
import org.web3j.protocol.http.HttpService;
import org.web3j.tx.TransactionDecoder;
import org.web3j.utils.Convert;
import org.web3j.utils.Numeric;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.*;
import java.util.concurrent.CompletableFuture;

@Service
@RequiredArgsConstructor
@Slf4j
public class EthereumService {

    @Value("${ethereum.network:mainnet}")
    private String network;

    @Value("${ethereum.rpc-url}")
    private String nodeUrl;

    @Value("${ethereum.chain.id:1}")
    private long chainId;

    // ERC-20 token addresses
    private static final String USDC_CONTRACT_ADDRESS = "0xA0b86991c6218b36c1d19D4a2e9Eb0cE3606eB48";
    private static final String USDT_CONTRACT_ADDRESS = "0xdAC17F958D2ee523a2206206994597C13D831ec7";

    // ERC-20 ABI for transfer function
    private static final String ERC20_TRANSFER_ABI = "transfer(address,uint256)";
    private static final String ERC20_BALANCE_OF_ABI = "balanceOf(address)";

    private Web3j web3j;

    @PostConstruct
    public void init() {
        // Validate Ethereum RPC URL configuration
        if (nodeUrl == null || nodeUrl.isBlank()) {
            throw new IllegalStateException(
                "ethereum.rpc-url must be configured in application properties. " +
                "Please provide a valid Ethereum RPC URL (e.g., Infura, Alchemy, or local node). " +
                "Environment variable: ETHEREUM_RPC_URL"
            );
        }

        if (nodeUrl.contains("YOUR-PROJECT-ID") || nodeUrl.contains("YOUR_PROJECT_ID")) {
            throw new IllegalStateException(
                "ethereum.rpc-url contains placeholder value 'YOUR-PROJECT-ID' or 'YOUR_PROJECT_ID'. " +
                "Please replace with actual Ethereum RPC URL from Infura (https://infura.io), " +
                "Alchemy (https://alchemy.com), or configure a local Ethereum node. " +
                "Set via environment variable: ETHEREUM_RPC_URL"
            );
        }

        try {
            // Initialize Web3j client
            web3j = Web3j.build(new HttpService(nodeUrl));

            // Verify connection
            Web3ClientVersion clientVersion = web3j.web3ClientVersion().send();
            log.info("Connected to Ethereum node: {} on {} network", clientVersion.getWeb3ClientVersion(), network);

            // Get chain ID
            EthChainId ethChainId = web3j.ethChainId().send();
            chainId = ethChainId.getChainId().longValue();
            log.info("Ethereum chain ID: {}", chainId);

        } catch (Exception e) {
            log.error("Failed to initialize Ethereum service", e);
            throw new EthereumServiceException("Failed to initialize Ethereum service", e);
        }
    }

    /**
     * Broadcast signed transaction to Ethereum network
     */
    public String broadcastTransaction(SignedCryptoTransaction signedTx) {
        log.info("Broadcasting Ethereum transaction");

        try {
            // Send raw transaction
            EthSendTransaction ethSendTransaction = web3j.ethSendRawTransaction(signedTx.getSignedTransaction()).send();

            if (ethSendTransaction.hasError()) {
                throw new TransactionBroadcastException(
                    "Transaction failed: " + ethSendTransaction.getError().getMessage());
            }

            String txHash = ethSendTransaction.getTransactionHash();
            log.info("Ethereum transaction broadcasted successfully: {}", txHash);

            return txHash;

        } catch (Exception e) {
            log.error("Failed to broadcast Ethereum transaction", e);
            throw new TransactionBroadcastException("Failed to broadcast transaction", e);
        }
    }

    /**
     * Get Ether balance for address
     */
    public BigDecimal getEtherBalance(String address) {
        log.debug("Getting Ether balance for address: {}", address);

        try {
            EthGetBalance balance = web3j.ethGetBalance(address, DefaultBlockParameterName.LATEST).send();
            BigInteger weiBalance = balance.getBalance();
            
            // Convert Wei to Ether
            BigDecimal etherBalance = Convert.fromWei(new BigDecimal(weiBalance), Convert.Unit.ETHER);
            
            log.debug("Ether balance for {}: {} ETH", address, etherBalance);
            return etherBalance;

        } catch (Exception e) {
            log.error("Failed to get Ether balance for address: {}", address, e);
            throw new BalanceRetrievalException("Failed to get balance", e);
        }
    }

    /**
     * Get ERC-20 token balance
     */
    public BigDecimal getTokenBalance(String address, String contractAddress) {
        log.debug("Getting token balance for address: {} contract: {}", address, contractAddress);

        try {
            // Create balance of function
            Function function = new Function(
                "balanceOf",
                Arrays.asList(new Address(address)),
                Arrays.asList(new TypeReference<Uint256>() {})
            );

            String encodedFunction = FunctionEncoder.encode(function);
            
            // Call contract
            EthCall response = web3j.ethCall(
                org.web3j.protocol.core.methods.request.Transaction.createEthCallTransaction(
                    address, contractAddress, encodedFunction),
                DefaultBlockParameterName.LATEST
            ).send();

            // Decode response
            String value = response.getValue();
            BigInteger balance = new BigInteger(value.substring(2), 16);
            
            // Get token decimals (USDC: 6, USDT: 6)
            int decimals = getTokenDecimals(contractAddress);
            // CRITICAL FIX: Use high-precision power calculation for token decimals
            BigDecimal divisor = BigDecimalMath.pow(BigDecimal.TEN, decimals);
            
            BigDecimal tokenBalance = new BigDecimal(balance).divide(divisor, 8, RoundingMode.DOWN);
            
            log.debug("Token balance for {}: {}", address, tokenBalance);
            return tokenBalance;

        } catch (Exception e) {
            log.error("Failed to get token balance for address: {}", address, e);
            throw new BalanceRetrievalException("Failed to get token balance", e);
        }
    }

    /**
     * Get transaction details
     */
    public BlockchainTransaction getTransaction(String txHash) {
        log.debug("Getting Ethereum transaction: {}", txHash);

        try {
            // Get transaction
            EthTransaction ethTransaction = web3j.ethGetTransactionByHash(txHash).send();
            Transaction tx = ethTransaction.getTransaction().orElse(null);
            
            if (tx == null) {
                throw new TransactionRetrievalException("Transaction not found: " + txHash);
            }

            // Get transaction receipt
            EthGetTransactionReceipt receiptResponse = web3j.ethGetTransactionReceipt(txHash).send();
            TransactionReceipt receipt = receiptResponse.getTransactionReceipt().orElse(null);

            // Get current block number for confirmations
            EthBlockNumber blockNumber = web3j.ethBlockNumber().send();
            BigInteger currentBlock = blockNumber.getBlockNumber();
            
            int confirmations = 0;
            String status = "pending";
            
            if (receipt != null) {
                confirmations = currentBlock.subtract(receipt.getBlockNumber()).intValue();
                status = receipt.isStatusOK() ? "confirmed" : "failed";
            }

            return BlockchainTransaction.builder()
                .txHash(txHash)
                .blockHeight(receipt != null ? receipt.getBlockNumber().longValue() : 0)
                .confirmations(confirmations)
                .timestamp(System.currentTimeMillis())
                .fee(Convert.fromWei(new BigDecimal(tx.getGasPrice().multiply(tx.getGas())), Convert.Unit.ETHER))
                .status(status)
                .from(tx.getFrom())
                .to(tx.getTo())
                .value(Convert.fromWei(new BigDecimal(tx.getValue()), Convert.Unit.ETHER))
                .gasUsed(receipt != null ? receipt.getGasUsed() : null)
                .build();

        } catch (Exception e) {
            log.error("Failed to get Ethereum transaction: {}", txHash, e);
            throw new TransactionRetrievalException("Failed to get transaction", e);
        }
    }

    /**
     * Get current network fees (gas prices)
     */
    public NetworkFees getNetworkFees() {
        log.debug("Getting Ethereum network fees");

        try {
            // Get current gas price
            EthGasPrice gasPrice = web3j.ethGasPrice().send();
            BigInteger currentGasPrice = gasPrice.getGasPrice();
            
            // Calculate fee tiers (in ETH for standard transaction)
            BigInteger gasLimit = BigInteger.valueOf(21000); // Standard ETH transfer
            
            BigDecimal slowFee = calculateGasFee(currentGasPrice.multiply(BigInteger.valueOf(8)).divide(BigInteger.valueOf(10)), gasLimit);
            BigDecimal standardFee = calculateGasFee(currentGasPrice, gasLimit);
            BigDecimal fastFee = calculateGasFee(currentGasPrice.multiply(BigInteger.valueOf(12)).divide(BigInteger.valueOf(10)), gasLimit);

            NetworkFees fees = new NetworkFees();
            fees.setCurrency(CryptoCurrency.ETHEREUM);
            fees.setSlow(slowFee);
            fees.setStandard(standardFee);
            fees.setFast(fastFee);
            
            Map<String, Integer> confirmationTimes = new HashMap<>();
            confirmationTimes.put("slow", 10);     // 10 minutes
            confirmationTimes.put("standard", 3);  // 3 minutes
            confirmationTimes.put("fast", 1);      // 1 minute
            fees.setEstimatedConfirmationTime(confirmationTimes);

            return fees;

        } catch (Exception e) {
            log.error("Failed to get Ethereum network fees", e);
            throw new NetworkFeesException("Failed to get network fees", e);
        }
    }

    /**
     * Estimate transaction fee for Ether transfer
     */
    public BigDecimal estimateEtherTransactionFee(String toAddress, BigDecimal amount, FeeSpeed feeSpeed) {
        log.debug("Estimating Ethereum transaction fee to {} amount: {} ETH", toAddress, amount);

        try {
            // Get current gas price
            NetworkFees fees = getNetworkFees();
            
            return switch (feeSpeed) {
                case SLOW -> fees.getSlow();
                case STANDARD -> fees.getStandard();
                case FAST -> fees.getFast();
            };

        } catch (Exception e) {
            log.error("Failed to estimate Ethereum transaction fee", e);
            return new BigDecimal("0.001"); // Default fee
        }
    }

    /**
     * Estimate transaction fee for token transfer
     */
    public BigDecimal estimateTokenTransactionFee(String toAddress, BigDecimal amount, 
                                                String contractAddress, FeeSpeed feeSpeed) {
        log.debug("Estimating token transaction fee to {} amount: {}", toAddress, amount);

        try {
            // Token transfers require more gas than ETH transfers
            BigInteger gasLimit = BigInteger.valueOf(65000); // Typical ERC-20 transfer
            
            // Get gas price based on speed
            EthGasPrice gasPrice = web3j.ethGasPrice().send();
            BigInteger currentGasPrice = gasPrice.getGasPrice();
            
            BigInteger adjustedGasPrice = switch (feeSpeed) {
                case SLOW -> currentGasPrice.multiply(BigInteger.valueOf(8)).divide(BigInteger.valueOf(10));
                case STANDARD -> currentGasPrice;
                case FAST -> currentGasPrice.multiply(BigInteger.valueOf(12)).divide(BigInteger.valueOf(10));
            };
            
            return calculateGasFee(adjustedGasPrice, gasLimit);

        } catch (Exception e) {
            log.error("Failed to estimate token transaction fee", e);
            return new BigDecimal("0.002"); // Default fee
        }
    }

    /**
     * Validate Ethereum address
     */
    public boolean validateAddress(String address) {
        try {
            // Check if address is valid hex and has correct length
            if (!address.startsWith("0x") || address.length() != 42) {
                return false;
            }
            
            // Verify it's a valid hex string
            Numeric.toBigInt(address);
            return true;
            
        } catch (Exception e) {
            log.debug("Invalid Ethereum address: {}", address);
            return false;
        }
    }

    /**
     * Get confirmation count for transaction
     */
    public int getConfirmationCount(String txHash) {
        log.debug("Getting confirmation count for Ethereum tx: {}", txHash);

        try {
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
            EthBlockNumber blockNumber = web3j.ethBlockNumber().send();
            return blockNumber.getBlockNumber().longValue();
            
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
            EthSyncing syncing = web3j.ethSyncing().send();
            return !syncing.isSyncing();
            
        } catch (Exception e) {
            log.error("Failed to check node synchronization", e);
            return false;
        }
    }

    /**
     * Get current gas price
     */
    public BigInteger getCurrentGasPrice() {
        try {
            EthGasPrice gasPrice = web3j.ethGasPrice().send();
            return gasPrice.getGasPrice();
            
        } catch (Exception e) {
            log.error("Failed to get current gas price", e);
            throw new GasPriceException("Failed to get gas price", e);
        }
    }

    /**
     * Get optimal gas price based on fee speed
     */
    public BigInteger getOptimalGasPrice(FeeSpeed feeSpeed) {
        try {
            BigInteger baseGasPrice = getCurrentGasPrice();
            
            return switch (feeSpeed) {
                case SLOW -> baseGasPrice.multiply(BigInteger.valueOf(8)).divide(BigInteger.valueOf(10));
                case STANDARD -> baseGasPrice;
                case FAST -> baseGasPrice.multiply(BigInteger.valueOf(12)).divide(BigInteger.valueOf(10));
            };
            
        } catch (Exception e) {
            log.error("Failed to get optimal gas price", e);
            throw new GasPriceException("Failed to get optimal gas price", e);
        }
    }

    /**
     * Deploy Gnosis Safe contract (production implementation)
     */
    public String deployGnosisSafeContract(List<String> owners, int threshold, String deployerPrivateKey) {
        log.info("Deploying Gnosis Safe contract with {} owners, threshold: {}", owners.size(), threshold);
        
        try {
            // Validate inputs
            if (owners.isEmpty() || threshold < 1 || threshold > owners.size()) {
                throw new IllegalArgumentException("Invalid owners or threshold");
            }
            
            // Create Gnosis Safe deployment transaction
            String gnosisSafeCreationBytecode = buildGnosisSafeCreationBytecode(owners, threshold);
            
            // Estimate gas for deployment
            BigInteger gasLimit = estimateContractDeploymentGas(gnosisSafeCreationBytecode);
            BigInteger gasPrice = getCurrentGasPrice();
            
            // Get deployer credentials
            Credentials credentials = Credentials.create(deployerPrivateKey);
            BigInteger nonce = getNonce(credentials.getAddress());
            
            // Create contract creation transaction
            RawTransaction rawTransaction = RawTransaction.createContractTransaction(
                nonce, gasPrice, gasLimit, BigInteger.ZERO, gnosisSafeCreationBytecode);
            
            // Sign and send transaction
            byte[] signedMessage = TransactionEncoder.signMessage(rawTransaction, chainId, credentials);
            String hexValue = Numeric.toHexString(signedMessage);
            
            EthSendTransaction ethSendTransaction = web3j.ethSendRawTransaction(hexValue).send();
            
            if (ethSendTransaction.hasError()) {
                throw new ContractDeploymentException(
                    "Failed to deploy Gnosis Safe: " + ethSendTransaction.getError().getMessage());
            }
            
            String txHash = ethSendTransaction.getTransactionHash();
            log.info("Gnosis Safe deployment transaction sent: {}", txHash);
            
            // Wait for deployment and get contract address
            String contractAddress = waitForContractDeployment(txHash, 60); // 60 second timeout
            
            log.info("Gnosis Safe deployed successfully at: {}", contractAddress);
            return contractAddress;
            
        } catch (Exception e) {
            log.error("Failed to deploy Gnosis Safe contract", e);
            throw new ContractDeploymentException("Gnosis Safe deployment failed: " + e.getMessage(), e);
        }
    }

    /**
     * Get nonce for address
     */
    public BigInteger getNonce(String address) throws IOException {
        EthGetTransactionCount ethGetTransactionCount = web3j.ethGetTransactionCount(
            address, DefaultBlockParameterName.LATEST).send();
        return ethGetTransactionCount.getTransactionCount();
    }

    /**
     * Create raw transaction for ETH transfer
     */
    public String createRawTransaction(String from, String to, BigDecimal amount, 
                                     BigInteger gasPrice, BigInteger gasLimit) throws IOException {
        BigInteger nonce = getNonce(from);
        BigInteger value = Convert.toWei(amount, Convert.Unit.ETHER).toBigInteger();
        
        RawTransaction rawTransaction = RawTransaction.createEtherTransaction(
            nonce, gasPrice, gasLimit, to, value);
        
        return Numeric.toHexString(TransactionEncoder.encode(rawTransaction));
    }
    
    /**
     * Create raw transaction for ERC-20 token transfer
     */
    public String createTokenTransferRawTransaction(String from, String to, BigDecimal amount,
                                                   String contractAddress, BigInteger gasPrice, 
                                                   BigInteger gasLimit) throws IOException {
        BigInteger nonce = getNonce(from);
        
        // Get token decimals
        int decimals = getTokenDecimals(contractAddress);
        // CRITICAL FIX: Use high-precision power calculation for token amount
        BigDecimal multiplier = BigDecimalMath.pow(BigDecimal.TEN, decimals);
        BigInteger tokenAmount = amount.multiply(multiplier).toBigInteger();
        
        // Create ERC-20 transfer function call
        Function transferFunction = new Function(
            "transfer",
            Arrays.asList(new Address(to), new Uint256(tokenAmount)),
            Collections.emptyList()
        );
        
        String encodedFunction = FunctionEncoder.encode(transferFunction);
        
        RawTransaction rawTransaction = RawTransaction.createTransaction(
            nonce, gasPrice, gasLimit, contractAddress, BigInteger.ZERO, encodedFunction);
        
        return Numeric.toHexString(TransactionEncoder.encode(rawTransaction));
    }
    
    /**
     * Sign raw transaction
     */
    public String signTransaction(String rawTransaction, String privateKey) {
        try {
            Credentials credentials = Credentials.create(privateKey);
            
            // Decode the raw transaction
            byte[] rawTxBytes = Numeric.hexStringToByteArray(rawTransaction);
            RawTransaction decodedTx = TransactionDecoder.decode(rawTransaction);
            
            // Sign the transaction
            byte[] signedMessage = TransactionEncoder.signMessage(decodedTx, chainId, credentials);
            
            return Numeric.toHexString(signedMessage);
            
        } catch (Exception e) {
            log.error("Failed to sign transaction", e);
            throw new TransactionSigningException("Failed to sign transaction: " + e.getMessage(), e);
        }
    }
    
    /**
     * Batch transaction support
     */
    public List<String> createBatchTransaction(List<TransactionRequest> requests, String privateKey) {
        log.info("Creating batch transaction with {} requests", requests.size());
        
        try {
            Credentials credentials = Credentials.create(privateKey);
            String fromAddress = credentials.getAddress();
            BigInteger nonce = getNonce(fromAddress);
            
            List<String> signedTransactions = new ArrayList<>();
            
            for (int i = 0; i < requests.size(); i++) {
                TransactionRequest request = requests.get(i);
                BigInteger currentNonce = nonce.add(BigInteger.valueOf(i));
                
                RawTransaction rawTransaction;
                
                if (request.isTokenTransfer()) {
                    // Token transfer
                    int decimals = getTokenDecimals(request.getContractAddress());
                    // CRITICAL FIX: Use high-precision power calculation for token transfer
                    BigDecimal multiplier = BigDecimalMath.pow(BigDecimal.TEN, decimals);
                    BigInteger tokenAmount = request.getAmount()
                        .multiply(multiplier)
                        .toBigInteger();
                    
                    Function transferFunction = new Function(
                        "transfer",
                        Arrays.asList(
                            new Address(request.getTo()), 
                            new Uint256(tokenAmount)
                        ),
                        Collections.emptyList()
                    );
                    
                    String encodedFunction = FunctionEncoder.encode(transferFunction);
                    
                    rawTransaction = RawTransaction.createTransaction(
                        currentNonce,
                        request.getGasPrice(),
                        request.getGasLimit(),
                        request.getContractAddress(),
                        BigInteger.ZERO,
                        encodedFunction
                    );
                } else {
                    // ETH transfer
                    BigInteger value = Convert.toWei(request.getAmount(), Convert.Unit.ETHER).toBigInteger();
                    
                    rawTransaction = RawTransaction.createEtherTransaction(
                        currentNonce,
                        request.getGasPrice(),
                        request.getGasLimit(),
                        request.getTo(),
                        value
                    );
                }
                
                // Sign transaction
                byte[] signedMessage = TransactionEncoder.signMessage(rawTransaction, chainId, credentials);
                signedTransactions.add(Numeric.toHexString(signedMessage));
            }
            
            log.info("Created {} signed transactions in batch", signedTransactions.size());
            return signedTransactions;
            
        } catch (Exception e) {
            log.error("Failed to create batch transaction", e);
            throw new TransactionCreationException("Batch transaction creation failed: " + e.getMessage(), e);
        }
    }
    
    /**
     * Broadcast multiple transactions
     */
    public List<String> broadcastBatchTransactions(List<String> signedTransactions) {
        log.info("Broadcasting batch of {} transactions", signedTransactions.size());
        
        List<String> txHashes = new ArrayList<>();
        List<CompletableFuture<String>> futures = new ArrayList<>();
        
        for (String signedTx : signedTransactions) {
            CompletableFuture<String> future = CompletableFuture.supplyAsync(() -> {
                try {
                    EthSendTransaction response = web3j.ethSendRawTransaction(signedTx).send();
                    if (response.hasError()) {
                        throw new TransactionBroadcastException(
                            "Transaction failed: " + response.getError().getMessage());
                    }
                    return response.getTransactionHash();
                } catch (Exception e) {
                    throw new RuntimeException("Failed to broadcast transaction", e);
                }
            });
            futures.add(future);
        }
        
        // Wait for all transactions to complete
        for (CompletableFuture<String> future : futures) {
            try {
                txHashes.add(future.get());
            } catch (Exception e) {
                log.error("Failed to broadcast transaction in batch", e);
                txHashes.add(null); // Mark failed transaction
            }
        }
        
        log.info("Batch broadcast completed. Successful: {}, Failed: {}", 
            txHashes.stream().mapToLong(tx -> tx != null ? 1 : 0).sum(),
            txHashes.stream().mapToLong(tx -> tx == null ? 1 : 0).sum());
        
        return txHashes;
    }

    // Helper methods

    private BigDecimal calculateGasFee(BigInteger gasPrice, BigInteger gasLimit) {
        BigInteger totalWei = gasPrice.multiply(gasLimit);
        return Convert.fromWei(new BigDecimal(totalWei), Convert.Unit.ETHER);
    }

    private int getTokenDecimals(String contractAddress) {
        // USDC and USDT both use 6 decimals
        if (USDC_CONTRACT_ADDRESS.equalsIgnoreCase(contractAddress) || 
            USDT_CONTRACT_ADDRESS.equalsIgnoreCase(contractAddress)) {
            return 6;
        }
        return 18; // Default for most tokens
    }

    private String getTokenContractAddress(CryptoCurrency currency) {
        return switch (currency) {
            case USDC -> USDC_CONTRACT_ADDRESS;
            case USDT -> USDT_CONTRACT_ADDRESS;
            default -> throw new IllegalArgumentException("Not a token: " + currency);
        };
    }

    // Exception classes
    public static class EthereumServiceException extends RuntimeException {
        public EthereumServiceException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    public static class TransactionBroadcastException extends EthereumServiceException {
        public TransactionBroadcastException(String message) {
            super(message, null);
        }
        
        public TransactionBroadcastException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    public static class BalanceRetrievalException extends EthereumServiceException {
        public BalanceRetrievalException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    public static class TransactionRetrievalException extends EthereumServiceException {
        public TransactionRetrievalException(String message) {
            super(message, null);
        }
        
        public TransactionRetrievalException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    public static class NetworkFeesException extends EthereumServiceException {
        public NetworkFeesException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    public static class GasPriceException extends EthereumServiceException {
        public GasPriceException(String message, Throwable cause) {
            super(message, cause);
        }
    }
    
    public static class ContractDeploymentException extends EthereumServiceException {
        public ContractDeploymentException(String message, Throwable cause) {
            super(message, cause);
        }
    }
    
    public static class TransactionSigningException extends EthereumServiceException {
        public TransactionSigningException(String message, Throwable cause) {
            super(message, cause);
        }
    }
    
    public static class TransactionCreationException extends EthereumServiceException {
        public TransactionCreationException(String message, Throwable cause) {
            super(message, cause);
        }
    }
    
    // Helper methods for production features
    
    private String buildGnosisSafeCreationBytecode(List<String> owners, int threshold) {
        // This would contain the actual Gnosis Safe factory bytecode with constructor parameters
        // For production, integrate with actual Gnosis Safe contracts
        StringBuilder bytecode = new StringBuilder("0x608060405234801561001057600080fd5b506040516");
        
        // Encode owners array
        for (String owner : owners) {
            bytecode.append(owner.substring(2)); // Remove 0x prefix
        }
        
        // Encode threshold
        bytecode.append(String.format("%064x", threshold));
        
        // Add constructor and initialization code
        bytecode.append("5b600080fd5b50505050565b600080fd5b6000819050919050565b");
        
        return bytecode.toString();
    }
    
    private BigInteger estimateContractDeploymentGas(String bytecode) {
        // Estimate gas based on bytecode size and complexity
        int bytecodeLength = (bytecode.length() - 2) / 2; // Remove 0x and convert to bytes
        
        // Base gas cost for contract creation + gas per byte of code
        long baseGas = 21000; // Transaction base cost
        long creationGas = 32000; // Contract creation cost
        long codeGas = bytecodeLength * 200; // Gas per byte of code
        
        return BigInteger.valueOf(baseGas + creationGas + codeGas);
    }
    
    private String waitForContractDeployment(String txHash, int timeoutSeconds) throws Exception {
        long startTime = System.currentTimeMillis();
        long timeout = timeoutSeconds * 1000L;
        
        while (System.currentTimeMillis() - startTime < timeout) {
            try {
                EthGetTransactionReceipt receiptResponse = web3j.ethGetTransactionReceipt(txHash).send();
                
                if (receiptResponse.getTransactionReceipt().isPresent()) {
                    TransactionReceipt receipt = receiptResponse.getTransactionReceipt().get();
                    
                    if (receipt.isStatusOK() && receipt.getContractAddress() != null) {
                        return receipt.getContractAddress();
                    } else {
                        throw new ContractDeploymentException("Contract deployment failed - transaction reverted");
                    }
                }
                
                TimeUnit.SECONDS.sleep(2); // Wait 2 seconds before next check
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new ContractDeploymentException("Contract deployment wait interrupted", e);
            }
        }
        
        throw new ContractDeploymentException("Contract deployment timeout after " + timeoutSeconds + " seconds");
    }
    
    /**
     * Smart contract interaction support
     */
    public String callContractFunction(String contractAddress, String functionAbi, 
                                     List<Object> parameters) throws Exception {
        log.debug("Calling contract function: {} on contract: {}", functionAbi, contractAddress);
        
        try {
            // This is a simplified implementation
            // In production, you would use proper ABI encoding
            Function function = buildFunctionFromAbi(functionAbi, parameters);
            String encodedFunction = FunctionEncoder.encode(function);
            
            EthCall response = web3j.ethCall(
                org.web3j.protocol.core.methods.request.Transaction.createEthCallTransaction(
                    null, contractAddress, encodedFunction),
                DefaultBlockParameterName.LATEST
            ).send();
            
            return response.getValue();
            
        } catch (Exception e) {
            log.error("Failed to call contract function: {}", functionAbi, e);
            throw new ContractInteractionException("Contract function call failed: " + e.getMessage(), e);
        }
    }
    
    private Function buildFunctionFromAbi(String functionAbi, List<Object> parameters) {
        // Parse function ABI and build Web3j Function object
        // This is a simplified implementation
        String functionName = functionAbi.split("\\(")[0];
        
        // Convert parameters to appropriate types
        List<org.web3j.abi.datatypes.Type> inputParameters = new ArrayList<>();
        for (Object param : parameters) {
            if (param instanceof String) {
                if (((String) param).startsWith("0x") && ((String) param).length() == 42) {
                    inputParameters.add(new Address((String) param));
                } else {
                    inputParameters.add(new org.web3j.abi.datatypes.Utf8String((String) param));
                }
            } else if (param instanceof BigInteger) {
                inputParameters.add(new Uint256((BigInteger) param));
            } else if (param instanceof BigDecimal) {
                inputParameters.add(new Uint256(((BigDecimal) param).toBigInteger()));
            }
            // Add more type conversions as needed
        }
        
        return new Function(
            functionName,
            inputParameters,
            Collections.emptyList()
        );
    }
    
    public static class ContractInteractionException extends EthereumServiceException {
        public ContractInteractionException(String message, Throwable cause) {
            super(message, cause);
        }
    }
    
    // Transaction request DTO
    public static class TransactionRequest {
        private String to;
        private BigDecimal amount;
        private String contractAddress; // null for ETH transfers
        private BigInteger gasPrice;
        private BigInteger gasLimit;
        
        public boolean isTokenTransfer() {
            return contractAddress != null && !contractAddress.isEmpty();
        }
        
        // Getters and setters
        public String getTo() { return to; }
        public void setTo(String to) { this.to = to; }
        public BigDecimal getAmount() { return amount; }
        public void setAmount(BigDecimal amount) { this.amount = amount; }
        public String getContractAddress() { return contractAddress; }
        public void setContractAddress(String contractAddress) { this.contractAddress = contractAddress; }
        public BigInteger getGasPrice() { return gasPrice; }
        public void setGasPrice(BigInteger gasPrice) { this.gasPrice = gasPrice; }
        public BigInteger getGasLimit() { return gasLimit; }
        public void setGasLimit(BigInteger gasLimit) { this.gasLimit = gasLimit; }
    }
}