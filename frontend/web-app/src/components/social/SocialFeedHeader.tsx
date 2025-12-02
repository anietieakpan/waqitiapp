import React, { memo } from 'react';
import {
  Box,
  Typography,
  IconButton,
  Chip,
  Menu,
  MenuItem,
  Tabs,
  Tab,
  ToggleButton,
  ToggleButtonGroup,
} from '@mui/material';
import MoreIcon from '@mui/icons-material/MoreVert';
import TimerIcon from '@mui/icons-material/Timer';
import FilterIcon from '@mui/icons-material/FilterList';
import RefreshIcon from '@mui/icons-material/Refresh';;

interface SocialFeedHeaderProps {
  selectedTab: string;
  onTabChange: (tab: string) => void;
  onRefresh: () => void;
  isLoading: boolean;
  lastUpdated: Date;
}

const SocialFeedHeader = memo<SocialFeedHeaderProps>(({
  selectedTab,
  onTabChange,
  onRefresh,
  isLoading,
  lastUpdated
}) => {
  const [anchorEl, setAnchorEl] = React.useState<null | HTMLElement>(null);

  const handleMenuOpen = (event: React.MouseEvent<HTMLElement>) => {
    setAnchorEl(event.currentTarget);
  };

  const handleMenuClose = () => {
    setAnchorEl(null);
  };

  const tabs = [
    { value: 'all', label: 'All Activity' },
    { value: 'following', label: 'Following' },
    { value: 'trending', label: 'Trending' },
  ];

  return (
    <Box sx={{ borderBottom: 1, borderColor: 'divider', pb: 2, mb: 3 }}>
      {/* Header */}
      <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 2 }}>
        <Typography variant="h5" component="h1" fontWeight="bold">
          Social Feed
        </Typography>
        
        <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
          <Chip
            label={`Updated ${lastUpdated.toLocaleTimeString()}`}
            size="small"
            icon={<TimerIcon />}
            variant="outlined"
          />
          
          <IconButton
            onClick={onRefresh}
            disabled={isLoading}
            size="small"
          >
            <RefreshIcon />
          </IconButton>
          
          <IconButton
            onClick={handleMenuOpen}
            size="small"
          >
            <MoreIcon />
          </IconButton>
        </Box>
      </Box>

      {/* Tabs */}
      <Tabs
        value={selectedTab}
        onChange={(_, newValue) => onTabChange(newValue)}
        variant="fullWidth"
        sx={{ mb: 2 }}
      >
        {tabs.map((tab) => (
          <Tab
            key={tab.value}
            label={tab.label}
            value={tab.value}
          />
        ))}
      </Tabs>

      {/* Menu */}
      <Menu
        anchorEl={anchorEl}
        open={Boolean(anchorEl)}
        onClose={handleMenuClose}
      >
        <MenuItem onClick={handleMenuClose}>
          <FilterIcon sx={{ mr: 1 }} />
          Filters
        </MenuItem>
        <MenuItem onClick={handleMenuClose}>
          Settings
        </MenuItem>
        <MenuItem onClick={handleMenuClose}>
          Privacy
        </MenuItem>
      </Menu>
    </Box>
  );
});

SocialFeedHeader.displayName = 'SocialFeedHeader';

export default SocialFeedHeader;