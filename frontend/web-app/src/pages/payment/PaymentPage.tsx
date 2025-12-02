import React, { useState } from 'react';
import { Routes, Route, useNavigate, useLocation } from 'react-router-dom';
import {
  Box,
  Container,
  Paper,
  Tabs,
  Tab,
  Typography,
  Grid,
  Card,
  CardContent,
  Button,
  Avatar,
  Chip,
  IconButton,
  Badge,
} from '@mui/material';
import SendIcon from '@mui/icons-material/Send';
import RequestQuoteIcon from '@mui/icons-material/RequestQuote';
import QrCodeIcon from '@mui/icons-material/QrCode';
import HistoryIcon from '@mui/icons-material/History';
import ContactsOutlinedIcon from '@mui/icons-material/ContactsOutlined';
import AddIcon from '@mui/icons-material/Add';
import ArrowBackIcon from '@mui/icons-material/ArrowBack';;

import SendMoneyForm from '@/components/payment/SendMoneyForm';
import RequestMoneyForm from '@/components/payment/RequestMoneyForm';
import QRCodeScanner from '@/components/payment/QRCodeScanner';
import PaymentHistory from '@/components/payment/PaymentHistory';
import PaymentRequests from '@/components/payment/PaymentRequests';
import ContactsList from '@/components/payment/ContactsList';

interface TabPanelProps {
  children?: React.ReactNode;
  index: number;
  value: number;
}

const TabPanel: React.FC<TabPanelProps> = ({ children, value, index, ...other }) => {
  return (
    <div
      role="tabpanel"
      hidden={value !== index}
      id={`payment-tabpanel-${index}`}
      aria-labelledby={`payment-tab-${index}`}
      {...other}
    >
      {value === index && <Box sx={{ pt: 3 }}>{children}</Box>}
    </div>
  );
};

const PaymentPage: React.FC = () => {
  const navigate = useNavigate();
  const location = useLocation();
  const [activeTab, setActiveTab] = useState(0);

  // Get initial tab from URL path
  React.useEffect(() => {
    const path = location.pathname.split('/').pop();
    switch (path) {
      case 'send':
        setActiveTab(0);
        break;
      case 'request':
        setActiveTab(1);
        break;
      case 'scan':
        setActiveTab(2);
        break;
      case 'history':
        setActiveTab(3);
        break;
      case 'requests':
        setActiveTab(4);
        break;
      case 'contacts':
        setActiveTab(5);
        break;
      default:
        setActiveTab(0);
    }
  }, [location.pathname]);

  const handleTabChange = (event: React.SyntheticEvent, newValue: number) => {
    setActiveTab(newValue);
    
    // Update URL based on tab
    const paths = ['send', 'request', 'scan', 'history', 'requests', 'contacts'];
    navigate(`/payment/${paths[newValue]}`);
  };

  const handleBackToDashboard = () => {
    navigate('/dashboard');
  };

  return (
    <Container maxWidth="lg">
      <Box sx={{ py: 3 }}>
        {/* Header */}
        <Box sx={{ display: 'flex', alignItems: 'center', mb: 3 }}>
          <IconButton onClick={handleBackToDashboard} sx={{ mr: 2 }}>
            <ArrowBack />
          </IconButton>
          <Typography variant="h4" component="h1">
            Payments
          </Typography>
        </Box>

        {/* Quick Actions */}
        <Grid container spacing={2} sx={{ mb: 4 }}>
          <Grid item xs={6} sm={3}>
            <Card 
              sx={{ 
                cursor: 'pointer',
                '&:hover': { bgcolor: 'action.hover' }
              }}
              onClick={() => handleTabChange({} as React.SyntheticEvent, 0)}
            >
              <CardContent sx={{ textAlign: 'center', py: 2 }}>
                <Avatar sx={{ bgcolor: 'primary.main', mx: 'auto', mb: 1 }}>
                  <Send />
                </Avatar>
                <Typography variant="subtitle2">Send Money</Typography>
              </CardContent>
            </Card>
          </Grid>
          <Grid item xs={6} sm={3}>
            <Card 
              sx={{ 
                cursor: 'pointer',
                '&:hover': { bgcolor: 'action.hover' }
              }}
              onClick={() => handleTabChange({} as React.SyntheticEvent, 1)}
            >
              <CardContent sx={{ textAlign: 'center', py: 2 }}>
                <Avatar sx={{ bgcolor: 'secondary.main', mx: 'auto', mb: 1 }}>
                  <RequestQuote />
                </Avatar>
                <Typography variant="subtitle2">Request Money</Typography>
              </CardContent>
            </Card>
          </Grid>
          <Grid item xs={6} sm={3}>
            <Card 
              sx={{ 
                cursor: 'pointer',
                '&:hover': { bgcolor: 'action.hover' }
              }}
              onClick={() => handleTabChange({} as React.SyntheticEvent, 2)}
            >
              <CardContent sx={{ textAlign: 'center', py: 2 }}>
                <Avatar sx={{ bgcolor: 'info.main', mx: 'auto', mb: 1 }}>
                  <QrCode />
                </Avatar>
                <Typography variant="subtitle2">Scan QR</Typography>
              </CardContent>
            </Card>
          </Grid>
          <Grid item xs={6} sm={3}>
            <Card 
              sx={{ 
                cursor: 'pointer',
                '&:hover': { bgcolor: 'action.hover' }
              }}
              onClick={() => handleTabChange({} as React.SyntheticEvent, 4)}
            >
              <CardContent sx={{ textAlign: 'center', py: 2 }}>
                <Badge badgeContent={3} color="error">
                  <Avatar sx={{ bgcolor: 'warning.main', mx: 'auto', mb: 1 }}>
                    <RequestQuote />
                  </Avatar>
                </Badge>
                <Typography variant="subtitle2">Requests</Typography>
              </CardContent>
            </Card>
          </Grid>
        </Grid>

        {/* Main Content */}
        <Paper elevation={2}>
          <Box sx={{ borderBottom: 1, borderColor: 'divider' }}>
            <Tabs
              value={activeTab}
              onChange={handleTabChange}
              variant="scrollable"
              scrollButtons="auto"
              aria-label="payment tabs"
            >
              <Tab
                icon={<Send />}
                label="Send Money"
                id="payment-tab-0"
                aria-controls="payment-tabpanel-0"
              />
              <Tab
                icon={<RequestQuote />}
                label="Request Money"
                id="payment-tab-1"
                aria-controls="payment-tabpanel-1"
              />
              <Tab
                icon={<QrCode />}
                label="Scan QR"
                id="payment-tab-2"
                aria-controls="payment-tabpanel-2"
              />
              <Tab
                icon={<History />}
                label="History"
                id="payment-tab-3"
                aria-controls="payment-tabpanel-3"
              />
              <Tab
                icon={
                  <Badge badgeContent={3} color="error">
                    <RequestQuote />
                  </Badge>
                }
                label="Requests"
                id="payment-tab-4"
                aria-controls="payment-tabpanel-4"
              />
              <Tab
                icon={<ContactsOutlined />}
                label="Contacts"
                id="payment-tab-5"
                aria-controls="payment-tabpanel-5"
              />
            </Tabs>
          </Box>

          <TabPanel value={activeTab} index={0}>
            <SendMoneyForm />
          </TabPanel>

          <TabPanel value={activeTab} index={1}>
            <RequestMoneyForm />
          </TabPanel>

          <TabPanel value={activeTab} index={2}>
            <QRCodeScanner />
          </TabPanel>

          <TabPanel value={activeTab} index={3}>
            <PaymentHistory />
          </TabPanel>

          <TabPanel value={activeTab} index={4}>
            <PaymentRequests />
          </TabPanel>

          <TabPanel value={activeTab} index={5}>
            <ContactsList />
          </TabPanel>
        </Paper>

        {/* Recent Activity Summary */}
        <Box sx={{ mt: 4 }}>
          <Typography variant="h6" gutterBottom>
            Recent Activity
          </Typography>
          <Grid container spacing={2}>
            <Grid item xs={12} sm={6} md={4}>
              <Card>
                <CardContent>
                  <Typography variant="subtitle2" color="text.secondary">
                    Today's Sent
                  </Typography>
                  <Typography variant="h6" color="error.main">
                    $243.50
                  </Typography>
                  <Chip label="3 transactions" size="small" />
                </CardContent>
              </Card>
            </Grid>
            <Grid item xs={12} sm={6} md={4}>
              <Card>
                <CardContent>
                  <Typography variant="subtitle2" color="text.secondary">
                    Today's Received
                  </Typography>
                  <Typography variant="h6" color="success.main">
                    $156.00
                  </Typography>
                  <Chip label="2 transactions" size="small" />
                </CardContent>
              </Card>
            </Grid>
            <Grid item xs={12} sm={6} md={4}>
              <Card>
                <CardContent>
                  <Typography variant="subtitle2" color="text.secondary">
                    Pending Requests
                  </Typography>
                  <Typography variant="h6" color="warning.main">
                    $89.25
                  </Typography>
                  <Chip label="3 requests" size="small" />
                </CardContent>
              </Card>
            </Grid>
          </Grid>
        </Box>

        {/* Routes for sub-pages */}
        <Routes>
          <Route path="send" element={<SendMoneyForm />} />
          <Route path="request" element={<RequestMoneyForm />} />
          <Route path="scan" element={<QRCodeScanner />} />
          <Route path="history" element={<PaymentHistory />} />
          <Route path="requests" element={<PaymentRequests />} />
          <Route path="contacts" element={<ContactsList />} />
        </Routes>
      </Box>
    </Container>
  );
};

export default PaymentPage;