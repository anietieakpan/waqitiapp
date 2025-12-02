import React, { useState } from 'react';
import { Box, Toolbar, useTheme, useMediaQuery } from '@mui/material';
import { Outlet } from 'react-router-dom';
import Sidebar from './Sidebar';
import Header from './Header';
import MobileNav from './MobileNav';

const DRAWER_WIDTH = 280;

const AppLayout: React.FC = () => {
  const theme = useTheme();
  const isMobile = useMediaQuery(theme.breakpoints.down('md'));
  const [mobileOpen, setMobileOpen] = useState(false);

  const handleDrawerToggle = () => {
    setMobileOpen(!mobileOpen);
  };

  return (
    <Box sx={{ display: 'flex', minHeight: '100vh' }}>
      {/* Sidebar */}
      <Sidebar open={mobileOpen} onClose={handleDrawerToggle} />

      {/* Main Content */}
      <Box
        component="main"
        sx={{
          flexGrow: 1,
          width: isMobile ? '100%' : `calc(100% - ${DRAWER_WIDTH}px)`,
          bgcolor: 'background.default',
          minHeight: '100vh',
        }}
      >
        {/* Header */}
        <Header onMenuClick={handleDrawerToggle} />

        {/* Toolbar Spacer */}
        <Toolbar />

        {/* Page Content */}
        <Box
          sx={{
            p: isMobile ? 2 : 3,
            pb: isMobile ? 10 : 3, // Extra padding on mobile for bottom nav
          }}
        >
          <Outlet />
        </Box>
      </Box>

      {/* Mobile Bottom Navigation */}
      {isMobile && <MobileNav />}
    </Box>
  );
};

export default AppLayout;
