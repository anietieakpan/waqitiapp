/**
 * Crypto Address Entity
 * JPA entity representing cryptocurrency addresses derived from HD wallets
 */
package com.waqiti.crypto.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "crypto_addresses")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CryptoAddress {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "wallet_id", nullable = false)
    private UUID walletId;

    @Column(name = "address", nullable = false, unique = true)
    private String address;

    @Column(name = "derivation_path", nullable = false)
    private String derivationPath;

    @Column(name = "public_key", nullable = false, columnDefinition = "TEXT")
    private String publicKey;

    @Column(name = "address_index", nullable = false)
    private Integer addressIndex;

    @Enumerated(EnumType.STRING)
    @Column(name = "address_type", nullable = false)
    @Builder.Default
    private AddressType addressType = AddressType.RECEIVING;

    @Column(name = "label", length = 100)
    private String label;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    @Builder.Default
    private AddressStatus status = AddressStatus.ACTIVE;

    @Column(name = "used_count", nullable = false)
    @Builder.Default
    private Integer usedCount = 0;

    @Column(name = "last_used_at")
    private LocalDateTime lastUsedAt;

    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @Version
    private Long version;
    // Helper method to get currency from wallet
    @Transient
    private CryptoCurrency walletCurrency;

    public CryptoCurrency getWalletCurrency() {
        return walletCurrency;
    }

    public void setWalletCurrency(CryptoCurrency currency) {
        this.walletCurrency = currency;
    }

    // Convenience methods
    public void markAsUsed() {
        this.usedCount++;
        this.lastUsedAt = LocalDateTime.now();
        if (this.status == AddressStatus.ACTIVE && this.usedCount >= 1) {
            this.status = AddressStatus.USED;
        }
    }

    public void deactivate() {
        this.status = AddressStatus.INACTIVE;
    }

    public void reactivate() {
        this.status = AddressStatus.ACTIVE;
    }

    public boolean isActive() {
        return this.status == AddressStatus.ACTIVE;
    }

    public boolean isUsed() {
        return this.usedCount > 0;
    }
}