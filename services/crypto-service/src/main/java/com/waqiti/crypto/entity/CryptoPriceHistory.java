/**
 * Crypto Price History Entity
 * JPA entity representing cryptocurrency price history
 */
package com.waqiti.crypto.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "crypto_price_history")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CryptoPriceHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "currency", nullable = false)
    private CryptoCurrency currency;

    @Column(name = "price", nullable = false, precision = 12, scale = 8)
    private BigDecimal price;

    @Column(name = "volume_24h", precision = 20, scale = 2)
    private BigDecimal volume24h;

    @Column(name = "market_cap", precision = 20, scale = 2)
    private BigDecimal marketCap;

    @Column(name = "change_24h", precision = 12, scale = 8)
    private BigDecimal change24h;

    @Column(name = "change_percent_24h", precision = 8, scale = 4)
    private BigDecimal changePercent24h;

    @Column(name = "timestamp", nullable = false)
    @Builder.Default
    private LocalDateTime timestamp = LocalDateTime.now();

    @Version
    private Long version;
    @Column(name = "source", length = 50)
    @Builder.Default
    private String source = "INTERNAL";
}