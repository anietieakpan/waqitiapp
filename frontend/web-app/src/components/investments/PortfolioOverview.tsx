import React from 'react';
import { Grid, Card, CardContent, Typography, Box, Chip } from '@mui/material';
import { PieChart, Pie, Cell, ResponsiveContainer, Legend, Tooltip } from 'recharts';
import TrendingUpIcon from '@mui/icons-material/TrendingUp';
import TrendingDownIcon from '@mui/icons-material/TrendingDown';
import { formatCurrency, formatPercent } from '@/utils/formatters';

interface Portfolio {
  totalValue: number;
  totalGain: number;
  totalGainPercent: number;
  todayChange: number;
  todayChangePercent: number;
  assetAllocation?: Array<{
    name: string;
    value: number;
    color: string;
  }>;
}

interface PortfolioOverviewProps {
  portfolio: Portfolio | null;
}

const COLORS = ['#0088FE', '#00C49F', '#FFBB28', '#FF8042', '#8884D8'];

const PortfolioOverview: React.FC<PortfolioOverviewProps> = ({ portfolio }) => {
  if (!portfolio) {
    return (
      <Card>
        <CardContent>
          <Typography color="text.secondary">No portfolio data available</Typography>
        </CardContent>
      </Card>
    );
  }

  const isPositive = portfolio.totalGain >= 0;

  return (
    <Grid container spacing={3}>
      <Grid item xs={12} md={6}>
        <Card>
          <CardContent>
            <Typography variant="h6" gutterBottom>
              Asset Allocation
            </Typography>
            {portfolio.assetAllocation && portfolio.assetAllocation.length > 0 ? (
              <ResponsiveContainer width="100%" height={300}>
                <PieChart>
                  <Pie
                    data={portfolio.assetAllocation}
                    cx="50%"
                    cy="50%"
                    labelLine={false}
                    label={({ name, percent }) => `${name} ${(percent * 100).toFixed(0)}%`}
                    outerRadius={80}
                    fill="#8884d8"
                    dataKey="value"
                  >
                    {portfolio.assetAllocation.map((entry, index) => (
                      <Cell key={`cell-${index}`} fill={entry.color || COLORS[index % COLORS.length]} />
                    ))}
                  </Pie>
                  <Tooltip formatter={(value: number) => formatCurrency(value)} />
                  <Legend />
                </PieChart>
              </ResponsiveContainer>
            ) : (
              <Typography color="text.secondary" align="center" sx={{ py: 4 }}>
                No allocation data available
              </Typography>
            )}
          </CardContent>
        </Card>
      </Grid>

      <Grid item xs={12} md={6}>
        <Grid container spacing={2}>
          <Grid item xs={12}>
            <Card>
              <CardContent>
                <Typography color="text.secondary" gutterBottom>
                  Total Gain/Loss
                </Typography>
                <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
                  {isPositive ? (
                    <TrendingUpIcon color="success" fontSize="large" />
                  ) : (
                    <TrendingDownIcon color="error" fontSize="large" />
                  )}
                  <Box>
                    <Typography variant="h4" color={isPositive ? 'success.main' : 'error.main'}>
                      {isPositive ? '+' : ''}
                      {formatCurrency(portfolio.totalGain)}
                    </Typography>
                    <Typography variant="body2" color={isPositive ? 'success.main' : 'error.main'}>
                      {isPositive ? '+' : ''}
                      {formatPercent(portfolio.totalGainPercent)}
                    </Typography>
                  </Box>
                </Box>
              </CardContent>
            </Card>
          </Grid>

          <Grid item xs={12}>
            <Card>
              <CardContent>
                <Typography color="text.secondary" gutterBottom>
                  Today's Change
                </Typography>
                <Typography
                  variant="h5"
                  color={portfolio.todayChange >= 0 ? 'success.main' : 'error.main'}
                >
                  {portfolio.todayChange >= 0 ? '+' : ''}
                  {formatCurrency(portfolio.todayChange)}
                </Typography>
                <Typography
                  variant="body2"
                  color={portfolio.todayChange >= 0 ? 'success.main' : 'error.main'}
                >
                  {portfolio.todayChange >= 0 ? '+' : ''}
                  {formatPercent(portfolio.todayChangePercent)}
                </Typography>
              </CardContent>
            </Card>
          </Grid>
        </Grid>
      </Grid>
    </Grid>
  );
};

export default PortfolioOverview;
