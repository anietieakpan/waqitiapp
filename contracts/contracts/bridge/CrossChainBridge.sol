// SPDX-License-Identifier: MIT
pragma solidity ^0.8.20;

import "@openzeppelin/contracts-upgradeable/access/AccessControlUpgradeable.sol";
import "@openzeppelin/contracts-upgradeable/security/PausableUpgradeable.sol";
import "@openzeppelin/contracts-upgradeable/security/ReentrancyGuardUpgradeable.sol";
import "@openzeppelin/contracts-upgradeable/proxy/utils/Initializable.sol";
import "@openzeppelin/contracts-upgradeable/proxy/utils/UUPSUpgradeable.sol";
import "@openzeppelin/contracts/token/ERC20/IERC20.sol";
import "@openzeppelin/contracts/token/ERC20/utils/SafeERC20.sol";

/**
 * @title CrossChainBridge
 * @dev Enables cross-chain token transfers between supported blockchains
 */
contract CrossChainBridge is 
    Initializable,
    AccessControlUpgradeable,
    PausableUpgradeable,
    ReentrancyGuardUpgradeable,
    UUPSUpgradeable
{
    using SafeERC20 for IERC20;

    bytes32 public constant VALIDATOR_ROLE = keccak256("VALIDATOR_ROLE");
    bytes32 public constant OPERATOR_ROLE = keccak256("OPERATOR_ROLE");
    bytes32 public constant UPGRADER_ROLE = keccak256("UPGRADER_ROLE");

    struct BridgeRequest {
        address user;
        address token;
        uint256 amount;
        uint256 targetChainId;
        uint256 nonce;
        uint256 timestamp;
        bool processed;
    }

    struct ChainConfig {
        bool isSupported;
        uint256 minConfirmations;
        uint256 dailyLimit;
        uint256 dailyVolume;
        uint256 lastResetTime;
    }

    struct TokenConfig {
        bool isSupported;
        address wrappedToken; // Address on this chain
        uint256 minAmount;
        uint256 maxAmount;
        uint256 fee; // In basis points (100 = 1%)
    }

    // State variables
    mapping(uint256 => ChainConfig) public supportedChains;
    mapping(address => TokenConfig) public supportedTokens;
    mapping(bytes32 => BridgeRequest) public bridgeRequests;
    mapping(address => uint256) public userNonces;
    mapping(bytes32 => bool) public processedTransactions;
    mapping(bytes32 => uint256) public validatorSignatures;
    mapping(bytes32 => mapping(address => bool)) public hasValidatorSigned;

    uint256 public requiredValidators;
    uint256 public bridgeFee;
    address public feeRecipient;
    uint256 public currentChainId;

    // Events
    event BridgeInitiated(
        bytes32 indexed requestId,
        address indexed user,
        address indexed token,
        uint256 amount,
        uint256 fromChain,
        uint256 toChain,
        uint256 nonce
    );

    event BridgeCompleted(
        bytes32 indexed requestId,
        address indexed user,
        address indexed token,
        uint256 amount,
        uint256 fromChain,
        uint256 toChain
    );

    event ValidatorSignature(
        bytes32 indexed requestId,
        address indexed validator,
        uint256 signatureCount
    );

    event ChainAdded(uint256 indexed chainId, uint256 minConfirmations, uint256 dailyLimit);
    event ChainRemoved(uint256 indexed chainId);
    event TokenAdded(address indexed token, address wrappedToken, uint256 fee);
    event TokenRemoved(address indexed token);
    event FeeUpdated(uint256 newFee);
    event DailyLimitUpdated(uint256 indexed chainId, uint256 newLimit);

    /// @custom:oz-upgrades-unsafe-allow constructor
    constructor() {
        _disableInitializers();
    }

    function initialize(
        address admin,
        uint256 _requiredValidators,
        uint256 _bridgeFee,
        address _feeRecipient,
        uint256 _currentChainId
    ) public initializer {
        __AccessControl_init();
        __Pausable_init();
        __ReentrancyGuard_init();
        __UUPSUpgradeable_init();

        _grantRole(DEFAULT_ADMIN_ROLE, admin);
        _grantRole(VALIDATOR_ROLE, admin);
        _grantRole(OPERATOR_ROLE, admin);
        _grantRole(UPGRADER_ROLE, admin);

        requiredValidators = _requiredValidators;
        bridgeFee = _bridgeFee;
        feeRecipient = _feeRecipient;
        currentChainId = _currentChainId;
    }

    /**
     * @dev Initiates a bridge transfer to another chain
     */
    function bridge(
        address token,
        uint256 amount,
        uint256 targetChainId
    ) external payable whenNotPaused nonReentrant {
        require(supportedChains[targetChainId].isSupported, "Target chain not supported");
        require(supportedTokens[token].isSupported, "Token not supported");
        require(amount >= supportedTokens[token].minAmount, "Amount below minimum");
        require(amount <= supportedTokens[token].maxAmount, "Amount exceeds maximum");
        require(msg.value >= bridgeFee, "Insufficient bridge fee");

        // Check daily limit
        ChainConfig storage chainConfig = supportedChains[targetChainId];
        if (block.timestamp >= chainConfig.lastResetTime + 1 days) {
            chainConfig.dailyVolume = 0;
            chainConfig.lastResetTime = block.timestamp;
        }
        require(
            chainConfig.dailyVolume + amount <= chainConfig.dailyLimit,
            "Daily limit exceeded"
        );

        // Calculate fees
        uint256 tokenFee = (amount * supportedTokens[token].fee) / 10000;
        uint256 amountAfterFee = amount - tokenFee;

        // Transfer tokens to bridge
        IERC20(token).safeTransferFrom(msg.sender, address(this), amount);

        // Transfer bridge fee to recipient
        if (msg.value > 0) {
            payable(feeRecipient).transfer(msg.value);
        }

        // Transfer token fee if applicable
        if (tokenFee > 0) {
            IERC20(token).safeTransfer(feeRecipient, tokenFee);
        }

        // Create bridge request
        uint256 nonce = userNonces[msg.sender]++;
        bytes32 requestId = keccak256(
            abi.encodePacked(
                msg.sender,
                token,
                amountAfterFee,
                currentChainId,
                targetChainId,
                nonce,
                block.timestamp
            )
        );

        bridgeRequests[requestId] = BridgeRequest({
            user: msg.sender,
            token: token,
            amount: amountAfterFee,
            targetChainId: targetChainId,
            nonce: nonce,
            timestamp: block.timestamp,
            processed: false
        });

        // Update daily volume
        chainConfig.dailyVolume += amount;

        emit BridgeInitiated(
            requestId,
            msg.sender,
            token,
            amountAfterFee,
            currentChainId,
            targetChainId,
            nonce
        );
    }

    /**
     * @dev Completes a bridge transfer from another chain
     */
    function completeBridge(
        bytes32 requestId,
        address user,
        address token,
        uint256 amount,
        uint256 fromChainId,
        uint256 nonce
    ) external onlyRole(VALIDATOR_ROLE) whenNotPaused nonReentrant {
        bytes32 txHash = keccak256(
            abi.encodePacked(
                user,
                token,
                amount,
                fromChainId,
                currentChainId,
                nonce
            )
        );

        require(!processedTransactions[txHash], "Transaction already processed");
        require(!hasValidatorSigned[txHash][msg.sender], "Validator already signed");

        hasValidatorSigned[txHash][msg.sender] = true;
        validatorSignatures[txHash]++;

        emit ValidatorSignature(txHash, msg.sender, validatorSignatures[txHash]);

        if (validatorSignatures[txHash] >= requiredValidators) {
            processedTransactions[txHash] = true;

            // Get the wrapped token address for this chain
            address tokenToTransfer = supportedTokens[token].wrappedToken;
            if (tokenToTransfer == address(0)) {
                tokenToTransfer = token;
            }

            // Transfer tokens to user
            IERC20(tokenToTransfer).safeTransfer(user, amount);

            emit BridgeCompleted(
                requestId,
                user,
                token,
                amount,
                fromChainId,
                currentChainId
            );
        }
    }

    /**
     * @dev Adds a supported chain
     */
    function addSupportedChain(
        uint256 chainId,
        uint256 minConfirmations,
        uint256 dailyLimit
    ) external onlyRole(DEFAULT_ADMIN_ROLE) {
        supportedChains[chainId] = ChainConfig({
            isSupported: true,
            minConfirmations: minConfirmations,
            dailyLimit: dailyLimit,
            dailyVolume: 0,
            lastResetTime: block.timestamp
        });

        emit ChainAdded(chainId, minConfirmations, dailyLimit);
    }

    /**
     * @dev Removes a supported chain
     */
    function removeSupportedChain(uint256 chainId) external onlyRole(DEFAULT_ADMIN_ROLE) {
        supportedChains[chainId].isSupported = false;
        emit ChainRemoved(chainId);
    }

    /**
     * @dev Adds a supported token
     */
    function addSupportedToken(
        address token,
        address wrappedToken,
        uint256 minAmount,
        uint256 maxAmount,
        uint256 fee
    ) external onlyRole(DEFAULT_ADMIN_ROLE) {
        supportedTokens[token] = TokenConfig({
            isSupported: true,
            wrappedToken: wrappedToken,
            minAmount: minAmount,
            maxAmount: maxAmount,
            fee: fee
        });

        emit TokenAdded(token, wrappedToken, fee);
    }

    /**
     * @dev Removes a supported token
     */
    function removeSupportedToken(address token) external onlyRole(DEFAULT_ADMIN_ROLE) {
        supportedTokens[token].isSupported = false;
        emit TokenRemoved(token);
    }

    /**
     * @dev Updates the bridge fee
     */
    function updateBridgeFee(uint256 newFee) external onlyRole(DEFAULT_ADMIN_ROLE) {
        bridgeFee = newFee;
        emit FeeUpdated(newFee);
    }

    /**
     * @dev Updates the daily limit for a chain
     */
    function updateDailyLimit(
        uint256 chainId,
        uint256 newLimit
    ) external onlyRole(DEFAULT_ADMIN_ROLE) {
        require(supportedChains[chainId].isSupported, "Chain not supported");
        supportedChains[chainId].dailyLimit = newLimit;
        emit DailyLimitUpdated(chainId, newLimit);
    }

    /**
     * @dev Updates the required number of validators
     */
    function updateRequiredValidators(uint256 newRequired) external onlyRole(DEFAULT_ADMIN_ROLE) {
        requiredValidators = newRequired;
    }

    /**
     * @dev Pauses the bridge
     */
    function pause() external onlyRole(OPERATOR_ROLE) {
        _pause();
    }

    /**
     * @dev Unpauses the bridge
     */
    function unpause() external onlyRole(OPERATOR_ROLE) {
        _unpause();
    }

    /**
     * @dev Emergency withdrawal of stuck tokens
     */
    function emergencyWithdraw(
        address token,
        address to,
        uint256 amount
    ) external onlyRole(DEFAULT_ADMIN_ROLE) {
        if (token == address(0)) {
            payable(to).transfer(amount);
        } else {
            IERC20(token).safeTransfer(to, amount);
        }
    }

    /**
     * @dev Returns the current daily volume for a chain
     */
    function getDailyVolume(uint256 chainId) external view returns (uint256) {
        ChainConfig memory config = supportedChains[chainId];
        if (block.timestamp >= config.lastResetTime + 1 days) {
            return 0;
        }
        return config.dailyVolume;
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