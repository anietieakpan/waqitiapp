import React, { useState, useMemo } from 'react';
import {
  Paper,
  Typography,
  Box,
  Grid,
  Card,
  CardContent,
  List,
  ListItem,
  ListItemIcon,
  ListItemText,
  ListItemSecondaryAction,
  Avatar,
  Chip,
  LinearProgress,
  IconButton,
  Menu,
  MenuItem,
  useTheme,
  alpha,
  Divider,
  Tabs,
  Tab,
} from '@mui/material';
import TrendingUpIcon from '@mui/icons-material/TrendingUp';
import TrendingDownIcon from '@mui/icons-material/TrendingDown';
import TrendingFlatIcon from '@mui/icons-material/TrendingFlat';
import ShoppingIcon from '@mui/icons-material/ShoppingCart';
import RestaurantIcon from '@mui/icons-material/Restaurant';
import GasIcon from '@mui/icons-material/LocalGasStation';
import EntertainmentIcon from '@mui/icons-material/Movie';
import HealthIcon from '@mui/icons-material/LocalHospital';
import EducationIcon from '@mui/icons-material/School';
import HomeIcon from '@mui/icons-material/Home';
import TransportIcon from '@mui/icons-material/DirectionsCar';
import MoreVertIcon from '@mui/icons-material/MoreVert';
import TimelineIcon from '@mui/icons-material/Timeline';
import PieChartIcon from '@mui/icons-material/PieChart';
import BarChartIcon from '@mui/icons-material/BarChart';
import MoneyIcon from '@mui/icons-material/AttachMoney';
import SavingsIcon from '@mui/icons-material/Savings';
import CreditCardIcon from '@mui/icons-material/CreditCard';
import BankIcon from '@mui/icons-material/AccountBalance';;
import { format, startOfMonth, endOfMonth, subMonths, startOfWeek, endOfWeek } from 'date-fns';
import { Transaction, TransactionType, SpendingInsight } from '../../types/wallet';
import { formatCurrency, formatPercentage } from '../../utils/formatters';

interface SpendingInsightsProps {
  transactions: Transaction[];
  period?: 'WEEK' | 'MONTH' | 'QUARTER';
}

interface CategoryData {
  category: string;
  amount: number;
  transactionCount: number;
  percentage: number;
  change: number;
  icon: React.ReactElement;
  color: string;
}

const SpendingInsights: React.FC<SpendingInsightsProps> = ({
  transactions = [],
  period = 'MONTH',
}) => {
  const theme = useTheme();
  const [selectedPeriod, setSelectedPeriod] = useState<'WEEK' | 'MONTH' | 'QUARTER'>(period);
  const [viewMode, setViewMode] = useState<'CATEGORIES' | 'TRENDS' | 'MERCHANTS'>('CATEGORIES');
  const [menuAnchor, setMenuAnchor] = useState<null | HTMLElement>(null);

  const categoryIcons: Record<string, { icon: React.ReactElement; color: string }> = {
    'Food & Dining': { icon: <RestaurantIcon />, color: theme.palette.warning.main },
    'Shopping': { icon: <ShoppingIcon />, color: theme.palette.primary.main },
    'Transportation': { icon: <TransportIcon />, color: theme.palette.info.main },
    'Entertainment': { icon: <EntertainmentIcon />, color: theme.palette.secondary.main },
    'Health & Fitness': { icon: <HealthIcon />, color: theme.palette.success.main },
    'Education': { icon: <EducationIcon />, color: theme.palette.error.main },
    'Home & Garden': { icon: <HomeIcon />, color: '#8e24aa' },
    'Gas & Fuel': { icon: <GasIcon />, color: '#ff6f00' },
    'Bills & Utilities': { icon: <BankIcon />, color: '#795548' },
    'Savings': { icon: <SavingsIcon />, color: '#4caf50' },
    'Other': { icon: <MoneyIcon />, color: theme.palette.grey[500] },
  };

  const getPeriodDates = (period: 'WEEK' | 'MONTH' | 'QUARTER') => {
    const now = new Date();
    switch (period) {
      case 'WEEK':
        return {
          current: { start: startOfWeek(now), end: endOfWeek(now) },
          previous: { 
            start: startOfWeek(subMonths(now, 0)), 
            end: endOfWeek(subMonths(now, 0)) 
          },
        };
      case 'MONTH':
        return {
          current: { start: startOfMonth(now), end: endOfMonth(now) },
          previous: { 
            start: startOfMonth(subMonths(now, 1)), 
            end: endOfMonth(subMonths(now, 1)) 
          },
        };
      case 'QUARTER':
        const quarterStart = new Date(now.getFullYear(), Math.floor(now.getMonth() / 3) * 3, 1);
        const quarterEnd = new Date(quarterStart.getFullYear(), quarterStart.getMonth() + 3, 0);
        const prevQuarterStart = new Date(quarterStart.getFullYear(), quarterStart.getMonth() - 3, 1);
        const prevQuarterEnd = new Date(prevQuarterStart.getFullYear(), prevQuarterStart.getMonth() + 3, 0);
        return {
          current: { start: quarterStart, end: quarterEnd },
          previous: { start: prevQuarterStart, end: prevQuarterEnd },
        };
    }
  };

  const categoryData = useMemo(() => {
    const dates = getPeriodDates(selectedPeriod);
    
    // Filter transactions for current period (only spending)
    const currentTransactions = transactions.filter(tx => {
      const txDate = new Date(tx.createdAt);
      return txDate >= dates.current.start && 
             txDate <= dates.current.end && 
             (tx.type === TransactionType.DEBIT || tx.type === TransactionType.WITHDRAWAL);
    });

    // Filter transactions for previous period
    const previousTransactions = transactions.filter(tx => {
      const txDate = new Date(tx.createdAt);
      return txDate >= dates.previous.start && 
             txDate <= dates.previous.end && 
             (tx.type === TransactionType.DEBIT || tx.type === TransactionType.WITHDRAWAL);
    });

    // Group by category
    const currentByCategory = currentTransactions.reduce((acc, tx) => {
      const category = tx.metadata?.category || 'Other';
      if (!acc[category]) acc[category] = { amount: 0, count: 0 };
      acc[category].amount += tx.amount;
      acc[category].count += 1;
      return acc;
    }, {} as Record<string, { amount: number; count: number }>);

    const previousByCategory = previousTransactions.reduce((acc, tx) => {
      const category = tx.metadata?.category || 'Other';
      if (!acc[category]) acc[category] = { amount: 0, count: 0 };
      acc[category].amount += tx.amount;
      acc[category].count += 1;
      return acc;
    }, {} as Record<string, { amount: number; count: number }>);

    const totalSpending = Object.values(currentByCategory).reduce((sum, cat) => sum + cat.amount, 0);

    // Create category data with change calculation
    const categories: CategoryData[] = Object.entries(currentByCategory)
      .map(([category, current]) => {
        const previous = previousByCategory[category] || { amount: 0, count: 0 };
        const change = previous.amount > 0 
          ? ((current.amount - previous.amount) / previous.amount) * 100
          : current.amount > 0 ? 100 : 0;

        const iconData = categoryIcons[category] || categoryIcons['Other'];

        return {
          category,
          amount: current.amount,
          transactionCount: current.count,
          percentage: totalSpending > 0 ? (current.amount / totalSpending) * 100 : 0,
          change,
          icon: iconData.icon,
          color: iconData.color,
        };
      })
      .sort((a, b) => b.amount - a.amount);

    return categories;
  }, [transactions, selectedPeriod]);

  const totalSpending = categoryData.reduce((sum, cat) => sum + cat.amount, 0);
  const transactionCount = categoryData.reduce((sum, cat) => sum + cat.transactionCount, 0);
  const averageTransaction = transactionCount > 0 ? totalSpending / transactionCount : 0;

  const handleMenuOpen = (event: React.MouseEvent<HTMLElement>) => {
    setMenuAnchor(event.currentTarget);
  };

  const handleMenuClose = () => {
    setMenuAnchor(null);
  };

  const renderOverviewCards = () => (
    <Grid container spacing={2} sx={{ mb: 3 }}>
      <Grid item xs={12} sm={4}>
        <Card>
          <CardContent sx={{ textAlign: 'center' }}>
            <MoneyIcon sx={{ fontSize: 40, color: theme.palette.primary.main, mb: 1 }} />
            <Typography variant="h5" sx={{ fontWeight: 600 }}>
              {formatCurrency(totalSpending)}
            </Typography>
            <Typography variant="body2" color="text.secondary">
              Total Spending
            </Typography>
          </CardContent>
        </Card>
      </Grid>
      
      <Grid item xs={12} sm={4}>
        <Card>
          <CardContent sx={{ textAlign: 'center' }}>
            <CreditCardIcon sx={{ fontSize: 40, color: theme.palette.info.main, mb: 1 }} />
            <Typography variant="h5" sx={{ fontWeight: 600 }}>
              {transactionCount}
            </Typography>
            <Typography variant="body2" color="text.secondary">
              Transactions
            </Typography>
          </CardContent>
        </Card>
      </Grid>
      
      <Grid item xs={12} sm={4}>
        <Card>
          <CardContent sx={{ textAlign: 'center' }}>
            <TimelineIcon sx={{ fontSize: 40, color: theme.palette.success.main, mb: 1 }} />
            <Typography variant="h5" sx={{ fontWeight: 600 }}>
              {formatCurrency(averageTransaction)}
            </Typography>
            <Typography variant="body2" color="text.secondary">
              Average Transaction
            </Typography>
          </CardContent>
        </Card>
      </Grid>
    </Grid>
  );

  const renderCategories = () => (
    <List sx={{ py: 0 }}>
      {categoryData.map((category, index) => (
        <React.Fragment key={category.category}>
          <ListItem sx={{ py: 2 }}>
            <ListItemIcon>
              <Avatar
                sx={{
                  bgcolor: alpha(category.color, 0.1),
                  color: category.color,
                }}
              >
                {category.icon}
              </Avatar>
            </ListItemIcon>
            
            <ListItemText
              primary={
                <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
                  <Typography variant="subtitle1" sx={{ fontWeight: 500 }}>
                    {category.category}
                  </Typography>
                  <Chip
                    size="small"
                    label={`${category.transactionCount} transactions`}
                    variant="outlined"
                  />
                </Box>
              }
              secondary={
                <Box sx={{ mt: 1 }}>
                  <Box sx={{ display: 'flex', justifyContent: 'space-between', mb: 0.5 }}>
                    <Typography variant="body2" color="text.secondary">
                      {formatPercentage(category.percentage)} of total spending
                    </Typography>
                    <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.5 }}>
                      {category.change > 0 ? (
                        <TrendingUpIcon sx={{ fontSize: 16, color: theme.palette.error.main }} />
                      ) : category.change < 0 ? (
                        <TrendingDownIcon sx={{ fontSize: 16, color: theme.palette.success.main }} />
                      ) : (
                        <TrendingFlatIcon sx={{ fontSize: 16, color: theme.palette.grey[500] }} />
                      )}
                      <Typography
                        variant="caption"
                        sx={{
                          color: category.change > 0 
                            ? theme.palette.error.main 
                            : category.change < 0 
                              ? theme.palette.success.main 
                              : theme.palette.grey[500],
                          fontWeight: 500,
                        }}
                      >
                        {Math.abs(category.change).toFixed(1)}%
                      </Typography>
                    </Box>
                  </Box>
                  
                  <LinearProgress
                    variant="determinate"
                    value={category.percentage}
                    sx={{
                      height: 6,
                      borderRadius: 3,
                      backgroundColor: alpha(theme.palette.grey[300], 0.3),
                      '& .MuiLinearProgress-bar': {
                        backgroundColor: category.color,
                        borderRadius: 3,
                      },
                    }}
                  />
                </Box>
              }
            />
            
            <ListItemSecondaryAction>
              <Typography variant="h6" sx={{ fontWeight: 600 }}>
                {formatCurrency(category.amount)}
              </Typography>
            </ListItemSecondaryAction>
          </ListItem>
          
          {index < categoryData.length - 1 && <Divider variant="inset" component="li" />}
        </React.Fragment>
      ))}
    </List>
  );

  return (
    <Paper sx={{ p: 0 }}>
      <Box sx={{ p: 2, display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
        <Typography variant="h6" sx={{ fontWeight: 600 }}>
          Spending Insights
        </Typography>
        <IconButton onClick={handleMenuOpen}>
          <MoreVertIcon />
        </IconButton>
      </Box>

      <Box sx={{ px: 2 }}>
        <Tabs 
          value={selectedPeriod} 
          onChange={(_, value) => setSelectedPeriod(value)}
          variant="fullWidth"
        >
          <Tab label="This Week" value="WEEK" />
          <Tab label="This Month" value="MONTH" />
          <Tab label="This Quarter" value="QUARTER" />
        </Tabs>
      </Box>

      <Divider />

      <Box sx={{ p: 2 }}>
        {renderOverviewCards()}
        
        {categoryData.length === 0 ? (
          <Box sx={{ textAlign: 'center', py: 4 }}>
            <PieChartIcon sx={{ fontSize: 48, color: 'text.secondary', mb: 2 }} />
            <Typography variant="body1" color="text.secondary">
              No spending data available
            </Typography>
            <Typography variant="body2" color="text.secondary">
              Start making transactions to see insights
            </Typography>
          </Box>
        ) : (
          renderCategories()
        )}
      </Box>

      {/* Menu */}
      <Menu
        anchorEl={menuAnchor}
        open={Boolean(menuAnchor)}
        onClose={handleMenuClose}
        anchorOrigin={{ vertical: 'top', horizontal: 'right' }}
        transformOrigin={{ vertical: 'top', horizontal: 'right' }}
      >
        <MenuItem onClick={handleMenuClose}>
          <PieChartIcon sx={{ mr: 1 }} /> Category Breakdown
        </MenuItem>
        <MenuItem onClick={handleMenuClose}>
          <BarChartIcon sx={{ mr: 1 }} /> Monthly Trends
        </MenuItem>
        <MenuItem onClick={handleMenuClose}>
          <TimelineIcon sx={{ mr: 1 }} /> Export Report
        </MenuItem>
      </Menu>
    </Paper>
  );
};

export default SpendingInsights;