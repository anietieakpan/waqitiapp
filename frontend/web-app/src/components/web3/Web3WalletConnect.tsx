import React, { useState } from 'react';
import { useWeb3 } from '../../hooks/useWeb3';
import { SUPPORTED_NETWORKS } from '../../services/web3Service';
import { 
  FaWallet, 
  FaEthereum, 
  FaCopy, 
  FaExternalLinkAlt, 
  FaSignOutAlt,
  FaExchangeAlt,
  FaCheckCircle,
  FaTimesCircle
} from 'react-icons/fa';
import { toast } from 'react-toastify';
import './Web3WalletConnect.css';

interface NetworkSelectorProps {
  currentChainId: number | null;
  onSwitch: (chainId: number) => void;
}

const NetworkSelector: React.FC<NetworkSelectorProps> = ({ currentChainId, onSwitch }) => {
  const [isOpen, setIsOpen] = useState(false);

  const currentNetwork = currentChainId ? SUPPORTED_NETWORKS[currentChainId] : null;

  return (
    <div className="network-selector">
      <button 
        className="network-button"
        onClick={() => setIsOpen(!isOpen)}
      >
        <span className={`network-indicator ${currentNetwork ? 'connected' : 'disconnected'}`}></span>
        <span>{currentNetwork?.name || 'Select Network'}</span>
        <FaExchangeAlt />
      </button>

      {isOpen && (
        <div className="network-dropdown">
          {Object.entries(SUPPORTED_NETWORKS).map(([chainId, network]) => (
            <button
              key={chainId}
              className={`network-option ${currentChainId === parseInt(chainId) ? 'active' : ''}`}
              onClick={() => {
                onSwitch(parseInt(chainId));
                setIsOpen(false);
              }}
            >
              <span className="network-name">{network.name}</span>
              {currentChainId === parseInt(chainId) && <FaCheckCircle className="check-icon" />}
            </button>
          ))}
        </div>
      )}
    </div>
  );
};

interface AddressDisplayProps {
  address: string;
  ens: string | null;
  balance: string | null;
  explorerUrl: string | null;
}

const AddressDisplay: React.FC<AddressDisplayProps> = ({ address, ens, balance, explorerUrl }) => {
  const [copied, setCopied] = useState(false);

  const copyAddress = () => {
    navigator.clipboard.writeText(address);
    setCopied(true);
    toast.success('Address copied!');
    setTimeout(() => setCopied(false), 2000);
  };

  const formatAddress = (addr: string) => {
    return `${addr.substring(0, 6)}...${addr.substring(38)}`;
  };

  return (
    <div className="address-display">
      <div className="address-info">
        <div className="address-main">
          <span className="ens-name">{ens || formatAddress(address)}</span>
          {ens && <span className="address-short">{formatAddress(address)}</span>}
        </div>
        {balance && (
          <div className="balance">
            <FaEthereum className="eth-icon" />
            <span>{parseFloat(balance).toFixed(4)} ETH</span>
          </div>
        )}
      </div>
      <div className="address-actions">
        <button 
          className="icon-button"
          onClick={copyAddress}
          title="Copy address"
        >
          {copied ? <FaCheckCircle className="success" /> : <FaCopy />}
        </button>
        {explorerUrl && (
          <a 
            href={`${explorerUrl}/address/${address}`}
            target="_blank"
            rel="noopener noreferrer"
            className="icon-button"
            title="View on explorer"
          >
            <FaExternalLinkAlt />
          </a>
        )}
      </div>
    </div>
  );
};

const Web3WalletConnect: React.FC = () => {
  const {
    connected,
    connecting,
    address,
    chainId,
    balance,
    ens,
    networkName,
    explorerUrl,
    error,
    connect,
    disconnect,
    switchNetwork
  } = useWeb3();

  const [showDetails, setShowDetails] = useState(false);

  const handleConnect = async () => {
    try {
      await connect();
    } catch (err) {
      console.error('Connection failed:', err);
    }
  };

  const handleDisconnect = async () => {
    try {
      await disconnect();
      setShowDetails(false);
    } catch (err) {
      console.error('Disconnect failed:', err);
    }
  };

  const handleNetworkSwitch = async (newChainId: number) => {
    try {
      await switchNetwork(newChainId);
    } catch (err) {
      console.error('Network switch failed:', err);
    }
  };

  if (!connected) {
    return (
      <div className="web3-wallet-connect">
        <button 
          className="connect-button"
          onClick={handleConnect}
          disabled={connecting}
        >
          <FaWallet />
          <span>{connecting ? 'Connecting...' : 'Connect Wallet'}</span>
        </button>
        {error && (
          <div className="error-message">
            <FaTimesCircle />
            <span>{error}</span>
          </div>
        )}
      </div>
    );
  }

  return (
    <div className="web3-wallet-connect connected">
      <div className="wallet-header">
        <NetworkSelector 
          currentChainId={chainId}
          onSwitch={handleNetworkSwitch}
        />
        
        <button 
          className="wallet-button"
          onClick={() => setShowDetails(!showDetails)}
        >
          <div className="wallet-status">
            <span className="status-indicator"></span>
            <FaWallet />
          </div>
          <span className="address-text">
            {ens || `${address?.substring(0, 6)}...${address?.substring(38)}`}
          </span>
        </button>
      </div>

      {showDetails && (
        <div className="wallet-details">
          {address && (
            <AddressDisplay 
              address={address}
              ens={ens}
              balance={balance}
              explorerUrl={explorerUrl}
            />
          )}
          
          <div className="wallet-info">
            <div className="info-row">
              <span className="label">Network:</span>
              <span className="value">{networkName || 'Unknown'}</span>
            </div>
            <div className="info-row">
              <span className="label">Chain ID:</span>
              <span className="value">{chainId}</span>
            </div>
          </div>

          <button 
            className="disconnect-button"
            onClick={handleDisconnect}
          >
            <FaSignOutAlt />
            <span>Disconnect</span>
          </button>
        </div>
      )}
    </div>
  );
};

export default Web3WalletConnect;