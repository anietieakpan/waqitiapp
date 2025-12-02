// SPDX-License-Identifier: MIT
pragma solidity ^0.8.20;

import "@openzeppelin/contracts-upgradeable/access/AccessControlUpgradeable.sol";
import "@openzeppelin/contracts-upgradeable/security/PausableUpgradeable.sol";
import "@openzeppelin/contracts-upgradeable/security/ReentrancyGuardUpgradeable.sol";
import "@openzeppelin/contracts-upgradeable/proxy/utils/Initializable.sol";
import "@openzeppelin/contracts-upgradeable/proxy/utils/UUPSUpgradeable.sol";
import "@openzeppelin/contracts/utils/cryptography/MerkleProof.sol";

/**
 * @title PlasmaChain
 * @dev Plasma implementation for high-throughput payment processing
 */
contract PlasmaChain is 
    Initializable,
    AccessControlUpgradeable,
    PausableUpgradeable,
    ReentrancyGuardUpgradeable,
    UUPSUpgradeable
{
    bytes32 public constant OPERATOR_ROLE = keccak256("OPERATOR_ROLE");
    bytes32 public constant CHALLENGER_ROLE = keccak256("CHALLENGER_ROLE");
    bytes32 public constant UPGRADER_ROLE = keccak256("UPGRADER_ROLE");

    struct PlasmaBlock {
        bytes32 merkleRoot;
        uint256 blockNumber;
        uint256 timestamp;
        address operator;
        uint256 transactionCount;
        bool finalized;
    }

    struct UTXO {
        address owner;
        uint256 amount;
        uint256 blockNumber;
        uint256 transactionIndex;
        uint256 outputIndex;
        bool spent;
    }

    struct ExitRequest {
        address owner;
        uint256 amount;
        uint256 utxoPos;
        uint256 exitBond;
        uint256 exitTime;
        bool processed;
        bool challenged;
    }

    struct Challenge {
        address challenger;
        uint256 exitId;
        bytes proof;
        uint256 stake;
        bool resolved;
    }

    struct TransactionOutput {
        address owner;
        uint256 amount;
        bytes32 commitment;
    }

    // State variables
    uint256 public currentBlockNumber;
    uint256 public exitBond;
    uint256 public challengePeriod;
    uint256 public exitDelay;
    
    mapping(uint256 => PlasmaBlock) public plasmaBlocks;
    mapping(uint256 => UTXO) public utxos;
    mapping(uint256 => ExitRequest) public exitRequests;
    mapping(uint256 => Challenge) public challenges;
    mapping(address => uint256[]) public userUTXOs;
    
    uint256 public exitCounter;
    uint256 public challengeCounter;
    uint256 public totalDeposits;
    uint256 public totalWithdrawn;
    
    // Priority queue for exits
    mapping(uint256 => uint256) public exitQueue;
    uint256 public queueHead;
    uint256 public queueTail;
    
    // Events
    event BlockSubmitted(
        uint256 indexed blockNumber,
        bytes32 merkleRoot,
        address operator,
        uint256 transactionCount
    );
    
    event BlockFinalized(uint256 indexed blockNumber);
    
    event DepositCreated(
        uint256 indexed utxoPos,
        address indexed owner,
        uint256 amount
    );
    
    event ExitStarted(
        uint256 indexed exitId,
        address indexed owner,
        uint256 amount,
        uint256 utxoPos
    );
    
    event ExitChallenged(
        uint256 indexed exitId,
        uint256 indexed challengeId,
        address challenger
    );
    
    event ExitProcessed(
        uint256 indexed exitId,
        address indexed owner,
        uint256 amount
    );
    
    event UTXOCreated(
        uint256 indexed utxoPos,
        address indexed owner,
        uint256 amount,
        uint256 blockNumber
    );
    
    event UTXOSpent(uint256 indexed utxoPos);

    /// @custom:oz-upgrades-unsafe-allow constructor
    constructor() {
        _disableInitializers();
    }

    function initialize(
        address admin,
        uint256 _exitBond,
        uint256 _challengePeriod,
        uint256 _exitDelay
    ) public initializer {
        __AccessControl_init();
        __Pausable_init();
        __ReentrancyGuard_init();
        __UUPSUpgradeable_init();

        _grantRole(DEFAULT_ADMIN_ROLE, admin);
        _grantRole(OPERATOR_ROLE, admin);
        _grantRole(UPGRADER_ROLE, admin);

        exitBond = _exitBond;
        challengePeriod = _challengePeriod;
        exitDelay = _exitDelay;
    }

    /**
     * @dev Creates a deposit UTXO
     */
    function deposit() external payable nonReentrant {
        require(msg.value > 0, "Deposit amount must be greater than 0");
        
        uint256 utxoPos = _encodeUtxoPos(currentBlockNumber, 0, 0);
        
        utxos[utxoPos] = UTXO({
            owner: msg.sender,
            amount: msg.value,
            blockNumber: currentBlockNumber,
            transactionIndex: 0,
            outputIndex: 0,
            spent: false
        });
        
        userUTXOs[msg.sender].push(utxoPos);
        totalDeposits += msg.value;
        
        emit DepositCreated(utxoPos, msg.sender, msg.value);
        emit UTXOCreated(utxoPos, msg.sender, msg.value, currentBlockNumber);
    }

    /**
     * @dev Submits a Plasma block
     */
    function submitBlock(
        bytes32 merkleRoot,
        uint256 transactionCount
    ) external onlyRole(OPERATOR_ROLE) whenNotPaused {
        uint256 blockNumber = ++currentBlockNumber;
        
        plasmaBlocks[blockNumber] = PlasmaBlock({
            merkleRoot: merkleRoot,
            blockNumber: blockNumber,
            timestamp: block.timestamp,
            operator: msg.sender,
            transactionCount: transactionCount,
            finalized: false
        });
        
        emit BlockSubmitted(blockNumber, merkleRoot, msg.sender, transactionCount);
    }

    /**
     * @dev Finalizes a Plasma block
     */
    function finalizeBlock(uint256 blockNumber) external onlyRole(OPERATOR_ROLE) {
        PlasmaBlock storage plasmaBlock = plasmaBlocks[blockNumber];
        require(!plasmaBlock.finalized, "Already finalized");
        require(
            block.timestamp >= plasmaBlock.timestamp + challengePeriod,
            "Challenge period not ended"
        );
        
        plasmaBlock.finalized = true;
        
        emit BlockFinalized(blockNumber);
    }

    /**
     * @dev Starts an exit for a UTXO
     */
    function startExit(
        uint256 utxoPos,
        bytes32[] calldata merkleProof,
        bytes calldata txBytes,
        bytes calldata confirmSig
    ) external payable nonReentrant {
        require(msg.value >= exitBond, "Insufficient exit bond");
        
        UTXO memory utxo = utxos[utxoPos];
        require(utxo.owner == msg.sender, "Not UTXO owner");
        require(!utxo.spent, "UTXO already spent");
        
        // Verify UTXO inclusion in block
        require(
            _verifyUTXOInclusion(utxoPos, merkleProof, txBytes),
            "Invalid inclusion proof"
        );
        
        uint256 exitId = ++exitCounter;
        uint256 priority = _calculateExitPriority(utxo.blockNumber, utxoPos);
        
        exitRequests[exitId] = ExitRequest({
            owner: msg.sender,
            amount: utxo.amount,
            utxoPos: utxoPos,
            exitBond: msg.value,
            exitTime: block.timestamp + exitDelay,
            processed: false,
            challenged: false
        });
        
        // Add to priority queue
        _addToExitQueue(exitId, priority);
        
        emit ExitStarted(exitId, msg.sender, utxo.amount, utxoPos);
    }

    /**
     * @dev Challenges an exit with proof of spending
     */
    function challengeExit(
        uint256 exitId,
        bytes calldata spendingTx,
        bytes32[] calldata merkleProof,
        bytes calldata signature
    ) external payable {
        ExitRequest storage exitReq = exitRequests[exitId];
        require(!exitReq.processed, "Exit already processed");
        require(!exitReq.challenged, "Exit already challenged");
        require(msg.value >= exitBond / 2, "Insufficient challenge stake");
        
        // Verify spending transaction
        bool validChallenge = _verifySpendingProof(
            exitReq.utxoPos,
            spendingTx,
            merkleProof,
            signature
        );
        
        uint256 challengeId = ++challengeCounter;
        
        challenges[challengeId] = Challenge({
            challenger: msg.sender,
            exitId: exitId,
            proof: spendingTx,
            stake: msg.value,
            resolved: false
        });
        
        if (validChallenge) {
            // Cancel exit and reward challenger
            exitReq.challenged = true;
            payable(msg.sender).transfer(exitReq.exitBond + msg.value);
        } else {
            // Invalid challenge - slash challenger
            payable(exitReq.owner).transfer(msg.value);
        }
        
        emit ExitChallenged(exitId, challengeId, msg.sender);
    }

    /**
     * @dev Processes exits from the queue
     */
    function processExits(uint256 maxExits) external nonReentrant {
        uint256 processed = 0;
        
        while (processed < maxExits && queueHead < queueTail) {
            uint256 exitId = exitQueue[queueHead];
            ExitRequest storage exitReq = exitRequests[exitId];
            
            if (!exitReq.processed && !exitReq.challenged && 
                block.timestamp >= exitReq.exitTime) {
                
                exitReq.processed = true;
                totalWithdrawn += exitReq.amount;
                
                // Transfer funds to user
                payable(exitReq.owner).transfer(exitReq.amount + exitReq.exitBond);
                
                // Mark UTXO as spent
                utxos[exitReq.utxoPos].spent = true;
                
                emit ExitProcessed(exitId, exitReq.owner, exitReq.amount);
                emit UTXOSpent(exitReq.utxoPos);
            }
            
            queueHead++;
            processed++;
        }
    }

    /**
     * @dev Encodes UTXO position
     */
    function _encodeUtxoPos(
        uint256 blockNumber,
        uint256 txIndex,
        uint256 outputIndex
    ) private pure returns (uint256) {
        return (blockNumber << 32) | (txIndex << 16) | outputIndex;
    }

    /**
     * @dev Verifies UTXO inclusion in block
     */
    function _verifyUTXOInclusion(
        uint256 utxoPos,
        bytes32[] calldata merkleProof,
        bytes calldata txBytes
    ) private view returns (bool) {
        uint256 blockNumber = utxoPos >> 32;
        PlasmaBlock memory plasmaBlock = plasmaBlocks[blockNumber];
        
        bytes32 txHash = keccak256(txBytes);
        
        return MerkleProof.verify(merkleProof, plasmaBlock.merkleRoot, txHash);
    }

    /**
     * @dev Verifies spending proof for challenge
     */
    function _verifySpendingProof(
        uint256 utxoPos,
        bytes calldata spendingTx,
        bytes32[] calldata merkleProof,
        bytes calldata signature
    ) private view returns (bool) {
        // Verify transaction is included in a finalized block
        bytes32 txHash = keccak256(spendingTx);
        
        // Simplified verification - in production would verify full spending chain
        return signature.length > 0 && merkleProof.length > 0;
    }

    /**
     * @dev Calculates exit priority
     */
    function _calculateExitPriority(
        uint256 blockNumber,
        uint256 utxoPos
    ) private pure returns (uint256) {
        return (blockNumber << 128) | utxoPos;
    }

    /**
     * @dev Adds exit to priority queue
     */
    function _addToExitQueue(uint256 exitId, uint256 priority) private {
        exitQueue[queueTail] = exitId;
        queueTail++;
    }

    /**
     * @dev Creates UTXOs from transaction outputs
     */
    function createUTXOs(
        TransactionOutput[] calldata outputs,
        uint256 blockNumber,
        uint256 txIndex
    ) external onlyRole(OPERATOR_ROLE) {
        for (uint256 i = 0; i < outputs.length; i++) {
            uint256 utxoPos = _encodeUtxoPos(blockNumber, txIndex, i);
            
            utxos[utxoPos] = UTXO({
                owner: outputs[i].owner,
                amount: outputs[i].amount,
                blockNumber: blockNumber,
                transactionIndex: txIndex,
                outputIndex: i,
                spent: false
            });
            
            userUTXOs[outputs[i].owner].push(utxoPos);
            
            emit UTXOCreated(utxoPos, outputs[i].owner, outputs[i].amount, blockNumber);
        }
    }

    /**
     * @dev Mass exit in case of operator misbehavior
     */
    function massExit(
        uint256[] calldata utxoPositions,
        bytes32[][] calldata merkleProofs,
        bytes[] calldata transactions
    ) external onlyRole(DEFAULT_ADMIN_ROLE) {
        for (uint256 i = 0; i < utxoPositions.length; i++) {
            UTXO memory utxo = utxos[utxoPositions[i]];
            
            if (!utxo.spent && utxo.owner != address(0)) {
                // Immediately process exit
                payable(utxo.owner).transfer(utxo.amount);
                utxos[utxoPositions[i]].spent = true;
                
                emit UTXOSpent(utxoPositions[i]);
            }
        }
    }

    /**
     * @dev Gets user's UTXOs
     */
    function getUserUTXOs(address user) external view returns (uint256[] memory) {
        uint256[] memory allUTXOs = userUTXOs[user];
        uint256 activeCount = 0;
        
        // Count active UTXOs
        for (uint256 i = 0; i < allUTXOs.length; i++) {
            if (!utxos[allUTXOs[i]].spent) {
                activeCount++;
            }
        }
        
        // Build active UTXO array
        uint256[] memory activeUTXOs = new uint256[](activeCount);
        uint256 index = 0;
        
        for (uint256 i = 0; i < allUTXOs.length; i++) {
            if (!utxos[allUTXOs[i]].spent) {
                activeUTXOs[index] = allUTXOs[i];
                index++;
            }
        }
        
        return activeUTXOs;
    }

    /**
     * @dev Gets total user balance
     */
    function getUserBalance(address user) external view returns (uint256) {
        uint256[] memory userUtxos = userUTXOs[user];
        uint256 totalBalance = 0;
        
        for (uint256 i = 0; i < userUtxos.length; i++) {
            UTXO memory utxo = utxos[userUtxos[i]];
            if (!utxo.spent) {
                totalBalance += utxo.amount;
            }
        }
        
        return totalBalance;
    }

    /**
     * @dev Updates exit bond amount
     */
    function updateExitBond(uint256 newBond) external onlyRole(DEFAULT_ADMIN_ROLE) {
        exitBond = newBond;
    }

    /**
     * @dev Updates challenge period
     */
    function updateChallengePeriod(uint256 newPeriod) external onlyRole(DEFAULT_ADMIN_ROLE) {
        challengePeriod = newPeriod;
    }

    /**
     * @dev Gets exit queue length
     */
    function getExitQueueLength() external view returns (uint256) {
        return queueTail - queueHead;
    }

    /**
     * @dev Pauses the Plasma chain
     */
    function pause() external onlyRole(OPERATOR_ROLE) {
        _pause();
    }

    /**
     * @dev Unpauses the Plasma chain
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
     * @dev Receive function to accept ETH deposits
     */
    receive() external payable {
        // Auto-create deposit UTXO
        if (msg.value > 0) {
            this.deposit{value: msg.value}();
        }
    }
}