import React, { useState, useEffect } from 'react';
import {
  Box,
  AppBar,
  Toolbar,
  Typography,
  IconButton,
  Drawer,
  List,
  ListItem,
  ListItemIcon,
  ListItemText,
  ListItemButton,
  BottomNavigation,
  BottomNavigationAction,
  Fab,
  Collapse,
  Badge,
  Avatar,
  useTheme,
  useMediaQuery,
  SwipeableDrawer,
  Snackbar,
  Alert,
} from '@mui/material';
import MenuIcon from '@mui/icons-material/Menu';
import HomeIcon from '@mui/icons-material/Home';
import AccountBalanceIcon from '@mui/icons-material/AccountBalance';
import TrendingUpIcon from '@mui/icons-material/TrendingUp';
import SendIcon from '@mui/icons-material/Send';
import PersonIcon from '@mui/icons-material/Person';
import NotificationsIcon from '@mui/icons-material/Notifications';
import AddIcon from '@mui/icons-material/Add';
import ExpandLessIcon from '@mui/icons-material/ExpandLess';
import ExpandMoreIcon from '@mui/icons-material/ExpandMore';
import CreditCardIcon from '@mui/icons-material/CreditCard';
import SavingsIcon from '@mui/icons-material/Savings';
import AnalyticsIcon from '@mui/icons-material/Analytics';
import SettingsIcon from '@mui/icons-material/Settings';
import HelpIcon from '@mui/icons-material/Help';
import LogoutIcon from '@mui/icons-material/Logout';
import QrCodeIcon from '@mui/icons-material/QrCode';
import NearMeIcon from '@mui/icons-material/NearMe';;
import { useSwipeable } from 'react-swipeable';
import { useNavigate, useLocation } from 'react-router-dom';

interface MobileOptimizedLayoutProps {
  children: React.ReactNode;
}

interface NavigationItem {
  id: string;
  label: string;
  icon: React.ReactNode;
  path: string;
  badge?: number;
  children?: NavigationItem[];
}

const navigationItems: NavigationItem[] = [
  {
    id: 'home',
    label: 'Home',
    icon: <Home />,
    path: '/dashboard',
  },
  {
    id: 'accounts',
    label: 'Accounts',
    icon: <AccountBalance />,
    path: '/accounts',
    children: [
      { id: 'checking', label: 'Checking', icon: <CreditCard />, path: '/accounts/checking' },
      { id: 'savings', label: 'Savings', icon: <Savings />, path: '/accounts/savings' },
      { id: 'investments', label: 'Investments', icon: <TrendingUp />, path: '/accounts/investments' },
    ],
  },
  {
    id: 'payments',
    label: 'Payments',
    icon: <Send />,
    path: '/payments',
    children: [
      { id: 'send', label: 'Send Money', icon: <Send />, path: '/payments/send' },
      { id: 'request', label: 'Request Money', icon: <NearMe />, path: '/payments/request' },
      { id: 'qr', label: 'QR Payments', icon: <QrCode />, path: '/payments/qr' },
    ],
  },
  {
    id: 'analytics',
    label: 'Analytics',
    icon: <Analytics />,
    path: '/analytics',
  },
  {
    id: 'profile',
    label: 'Profile',
    icon: <Person />,
    path: '/profile',
    children: [
      { id: 'settings', label: 'Settings', icon: <Settings />, path: '/profile/settings' },
      { id: 'help', label: 'Help', icon: <Help />, path: '/profile/help' },
    ],
  },
];

const bottomNavItems = [
  { id: 'home', label: 'Home', icon: <Home />, path: '/dashboard' },
  { id: 'accounts', label: 'Accounts', icon: <AccountBalance />, path: '/accounts' },
  { id: 'send', label: 'Send', icon: <Send />, path: '/payments/send' },
  { id: 'analytics', label: 'Analytics', icon: <Analytics />, path: '/analytics' },
  { id: 'profile', label: 'Profile', icon: <Person />, path: '/profile' },
];

export const MobileOptimizedLayout: React.FC<MobileOptimizedLayoutProps> = ({ children }) => {
  const theme = useTheme();
  const isMobile = useMediaQuery(theme.breakpoints.down('md'));
  const isSmallMobile = useMediaQuery(theme.breakpoints.down('sm'));
  const navigate = useNavigate();
  const location = useLocation();

  const [drawerOpen, setDrawerOpen] = useState(false);
  const [expandedItems, setExpandedItems] = useState<string[]>([]);
  const [bottomNavValue, setBottomNavValue] = useState(0);
  const [notifications] = useState(3);
  const [showQuickAction, setShowQuickAction] = useState(false);
  const [snackbarOpen, setSnackbarOpen] = useState(false);
  const [swipeDirection, setSwipeDirection] = useState<'left' | 'right' | null>(null);

  useEffect(() => {
    // Set bottom navigation active item based on current path
    const currentItem = bottomNavItems.findIndex(item => 
      location.pathname.startsWith(item.path)
    );
    if (currentItem !== -1) {
      setBottomNavValue(currentItem);
    }
  }, [location.pathname]);

  useEffect(() => {
    // Show/hide quick action based on scroll
    let lastScrollY = window.scrollY;
    const handleScroll = () => {
      const currentScrollY = window.scrollY;
      setShowQuickAction(currentScrollY < lastScrollY || currentScrollY < 100);
      lastScrollY = currentScrollY;
    };

    window.addEventListener('scroll', handleScroll, { passive: true });
    return () => window.removeEventListener('scroll', handleScroll);
  }, []);

  // Swipe gestures for navigation
  const swipeHandlers = useSwipeable({
    onSwipedLeft: () => {
      if (isMobile && !drawerOpen) {
        setSwipeDirection('left');
        setDrawerOpen(true);
      }
    },
    onSwipedRight: () => {
      if (drawerOpen) {
        setSwipeDirection('right');
        setDrawerOpen(false);
      }
    },
    trackMouse: true,
  });

  const handleDrawerToggle = () => {
    setDrawerOpen(!drawerOpen);
  };

  const handleExpandClick = (itemId: string) => {
    setExpandedItems(prev =>
      prev.includes(itemId)
        ? prev.filter(id => id !== itemId)
        : [...prev, itemId]
    );
  };

  const handleNavigation = (path: string) => {
    navigate(path);
    if (isMobile) {
      setDrawerOpen(false);
    }
  };

  const handleBottomNavigation = (event: React.SyntheticEvent, newValue: number) => {
    setBottomNavValue(newValue);
    navigate(bottomNavItems[newValue].path);
  };

  const renderNavigationItem = (item: NavigationItem, level: number = 0) => {
    const hasChildren = item.children && item.children.length > 0;
    const isExpanded = expandedItems.includes(item.id);
    const isActive = location.pathname === item.path || 
                    location.pathname.startsWith(item.path + '/');

    return (
      <React.Fragment key={item.id}>
        <ListItem disablePadding>
          <ListItemButton
            onClick={() => {
              if (hasChildren) {
                handleExpandClick(item.id);
              } else {
                handleNavigation(item.path);
              }
            }}
            sx={{
              pl: 2 + level * 2,
              backgroundColor: isActive ? 'action.selected' : 'transparent',
              '&:hover': {
                backgroundColor: 'action.hover',
              },
            }}
          >
            <ListItemIcon
              sx={{
                color: isActive ? 'primary.main' : 'text.primary',
                minWidth: 40,
              }}
            >
              <Badge badgeContent={item.badge} color="error">
                {item.icon}
              </Badge>
            </ListItemIcon>
            <ListItemText
              primary={item.label}
              sx={{
                '& .MuiListItemText-primary': {
                  color: isActive ? 'primary.main' : 'text.primary',
                  fontWeight: isActive ? 600 : 400,
                },
              }}
            />
            {hasChildren && (
              <IconButton size="small">
                {isExpanded ? <ExpandLess /> : <ExpandMore />}
              </IconButton>
            )}
          </ListItemButton>
        </ListItem>
        
        {hasChildren && (
          <Collapse in={isExpanded} timeout="auto" unmountOnExit>
            <List component="div" disablePadding>
              {item.children!.map(child => renderNavigationItem(child, level + 1))}
            </List>
          </Collapse>
        )}
      </React.Fragment>
    );
  };

  const drawerContent = (
    <Box sx={{ width: 280, height: '100%', display: 'flex', flexDirection: 'column' }}>
      {/* Drawer Header */}
      <Box
        sx={{
          p: 2,
          background: 'linear-gradient(135deg, #667eea 0%, #764ba2 100%)',
          color: 'white',
          display: 'flex',
          alignItems: 'center',
          gap: 2,
        }}
      >
        <Avatar
          sx={{
            width: 48,
            height: 48,
            backgroundColor: 'rgba(255,255,255,0.2)',
          }}
        >
          JD
        </Avatar>
        <Box>
          <Typography variant="h6" component="div">
            John Doe
          </Typography>
          <Typography variant="body2" sx={{ opacity: 0.8 }}>
            john.doe@example.com
          </Typography>
        </Box>
      </Box>

      {/* Navigation Items */}
      <Box sx={{ flex: 1, overflow: 'auto' }}>
        <List>
          {navigationItems.map(item => renderNavigationItem(item))}
        </List>
      </Box>

      {/* Drawer Footer */}
      <Box sx={{ p: 2, borderTop: 1, borderColor: 'divider' }}>
        <ListItemButton onClick={() => handleNavigation('/logout')}>
          <ListItemIcon>
            <Logout />
          </ListItemIcon>
          <ListItemText primary="Logout" />
        </ListItemButton>
      </Box>
    </Box>
  );

  return (
    <Box sx={{ display: 'flex', flexDirection: 'column', minHeight: '100vh' }} {...swipeHandlers}>
      {/* Top App Bar */}
      <AppBar
        position="fixed"
        sx={{
          zIndex: theme.zIndex.drawer + 1,
          background: isMobile
            ? 'linear-gradient(135deg, #667eea 0%, #764ba2 100%)'
            : 'background.paper',
          color: isMobile ? 'white' : 'text.primary',
          boxShadow: isMobile ? 'none' : 1,
        }}
      >
        <Toolbar>
          {isMobile && (
            <IconButton
              color="inherit"
              aria-label="open drawer"
              edge="start"
              onClick={handleDrawerToggle}
              sx={{ mr: 2 }}
            >
              <MenuIcon />
            </IconButton>
          )}

          <Typography variant="h6" noWrap component="div" sx={{ flexGrow: 1 }}>
            Waqiti
          </Typography>

          <IconButton color="inherit" onClick={() => navigate('/notifications')}>
            <Badge badgeContent={notifications} color="error">
              <Notifications />
            </Badge>
          </IconButton>

          {!isMobile && (
            <IconButton color="inherit" onClick={() => navigate('/profile')}>
              <Avatar sx={{ width: 32, height: 32 }}>JD</Avatar>
            </IconButton>
          )}
        </Toolbar>
      </AppBar>

      {/* Navigation Drawer */}
      {isMobile ? (
        <SwipeableDrawer
          anchor="left"
          open={drawerOpen}
          onClose={() => setDrawerOpen(false)}
          onOpen={() => setDrawerOpen(true)}
          swipeAreaWidth={20}
          disableSwipeToOpen={false}
          ModalProps={{
            keepMounted: true, // Better open performance on mobile
          }}
        >
          {drawerContent}
        </SwipeableDrawer>
      ) : (
        <Drawer
          variant="permanent"
          sx={{
            width: 280,
            flexShrink: 0,
            '& .MuiDrawer-paper': {
              width: 280,
              boxSizing: 'border-box',
            },
          }}
        >
          {drawerContent}
        </Drawer>
      )}

      {/* Main Content */}
      <Box
        component="main"
        sx={{
          flexGrow: 1,
          bgcolor: 'background.default',
          pt: { xs: 8, sm: 9 }, // Account for app bar height
          pb: isMobile ? 8 : 2, // Account for bottom navigation on mobile
          pl: isMobile ? 1 : 37, // Account for drawer width on desktop
          pr: 1,
          minHeight: '100vh',
        }}
      >
        <Box
          sx={{
            maxWidth: '100%',
            mx: 'auto',
            px: { xs: 1, sm: 2, md: 3 },
          }}
        >
          {children}
        </Box>
      </Box>

      {/* Bottom Navigation (Mobile Only) */}
      {isMobile && (
        <BottomNavigation
          value={bottomNavValue}
          onChange={handleBottomNavigation}
          sx={{
            position: 'fixed',
            bottom: 0,
            left: 0,
            right: 0,
            zIndex: theme.zIndex.appBar,
            borderTop: 1,
            borderColor: 'divider',
            height: 64,
          }}
        >
          {bottomNavItems.map((item, index) => (
            <BottomNavigationAction
              key={item.id}
              label={item.label}
              icon={item.icon}
              sx={{
                '&.Mui-selected': {
                  color: 'primary.main',
                },
                minWidth: 'auto',
                fontSize: isSmallMobile ? '0.75rem' : '0.875rem',
              }}
            />
          ))}
        </BottomNavigation>
      )}

      {/* Floating Action Button (Mobile Only) */}
      {isMobile && (
        <Fab
          color="primary"
          aria-label="quick action"
          sx={{
            position: 'fixed',
            bottom: 80,
            right: 16,
            transform: showQuickAction ? 'scale(1)' : 'scale(0)',
            transition: 'transform 0.3s ease-in-out',
            background: 'linear-gradient(135deg, #667eea 0%, #764ba2 100%)',
          }}
          onClick={() => navigate('/payments/send')}
        >
          <Add />
        </Fab>
      )}

      {/* Swipe Feedback Snackbar */}
      <Snackbar
        open={snackbarOpen}
        autoHideDuration={2000}
        onClose={() => setSnackbarOpen(false)}
        anchorOrigin={{ vertical: 'top', horizontal: 'center' }}
      >
        <Alert severity="info" onClose={() => setSnackbarOpen(false)}>
          {swipeDirection === 'left' ? 'Swipe right to close menu' : 'Swipe left to open menu'}
        </Alert>
      </Snackbar>
    </Box>
  );
};