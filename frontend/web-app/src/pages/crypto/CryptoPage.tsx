import React, { useEffect, useState } from 'react';
import {
  Container,
  Grid,
  Card,
  CardContent,
  Typography,
  Box,
  Button,
  Tabs,
  Tab,
  CircularProgress,
  Alert,
} from '@mui/material';
import AddIcon from '@mui/icons-material/Add';
import SendIcon from '@mui/icons-material/Send';
import SwapHorizIcon from '@mui/icons-material/SwapHoriz';
import ShoppingCartIcon from '@mui/icons-material/ShoppingCart';
import { useAppDispatch, useAppSelector } from '@/hooks/redux';
import { fetchWallets, fetchPrices, fetchTransactions } from '@/store/slices/cryptoSlice';
import CryptoWalletsList from '@/components/crypto/CryptoWalletsList';
import CryptoPrices from '@/components/crypto/CryptoPrices';
import CryptoTransactions from '@/components/crypto/CryptoTransactions';
import BuyCryptoDialog from '@/components/crypto/BuyCryptoDialog';
import SendCryptoDialog from '@/components/crypto/SendCryptoDialog';
import SwapCryptoDialog from '@/components/crypto/SwapCryptoDialog';

interface TabPanelProps {
  children?: React.ReactNode;
  index: number;
  value: number;
}

const TabPanel: React.FC<TabPanelProps> = ({ children, value, index }) => {
  return (
    <div role="tabpanel" hidden={value !== index}>
      {value === index && <Box sx={{ pt: 3 }}>{children}</Box>}
    </div>
  );
};

const CryptoPage: React.FC = () => {
  const dispatch = useAppDispatch();
  const { wallets, prices, transactions, loading, error } = useAppSelector((state) => state.crypto);

  const [tabValue, setTabValue] = useState(0);
  const [buyDialogOpen, setBuyDialogOpen] = useState(false);
  const [sendDialogOpen, setSendDialogOpen] = useState(false);
  const [swapDialogOpen, setSwapDialogOpen] = useState(false);

  useEffect(() => {
    dispatch(fetchWallets());
    dispatch(fetchPrices());
    dispatch(fetchTransactions());
  }, [dispatch]);

  const handleTabChange = (event: React.SyntheticEvent, newValue: number) => {
    setTabValue(newValue);
  };

  const totalBalanceUSD = wallets.reduce((sum, wallet) => sum + wallet.balanceUSD, 0);

  return (
    <Container maxWidth="lg" sx={{ mt: 4, mb: 4 }}>
      {/* Header */}
      <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 4 }}>
        <Box>
          <Typography variant="h4" component="h1" gutterBottom>
            Crypto Wallet
          </Typography>
          <Typography variant="body2" color="text.secondary">
            Manage your cryptocurrency portfolio
          </Typography>
        </Box>
        <Box sx={{ display: 'flex', gap: 1 }}>
          <Button
            variant="contained"
            startIcon={<ShoppingCartIcon />}
            onClick={() => setBuyDialogOpen(true)}
          >
            Buy
          </Button>
          <Button
            variant="outlined"
            startIcon={<SendIcon />}
            onClick={() => setSendDialogOpen(true)}
          >
            Send
          </Button>
          <Button
            variant="outlined"
            startIcon={<SwapHorizIcon />}
            onClick={() => setSwapDialogOpen(true)}
          >
            Swap
          </Button>
        </Box>
      </Box>

      {/* Error Alert */}
      {error && (
        <Alert severity="error" sx={{ mb: 3 }}>
          {error}
        </Alert>
      )}

      {/* Total Balance Card */}
      <Card sx={{ mb: 3, background: 'linear-gradient(135deg, #667eea 0%, #764ba2 100%)', color: 'white' }}>
        <CardContent>
          <Typography variant="body2" sx={{ opacity: 0.9 }}>
            Total Portfolio Value
          </Typography>
          <Typography variant="h3" sx={{ my: 2 }}>
            ${totalBalanceUSD.toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}
          </Typography>
          <Typography variant="body2" sx={{ opacity: 0.8 }}>
            {wallets.length} {wallets.length === 1 ? 'Asset' : 'Assets'}
          </Typography>
        </CardContent>
      </Card>

      {/* Tabs */}
      <Box sx={{ borderBottom: 1, borderColor: 'divider', mb: 3 }}>
        <Tabs value={tabValue} onChange={handleTabChange}>
          <Tab label="Wallets" />
          <Tab label="Prices" />
          <Tab label="Transactions" />
        </Tabs>
      </Box>

      {/* Tab Panels */}
      {loading && wallets.length === 0 ? (
        <Box sx={{ display: 'flex', justifyContent: 'center', py: 8 }}>
          <CircularProgress />
        </Box>
      ) : (
        <>
          <TabPanel value={tabValue} index={0}>
            <CryptoWalletsList wallets={wallets} prices={prices} />
          </TabPanel>

          <TabPanel value={tabValue} index={1}>
            <CryptoPrices prices={prices} />
          </TabPanel>

          <TabPanel value={tabValue} index={2}>
            <CryptoTransactions transactions={transactions} />
          </TabPanel>
        </>
      )}

      {/* Dialogs */}
      <BuyCryptoDialog
        open={buyDialogOpen}
        onClose={() => setBuyDialogOpen(false)}
        prices={prices}
      />
      <SendCryptoDialog
        open={sendDialogOpen}
        onClose={() => setSendDialogOpen(false)}
        wallets={wallets}
      />
      <SwapCryptoDialog
        open={swapDialogOpen}
        onClose={() => setSwapDialogOpen(false)}
        wallets={wallets}
        prices={prices}
      />
    </Container>
  );
};

export default CryptoPage;
