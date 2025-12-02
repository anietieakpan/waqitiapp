import React, { useState } from 'react';
import { Routes, Route, useNavigate, useLocation } from 'react-router-dom';
import {
  Box,
  Container,
  Paper,
  List,
  ListItem,
  ListItemIcon,
  ListItemText,
  ListItemButton,
  Typography,
  Grid,
  Card,
  CardContent,
  Switch,
  Button,
  Avatar,
  Chip,
  Alert,
  IconButton,
  Divider,
} from '@mui/material';
import PersonIcon from '@mui/icons-material/Person';
import SecurityIcon from '@mui/icons-material/Security';
import NotificationsIcon from '@mui/icons-material/Notifications';
import PaymentIcon from '@mui/icons-material/Payment';
import PrivacyIcon from '@mui/icons-material/PrivacyTip';
import HelpIcon from '@mui/icons-material/Help';
import LogoutIcon from '@mui/icons-material/Logout';
import ArrowBackIcon from '@mui/icons-material/ArrowBack';
import EditIcon from '@mui/icons-material/Edit';
import VerifiedIcon from '@mui/icons-material/Verified';
import WarningIcon from '@mui/icons-material/Warning';
import ChevronRightIcon from '@mui/icons-material/ChevronRight';;

import ProfileSettings from '@/components/settings/ProfileSettings';
import SecuritySettings from '@/components/settings/SecuritySettings';
import NotificationSettings from '@/components/settings/NotificationSettings';
import PaymentSettings from '@/components/settings/PaymentSettings';
import PrivacySettings from '@/components/settings/PrivacySettings';
import HelpSupport from '@/components/settings/HelpSupport';

const settingsItems = [
  {
    id: 'profile',
    title: 'Profile',
    subtitle: 'Personal information and verification',
    icon: Person,
    path: '/settings/profile',
  },
  {
    id: 'security',
    title: 'Security',
    subtitle: 'Password, 2FA, and account security',
    icon: Security,
    path: '/settings/security',
  },
  {
    id: 'notifications',
    title: 'Notifications',
    subtitle: 'Email, SMS, and push notifications',
    icon: Notifications,
    path: '/settings/notifications',
  },
  {
    id: 'payment',
    title: 'Payment Methods',
    subtitle: 'Cards, bank accounts, and payment limits',
    icon: Payment,
    path: '/settings/payment',
  },
  {
    id: 'privacy',
    title: 'Privacy',
    subtitle: 'Data usage and privacy controls',
    icon: Privacy,
    path: '/settings/privacy',
  },
  {
    id: 'help',
    title: 'Help & Support',
    subtitle: 'FAQ, contact support, and feedback',
    icon: Help,
    path: '/settings/help',
  },
];

const SettingsPage: React.FC = () => {
  const navigate = useNavigate();
  const location = useLocation();
  const [selectedItem, setSelectedItem] = useState('profile');

  // Mock user data
  const user = {
    name: 'John Doe',
    email: 'john.doe@example.com',
    avatar: null,
    verified: true,
    kycStatus: 'approved',
    securityScore: 85,
  };

  React.useEffect(() => {
    const currentPath = location.pathname;
    const activeItem = settingsItems.find(item => currentPath.includes(item.id));
    if (activeItem) {
      setSelectedItem(activeItem.id);
    }
  }, [location.pathname]);

  const handleItemClick = (item: typeof settingsItems[0]) => {
    setSelectedItem(item.id);
    navigate(item.path);
  };

  const handleBackToDashboard = () => {
    navigate('/dashboard');
  };

  const handleLogout = () => {
    // Implement logout logic
    navigate('/login');
  };

  const renderSettingsOverview = () => (
    <Box>
      {/* User Profile Card */}
      <Card sx={{ mb: 3 }}>
        <CardContent>
          <Box display="flex" alignItems="center" justifyContent="space-between">
            <Box display="flex" alignItems="center">
              <Avatar sx={{ width: 64, height: 64, mr: 2 }}>
                {user.name.charAt(0)}
              </Avatar>
              <Box>
                <Box display="flex" alignItems="center" gap={1}>
                  <Typography variant="h6">{user.name}</Typography>
                  {user.verified && (
                    <Verified color="primary" fontSize="small" />
                  )}
                </Box>
                <Typography variant="body2" color="text.secondary">
                  {user.email}
                </Typography>
                <Box display="flex" gap={1} mt={1}>
                  <Chip 
                    label={`KYC: ${user.kycStatus.toUpperCase()}`} 
                    color="success" 
                    size="small" 
                  />
                  <Chip 
                    label={`Security: ${user.securityScore}%`} 
                    color={user.securityScore >= 80 ? 'success' : 'warning'} 
                    size="small" 
                  />
                </Box>
              </Box>
            </Box>
            <IconButton onClick={() => handleItemClick(settingsItems[0])}>
              <Edit />
            </IconButton>
          </Box>
        </CardContent>
      </Card>

      {/* Quick Actions */}
      <Card sx={{ mb: 3 }}>
        <CardContent>
          <Typography variant="h6" gutterBottom>
            Quick Actions
          </Typography>
          <Grid container spacing={2}>
            <Grid item xs={6} sm={3}>
              <Button
                fullWidth
                variant="outlined"
                startIcon={<Security />}
                onClick={() => handleItemClick(settingsItems[1])}
              >
                Security
              </Button>
            </Grid>
            <Grid item xs={6} sm={3}>
              <Button
                fullWidth
                variant="outlined"
                startIcon={<Payment />}
                onClick={() => handleItemClick(settingsItems[3])}
              >
                Payment
              </Button>
            </Grid>
            <Grid item xs={6} sm={3}>
              <Button
                fullWidth
                variant="outlined"
                startIcon={<Notifications />}
                onClick={() => handleItemClick(settingsItems[2])}
              >
                Notifications
              </Button>
            </Grid>
            <Grid item xs={6} sm={3}>
              <Button
                fullWidth
                variant="outlined"
                startIcon={<Help />}
                onClick={() => handleItemClick(settingsItems[5])}
              >
                Help
              </Button>
            </Grid>
          </Grid>
        </CardContent>
      </Card>

      {/* Security Alerts */}
      <Card sx={{ mb: 3 }}>
        <CardContent>
          <Typography variant="h6" gutterBottom>
            Security Status
          </Typography>
          
          {user.securityScore < 80 && (
            <Alert severity="warning" sx={{ mb: 2 }}>
              <Typography variant="body2">
                Your account security score is below 80%. Consider enabling two-factor authentication.
              </Typography>
            </Alert>
          )}
          
          <Alert severity="info">
            <Typography variant="body2">
              Last login: Today at 2:30 PM from New York, US
            </Typography>
          </Alert>
        </CardContent>
      </Card>

      {/* Settings Menu */}
      <Paper>
        <List>
          {settingsItems.map((item, index) => {
            const IconComponent = item.icon;
            return (
              <React.Fragment key={item.id}>
                <ListItemButton
                  onClick={() => handleItemClick(item)}
                  selected={selectedItem === item.id}
                >
                  <ListItemIcon>
                    <IconComponent />
                  </ListItemIcon>
                  <ListItemText
                    primary={item.title}
                    secondary={item.subtitle}
                  />
                  <ChevronRight />
                </ListItemButton>
                {index < settingsItems.length - 1 && <Divider />}
              </React.Fragment>
            );
          })}
          
          <Divider />
          
          <ListItemButton onClick={handleLogout}>
            <ListItemIcon>
              <Logout color="error" />
            </ListItemIcon>
            <ListItemText 
              primary="Logout" 
              secondary="Sign out of your account"
              primaryTypographyProps={{ color: 'error' }}
            />
          </ListItemButton>
        </List>
      </Paper>
    </Box>
  );

  return (
    <Container maxWidth="lg">
      <Box sx={{ py: 3 }}>
        {/* Header */}
        <Box sx={{ display: 'flex', alignItems: 'center', mb: 3 }}>
          <IconButton onClick={handleBackToDashboard} sx={{ mr: 2 }}>
            <ArrowBack />
          </IconButton>
          <Typography variant="h4" component="h1">
            Settings
          </Typography>
        </Box>

        <Grid container spacing={3}>
          {/* Settings Navigation */}
          <Grid item xs={12} md={4}>
            <Paper sx={{ p: 2 }}>
              <List>
                {settingsItems.map((item) => {
                  const IconComponent = item.icon;
                  return (
                    <ListItemButton
                      key={item.id}
                      onClick={() => handleItemClick(item)}
                      selected={selectedItem === item.id}
                      sx={{ borderRadius: 1, mb: 0.5 }}
                    >
                      <ListItemIcon>
                        <IconComponent />
                      </ListItemIcon>
                      <ListItemText
                        primary={item.title}
                        primaryTypographyProps={{ fontWeight: 'medium' }}
                      />
                    </ListItemButton>
                  );
                })}
                
                <Divider sx={{ my: 1 }} />
                
                <ListItemButton onClick={handleLogout} sx={{ borderRadius: 1 }}>
                  <ListItemIcon>
                    <Logout color="error" />
                  </ListItemIcon>
                  <ListItemText 
                    primary="Logout"
                    primaryTypographyProps={{ color: 'error', fontWeight: 'medium' }}
                  />
                </ListItemButton>
              </List>
            </Paper>
          </Grid>

          {/* Settings Content */}
          <Grid item xs={12} md={8}>
            <Paper sx={{ p: 3, minHeight: 600 }}>
              <Routes>
                <Route path="/" element={renderSettingsOverview()} />
                <Route path="/profile" element={<ProfileSettings />} />
                <Route path="/security" element={<SecuritySettings />} />
                <Route path="/notifications" element={<NotificationSettings />} />
                <Route path="/payment" element={<PaymentSettings />} />
                <Route path="/privacy" element={<PrivacySettings />} />
                <Route path="/help" element={<HelpSupport />} />
              </Routes>
              
              {/* Show overview if no specific route */}
              {location.pathname === '/settings' && renderSettingsOverview()}
            </Paper>
          </Grid>
        </Grid>
      </Box>
    </Container>
  );
};

export default SettingsPage;