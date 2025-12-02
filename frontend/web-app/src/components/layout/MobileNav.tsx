import React from 'react';
import { BottomNavigation, BottomNavigationAction, Paper } from '@mui/material';
import { useNavigate, useLocation } from 'react-router-dom';
import DashboardIcon from '@mui/icons-material/Dashboard';
import AccountBalanceWalletIcon from '@mui/icons-material/AccountBalanceWallet';
import SendIcon from '@mui/icons-material/Send';
import CreditCardIcon from '@mui/icons-material/CreditCard';
import MoreHorizIcon from '@mui/icons-material/MoreHoriz';

const MobileNav: React.FC = () => {
  const navigate = useNavigate();
  const location = useLocation();

  const getActiveTab = () => {
    if (location.pathname.startsWith('/dashboard')) return 0;
    if (location.pathname.startsWith('/wallet')) return 1;
    if (location.pathname.startsWith('/payment')) return 2;
    if (location.pathname.startsWith('/cards')) return 3;
    return 4;
  };

  const [value, setValue] = React.useState(getActiveTab());

  React.useEffect(() => {
    setValue(getActiveTab());
  }, [location.pathname]);

  const handleChange = (event: React.SyntheticEvent, newValue: number) => {
    setValue(newValue);
    switch (newValue) {
      case 0:
        navigate('/dashboard');
        break;
      case 1:
        navigate('/wallet');
        break;
      case 2:
        navigate('/payment');
        break;
      case 3:
        navigate('/cards');
        break;
      case 4:
        navigate('/settings');
        break;
    }
  };

  return (
    <Paper
      sx={{
        position: 'fixed',
        bottom: 0,
        left: 0,
        right: 0,
        zIndex: 1000,
      }}
      elevation={3}
    >
      <BottomNavigation value={value} onChange={handleChange} showLabels>
        <BottomNavigationAction label="Home" icon={<DashboardIcon />} />
        <BottomNavigationAction label="Wallet" icon={<AccountBalanceWalletIcon />} />
        <BottomNavigationAction label="Pay" icon={<SendIcon />} />
        <BottomNavigationAction label="Cards" icon={<CreditCardIcon />} />
        <BottomNavigationAction label="More" icon={<MoreHorizIcon />} />
      </BottomNavigation>
    </Paper>
  );
};

export default MobileNav;
