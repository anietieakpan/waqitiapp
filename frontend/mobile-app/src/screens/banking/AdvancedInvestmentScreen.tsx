import React, { useState, useEffect } from 'react';
import {
  View,
  Text,
  ScrollView,
  TouchableOpacity,
  StyleSheet,
  Dimensions,
  Alert,
  RefreshControl,
} from 'react-native';
import { LineChart, PieChart, BarChart } from 'react-native-chart-kit';
import { investmentService } from '../../services/InvestmentService';
import { usePerformanceMonitor } from '../../hooks/usePerformanceMonitor';

interface Portfolio {
  totalValue: number;
  totalGain: number;
  totalGainPercent: number;
  assets: PortfolioAsset[];
  allocation: AssetAllocation[];
  performance: PerformanceData[];
}

interface PortfolioAsset {
  symbol: string;
  name: string;
  quantity: number;
  currentPrice: number;
  marketValue: number;
  gain: number;
  gainPercent: number;
  assetType: 'STOCK' | 'CRYPTO' | 'BOND' | 'ETF' | 'MUTUAL_FUND';
}

interface AssetAllocation {
  category: string;
  value: number;
  percentage: number;
  color: string;
}

interface PerformanceData {
  date: string;
  value: number;
}

const screenWidth = Dimensions.get('window').width;

export const AdvancedInvestmentScreen: React.FC = () => {
  const performanceMonitor = usePerformanceMonitor('AdvancedInvestment');
  
  const [portfolio, setPortfolio] = useState<Portfolio | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [selectedPeriod, setSelectedPeriod] = useState('1M');
  const [viewMode, setViewMode] = useState<'overview' | 'performance' | 'allocation'>('overview');

  const periods = ['1D', '1W', '1M', '3M', '6M', '1Y', 'ALL'];

  useEffect(() => {
    loadPortfolioData();
  }, [selectedPeriod]);

  const loadPortfolioData = async () => {
    try {
      performanceMonitor.startTimer('portfolio_load');
      
      const response = await investmentService.getPortfolioSummary({
        period: selectedPeriod,
        includePerformance: true,
        includeAllocation: true,
      });

      if (response.success) {
        setPortfolio(response.data);
        performanceMonitor.recordEvent('portfolio_loaded');
      } else {
        throw new Error(response.errorMessage || 'Failed to load portfolio');
      }
    } catch (error) {
      performanceMonitor.recordError('portfolio_load_error', error.toString());
      Alert.alert('Error', 'Failed to load investment portfolio');
    } finally {
      setIsLoading(false);
      setRefreshing(false);
      performanceMonitor.endTimer('portfolio_load');
    }
  };

  const handleRefresh = () => {
    setRefreshing(true);
    loadPortfolioData();
  };

  const formatCurrency = (amount: number): string => {
    return new Intl.NumberFormat('en-US', {
      style: 'currency',
      currency: 'USD',
      minimumFractionDigits: 2,
    }).format(amount);
  };

  const formatPercent = (percent: number): string => {
    return `${percent >= 0 ? '+' : ''}${percent.toFixed(2)}%`;
  };

  const renderOverview = () => (
    <View style={styles.overviewContainer}>
      {/* Portfolio Value */}
      <View style={styles.valueCard}>
        <Text style={styles.portfolioLabel}>Total Portfolio Value</Text>
        <Text style={styles.portfolioValue}>
          {formatCurrency(portfolio?.totalValue || 0)}
        </Text>
        <View style={styles.gainContainer}>
          <Text style={[
            styles.portfolioGain,
            { color: (portfolio?.totalGain || 0) >= 0 ? '#22c55e' : '#ef4444' }
          ]}>
            {formatCurrency(portfolio?.totalGain || 0)} ({formatPercent(portfolio?.totalGainPercent || 0)})
          </Text>
        </View>
      </View>

      {/* Asset Breakdown */}
      <View style={styles.sectionContainer}>
        <Text style={styles.sectionTitle}>Holdings</Text>
        {portfolio?.assets.map((asset, index) => (
          <TouchableOpacity
            key={asset.symbol}
            style={styles.assetItem}
            onPress={() => navigateToAssetDetail(asset)}
          >
            <View style={styles.assetInfo}>
              <View style={styles.assetHeader}>
                <Text style={styles.assetSymbol}>{asset.symbol}</Text>
                <Text style={styles.assetType}>{asset.assetType}</Text>
              </View>
              <Text style={styles.assetName}>{asset.name}</Text>
              <Text style={styles.assetQuantity}>
                {asset.quantity} shares @ {formatCurrency(asset.currentPrice)}
              </Text>
            </View>
            <View style={styles.assetValues}>
              <Text style={styles.assetValue}>
                {formatCurrency(asset.marketValue)}
              </Text>
              <Text style={[
                styles.assetGain,
                { color: asset.gain >= 0 ? '#22c55e' : '#ef4444' }
              ]}>
                {formatPercent(asset.gainPercent)}
              </Text>
            </View>
          </TouchableOpacity>
        ))}
      </View>

      {/* Quick Actions */}
      <View style={styles.actionsContainer}>
        <TouchableOpacity style={styles.actionButton} onPress={() => navigateToBuyScreen()}>
          <Text style={styles.actionIcon}>📈</Text>
          <Text style={styles.actionText}>Buy</Text>
        </TouchableOpacity>
        <TouchableOpacity style={styles.actionButton} onPress={() => navigateToSellScreen()}>
          <Text style={styles.actionIcon}>📉</Text>
          <Text style={styles.actionText}>Sell</Text>
        </TouchableOpacity>
        <TouchableOpacity style={styles.actionButton} onPress={() => navigateToResearch()}>
          <Text style={styles.actionIcon}>🔍</Text>
          <Text style={styles.actionText}>Research</Text>
        </TouchableOpacity>
        <TouchableOpacity style={styles.actionButton} onPress={() => navigateToWatchlist()}>
          <Text style={styles.actionIcon}>👁️</Text>
          <Text style={styles.actionText}>Watchlist</Text>
        </TouchableOpacity>
      </View>
    </View>
  );

  const renderPerformance = () => (
    <View style={styles.performanceContainer}>
      <Text style={styles.sectionTitle}>Performance</Text>
      
      {/* Time Period Selector */}
      <ScrollView 
        horizontal 
        showsHorizontalScrollIndicator={false}
        style={styles.periodSelector}
      >
        {periods.map((period) => (
          <TouchableOpacity
            key={period}
            style={[
              styles.periodButton,
              selectedPeriod === period && styles.selectedPeriodButton
            ]}
            onPress={() => setSelectedPeriod(period)}
          >
            <Text style={[
              styles.periodButtonText,
              selectedPeriod === period && styles.selectedPeriodButtonText
            ]}>
              {period}
            </Text>
          </TouchableOpacity>
        ))}
      </ScrollView>

      {/* Performance Chart */}
      {portfolio?.performance && (
        <LineChart
          data={{
            labels: portfolio.performance.slice(-6).map(p => 
              new Date(p.date).toLocaleDateString('en-US', { month: 'short', day: 'numeric' })
            ),
            datasets: [{
              data: portfolio.performance.slice(-6).map(p => p.value),
              color: (opacity = 1) => `rgba(49, 130, 206, ${opacity})`,
              strokeWidth: 2,
            }],
          }}
          width={screenWidth - 32}
          height={220}
          yAxisLabel="$"
          yAxisInterval={1}
          chartConfig={{
            backgroundColor: '#ffffff',
            backgroundGradientFrom: '#ffffff',
            backgroundGradientTo: '#ffffff',
            decimalPlaces: 0,
            color: (opacity = 1) => `rgba(0, 0, 0, ${opacity})`,
            labelColor: (opacity = 1) => `rgba(0, 0, 0, ${opacity})`,
            style: { borderRadius: 16 },
            propsForDots: {
              r: '4',
              strokeWidth: '2',
              stroke: '#3182ce',
            },
          }}
          bezier
          style={styles.chart}
        />
      )}

      {/* Performance Metrics */}
      <View style={styles.metricsContainer}>
        <View style={styles.metricItem}>
          <Text style={styles.metricLabel}>Best Day</Text>
          <Text style={[styles.metricValue, { color: '#22c55e' }]}>+2.34%</Text>
        </View>
        <View style={styles.metricItem}>
          <Text style={styles.metricLabel}>Worst Day</Text>
          <Text style={[styles.metricValue, { color: '#ef4444' }]}>-1.89%</Text>
        </View>
        <View style={styles.metricItem}>
          <Text style={styles.metricLabel}>Volatility</Text>
          <Text style={styles.metricValue}>12.45%</Text>
        </View>
        <View style={styles.metricItem}>
          <Text style={styles.metricLabel}>Sharpe Ratio</Text>
          <Text style={styles.metricValue}>1.23</Text>
        </View>
      </View>
    </View>
  );

  const renderAllocation = () => (
    <View style={styles.allocationContainer}>
      <Text style={styles.sectionTitle}>Asset Allocation</Text>
      
      {/* Pie Chart */}
      {portfolio?.allocation && (
        <PieChart
          data={portfolio.allocation.map(item => ({
            name: item.category,
            population: item.percentage,
            color: item.color,
            legendFontColor: '#374151',
            legendFontSize: 12,
          }))}
          width={screenWidth - 32}
          height={220}
          chartConfig={{
            color: (opacity = 1) => `rgba(0, 0, 0, ${opacity})`,
          }}
          accessor="population"
          backgroundColor="transparent"
          paddingLeft="15"
          absolute
          style={styles.chart}
        />
      )}

      {/* Allocation Details */}
      <View style={styles.allocationList}>
        {portfolio?.allocation.map((item, index) => (
          <View key={index} style={styles.allocationItem}>
            <View style={styles.allocationInfo}>
              <View style={[styles.colorDot, { backgroundColor: item.color }]} />
              <Text style={styles.allocationCategory}>{item.category}</Text>
            </View>
            <View style={styles.allocationValues}>
              <Text style={styles.allocationValue}>
                {formatCurrency(item.value)}
              </Text>
              <Text style={styles.allocationPercent}>
                {item.percentage.toFixed(1)}%
              </Text>
            </View>
          </View>
        ))}
      </View>

      {/* Rebalancing Recommendation */}
      <View style={styles.recommendationCard}>
        <Text style={styles.recommendationTitle}>💡 Rebalancing Suggestion</Text>
        <Text style={styles.recommendationText}>
          Consider reducing your tech exposure by 5% and increasing your bond allocation 
          to maintain optimal risk/return balance.
        </Text>
        <TouchableOpacity style={styles.rebalanceButton}>
          <Text style={styles.rebalanceButtonText}>Auto Rebalance</Text>
        </TouchableOpacity>
      </View>
    </View>
  );

  const navigateToAssetDetail = (asset: PortfolioAsset) => {
    // Navigation implementation
  };

  const navigateToBuyScreen = () => {
    // Navigation implementation
  };

  const navigateToSellScreen = () => {
    // Navigation implementation
  };

  const navigateToResearch = () => {
    // Navigation implementation
  };

  const navigateToWatchlist = () => {
    // Navigation implementation
  };

  if (isLoading) {
    return (
      <View style={styles.loadingContainer}>
        <Text>Loading portfolio...</Text>
      </View>
    );
  }

  return (
    <ScrollView
      style={styles.container}
      refreshControl={<RefreshControl refreshing={refreshing} onRefresh={handleRefresh} />}
    >
      {/* Tab Navigation */}
      <View style={styles.tabContainer}>
        {['overview', 'performance', 'allocation'].map((tab) => (
          <TouchableOpacity
            key={tab}
            style={[
              styles.tabButton,
              viewMode === tab && styles.activeTabButton
            ]}
            onPress={() => setViewMode(tab as any)}
          >
            <Text style={[
              styles.tabButtonText,
              viewMode === tab && styles.activeTabButtonText
            ]}>
              {tab.charAt(0).toUpperCase() + tab.slice(1)}
            </Text>
          </TouchableOpacity>
        ))}
      </View>

      {/* Content */}
      {viewMode === 'overview' && renderOverview()}
      {viewMode === 'performance' && renderPerformance()}
      {viewMode === 'allocation' && renderAllocation()}
    </ScrollView>
  );
};

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#f8fafc',
  },
  loadingContainer: {
    flex: 1,
    justifyContent: 'center',
    alignItems: 'center',
  },
  tabContainer: {
    flexDirection: 'row',
    backgroundColor: '#ffffff',
    marginHorizontal: 16,
    marginTop: 16,
    borderRadius: 8,
    padding: 4,
  },
  tabButton: {
    flex: 1,
    paddingVertical: 12,
    alignItems: 'center',
    borderRadius: 6,
  },
  activeTabButton: {
    backgroundColor: '#3182ce',
  },
  tabButtonText: {
    fontSize: 16,
    fontWeight: '500',
    color: '#6b7280',
  },
  activeTabButtonText: {
    color: '#ffffff',
  },
  overviewContainer: {
    padding: 16,
  },
  valueCard: {
    backgroundColor: '#ffffff',
    padding: 24,
    borderRadius: 12,
    marginBottom: 16,
    alignItems: 'center',
  },
  portfolioLabel: {
    fontSize: 16,
    color: '#6b7280',
    marginBottom: 8,
  },
  portfolioValue: {
    fontSize: 36,
    fontWeight: 'bold',
    color: '#1a202c',
  },
  gainContainer: {
    marginTop: 8,
  },
  portfolioGain: {
    fontSize: 18,
    fontWeight: '600',
  },
  sectionContainer: {
    backgroundColor: '#ffffff',
    borderRadius: 12,
    padding: 16,
    marginBottom: 16,
  },
  sectionTitle: {
    fontSize: 20,
    fontWeight: 'bold',
    color: '#1a202c',
    marginBottom: 16,
  },
  assetItem: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    paddingVertical: 12,
    borderBottomWidth: 1,
    borderBottomColor: '#f1f5f9',
  },
  assetInfo: {
    flex: 1,
  },
  assetHeader: {
    flexDirection: 'row',
    alignItems: 'center',
    marginBottom: 4,
  },
  assetSymbol: {
    fontSize: 16,
    fontWeight: 'bold',
    color: '#1a202c',
    marginRight: 8,
  },
  assetType: {
    fontSize: 12,
    color: '#6b7280',
    backgroundColor: '#f1f5f9',
    paddingHorizontal: 8,
    paddingVertical: 2,
    borderRadius: 4,
  },
  assetName: {
    fontSize: 14,
    color: '#6b7280',
    marginBottom: 2,
  },
  assetQuantity: {
    fontSize: 12,
    color: '#9ca3af',
  },
  assetValues: {
    alignItems: 'flex-end',
  },
  assetValue: {
    fontSize: 16,
    fontWeight: '600',
    color: '#1a202c',
    marginBottom: 2,
  },
  assetGain: {
    fontSize: 14,
    fontWeight: '500',
  },
  actionsContainer: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    marginTop: 8,
  },
  actionButton: {
    flex: 1,
    backgroundColor: '#ffffff',
    padding: 16,
    borderRadius: 12,
    alignItems: 'center',
    marginHorizontal: 4,
  },
  actionIcon: {
    fontSize: 24,
    marginBottom: 8,
  },
  actionText: {
    fontSize: 14,
    fontWeight: '600',
    color: '#1a202c',
  },
  performanceContainer: {
    padding: 16,
  },
  periodSelector: {
    marginBottom: 16,
  },
  periodButton: {
    paddingHorizontal: 16,
    paddingVertical: 8,
    marginRight: 8,
    backgroundColor: '#f1f5f9',
    borderRadius: 20,
  },
  selectedPeriodButton: {
    backgroundColor: '#3182ce',
  },
  periodButtonText: {
    fontSize: 14,
    color: '#6b7280',
  },
  selectedPeriodButtonText: {
    color: '#ffffff',
  },
  chart: {
    marginVertical: 8,
    borderRadius: 16,
  },
  metricsContainer: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    justifyContent: 'space-between',
    marginTop: 16,
  },
  metricItem: {
    width: '48%',
    backgroundColor: '#ffffff',
    padding: 16,
    borderRadius: 8,
    marginBottom: 8,
  },
  metricLabel: {
    fontSize: 14,
    color: '#6b7280',
    marginBottom: 4,
  },
  metricValue: {
    fontSize: 18,
    fontWeight: 'bold',
    color: '#1a202c',
  },
  allocationContainer: {
    padding: 16,
  },
  allocationList: {
    backgroundColor: '#ffffff',
    borderRadius: 12,
    padding: 16,
    marginTop: 16,
  },
  allocationItem: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    paddingVertical: 12,
    borderBottomWidth: 1,
    borderBottomColor: '#f1f5f9',
  },
  allocationInfo: {
    flexDirection: 'row',
    alignItems: 'center',
    flex: 1,
  },
  colorDot: {
    width: 12,
    height: 12,
    borderRadius: 6,
    marginRight: 12,
  },
  allocationCategory: {
    fontSize: 16,
    color: '#1a202c',
    fontWeight: '500',
  },
  allocationValues: {
    alignItems: 'flex-end',
  },
  allocationValue: {
    fontSize: 16,
    fontWeight: '600',
    color: '#1a202c',
  },
  allocationPercent: {
    fontSize: 14,
    color: '#6b7280',
  },
  recommendationCard: {
    backgroundColor: '#fef3c7',
    padding: 16,
    borderRadius: 12,
    marginTop: 16,
  },
  recommendationTitle: {
    fontSize: 16,
    fontWeight: 'bold',
    color: '#92400e',
    marginBottom: 8,
  },
  recommendationText: {
    fontSize: 14,
    color: '#92400e',
    marginBottom: 12,
    lineHeight: 20,
  },
  rebalanceButton: {
    backgroundColor: '#f59e0b',
    paddingHorizontal: 16,
    paddingVertical: 8,
    borderRadius: 6,
    alignSelf: 'flex-start',
  },
  rebalanceButtonText: {
    color: '#ffffff',
    fontWeight: '600',
  },
});