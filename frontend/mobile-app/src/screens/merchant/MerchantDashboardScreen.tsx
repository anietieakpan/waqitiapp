import React, { useState, useEffect, useCallback } from 'react';
import {
  View,
  Text,
  StyleSheet,
  ScrollView,
  TouchableOpacity,
  RefreshControl,
  ActivityIndicator,
  Alert,
  Dimensions,
} from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { useFocusEffect, useNavigation } from '@react-navigation/native';
import Icon from 'react-native-vector-icons/MaterialIcons';
import { LineChart, BarChart } from 'react-native-chart-kit';
import { useTheme } from '../../contexts/ThemeContext';
import { useAuth } from '../../contexts/AuthContext';
import { merchantService } from '../../services/merchantService';
import { formatCurrency, formatNumber } from '../../utils/formatters';

const { width: screenWidth } = Dimensions.get('window');

interface MerchantDashboardData {
  balance: {
    total: number;
    available: number;
    pending: number;
  };
  todayStats: {
    sales: number;
    transactions: number;
    customers: number;
  };
  weeklyStats: {
    sales: number[];
    transactions: number[];
    labels: string[];
  };
  topProducts: Array<{
    id: string;
    name: string;
    sales: number;
    quantity: number;
  }>;
  recentTransactions: Array<{
    id: string;
    customerName: string;
    amount: number;
    status: string;
    timestamp: string;
  }>;
}

const MerchantDashboardScreen: React.FC = () => {
  const navigation = useNavigation();
  const { theme } = useTheme();
  const { user } = useAuth();
  const [dashboardData, setDashboardData] = useState<MerchantDashboardData | null>(null);
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);

  useFocusEffect(
    useCallback(() => {
      loadDashboardData();
    }, [])
  );

  const loadDashboardData = async () => {
    try {
      setLoading(true);
      const data = await merchantService.getDashboardData();
      setDashboardData(data);
    } catch (error) {
      console.error('Failed to load dashboard data:', error);
      Alert.alert('Error', 'Failed to load dashboard data');
    } finally {
      setLoading(false);
    }
  };

  const onRefresh = useCallback(async () => {
    setRefreshing(true);
    await loadDashboardData();
    setRefreshing(false);
  }, []);

  const chartConfig = {
    backgroundColor: theme.colors.surface,
    backgroundGradientFrom: theme.colors.surface,
    backgroundGradientTo: theme.colors.surface,
    decimalPlaces: 0,
    color: (opacity = 1) => `rgba(33, 150, 243, ${opacity})`,
    labelColor: (opacity = 1) => theme.colors.text,
    style: {
      borderRadius: 16,
    },
    propsForDots: {
      r: '4',
      strokeWidth: '2',
      stroke: theme.colors.primary,
    },
  };

  const renderBalanceCard = () => (
    <View style={[styles.balanceCard, { backgroundColor: theme.colors.primary }]}>
      <View style={styles.balanceHeader}>
        <Text style={styles.balanceTitle}>Available Balance</Text>
        <TouchableOpacity
          onPress={() => navigation.navigate('MerchantBalance')}
          style={styles.balanceDetailButton}
        >
          <Icon name="visibility" size={20} color="#FFFFFF" />
        </TouchableOpacity>
      </View>
      
      <Text style={styles.balanceAmount}>
        {formatCurrency(dashboardData?.balance.available || 0)}
      </Text>
      
      <View style={styles.balanceRow}>
        <View style={styles.balanceItem}>
          <Text style={styles.balanceLabel}>Total</Text>
          <Text style={styles.balanceValue}>
            {formatCurrency(dashboardData?.balance.total || 0)}
          </Text>
        </View>
        <View style={styles.balanceItem}>
          <Text style={styles.balanceLabel}>Pending</Text>
          <Text style={styles.balanceValue}>
            {formatCurrency(dashboardData?.balance.pending || 0)}
          </Text>
        </View>
      </View>
      
      <TouchableOpacity
        style={styles.payoutButton}
        onPress={() => navigation.navigate('RequestPayout')}
      >
        <Icon name="account-balance" size={16} color={theme.colors.primary} />
        <Text style={[styles.payoutButtonText, { color: theme.colors.primary }]}>
          Request Payout
        </Text>
      </TouchableOpacity>
    </View>
  );

  const renderTodayStats = () => (
    <View style={styles.statsContainer}>
      <Text style={[styles.sectionTitle, { color: theme.colors.text }]}>
        Today's Performance
      </Text>
      
      <View style={styles.statsGrid}>
        <View style={[styles.statCard, { backgroundColor: theme.colors.surface }]}>
          <Icon name="trending-up" size={32} color="#4CAF50" />
          <Text style={[styles.statValue, { color: theme.colors.text }]}>
            {formatCurrency(dashboardData?.todayStats.sales || 0)}
          </Text>
          <Text style={[styles.statLabel, { color: theme.colors.textSecondary }]}>
            Sales
          </Text>
        </View>
        
        <View style={[styles.statCard, { backgroundColor: theme.colors.surface }]}>
          <Icon name="receipt" size={32} color="#2196F3" />
          <Text style={[styles.statValue, { color: theme.colors.text }]}>
            {formatNumber(dashboardData?.todayStats.transactions || 0)}
          </Text>
          <Text style={[styles.statLabel, { color: theme.colors.textSecondary }]}>
            Transactions
          </Text>
        </View>
        
        <View style={[styles.statCard, { backgroundColor: theme.colors.surface }]}>
          <Icon name="people" size={32} color="#FF9800" />
          <Text style={[styles.statValue, { color: theme.colors.text }]}>
            {formatNumber(dashboardData?.todayStats.customers || 0)}
          </Text>
          <Text style={[styles.statLabel, { color: theme.colors.textSecondary }]}>
            Customers
          </Text>
        </View>
      </View>
    </View>
  );

  const renderSalesChart = () => {
    if (!dashboardData?.weeklyStats.sales.length) return null;

    return (
      <View style={styles.chartContainer}>
        <View style={styles.chartHeader}>
          <Text style={[styles.sectionTitle, { color: theme.colors.text }]}>
            Weekly Sales
          </Text>
          <TouchableOpacity onPress={() => navigation.navigate('MerchantAnalytics')}>
            <Text style={[styles.viewAllText, { color: theme.colors.primary }]}>
              View All
            </Text>
          </TouchableOpacity>
        </View>
        
        <LineChart
          data={{
            labels: dashboardData.weeklyStats.labels,
            datasets: [
              {
                data: dashboardData.weeklyStats.sales,
              },
            ],
          }}
          width={screenWidth - 32}
          height={220}
          chartConfig={chartConfig}
          bezier
          style={styles.chart}
        />
      </View>
    );
  };

  const renderQuickActions = () => (
    <View style={styles.quickActionsContainer}>
      <Text style={[styles.sectionTitle, { color: theme.colors.text }]}>
        Quick Actions
      </Text>
      
      <View style={styles.quickActionsGrid}>
        <TouchableOpacity
          style={[styles.quickActionButton, { backgroundColor: theme.colors.surface }]}
          onPress={() => navigation.navigate('GenerateQR')}
        >
          <Icon name="qr-code" size={32} color={theme.colors.primary} />
          <Text style={[styles.quickActionText, { color: theme.colors.text }]}>
            Generate QR
          </Text>
        </TouchableOpacity>
        
        <TouchableOpacity
          style={[styles.quickActionButton, { backgroundColor: theme.colors.surface }]}
          onPress={() => navigation.navigate('MerchantTransactions')}
        >
          <Icon name="history" size={32} color={theme.colors.primary} />
          <Text style={[styles.quickActionText, { color: theme.colors.text }]}>
            Transactions
          </Text>
        </TouchableOpacity>
        
        <TouchableOpacity
          style={[styles.quickActionButton, { backgroundColor: theme.colors.surface }]}
          onPress={() => navigation.navigate('MerchantCustomers')}
        >
          <Icon name="group" size={32} color={theme.colors.primary} />
          <Text style={[styles.quickActionText, { color: theme.colors.text }]}>
            Customers
          </Text>
        </TouchableOpacity>
        
        <TouchableOpacity
          style={[styles.quickActionButton, { backgroundColor: theme.colors.surface }]}
          onPress={() => navigation.navigate('MerchantSettings')}
        >
          <Icon name="settings" size={32} color={theme.colors.primary} />
          <Text style={[styles.quickActionText, { color: theme.colors.text }]}>
            Settings
          </Text>
        </TouchableOpacity>
      </View>
    </View>
  );

  const renderRecentTransactions = () => (
    <View style={styles.transactionsContainer}>
      <View style={styles.transactionsHeader}>
        <Text style={[styles.sectionTitle, { color: theme.colors.text }]}>
          Recent Transactions
        </Text>
        <TouchableOpacity onPress={() => navigation.navigate('MerchantTransactions')}>
          <Text style={[styles.viewAllText, { color: theme.colors.primary }]}>
            View All
          </Text>
        </TouchableOpacity>
      </View>
      
      {dashboardData?.recentTransactions.map((transaction, index) => (
        <TouchableOpacity
          key={transaction.id}
          style={[styles.transactionItem, { backgroundColor: theme.colors.surface }]}
          onPress={() => navigation.navigate('TransactionDetails', { transactionId: transaction.id })}
        >
          <View style={styles.transactionLeft}>
            <View style={[styles.transactionIcon, { backgroundColor: '#4CAF50' + '20' }]}>
              <Icon name="arrow-downward" size={20} color="#4CAF50" />
            </View>
            <View style={styles.transactionInfo}>
              <Text style={[styles.transactionCustomer, { color: theme.colors.text }]}>
                {transaction.customerName}
              </Text>
              <Text style={[styles.transactionTime, { color: theme.colors.textSecondary }]}>
                {transaction.timestamp}
              </Text>
            </View>
          </View>
          
          <View style={styles.transactionRight}>
            <Text style={[styles.transactionAmount, { color: theme.colors.text }]}>
              {formatCurrency(transaction.amount)}
            </Text>
            <View style={[
              styles.transactionStatus,
              { backgroundColor: transaction.status === 'completed' ? '#4CAF50' : '#FF9800' }
            ]}>
              <Text style={styles.transactionStatusText}>
                {transaction.status.toUpperCase()}
              </Text>
            </View>
          </View>
        </TouchableOpacity>
      ))}
    </View>
  );

  if (loading) {
    return (
      <SafeAreaView style={[styles.container, { backgroundColor: theme.colors.background }]}>
        <View style={styles.loadingContainer}>
          <ActivityIndicator size="large" color={theme.colors.primary} />
          <Text style={[styles.loadingText, { color: theme.colors.text }]}>
            Loading dashboard...
          </Text>
        </View>
      </SafeAreaView>
    );
  }

  return (
    <SafeAreaView style={[styles.container, { backgroundColor: theme.colors.background }]}>
      <View style={styles.header}>
        <TouchableOpacity
          onPress={() => navigation.openDrawer()}
          style={styles.menuButton}
        >
          <Icon name="menu" size={24} color={theme.colors.text} />
        </TouchableOpacity>
        
        <Text style={[styles.headerTitle, { color: theme.colors.text }]}>
          Business Dashboard
        </Text>
        
        <TouchableOpacity
          onPress={() => navigation.navigate('MerchantNotifications')}
          style={styles.notificationsButton}
        >
          <Icon name="notifications" size={24} color={theme.colors.text} />
        </TouchableOpacity>
      </View>

      <ScrollView
        style={styles.content}
        showsVerticalScrollIndicator={false}
        refreshControl={
          <RefreshControl
            refreshing={refreshing}
            onRefresh={onRefresh}
            colors={[theme.colors.primary]}
          />
        }
      >
        {renderBalanceCard()}
        {renderTodayStats()}
        {renderSalesChart()}
        {renderQuickActions()}
        {renderRecentTransactions()}
      </ScrollView>
    </SafeAreaView>
  );
};

const styles = StyleSheet.create({
  container: {
    flex: 1,
  },
  loadingContainer: {
    flex: 1,
    justifyContent: 'center',
    alignItems: 'center',
  },
  loadingText: {
    marginTop: 16,
    fontSize: 16,
  },
  header: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    paddingHorizontal: 16,
    paddingVertical: 12,
  },
  menuButton: {
    padding: 8,
  },
  headerTitle: {
    fontSize: 20,
    fontWeight: '600',
    flex: 1,
    textAlign: 'center',
    marginHorizontal: 16,
  },
  notificationsButton: {
    padding: 8,
  },
  content: {
    flex: 1,
    paddingHorizontal: 16,
  },
  balanceCard: {
    padding: 20,
    borderRadius: 16,
    marginBottom: 20,
  },
  balanceHeader: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginBottom: 8,
  },
  balanceTitle: {
    color: '#FFFFFF',
    fontSize: 14,
    opacity: 0.8,
  },
  balanceDetailButton: {
    padding: 4,
  },
  balanceAmount: {
    color: '#FFFFFF',
    fontSize: 32,
    fontWeight: 'bold',
    marginBottom: 16,
  },
  balanceRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    marginBottom: 20,
  },
  balanceItem: {
    flex: 1,
  },
  balanceLabel: {
    color: '#FFFFFF',
    fontSize: 12,
    opacity: 0.8,
    marginBottom: 4,
  },
  balanceValue: {
    color: '#FFFFFF',
    fontSize: 16,
    fontWeight: '600',
  },
  payoutButton: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: '#FFFFFF',
    paddingVertical: 12,
    borderRadius: 8,
  },
  payoutButtonText: {
    fontSize: 14,
    fontWeight: '600',
    marginLeft: 8,
  },
  statsContainer: {
    marginBottom: 24,
  },
  sectionTitle: {
    fontSize: 18,
    fontWeight: '600',
    marginBottom: 16,
  },
  statsGrid: {
    flexDirection: 'row',
    justifyContent: 'space-between',
  },
  statCard: {
    flex: 1,
    padding: 16,
    borderRadius: 12,
    alignItems: 'center',
    marginHorizontal: 4,
  },
  statValue: {
    fontSize: 18,
    fontWeight: 'bold',
    marginTop: 8,
    marginBottom: 4,
  },
  statLabel: {
    fontSize: 12,
  },
  chartContainer: {
    marginBottom: 24,
  },
  chartHeader: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginBottom: 16,
  },
  viewAllText: {
    fontSize: 14,
    fontWeight: '500',
  },
  chart: {
    marginVertical: 8,
    borderRadius: 16,
  },
  quickActionsContainer: {
    marginBottom: 24,
  },
  quickActionsGrid: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    justifyContent: 'space-between',
  },
  quickActionButton: {
    width: '48%',
    aspectRatio: 1,
    borderRadius: 12,
    justifyContent: 'center',
    alignItems: 'center',
    marginBottom: 12,
  },
  quickActionText: {
    fontSize: 12,
    fontWeight: '500',
    marginTop: 8,
    textAlign: 'center',
  },
  transactionsContainer: {
    marginBottom: 24,
  },
  transactionsHeader: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginBottom: 16,
  },
  transactionItem: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    padding: 16,
    borderRadius: 12,
    marginBottom: 8,
  },
  transactionLeft: {
    flexDirection: 'row',
    alignItems: 'center',
    flex: 1,
  },
  transactionIcon: {
    width: 40,
    height: 40,
    borderRadius: 20,
    justifyContent: 'center',
    alignItems: 'center',
    marginRight: 12,
  },
  transactionInfo: {
    flex: 1,
  },
  transactionCustomer: {
    fontSize: 16,
    fontWeight: '500',
    marginBottom: 2,
  },
  transactionTime: {
    fontSize: 12,
  },
  transactionRight: {
    alignItems: 'flex-end',
  },
  transactionAmount: {
    fontSize: 16,
    fontWeight: '600',
    marginBottom: 4,
  },
  transactionStatus: {
    paddingHorizontal: 8,
    paddingVertical: 2,
    borderRadius: 10,
  },
  transactionStatusText: {
    color: '#FFFFFF',
    fontSize: 10,
    fontWeight: '600',
  },
});

export default MerchantDashboardScreen;