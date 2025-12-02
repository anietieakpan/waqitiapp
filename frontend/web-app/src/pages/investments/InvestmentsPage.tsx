import React, { useEffect, useState } from 'react';
import {
  Container,
  Grid,
  Paper,
  Typography,
  Box,
  Button,
  Tab,
  Tabs,
  Card,
  CardContent,
  IconButton,
  Chip,
  LinearProgress,
  useTheme,
  useMediaQuery,
  Alert,
  CircularProgress,
} from '@mui/material';
import TrendingUpIcon from '@mui/icons-material/TrendingUp';
import TrendingDownIcon from '@mui/icons-material/TrendingDown';
import AddIcon from '@mui/icons-material/Add';
import RefreshIcon from '@mui/icons-material/Refresh';
import DownloadIcon from '@mui/icons-material/Download';
import { useNavigate } from 'react-router-dom';
import { useAppDispatch, useAppSelector } from '@/hooks/redux';
import { fetchPortfolio, fetchHoldings } from '@/store/slices/investmentSlice';
import { formatCurrency, formatPercent } from '@/utils/formatters';
import PortfolioOverview from '@/components/investments/PortfolioOverview';
import HoldingsList from '@/components/investments/HoldingsList';
import PerformanceChart from '@/components/investments/PerformanceChart';
import InvestmentTransactions from '@/components/investments/InvestmentTransactions';

interface TabPanelProps {
  children?: React.ReactNode;
  index: number;
  value: number;
}

const TabPanel: React.FC<TabPanelProps> = ({ children, value, index }) => {
  return (
    <div
      role="tabpanel"
      hidden={value !== index}
      id={`investment-tabpanel-${index}`}
      aria-labelledby={`investment-tab-${index}`}
    >
      {value === index && <Box sx={{ pt: 3 }}>{children}</Box>}
    </div>
  );
};

const InvestmentsPage: React.FC = () => {
  const theme = useTheme();
  const navigate = useNavigate();
  const dispatch = useAppDispatch();
  const isMobile = useMediaQuery(theme.breakpoints.down('sm'));

  const [tabValue, setTabValue] = useState(0);

  const {
    portfolio,
    holdings,
    loading,
    error,
  } = useAppSelector((state) => state.investment);

  useEffect(() => {
    dispatch(fetchPortfolio());
    dispatch(fetchHoldings());
  }, [dispatch]);

  const handleTabChange = (event: React.SyntheticEvent, newValue: number) => {
    setTabValue(newValue);
  };

  const handleRefresh = () => {
    dispatch(fetchPortfolio());
    dispatch(fetchHoldings());
  };

  const portfolioValue = portfolio?.totalValue || 0;
  const totalGain = portfolio?.totalGain || 0;
  const totalGainPercent = portfolio?.totalGainPercent || 0;
  const isPositive = totalGain >= 0;

  return (
    <Container maxWidth="lg" sx={{ mt: 4, mb: 4 }}>
      {/* Header */}
      <Box sx={{ mb: 3, display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
        <Typography variant="h4" component="h1" gutterBottom>
          Investments
        </Typography>
        <Box>
          <IconButton onClick={handleRefresh} disabled={loading}>
            <RefreshIcon />
          </IconButton>
          <Button
            variant="contained"
            startIcon={<AddIcon />}
            onClick={() => navigate('/investments/buy')}
            sx={{ ml: 1 }}
          >
            Invest
          </Button>
        </Box>
      </Box>

      {error && (
        <Alert severity="error" sx={{ mb: 3 }}>
          {error}
        </Alert>
      )}

      {/* Portfolio Summary Cards */}
      <Grid container spacing={3} sx={{ mb: 3 }}>
        <Grid item xs={12} md={4}>
          <Card>
            <CardContent>
              <Typography color="text.secondary" gutterBottom>
                Total Portfolio Value
              </Typography>
              <Typography variant="h4" component="div">
                {formatCurrency(portfolioValue)}
              </Typography>
              <Box sx={{ display: 'flex', alignItems: 'center', mt: 1 }}>
                {isPositive ? (
                  <TrendingUpIcon color="success" />
                ) : (
                  <TrendingDownIcon color="error" />
                )}
                <Typography
                  variant="body2"
                  color={isPositive ? 'success.main' : 'error.main'}
                  sx={{ ml: 0.5 }}
                >
                  {isPositive ? '+' : ''}
                  {formatCurrency(totalGain)} ({formatPercent(totalGainPercent)})
                </Typography>
              </Box>
            </CardContent>
          </Card>
        </Grid>

        <Grid item xs={12} md={4}>
          <Card>
            <CardContent>
              <Typography color="text.secondary" gutterBottom>
                Number of Holdings
              </Typography>
              <Typography variant="h4" component="div">
                {holdings?.length || 0}
              </Typography>
              <Typography variant="body2" color="text.secondary" sx={{ mt: 1 }}>
                Across {portfolio?.assetTypes?.length || 0} asset types
              </Typography>
            </CardContent>
          </Card>
        </Grid>

        <Grid item xs={12} md={4}>
          <Card>
            <CardContent>
              <Typography color="text.secondary" gutterBottom>
                Today's Change
              </Typography>
              <Typography variant="h4" component="div" color={portfolio?.todayChange >= 0 ? 'success.main' : 'error.main'}>
                {portfolio?.todayChange >= 0 ? '+' : ''}
                {formatCurrency(portfolio?.todayChange || 0)}
              </Typography>
              <Typography variant="body2" color="text.secondary" sx={{ mt: 1 }}>
                {formatPercent(portfolio?.todayChangePercent || 0)}
              </Typography>
            </CardContent>
          </Card>
        </Grid>
      </Grid>

      {/* Tabs */}
      <Paper sx={{ mb: 3 }}>
        <Tabs
          value={tabValue}
          onChange={handleTabChange}
          variant={isMobile ? 'scrollable' : 'fullWidth'}
          scrollButtons={isMobile ? 'auto' : false}
        >
          <Tab label="Overview" />
          <Tab label="Holdings" />
          <Tab label="Performance" />
          <Tab label="Transactions" />
        </Tabs>
      </Paper>

      {/* Tab Panels */}
      {loading ? (
        <Box sx={{ display: 'flex', justifyContent: 'center', py: 8 }}>
          <CircularProgress />
        </Box>
      ) : (
        <>
          <TabPanel value={tabValue} index={0}>
            <PortfolioOverview portfolio={portfolio} />
          </TabPanel>

          <TabPanel value={tabValue} index={1}>
            <HoldingsList holdings={holdings} />
          </TabPanel>

          <TabPanel value={tabValue} index={2}>
            <PerformanceChart data={portfolio?.performanceData || []} />
          </TabPanel>

          <TabPanel value={tabValue} index={3}>
            <InvestmentTransactions />
          </TabPanel>
        </>
      )}
    </Container>
  );
};

export default InvestmentsPage;
