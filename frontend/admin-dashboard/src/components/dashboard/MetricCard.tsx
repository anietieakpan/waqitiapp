import React from 'react';
import {
  Card,
  CardContent,
  Typography,
  Box,
  Avatar,
  Chip,
  useTheme,
  alpha,
} from '@mui/material';
import { TrendingUp, TrendingDown } from '@mui/icons-material';

interface MetricCardProps {
  title: string;
  value: number | string;
  change?: number;
  icon: React.ReactNode;
  color?: 'primary' | 'secondary' | 'success' | 'error' | 'warning' | 'info';
  format?: 'number' | 'currency' | 'percentage';
  prefix?: string;
  suffix?: string;
}

const MetricCard: React.FC<MetricCardProps> = ({
  title,
  value,
  change = 0,
  icon,
  color = 'primary',
  format = 'number',
  prefix = '',
  suffix = '',
}) => {
  const theme = useTheme();

  const formatValue = (val: number | string): string => {
    if (typeof val === 'string') return val;
    
    switch (format) {
      case 'currency':
        return new Intl.NumberFormat('en-US', {
          style: 'currency',
          currency: 'USD',
          minimumFractionDigits: 0,
          maximumFractionDigits: 0,
        }).format(val);
      case 'percentage':
        return `${val.toFixed(1)}%`;
      case 'number':
      default:
        return new Intl.NumberFormat('en-US').format(val);
    }
  };

  const getChangeColor = () => {
    if (change > 0) return 'success';
    if (change < 0) return 'error';
    return 'default';
  };

  const getChangeIcon = () => {
    if (change > 0) return <TrendingUp fontSize="small" />;
    if (change < 0) return <TrendingDown fontSize="small" />;
    return null;
  };

  return (
    <Card
      sx={{
        height: '100%',
        position: 'relative',
        overflow: 'hidden',
        '&:hover': {
          transform: 'translateY(-2px)',
          boxShadow: theme.shadows[4],
        },
        transition: 'all 0.3s ease',
      }}
    >
      <CardContent>
        <Box sx={{ display: 'flex', alignItems: 'flex-start', mb: 2 }}>
          <Avatar
            sx={{
              bgcolor: alpha(theme.palette[color].main, 0.1),
              color: theme.palette[color].main,
              width: 48,
              height: 48,
            }}
          >
            {icon}
          </Avatar>
          {change !== 0 && (
            <Box sx={{ ml: 'auto' }}>
              <Chip
                size="small"
                icon={getChangeIcon()}
                label={`${change > 0 ? '+' : ''}${change.toFixed(1)}%`}
                color={getChangeColor()}
                sx={{ fontWeight: 'medium' }}
              />
            </Box>
          )}
        </Box>

        <Typography color="text.secondary" variant="body2" gutterBottom>
          {title}
        </Typography>

        <Typography variant="h4" component="div" fontWeight="bold">
          {prefix}
          {formatValue(value)}
          {suffix}
        </Typography>

        {/* Background decoration */}
        <Box
          sx={{
            position: 'absolute',
            right: -20,
            bottom: -20,
            opacity: 0.05,
            transform: 'rotate(-15deg)',
          }}
        >
          <Box
            component={icon.type}
            sx={{
              fontSize: 120,
              color: theme.palette[color].main,
            }}
          />
        </Box>
      </CardContent>
    </Card>
  );
};

export default MetricCard;