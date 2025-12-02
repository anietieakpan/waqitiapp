/**
 * Blockchain Service Factory
 * Factory for getting the appropriate blockchain service based on currency
 */
package com.waqiti.crypto.blockchain;

import com.waqiti.crypto.entity.CryptoCurrency;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class BlockchainServiceFactory {

    private final BitcoinService bitcoinService;
    private final EthereumService ethereumService;
    private final LitecoinService litecoinService;

    /**
     * Get the appropriate blockchain service for the currency
     */
    public Object getBlockchainService(CryptoCurrency currency) {
        return switch (currency) {
            case BITCOIN -> bitcoinService;
            case ETHEREUM, USDC, USDT -> ethereumService;
            case LITECOIN -> litecoinService;
        };
    }

    /**
     * Check if currency is ERC-20 token
     */
    public boolean isERC20Token(CryptoCurrency currency) {
        return currency == CryptoCurrency.USDC || currency == CryptoCurrency.USDT;
    }

    /**
     * Check if currency uses UTXO model
     */
    public boolean isUTXOBased(CryptoCurrency currency) {
        return currency == CryptoCurrency.BITCOIN || currency == CryptoCurrency.LITECOIN;
    }

    /**
     * Check if currency uses account model
     */
    public boolean isAccountBased(CryptoCurrency currency) {
        return currency == CryptoCurrency.ETHEREUM || isERC20Token(currency);
    }
}