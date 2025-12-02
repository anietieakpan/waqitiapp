import React, { useState, useEffect } from 'react';
import { useWeb3 } from '../../hooks/useWeb3';
import { 
  FaRocket, 
  FaShieldAlt, 
  FaBolt, 
  FaLayerGroup,
  FaClock,
  FaDollarSign,
  FaCheckCircle,
  FaExclamationTriangle
} from 'react-icons/fa';
import './Layer2Selector.css';

interface Layer2Option {
  id: string;
  name: string;
  description: string;
  icon: React.ReactNode;
  finality: string;
  cost: string;
  throughput: string;
  privacy: 'Low' | 'Medium' | 'High';
  recommended: boolean;
  available: boolean;
}

interface Layer2SelectorProps {
  amount: string;
  transactionType: 'transfer' | 'defi' | 'nft' | 'micropayment';
  onSelect: (solution: string) => void;
  selectedSolution?: string;
}

const LAYER2_OPTIONS: Layer2Option[] = [
  {
    id: 'optimistic',
    name: 'Optimistic Rollup',
    description: 'Best for DeFi interactions and general transfers',
    icon: <FaRocket />,
    finality: '1-7 days',
    cost: '~$0.50',
    throughput: '2000 TPS',
    privacy: 'Low',
    recommended: true,
    available: true
  },
  {
    id: 'zk',
    name: 'ZK Rollup',
    description: 'Privacy-focused with cryptographic proofs',
    icon: <FaShieldAlt />,
    finality: '10-30 min',
    cost: '~$1.00',
    throughput: '2000 TPS',
    privacy: 'High',
    recommended: false,
    available: true
  },
  {
    id: 'channel',
    name: 'State Channel',
    description: 'Instant payments for frequent partners',
    icon: <FaBolt />,
    finality: 'Instant',
    cost: 'Free*',
    throughput: 'Unlimited',
    privacy: 'Medium',
    recommended: false,
    available: false
  },
  {
    id: 'plasma',
    name: 'Plasma Chain',
    description: 'High throughput for micropayments',
    icon: <FaLayerGroup />,
    finality: '10 min',
    cost: '~$0.10',
    throughput: '65000 TPS',
    privacy: 'Low',
    recommended: false,
    available: true
  }
];

const Layer2Selector: React.FC<Layer2SelectorProps> = ({
  amount,
  transactionType,
  onSelect,
  selectedSolution
}) => {
  const { connected, chainId } = useWeb3();
  const [options, setOptions] = useState<Layer2Option[]>(LAYER2_OPTIONS);
  const [loading, setLoading] = useState(false);
  const [metrics, setMetrics] = useState<Record<string, any>>({});

  useEffect(() => {
    if (connected) {
      updateRecommendations();
      fetchMetrics();
    }
  }, [amount, transactionType, connected, chainId]);

  const updateRecommendations = () => {
    const updatedOptions = LAYER2_OPTIONS.map(option => {
      let recommended = false;
      let available = option.available;

      // Update recommendations based on transaction characteristics
      if (transactionType === 'micropayment' && parseFloat(amount) < 0.1) {
        recommended = option.id === 'plasma' || option.id === 'channel';
      } else if (transactionType === 'defi') {
        recommended = option.id === 'optimistic';
      } else if (transactionType === 'nft') {
        recommended = option.id === 'optimistic' || option.id === 'zk';
      } else {
        recommended = option.id === 'optimistic';
      }

      // Check channel availability
      if (option.id === 'channel') {
        // In real implementation, would check if user has active channels
        available = false;
      }

      return { ...option, recommended, available };
    });

    setOptions(updatedOptions);
  };

  const fetchMetrics = async () => {
    setLoading(true);
    try {
      // Simulate fetching real-time metrics
      const mockMetrics = {
        optimistic: {
          currentCost: '$0.45',
          avgFinality: '2.5 hours',
          congestion: 'Low'
        },
        zk: {
          currentCost: '$0.95',
          avgFinality: '25 min',
          congestion: 'Medium'
        },
        plasma: {
          currentCost: '$0.08',
          avgFinality: '12 min',
          congestion: 'Low'
        }
      };
      
      setMetrics(mockMetrics);
    } catch (error) {
      console.error('Failed to fetch Layer 2 metrics:', error);
    } finally {
      setLoading(false);
    }
  };

  const handleOptionSelect = (optionId: string) => {
    const option = options.find(opt => opt.id === optionId);
    if (option?.available) {
      onSelect(optionId);
    }
  };

  const getOptionClass = (option: Layer2Option) => {
    let className = 'layer2-option';
    if (!option.available) className += ' disabled';
    if (option.recommended) className += ' recommended';
    if (selectedSolution === option.id) className += ' selected';
    return className;
  };

  const getPrivacyColor = (privacy: string) => {
    switch (privacy) {
      case 'High': return 'success';
      case 'Medium': return 'warning';
      case 'Low': return 'danger';
      default: return 'secondary';
    }
  };

  if (!connected) {
    return (
      <div className="layer2-selector disconnected">
        <div className="connect-prompt">
          <FaExclamationTriangle />
          <span>Connect your wallet to access Layer 2 solutions</span>
        </div>
      </div>
    );
  }

  return (
    <div className="layer2-selector">
      <div className="selector-header">
        <h3>Choose Layer 2 Solution</h3>
        <p>Optimize your transaction for speed, cost, and privacy</p>
      </div>

      {loading && (
        <div className="loading-metrics">
          <span>Loading real-time metrics...</span>
        </div>
      )}

      <div className="layer2-options">
        {options.map((option) => (
          <div
            key={option.id}
            className={getOptionClass(option)}
            onClick={() => handleOptionSelect(option.id)}
          >
            <div className="option-header">
              <div className="option-icon">{option.icon}</div>
              <div className="option-title">
                <h4>{option.name}</h4>
                {option.recommended && (
                  <span className="recommended-badge">Recommended</span>
                )}
              </div>
              {selectedSolution === option.id && (
                <FaCheckCircle className="selected-icon" />
              )}
            </div>

            <p className="option-description">{option.description}</p>

            <div className="option-metrics">
              <div className="metric">
                <FaClock className="metric-icon" />
                <div className="metric-info">
                  <span className="metric-label">Finality</span>
                  <span className="metric-value">
                    {metrics[option.id]?.avgFinality || option.finality}
                  </span>
                </div>
              </div>

              <div className="metric">
                <FaDollarSign className="metric-icon" />
                <div className="metric-info">
                  <span className="metric-label">Cost</span>
                  <span className="metric-value">
                    {metrics[option.id]?.currentCost || option.cost}
                  </span>
                </div>
              </div>

              <div className="metric">
                <span className="metric-label">Throughput</span>
                <span className="metric-value">{option.throughput}</span>
              </div>

              <div className="metric">
                <span className="metric-label">Privacy</span>
                <span className={`privacy-badge ${getPrivacyColor(option.privacy)}`}>
                  {option.privacy}
                </span>
              </div>
            </div>

            {metrics[option.id]?.congestion && (
              <div className="congestion-indicator">
                <span className={`congestion ${metrics[option.id].congestion.toLowerCase()}`}>
                  Network: {metrics[option.id].congestion}
                </span>
              </div>
            )}

            {!option.available && (
              <div className="unavailable-overlay">
                <span>Coming Soon</span>
              </div>
            )}
          </div>
        ))}
      </div>

      <div className="selector-footer">
        <div className="cost-comparison">
          <h4>Layer 1 vs Layer 2 Savings</h4>
          <div className="savings-stats">
            <div className="stat">
              <span className="label">L1 Cost:</span>
              <span className="value">~$25.00</span>
            </div>
            <div className="stat">
              <span className="label">L2 Average:</span>
              <span className="value success">~$0.65</span>
            </div>
            <div className="stat">
              <span className="label">Savings:</span>
              <span className="value success">97%</span>
            </div>
          </div>
        </div>

        <div className="security-note">
          <p>
            <strong>Security:</strong> All Layer 2 solutions inherit Ethereum's security 
            with additional optimizations for speed and cost.
          </p>
        </div>
      </div>
    </div>
  );
};

export default Layer2Selector;