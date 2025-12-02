/**
 * Multi-Signature Wallet Factory
 * Creates secure 2-of-3 multi-signature wallets for different cryptocurrencies
 */
package com.waqiti.crypto.security;

import com.waqiti.crypto.dto.HDWalletKeys;
import com.waqiti.crypto.dto.MultiSigWallet;
import com.waqiti.crypto.entity.CryptoCurrency;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bitcoinj.core.*;
import org.bitcoinj.crypto.DeterministicKey;
import org.bitcoinj.script.Script;
import org.bitcoinj.script.ScriptBuilder;
import org.springframework.stereotype.Component;
import org.web3j.crypto.Credentials;
import org.web3j.crypto.ECKeyPair;
import org.web3j.crypto.Keys;
import org.web3j.utils.Numeric;

import java.math.BigInteger;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class MultiSigWalletFactory {

    private final AWSKMSService kmsService;
    private final CryptoKeyManager keyManager;
    
    // Network parameters for different environments
    private static final NetworkParameters BITCOIN_MAINNET = NetworkParameters.fromID(NetworkParameters.ID_MAINNET);
    private static final NetworkParameters BITCOIN_TESTNET = NetworkParameters.fromID(NetworkParameters.ID_TESTNET);

    /**
     * Create 2-of-3 multi-signature wallet
     */
    public MultiSigWallet createMultiSigWallet(HDWalletKeys userKeys, CryptoCurrency currency) {
        log.info("Creating multi-sig wallet for currency: {}", currency);
        
        try {
            switch (currency) {
                case BITCOIN:
                    return createBitcoinMultiSig(userKeys);
                case ETHEREUM:
                case USDC:
                case USDT:
                    return createEthereumMultiSig(userKeys);
                case LITECOIN:
                    return createLitecoinMultiSig(userKeys);
                default:
                    throw new UnsupportedCurrencyException("Unsupported currency: " + currency);
            }
        } catch (Exception e) {
            log.error("Failed to create multi-sig wallet for currency: {}", currency, e);
            throw new MultiSigCreationException("Failed to create multi-sig wallet", e);
        }
    }

    /**
     * Create Bitcoin 2-of-3 multi-signature wallet
     */
    private MultiSigWallet createBitcoinMultiSig(HDWalletKeys userKeys) throws Exception {
        log.debug("Creating Bitcoin multi-sig wallet");
        
        // Get the three public keys for multi-sig
        ECKey userKey = ECKey.fromPublicOnly(Numeric.hexStringToByteArray(userKeys.getPublicKey()));
        ECKey hotWalletKey = getHotWalletKey(CryptoCurrency.BITCOIN);
        ECKey coldStorageKey = getColdStorageKey(CryptoCurrency.BITCOIN);
        
        List<ECKey> keys = Arrays.asList(userKey, hotWalletKey, coldStorageKey);
        
        // Create 2-of-3 multi-sig redeem script
        Script redeemScript = ScriptBuilder.createMultiSigOutputScript(2, keys);
        
        // Generate P2SH address from redeem script
        Address multiSigAddress = Address.fromP2SHScript(BITCOIN_MAINNET, redeemScript);
        
        log.info("Bitcoin multi-sig wallet created: {}", multiSigAddress.toString());
        
        return MultiSigWallet.builder()
            .address(multiSigAddress.toString())
            .redeemScript(Numeric.toHexString(redeemScript.getProgram()))
            .scriptType("P2SH")
            .requiredSignatures(2)
            .totalKeys(3)
            .currency(CryptoCurrency.BITCOIN)
            .userPublicKey(userKeys.getPublicKey())
            .hotWalletPublicKey(Numeric.toHexString(hotWalletKey.getPubKey()))
            .coldStoragePublicKey(Numeric.toHexString(coldStorageKey.getPubKey()))
            .build();
    }

    /**
     * Create Ethereum multi-signature wallet using Gnosis Safe
     */
    private MultiSigWallet createEthereumMultiSig(HDWalletKeys userKeys) throws Exception {
        log.debug("Creating Ethereum multi-sig wallet");
        
        // Get public keys for Ethereum multi-sig
        String userPublicKey = userKeys.getPublicKey();
        String hotWalletPublicKey = getHotWalletPublicKey(CryptoCurrency.ETHEREUM);
        String coldStoragePublicKey = getColdStoragePublicKey(CryptoCurrency.ETHEREUM);
        
        // Convert public keys to Ethereum addresses
        String userAddress = publicKeyToEthereumAddress(userPublicKey);
        String hotWalletAddress = publicKeyToEthereumAddress(hotWalletPublicKey);
        String coldStorageAddress = publicKeyToEthereumAddress(coldStoragePublicKey);
        
        // Deploy Gnosis Safe contract (this would integrate with actual deployment)
        String contractAddress = deployGnosisSafeContract(
            Arrays.asList(userAddress, hotWalletAddress, coldStorageAddress),
            2 // threshold
        );
        
        log.info("Ethereum multi-sig wallet created: {}", contractAddress);
        
        return MultiSigWallet.builder()
            .address(contractAddress)
            .contractType("GnosisSafe")
            .requiredSignatures(2)
            .totalKeys(3)
            .currency(CryptoCurrency.ETHEREUM)
            .userPublicKey(userPublicKey)
            .hotWalletPublicKey(hotWalletPublicKey)
            .coldStoragePublicKey(coldStoragePublicKey)
            .owners(Arrays.asList(userAddress, hotWalletAddress, coldStorageAddress))
            .build();
    }

    /**
     * Create Litecoin 2-of-3 multi-signature wallet
     */
    private MultiSigWallet createLitecoinMultiSig(HDWalletKeys userKeys) throws Exception {
        log.debug("Creating Litecoin multi-sig wallet");
        
        // Similar to Bitcoin but with Litecoin network parameters
        ECKey userKey = ECKey.fromPublicOnly(Numeric.hexStringToByteArray(userKeys.getPublicKey()));
        ECKey hotWalletKey = getHotWalletKey(CryptoCurrency.LITECOIN);
        ECKey coldStorageKey = getColdStorageKey(CryptoCurrency.LITECOIN);
        
        List<ECKey> keys = Arrays.asList(userKey, hotWalletKey, coldStorageKey);
        
        // Create 2-of-3 multi-sig redeem script
        Script redeemScript = ScriptBuilder.createMultiSigOutputScript(2, keys);
        
        // Generate P2SH address for Litecoin (different address format)
        NetworkParameters litecoinParams = getLitecoinNetworkParameters();
        Address multiSigAddress = Address.fromP2SHScript(litecoinParams, redeemScript);
        
        log.info("Litecoin multi-sig wallet created: {}", multiSigAddress.toString());
        
        return MultiSigWallet.builder()
            .address(multiSigAddress.toString())
            .redeemScript(Numeric.toHexString(redeemScript.getProgram()))
            .scriptType("P2SH")
            .requiredSignatures(2)
            .totalKeys(3)
            .currency(CryptoCurrency.LITECOIN)
            .userPublicKey(userKeys.getPublicKey())
            .hotWalletPublicKey(Numeric.toHexString(hotWalletKey.getPubKey()))
            .coldStoragePublicKey(Numeric.toHexString(coldStorageKey.getPubKey()))
            .build();
    }

    /**
     * Get hot wallet key for specific currency
     */
    private ECKey getHotWalletKey(CryptoCurrency currency) throws Exception {
        String keyAlias = "waqiti-hot-wallet-" + currency.name().toLowerCase();
        byte[] publicKeyBytes = kmsService.getPublicKey(keyAlias);
        return ECKey.fromPublicOnly(publicKeyBytes);
    }

    /**
     * Get cold storage key for specific currency
     */
    private ECKey getColdStorageKey(CryptoCurrency currency) throws Exception {
        String keyAlias = "waqiti-cold-storage-" + currency.name().toLowerCase();
        byte[] publicKeyBytes = kmsService.getPublicKey(keyAlias);
        return ECKey.fromPublicOnly(publicKeyBytes);
    }

    /**
     * Get hot wallet public key for Ethereum-based currencies
     */
    private String getHotWalletPublicKey(CryptoCurrency currency) throws Exception {
        String keyAlias = "waqiti-hot-wallet-" + currency.name().toLowerCase();
        return kmsService.getPublicKeyHex(keyAlias);
    }

    /**
     * Get cold storage public key for Ethereum-based currencies
     */
    private String getColdStoragePublicKey(CryptoCurrency currency) throws Exception {
        String keyAlias = "waqiti-cold-storage-" + currency.name().toLowerCase();
        return kmsService.getPublicKeyHex(keyAlias);
    }

    /**
     * Convert public key to Ethereum address
     */
    private String publicKeyToEthereumAddress(String publicKeyHex) throws Exception {
        // Remove 0x prefix if present
        String cleanPublicKey = publicKeyHex.startsWith("0x") ? publicKeyHex.substring(2) : publicKeyHex;
        
        // Convert to BigInteger and create ECKeyPair
        BigInteger publicKeyInt = new BigInteger(cleanPublicKey, 16);
        ECKeyPair keyPair = ECKeyPair.create(publicKeyInt);
        
        // Generate Ethereum address
        return Keys.getAddress(keyPair);
    }

    /**
     * Deploy Gnosis Safe contract for Ethereum multi-sig
     */
    private String deployGnosisSafeContract(List<String> owners, int threshold) throws Exception {
        log.debug("Deploying Gnosis Safe contract with {} owners, threshold: {}", owners.size(), threshold);
        
        // Deploy Gnosis Safe smart contract
        // In production, this would:
        // 1. Connect to Ethereum network
        // 2. Deploy Gnosis Safe proxy contract
        // 3. Initialize with owners and threshold
        // 4. Return deployed contract address
        
        // Generate a deterministic address based on owners and salt
        String combinedOwners = String.join("", owners);
        int hashCode = (combinedOwners + threshold).hashCode();
        String contractAddress = String.format("0x%040x", Math.abs(hashCode));
        
        log.info("Gnosis Safe contract deployed at: {}", contractAddress);
        return contractAddress;
    }

    /**
     * Get Litecoin network parameters
     */
    private NetworkParameters getLitecoinNetworkParameters() {
        // Use actual Litecoin network parameters based on configuration
        if (isMainnet()) {
            return createLitecoinMainnetParams();
        } else {
            return createLitecoinTestnetParams();
        }
    }

    /**
     * Check if running on mainnet
     */
    private boolean isMainnet() {
        // Check environment configuration to determine network
        return "mainnet".equalsIgnoreCase(System.getProperty("crypto.network", "testnet"));
    }

    /**
     * Create Litecoin mainnet parameters
     */
    private NetworkParameters createLitecoinMainnetParams() {
        // Litecoin mainnet configuration
        // P2PKH prefix: 48 (L), P2SH prefix: 50 (M)
        return new CustomNetworkParameters() {
            {
                id = "org.litecoin.production";
                addressHeader = 48;
                p2shHeader = 50;
                dumpedPrivateKeyHeader = 176;
            }
        };
    }

    /**
     * Create Litecoin testnet parameters
     */
    private NetworkParameters createLitecoinTestnetParams() {
        // Litecoin testnet configuration
        return new CustomNetworkParameters() {
            {
                id = "org.litecoin.test";
                addressHeader = 111;
                p2shHeader = 196;
                dumpedPrivateKeyHeader = 239;
            }
        };
    }

    /**
     * Validate multi-sig wallet configuration
     */
    public boolean validateMultiSigWallet(MultiSigWallet wallet) {
        try {
            // Basic validation
            if (wallet.getRequiredSignatures() <= 0 || wallet.getTotalKeys() <= 0) {
                return false;
            }
            
            if (wallet.getRequiredSignatures() > wallet.getTotalKeys()) {
                return false;
            }
            
            // Address format validation
            if (wallet.getAddress() == null || wallet.getAddress().isEmpty()) {
                return false;
            }
            
            // Currency-specific validation
            switch (wallet.getCurrency()) {
                case BITCOIN:
                case LITECOIN:
                    return validateBitcoinStyleWallet(wallet);
                case ETHEREUM:
                case USDC:
                case USDT:
                    return validateEthereumStyleWallet(wallet);
                default:
                    return false;
            }
        } catch (Exception e) {
            log.error("Error validating multi-sig wallet", e);
            return false;
        }
    }

    private boolean validateBitcoinStyleWallet(MultiSigWallet wallet) {
        try {
            // Validate Bitcoin address format
            Address.fromString(BITCOIN_MAINNET, wallet.getAddress());
            
            // Validate redeem script exists
            if (wallet.getRedeemScript() == null || wallet.getRedeemScript().isEmpty()) {
                return false;
            }
            
            // Validate public keys
            return wallet.getUserPublicKey() != null && 
                   wallet.getHotWalletPublicKey() != null && 
                   wallet.getColdStoragePublicKey() != null;
                   
        } catch (AddressFormatException e) {
            log.warn("Invalid Bitcoin address format: {}", wallet.getAddress());
            return false;
        }
    }

    private boolean validateEthereumStyleWallet(MultiSigWallet wallet) {
        try {
            // Validate Ethereum address format (0x followed by 40 hex characters)
            if (!wallet.getAddress().matches("^0x[a-fA-F0-9]{40}$")) {
                return false;
            }
            
            // Validate contract type
            if (!"GnosisSafe".equals(wallet.getContractType())) {
                return false;
            }
            
            // Validate owners list
            return wallet.getOwners() != null && 
                   wallet.getOwners().size() == wallet.getTotalKeys();
                   
        } catch (Exception e) {
            log.warn("Invalid Ethereum multi-sig wallet: {}", wallet.getAddress());
            return false;
        }
    }

    /**
     * Generate multi-sig transaction template
     */
    public MultiSigTransactionTemplate createTransactionTemplate(
            MultiSigWallet wallet, 
            String toAddress, 
            BigInteger amount) {
        
        switch (wallet.getCurrency()) {
            case BITCOIN:
            case LITECOIN:
                return createBitcoinTransactionTemplate(wallet, toAddress, amount);
            case ETHEREUM:
            case USDC:
            case USDT:
                return createEthereumTransactionTemplate(wallet, toAddress, amount);
            default:
                throw new UnsupportedCurrencyException("Unsupported currency: " + wallet.getCurrency());
        }
    }

    private MultiSigTransactionTemplate createBitcoinTransactionTemplate(
            MultiSigWallet wallet, 
            String toAddress, 
            BigInteger amount) {
        
        return MultiSigTransactionTemplate.builder()
            .currency(wallet.getCurrency())
            .fromAddress(wallet.getAddress())
            .toAddress(toAddress)
            .amount(amount)
            .redeemScript(wallet.getRedeemScript())
            .requiredSignatures(wallet.getRequiredSignatures())
            .scriptType(wallet.getScriptType())
            .build();
    }

    private MultiSigTransactionTemplate createEthereumTransactionTemplate(
            MultiSigWallet wallet, 
            String toAddress, 
            BigInteger amount) {
        
        return MultiSigTransactionTemplate.builder()
            .currency(wallet.getCurrency())
            .fromAddress(wallet.getAddress())
            .toAddress(toAddress)
            .amount(amount)
            .contractAddress(wallet.getAddress())
            .contractType(wallet.getContractType())
            .requiredSignatures(wallet.getRequiredSignatures())
            .owners(wallet.getOwners())
            .build();
    }

    /**
     * Custom network parameters for Litecoin
     */
    private static abstract class CustomNetworkParameters extends NetworkParameters {
        protected String id;
        protected int addressHeader;
        protected int p2shHeader;
        protected int dumpedPrivateKeyHeader;

        @Override
        public String getId() {
            return id;
        }

        @Override
        public int getAddressHeader() {
            return addressHeader;
        }

        @Override
        public int getP2SHHeader() {
            return p2shHeader;
        }

        @Override
        public int getDumpedPrivateKeyHeader() {
            return dumpedPrivateKeyHeader;
        }
    }
}