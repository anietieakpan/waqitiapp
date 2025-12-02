import React, { useState, useMemo } from 'react';
import {
  Box,
  Grid,
  Paper,
  Typography,
  Card,
  CardContent,
  CardActions,
  Button,
  IconButton,
  Chip,
  Avatar,
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
  Tabs,
  Tab,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  List,
  ListItem,
  ListItemText,
  ListItemAvatar,
  ListItemSecondaryAction,
  Alert,
  LinearProgress,
  Divider,
  Stack,
  Badge,
  Tooltip,
  ToggleButton,
  ToggleButtonGroup,
  Switch,
  FormControlLabel,
  useTheme,
  alpha,
} from '@mui/material';
import TrendingUpIcon from '@mui/icons-material/TrendingUp';
import TrendingDownIcon from '@mui/icons-material/TrendingDown';
import ShowChartIcon from '@mui/icons-material/ShowChart';
import BarChartIcon from '@mui/icons-material/BarChart';
import PieChartIcon from '@mui/icons-material/PieChart';
import AttachMoneyIcon from '@mui/icons-material/AttachMoney';
import SearchIcon from '@mui/icons-material/Search';
import FilterListIcon from '@mui/icons-material/FilterList';
import AddIcon from '@mui/icons-material/Add';
import RemoveIcon from '@mui/icons-material/Remove';
import StarIcon from '@mui/icons-material/Star';
import StarBorderIcon from '@mui/icons-material/StarBorder';
import NotificationsIcon from '@mui/icons-material/Notifications';
import NotificationsActiveIcon from '@mui/icons-material/NotificationsActive';
import InfoIcon from '@mui/icons-material/Info';
import WarningIcon from '@mui/icons-material/Warning';
import CheckCircleIcon from '@mui/icons-material/CheckCircle';
import ScheduleIcon from '@mui/icons-material/Schedule';
import ArrowUpwardIcon from '@mui/icons-material/ArrowUpward';
import ArrowDownwardIcon from '@mui/icons-material/ArrowDownward';
import BusinessIcon from '@mui/icons-material/Business';
import LocalAtmIcon from '@mui/icons-material/LocalAtm';
import TimelineIcon from '@mui/icons-material/Timeline';
import AssessmentIcon from '@mui/icons-material/Assessment';
import LightbulbIcon from '@mui/icons-material/Lightbulb';
import SecurityIcon from '@mui/icons-material/Security';
import SpeedIcon from '@mui/icons-material/Speed';
import AccountBalanceIcon from '@mui/icons-material/AccountBalance';
import CandlestickChartIcon from '@mui/icons-material/CandlestickChart';
import AnalyticsIcon from '@mui/icons-material/Analytics';
import DownloadIcon from '@mui/icons-material/Download';
import ShareIcon from '@mui/icons-material/Share';
import ArticleIcon from '@mui/icons-material/Article';
import TrendingFlatIcon from '@mui/icons-material/TrendingFlat';;
import { format, subDays, differenceInDays } from 'date-fns';

interface Stock {
  id: string;
  symbol: string;
  name: string;
  sector: string;
  price: number;
  change: number;
  changePercent: number;
  dayHigh: number;
  dayLow: number;
  volume: number;
  marketCap: number;
  pe: number;
  eps: number;
  beta: number;
  dividendYield: number;
  shares?: number;
  avgCost?: number;
  totalValue?: number;
  totalGainLoss?: number;
  totalGainLossPercent?: number;
  isWatchlisted: boolean;
}

interface Order {
  id: string;
  type: 'market' | 'limit' | 'stop' | 'stop-limit';
  action: 'buy' | 'sell';
  symbol: string;
  quantity: number;
  price?: number;
  stopPrice?: number;
  status: 'pending' | 'filled' | 'cancelled' | 'partial';
  filledQuantity: number;
  avgFillPrice?: number;
  createdAt: string;
  filledAt?: string;
  expiresAt?: string;
}

interface PortfolioStats {
  totalValue: number;
  totalCost: number;
  totalGainLoss: number;
  totalGainLossPercent: number;
  dayGainLoss: number;
  dayGainLossPercent: number;
  cashBalance: number;
  buyingPower: number;
  positions: number;
}

const mockStocks: Stock[] = [
  {
    id: '1',
    symbol: 'AAPL',
    name: 'Apple Inc.',
    sector: 'Technology',
    price: 182.52,
    change: 2.15,
    changePercent: 1.19,
    dayHigh: 183.45,
    dayLow: 180.20,
    volume: 52384729,
    marketCap: 2810000000000,
    pe: 29.8,
    eps: 6.12,
    beta: 1.25,
    dividendYield: 0.5,
    shares: 100,
    avgCost: 165.00,
    totalValue: 18252.00,
    totalGainLoss: 1752.00,
    totalGainLossPercent: 10.62,
    isWatchlisted: true,
  },
  {
    id: '2',
    symbol: 'MSFT',
    name: 'Microsoft Corporation',
    sector: 'Technology',
    price: 378.85,
    change: -1.25,
    changePercent: -0.33,
    dayHigh: 382.10,
    dayLow: 377.50,
    volume: 18923456,
    marketCap: 2810000000000,
    pe: 32.5,
    eps: 11.65,
    beta: 0.93,
    dividendYield: 0.88,
    shares: 50,
    avgCost: 340.00,
    totalValue: 18942.50,
    totalGainLoss: 1942.50,
    totalGainLossPercent: 11.43,
    isWatchlisted: true,
  },
  {
    id: '3',
    symbol: 'GOOGL',
    name: 'Alphabet Inc.',
    sector: 'Technology',
    price: 142.65,
    change: 3.25,
    changePercent: 2.33,
    dayHigh: 143.80,
    dayLow: 139.50,
    volume: 25678901,
    marketCap: 1810000000000,
    pe: 25.2,
    eps: 5.66,
    beta: 1.07,
    dividendYield: 0,
    shares: 75,
    avgCost: 125.00,
    totalValue: 10698.75,
    totalGainLoss: 1323.75,
    totalGainLossPercent: 14.12,
    isWatchlisted: false,
  },
  {
    id: '4',
    symbol: 'TSLA',
    name: 'Tesla Inc.',
    sector: 'Automotive',
    price: 238.45,
    change: -8.32,
    changePercent: -3.37,
    dayHigh: 248.50,
    dayLow: 236.80,
    volume: 98765432,
    marketCap: 756000000000,
    pe: 65.3,
    eps: 3.65,
    beta: 2.08,
    dividendYield: 0,
    isWatchlisted: true,
  },
];

const mockOrders: Order[] = [
  {
    id: 'o1',
    type: 'market',
    action: 'buy',
    symbol: 'AAPL',
    quantity: 10,
    status: 'filled',
    filledQuantity: 10,
    avgFillPrice: 182.52,
    createdAt: '2024-01-20T09:30:00Z',
    filledAt: '2024-01-20T09:30:15Z',
  },
  {
    id: 'o2',
    type: 'limit',
    action: 'sell',
    symbol: 'MSFT',
    quantity: 5,
    price: 385.00,
    status: 'pending',
    filledQuantity: 0,
    createdAt: '2024-01-20T10:15:00Z',
    expiresAt: '2024-01-20T16:00:00Z',
  },
  {
    id: 'o3',
    type: 'stop',
    action: 'sell',
    symbol: 'TSLA',
    quantity: 20,
    stopPrice: 230.00,
    status: 'pending',
    filledQuantity: 0,
    createdAt: '2024-01-19T14:30:00Z',
  },
];

const mockPortfolioStats: PortfolioStats = {
  totalValue: 47893.25,
  totalCost: 43000.00,
  totalGainLoss: 4893.25,
  totalGainLossPercent: 11.38,
  dayGainLoss: 324.50,
  dayGainLossPercent: 0.68,
  cashBalance: 15234.75,
  buyingPower: 30469.50,
  positions: 3,
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

const StockTradingPortfolio: React.FC = () => {
  const theme = useTheme();
  const [activeTab, setActiveTab] = useState(0);
  const [selectedStock, setSelectedStock] = useState<Stock | null>(null);
  const [showTradeDialog, setShowTradeDialog] = useState(false);
  const [tradeAction, setTradeAction] = useState<'buy' | 'sell'>('buy');
  const [searchTerm, setSearchTerm] = useState('');
  const [sortBy, setSortBy] = useState<'value' | 'gain' | 'change'>('value');
  const [viewMode, setViewMode] = useState<'cards' | 'table'>('cards');
  const [showOnlyOwned, setShowOnlyOwned] = useState(false);

  const [orderType, setOrderType] = useState<'market' | 'limit' | 'stop' | 'stop-limit'>('market');
  const [orderQuantity, setOrderQuantity] = useState('');
  const [orderPrice, setOrderPrice] = useState('');
  const [orderStopPrice, setOrderStopPrice] = useState('');

  const filteredStocks = useMemo(() => {
    let filtered = mockStocks.filter(stock => {
      const matchesSearch = stock.symbol.toLowerCase().includes(searchTerm.toLowerCase()) ||
        stock.name.toLowerCase().includes(searchTerm.toLowerCase());
      const matchesOwnership = !showOnlyOwned || (stock.shares && stock.shares > 0);
      return matchesSearch && matchesOwnership;
    });

    filtered.sort((a, b) => {
      switch (sortBy) {
        case 'value':
          return (b.totalValue || 0) - (a.totalValue || 0);
        case 'gain':
          return (b.totalGainLossPercent || 0) - (a.totalGainLossPercent || 0);
        case 'change':
          return b.changePercent - a.changePercent;
        default:
          return 0;
      }
    });

    return filtered;
  }, [searchTerm, sortBy, showOnlyOwned]);

  const getSectorIcon = (sector: string) => {
    switch (sector) {
      case 'Technology':
        return <Business />;
      case 'Automotive':
        return <LocalAtm />;
      case 'Healthcare':
        return <Security />;
      default:
        return <ShowChart />;
    }
  };

  const handleTrade = () => {
    // Handle trade execution
    setShowTradeDialog(false);
    setOrderQuantity('');
    setOrderPrice('');
    setOrderStopPrice('');
  };

  const renderPortfolioOverview = () => (
    <Grid container spacing={3}>
      <Grid item xs={12} lg={8}>
        <Card>
          <CardContent>
            <Typography variant="h6" gutterBottom>
              Portfolio Overview
            </Typography>
            <Grid container spacing={3}>
              <Grid item xs={6} sm={3}>
                <Typography variant="body2" color="text.secondary">
                  Total Value
                </Typography>
                <Typography variant="h5" fontWeight="bold">
                  ${mockPortfolioStats.totalValue.toLocaleString()}
                </Typography>
                <Box display="flex" alignItems="center" mt={1}>
                  {mockPortfolioStats.dayGainLoss >= 0 ? (
                    <TrendingUp color="success" fontSize="small" />
                  ) : (
                    <TrendingDown color="error" fontSize="small" />
                  )}
                  <Typography
                    variant="body2"
                    color={mockPortfolioStats.dayGainLoss >= 0 ? 'success.main' : 'error.main'}
                    ml={0.5}
                  >
                    ${Math.abs(mockPortfolioStats.dayGainLoss).toFixed(2)} ({mockPortfolioStats.dayGainLossPercent.toFixed(2)}%)
                  </Typography>
                </Box>
              </Grid>

              <Grid item xs={6} sm={3}>
                <Typography variant="body2" color="text.secondary">
                  Total Gain/Loss
                </Typography>
                <Typography 
                  variant="h5" 
                  fontWeight="bold"
                  color={mockPortfolioStats.totalGainLoss >= 0 ? 'success.main' : 'error.main'}
                >
                  ${mockPortfolioStats.totalGainLoss.toLocaleString()}
                </Typography>
                <Typography variant="body2" color="text.secondary" mt={1}>
                  {mockPortfolioStats.totalGainLossPercent >= 0 ? '+' : ''}{mockPortfolioStats.totalGainLossPercent.toFixed(2)}% all time
                </Typography>
              </Grid>

              <Grid item xs={6} sm={3}>
                <Typography variant="body2" color="text.secondary">
                  Cash Balance
                </Typography>
                <Typography variant="h5" fontWeight="bold">
                  ${mockPortfolioStats.cashBalance.toLocaleString()}
                </Typography>
                <Typography variant="body2" color="text.secondary" mt={1}>
                  Available to invest
                </Typography>
              </Grid>

              <Grid item xs={6} sm={3}>
                <Typography variant="body2" color="text.secondary">
                  Buying Power
                </Typography>
                <Typography variant="h5" fontWeight="bold">
                  ${mockPortfolioStats.buyingPower.toLocaleString()}
                </Typography>
                <Typography variant="body2" color="text.secondary" mt={1}>
                  With margin
                </Typography>
              </Grid>
            </Grid>

            <Divider sx={{ my: 3 }} />

            <Box display="flex" justifyContent="space-between" alignItems="center">
              <Stack direction="row" spacing={2}>
                <Chip
                  icon={<PieChart />}
                  label={`${mockPortfolioStats.positions} Positions`}
                  color="primary"
                />
                <Chip
                  icon={<Assessment />}
                  label="Balanced Risk"
                  color="success"
                />
                <Chip
                  icon={<Speed />}
                  label="High Performance"
                  color="info"
                />
              </Stack>
              <Button variant="outlined" startIcon={<Download />} size="small">
                Export
              </Button>
            </Box>
          </CardContent>
        </Card>
      </Grid>

      <Grid item xs={12} lg={4}>
        <Stack spacing={2}>
          <Card>
            <CardContent>
              <Typography variant="h6" gutterBottom>
                Market Status
              </Typography>
              <Stack spacing={2}>
                <Box display="flex" justifyContent="space-between" alignItems="center">
                  <Typography variant="body2">NYSE</Typography>
                  <Chip label="Open" color="success" size="small" />
                </Box>
                <Box display="flex" justifyContent="space-between" alignItems="center">
                  <Typography variant="body2">NASDAQ</Typography>
                  <Chip label="Open" color="success" size="small" />
                </Box>
                <Box display="flex" justifyContent="space-between" alignItems="center">
                  <Typography variant="body2">Next Close</Typography>
                  <Typography variant="body2" color="text.secondary">
                    4:00 PM EST
                  </Typography>
                </Box>
              </Stack>
            </CardContent>
          </Card>

          <Card>
            <CardContent>
              <Box display="flex" justifyContent="space-between" alignItems="center" mb={2}>
                <Typography variant="h6">
                  Quick Actions
                </Typography>
                <NotificationsActive color="primary" />
              </Box>
              <Stack spacing={1}>
                <Button
                  variant="contained"
                  fullWidth
                  startIcon={<Add />}
                  onClick={() => {
                    setTradeAction('buy');
                    setShowTradeDialog(true);
                  }}
                >
                  Buy Stocks
                </Button>
                <Button
                  variant="outlined"
                  fullWidth
                  startIcon={<Article />}
                >
                  Research
                </Button>
              </Stack>
            </CardContent>
          </Card>
        </Stack>
      </Grid>
    </Grid>
  );

  const renderStockCard = (stock: Stock) => (
    <Card key={stock.id}>
      <CardContent>
        <Box display="flex" justifyContent="space-between" alignItems="flex-start" mb={2}>
          <Box>
            <Box display="flex" alignItems="center" gap={1}>
              <Typography variant="h6" fontWeight="bold">
                {stock.symbol}
              </Typography>
              <Chip
                label={stock.sector}
                size="small"
                icon={getSectorIcon(stock.sector)}
              />
            </Box>
            <Typography variant="body2" color="text.secondary">
              {stock.name}
            </Typography>
          </Box>
          <IconButton
            size="small"
            onClick={() => {
              // Toggle watchlist
            }}
          >
            {stock.isWatchlisted ? <Star color="warning" /> : <StarBorder />}
          </IconButton>
        </Box>

        <Grid container spacing={2}>
          <Grid item xs={6}>
            <Typography variant="h5" fontWeight="bold">
              ${stock.price.toFixed(2)}
            </Typography>
            <Box display="flex" alignItems="center" gap={0.5}>
              {stock.change >= 0 ? (
                <ArrowUpward color="success" fontSize="small" />
              ) : (
                <ArrowDownward color="error" fontSize="small" />
              )}
              <Typography
                variant="body2"
                color={stock.change >= 0 ? 'success.main' : 'error.main'}
              >
                ${Math.abs(stock.change).toFixed(2)} ({stock.changePercent.toFixed(2)}%)
              </Typography>
            </Box>
          </Grid>
          <Grid item xs={6}>
            {stock.shares && (
              <Box textAlign="right">
                <Typography variant="body2" color="text.secondary">
                  {stock.shares} shares
                </Typography>
                <Typography variant="h6" fontWeight="bold">
                  ${stock.totalValue?.toLocaleString()}
                </Typography>
                <Typography
                  variant="body2"
                  color={stock.totalGainLoss! >= 0 ? 'success.main' : 'error.main'}
                >
                  {stock.totalGainLoss! >= 0 ? '+' : ''}${Math.abs(stock.totalGainLoss!).toFixed(2)} ({stock.totalGainLossPercent?.toFixed(2)}%)
                </Typography>
              </Box>
            )}
          </Grid>
        </Grid>

        <Divider sx={{ my: 2 }} />

        <Grid container spacing={1}>
          <Grid item xs={6}>
            <Typography variant="caption" color="text.secondary">
              Day Range
            </Typography>
            <Typography variant="body2">
              ${stock.dayLow.toFixed(2)} - ${stock.dayHigh.toFixed(2)}
            </Typography>
          </Grid>
          <Grid item xs={6}>
            <Typography variant="caption" color="text.secondary">
              Volume
            </Typography>
            <Typography variant="body2">
              {(stock.volume / 1000000).toFixed(1)}M
            </Typography>
          </Grid>
          <Grid item xs={6}>
            <Typography variant="caption" color="text.secondary">
              P/E Ratio
            </Typography>
            <Typography variant="body2">
              {stock.pe.toFixed(2)}
            </Typography>
          </Grid>
          <Grid item xs={6}>
            <Typography variant="caption" color="text.secondary">
              Div Yield
            </Typography>
            <Typography variant="body2">
              {stock.dividendYield > 0 ? `${stock.dividendYield}%` : 'N/A'}
            </Typography>
          </Grid>
        </Grid>
      </CardContent>
      <CardActions>
        <Button
          size="small"
          variant="contained"
          onClick={() => {
            setSelectedStock(stock);
            setTradeAction('buy');
            setShowTradeDialog(true);
          }}
        >
          Buy
        </Button>
        {stock.shares && stock.shares > 0 && (
          <Button
            size="small"
            variant="outlined"
            onClick={() => {
              setSelectedStock(stock);
              setTradeAction('sell');
              setShowTradeDialog(true);
            }}
          >
            Sell
          </Button>
        )}
        <Button size="small">Details</Button>
      </CardActions>
    </Card>
  );

  return (
    <Box>
      <Box display="flex" justifyContent="space-between" alignItems="center" mb={3}>
        <Typography variant="h5" fontWeight="bold">
          Stock Trading Portfolio
        </Typography>
        <Stack direction="row" spacing={2}>
          <Button variant="outlined" startIcon={<Notifications />}>
            Alerts
          </Button>
          <Button variant="contained" startIcon={<Analytics />}>
            Analysis
          </Button>
        </Stack>
      </Box>

      {renderPortfolioOverview()}

      <Paper sx={{ mt: 3 }}>
        <Tabs
          value={activeTab}
          onChange={(_, value) => setActiveTab(value)}
          sx={{ borderBottom: 1, borderColor: 'divider' }}
        >
          <Tab label="Positions" icon={<PieChart />} iconPosition="start" />
          <Tab label="Watchlist" icon={<Star />} iconPosition="start" />
          <Tab label="Orders" icon={<Schedule />} iconPosition="start" />
          <Tab label="Research" icon={<Lightbulb />} iconPosition="start" />
        </Tabs>

        <TabPanel value={activeTab} index={0}>
          <Box p={2}>
            <Box display="flex" justifyContent="space-between" alignItems="center" mb={3}>
              <TextField
                placeholder="Search stocks..."
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
              <Stack direction="row" spacing={2}>
                <FormControlLabel
                  control={
                    <Switch
                      checked={showOnlyOwned}
                      onChange={(e) => setShowOnlyOwned(e.target.checked)}
                    />
                  }
                  label="My Positions"
                />
                <FormControl size="small" sx={{ minWidth: 120 }}>
                  <InputLabel>Sort by</InputLabel>
                  <Select
                    value={sortBy}
                    onChange={(e) => setSortBy(e.target.value as any)}
                    label="Sort by"
                  >
                    <MenuItem value="value">Value</MenuItem>
                    <MenuItem value="gain">Gain %</MenuItem>
                    <MenuItem value="change">Day Change</MenuItem>
                  </Select>
                </FormControl>
                <ToggleButtonGroup
                  value={viewMode}
                  exclusive
                  onChange={(_, value) => value && setViewMode(value)}
                  size="small"
                >
                  <ToggleButton value="cards">
                    <PieChart />
                  </ToggleButton>
                  <ToggleButton value="table">
                    <BarChart />
                  </ToggleButton>
                </ToggleButtonGroup>
              </Stack>
            </Box>

            {viewMode === 'cards' ? (
              <Grid container spacing={3}>
                {filteredStocks.map(stock => (
                  <Grid item xs={12} sm={6} md={4} key={stock.id}>
                    {renderStockCard(stock)}
                  </Grid>
                ))}
              </Grid>
            ) : (
              <TableContainer>
                <Table>
                  <TableHead>
                    <TableRow>
                      <TableCell>Symbol</TableCell>
                      <TableCell align="right">Price</TableCell>
                      <TableCell align="right">Change</TableCell>
                      <TableCell align="right">Shares</TableCell>
                      <TableCell align="right">Value</TableCell>
                      <TableCell align="right">Gain/Loss</TableCell>
                      <TableCell align="right">Actions</TableCell>
                    </TableRow>
                  </TableHead>
                  <TableBody>
                    {filteredStocks.map((stock) => (
                      <TableRow key={stock.id}>
                        <TableCell>
                          <Box display="flex" alignItems="center" gap={1}>
                            <Box>
                              <Typography variant="body2" fontWeight="bold">
                                {stock.symbol}
                              </Typography>
                              <Typography variant="caption" color="text.secondary">
                                {stock.name}
                              </Typography>
                            </Box>
                          </Box>
                        </TableCell>
                        <TableCell align="right">
                          ${stock.price.toFixed(2)}
                        </TableCell>
                        <TableCell align="right">
                          <Box display="flex" alignItems="center" justifyContent="flex-end" gap={0.5}>
                            {stock.change >= 0 ? (
                              <ArrowUpward color="success" fontSize="small" />
                            ) : (
                              <ArrowDownward color="error" fontSize="small" />
                            )}
                            <Typography
                              variant="body2"
                              color={stock.change >= 0 ? 'success.main' : 'error.main'}
                            >
                              {stock.changePercent.toFixed(2)}%
                            </Typography>
                          </Box>
                        </TableCell>
                        <TableCell align="right">
                          {stock.shares || '-'}
                        </TableCell>
                        <TableCell align="right">
                          {stock.totalValue ? `$${stock.totalValue.toLocaleString()}` : '-'}
                        </TableCell>
                        <TableCell align="right">
                          {stock.totalGainLoss !== undefined ? (
                            <Typography
                              variant="body2"
                              color={stock.totalGainLoss >= 0 ? 'success.main' : 'error.main'}
                            >
                              {stock.totalGainLoss >= 0 ? '+' : ''}${stock.totalGainLoss.toFixed(2)} ({stock.totalGainLossPercent?.toFixed(2)}%)
                            </Typography>
                          ) : '-'}
                        </TableCell>
                        <TableCell align="right">
                          <Stack direction="row" spacing={1}>
                            <Button
                              size="small"
                              variant="contained"
                              onClick={() => {
                                setSelectedStock(stock);
                                setTradeAction('buy');
                                setShowTradeDialog(true);
                              }}
                            >
                              Trade
                            </Button>
                            <IconButton
                              size="small"
                              onClick={() => {
                                // Toggle watchlist
                              }}
                            >
                              {stock.isWatchlisted ? <Star color="warning" /> : <StarBorder />}
                            </IconButton>
                          </Stack>
                        </TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              </TableContainer>
            )}
          </Box>
        </TabPanel>

        <TabPanel value={activeTab} index={1}>
          <Box p={2}>
            <Typography variant="h6" gutterBottom>
              Watchlist
            </Typography>
            <List>
              {mockStocks.filter(s => s.isWatchlisted).map((stock) => (
                <ListItem key={stock.id} divider>
                  <ListItemAvatar>
                    <Avatar>{stock.symbol.charAt(0)}</Avatar>
                  </ListItemAvatar>
                  <ListItemText
                    primary={stock.symbol}
                    secondary={stock.name}
                  />
                  <Box textAlign="right" mr={2}>
                    <Typography variant="body1">${stock.price.toFixed(2)}</Typography>
                    <Typography
                      variant="body2"
                      color={stock.change >= 0 ? 'success.main' : 'error.main'}
                    >
                      {stock.change >= 0 ? '+' : ''}{stock.changePercent.toFixed(2)}%
                    </Typography>
                  </Box>
                  <ListItemSecondaryAction>
                    <Stack direction="row" spacing={1}>
                      <Button
                        size="small"
                        variant="contained"
                        onClick={() => {
                          setSelectedStock(stock);
                          setTradeAction('buy');
                          setShowTradeDialog(true);
                        }}
                      >
                        Buy
                      </Button>
                      <IconButton
                        edge="end"
                        onClick={() => {
                          // Remove from watchlist
                        }}
                      >
                        <Remove />
                      </IconButton>
                    </Stack>
                  </ListItemSecondaryAction>
                </ListItem>
              ))}
            </List>
          </Box>
        </TabPanel>

        <TabPanel value={activeTab} index={2}>
          <Box p={2}>
            <Typography variant="h6" gutterBottom>
              Order History
            </Typography>
            <TableContainer>
              <Table>
                <TableHead>
                  <TableRow>
                    <TableCell>Type</TableCell>
                    <TableCell>Symbol</TableCell>
                    <TableCell align="right">Quantity</TableCell>
                    <TableCell align="right">Price</TableCell>
                    <TableCell>Status</TableCell>
                    <TableCell>Time</TableCell>
                  </TableRow>
                </TableHead>
                <TableBody>
                  {mockOrders.map((order) => (
                    <TableRow key={order.id}>
                      <TableCell>
                        <Chip
                          label={`${order.type} ${order.action}`}
                          size="small"
                          color={order.action === 'buy' ? 'success' : 'error'}
                          variant="outlined"
                        />
                      </TableCell>
                      <TableCell>{order.symbol}</TableCell>
                      <TableCell align="right">{order.quantity}</TableCell>
                      <TableCell align="right">
                        {order.avgFillPrice ? `$${order.avgFillPrice.toFixed(2)}` : 
                         order.price ? `$${order.price.toFixed(2)}` : 
                         'Market'}
                      </TableCell>
                      <TableCell>
                        <Chip
                          label={order.status}
                          size="small"
                          color={
                            order.status === 'filled' ? 'success' :
                            order.status === 'pending' ? 'warning' :
                            'error'
                          }
                        />
                      </TableCell>
                      <TableCell>
                        {format(new Date(order.createdAt), 'MMM dd, HH:mm')}
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </TableContainer>
          </Box>
        </TabPanel>

        <TabPanel value={activeTab} index={3}>
          <Box p={2}>
            <Grid container spacing={3}>
              <Grid item xs={12} md={6}>
                <Card>
                  <CardContent>
                    <Typography variant="h6" gutterBottom>
                      Market Movers
                    </Typography>
                    <Stack spacing={2}>
                      <Box>
                        <Typography variant="subtitle2" color="success.main" gutterBottom>
                          Top Gainers
                        </Typography>
                        {mockStocks
                          .filter(s => s.change > 0)
                          .sort((a, b) => b.changePercent - a.changePercent)
                          .slice(0, 3)
                          .map((stock) => (
                            <Box key={stock.id} display="flex" justifyContent="space-between" py={1}>
                              <Typography variant="body2">{stock.symbol}</Typography>
                              <Typography variant="body2" color="success.main">
                                +{stock.changePercent.toFixed(2)}%
                              </Typography>
                            </Box>
                          ))}
                      </Box>
                      <Divider />
                      <Box>
                        <Typography variant="subtitle2" color="error.main" gutterBottom>
                          Top Losers
                        </Typography>
                        {mockStocks
                          .filter(s => s.change < 0)
                          .sort((a, b) => a.changePercent - b.changePercent)
                          .slice(0, 3)
                          .map((stock) => (
                            <Box key={stock.id} display="flex" justifyContent="space-between" py={1}>
                              <Typography variant="body2">{stock.symbol}</Typography>
                              <Typography variant="body2" color="error.main">
                                {stock.changePercent.toFixed(2)}%
                              </Typography>
                            </Box>
                          ))}
                      </Box>
                    </Stack>
                  </CardContent>
                </Card>
              </Grid>

              <Grid item xs={12} md={6}>
                <Card>
                  <CardContent>
                    <Typography variant="h6" gutterBottom>
                      Research Tools
                    </Typography>
                    <Stack spacing={2}>
                      <Button
                        variant="outlined"
                        fullWidth
                        startIcon={<Article />}
                        sx={{ justifyContent: 'flex-start' }}
                      >
                        Company Financials
                      </Button>
                      <Button
                        variant="outlined"
                        fullWidth
                        startIcon={<Timeline />}
                        sx={{ justifyContent: 'flex-start' }}
                      >
                        Technical Analysis
                      </Button>
                      <Button
                        variant="outlined"
                        fullWidth
                        startIcon={<Assessment />}
                        sx={{ justifyContent: 'flex-start' }}
                      >
                        Analyst Ratings
                      </Button>
                      <Button
                        variant="outlined"
                        fullWidth
                        startIcon={<Info />}
                        sx={{ justifyContent: 'flex-start' }}
                      >
                        News & Events
                      </Button>
                    </Stack>
                  </CardContent>
                </Card>
              </Grid>

              <Grid item xs={12}>
                <Alert severity="info" icon={<Lightbulb />}>
                  <Typography variant="body2">
                    <strong>Investment Tip:</strong> Diversify your portfolio across different sectors to minimize risk. Consider adding healthcare or consumer goods stocks to balance your tech-heavy portfolio.
                  </Typography>
                </Alert>
              </Grid>
            </Grid>
          </Box>
        </TabPanel>
      </Paper>

      {/* Trade Dialog */}
      <Dialog open={showTradeDialog} onClose={() => setShowTradeDialog(false)} maxWidth="sm" fullWidth>
        <DialogTitle>
          <Box display="flex" alignItems="center" justifyContent="space-between">
            <Typography variant="h6">
              {tradeAction === 'buy' ? 'Buy' : 'Sell'} {selectedStock?.symbol}
            </Typography>
            <Chip
              label={`$${selectedStock?.price.toFixed(2)}`}
              color="primary"
            />
          </Box>
        </DialogTitle>
        <DialogContent>
          <Stack spacing={3} mt={2}>
            <FormControl fullWidth>
              <InputLabel>Order Type</InputLabel>
              <Select
                value={orderType}
                onChange={(e) => setOrderType(e.target.value as any)}
                label="Order Type"
              >
                <MenuItem value="market">Market Order</MenuItem>
                <MenuItem value="limit">Limit Order</MenuItem>
                <MenuItem value="stop">Stop Loss</MenuItem>
                <MenuItem value="stop-limit">Stop Limit</MenuItem>
              </Select>
            </FormControl>

            <TextField
              label="Quantity"
              type="number"
              fullWidth
              value={orderQuantity}
              onChange={(e) => setOrderQuantity(e.target.value)}
              inputProps={{ min: 1 }}
            />

            {(orderType === 'limit' || orderType === 'stop-limit') && (
              <TextField
                label="Limit Price"
                type="number"
                fullWidth
                value={orderPrice}
                onChange={(e) => setOrderPrice(e.target.value)}
                InputProps={{
                  startAdornment: <InputAdornment position="start">$</InputAdornment>,
                }}
              />
            )}

            {(orderType === 'stop' || orderType === 'stop-limit') && (
              <TextField
                label="Stop Price"
                type="number"
                fullWidth
                value={orderStopPrice}
                onChange={(e) => setOrderStopPrice(e.target.value)}
                InputProps={{
                  startAdornment: <InputAdornment position="start">$</InputAdornment>,
                }}
              />
            )}

            {orderQuantity && selectedStock && (
              <Alert severity="info">
                <Typography variant="body2">
                  Estimated {tradeAction === 'buy' ? 'Cost' : 'Proceeds'}: ${(Number(orderQuantity) * selectedStock.price).toFixed(2)}
                </Typography>
                {tradeAction === 'buy' && (
                  <Typography variant="caption">
                    Available cash: ${mockPortfolioStats.cashBalance.toLocaleString()}
                  </Typography>
                )}
              </Alert>
            )}

            <Box>
              <Typography variant="body2" color="text.secondary" gutterBottom>
                Quick amounts
              </Typography>
              <Stack direction="row" spacing={1}>
                {[10, 25, 50, 100].map((qty) => (
                  <Chip
                    key={qty}
                    label={qty}
                    onClick={() => setOrderQuantity(qty.toString())}
                    clickable
                  />
                ))}
              </Stack>
            </Box>
          </Stack>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setShowTradeDialog(false)}>Cancel</Button>
          <Button
            variant="contained"
            color={tradeAction === 'buy' ? 'primary' : 'error'}
            onClick={handleTrade}
            disabled={!orderQuantity || Number(orderQuantity) <= 0}
          >
            {tradeAction === 'buy' ? 'Place Buy Order' : 'Place Sell Order'}
          </Button>
        </DialogActions>
      </Dialog>
    </Box>
  );
};

export default StockTradingPortfolio;