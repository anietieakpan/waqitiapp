// SPDX-License-Identifier: MIT
pragma solidity ^0.8.20;

import "@openzeppelin/contracts-upgradeable/token/ERC20/ERC20Upgradeable.sol";
import "@openzeppelin/contracts-upgradeable/token/ERC20/extensions/ERC20BurnableUpgradeable.sol";
import "@openzeppelin/contracts-upgradeable/access/AccessControlUpgradeable.sol";
import "@openzeppelin/contracts-upgradeable/security/PausableUpgradeable.sol";
import "@openzeppelin/contracts-upgradeable/proxy/utils/Initializable.sol";
import "@openzeppelin/contracts-upgradeable/proxy/utils/UUPSUpgradeable.sol";
import "@chainlink/contracts/src/v0.8/interfaces/AggregatorV3Interface.sol";

/**
 * @title WaqitiUSD
 * @dev Overcollateralized stablecoin pegged to USD
 */
contract WaqitiUSD is 
    Initializable,
    ERC20Upgradeable,
    ERC20BurnableUpgradeable,
    AccessControlUpgradeable,
    PausableUpgradeable,
    UUPSUpgradeable
{
    bytes32 public constant MINTER_ROLE = keccak256("MINTER_ROLE");
    bytes32 public constant PAUSER_ROLE = keccak256("PAUSER_ROLE");
    bytes32 public constant UPGRADER_ROLE = keccak256("UPGRADER_ROLE");
    bytes32 public constant STABILITY_ROLE = keccak256("STABILITY_ROLE");

    struct Collateral {
        address asset;
        uint256 amount;
        uint256 collateralRatio; // In basis points (15000 = 150%)
        address priceOracle;
        bool isActive;
    }

    struct Vault {
        mapping(address => uint256) collateral; // Collateral asset => amount
        uint256 debt; // WUSD debt
        uint256 lastInterestUpdate;
    }

    // State variables
    mapping(address => Collateral) public collateralTypes;
    mapping(address => Vault) public vaults;
    mapping(address => bool) public whitelistedCollateral;
    
    address[] public collateralList;
    uint256 public stabilityFee; // Annual interest rate in basis points
    uint256 public liquidationPenalty; // In basis points (1000 = 10%)
    uint256 public minCollateralRatio; // Minimum collateral ratio (15000 = 150%)
    uint256 public totalDebt;
    uint256 public debtCeiling;
    address public stabilityPool;
    address public treasury;
    
    // Events
    event VaultOpened(address indexed owner);
    event CollateralDeposited(address indexed owner, address indexed asset, uint256 amount);
    event CollateralWithdrawn(address indexed owner, address indexed asset, uint256 amount);
    event DebtMinted(address indexed owner, uint256 amount);
    event DebtBurned(address indexed owner, uint256 amount);
    event VaultLiquidated(
        address indexed owner,
        address indexed liquidator,
        uint256 debtRepaid,
        uint256 collateralSeized
    );
    event CollateralAdded(address indexed asset, uint256 collateralRatio);
    event CollateralRemoved(address indexed asset);
    event StabilityFeeUpdated(uint256 newFee);
    event PegDefenderActivated(uint256 amount, bool isMint);

    /// @custom:oz-upgrades-unsafe-allow constructor
    constructor() {
        _disableInitializers();
    }

    function initialize(
        address admin,
        uint256 _stabilityFee,
        uint256 _liquidationPenalty,
        uint256 _minCollateralRatio,
        uint256 _debtCeiling,
        address _treasury
    ) public initializer {
        __ERC20_init("Waqiti USD", "WUSD");
        __ERC20Burnable_init();
        __AccessControl_init();
        __Pausable_init();
        __UUPSUpgradeable_init();

        _grantRole(DEFAULT_ADMIN_ROLE, admin);
        _grantRole(MINTER_ROLE, admin);
        _grantRole(PAUSER_ROLE, admin);
        _grantRole(UPGRADER_ROLE, admin);
        _grantRole(STABILITY_ROLE, admin);

        stabilityFee = _stabilityFee;
        liquidationPenalty = _liquidationPenalty;
        minCollateralRatio = _minCollateralRatio;
        debtCeiling = _debtCeiling;
        treasury = _treasury;
    }

    /**
     * @dev Adds a new collateral type
     */
    function addCollateral(
        address asset,
        uint256 collateralRatio,
        address priceOracle
    ) external onlyRole(DEFAULT_ADMIN_ROLE) {
        require(!whitelistedCollateral[asset], "Collateral already exists");
        require(collateralRatio >= minCollateralRatio, "Ratio too low");
        
        collateralTypes[asset] = Collateral({
            asset: asset,
            amount: 0,
            collateralRatio: collateralRatio,
            priceOracle: priceOracle,
            isActive: true
        });
        
        whitelistedCollateral[asset] = true;
        collateralList.push(asset);
        
        emit CollateralAdded(asset, collateralRatio);
    }

    /**
     * @dev Opens a new vault or adds collateral to existing vault
     */
    function depositCollateral(
        address asset,
        uint256 amount
    ) external whenNotPaused {
        require(whitelistedCollateral[asset], "Collateral not whitelisted");
        require(collateralTypes[asset].isActive, "Collateral not active");
        require(amount > 0, "Amount must be greater than 0");
        
        // Transfer collateral from user
        IERC20Upgradeable(asset).transferFrom(msg.sender, address(this), amount);
        
        // Update vault
        Vault storage vault = vaults[msg.sender];
        if (vault.lastInterestUpdate == 0) {
            vault.lastInterestUpdate = block.timestamp;
            emit VaultOpened(msg.sender);
        }
        
        vault.collateral[asset] += amount;
        collateralTypes[asset].amount += amount;
        
        emit CollateralDeposited(msg.sender, asset, amount);
    }

    /**
     * @dev Withdraws collateral from vault
     */
    function withdrawCollateral(
        address asset,
        uint256 amount
    ) external whenNotPaused {
        Vault storage vault = vaults[msg.sender];
        require(vault.collateral[asset] >= amount, "Insufficient collateral");
        
        // Update interest
        _updateInterest(msg.sender);
        
        // Temporarily reduce collateral
        vault.collateral[asset] -= amount;
        collateralTypes[asset].amount -= amount;
        
        // Check collateral ratio
        require(_getCollateralRatio(msg.sender) >= minCollateralRatio, "Below minimum ratio");
        
        // Transfer collateral to user
        IERC20Upgradeable(asset).transfer(msg.sender, amount);
        
        emit CollateralWithdrawn(msg.sender, asset, amount);
    }

    /**
     * @dev Mints WUSD against collateral
     */
    function mintWUSD(uint256 amount) external whenNotPaused {
        require(amount > 0, "Amount must be greater than 0");
        require(totalDebt + amount <= debtCeiling, "Debt ceiling reached");
        
        Vault storage vault = vaults[msg.sender];
        require(vault.lastInterestUpdate > 0, "Vault not opened");
        
        // Update interest
        _updateInterest(msg.sender);
        
        // Update debt
        vault.debt += amount;
        totalDebt += amount;
        
        // Check collateral ratio
        require(_getCollateralRatio(msg.sender) >= minCollateralRatio, "Below minimum ratio");
        
        // Mint WUSD to user
        _mint(msg.sender, amount);
        
        emit DebtMinted(msg.sender, amount);
    }

    /**
     * @dev Burns WUSD to reduce debt
     */
    function burnWUSD(uint256 amount) external {
        Vault storage vault = vaults[msg.sender];
        require(vault.debt > 0, "No debt to repay");
        
        // Update interest
        _updateInterest(msg.sender);
        
        uint256 repayAmount = amount > vault.debt ? vault.debt : amount;
        
        // Burn WUSD from user
        _burn(msg.sender, repayAmount);
        
        // Update debt
        vault.debt -= repayAmount;
        totalDebt -= repayAmount;
        
        emit DebtBurned(msg.sender, repayAmount);
    }

    /**
     * @dev Liquidates an undercollateralized vault
     */
    function liquidate(
        address vaultOwner,
        address collateralAsset,
        uint256 debtToRepay
    ) external whenNotPaused {
        require(vaultOwner != msg.sender, "Cannot liquidate own vault");
        
        Vault storage vault = vaults[vaultOwner];
        require(vault.debt > 0, "No debt");
        
        // Update interest
        _updateInterest(vaultOwner);
        
        // Check if vault is undercollateralized
        require(_getCollateralRatio(vaultOwner) < minCollateralRatio, "Vault is healthy");
        
        uint256 maxRepay = vault.debt / 2; // Can liquidate max 50%
        uint256 actualRepay = debtToRepay > maxRepay ? maxRepay : debtToRepay;
        
        // Calculate collateral to seize
        uint256 collateralPrice = _getAssetPrice(collateralAsset);
        uint256 collateralToSeize = (actualRepay * (10000 + liquidationPenalty) * 1e18) / 
                                    (collateralPrice * 10000);
        
        require(vault.collateral[collateralAsset] >= collateralToSeize, "Insufficient collateral");
        
        // Burn WUSD from liquidator
        _burn(msg.sender, actualRepay);
        
        // Update vault
        vault.debt -= actualRepay;
        vault.collateral[collateralAsset] -= collateralToSeize;
        totalDebt -= actualRepay;
        collateralTypes[collateralAsset].amount -= collateralToSeize;
        
        // Transfer collateral to liquidator
        IERC20Upgradeable(collateralAsset).transfer(msg.sender, collateralToSeize);
        
        emit VaultLiquidated(vaultOwner, msg.sender, actualRepay, collateralToSeize);
    }

    /**
     * @dev Peg stability mechanism - mints/burns to maintain peg
     */
    function pegDefender(bool shouldMint, uint256 amount) external onlyRole(STABILITY_ROLE) {
        if (shouldMint) {
            // Mint to stability pool when WUSD > $1
            require(stabilityPool != address(0), "Stability pool not set");
            _mint(stabilityPool, amount);
        } else {
            // Burn from stability pool when WUSD < $1
            require(balanceOf(stabilityPool) >= amount, "Insufficient balance");
            _burn(stabilityPool, amount);
        }
        
        emit PegDefenderActivated(amount, shouldMint);
    }

    /**
     * @dev Gets vault information
     */
    function getVaultInfo(address owner) external view returns (
        uint256 totalCollateralValue,
        uint256 debt,
        uint256 collateralRatio,
        bool isHealthy
    ) {
        Vault storage vault = vaults[owner];
        debt = vault.debt + _calculateInterest(owner);
        totalCollateralValue = _getTotalCollateralValue(owner);
        
        if (debt > 0) {
            collateralRatio = (totalCollateralValue * 10000) / debt;
        } else {
            collateralRatio = type(uint256).max;
        }
        
        isHealthy = collateralRatio >= minCollateralRatio;
    }

    /**
     * @dev Updates interest for a vault
     */
    function _updateInterest(address owner) private {
        Vault storage vault = vaults[owner];
        
        if (vault.debt == 0 || vault.lastInterestUpdate >= block.timestamp) {
            return;
        }
        
        uint256 interest = _calculateInterest(owner);
        vault.debt += interest;
        totalDebt += interest;
        vault.lastInterestUpdate = block.timestamp;
        
        // Mint interest to treasury
        if (interest > 0 && treasury != address(0)) {
            _mint(treasury, interest);
        }
    }

    /**
     * @dev Calculates accrued interest for a vault
     */
    function _calculateInterest(address owner) private view returns (uint256) {
        Vault storage vault = vaults[owner];
        
        if (vault.debt == 0 || vault.lastInterestUpdate >= block.timestamp) {
            return 0;
        }
        
        uint256 timeElapsed = block.timestamp - vault.lastInterestUpdate;
        uint256 interest = (vault.debt * stabilityFee * timeElapsed) / (365 days * 10000);
        
        return interest;
    }

    /**
     * @dev Gets total collateral value for a vault
     */
    function _getTotalCollateralValue(address owner) private view returns (uint256) {
        Vault storage vault = vaults[owner];
        uint256 totalValue = 0;
        
        for (uint256 i = 0; i < collateralList.length; i++) {
            address asset = collateralList[i];
            uint256 amount = vault.collateral[asset];
            
            if (amount > 0) {
                uint256 price = _getAssetPrice(asset);
                totalValue += (amount * price) / 1e18;
            }
        }
        
        return totalValue;
    }

    /**
     * @dev Gets collateral ratio for a vault
     */
    function _getCollateralRatio(address owner) private view returns (uint256) {
        Vault storage vault = vaults[owner];
        uint256 debt = vault.debt + _calculateInterest(owner);
        
        if (debt == 0) {
            return type(uint256).max;
        }
        
        uint256 collateralValue = _getTotalCollateralValue(owner);
        return (collateralValue * 10000) / debt;
    }

    /**
     * @dev Gets asset price from oracle
     */
    function _getAssetPrice(address asset) private view returns (uint256) {
        Collateral memory collateral = collateralTypes[asset];
        
        if (collateral.priceOracle == address(0)) {
            return 1e18; // Default to $1 for stablecoins
        }
        
        AggregatorV3Interface priceFeed = AggregatorV3Interface(collateral.priceOracle);
        (, int256 price,,,) = priceFeed.latestRoundData();
        
        return uint256(price) * 1e10; // Convert to 18 decimals
    }

    /**
     * @dev Sets the stability pool address
     */
    function setStabilityPool(address _stabilityPool) external onlyRole(DEFAULT_ADMIN_ROLE) {
        stabilityPool = _stabilityPool;
    }

    /**
     * @dev Updates stability fee
     */
    function updateStabilityFee(uint256 newFee) external onlyRole(STABILITY_ROLE) {
        require(newFee <= 2000, "Fee too high"); // Max 20% APR
        stabilityFee = newFee;
        emit StabilityFeeUpdated(newFee);
    }

    /**
     * @dev Updates debt ceiling
     */
    function updateDebtCeiling(uint256 newCeiling) external onlyRole(DEFAULT_ADMIN_ROLE) {
        debtCeiling = newCeiling;
    }

    /**
     * @dev Emergency pause
     */
    function pause() public onlyRole(PAUSER_ROLE) {
        _pause();
    }

    /**
     * @dev Unpause
     */
    function unpause() public onlyRole(PAUSER_ROLE) {
        _unpause();
    }

    /**
     * @dev Hook that is called before any transfer of tokens
     */
    function _beforeTokenTransfer(
        address from,
        address to,
        uint256 amount
    ) internal override whenNotPaused {
        super._beforeTokenTransfer(from, to, amount);
    }

    /**
     * @dev Authorizes upgrades
     */
    function _authorizeUpgrade(address newImplementation)
        internal
        override
        onlyRole(UPGRADER_ROLE)
    {}
}