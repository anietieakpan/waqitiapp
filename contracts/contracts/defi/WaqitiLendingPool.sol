// SPDX-License-Identifier: MIT
pragma solidity ^0.8.20;

import "@openzeppelin/contracts-upgradeable/access/AccessControlUpgradeable.sol";
import "@openzeppelin/contracts-upgradeable/security/PausableUpgradeable.sol";
import "@openzeppelin/contracts-upgradeable/security/ReentrancyGuardUpgradeable.sol";
import "@openzeppelin/contracts-upgradeable/proxy/utils/Initializable.sol";
import "@openzeppelin/contracts-upgradeable/proxy/utils/UUPSUpgradeable.sol";
import "@openzeppelin/contracts/token/ERC20/IERC20.sol";
import "@openzeppelin/contracts/token/ERC20/utils/SafeERC20.sol";
import "@chainlink/contracts/src/v0.8/interfaces/AggregatorV3Interface.sol";

/**
 * @title WaqitiLendingPool
 * @dev Decentralized lending and borrowing protocol
 */
contract WaqitiLendingPool is 
    Initializable,
    AccessControlUpgradeable,
    PausableUpgradeable,
    ReentrancyGuardUpgradeable,
    UUPSUpgradeable
{
    using SafeERC20 for IERC20;

    bytes32 public constant OPERATOR_ROLE = keccak256("OPERATOR_ROLE");
    bytes32 public constant UPGRADER_ROLE = keccak256("UPGRADER_ROLE");
    bytes32 public constant RISK_MANAGER_ROLE = keccak256("RISK_MANAGER_ROLE");
    bytes32 public constant LIQUIDATOR_ROLE = keccak256("LIQUIDATOR_ROLE");

    struct Market {
        address asset;
        uint256 totalSupply;
        uint256 totalBorrow;
        uint256 supplyIndex;
        uint256 borrowIndex;
        uint256 supplyRate;
        uint256 borrowRate;
        uint256 lastUpdateTimestamp;
        uint256 collateralFactor; // In basis points (8000 = 80%)
        uint256 liquidationThreshold; // In basis points (8500 = 85%)
        uint256 liquidationPenalty; // In basis points (500 = 5%)
        uint256 reserveFactor; // In basis points (1000 = 10%)
        uint256 reserves;
        bool isActive;
        bool borrowingEnabled;
        bool usedAsCollateral;
        address priceOracle;
    }

    struct UserAccount {
        mapping(address => uint256) supplied; // Asset => Amount supplied
        mapping(address => uint256) borrowed; // Asset => Amount borrowed
        mapping(address => bool) usedAsCollateral; // Asset => Is used as collateral
        address[] suppliedAssets;
        address[] borrowedAssets;
    }

    struct InterestRateModel {
        uint256 baseRate; // Base interest rate
        uint256 multiplier; // Rate increase per utilization
        uint256 jumpMultiplier; // Rate increase after optimal utilization
        uint256 optimalUtilization; // Optimal utilization rate (in basis points)
    }

    // State variables
    mapping(address => Market) public markets;
    mapping(address => UserAccount) private userAccounts;
    mapping(address => InterestRateModel) public interestModels;
    
    address[] public marketsList;
    uint256 public liquidationIncentive; // Extra incentive for liquidators
    uint256 public flashLoanFee; // Fee for flash loans (in basis points)
    address public treasury;
    
    // Events
    event MarketAdded(address indexed asset, uint256 collateralFactor);
    event MarketUpdated(address indexed asset, uint256 collateralFactor);
    event Supply(address indexed user, address indexed asset, uint256 amount);
    event Withdraw(address indexed user, address indexed asset, uint256 amount);
    event Borrow(address indexed user, address indexed asset, uint256 amount);
    event Repay(address indexed user, address indexed asset, uint256 amount);
    event Liquidation(
        address indexed liquidator,
        address indexed borrower,
        address indexed collateralAsset,
        address debtAsset,
        uint256 debtRepaid,
        uint256 collateralSeized
    );
    event FlashLoan(
        address indexed borrower,
        address indexed asset,
        uint256 amount,
        uint256 fee
    );
    event CollateralEnabled(address indexed user, address indexed asset);
    event CollateralDisabled(address indexed user, address indexed asset);
    event ReservesWithdrawn(address indexed asset, uint256 amount);

    /// @custom:oz-upgrades-unsafe-allow constructor
    constructor() {
        _disableInitializers();
    }

    function initialize(
        address admin,
        address _treasury,
        uint256 _liquidationIncentive,
        uint256 _flashLoanFee
    ) public initializer {
        __AccessControl_init();
        __Pausable_init();
        __ReentrancyGuard_init();
        __UUPSUpgradeable_init();

        _grantRole(DEFAULT_ADMIN_ROLE, admin);
        _grantRole(OPERATOR_ROLE, admin);
        _grantRole(UPGRADER_ROLE, admin);
        _grantRole(RISK_MANAGER_ROLE, admin);

        treasury = _treasury;
        liquidationIncentive = _liquidationIncentive;
        flashLoanFee = _flashLoanFee;
    }

    /**
     * @dev Adds a new market
     */
    function addMarket(
        address asset,
        uint256 collateralFactor,
        uint256 liquidationThreshold,
        uint256 liquidationPenalty,
        uint256 reserveFactor,
        address priceOracle,
        bool borrowingEnabled,
        bool usedAsCollateral
    ) external onlyRole(RISK_MANAGER_ROLE) {
        require(markets[asset].asset == address(0), "Market already exists");
        require(collateralFactor <= 9500, "Collateral factor too high");
        require(liquidationThreshold > collateralFactor, "Invalid liquidation threshold");
        
        markets[asset] = Market({
            asset: asset,
            totalSupply: 0,
            totalBorrow: 0,
            supplyIndex: 1e18,
            borrowIndex: 1e18,
            supplyRate: 0,
            borrowRate: 0,
            lastUpdateTimestamp: block.timestamp,
            collateralFactor: collateralFactor,
            liquidationThreshold: liquidationThreshold,
            liquidationPenalty: liquidationPenalty,
            reserveFactor: reserveFactor,
            reserves: 0,
            isActive: true,
            borrowingEnabled: borrowingEnabled,
            usedAsCollateral: usedAsCollateral,
            priceOracle: priceOracle
        });
        
        // Set default interest rate model
        interestModels[asset] = InterestRateModel({
            baseRate: 2e16, // 2% base rate
            multiplier: 15e16, // 15% increase per utilization
            jumpMultiplier: 50e16, // 50% increase after optimal
            optimalUtilization: 8000 // 80% optimal utilization
        });
        
        marketsList.push(asset);
        
        emit MarketAdded(asset, collateralFactor);
    }

    /**
     * @dev Supplies assets to the lending pool
     */
    function supply(address asset, uint256 amount) external whenNotPaused nonReentrant {
        Market storage market = markets[asset];
        require(market.isActive, "Market not active");
        require(amount > 0, "Amount must be greater than 0");
        
        // Update interest rates
        _updateInterest(asset);
        
        // Transfer tokens from user
        IERC20(asset).safeTransferFrom(msg.sender, address(this), amount);
        
        // Update user balance
        UserAccount storage account = userAccounts[msg.sender];
        if (account.supplied[asset] == 0) {
            account.suppliedAssets.push(asset);
        }
        account.supplied[asset] += amount;
        
        // Update market
        market.totalSupply += amount;
        
        // Update interest rates
        _updateRates(asset);
        
        emit Supply(msg.sender, asset, amount);
    }

    /**
     * @dev Withdraws supplied assets
     */
    function withdraw(address asset, uint256 amount) external whenNotPaused nonReentrant {
        Market storage market = markets[asset];
        UserAccount storage account = userAccounts[msg.sender];
        
        require(account.supplied[asset] >= amount, "Insufficient balance");
        
        // Update interest rates
        _updateInterest(asset);
        
        // Check if withdrawal would cause undercollateralization
        account.supplied[asset] -= amount;
        require(_checkHealthFactor(msg.sender) >= 1e18, "Health factor too low");
        
        // Update market
        market.totalSupply -= amount;
        
        // Transfer tokens to user
        IERC20(asset).safeTransfer(msg.sender, amount);
        
        // Update interest rates
        _updateRates(asset);
        
        emit Withdraw(msg.sender, asset, amount);
    }

    /**
     * @dev Borrows assets from the lending pool
     */
    function borrow(address asset, uint256 amount) external whenNotPaused nonReentrant {
        Market storage market = markets[asset];
        require(market.isActive && market.borrowingEnabled, "Borrowing not enabled");
        require(amount > 0, "Amount must be greater than 0");
        require(market.totalSupply >= market.totalBorrow + amount, "Insufficient liquidity");
        
        // Update interest rates
        _updateInterest(asset);
        
        // Update user balance
        UserAccount storage account = userAccounts[msg.sender];
        if (account.borrowed[asset] == 0) {
            account.borrowedAssets.push(asset);
        }
        account.borrowed[asset] += amount;
        
        // Check health factor
        require(_checkHealthFactor(msg.sender) >= 1e18, "Insufficient collateral");
        
        // Update market
        market.totalBorrow += amount;
        
        // Transfer tokens to user
        IERC20(asset).safeTransfer(msg.sender, amount);
        
        // Update interest rates
        _updateRates(asset);
        
        emit Borrow(msg.sender, asset, amount);
    }

    /**
     * @dev Repays borrowed assets
     */
    function repay(address asset, uint256 amount) external whenNotPaused nonReentrant {
        Market storage market = markets[asset];
        UserAccount storage account = userAccounts[msg.sender];
        
        require(account.borrowed[asset] > 0, "No debt to repay");
        
        // Update interest rates
        _updateInterest(asset);
        
        uint256 repayAmount = amount > account.borrowed[asset] ? account.borrowed[asset] : amount;
        
        // Transfer tokens from user
        IERC20(asset).safeTransferFrom(msg.sender, address(this), repayAmount);
        
        // Update user balance
        account.borrowed[asset] -= repayAmount;
        
        // Update market
        market.totalBorrow -= repayAmount;
        
        // Update interest rates
        _updateRates(asset);
        
        emit Repay(msg.sender, asset, repayAmount);
    }

    /**
     * @dev Liquidates an undercollateralized position
     */
    function liquidate(
        address borrower,
        address collateralAsset,
        address debtAsset,
        uint256 debtAmount
    ) external whenNotPaused nonReentrant {
        require(borrower != msg.sender, "Cannot liquidate yourself");
        require(_checkHealthFactor(borrower) < 1e18, "Position is healthy");
        
        UserAccount storage borrowerAccount = userAccounts[borrower];
        Market storage collateralMarket = markets[collateralAsset];
        Market storage debtMarket = markets[debtAsset];
        
        // Update interest rates
        _updateInterest(collateralAsset);
        _updateInterest(debtAsset);
        
        // Calculate liquidation amounts
        uint256 maxDebtRepay = (borrowerAccount.borrowed[debtAsset] * 5000) / 10000; // Max 50%
        uint256 actualDebtRepay = debtAmount > maxDebtRepay ? maxDebtRepay : debtAmount;
        
        uint256 collateralPrice = _getAssetPrice(collateralAsset);
        uint256 debtPrice = _getAssetPrice(debtAsset);
        uint256 collateralToSeize = (actualDebtRepay * debtPrice * (10000 + liquidationIncentive)) / 
                                    (collateralPrice * 10000);
        
        require(borrowerAccount.supplied[collateralAsset] >= collateralToSeize, "Insufficient collateral");
        
        // Transfer debt from liquidator
        IERC20(debtAsset).safeTransferFrom(msg.sender, address(this), actualDebtRepay);
        
        // Update borrower's debt
        borrowerAccount.borrowed[debtAsset] -= actualDebtRepay;
        debtMarket.totalBorrow -= actualDebtRepay;
        
        // Transfer collateral to liquidator
        borrowerAccount.supplied[collateralAsset] -= collateralToSeize;
        collateralMarket.totalSupply -= collateralToSeize;
        IERC20(collateralAsset).safeTransfer(msg.sender, collateralToSeize);
        
        emit Liquidation(
            msg.sender,
            borrower,
            collateralAsset,
            debtAsset,
            actualDebtRepay,
            collateralToSeize
        );
    }

    /**
     * @dev Enables an asset as collateral
     */
    function enableCollateral(address asset) external {
        Market memory market = markets[asset];
        require(market.isActive && market.usedAsCollateral, "Cannot use as collateral");
        
        UserAccount storage account = userAccounts[msg.sender];
        require(account.supplied[asset] > 0, "No supply balance");
        require(!account.usedAsCollateral[asset], "Already enabled");
        
        account.usedAsCollateral[asset] = true;
        
        emit CollateralEnabled(msg.sender, asset);
    }

    /**
     * @dev Disables an asset as collateral
     */
    function disableCollateral(address asset) external {
        UserAccount storage account = userAccounts[msg.sender];
        require(account.usedAsCollateral[asset], "Not enabled");
        
        // Temporarily disable collateral
        account.usedAsCollateral[asset] = false;
        
        // Check if this would cause undercollateralization
        require(_checkHealthFactor(msg.sender) >= 1e18, "Would cause undercollateralization");
        
        emit CollateralDisabled(msg.sender, asset);
    }

    /**
     * @dev Flash loan implementation
     */
    function flashLoan(
        address asset,
        uint256 amount,
        bytes calldata data
    ) external whenNotPaused nonReentrant {
        Market storage market = markets[asset];
        require(market.isActive, "Market not active");
        
        uint256 balanceBefore = IERC20(asset).balanceOf(address(this));
        require(balanceBefore >= amount, "Insufficient liquidity");
        
        uint256 fee = (amount * flashLoanFee) / 10000;
        
        // Transfer tokens to borrower
        IERC20(asset).safeTransfer(msg.sender, amount);
        
        // Execute borrower's logic
        (bool success,) = msg.sender.call(data);
        require(success, "Flash loan execution failed");
        
        // Check repayment
        uint256 balanceAfter = IERC20(asset).balanceOf(address(this));
        require(balanceAfter >= balanceBefore + fee, "Flash loan not repaid");
        
        // Add fee to reserves
        market.reserves += fee;
        
        emit FlashLoan(msg.sender, asset, amount, fee);
    }

    /**
     * @dev Gets user account data
     */
    function getUserAccountData(address user) external view returns (
        uint256 totalCollateral,
        uint256 totalDebt,
        uint256 availableBorrow,
        uint256 healthFactor
    ) {
        UserAccount storage account = userAccounts[user];
        
        // Calculate total collateral
        for (uint256 i = 0; i < account.suppliedAssets.length; i++) {
            address asset = account.suppliedAssets[i];
            if (account.usedAsCollateral[asset]) {
                uint256 assetPrice = _getAssetPrice(asset);
                uint256 collateralValue = (account.supplied[asset] * assetPrice * markets[asset].collateralFactor) / 1e22;
                totalCollateral += collateralValue;
            }
        }
        
        // Calculate total debt
        for (uint256 i = 0; i < account.borrowedAssets.length; i++) {
            address asset = account.borrowedAssets[i];
            uint256 assetPrice = _getAssetPrice(asset);
            totalDebt += (account.borrowed[asset] * assetPrice) / 1e18;
        }
        
        // Calculate available borrow
        if (totalCollateral > totalDebt) {
            availableBorrow = totalCollateral - totalDebt;
        }
        
        // Calculate health factor
        healthFactor = _checkHealthFactor(user);
    }

    /**
     * @dev Updates interest rates for a market
     */
    function _updateInterest(address asset) private {
        Market storage market = markets[asset];
        
        if (block.timestamp == market.lastUpdateTimestamp) {
            return;
        }
        
        uint256 timeDelta = block.timestamp - market.lastUpdateTimestamp;
        
        // Update supply index
        if (market.totalSupply > 0) {
            uint256 supplyInterest = (market.supplyRate * timeDelta * market.totalSupply) / 1e18;
            market.supplyIndex += (supplyInterest * market.supplyIndex) / market.totalSupply;
        }
        
        // Update borrow index
        if (market.totalBorrow > 0) {
            uint256 borrowInterest = (market.borrowRate * timeDelta * market.totalBorrow) / 1e18;
            market.borrowIndex += (borrowInterest * market.borrowIndex) / market.totalBorrow;
            
            // Add to reserves
            uint256 reserveIncrease = (borrowInterest * market.reserveFactor) / 10000;
            market.reserves += reserveIncrease;
        }
        
        market.lastUpdateTimestamp = block.timestamp;
    }

    /**
     * @dev Updates interest rates based on utilization
     */
    function _updateRates(address asset) private {
        Market storage market = markets[asset];
        InterestRateModel memory model = interestModels[asset];
        
        if (market.totalSupply == 0) {
            market.supplyRate = 0;
            market.borrowRate = model.baseRate;
            return;
        }
        
        uint256 utilization = (market.totalBorrow * 1e18) / market.totalSupply;
        
        // Calculate borrow rate
        if (utilization <= model.optimalUtilization * 1e14) {
            market.borrowRate = model.baseRate + (utilization * model.multiplier) / 1e18;
        } else {
            uint256 excessUtilization = utilization - (model.optimalUtilization * 1e14);
            market.borrowRate = model.baseRate + 
                               (model.optimalUtilization * 1e14 * model.multiplier) / 1e18 +
                               (excessUtilization * model.jumpMultiplier) / 1e18;
        }
        
        // Calculate supply rate
        uint256 borrowInterest = (market.borrowRate * market.totalBorrow) / 1e18;
        uint256 supplierInterest = (borrowInterest * (10000 - market.reserveFactor)) / 10000;
        market.supplyRate = (supplierInterest * 1e18) / market.totalSupply;
    }

    /**
     * @dev Checks user health factor
     */
    function _checkHealthFactor(address user) private view returns (uint256) {
        UserAccount storage account = userAccounts[user];
        
        uint256 totalCollateral = 0;
        uint256 totalDebt = 0;
        
        // Calculate weighted collateral
        for (uint256 i = 0; i < account.suppliedAssets.length; i++) {
            address asset = account.suppliedAssets[i];
            if (account.usedAsCollateral[asset]) {
                uint256 assetPrice = _getAssetPrice(asset);
                uint256 collateralValue = (account.supplied[asset] * assetPrice * markets[asset].liquidationThreshold) / 1e22;
                totalCollateral += collateralValue;
            }
        }
        
        // Calculate total debt
        for (uint256 i = 0; i < account.borrowedAssets.length; i++) {
            address asset = account.borrowedAssets[i];
            uint256 assetPrice = _getAssetPrice(asset);
            totalDebt += (account.borrowed[asset] * assetPrice) / 1e18;
        }
        
        if (totalDebt == 0) {
            return type(uint256).max;
        }
        
        return (totalCollateral * 1e18) / totalDebt;
    }

    /**
     * @dev Gets asset price from oracle
     */
    function _getAssetPrice(address asset) private view returns (uint256) {
        Market memory market = markets[asset];
        if (market.priceOracle == address(0)) {
            return 1e18; // Default to 1:1 for stablecoins
        }
        
        AggregatorV3Interface priceFeed = AggregatorV3Interface(market.priceOracle);
        (, int256 price,,,) = priceFeed.latestRoundData();
        
        return uint256(price) * 1e10; // Convert to 18 decimals
    }

    /**
     * @dev Withdraws reserves
     */
    function withdrawReserves(address asset, uint256 amount) external onlyRole(DEFAULT_ADMIN_ROLE) {
        Market storage market = markets[asset];
        require(market.reserves >= amount, "Insufficient reserves");
        
        market.reserves -= amount;
        IERC20(asset).safeTransfer(treasury, amount);
        
        emit ReservesWithdrawn(asset, amount);
    }

    /**
     * @dev Updates interest rate model
     */
    function updateInterestModel(
        address asset,
        uint256 baseRate,
        uint256 multiplier,
        uint256 jumpMultiplier,
        uint256 optimalUtilization
    ) external onlyRole(RISK_MANAGER_ROLE) {
        interestModels[asset] = InterestRateModel({
            baseRate: baseRate,
            multiplier: multiplier,
            jumpMultiplier: jumpMultiplier,
            optimalUtilization: optimalUtilization
        });
        
        _updateRates(asset);
    }

    /**
     * @dev Pauses the lending pool
     */
    function pause() external onlyRole(OPERATOR_ROLE) {
        _pause();
    }

    /**
     * @dev Unpauses the lending pool
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
}