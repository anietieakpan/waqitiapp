/**
 * Blockchain Node Manager
 * Manages connections to blockchain nodes and health monitoring
 */
package com.waqiti.crypto.blockchain;

import com.waqiti.crypto.entity.CryptoCurrency;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
@RequiredArgsConstructor
@Slf4j
public class BlockchainNodeManager {

    private final BitcoinService bitcoinService;
    private final EthereumService ethereumService;
    private final LitecoinService litecoinService;

    private final Map<CryptoCurrency, NodeStatus> nodeStatusMap = new ConcurrentHashMap<>();

    /**
     * Check health of all blockchain nodes
     */
    @Scheduled(fixedDelay = 60000) // Every minute
    public void checkNodeHealth() {
        log.debug("Checking blockchain node health");

        // Check Bitcoin node
        checkBitcoinNodeHealth();
        
        // Check Ethereum node
        checkEthereumNodeHealth();
        
        // Check Litecoin node
        checkLitecoinNodeHealth();
    }

    /**
     * Get node status for currency
     */
    public NodeStatus getNodeStatus(CryptoCurrency currency) {
        return nodeStatusMap.getOrDefault(currency, NodeStatus.UNKNOWN);
    }

    /**
     * Check if node is healthy for currency
     */
    public boolean isNodeHealthy(CryptoCurrency currency) {
        NodeStatus status = getNodeStatus(currency);
        return status == NodeStatus.HEALTHY;
    }

    private void checkBitcoinNodeHealth() {
        try {
            boolean synced = bitcoinService.isNodeSynchronized();
            long blockHeight = bitcoinService.getCurrentBlockHeight();
            
            NodeStatus status = synced && blockHeight > 0 ? NodeStatus.HEALTHY : NodeStatus.DEGRADED;
            nodeStatusMap.put(CryptoCurrency.BITCOIN, status);
            
            log.debug("Bitcoin node status: {} block height: {}", status, blockHeight);
            
        } catch (Exception e) {
            log.error("Failed to check Bitcoin node health", e);
            nodeStatusMap.put(CryptoCurrency.BITCOIN, NodeStatus.UNHEALTHY);
        }
    }

    private void checkEthereumNodeHealth() {
        try {
            boolean synced = ethereumService.isNodeSynchronized();
            long blockHeight = ethereumService.getCurrentBlockHeight();
            
            NodeStatus status = synced && blockHeight > 0 ? NodeStatus.HEALTHY : NodeStatus.DEGRADED;
            nodeStatusMap.put(CryptoCurrency.ETHEREUM, status);
            nodeStatusMap.put(CryptoCurrency.USDC, status);
            nodeStatusMap.put(CryptoCurrency.USDT, status);
            
            log.debug("Ethereum node status: {} block height: {}", status, blockHeight);
            
        } catch (Exception e) {
            log.error("Failed to check Ethereum node health", e);
            nodeStatusMap.put(CryptoCurrency.ETHEREUM, NodeStatus.UNHEALTHY);
            nodeStatusMap.put(CryptoCurrency.USDC, NodeStatus.UNHEALTHY);
            nodeStatusMap.put(CryptoCurrency.USDT, NodeStatus.UNHEALTHY);
        }
    }

    private void checkLitecoinNodeHealth() {
        try {
            boolean synced = litecoinService.isNodeSynchronized();
            long blockHeight = litecoinService.getCurrentBlockHeight();
            
            NodeStatus status = synced && blockHeight > 0 ? NodeStatus.HEALTHY : NodeStatus.DEGRADED;
            nodeStatusMap.put(CryptoCurrency.LITECOIN, status);
            
            log.debug("Litecoin node status: {} block height: {}", status, blockHeight);
            
        } catch (Exception e) {
            log.error("Failed to check Litecoin node health", e);
            nodeStatusMap.put(CryptoCurrency.LITECOIN, NodeStatus.UNHEALTHY);
        }
    }

    public enum NodeStatus {
        HEALTHY,
        DEGRADED,
        UNHEALTHY,
        UNKNOWN
    }
}