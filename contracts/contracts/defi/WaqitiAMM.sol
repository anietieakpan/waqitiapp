// SPDX-License-Identifier: MIT
pragma solidity ^0.8.20;

import "@openzeppelin/contracts-upgradeable/access/AccessControlUpgradeable.sol";
import "@openzeppelin/contracts-upgradeable/security/PausableUpgradeable.sol";
import "@openzeppelin/contracts-upgradeable/security/ReentrancyGuardUpgradeable.sol";
import "@openzeppelin/contracts-upgradeable/proxy/utils/Initializable.sol";
import "@openzeppelin/contracts-upgradeable/proxy/utils/UUPSUpgradeable.sol";
import "@openzeppelin/contracts/token/ERC20/IERC20.sol";
import "@openzeppelin/contracts/token/ERC20/utils/SafeERC20.sol";
import "@openzeppelin/contracts/utils/math/Math.sol";

/**
 * @title WaqitiAMM
 * @dev Automated Market Maker for token swaps with liquidity pools
 */
contract WaqitiAMM is 
    Initializable,
    AccessControlUpgradeable,
    PausableUpgradeable,
    ReentrancyGuardUpgradeable,
    UUPSUpgradeable
{
    using SafeERC20 for IERC20;
    using Math for uint256;

    bytes32 public constant OPERATOR_ROLE = keccak256("OPERATOR_ROLE");
    bytes32 public constant UPGRADER_ROLE = keccak256("UPGRADER_ROLE");
    bytes32 public constant FEE_MANAGER_ROLE = keccak256("FEE_MANAGER_ROLE");

    struct Pool {
        address token0;
        address token1;
        uint256 reserve0;
        uint256 reserve1;
        uint256 totalLiquidity;
        uint256 kLast; // reserve0 * reserve1, as of immediately after the most recent liquidity event
        uint32 blockTimestampLast;
        uint256 price0CumulativeLast;
        uint256 price1CumulativeLast;
    }

    struct LiquidityPosition {
        uint256 liquidity;
        uint256 token0Deposited;
        uint256 token1Deposited;
    }

    // State variables
    uint256 public constant MINIMUM_LIQUIDITY = 10**3;
    uint256 public swapFee; // In basis points (30 = 0.3%)
    uint256 public protocolFee; // In basis points (5 = 0.05%)
    address public feeRecipient;
    
    mapping(bytes32 => Pool) public pools;
    mapping(bytes32 => mapping(address => LiquidityPosition)) public liquidityPositions;
    mapping(bytes32 => address) public lpTokens; // LP token for each pool
    mapping(address => mapping(address => bytes32)) public getPair;
    
    bytes32[] public allPools;
    
    // Events
    event PoolCreated(
        bytes32 indexed poolId,
        address indexed token0,
        address indexed token1,
        address lpToken
    );
    
    event LiquidityAdded(
        bytes32 indexed poolId,
        address indexed provider,
        uint256 amount0,
        uint256 amount1,
        uint256 liquidity
    );
    
    event LiquidityRemoved(
        bytes32 indexed poolId,
        address indexed provider,
        uint256 amount0,
        uint256 amount1,
        uint256 liquidity
    );
    
    event Swap(
        bytes32 indexed poolId,
        address indexed trader,
        address tokenIn,
        address tokenOut,
        uint256 amountIn,
        uint256 amountOut
    );
    
    event Sync(bytes32 indexed poolId, uint256 reserve0, uint256 reserve1);
    event FeeUpdated(uint256 newSwapFee, uint256 newProtocolFee);

    /// @custom:oz-upgrades-unsafe-allow constructor
    constructor() {
        _disableInitializers();
    }

    function initialize(
        address admin,
        uint256 _swapFee,
        uint256 _protocolFee,
        address _feeRecipient
    ) public initializer {
        __AccessControl_init();
        __Pausable_init();
        __ReentrancyGuard_init();
        __UUPSUpgradeable_init();

        _grantRole(DEFAULT_ADMIN_ROLE, admin);
        _grantRole(OPERATOR_ROLE, admin);
        _grantRole(UPGRADER_ROLE, admin);
        _grantRole(FEE_MANAGER_ROLE, admin);

        swapFee = _swapFee;
        protocolFee = _protocolFee;
        feeRecipient = _feeRecipient;
    }

    /**
     * @dev Creates a new liquidity pool
     */
    function createPool(
        address tokenA,
        address tokenB
    ) external whenNotPaused returns (bytes32 poolId) {
        require(tokenA != tokenB, "Identical tokens");
        require(tokenA != address(0) && tokenB != address(0), "Zero address");
        
        (address token0, address token1) = tokenA < tokenB ? (tokenA, tokenB) : (tokenB, tokenA);
        require(getPair[token0][token1] == bytes32(0), "Pool already exists");
        
        poolId = keccak256(abi.encodePacked(token0, token1));
        
        pools[poolId] = Pool({
            token0: token0,
            token1: token1,
            reserve0: 0,
            reserve1: 0,
            totalLiquidity: 0,
            kLast: 0,
            blockTimestampLast: 0,
            price0CumulativeLast: 0,
            price1CumulativeLast: 0
        });
        
        getPair[token0][token1] = poolId;
        getPair[token1][token0] = poolId;
        allPools.push(poolId);
        
        // Deploy LP token (simplified - in production, would deploy actual ERC20)
        lpTokens[poolId] = address(this); // Placeholder
        
        emit PoolCreated(poolId, token0, token1, lpTokens[poolId]);
    }

    /**
     * @dev Adds liquidity to a pool
     */
    function addLiquidity(
        bytes32 poolId,
        uint256 amount0Desired,
        uint256 amount1Desired,
        uint256 amount0Min,
        uint256 amount1Min
    ) external whenNotPaused nonReentrant returns (
        uint256 amount0,
        uint256 amount1,
        uint256 liquidity
    ) {
        Pool storage pool = pools[poolId];
        require(pool.token0 != address(0), "Pool doesn't exist");
        
        (amount0, amount1) = _calculateOptimalAmounts(
            pool,
            amount0Desired,
            amount1Desired,
            amount0Min,
            amount1Min
        );
        
        // Transfer tokens from user
        IERC20(pool.token0).safeTransferFrom(msg.sender, address(this), amount0);
        IERC20(pool.token1).safeTransferFrom(msg.sender, address(this), amount1);
        
        // Calculate liquidity
        if (pool.totalLiquidity == 0) {
            liquidity = Math.sqrt(amount0 * amount1) - MINIMUM_LIQUIDITY;
            pool.totalLiquidity = MINIMUM_LIQUIDITY; // Permanently lock the first MINIMUM_LIQUIDITY tokens
        } else {
            liquidity = Math.min(
                (amount0 * pool.totalLiquidity) / pool.reserve0,
                (amount1 * pool.totalLiquidity) / pool.reserve1
            );
        }
        
        require(liquidity > 0, "Insufficient liquidity minted");
        
        // Update reserves and liquidity
        pool.reserve0 += amount0;
        pool.reserve1 += amount1;
        pool.totalLiquidity += liquidity;
        
        // Update user's position
        LiquidityPosition storage position = liquidityPositions[poolId][msg.sender];
        position.liquidity += liquidity;
        position.token0Deposited += amount0;
        position.token1Deposited += amount1;
        
        // Update price oracle
        _update(poolId, pool.reserve0, pool.reserve1);
        
        emit LiquidityAdded(poolId, msg.sender, amount0, amount1, liquidity);
    }

    /**
     * @dev Removes liquidity from a pool
     */
    function removeLiquidity(
        bytes32 poolId,
        uint256 liquidity,
        uint256 amount0Min,
        uint256 amount1Min
    ) external whenNotPaused nonReentrant returns (
        uint256 amount0,
        uint256 amount1
    ) {
        Pool storage pool = pools[poolId];
        require(pool.token0 != address(0), "Pool doesn't exist");
        
        LiquidityPosition storage position = liquidityPositions[poolId][msg.sender];
        require(position.liquidity >= liquidity, "Insufficient liquidity");
        
        // Calculate amounts to return
        amount0 = (liquidity * pool.reserve0) / pool.totalLiquidity;
        amount1 = (liquidity * pool.reserve1) / pool.totalLiquidity;
        
        require(amount0 >= amount0Min, "Amount0 too low");
        require(amount1 >= amount1Min, "Amount1 too low");
        
        // Update position and pool
        position.liquidity -= liquidity;
        pool.totalLiquidity -= liquidity;
        pool.reserve0 -= amount0;
        pool.reserve1 -= amount1;
        
        // Transfer tokens to user
        IERC20(pool.token0).safeTransfer(msg.sender, amount0);
        IERC20(pool.token1).safeTransfer(msg.sender, amount1);
        
        // Update price oracle
        _update(poolId, pool.reserve0, pool.reserve1);
        
        emit LiquidityRemoved(poolId, msg.sender, amount0, amount1, liquidity);
    }

    /**
     * @dev Swaps tokens
     */
    function swap(
        bytes32 poolId,
        address tokenIn,
        uint256 amountIn,
        uint256 amountOutMin
    ) external whenNotPaused nonReentrant returns (uint256 amountOut) {
        Pool storage pool = pools[poolId];
        require(pool.token0 != address(0), "Pool doesn't exist");
        require(tokenIn == pool.token0 || tokenIn == pool.token1, "Invalid token");
        
        bool isToken0 = tokenIn == pool.token0;
        
        // Calculate output amount
        amountOut = _getAmountOut(
            amountIn,
            isToken0 ? pool.reserve0 : pool.reserve1,
            isToken0 ? pool.reserve1 : pool.reserve0
        );
        
        require(amountOut >= amountOutMin, "Insufficient output amount");
        
        // Transfer input token from user
        IERC20(tokenIn).safeTransferFrom(msg.sender, address(this), amountIn);
        
        // Update reserves
        if (isToken0) {
            pool.reserve0 += amountIn;
            pool.reserve1 -= amountOut;
            IERC20(pool.token1).safeTransfer(msg.sender, amountOut);
        } else {
            pool.reserve1 += amountIn;
            pool.reserve0 -= amountOut;
            IERC20(pool.token0).safeTransfer(msg.sender, amountOut);
        }
        
        // Collect protocol fee if applicable
        if (protocolFee > 0) {
            uint256 feeAmount = (amountIn * protocolFee) / 10000;
            IERC20(tokenIn).safeTransfer(feeRecipient, feeAmount);
        }
        
        // Update price oracle
        _update(poolId, pool.reserve0, pool.reserve1);
        
        emit Swap(
            poolId,
            msg.sender,
            tokenIn,
            isToken0 ? pool.token1 : pool.token0,
            amountIn,
            amountOut
        );
    }

    /**
     * @dev Performs multi-hop swap
     */
    function multiSwap(
        bytes32[] calldata poolIds,
        address[] calldata path,
        uint256 amountIn,
        uint256 amountOutMin
    ) external whenNotPaused nonReentrant returns (uint256 amountOut) {
        require(poolIds.length == path.length - 1, "Invalid path");
        require(path.length >= 2, "Path too short");
        
        // Transfer initial token from user
        IERC20(path[0]).safeTransferFrom(msg.sender, address(this), amountIn);
        
        uint256 currentAmount = amountIn;
        
        for (uint256 i = 0; i < poolIds.length; i++) {
            Pool storage pool = pools[poolIds[i]];
            address tokenIn = path[i];
            address tokenOut = path[i + 1];
            
            // Perform swap
            currentAmount = _performSwap(pool, tokenIn, tokenOut, currentAmount);
        }
        
        require(currentAmount >= amountOutMin, "Insufficient output");
        
        // Transfer final token to user
        IERC20(path[path.length - 1]).safeTransfer(msg.sender, currentAmount);
        
        amountOut = currentAmount;
    }

    /**
     * @dev Gets the output amount for a given input
     */
    function getAmountOut(
        bytes32 poolId,
        address tokenIn,
        uint256 amountIn
    ) external view returns (uint256 amountOut) {
        Pool memory pool = pools[poolId];
        require(pool.token0 != address(0), "Pool doesn't exist");
        require(tokenIn == pool.token0 || tokenIn == pool.token1, "Invalid token");
        
        bool isToken0 = tokenIn == pool.token0;
        
        amountOut = _getAmountOut(
            amountIn,
            isToken0 ? pool.reserve0 : pool.reserve1,
            isToken0 ? pool.reserve1 : pool.reserve0
        );
    }

    /**
     * @dev Gets pool information
     */
    function getPoolInfo(bytes32 poolId) external view returns (
        address token0,
        address token1,
        uint256 reserve0,
        uint256 reserve1,
        uint256 totalLiquidity
    ) {
        Pool memory pool = pools[poolId];
        return (
            pool.token0,
            pool.token1,
            pool.reserve0,
            pool.reserve1,
            pool.totalLiquidity
        );
    }

    /**
     * @dev Updates swap and protocol fees
     */
    function updateFees(
        uint256 newSwapFee,
        uint256 newProtocolFee
    ) external onlyRole(FEE_MANAGER_ROLE) {
        require(newSwapFee <= 1000, "Swap fee too high"); // Max 10%
        require(newProtocolFee <= 100, "Protocol fee too high"); // Max 1%
        
        swapFee = newSwapFee;
        protocolFee = newProtocolFee;
        
        emit FeeUpdated(newSwapFee, newProtocolFee);
    }

    /**
     * @dev Calculates optimal amounts for liquidity
     */
    function _calculateOptimalAmounts(
        Pool memory pool,
        uint256 amount0Desired,
        uint256 amount1Desired,
        uint256 amount0Min,
        uint256 amount1Min
    ) private pure returns (uint256 amount0, uint256 amount1) {
        if (pool.reserve0 == 0 && pool.reserve1 == 0) {
            (amount0, amount1) = (amount0Desired, amount1Desired);
        } else {
            uint256 amount1Optimal = (amount0Desired * pool.reserve1) / pool.reserve0;
            if (amount1Optimal <= amount1Desired) {
                require(amount1Optimal >= amount1Min, "Amount1 insufficient");
                (amount0, amount1) = (amount0Desired, amount1Optimal);
            } else {
                uint256 amount0Optimal = (amount1Desired * pool.reserve0) / pool.reserve1;
                require(amount0Optimal >= amount0Min, "Amount0 insufficient");
                (amount0, amount1) = (amount0Optimal, amount1Desired);
            }
        }
    }

    /**
     * @dev Calculates output amount using constant product formula
     */
    function _getAmountOut(
        uint256 amountIn,
        uint256 reserveIn,
        uint256 reserveOut
    ) private view returns (uint256) {
        require(amountIn > 0, "Insufficient input amount");
        require(reserveIn > 0 && reserveOut > 0, "Insufficient liquidity");
        
        uint256 amountInWithFee = amountIn * (10000 - swapFee);
        uint256 numerator = amountInWithFee * reserveOut;
        uint256 denominator = (reserveIn * 10000) + amountInWithFee;
        
        return numerator / denominator;
    }

    /**
     * @dev Performs a single swap in a pool
     */
    function _performSwap(
        Pool storage pool,
        address tokenIn,
        address tokenOut,
        uint256 amountIn
    ) private returns (uint256) {
        bool isToken0 = tokenIn == pool.token0;
        require(
            (isToken0 && tokenOut == pool.token1) || (!isToken0 && tokenOut == pool.token0),
            "Invalid swap path"
        );
        
        uint256 amountOut = _getAmountOut(
            amountIn,
            isToken0 ? pool.reserve0 : pool.reserve1,
            isToken0 ? pool.reserve1 : pool.reserve0
        );
        
        // Update reserves
        if (isToken0) {
            pool.reserve0 += amountIn;
            pool.reserve1 -= amountOut;
        } else {
            pool.reserve1 += amountIn;
            pool.reserve0 -= amountOut;
        }
        
        return amountOut;
    }

    /**
     * @dev Updates price oracle
     */
    function _update(
        bytes32 poolId,
        uint256 reserve0,
        uint256 reserve1
    ) private {
        Pool storage pool = pools[poolId];
        
        uint32 blockTimestamp = uint32(block.timestamp % 2**32);
        uint32 timeElapsed = blockTimestamp - pool.blockTimestampLast;
        
        if (timeElapsed > 0 && reserve0 != 0 && reserve1 != 0) {
            pool.price0CumulativeLast += uint256(reserve1 / reserve0) * timeElapsed;
            pool.price1CumulativeLast += uint256(reserve0 / reserve1) * timeElapsed;
        }
        
        pool.reserve0 = reserve0;
        pool.reserve1 = reserve1;
        pool.blockTimestampLast = blockTimestamp;
        
        emit Sync(poolId, reserve0, reserve1);
    }

    /**
     * @dev Pauses the AMM
     */
    function pause() external onlyRole(OPERATOR_ROLE) {
        _pause();
    }

    /**
     * @dev Unpauses the AMM
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