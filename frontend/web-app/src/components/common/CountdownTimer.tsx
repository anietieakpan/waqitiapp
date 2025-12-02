import React, { useState, useEffect } from 'react';
import {
  Box,
  Typography,
  LinearProgress,
  Chip,
  useTheme,
  alpha,
} from '@mui/material';
import TimerIcon from '@mui/icons-material/Timer';
import WarningIcon from '@mui/icons-material/Warning';;
import { differenceInSeconds } from 'date-fns';

interface CountdownTimerProps {
  endTime: Date;
  onExpire?: () => void;
  showProgress?: boolean;
  warningThreshold?: number; // seconds
  compact?: boolean;
}

const CountdownTimer: React.FC<CountdownTimerProps> = ({
  endTime,
  onExpire,
  showProgress = true,
  warningThreshold = 60,
  compact = false,
}) => {
  const theme = useTheme();
  const [timeLeft, setTimeLeft] = useState(0);
  const [expired, setExpired] = useState(false);

  useEffect(() => {
    const calculateTimeLeft = () => {
      const seconds = differenceInSeconds(endTime, new Date());
      if (seconds <= 0) {
        setTimeLeft(0);
        if (!expired) {
          setExpired(true);
          onExpire?.();
        }
      } else {
        setTimeLeft(seconds);
      }
    };

    calculateTimeLeft();
    const interval = setInterval(calculateTimeLeft, 1000);

    return () => clearInterval(interval);
  }, [endTime, expired, onExpire]);

  const formatTime = (seconds: number): string => {
    const minutes = Math.floor(seconds / 60);
    const remainingSeconds = seconds % 60;
    return `${minutes}:${remainingSeconds.toString().padStart(2, '0')}`;
  };

  const getProgress = (): number => {
    const totalSeconds = differenceInSeconds(endTime, new Date());
    const maxSeconds = 300; // 5 minutes default
    return Math.max(0, Math.min(100, (timeLeft / maxSeconds) * 100));
  };

  const isWarning = timeLeft <= warningThreshold && timeLeft > 0;
  const color = expired ? 'error' : isWarning ? 'warning' : 'primary';

  if (compact) {
    return (
      <Chip
        icon={<TimerIcon />}
        label={expired ? 'Expired' : formatTime(timeLeft)}
        color={color}
        size="small"
      />
    );
  }

  return (
    <Box sx={{ textAlign: 'center' }}>
      <Box sx={{ display: 'flex', alignItems: 'center', justifyContent: 'center', gap: 1, mb: 1 }}>
        {isWarning && <WarningIcon color="warning" fontSize="small" />}
        <Typography variant="h6" sx={{ fontWeight: 600, color: theme.palette[color].main }}>
          {expired ? 'Expired' : formatTime(timeLeft)}
        </Typography>
      </Box>
      
      {showProgress && !expired && (
        <LinearProgress
          variant="determinate"
          value={getProgress()}
          sx={{
            height: 8,
            borderRadius: 4,
            bgcolor: alpha(theme.palette[color].main, 0.1),
            '& .MuiLinearProgress-bar': {
              bgcolor: theme.palette[color].main,
            },
          }}
        />
      )}
      
      <Typography variant="caption" color="text.secondary">
        {expired ? 'This QR code has expired' : 'Time remaining'}
      </Typography>
    </Box>
  );
};

export default CountdownTimer;