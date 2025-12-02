package com.waqiti.payment.core.model;

import lombok.*;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Cryptocurrency payment data model
 * Industrial-grade implementation for crypto payment processing
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString(exclude = {"privateKey", "seedPhrase", "metadata"})
public class CryptoPaymentData {
    
    @NotNull
    private UUID cryptoPaymentId;
    
    @NotNull
    private CryptoCurrency currency;
    
    @NotNull
    private NetworkType network;
    
    @NotNull
    @NotBlank
    private String fromAddress;
    
    @NotNull
    @NotBlank
    private String toAddress;
    
    @NotNull
    @DecimalMin("0.0")
    private BigDecimal amount;
    
    private BigDecimal amountInFiat;
    
    private String fiatCurrency;
    
    private BigDecimal exchangeRate;
    
    @NotNull
    private BigDecimal networkFee;
    
    private BigDecimal platformFee;
    
    private String transactionHash;
    
    private String blockHash;
    
    private Long blockNumber;
    
    @Min(0)
    @Builder.Default
    private int confirmations = 0;
    
    @Min(1)
    @Builder.Default
    private int requiredConfirmations = 6;
    
    @NotNull
    @Builder.Default
    private CryptoPaymentStatus status = CryptoPaymentStatus.PENDING;
    
    @Builder.Default
    private TransactionSpeed speed = TransactionSpeed.STANDARD;
    
    private Long gasLimit;
    
    private BigDecimal gasPrice;
    
    private Long nonce;
    
    private String memo;
    
    private String destinationTag;
    
    @Builder.Default
    private PaymentDirection direction = PaymentDirection.OUTGOING;
    
    private LocalDateTime initiatedAt;
    
    private LocalDateTime confirmedAt;
    
    private LocalDateTime failedAt;
    
    private String failureReason;
    
    @Builder.Default
    private List<String> inputAddresses = new ArrayList<>();
    
    @Builder.Default
    private List<String> outputAddresses = new ArrayList<>();
    
    private SmartContractInfo smartContract;
    
    private LightningInfo lightning;
    
    private String privateKey;
    
    private String seedPhrase;
    
    private Map<String, Object> metadata;
    
    public enum CryptoCurrency {
        BTC("Bitcoin", 8),
        ETH("Ethereum", 18),
        USDT("Tether", 6),
        USDC("USD Coin", 6),
        BNB("Binance Coin", 8),
        XRP("Ripple", 6),
        ADA("Cardano", 6),
        DOGE("Dogecoin", 8),
        SOL("Solana", 9),
        DOT("Polkadot", 10),
        MATIC("Polygon", 18),
        LTC("Litecoin", 8),
        AVAX("Avalanche", 18),
        LINK("Chainlink", 18),
        XLM("Stellar", 7),
        ALGO("Algorand", 6),
        ATOM("Cosmos", 6),
        CUSTOM("Custom Token", 18);
        
        private final String displayName;
        private final int decimals;
        
        CryptoCurrency(String displayName, int decimals) {
            this.displayName = displayName;
            this.decimals = decimals;
        }
        
        public String getDisplayName() {
            return displayName;
        }
        
        public int getDecimals() {
            return decimals;
        }
    }
    
    public enum NetworkType {
        MAINNET,
        TESTNET,
        ETHEREUM,
        BINANCE_SMART_CHAIN,
        POLYGON,
        AVALANCHE,
        SOLANA,
        ARBITRUM,
        OPTIMISM,
        TRON,
        LIGHTNING,
        LAYER2,
        SIDECHAIN,
        CUSTOM
    }
    
    public enum CryptoPaymentStatus {
        PENDING,
        BROADCASTING,
        UNCONFIRMED,
        CONFIRMING,
        CONFIRMED,
        COMPLETED,
        FAILED,
        CANCELLED,
        EXPIRED,
        REPLACED,
        DROPPED,
        REVERSED
    }
    
    public enum TransactionSpeed {
        SLOW,
        STANDARD,
        FAST,
        INSTANT,
        CUSTOM
    }
    
    public enum PaymentDirection {
        INCOMING,
        OUTGOING,
        INTERNAL
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SmartContractInfo {
        private String contractAddress;
        private String methodName;
        private List<String> parameters;
        private String abi;
        private BigDecimal value;
        private String data;
        private ContractType type;
    }
    
    public enum ContractType {
        ERC20,
        ERC721,
        ERC1155,
        DEFI,
        DEX,
        STAKING,
        CUSTOM
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LightningInfo {
        private String paymentRequest;
        private String paymentHash;
        private String preimage;
        private String routingHints;
        private Long expiryTime;
        private String nodeId;
        private List<String> channelIds;
    }
    
    // Business logic methods
    public boolean isConfirmed() {
        return confirmations >= requiredConfirmations;
    }
    
    public boolean isPending() {
        return status == CryptoPaymentStatus.PENDING || 
               status == CryptoPaymentStatus.BROADCASTING ||
               status == CryptoPaymentStatus.UNCONFIRMED ||
               status == CryptoPaymentStatus.CONFIRMING;
    }
    
    public boolean requiresGas() {
        return network == NetworkType.ETHEREUM || 
               network == NetworkType.BINANCE_SMART_CHAIN ||
               network == NetworkType.POLYGON ||
               network == NetworkType.AVALANCHE ||
               network == NetworkType.ARBITRUM ||
               network == NetworkType.OPTIMISM;
    }
    
    public BigDecimal getTotalFee() {
        BigDecimal total = networkFee != null ? networkFee : BigDecimal.ZERO;
        if (platformFee != null) {
            total = total.add(platformFee);
        }
        if (requiresGas() && gasPrice != null && gasLimit != null) {
            BigDecimal gasFee = gasPrice.multiply(new BigDecimal(gasLimit));
            total = total.add(gasFee);
        }
        return total;
    }
    
    public BigDecimal getNetAmount() {
        return amount.subtract(getTotalFee());
    }
    
    public double getConfirmationProgress() {
        if (requiredConfirmations == 0) {
            return 100.0;
        }
        return Math.min(100.0, (confirmations * 100.0) / requiredConfirmations);
    }
    
    public boolean isReplaceable() {
        return isPending() && 
               (network == NetworkType.ETHEREUM || network == NetworkType.MAINNET);
    }
}