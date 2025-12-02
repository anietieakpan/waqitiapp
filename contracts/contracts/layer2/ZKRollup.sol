// SPDX-License-Identifier: MIT
pragma solidity ^0.8.20;

import "@openzeppelin/contracts-upgradeable/access/AccessControlUpgradeable.sol";
import "@openzeppelin/contracts-upgradeable/security/PausableUpgradeable.sol";
import "@openzeppelin/contracts-upgradeable/security/ReentrancyGuardUpgradeable.sol";
import "@openzeppelin/contracts-upgradeable/proxy/utils/Initializable.sol";
import "@openzeppelin/contracts-upgradeable/proxy/utils/UUPSUpgradeable.sol";

/**
 * @title ZKRollup
 * @dev Zero-Knowledge rollup implementation for L2 scaling
 */
contract ZKRollup is 
    Initializable,
    AccessControlUpgradeable,
    PausableUpgradeable,
    ReentrancyGuardUpgradeable,
    UUPSUpgradeable
{
    bytes32 public constant PROVER_ROLE = keccak256("PROVER_ROLE");
    bytes32 public constant OPERATOR_ROLE = keccak256("OPERATOR_ROLE");
    bytes32 public constant VERIFIER_ROLE = keccak256("VERIFIER_ROLE");
    bytes32 public constant UPGRADER_ROLE = keccak256("UPGRADER_ROLE");

    struct Proof {
        uint256[2] a;
        uint256[2][2] b;
        uint256[2] c;
        uint256[4] publicInputs;
    }

    struct Block {
        uint256 blockNumber;
        bytes32 stateRoot;
        bytes32 transactionRoot;
        uint256 timestamp;
        address prover;
        Proof zkProof;
        bool verified;
    }

    struct PendingDeposit {
        address user;
        address token;
        uint256 amount;
        uint256 l2Account;
        bool processed;
    }

    struct PendingWithdrawal {
        address user;
        address token;
        uint256 amount;
        bytes32 merkleProof;
        uint256 blockNumber;
        bool processed;
    }

    struct AccountState {
        uint256 nonce;
        mapping(address => uint256) balances;
        bytes32 storageRoot;
    }

    // State variables
    uint256 public currentBlockNumber;
    bytes32 public currentStateRoot;
    mapping(uint256 => Block) public blocks;
    mapping(uint256 => PendingDeposit) public pendingDeposits;
    mapping(uint256 => PendingWithdrawal) public pendingWithdrawals;
    mapping(address => AccountState) private accountStates;
    
    uint256 public depositCounter;
    uint256 public withdrawalCounter;
    uint256 public maxTransactionsPerBlock;
    uint256 public blockProductionTime;
    uint256 public lastBlockTimestamp;
    
    // Verifier contract for ZK proofs
    address public verifierContract;
    
    // Exit roots for withdrawals
    mapping(bytes32 => bool) public exitRoots;
    bytes32 public globalExitRoot;
    
    // Events
    event BlockProduced(
        uint256 indexed blockNumber,
        bytes32 stateRoot,
        bytes32 transactionRoot,
        address prover
    );
    
    event BlockVerified(
        uint256 indexed blockNumber,
        bool valid
    );
    
    event DepositQueued(
        uint256 indexed depositId,
        address indexed user,
        address token,
        uint256 amount,
        uint256 l2Account
    );
    
    event DepositProcessed(
        uint256 indexed depositId,
        uint256 blockNumber
    );
    
    event WithdrawalQueued(
        uint256 indexed withdrawalId,
        address indexed user,
        address token,
        uint256 amount
    );
    
    event WithdrawalProcessed(
        uint256 indexed withdrawalId,
        address indexed user,
        uint256 amount
    );
    
    event ProofSubmitted(
        uint256 indexed blockNumber,
        address indexed prover
    );
    
    event StateTransition(
        bytes32 oldStateRoot,
        bytes32 newStateRoot,
        uint256 blockNumber
    );

    /// @custom:oz-upgrades-unsafe-allow constructor
    constructor() {
        _disableInitializers();
    }

    function initialize(
        address admin,
        address _verifierContract,
        uint256 _maxTransactionsPerBlock,
        uint256 _blockProductionTime
    ) public initializer {
        __AccessControl_init();
        __Pausable_init();
        __ReentrancyGuard_init();
        __UUPSUpgradeable_init();

        _grantRole(DEFAULT_ADMIN_ROLE, admin);
        _grantRole(PROVER_ROLE, admin);
        _grantRole(OPERATOR_ROLE, admin);
        _grantRole(VERIFIER_ROLE, admin);
        _grantRole(UPGRADER_ROLE, admin);

        verifierContract = _verifierContract;
        maxTransactionsPerBlock = _maxTransactionsPerBlock;
        blockProductionTime = _blockProductionTime;
        currentStateRoot = bytes32(0);
        lastBlockTimestamp = block.timestamp;
    }

    /**
     * @dev Deposits funds from L1 to L2
     */
    function deposit(
        address token,
        uint256 amount,
        uint256 l2Account
    ) external payable whenNotPaused nonReentrant {
        if (token == address(0)) {
            require(msg.value == amount, "Incorrect ETH amount");
        } else {
            require(msg.value == 0, "ETH sent with token deposit");
            // Transfer ERC20 tokens
            (bool success, ) = token.call(
                abi.encodeWithSignature(
                    "transferFrom(address,address,uint256)",
                    msg.sender,
                    address(this),
                    amount
                )
            );
            require(success, "Token transfer failed");
        }
        
        uint256 depositId = ++depositCounter;
        
        pendingDeposits[depositId] = PendingDeposit({
            user: msg.sender,
            token: token,
            amount: amount,
            l2Account: l2Account,
            processed: false
        });
        
        emit DepositQueued(depositId, msg.sender, token, amount, l2Account);
    }

    /**
     * @dev Submits a ZK proof for a new block
     */
    function submitBlock(
        bytes32 newStateRoot,
        bytes32 transactionRoot,
        Proof calldata proof,
        uint256[] calldata processedDeposits,
        uint256[] calldata processedWithdrawals
    ) external onlyRole(PROVER_ROLE) whenNotPaused {
        require(
            block.timestamp >= lastBlockTimestamp + blockProductionTime,
            "Block production too fast"
        );
        
        uint256 blockNumber = ++currentBlockNumber;
        
        // Store block data
        blocks[blockNumber] = Block({
            blockNumber: blockNumber,
            stateRoot: newStateRoot,
            transactionRoot: transactionRoot,
            timestamp: block.timestamp,
            prover: msg.sender,
            zkProof: proof,
            verified: false
        });
        
        // Mark deposits as processed
        for (uint256 i = 0; i < processedDeposits.length; i++) {
            pendingDeposits[processedDeposits[i]].processed = true;
            emit DepositProcessed(processedDeposits[i], blockNumber);
        }
        
        // Process withdrawals
        for (uint256 i = 0; i < processedWithdrawals.length; i++) {
            _processWithdrawal(processedWithdrawals[i]);
        }
        
        lastBlockTimestamp = block.timestamp;
        
        emit BlockProduced(blockNumber, newStateRoot, transactionRoot, msg.sender);
        emit ProofSubmitted(blockNumber, msg.sender);
        
        // Auto-verify for testing (in production, would call verifier)
        _verifyBlock(blockNumber);
    }

    /**
     * @dev Verifies a ZK proof for a block
     */
    function verifyBlock(uint256 blockNumber) external onlyRole(VERIFIER_ROLE) {
        _verifyBlock(blockNumber);
    }

    /**
     * @dev Internal function to verify a block
     */
    function _verifyBlock(uint256 blockNumber) private {
        Block storage zkBlock = blocks[blockNumber];
        require(!zkBlock.verified, "Already verified");
        
        // Verify the ZK proof
        bool isValid = _verifyProof(zkBlock.zkProof, zkBlock.stateRoot);
        
        if (isValid) {
            zkBlock.verified = true;
            
            // Update state root
            bytes32 oldRoot = currentStateRoot;
            currentStateRoot = zkBlock.stateRoot;
            
            emit StateTransition(oldRoot, currentStateRoot, blockNumber);
        }
        
        emit BlockVerified(blockNumber, isValid);
    }

    /**
     * @dev Queues a withdrawal from L2 to L1
     */
    function queueWithdrawal(
        address token,
        uint256 amount,
        bytes32 merkleProof
    ) external whenNotPaused nonReentrant {
        uint256 withdrawalId = ++withdrawalCounter;
        
        pendingWithdrawals[withdrawalId] = PendingWithdrawal({
            user: msg.sender,
            token: token,
            amount: amount,
            merkleProof: merkleProof,
            blockNumber: currentBlockNumber,
            processed: false
        });
        
        emit WithdrawalQueued(withdrawalId, msg.sender, token, amount);
    }

    /**
     * @dev Processes a withdrawal
     */
    function _processWithdrawal(uint256 withdrawalId) private {
        PendingWithdrawal storage withdrawal = pendingWithdrawals[withdrawalId];
        require(!withdrawal.processed, "Already processed");
        
        withdrawal.processed = true;
        
        // Transfer funds to user
        if (withdrawal.token == address(0)) {
            payable(withdrawal.user).transfer(withdrawal.amount);
        } else {
            (bool success, ) = withdrawal.token.call(
                abi.encodeWithSignature(
                    "transfer(address,uint256)",
                    withdrawal.user,
                    withdrawal.amount
                )
            );
            require(success, "Token transfer failed");
        }
        
        emit WithdrawalProcessed(withdrawalId, withdrawal.user, withdrawal.amount);
    }

    /**
     * @dev Emergency withdrawal using Merkle proof
     */
    function emergencyWithdraw(
        address token,
        uint256 amount,
        bytes32[] calldata merkleProof,
        bytes32 leaf
    ) external whenPaused nonReentrant {
        // Verify Merkle proof against exit root
        require(_verifyMerkleProof(merkleProof, leaf, globalExitRoot), "Invalid proof");
        require(!exitRoots[leaf], "Already exited");
        
        exitRoots[leaf] = true;
        
        // Transfer funds
        if (token == address(0)) {
            payable(msg.sender).transfer(amount);
        } else {
            (bool success, ) = token.call(
                abi.encodeWithSignature(
                    "transfer(address,uint256)",
                    msg.sender,
                    amount
                )
            );
            require(success, "Token transfer failed");
        }
    }

    /**
     * @dev Updates global exit root for emergency withdrawals
     */
    function updateGlobalExitRoot(bytes32 newRoot) external onlyRole(OPERATOR_ROLE) {
        globalExitRoot = newRoot;
    }

    /**
     * @dev Gets account balance on L2
     */
    function getL2Balance(
        address account,
        address token
    ) external view returns (uint256) {
        return accountStates[account].balances[token];
    }

    /**
     * @dev Gets block information
     */
    function getBlock(uint256 blockNumber) external view returns (
        bytes32 stateRoot,
        bytes32 transactionRoot,
        uint256 timestamp,
        address prover,
        bool verified
    ) {
        Block memory zkBlock = blocks[blockNumber];
        return (
            zkBlock.stateRoot,
            zkBlock.transactionRoot,
            zkBlock.timestamp,
            zkBlock.prover,
            zkBlock.verified
        );
    }

    /**
     * @dev Verifies a ZK proof
     */
    function _verifyProof(
        Proof memory proof,
        bytes32 stateRoot
    ) private view returns (bool) {
        // Simplified verification - in production would call verifier contract
        // that implements the actual ZK-SNARK verification logic
        if (verifierContract == address(0)) {
            // For testing: auto-approve
            return true;
        }
        
        // Call external verifier
        (bool success, bytes memory result) = verifierContract.staticcall(
            abi.encodeWithSignature(
                "verifyProof(uint256[2],uint256[2][2],uint256[2],uint256[4])",
                proof.a,
                proof.b,
                proof.c,
                proof.publicInputs
            )
        );
        
        return success && abi.decode(result, (bool));
    }

    /**
     * @dev Verifies a Merkle proof
     */
    function _verifyMerkleProof(
        bytes32[] memory proof,
        bytes32 leaf,
        bytes32 root
    ) private pure returns (bool) {
        bytes32 computedHash = leaf;
        
        for (uint256 i = 0; i < proof.length; i++) {
            bytes32 proofElement = proof[i];
            
            if (computedHash <= proofElement) {
                computedHash = keccak256(abi.encodePacked(computedHash, proofElement));
            } else {
                computedHash = keccak256(abi.encodePacked(proofElement, computedHash));
            }
        }
        
        return computedHash == root;
    }

    /**
     * @dev Updates block production time
     */
    function updateBlockProductionTime(uint256 newTime) external onlyRole(OPERATOR_ROLE) {
        blockProductionTime = newTime;
    }

    /**
     * @dev Updates max transactions per block
     */
    function updateMaxTransactionsPerBlock(uint256 newMax) external onlyRole(OPERATOR_ROLE) {
        maxTransactionsPerBlock = newMax;
    }

    /**
     * @dev Updates verifier contract
     */
    function updateVerifier(address newVerifier) external onlyRole(DEFAULT_ADMIN_ROLE) {
        verifierContract = newVerifier;
    }

    /**
     * @dev Pauses the rollup
     */
    function pause() external onlyRole(OPERATOR_ROLE) {
        _pause();
    }

    /**
     * @dev Unpauses the rollup
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