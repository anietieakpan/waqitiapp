import React from 'react';
import {
  Paper,
  Typography,
  Grid,
  Box,
  IconButton,
  useTheme,
  useMediaQuery,
} from '@mui/material';
import SendIcon from '@mui/icons-material/Send';
import RequestIcon from '@mui/icons-material/RequestPage';
import AddIcon from '@mui/icons-material/Add';
import ReceiptIcon from '@mui/icons-material/Receipt';
import ScanIcon from '@mui/icons-material/QrCodeScanner';
import BankIcon from '@mui/icons-material/AccountBalance';
import CardIcon from '@mui/icons-material/CreditCard';
import HistoryIcon from '@mui/icons-material/History';
import SettingsIcon from '@mui/icons-material/Settings';
import AnalyticsIcon from '@mui/icons-material/Analytics';
import GroupIcon from '@mui/icons-material/Group';
import ScheduleIcon from '@mui/icons-material/Schedule';;
import { useNavigate } from 'react-router-dom';

interface QuickAction {
  icon: React.ReactNode;
  label: string;
  route: string;
  color?: string;
}

const QuickActions: React.FC = () => {
  const theme = useTheme();
  const navigate = useNavigate();
  const isMobile = useMediaQuery(theme.breakpoints.down('sm'));

  const quickActions: QuickAction[] = [
    {
      icon: <SendIcon />,
      label: 'Send Money',
      route: '/payments/send',
      color: theme.palette.primary.main,
    },
    {
      icon: <RequestIcon />,
      label: 'Request Money',
      route: '/payments/request',
      color: theme.palette.secondary.main,
    },
    {
      icon: <AddIcon />,
      label: 'Add Money',
      route: '/wallet/add-money',
      color: theme.palette.success.main,
    },
    {
      icon: <ScanIcon />,
      label: 'Scan QR',
      route: '/qr/scan',
      color: theme.palette.warning.main,
    },
    {
      icon: <HistoryIcon />,
      label: 'Transactions',
      route: '/transactions',
      color: theme.palette.info.main,
    },
    {
      icon: <CardIcon />,
      label: 'Cards',
      route: '/payment-methods',
      color: theme.palette.error.main,
    },
    {
      icon: <BankIcon />,
      label: 'Bank Accounts',
      route: '/bank-accounts',
      color: theme.palette.primary.dark,
    },
    {
      icon: <GroupIcon />,
      label: 'Split Bill',
      route: '/payments/split',
      color: theme.palette.secondary.dark,
    },
    {
      icon: <ScheduleIcon />,
      label: 'Scheduled',
      route: '/payments/scheduled',
      color: theme.palette.success.dark,
    },
    {
      icon: <AnalyticsIcon />,
      label: 'Analytics',
      route: '/analytics',
      color: theme.palette.warning.dark,
    },
    {
      icon: <ReceiptIcon />,
      label: 'Receipts',
      route: '/receipts',
      color: theme.palette.info.dark,
    },
    {
      icon: <SettingsIcon />,
      label: 'Settings',
      route: '/settings',
      color: theme.palette.text.secondary,
    },
  ];

  const handleActionClick = (route: string) => {
    navigate(route);
  };

  const displayedActions = isMobile ? quickActions.slice(0, 8) : quickActions;

  return (
    <Paper sx={{ p: 2 }}>
      <Typography variant="h6" gutterBottom>
        Quick Actions
      </Typography>
      
      <Grid container spacing={2}>
        {displayedActions.map((action, index) => (
          <Grid item xs={3} sm={2} md={1.5} key={index}>
            <Box
              sx={{
                display: 'flex',
                flexDirection: 'column',
                alignItems: 'center',
                cursor: 'pointer',
                p: 1,
                borderRadius: 2,
                transition: 'all 0.2s',
                '&:hover': {
                  bgcolor: 'action.hover',
                  transform: 'translateY(-2px)',
                },
              }}
              onClick={() => handleActionClick(action.route)}
            >
              <IconButton
                sx={{
                  bgcolor: action.color ? `${action.color}15` : 'action.hover',
                  color: action.color || 'text.primary',
                  mb: 1,
                  '&:hover': {
                    bgcolor: action.color ? `${action.color}25` : 'action.selected',
                  },
                }}
                size={isMobile ? 'medium' : 'large'}
              >
                {action.icon}
              </IconButton>
              <Typography
                variant="caption"
                align="center"
                sx={{
                  fontSize: isMobile ? '0.7rem' : '0.75rem',
                  lineHeight: 1.2,
                  color: 'text.secondary',
                }}
              >
                {action.label}
              </Typography>
            </Box>
          </Grid>
        ))}
      </Grid>
      
      {isMobile && quickActions.length > 8 && (
        <Box sx={{ mt: 2, textAlign: 'center' }}>
          <Typography
            variant="body2"
            color="primary"
            sx={{ cursor: 'pointer' }}
            onClick={() => navigate('/actions')}
          >
            View More Actions
          </Typography>
        </Box>
      )}
    </Paper>
  );
};

export default QuickActions;