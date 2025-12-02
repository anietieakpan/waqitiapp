import React, { useState, useMemo } from 'react';
import {
  Box,
  Grid,
  Paper,
  Typography,
  Card,
  CardContent,
  Button,
  IconButton,
  Chip,
  Avatar,
  Tabs,
  Tab,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  TextField,
  InputAdornment,
  Select,
  MenuItem,
  FormControl,
  InputLabel,
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  Slider,
  Alert,
  Badge,
  Stack,
  Divider,
  LinearProgress,
  Tooltip,
  List,
  ListItem,
  ListItemText,
  ListItemAvatar,
  ListItemSecondaryAction,
  ToggleButton,
  ToggleButtonGroup,
  useTheme,
  alpha,
} from '@mui/material';
import TrendingUpIcon from '@mui/icons-material/TrendingUp';
import TrendingDownIcon from '@mui/icons-material/TrendingDown';
import AttachMoneyIcon from '@mui/icons-material/AttachMoney';
import ShowChartIcon from '@mui/icons-material/ShowChart';
import AccountBalanceWalletIcon from '@mui/icons-material/AccountBalanceWallet';
import SwapHorizIcon from '@mui/icons-material/SwapHoriz';
import AddIcon from '@mui/icons-material/Add';
import RemoveIcon from '@mui/icons-material/Remove';
import StarIcon from '@mui/icons-material/Star';
import StarBorderIcon from '@mui/icons-material/StarBorder';
import SearchIcon from '@mui/icons-material/Search';
import FilterListIcon from '@mui/icons-material/FilterList';
import ArrowUpwardIcon from '@mui/icons-material/ArrowUpward';
import ArrowDownwardIcon from '@mui/icons-material/ArrowDownward';
import InfoIcon from '@mui/icons-material/Info';
import WarningIcon from '@mui/icons-material/Warning';
import CheckCircleIcon from '@mui/icons-material/CheckCircle';
import CancelIcon from '@mui/icons-material/Cancel';
import ScheduleIcon from '@mui/icons-material/Schedule';
import LocalFireDepartmentIcon from '@mui/icons-material/LocalFireDepartment';
import BoltIcon from '@mui/icons-material/Bolt';
import DiamondIcon from '@mui/icons-material/Diamond';
import CurrencyBitcoinIcon from '@mui/icons-material/CurrencyBitcoin';
import CandlestickChartIcon from '@mui/icons-material/CandlestickChart';
import AnalyticsIcon from '@mui/icons-material/Analytics';
import NotificationsIcon from '@mui/icons-material/Notifications';
import SecurityIcon from '@mui/icons-material/Security';
import SpeedIcon from '@mui/icons-material/Speed';
import BarChartIcon from '@mui/icons-material/BarChart';
import PieChartIcon from '@mui/icons-material/PieChart';;
import { format } from 'date-fns';

interface CryptoAsset {
  id: string;
  symbol: string;
  name: string;
  icon: string;
  price: number;
  change24h: number;
  change7d: number;
  marketCap: number;
  volume24h: number;
  holdings: number;
  value: number;
  avgBuyPrice: number;
  profitLoss: number;
  profitLossPercent: number;
  isFavorite: boolean;
}

interface Transaction {
  id: string;
  type: 'buy' | 'sell' | 'convert';
  cryptoSymbol: string;
  cryptoAmount: number;
  fiatAmount: number;
  price: number;
  fee: number;
  status: 'completed' | 'pending' | 'failed';
  timestamp: string;
}

interface MarketStats {
  totalMarketCap: number;
  totalVolume24h: number;
  btcDominance: number;
  altcoinMarketCap: number;
  fearGreedIndex: number;
  topGainers: CryptoAsset[];
  topLosers: CryptoAsset[];
}

const mockCryptoAssets: CryptoAsset[] = [
  {
    id: '1',
    symbol: 'BTC',
    name: 'Bitcoin',
    icon: '₿',
    price: 43250.00,
    change24h: 2.5,
    change7d: 8.3,
    marketCap: 845000000000,
    volume24h: 28500000000,
    holdings: 0.5,
    value: 21625.00,
    avgBuyPrice: 38000.00,
    profitLoss: 2625.00,
    profitLossPercent: 13.8,
    isFavorite: true,
  },
  {
    id: '2',
    symbol: 'ETH',
    name: 'Ethereum',
    icon: 'Ξ',
    price: 2250.00,
    change24h: 3.2,
    change7d: 12.5,
    marketCap: 270000000000,
    volume24h: 15000000000,
    holdings: 2.5,
    value: 5625.00,
    avgBuyPrice: 1800.00,
    profitLoss: 1125.00,
    profitLossPercent: 25.0,
    isFavorite: true,
  },
  {
    id: '3',
    symbol: 'SOL',
    name: 'Solana',
    icon: '◎',
    price: 98.50,
    change24h: -1.8,
    change7d: 15.2,
    marketCap: 42000000000,
    volume24h: 2500000000,
    holdings: 50,
    value: 4925.00,
    avgBuyPrice: 65.00,
    profitLoss: 1675.00,
    profitLossPercent: 51.5,
    isFavorite: false,
  },
  {
    id: '4',
    symbol: 'MATIC',
    name: 'Polygon',
    icon: 'Ⓜ',
    price: 0.85,
    change24h: 1.2,
    change7d: 5.8,
    marketCap: 7800000000,
    volume24h: 450000000,
    holdings: 1000,
    value: 850.00,
    avgBuyPrice: 0.95,
    profitLoss: -100.00,
    profitLossPercent: -10.5,
    isFavorite: false,
  },
];

const mockTransactions: Transaction[] = [
  {
    id: 't1',
    type: 'buy',
    cryptoSymbol: 'BTC',
    cryptoAmount: 0.1,
    fiatAmount: 4325.00,
    price: 43250.00,
    fee: 10.00,
    status: 'completed',
    timestamp: '2024-01-20T10:30:00Z',
  },
  {
    id: 't2',
    type: 'sell',
    cryptoSymbol: 'ETH',
    cryptoAmount: 0.5,
    fiatAmount: 1125.00,
    price: 2250.00,
    fee: 5.00,
    status: 'completed',
    timestamp: '2024-01-19T15:45:00Z',
  },
  {
    id: 't3',
    type: 'convert',
    cryptoSymbol: 'SOL',
    cryptoAmount: 10,
    fiatAmount: 985.00,
    price: 98.50,
    fee: 2.00,
    status: 'pending',
    timestamp: '2024-01-18T09:15:00Z',
  },
];

const mockMarketStats: MarketStats = {
  totalMarketCap: 1650000000000,
  totalVolume24h: 85000000000,
  btcDominance: 51.2,
  altcoinMarketCap: 804000000000,
  fearGreedIndex: 65,
  topGainers: mockCryptoAssets.filter(a => a.change24h > 0).slice(0, 3),
  topLosers: mockCryptoAssets.filter(a => a.change24h < 0).slice(0, 3),
};

interface TabPanelProps {
  children?: React.ReactNode;
  index: number;
  value: number;
}

const TabPanel: React.FC<TabPanelProps> = ({ children, value, index }) => (
  <Box hidden={value !== index} pt={3}>
    {value === index && children}
  </Box>
);

const CryptoTradingDashboard: React.FC = () => {
  const theme = useTheme();
  const [activeTab, setActiveTab] = useState(0);
  const [selectedCrypto, setSelectedCrypto] = useState<CryptoAsset | null>(null);
  const [showBuyDialog, setShowBuyDialog] = useState(false);
  const [showSellDialog, setShowSellDialog] = useState(false);
  const [timeRange, setTimeRange] = useState('24h');
  const [searchTerm, setSearchTerm] = useState('');
  const [sortBy, setSortBy] = useState<'price' | 'change' | 'holdings'>('holdings');
  const [viewMode, setViewMode] = useState<'grid' | 'list'>('grid');

  const [buyAmount, setBuyAmount] = useState('');
  const [sellAmount, setSellAmount] = useState('');

  const portfolio = useMemo(() => {
    const totalValue = mockCryptoAssets.reduce((sum, asset) => sum + asset.value, 0);
    const totalProfitLoss = mockCryptoAssets.reduce((sum, asset) => sum + asset.profitLoss, 0);
    const totalProfitLossPercent = (totalProfitLoss / (totalValue - totalProfitLoss)) * 100;

    return {
      totalValue,
      totalProfitLoss,
      totalProfitLossPercent,
      assetCount: mockCryptoAssets.filter(a => a.holdings > 0).length,
    };
  }, []);

  const filteredAssets = useMemo(() => {
    let filtered = mockCryptoAssets.filter(asset =>
      asset.name.toLowerCase().includes(searchTerm.toLowerCase()) ||
      asset.symbol.toLowerCase().includes(searchTerm.toLowerCase())
    );

    filtered.sort((a, b) => {
      switch (sortBy) {
        case 'price':
          return b.price - a.price;
        case 'change':
          return b.change24h - a.change24h;
        case 'holdings':
          return b.value - a.value;
        default:
          return 0;
      }
    });

    return filtered;
  }, [searchTerm, sortBy]);

  const getFearGreedColor = (index: number) => {
    if (index < 25) return theme.palette.error.main;
    if (index < 50) return theme.palette.warning.main;
    if (index < 75) return theme.palette.success.main;
    return theme.palette.success.dark;
  };

  const getFearGreedLabel = (index: number) => {
    if (index < 25) return 'Extreme Fear';
    if (index < 50) return 'Fear';
    if (index < 75) return 'Greed';
    return 'Extreme Greed';
  };

  const handleBuy = () => {
    // Handle buy transaction
    setShowBuyDialog(false);
    setBuyAmount('');
  };

  const handleSell = () => {
    // Handle sell transaction
    setShowSellDialog(false);
    setSellAmount('');
  };

  const renderPortfolioCard = () => (
    <Card>
      <CardContent>
        <Typography variant="h6" gutterBottom>
          Portfolio Overview
        </Typography>
        <Grid container spacing={3}>
          <Grid item xs={6}>
            <Typography variant="body2" color="text.secondary">
              Total Value
            </Typography>
            <Typography variant="h4" fontWeight="bold">
              ${portfolio.totalValue.toLocaleString()}
            </Typography>
          </Grid>
          <Grid item xs={6}>
            <Typography variant="body2" color="text.secondary">
              Total Profit/Loss
            </Typography>
            <Box display="flex" alignItems="center" gap={1}>
              {portfolio.totalProfitLoss >= 0 ? (
                <TrendingUp color="success" />
              ) : (
                <TrendingDown color="error" />
              )}
              <Typography
                variant="h5"
                fontWeight="bold"
                color={portfolio.totalProfitLoss >= 0 ? 'success.main' : 'error.main'}
              >
                ${Math.abs(portfolio.totalProfitLoss).toLocaleString()}
              </Typography>
              <Chip
                label={`${portfolio.totalProfitLossPercent.toFixed(1)}%`}
                color={portfolio.totalProfitLoss >= 0 ? 'success' : 'error'}
                size="small"
              />
            </Box>
          </Grid>
          <Grid item xs={12}>
            <Divider sx={{ my: 1 }} />
          </Grid>
          <Grid item xs={4}>
            <Box textAlign="center">
              <AccountBalanceWallet color="primary" />
              <Typography variant="body2" color="text.secondary">
                Assets
              </Typography>
              <Typography variant="h6">{portfolio.assetCount}</Typography>
            </Box>
          </Grid>
          <Grid item xs={4}>
            <Box textAlign="center">
              <SwapHoriz color="primary" />
              <Typography variant="body2" color="text.secondary">
                24h Change
              </Typography>
              <Typography variant="h6" color="success.main">
                +$425
              </Typography>
            </Box>
          </Grid>
          <Grid item xs={4}>
            <Box textAlign="center">
              <ShowChart color="primary" />
              <Typography variant="body2" color="text.secondary">
                Best Performer
              </Typography>
              <Typography variant="h6">SOL</Typography>
            </Box>
          </Grid>
        </Grid>
      </CardContent>
    </Card>
  );

  const renderAssetCard = (asset: CryptoAsset) => (
    <Card key={asset.id} sx={{ height: '100%' }}>
      <CardContent>
        <Box display="flex" justifyContent="space-between" alignItems="flex-start" mb={2}>
          <Box display="flex" alignItems="center" gap={1}>
            <Avatar sx={{ bgcolor: theme.palette.primary.main, width: 40, height: 40 }}>
              {asset.icon}
            </Avatar>
            <Box>
              <Typography variant="subtitle1" fontWeight="bold">
                {asset.symbol}
              </Typography>
              <Typography variant="caption" color="text.secondary">
                {asset.name}
              </Typography>
            </Box>
          </Box>
          <IconButton
            size="small"
            onClick={() => {
              // Toggle favorite
            }}
          >
            {asset.isFavorite ? <Star color="warning" /> : <StarBorder />}
          </IconButton>
        </Box>

        <Typography variant="h5" fontWeight="bold" mb={1}>
          ${asset.price.toLocaleString()}
        </Typography>

        <Box display="flex" alignItems="center" gap={1} mb={2}>
          {asset.change24h >= 0 ? (
            <ArrowUpward color="success" fontSize="small" />
          ) : (
            <ArrowDownward color="error" fontSize="small" />
          )}
          <Typography
            variant="body2"
            color={asset.change24h >= 0 ? 'success.main' : 'error.main'}
          >
            {Math.abs(asset.change24h).toFixed(2)}% (24h)
          </Typography>
        </Box>

        {asset.holdings > 0 && (
          <>
            <Divider sx={{ my: 2 }} />
            <Box>
              <Box display="flex" justifyContent="space-between" mb={1}>
                <Typography variant="body2" color="text.secondary">
                  Holdings
                </Typography>
                <Typography variant="body2">
                  {asset.holdings} {asset.symbol}
                </Typography>
              </Box>
              <Box display="flex" justifyContent="space-between" mb={1}>
                <Typography variant="body2" color="text.secondary">
                  Value
                </Typography>
                <Typography variant="body2" fontWeight="bold">
                  ${asset.value.toLocaleString()}
                </Typography>
              </Box>
              <Box display="flex" justifyContent="space-between">
                <Typography variant="body2" color="text.secondary">
                  P/L
                </Typography>
                <Typography
                  variant="body2"
                  fontWeight="bold"
                  color={asset.profitLoss >= 0 ? 'success.main' : 'error.main'}
                >
                  {asset.profitLoss >= 0 ? '+' : ''}${Math.abs(asset.profitLoss).toFixed(2)} ({asset.profitLossPercent.toFixed(1)}%)
                </Typography>
              </Box>
            </Box>
          </>
        )}

        <Box display="flex" gap={1} mt={2}>
          <Button
            variant="contained"
            size="small"
            fullWidth
            onClick={() => {
              setSelectedCrypto(asset);
              setShowBuyDialog(true);
            }}
          >
            Buy
          </Button>
          {asset.holdings > 0 && (
            <Button
              variant="outlined"
              size="small"
              fullWidth
              onClick={() => {
                setSelectedCrypto(asset);
                setShowSellDialog(true);
              }}
            >
              Sell
            </Button>
          )}
        </Box>
      </CardContent>
    </Card>
  );

  const renderMarketOverview = () => (
    <Grid container spacing={3}>
      <Grid item xs={12} md={8}>
        <Card>
          <CardContent>
            <Typography variant="h6" gutterBottom>
              Market Overview
            </Typography>
            <Grid container spacing={2}>
              <Grid item xs={6} sm={3}>
                <Typography variant="body2" color="text.secondary">
                  Market Cap
                </Typography>
                <Typography variant="h6">
                  ${(mockMarketStats.totalMarketCap / 1000000000000).toFixed(2)}T
                </Typography>
              </Grid>
              <Grid item xs={6} sm={3}>
                <Typography variant="body2" color="text.secondary">
                  24h Volume
                </Typography>
                <Typography variant="h6">
                  ${(mockMarketStats.totalVolume24h / 1000000000).toFixed(0)}B
                </Typography>
              </Grid>
              <Grid item xs={6} sm={3}>
                <Typography variant="body2" color="text.secondary">
                  BTC Dominance
                </Typography>
                <Typography variant="h6">
                  {mockMarketStats.btcDominance.toFixed(1)}%
                </Typography>
              </Grid>
              <Grid item xs={6} sm={3}>
                <Typography variant="body2" color="text.secondary">
                  Altcoin Cap
                </Typography>
                <Typography variant="h6">
                  ${(mockMarketStats.altcoinMarketCap / 1000000000000).toFixed(2)}T
                </Typography>
              </Grid>
            </Grid>

            <Box mt={3}>
              <Typography variant="subtitle2" gutterBottom>
                Fear & Greed Index
              </Typography>
              <Box display="flex" alignItems="center" gap={2}>
                <Box sx={{ flex: 1 }}>
                  <LinearProgress
                    variant="determinate"
                    value={mockMarketStats.fearGreedIndex}
                    sx={{
                      height: 10,
                      borderRadius: 5,
                      backgroundColor: alpha(theme.palette.grey[500], 0.2),
                      '& .MuiLinearProgress-bar': {
                        borderRadius: 5,
                        backgroundColor: getFearGreedColor(mockMarketStats.fearGreedIndex),
                      },
                    }}
                  />
                </Box>
                <Chip
                  label={`${mockMarketStats.fearGreedIndex} - ${getFearGreedLabel(mockMarketStats.fearGreedIndex)}`}
                  size="small"
                  sx={{
                    backgroundColor: alpha(getFearGreedColor(mockMarketStats.fearGreedIndex), 0.1),
                    color: getFearGreedColor(mockMarketStats.fearGreedIndex),
                  }}
                />
              </Box>
            </Box>
          </CardContent>
        </Card>
      </Grid>

      <Grid item xs={12} md={4}>
        <Card>
          <CardContent>
            <Typography variant="h6" gutterBottom>
              Market Alerts
            </Typography>
            <Stack spacing={2}>
              <Alert severity="info" icon={<Info />}>
                BTC showing strong support at $42,000
              </Alert>
              <Alert severity="warning" icon={<Warning />}>
                High volatility expected during FOMC meeting
              </Alert>
              <Alert severity="success" icon={<CheckCircle />}>
                ETH successfully merged to PoS
              </Alert>
            </Stack>
          </CardContent>
        </Card>
      </Grid>
    </Grid>
  );

  return (
    <Box>
      <Box display="flex" justifyContent="space-between" alignItems="center" mb={3}>
        <Typography variant="h5" fontWeight="bold">
          Crypto Trading
        </Typography>
        <Stack direction="row" spacing={2}>
          <ToggleButtonGroup
            value={viewMode}
            exclusive
            onChange={(_, value) => value && setViewMode(value)}
            size="small"
          >
            <ToggleButton value="grid">
              <PieChart />
            </ToggleButton>
            <ToggleButton value="list">
              <BarChart />
            </ToggleButton>
          </ToggleButtonGroup>
          <Button
            variant="contained"
            startIcon={<Notifications />}
            size="small"
          >
            Price Alerts
          </Button>
        </Stack>
      </Box>

      <Grid container spacing={3} mb={3}>
        <Grid item xs={12} lg={4}>
          {renderPortfolioCard()}
        </Grid>
        <Grid item xs={12} lg={8}>
          {renderMarketOverview()}
        </Grid>
      </Grid>

      <Paper sx={{ mb: 3 }}>
        <Tabs
          value={activeTab}
          onChange={(_, value) => setActiveTab(value)}
          sx={{ borderBottom: 1, borderColor: 'divider' }}
        >
          <Tab label="My Portfolio" icon={<AccountBalanceWallet />} iconPosition="start" />
          <Tab label="All Assets" icon={<CurrencyBitcoin />} iconPosition="start" />
          <Tab label="Transactions" icon={<SwapHoriz />} iconPosition="start" />
          <Tab label="Analytics" icon={<Analytics />} iconPosition="start" />
        </Tabs>

        <TabPanel value={activeTab} index={0}>
          <Box p={2}>
            <Box display="flex" justifyContent="space-between" alignItems="center" mb={3}>
              <TextField
                placeholder="Search assets..."
                size="small"
                value={searchTerm}
                onChange={(e) => setSearchTerm(e.target.value)}
                InputProps={{
                  startAdornment: (
                    <InputAdornment position="start">
                      <Search />
                    </InputAdornment>
                  ),
                }}
                sx={{ width: 300 }}
              />
              <FormControl size="small" sx={{ minWidth: 120 }}>
                <InputLabel>Sort by</InputLabel>
                <Select
                  value={sortBy}
                  onChange={(e) => setSortBy(e.target.value as any)}
                  label="Sort by"
                >
                  <MenuItem value="holdings">Holdings</MenuItem>
                  <MenuItem value="price">Price</MenuItem>
                  <MenuItem value="change">24h Change</MenuItem>
                </Select>
              </FormControl>
            </Box>

            <Grid container spacing={3}>
              {filteredAssets
                .filter(asset => asset.holdings > 0)
                .map(asset => (
                  <Grid item xs={12} sm={6} md={4} key={asset.id}>
                    {renderAssetCard(asset)}
                  </Grid>
                ))}
            </Grid>

            {filteredAssets.filter(asset => asset.holdings > 0).length === 0 && (
              <Box textAlign="center" py={8}>
                <CurrencyBitcoin sx={{ fontSize: 64, color: 'text.secondary', mb: 2 }} />
                <Typography variant="h6" color="text.secondary" gutterBottom>
                  No crypto holdings yet
                </Typography>
                <Typography variant="body2" color="text.secondary" mb={3}>
                  Start building your crypto portfolio today
                </Typography>
                <Button variant="contained" startIcon={<Add />}>
                  Buy Crypto
                </Button>
              </Box>
            )}
          </Box>
        </TabPanel>

        <TabPanel value={activeTab} index={1}>
          <Box p={2}>
            <Box display="flex" gap={2} mb={3}>
              <TextField
                placeholder="Search cryptocurrencies..."
                size="small"
                fullWidth
                value={searchTerm}
                onChange={(e) => setSearchTerm(e.target.value)}
                InputProps={{
                  startAdornment: (
                    <InputAdornment position="start">
                      <Search />
                    </InputAdornment>
                  ),
                }}
              />
              <Button variant="outlined" startIcon={<FilterList />}>
                Filters
              </Button>
            </Box>

            <TableContainer>
              <Table>
                <TableHead>
                  <TableRow>
                    <TableCell>Asset</TableCell>
                    <TableCell align="right">Price</TableCell>
                    <TableCell align="right">24h Change</TableCell>
                    <TableCell align="right">7d Change</TableCell>
                    <TableCell align="right">Market Cap</TableCell>
                    <TableCell align="right">Volume (24h)</TableCell>
                    <TableCell align="right">Actions</TableCell>
                  </TableRow>
                </TableHead>
                <TableBody>
                  {filteredAssets.map((asset) => (
                    <TableRow key={asset.id}>
                      <TableCell>
                        <Box display="flex" alignItems="center" gap={2}>
                          <Avatar sx={{ width: 32, height: 32 }}>
                            {asset.icon}
                          </Avatar>
                          <Box>
                            <Typography variant="body2" fontWeight="bold">
                              {asset.symbol}
                            </Typography>
                            <Typography variant="caption" color="text.secondary">
                              {asset.name}
                            </Typography>
                          </Box>
                        </Box>
                      </TableCell>
                      <TableCell align="right">
                        ${asset.price.toLocaleString()}
                      </TableCell>
                      <TableCell align="right">
                        <Box display="flex" alignItems="center" justifyContent="flex-end" gap={0.5}>
                          {asset.change24h >= 0 ? (
                            <ArrowUpward color="success" fontSize="small" />
                          ) : (
                            <ArrowDownward color="error" fontSize="small" />
                          )}
                          <Typography
                            variant="body2"
                            color={asset.change24h >= 0 ? 'success.main' : 'error.main'}
                          >
                            {Math.abs(asset.change24h).toFixed(2)}%
                          </Typography>
                        </Box>
                      </TableCell>
                      <TableCell align="right">
                        <Typography
                          variant="body2"
                          color={asset.change7d >= 0 ? 'success.main' : 'error.main'}
                        >
                          {asset.change7d >= 0 ? '+' : ''}{asset.change7d.toFixed(2)}%
                        </Typography>
                      </TableCell>
                      <TableCell align="right">
                        ${(asset.marketCap / 1000000000).toFixed(2)}B
                      </TableCell>
                      <TableCell align="right">
                        ${(asset.volume24h / 1000000000).toFixed(2)}B
                      </TableCell>
                      <TableCell align="right">
                        <Stack direction="row" spacing={1}>
                          <Button
                            size="small"
                            variant="contained"
                            onClick={() => {
                              setSelectedCrypto(asset);
                              setShowBuyDialog(true);
                            }}
                          >
                            Buy
                          </Button>
                          <IconButton
                            size="small"
                            onClick={() => {
                              // Toggle favorite
                            }}
                          >
                            {asset.isFavorite ? <Star color="warning" /> : <StarBorder />}
                          </IconButton>
                        </Stack>
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </TableContainer>
          </Box>
        </TabPanel>

        <TabPanel value={activeTab} index={2}>
          <Box p={2}>
            <Typography variant="h6" gutterBottom>
              Transaction History
            </Typography>
            <List>
              {mockTransactions.map((transaction) => (
                <ListItem key={transaction.id} divider>
                  <ListItemAvatar>
                    <Avatar sx={{ bgcolor: 
                      transaction.type === 'buy' ? 'success.main' : 
                      transaction.type === 'sell' ? 'error.main' : 
                      'info.main' 
                    }}>
                      {transaction.type === 'buy' ? <Add /> : 
                       transaction.type === 'sell' ? <Remove /> : 
                       <SwapHoriz />}
                    </Avatar>
                  </ListItemAvatar>
                  <ListItemText
                    primary={
                      <Box display="flex" alignItems="center" gap={1}>
                        <Typography variant="body1">
                          {transaction.type === 'buy' ? 'Bought' : 
                           transaction.type === 'sell' ? 'Sold' : 
                           'Converted'} {transaction.cryptoAmount} {transaction.cryptoSymbol}
                        </Typography>
                        <Chip
                          label={transaction.status}
                          size="small"
                          color={
                            transaction.status === 'completed' ? 'success' :
                            transaction.status === 'pending' ? 'warning' :
                            'error'
                          }
                        />
                      </Box>
                    }
                    secondary={
                      <Box>
                        <Typography variant="caption" color="text.secondary">
                          {format(new Date(transaction.timestamp), 'MMM dd, yyyy HH:mm')}
                        </Typography>
                        <Typography variant="caption" color="text.secondary" sx={{ ml: 2 }}>
                          @ ${transaction.price.toLocaleString()}
                        </Typography>
                      </Box>
                    }
                  />
                  <ListItemSecondaryAction>
                    <Box textAlign="right">
                      <Typography variant="body1" fontWeight="bold">
                        ${transaction.fiatAmount.toLocaleString()}
                      </Typography>
                      <Typography variant="caption" color="text.secondary">
                        Fee: ${transaction.fee.toFixed(2)}
                      </Typography>
                    </Box>
                  </ListItemSecondaryAction>
                </ListItem>
              ))}
            </List>
          </Box>
        </TabPanel>

        <TabPanel value={activeTab} index={3}>
          <Box p={2}>
            <Grid container spacing={3}>
              <Grid item xs={12} md={6}>
                <Card>
                  <CardContent>
                    <Typography variant="h6" gutterBottom>
                      Portfolio Distribution
                    </Typography>
                    {/* Simplified pie chart representation */}
                    <Box sx={{ position: 'relative', height: 200 }}>
                      <Box
                        sx={{
                          position: 'absolute',
                          top: '50%',
                          left: '50%',
                          transform: 'translate(-50%, -50%)',
                          textAlign: 'center',
                        }}
                      >
                        <Typography variant="h4" fontWeight="bold">
                          {portfolio.assetCount}
                        </Typography>
                        <Typography variant="body2" color="text.secondary">
                          Assets
                        </Typography>
                      </Box>
                    </Box>
                    <Stack spacing={1} mt={2}>
                      {mockCryptoAssets
                        .filter(a => a.holdings > 0)
                        .map((asset) => (
                          <Box key={asset.id} display="flex" alignItems="center" justifyContent="space-between">
                            <Box display="flex" alignItems="center" gap={1}>
                              <Box
                                sx={{
                                  width: 12,
                                  height: 12,
                                  borderRadius: '50%',
                                  bgcolor: theme.palette.primary.main,
                                }}
                              />
                              <Typography variant="body2">{asset.symbol}</Typography>
                            </Box>
                            <Typography variant="body2">
                              {((asset.value / portfolio.totalValue) * 100).toFixed(1)}%
                            </Typography>
                          </Box>
                        ))}
                    </Stack>
                  </CardContent>
                </Card>
              </Grid>

              <Grid item xs={12} md={6}>
                <Card>
                  <CardContent>
                    <Typography variant="h6" gutterBottom>
                      Performance Metrics
                    </Typography>
                    <Stack spacing={3}>
                      <Box>
                        <Box display="flex" justifyContent="space-between" mb={1}>
                          <Typography variant="body2" color="text.secondary">
                            Best Performer
                          </Typography>
                          <Chip
                            label="SOL"
                            size="small"
                            avatar={<Avatar sx={{ width: 20, height: 20 }}>◎</Avatar>}
                          />
                        </Box>
                        <Typography variant="h6" color="success.main">
                          +51.5%
                        </Typography>
                      </Box>
                      <Divider />
                      <Box>
                        <Box display="flex" justifyContent="space-between" mb={1}>
                          <Typography variant="body2" color="text.secondary">
                            Worst Performer
                          </Typography>
                          <Chip
                            label="MATIC"
                            size="small"
                            avatar={<Avatar sx={{ width: 20, height: 20 }}>Ⓜ</Avatar>}
                          />
                        </Box>
                        <Typography variant="h6" color="error.main">
                          -10.5%
                        </Typography>
                      </Box>
                      <Divider />
                      <Box>
                        <Typography variant="body2" color="text.secondary" gutterBottom>
                          Portfolio Health Score
                        </Typography>
                        <Box display="flex" alignItems="center" gap={2}>
                          <LinearProgress
                            variant="determinate"
                            value={78}
                            sx={{
                              flex: 1,
                              height: 8,
                              borderRadius: 4,
                              backgroundColor: alpha(theme.palette.success.main, 0.2),
                              '& .MuiLinearProgress-bar': {
                                borderRadius: 4,
                                backgroundColor: theme.palette.success.main,
                              },
                            }}
                          />
                          <Typography variant="h6">78/100</Typography>
                        </Box>
                      </Box>
                    </Stack>
                  </CardContent>
                </Card>
              </Grid>

              <Grid item xs={12}>
                <Card>
                  <CardContent>
                    <Typography variant="h6" gutterBottom>
                      Investment Insights
                    </Typography>
                    <Grid container spacing={2}>
                      <Grid item xs={12} sm={6} md={3}>
                        <Alert severity="info" icon={<Bolt />}>
                          <Typography variant="body2">
                            Consider diversifying - BTC makes up 65% of portfolio
                          </Typography>
                        </Alert>
                      </Grid>
                      <Grid item xs={12} sm={6} md={3}>
                        <Alert severity="success" icon={<TrendingUp />}>
                          <Typography variant="body2">
                            Your SOL investment is up 51.5% - consider taking profits
                          </Typography>
                        </Alert>
                      </Grid>
                      <Grid item xs={12} sm={6} md={3}>
                        <Alert severity="warning" icon={<Schedule />}>
                          <Typography variant="body2">
                            MATIC is down 10.5% - might be a buying opportunity
                          </Typography>
                        </Alert>
                      </Grid>
                      <Grid item xs={12} sm={6} md={3}>
                        <Alert severity="info" icon={<Security />}>
                          <Typography variant="body2">
                            Enable 2FA for enhanced security
                          </Typography>
                        </Alert>
                      </Grid>
                    </Grid>
                  </CardContent>
                </Card>
              </Grid>
            </Grid>
          </Box>
        </TabPanel>
      </Paper>

      {/* Buy Dialog */}
      <Dialog open={showBuyDialog} onClose={() => setShowBuyDialog(false)} maxWidth="sm" fullWidth>
        <DialogTitle>
          <Box display="flex" alignItems="center" gap={2}>
            Buy {selectedCrypto?.name}
            {selectedCrypto && (
              <Chip
                label={`$${selectedCrypto.price.toLocaleString()}`}
                size="small"
                color="primary"
              />
            )}
          </Box>
        </DialogTitle>
        <DialogContent>
          <Stack spacing={3} mt={2}>
            <TextField
              label="Amount in USD"
              type="number"
              fullWidth
              value={buyAmount}
              onChange={(e) => setBuyAmount(e.target.value)}
              InputProps={{
                startAdornment: <InputAdornment position="start">$</InputAdornment>,
              }}
            />
            {buyAmount && selectedCrypto && (
              <Alert severity="info">
                You will receive approximately {(Number(buyAmount) / selectedCrypto.price).toFixed(6)} {selectedCrypto.symbol}
              </Alert>
            )}
            <Box>
              <Typography variant="body2" color="text.secondary" gutterBottom>
                Quick amounts
              </Typography>
              <Stack direction="row" spacing={1}>
                {[100, 500, 1000, 5000].map((amount) => (
                  <Chip
                    key={amount}
                    label={`$${amount}`}
                    onClick={() => setBuyAmount(amount.toString())}
                    clickable
                  />
                ))}
              </Stack>
            </Box>
          </Stack>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setShowBuyDialog(false)}>Cancel</Button>
          <Button variant="contained" onClick={handleBuy} disabled={!buyAmount || Number(buyAmount) <= 0}>
            Buy Now
          </Button>
        </DialogActions>
      </Dialog>

      {/* Sell Dialog */}
      <Dialog open={showSellDialog} onClose={() => setShowSellDialog(false)} maxWidth="sm" fullWidth>
        <DialogTitle>
          <Box display="flex" alignItems="center" gap={2}>
            Sell {selectedCrypto?.name}
            {selectedCrypto && (
              <Chip
                label={`Holdings: ${selectedCrypto.holdings} ${selectedCrypto.symbol}`}
                size="small"
                color="primary"
              />
            )}
          </Box>
        </DialogTitle>
        <DialogContent>
          <Stack spacing={3} mt={2}>
            <TextField
              label={`Amount in ${selectedCrypto?.symbol}`}
              type="number"
              fullWidth
              value={sellAmount}
              onChange={(e) => setSellAmount(e.target.value)}
              inputProps={{
                max: selectedCrypto?.holdings,
                step: 0.000001,
              }}
            />
            {sellAmount && selectedCrypto && (
              <Alert severity="info">
                You will receive approximately ${(Number(sellAmount) * selectedCrypto.price).toFixed(2)} USD
              </Alert>
            )}
            <Box>
              <Typography variant="body2" color="text.secondary" gutterBottom>
                Quick amounts
              </Typography>
              <Stack direction="row" spacing={1}>
                {selectedCrypto && [25, 50, 75, 100].map((percent) => (
                  <Chip
                    key={percent}
                    label={`${percent}%`}
                    onClick={() => setSellAmount(((selectedCrypto.holdings * percent) / 100).toString())}
                    clickable
                  />
                ))}
              </Stack>
            </Box>
            <Slider
              value={selectedCrypto ? (Number(sellAmount) / selectedCrypto.holdings) * 100 : 0}
              onChange={(_, value) => {
                if (selectedCrypto) {
                  setSellAmount(((selectedCrypto.holdings * (value as number)) / 100).toString());
                }
              }}
              valueLabelDisplay="auto"
              valueLabelFormat={(value) => `${value.toFixed(0)}%`}
            />
          </Stack>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setShowSellDialog(false)}>Cancel</Button>
          <Button 
            variant="contained" 
            color="error" 
            onClick={handleSell}
            disabled={!sellAmount || Number(sellAmount) <= 0 || (selectedCrypto && Number(sellAmount) > selectedCrypto.holdings)}
          >
            Sell Now
          </Button>
        </DialogActions>
      </Dialog>
    </Box>
  );
};

export default CryptoTradingDashboard;