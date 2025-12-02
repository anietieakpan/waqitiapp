import React, { useState } from 'react';
import {
  View,
  Text,
  StyleSheet,
  TouchableOpacity,
  FlatList,
  Image,
  ActivityIndicator,
} from 'react-native';
import Icon from 'react-native-vector-icons/MaterialCommunityIcons';
import { AnalyticsService } from '../services/AnalyticsService';

/**
 * PaymentMethodSelector Component
 *
 * Reusable payment method picker for cards, bank accounts, and wallets
 *
 * Features:
 * - Multiple payment method types (card, bank, wallet)
 * - Default payment method support
 * - Add new payment method
 * - Card brand icons
 * - Loading states
 * - Empty states
 *
 * @author Waqiti Engineering Team
 * @version 1.0.0
 */

export interface PaymentMethod {
  id: string;
  type: 'card' | 'bank' | 'wallet';
  name: string;
  last4?: string;
  brand?: string;
  expiryMonth?: number;
  expiryYear?: number;
  bankName?: string;
  accountType?: string;
  isDefault?: boolean;
  isExpired?: boolean;
}

interface PaymentMethodSelectorProps {
  paymentMethods: PaymentMethod[];
  selectedPaymentMethodId?: string | null;
  onSelectPaymentMethod: (method: PaymentMethod) => void;
  onAddPaymentMethod?: () => void;
  loading?: boolean;
  showAddButton?: boolean;
  emptyMessage?: string;
}

const PaymentMethodSelector: React.FC<PaymentMethodSelectorProps> = ({
  paymentMethods,
  selectedPaymentMethodId,
  onSelectPaymentMethod,
  onAddPaymentMethod,
  loading = false,
  showAddButton = true,
  emptyMessage = 'No payment methods added',
}) => {
  const [expandedId, setExpandedId] = useState<string | null>(null);

  const getCardBrandIcon = (brand?: string): string => {
    switch (brand?.toLowerCase()) {
      case 'visa':
        return 'credit-card';
      case 'mastercard':
        return 'credit-card';
      case 'amex':
        return 'credit-card';
      case 'discover':
        return 'credit-card';
      default:
        return 'credit-card-outline';
    }
  };

  const getPaymentMethodIcon = (method: PaymentMethod): string => {
    switch (method.type) {
      case 'card':
        return getCardBrandIcon(method.brand);
      case 'bank':
        return 'bank';
      case 'wallet':
        return 'wallet';
      default:
        return 'help-circle';
    }
  };

  const getPaymentMethodColor = (method: PaymentMethod): string => {
    if (method.isExpired) return '#F44336';
    if (method.isDefault) return '#6200EE';
    return '#666';
  };

  const formatExpiryDate = (month?: number, year?: number): string => {
    if (!month || !year) return '';
    return `${month.toString().padStart(2, '0')}/${year.toString().slice(-2)}`;
  };

  const handleSelectPaymentMethod = (method: PaymentMethod) => {
    if (method.isExpired) {
      return; // Don't allow selection of expired cards
    }

    AnalyticsService.trackEvent('payment_method_selected', {
      paymentMethodId: method.id,
      type: method.type,
      brand: method.brand,
    });

    onSelectPaymentMethod(method);
  };

  const handleToggleExpanded = (id: string) => {
    setExpandedId(expandedId === id ? null : id);
  };

  const renderPaymentMethodItem = ({ item }: { item: PaymentMethod }) => {
    const isSelected = selectedPaymentMethodId === item.id;
    const isExpanded = expandedId === item.id;

    return (
      <View style={styles.paymentMethodContainer}>
        <TouchableOpacity
          style={[
            styles.paymentMethodItem,
            isSelected && styles.paymentMethodItemSelected,
            item.isExpired && styles.paymentMethodItemExpired,
          ]}
          onPress={() => handleSelectPaymentMethod(item)}
          onLongPress={() => handleToggleExpanded(item.id)}
          disabled={item.isExpired}
        >
          <View style={styles.paymentMethodLeft}>
            <View
              style={[
                styles.iconContainer,
                { backgroundColor: getPaymentMethodColor(item) + '20' },
              ]}
            >
              <Icon
                name={getPaymentMethodIcon(item)}
                size={24}
                color={getPaymentMethodColor(item)}
              />
            </View>

            <View style={styles.paymentMethodInfo}>
              <View style={styles.paymentMethodNameRow}>
                <Text
                  style={[
                    styles.paymentMethodName,
                    item.isExpired && styles.textExpired,
                  ]}
                >
                  {item.name}
                </Text>
                {item.isDefault && (
                  <View style={styles.defaultBadge}>
                    <Text style={styles.defaultBadgeText}>Default</Text>
                  </View>
                )}
              </View>

              {item.type === 'card' && (
                <View style={styles.detailsRow}>
                  <Text style={styles.paymentMethodDetails}>
                    •••• {item.last4}
                  </Text>
                  {item.expiryMonth && item.expiryYear && (
                    <Text
                      style={[
                        styles.expiryDate,
                        item.isExpired && styles.textExpired,
                      ]}
                    >
                      Exp: {formatExpiryDate(item.expiryMonth, item.expiryYear)}
                    </Text>
                  )}
                </View>
              )}

              {item.type === 'bank' && (
                <View style={styles.detailsRow}>
                  <Text style={styles.paymentMethodDetails}>
                    {item.bankName}
                  </Text>
                  <Text style={styles.accountType}>
                    {item.accountType} •••• {item.last4}
                  </Text>
                </View>
              )}

              {item.type === 'wallet' && (
                <Text style={styles.paymentMethodDetails}>
                  Balance-based payment
                </Text>
              )}

              {item.isExpired && (
                <Text style={styles.expiredLabel}>Card Expired</Text>
              )}
            </View>
          </View>

          <View style={styles.paymentMethodRight}>
            {isSelected && (
              <Icon name="check-circle" size={24} color="#6200EE" />
            )}
          </View>
        </TouchableOpacity>

        {isExpanded && (
          <View style={styles.expandedDetails}>
            <Text style={styles.expandedTitle}>Details</Text>
            <View style={styles.expandedRow}>
              <Text style={styles.expandedLabel}>Type:</Text>
              <Text style={styles.expandedValue}>{item.type}</Text>
            </View>
            {item.brand && (
              <View style={styles.expandedRow}>
                <Text style={styles.expandedLabel}>Brand:</Text>
                <Text style={styles.expandedValue}>{item.brand}</Text>
              </View>
            )}
            <TouchableOpacity
              style={styles.removeButton}
              onPress={() => {
                // TODO: Implement remove functionality
              }}
            >
              <Icon name="delete" size={16} color="#F44336" />
              <Text style={styles.removeButtonText}>Remove</Text>
            </TouchableOpacity>
          </View>
        )}
      </View>
    );
  };

  const renderAddButton = () => {
    if (!showAddButton || !onAddPaymentMethod) return null;

    return (
      <TouchableOpacity
        style={styles.addButton}
        onPress={() => {
          AnalyticsService.trackEvent('add_payment_method_clicked');
          onAddPaymentMethod();
        }}
      >
        <Icon name="plus-circle" size={24} color="#6200EE" />
        <Text style={styles.addButtonText}>Add Payment Method</Text>
      </TouchableOpacity>
    );
  };

  const renderEmptyState = () => (
    <View style={styles.emptyContainer}>
      <Icon name="credit-card-off" size={64} color="#E0E0E0" />
      <Text style={styles.emptyText}>{emptyMessage}</Text>
      {showAddButton && onAddPaymentMethod && (
        <TouchableOpacity
          style={styles.addButtonPrimary}
          onPress={onAddPaymentMethod}
        >
          <Icon name="plus" size={20} color="#FFFFFF" />
          <Text style={styles.addButtonPrimaryText}>Add Payment Method</Text>
        </TouchableOpacity>
      )}
    </View>
  );

  const renderLoadingState = () => (
    <View style={styles.loadingContainer}>
      <ActivityIndicator size="large" color="#6200EE" />
      <Text style={styles.loadingText}>Loading payment methods...</Text>
    </View>
  );

  if (loading) {
    return renderLoadingState();
  }

  if (paymentMethods.length === 0) {
    return renderEmptyState();
  }

  return (
    <View style={styles.container}>
      <FlatList
        data={paymentMethods}
        renderItem={renderPaymentMethodItem}
        keyExtractor={(item) => item.id}
        showsVerticalScrollIndicator={false}
        ListHeaderComponent={
          <Text style={styles.header}>Select Payment Method</Text>
        }
        ListFooterComponent={renderAddButton}
      />
    </View>
  );
};

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#F5F5F5',
  },
  header: {
    fontSize: 16,
    fontWeight: 'bold',
    color: '#212121',
    paddingHorizontal: 16,
    paddingTop: 16,
    paddingBottom: 8,
  },
  paymentMethodContainer: {
    marginBottom: 8,
  },
  paymentMethodItem: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    backgroundColor: '#FFFFFF',
    paddingVertical: 16,
    paddingHorizontal: 16,
    borderBottomWidth: 1,
    borderBottomColor: '#F5F5F5',
  },
  paymentMethodItemSelected: {
    backgroundColor: '#F3E5F5',
    borderLeftWidth: 4,
    borderLeftColor: '#6200EE',
  },
  paymentMethodItemExpired: {
    opacity: 0.6,
  },
  paymentMethodLeft: {
    flexDirection: 'row',
    alignItems: 'center',
    flex: 1,
  },
  iconContainer: {
    width: 48,
    height: 48,
    borderRadius: 24,
    justifyContent: 'center',
    alignItems: 'center',
  },
  paymentMethodInfo: {
    marginLeft: 12,
    flex: 1,
  },
  paymentMethodNameRow: {
    flexDirection: 'row',
    alignItems: 'center',
  },
  paymentMethodName: {
    fontSize: 16,
    fontWeight: '600',
    color: '#212121',
    marginRight: 8,
  },
  defaultBadge: {
    backgroundColor: '#6200EE',
    paddingHorizontal: 8,
    paddingVertical: 2,
    borderRadius: 10,
  },
  defaultBadgeText: {
    color: '#FFFFFF',
    fontSize: 10,
    fontWeight: 'bold',
  },
  detailsRow: {
    flexDirection: 'row',
    alignItems: 'center',
    marginTop: 4,
  },
  paymentMethodDetails: {
    fontSize: 14,
    color: '#666',
    marginRight: 8,
  },
  expiryDate: {
    fontSize: 12,
    color: '#999',
  },
  accountType: {
    fontSize: 12,
    color: '#999',
    textTransform: 'capitalize',
  },
  textExpired: {
    color: '#F44336',
  },
  expiredLabel: {
    fontSize: 12,
    color: '#F44336',
    fontWeight: 'bold',
    marginTop: 4,
  },
  paymentMethodRight: {
    marginLeft: 12,
  },
  expandedDetails: {
    backgroundColor: '#FAFAFA',
    paddingHorizontal: 16,
    paddingVertical: 12,
  },
  expandedTitle: {
    fontSize: 14,
    fontWeight: 'bold',
    color: '#212121',
    marginBottom: 8,
  },
  expandedRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    paddingVertical: 4,
  },
  expandedLabel: {
    fontSize: 14,
    color: '#666',
  },
  expandedValue: {
    fontSize: 14,
    color: '#212121',
    fontWeight: '600',
  },
  removeButton: {
    flexDirection: 'row',
    alignItems: 'center',
    marginTop: 8,
    paddingVertical: 8,
  },
  removeButtonText: {
    color: '#F44336',
    fontSize: 14,
    fontWeight: '600',
    marginLeft: 4,
  },
  addButton: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: '#FFFFFF',
    paddingVertical: 16,
    borderWidth: 2,
    borderColor: '#6200EE',
    borderStyle: 'dashed',
    borderRadius: 8,
    marginHorizontal: 16,
    marginVertical: 16,
  },
  addButtonText: {
    color: '#6200EE',
    fontSize: 16,
    fontWeight: '600',
    marginLeft: 8,
  },
  addButtonPrimary: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: '#6200EE',
    paddingHorizontal: 24,
    paddingVertical: 12,
    borderRadius: 24,
    marginTop: 24,
  },
  addButtonPrimaryText: {
    color: '#FFFFFF',
    fontSize: 16,
    fontWeight: '600',
    marginLeft: 8,
  },
  emptyContainer: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
    paddingVertical: 48,
    paddingHorizontal: 32,
  },
  emptyText: {
    fontSize: 16,
    color: '#999',
    marginTop: 16,
    textAlign: 'center',
  },
  loadingContainer: {
    flex: 1,
    justifyContent: 'center',
    alignItems: 'center',
    backgroundColor: '#F5F5F5',
  },
  loadingText: {
    marginTop: 16,
    fontSize: 16,
    color: '#666',
  },
});

export default PaymentMethodSelector;
