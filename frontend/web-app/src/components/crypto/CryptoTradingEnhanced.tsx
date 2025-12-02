import React, { useState, useEffect, useCallback } from 'react';
import {
  Box,
  Typography,
  Card,
  CardContent,
  Grid,
  Button,
  TextField,
  Tab,
  Tabs,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  Paper,
  Chip,
  Avatar,
  IconButton,
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  List,
  ListItem,
  ListItemIcon,
  ListItemText,
  ListItemSecondaryAction,
  Divider,
  Alert,
  LinearProgress,
  CircularProgress,
  FormControl,
  InputLabel,
  Select,
  MenuItem,
  Switch,
  FormControlLabel,
  Tooltip,
  Badge,
  Accordion,
  AccordionSummary,
  AccordionDetails,
  Slider,
  ToggleButton,
  ToggleButtonGroup,
  InputAdornment,
} from '@mui/material';
import TrendingUpIcon from '@mui/icons-material/TrendingUp';
import TrendingDownIcon from '@mui/icons-material/TrendingDown';
import TrendingFlatIcon from '@mui/icons-material/TrendingFlat';
import CurrencyBitcoinIcon from '@mui/icons-material/CurrencyBitcoin';
import AttachMoneyIcon from '@mui/icons-material/AttachMoney';
import SwapHorizIcon from '@mui/icons-material/SwapHoriz';
import ShowChartIcon from '@mui/icons-material/ShowChart';
import AssessmentIcon from '@mui/icons-material/Assessment';
import AccountBalanceWalletIcon from '@mui/icons-material/AccountBalanceWallet';
import HistoryIcon from '@mui/icons-material/History';
import StarIcon from '@mui/icons-material/Star';
import StarBorderIcon from '@mui/icons-material/StarBorder';
import NotificationsIcon from '@mui/icons-material/Notifications';
import SettingsIcon from '@mui/icons-material/Settings';
import InfoIcon from '@mui/icons-material/Info';
import WarningIcon from '@mui/icons-material/Warning';
import CheckCircleIcon from '@mui/icons-material/CheckCircle';
import CancelIcon from '@mui/icons-material/Cancel';
import RefreshIcon from '@mui/icons-material/Refresh';
import ExpandMoreIcon from '@mui/icons-material/ExpandMore';
import TimelineIcon from '@mui/icons-material/Timeline';
import PieChartIcon from '@mui/icons-material/PieChart';
import BarChartIcon from '@mui/icons-material/BarChart';
import CandlestickIcon from '@mui/icons-material/Candlestick';
import AnalyticsIcon from '@mui/icons-material/Analytics';
import SecurityIcon from '@mui/icons-material/Security';
import ScheduleIcon from '@mui/icons-material/Schedule';
import PlayArrowIcon from '@mui/icons-material/PlayArrow';
import PauseIcon from '@mui/icons-material/Pause';
import StopIcon from '@mui/icons-material/Stop';;
import {
  LineChart,
  Line,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip as RechartsTooltip,
  ResponsiveContainer,
  AreaChart,
  Area,
  PieChart as RechartsPieChart,
  Cell,
  BarChart as RechartsBarChart,
  Bar,
} from 'recharts';
import { format } from 'date-fns';
import { useAuth } from '@/contexts/AuthContext';
import toast from 'react-hot-toast';

interface CryptoCurrency {
  id: string;
  symbol: string;
  name: string;
  price: number;
  change24h: number;
  volume24h: number;
  marketCap: number;
  icon: string;
  balance?: number;
  balanceUSD?: number;
}

interface TradingOrder {
  id: string;
  type: 'buy' | 'sell';
  orderType: 'market' | 'limit' | 'stop';
  symbol: string;
  amount: number;
  price: number;
  status: 'pending' | 'filled' | 'cancelled' | 'partial';
  timestamp: Date;
  filled: number;
  remaining: number;
}

interface Transaction {
  id: string;
  type: 'buy' | 'sell' | 'send' | 'receive' | 'stake' | 'unstake';
  symbol: string;
  amount: number;
  price: number;
  fee: number;
  status: 'completed' | 'pending' | 'failed';
  timestamp: Date;
  txHash?: string;
}

interface TabPanelProps {
  children?: React.ReactNode;
  index: number;
  value: number;
}

function TabPanel(props: TabPanelProps) {
  const { children, value, index, ...other } = props;
  return (
    <div
      role="tabpanel"
      hidden={value !== index}
      id={`crypto-tabpanel-${index}`}
      aria-labelledby={`crypto-tab-${index}`}
      {...other}
    >
      {value === index && <Box sx={{ pt: 3 }}>{children}</Box>}
    </div>
  );
}

const CryptoTradingEnhanced: React.FC = () => {
  const { user } = useAuth();
  const [activeTab, setActiveTab] = useState(0);
  const [selectedCrypto, setSelectedCrypto] = useState<CryptoCurrency | null>(null);
  const [showTradeDialog, setShowTradeDialog] = useState(false);
  const [showPortfolioDialog, setShowPortfolioDialog] = useState(false);
  const [tradeType, setTradeType] = useState<'buy' | 'sell'>('buy');
  const [orderType, setOrderType] = useState<'market' | 'limit' | 'stop'>('market');
  const [tradeAmount, setTradeAmount] = useState('');
  const [tradePrice, setTradePrice] = useState('');
  const [watchlist, setWatchlist] = useState<string[]>(['BTC', 'ETH', 'ADA']);
  const [showAdvanced, setShowAdvanced] = useState(false);
  const [autoRefresh, setAutoRefresh] = useState(true);
  const [chartTimeframe, setChartTimeframe] = useState('1D');
  const [loading, setLoading] = useState(false);

  // Mock data
  const [cryptos, setCryptos] = useState<CryptoCurrency[]>([
    {
      id: 'bitcoin',
      symbol: 'BTC',
      name: 'Bitcoin',
      price: 43250.75,
      change24h: 2.45,
      volume24h: 28500000000,
      marketCap: 847000000000,
      icon: '₿',
      balance: 0.0245,
      balanceUSD: 1059.64,
    },
    {
      id: 'ethereum',
      symbol: 'ETH',
      name: 'Ethereum',
      price: 2675.32,
      change24h: -1.23,
      volume24h: 15200000000,
      marketCap: 321000000000,
      icon: 'Ξ',
      balance: 1.567,
      balanceUSD: 4192.82,
    },
    {
      id: 'cardano',
      symbol: 'ADA',
      name: 'Cardano',
      price: 0.485,
      change24h: 5.67,
      volume24h: 890000000,
      marketCap: 17200000000,
      icon: '₳',
      balance: 2500,
      balanceUSD: 1212.50,
    },
    {
      id: 'solana',
      symbol: 'SOL',
      name: 'Solana',
      price: 98.45,
      change24h: 3.21,
      volume24h: 2100000000,
      marketCap: 42500000000,
      icon: '◎',
      balance: 12.5,
      balanceUSD: 1230.63,
    },
    {
      id: 'polkadot',
      symbol: 'DOT',
      name: 'Polkadot',
      price: 7.82,
      change24h: -2.15,
      volume24h: 340000000,
      marketCap: 9800000000,
      icon: '●',
      balance: 150,
      balanceUSD: 1173.00,
    },
  ]);

  const [orders, setOrders] = useState<TradingOrder[]>([
    {
      id: '1',
      type: 'buy',
      orderType: 'limit',
      symbol: 'BTC',
      amount: 0.1,
      price: 42000,
      status: 'pending',
      timestamp: new Date(),
      filled: 0,
      remaining: 0.1,
    },
    {
      id: '2',
      type: 'sell',
      orderType: 'market',
      symbol: 'ETH',
      amount: 0.5,
      price: 2675.32,
      status: 'filled',
      timestamp: new Date(Date.now() - 3600000),
      filled: 0.5,
      remaining: 0,
    },
  ]);

  const [transactions, setTransactions] = useState<Transaction[]>([
    {
      id: '1',
      type: 'buy',
      symbol: 'BTC',
      amount: 0.0245,
      price: 43000,
      fee: 12.50,
      status: 'completed',
      timestamp: new Date(Date.now() - 86400000),
      txHash: '0x123...abc',
    },
    {
      id: '2',
      type: 'sell',
      symbol: 'ETH',
      amount: 0.5,
      price: 2650,
      fee: 8.25,
      status: 'completed',
      timestamp: new Date(Date.now() - 172800000),
      txHash: '0x456...def',
    },
  ]);

  // Mock chart data
  const chartData = [
    { time: '00:00', price: 42800, volume: 1200000 },
    { time: '04:00', price: 43100, volume: 1500000 },
    { time: '08:00', price: 42900, volume: 1800000 },
    { time: '12:00', price: 43200, volume: 2100000 },
    { time: '16:00', price: 43350, volume: 1900000 },
    { time: '20:00', price: 43250, volume: 1600000 },
  ];

  const portfolioData = cryptos.filter(c => c.balance && c.balance > 0).map(c => ({
    name: c.symbol,
    value: c.balanceUSD || 0,
    color: getColorForCrypto(c.symbol),
  }));

  function getColorForCrypto(symbol: string) {
    const colors: { [key: string]: string } = {
      BTC: '#f7931a',
      ETH: '#627eea',
      ADA: '#0033ad',
      SOL: '#00d4aa',
      DOT: '#e6007a',
    };
    return colors[symbol] || '#8884d8';
  }

  // Auto-refresh prices
  useEffect(() => {
    if (!autoRefresh) return;

    const interval = setInterval(() => {
      setCryptos(prev => prev.map(crypto => ({
        ...crypto,
        price: crypto.price * (1 + (Math.random() - 0.5) * 0.02), // ±1% random change
        change24h: crypto.change24h + (Math.random() - 0.5) * 0.5,
      })));
    }, 5000);

    return () => clearInterval(interval);
  }, [autoRefresh]);

  const handleTabChange = (event: React.SyntheticEvent, newValue: number) => {
    setActiveTab(newValue);
  };

  const handleToggleWatchlist = (symbol: string) => {
    setWatchlist(prev => {
      if (prev.includes(symbol)) {
        return prev.filter(s => s !== symbol);
      } else {
        return [...prev, symbol];
      }
    });
  };

  const handleTrade = async () => {
    if (!selectedCrypto || !tradeAmount) {
      toast.error('Please enter trade details');
      return;
    }

    setLoading(true);
    try {
      const amount = parseFloat(tradeAmount);
      const price = orderType === 'market' ? selectedCrypto.price : parseFloat(tradePrice);
      
      const newOrder: TradingOrder = {
        id: Date.now().toString(),
        type: tradeType,
        orderType,
        symbol: selectedCrypto.symbol,
        amount,
        price,
        status: orderType === 'market' ? 'filled' : 'pending',
        timestamp: new Date(),
        filled: orderType === 'market' ? amount : 0,
        remaining: orderType === 'market' ? 0 : amount,
      };

      setOrders(prev => [newOrder, ...prev]);

      const newTransaction: Transaction = {
        id: Date.now().toString(),
        type: tradeType,
        symbol: selectedCrypto.symbol,
        amount,
        price,
        fee: amount * price * 0.001, // 0.1% fee
        status: 'completed',
        timestamp: new Date(),
      };

      setTransactions(prev => [newTransaction, ...prev]);

      // Update balances
      setCryptos(prev => prev.map(crypto => {
        if (crypto.symbol === selectedCrypto.symbol) {
          const balanceChange = tradeType === 'buy' ? amount : -amount;
          return {
            ...crypto,
            balance: (crypto.balance || 0) + balanceChange,
            balanceUSD: ((crypto.balance || 0) + balanceChange) * crypto.price,
          };
        }
        return crypto;
      }));

      toast.success(`${tradeType.toUpperCase()} order ${orderType === 'market' ? 'executed' : 'placed'} successfully`);
      setShowTradeDialog(false);
      setTradeAmount('');
      setTradePrice('');
    } catch (error) {
      toast.error('Trade failed');
    } finally {
      setLoading(false);
    }
  };

  const handleCancelOrder = (orderId: string) => {
    setOrders(prev => prev.map(order => 
      order.id === orderId ? { ...order, status: 'cancelled' } : order
    ));
    toast.success('Order cancelled');
  };

  const getTotalPortfolioValue = () => {
    return cryptos.reduce((total, crypto) => total + (crypto.balanceUSD || 0), 0);
  };

  const getTotalPortfolioChange = () => {
    const totalValue = getTotalPortfolioValue();
    const totalChange = cryptos.reduce((total, crypto) => {
      const value = crypto.balanceUSD || 0;
      return total + (value * crypto.change24h / 100);
    }, 0);
    return totalValue > 0 ? (totalChange / totalValue) * 100 : 0;
  };

  const renderMarketsTab = () => (
    <Box>
      {/* Market Overview */}
      <Grid container spacing={3} sx={{ mb: 3 }}>
        <Grid item xs={12} md={8}>
          <Card>
            <CardContent>
              <Box display="flex" alignItems="center" justifyContent="between" mb={2}>
                <Typography variant="h6">Market Overview</Typography>
                <Box display="flex" alignItems="center" gap={1}>
                  <FormControlLabel
                    control={
                      <Switch
                        checked={autoRefresh}
                        onChange={(e) => setAutoRefresh(e.target.checked)}
                      />
                    }
                    label="Auto-refresh"
                  />
                  <IconButton onClick={() => window.location.reload()}>
                    <Refresh />
                  </IconButton>
                </Box>
              </Box>
              <TableContainer>
                <Table>
                  <TableHead>
                    <TableRow>
                      <TableCell>Asset</TableCell>
                      <TableCell align="right">Price</TableCell>
                      <TableCell align="right">24h Change</TableCell>
                      <TableCell align="right">Volume</TableCell>
                      <TableCell align="right">Market Cap</TableCell>
                      <TableCell align="center">Actions</TableCell>
                    </TableRow>
                  </TableHead>
                  <TableBody>
                    {cryptos.map((crypto) => (
                      <TableRow key={crypto.id}>
                        <TableCell>
                          <Box display="flex" alignItems="center" gap={2}>
                            <Avatar sx={{ bgcolor: getColorForCrypto(crypto.symbol) }}>
                              {crypto.icon}
                            </Avatar>
                            <Box>
                              <Typography variant="subtitle2">{crypto.name}</Typography>
                              <Typography variant="caption" color="text.secondary">
                                {crypto.symbol}
                              </Typography>
                            </Box>
                          </Box>
                        </TableCell>
                        <TableCell align="right">
                          <Typography variant="body2" fontWeight="medium">
                            ${crypto.price.toLocaleString(undefined, { minimumFractionDigits: 2 })}
                          </Typography>
                        </TableCell>
                        <TableCell align="right">
                          <Box display="flex" alignItems="center" justifyContent="flex-end">
                            {crypto.change24h >= 0 ? <TrendingUp color="success" /> : <TrendingDown color="error" />}
                            <Typography
                              variant="body2"
                              color={crypto.change24h >= 0 ? 'success.main' : 'error.main'}
                              fontWeight="medium"
                            >
                              {crypto.change24h.toFixed(2)}%
                            </Typography>
                          </Box>
                        </TableCell>
                        <TableCell align="right">
                          <Typography variant="body2">
                            ${(crypto.volume24h / 1000000).toFixed(0)}M
                          </Typography>
                        </TableCell>
                        <TableCell align="right">
                          <Typography variant="body2">
                            ${(crypto.marketCap / 1000000000).toFixed(1)}B
                          </Typography>
                        </TableCell>
                        <TableCell align="center">
                          <Box display="flex" gap={0.5}>
                            <Tooltip title={watchlist.includes(crypto.symbol) ? 'Remove from watchlist' : 'Add to watchlist'}>
                              <IconButton
                                size="small"
                                onClick={() => handleToggleWatchlist(crypto.symbol)}
                              >
                                {watchlist.includes(crypto.symbol) ? <Star color="warning" /> : <StarBorder />}
                              </IconButton>
                            </Tooltip>
                            <Button
                              size="small"
                              variant="contained"
                              onClick={() => {
                                setSelectedCrypto(crypto);
                                setShowTradeDialog(true);
                              }}
                            >
                              Trade
                            </Button>
                          </Box>
                        </TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              </TableContainer>
            </CardContent>
          </Card>
        </Grid>
        <Grid item xs={12} md={4}>
          <Card sx={{ mb: 3 }}>
            <CardContent>
              <Typography variant="h6" gutterBottom>Market Stats</Typography>
              <List dense>
                <ListItem>
                  <ListItemText
                    primary="Total Market Cap"
                    secondary="$1.2T"
                  />
                  <Chip label="+2.4%" color="success" size="small" />
                </ListItem>
                <ListItem>
                  <ListItemText
                    primary="24h Volume"
                    secondary="$89.5B"
                  />
                  <Chip label="-0.8%" color="error" size="small" />
                </ListItem>
                <ListItem>
                  <ListItemText
                    primary="BTC Dominance"
                    secondary="52.1%"
                  />
                </ListItem>
                <ListItem>
                  <ListItemText
                    primary="Active Cryptos"
                    secondary="2,847"
                  />
                </ListItem>
              </List>
            </CardContent>
          </Card>

          <Card>
            <CardContent>
              <Typography variant="h6" gutterBottom>Price Alerts</Typography>
              <Alert severity="info" sx={{ mb: 2 }}>
                Set price alerts to get notified when your target prices are reached
              </Alert>
              <Button variant="outlined" fullWidth startIcon={<Notifications />}>
                Manage Alerts
              </Button>
            </CardContent>
          </Card>
        </Grid>
      </Grid>
    </Box>
  );

  const renderPortfolioTab = () => (
    <Box>
      {/* Portfolio Overview */}
      <Grid container spacing={3} sx={{ mb: 3 }}>
        <Grid item xs={12} md={8}>
          <Card>
            <CardContent>
              <Box display="flex" alignItems="center" justifyContent="between" mb={3}>
                <Box>
                  <Typography variant="h4" gutterBottom>
                    ${getTotalPortfolioValue().toLocaleString(undefined, { minimumFractionDigits: 2 })}
                  </Typography>
                  <Box display="flex" alignItems="center" gap={1}>
                    {getTotalPortfolioChange() >= 0 ? (
                      <TrendingUp color="success" />
                    ) : (
                      <TrendingDown color="error" />
                    )}
                    <Typography
                      variant="h6"
                      color={getTotalPortfolioChange() >= 0 ? 'success.main' : 'error.main'}
                    >
                      {getTotalPortfolioChange().toFixed(2)}%
                    </Typography>
                    <Typography variant="body2" color="text.secondary">
                      24h change
                    </Typography>
                  </Box>
                </Box>
                <Button
                  variant="contained"
                  startIcon={<ShowChart />}
                  onClick={() => setShowPortfolioDialog(true)}
                >
                  View Details
                </Button>
              </Box>

              {/* Portfolio Allocation Chart */}
              <Box height={300}>
                <ResponsiveContainer width="100%" height="100%">
                  <RechartsPieChart>
                    <pie
                      data={portfolioData}
                      cx="50%"
                      cy="50%"
                      innerRadius={60}
                      outerRadius={120}
                      paddingAngle={5}
                      dataKey="value"
                    >
                      {portfolioData.map((entry, index) => (
                        <Cell key={`cell-${index}`} fill={entry.color} />
                      ))}
                    </pie>
                    <RechartsTooltip
                      formatter={(value: number) => [
                        `$${value.toLocaleString(undefined, { minimumFractionDigits: 2 })}`,
                        'Value'
                      ]}
                    />
                  </RechartsPieChart>
                </ResponsiveContainer>
              </Box>
            </CardContent>
          </Card>
        </Grid>
        <Grid item xs={12} md={4}>
          <Card>
            <CardContent>
              <Typography variant="h6" gutterBottom>Holdings</Typography>
              <List>
                {cryptos.filter(c => c.balance && c.balance > 0).map((crypto) => (
                  <ListItem key={crypto.id}>
                    <ListItemIcon>
                      <Avatar sx={{ bgcolor: getColorForCrypto(crypto.symbol), width: 32, height: 32 }}>
                        {crypto.icon}
                      </Avatar>
                    </ListItemIcon>
                    <ListItemText
                      primary={
                        <Box display="flex" alignItems="center" gap={1}>
                          <Typography variant="body2">{crypto.symbol}</Typography>
                          <Chip 
                            label={`${crypto.change24h?.toFixed(2)}%`}
                            size="small"
                            color={crypto.change24h >= 0 ? 'success' : 'error'}
                          />
                        </Box>
                      }
                      secondary={`${crypto.balance?.toFixed(6)} ${crypto.symbol}`}
                    />
                    <ListItemSecondaryAction>
                      <Typography variant="body2" fontWeight="medium">
                        ${crypto.balanceUSD?.toLocaleString(undefined, { minimumFractionDigits: 2 })}
                      </Typography>
                    </ListItemSecondaryAction>
                  </ListItem>
                ))}
              </List>
            </CardContent>
          </Card>
        </Grid>
      </Grid>
    </Box>
  );

  const renderTradingTab = () => (
    <Box>
      <Grid container spacing={3}>
        {/* Trading Chart */}
        <Grid item xs={12} md={8}>
          <Card>
            <CardContent>
              <Box display="flex" alignItems="center" justifyContent="between" mb={3}>
                <Typography variant="h6">
                  {selectedCrypto ? `${selectedCrypto.name} (${selectedCrypto.symbol})` : 'Select a cryptocurrency'}
                </Typography>
                <ToggleButtonGroup
                  value={chartTimeframe}
                  exclusive
                  onChange={(e, value) => value && setChartTimeframe(value)}
                  size="small"
                >
                  <ToggleButton value="1H">1H</ToggleButton>
                  <ToggleButton value="1D">1D</ToggleButton>
                  <ToggleButton value="1W">1W</ToggleButton>
                  <ToggleButton value="1M">1M</ToggleButton>
                </ToggleButtonGroup>
              </Box>

              {selectedCrypto && (
                <>
                  <Box display="flex" alignItems="center" gap={2} mb={3}>
                    <Typography variant="h4">
                      ${selectedCrypto.price.toLocaleString(undefined, { minimumFractionDigits: 2 })}
                    </Typography>
                    <Box display="flex" alignItems="center" gap={0.5}>
                      {selectedCrypto.change24h >= 0 ? (
                        <TrendingUp color="success" />
                      ) : (
                        <TrendingDown color="error" />
                      )}
                      <Typography
                        variant="h6"
                        color={selectedCrypto.change24h >= 0 ? 'success.main' : 'error.main'}
                      >
                        {selectedCrypto.change24h.toFixed(2)}%
                      </Typography>
                    </Box>
                  </Box>

                  <Box height={400}>
                    <ResponsiveContainer width="100%" height="100%">
                      <AreaChart data={chartData}>
                        <CartesianGrid strokeDasharray="3 3" />
                        <XAxis dataKey="time" />
                        <YAxis />
                        <RechartsTooltip />
                        <Area
                          type="monotone"
                          dataKey="price"
                          stroke={getColorForCrypto(selectedCrypto.symbol)}
                          fill={getColorForCrypto(selectedCrypto.symbol)}
                          fillOpacity={0.3}
                        />
                      </AreaChart>
                    </ResponsiveContainer>
                  </Box>
                </>
              )}
            </CardContent>
          </Card>
        </Grid>

        {/* Trading Panel */}
        <Grid item xs={12} md={4}>
          <Card>
            <CardContent>
              <Typography variant="h6" gutterBottom>Quick Trade</Typography>
              
              <FormControl fullWidth sx={{ mb: 2 }}>
                <InputLabel>Select Cryptocurrency</InputLabel>
                <Select
                  value={selectedCrypto?.symbol || ''}
                  onChange={(e) => {
                    const crypto = cryptos.find(c => c.symbol === e.target.value);
                    setSelectedCrypto(crypto || null);
                  }}
                >
                  {cryptos.map((crypto) => (
                    <MenuItem key={crypto.id} value={crypto.symbol}>
                      <Box display="flex" alignItems="center" gap={1}>
                        <Avatar sx={{ bgcolor: getColorForCrypto(crypto.symbol), width: 24, height: 24 }}>
                          {crypto.icon}
                        </Avatar>
                        {crypto.name} ({crypto.symbol})
                      </Box>
                    </MenuItem>
                  ))}
                </Select>
              </FormControl>

              {selectedCrypto && (
                <>
                  <ToggleButtonGroup
                    value={tradeType}
                    exclusive
                    onChange={(e, value) => value && setTradeType(value)}
                    fullWidth
                    sx={{ mb: 2 }}
                  >
                    <ToggleButton value="buy" color="success">
                      Buy
                    </ToggleButton>
                    <ToggleButton value="sell" color="error">
                      Sell
                    </ToggleButton>
                  </ToggleButtonGroup>

                  <FormControl fullWidth sx={{ mb: 2 }}>
                    <InputLabel>Order Type</InputLabel>
                    <Select
                      value={orderType}
                      onChange={(e) => setOrderType(e.target.value as any)}
                    >
                      <MenuItem value="market">Market Order</MenuItem>
                      <MenuItem value="limit">Limit Order</MenuItem>
                      <MenuItem value="stop">Stop Order</MenuItem>
                    </Select>
                  </FormControl>

                  <TextField
                    fullWidth
                    label={`Amount (${selectedCrypto.symbol})`}
                    value={tradeAmount}
                    onChange={(e) => setTradeAmount(e.target.value)}
                    type="number"
                    sx={{ mb: 2 }}
                    InputProps={{
                      endAdornment: (
                        <InputAdornment position="end">
                          {selectedCrypto.symbol}
                        </InputAdornment>
                      ),
                    }}
                  />

                  {orderType !== 'market' && (
                    <TextField
                      fullWidth
                      label="Price (USD)"
                      value={tradePrice}
                      onChange={(e) => setTradePrice(e.target.value)}
                      type="number"
                      sx={{ mb: 2 }}
                      InputProps={{
                        startAdornment: (
                          <InputAdornment position="start">$</InputAdornment>
                        ),
                      }}
                    />
                  )}

                  <Box sx={{ mb: 2 }}>
                    <Typography variant="body2" color="text.secondary">
                      Available: {selectedCrypto.balance?.toFixed(6)} {selectedCrypto.symbol}
                    </Typography>
                    <Typography variant="body2" color="text.secondary">
                      Est. Total: ${tradeAmount && (parseFloat(tradeAmount) * selectedCrypto.price).toFixed(2)}
                    </Typography>
                  </Box>

                  <Button
                    fullWidth
                    variant="contained"
                    size="large"
                    onClick={handleTrade}
                    disabled={!tradeAmount || loading}
                    color={tradeType === 'buy' ? 'success' : 'error'}
                  >
                    {loading ? <CircularProgress size={20} /> : `${tradeType.toUpperCase()} ${selectedCrypto.symbol}`}
                  </Button>
                </>
              )}
            </CardContent>
          </Card>

          {/* Order Book Preview */}
          <Card sx={{ mt: 2 }}>
            <CardContent>
              <Typography variant="h6" gutterBottom>Order Book</Typography>
              <Box display="flex" justifyContent="between" mb={1}>
                <Typography variant="body2" color="text.secondary">Price</Typography>
                <Typography variant="body2" color="text.secondary">Amount</Typography>
              </Box>
              
              {/* Sell Orders */}
              <Box sx={{ mb: 2 }}>
                <Typography variant="caption" color="error.main">SELL ORDERS</Typography>
                {[...Array(3)].map((_, i) => (
                  <Box key={i} display="flex" justifyContent="between" py={0.5}>
                    <Typography variant="body2" color="error.main">
                      ${selectedCrypto ? (selectedCrypto.price + (i + 1) * 10).toFixed(2) : '0.00'}
                    </Typography>
                    <Typography variant="body2">
                      {(Math.random() * 10).toFixed(4)}
                    </Typography>
                  </Box>
                ))}
              </Box>

              <Divider />

              {/* Buy Orders */}
              <Box sx={{ mt: 2 }}>
                <Typography variant="caption" color="success.main">BUY ORDERS</Typography>
                {[...Array(3)].map((_, i) => (
                  <Box key={i} display="flex" justifyContent="between" py={0.5}>
                    <Typography variant="body2" color="success.main">
                      ${selectedCrypto ? (selectedCrypto.price - (i + 1) * 10).toFixed(2) : '0.00'}
                    </Typography>
                    <Typography variant="body2">
                      {(Math.random() * 10).toFixed(4)}
                    </Typography>
                  </Box>
                ))}
              </Box>
            </CardContent>
          </Card>
        </Grid>
      </Grid>
    </Box>
  );

  const renderOrdersTab = () => (
    <Box>
      <Typography variant="h6" gutterBottom>Active Orders</Typography>
      <Card sx={{ mb: 3 }}>
        <TableContainer>
          <Table>
            <TableHead>
              <TableRow>
                <TableCell>Type</TableCell>
                <TableCell>Asset</TableCell>
                <TableCell align="right">Amount</TableCell>
                <TableCell align="right">Price</TableCell>
                <TableCell align="right">Filled</TableCell>
                <TableCell>Status</TableCell>
                <TableCell>Time</TableCell>
                <TableCell align="center">Action</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {orders.map((order) => (
                <TableRow key={order.id}>
                  <TableCell>
                    <Chip
                      label={`${order.type.toUpperCase()} ${order.orderType.toUpperCase()}`}
                      color={order.type === 'buy' ? 'success' : 'error'}
                      size="small"
                    />
                  </TableCell>
                  <TableCell>{order.symbol}</TableCell>
                  <TableCell align="right">{order.amount.toFixed(6)}</TableCell>
                  <TableCell align="right">${order.price.toFixed(2)}</TableCell>
                  <TableCell align="right">
                    {order.filled > 0 && (
                      <Box>
                        <Typography variant="body2">
                          {((order.filled / order.amount) * 100).toFixed(1)}%
                        </Typography>
                        <LinearProgress
                          variant="determinate"
                          value={(order.filled / order.amount) * 100}
                          sx={{ width: 60, height: 4 }}
                        />
                      </Box>
                    )}
                  </TableCell>
                  <TableCell>
                    <Chip
                      label={order.status}
                      color={
                        order.status === 'filled' ? 'success' :
                        order.status === 'cancelled' ? 'error' : 'warning'
                      }
                      size="small"
                    />
                  </TableCell>
                  <TableCell>
                    {format(order.timestamp, 'MMM dd, HH:mm')}
                  </TableCell>
                  <TableCell align="center">
                    {order.status === 'pending' && (
                      <Button
                        size="small"
                        color="error"
                        onClick={() => handleCancelOrder(order.id)}
                      >
                        Cancel
                      </Button>
                    )}
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </TableContainer>
      </Card>

      <Typography variant="h6" gutterBottom>Transaction History</Typography>
      <Card>
        <TableContainer>
          <Table>
            <TableHead>
              <TableRow>
                <TableCell>Type</TableCell>
                <TableCell>Asset</TableCell>
                <TableCell align="right">Amount</TableCell>
                <TableCell align="right">Price</TableCell>
                <TableCell align="right">Fee</TableCell>
                <TableCell>Status</TableCell>
                <TableCell>Time</TableCell>
                <TableCell>Tx Hash</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {transactions.map((tx) => (
                <TableRow key={tx.id}>
                  <TableCell>
                    <Chip
                      label={tx.type.toUpperCase()}
                      color={
                        tx.type === 'buy' || tx.type === 'receive' ? 'success' :
                        tx.type === 'sell' || tx.type === 'send' ? 'error' : 'info'
                      }
                      size="small"
                    />
                  </TableCell>
                  <TableCell>{tx.symbol}</TableCell>
                  <TableCell align="right">{tx.amount.toFixed(6)}</TableCell>
                  <TableCell align="right">${tx.price.toFixed(2)}</TableCell>
                  <TableCell align="right">${tx.fee.toFixed(2)}</TableCell>
                  <TableCell>
                    <Chip
                      label={tx.status}
                      color={
                        tx.status === 'completed' ? 'success' :
                        tx.status === 'failed' ? 'error' : 'warning'
                      }
                      size="small"
                    />
                  </TableCell>
                  <TableCell>
                    {format(tx.timestamp, 'MMM dd, HH:mm')}
                  </TableCell>
                  <TableCell>
                    {tx.txHash && (
                      <Tooltip title="View on blockchain explorer">
                        <Button size="small" variant="outlined">
                          {tx.txHash.slice(0, 8)}...
                        </Button>
                      </Tooltip>
                    )}
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </TableContainer>
      </Card>
    </Box>
  );

  return (
    <Box>
      <Typography variant="h4" gutterBottom>
        Cryptocurrency Trading
      </Typography>
      <Typography variant="body1" color="text.secondary" paragraph>
        Trade cryptocurrencies, manage your portfolio, and track market movements
      </Typography>

      <Paper sx={{ width: '100%' }}>
        <Tabs
          value={activeTab}
          onChange={handleTabChange}
          variant="scrollable"
          scrollButtons="auto"
        >
          <Tab label="Markets" icon={<ShowChart />} />
          <Tab label="Portfolio" icon={<PieChart />} />
          <Tab label="Trading" icon={<SwapHoriz />} />
          <Tab label="Orders & History" icon={<History />} />
        </Tabs>

        <TabPanel value={activeTab} index={0}>
          {renderMarketsTab()}
        </TabPanel>

        <TabPanel value={activeTab} index={1}>
          {renderPortfolioTab()}
        </TabPanel>

        <TabPanel value={activeTab} index={2}>
          {renderTradingTab()}
        </TabPanel>

        <TabPanel value={activeTab} index={3}>
          {renderOrdersTab()}
        </TabPanel>
      </Paper>

      {/* Trade Dialog */}
      <Dialog
        open={showTradeDialog}
        onClose={() => setShowTradeDialog(false)}
        maxWidth="sm"
        fullWidth
      >
        <DialogTitle>
          Trade {selectedCrypto?.name} ({selectedCrypto?.symbol})
        </DialogTitle>
        <DialogContent>
          <Box sx={{ pt: 2 }}>
            <Alert severity="info" sx={{ mb: 3 }}>
              Current price: ${selectedCrypto?.price.toFixed(2)} | 24h change: {selectedCrypto?.change24h.toFixed(2)}%
            </Alert>
            
            {/* Trade form would go here - same as in trading tab */}
          </Box>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setShowTradeDialog(false)}>Cancel</Button>
          <Button variant="contained" onClick={handleTrade} disabled={loading}>
            {loading ? <CircularProgress size={20} /> : `${tradeType.toUpperCase()}`}
          </Button>
        </DialogActions>
      </Dialog>

      {/* Portfolio Details Dialog */}
      <Dialog
        open={showPortfolioDialog}
        onClose={() => setShowPortfolioDialog(false)}
        maxWidth="md"
        fullWidth
      >
        <DialogTitle>Portfolio Details</DialogTitle>
        <DialogContent>
          <Box sx={{ pt: 2 }}>
            <Grid container spacing={3}>
              <Grid item xs={12} md={6}>
                <Typography variant="h6" gutterBottom>Portfolio Allocation</Typography>
                <Box height={300}>
                  <ResponsiveContainer width="100%" height="100%">
                    <RechartsPieChart>
                      <pie
                        data={portfolioData}
                        cx="50%"
                        cy="50%"
                        innerRadius={60}
                        outerRadius={120}
                        paddingAngle={5}
                        dataKey="value"
                      >
                        {portfolioData.map((entry, index) => (
                          <Cell key={`cell-${index}`} fill={entry.color} />
                        ))}
                      </pie>
                      <RechartsTooltip />
                    </RechartsPieChart>
                  </ResponsiveContainer>
                </Box>
              </Grid>
              <Grid item xs={12} md={6}>
                <Typography variant="h6" gutterBottom>Performance Metrics</Typography>
                <List>
                  <ListItem>
                    <ListItemText
                      primary="Total Value"
                      secondary={`$${getTotalPortfolioValue().toFixed(2)}`}
                    />
                  </ListItem>
                  <ListItem>
                    <ListItemText
                      primary="24h Change"
                      secondary={`${getTotalPortfolioChange().toFixed(2)}%`}
                    />
                  </ListItem>
                  <ListItem>
                    <ListItemText
                      primary="Best Performer"
                      secondary="ADA (+5.67%)"
                    />
                  </ListItem>
                  <ListItem>
                    <ListItemText
                      primary="Worst Performer"
                      secondary="DOT (-2.15%)"
                    />
                  </ListItem>
                </List>
              </Grid>
            </Grid>
          </Box>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setShowPortfolioDialog(false)}>Close</Button>
        </DialogActions>
      </Dialog>
    </Box>
  );
};

export default CryptoTradingEnhanced;