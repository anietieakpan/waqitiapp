/**
 * Crypto Wallet Service
 * Core service for managing cryptocurrency wallets with HD wallet and multi-signature support
 */
package com.waqiti.crypto.service;

import com.waqiti.crypto.dto.*;
import com.waqiti.crypto.entity.*;
import com.waqiti.crypto.repository.*;
import com.waqiti.crypto.security.AWSKMSService;
import com.waqiti.crypto.security.HDWalletGenerator;
import com.waqiti.crypto.security.MultiSigWalletFactory;
import com.waqiti.crypto.blockchain.BlockchainServiceFactory;
import com.waqiti.crypto.compliance.ComplianceService;
import com.waqiti.common.events.CryptoWalletCreatedEvent;
import com.waqiti.common.kyc.annotation.RequireKYCVerification;
import com.waqiti.common.kyc.annotation.RequireKYCVerification.VerificationLevel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class CryptoWalletService {

    private final CryptoWalletRepository cryptoWalletRepository;
    private final CryptoBalanceRepository cryptoBalanceRepository;
    private final CryptoAddressRepository cryptoAddressRepository;
    private final AWSKMSService kmsService;
    private final HDWalletGenerator hdWalletGenerator;
    private final MultiSigWalletFactory multiSigWalletFactory;
    private final BlockchainServiceFactory blockchainServiceFactory;
    private final ComplianceService complianceService;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final CryptoPriceOracleService priceOracleService;

    /**
     * Create a new cryptocurrency wallet for user
     */
    @RequireKYCVerification(level = VerificationLevel.ADVANCED, action = "CRYPTO_WALLET")
    public CryptoWalletResponse createCryptoWallet(UUID userId, CreateCryptoWalletRequest request) {
        log.info("Creating crypto wallet for user: {} currency: {}", userId, request.getCurrency());
        
        try {
            // Validate user eligibility and compliance
            complianceService.validateWalletCreation(userId, request.getCurrency());
            
            // Check if wallet already exists
            if (cryptoWalletRepository.existsByUserIdAndCurrency(userId, request.getCurrency())) {
                throw new WalletAlreadyExistsException("Wallet already exists for currency: " + request.getCurrency());
            }
            
            // Generate HD wallet keys
            HDWalletKeys hdKeys = hdWalletGenerator.generateHDWallet(userId, request.getCurrency());
            
            // Create multi-signature wallet
            MultiSigWallet multiSigWallet = multiSigWalletFactory.createMultiSigWallet(
                hdKeys, request.getCurrency());
            
            // Encrypt and store private key with AWS KMS
            EncryptedKey encryptedKey = kmsService.encryptPrivateKey(hdKeys.getPrivateKey(), userId, request.getCurrency());
            
            // Create wallet entity
            CryptoWallet wallet = CryptoWallet.builder()
                .userId(userId)
                .currency(request.getCurrency())
                .walletType(WalletType.MULTISIG_HD)
                .derivationPath(hdKeys.getDerivationPath())
                .publicKey(hdKeys.getPublicKey())
                .encryptedPrivateKey(encryptedKey.getEncryptedData())
                .kmsKeyId(encryptedKey.getKeyId())
                .encryptionContext(encryptedKey.getEncryptionContext())
                .multiSigAddress(multiSigWallet.getAddress())
                .redeemScript(multiSigWallet.getRedeemScript())
                .requiredSignatures(multiSigWallet.getRequiredSignatures())
                .totalKeys(multiSigWallet.getTotalKeys())
                .status(WalletStatus.ACTIVE)
                .createdAt(LocalDateTime.now())
                .build();
            
            wallet = cryptoWalletRepository.save(wallet);
            
            // Initialize balance record
            CryptoBalance balance = CryptoBalance.builder()
                .walletId(wallet.getId())
                .currency(request.getCurrency())
                .availableBalance(BigDecimal.ZERO)
                .pendingBalance(BigDecimal.ZERO)
                .stakedBalance(BigDecimal.ZERO)
                .totalBalance(BigDecimal.ZERO)
                .lastUpdated(LocalDateTime.now())
                .build();
            
            cryptoBalanceRepository.save(balance);
            
            // Generate initial receiving addresses
            generateReceivingAddresses(wallet, 5);
            
            // Publish wallet created event
            publishWalletCreatedEvent(wallet);
            
            log.info("Crypto wallet created successfully: {} for user: {}", wallet.getId(), userId);
            
            return mapToWalletResponse(wallet, balance);
            
        } catch (Exception e) {
            log.error("Failed to create crypto wallet for user: {} currency: {}", userId, request.getCurrency(), e);
            throw new CryptoWalletCreationException("Failed to create crypto wallet", e);
        }
    }

    /**
     * Get user's cryptocurrency wallets
     */
    @Transactional(readOnly = true)
    public List<CryptoWalletResponse> getUserCryptoWallets(UUID userId) {
        log.debug("Getting crypto wallets for user: {}", userId);
        
        List<CryptoWallet> wallets = cryptoWalletRepository.findByUserIdAndStatus(userId, WalletStatus.ACTIVE);
        
        return wallets.stream()
            .map(wallet -> {
                CryptoBalance balance = cryptoBalanceRepository.findByWalletId(wallet.getId())
                    .orElse(createEmptyBalance(wallet));
                return mapToWalletResponse(wallet, balance);
            })
            .collect(Collectors.toList());
    }

    /**
     * Get detailed wallet information
     */
    @Transactional(readOnly = true)
    public CryptoWalletDetailsResponse getCryptoWalletDetails(UUID userId, UUID walletId) {
        log.debug("Getting crypto wallet details: {} for user: {}", walletId, userId);
        
        CryptoWallet wallet = cryptoWalletRepository.findByIdAndUserId(walletId, userId)
            .orElseThrow(() -> new WalletNotFoundException("Wallet not found: " + walletId));
        
        CryptoBalance balance = cryptoBalanceRepository.findByWalletId(walletId)
            .orElse(createEmptyBalance(wallet));
        
        List<CryptoAddress> addresses = cryptoAddressRepository.findByWalletIdAndStatus(
            walletId, AddressStatus.ACTIVE);
        
        // Get current market price from oracle
        BigDecimal currentPrice = priceOracleService.getCurrentPrice(wallet.getCurrency())
            .defaultIfEmpty(BigDecimal.ZERO)
            .block();
        BigDecimal usdValue = balance.getTotalBalance().multiply(currentPrice != null ? currentPrice : BigDecimal.ZERO);
        
        return CryptoWalletDetailsResponse.builder()
            .walletId(wallet.getId())
            .currency(wallet.getCurrency())
            .walletType(wallet.getWalletType())
            .multiSigAddress(wallet.getMultiSigAddress())
            .availableBalance(balance.getAvailableBalance())
            .pendingBalance(balance.getPendingBalance())
            .stakedBalance(balance.getStakedBalance())
            .totalBalance(balance.getTotalBalance())
            .usdValue(usdValue)
            .currentPrice(currentPrice)
            .addresses(addresses.stream()
                .map(this::mapToAddressResponse)
                .collect(Collectors.toList()))
            .createdAt(wallet.getCreatedAt())
            .lastUpdated(balance.getLastUpdated())
            .build();
    }

    /**
     * Generate new receiving address for wallet
     */
    public CryptoReceiveResponse generateReceiveAddress(UUID userId, GenerateReceiveAddressRequest request) {
        log.info("Generating receive address for user: {} currency: {}", userId, request.getCurrency());
        
        try {
            CryptoWallet wallet = cryptoWalletRepository.findByUserIdAndCurrency(userId, request.getCurrency())
                .orElseThrow(() -> new WalletNotFoundException("Wallet not found for currency: " + request.getCurrency()));
            
            // Generate new address from HD wallet
            int nextAddressIndex = getNextAddressIndex(wallet.getId());
            String derivationPath = wallet.getDerivationPath() + "/" + nextAddressIndex;
            
            HDWalletKeys addressKeys = hdWalletGenerator.deriveAddress(
                wallet.getPublicKey(), derivationPath, request.getCurrency());
            
            // Create address record
            CryptoAddress address = CryptoAddress.builder()
                .walletId(wallet.getId())
                .address(addressKeys.getAddress())
                .derivationPath(derivationPath)
                .publicKey(addressKeys.getPublicKey())
                .addressIndex(nextAddressIndex)
                .addressType(request.getAddressType() != null ? request.getAddressType() : AddressType.RECEIVING)
                .label(request.getLabel())
                .status(AddressStatus.ACTIVE)
                .createdAt(LocalDateTime.now())
                .build();
            
            address = cryptoAddressRepository.save(address);
            
            // Generate QR code data
            String qrCodeData = generateQRCodeData(address, request);
            
            log.info("Receive address generated: {} for wallet: {}", address.getAddress(), wallet.getId());
            
            return CryptoReceiveResponse.builder()
                .address(address.getAddress())
                .qrCodeData(qrCodeData)
                .addressType(address.getAddressType())
                .label(address.getLabel())
                .currency(wallet.getCurrency())
                .derivationPath(address.getDerivationPath())
                .createdAt(address.getCreatedAt())
                .build();
            
        } catch (Exception e) {
            log.error("Failed to generate receive address for user: {} currency: {}", userId, request.getCurrency(), e);
            throw new AddressGenerationException("Failed to generate receive address", e);
        }
    }

    /**
     * Update wallet balance
     */
    public void updateWalletBalance(UUID walletId, BigDecimal amount, BalanceUpdateType updateType) {
        log.debug("Updating wallet balance: {} amount: {} type: {}", walletId, amount, updateType);
        
        try {
            CryptoBalance balance = cryptoBalanceRepository.findByWalletId(walletId)
                .orElseThrow(() -> new BalanceNotFoundException("Balance not found for wallet: " + walletId));
            
            switch (updateType) {
                case AVAILABLE_INCREASE:
                    balance.setAvailableBalance(balance.getAvailableBalance().add(amount));
                    break;
                case AVAILABLE_DECREASE:
                    balance.setAvailableBalance(balance.getAvailableBalance().subtract(amount));
                    break;
                case PENDING_INCREASE:
                    balance.setPendingBalance(balance.getPendingBalance().add(amount));
                    break;
                case PENDING_DECREASE:
                    balance.setPendingBalance(balance.getPendingBalance().subtract(amount));
                    break;
                case PENDING_TO_AVAILABLE:
                    balance.setPendingBalance(balance.getPendingBalance().subtract(amount));
                    balance.setAvailableBalance(balance.getAvailableBalance().add(amount));
                    break;
                case STAKED_INCREASE:
                    balance.setStakedBalance(balance.getStakedBalance().add(amount));
                    break;
                case STAKED_DECREASE:
                    balance.setStakedBalance(balance.getStakedBalance().subtract(amount));
                    break;
            }
            
            // Update total balance
            balance.setTotalBalance(
                balance.getAvailableBalance()
                    .add(balance.getPendingBalance())
                    .add(balance.getStakedBalance())
            );
            
            balance.setLastUpdated(LocalDateTime.now());
            cryptoBalanceRepository.save(balance);
            
        } catch (Exception e) {
            log.error("Failed to update wallet balance: {} amount: {} type: {}", walletId, amount, updateType, e);
            throw new BalanceUpdateException("Failed to update wallet balance", e);
        }
    }

    /**
     * Validate wallet ownership and status
     */
    public void validateWalletAccess(UUID userId, UUID walletId) {
        CryptoWallet wallet = cryptoWalletRepository.findByIdAndUserId(walletId, userId)
            .orElseThrow(() -> new WalletAccessDeniedException("Wallet access denied: " + walletId));
        
        if (wallet.getStatus() != WalletStatus.ACTIVE) {
            throw new WalletInactiveException("Wallet is not active: " + walletId);
        }
    }

    /**
     * Get wallet by currency for user
     */
    @Transactional(readOnly = true)
    public CryptoWallet getWalletByCurrency(UUID userId, CryptoCurrency currency) {
        return cryptoWalletRepository.findByUserIdAndCurrency(userId, currency)
            .orElseThrow(() -> new WalletNotFoundException("Wallet not found for currency: " + currency));
    }

    // Private helper methods
    
    private void generateReceivingAddresses(CryptoWallet wallet, int count) {
        for (int i = 0; i < count; i++) {
            try {
                int addressIndex = i;
                String derivationPath = wallet.getDerivationPath() + "/" + addressIndex;
                
                HDWalletKeys addressKeys = hdWalletGenerator.deriveAddress(
                    wallet.getPublicKey(), derivationPath, wallet.getCurrency());
                
                CryptoAddress address = CryptoAddress.builder()
                    .walletId(wallet.getId())
                    .address(addressKeys.getAddress())
                    .derivationPath(derivationPath)
                    .publicKey(addressKeys.getPublicKey())
                    .addressIndex(addressIndex)
                    .addressType(AddressType.RECEIVING)
                    .status(AddressStatus.ACTIVE)
                    .createdAt(LocalDateTime.now())
                    .build();
                
                cryptoAddressRepository.save(address);
                
            } catch (Exception e) {
                log.warn("Failed to generate receiving address {} for wallet: {}", i, wallet.getId(), e);
            }
        }
    }
    
    private int getNextAddressIndex(UUID walletId) {
        return cryptoAddressRepository.findMaxAddressIndexByWalletId(walletId)
            .map(index -> index + 1)
            .orElse(0);
    }
    
    private String generateQRCodeData(CryptoAddress address, GenerateReceiveAddressRequest request) {
        StringBuilder qrData = new StringBuilder();
        
        // Get the currency from the request since it's not stored in CryptoAddress
        CryptoCurrency currency = request.getCurrency();
        
        switch (currency) {
            case BITCOIN:
                qrData.append("bitcoin:");
                break;
            case ETHEREUM:
                qrData.append("ethereum:");
                break;
            default:
                // For other currencies, use the address directly
                break;
        }
        
        qrData.append(address.getAddress());
        
        if (request.getAmount() != null) {
            qrData.append("?amount=").append(request.getAmount());
        }
        
        if (request.getLabel() != null) {
            qrData.append(qrData.toString().contains("?") ? "&" : "?")
                .append("label=").append(request.getLabel());
        }
        
        return qrData.toString();
    }
    
    private BigDecimal getCurrentMarketPrice(CryptoCurrency currency) {
        return priceOracleService.getCurrentPrice(currency)
            .defaultIfEmpty(getEmergencyFallbackPrice(currency))
            .block();
    }

    private BigDecimal getEmergencyFallbackPrice(CryptoCurrency currency) {
        log.warn("EMERGENCY: Using fallback price for {} - price oracles unavailable", currency);
        return switch (currency) {
            case BITCOIN -> new BigDecimal("43000.00");
            case ETHEREUM -> new BigDecimal("2800.00");
            case LITECOIN -> new BigDecimal("140.00");
            case USDC, USDT -> new BigDecimal("1.00");
            default -> BigDecimal.ZERO;
        };
    }
    
    private CryptoBalance createEmptyBalance(CryptoWallet wallet) {
        return CryptoBalance.builder()
            .walletId(wallet.getId())
            .currency(wallet.getCurrency())
            .availableBalance(BigDecimal.ZERO)
            .pendingBalance(BigDecimal.ZERO)
            .stakedBalance(BigDecimal.ZERO)
            .totalBalance(BigDecimal.ZERO)
            .lastUpdated(LocalDateTime.now())
            .build();
    }
    
    private CryptoWalletResponse mapToWalletResponse(CryptoWallet wallet, CryptoBalance balance) {
        BigDecimal currentPrice = getCurrentMarketPrice(wallet.getCurrency());
        BigDecimal usdValue = balance.getTotalBalance().multiply(currentPrice);
        
        return CryptoWalletResponse.builder()
            .walletId(wallet.getId())
            .currency(wallet.getCurrency())
            .walletType(wallet.getWalletType())
            .multiSigAddress(wallet.getMultiSigAddress())
            .availableBalance(balance.getAvailableBalance())
            .pendingBalance(balance.getPendingBalance())
            .stakedBalance(balance.getStakedBalance())
            .totalBalance(balance.getTotalBalance())
            .usdValue(usdValue)
            .currentPrice(currentPrice)
            .status(wallet.getStatus())
            .createdAt(wallet.getCreatedAt())
            .lastUpdated(balance.getLastUpdated())
            .build();
    }
    
    private CryptoAddressResponse mapToAddressResponse(CryptoAddress address) {
        return CryptoAddressResponse.builder()
            .address(address.getAddress())
            .addressType(address.getAddressType())
            .label(address.getLabel())
            .derivationPath(address.getDerivationPath())
            .addressIndex(address.getAddressIndex())
            .status(address.getStatus())
            .createdAt(address.getCreatedAt())
            .build();
    }
    
    private void publishWalletCreatedEvent(CryptoWallet wallet) {
        CryptoWalletCreatedEvent event = CryptoWalletCreatedEvent.builder()
            .walletId(wallet.getId())
            .userId(wallet.getUserId())
            .currency(wallet.getCurrency().name())
            .address(wallet.getMultiSigAddress())
            .timestamp(LocalDateTime.now())
            .build();
        
        kafkaTemplate.send("crypto-wallet-created", event);
    }
}