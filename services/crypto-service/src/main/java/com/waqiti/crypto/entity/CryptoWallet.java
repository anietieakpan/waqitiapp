/**
 * Crypto Wallet Entity
 * JPA entity representing cryptocurrency wallets
 */
package com.waqiti.crypto.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Type;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "crypto_wallets")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CryptoWallet {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "currency", nullable = false)
    private CryptoCurrency currency;

    @Enumerated(EnumType.STRING)
    @Column(name = "wallet_type", nullable = false)
    @Builder.Default
    private WalletType walletType = WalletType.MULTISIG_HD;

    @Column(name = "derivation_path", nullable = false)
    private String derivationPath;

    @Column(name = "public_key", nullable = false, columnDefinition = "TEXT")
    private String publicKey;

    @Column(name = "encrypted_private_key", nullable = false, columnDefinition = "TEXT")
    private String encryptedPrivateKey;

    @Column(name = "kms_key_id", nullable = false)
    private String kmsKeyId;

    @Type(io.hypersistence.utils.hibernate.type.json.JsonType.class)
    @Column(name = "encryption_context", columnDefinition = "jsonb")
    private Map<String, String> encryptionContext;

    @Column(name = "multi_sig_address", nullable = false, unique = true)
    private String multiSigAddress;

    @Column(name = "redeem_script", columnDefinition = "TEXT")
    private String redeemScript;

    @Column(name = "required_signatures", nullable = false)
    @Builder.Default
    private Integer requiredSignatures = 2;

    @Column(name = "total_keys", nullable = false)
    @Builder.Default
    private Integer totalKeys = 3;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    @Builder.Default
    private WalletStatus status = WalletStatus.ACTIVE;

    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at", nullable = false)
    @Builder.Default
    private LocalDateTime updatedAt = LocalDateTime.now();
    
    @Version
    private Long version;

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}