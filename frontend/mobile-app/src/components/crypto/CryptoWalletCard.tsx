/**
 * Crypto Wallet Card Component
 * Displays individual cryptocurrency wallet information in a card format
 */
import React from 'react';
import { View, Text, TouchableOpacity, StyleSheet } from 'react-native';
import { LinearGradient } from 'expo-linear-gradient';
import { Ionicons } from '@expo/vector-icons';
import { theme } from '../../theme';
import { CryptoWallet, CryptoCurrency } from '../../types/crypto';

interface CryptoWalletCardProps {
  wallet: CryptoWallet;
  onPress: () => void;
  showBalance?: boolean;
}

export const CryptoWalletCard: React.FC<CryptoWalletCardProps> = ({
  wallet,
  onPress,
  showBalance = true,
}) => {
  const getCurrencyIcon = (currency: CryptoCurrency): string => {
    switch (currency) {
      case CryptoCurrency.BITCOIN:
        return 'logo-bitcoin';
      case CryptoCurrency.ETHEREUM:
        return 'logo-ethereum';
      case CryptoCurrency.LITECOIN:
        return 'logo-bitcoin'; // Using bitcoin icon as placeholder
      case CryptoCurrency.USDC:
      case CryptoCurrency.USDT:
        return 'logo-usd';
      default:
        return 'wallet';
    }
  };

  const getCurrencyColors = (currency: CryptoCurrency): string[] => {
    switch (currency) {
      case CryptoCurrency.BITCOIN:
        return ['#f7931a', '#ff9500'];
      case CryptoCurrency.ETHEREUM:
        return ['#627eea', '#4c6ef5'];
      case CryptoCurrency.LITECOIN:
        return ['#bfbbbb', '#a5a5a5'];
      case CryptoCurrency.USDC:
        return ['#2775ca', '#1a64c7'];
      case CryptoCurrency.USDT:
        return ['#26a17b', '#1a7f64'];
      default:
        return [theme.colors.primary, theme.colors.primaryDark];
    }
  };

  const getCurrencyName = (currency: CryptoCurrency): string => {
    switch (currency) {
      case CryptoCurrency.BITCOIN:
        return 'Bitcoin';
      case CryptoCurrency.ETHEREUM:
        return 'Ethereum';
      case CryptoCurrency.LITECOIN:
        return 'Litecoin';
      case CryptoCurrency.USDC:
        return 'USD Coin';
      case CryptoCurrency.USDT:
        return 'Tether';
      default:
        return currency;
    }
  };

  const formatBalance = (balance: number): string => {
    if (balance >= 1) {
      return balance.toFixed(4);
    } else if (balance >= 0.0001) {
      return balance.toFixed(6);
    } else {
      return balance.toFixed(8);
    }
  };

  const formatAddress = (address: string): string => {
    if (address.length <= 12) return address;
    return `${address.slice(0, 6)}...${address.slice(-6)}`;
  };

  const getChangeIndicator = () => {
    // This would be calculated based on 24h price change
    const change = Math.random() * 10 - 5; // Placeholder
    const isPositive = change >= 0;
    
    return (
      <View style={styles.changeContainer}>
        <Ionicons
          name={isPositive ? 'trending-up' : 'trending-down'}
          size={12}
          color={isPositive ? theme.colors.success : theme.colors.error}
        />
        <Text
          style={[
            styles.changeText,
            { color: isPositive ? theme.colors.success : theme.colors.error },
          ]}
        >
          {isPositive ? '+' : ''}{change.toFixed(2)}%
        </Text>
      </View>
    );
  };

  return (
    <TouchableOpacity
      style={styles.container}
      onPress={onPress}
      activeOpacity={0.8}
    >
      <LinearGradient
        colors={getCurrencyColors(wallet.currency)}
        style={styles.gradient}
        start={{ x: 0, y: 0 }}
        end={{ x: 1, y: 1 }}
      >
        <View style={styles.header}>
          <View style={styles.currencyInfo}>
            <View style={styles.iconContainer}>
              <Ionicons
                name={getCurrencyIcon(wallet.currency) as any}
                size={24}
                color={theme.colors.white}
              />
            </View>
            <View style={styles.currencyDetails}>
              <Text style={styles.currencyName}>
                {getCurrencyName(wallet.currency)}
              </Text>
              <Text style={styles.currencySymbol}>
                {wallet.currency}
              </Text>
            </View>
          </View>
          {getChangeIndicator()}
        </View>

        {showBalance && (
          <View style={styles.balanceSection}>
            <Text style={styles.balanceLabel}>Available Balance</Text>
            <Text style={styles.balanceValue}>
              {formatBalance(wallet.availableBalance)} {wallet.currency}
            </Text>
            <Text style={styles.usdValue}>
              ≈ ${wallet.usdValue.toLocaleString('en-US', {
                minimumFractionDigits: 2,
                maximumFractionDigits: 2,
              })}
            </Text>
          </View>
        )}

        <View style={styles.footer}>
          <View style={styles.addressContainer}>
            <Text style={styles.addressLabel}>Address</Text>
            <Text style={styles.addressValue}>
              {formatAddress(wallet.multiSigAddress)}
            </Text>
          </View>
          <Ionicons
            name="chevron-forward"
            size={20}
            color={theme.colors.white}
            style={styles.chevron}
          />
        </View>

        {wallet.pendingBalance > 0 && (
          <View style={styles.pendingIndicator}>
            <Ionicons name="time" size={12} color={theme.colors.warning} />
            <Text style={styles.pendingText}>
              {formatBalance(wallet.pendingBalance)} pending
            </Text>
          </View>
        )}
      </LinearGradient>
    </TouchableOpacity>
  );
};

const styles = StyleSheet.create({
  container: {
    marginBottom: 16,
    borderRadius: 16,
    elevation: 4,
    shadowColor: theme.colors.black,
    shadowOffset: { width: 0, height: 2 },
    shadowOpacity: 0.15,
    shadowRadius: 8,
  },
  gradient: {
    borderRadius: 16,
    padding: 20,
    minHeight: 140,
  },
  header: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'flex-start',
    marginBottom: 16,
  },
  currencyInfo: {
    flexDirection: 'row',
    alignItems: 'center',
    flex: 1,
  },
  iconContainer: {
    width: 40,
    height: 40,
    borderRadius: 20,
    backgroundColor: 'rgba(255, 255, 255, 0.2)',
    justifyContent: 'center',
    alignItems: 'center',
    marginRight: 12,
  },
  currencyDetails: {
    flex: 1,
  },
  currencyName: {
    fontSize: 16,
    fontWeight: 'bold',
    color: theme.colors.white,
    marginBottom: 2,
  },
  currencySymbol: {
    fontSize: 12,
    color: theme.colors.white,
    opacity: 0.8,
  },
  changeContainer: {
    flexDirection: 'row',
    alignItems: 'center',
    backgroundColor: 'rgba(255, 255, 255, 0.2)',
    paddingHorizontal: 8,
    paddingVertical: 4,
    borderRadius: 8,
    gap: 4,
  },
  changeText: {
    fontSize: 12,
    fontWeight: '600',
  },
  balanceSection: {
    marginBottom: 16,
  },
  balanceLabel: {
    fontSize: 12,
    color: theme.colors.white,
    opacity: 0.8,
    marginBottom: 4,
  },
  balanceValue: {
    fontSize: 20,
    fontWeight: 'bold',
    color: theme.colors.white,
    marginBottom: 4,
  },
  usdValue: {
    fontSize: 14,
    color: theme.colors.white,
    opacity: 0.9,
  },
  footer: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
  },
  addressContainer: {
    flex: 1,
  },
  addressLabel: {
    fontSize: 10,
    color: theme.colors.white,
    opacity: 0.7,
    marginBottom: 2,
  },
  addressValue: {
    fontSize: 12,
    color: theme.colors.white,
    fontFamily: Platform.OS === 'ios' ? 'Menlo' : 'monospace',
  },
  chevron: {
    opacity: 0.8,
  },
  pendingIndicator: {
    position: 'absolute',
    top: 16,
    right: 16,
    flexDirection: 'row',
    alignItems: 'center',
    backgroundColor: 'rgba(255, 255, 255, 0.9)',
    paddingHorizontal: 8,
    paddingVertical: 4,
    borderRadius: 8,
    gap: 4,
  },
  pendingText: {
    fontSize: 10,
    color: theme.colors.warning,
    fontWeight: '500',
  },
});