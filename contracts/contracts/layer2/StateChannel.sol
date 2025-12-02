// SPDX-License-Identifier: MIT
pragma solidity ^0.8.20;

import "@openzeppelin/contracts-upgradeable/access/AccessControlUpgradeable.sol";
import "@openzeppelin/contracts-upgradeable/security/ReentrancyGuardUpgradeable.sol";
import "@openzeppelin/contracts-upgradeable/proxy/utils/Initializable.sol";
import "@openzeppelin/contracts-upgradeable/proxy/utils/UUPSUpgradeable.sol";
import "@openzeppelin/contracts/utils/cryptography/ECDSA.sol";

/**
 * @title StateChannel
 * @dev Payment channel implementation for instant off-chain transactions
 */
contract StateChannel is 
    Initializable,
    AccessControlUpgradeable,
    ReentrancyGuardUpgradeable,
    UUPSUpgradeable
{
    using ECDSA for bytes32;

    bytes32 public constant OPERATOR_ROLE = keccak256("OPERATOR_ROLE");
    bytes32 public constant UPGRADER_ROLE = keccak256("UPGRADER_ROLE");

    struct Channel {
        address participant1;
        address participant2;
        uint256 deposit1;
        uint256 deposit2;
        uint256 nonce;
        uint256 challengePeriod;
        uint256 closingTime;
        bool isOpen;
        bool disputed;
    }

    struct State {
        uint256 balance1;
        uint256 balance2;
        uint256 nonce;
        bytes32 stateHash;
    }

    struct Signature {
        uint8 v;
        bytes32 r;
        bytes32 s;
    }

    // State variables
    mapping(bytes32 => Channel) public channels;
    mapping(bytes32 => State) public latestStates;
    mapping(bytes32 => mapping(uint256 => State)) public disputedStates;
    mapping(address => bytes32[]) public userChannels;
    
    uint256 public defaultChallengePeriod;
    uint256 public channelCounter;
    uint256 public minChannelDeposit;
    uint256 public maxChannelDuration;
    
    // Events
    event ChannelOpened(
        bytes32 indexed channelId,
        address indexed participant1,
        address indexed participant2,
        uint256 deposit1,
        uint256 deposit2
    );
    
    event ChannelUpdated(
        bytes32 indexed channelId,
        uint256 nonce,
        uint256 balance1,
        uint256 balance2
    );
    
    event ChannelClosing(
        bytes32 indexed channelId,
        address closer,
        uint256 closingTime
    );
    
    event ChannelClosed(
        bytes32 indexed channelId,
        uint256 finalBalance1,
        uint256 finalBalance2
    );
    
    event ChannelDisputed(
        bytes32 indexed channelId,
        address disputer,
        uint256 nonce
    );
    
    event ChannelForceProgressed(
        bytes32 indexed channelId,
        uint256 newNonce
    );

    /// @custom:oz-upgrades-unsafe-allow constructor
    constructor() {
        _disableInitializers();
    }

    function initialize(
        address admin,
        uint256 _defaultChallengePeriod,
        uint256 _minChannelDeposit,
        uint256 _maxChannelDuration
    ) public initializer {
        __AccessControl_init();
        __ReentrancyGuard_init();
        __UUPSUpgradeable_init();

        _grantRole(DEFAULT_ADMIN_ROLE, admin);
        _grantRole(OPERATOR_ROLE, admin);
        _grantRole(UPGRADER_ROLE, admin);

        defaultChallengePeriod = _defaultChallengePeriod;
        minChannelDeposit = _minChannelDeposit;
        maxChannelDuration = _maxChannelDuration;
    }

    /**
     * @dev Opens a new payment channel
     */
    function openChannel(
        address participant2,
        uint256 deposit2Required,
        uint256 challengePeriod
    ) external payable nonReentrant returns (bytes32) {
        require(msg.value >= minChannelDeposit, "Deposit too small");
        require(participant2 != msg.sender, "Cannot open channel with yourself");
        require(participant2 != address(0), "Invalid participant");
        
        bytes32 channelId = keccak256(
            abi.encodePacked(
                msg.sender,
                participant2,
                channelCounter++,
                block.timestamp
            )
        );
        
        channels[channelId] = Channel({
            participant1: msg.sender,
            participant2: participant2,
            deposit1: msg.value,
            deposit2: deposit2Required,
            nonce: 0,
            challengePeriod: challengePeriod > 0 ? challengePeriod : defaultChallengePeriod,
            closingTime: 0,
            isOpen: false,
            disputed: false
        });
        
        userChannels[msg.sender].push(channelId);
        
        emit ChannelOpened(
            channelId,
            msg.sender,
            participant2,
            msg.value,
            deposit2Required
        );
        
        return channelId;
    }

    /**
     * @dev Joins an existing channel as participant2
     */
    function joinChannel(bytes32 channelId) external payable nonReentrant {
        Channel storage channel = channels[channelId];
        
        require(!channel.isOpen, "Channel already open");
        require(channel.participant2 == msg.sender, "Not authorized participant");
        require(msg.value >= channel.deposit2, "Insufficient deposit");
        
        channel.deposit2 = msg.value;
        channel.isOpen = true;
        
        // Initialize state
        latestStates[channelId] = State({
            balance1: channel.deposit1,
            balance2: channel.deposit2,
            nonce: 0,
            stateHash: bytes32(0)
        });
        
        userChannels[msg.sender].push(channelId);
    }

    /**
     * @dev Updates channel state with signatures from both participants
     */
    function updateState(
        bytes32 channelId,
        uint256 balance1,
        uint256 balance2,
        uint256 nonce,
        Signature calldata sig1,
        Signature calldata sig2
    ) external {
        Channel storage channel = channels[channelId];
        require(channel.isOpen, "Channel not open");
        require(nonce > latestStates[channelId].nonce, "Invalid nonce");
        require(balance1 + balance2 == channel.deposit1 + channel.deposit2, "Invalid balances");
        
        // Verify signatures
        bytes32 stateHash = keccak256(
            abi.encodePacked(channelId, balance1, balance2, nonce)
        );
        
        address signer1 = _recoverSigner(stateHash, sig1);
        address signer2 = _recoverSigner(stateHash, sig2);
        
        require(
            (signer1 == channel.participant1 && signer2 == channel.participant2) ||
            (signer1 == channel.participant2 && signer2 == channel.participant1),
            "Invalid signatures"
        );
        
        // Update state
        latestStates[channelId] = State({
            balance1: balance1,
            balance2: balance2,
            nonce: nonce,
            stateHash: stateHash
        });
        
        emit ChannelUpdated(channelId, nonce, balance1, balance2);
    }

    /**
     * @dev Initiates channel closure
     */
    function initiateClose(bytes32 channelId) external {
        Channel storage channel = channels[channelId];
        
        require(channel.isOpen, "Channel not open");
        require(
            msg.sender == channel.participant1 || msg.sender == channel.participant2,
            "Not a participant"
        );
        require(channel.closingTime == 0, "Already closing");
        
        channel.closingTime = block.timestamp + channel.challengePeriod;
        
        emit ChannelClosing(channelId, msg.sender, channel.closingTime);
    }

    /**
     * @dev Disputes channel closure with a newer state
     */
    function disputeClose(
        bytes32 channelId,
        uint256 balance1,
        uint256 balance2,
        uint256 nonce,
        Signature calldata sig1,
        Signature calldata sig2
    ) external {
        Channel storage channel = channels[channelId];
        
        require(channel.closingTime > 0, "Channel not closing");
        require(block.timestamp < channel.closingTime, "Challenge period ended");
        require(nonce > latestStates[channelId].nonce, "Invalid nonce");
        
        // Verify signatures
        bytes32 stateHash = keccak256(
            abi.encodePacked(channelId, balance1, balance2, nonce)
        );
        
        address signer1 = _recoverSigner(stateHash, sig1);
        address signer2 = _recoverSigner(stateHash, sig2);
        
        require(
            (signer1 == channel.participant1 && signer2 == channel.participant2) ||
            (signer1 == channel.participant2 && signer2 == channel.participant1),
            "Invalid signatures"
        );
        
        // Update state and extend challenge period
        latestStates[channelId] = State({
            balance1: balance1,
            balance2: balance2,
            nonce: nonce,
            stateHash: stateHash
        });
        
        disputedStates[channelId][nonce] = latestStates[channelId];
        channel.disputed = true;
        channel.closingTime = block.timestamp + channel.challengePeriod;
        
        emit ChannelDisputed(channelId, msg.sender, nonce);
    }

    /**
     * @dev Finalizes channel closure after challenge period
     */
    function finalizeClose(bytes32 channelId) external nonReentrant {
        Channel storage channel = channels[channelId];
        
        require(channel.isOpen, "Channel not open");
        require(channel.closingTime > 0, "Close not initiated");
        require(block.timestamp >= channel.closingTime, "Challenge period not ended");
        
        State memory finalState = latestStates[channelId];
        
        // Transfer balances
        if (finalState.balance1 > 0) {
            payable(channel.participant1).transfer(finalState.balance1);
        }
        if (finalState.balance2 > 0) {
            payable(channel.participant2).transfer(finalState.balance2);
        }
        
        // Close channel
        channel.isOpen = false;
        channel.closingTime = 0;
        
        emit ChannelClosed(channelId, finalState.balance1, finalState.balance2);
    }

    /**
     * @dev Cooperative close with signatures from both parties
     */
    function cooperativeClose(
        bytes32 channelId,
        uint256 balance1,
        uint256 balance2,
        uint256 nonce,
        Signature calldata sig1,
        Signature calldata sig2
    ) external nonReentrant {
        Channel storage channel = channels[channelId];
        
        require(channel.isOpen, "Channel not open");
        require(balance1 + balance2 <= channel.deposit1 + channel.deposit2, "Invalid balances");
        
        // Verify signatures for close agreement
        bytes32 closeHash = keccak256(
            abi.encodePacked(channelId, balance1, balance2, nonce, "CLOSE")
        );
        
        address signer1 = _recoverSigner(closeHash, sig1);
        address signer2 = _recoverSigner(closeHash, sig2);
        
        require(
            (signer1 == channel.participant1 && signer2 == channel.participant2) ||
            (signer1 == channel.participant2 && signer2 == channel.participant1),
            "Invalid signatures"
        );
        
        // Immediate settlement
        if (balance1 > 0) {
            payable(channel.participant1).transfer(balance1);
        }
        if (balance2 > 0) {
            payable(channel.participant2).transfer(balance2);
        }
        
        // Return any excess deposits
        uint256 totalSettled = balance1 + balance2;
        uint256 totalDeposits = channel.deposit1 + channel.deposit2;
        if (totalDeposits > totalSettled) {
            uint256 excess = totalDeposits - totalSettled;
            // Split excess proportionally
            uint256 excess1 = (excess * channel.deposit1) / totalDeposits;
            uint256 excess2 = excess - excess1;
            
            if (excess1 > 0) {
                payable(channel.participant1).transfer(excess1);
            }
            if (excess2 > 0) {
                payable(channel.participant2).transfer(excess2);
            }
        }
        
        // Close channel
        channel.isOpen = false;
        
        emit ChannelClosed(channelId, balance1, balance2);
    }

    /**
     * @dev Force progress for unresponsive participant
     */
    function forceProgress(
        bytes32 channelId,
        bytes calldata action,
        Signature calldata signature
    ) external {
        Channel storage channel = channels[channelId];
        require(channel.isOpen, "Channel not open");
        
        address signer = _recoverSigner(keccak256(action), signature);
        require(
            signer == channel.participant1 || signer == channel.participant2,
            "Invalid signer"
        );
        
        // Process forced action
        State storage state = latestStates[channelId];
        state.nonce++;
        
        emit ChannelForceProgressed(channelId, state.nonce);
    }

    /**
     * @dev Gets channel information
     */
    function getChannel(bytes32 channelId) external view returns (
        address participant1,
        address participant2,
        uint256 deposit1,
        uint256 deposit2,
        bool isOpen,
        uint256 closingTime
    ) {
        Channel memory channel = channels[channelId];
        return (
            channel.participant1,
            channel.participant2,
            channel.deposit1,
            channel.deposit2,
            channel.isOpen,
            channel.closingTime
        );
    }

    /**
     * @dev Gets latest state of a channel
     */
    function getLatestState(bytes32 channelId) external view returns (
        uint256 balance1,
        uint256 balance2,
        uint256 nonce
    ) {
        State memory state = latestStates[channelId];
        return (state.balance1, state.balance2, state.nonce);
    }

    /**
     * @dev Gets user's channels
     */
    function getUserChannels(address user) external view returns (bytes32[] memory) {
        return userChannels[user];
    }

    /**
     * @dev Recovers signer from signature
     */
    function _recoverSigner(
        bytes32 messageHash,
        Signature memory sig
    ) private pure returns (address) {
        bytes32 ethSignedHash = messageHash.toEthSignedMessageHash();
        return ecrecover(ethSignedHash, sig.v, sig.r, sig.s);
    }

    /**
     * @dev Updates default challenge period
     */
    function updateChallengePeriod(uint256 newPeriod) external onlyRole(OPERATOR_ROLE) {
        defaultChallengePeriod = newPeriod;
    }

    /**
     * @dev Updates minimum channel deposit
     */
    function updateMinDeposit(uint256 newMin) external onlyRole(OPERATOR_ROLE) {
        minChannelDeposit = newMin;
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