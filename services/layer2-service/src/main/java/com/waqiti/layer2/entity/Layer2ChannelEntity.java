package com.waqiti.layer2.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Type;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "layer2_channel")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class Layer2ChannelEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "channel_id", unique = true, nullable = false, length = 100)
    private String channelId;

    @Column(name = "channel_type", nullable = false, length = 50)
    private String channelType;

    @Column(name = "blockchain_network", nullable = false, length = 50)
    private String blockchainNetwork;

    @Column(name = "channel_status", nullable = false, length = 20)
    private String channelStatus;

    @Column(name = "participant_addresses", columnDefinition = "text[]")
    private String[] participantAddresses;

    @Column(name = "initiator_address", nullable = false, length = 100)
    private String initiatorAddress;

    @Column(name = "responder_address", length = 100)
    private String responderAddress;

    @Column(name = "multi_party")
    private Boolean multiParty;

    @Column(name = "total_participants", nullable = false)
    private Integer totalParticipants;

    @Column(name = "channel_balance", precision = 30, scale = 18, nullable = false)
    private BigDecimal channelBalance;

    @Column(name = "initial_deposits", columnDefinition = "jsonb", nullable = false)
    private String initialDeposits;

    @Column(name = "current_balances", columnDefinition = "jsonb", nullable = false)
    private String currentBalances;

    @Column(name = "asset_type", nullable = false, length = 50)
    private String assetType;

    @Column(name = "asset_contract_address", length = 100)
    private String assetContractAddress;

    @Column(name = "channel_capacity", precision = 30, scale = 18)
    private BigDecimal channelCapacity;

    @Column(name = "opened_at")
    private LocalDateTime openedAt;

    @Column(name = "last_state_update")
    private LocalDateTime lastStateUpdate;

    @Column(name = "challenge_period_seconds")
    private Integer challengePeriodSeconds;

    @Column(name = "dispute_deadline")
    private LocalDateTime disputeDeadline;

    @Column(name = "closed_at")
    private LocalDateTime closedAt;

    @Column(name = "closure_reason", length = 100)
    private String closureReason;

    @Column(name = "settlement_transaction_hash", length = 100)
    private String settlementTransactionHash;

    @Column(name = "on_chain_updates")
    private Integer onChainUpdates;

    @Column(name = "off_chain_transactions")
    private Integer offChainTransactions;

    @Column(name = "total_volume", precision = 30, scale = 18)
    private BigDecimal totalVolume;

    @Column(name = "channel_metadata", columnDefinition = "jsonb")
    private String channelMetadata;

    @Column(name = "smart_contract_address", length = 100)
    private String smartContractAddress;

    @Column(name = "nonce")
    private Integer nonce;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
