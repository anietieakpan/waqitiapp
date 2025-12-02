// SPDX-License-Identifier: MIT
pragma solidity ^0.8.20;

import "@openzeppelin/contracts-upgradeable/access/AccessControlUpgradeable.sol";
import "@openzeppelin/contracts-upgradeable/security/PausableUpgradeable.sol";
import "@openzeppelin/contracts-upgradeable/security/ReentrancyGuardUpgradeable.sol";
import "@openzeppelin/contracts-upgradeable/proxy/utils/Initializable.sol";
import "@openzeppelin/contracts-upgradeable/proxy/utils/UUPSUpgradeable.sol";
import "@openzeppelin/contracts/utils/cryptography/MerkleProof.sol";

/**
 * @title OptimisticRollup
 * @dev Optimistic rollup implementation for L2 scaling
 */
contract OptimisticRollup is 
    Initializable,
    AccessControlUpgradeable,
    PausableUpgradeable,
    ReentrancyGuardUpgradeable,
    UUPSUpgradeable
{
    bytes32 public constant SEQUENCER_ROLE = keccak256("SEQUENCER_ROLE");
    bytes32 public constant VALIDATOR_ROLE = keccak256("VALIDATOR_ROLE");
    bytes32 public constant CHALLENGER_ROLE = keccak256("CHALLENGER_ROLE");
    bytes32 public constant UPGRADER_ROLE = keccak256("UPGRADER_ROLE");

    struct StateCommitment {
        bytes32 stateRoot;
        bytes32 transactionRoot;
        uint256 blockNumber;
        uint256 timestamp;
        address sequencer;
        bool finalized;
        uint256 challengePeriodEnd;
    }

    struct Transaction {
        address from;
        address to;
        uint256 value;
        bytes data;
        uint256 nonce;
        uint256 gasLimit;
        uint256 gasPrice;
    }

    struct Challenge {
        address challenger;
        uint256 commitmentId;
        bytes32 disputedStateRoot;
        bytes proof;
        uint256 stake;
        bool resolved;
        bool successful;
    }

    struct WithdrawalRequest {
        address user;
        address token;
        uint256 amount;
        uint256 timestamp;
        bool processed;
        bytes32 merkleProof;
    }

    // State variables
    uint256 public currentCommitmentId;
    uint256 public challengePeriod; // Time window for challenges
    uint256 public minimumStake; // Minimum stake for challenges
    uint256 public sequencerBond; // Bond required from sequencers
    
    mapping(uint256 => StateCommitment) public commitments;
    mapping(uint256 => Challenge) public challenges;
    mapping(bytes32 => bool) public processedTransactions;
    mapping(address => uint256) public sequencerBonds;
    mapping(address => uint256) public pendingWithdrawals;
    mapping(uint256 => WithdrawalRequest) public withdrawalRequests;
    
    uint256 public withdrawalDelay;
    uint256 public withdrawalRequestCounter;
    
    // Fraud proof verification
    mapping(bytes32 => bytes32) public stateTransitions;
    bytes32 public latestStateRoot;
    
    // Events
    event StateCommitted(
        uint256 indexed commitmentId,
        bytes32 stateRoot,
        bytes32 transactionRoot,
        uint256 blockNumber,
        address sequencer
    );
    
    event StateFinalized(
        uint256 indexed commitmentId,
        bytes32 stateRoot
    );
    
    event ChallengeInitiated(
        uint256 indexed challengeId,
        uint256 indexed commitmentId,
        address challenger,
        uint256 stake
    );
    
    event ChallengeResolved(
        uint256 indexed challengeId,
        bool successful,
        address winner
    );
    
    event WithdrawalInitiated(
        uint256 indexed requestId,
        address indexed user,
        address token,
        uint256 amount
    );
    
    event WithdrawalProcessed(
        uint256 indexed requestId,
        address indexed user,
        uint256 amount
    );
    
    event SequencerBondDeposited(address indexed sequencer, uint256 amount);
    event SequencerBondWithdrawn(address indexed sequencer, uint256 amount);

    /// @custom:oz-upgrades-unsafe-allow constructor
    constructor() {
        _disableInitializers();
    }

    function initialize(
        address admin,
        uint256 _challengePeriod,
        uint256 _minimumStake,
        uint256 _sequencerBond,
        uint256 _withdrawalDelay
    ) public initializer {
        __AccessControl_init();
        __Pausable_init();
        __ReentrancyGuard_init();
        __UUPSUpgradeable_init();

        _grantRole(DEFAULT_ADMIN_ROLE, admin);
        _grantRole(SEQUENCER_ROLE, admin);
        _grantRole(VALIDATOR_ROLE, admin);
        _grantRole(UPGRADER_ROLE, admin);

        challengePeriod = _challengePeriod;
        minimumStake = _minimumStake;
        sequencerBond = _sequencerBond;
        withdrawalDelay = _withdrawalDelay;
        latestStateRoot = bytes32(0);
    }

    /**
     * @dev Deposits sequencer bond
     */
    function depositSequencerBond() external payable onlyRole(SEQUENCER_ROLE) {
        require(msg.value >= sequencerBond, "Insufficient bond");
        sequencerBonds[msg.sender] += msg.value;
        emit SequencerBondDeposited(msg.sender, msg.value);
    }

    /**
     * @dev Commits a new state to L1
     */
    function commitState(
        bytes32 stateRoot,
        bytes32 transactionRoot,
        uint256 blockNumber,
        bytes32[] calldata transactionHashes
    ) external onlyRole(SEQUENCER_ROLE) whenNotPaused {
        require(sequencerBonds[msg.sender] >= sequencerBond, "Insufficient bond");
        
        uint256 commitmentId = ++currentCommitmentId;
        
        commitments[commitmentId] = StateCommitment({
            stateRoot: stateRoot,
            transactionRoot: transactionRoot,
            blockNumber: blockNumber,
            timestamp: block.timestamp,
            sequencer: msg.sender,
            finalized: false,
            challengePeriodEnd: block.timestamp + challengePeriod
        });
        
        // Store state transition for fraud proof
        stateTransitions[latestStateRoot] = stateRoot;
        latestStateRoot = stateRoot;
        
        // Mark transactions as processed
        for (uint256 i = 0; i < transactionHashes.length; i++) {
            processedTransactions[transactionHashes[i]] = true;
        }
        
        emit StateCommitted(
            commitmentId,
            stateRoot,
            transactionRoot,
            blockNumber,
            msg.sender
        );
    }

    /**
     * @dev Finalizes a state commitment after challenge period
     */
    function finalizeCommitment(uint256 commitmentId) external {
        StateCommitment storage commitment = commitments[commitmentId];
        
        require(!commitment.finalized, "Already finalized");
        require(block.timestamp > commitment.challengePeriodEnd, "Challenge period not ended");
        require(!_hasActiveChallenge(commitmentId), "Active challenge exists");
        
        commitment.finalized = true;
        
        emit StateFinalized(commitmentId, commitment.stateRoot);
    }

    /**
     * @dev Initiates a fraud challenge
     */
    function initiateChallenge(
        uint256 commitmentId,
        bytes32 disputedStateRoot,
        bytes calldata proof
    ) external payable whenNotPaused {
        StateCommitment memory commitment = commitments[commitmentId];
        
        require(!commitment.finalized, "Already finalized");
        require(block.timestamp <= commitment.challengePeriodEnd, "Challenge period ended");
        require(msg.value >= minimumStake, "Insufficient stake");
        
        uint256 challengeId = uint256(keccak256(abi.encodePacked(commitmentId, msg.sender, block.timestamp)));
        
        challenges[challengeId] = Challenge({
            challenger: msg.sender,
            commitmentId: commitmentId,
            disputedStateRoot: disputedStateRoot,
            proof: proof,
            stake: msg.value,
            resolved: false,
            successful: false
        });
        
        emit ChallengeInitiated(challengeId, commitmentId, msg.sender, msg.value);
    }

    /**
     * @dev Resolves a challenge through fraud proof verification
     */
    function resolveChallenge(
        uint256 challengeId,
        bytes calldata fraudProof
    ) external onlyRole(VALIDATOR_ROLE) {
        Challenge storage challenge = challenges[challengeId];
        require(!challenge.resolved, "Already resolved");
        
        StateCommitment memory commitment = commitments[challenge.commitmentId];
        
        // Verify fraud proof (simplified - in production would use more complex verification)
        bool isValid = _verifyFraudProof(
            commitment.stateRoot,
            challenge.disputedStateRoot,
            fraudProof
        );
        
        challenge.resolved = true;
        challenge.successful = isValid;
        
        if (isValid) {
            // Challenge successful - slash sequencer bond
            uint256 slashAmount = sequencerBonds[commitment.sequencer] / 2;
            sequencerBonds[commitment.sequencer] -= slashAmount;
            
            // Reward challenger
            payable(challenge.challenger).transfer(challenge.stake + slashAmount);
            
            // Mark commitment as invalid
            commitments[challenge.commitmentId].finalized = true; // Prevent finalization
        } else {
            // Challenge failed - slash challenger stake
            payable(commitment.sequencer).transfer(challenge.stake);
        }
        
        emit ChallengeResolved(challengeId, isValid, isValid ? challenge.challenger : commitment.sequencer);
    }

    /**
     * @dev Initiates withdrawal from L2 to L1
     */
    function initiateWithdrawal(
        address token,
        uint256 amount,
        bytes32[] calldata merkleProof
    ) external whenNotPaused nonReentrant {
        uint256 requestId = ++withdrawalRequestCounter;
        
        withdrawalRequests[requestId] = WithdrawalRequest({
            user: msg.sender,
            token: token,
            amount: amount,
            timestamp: block.timestamp,
            processed: false,
            merkleProof: keccak256(abi.encodePacked(merkleProof))
        });
        
        pendingWithdrawals[msg.sender] += amount;
        
        emit WithdrawalInitiated(requestId, msg.sender, token, amount);
    }

    /**
     * @dev Processes a withdrawal after delay period
     */
    function processWithdrawal(uint256 requestId) external nonReentrant {
        WithdrawalRequest storage request = withdrawalRequests[requestId];
        
        require(request.user == msg.sender, "Not withdrawal owner");
        require(!request.processed, "Already processed");
        require(block.timestamp >= request.timestamp + withdrawalDelay, "Delay period not met");
        
        request.processed = true;
        pendingWithdrawals[msg.sender] -= request.amount;
        
        // Transfer tokens (simplified - would integrate with bridge)
        if (request.token == address(0)) {
            payable(msg.sender).transfer(request.amount);
        } else {
            // Transfer ERC20 tokens
            (bool success, ) = request.token.call(
                abi.encodeWithSignature("transfer(address,uint256)", msg.sender, request.amount)
            );
            require(success, "Transfer failed");
        }
        
        emit WithdrawalProcessed(requestId, msg.sender, request.amount);
    }

    /**
     * @dev Batch processes L2 transactions
     */
    function processBatch(
        Transaction[] calldata transactions,
        bytes32 newStateRoot
    ) external onlyRole(SEQUENCER_ROLE) returns (bytes32) {
        bytes32 transactionRoot = _calculateTransactionRoot(transactions);
        
        // Process each transaction
        for (uint256 i = 0; i < transactions.length; i++) {
            _processTransaction(transactions[i]);
        }
        
        return transactionRoot;
    }

    /**
     * @dev Emergency pause
     */
    function emergencyPause() external onlyRole(DEFAULT_ADMIN_ROLE) {
        _pause();
        
        // Allow emergency withdrawals
        withdrawalDelay = 0;
    }

    /**
     * @dev Verifies a Merkle proof for state inclusion
     */
    function verifyStateInclusion(
        bytes32 leaf,
        bytes32[] calldata proof,
        bytes32 root
    ) external pure returns (bool) {
        return MerkleProof.verify(proof, root, leaf);
    }

    /**
     * @dev Updates challenge period
     */
    function updateChallengePeriod(uint256 newPeriod) external onlyRole(DEFAULT_ADMIN_ROLE) {
        challengePeriod = newPeriod;
    }

    /**
     * @dev Updates minimum stake for challenges
     */
    function updateMinimumStake(uint256 newStake) external onlyRole(DEFAULT_ADMIN_ROLE) {
        minimumStake = newStake;
    }

    /**
     * @dev Withdraws sequencer bond
     */
    function withdrawSequencerBond(uint256 amount) external onlyRole(SEQUENCER_ROLE) {
        require(sequencerBonds[msg.sender] >= amount, "Insufficient bond");
        
        sequencerBonds[msg.sender] -= amount;
        payable(msg.sender).transfer(amount);
        
        emit SequencerBondWithdrawn(msg.sender, amount);
    }

    /**
     * @dev Checks if commitment has active challenges
     */
    function _hasActiveChallenge(uint256 commitmentId) private view returns (bool) {
        // In production, would iterate through challenges mapping
        // Simplified for demonstration
        return false;
    }

    /**
     * @dev Verifies fraud proof
     */
    function _verifyFraudProof(
        bytes32 committedRoot,
        bytes32 disputedRoot,
        bytes calldata proof
    ) private pure returns (bool) {
        // Simplified fraud proof verification
        // In production, would implement proper verification logic
        return keccak256(proof) == keccak256(abi.encodePacked(committedRoot, disputedRoot));
    }

    /**
     * @dev Calculates transaction root
     */
    function _calculateTransactionRoot(Transaction[] calldata transactions) private pure returns (bytes32) {
        bytes32[] memory hashes = new bytes32[](transactions.length);
        
        for (uint256 i = 0; i < transactions.length; i++) {
            hashes[i] = keccak256(abi.encode(transactions[i]));
        }
        
        // Calculate Merkle root (simplified)
        return keccak256(abi.encodePacked(hashes));
    }

    /**
     * @dev Processes a single transaction
     */
    function _processTransaction(Transaction calldata transaction) private {
        // Transaction processing logic
        // In production, would execute state transitions
        bytes32 txHash = keccak256(abi.encode(transaction));
        processedTransactions[txHash] = true;
    }

    /**
     * @dev Pauses the rollup
     */
    function pause() external onlyRole(DEFAULT_ADMIN_ROLE) {
        _pause();
    }

    /**
     * @dev Unpauses the rollup
     */
    function unpause() external onlyRole(DEFAULT_ADMIN_ROLE) {
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