// SPDX-License-Identifier: MIT
pragma solidity ^0.8.20;

import "@openzeppelin/contracts-upgradeable/access/AccessControlUpgradeable.sol";
import "@openzeppelin/contracts-upgradeable/security/PausableUpgradeable.sol";
import "@openzeppelin/contracts-upgradeable/security/ReentrancyGuardUpgradeable.sol";
import "@openzeppelin/contracts-upgradeable/proxy/utils/Initializable.sol";
import "@openzeppelin/contracts-upgradeable/proxy/utils/UUPSUpgradeable.sol";
import "@openzeppelin/contracts/token/ERC721/IERC721.sol";
import "@openzeppelin/contracts/token/ERC1155/IERC1155.sol";
import "@openzeppelin/contracts/token/ERC20/IERC20.sol";
import "@openzeppelin/contracts/token/ERC721/IERC721Receiver.sol";
import "@openzeppelin/contracts/token/ERC1155/IERC1155Receiver.sol";
import "@openzeppelin/contracts/utils/introspection/ERC165.sol";
import "@openzeppelin/contracts/token/ERC20/utils/SafeERC20.sol";

/**
 * @title WaqitiNFTMarketplace
 * @dev NFT marketplace with support for ERC721 and ERC1155 tokens
 */
contract WaqitiNFTMarketplace is 
    Initializable,
    AccessControlUpgradeable,
    PausableUpgradeable,
    ReentrancyGuardUpgradeable,
    UUPSUpgradeable,
    IERC721Receiver,
    IERC1155Receiver,
    ERC165
{
    using SafeERC20 for IERC20;

    bytes32 public constant OPERATOR_ROLE = keccak256("OPERATOR_ROLE");
    bytes32 public constant UPGRADER_ROLE = keccak256("UPGRADER_ROLE");

    enum ListingStatus { ACTIVE, SOLD, CANCELLED }
    enum TokenType { ERC721, ERC1155 }

    struct Listing {
        address seller;
        address nftContract;
        uint256 tokenId;
        uint256 quantity; // For ERC1155
        address paymentToken; // address(0) for ETH
        uint256 price;
        uint256 startTime;
        uint256 endTime;
        ListingStatus status;
        TokenType tokenType;
    }

    struct Offer {
        address buyer;
        address nftContract;
        uint256 tokenId;
        uint256 quantity;
        address paymentToken;
        uint256 offerPrice;
        uint256 expiryTime;
        bool isActive;
    }

    struct Auction {
        address seller;
        address nftContract;
        uint256 tokenId;
        uint256 quantity;
        address paymentToken;
        uint256 startPrice;
        uint256 reservePrice;
        uint256 currentBid;
        address currentBidder;
        uint256 startTime;
        uint256 endTime;
        bool settled;
        TokenType tokenType;
    }

    struct RoyaltyInfo {
        address recipient;
        uint256 percentage; // Basis points (10000 = 100%)
    }

    // State variables
    uint256 public platformFee; // Basis points
    address public feeRecipient;
    uint256 public listingCounter;
    uint256 public offerCounter;
    uint256 public auctionCounter;

    mapping(uint256 => Listing) public listings;
    mapping(uint256 => Offer) public offers;
    mapping(uint256 => Auction) public auctions;
    
    // NFT => tokenId => RoyaltyInfo
    mapping(address => mapping(uint256 => RoyaltyInfo)) public royalties;
    
    // User => NFT => tokenId => listingId
    mapping(address => mapping(address => mapping(uint256 => uint256))) public userListings;
    
    // Auction bidding
    mapping(uint256 => mapping(address => uint256)) public auctionBids;
    
    // Whitelisted payment tokens
    mapping(address => bool) public whitelistedPaymentTokens;
    
    // Collection trading stats
    mapping(address => uint256) public collectionVolume;
    mapping(address => uint256) public collectionSales;

    // Events
    event ListingCreated(
        uint256 indexed listingId,
        address indexed seller,
        address indexed nftContract,
        uint256 tokenId,
        uint256 price,
        address paymentToken
    );

    event ListingSold(
        uint256 indexed listingId,
        address indexed buyer,
        address indexed seller,
        uint256 price
    );

    event ListingCancelled(uint256 indexed listingId);

    event OfferCreated(
        uint256 indexed offerId,
        address indexed buyer,
        address indexed nftContract,
        uint256 tokenId,
        uint256 offerPrice
    );

    event OfferAccepted(
        uint256 indexed offerId,
        address indexed seller,
        address indexed buyer
    );

    event AuctionCreated(
        uint256 indexed auctionId,
        address indexed seller,
        address indexed nftContract,
        uint256 tokenId,
        uint256 startPrice
    );

    event BidPlaced(
        uint256 indexed auctionId,
        address indexed bidder,
        uint256 bidAmount
    );

    event AuctionSettled(
        uint256 indexed auctionId,
        address indexed winner,
        uint256 finalPrice
    );

    event RoyaltySet(
        address indexed nftContract,
        uint256 indexed tokenId,
        address recipient,
        uint256 percentage
    );

    /// @custom:oz-upgrades-unsafe-allow constructor
    constructor() {
        _disableInitializers();
    }

    function initialize(
        address admin,
        uint256 _platformFee,
        address _feeRecipient
    ) public initializer {
        __AccessControl_init();
        __Pausable_init();
        __ReentrancyGuard_init();
        __UUPSUpgradeable_init();

        _grantRole(DEFAULT_ADMIN_ROLE, admin);
        _grantRole(OPERATOR_ROLE, admin);
        _grantRole(UPGRADER_ROLE, admin);

        platformFee = _platformFee;
        feeRecipient = _feeRecipient;
        
        // Whitelist ETH (represented as address(0))
        whitelistedPaymentTokens[address(0)] = true;
    }

    /**
     * @dev Lists an NFT for sale
     */
    function createListing(
        address nftContract,
        uint256 tokenId,
        uint256 quantity,
        address paymentToken,
        uint256 price,
        uint256 duration,
        TokenType tokenType
    ) external whenNotPaused nonReentrant returns (uint256) {
        require(price > 0, "Price must be greater than 0");
        require(whitelistedPaymentTokens[paymentToken], "Payment token not whitelisted");
        
        if (tokenType == TokenType.ERC721) {
            require(quantity == 1, "ERC721 quantity must be 1");
            require(IERC721(nftContract).ownerOf(tokenId) == msg.sender, "Not token owner");
            require(
                IERC721(nftContract).getApproved(tokenId) == address(this) ||
                IERC721(nftContract).isApprovedForAll(msg.sender, address(this)),
                "Marketplace not approved"
            );
        } else {
            require(quantity > 0, "Quantity must be greater than 0");
            require(
                IERC1155(nftContract).balanceOf(msg.sender, tokenId) >= quantity,
                "Insufficient balance"
            );
            require(
                IERC1155(nftContract).isApprovedForAll(msg.sender, address(this)),
                "Marketplace not approved"
            );
        }

        uint256 listingId = ++listingCounter;
        uint256 endTime = duration > 0 ? block.timestamp + duration : 0;

        listings[listingId] = Listing({
            seller: msg.sender,
            nftContract: nftContract,
            tokenId: tokenId,
            quantity: quantity,
            paymentToken: paymentToken,
            price: price,
            startTime: block.timestamp,
            endTime: endTime,
            status: ListingStatus.ACTIVE,
            tokenType: tokenType
        });

        userListings[msg.sender][nftContract][tokenId] = listingId;

        emit ListingCreated(
            listingId,
            msg.sender,
            nftContract,
            tokenId,
            price,
            paymentToken
        );

        return listingId;
    }

    /**
     * @dev Buys a listed NFT
     */
    function buyListing(uint256 listingId) external payable whenNotPaused nonReentrant {
        Listing storage listing = listings[listingId];
        
        require(listing.status == ListingStatus.ACTIVE, "Listing not active");
        require(listing.seller != msg.sender, "Cannot buy own listing");
        require(
            listing.endTime == 0 || block.timestamp <= listing.endTime,
            "Listing expired"
        );

        uint256 price = listing.price;
        address seller = listing.seller;

        // Handle payment
        _handlePayment(
            msg.sender,
            seller,
            price,
            listing.paymentToken,
            listing.nftContract,
            listing.tokenId
        );

        // Transfer NFT
        _transferNFT(
            listing.nftContract,
            seller,
            msg.sender,
            listing.tokenId,
            listing.quantity,
            listing.tokenType
        );

        // Update listing status
        listing.status = ListingStatus.SOLD;

        // Update collection stats
        collectionVolume[listing.nftContract] += price;
        collectionSales[listing.nftContract]++;

        emit ListingSold(listingId, msg.sender, seller, price);
    }

    /**
     * @dev Cancels a listing
     */
    function cancelListing(uint256 listingId) external nonReentrant {
        Listing storage listing = listings[listingId];
        
        require(listing.seller == msg.sender, "Not the seller");
        require(listing.status == ListingStatus.ACTIVE, "Listing not active");

        listing.status = ListingStatus.CANCELLED;

        emit ListingCancelled(listingId);
    }

    /**
     * @dev Creates an offer for an NFT
     */
    function createOffer(
        address nftContract,
        uint256 tokenId,
        uint256 quantity,
        address paymentToken,
        uint256 offerPrice,
        uint256 duration
    ) external payable whenNotPaused nonReentrant returns (uint256) {
        require(offerPrice > 0, "Offer price must be greater than 0");
        require(whitelistedPaymentTokens[paymentToken], "Payment token not whitelisted");
        
        uint256 offerId = ++offerCounter;
        uint256 expiryTime = block.timestamp + duration;

        // Lock payment
        if (paymentToken == address(0)) {
            require(msg.value == offerPrice, "Incorrect ETH amount");
        } else {
            IERC20(paymentToken).safeTransferFrom(msg.sender, address(this), offerPrice);
        }

        offers[offerId] = Offer({
            buyer: msg.sender,
            nftContract: nftContract,
            tokenId: tokenId,
            quantity: quantity,
            paymentToken: paymentToken,
            offerPrice: offerPrice,
            expiryTime: expiryTime,
            isActive: true
        });

        emit OfferCreated(offerId, msg.sender, nftContract, tokenId, offerPrice);

        return offerId;
    }

    /**
     * @dev Accepts an offer
     */
    function acceptOffer(uint256 offerId, TokenType tokenType) external whenNotPaused nonReentrant {
        Offer storage offer = offers[offerId];
        
        require(offer.isActive, "Offer not active");
        require(block.timestamp <= offer.expiryTime, "Offer expired");

        // Verify ownership
        if (tokenType == TokenType.ERC721) {
            require(IERC721(offer.nftContract).ownerOf(offer.tokenId) == msg.sender, "Not owner");
        } else {
            require(
                IERC1155(offer.nftContract).balanceOf(msg.sender, offer.tokenId) >= offer.quantity,
                "Insufficient balance"
            );
        }

        address buyer = offer.buyer;
        uint256 price = offer.offerPrice;

        // Transfer NFT
        _transferNFT(
            offer.nftContract,
            msg.sender,
            buyer,
            offer.tokenId,
            offer.quantity,
            tokenType
        );

        // Handle payment distribution
        _distributePayment(
            msg.sender,
            price,
            offer.paymentToken,
            offer.nftContract,
            offer.tokenId
        );

        // Update offer status
        offer.isActive = false;

        // Update collection stats
        collectionVolume[offer.nftContract] += price;
        collectionSales[offer.nftContract]++;

        emit OfferAccepted(offerId, msg.sender, buyer);
    }

    /**
     * @dev Creates an auction
     */
    function createAuction(
        address nftContract,
        uint256 tokenId,
        uint256 quantity,
        address paymentToken,
        uint256 startPrice,
        uint256 reservePrice,
        uint256 duration,
        TokenType tokenType
    ) external whenNotPaused nonReentrant returns (uint256) {
        require(startPrice > 0, "Start price must be greater than 0");
        require(reservePrice >= startPrice, "Reserve must be >= start price");
        require(duration > 0, "Duration must be greater than 0");
        require(whitelistedPaymentTokens[paymentToken], "Payment token not whitelisted");

        // Transfer NFT to marketplace
        _transferNFT(
            nftContract,
            msg.sender,
            address(this),
            tokenId,
            quantity,
            tokenType
        );

        uint256 auctionId = ++auctionCounter;

        auctions[auctionId] = Auction({
            seller: msg.sender,
            nftContract: nftContract,
            tokenId: tokenId,
            quantity: quantity,
            paymentToken: paymentToken,
            startPrice: startPrice,
            reservePrice: reservePrice,
            currentBid: 0,
            currentBidder: address(0),
            startTime: block.timestamp,
            endTime: block.timestamp + duration,
            settled: false,
            tokenType: tokenType
        });

        emit AuctionCreated(auctionId, msg.sender, nftContract, tokenId, startPrice);

        return auctionId;
    }

    /**
     * @dev Places a bid on an auction
     */
    function placeBid(uint256 auctionId, uint256 bidAmount) external payable whenNotPaused nonReentrant {
        Auction storage auction = auctions[auctionId];
        
        require(!auction.settled, "Auction already settled");
        require(block.timestamp <= auction.endTime, "Auction ended");
        require(bidAmount >= auction.startPrice, "Bid below start price");
        require(
            bidAmount > auction.currentBid,
            "Bid must be higher than current bid"
        );

        address previousBidder = auction.currentBidder;
        uint256 previousBid = auction.currentBid;

        // Handle payment
        if (auction.paymentToken == address(0)) {
            require(msg.value == bidAmount, "Incorrect ETH amount");
        } else {
            IERC20(auction.paymentToken).safeTransferFrom(msg.sender, address(this), bidAmount);
        }

        // Refund previous bidder
        if (previousBidder != address(0) && previousBid > 0) {
            if (auction.paymentToken == address(0)) {
                payable(previousBidder).transfer(previousBid);
            } else {
                IERC20(auction.paymentToken).safeTransfer(previousBidder, previousBid);
            }
        }

        auction.currentBid = bidAmount;
        auction.currentBidder = msg.sender;
        auctionBids[auctionId][msg.sender] = bidAmount;

        // Extend auction if bid in last 5 minutes
        if (auction.endTime - block.timestamp < 300) {
            auction.endTime = block.timestamp + 300;
        }

        emit BidPlaced(auctionId, msg.sender, bidAmount);
    }

    /**
     * @dev Settles an auction
     */
    function settleAuction(uint256 auctionId) external whenNotPaused nonReentrant {
        Auction storage auction = auctions[auctionId];
        
        require(!auction.settled, "Already settled");
        require(block.timestamp > auction.endTime, "Auction not ended");

        auction.settled = true;

        if (auction.currentBidder != address(0) && auction.currentBid >= auction.reservePrice) {
            // Transfer NFT to winner
            _transferNFT(
                auction.nftContract,
                address(this),
                auction.currentBidder,
                auction.tokenId,
                auction.quantity,
                auction.tokenType
            );

            // Distribute payment
            _distributePayment(
                auction.seller,
                auction.currentBid,
                auction.paymentToken,
                auction.nftContract,
                auction.tokenId
            );

            // Update collection stats
            collectionVolume[auction.nftContract] += auction.currentBid;
            collectionSales[auction.nftContract]++;

            emit AuctionSettled(auctionId, auction.currentBidder, auction.currentBid);
        } else {
            // Return NFT to seller if reserve not met
            _transferNFT(
                auction.nftContract,
                address(this),
                auction.seller,
                auction.tokenId,
                auction.quantity,
                auction.tokenType
            );

            // Refund bidder if any
            if (auction.currentBidder != address(0) && auction.currentBid > 0) {
                if (auction.paymentToken == address(0)) {
                    payable(auction.currentBidder).transfer(auction.currentBid);
                } else {
                    IERC20(auction.paymentToken).safeTransfer(auction.currentBidder, auction.currentBid);
                }
            }

            emit AuctionSettled(auctionId, address(0), 0);
        }
    }

    /**
     * @dev Sets royalty for an NFT
     */
    function setRoyalty(
        address nftContract,
        uint256 tokenId,
        address recipient,
        uint256 percentage
    ) external {
        require(percentage <= 1000, "Royalty too high"); // Max 10%
        
        // Only NFT owner or contract owner can set royalty
        require(
            IERC721(nftContract).ownerOf(tokenId) == msg.sender ||
            hasRole(DEFAULT_ADMIN_ROLE, msg.sender),
            "Not authorized"
        );

        royalties[nftContract][tokenId] = RoyaltyInfo({
            recipient: recipient,
            percentage: percentage
        });

        emit RoyaltySet(nftContract, tokenId, recipient, percentage);
    }

    /**
     * @dev Updates platform fee
     */
    function setPlatformFee(uint256 newFee) external onlyRole(DEFAULT_ADMIN_ROLE) {
        require(newFee <= 1000, "Fee too high"); // Max 10%
        platformFee = newFee;
    }

    /**
     * @dev Whitelists a payment token
     */
    function whitelistPaymentToken(address token) external onlyRole(OPERATOR_ROLE) {
        whitelistedPaymentTokens[token] = true;
    }

    /**
     * @dev Internal function to handle payments
     */
    function _handlePayment(
        address buyer,
        address seller,
        uint256 price,
        address paymentToken,
        address nftContract,
        uint256 tokenId
    ) private {
        if (paymentToken == address(0)) {
            require(msg.value == price, "Incorrect ETH amount");
        } else {
            IERC20(paymentToken).safeTransferFrom(buyer, address(this), price);
        }

        _distributePayment(seller, price, paymentToken, nftContract, tokenId);
    }

    /**
     * @dev Internal function to distribute payment
     */
    function _distributePayment(
        address seller,
        uint256 price,
        address paymentToken,
        address nftContract,
        uint256 tokenId
    ) private {
        uint256 platformAmount = (price * platformFee) / 10000;
        uint256 remaining = price - platformAmount;

        // Handle royalties
        RoyaltyInfo memory royalty = royalties[nftContract][tokenId];
        uint256 royaltyAmount = 0;
        
        if (royalty.recipient != address(0) && royalty.percentage > 0) {
            royaltyAmount = (price * royalty.percentage) / 10000;
            remaining -= royaltyAmount;
            
            if (paymentToken == address(0)) {
                payable(royalty.recipient).transfer(royaltyAmount);
            } else {
                IERC20(paymentToken).safeTransfer(royalty.recipient, royaltyAmount);
            }
        }

        // Transfer platform fee
        if (platformAmount > 0) {
            if (paymentToken == address(0)) {
                payable(feeRecipient).transfer(platformAmount);
            } else {
                IERC20(paymentToken).safeTransfer(feeRecipient, platformAmount);
            }
        }

        // Transfer to seller
        if (paymentToken == address(0)) {
            payable(seller).transfer(remaining);
        } else {
            IERC20(paymentToken).safeTransfer(seller, remaining);
        }
    }

    /**
     * @dev Internal function to transfer NFT
     */
    function _transferNFT(
        address nftContract,
        address from,
        address to,
        uint256 tokenId,
        uint256 quantity,
        TokenType tokenType
    ) private {
        if (tokenType == TokenType.ERC721) {
            IERC721(nftContract).safeTransferFrom(from, to, tokenId);
        } else {
            IERC1155(nftContract).safeTransferFrom(from, to, tokenId, quantity, "");
        }
    }

    /**
     * @dev Required for receiving ERC721 tokens
     */
    function onERC721Received(
        address,
        address,
        uint256,
        bytes calldata
    ) external pure override returns (bytes4) {
        return IERC721Receiver.onERC721Received.selector;
    }

    /**
     * @dev Required for receiving ERC1155 tokens
     */
    function onERC1155Received(
        address,
        address,
        uint256,
        uint256,
        bytes calldata
    ) external pure override returns (bytes4) {
        return IERC1155Receiver.onERC1155Received.selector;
    }

    /**
     * @dev Required for receiving ERC1155 batch tokens
     */
    function onERC1155BatchReceived(
        address,
        address,
        uint256[] calldata,
        uint256[] calldata,
        bytes calldata
    ) external pure override returns (bytes4) {
        return IERC1155Receiver.onERC1155BatchReceived.selector;
    }

    /**
     * @dev Supports interface check
     */
    function supportsInterface(bytes4 interfaceId)
        public
        view
        override(AccessControlUpgradeable, ERC165)
        returns (bool)
    {
        return
            interfaceId == type(IERC721Receiver).interfaceId ||
            interfaceId == type(IERC1155Receiver).interfaceId ||
            super.supportsInterface(interfaceId);
    }

    /**
     * @dev Pauses the marketplace
     */
    function pause() external onlyRole(OPERATOR_ROLE) {
        _pause();
    }

    /**
     * @dev Unpauses the marketplace
     */
    function unpause() external onlyRole(OPERATOR_ROLE) {
        _unpause();
    }

    /**
     * @dev Authorizes contract upgrades
     */
    function _authorizeUpgrade(address newImplementation)
        internal
        override
        onlyRole(UPGRADER_ROLE)
    {}

    /**
     * @dev Receive function to accept ETH
     */
    receive() external payable {}
}