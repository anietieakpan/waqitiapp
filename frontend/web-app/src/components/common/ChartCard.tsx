import React from 'react';
import {
  Card,
  CardContent,
  CardHeader,
  Box,
  IconButton,
  Menu,
  MenuItem,
  Skeleton,
} from '@mui/material';
import MoreVertIcon from '@mui/icons-material/MoreVert';

export interface ChartCardProps {
  title: string;
  subtitle?: string;
  children: React.ReactNode;
  loading?: boolean;
  actions?: Array<{ label: string; onClick: () => void }>;
  height?: number | string;
}

const ChartCard: React.FC<ChartCardProps> = ({
  title,
  subtitle,
  children,
  loading = false,
  actions,
  height = 400,
}) => {
  const [anchorEl, setAnchorEl] = React.useState<null | HTMLElement>(null);

  const handleMenuOpen = (event: React.MouseEvent<HTMLElement>) => {
    setAnchorEl(event.currentTarget);
  };

  const handleMenuClose = () => {
    setAnchorEl(null);
  };

  const handleActionClick = (action: () => void) => {
    action();
    handleMenuClose();
  };

  return (
    <Card>
      <CardHeader
        title={title}
        subheader={subtitle}
        action={
          actions && actions.length > 0 ? (
            <>
              <IconButton onClick={handleMenuOpen}>
                <MoreVertIcon />
              </IconButton>
              <Menu
                anchorEl={anchorEl}
                open={Boolean(anchorEl)}
                onClose={handleMenuClose}
              >
                {actions.map((action, index) => (
                  <MenuItem
                    key={index}
                    onClick={() => handleActionClick(action.onClick)}
                  >
                    {action.label}
                  </MenuItem>
                ))}
              </Menu>
            </>
          ) : null
        }
      />
      <CardContent>
        <Box height={height}>
          {loading ? (
            <Skeleton variant="rectangular" width="100%" height="100%" />
          ) : (
            children
          )}
        </Box>
      </CardContent>
    </Card>
  );
};

export default ChartCard;
