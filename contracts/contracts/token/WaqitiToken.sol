// SPDX-License-Identifier: MIT
pragma solidity ^0.8.20;

import "@openzeppelin/contracts-upgradeable/token/ERC20/ERC20Upgradeable.sol";
import "@openzeppelin/contracts-upgradeable/token/ERC20/extensions/ERC20BurnableUpgradeable.sol";
import "@openzeppelin/contracts-upgradeable/token/ERC20/extensions/ERC20SnapshotUpgradeable.sol";
import "@openzeppelin/contracts-upgradeable/access/AccessControlUpgradeable.sol";
import "@openzeppelin/contracts-upgradeable/security/PausableUpgradeable.sol";
import "@openzeppelin/contracts-upgradeable/proxy/utils/Initializable.sol";
import "@openzeppelin/contracts-upgradeable/proxy/utils/UUPSUpgradeable.sol";

/**
 * @title WaqitiToken
 * @dev Implementation of the Waqiti platform token with governance features
 */
contract WaqitiToken is 
    Initializable,
    ERC20Upgradeable,
    ERC20BurnableUpgradeable,
    ERC20SnapshotUpgradeable,
    AccessControlUpgradeable,
    PausableUpgradeable,
    UUPSUpgradeable
{
    bytes32 public constant MINTER_ROLE = keccak256("MINTER_ROLE");
    bytes32 public constant SNAPSHOT_ROLE = keccak256("SNAPSHOT_ROLE");
    bytes32 public constant PAUSER_ROLE = keccak256("PAUSER_ROLE");
    bytes32 public constant UPGRADER_ROLE = keccak256("UPGRADER_ROLE");

    uint256 public constant MAX_SUPPLY = 1_000_000_000 * 10**18; // 1 billion tokens
    
    mapping(address => bool) public blacklisted;
    mapping(address => uint256) public stakingBalance;
    mapping(address => uint256) public stakingTimestamp;
    
    uint256 public totalStaked;
    uint256 public rewardRate; // Reward rate per second per token staked
    
    event Blacklisted(address indexed account);
    event Unblacklisted(address indexed account);
    event Staked(address indexed user, uint256 amount);
    event Unstaked(address indexed user, uint256 amount);
    event RewardsClaimed(address indexed user, uint256 amount);
    event RewardRateUpdated(uint256 newRate);

    /// @custom:oz-upgrades-unsafe-allow constructor
    constructor() {
        _disableInitializers();
    }

    function initialize(
        string memory name,
        string memory symbol,
        address admin,
        uint256 initialSupply
    ) public initializer {
        require(initialSupply <= MAX_SUPPLY, "Initial supply exceeds max supply");
        
        __ERC20_init(name, symbol);
        __ERC20Burnable_init();
        __ERC20Snapshot_init();
        __AccessControl_init();
        __Pausable_init();
        __UUPSUpgradeable_init();

        _grantRole(DEFAULT_ADMIN_ROLE, admin);
        _grantRole(MINTER_ROLE, admin);
        _grantRole(SNAPSHOT_ROLE, admin);
        _grantRole(PAUSER_ROLE, admin);
        _grantRole(UPGRADER_ROLE, admin);

        _mint(admin, initialSupply);
        rewardRate = 100; // 100 wei per second per token staked (adjustable)
    }

    /**
     * @dev Mints new tokens
     */
    function mint(address to, uint256 amount) public onlyRole(MINTER_ROLE) {
        require(totalSupply() + amount <= MAX_SUPPLY, "Minting would exceed max supply");
        _mint(to, amount);
    }

    /**
     * @dev Creates a snapshot for governance
     */
    function snapshot() public onlyRole(SNAPSHOT_ROLE) returns (uint256) {
        return _snapshot();
    }

    /**
     * @dev Pauses all token transfers
     */
    function pause() public onlyRole(PAUSER_ROLE) {
        _pause();
    }

    /**
     * @dev Unpauses all token transfers
     */
    function unpause() public onlyRole(PAUSER_ROLE) {
        _unpause();
    }

    /**
     * @dev Blacklists an address from transfers
     */
    function blacklistAddress(address account) public onlyRole(DEFAULT_ADMIN_ROLE) {
        blacklisted[account] = true;
        emit Blacklisted(account);
    }

    /**
     * @dev Removes an address from blacklist
     */
    function unblacklistAddress(address account) public onlyRole(DEFAULT_ADMIN_ROLE) {
        blacklisted[account] = false;
        emit Unblacklisted(account);
    }

    /**
     * @dev Stakes tokens for rewards
     */
    function stake(uint256 amount) public whenNotPaused {
        require(amount > 0, "Cannot stake 0 tokens");
        require(balanceOf(msg.sender) >= amount, "Insufficient balance");
        
        // Claim pending rewards first
        if (stakingBalance[msg.sender] > 0) {
            _claimRewards();
        }
        
        _transfer(msg.sender, address(this), amount);
        stakingBalance[msg.sender] += amount;
        stakingTimestamp[msg.sender] = block.timestamp;
        totalStaked += amount;
        
        emit Staked(msg.sender, amount);
    }

    /**
     * @dev Unstakes tokens
     */
    function unstake(uint256 amount) public {
        require(amount > 0, "Cannot unstake 0 tokens");
        require(stakingBalance[msg.sender] >= amount, "Insufficient staked balance");
        
        // Claim pending rewards first
        _claimRewards();
        
        stakingBalance[msg.sender] -= amount;
        totalStaked -= amount;
        _transfer(address(this), msg.sender, amount);
        
        emit Unstaked(msg.sender, amount);
    }

    /**
     * @dev Claims staking rewards
     */
    function claimRewards() public {
        _claimRewards();
    }

    /**
     * @dev Internal function to claim rewards
     */
    function _claimRewards() private {
        uint256 rewards = calculateRewards(msg.sender);
        if (rewards > 0) {
            stakingTimestamp[msg.sender] = block.timestamp;
            _mint(msg.sender, rewards);
            emit RewardsClaimed(msg.sender, rewards);
        }
    }

    /**
     * @dev Calculates pending rewards for an address
     */
    function calculateRewards(address account) public view returns (uint256) {
        if (stakingBalance[account] == 0) {
            return 0;
        }
        
        uint256 stakingDuration = block.timestamp - stakingTimestamp[account];
        uint256 rewards = (stakingBalance[account] * stakingDuration * rewardRate) / 10**18;
        
        // Ensure rewards don't exceed max supply
        if (totalSupply() + rewards > MAX_SUPPLY) {
            rewards = MAX_SUPPLY - totalSupply();
        }
        
        return rewards;
    }

    /**
     * @dev Updates the reward rate
     */
    function setRewardRate(uint256 newRate) public onlyRole(DEFAULT_ADMIN_ROLE) {
        rewardRate = newRate;
        emit RewardRateUpdated(newRate);
    }

    /**
     * @dev Returns staking information for an account
     */
    function getStakingInfo(address account) public view returns (
        uint256 stakedAmount,
        uint256 pendingRewards,
        uint256 stakingTime
    ) {
        stakedAmount = stakingBalance[account];
        pendingRewards = calculateRewards(account);
        stakingTime = stakingTimestamp[account];
    }

    /**
     * @dev Hook that is called before any transfer of tokens
     */
    function _beforeTokenTransfer(
        address from,
        address to,
        uint256 amount
    ) internal override(ERC20Upgradeable, ERC20SnapshotUpgradeable) whenNotPaused {
        require(!blacklisted[from], "Sender is blacklisted");
        require(!blacklisted[to], "Recipient is blacklisted");
        super._beforeTokenTransfer(from, to, amount);
    }

    /**
     * @dev Authorizes upgrades (only UPGRADER_ROLE)
     */
    function _authorizeUpgrade(address newImplementation)
        internal
        override
        onlyRole(UPGRADER_ROLE)
    {}

    /**
     * @dev Returns the current version of the contract
     */
    function version() public pure returns (string memory) {
        return "1.0.0";
    }

    /**
     * @dev Emergency withdrawal function for stuck tokens
     */
    function emergencyWithdraw(
        address token,
        address to,
        uint256 amount
    ) public onlyRole(DEFAULT_ADMIN_ROLE) {
        if (token == address(0)) {
            payable(to).transfer(amount);
        } else {
            IERC20Upgradeable(token).transfer(to, amount);
        }
    }

    /**
     * @dev Receive function to accept ETH
     */
    receive() external payable {}
}