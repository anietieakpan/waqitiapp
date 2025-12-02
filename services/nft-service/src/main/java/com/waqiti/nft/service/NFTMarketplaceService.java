package com.waqiti.nft.service;

import com.waqiti.nft.model.entity.*;
import com.waqiti.nft.model.dto.*;
import com.waqiti.nft.repository.*;
import com.waqiti.nft.blockchain.WaqitiNFTMarketplaceContract;
import com.waqiti.nft.ipfs.IPFSService;
import com.waqiti.nft.security.BlockchainKeyManager;
import com.waqiti.common.config.VaultTemplate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.web3j.crypto.Credentials;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.methods.response.TransactionReceipt;
import org.web3j.protocol.http.HttpService;
import org.web3j.tx.gas.DefaultGasProvider;
import org.web3j.protocol.core.methods.response.Log;

import jakarta.annotation.PostConstruct;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.*;

/**
 * NFT Marketplace Service
 * Handles NFT marketplace operations including minting, listing, buying, and auctioning
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class NFTMarketplaceService {

    private final NFTCollectionRepository collectionRepository;
    private final NFTRepository nftRepository;
    private final NFTListingRepository listingRepository;
    private final NFTOfferRepository offerRepository;
    private final NFTAuctionRepository auctionRepository;
    private final NFTTransactionRepository transactionRepository;
    private final NFTMetadataRepository metadataRepository;

    private final IPFSService ipfsService;
    private final VaultTemplate vaultTemplate;
    private final BlockchainKeyManager keyManager;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Value("${nft.marketplace.contract.address:}")
    private String marketplaceContractAddress;

    @Value("${ethereum.rpc.url:}")
    private String ethereumRpcUrl;

    @Value("${nft.default.royalty.percentage:250}") // 2.5%
    private int defaultRoyaltyPercentage;

    @Value("${nft.platform.fee.percentage:250}") // 2.5%
    private int platformFeePercentage;

    private Web3j web3j;
    private Credentials marketplaceCredentials;
    private WaqitiNFTMarketplaceContract marketplaceContract;

    @PostConstruct
    public void initialize() {
        try {
            // Get configuration from Vault
            var nftConfig = vaultTemplate.read("secret/nft-config").getData();
            
            if (marketplaceContractAddress.isEmpty()) {
                marketplaceContractAddress = nftConfig.get("marketplace-contract-address").toString();
            }
            
            if (ethereumRpcUrl.isEmpty()) {
                ethereumRpcUrl = nftConfig.get("ethereum-rpc-url").toString();
            }

            // Initialize Web3j
            this.web3j = Web3j.build(new HttpService(ethereumRpcUrl));
            
            // Securely retrieve marketplace credentials using key manager
            this.marketplaceCredentials = keyManager.getCredentials("marketplace");

            // Initialize marketplace contract
            this.marketplaceContract = WaqitiNFTMarketplaceContract.load(
                marketplaceContractAddress,
                web3j,
                marketplaceCredentials,
                new DefaultGasProvider()
            );

            log.info("NFT Marketplace service initialized with contract: {}", marketplaceContractAddress);

        } catch (Exception e) {
            log.error("Failed to initialize NFT Marketplace service", e);
            throw new RuntimeException("Cannot initialize NFT Marketplace service", e);
        }
    }

    /**
     * Create a new NFT collection
     */
    @Transactional
    public NFTCollectionResponse createCollection(CreateCollectionRequest request) {
        log.info("Creating NFT collection: {}", request.getName());

        try {
            // Upload collection image to IPFS
            String imageHash = ipfsService.uploadFile(request.getImageFile());
            String imageUrl = ipfsService.getGatewayUrl(imageHash);

            // Create collection entity
            NFTCollection collection = NFTCollection.builder()
                .name(request.getName())
                .symbol(request.getSymbol())
                .description(request.getDescription())
                .imageUrl(imageUrl)
                .imageHash(imageHash)
                .creatorAddress(request.getCreatorAddress())
                .contractAddress(request.getContractAddress())
                .totalSupply(BigInteger.valueOf(request.getTotalSupply()))
                .maxSupply(BigInteger.valueOf(request.getMaxSupply()))
                .royaltyPercentage(request.getRoyaltyPercentage() != null ? 
                    request.getRoyaltyPercentage() : defaultRoyaltyPercentage)
                .royaltyRecipient(request.getRoyaltyRecipient() != null ? 
                    request.getRoyaltyRecipient() : request.getCreatorAddress())
                .category(request.getCategory())
                .isVerified(false)
                .isActive(true)
                .build();

            collection = collectionRepository.save(collection);

            // Publish collection created event
            publishCollectionEvent("COLLECTION_CREATED", collection);

            log.info("NFT collection created successfully: {}", collection.getId());
            return mapToCollectionResponse(collection);

        } catch (Exception e) {
            log.error("Failed to create NFT collection", e);
            throw new NFTException("Failed to create NFT collection", e);
        }
    }

    /**
     * Mint a new NFT
     */
    @Transactional
    public NFTResponse mintNFT(MintNFTRequest request) {
        log.info("Minting NFT: {}", request.getName());

        try {
            // Get collection
            NFTCollection collection = collectionRepository.findById(request.getCollectionId())
                .orElseThrow(() -> new NFTException("Collection not found"));

            // Upload NFT image to IPFS
            String imageHash = ipfsService.uploadFile(request.getImageFile());
            String imageUrl = ipfsService.getGatewayUrl(imageHash);

            // Create NFT metadata
            NFTMetadata metadata = NFTMetadata.builder()
                .name(request.getName())
                .description(request.getDescription())
                .imageUrl(imageUrl)
                .imageHash(imageHash)
                .attributes(request.getAttributes())
                .externalUrl(request.getExternalUrl())
                .animationUrl(request.getAnimationUrl())
                .build();

            metadata = metadataRepository.save(metadata);

            // Upload metadata to IPFS
            String metadataHash = ipfsService.uploadJson(mapMetadataToJson(metadata));
            String metadataUrl = ipfsService.getGatewayUrl(metadataHash);
            
            metadata.setMetadataUrl(metadataUrl);
            metadata.setMetadataHash(metadataHash);
            metadata = metadataRepository.save(metadata);

            // Create NFT entity
            NFT nft = NFT.builder()
                .collection(collection)
                .metadata(metadata)
                .tokenId(BigInteger.valueOf(request.getTokenId()))
                .ownerAddress(request.getMinterAddress())
                .creatorAddress(request.getMinterAddress())
                .mintedAt(LocalDateTime.now())
                .isListed(false)
                .isOnAuction(false)
                .build();

            nft = nftRepository.save(nft);

            // Update collection supply
            collection.setTotalSupply(collection.getTotalSupply().add(BigInteger.ONE));
            collectionRepository.save(collection);

            // Create transaction record
            createTransactionRecord(nft, NFTTransactionType.MINT, request.getMinterAddress(), 
                null, BigDecimal.ZERO, "NFT minted");

            // Publish NFT minted event
            publishNFTEvent("NFT_MINTED", nft);

            log.info("NFT minted successfully: {}", nft.getId());
            return mapToNFTResponse(nft);

        } catch (Exception e) {
            log.error("Failed to mint NFT", e);
            throw new NFTException("Failed to mint NFT", e);
        }
    }

    /**
     * List NFT for sale
     */
    @Transactional
    public NFTListingResponse listNFT(CreateListingRequest request) {
        log.info("Listing NFT for sale: {}", request.getNftId());

        try {
            // Get NFT
            NFT nft = nftRepository.findById(request.getNftId())
                .orElseThrow(() -> new NFTException("NFT not found"));

            // Verify ownership
            if (!nft.getOwnerAddress().equalsIgnoreCase(request.getSellerAddress())) {
                throw new NFTException("Only NFT owner can list for sale");
            }

            // Create listing on blockchain
            TransactionReceipt receipt = marketplaceContract.createListing(
                nft.getCollection().getContractAddress(),
                nft.getTokenId(),
                BigInteger.ONE, // ERC721 quantity is always 1
                request.getPaymentTokenAddress() != null ? request.getPaymentTokenAddress() : "0x0",
                convertToWei(request.getPrice()),
                BigInteger.valueOf(request.getDurationHours() * 3600), // Convert to seconds
                BigInteger.valueOf(0) // TokenType.ERC721
            ).send();

            // Get listing ID from blockchain event
            BigInteger listingId = getListingIdFromReceipt(receipt);

            // Create listing entity
            NFTListing listing = NFTListing.builder()
                .nft(nft)
                .seller(request.getSellerAddress())
                .price(request.getPrice())
                .paymentToken(request.getPaymentTokenAddress())
                .listingId(listingId)
                .startTime(LocalDateTime.now())
                .endTime(request.getDurationHours() > 0 ? 
                    LocalDateTime.now().plusHours(request.getDurationHours()) : null)
                .status(NFTListingStatus.ACTIVE)
                .transactionHash(receipt.getTransactionHash())
                .build();

            listing = listingRepository.save(listing);

            // Update NFT listing status
            nft.setIsListed(true);
            nft.setCurrentListing(listing);
            nftRepository.save(nft);

            // Create transaction record
            createTransactionRecord(nft, NFTTransactionType.LIST, request.getSellerAddress(), 
                null, request.getPrice(), "NFT listed for sale");

            // Publish listing created event
            publishListingEvent("LISTING_CREATED", listing);

            log.info("NFT listed successfully: {}", listing.getId());
            return mapToListingResponse(listing);

        } catch (Exception e) {
            log.error("Failed to list NFT", e);
            throw new NFTException("Failed to list NFT", e);
        }
    }

    /**
     * Buy a listed NFT
     */
    @Transactional
    public NFTTransactionResponse buyNFT(BuyNFTRequest request) {
        log.info("Buying NFT: {}", request.getListingId());

        try {
            // Get listing
            NFTListing listing = listingRepository.findById(request.getListingId())
                .orElseThrow(() -> new NFTException("Listing not found"));

            if (listing.getStatus() != NFTListingStatus.ACTIVE) {
                throw new NFTException("Listing is not active");
            }

            // Buy on blockchain
            TransactionReceipt receipt = marketplaceContract.buyListing(
                listing.getListingId()
            ).send();

            // Update listing status
            listing.setStatus(NFTListingStatus.SOLD);
            listing.setBuyer(request.getBuyerAddress());
            listing.setSoldAt(LocalDateTime.now());
            listingRepository.save(listing);

            // Update NFT ownership
            NFT nft = listing.getNft();
            nft.setOwnerAddress(request.getBuyerAddress());
            nft.setIsListed(false);
            nft.setCurrentListing(null);
            nftRepository.save(nft);

            // Create transaction record
            NFTTransaction transaction = createTransactionRecord(nft, NFTTransactionType.SALE, 
                listing.getSeller(), request.getBuyerAddress(), listing.getPrice(), 
                "NFT purchased");

            transaction.setTransactionHash(receipt.getTransactionHash());
            transactionRepository.save(transaction);

            // Update collection stats
            updateCollectionStats(nft.getCollection(), listing.getPrice());

            // Publish sale event
            publishSaleEvent("NFT_SOLD", transaction);

            log.info("NFT purchased successfully: {}", transaction.getId());
            return mapToTransactionResponse(transaction);

        } catch (Exception e) {
            log.error("Failed to buy NFT", e);
            throw new NFTException("Failed to buy NFT", e);
        }
    }

    /**
     * Create an offer for an NFT
     */
    @Transactional
    public NFTOfferResponse createOffer(CreateOfferRequest request) {
        log.info("Creating offer for NFT: {}", request.getNftId());

        try {
            // Get NFT
            NFT nft = nftRepository.findById(request.getNftId())
                .orElseThrow(() -> new NFTException("NFT not found"));

            // Create offer on blockchain
            TransactionReceipt receipt = marketplaceContract.createOffer(
                nft.getCollection().getContractAddress(),
                nft.getTokenId(),
                BigInteger.ONE,
                request.getPaymentTokenAddress() != null ? request.getPaymentTokenAddress() : "0x0",
                convertToWei(request.getOfferPrice()),
                BigInteger.valueOf(request.getDurationHours() * 3600)
            ).send();

            // Get offer ID from blockchain event
            BigInteger offerId = getOfferIdFromReceipt(receipt);

            // Create offer entity
            NFTOffer offer = NFTOffer.builder()
                .nft(nft)
                .buyer(request.getBuyerAddress())
                .offerPrice(request.getOfferPrice())
                .paymentToken(request.getPaymentTokenAddress())
                .offerId(offerId)
                .createdAt(LocalDateTime.now())
                .expiresAt(LocalDateTime.now().plusHours(request.getDurationHours()))
                .status(NFTOfferStatus.ACTIVE)
                .transactionHash(receipt.getTransactionHash())
                .build();

            offer = offerRepository.save(offer);

            // Create transaction record
            createTransactionRecord(nft, NFTTransactionType.OFFER, request.getBuyerAddress(), 
                nft.getOwnerAddress(), request.getOfferPrice(), "Offer created");

            // Publish offer created event
            publishOfferEvent("OFFER_CREATED", offer);

            log.info("NFT offer created successfully: {}", offer.getId());
            return mapToOfferResponse(offer);

        } catch (Exception e) {
            log.error("Failed to create NFT offer", e);
            throw new NFTException("Failed to create NFT offer", e);
        }
    }

    /**
     * Accept an offer
     */
    @Transactional
    public NFTTransactionResponse acceptOffer(AcceptOfferRequest request) {
        log.info("Accepting offer: {}", request.getOfferId());

        try {
            // Get offer
            NFTOffer offer = offerRepository.findById(request.getOfferId())
                .orElseThrow(() -> new NFTException("Offer not found"));

            if (offer.getStatus() != NFTOfferStatus.ACTIVE) {
                throw new NFTException("Offer is not active");
            }

            NFT nft = offer.getNft();

            // Verify ownership
            if (!nft.getOwnerAddress().equalsIgnoreCase(request.getSellerAddress())) {
                throw new NFTException("Only NFT owner can accept offer");
            }

            // Accept offer on blockchain
            TransactionReceipt receipt = marketplaceContract.acceptOffer(
                offer.getOfferId(),
                BigInteger.valueOf(0) // TokenType.ERC721
            ).send();

            // Update offer status
            offer.setStatus(NFTOfferStatus.ACCEPTED);
            offer.setAcceptedAt(LocalDateTime.now());
            offerRepository.save(offer);

            // Update NFT ownership
            nft.setOwnerAddress(offer.getBuyer());
            if (nft.getIsListed() && nft.getCurrentListing() != null) {
                nft.getCurrentListing().setStatus(NFTListingStatus.CANCELLED);
                nft.setIsListed(false);
                nft.setCurrentListing(null);
            }
            nftRepository.save(nft);

            // Create transaction record
            NFTTransaction transaction = createTransactionRecord(nft, NFTTransactionType.SALE, 
                request.getSellerAddress(), offer.getBuyer(), offer.getOfferPrice(), 
                "Offer accepted");

            transaction.setTransactionHash(receipt.getTransactionHash());
            transactionRepository.save(transaction);

            // Update collection stats
            updateCollectionStats(nft.getCollection(), offer.getOfferPrice());

            // Publish sale event
            publishSaleEvent("OFFER_ACCEPTED", transaction);

            log.info("NFT offer accepted successfully: {}", transaction.getId());
            return mapToTransactionResponse(transaction);

        } catch (Exception e) {
            log.error("Failed to accept NFT offer", e);
            throw new NFTException("Failed to accept NFT offer", e);
        }
    }

    /**
     * Create an auction for an NFT
     */
    @Transactional
    public NFTAuctionResponse createAuction(CreateAuctionRequest request) {
        log.info("Creating auction for NFT: {}", request.getNftId());

        try {
            // Get NFT
            NFT nft = nftRepository.findById(request.getNftId())
                .orElseThrow(() -> new NFTException("NFT not found"));

            // Verify ownership
            if (!nft.getOwnerAddress().equalsIgnoreCase(request.getSellerAddress())) {
                throw new NFTException("Only NFT owner can create auction");
            }

            // Create auction on blockchain
            TransactionReceipt receipt = marketplaceContract.createAuction(
                nft.getCollection().getContractAddress(),
                nft.getTokenId(),
                BigInteger.ONE,
                request.getPaymentTokenAddress() != null ? request.getPaymentTokenAddress() : "0x0",
                convertToWei(request.getStartPrice()),
                convertToWei(request.getReservePrice()),
                BigInteger.valueOf(request.getDurationHours() * 3600),
                BigInteger.valueOf(0) // TokenType.ERC721
            ).send();

            // Get auction ID from blockchain event
            BigInteger auctionId = getAuctionIdFromReceipt(receipt);

            // Create auction entity
            NFTAuction auction = NFTAuction.builder()
                .nft(nft)
                .seller(request.getSellerAddress())
                .startPrice(request.getStartPrice())
                .reservePrice(request.getReservePrice())
                .currentBid(BigDecimal.ZERO)
                .currentBidder(null)
                .paymentToken(request.getPaymentTokenAddress())
                .auctionId(auctionId)
                .startTime(LocalDateTime.now())
                .endTime(LocalDateTime.now().plusHours(request.getDurationHours()))
                .status(NFTAuctionStatus.ACTIVE)
                .transactionHash(receipt.getTransactionHash())
                .build();

            auction = auctionRepository.save(auction);

            // Update NFT auction status
            nft.setIsOnAuction(true);
            nft.setCurrentAuction(auction);
            if (nft.getIsListed() && nft.getCurrentListing() != null) {
                nft.getCurrentListing().setStatus(NFTListingStatus.CANCELLED);
                nft.setIsListed(false);
                nft.setCurrentListing(null);
            }
            nftRepository.save(nft);

            // Create transaction record
            createTransactionRecord(nft, NFTTransactionType.AUCTION_START, request.getSellerAddress(), 
                null, request.getStartPrice(), "Auction created");

            // Publish auction created event
            publishAuctionEvent("AUCTION_CREATED", auction);

            log.info("NFT auction created successfully: {}", auction.getId());
            return mapToAuctionResponse(auction);

        } catch (Exception e) {
            log.error("Failed to create NFT auction", e);
            throw new NFTException("Failed to create NFT auction", e);
        }
    }

    /**
     * Place a bid on an auction
     */
    @Transactional
    public NFTBidResponse placeBid(PlaceBidRequest request) {
        log.info("Placing bid on auction: {}", request.getAuctionId());

        try {
            // Get auction
            NFTAuction auction = auctionRepository.findById(request.getAuctionId())
                .orElseThrow(() -> new NFTException("Auction not found"));

            if (auction.getStatus() != NFTAuctionStatus.ACTIVE) {
                throw new NFTException("Auction is not active");
            }

            if (auction.getEndTime().isBefore(LocalDateTime.now())) {
                throw new NFTException("Auction has ended");
            }

            // Place bid on blockchain
            TransactionReceipt receipt = marketplaceContract.placeBid(
                auction.getAuctionId(),
                convertToWei(request.getBidAmount())
            ).send();

            // Update auction with new bid
            auction.setCurrentBid(request.getBidAmount());
            auction.setCurrentBidder(request.getBidderAddress());
            
            // Extend auction if bid placed in last 5 minutes
            if (auction.getEndTime().minusMinutes(5).isBefore(LocalDateTime.now())) {
                auction.setEndTime(LocalDateTime.now().plusMinutes(5));
            }
            
            auctionRepository.save(auction);

            // Create bid response
            NFTBidResponse bidResponse = NFTBidResponse.builder()
                .auctionId(auction.getId())
                .bidder(request.getBidderAddress())
                .bidAmount(request.getBidAmount())
                .timestamp(LocalDateTime.now())
                .transactionHash(receipt.getTransactionHash())
                .build();

            // Create transaction record
            createTransactionRecord(auction.getNft(), NFTTransactionType.BID, request.getBidderAddress(), 
                auction.getSeller(), request.getBidAmount(), "Bid placed");

            // Publish bid placed event
            publishBidEvent("BID_PLACED", auction, bidResponse);

            log.info("Bid placed successfully on auction: {}", auction.getId());
            return bidResponse;

        } catch (Exception e) {
            log.error("Failed to place bid", e);
            throw new NFTException("Failed to place bid", e);
        }
    }

    /**
     * Get NFT collections with pagination
     */
    @Cacheable(value = "nft_collections", key = "#pageable.pageNumber + '_' + #pageable.pageSize")
    public Page<NFTCollectionResponse> getCollections(Pageable pageable) {
        Page<NFTCollection> collections = collectionRepository.findByIsActiveTrueOrderByCreatedAtDesc(pageable);
        return collections.map(this::mapToCollectionResponse);
    }

    /**
     * Get NFTs in a collection
     */
    @Cacheable(value = "collection_nfts", key = "#collectionId + '_' + #pageable.pageNumber")
    public Page<NFTResponse> getNFTsByCollection(Long collectionId, Pageable pageable) {
        Page<NFT> nfts = nftRepository.findByCollectionIdOrderByTokenIdAsc(collectionId, pageable);
        return nfts.map(this::mapToNFTResponse);
    }

    /**
     * Get active listings
     */
    @Cacheable(value = "active_listings", key = "#pageable.pageNumber + '_' + #pageable.pageSize")
    public Page<NFTListingResponse> getActiveListings(Pageable pageable) {
        Page<NFTListing> listings = listingRepository.findByStatusOrderByCreatedAtDesc(NFTListingStatus.ACTIVE, pageable);
        return listings.map(this::mapToListingResponse);
    }

    /**
     * Get marketplace statistics
     */
    @Cacheable(value = "marketplace_stats", key = "'global'")
    public NFTMarketplaceStats getMarketplaceStats() {
        long totalCollections = collectionRepository.countByIsActiveTrue();
        long totalNFTs = nftRepository.count();
        long totalListings = listingRepository.count();
        BigDecimal totalVolume = transactionRepository.getTotalVolume();
        
        return NFTMarketplaceStats.builder()
            .totalCollections(totalCollections)
            .totalNFTs(totalNFTs)
            .totalListings(totalListings)
            .totalVolume(totalVolume)
            .build();
    }

    // Private helper methods

    private NFTTransaction createTransactionRecord(NFT nft, NFTTransactionType type, 
            String fromAddress, String toAddress, BigDecimal amount, String description) {
        NFTTransaction transaction = NFTTransaction.builder()
            .nft(nft)
            .type(type)
            .fromAddress(fromAddress)
            .toAddress(toAddress)
            .amount(amount)
            .description(description)
            .timestamp(LocalDateTime.now())
            .build();

        return transactionRepository.save(transaction);
    }

    private void updateCollectionStats(NFTCollection collection, BigDecimal salePrice) {
        collection.setTotalVolume(collection.getTotalVolume().add(salePrice));
        collection.setTotalSales(collection.getTotalSales() + 1);
        
        if (collection.getFloorPrice() == null || salePrice.compareTo(collection.getFloorPrice()) < 0) {
            collection.setFloorPrice(salePrice);
        }
        
        collectionRepository.save(collection);
    }

    private BigInteger convertToWei(BigDecimal amount) {
        return amount.multiply(BigDecimal.valueOf(1_000_000_000_000_000_000L)).toBigInteger();
    }

    private Map<String, Object> mapMetadataToJson(NFTMetadata metadata) {
        Map<String, Object> json = new HashMap<>();
        json.put("name", metadata.getName());
        json.put("description", metadata.getDescription());
        json.put("image", metadata.getImageUrl());
        
        if (metadata.getExternalUrl() != null) {
            json.put("external_url", metadata.getExternalUrl());
        }
        
        if (metadata.getAnimationUrl() != null) {
            json.put("animation_url", metadata.getAnimationUrl());
        }
        
        if (metadata.getAttributes() != null && !metadata.getAttributes().isEmpty()) {
            json.put("attributes", metadata.getAttributes());
        }
        
        return json;
    }

    // Blockchain event parsing methods
    private BigInteger getListingIdFromReceipt(TransactionReceipt receipt) {
        // Parse ListingCreated event from transaction receipt
        try {
            // In production, this would parse the actual event logs
            // Event signature: ListingCreated(uint256 indexed listingId, ...)
            List<Log> logs = receipt.getLogs();
            for (Log log : logs) {
                if (log.getTopics().size() > 1) {
                    // The first topic after the event signature is the indexed listingId
                    String listingIdHex = log.getTopics().get(1);
                    return new BigInteger(listingIdHex.substring(2), 16);
                }
            }
        } catch (Exception e) {
            log.warn("Failed to parse listing ID from receipt, using fallback", e);
        }
        
        // Fallback: generate deterministic ID based on transaction hash
        return generateDeterministicId(receipt.getTransactionHash());
    }

    private BigInteger getOfferIdFromReceipt(TransactionReceipt receipt) {
        // Parse OfferCreated event from transaction receipt
        try {
            // Similar parsing logic as listing ID
            List<Log> logs = receipt.getLogs();
            for (Log log : logs) {
                if (log.getTopics().size() > 1) {
                    String offerIdHex = log.getTopics().get(1);
                    return new BigInteger(offerIdHex.substring(2), 16);
                }
            }
        } catch (Exception e) {
            log.warn("Failed to parse offer ID from receipt, using fallback", e);
        }
        
        return generateDeterministicId(receipt.getTransactionHash());
    }

    private BigInteger getAuctionIdFromReceipt(TransactionReceipt receipt) {
        // Parse AuctionCreated event from transaction receipt
        try {
            List<Log> logs = receipt.getLogs();
            for (Log log : logs) {
                if (log.getTopics().size() > 1) {
                    String auctionIdHex = log.getTopics().get(1);
                    return new BigInteger(auctionIdHex.substring(2), 16);
                }
            }
        } catch (Exception e) {
            log.warn("Failed to parse auction ID from receipt, using fallback", e);
        }
        
        return generateDeterministicId(receipt.getTransactionHash());
    }

    /**
     * Generate deterministic ID from transaction hash
     */
    private BigInteger generateDeterministicId(String transactionHash) {
        try {
            // Use SHA-256 hash of transaction hash for deterministic ID generation
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(transactionHash.getBytes());
            byte[] hashBytes = digest.digest();
            
            // Take first 8 bytes to create a reasonable-sized ID
            byte[] idBytes = Arrays.copyOf(hashBytes, 8);
            
            // Convert to positive BigInteger
            BigInteger id = new BigInteger(1, idBytes);
            
            return id;
        } catch (Exception e) {
            log.warn("Failed to generate deterministic ID, using timestamp fallback", e);
            return BigInteger.valueOf(System.currentTimeMillis());
        }
    }

    // Event publishing methods
    private void publishCollectionEvent(String eventType, NFTCollection collection) {
        Map<String, Object> event = Map.of(
            "eventType", eventType,
            "collectionId", collection.getId(),
            "contractAddress", collection.getContractAddress(),
            "creatorAddress", collection.getCreatorAddress(),
            "timestamp", LocalDateTime.now()
        );
        
        kafkaTemplate.send("nft-collection-events", event);
    }

    private void publishNFTEvent(String eventType, NFT nft) {
        Map<String, Object> event = Map.of(
            "eventType", eventType,
            "nftId", nft.getId(),
            "tokenId", nft.getTokenId().toString(),
            "ownerAddress", nft.getOwnerAddress(),
            "timestamp", LocalDateTime.now()
        );
        
        kafkaTemplate.send("nft-events", event);
    }

    private void publishListingEvent(String eventType, NFTListing listing) {
        Map<String, Object> event = Map.of(
            "eventType", eventType,
            "listingId", listing.getId(),
            "nftId", listing.getNft().getId(),
            "seller", listing.getSeller(),
            "price", listing.getPrice(),
            "timestamp", LocalDateTime.now()
        );
        
        kafkaTemplate.send("nft-listing-events", event);
    }

    private void publishOfferEvent(String eventType, NFTOffer offer) {
        Map<String, Object> event = Map.of(
            "eventType", eventType,
            "offerId", offer.getId(),
            "nftId", offer.getNft().getId(),
            "buyer", offer.getBuyer(),
            "offerPrice", offer.getOfferPrice(),
            "timestamp", LocalDateTime.now()
        );
        
        kafkaTemplate.send("nft-offer-events", event);
    }

    private void publishSaleEvent(String eventType, NFTTransaction transaction) {
        Map<String, Object> event = Map.of(
            "eventType", eventType,
            "transactionId", transaction.getId(),
            "nftId", transaction.getNft().getId(),
            "fromAddress", transaction.getFromAddress(),
            "toAddress", transaction.getToAddress(),
            "amount", transaction.getAmount(),
            "timestamp", LocalDateTime.now()
        );
        
        kafkaTemplate.send("nft-sale-events", event);
    }

    private void publishAuctionEvent(String eventType, NFTAuction auction) {
        Map<String, Object> event = Map.of(
            "eventType", eventType,
            "auctionId", auction.getId(),
            "nftId", auction.getNft().getId(),
            "seller", auction.getSeller(),
            "startPrice", auction.getStartPrice(),
            "endTime", auction.getEndTime(),
            "timestamp", LocalDateTime.now()
        );
        
        kafkaTemplate.send("nft-auction-events", event);
    }

    private void publishBidEvent(String eventType, NFTAuction auction, NFTBidResponse bid) {
        Map<String, Object> event = Map.of(
            "eventType", eventType,
            "auctionId", auction.getId(),
            "nftId", auction.getNft().getId(),
            "bidder", bid.getBidder(),
            "bidAmount", bid.getBidAmount(),
            "timestamp", LocalDateTime.now()
        );
        
        kafkaTemplate.send("nft-bid-events", event);
    }

    // Mapping methods
    private NFTCollectionResponse mapToCollectionResponse(NFTCollection collection) {
        return NFTCollectionResponse.builder()
            .id(collection.getId())
            .name(collection.getName())
            .symbol(collection.getSymbol())
            .description(collection.getDescription())
            .imageUrl(collection.getImageUrl())
            .creatorAddress(collection.getCreatorAddress())
            .contractAddress(collection.getContractAddress())
            .totalSupply(collection.getTotalSupply())
            .maxSupply(collection.getMaxSupply())
            .floorPrice(collection.getFloorPrice())
            .totalVolume(collection.getTotalVolume())
            .totalSales(collection.getTotalSales())
            .royaltyPercentage(collection.getRoyaltyPercentage())
            .category(collection.getCategory())
            .isVerified(collection.getIsVerified())
            .createdAt(collection.getCreatedAt())
            .build();
    }

    private NFTResponse mapToNFTResponse(NFT nft) {
        return NFTResponse.builder()
            .id(nft.getId())
            .tokenId(nft.getTokenId())
            .collection(mapToCollectionResponse(nft.getCollection()))
            .metadata(mapToMetadataResponse(nft.getMetadata()))
            .ownerAddress(nft.getOwnerAddress())
            .creatorAddress(nft.getCreatorAddress())
            .isListed(nft.getIsListed())
            .isOnAuction(nft.getIsOnAuction())
            .mintedAt(nft.getMintedAt())
            .build();
    }

    private NFTMetadataResponse mapToMetadataResponse(NFTMetadata metadata) {
        return NFTMetadataResponse.builder()
            .name(metadata.getName())
            .description(metadata.getDescription())
            .imageUrl(metadata.getImageUrl())
            .metadataUrl(metadata.getMetadataUrl())
            .attributes(metadata.getAttributes())
            .externalUrl(metadata.getExternalUrl())
            .animationUrl(metadata.getAnimationUrl())
            .build();
    }

    private NFTListingResponse mapToListingResponse(NFTListing listing) {
        return NFTListingResponse.builder()
            .id(listing.getId())
            .nft(mapToNFTResponse(listing.getNft()))
            .seller(listing.getSeller())
            .price(listing.getPrice())
            .paymentToken(listing.getPaymentToken())
            .startTime(listing.getStartTime())
            .endTime(listing.getEndTime())
            .status(listing.getStatus())
            .build();
    }

    private NFTOfferResponse mapToOfferResponse(NFTOffer offer) {
        return NFTOfferResponse.builder()
            .id(offer.getId())
            .nft(mapToNFTResponse(offer.getNft()))
            .buyer(offer.getBuyer())
            .offerPrice(offer.getOfferPrice())
            .paymentToken(offer.getPaymentToken())
            .createdAt(offer.getCreatedAt())
            .expiresAt(offer.getExpiresAt())
            .status(offer.getStatus())
            .build();
    }

    private NFTAuctionResponse mapToAuctionResponse(NFTAuction auction) {
        return NFTAuctionResponse.builder()
            .id(auction.getId())
            .nft(mapToNFTResponse(auction.getNft()))
            .seller(auction.getSeller())
            .startPrice(auction.getStartPrice())
            .reservePrice(auction.getReservePrice())
            .currentBid(auction.getCurrentBid())
            .currentBidder(auction.getCurrentBidder())
            .paymentToken(auction.getPaymentToken())
            .startTime(auction.getStartTime())
            .endTime(auction.getEndTime())
            .status(auction.getStatus())
            .build();
    }

    private NFTTransactionResponse mapToTransactionResponse(NFTTransaction transaction) {
        return NFTTransactionResponse.builder()
            .id(transaction.getId())
            .nft(mapToNFTResponse(transaction.getNft()))
            .type(transaction.getType())
            .fromAddress(transaction.getFromAddress())
            .toAddress(transaction.getToAddress())
            .amount(transaction.getAmount())
            .description(transaction.getDescription())
            .transactionHash(transaction.getTransactionHash())
            .timestamp(transaction.getTimestamp())
            .build();
    }
}