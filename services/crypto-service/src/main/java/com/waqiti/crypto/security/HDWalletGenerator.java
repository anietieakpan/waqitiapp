/**
 * HD Wallet Generator
 * Generates Hierarchical Deterministic (HD) wallets following BIP32/BIP44 standards
 */
package com.waqiti.crypto.security;

import com.waqiti.crypto.dto.HDWalletKeys;
import com.waqiti.crypto.entity.CryptoCurrency;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.Address;
import org.bitcoinj.core.LegacyAddress;
import org.bitcoinj.crypto.*;
import org.bitcoinj.params.MainNetParams;
import org.bitcoinj.wallet.DeterministicKeyChain;
import org.bitcoinj.wallet.DeterministicSeed;
import org.springframework.stereotype.Component;
import org.web3j.crypto.ECKeyPair;
import org.web3j.crypto.Keys;
import org.web3j.utils.Numeric;

import java.math.BigInteger;
import java.security.SecureRandom;
import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class HDWalletGenerator {

    private static final String HD_PATH_PREFIX = "m/44'";
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    // Coin type constants (BIP44)
    private static final int BITCOIN_COIN_TYPE = 0;
    private static final int ETHEREUM_COIN_TYPE = 60;
    private static final int LITECOIN_COIN_TYPE = 2;

    /**
     * Custom Litecoin Network Parameters
     * Litecoin mainnet uses different address version bytes than Bitcoin
     */
    private static class LitecoinMainNetParams extends MainNetParams {
        private static LitecoinMainNetParams instance;

        public static synchronized LitecoinMainNetParams get() {
            if (instance == null) {
                instance = new LitecoinMainNetParams();
            }
            return instance;
        }

        private LitecoinMainNetParams() {
            super();
            // Override ID to distinguish from Bitcoin
            id = "org.litecoin.production";

            // Litecoin mainnet address version bytes
            // P2PKH addresses start with 'L' (version byte 48 = 0x30)
            addressHeader = 48;

            // P2SH addresses start with 'M' (version byte 50 = 0x32)
            p2shHeader = 50;

            // Litecoin uses different port than Bitcoin
            port = 9333;
        }

        @Override
        public String getPaymentProtocolId() {
            return "litecoin";
        }
    }

    /**
     * Generate a new HD wallet for a user and currency
     */
    public HDWalletKeys generateHDWallet(UUID userId, CryptoCurrency currency) {
        log.info("Generating HD wallet for user: {} currency: {}", userId, currency);
        
        try {
            // Generate mnemonic seed phrase (12 words)
            byte[] entropy = new byte[DeterministicSeed.DEFAULT_SEED_ENTROPY_BITS / 8];
            SECURE_RANDOM.nextBytes(entropy);
            
            List<String> mnemonic = MnemonicCode.INSTANCE.toMnemonic(entropy);
            byte[] seed = MnemonicCode.toSeed(mnemonic, "");
            
            // Create deterministic key chain
            DeterministicKey masterKey = HDKeyDerivation.createMasterPrivateKey(seed);
            
            // Derive keys based on currency
            HDWalletKeys walletKeys = deriveKeysForCurrency(masterKey, currency, 0, 0);

            // CRITICAL SECURITY: Store mnemonic in response for user to backup
            // The mnemonic will be shown to user ONCE and NEVER stored in database
            // User MUST write down and securely store their 12-word recovery phrase
            walletKeys.setMnemonic(String.join(" ", mnemonic));

            // SECURITY WARNING: The mnemonic in this response MUST be shown to the user
            // immediately and then cleared from memory. It should NEVER be persisted to
            // database or logs. The calling service must handle this securely.
            log.info("Successfully generated HD wallet for user: {} currency: {} - MNEMONIC MUST BE SHOWN TO USER ONCE",
                    userId, currency);
            log.warn("SECURITY: Mnemonic generated for wallet creation - ensure it's displayed to user and NOT stored");

            return walletKeys;
            
        } catch (Exception e) {
            log.error("Failed to generate HD wallet for user: {} currency: {}", userId, currency, e);
            throw new HDWalletGenerationException("Failed to generate HD wallet", e);
        }
    }

    /**
     * Derive child address from HD wallet
     */
    public HDWalletKeys deriveAddress(String publicKeyHex, String derivationPath, CryptoCurrency currency) {
        log.debug("Deriving address from path: {} for currency: {}", derivationPath, currency);
        
        try {
            // Parse derivation path
            String[] pathComponents = derivationPath.split("/");
            int addressIndex = Integer.parseInt(pathComponents[pathComponents.length - 1]);
            
            // For address derivation from public key only
            // In production, this would use the extended public key (xpub)
            String address = generateAddressFromPublicKey(publicKeyHex, currency, addressIndex);
            
            return HDWalletKeys.builder()
                    .publicKey(publicKeyHex)
                    .address(address)
                    .derivationPath(derivationPath)
                    .build();
            
        } catch (Exception e) {
            log.error("Failed to derive address from path: {}", derivationPath, e);
            throw new HDWalletDerivationException("Failed to derive address", e);
        }
    }

    /**
     * Derive keys for specific currency following BIP44
     */
    private HDWalletKeys deriveKeysForCurrency(DeterministicKey masterKey, CryptoCurrency currency, 
                                               int accountIndex, int addressIndex) throws Exception {
        
        // BIP44 path: m / purpose' / coin_type' / account' / change / address_index
        String derivationPath = String.format("%s/%d'/%d'/0/%d", 
                HD_PATH_PREFIX, getCoinType(currency), accountIndex, addressIndex);
        
        DeterministicKey derivedKey = deriveKeyFromPath(masterKey, derivationPath);
        
        String privateKey = derivedKey.getPrivateKeyAsHex();
        String publicKey = derivedKey.getPublicKeyAsHex();
        String address = generateAddress(derivedKey, currency);
        String chainCode = Numeric.toHexString(derivedKey.getChainCode());
        
        return HDWalletKeys.builder()
                .privateKey(privateKey)
                .publicKey(publicKey)
                .address(address)
                .derivationPath(derivationPath)
                .chainCode(chainCode)
                .build();
    }

    /**
     * Derive key from path
     */
    private DeterministicKey deriveKeyFromPath(DeterministicKey masterKey, String path) {
        String[] pathComponents = path.substring(2).split("/"); // Remove "m/" prefix
        DeterministicKey currentKey = masterKey;
        
        for (String component : pathComponents) {
            boolean hardened = component.endsWith("'");
            int index = Integer.parseInt(component.replace("'", ""));
            
            if (hardened) {
                currentKey = HDKeyDerivation.deriveChildKey(currentKey, new ChildNumber(index, true));
            } else {
                currentKey = HDKeyDerivation.deriveChildKey(currentKey, new ChildNumber(index, false));
            }
        }
        
        return currentKey;
    }

    /**
     * Generate address based on currency
     */
    private String generateAddress(DeterministicKey key, CryptoCurrency currency) throws Exception {
        switch (currency) {
            case BITCOIN:
                return generateBitcoinAddress(key);
            case ETHEREUM:
            case USDC:
            case USDT:
                return generateEthereumAddress(key);
            case LITECOIN:
                return generateLitecoinAddress(key);
            default:
                throw new UnsupportedCurrencyException("Unsupported currency: " + currency);
        }
    }

    /**
     * Generate Bitcoin address (P2PKH)
     */
    private String generateBitcoinAddress(DeterministicKey key) {
        NetworkParameters params = NetworkParameters.fromID(NetworkParameters.ID_MAINNET);
        return key.toAddress(params).toString();
    }

    /**
     * Generate Ethereum address
     */
    private String generateEthereumAddress(DeterministicKey key) throws Exception {
        // Convert Bitcoin key to Ethereum format
        BigInteger privateKeyInt = new BigInteger(1, key.getPrivKeyBytes());
        ECKeyPair keyPair = ECKeyPair.create(privateKeyInt);
        
        return "0x" + Keys.getAddress(keyPair);
    }

    /**
     * Generate Litecoin address with proper version bytes
     * Litecoin mainnet addresses start with 'L' (P2PKH) or 'M' (P2SH)
     */
    private String generateLitecoinAddress(DeterministicKey key) {
        // Use custom Litecoin network parameters with correct version bytes
        NetworkParameters litecoinParams = LitecoinMainNetParams.get();

        // Generate P2PKH address (starts with 'L' for Litecoin mainnet)
        Address address = LegacyAddress.fromKey(litecoinParams, key);
        String litecoinAddress = address.toString();

        // Validate that the address starts with 'L' for mainnet
        if (!litecoinAddress.startsWith("L")) {
            log.warn("Generated Litecoin address does not start with 'L': {}", litecoinAddress);
        }

        log.debug("Generated Litecoin address: {}", litecoinAddress);
        return litecoinAddress;
    }

    /**
     * Generate address from public key only (for derived addresses)
     */
    private String generateAddressFromPublicKey(String publicKeyHex, CryptoCurrency currency, int index) throws Exception {
        byte[] publicKeyBytes = Numeric.hexStringToByteArray(publicKeyHex);
        
        switch (currency) {
            case BITCOIN:
                ECKey bitcoinKey = ECKey.fromPublicOnly(publicKeyBytes);
                NetworkParameters bitcoinParams = NetworkParameters.fromID(NetworkParameters.ID_MAINNET);
                Address bitcoinAddress = LegacyAddress.fromKey(bitcoinParams, bitcoinKey);
                return bitcoinAddress.toString();

            case LITECOIN:
                ECKey litecoinKey = ECKey.fromPublicOnly(publicKeyBytes);
                NetworkParameters litecoinParams = LitecoinMainNetParams.get();
                Address litecoinAddress = LegacyAddress.fromKey(litecoinParams, litecoinKey);
                return litecoinAddress.toString();
                
            case ETHEREUM:
            case USDC:
            case USDT:
                // For Ethereum, we need to hash the public key to get the address
                BigInteger publicKeyInt = new BigInteger(1, publicKeyBytes);
                return "0x" + Keys.getAddress(publicKeyInt.toString(16));
                
            default:
                throw new UnsupportedCurrencyException("Unsupported currency: " + currency);
        }
    }

    /**
     * Get coin type for BIP44
     */
    private int getCoinType(CryptoCurrency currency) {
        switch (currency) {
            case BITCOIN:
                return BITCOIN_COIN_TYPE;
            case ETHEREUM:
            case USDC:
            case USDT:
                return ETHEREUM_COIN_TYPE;
            case LITECOIN:
                return LITECOIN_COIN_TYPE;
            default:
                throw new UnsupportedCurrencyException("Unsupported currency: " + currency);
        }
    }

    /**
     * Validate mnemonic phrase
     */
    public boolean validateMnemonic(String mnemonic) {
        try {
            List<String> words = List.of(mnemonic.split(" "));
            MnemonicCode.INSTANCE.check(words);
            return true;
        } catch (Exception e) {
            log.debug("Invalid mnemonic phrase: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Restore wallet from mnemonic
     */
    public HDWalletKeys restoreFromMnemonic(String mnemonic, CryptoCurrency currency, int accountIndex, int addressIndex) {
        log.info("Restoring wallet from mnemonic for currency: {}", currency);
        
        try {
            List<String> words = List.of(mnemonic.split(" "));
            
            // Validate mnemonic
            MnemonicCode.INSTANCE.check(words);
            
            // Generate seed from mnemonic
            byte[] seed = MnemonicCode.toSeed(words, "");
            
            // Create master key
            DeterministicKey masterKey = HDKeyDerivation.createMasterPrivateKey(seed);
            
            // Derive keys for currency
            HDWalletKeys walletKeys = deriveKeysForCurrency(masterKey, currency, accountIndex, addressIndex);
            walletKeys.setMnemonic(mnemonic);
            
            log.info("Successfully restored wallet from mnemonic");
            
            return walletKeys;
            
        } catch (Exception e) {
            log.error("Failed to restore wallet from mnemonic", e);
            throw new HDWalletRestorationException("Failed to restore wallet from mnemonic", e);
        }
    }

    /**
     * Generate extended public key (xpub) for watch-only wallets
     */
    public String generateExtendedPublicKey(DeterministicKey key) {
        DeterministicKey pubOnly = key.dropPrivateBytes();
        return pubOnly.serializePubB58(NetworkParameters.fromID(NetworkParameters.ID_MAINNET));
    }

    /**
     * Create watch-only wallet from extended public key
     */
    public HDWalletKeys createWatchOnlyWallet(String xpub, CryptoCurrency currency, int addressIndex) {
        try {
            DeterministicKey watchKey = DeterministicKey.deserializeB58(
                xpub, NetworkParameters.fromID(NetworkParameters.ID_MAINNET));
            
            String derivationPath = String.format("m/0/%d", addressIndex);
            DeterministicKey derivedKey = HDKeyDerivation.deriveChildKey(watchKey, new ChildNumber(addressIndex, false));
            
            String publicKey = derivedKey.getPublicKeyAsHex();
            String address = generateAddress(derivedKey, currency);
            
            return HDWalletKeys.builder()
                    .publicKey(publicKey)
                    .address(address)
                    .derivationPath(derivationPath)
                    .build();
                    
        } catch (Exception e) {
            log.error("Failed to create watch-only wallet from xpub", e);
            throw new HDWalletException("Failed to create watch-only wallet", e);
        }
    }

    // Exception classes
    public static class HDWalletException extends RuntimeException {
        public HDWalletException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    public static class HDWalletGenerationException extends HDWalletException {
        public HDWalletGenerationException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    public static class HDWalletDerivationException extends HDWalletException {
        public HDWalletDerivationException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    public static class HDWalletRestorationException extends HDWalletException {
        public HDWalletRestorationException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    public static class UnsupportedCurrencyException extends HDWalletException {
        public UnsupportedCurrencyException(String message) {
            super(message, null);
        }
    }
}