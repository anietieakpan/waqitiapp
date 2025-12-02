import { ethers } from 'ethers';
import WalletConnectProvider from '@walletconnect/web3-provider';
import Web3Modal from 'web3modal';
import { toast } from 'react-toastify';
import secureStorage from '../../../shared/security/SecureStorageService';

// Supported networks
export const SUPPORTED_NETWORKS = {
  1: {
    name: 'Ethereum Mainnet',
    chainId: '0x1',
    rpcUrl: process.env.REACT_APP_ETH_RPC_URL || 'https://mainnet.infura.io/v3/',
    blockExplorer: 'https://etherscan.io',
    currency: {
      name: 'Ether',
      symbol: 'ETH',
      decimals: 18
    }
  },
  137: {
    name: 'Polygon',
    chainId: '0x89',
    rpcUrl: process.env.REACT_APP_POLYGON_RPC_URL || 'https://polygon-rpc.com',
    blockExplorer: 'https://polygonscan.com',
    currency: {
      name: 'MATIC',
      symbol: 'MATIC',
      decimals: 18
    }
  },
  56: {
    name: 'BSC',
    chainId: '0x38',
    rpcUrl: process.env.REACT_APP_BSC_RPC_URL || 'https://bsc-dataseed.binance.org',
    blockExplorer: 'https://bscscan.com',
    currency: {
      name: 'BNB',
      symbol: 'BNB',
      decimals: 18
    }
  },
  42161: {
    name: 'Arbitrum',
    chainId: '0xa4b1',
    rpcUrl: process.env.REACT_APP_ARBITRUM_RPC_URL || 'https://arb1.arbitrum.io/rpc',
    blockExplorer: 'https://arbiscan.io',
    currency: {
      name: 'Ether',
      symbol: 'ETH',
      decimals: 18
    }
  },
  10: {
    name: 'Optimism',
    chainId: '0xa',
    rpcUrl: process.env.REACT_APP_OPTIMISM_RPC_URL || 'https://mainnet.optimism.io',
    blockExplorer: 'https://optimistic.etherscan.io',
    currency: {
      name: 'Ether',
      symbol: 'ETH',
      decimals: 18
    }
  }
};

// Contract ABIs
const ERC20_ABI = [
  'function balanceOf(address owner) view returns (uint256)',
  'function decimals() view returns (uint8)',
  'function symbol() view returns (string)',
  'function transfer(address to, uint256 amount) returns (bool)',
  'function approve(address spender, uint256 amount) returns (bool)',
  'function allowance(address owner, address spender) view returns (uint256)'
];

const ERC721_ABI = [
  'function balanceOf(address owner) view returns (uint256)',
  'function ownerOf(uint256 tokenId) view returns (address)',
  'function safeTransferFrom(address from, address to, uint256 tokenId)',
  'function approve(address to, uint256 tokenId)',
  'function getApproved(uint256 tokenId) view returns (address)',
  'function setApprovalForAll(address operator, bool approved)',
  'function isApprovedForAll(address owner, address operator) view returns (bool)',
  'function tokenURI(uint256 tokenId) view returns (string)'
];

export interface Web3State {
  provider: ethers.providers.Web3Provider | null;
  signer: ethers.Signer | null;
  address: string | null;
  chainId: number | null;
  connected: boolean;
  connecting: boolean;
  error: string | null;
}

export interface TransactionRequest {
  to: string;
  value?: string;
  data?: string;
  gasLimit?: string;
  gasPrice?: string;
  nonce?: number;
}

export interface TokenBalance {
  token: string;
  symbol: string;
  balance: string;
  decimals: number;
  usdValue?: number;
}

class Web3Service {
  private web3Modal: Web3Modal | null = null;
  private provider: ethers.providers.Web3Provider | null = null;
  private signer: ethers.Signer | null = null;
  private address: string | null = null;
  private chainId: number | null = null;
  private listeners: Map<string, Set<Function>> = new Map();

  constructor() {
    this.initializeWeb3Modal();
  }

  private initializeWeb3Modal() {
    const providerOptions = {
      walletconnect: {
        package: WalletConnectProvider,
        options: {
          infuraId: process.env.REACT_APP_INFURA_ID,
          rpc: {
            1: process.env.REACT_APP_ETH_RPC_URL || '',
            137: process.env.REACT_APP_POLYGON_RPC_URL || '',
            56: process.env.REACT_APP_BSC_RPC_URL || '',
            42161: process.env.REACT_APP_ARBITRUM_RPC_URL || '',
            10: process.env.REACT_APP_OPTIMISM_RPC_URL || ''
          }
        }
      }
    };

    this.web3Modal = new Web3Modal({
      network: 'mainnet',
      cacheProvider: true,
      providerOptions,
      theme: {
        background: 'rgb(39, 49, 56)',
        main: 'rgb(199, 199, 199)',
        secondary: 'rgb(136, 136, 136)',
        border: 'rgba(195, 195, 195, 0.14)',
        hover: 'rgb(16, 26, 32)'
      }
    });
  }

  async connect(): Promise<Web3State> {
    try {
      if (!this.web3Modal) {
        throw new Error('Web3Modal not initialized');
      }

      const instance = await this.web3Modal.connect();
      const provider = new ethers.providers.Web3Provider(instance);
      const signer = provider.getSigner();
      const address = await signer.getAddress();
      const network = await provider.getNetwork();

      this.provider = provider;
      this.signer = signer;
      this.address = address;
      this.chainId = network.chainId;

      // Subscribe to provider events
      this.subscribeToProviderEvents(instance);

      // Store connection securely with encryption
      await secureStorage.setItem('web3_connected', 'true', {
        encrypt: true,
        httpOnly: true,
        secure: true,
        sameSite: 'strict',
        maxAge: 86400 // 24 hours
      });

      const state: Web3State = {
        provider: this.provider,
        signer: this.signer,
        address: this.address,
        chainId: this.chainId,
        connected: true,
        connecting: false,
        error: null
      };

      this.emit('connect', state);
      return state;

    } catch (error: any) {
      console.error('Failed to connect:', error);
      const state: Web3State = {
        provider: null,
        signer: null,
        address: null,
        chainId: null,
        connected: false,
        connecting: false,
        error: error.message
      };
      
      this.emit('error', error);
      return state;
    }
  }

  async disconnect(): Promise<void> {
    try {
      if (this.web3Modal) {
        await this.web3Modal.clearCachedProvider();
      }

      this.provider = null;
      this.signer = null;
      this.address = null;
      this.chainId = null;

      // Remove from secure storage
      await secureStorage.removeItem('web3_connected');

      this.emit('disconnect', null);

    } catch (error) {
      console.error('Failed to disconnect:', error);
      throw error;
    }
  }

  async switchNetwork(chainId: number): Promise<void> {
    try {
      const network = SUPPORTED_NETWORKS[chainId];
      if (!network) {
        throw new Error('Unsupported network');
      }

      const chainIdHex = network.chainId;

      try {
        // Try to switch to the network
        await (window as any).ethereum.request({
          method: 'wallet_switchEthereumChain',
          params: [{ chainId: chainIdHex }]
        });
      } catch (switchError: any) {
        // This error code indicates that the chain has not been added to MetaMask
        if (switchError.code === 4902) {
          // Try to add the network
          await (window as any).ethereum.request({
            method: 'wallet_addEthereumChain',
            params: [{
              chainId: chainIdHex,
              chainName: network.name,
              nativeCurrency: network.currency,
              rpcUrls: [network.rpcUrl],
              blockExplorerUrls: [network.blockExplorer]
            }]
          });
        } else {
          throw switchError;
        }
      }

      // Update provider after switch
      if (this.provider) {
        const newNetwork = await this.provider.getNetwork();
        this.chainId = newNetwork.chainId;
        this.emit('chainChanged', this.chainId);
      }

    } catch (error: any) {
      console.error('Failed to switch network:', error);
      toast.error(`Failed to switch network: ${error.message}`);
      throw error;
    }
  }

  async getBalance(address?: string): Promise<string> {
    try {
      if (!this.provider) {
        throw new Error('Provider not connected');
      }

      const targetAddress = address || this.address;
      if (!targetAddress) {
        throw new Error('No address provided');
      }

      const balance = await this.provider.getBalance(targetAddress);
      return ethers.utils.formatEther(balance);

    } catch (error) {
      console.error('Failed to get balance:', error);
      throw error;
    }
  }

  async getTokenBalance(tokenAddress: string, address?: string): Promise<TokenBalance> {
    try {
      if (!this.provider) {
        throw new Error('Provider not connected');
      }

      const targetAddress = address || this.address;
      if (!targetAddress) {
        throw new Error('No address provided');
      }

      const tokenContract = new ethers.Contract(tokenAddress, ERC20_ABI, this.provider);
      
      const [balance, decimals, symbol] = await Promise.all([
        tokenContract.balanceOf(targetAddress),
        tokenContract.decimals(),
        tokenContract.symbol()
      ]);

      return {
        token: tokenAddress,
        symbol,
        balance: ethers.utils.formatUnits(balance, decimals),
        decimals
      };

    } catch (error) {
      console.error('Failed to get token balance:', error);
      throw error;
    }
  }

  async sendTransaction(request: TransactionRequest): Promise<ethers.providers.TransactionResponse> {
    try {
      if (!this.signer) {
        throw new Error('Signer not connected');
      }

      // Prepare transaction
      const tx: ethers.providers.TransactionRequest = {
        to: request.to,
        value: request.value ? ethers.utils.parseEther(request.value) : undefined,
        data: request.data,
        gasLimit: request.gasLimit ? ethers.BigNumber.from(request.gasLimit) : undefined,
        gasPrice: request.gasPrice ? ethers.utils.parseUnits(request.gasPrice, 'gwei') : undefined,
        nonce: request.nonce
      };

      // Estimate gas if not provided
      if (!tx.gasLimit) {
        tx.gasLimit = await this.signer.estimateGas(tx);
      }

      // Send transaction
      const response = await this.signer.sendTransaction(tx);
      
      toast.info(`Transaction sent: ${response.hash}`);
      
      // Wait for confirmation
      const receipt = await response.wait();
      
      if (receipt.status === 1) {
        toast.success('Transaction confirmed!');
      } else {
        toast.error('Transaction failed');
      }

      return response;

    } catch (error: any) {
      console.error('Failed to send transaction:', error);
      toast.error(`Transaction failed: ${error.message}`);
      throw error;
    }
  }

  async signMessage(message: string): Promise<string> {
    try {
      if (!this.signer) {
        throw new Error('Signer not connected');
      }

      const signature = await this.signer.signMessage(message);
      return signature;

    } catch (error) {
      console.error('Failed to sign message:', error);
      throw error;
    }
  }

  async signTypedData(domain: any, types: any, value: any): Promise<string> {
    try {
      if (!this.signer) {
        throw new Error('Signer not connected');
      }

      const signature = await (this.signer as any)._signTypedData(domain, types, value);
      return signature;

    } catch (error) {
      console.error('Failed to sign typed data:', error);
      throw error;
    }
  }

  async approveToken(
    tokenAddress: string,
    spenderAddress: string,
    amount: string
  ): Promise<ethers.providers.TransactionResponse> {
    try {
      if (!this.signer) {
        throw new Error('Signer not connected');
      }

      const tokenContract = new ethers.Contract(tokenAddress, ERC20_ABI, this.signer);
      const decimals = await tokenContract.decimals();
      const amountInWei = ethers.utils.parseUnits(amount, decimals);

      const tx = await tokenContract.approve(spenderAddress, amountInWei);
      
      toast.info(`Approval transaction sent: ${tx.hash}`);
      
      await tx.wait();
      
      toast.success('Token approval confirmed!');
      
      return tx;

    } catch (error: any) {
      console.error('Failed to approve token:', error);
      toast.error(`Approval failed: ${error.message}`);
      throw error;
    }
  }

  async getAllowance(
    tokenAddress: string,
    ownerAddress: string,
    spenderAddress: string
  ): Promise<string> {
    try {
      if (!this.provider) {
        throw new Error('Provider not connected');
      }

      const tokenContract = new ethers.Contract(tokenAddress, ERC20_ABI, this.provider);
      const [allowance, decimals] = await Promise.all([
        tokenContract.allowance(ownerAddress, spenderAddress),
        tokenContract.decimals()
      ]);

      return ethers.utils.formatUnits(allowance, decimals);

    } catch (error) {
      console.error('Failed to get allowance:', error);
      throw error;
    }
  }

  async getNFTs(address?: string): Promise<any[]> {
    try {
      if (!this.provider) {
        throw new Error('Provider not connected');
      }

      const targetAddress = address || this.address;
      if (!targetAddress) {
        throw new Error('No address provided');
      }

      // This would typically call an NFT indexing service like OpenSea API, Alchemy NFT API, etc.
      // For now, returning placeholder
      return [];

    } catch (error) {
      console.error('Failed to get NFTs:', error);
      throw error;
    }
  }

  async transferNFT(
    contractAddress: string,
    tokenId: string,
    toAddress: string
  ): Promise<ethers.providers.TransactionResponse> {
    try {
      if (!this.signer || !this.address) {
        throw new Error('Signer not connected');
      }

      const nftContract = new ethers.Contract(contractAddress, ERC721_ABI, this.signer);
      
      const tx = await nftContract.safeTransferFrom(this.address, toAddress, tokenId);
      
      toast.info(`NFT transfer sent: ${tx.hash}`);
      
      await tx.wait();
      
      toast.success('NFT transferred successfully!');
      
      return tx;

    } catch (error: any) {
      console.error('Failed to transfer NFT:', error);
      toast.error(`NFT transfer failed: ${error.message}`);
      throw error;
    }
  }

  async getGasPrice(): Promise<string> {
    try {
      if (!this.provider) {
        throw new Error('Provider not connected');
      }

      const gasPrice = await this.provider.getGasPrice();
      return ethers.utils.formatUnits(gasPrice, 'gwei');

    } catch (error) {
      console.error('Failed to get gas price:', error);
      throw error;
    }
  }

  async estimateGas(tx: TransactionRequest): Promise<string> {
    try {
      if (!this.provider) {
        throw new Error('Provider not connected');
      }

      const transaction: ethers.providers.TransactionRequest = {
        to: tx.to,
        value: tx.value ? ethers.utils.parseEther(tx.value) : undefined,
        data: tx.data
      };

      const gasEstimate = await this.provider.estimateGas(transaction);
      return gasEstimate.toString();

    } catch (error) {
      console.error('Failed to estimate gas:', error);
      throw error;
    }
  }

  private subscribeToProviderEvents(provider: any) {
    if (!provider.on) {
      return;
    }

    provider.on('accountsChanged', async (accounts: string[]) => {
      if (accounts.length > 0) {
        this.address = accounts[0];
        this.emit('accountsChanged', accounts);
      } else {
        await this.disconnect();
      }
    });

    provider.on('chainChanged', (chainId: string) => {
      const newChainId = parseInt(chainId, 16);

      // Security: Validate chain before reload to prevent malicious redirects
      if (SUPPORTED_NETWORKS[newChainId]) {
        this.chainId = newChainId;
        this.emit('chainChanged', this.chainId);

        // Safe reload with confirmation
        if (confirm(`Network changed to ${SUPPORTED_NETWORKS[newChainId].name}. Reload page?`)) {
          window.location.reload();
        }
      } else {
        toast.error(`Unsupported network detected (Chain ID: ${newChainId})`);
        this.disconnect();
      }
    });

    provider.on('disconnect', () => {
      this.disconnect();
    });
  }

  // Event emitter methods
  on(event: string, callback: Function) {
    if (!this.listeners.has(event)) {
      this.listeners.set(event, new Set());
    }
    this.listeners.get(event)?.add(callback);
  }

  off(event: string, callback: Function) {
    this.listeners.get(event)?.delete(callback);
  }

  private emit(event: string, data: any) {
    this.listeners.get(event)?.forEach(callback => callback(data));
  }

  // Utility methods
  isConnected(): boolean {
    return this.address !== null && this.provider !== null;
  }

  getAddress(): string | null {
    return this.address;
  }

  getChainId(): number | null {
    return this.chainId;
  }

  getProvider(): ethers.providers.Web3Provider | null {
    return this.provider;
  }

  getSigner(): ethers.Signer | null {
    return this.signer;
  }

  formatEther(wei: string | ethers.BigNumber): string {
    return ethers.utils.formatEther(wei);
  }

  parseEther(ether: string): ethers.BigNumber {
    return ethers.utils.parseEther(ether);
  }

  formatUnits(value: string | ethers.BigNumber, decimals: number): string {
    return ethers.utils.formatUnits(value, decimals);
  }

  parseUnits(value: string, decimals: number): ethers.BigNumber {
    return ethers.utils.parseUnits(value, decimals);
  }

  isAddress(address: string): boolean {
    return ethers.utils.isAddress(address);
  }

  getContract(address: string, abi: any, signerOrProvider?: any): ethers.Contract {
    const provider = signerOrProvider || this.signer || this.provider;
    if (!provider) {
      throw new Error('No provider available');
    }
    return new ethers.Contract(address, abi, provider);
  }
}

// Singleton instance
const web3Service = new Web3Service();

// Auto-connect if previously connected (async check with secure storage)
if (typeof window !== 'undefined') {
  (async () => {
    try {
      const wasConnected = await secureStorage.getItem('web3_connected');
      if (wasConnected === 'true') {
        await web3Service.connect();
      }
    } catch (error) {
      console.error('Failed to check previous connection:', error);
    }
  })();
}

export default web3Service;