package com.waqiti.crypto.rpc;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.crypto.dto.BitcoinTransaction;
import com.waqiti.common.config.VaultTemplate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import jakarta.annotation.PostConstruct;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Bitcoin RPC Client Implementation
 * Handles JSON-RPC communication with Bitcoin Core node
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class BitcoinRpcClientImpl implements BitcoinRpcClient {

    private final VaultTemplate vaultTemplate;
    private final ObjectMapper objectMapper;

    @Value("${bitcoin.rpc.url:http://localhost:8332}")
    private String rpcUrl;

    @Value("${bitcoin.rpc.timeout:30000}")
    private int rpcTimeout;

    private RestTemplate restTemplate;
    private HttpHeaders httpHeaders;

    @PostConstruct
    public void initialize() {
        try {
            // Get RPC credentials from Vault
            var rpcCredentials = vaultTemplate.read("secret/bitcoin-rpc").getData();
            String username = rpcCredentials.get("username").toString();
            String password = rpcCredentials.get("password").toString();

            // Setup HTTP Basic Auth
            String auth = username + ":" + password;
            String encodedAuth = Base64.getEncoder().encodeToString(auth.getBytes(StandardCharsets.UTF_8));

            httpHeaders = new HttpHeaders();
            httpHeaders.setContentType(MediaType.APPLICATION_JSON);
            httpHeaders.set("Authorization", "Basic " + encodedAuth);

            // Configure RestTemplate
            restTemplate = new RestTemplate();

            log.info("Bitcoin RPC client initialized for URL: {}", rpcUrl);
            
            // Test connection
            if (!ping()) {
                log.warn("Bitcoin node connection test failed - node may be offline");
            }

        } catch (Exception e) {
            log.error("Failed to initialize Bitcoin RPC client", e);
            throw new RuntimeException("Cannot initialize Bitcoin RPC client", e);
        }
    }

    @Override
    public BigDecimal getReceivedByAddress(String address, int minConfirmations) {
        log.debug("Getting received amount for address: {} with {} confirmations", address, minConfirmations);
        
        try {
            RpcRequest request = RpcRequest.builder()
                .method("getreceivedbyaddress")
                .params(Arrays.asList(address, minConfirmations))
                .build();

            RpcResponse response = executeRpcCall(request);
            
            if (response.getError() != null) {
                throw new BitcoinRpcException("RPC error: " + response.getError().getMessage());
            }

            return new BigDecimal(response.getResult().toString());

        } catch (Exception e) {
            log.error("Failed to get received amount for address: {}", address, e);
            throw new BitcoinRpcException("Failed to get received amount", e);
        }
    }

    @Override
    public BigDecimal getUnconfirmedBalance(String address) {
        log.debug("Getting unconfirmed balance for address: {}", address);
        
        try {
            // Bitcoin Core doesn't have a direct method for this, so we'll use listunspent with 0 confirmations
            List<UTXO> unconfirmedUtxos = listUnspent(address, 0, 0);
            
            return unconfirmedUtxos.stream()
                .map(UTXO::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        } catch (Exception e) {
            log.error("Failed to get unconfirmed balance for address: {}", address, e);
            return BigDecimal.ZERO;
        }
    }

    @Override
    public String getRawTransaction(String txHash) {
        log.debug("Getting raw transaction: {}", txHash);
        
        try {
            RpcRequest request = RpcRequest.builder()
                .method("getrawtransaction")
                .params(Arrays.asList(txHash))
                .build();

            RpcResponse response = executeRpcCall(request);
            
            if (response.getError() != null) {
                throw new BitcoinRpcException("RPC error: " + response.getError().getMessage());
            }

            return response.getResult().toString();

        } catch (Exception e) {
            log.error("Failed to get raw transaction: {}", txHash, e);
            throw new BitcoinRpcException("Failed to get raw transaction", e);
        }
    }

    @Override
    public BitcoinTransaction decodeRawTransaction(String rawTransaction) {
        log.debug("Decoding raw transaction");
        
        try {
            RpcRequest request = RpcRequest.builder()
                .method("decoderawtransaction")
                .params(Arrays.asList(rawTransaction))
                .build();

            RpcResponse response = executeRpcCall(request);
            
            if (response.getError() != null) {
                throw new BitcoinRpcException("RPC error: " + response.getError().getMessage());
            }

            // Parse the response into BitcoinTransaction
            Map<String, Object> txData = (Map<String, Object>) response.getResult();
            return parseBitcoinTransaction(txData);

        } catch (Exception e) {
            log.error("Failed to decode raw transaction", e);
            throw new BitcoinRpcException("Failed to decode raw transaction", e);
        }
    }

    @Override
    public BitcoinTransaction getTransaction(String txHash) {
        log.debug("Getting transaction: {}", txHash);
        
        try {
            RpcRequest request = RpcRequest.builder()
                .method("gettransaction")
                .params(Arrays.asList(txHash, true)) // Include watch-only addresses
                .build();

            RpcResponse response = executeRpcCall(request);
            
            if (response.getError() != null) {
                throw new BitcoinRpcException("RPC error: " + response.getError().getMessage());
            }

            Map<String, Object> txData = (Map<String, Object>) response.getResult();
            return parseBitcoinTransaction(txData);

        } catch (Exception e) {
            log.error("Failed to get transaction: {}", txHash, e);
            throw new BitcoinRpcException("Failed to get transaction", e);
        }
    }

    @Override
    public long getBlockCount() {
        log.debug("Getting current block count");
        
        try {
            RpcRequest request = RpcRequest.builder()
                .method("getblockcount")
                .params(Collections.emptyList())
                .build();

            RpcResponse response = executeRpcCall(request);
            
            if (response.getError() != null) {
                throw new BitcoinRpcException("RPC error: " + response.getError().getMessage());
            }

            return Long.parseLong(response.getResult().toString());

        } catch (Exception e) {
            log.error("Failed to get block count", e);
            throw new BitcoinRpcException("Failed to get block count", e);
        }
    }

    @Override
    public String getBlockHash(long blockHeight) {
        log.debug("Getting block hash for height: {}", blockHeight);
        
        try {
            RpcRequest request = RpcRequest.builder()
                .method("getblockhash")
                .params(Arrays.asList(blockHeight))
                .build();

            RpcResponse response = executeRpcCall(request);
            
            if (response.getError() != null) {
                throw new BitcoinRpcException("RPC error: " + response.getError().getMessage());
            }

            return response.getResult().toString();

        } catch (Exception e) {
            log.error("Failed to get block hash for height: {}", blockHeight, e);
            throw new BitcoinRpcException("Failed to get block hash", e);
        }
    }

    @Override
    public BitcoinBlock getBlock(String blockHash) {
        log.debug("Getting block: {}", blockHash);
        
        try {
            RpcRequest request = RpcRequest.builder()
                .method("getblock")
                .params(Arrays.asList(blockHash, 2)) // Verbosity level 2 for detailed transaction data
                .build();

            RpcResponse response = executeRpcCall(request);
            
            if (response.getError() != null) {
                throw new BitcoinRpcException("RPC error: " + response.getError().getMessage());
            }

            Map<String, Object> blockData = (Map<String, Object>) response.getResult();
            return parseBitcoinBlock(blockData);

        } catch (Exception e) {
            log.error("Failed to get block: {}", blockHash, e);
            throw new BitcoinRpcException("Failed to get block", e);
        }
    }

    @Override
    public BigDecimal estimateSmartFee(int targetBlocks) {
        log.debug("Estimating smart fee for {} blocks", targetBlocks);
        
        try {
            RpcRequest request = RpcRequest.builder()
                .method("estimatesmartfee")
                .params(Arrays.asList(targetBlocks))
                .build();

            RpcResponse response = executeRpcCall(request);
            
            if (response.getError() != null) {
                throw new BitcoinRpcException("RPC error: " + response.getError().getMessage());
            }

            Map<String, Object> feeData = (Map<String, Object>) response.getResult();
            
            if (feeData.containsKey("feerate")) {
                return new BigDecimal(feeData.get("feerate").toString());
            } else {
                log.warn("Fee estimation failed, using fallback fee");
                return new BigDecimal("0.00001"); // 1 sat/byte fallback
            }

        } catch (Exception e) {
            log.error("Failed to estimate smart fee", e);
            return new BigDecimal("0.00001"); // Fallback fee
        }
    }

    @Override
    public MempoolInfo getMempoolInfo() {
        log.debug("Getting mempool info");
        
        try {
            RpcRequest request = RpcRequest.builder()
                .method("getmempoolinfo")
                .params(Collections.emptyList())
                .build();

            RpcResponse response = executeRpcCall(request);
            
            if (response.getError() != null) {
                throw new BitcoinRpcException("RPC error: " + response.getError().getMessage());
            }

            Map<String, Object> mempoolData = (Map<String, Object>) response.getResult();
            return parseMempoolInfo(mempoolData);

        } catch (Exception e) {
            log.error("Failed to get mempool info", e);
            throw new BitcoinRpcException("Failed to get mempool info", e);
        }
    }

    @Override
    public List<UTXO> listUnspent(String address, int minConfirmations, int maxConfirmations) {
        log.debug("Listing unspent outputs for address: {}", address);
        
        try {
            RpcRequest request = RpcRequest.builder()
                .method("listunspent")
                .params(Arrays.asList(minConfirmations, maxConfirmations, Arrays.asList(address)))
                .build();

            RpcResponse response = executeRpcCall(request);
            
            if (response.getError() != null) {
                throw new BitcoinRpcException("RPC error: " + response.getError().getMessage());
            }

            List<Map<String, Object>> utxoList = (List<Map<String, Object>>) response.getResult();
            return parseUtxoList(utxoList);

        } catch (Exception e) {
            log.error("Failed to list unspent outputs for address: {}", address, e);
            throw new BitcoinRpcException("Failed to list unspent outputs", e);
        }
    }

    @Override
    public String sendRawTransaction(String signedTransactionHex) {
        log.debug("Broadcasting raw transaction");
        
        try {
            RpcRequest request = RpcRequest.builder()
                .method("sendrawtransaction")
                .params(Arrays.asList(signedTransactionHex))
                .build();

            RpcResponse response = executeRpcCall(request);
            
            if (response.getError() != null) {
                throw new BitcoinRpcException("RPC error: " + response.getError().getMessage());
            }

            String txHash = response.getResult().toString();
            log.info("Transaction broadcasted successfully: {}", txHash);
            return txHash;

        } catch (Exception e) {
            log.error("Failed to broadcast raw transaction", e);
            throw new BitcoinRpcException("Failed to broadcast transaction", e);
        }
    }

    @Override
    public BlockchainInfo getBlockchainInfo() {
        log.debug("Getting blockchain info");
        
        try {
            RpcRequest request = RpcRequest.builder()
                .method("getblockchaininfo")
                .params(Collections.emptyList())
                .build();

            RpcResponse response = executeRpcCall(request);
            
            if (response.getError() != null) {
                throw new BitcoinRpcException("RPC error: " + response.getError().getMessage());
            }

            Map<String, Object> blockchainData = (Map<String, Object>) response.getResult();
            return parseBlockchainInfo(blockchainData);

        } catch (Exception e) {
            log.error("Failed to get blockchain info", e);
            throw new BitcoinRpcException("Failed to get blockchain info", e);
        }
    }

    @Override
    public boolean ping() {
        try {
            RpcRequest request = RpcRequest.builder()
                .method("ping")
                .params(Collections.emptyList())
                .build();

            RpcResponse response = executeRpcCall(request);
            return response.getError() == null;

        } catch (Exception e) {
            log.debug("Bitcoin node ping failed: {}", e.getMessage());
            return false;
        }
    }

    // Private helper methods

    private RpcResponse executeRpcCall(RpcRequest request) throws Exception {
        request.setId(System.currentTimeMillis());
        request.setJsonrpc("1.0");

        String requestJson = objectMapper.writeValueAsString(request);
        HttpEntity<String> entity = new HttpEntity<>(requestJson, httpHeaders);

        log.trace("Bitcoin RPC request: {}", requestJson);

        ResponseEntity<String> responseEntity = restTemplate.exchange(
            rpcUrl, HttpMethod.POST, entity, String.class);

        String responseJson = responseEntity.getBody();
        log.trace("Bitcoin RPC response: {}", responseJson);

        return objectMapper.readValue(responseJson, RpcResponse.class);
    }

    private BitcoinTransaction parseBitcoinTransaction(Map<String, Object> txData) {
        return BitcoinTransaction.builder()
            .txId(txData.get("txid").toString())
            .version(Integer.parseInt(txData.get("version").toString()))
            .size(Integer.parseInt(txData.get("size").toString()))
            .lockTime(Long.parseLong(txData.get("locktime").toString()))
            .fee(txData.containsKey("fee") ? new BigDecimal(txData.get("fee").toString()) : BigDecimal.ZERO)
            .confirmations(txData.containsKey("confirmations") ? 
                Integer.parseInt(txData.get("confirmations").toString()) : 0)
            .blockHash(txData.containsKey("blockhash") ? 
                txData.get("blockhash").toString() : null)
            .blockHeight(txData.containsKey("blockheight") ? 
                Long.parseLong(txData.get("blockheight").toString()) : 0L)
            .timestamp(txData.containsKey("time") ? 
                Long.parseLong(txData.get("time").toString()) : 0L)
            .build();
    }

    private BitcoinBlock parseBitcoinBlock(Map<String, Object> blockData) {
        return BitcoinBlock.builder()
            .hash(blockData.get("hash").toString())
            .height(Long.parseLong(blockData.get("height").toString()))
            .version(Integer.parseInt(blockData.get("version").toString()))
            .merkleRoot(blockData.get("merkleroot").toString())
            .timestamp(Long.parseLong(blockData.get("time").toString()))
            .nonce(Long.parseLong(blockData.get("nonce").toString()))
            .difficulty(new BigDecimal(blockData.get("difficulty").toString()))
            .size(Integer.parseInt(blockData.get("size").toString()))
            .transactionCount(((List<?>) blockData.get("tx")).size())
            .build();
    }

    private MempoolInfo parseMempoolInfo(Map<String, Object> mempoolData) {
        return MempoolInfo.builder()
            .size(Integer.parseInt(mempoolData.get("size").toString()))
            .bytes(Long.parseLong(mempoolData.get("bytes").toString()))
            .usage(Long.parseLong(mempoolData.get("usage").toString()))
            .maxMempool(Long.parseLong(mempoolData.get("maxmempool").toString()))
            .mempoolMinFee(new BigDecimal(mempoolData.get("mempoolminfee").toString()))
            .build();
    }

    private BlockchainInfo parseBlockchainInfo(Map<String, Object> blockchainData) {
        return BlockchainInfo.builder()
            .chain(blockchainData.get("chain").toString())
            .blocks(Long.parseLong(blockchainData.get("blocks").toString()))
            .headers(Long.parseLong(blockchainData.get("headers").toString()))
            .bestBlockHash(blockchainData.get("bestblockhash").toString())
            .difficulty(new BigDecimal(blockchainData.get("difficulty").toString()))
            .medianTime(Long.parseLong(blockchainData.get("mediantime").toString()))
            .verificationProgress(Double.parseDouble(blockchainData.get("verificationprogress").toString()))
            .initialBlockDownload(Boolean.parseBoolean(blockchainData.get("initialblockdownload").toString()))
            .chainwork(blockchainData.get("chainwork").toString())
            .build();
    }

    private List<UTXO> parseUtxoList(List<Map<String, Object>> utxoList) {
        return utxoList.stream()
            .map(utxoData -> UTXO.builder()
                .txHash(utxoData.get("txid").toString())
                .outputIndex(Integer.parseInt(utxoData.get("vout").toString()))
                .amount(new BigDecimal(utxoData.get("amount").toString()))
                .confirmations(Integer.parseInt(utxoData.get("confirmations").toString()))
                .scriptPubKey(utxoData.get("scriptPubKey").toString())
                .address(utxoData.containsKey("address") ? 
                    utxoData.get("address").toString() : "")
                .spendable(Boolean.parseBoolean(utxoData.get("spendable").toString()))
                .safe(Boolean.parseBoolean(utxoData.get("safe").toString()))
                .build())
            .toList();
    }

    // Exception class for Bitcoin RPC errors
    public static class BitcoinRpcException extends RuntimeException {
        public BitcoinRpcException(String message) {
            super(message);
        }

        public BitcoinRpcException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}