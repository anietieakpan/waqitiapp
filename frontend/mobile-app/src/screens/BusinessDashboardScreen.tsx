import React, { useState, useEffect } from 'react';
import {
  View,
  Text,
  StyleSheet,
  ScrollView,
  TouchableOpacity,
  RefreshControl,
} from 'react-native';
import { useNavigation } from '@react-navigation/native';
import { useSelector, useDispatch } from 'react-redux';
import Icon from 'react-native-vector-icons/MaterialCommunityIcons';
import { RootState } from '../store';
import Header from '../components/Header';
import { AnalyticsService } from '../services/AnalyticsService';

/**
 * BusinessDashboardScreen
 *
 * Main dashboard for business accounts
 *
 * Features:
 * - Business metrics overview
 * - Transaction statistics
 * - Quick actions
 * - Revenue analytics
 * - Recent transactions
 *
 * @author Waqiti Engineering Team
 * @version 1.0.0
 */
const BusinessDashboardScreen: React.FC = () => {
  const navigation = useNavigation();
  const dispatch = useDispatch();
  const { user } = useSelector((state: RootState) => state.auth);
  const { transactions } = useSelector((state: RootState) => state.transactions);

  const [refreshing, setRefreshing] = useState(false);
  const [period, setPeriod] = useState<'today' | 'week' | 'month' | 'year'>('month');

  useEffect(() => {
    AnalyticsService.trackScreenView('BusinessDashboardScreen');
    loadBusinessData();
  }, [period]);

  const loadBusinessData = async () => {
    // TODO: Load business-specific data
  };

  const handleRefresh = async () => {
    setRefreshing(true);
    await loadBusinessData();
    setRefreshing(false);
  };

  // Calculate business metrics
  const metrics = {
    totalRevenue: 45230.50,
    transactionCount: 1247,
    avgTransactionValue: 36.28,
    activeCustomers: 328,
    growthRate: 12.5,
  };

  const formatCurrency = (amount: number) => {
    return new Intl.NumberFormat('en-US', {
      style: 'currency',
      currency: 'USD',
    }).format(amount);
  };

  const renderMetricCard = (
    title: string,
    value: string,
    change: number,
    icon: string,
    color: string
  ) => (
    <View style={[styles.metricCard, { borderLeftColor: color }]}>
      <View style={styles.metricHeader}>
        <Icon name={icon} size={24} color={color} />
        <View style={[styles.changeBadge, { backgroundColor: change >= 0 ? '#E8F5E9' : '#FFEBEE' }]}>
          <Icon
            name={change >= 0 ? 'trending-up' : 'trending-down'}
            size={12}
            color={change >= 0 ? '#4CAF50' : '#F44336'}
          />
          <Text
            style={[
              styles.changeText,
              { color: change >= 0 ? '#4CAF50' : '#F44336' },
            ]}
          >
            {Math.abs(change)}%
          </Text>
        </View>
      </View>
      <Text style={styles.metricValue}>{value}</Text>
      <Text style={styles.metricTitle}>{title}</Text>
    </View>
  );

  const renderPeriodSelector = () => (
    <View style={styles.periodSelector}>
      {(['today', 'week', 'month', 'year'] as const).map((p) => (
        <TouchableOpacity
          key={p}
          style={[styles.periodButton, period === p && styles.periodButtonActive]}
          onPress={() => setPeriod(p)}
        >
          <Text
            style={[
              styles.periodButtonText,
              period === p && styles.periodButtonTextActive,
            ]}
          >
            {p.charAt(0).toUpperCase() + p.slice(1)}
          </Text>
        </TouchableOpacity>
      ))}
    </View>
  );

  const renderQuickActions = () => (
    <View style={styles.quickActionsContainer}>
      <Text style={styles.sectionTitle}>Quick Actions</Text>
      <View style={styles.quickActionsGrid}>
        <TouchableOpacity
          style={styles.quickActionCard}
          onPress={() => navigation.navigate('QRCodeGenerator' as never)}
        >
          <View style={[styles.quickActionIcon, { backgroundColor: '#E3F2FD' }]}>
            <Icon name="qrcode" size={28} color="#2196F3" />
          </View>
          <Text style={styles.quickActionText}>QR Code</Text>
        </TouchableOpacity>

        <TouchableOpacity
          style={styles.quickActionCard}
          onPress={() => navigation.navigate('Invoices' as never)}
        >
          <View style={[styles.quickActionIcon, { backgroundColor: '#F3E5F5' }]}>
            <Icon name="receipt" size={28} color="#9C27B0" />
          </View>
          <Text style={styles.quickActionText}>Invoices</Text>
        </TouchableOpacity>

        <TouchableOpacity
          style={styles.quickActionCard}
          onPress={() => navigation.navigate('BusinessPayments' as never)}
        >
          <View style={[styles.quickActionIcon, { backgroundColor: '#E8F5E9' }]}>
            <Icon name="cash-multiple" size={28} color="#4CAF50" />
          </View>
          <Text style={styles.quickActionText}>Payments</Text>
        </TouchableOpacity>

        <TouchableOpacity
          style={styles.quickActionCard}
          onPress={() => navigation.navigate('BusinessAnalytics' as never)}
        >
          <View style={[styles.quickActionIcon, { backgroundColor: '#FFF3E0' }]}>
            <Icon name="chart-line" size={28} color="#FF9800" />
          </View>
          <Text style={styles.quickActionText}>Analytics</Text>
        </TouchableOpacity>
      </View>
    </View>
  );

  return (
    <View style={styles.container}>
      <Header
        title="Business Dashboard"
        showBack
        rightActions={[
          {
            icon: 'cog',
            onPress: () => navigation.navigate('BusinessSettings' as never),
          },
        ]}
      />

      <ScrollView
        style={styles.content}
        refreshControl={
          <RefreshControl refreshing={refreshing} onRefresh={handleRefresh} />
        }
      >
        {renderPeriodSelector()}

        <View style={styles.metricsContainer}>
          {renderMetricCard(
            'Total Revenue',
            formatCurrency(metrics.totalRevenue),
            metrics.growthRate,
            'currency-usd',
            '#4CAF50'
          )}
          {renderMetricCard(
            'Transactions',
            metrics.transactionCount.toString(),
            8.3,
            'swap-horizontal',
            '#2196F3'
          )}
          {renderMetricCard(
            'Avg Transaction',
            formatCurrency(metrics.avgTransactionValue),
            5.2,
            'chart-line',
            '#FF9800'
          )}
          {renderMetricCard(
            'Active Customers',
            metrics.activeCustomers.toString(),
            15.7,
            'account-group',
            '#9C27B0'
          )}
        </View>

        {renderQuickActions()}
      </ScrollView>
    </View>
  );
};

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#F5F5F5',
  },
  content: {
    flex: 1,
  },
  periodSelector: {
    flexDirection: 'row',
    backgroundColor: '#FFFFFF',
    paddingHorizontal: 16,
    paddingVertical: 12,
    borderBottomWidth: 1,
    borderBottomColor: '#E0E0E0',
  },
  periodButton: {
    flex: 1,
    paddingVertical: 8,
    paddingHorizontal: 12,
    borderRadius: 20,
    alignItems: 'center',
    marginHorizontal: 4,
  },
  periodButtonActive: {
    backgroundColor: '#6200EE',
  },
  periodButtonText: {
    fontSize: 14,
    fontWeight: '600',
    color: '#666',
  },
  periodButtonTextActive: {
    color: '#FFFFFF',
  },
  metricsContainer: {
    paddingHorizontal: 16,
    paddingTop: 16,
  },
  metricCard: {
    backgroundColor: '#FFFFFF',
    paddingVertical: 16,
    paddingHorizontal: 16,
    borderRadius: 8,
    marginBottom: 12,
    borderLeftWidth: 4,
    elevation: 1,
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 1 },
    shadowOpacity: 0.05,
    shadowRadius: 1,
  },
  metricHeader: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    marginBottom: 12,
  },
  changeBadge: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingHorizontal: 8,
    paddingVertical: 4,
    borderRadius: 12,
  },
  changeText: {
    fontSize: 12,
    fontWeight: 'bold',
    marginLeft: 4,
  },
  metricValue: {
    fontSize: 28,
    fontWeight: 'bold',
    color: '#212121',
    marginBottom: 4,
  },
  metricTitle: {
    fontSize: 14,
    color: '#666',
  },
  quickActionsContainer: {
    paddingHorizontal: 16,
    paddingTop: 24,
    paddingBottom: 16,
  },
  sectionTitle: {
    fontSize: 18,
    fontWeight: 'bold',
    color: '#212121',
    marginBottom: 16,
  },
  quickActionsGrid: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    marginHorizontal: -6,
  },
  quickActionCard: {
    width: '25%',
    alignItems: 'center',
    paddingVertical: 16,
    paddingHorizontal: 6,
  },
  quickActionIcon: {
    width: 56,
    height: 56,
    borderRadius: 28,
    justifyContent: 'center',
    alignItems: 'center',
    marginBottom: 8,
  },
  quickActionText: {
    fontSize: 12,
    color: '#666',
    textAlign: 'center',
  },
});

export default BusinessDashboardScreen;
