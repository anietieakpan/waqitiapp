import React, { useState, useEffect, useRef } from 'react';
import {
  View,
  ScrollView,
  StyleSheet,
  RefreshControl,
  TouchableOpacity,
  Dimensions,
  Animated,
} from 'react-native';
import {
  Text,
  Card,
  Button,
  useTheme,
  Surface,
  Chip,
  IconButton,
  List,
  Divider,
  ProgressBar,
  Portal,
  Modal,
  FAB,
} from 'react-native-paper';
import { useNavigation } from '@react-navigation/native';
import { useSelector, useDispatch } from 'react-redux';
import LinearGradient from 'react-native-linear-gradient';
import Icon from 'react-native-vector-icons/MaterialCommunityIcons';
import { LineChart } from 'react-native-chart-kit';
import { useQuery } from 'react-query';
import Animated as Reanimated, { 
  FadeInDown, 
  FadeInRight,
  useAnimatedStyle,
  useSharedValue,
  withSpring,
} from 'react-native-reanimated';

import { RootState, AppDispatch } from '../../store/store';
import { fetchWalletBalance, fetchWalletDetails } from '../../store/slices/walletSlice';
import { walletService } from '../../services/walletService';
import { formatCurrency, formatDate } from '../../utils/formatters';
import Header from '../../components/common/Header';
import TransactionItem from '../../components/wallet/TransactionItem';
import PaymentMethodCard from '../../components/wallet/PaymentMethodCard';
import SpendingInsights from '../../components/wallet/SpendingInsights';

const { width: screenWidth } = Dimensions.get('window');

interface WalletStats {
  monthlyIncome: number;
  monthlyExpenses: number;
  savingsRate: number;
  topCategories: Array<{ category: string; amount: number; percentage: number }>;
  weeklySpending: Array<{ date: string; amount: number }>;
}

/**
 * Wallet Screen - Comprehensive wallet management interface
 */
const WalletScreen: React.FC = () => {
  const theme = useTheme();
  const navigation = useNavigation();
  const dispatch = useDispatch<AppDispatch>();
  
  const [refreshing, setRefreshing] = useState(false);
  const [selectedTab, setSelectedTab] = useState<'overview' | 'activity' | 'insights'>('overview');
  const [showAddMoneyModal, setShowAddMoneyModal] = useState(false);
  const [balanceVisible, setBalanceVisible] = useState(true);
  
  const scrollY = useRef(new Animated.Value(0)).current;
  const headerScale = useSharedValue(1);
  
  // Redux state
  const wallet = useSelector((state: RootState) => state.wallet);
  const { user } = useSelector((state: RootState) => state.auth);
  
  // Fetch wallet statistics
  const { data: walletStats, refetch: refetchStats } = useQuery<WalletStats>(
    'walletStats',
    walletService.getWalletStatistics,
    {
      refetchInterval: 60000, // Refresh every minute
    }
  );
  
  // Fetch payment methods
  const { data: paymentMethods } = useQuery(
    'paymentMethods',
    walletService.getPaymentMethods
  );
  
  // Fetch recent transactions
  const { data: recentTransactions } = useQuery(
    'recentTransactions',
    () => walletService.getRecentTransactions(20)
  );
  
  useEffect(() => {
    loadWalletData();
  }, []);
  
  const loadWalletData = async () => {
    dispatch(fetchWalletBalance());
    dispatch(fetchWalletDetails());
  };
  
  const onRefresh = async () => {
    setRefreshing(true);
    await Promise.all([
      loadWalletData(),
      refetchStats(),
    ]);
    setRefreshing(false);
  };
  
  const toggleBalanceVisibility = () => {
    setBalanceVisible(!balanceVisible);
  };
  
  const animatedHeaderStyle = useAnimatedStyle(() => {
    return {
      transform: [{ scale: withSpring(headerScale.value) }],
    };
  });
  
  const chartConfig = {
    backgroundColor: theme.colors.primary,
    backgroundGradientFrom: theme.colors.primary,
    backgroundGradientTo: theme.colors.secondary,
    decimalPlaces: 0,
    color: (opacity = 1) => `rgba(255, 255, 255, ${opacity})`,
    labelColor: (opacity = 1) => `rgba(255, 255, 255, ${opacity})`,
    style: {
      borderRadius: 16,
    },
    propsForDots: {
      r: '6',
      strokeWidth: '2',
      stroke: theme.colors.primaryContainer,
    },
  };
  
  const renderOverviewTab = () => (
    <>
      {/* Balance Card */}
      <Reanimated.View entering={FadeInDown.delay(100)}>
        <LinearGradient
          colors={[theme.colors.primary, theme.colors.secondary]}
          style={styles.balanceCard}
          start={{ x: 0, y: 0 }}
          end={{ x: 1, y: 1 }}
        >
          <Reanimated.View style={animatedHeaderStyle}>
            <View style={styles.balanceHeader}>
              <Text style={styles.balanceLabel}>Available Balance</Text>
              <TouchableOpacity onPress={toggleBalanceVisibility}>
                <Icon 
                  name={balanceVisible ? 'eye' : 'eye-off'} 
                  size={20} 
                  color="white" 
                />
              </TouchableOpacity>
            </View>
            
            <Text style={styles.balanceAmount}>
              {balanceVisible 
                ? formatCurrency(wallet.balance, wallet.currency)
                : '••••••'
              }
            </Text>
            
            <View style={styles.balanceDetails}>
              <View style={styles.balanceDetail}>
                <Text style={styles.balanceDetailLabel}>Pending</Text>
                <Text style={styles.balanceDetailValue}>
                  {balanceVisible 
                    ? formatCurrency(wallet.pendingBalance || 0, wallet.currency)
                    : '••••'
                  }
                </Text>
              </View>
              <View style={styles.balanceDetailDivider} />
              <View style={styles.balanceDetail}>
                <Text style={styles.balanceDetailLabel}>On Hold</Text>
                <Text style={styles.balanceDetailValue}>
                  {balanceVisible 
                    ? formatCurrency(wallet.holdBalance || 0, wallet.currency)
                    : '••••'
                  }
                </Text>
              </View>
            </View>
          </Reanimated.View>
          
          <View style={styles.balanceActions}>
            <Button
              mode="contained"
              onPress={() => setShowAddMoneyModal(true)}
              style={styles.balanceActionButton}
              contentStyle={styles.balanceActionButtonContent}
              labelStyle={styles.balanceActionButtonLabel}
              icon="plus"
            >
              Add Money
            </Button>
            <Button
              mode="outlined"
              onPress={() => navigation.navigate('CashOut' as never)}
              style={[styles.balanceActionButton, styles.balanceActionButtonOutlined]}
              contentStyle={styles.balanceActionButtonContent}
              labelStyle={[styles.balanceActionButtonLabel, styles.balanceActionButtonLabelOutlined]}
              icon="bank-transfer-out"
            >
              Cash Out
            </Button>
          </View>
        </LinearGradient>
      </Reanimated.View>
      
      {/* Quick Stats */}
      {walletStats && (
        <Reanimated.View entering={FadeInDown.delay(200)}>
          <View style={styles.statsContainer}>
            <Surface style={styles.statCard}>
              <Icon name="trending-up" size={24} color="#4CAF50" />
              <Text style={styles.statLabel}>Income</Text>
              <Text style={styles.statValue}>
                {formatCurrency(walletStats.monthlyIncome, wallet.currency)}
              </Text>
              <Text style={styles.statChange}>+12% vs last month</Text>
            </Surface>
            
            <Surface style={styles.statCard}>
              <Icon name="trending-down" size={24} color="#FF5252" />
              <Text style={styles.statLabel}>Expenses</Text>
              <Text style={styles.statValue}>
                {formatCurrency(walletStats.monthlyExpenses, wallet.currency)}
              </Text>
              <Text style={styles.statChange}>-5% vs last month</Text>
            </Surface>
            
            <Surface style={styles.statCard}>
              <Icon name="piggy-bank" size={24} color="#2196F3" />
              <Text style={styles.statLabel}>Saved</Text>
              <Text style={styles.statValue}>{walletStats.savingsRate}%</Text>
              <Text style={styles.statChange}>of income</Text>
            </Surface>
          </View>
        </Reanimated.View>
      )}
      
      {/* Payment Methods */}
      <Reanimated.View entering={FadeInDown.delay(300)} style={styles.section}>
        <View style={styles.sectionHeader}>
          <Text style={styles.sectionTitle}>Payment Methods</Text>
          <IconButton
            icon="plus"
            size={20}
            onPress={() => navigation.navigate('AddPaymentMethod' as never)}
          />
        </View>
        
        {paymentMethods && paymentMethods.length > 0 ? (
          <ScrollView
            horizontal
            showsHorizontalScrollIndicator={false}
            contentContainerStyle={styles.paymentMethodsList}
          >
            {paymentMethods.map((method, index) => (
              <Reanimated.View
                key={method.id}
                entering={FadeInRight.delay(300 + index * 100)}
              >
                <PaymentMethodCard
                  method={method}
                  onPress={() => navigation.navigate('PaymentMethodDetails', {
                    methodId: method.id
                  } as never)}
                />
              </Reanimated.View>
            ))}
          </ScrollView>
        ) : (
          <Card style={styles.emptyCard}>
            <Card.Content style={styles.emptyContent}>
              <Icon name="credit-card-off" size={48} color={theme.colors.onSurfaceDisabled} />
              <Text style={styles.emptyText}>No payment methods</Text>
              <Button
                mode="text"
                onPress={() => navigation.navigate('AddPaymentMethod' as never)}
                style={styles.emptyButton}
              >
                Add your first card or bank
              </Button>
            </Card.Content>
          </Card>
        )}
      </Reanimated.View>
      
      {/* Spending Chart */}
      {walletStats && walletStats.weeklySpending.length > 0 && (
        <Reanimated.View entering={FadeInDown.delay(400)} style={styles.section}>
          <Text style={styles.sectionTitle}>Weekly Spending</Text>
          <Surface style={styles.chartContainer}>
            <LineChart
              data={{
                labels: walletStats.weeklySpending.map(d => d.date),
                datasets: [{
                  data: walletStats.weeklySpending.map(d => d.amount),
                }],
              }}
              width={screenWidth - 32}
              height={200}
              chartConfig={chartConfig}
              bezier
              style={styles.chart}
              withInnerLines={false}
              withOuterLines={false}
              withVerticalLabels={true}
              withHorizontalLabels={true}
            />
          </Surface>
        </Reanimated.View>
      )}
    </>
  );
  
  const renderActivityTab = () => (
    <Reanimated.View entering={FadeInDown}>
      {recentTransactions && recentTransactions.length > 0 ? (
        <List.Section>
          <List.Subheader>Recent Transactions</List.Subheader>
          {recentTransactions.map((transaction, index) => (
            <React.Fragment key={transaction.id}>
              <TransactionItem
                transaction={transaction}
                onPress={() => navigation.navigate('TransactionDetails', {
                  transactionId: transaction.id
                } as never)}
              />
              {index < recentTransactions.length - 1 && <Divider />}
            </React.Fragment>
          ))}
        </List.Section>
      ) : (
        <Card style={styles.emptyCard}>
          <Card.Content style={styles.emptyContent}>
            <Icon name="receipt" size={48} color={theme.colors.onSurfaceDisabled} />
            <Text style={styles.emptyText}>No transactions yet</Text>
            <Text style={styles.emptySubtext}>
              Your transaction history will appear here
            </Text>
          </Card.Content>
        </Card>
      )}
    </Reanimated.View>
  );
  
  const renderInsightsTab = () => (
    <Reanimated.View entering={FadeInDown}>
      {walletStats ? (
        <SpendingInsights stats={walletStats} currency={wallet.currency} />
      ) : (
        <Card style={styles.emptyCard}>
          <Card.Content style={styles.emptyContent}>
            <Icon name="chart-pie" size={48} color={theme.colors.onSurfaceDisabled} />
            <Text style={styles.emptyText}>No insights yet</Text>
            <Text style={styles.emptySubtext}>
              Start using your wallet to see spending insights
            </Text>
          </Card.Content>
        </Card>
      )}
    </Reanimated.View>
  );
  
  return (
    <View style={styles.container}>
      <Header
        title="My Wallet"
        subtitle={`${user?.firstName || 'User'}'s Wallet`}
        rightAction={
          <IconButton
            icon="cog"
            size={24}
            onPress={() => navigation.navigate('WalletSettings' as never)}
          />
        }
      />
      
      {/* Tab Navigation */}
      <Surface style={styles.tabContainer}>
        <ScrollView
          horizontal
          showsHorizontalScrollIndicator={false}
          contentContainerStyle={styles.tabs}
        >
          <Chip
            mode={selectedTab === 'overview' ? 'flat' : 'outlined'}
            selected={selectedTab === 'overview'}
            onPress={() => setSelectedTab('overview')}
            style={styles.tab}
          >
            Overview
          </Chip>
          <Chip
            mode={selectedTab === 'activity' ? 'flat' : 'outlined'}
            selected={selectedTab === 'activity'}
            onPress={() => setSelectedTab('activity')}
            style={styles.tab}
          >
            Activity
          </Chip>
          <Chip
            mode={selectedTab === 'insights' ? 'flat' : 'outlined'}
            selected={selectedTab === 'insights'}
            onPress={() => setSelectedTab('insights')}
            style={styles.tab}
          >
            Insights
          </Chip>
        </ScrollView>
      </Surface>
      
      <Animated.ScrollView
        style={styles.scrollView}
        contentContainerStyle={styles.scrollContent}
        showsVerticalScrollIndicator={false}
        refreshControl={
          <RefreshControl refreshing={refreshing} onRefresh={onRefresh} />
        }
        onScroll={Animated.event(
          [{ nativeEvent: { contentOffset: { y: scrollY } } }],
          { useNativeDriver: true }
        )}
        scrollEventThrottle={16}
      >
        {selectedTab === 'overview' && renderOverviewTab()}
        {selectedTab === 'activity' && renderActivityTab()}
        {selectedTab === 'insights' && renderInsightsTab()}
      </Animated.ScrollView>
      
      {/* Floating Action Button */}
      <FAB.Group
        open={false}
        icon="plus"
        actions={[
          {
            icon: 'send',
            label: 'Send Money',
            onPress: () => navigation.navigate('SendMoney' as never),
          },
          {
            icon: 'call-received',
            label: 'Request Money',
            onPress: () => navigation.navigate('RequestMoney' as never),
          },
          {
            icon: 'bank-plus',
            label: 'Add Money',
            onPress: () => setShowAddMoneyModal(true),
          },
        ]}
        onStateChange={({ open }) => console.log(open)}
        style={styles.fab}
      />
      
      {/* Add Money Modal */}
      <Portal>
        <Modal
          visible={showAddMoneyModal}
          onDismiss={() => setShowAddMoneyModal(false)}
          contentContainerStyle={styles.modalContent}
        >
          <Text style={styles.modalTitle}>Add Money</Text>
          <List.Section>
            <List.Item
              title="From Bank Account"
              description="Transfer from your linked bank"
              left={(props) => <List.Icon {...props} icon="bank" />}
              onPress={() => {
                setShowAddMoneyModal(false);
                navigation.navigate('AddFromBank' as never);
              }}
            />
            <Divider />
            <List.Item
              title="From Debit Card"
              description="Instant transfer with card"
              left={(props) => <List.Icon {...props} icon="credit-card" />}
              onPress={() => {
                setShowAddMoneyModal(false);
                navigation.navigate('AddFromCard' as never);
              }}
            />
            <Divider />
            <List.Item
              title="Direct Deposit"
              description="Set up paycheck deposit"
              left={(props) => <List.Icon {...props} icon="cash" />}
              onPress={() => {
                setShowAddMoneyModal(false);
                navigation.navigate('DirectDeposit' as never);
              }}
            />
          </List.Section>
        </Modal>
      </Portal>
    </View>
  );
};

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#f5f5f5',
  },
  tabContainer: {
    backgroundColor: 'white',
    elevation: 2,
  },
  tabs: {
    flexDirection: 'row',
    paddingHorizontal: 16,
    paddingVertical: 8,
    gap: 8,
  },
  tab: {
    marginRight: 8,
  },
  scrollView: {
    flex: 1,
  },
  scrollContent: {
    paddingBottom: 80,
  },
  balanceCard: {
    margin: 16,
    padding: 20,
    borderRadius: 16,
    elevation: 4,
  },
  balanceHeader: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginBottom: 8,
  },
  balanceLabel: {
    color: 'white',
    fontSize: 14,
    opacity: 0.8,
  },
  balanceAmount: {
    color: 'white',
    fontSize: 32,
    fontWeight: 'bold',
    marginBottom: 16,
  },
  balanceDetails: {
    flexDirection: 'row',
    alignItems: 'center',
    marginBottom: 20,
  },
  balanceDetail: {
    flex: 1,
  },
  balanceDetailLabel: {
    color: 'white',
    fontSize: 12,
    opacity: 0.7,
  },
  balanceDetailValue: {
    color: 'white',
    fontSize: 16,
    fontWeight: '600',
    marginTop: 2,
  },
  balanceDetailDivider: {
    width: 1,
    height: 30,
    backgroundColor: 'rgba(255, 255, 255, 0.3)',
    marginHorizontal: 16,
  },
  balanceActions: {
    flexDirection: 'row',
    gap: 12,
  },
  balanceActionButton: {
    flex: 1,
    borderRadius: 8,
    backgroundColor: 'rgba(255, 255, 255, 0.2)',
  },
  balanceActionButtonOutlined: {
    backgroundColor: 'transparent',
    borderColor: 'rgba(255, 255, 255, 0.5)',
  },
  balanceActionButtonContent: {
    paddingVertical: 4,
  },
  balanceActionButtonLabel: {
    color: 'white',
    fontSize: 14,
  },
  balanceActionButtonLabelOutlined: {
    color: 'white',
  },
  statsContainer: {
    flexDirection: 'row',
    paddingHorizontal: 16,
    gap: 12,
    marginBottom: 16,
  },
  statCard: {
    flex: 1,
    padding: 16,
    borderRadius: 12,
    alignItems: 'center',
    elevation: 1,
  },
  statLabel: {
    fontSize: 12,
    color: '#666',
    marginTop: 8,
  },
  statValue: {
    fontSize: 18,
    fontWeight: 'bold',
    color: '#333',
    marginTop: 4,
  },
  statChange: {
    fontSize: 10,
    color: '#666',
    marginTop: 2,
  },
  section: {
    marginBottom: 16,
  },
  sectionHeader: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    paddingHorizontal: 16,
    marginBottom: 12,
  },
  sectionTitle: {
    fontSize: 18,
    fontWeight: '600',
    color: '#333',
  },
  paymentMethodsList: {
    paddingHorizontal: 16,
    gap: 12,
  },
  chartContainer: {
    marginHorizontal: 16,
    padding: 16,
    borderRadius: 12,
    elevation: 1,
  },
  chart: {
    marginVertical: 8,
    borderRadius: 16,
  },
  emptyCard: {
    margin: 16,
    elevation: 1,
  },
  emptyContent: {
    alignItems: 'center',
    paddingVertical: 32,
  },
  emptyText: {
    fontSize: 16,
    fontWeight: '500',
    color: '#666',
    marginTop: 12,
  },
  emptySubtext: {
    fontSize: 14,
    color: '#999',
    marginTop: 4,
    textAlign: 'center',
  },
  emptyButton: {
    marginTop: 12,
  },
  fab: {
    position: 'absolute',
    right: 16,
    bottom: 16,
  },
  modalContent: {
    backgroundColor: 'white',
    margin: 20,
    borderRadius: 12,
    padding: 20,
  },
  modalTitle: {
    fontSize: 20,
    fontWeight: '600',
    marginBottom: 16,
  },
});

export default WalletScreen;