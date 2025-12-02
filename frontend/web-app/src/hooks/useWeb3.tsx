import React, { createContext, useContext, useState, useEffect, useCallback, ReactNode } from 'react';
import web3Service, { Web3State, TransactionRequest, TokenBalance, SUPPORTED_NETWORKS } from '../services/web3Service';
import { ethers } from 'ethers';
import { toast } from 'react-toastify';

interface Web3ContextValue extends Web3State {
  connect: () => Promise<void>;
  disconnect: () => Promise<void>;
  switchNetwork: (chainId: number) => Promise<void>;
  sendTransaction: (request: TransactionRequest) => Promise<ethers.providers.TransactionResponse>;
  signMessage: (message: string) => Promise<string>;
  getBalance: (address?: string) => Promise<string>;
  getTokenBalance: (tokenAddress: string, address?: string) => Promise<TokenBalance>;
  approveToken: (tokenAddress: string, spenderAddress: string, amount: string) => Promise<ethers.providers.TransactionResponse>;
  getNFTs: (address?: string) => Promise<any[]>;
  transferNFT: (contractAddress: string, tokenId: string, toAddress: string) => Promise<ethers.providers.TransactionResponse>;
  getGasPrice: () => Promise<string>;
  estimateGas: (tx: TransactionRequest) => Promise<string>;
  isAddress: (address: string) => boolean;
  formatEther: (wei: string | ethers.BigNumber) => string;
  parseEther: (ether: string) => ethers.BigNumber;
  networkName: string | null;
  explorerUrl: string | null;
  balance: string | null;
  ens: string | null;
}

const Web3Context = createContext<Web3ContextValue | null>(null);

interface Web3ProviderProps {
  children: ReactNode;
  autoConnect?: boolean;
}

export const Web3Provider: React.FC<Web3ProviderProps> = ({ children, autoConnect = true }) => {
  const [state, setState] = useState<Web3State>({
    provider: null,
    signer: null,
    address: null,
    chainId: null,
    connected: false,
    connecting: false,
    error: null
  });

  const [balance, setBalance] = useState<string | null>(null);
  const [ens, setEns] = useState<string | null>(null);
  const [networkName, setNetworkName] = useState<string | null>(null);
  const [explorerUrl, setExplorerUrl] = useState<string | null>(null);

  // Update network info when chain changes
  useEffect(() => {
    if (state.chainId && SUPPORTED_NETWORKS[state.chainId]) {
      const network = SUPPORTED_NETWORKS[state.chainId];
      setNetworkName(network.name);
      setExplorerUrl(network.blockExplorer);
    } else {
      setNetworkName(null);
      setExplorerUrl(null);
    }
  }, [state.chainId]);

  // Fetch balance when address changes
  useEffect(() => {
    const fetchBalance = async () => {
      if (state.address && state.provider) {
        try {
          const bal = await web3Service.getBalance(state.address);
          setBalance(bal);
        } catch (error) {
          console.error('Failed to fetch balance:', error);
          setBalance(null);
        }
      } else {
        setBalance(null);
      }
    };

    fetchBalance();
    
    // Set up interval to refresh balance
    const interval = setInterval(fetchBalance, 15000); // Every 15 seconds
    
    return () => clearInterval(interval);
  }, [state.address, state.provider, state.chainId]);

  // Resolve ENS name
  useEffect(() => {
    const resolveENS = async () => {
      if (state.address && state.provider && state.chainId === 1) { // Only on mainnet
        try {
          const ensName = await state.provider.lookupAddress(state.address);
          setEns(ensName);
        } catch (error) {
          console.error('Failed to resolve ENS:', error);
          setEns(null);
        }
      } else {
        setEns(null);
      }
    };

    resolveENS();
  }, [state.address, state.provider, state.chainId]);

  // Connect wallet
  const connect = useCallback(async () => {
    setState(prev => ({ ...prev, connecting: true, error: null }));
    
    try {
      const newState = await web3Service.connect();
      setState({
        ...newState,
        connecting: false
      });
      
      toast.success(`Connected to ${newState.address?.substring(0, 6)}...${newState.address?.substring(38)}`);
    } catch (error: any) {
      setState(prev => ({
        ...prev,
        connecting: false,
        error: error.message
      }));
      
      toast.error('Failed to connect wallet');
    }
  }, []);

  // Disconnect wallet
  const disconnect = useCallback(async () => {
    try {
      await web3Service.disconnect();
      setState({
        provider: null,
        signer: null,
        address: null,
        chainId: null,
        connected: false,
        connecting: false,
        error: null
      });
      setBalance(null);
      setEns(null);
      
      toast.info('Wallet disconnected');
    } catch (error: any) {
      console.error('Failed to disconnect:', error);
      toast.error('Failed to disconnect wallet');
    }
  }, []);

  // Switch network
  const switchNetwork = useCallback(async (chainId: number) => {
    try {
      await web3Service.switchNetwork(chainId);
      
      // Update state after network switch
      if (web3Service.getProvider()) {
        const network = await web3Service.getProvider()!.getNetwork();
        setState(prev => ({
          ...prev,
          chainId: network.chainId
        }));
      }
      
      toast.success(`Switched to ${SUPPORTED_NETWORKS[chainId]?.name || 'Unknown Network'}`);
    } catch (error: any) {
      console.error('Failed to switch network:', error);
      toast.error('Failed to switch network');
      throw error;
    }
  }, []);

  // Subscribe to Web3 events
  useEffect(() => {
    const handleConnect = (newState: Web3State) => {
      setState(newState);
    };

    const handleDisconnect = () => {
      setState({
        provider: null,
        signer: null,
        address: null,
        chainId: null,
        connected: false,
        connecting: false,
        error: null
      });
    };

    const handleAccountsChanged = (accounts: string[]) => {
      if (accounts.length > 0) {
        setState(prev => ({ ...prev, address: accounts[0] }));
      } else {
        handleDisconnect();
      }
    };

    const handleChainChanged = (chainId: number) => {
      setState(prev => ({ ...prev, chainId }));
    };

    web3Service.on('connect', handleConnect);
    web3Service.on('disconnect', handleDisconnect);
    web3Service.on('accountsChanged', handleAccountsChanged);
    web3Service.on('chainChanged', handleChainChanged);

    return () => {
      web3Service.off('connect', handleConnect);
      web3Service.off('disconnect', handleDisconnect);
      web3Service.off('accountsChanged', handleAccountsChanged);
      web3Service.off('chainChanged', handleChainChanged);
    };
  }, []);

  // Auto-connect on mount if enabled
  useEffect(() => {
    if (autoConnect && localStorage.getItem('web3_connected') === 'true') {
      connect();
    }
  }, [autoConnect, connect]);

  // Proxy methods to web3Service
  const sendTransaction = useCallback(async (request: TransactionRequest) => {
    return web3Service.sendTransaction(request);
  }, []);

  const signMessage = useCallback(async (message: string) => {
    return web3Service.signMessage(message);
  }, []);

  const getBalance = useCallback(async (address?: string) => {
    return web3Service.getBalance(address);
  }, []);

  const getTokenBalance = useCallback(async (tokenAddress: string, address?: string) => {
    return web3Service.getTokenBalance(tokenAddress, address);
  }, []);

  const approveToken = useCallback(async (tokenAddress: string, spenderAddress: string, amount: string) => {
    return web3Service.approveToken(tokenAddress, spenderAddress, amount);
  }, []);

  const getNFTs = useCallback(async (address?: string) => {
    return web3Service.getNFTs(address);
  }, []);

  const transferNFT = useCallback(async (contractAddress: string, tokenId: string, toAddress: string) => {
    return web3Service.transferNFT(contractAddress, tokenId, toAddress);
  }, []);

  const getGasPrice = useCallback(async () => {
    return web3Service.getGasPrice();
  }, []);

  const estimateGas = useCallback(async (tx: TransactionRequest) => {
    return web3Service.estimateGas(tx);
  }, []);

  const isAddress = useCallback((address: string) => {
    return web3Service.isAddress(address);
  }, []);

  const formatEther = useCallback((wei: string | ethers.BigNumber) => {
    return web3Service.formatEther(wei);
  }, []);

  const parseEther = useCallback((ether: string) => {
    return web3Service.parseEther(ether);
  }, []);

  const value: Web3ContextValue = {
    ...state,
    connect,
    disconnect,
    switchNetwork,
    sendTransaction,
    signMessage,
    getBalance,
    getTokenBalance,
    approveToken,
    getNFTs,
    transferNFT,
    getGasPrice,
    estimateGas,
    isAddress,
    formatEther,
    parseEther,
    networkName,
    explorerUrl,
    balance,
    ens
  };

  return <Web3Context.Provider value={value}>{children}</Web3Context.Provider>;
};

export const useWeb3 = (): Web3ContextValue => {
  const context = useContext(Web3Context);
  if (!context) {
    throw new Error('useWeb3 must be used within Web3Provider');
  }
  return context;
};

// Utility hook for checking connection status
export const useWeb3Connected = (): boolean => {
  const { connected } = useWeb3();
  return connected;
};

// Utility hook for getting current address
export const useWeb3Address = (): string | null => {
  const { address } = useWeb3();
  return address;
};

// Utility hook for getting current network
export const useWeb3Network = (): { chainId: number | null; name: string | null } => {
  const { chainId, networkName } = useWeb3();
  return { chainId, name: networkName };
};

// Utility hook for handling transactions with loading state
export const useWeb3Transaction = () => {
  const { sendTransaction } = useWeb3();
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const send = useCallback(async (request: TransactionRequest) => {
    setLoading(true);
    setError(null);
    
    try {
      const tx = await sendTransaction(request);
      setLoading(false);
      return tx;
    } catch (err: any) {
      setError(err.message);
      setLoading(false);
      throw err;
    }
  }, [sendTransaction]);

  return { send, loading, error };
};

// Utility hook for token operations
export const useWeb3Token = (tokenAddress: string) => {
  const { getTokenBalance, approveToken } = useWeb3();
  const [balance, setBalance] = useState<TokenBalance | null>(null);
  const [loading, setLoading] = useState(false);

  const fetchBalance = useCallback(async (address?: string) => {
    if (!tokenAddress) return;
    
    setLoading(true);
    try {
      const bal = await getTokenBalance(tokenAddress, address);
      setBalance(bal);
    } catch (error) {
      console.error('Failed to fetch token balance:', error);
      setBalance(null);
    } finally {
      setLoading(false);
    }
  }, [tokenAddress, getTokenBalance]);

  const approve = useCallback(async (spender: string, amount: string) => {
    return approveToken(tokenAddress, spender, amount);
  }, [tokenAddress, approveToken]);

  useEffect(() => {
    fetchBalance();
  }, [fetchBalance]);

  return { balance, loading, fetchBalance, approve };
};