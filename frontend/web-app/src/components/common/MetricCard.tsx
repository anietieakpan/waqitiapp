import React from 'react';
import {
  Card,
  CardContent,
  Typography,
  Box,
  Skeleton,
} from '@mui/material';
import TrendingUpIcon from '@mui/icons-material/TrendingUp';
import TrendingDownIcon from '@mui/icons-material/TrendingDown';

export interface MetricCardProps {
  title: string;
  value: string | number;
  change?: number;
  changeLabel?: string;
  icon?: React.ReactNode;
  loading?: boolean;
  currency?: string;
}

const MetricCard: React.FC<MetricCardProps> = ({
  title,
  value,
  change,
  changeLabel,
  icon,
  loading = false,
  currency,
}) => {
  if (loading) {
    return (
      <Card>
        <CardContent>
          <Skeleton variant="text" width="60%" />
          <Skeleton variant="text" width="80%" height={40} />
          <Skeleton variant="text" width="40%" />
        </CardContent>
      </Card>
    );
  }

  const isPositive = change !== undefined && change >= 0;

  return (
    <Card>
      <CardContent>
        <Box display="flex" justifyContent="space-between" alignItems="start">
          <Box>
            <Typography variant="body2" color="text.secondary" gutterBottom>
              {title}
            </Typography>
            <Typography variant="h4" fontWeight={600}>
              {currency && `${currency} `}{value}
            </Typography>
            {change !== undefined && (
              <Box display="flex" alignItems="center" gap={0.5} mt={1}>
                {isPositive ? (
                  <TrendingUpIcon fontSize="small" color="success" />
                ) : (
                  <TrendingDownIcon fontSize="small" color="error" />
                )}
                <Typography
                  variant="body2"
                  color={isPositive ? 'success.main' : 'error.main'}
                >
                  {isPositive ? '+' : ''}{change}% {changeLabel || 'vs last period'}
                </Typography>
              </Box>
            )}
          </Box>
          {icon && (
            <Box
              sx={{
                bgcolor: 'primary.light',
                borderRadius: 2,
                p: 1,
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
              }}
            >
              {icon}
            </Box>
          )}
        </Box>
      </CardContent>
    </Card>
  );
};

export default MetricCard;
