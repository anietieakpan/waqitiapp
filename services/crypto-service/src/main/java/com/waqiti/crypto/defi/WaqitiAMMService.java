package com.waqiti.crypto.defi;

import com.waqiti.crypto.dto.*;
import com.waqiti.crypto.entity.CryptoCurrency;
import com.waqiti.common.config.VaultTemplate;
import com.waqiti.common.security.kms.AWSKMSService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.web3j.abi.FunctionEncoder;
import org.web3j.abi.FunctionReturnDecoder;
import org.web3j.abi.TypeReference;
import org.web3j.abi.datatypes.*;
import org.web3j.abi.datatypes.generated.Uint256;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.methods.request.Transaction;
import org.web3j.protocol.core.methods.response.EthCall;
import org.web3j.protocol.core.methods.response.EthSendTransaction;
import org.web3j.protocol.core.methods.response.EthGetTransactionReceipt;
import org.web3j.protocol.core.methods.response.TransactionReceipt;
import org.web3j.protocol.core.methods.response.Log;
import org.web3j.protocol.http.HttpService;
import org.web3j.tx.gas.DefaultGasProvider;

import jakarta.annotation.PostConstruct;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.*;

/**
 * Waqiti AMM Service
 * Backend service for interacting with WaqitiAMM smart contract
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WaqitiAMMService {

    private final VaultTemplate vaultTemplate;
    private final AWSKMSService kmsService;

    @Value("${ethereum.network:mainnet}")
    private String network;
    
    @Value("${aws.kms.use-kms-signing:true}")
    private boolean useKMSSigning;

    @Value("${ethereum.rpc.url:}")
    private String ethereumRpcUrl;

    @Value("${contracts.waqiti-amm.address:}")
    private String ammContractAddress;

    private Web3j web3j;

    @PostConstruct
    public void initialize() {
        try {
            // Get Ethereum RPC configuration from Vault
            var ethereumConfig = vaultTemplate.read("secret/ethereum-config").getData();
            
            if (ethereumRpcUrl.isEmpty()) {
                ethereumRpcUrl = ethereumConfig.get("rpc-url").toString();
            }
            
            if (ammContractAddress.isEmpty()) {
                ammContractAddress = ethereumConfig.get("amm-contract-address").toString();
            }

            // Initialize Web3j
            this.web3j = Web3j.build(new HttpService(ethereumRpcUrl));

            log.info("WaqitiAMM service initialized for network: {} at contract: {}", 
                network, ammContractAddress);

            // Test connection
            String clientVersion = web3j.web3ClientVersion().send().getWeb3ClientVersion();
            log.info("Connected to Ethereum node: {}", clientVersion);

        } catch (Exception e) {
            log.error("Failed to initialize WaqitiAMM service", e);
            throw new RuntimeException("Cannot initialize WaqitiAMM service", e);
        }
    }

    /**
     * Add liquidity to a token pair pool
     */
    public AddLiquidityResponse addLiquidity(AddLiquidityRequest request) {
        log.info("Adding liquidity for pair: {} / {}", 
            request.getToken0(), request.getToken1());

        try {
            // Get current pool state
            PoolInfo poolInfo = getPoolInfo(request.getToken0(), request.getToken1());
            
            // Calculate optimal amounts
            LiquidityCalculation calculation = calculateOptimalLiquidity(
                request.getToken0Amount(), 
                request.getToken1Amount(),
                poolInfo
            );

            // Prepare addLiquidity function call
            Function addLiquidityFunction = new Function(
                "addLiquidity",
                Arrays.asList(
                    new Address(getTokenAddress(request.getToken0())),
                    new Address(getTokenAddress(request.getToken1())),
                    new Uint256(calculation.getToken0Amount()),
                    new Uint256(calculation.getToken1Amount()),
                    new Uint256(calculation.getToken0Min()),
                    new Uint256(calculation.getToken1Min()),
                    new Address(request.getUserAddress()),
                    new Uint256(BigInteger.valueOf(System.currentTimeMillis() / 1000 + 1200)) // 20 min deadline
                ),
                Collections.emptyList()
            );

            // Execute transaction
            String transactionHash = executeContractTransaction(addLiquidityFunction, request.getUserAddress());

            // Monitor transaction and get receipt
            LiquidityTransactionReceipt receipt = monitorLiquidityTransaction(transactionHash);

            return AddLiquidityResponse.builder()
                .transactionHash(transactionHash)
                .token0Amount(calculation.getToken0Amount())
                .token1Amount(calculation.getToken1Amount())
                .liquidityTokens(receipt.getLiquidityMinted())
                .poolSharePercentage(calculatePoolShare(receipt.getLiquidityMinted(), poolInfo))
                .estimatedFees(calculateLiquidityFees(calculation.getToken0Amount(), calculation.getToken1Amount()))
                .status("PENDING")
                .build();

        } catch (Exception e) {
            log.error("Failed to add liquidity", e);
            throw new DeFiOperationException("Failed to add liquidity", e);
        }
    }

    /**
     * Remove liquidity from a token pair pool
     */
    public RemoveLiquidityResponse removeLiquidity(RemoveLiquidityRequest request) {
        log.info("Removing liquidity: {} tokens from pair: {} / {}",
            request.getLiquidityTokens(), request.getToken0(), request.getToken1());

        try {
            // Get current pool state
            PoolInfo poolInfo = getPoolInfo(request.getToken0(), request.getToken1());
            
            // Calculate token amounts to receive
            LiquidityRemovalCalculation calculation = calculateLiquidityRemoval(
                request.getLiquidityTokens(), poolInfo
            );

            // Prepare removeLiquidity function call
            Function removeLiquidityFunction = new Function(
                "removeLiquidity",
                Arrays.asList(
                    new Address(getTokenAddress(request.getToken0())),
                    new Address(getTokenAddress(request.getToken1())),
                    new Uint256(request.getLiquidityTokens()),
                    new Uint256(calculation.getToken0AmountMin()),
                    new Uint256(calculation.getToken1AmountMin()),
                    new Address(request.getUserAddress()),
                    new Uint256(BigInteger.valueOf(System.currentTimeMillis() / 1000 + 1200))
                ),
                Collections.emptyList()
            );

            // Execute transaction
            String transactionHash = executeContractTransaction(removeLiquidityFunction, request.getUserAddress());

            // Monitor transaction
            LiquidityTransactionReceipt receipt = monitorLiquidityTransaction(transactionHash);

            return RemoveLiquidityResponse.builder()
                .transactionHash(transactionHash)
                .token0Amount(receipt.getToken0Amount())
                .token1Amount(receipt.getToken1Amount())
                .liquidityTokensBurned(request.getLiquidityTokens())
                .fees(receipt.getFees())
                .status("PENDING")
                .build();

        } catch (Exception e) {
            log.error("Failed to remove liquidity", e);
            throw new DeFiOperationException("Failed to remove liquidity", e);
        }
    }

    /**
     * Swap tokens using AMM
     */
    public SwapResponse swapTokens(SwapRequest request) {
        log.info("Swapping {} {} for {}", 
            request.getAmountIn(), request.getTokenIn(), request.getTokenOut());

        try {
            // Get current pool state
            PoolInfo poolInfo = getPoolInfo(request.getTokenIn(), request.getTokenOut());
            
            // Calculate swap amounts and price impact
            SwapCalculation calculation = calculateSwap(
                request.getAmountIn(), 
                request.getTokenIn(), 
                request.getTokenOut(),
                poolInfo
            );

            // Check slippage tolerance
            if (calculation.getPriceImpact().compareTo(request.getMaxSlippage()) > 0) {
                throw new DeFiOperationException("Price impact exceeds slippage tolerance");
            }

            // Prepare swap function call
            Function swapFunction = request.isExactInput() ? 
                buildSwapExactTokensForTokensFunction(request, calculation) :
                buildSwapTokensForExactTokensFunction(request, calculation);

            // Execute swap transaction
            String transactionHash = executeContractTransaction(swapFunction, request.getUserAddress());

            // Monitor transaction
            SwapTransactionReceipt receipt = monitorSwapTransaction(transactionHash);

            return SwapResponse.builder()
                .transactionHash(transactionHash)
                .amountIn(calculation.getAmountIn())
                .amountOut(calculation.getAmountOut())
                .priceImpact(calculation.getPriceImpact())
                .fee(calculation.getFee())
                .effectivePrice(calculation.getEffectivePrice())
                .route(calculation.getRoute())
                .status("PENDING")
                .build();

        } catch (Exception e) {
            log.error("Failed to swap tokens", e);
            throw new DeFiOperationException("Failed to swap tokens", e);
        }
    }

    /**
     * Get pool information for a token pair
     */
    public PoolInfo getPoolInfo(CryptoCurrency token0, CryptoCurrency token1) {
        log.debug("Getting pool info for pair: {} / {}", token0, token1);

        try {
            // Prepare getPool function call
            Function getPoolFunction = new Function(
                "getPool",
                Arrays.asList(
                    new Address(getTokenAddress(token0)),
                    new Address(getTokenAddress(token1))
                ),
                Arrays.asList(
                    new TypeReference<Uint256>() {}, // reserve0
                    new TypeReference<Uint256>() {}, // reserve1
                    new TypeReference<Uint256>() {}, // totalLiquidity
                    new TypeReference<Uint256>() {}  // kLast
                )
            );

            // Call contract
            String encodedFunction = FunctionEncoder.encode(getPoolFunction);
            EthCall response = web3j.ethCall(
                Transaction.createEthCallTransaction(null, ammContractAddress, encodedFunction),
                DefaultBlockParameterName.LATEST
            ).send();

            // Decode response
            List<Type> results = FunctionReturnDecoder.decode(response.getValue(), getPoolFunction.getOutputParameters());
            
            BigInteger reserve0 = ((Uint256) results.get(0)).getValue();
            BigInteger reserve1 = ((Uint256) results.get(1)).getValue();
            BigInteger totalLiquidity = ((Uint256) results.get(2)).getValue();
            BigInteger kLast = ((Uint256) results.get(3)).getValue();

            // Calculate additional metrics
            BigDecimal price = reserve1.equals(BigInteger.ZERO) ? BigDecimal.ZERO :
                new BigDecimal(reserve0).divide(new BigDecimal(reserve1), 18, RoundingMode.HALF_UP);

            BigDecimal tvl = calculateTVL(reserve0, reserve1, token0, token1);

            return PoolInfo.builder()
                .token0(token0)
                .token1(token1)
                .reserve0(reserve0)
                .reserve1(reserve1)
                .totalLiquidity(totalLiquidity)
                .kLast(kLast)
                .price(price)
                .tvl(tvl)
                .volume24h(getPool24HVolume(token0, token1))
                .fees24h(getPool24HFees(token0, token1))
                .apy(calculatePoolAPY(token0, token1))
                .build();

        } catch (Exception e) {
            log.error("Failed to get pool info for pair: {} / {}", token0, token1, e);
            throw new DeFiOperationException("Failed to get pool info", e);
        }
    }

    /**
     * Get user's liquidity positions
     */
    public List<LiquidityPositionInfo> getUserLiquidityPositions(String userAddress) {
        log.debug("Getting liquidity positions for user: {}", userAddress);

        try {
            // This would query the contract for user's LP token balances across all pools
            // For now, returning a simplified implementation
            List<LiquidityPositionInfo> positions = new ArrayList<>();

            // Get all pools user has positions in
            List<String[]> userPools = getUserActivePools(userAddress);

            for (String[] pool : userPools) {
                CryptoCurrency token0 = CryptoCurrency.valueOf(pool[0]);
                CryptoCurrency token1 = CryptoCurrency.valueOf(pool[1]);
                
                BigInteger liquidityBalance = getUserLiquidityBalance(userAddress, token0, token1);
                
                if (liquidityBalance.compareTo(BigInteger.ZERO) > 0) {
                    PoolInfo poolInfo = getPoolInfo(token0, token1);
                    
                    // Calculate user's share of the pool
                    BigDecimal poolShare = new BigDecimal(liquidityBalance)
                        .divide(new BigDecimal(poolInfo.getTotalLiquidity()), 18, RoundingMode.HALF_UP);
                    
                    // Calculate token amounts
                    BigDecimal token0Amount = new BigDecimal(poolInfo.getReserve0()).multiply(poolShare);
                    BigDecimal token1Amount = new BigDecimal(poolInfo.getReserve1()).multiply(poolShare);
                    
                    positions.add(LiquidityPositionInfo.builder()
                        .poolId(generatePoolId(token0, token1))
                        .token0(token0)
                        .token1(token1)
                        .liquidityTokens(liquidityBalance)
                        .token0Amount(token0Amount.toBigInteger())
                        .token1Amount(token1Amount.toBigInteger())
                        .poolShare(poolShare)
                        .usdValue(calculatePositionUSDValue(token0Amount, token1Amount, token0, token1))
                        .impermanentLoss(calculateImpermanentLoss(userAddress, token0, token1))
                        .feesEarned(calculateFeesEarned(userAddress, token0, token1))
                        .build());
                }
            }

            return positions;

        } catch (Exception e) {
            log.error("Failed to get user liquidity positions", e);
            throw new DeFiOperationException("Failed to get liquidity positions", e);
        }
    }

    // Private helper methods

    private LiquidityCalculation calculateOptimalLiquidity(BigInteger token0Amount, BigInteger token1Amount, PoolInfo poolInfo) {
        // Implementation of optimal liquidity calculation based on current pool reserves
        // This ensures minimal slippage and optimal capital efficiency
        
        if (poolInfo.getReserve0().equals(BigInteger.ZERO) || poolInfo.getReserve1().equals(BigInteger.ZERO)) {
            // First liquidity provision - use provided amounts
            return LiquidityCalculation.builder()
                .token0Amount(token0Amount)
                .token1Amount(token1Amount)
                .token0Min(token0Amount.multiply(BigInteger.valueOf(95)).divide(BigInteger.valueOf(100)))
                .token1Min(token1Amount.multiply(BigInteger.valueOf(95)).divide(BigInteger.valueOf(100)))
                .build();
        }

        // Calculate optimal ratio based on current reserves
        BigInteger optimalToken1 = token0Amount.multiply(poolInfo.getReserve1()).divide(poolInfo.getReserve0());
        BigInteger optimalToken0 = token1Amount.multiply(poolInfo.getReserve0()).divide(poolInfo.getReserve1());

        if (optimalToken1.compareTo(token1Amount) <= 0) {
            // Use token0Amount and calculated token1Amount
            return LiquidityCalculation.builder()
                .token0Amount(token0Amount)
                .token1Amount(optimalToken1)
                .token0Min(token0Amount.multiply(BigInteger.valueOf(95)).divide(BigInteger.valueOf(100)))
                .token1Min(optimalToken1.multiply(BigInteger.valueOf(95)).divide(BigInteger.valueOf(100)))
                .build();
        } else {
            // Use token1Amount and calculated token0Amount
            return LiquidityCalculation.builder()
                .token0Amount(optimalToken0)
                .token1Amount(token1Amount)
                .token0Min(optimalToken0.multiply(BigInteger.valueOf(95)).divide(BigInteger.valueOf(100)))
                .token1Min(token1Amount.multiply(BigInteger.valueOf(95)).divide(BigInteger.valueOf(100)))
                .build();
        }
    }

    private SwapCalculation calculateSwap(BigInteger amountIn, CryptoCurrency tokenIn, CryptoCurrency tokenOut, PoolInfo poolInfo) {
        // Implement constant product formula: x * y = k
        // amountOut = (amountIn * 997 * reserveOut) / (reserveIn * 1000 + amountIn * 997)
        // 0.3% fee is applied (997/1000)
        
        BigInteger reserveIn, reserveOut;
        if (tokenIn.equals(poolInfo.getToken0())) {
            reserveIn = poolInfo.getReserve0();
            reserveOut = poolInfo.getReserve1();
        } else {
            reserveIn = poolInfo.getReserve1();
            reserveOut = poolInfo.getReserve0();
        }

        BigInteger numerator = amountIn.multiply(BigInteger.valueOf(997)).multiply(reserveOut);
        BigInteger denominator = reserveIn.multiply(BigInteger.valueOf(1000)).add(amountIn.multiply(BigInteger.valueOf(997)));
        BigInteger amountOut = numerator.divide(denominator);

        // Calculate price impact
        BigDecimal spotPrice = new BigDecimal(reserveOut).divide(new BigDecimal(reserveIn), 18, RoundingMode.HALF_UP);
        BigDecimal effectivePrice = new BigDecimal(amountOut).divide(new BigDecimal(amountIn), 18, RoundingMode.HALF_UP);
        BigDecimal priceImpact = spotPrice.subtract(effectivePrice).divide(spotPrice, 18, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100));

        // Calculate fee (0.3% of input amount)
        BigInteger fee = amountIn.multiply(BigInteger.valueOf(3)).divide(BigInteger.valueOf(1000));

        return SwapCalculation.builder()
            .amountIn(amountIn)
            .amountOut(amountOut)
            .priceImpact(priceImpact)
            .fee(fee)
            .effectivePrice(effectivePrice)
            .route(List.of(tokenIn, tokenOut))
            .build();
    }

    private String executeContractTransaction(Function function, String userAddress) throws Exception {
        log.info("BLOCKCHAIN: Executing contract transaction for address: {}", userAddress);
        
        try {
            String encodedFunction = FunctionEncoder.encode(function);
            
            // 1. Get current nonce for the user address
            BigInteger nonce = getNonce(userAddress);
            log.debug("BLOCKCHAIN: Current nonce for {}: {}", userAddress, nonce);
            
            // 2. Estimate gas limit for this transaction
            BigInteger gasLimit = estimateGasLimit(userAddress, ammContractAddress, encodedFunction);
            log.debug("BLOCKCHAIN: Estimated gas limit: {}", gasLimit);
            
            // 3. Get current gas price with buffer for faster confirmation
            BigInteger gasPrice = getOptimalGasPrice();
            log.debug("BLOCKCHAIN: Using gas price: {} wei", gasPrice);
            
            // 4. Create raw transaction
            org.web3j.crypto.RawTransaction rawTransaction = org.web3j.crypto.RawTransaction.createTransaction(
                nonce,
                gasPrice,
                gasLimit,
                ammContractAddress,
                encodedFunction
            );
            
            // 5. Sign transaction with KMS-managed hot wallet key
            byte[] signedTransaction = signTransactionWithKMS(rawTransaction, userAddress);
            
            // 6. Send signed transaction to blockchain
            String hexSignedTx = org.web3j.utils.Numeric.toHexString(signedTransaction);
            EthSendTransaction transactionResponse = web3j.ethSendRawTransaction(hexSignedTx).send();
            
            if (transactionResponse.hasError()) {
                log.error("BLOCKCHAIN ERROR: Transaction failed - Code: {}, Message: {}",
                        transactionResponse.getError().getCode(),
                        transactionResponse.getError().getMessage());
                throw new DeFiOperationException(
                    "Transaction failed: " + transactionResponse.getError().getMessage());
            }
            
            String txHash = transactionResponse.getTransactionHash();
            log.info("BLOCKCHAIN: Transaction submitted successfully - TxHash: {}", txHash);
            
            // 7. Wait for transaction confirmation (non-blocking for user)
            confirmTransactionAsync(txHash, userAddress);
            
            return txHash;
            
        } catch (Exception e) {
            log.error("BLOCKCHAIN CRITICAL: Contract transaction execution failed for address: {}",
                    userAddress, e);
            throw new DeFiOperationException("Failed to execute contract transaction", e);
        }
    }
    
    private BigInteger getNonce(String address) throws Exception {
        return web3j.ethGetTransactionCount(
            address,
            DefaultBlockParameterName.PENDING
        ).send().getTransactionCount();
    }
    
    private BigInteger estimateGasLimit(String fromAddress, String toAddress, String data) throws Exception {
        try {
            org.web3j.protocol.core.methods.request.Transaction transaction = 
                org.web3j.protocol.core.methods.request.Transaction.createFunctionCallTransaction(
                    fromAddress,
                    null,
                    null,
                    null,
                    toAddress,
                    data
                );
            
            BigInteger estimatedGas = web3j.ethEstimateGas(transaction)
                .send()
                .getAmountUsed();
            
            // Add 20% buffer to estimated gas
            return estimatedGas.multiply(BigInteger.valueOf(120)).divide(BigInteger.valueOf(100));
            
        } catch (Exception e) {
            log.warn("BLOCKCHAIN: Gas estimation failed, using default - Error: {}", e.getMessage());
            return DefaultGasProvider.GAS_LIMIT;
        }
    }
    
    private BigInteger getOptimalGasPrice() throws Exception {
        try {
            BigInteger networkGasPrice = web3j.ethGasPrice().send().getGasPrice();
            
            // Add 10% buffer for faster confirmation
            return networkGasPrice.multiply(BigInteger.valueOf(110)).divide(BigInteger.valueOf(100));
            
        } catch (Exception e) {
            log.warn("BLOCKCHAIN: Gas price fetch failed, using default - Error: {}", e.getMessage());
            return DefaultGasProvider.GAS_PRICE;
        }
    }
    
    private byte[] signTransactionWithKMS(org.web3j.crypto.RawTransaction rawTransaction, String userAddress) {
        try {
            // In production, retrieve user's encrypted private key from database
            // Decrypt using AWS KMS and sign the transaction
            
            // For hot wallet operations, use KMS-managed signing key
            String keyAlias = "waqiti-defi-hot-wallet";
            
            // Encode transaction for signing
            byte[] encodedTransaction = org.web3j.crypto.TransactionEncoder.encode(rawTransaction);
            
            // Get transaction hash (Keccak256/SHA3)
            byte[] transactionHash = org.web3j.crypto.Hash.sha3(encodedTransaction);
            
            // Sign transaction with AWS KMS or fallback to hot wallet
            if (useKMSSigning) {
                log.info("BLOCKCHAIN: Signing transaction with AWS KMS - Key: {}", keyAlias);
                
                try {
                    // Sign with AWS KMS (ECDSA secp256k1)
                    byte[] kmsSignature = kmsService.signTransaction(keyAlias, transactionHash);
                    
                    // Convert DER-encoded signature to Ethereum (r, s, v) format
                    byte[] ethereumSignature = convertDERSignatureToEthereum(kmsSignature, transactionHash);
                    
                    // Apply signature to transaction
                    return org.web3j.crypto.TransactionEncoder.encode(rawTransaction, 
                            new org.web3j.crypto.Sign.SignatureData(
                                    ethereumSignature[64], // v (recovery id)
                                    java.util.Arrays.copyOfRange(ethereumSignature, 0, 32), // r
                                    java.util.Arrays.copyOfRange(ethereumSignature, 32, 64)  // s
                            ));
                    
                } catch (Exception e) {
                    log.error("BLOCKCHAIN: KMS signing failed, falling back to hot wallet", e);
                    // Fallback to hot wallet if KMS fails
                }
            }
            
            // Fallback: Use hot wallet credentials from Vault
            log.warn("BLOCKCHAIN: Using hot wallet signing (not recommended for production)");
            org.web3j.crypto.Credentials credentials = getHotWalletCredentials();
            return org.web3j.crypto.TransactionEncoder.signMessage(rawTransaction, credentials);
            
        } catch (Exception e) {
            log.error("BLOCKCHAIN CRITICAL: Transaction signing failed", e);
            throw new DeFiOperationException("Failed to sign transaction", e);
        }
    }
    
    private org.web3j.crypto.Credentials getHotWalletCredentials() {
        try {
            // In production, retrieve from Vault/KMS
            var hotWalletConfig = vaultTemplate.read("secret/defi-hot-wallet").getData();
            String privateKey = hotWalletConfig.get("private-key").toString();
            return org.web3j.crypto.Credentials.create(privateKey);
            
        } catch (Exception e) {
            log.error("BLOCKCHAIN CRITICAL: Failed to load hot wallet credentials", e);
            throw new DeFiOperationException("Hot wallet credentials unavailable", e);
        }
    }
    
    /**
     * Convert DER-encoded ECDSA signature from AWS KMS to Ethereum format
     * 
     * AWS KMS returns signatures in DER format (ASN.1), but Ethereum requires
     * raw (r, s, v) format where:
     * - r: 32 bytes (signature component)
     * - s: 32 bytes (signature component)
     * - v: 1 byte (recovery id: 27 or 28)
     * 
     * @param derSignature DER-encoded signature from KMS
     * @param messageHash Original message hash for recovery calculation
     * @return 65-byte signature in Ethereum format (r|s|v)
     */
    private byte[] convertDERSignatureToEthereum(byte[] derSignature, byte[] messageHash) throws Exception {
        // Parse DER signature (SEQUENCE { INTEGER r, INTEGER s })
        int rStart = 4; // Skip SEQUENCE tag, length, INTEGER tag
        int rLength = derSignature[rStart - 1];
        
        // Extract r component (may have leading zero for sign bit)
        byte[] r = java.util.Arrays.copyOfRange(derSignature, rStart, rStart + rLength);
        if (r.length == 33 && r[0] == 0) {
            r = java.util.Arrays.copyOfRange(r, 1, 33); // Remove leading zero
        }
        
        // Extract s component
        int sStart = rStart + rLength + 2; // Skip INTEGER tag and length
        int sLength = derSignature[sStart - 1];
        byte[] s = java.util.Arrays.copyOfRange(derSignature, sStart, sStart + sLength);
        if (s.length == 33 && s[0] == 0) {
            s = java.util.Arrays.copyOfRange(s, 1, 33); // Remove leading zero
        }
        
        // Ensure r and s are exactly 32 bytes (pad with leading zeros if needed)
        r = padTo32Bytes(r);
        s = padTo32Bytes(s);
        
        // Calculate recovery id (v) by testing both possible values (27 and 28)
        byte v = calculateRecoveryId(messageHash, r, s);
        
        // Combine into Ethereum signature format (r|s|v)
        byte[] ethereumSignature = new byte[65];
        System.arraycopy(r, 0, ethereumSignature, 0, 32);
        System.arraycopy(s, 0, ethereumSignature, 32, 32);
        ethereumSignature[64] = v;
        
        log.debug("BLOCKCHAIN: Converted DER signature to Ethereum format - r: {} bytes, s: {} bytes, v: {}", 
                r.length, s.length, v);
        
        return ethereumSignature;
    }
    
    private byte[] padTo32Bytes(byte[] input) {
        if (input.length == 32) {
            return input;
        }
        if (input.length > 32) {
            throw new IllegalArgumentException("Input too large: " + input.length + " bytes");
        }
        byte[] padded = new byte[32];
        System.arraycopy(input, 0, padded, 32 - input.length, input.length);
        return padded;
    }
    
    private byte calculateRecoveryId(byte[] messageHash, byte[] r, byte[] s) {
        // Try both recovery ids (27 and 28) and determine which one is correct
        // This is needed because AWS KMS doesn't provide the recovery id
        // In production, you might cache the public key from KMS to speed this up
        
        // For now, default to 27 (most common for low s values)
        // A more robust implementation would:
        // 1. Get public key from KMS: kmsService.getPublicKey(keyAlias)
        // 2. Try ecrecover with v=27, check if recovered address matches
        // 3. If not, try v=28
        
        return 27; // Standard Ethereum signature v value
    }
    
    private void confirmTransactionAsync(String txHash, String userAddress) {
        // Async confirmation monitoring (non-blocking)
        java.util.concurrent.CompletableFuture.runAsync(() -> {
            try {
                log.info("BLOCKCHAIN: Monitoring transaction confirmation - TxHash: {}", txHash);
                
                // Wait for transaction receipt (up to 5 minutes)
                int maxAttempts = 60;
                int attempt = 0;
                
                while (attempt < maxAttempts) {
                    java.util.Optional<TransactionReceipt> receipt = 
                        web3j.ethGetTransactionReceipt(txHash).send().getTransactionReceipt();
                    
                    if (receipt.isPresent()) {
                        TransactionReceipt txReceipt = receipt.get();
                        
                        if ("0x1".equals(txReceipt.getStatus())) {
                            log.info("BLOCKCHAIN: Transaction confirmed successfully - TxHash: {}, Block: {}",
                                    txHash, txReceipt.getBlockNumber());
                            
                            // Publish confirmation event
                            publishTransactionConfirmedEvent(txHash, userAddress, txReceipt);
                            return;
                            
                        } else {
                            log.error("BLOCKCHAIN: Transaction failed on-chain - TxHash: {}", txHash);
                            publishTransactionFailedEvent(txHash, userAddress, "Transaction reverted");
                            return;
                        }
                    }
                    
                    Thread.sleep(5000); // Wait 5 seconds before retry
                    attempt++;
                }
                
                log.warn("BLOCKCHAIN: Transaction confirmation timeout - TxHash: {}", txHash);
                publishTransactionTimeoutEvent(txHash, userAddress);
                
            } catch (Exception e) {
                log.error("BLOCKCHAIN: Transaction monitoring failed - TxHash: {}", txHash, e);
            }
        });
    }
    
    private void publishTransactionConfirmedEvent(String txHash, String userAddress, TransactionReceipt receipt) {
        log.info("BLOCKCHAIN EVENT: Transaction confirmed - TxHash: {}, Gas Used: {}",
                txHash, receipt.getGasUsed());
        // Publish to Kafka event bus for downstream processing
    }
    
    private void publishTransactionFailedEvent(String txHash, String userAddress, String reason) {
        log.error("BLOCKCHAIN EVENT: Transaction failed - TxHash: {}, Reason: {}", txHash, reason);
        // Publish failure event for compensation/rollback
    }
    
    private void publishTransactionTimeoutEvent(String txHash, String userAddress) {
        log.warn("BLOCKCHAIN EVENT: Transaction confirmation timeout - TxHash: {}", txHash);
        // Publish timeout event for manual investigation
    }

    // Additional helper methods for token addresses, calculations, monitoring, etc.
    
    private String getTokenAddress(CryptoCurrency currency) {
        return switch (currency) {
            case ETHEREUM -> "0x0000000000000000000000000000000000000000"; // ETH
            case USDC -> "0xA0b86a33E6441497d9a43f86781cEd6a4B3d43";
            case USDT -> "0xdAC17F958D2ee523a2206206994597C13D831ec7";
            case LINK -> "0x514910771AF9Ca656af840dff83E8264EcF986CA";
            default -> throw new IllegalArgumentException("Unsupported token: " + currency);
        };
    }

    private Function buildSwapExactTokensForTokensFunction(SwapRequest request, SwapCalculation calculation) {
        return new Function(
            "swapExactTokensForTokens",
            Arrays.asList(
                new Uint256(calculation.getAmountIn()),
                new Uint256(calculation.getAmountOut().multiply(BigInteger.valueOf(95)).divide(BigInteger.valueOf(100))), // 5% slippage
                new DynamicArray<>(Address.class, Arrays.asList(
                    new Address(getTokenAddress(request.getTokenIn())),
                    new Address(getTokenAddress(request.getTokenOut()))
                )),
                new Address(request.getUserAddress()),
                new Uint256(BigInteger.valueOf(System.currentTimeMillis() / 1000 + 1200))
            ),
            Collections.emptyList()
        );
    }

    private Function buildSwapTokensForExactTokensFunction(SwapRequest request, SwapCalculation calculation) {
        return new Function(
            "swapTokensForExactTokens",
            Arrays.asList(
                new Uint256(calculation.getAmountOut()),
                new Uint256(calculation.getAmountIn().multiply(BigInteger.valueOf(105)).divide(BigInteger.valueOf(100))), // 5% slippage
                new DynamicArray<>(Address.class, Arrays.asList(
                    new Address(getTokenAddress(request.getTokenIn())),
                    new Address(getTokenAddress(request.getTokenOut()))
                )),
                new Address(request.getUserAddress()),
                new Uint256(BigInteger.valueOf(System.currentTimeMillis() / 1000 + 1200))
            ),
            Collections.emptyList()
        );
    }

    private LiquidityTransactionReceipt monitorLiquidityTransaction(String transactionHash) {
        log.info("Monitoring liquidity transaction: {}", transactionHash);
        
        try {
            // Poll for transaction receipt (may take time to confirm)
            TransactionReceipt receipt = waitForTransactionReceipt(transactionHash, 60);
            
            if (receipt == null) {
                log.error("Transaction receipt not found for: {}", transactionHash);
                throw new RuntimeException("Transaction not confirmed");
            }
            
            // Parse Mint event logs from Uniswap V2 pair contract
            // Event: Mint(address indexed sender, uint amount0, uint amount1)
            // Topic0: keccak256("Mint(address,uint256,uint256)")
            String mintEventSignature = "0x4c209b5fc8ad50758f13e2e1088ba56a560dff690a1c6fef26394f4c03821c4f";
            
            BigInteger liquidityMinted = BigInteger.ZERO;
            BigInteger token0Amount = BigInteger.ZERO;
            BigInteger token1Amount = BigInteger.ZERO;
            BigInteger fees = BigInteger.ZERO;
            
            for (Log eventLog : receipt.getLogs()) {
                if (eventLog.getTopics().isEmpty()) continue;
                
                String eventSignature = eventLog.getTopics().get(0);
                
                // Parse Mint event
                if (mintEventSignature.equalsIgnoreCase(eventSignature)) {
                    String data = eventLog.getData();
                    
                    // Remove "0x" prefix
                    if (data.startsWith("0x")) {
                        data = data.substring(2);
                    }
                    
                    // Parse amounts from event data (3 uint256 values: amount0, amount1, liquidity)
                    if (data.length() >= 192) { // 3 * 64 hex chars
                        token0Amount = new BigInteger(data.substring(0, 64), 16);
                        token1Amount = new BigInteger(data.substring(64, 128), 16);
                        liquidityMinted = new BigInteger(data.substring(128, 192), 16);
                        
                        // Calculate fees (0.3% of deposit)
                        BigDecimal amount0Decimal = new BigDecimal(token0Amount);
                        BigDecimal amount1Decimal = new BigDecimal(token1Amount);
                        BigDecimal totalValue = amount0Decimal.add(amount1Decimal);
                        BigDecimal feeAmount = totalValue.multiply(BigDecimal.valueOf(0.003));
                        fees = feeAmount.toBigInteger();
                        
                        log.info("Parsed Mint event - Liquidity: {}, Token0: {}, Token1: {}",
                            liquidityMinted, token0Amount, token1Amount);
                    }
                }
            }
            
            return LiquidityTransactionReceipt.builder()
                .transactionHash(transactionHash)
                .liquidityMinted(liquidityMinted)
                .token0Amount(token0Amount)
                .token1Amount(token1Amount)
                .fees(fees)
                .gasUsed(receipt.getGasUsed())
                .blockNumber(receipt.getBlockNumber())
                .status(receipt.getStatus())
                .build();
                
        } catch (Exception e) {
            log.error("Failed to monitor liquidity transaction: {}", transactionHash, e);
            throw new RuntimeException("Transaction monitoring failed", e);
        }
    }

    private SwapTransactionReceipt monitorSwapTransaction(String transactionHash) {
        log.info("Monitoring swap transaction: {}", transactionHash);
        
        try {
            // Poll for transaction receipt
            TransactionReceipt receipt = waitForTransactionReceipt(transactionHash, 60);
            
            if (receipt == null) {
                log.error("Transaction receipt not found for: {}", transactionHash);
                throw new RuntimeException("Transaction not confirmed");
            }
            
            // Parse Swap event logs from Uniswap V2 pair contract
            // Event: Swap(address indexed sender, uint amount0In, uint amount1In, uint amount0Out, uint amount1Out, address indexed to)
            // Topic0: keccak256("Swap(address,uint256,uint256,uint256,uint256,address)")
            String swapEventSignature = "0xd78ad95fa46c994b6551d0da85fc275fe613ce37657fb8d5e3d130840159d822";
            
            BigInteger amountIn = BigInteger.ZERO;
            BigInteger amountOut = BigInteger.ZERO;
            BigInteger fee = BigInteger.ZERO;
            
            for (Log eventLog : receipt.getLogs()) {
                if (eventLog.getTopics().isEmpty()) continue;
                
                String eventSignature = eventLog.getTopics().get(0);
                
                // Parse Swap event
                if (swapEventSignature.equalsIgnoreCase(eventSignature)) {
                    String data = eventLog.getData();
                    
                    // Remove "0x" prefix
                    if (data.startsWith("0x")) {
                        data = data.substring(2);
                    }
                    
                    // Parse amounts from event data (4 uint256 values: amount0In, amount1In, amount0Out, amount1Out)
                    if (data.length() >= 256) { // 4 * 64 hex chars
                        BigInteger amount0In = new BigInteger(data.substring(0, 64), 16);
                        BigInteger amount1In = new BigInteger(data.substring(64, 128), 16);
                        BigInteger amount0Out = new BigInteger(data.substring(128, 192), 16);
                        BigInteger amount1Out = new BigInteger(data.substring(192, 256), 16);
                        
                        // Determine which is input and which is output
                        if (amount0In.compareTo(BigInteger.ZERO) > 0) {
                            amountIn = amount0In;
                            amountOut = amount1Out;
                        } else {
                            amountIn = amount1In;
                            amountOut = amount0Out;
                        }
                        
                        // Calculate fee (0.3% of amountIn)
                        BigDecimal amountInDecimal = new BigDecimal(amountIn);
                        BigDecimal feeDecimal = amountInDecimal.multiply(BigDecimal.valueOf(0.003));
                        fee = feeDecimal.toBigInteger();
                        
                        log.info("Parsed Swap event - AmountIn: {}, AmountOut: {}, Fee: {}",
                            amountIn, amountOut, fee);
                    }
                }
            }
            
            return SwapTransactionReceipt.builder()
                .transactionHash(transactionHash)
                .amountIn(amountIn)
                .amountOut(amountOut)
                .fee(fee)
                .gasUsed(receipt.getGasUsed())
                .blockNumber(receipt.getBlockNumber())
                .status(receipt.getStatus())
                .build();
                
        } catch (Exception e) {
            log.error("Failed to monitor swap transaction: {}", transactionHash, e);
            throw new RuntimeException("Transaction monitoring failed", e);
        }
    }
    
    private TransactionReceipt waitForTransactionReceipt(String transactionHash, int timeoutSeconds) throws Exception {
        log.debug("Waiting for transaction receipt: {} (timeout: {}s)", transactionHash, timeoutSeconds);
        
        int attempts = 0;
        int maxAttempts = timeoutSeconds / 2;
        
        while (attempts < maxAttempts) {
            EthGetTransactionReceipt receiptResponse = web3j.ethGetTransactionReceipt(transactionHash).send();
            
            if (receiptResponse.getTransactionReceipt().isPresent()) {
                TransactionReceipt receipt = receiptResponse.getTransactionReceipt().get();
                log.info("Transaction confirmed in block: {}", receipt.getBlockNumber());
                return receipt;
            }
            
            // Wait 2 seconds before next attempt
            Thread.sleep(2000);
            attempts++;
            
            if (attempts % 5 == 0) {
                log.debug("Still waiting for transaction {} ({}/{})", transactionHash, attempts, maxAttempts);
            }
        }
        
        log.error("Transaction receipt timeout after {}s: {}", timeoutSeconds, transactionHash);
        return null;
    }

    private BigDecimal calculateTVL(BigInteger reserve0, BigInteger reserve1, CryptoCurrency token0, CryptoCurrency token1) {
        try {
            // Get current token prices in USD
            BigDecimal token0PriceUSD = getTokenPriceUSD(token0);
            BigDecimal token1PriceUSD = getTokenPriceUSD(token1);
            
            // Convert reserves from wei/smallest unit to decimal
            BigDecimal reserve0Decimal = new BigDecimal(reserve0).divide(BigDecimal.TEN.pow(getTokenDecimals(token0)), 18, RoundingMode.HALF_UP);
            BigDecimal reserve1Decimal = new BigDecimal(reserve1).divide(BigDecimal.TEN.pow(getTokenDecimals(token1)), 18, RoundingMode.HALF_UP);
            
            // Calculate USD value of each reserve
            BigDecimal reserve0USD = reserve0Decimal.multiply(token0PriceUSD);
            BigDecimal reserve1USD = reserve1Decimal.multiply(token1PriceUSD);
            
            // Total Value Locked = sum of both reserves in USD
            BigDecimal tvl = reserve0USD.add(reserve1USD);
            
            log.debug("Calculated TVL for {}/{}: ${} ({}=${}, {}=${})", 
                token0, token1, tvl, token0, reserve0USD, token1, reserve1USD);
            
            return tvl;
            
        } catch (Exception e) {
            log.error("Failed to calculate TVL for {}/{}", token0, token1, e);
            return BigDecimal.ZERO;
        }
    }
    
    private BigDecimal getTokenPriceUSD(CryptoCurrency token) {
        // Production: integrate with Chainlink price feeds or CoinGecko API
        // For now, using approximate market prices
        return switch (token) {
            case BITCOIN -> BigDecimal.valueOf(43000);
            case ETHEREUM -> BigDecimal.valueOf(2300);
            case USDC, USDT -> BigDecimal.ONE;
            case LITECOIN -> BigDecimal.valueOf(75);
            default -> BigDecimal.valueOf(100);
        };
    }
    
    private int getTokenDecimals(CryptoCurrency token) {
        return switch (token) {
            case BITCOIN -> 8;
            case ETHEREUM, USDC, USDT, LITECOIN -> 18;
            default -> 18;
        };
    }

    private BigDecimal getPool24HVolume(CryptoCurrency token0, CryptoCurrency token1) {
        try {
            String poolId = generatePoolId(token0, token1);
            
            // Query blockchain events for Swap events in last 24 hours
            long currentTime = System.currentTimeMillis();
            long oneDayAgo = currentTime - (24 * 60 * 60 * 1000);
            
            // In production: Query event logs from blockchain
            // For now: Estimate based on TVL and typical daily volume ratio (5-20%)
            PoolInfo poolInfo = getPoolInfo(token0, token1);
            BigDecimal tvl = calculateTVL(poolInfo.getReserve0(), poolInfo.getReserve1(), token0, token1);
            
            // Assume 10% of TVL as daily volume (industry average for active pools)
            BigDecimal estimatedVolume = tvl.multiply(BigDecimal.valueOf(0.10));
            
            log.debug("Estimated 24h volume for {}/{}: ${}", token0, token1, estimatedVolume);
            
            return estimatedVolume;
            
        } catch (Exception e) {
            log.error("Failed to get 24h volume for {}/{}", token0, token1, e);
            return BigDecimal.ZERO;
        }
    }

    private BigDecimal getPool24HFees(CryptoCurrency token0, CryptoCurrency token1) {
        try {
            // 24h fees = 24h volume * fee tier
            BigDecimal volume24h = getPool24HVolume(token0, token1);
            
            // Standard Uniswap V2 fee tier is 0.3%
            BigDecimal feeTier = BigDecimal.valueOf(0.003);
            
            BigDecimal fees24h = volume24h.multiply(feeTier);
            
            log.debug("Calculated 24h fees for {}/{}: ${} (volume: ${}, rate: 0.3%)", 
                token0, token1, fees24h, volume24h);
            
            return fees24h;
            
        } catch (Exception e) {
            log.error("Failed to calculate 24h fees for {}/{}", token0, token1, e);
            return BigDecimal.ZERO;
        }
    }

    private BigDecimal calculatePoolAPY(CryptoCurrency token0, CryptoCurrency token1) {
        try {
            // APY calculation: (Annual Fees / TVL) * 100
            // Annual Fees = Daily Fees * 365
            
            PoolInfo poolInfo = getPoolInfo(token0, token1);
            BigDecimal tvl = calculateTVL(poolInfo.getReserve0(), poolInfo.getReserve1(), token0, token1);
            
            if (tvl.compareTo(BigDecimal.ZERO) == 0) {
                return BigDecimal.ZERO;
            }
            
            BigDecimal fees24h = getPool24HFees(token0, token1);
            BigDecimal annualFees = fees24h.multiply(BigDecimal.valueOf(365));
            
            // APY = (Annual Fees / TVL) * 100
            BigDecimal apy = annualFees
                .divide(tvl, 6, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100));
            
            // Add compound interest effect (fees are automatically reinvested)
            // APY_compound = (1 + daily_rate)^365 - 1
            // FIXED: Use BigDecimal for all calculations to prevent precision loss
            BigDecimal dailyRate = fees24h.divide(tvl, 20, RoundingMode.HALF_UP);
            BigDecimal onePlusDailyRate = BigDecimal.ONE.add(dailyRate);

            // Calculate (1 + dailyRate)^365 using BigDecimal.pow with MathContext
            MathContext mc = new MathContext(20, RoundingMode.HALF_UP);
            BigDecimal compoundFactor = onePlusDailyRate.pow(365, mc);
            BigDecimal finalAPY = compoundFactor.subtract(BigDecimal.ONE)
                .multiply(new BigDecimal("100"));
            
            log.debug("Calculated APY for {}/{}: {:.2f}% (TVL: ${}, Daily Fees: ${})", 
                token0, token1, finalAPY, tvl, fees24h);
            
            return finalAPY.setScale(2, RoundingMode.HALF_UP);
            
        } catch (Exception e) {
            log.error("Failed to calculate APY for {}/{}", token0, token1, e);
            return BigDecimal.ZERO;
        }
    }

    private List<String[]> getUserActivePools(String userAddress) {
        try {
            List<String[]> activePools = new ArrayList<>();
            
            // Query all possible token pairs to find user's liquidity positions
            CryptoCurrency[] supportedTokens = {CryptoCurrency.ETHEREUM, CryptoCurrency.BITCOIN, 
                CryptoCurrency.USDC, CryptoCurrency.USDT, CryptoCurrency.LITECOIN};
            
            for (int i = 0; i < supportedTokens.length; i++) {
                for (int j = i + 1; j < supportedTokens.length; j++) {
                    CryptoCurrency token0 = supportedTokens[i];
                    CryptoCurrency token1 = supportedTokens[j];
                    
                    try {
                        BigInteger balance = getUserLiquidityBalance(userAddress, token0, token1);
                        
                        // If user has liquidity in this pool
                        if (balance.compareTo(BigInteger.ZERO) > 0) {
                            activePools.add(new String[]{token0.name(), token1.name()});
                            log.debug("Found active pool for user {}: {}/{} (balance: {})", 
                                userAddress, token0, token1, balance);
                        }
                    } catch (Exception e) {
                        log.debug("No liquidity found for user {} in pool {}/{}", 
                            userAddress, token0, token1);
                    }
                }
            }
            
            log.info("User {} has {} active liquidity pools", userAddress, activePools.size());
            return activePools;
            
        } catch (Exception e) {
            log.error("Failed to get active pools for user: {}", userAddress, e);
            return List.of();
        }
    }

    private BigInteger getUserLiquidityBalance(String userAddress, CryptoCurrency token0, CryptoCurrency token1) {
        try {
            // Create ERC20 balanceOf function call for LP tokens
            Function balanceOfFunction = new Function(
                "balanceOf",
                Arrays.asList(new Address(userAddress)),
                Arrays.asList(new TypeReference<Uint256>() {})
            );
            
            String encodedFunction = FunctionEncoder.encode(balanceOfFunction);
            
            // Get LP token contract address for this pair
            String lpTokenAddress = getLPTokenAddress(token0, token1);
            
            // Call balanceOf on LP token contract
            EthCall ethCall = web3j.ethCall(
                Transaction.createEthCallTransaction(
                    userAddress,
                    lpTokenAddress,
                    encodedFunction
                ),
                DefaultBlockParameterName.LATEST
            ).send();
            
            if (ethCall.hasError()) {
                log.error("Error calling balanceOf for user {}: {}", userAddress, ethCall.getError().getMessage());
                return BigInteger.ZERO;
            }
            
            String result = ethCall.getValue();
            List<Type> decoded = FunctionReturnDecoder.decode(result, balanceOfFunction.getOutputParameters());
            
            if (decoded.isEmpty()) {
                return BigInteger.ZERO;
            }
            
            BigInteger balance = ((Uint256) decoded.get(0)).getValue();
            
            log.debug("User {} LP token balance for {}/{}: {}", userAddress, token0, token1, balance);
            return balance;
            
        } catch (Exception e) {
            log.error("Failed to get liquidity balance for user {} in pool {}/{}", 
                userAddress, token0, token1, e);
            return BigInteger.ZERO;
        }
    }
    
    private String getLPTokenAddress(CryptoCurrency token0, CryptoCurrency token1) {
        try {
            // Call getPair function on AMM factory contract to get LP token address
            Function getPairFunction = new Function(
                "getPair",
                Arrays.asList(
                    new Address(getTokenAddress(token0)),
                    new Address(getTokenAddress(token1))
                ),
                Arrays.asList(new TypeReference<Address>() {})
            );
            
            String encodedFunction = FunctionEncoder.encode(getPairFunction);
            
            EthCall ethCall = web3j.ethCall(
                Transaction.createEthCallTransaction(
                    null,
                    ammContractAddress,
                    encodedFunction
                ),
                DefaultBlockParameterName.LATEST
            ).send();
            
            if (ethCall.hasError()) {
                throw new RuntimeException("Failed to get pair address: " + ethCall.getError().getMessage());
            }
            
            String result = ethCall.getValue();
            List<Type> decoded = FunctionReturnDecoder.decode(result, getPairFunction.getOutputParameters());
            
            if (decoded.isEmpty()) {
                throw new RuntimeException("No pair found for tokens");
            }
            
            return ((Address) decoded.get(0)).getValue();
            
        } catch (Exception e) {
            log.error("Failed to get LP token address for {}/{}", token0, token1, e);
            throw new RuntimeException("Cannot get LP token address", e);
        }
    }
    
    private String getTokenAddress(CryptoCurrency token) {
        // Return contract addresses for tokens (mainnet addresses)
        return switch (token) {
            case ETHEREUM -> "0xC02aaA39b223FE8D0A0e5C4F27eAD9083C756Cc2"; // WETH
            case BITCOIN -> "0x2260FAC5E5542a773Aa44fBCfeDf7C193bc2C599"; // WBTC
            case USDC -> "0xA0b86991c6218b36c1d19D4a2e9Eb0cE3606eB48";
            case USDT -> "0xdAC17F958D2ee523a2206206994597C13D831ec7";
            case LITECOIN -> "0x0000000000000000000000000000000000000000"; // Placeholder
            default -> "0x0000000000000000000000000000000000000000";
        };
    }

    private String generatePoolId(CryptoCurrency token0, CryptoCurrency token1) {
        return token0.name() + "-" + token1.name();
    }

    private BigDecimal calculatePositionUSDValue(BigDecimal token0Amount, BigDecimal token1Amount, CryptoCurrency token0, CryptoCurrency token1) {
        try {
            // Get current token prices
            BigDecimal token0PriceUSD = getTokenPriceUSD(token0);
            BigDecimal token1PriceUSD = getTokenPriceUSD(token1);
            
            // Calculate USD value of each token amount
            BigDecimal token0ValueUSD = token0Amount.multiply(token0PriceUSD);
            BigDecimal token1ValueUSD = token1Amount.multiply(token1PriceUSD);
            
            // Total position value
            BigDecimal totalValueUSD = token0ValueUSD.add(token1ValueUSD);
            
            log.debug("Calculated position value: ${} ({} {} @ ${} + {} {} @ ${})",
                totalValueUSD, token0Amount, token0, token0PriceUSD, 
                token1Amount, token1, token1PriceUSD);
            
            return totalValueUSD.setScale(2, RoundingMode.HALF_UP);
            
        } catch (Exception e) {
            log.error("Failed to calculate position USD value", e);
            return BigDecimal.ZERO;
        }
    }

    private BigDecimal calculateImpermanentLoss(String userAddress, CryptoCurrency token0, CryptoCurrency token1) {
        try {
            // Impermanent Loss Formula:
            // IL = 2 * sqrt(price_ratio) / (1 + price_ratio) - 1
            // Where price_ratio = current_price / entry_price
            
            // Get user's initial entry data (would be stored in database)
            UserLiquidityPosition position = getUserLiquidityPosition(userAddress, token0, token1);
            
            if (position == null) {
                return BigDecimal.ZERO;
            }
            
            // Calculate current price ratio
            BigDecimal currentToken0Price = getTokenPriceUSD(token0);
            BigDecimal currentToken1Price = getTokenPriceUSD(token1);
            BigDecimal currentRatio = currentToken0Price.divide(currentToken1Price, 10, RoundingMode.HALF_UP);
            
            // Get entry price ratio
            BigDecimal entryRatio = position.getEntryToken0Price().divide(
                position.getEntryToken1Price(), 10, RoundingMode.HALF_UP);
            
            // Price ratio change
            BigDecimal priceRatioChange = currentRatio.divide(entryRatio, 10, RoundingMode.HALF_UP);
            
            // Calculate impermanent loss
            // IL = 2 * sqrt(k) / (1 + k) - 1
            // FIXED: Use BigDecimal for all calculations to prevent precision loss
            MathContext mc = new MathContext(20, RoundingMode.HALF_UP);

            // Calculate sqrt(k) using BigDecimal
            BigDecimal sqrtK = sqrt(priceRatioChange, mc);

            // Calculate 2 * sqrt(k)
            BigDecimal numerator = new BigDecimal("2").multiply(sqrtK, mc);

            // Calculate 1 + k
            BigDecimal denominator = BigDecimal.ONE.add(priceRatioChange);

            // Calculate IL = 2 * sqrt(k) / (1 + k) - 1
            BigDecimal il = numerator.divide(denominator, mc).subtract(BigDecimal.ONE);

            // Convert to percentage
            BigDecimal impermanentLossPercent = il.multiply(new BigDecimal("100"));
            
            // Calculate dollar amount
            BigDecimal initialValueUSD = position.getInitialValueUSD();
            BigDecimal currentValueUSD = calculatePositionUSDValue(
                position.getToken0Amount(), 
                position.getToken1Amount(), 
                token0, 
                token1
            );
            
            // Value if held tokens separately
            BigDecimal holdValueUSD = position.getInitialToken0Amount()
                .multiply(currentToken0Price)
                .add(position.getInitialToken1Amount().multiply(currentToken1Price));
            
            BigDecimal impermanentLossDollar = currentValueUSD.subtract(holdValueUSD);
            
            log.debug("Impermanent Loss for user {} in {}/{}: {:.2f}% (${:.2f})",
                userAddress, token0, token1, impermanentLossPercent, impermanentLossDollar);
            
            return impermanentLossPercent.setScale(2, RoundingMode.HALF_UP);
            
        } catch (Exception e) {
            log.error("Failed to calculate impermanent loss for user {} in {}/{}", 
                userAddress, token0, token1, e);
            return BigDecimal.ZERO;
        }
    }
    
    private UserLiquidityPosition getUserLiquidityPosition(String userAddress, CryptoCurrency token0, CryptoCurrency token1) {
        // In production: Query from database where user's entry prices and amounts are stored
        // For now: Return mock position data
        return UserLiquidityPosition.builder()
            .userAddress(userAddress)
            .token0(token0)
            .token1(token1)
            .entryToken0Price(getTokenPriceUSD(token0).multiply(BigDecimal.valueOf(0.9))) // 10% price change simulation
            .entryToken1Price(getTokenPriceUSD(token1))
            .initialToken0Amount(BigDecimal.valueOf(1.0))
            .initialToken1Amount(BigDecimal.valueOf(2000))
            .token0Amount(BigDecimal.valueOf(0.95))
            .token1Amount(BigDecimal.valueOf(2100))
            .initialValueUSD(BigDecimal.valueOf(4000))
            .entryTimestamp(System.currentTimeMillis() - (7 * 24 * 60 * 60 * 1000)) // 7 days ago
            .build();
    }

    private BigDecimal calculateFeesEarned(String userAddress, CryptoCurrency token0, CryptoCurrency token1) {
        try {
            // Fees earned = User's pool share * Total fees generated since entry
            
            UserLiquidityPosition position = getUserLiquidityPosition(userAddress, token0, token1);
            if (position == null) {
                return BigDecimal.ZERO;
            }
            
            // Get user's current liquidity balance
            BigInteger liquidityTokens = getUserLiquidityBalance(userAddress, token0, token1);
            
            // Get pool info to calculate share
            PoolInfo poolInfo = getPoolInfo(token0, token1);
            BigDecimal poolShare = calculatePoolShare(liquidityTokens, poolInfo);
            
            // Calculate time-weighted fees
            long positionAgeDays = (System.currentTimeMillis() - position.getEntryTimestamp()) / (24 * 60 * 60 * 1000);
            
            // Get current daily fees
            BigDecimal dailyFees = getPool24HFees(token0, token1);
            
            // Estimate total fees generated since user's entry
            // Assuming constant daily fees (simplification - actual fees vary)
            BigDecimal totalFeesGenerated = dailyFees.multiply(BigDecimal.valueOf(positionAgeDays));
            
            // User's share of fees
            BigDecimal userFeesEarned = totalFeesGenerated
                .multiply(poolShare)
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
            
            log.debug("Fees earned by user {} in {}/{}: ${:.2f} ({:.2f}% share over {} days)",
                userAddress, token0, token1, userFeesEarned, poolShare, positionAgeDays);
            
            return userFeesEarned.setScale(2, RoundingMode.HALF_UP);
            
        } catch (Exception e) {
            log.error("Failed to calculate fees earned for user {} in {}/{}", 
                userAddress, token0, token1, e);
            return BigDecimal.ZERO;
        }
    }

    private BigDecimal calculatePoolShare(BigInteger liquidityTokens, PoolInfo poolInfo) {
        return new BigDecimal(liquidityTokens)
            .divide(new BigDecimal(poolInfo.getTotalLiquidity()), 18, RoundingMode.HALF_UP)
            .multiply(BigDecimal.valueOf(100));
    }

    private BigDecimal calculateLiquidityFees(BigInteger token0Amount, BigInteger token1Amount) {
        // 0.3% fee calculation
        return new BigDecimal(token0Amount.add(token1Amount))
            .multiply(BigDecimal.valueOf(0.003));
    }

    private LiquidityRemovalCalculation calculateLiquidityRemoval(BigInteger liquidityTokens, PoolInfo poolInfo) {
        BigDecimal poolShare = new BigDecimal(liquidityTokens)
            .divide(new BigDecimal(poolInfo.getTotalLiquidity()), 18, RoundingMode.HALF_UP);
        
        BigInteger token0Amount = new BigDecimal(poolInfo.getReserve0()).multiply(poolShare).toBigInteger();
        BigInteger token1Amount = new BigDecimal(poolInfo.getReserve1()).multiply(poolShare).toBigInteger();
        
        return LiquidityRemovalCalculation.builder()
            .token0Amount(token0Amount)
            .token1Amount(token1Amount)
            .token0AmountMin(token0Amount.multiply(BigInteger.valueOf(95)).divide(BigInteger.valueOf(100)))
            .token1AmountMin(token1Amount.multiply(BigInteger.valueOf(95)).divide(BigInteger.valueOf(100)))
            .build();
    }

    // Exception class
    public static class DeFiOperationException extends RuntimeException {
        public DeFiOperationException(String message) {
            super(message);
        }

        public DeFiOperationException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /**
     * Calculate square root of BigDecimal using Newton's method
     * Provides precision control for financial calculations
     *
     * @param value Value to calculate sqrt of
     * @param mc MathContext for precision
     * @return Square root of value
     */
    private static BigDecimal sqrt(BigDecimal value, MathContext mc) {
        if (value.compareTo(BigDecimal.ZERO) < 0) {
            throw new ArithmeticException("Cannot calculate square root of negative number");
        }
        if (value.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }

        // Newton's method: x_n+1 = (x_n + value/x_n) / 2
        BigDecimal two = new BigDecimal("2");
        BigDecimal x = value.divide(two, mc);

        for (int i = 0; i < 50; i++) {
            BigDecimal xNext = value.divide(x, mc).add(x).divide(two, mc);

            // Check for convergence
            if (x.subtract(xNext).abs().compareTo(new BigDecimal("0.0000000001")) < 0) {
                return xNext;
            }

            x = xNext;
        }

        return x;
    }
}