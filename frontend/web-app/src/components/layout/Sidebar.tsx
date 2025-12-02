import React from 'react';
import {
  Drawer,
  List,
  ListItem,
  ListItemButton,
  ListItemIcon,
  ListItemText,
  Divider,
  Box,
  Typography,
  Avatar,
  useTheme,
  useMediaQuery,
} from '@mui/material';
import { useNavigate, useLocation } from 'react-router-dom';
import DashboardIcon from '@mui/icons-material/Dashboard';
import AccountBalanceWalletIcon from '@mui/icons-material/AccountBalanceWallet';
import SendIcon from '@mui/icons-material/Send';
import CreditCardIcon from '@mui/icons-material/CreditCard';
import ShowChartIcon from '@mui/icons-material/ShowChart';
import CurrencyBitcoinIcon from '@mui/icons-material/CurrencyBitcoin';
import SavingsIcon from '@mui/icons-material/Savings';
import AccountBalanceIcon from '@mui/icons-material/AccountBalance';
import ShieldIcon from '@mui/icons-material/Shield';
import ReceiptIcon from '@mui/icons-material/Receipt';
import StarIcon from '@mui/icons-material/Star';
import VerifiedUserIcon from '@mui/icons-material/VerifiedUser';
import SecurityIcon from '@mui/icons-material/Security';
import SettingsIcon from '@mui/icons-material/Settings';
import { useAppSelector } from '@/hooks/redux';

const DRAWER_WIDTH = 280;

interface MenuItem {
  text: string;
  icon: React.ReactNode;
  path: string;
  badge?: number;
}

const menuItems: MenuItem[] = [
  { text: 'Dashboard', icon: <DashboardIcon />, path: '/dashboard' },
  { text: 'Wallet', icon: <AccountBalanceWalletIcon />, path: '/wallet' },
  { text: 'Payments', icon: <SendIcon />, path: '/payment' },
  { text: 'Cards', icon: <CreditCardIcon />, path: '/cards' },
  { text: 'Investments', icon: <ShowChartIcon />, path: '/investments' },
  { text: 'Crypto', icon: <CurrencyBitcoinIcon />, path: '/crypto' },
];

const financialItems: MenuItem[] = [
  { text: 'Savings', icon: <SavingsIcon />, path: '/savings' },
  { text: 'Loans', icon: <AccountBalanceIcon />, path: '/loans' },
  { text: 'BNPL', icon: <ReceiptIcon />, path: '/bnpl' },
  { text: 'Insurance', icon: <ShieldIcon />, path: '/insurance' },
  { text: 'Rewards', icon: <StarIcon />, path: '/rewards' },
];

const accountItems: MenuItem[] = [
  { text: 'KYC Verification', icon: <VerifiedUserIcon />, path: '/kyc' },
  { text: 'Security', icon: <SecurityIcon />, path: '/security' },
  { text: 'Settings', icon: <SettingsIcon />, path: '/settings' },
];

interface SidebarProps {
  open: boolean;
  onClose: () => void;
}

const Sidebar: React.FC<SidebarProps> = ({ open, onClose }) => {
  const theme = useTheme();
  const isMobile = useMediaQuery(theme.breakpoints.down('md'));
  const navigate = useNavigate();
  const location = useLocation();
  const { user } = useAppSelector((state) => state.auth);

  const handleNavigation = (path: string) => {
    navigate(path);
    if (isMobile) {
      onClose();
    }
  };

  const isSelected = (path: string) => {
    return location.pathname.startsWith(path);
  };

  const drawerContent = (
    <Box sx={{ display: 'flex', flexDirection: 'column', height: '100%' }}>
      {/* Logo & User Info */}
      <Box sx={{ p: 3, bgcolor: 'primary.main', color: 'white' }}>
        <Typography variant="h5" fontWeight="bold" gutterBottom>
          Waqiti
        </Typography>
        <Box sx={{ display: 'flex', alignItems: 'center', mt: 2 }}>
          <Avatar sx={{ bgcolor: 'white', color: 'primary.main', mr: 2 }}>
            {user?.name?.[0] || 'U'}
          </Avatar>
          <Box>
            <Typography variant="body1" fontWeight="medium">
              {user?.name || 'User'}
            </Typography>
            <Typography variant="caption" sx={{ opacity: 0.9 }}>
              {user?.email || 'user@example.com'}
            </Typography>
          </Box>
        </Box>
      </Box>

      {/* Main Menu */}
      <Box sx={{ flex: 1, overflowY: 'auto' }}>
        <List>
          {menuItems.map((item) => (
            <ListItem key={item.path} disablePadding>
              <ListItemButton
                selected={isSelected(item.path)}
                onClick={() => handleNavigation(item.path)}
                sx={{
                  '&.Mui-selected': {
                    bgcolor: 'primary.light',
                    color: 'primary.main',
                    '&:hover': {
                      bgcolor: 'primary.light',
                    },
                  },
                }}
              >
                <ListItemIcon
                  sx={{
                    color: isSelected(item.path) ? 'primary.main' : 'inherit',
                  }}
                >
                  {item.icon}
                </ListItemIcon>
                <ListItemText primary={item.text} />
              </ListItemButton>
            </ListItem>
          ))}
        </List>

        <Divider sx={{ my: 1 }} />

        {/* Financial Services */}
        <Box sx={{ px: 2, py: 1 }}>
          <Typography variant="caption" color="text.secondary" fontWeight="bold">
            FINANCIAL SERVICES
          </Typography>
        </Box>
        <List>
          {financialItems.map((item) => (
            <ListItem key={item.path} disablePadding>
              <ListItemButton
                selected={isSelected(item.path)}
                onClick={() => handleNavigation(item.path)}
                sx={{
                  '&.Mui-selected': {
                    bgcolor: 'primary.light',
                    color: 'primary.main',
                  },
                }}
              >
                <ListItemIcon
                  sx={{
                    color: isSelected(item.path) ? 'primary.main' : 'inherit',
                  }}
                >
                  {item.icon}
                </ListItemIcon>
                <ListItemText primary={item.text} />
              </ListItemButton>
            </ListItem>
          ))}
        </List>

        <Divider sx={{ my: 1 }} />

        {/* Account */}
        <Box sx={{ px: 2, py: 1 }}>
          <Typography variant="caption" color="text.secondary" fontWeight="bold">
            ACCOUNT
          </Typography>
        </Box>
        <List>
          {accountItems.map((item) => (
            <ListItem key={item.path} disablePadding>
              <ListItemButton
                selected={isSelected(item.path)}
                onClick={() => handleNavigation(item.path)}
                sx={{
                  '&.Mui-selected': {
                    bgcolor: 'primary.light',
                    color: 'primary.main',
                  },
                }}
              >
                <ListItemIcon
                  sx={{
                    color: isSelected(item.path) ? 'primary.main' : 'inherit',
                  }}
                >
                  {item.icon}
                </ListItemIcon>
                <ListItemText primary={item.text} />
              </ListItemButton>
            </ListItem>
          ))}
        </List>
      </Box>

      {/* Version Info */}
      <Box sx={{ p: 2, borderTop: 1, borderColor: 'divider' }}>
        <Typography variant="caption" color="text.secondary">
          Version 1.0.0
        </Typography>
      </Box>
    </Box>
  );

  return (
    <>
      {/* Desktop Drawer */}
      {!isMobile && (
        <Drawer
          variant="permanent"
          sx={{
            width: DRAWER_WIDTH,
            flexShrink: 0,
            '& .MuiDrawer-paper': {
              width: DRAWER_WIDTH,
              boxSizing: 'border-box',
            },
          }}
        >
          {drawerContent}
        </Drawer>
      )}

      {/* Mobile Drawer */}
      {isMobile && (
        <Drawer
          variant="temporary"
          open={open}
          onClose={onClose}
          ModalProps={{
            keepMounted: true, // Better mobile performance
          }}
          sx={{
            '& .MuiDrawer-paper': {
              width: DRAWER_WIDTH,
              boxSizing: 'border-box',
            },
          }}
        >
          {drawerContent}
        </Drawer>
      )}
    </>
  );
};

export default Sidebar;
