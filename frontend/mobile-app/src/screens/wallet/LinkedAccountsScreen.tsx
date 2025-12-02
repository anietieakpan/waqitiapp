import React, { useState } from 'react';
import {
  View,
  ScrollView,
  StyleSheet,
  TouchableOpacity,
  Alert,
} from 'react-native';
import {
  Text,
  Button,
  useTheme,
  Surface,
  IconButton,
  Menu,
  Divider,
  Chip,
} from 'react-native-paper';
import { useNavigation } from '@react-navigation/native';
import { SafeAreaView } from 'react-native-safe-area-context';
import Icon from 'react-native-vector-icons/MaterialCommunityIcons';
import Header from '../../components/common/Header';

interface LinkedAccount {
  id: string;
  type: 'card' | 'bank';
  name: string;
  last4: string;
  brand?: string;
  isPrimary: boolean;
  isVerified: boolean;
  expiryDate?: string;
}

/**
 * Linked Accounts Screen - Manage payment methods
 */
const LinkedAccountsScreen: React.FC = () => {
  const theme = useTheme();
  const navigation = useNavigation();
  
  const [menuVisible, setMenuVisible] = useState<string | null>(null);
  const [accounts, setAccounts] = useState<LinkedAccount[]>([
    {
      id: '1',
      type: 'card',
      name: 'Visa',
      last4: '4242',
      brand: 'visa',
      isPrimary: true,
      isVerified: true,
      expiryDate: '12/26',
    },
    {
      id: '2',
      type: 'bank',
      name: 'Chase Bank',
      last4: '8901',
      isPrimary: false,
      isVerified: true,
    },
    {
      id: '3',
      type: 'card',
      name: 'Mastercard',
      last4: '5678',
      brand: 'mastercard',
      isPrimary: false,
      isVerified: false,
    },
  ]);

  const handleAddCard = () => {
    navigation.navigate('AddCard' as never);
  };

  const handleAddBank = () => {
    navigation.navigate('AddBankAccount' as never);
  };

  const handleSetPrimary = (accountId: string) => {
    setAccounts(prev =>
      prev.map(account => ({
        ...account,
        isPrimary: account.id === accountId,
      }))
    );
    setMenuVisible(null);
  };

  const handleRemoveAccount = (accountId: string) => {
    Alert.alert(
      'Remove Payment Method',
      'Are you sure you want to remove this payment method?',
      [
        { text: 'Cancel', style: 'cancel' },
        {
          text: 'Remove',
          style: 'destructive',
          onPress: () => {
            setAccounts(prev => prev.filter(account => account.id !== accountId));
          },
        },
      ]
    );
    setMenuVisible(null);
  };

  const renderAccount = (account: LinkedAccount) => {
    const iconName = account.type === 'card' ? 'credit-card' : 'bank';
    const brandIcon = account.brand === 'visa' ? 'credit-card' : 'credit-card';

    return (
      <Surface key={account.id} style={styles.accountCard} elevation={2}>
        <View style={styles.accountHeader}>
          <View style={styles.accountInfo}>
            <Icon name={iconName} size={32} color={theme.colors.primary} />
            <View style={styles.accountDetails}>
              <Text style={styles.accountName}>
                {account.name} •••• {account.last4}
              </Text>
              <View style={styles.accountMeta}>
                {account.expiryDate && (
                  <Text style={styles.accountExpiry}>
                    Expires {account.expiryDate}
                  </Text>
                )}
                <View style={styles.accountStatus}>
                  {account.isPrimary && (
                    <Chip mode="flat" compact style={styles.primaryChip}>
                      Primary
                    </Chip>
                  )}
                  {!account.isVerified && (
                    <Chip mode="outlined" compact style={styles.verifyChip}>
                      Verify
                    </Chip>
                  )}
                </View>
              </View>
            </View>
          </View>

          <Menu
            visible={menuVisible === account.id}
            onDismiss={() => setMenuVisible(null)}
            anchor={
              <IconButton
                icon="dots-vertical"
                size={20}
                onPress={() => setMenuVisible(account.id)}
              />
            }
          >
            {!account.isPrimary && (
              <Menu.Item
                onPress={() => handleSetPrimary(account.id)}
                title="Set as Primary"
                leadingIcon="star"
              />
            )}
            {!account.isVerified && (
              <Menu.Item
                onPress={() => setMenuVisible(null)}
                title="Verify Account"
                leadingIcon="check-circle"
              />
            )}
            <Divider />
            <Menu.Item
              onPress={() => handleRemoveAccount(account.id)}
              title="Remove"
              leadingIcon="delete"
              titleStyle={{ color: theme.colors.error }}
            />
          </Menu>
        </View>

        {!account.isVerified && (
          <View style={styles.verificationBanner}>
            <Icon name="alert-circle" size={16} color="#FF9800" />
            <Text style={styles.verificationText}>
              This account needs to be verified before you can use it.
            </Text>
            <TouchableOpacity style={styles.verifyButton}>
              <Text style={styles.verifyButtonText}>Verify Now</Text>
            </TouchableOpacity>
          </View>
        )}
      </Surface>
    );
  };

  return (
    <SafeAreaView style={styles.container}>
      <Header
        title="Payment Methods"
        leftAction={
          <IconButton
            icon="arrow-left"
            size={24}
            onPress={() => navigation.goBack()}
          />
        }
      />

      <ScrollView
        style={styles.scrollView}
        contentContainerStyle={styles.scrollContent}
        showsVerticalScrollIndicator={false}
      >
        {/* Current Payment Methods */}
        <View style={styles.section}>
          <Text style={styles.sectionTitle}>Your Payment Methods</Text>
          <Text style={styles.sectionSubtitle}>
            Manage your cards and bank accounts
          </Text>

          <View style={styles.accountsList}>
            {accounts.map(renderAccount)}
          </View>
        </View>

        {/* Add Payment Methods */}
        <View style={styles.section}>
          <Text style={styles.sectionTitle}>Add Payment Method</Text>
          
          <Surface style={styles.addMethodCard} elevation={1}>
            <TouchableOpacity
              style={styles.addMethodOption}
              onPress={handleAddCard}
            >
              <Icon name="credit-card-plus" size={24} color={theme.colors.primary} />
              <View style={styles.addMethodInfo}>
                <Text style={styles.addMethodTitle}>Add Debit/Credit Card</Text>
                <Text style={styles.addMethodSubtitle}>
                  Instant transfers with small fees
                </Text>
              </View>
              <Icon name="chevron-right" size={20} color="#666" />
            </TouchableOpacity>
          </Surface>

          <Surface style={styles.addMethodCard} elevation={1}>
            <TouchableOpacity
              style={styles.addMethodOption}
              onPress={handleAddBank}
            >
              <Icon name="bank-plus" size={24} color={theme.colors.primary} />
              <View style={styles.addMethodInfo}>
                <Text style={styles.addMethodTitle}>Add Bank Account</Text>
                <Text style={styles.addMethodSubtitle}>
                  Free transfers, takes 1-3 business days
                </Text>
              </View>
              <Icon name="chevron-right" size={20} color="#666" />
            </TouchableOpacity>
          </Surface>
        </View>

        {/* Security Information */}
        <Surface style={styles.securityCard} elevation={1}>
          <Icon name="shield-check" size={24} color="#4CAF50" />
          <View style={styles.securityInfo}>
            <Text style={styles.securityTitle}>Your information is secure</Text>
            <Text style={styles.securityText}>
              We use bank-level encryption to protect your payment information. 
              We never store your full card or account numbers.
            </Text>
          </View>
        </Surface>

        {/* Help Section */}
        <View style={styles.helpSection}>
          <Text style={styles.helpTitle}>Need help?</Text>
          <TouchableOpacity style={styles.helpItem}>
            <Icon name="help-circle" size={20} color={theme.colors.primary} />
            <Text style={styles.helpText}>How to verify my bank account</Text>
            <Icon name="chevron-right" size={16} color="#666" />
          </TouchableOpacity>
          <TouchableOpacity style={styles.helpItem}>
            <Icon name="help-circle" size={20} color={theme.colors.primary} />
            <Text style={styles.helpText}>Why was my card declined?</Text>
            <Icon name="chevron-right" size={16} color="#666" />
          </TouchableOpacity>
          <TouchableOpacity style={styles.helpItem}>
            <Icon name="help-circle" size={20} color={theme.colors.primary} />
            <Text style={styles.helpText}>Supported banks and cards</Text>
            <Icon name="chevron-right" size={16} color="#666" />
          </TouchableOpacity>
        </View>
      </ScrollView>
    </SafeAreaView>
  );
};

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#f5f5f5',
  },
  scrollView: {
    flex: 1,
  },
  scrollContent: {
    padding: 16,
    paddingBottom: 32,
  },
  section: {
    marginBottom: 32,
  },
  sectionTitle: {
    fontSize: 18,
    fontWeight: '600',
    color: '#333',
    marginBottom: 4,
  },
  sectionSubtitle: {
    fontSize: 14,
    color: '#666',
    marginBottom: 16,
  },
  accountsList: {
    gap: 12,
  },
  accountCard: {
    borderRadius: 12,
    backgroundColor: 'white',
    overflow: 'hidden',
  },
  accountHeader: {
    padding: 16,
  },
  accountInfo: {
    flexDirection: 'row',
    alignItems: 'center',
  },
  accountDetails: {
    flex: 1,
    marginLeft: 12,
  },
  accountName: {
    fontSize: 16,
    fontWeight: '500',
    color: '#333',
    marginBottom: 4,
  },
  accountMeta: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
  },
  accountExpiry: {
    fontSize: 12,
    color: '#666',
  },
  accountStatus: {
    flexDirection: 'row',
    gap: 8,
  },
  primaryChip: {
    backgroundColor: '#e3f2fd',
    height: 24,
  },
  verifyChip: {
    borderColor: '#FF9800',
    height: 24,
  },
  verificationBanner: {
    flexDirection: 'row',
    alignItems: 'center',
    padding: 12,
    backgroundColor: '#fff8e1',
    borderTopWidth: 1,
    borderTopColor: '#FFE082',
  },
  verificationText: {
    flex: 1,
    fontSize: 12,
    color: '#F57C00',
    marginLeft: 8,
  },
  verifyButton: {
    paddingHorizontal: 12,
    paddingVertical: 4,
    borderRadius: 12,
    backgroundColor: '#FF9800',
  },
  verifyButtonText: {
    fontSize: 12,
    color: 'white',
    fontWeight: '500',
  },
  addMethodCard: {
    borderRadius: 12,
    backgroundColor: 'white',
    marginBottom: 8,
  },
  addMethodOption: {
    flexDirection: 'row',
    alignItems: 'center',
    padding: 16,
  },
  addMethodInfo: {
    flex: 1,
    marginLeft: 12,
  },
  addMethodTitle: {
    fontSize: 16,
    fontWeight: '500',
    color: '#333',
    marginBottom: 4,
  },
  addMethodSubtitle: {
    fontSize: 12,
    color: '#666',
  },
  securityCard: {
    flexDirection: 'row',
    alignItems: 'flex-start',
    padding: 16,
    borderRadius: 12,
    backgroundColor: '#f8fff8',
    borderColor: '#4CAF50',
    borderWidth: 1,
    marginBottom: 24,
  },
  securityInfo: {
    flex: 1,
    marginLeft: 12,
  },
  securityTitle: {
    fontSize: 14,
    fontWeight: '600',
    color: '#4CAF50',
    marginBottom: 4,
  },
  securityText: {
    fontSize: 12,
    color: '#666',
    lineHeight: 18,
  },
  helpSection: {
    gap: 16,
  },
  helpTitle: {
    fontSize: 16,
    fontWeight: '600',
    color: '#333',
  },
  helpItem: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingVertical: 8,
  },
  helpText: {
    flex: 1,
    fontSize: 14,
    color: '#333',
    marginLeft: 12,
  },
});

export default LinkedAccountsScreen;