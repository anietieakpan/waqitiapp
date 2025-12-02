import { HardhatUserConfig } from "hardhat/config";
import "@nomicfoundation/hardhat-toolbox";
import "@openzeppelin/hardhat-upgrades";
import "hardhat-deploy";
import "hardhat-gas-reporter";
import "solidity-coverage";
import * as dotenv from "dotenv";

dotenv.config();

/**
 * CRITICAL SECURITY FIX P0 (CVSS 10.0):
 *
 * Private keys MUST be provided via environment variables for production networks.
 * We NEVER use hardcoded fallback keys as they create catastrophic security vulnerabilities.
 *
 * For local development ONLY, we generate a random key if not provided.
 * Production deployments MUST fail fast if PRIVATE_KEY is missing.
 *
 * @see https://docs.waqiti.com/security/key-management
 */

// Validate PRIVATE_KEY for production networks
function getPrivateKey(): string {
  const privateKey = process.env.PRIVATE_KEY;

  // Production/Testnet: PRIVATE_KEY is REQUIRED
  const network = process.env.HARDHAT_NETWORK || process.env.NETWORK || 'hardhat';
  const isProductionNetwork = !['hardhat', 'localhost'].includes(network);

  if (isProductionNetwork && !privateKey) {
    throw new Error(
      `CRITICAL SECURITY ERROR: PRIVATE_KEY environment variable is required for network "${network}". ` +
      `NEVER use hardcoded keys. Set PRIVATE_KEY in your .env file or environment variables. ` +
      `For security best practices, see: https://docs.waqiti.com/security/key-management`
    );
  }

  // Local development: Generate random key if not provided (safe for local testing)
  if (!privateKey) {
    const crypto = require('crypto');
    const randomKey = '0x' + crypto.randomBytes(32).toString('hex');
    console.warn(
      '\n⚠️  WARNING: PRIVATE_KEY not set. Using random key for LOCAL DEVELOPMENT ONLY.\n' +
      '   Generated key:', randomKey, '\n' +
      '   This is SAFE for local hardhat network but NEVER use for production.\n' +
      '   Set PRIVATE_KEY in .env file for testnet/mainnet deployments.\n'
    );
    return randomKey;
  }

  // Validate private key format
  if (!privateKey.match(/^0x[0-9a-fA-F]{64}$/)) {
    throw new Error(
      `INVALID PRIVATE_KEY format. Expected 66-character hex string starting with '0x'. ` +
      `Got: ${privateKey.substring(0, 10)}... (length: ${privateKey.length})`
    );
  }

  return privateKey;
}

const PRIVATE_KEY = getPrivateKey();
const ETHERSCAN_API_KEY = process.env.ETHERSCAN_API_KEY || "";
const POLYGONSCAN_API_KEY = process.env.POLYGONSCAN_API_KEY || "";
const BSCSCAN_API_KEY = process.env.BSCSCAN_API_KEY || "";
const ARBISCAN_API_KEY = process.env.ARBISCAN_API_KEY || "";
const OPTIMISM_API_KEY = process.env.OPTIMISM_API_KEY || "";

const config: HardhatUserConfig = {
  solidity: {
    version: "0.8.20",
    settings: {
      optimizer: {
        enabled: true,
        runs: 200,
      },
      viaIR: true,
    },
  },
  networks: {
    hardhat: {
      chainId: 31337,
      forking: {
        url: process.env.ETH_MAINNET_RPC || "",
        enabled: false,
      },
    },
    localhost: {
      url: "http://127.0.0.1:8545",
    },
    ethereum: {
      url: process.env.ETH_MAINNET_RPC || "",
      chainId: 1,
      accounts: [PRIVATE_KEY],
    },
    goerli: {
      url: process.env.GOERLI_RPC || "",
      chainId: 5,
      accounts: [PRIVATE_KEY],
    },
    polygon: {
      url: process.env.POLYGON_RPC || "",
      chainId: 137,
      accounts: [PRIVATE_KEY],
    },
    polygonMumbai: {
      url: process.env.MUMBAI_RPC || "",
      chainId: 80001,
      accounts: [PRIVATE_KEY],
    },
    bsc: {
      url: process.env.BSC_RPC || "",
      chainId: 56,
      accounts: [PRIVATE_KEY],
    },
    bscTestnet: {
      url: process.env.BSC_TESTNET_RPC || "",
      chainId: 97,
      accounts: [PRIVATE_KEY],
    },
    arbitrum: {
      url: process.env.ARBITRUM_RPC || "",
      chainId: 42161,
      accounts: [PRIVATE_KEY],
    },
    arbitrumGoerli: {
      url: process.env.ARBITRUM_GOERLI_RPC || "",
      chainId: 421613,
      accounts: [PRIVATE_KEY],
    },
    optimism: {
      url: process.env.OPTIMISM_RPC || "",
      chainId: 10,
      accounts: [PRIVATE_KEY],
    },
    optimismGoerli: {
      url: process.env.OPTIMISM_GOERLI_RPC || "",
      chainId: 420,
      accounts: [PRIVATE_KEY],
    },
  },
  etherscan: {
    apiKey: {
      mainnet: ETHERSCAN_API_KEY,
      goerli: ETHERSCAN_API_KEY,
      polygon: POLYGONSCAN_API_KEY,
      polygonMumbai: POLYGONSCAN_API_KEY,
      bsc: BSCSCAN_API_KEY,
      bscTestnet: BSCSCAN_API_KEY,
      arbitrumOne: ARBISCAN_API_KEY,
      arbitrumGoerli: ARBISCAN_API_KEY,
      optimisticEthereum: OPTIMISM_API_KEY,
      optimisticGoerli: OPTIMISM_API_KEY,
    },
  },
  gasReporter: {
    enabled: process.env.REPORT_GAS === "true",
    currency: "USD",
    coinmarketcap: process.env.COINMARKETCAP_API_KEY,
    outputFile: "gas-report.txt",
    noColors: true,
  },
  namedAccounts: {
    deployer: {
      default: 0,
    },
    treasury: {
      default: 1,
    },
  },
  paths: {
    sources: "./contracts",
    tests: "./test",
    cache: "./cache",
    artifacts: "./artifacts",
    deploy: "./deploy",
  },
  mocha: {
    timeout: 200000,
  },
};

export default config;