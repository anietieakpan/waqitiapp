/**
 * Crypto Wallet Screen
 * Main cryptocurrency wallet interface with portfolio overview and quick actions
 */
import React, { useState, useEffect, useCallback } from 'react';
import {
  View,
  ScrollView,
  Text,
  TouchableOpacity,
  Alert,
  RefreshControl,
  Dimensions,
  StyleSheet,
} from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { LinearGradient } from 'expo-linear-gradient';
import { Ionicons, MaterialIcons } from '@expo/vector-icons';
import { useFocusEffect } from '@react-navigation/native';
import { useAuth } from '../../contexts/AuthContext';
import { useCrypto } from '../../contexts/CryptoContext';
import { CryptoWalletCard } from '../../components/crypto/CryptoWalletCard';
import { CryptoPriceChart } from '../../components/crypto/CryptoPriceChart';
import { CryptoQuickActions } from '../../components/crypto/CryptoQuickActions';
import { LoadingSpinner } from '../../components/common/LoadingSpinner';
import { ErrorBoundary } from '../../components/common/ErrorBoundary';
import { theme } from '../../theme';
import { CryptoWallet, CryptoCurrency } from '../../types/crypto';

const { width } = Dimensions.get('window');

interface CryptoWalletScreenProps {
  navigation: any;
}

export const CryptoWalletScreen: React.FC<CryptoWalletScreenProps> = ({ navigation }) => {
  const { user } = useAuth();
  const {
    wallets,
    totalPortfolioValue,
    portfolioChange24h,
    isLoading,
    error,
    refreshWallets,
    createWallet,
  } = useCrypto();

  const [refreshing, setRefreshing] = useState(false);
  const [selectedTimeframe, setSelectedTimeframe] = useState('24h');
  const [showCreateWallet, setShowCreateWallet] = useState(false);

  // Refresh data when screen comes into focus
  useFocusEffect(
    useCallback(() => {
      refreshWallets();
    }, [refreshWallets])
  );

  const handleRefresh = async () => {
    setRefreshing(true);
    try {
      await refreshWallets();
    } catch (error) {
      console.error('Failed to refresh wallets:', error);
    } finally {
      setRefreshing(false);
    }
  };

  const handleWalletPress = (wallet: CryptoWallet) => {
    navigation.navigate('CryptoWalletDetails', { walletId: wallet.walletId });
  };

  const handleQuickAction = (action: string) => {
    switch (action) {
      case 'buy':
        navigation.navigate('CryptoBuy');
        break;
      case 'sell':
        navigation.navigate('CryptoSell');
        break;
      case 'send':
        navigation.navigate('CryptoSend');
        break;
      case 'receive':
        navigation.navigate('CryptoReceive');
        break;
      case 'convert':
        navigation.navigate('CryptoConvert');
        break;
      case 'scan':
        navigation.navigate('QRScanner', { type: 'crypto' });
        break;
    }
  };

  const handleCreateWallet = async (currency: CryptoCurrency) => {
    try {
      await createWallet(currency);
      setShowCreateWallet(false);
      Alert.alert('Success', `${currency} wallet created successfully!`);
    } catch (error) {
      Alert.alert('Error', 'Failed to create wallet. Please try again.');
      console.error('Failed to create wallet:', error);
    }
  };

  const renderPortfolioHeader = () => (
    <LinearGradient
      colors={[theme.colors.primary, theme.colors.primaryDark]}
      style={styles.portfolioHeader}
    >
      <View style={styles.portfolioContent}>
        <Text style={styles.portfolioLabel}>Total Portfolio Value</Text>
        <Text style={styles.portfolioValue}>
          ${totalPortfolioValue.toLocaleString('en-US', {
            minimumFractionDigits: 2,
            maximumFractionDigits: 2,
          })}
        </Text>
        <View style={styles.portfolioChange}>
          <Ionicons
            name={portfolioChange24h >= 0 ? 'trending-up' : 'trending-down'}
            size={16}
            color={portfolioChange24h >= 0 ? theme.colors.success : theme.colors.error}
          />
          <Text
            style={[
              styles.portfolioChangeText,
              {
                color: portfolioChange24h >= 0 ? theme.colors.success : theme.colors.error,
              },
            ]}
          >
            {portfolioChange24h >= 0 ? '+' : ''}
            {portfolioChange24h.toFixed(2)}% (24h)
          </Text>
        </View>
      </View>
    </LinearGradient>
  );

  const renderWalletsList = () => (
    <View style={styles.walletsSection}>
      <View style={styles.sectionHeader}>
        <Text style={styles.sectionTitle}>Your Wallets</Text>
        <TouchableOpacity
          style={styles.addButton}
          onPress={() => setShowCreateWallet(true)}
        >
          <Ionicons name="add" size={24} color={theme.colors.primary} />
        </TouchableOpacity>
      </View>

      {wallets.length === 0 ? (
        <View style={styles.emptyState}>
          <MaterialIcons name="account-balance-wallet" size={64} color={theme.colors.textLight} />
          <Text style={styles.emptyStateTitle}>No Crypto Wallets</Text>
          <Text style={styles.emptyStateText}>
            Create your first cryptocurrency wallet to start trading
          </Text>
          <TouchableOpacity
            style={styles.createWalletButton}
            onPress={() => setShowCreateWallet(true)}
          >
            <Text style={styles.createWalletButtonText}>Create Wallet</Text>
          </TouchableOpacity>
        </View>
      ) : (
        wallets.map((wallet) => (
          <CryptoWalletCard
            key={wallet.walletId}
            wallet={wallet}
            onPress={() => handleWalletPress(wallet)}
          />
        ))
      )}
    </View>
  );

  const renderPriceChart = () => (
    <View style={styles.chartSection}>
      <View style={styles.chartHeader}>
        <Text style={styles.sectionTitle}>Market Overview</Text>
        <View style={styles.timeframeSelector}>
          {['24h', '7d', '30d'].map((timeframe) => (
            <TouchableOpacity
              key={timeframe}
              style={[
                styles.timeframeButton,
                selectedTimeframe === timeframe && styles.timeframeButtonActive,
              ]}
              onPress={() => setSelectedTimeframe(timeframe)}
            >
              <Text
                style={[
                  styles.timeframeButtonText,
                  selectedTimeframe === timeframe && styles.timeframeButtonTextActive,
                ]}
              >
                {timeframe}
              </Text>
            </TouchableOpacity>
          ))}
        </View>
      </View>
      <CryptoPriceChart timeframe={selectedTimeframe} />
    </View>
  );

  if (isLoading && wallets.length === 0) {
    return (
      <SafeAreaView style={styles.container}>
        <LoadingSpinner message="Loading your crypto portfolio..." />
      </SafeAreaView>
    );
  }

  if (error) {
    return (
      <SafeAreaView style={styles.container}>
        <View style={styles.errorContainer}>
          <MaterialIcons name="error-outline" size={64} color={theme.colors.error} />
          <Text style={styles.errorTitle}>Unable to Load Portfolio</Text>
          <Text style={styles.errorText}>{error}</Text>
          <TouchableOpacity style={styles.retryButton} onPress={handleRefresh}>
            <Text style={styles.retryButtonText}>Try Again</Text>
          </TouchableOpacity>
        </View>
      </SafeAreaView>
    );
  }

  return (
    <ErrorBoundary>
      <SafeAreaView style={styles.container}>
        <ScrollView
          style={styles.scrollView}
          refreshControl={
            <RefreshControl refreshing={refreshing} onRefresh={handleRefresh} />
          }
          showsVerticalScrollIndicator={false}
        >
          {renderPortfolioHeader()}
          
          <CryptoQuickActions onActionPress={handleQuickAction} />
          
          {wallets.length > 0 && renderPriceChart()}
          
          {renderWalletsList()}
        </ScrollView>

        {/* Create Wallet Modal would go here */}
      </SafeAreaView>
    </ErrorBoundary>
  );
};

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: theme.colors.background,
  },
  scrollView: {
    flex: 1,
  },
  portfolioHeader: {
    paddingHorizontal: 20,
    paddingVertical: 30,
    borderBottomLeftRadius: 20,
    borderBottomRightRadius: 20,
  },
  portfolioContent: {
    alignItems: 'center',
  },
  portfolioLabel: {
    fontSize: 14,
    color: theme.colors.white,
    opacity: 0.8,
    marginBottom: 8,
  },
  portfolioValue: {
    fontSize: 32,
    fontWeight: 'bold',
    color: theme.colors.white,
    marginBottom: 8,
  },
  portfolioChange: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 4,
  },
  portfolioChangeText: {
    fontSize: 14,
    fontWeight: '600',
  },
  walletsSection: {
    padding: 20,
  },
  sectionHeader: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginBottom: 16,
  },
  sectionTitle: {
    fontSize: 20,
    fontWeight: 'bold',
    color: theme.colors.text,
  },
  addButton: {
    width: 40,
    height: 40,
    borderRadius: 20,
    backgroundColor: theme.colors.surface,
    justifyContent: 'center',
    alignItems: 'center',
    elevation: 2,
    shadowColor: theme.colors.black,
    shadowOffset: { width: 0, height: 2 },
    shadowOpacity: 0.1,
    shadowRadius: 4,
  },
  emptyState: {
    alignItems: 'center',
    paddingVertical: 40,
  },
  emptyStateTitle: {
    fontSize: 18,
    fontWeight: 'bold',
    color: theme.colors.text,
    marginTop: 16,
    marginBottom: 8,
  },
  emptyStateText: {
    fontSize: 14,
    color: theme.colors.textLight,
    textAlign: 'center',
    marginBottom: 24,
    paddingHorizontal: 20,
  },
  createWalletButton: {
    backgroundColor: theme.colors.primary,
    paddingHorizontal: 24,
    paddingVertical: 12,
    borderRadius: 8,
  },
  createWalletButtonText: {
    color: theme.colors.white,
    fontSize: 16,
    fontWeight: '600',
  },
  chartSection: {
    padding: 20,
    paddingTop: 0,
  },
  chartHeader: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginBottom: 16,
  },
  timeframeSelector: {
    flexDirection: 'row',
    backgroundColor: theme.colors.surface,
    borderRadius: 8,
    padding: 4,
  },
  timeframeButton: {
    paddingHorizontal: 12,
    paddingVertical: 6,
    borderRadius: 6,
  },
  timeframeButtonActive: {
    backgroundColor: theme.colors.primary,
  },
  timeframeButtonText: {
    fontSize: 12,
    color: theme.colors.textLight,
    fontWeight: '500',
  },
  timeframeButtonTextActive: {
    color: theme.colors.white,
  },
  errorContainer: {
    flex: 1,
    justifyContent: 'center',
    alignItems: 'center',
    padding: 20,
  },
  errorTitle: {
    fontSize: 18,
    fontWeight: 'bold',
    color: theme.colors.text,
    marginTop: 16,
    marginBottom: 8,
  },
  errorText: {
    fontSize: 14,
    color: theme.colors.textLight,
    textAlign: 'center',
    marginBottom: 24,
  },
  retryButton: {
    backgroundColor: theme.colors.primary,
    paddingHorizontal: 24,
    paddingVertical: 12,
    borderRadius: 8,
  },
  retryButtonText: {
    color: theme.colors.white,
    fontSize: 16,
    fontWeight: '600',
  },
});