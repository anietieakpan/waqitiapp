/**
 * Crypto Currency Enum
 * Supported cryptocurrencies in the platform
 */
package com.waqiti.crypto.entity;

public enum CryptoCurrency {
    BITCOIN("BTC", "Bitcoin", 8),
    ETHEREUM("ETH", "Ethereum", 18),
    LITECOIN("LTC", "Litecoin", 8),
    USDC("USDC", "USD Coin", 6),
    USDT("USDT", "Tether", 6),
    BNB("BNB", "Binance Coin", 18),
    ADA("ADA", "Cardano", 6),
    DOT("DOT", "Polkadot", 10),
    LINK("LINK", "Chainlink", 18);

    private final String symbol;
    private final String name;
    private final int decimals;

    CryptoCurrency(String symbol, String name, int decimals) {
        this.symbol = symbol;
        this.name = name;
        this.decimals = decimals;
    }

    public String getSymbol() {
        return symbol;
    }

    public String getName() {
        return name;
    }

    public int getDecimals() {
        return decimals;
    }

    public boolean isERC20Token() {
        return this == USDC || this == USDT || this == LINK;
    }

    public boolean isNativeCoin() {
        return this == BITCOIN || this == ETHEREUM || this == LITECOIN || 
               this == BNB || this == ADA || this == DOT;
    }

    public boolean isStableCoin() {
        return this == USDC || this == USDT;
    }

    public boolean isDeFiToken() {
        return this == LINK;
    }
}