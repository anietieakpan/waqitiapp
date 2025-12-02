import React, { useEffect, useState } from 'react';
import {
  Box,
  Typography,
  Timeline,
  TimelineItem,
  TimelineSeparator,
  TimelineConnector,
  TimelineContent,
  TimelineDot,
  TimelineOppositeContent,
  Chip,
  CircularProgress,
  Alert,
  Paper,
  IconButton,
  Tooltip,
} from '@mui/material';
import CheckIcon from '@mui/icons-material/Check';
import BlockIcon from '@mui/icons-material/Block';
import InfoIcon from '@mui/icons-material/Info';
import TimerIcon from '@mui/icons-material/Timer';
import RefreshIcon from '@mui/icons-material/Refresh';;
import { ConsentHistory as ConsentHistoryType, ConsentPurpose } from '../../types/gdpr';
import { gdprService } from '../../services/gdprService';
import { format } from 'date-fns';

const purposeLabels: Record<ConsentPurpose, string> = {
  [ConsentPurpose.ESSENTIAL_SERVICE]: 'Essential Services',
  [ConsentPurpose.MARKETING_EMAILS]: 'Marketing Emails',
  [ConsentPurpose.PROMOTIONAL_SMS]: 'SMS Marketing',
  [ConsentPurpose.PUSH_NOTIFICATIONS]: 'Push Notifications',
  [ConsentPurpose.ANALYTICS]: 'Analytics',
  [ConsentPurpose.PERSONALIZATION]: 'Personalization',
  [ConsentPurpose.THIRD_PARTY_SHARING]: 'Third-Party Sharing',
  [ConsentPurpose.PROFILING]: 'User Profiling',
  [ConsentPurpose.AUTOMATED_DECISIONS]: 'Automated Decisions',
  [ConsentPurpose.LOCATION_TRACKING]: 'Location Tracking',
  [ConsentPurpose.BIOMETRIC_DATA]: 'Biometric Data',
  [ConsentPurpose.CROSS_BORDER_TRANSFER]: 'International Transfer',
};

const ConsentHistory: React.FC = () => {
  const [history, setHistory] = useState<ConsentHistoryType | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const loadHistory = async () => {
    setIsLoading(true);
    setError(null);
    try {
      const data = await gdprService.getConsentHistory();
      setHistory(data);
    } catch (err) {
      setError('Failed to load consent history');
      console.error(err);
    } finally {
      setIsLoading(false);
    }
  };

  useEffect(() => {
    loadHistory();
  }, []);

  if (isLoading) {
    return (
      <Box sx={{ display: 'flex', justifyContent: 'center', py: 4 }}>
        <CircularProgress />
      </Box>
    );
  }

  if (error) {
    return (
      <Alert severity="error" action={
        <IconButton size="small" onClick={loadHistory}>
          <RefreshIcon />
        </IconButton>
      }>
        {error}
      </Alert>
    );
  }

  if (!history || history.events.length === 0) {
    return (
      <Alert severity="info">
        No consent history available yet.
      </Alert>
    );
  }

  return (
    <Box>
      <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 2 }}>
        <Typography variant="body2" color="text.secondary">
          Total events: {history.totalEvents}
        </Typography>
        <Tooltip title="Refresh">
          <IconButton size="small" onClick={loadHistory}>
            <RefreshIcon />
          </IconButton>
        </Tooltip>
      </Box>

      <Timeline position="alternate">
        {history.events.map((event, index) => (
          <TimelineItem key={index}>
            <TimelineOppositeContent
              sx={{ m: 'auto 0' }}
              align="right"
              variant="body2"
              color="text.secondary"
            >
              {format(new Date(event.timestamp), 'MMM d, yyyy HH:mm')}
            </TimelineOppositeContent>
            <TimelineSeparator>
              <TimelineConnector sx={{ bgcolor: index === 0 ? 'transparent' : undefined }} />
              <TimelineDot 
                color={event.eventType === 'GRANTED' ? 'success' : 'error'}
                variant={index === 0 ? 'filled' : 'outlined'}
              >
                {event.eventType === 'GRANTED' ? <CheckIcon /> : <BlockIcon />}
              </TimelineDot>
              <TimelineConnector sx={{ 
                bgcolor: index === history.events.length - 1 ? 'transparent' : undefined 
              }} />
            </TimelineSeparator>
            <TimelineContent sx={{ py: '12px', px: 2 }}>
              <Paper sx={{ p: 2 }}>
                <Typography variant="subtitle2" component="span">
                  {event.eventType === 'GRANTED' ? 'Consent Granted' : 'Consent Withdrawn'}
                </Typography>
                <Typography variant="body2" color="text.secondary" gutterBottom>
                  {purposeLabels[event.purpose] || event.purpose}
                </Typography>
                <Box sx={{ display: 'flex', gap: 1, mt: 1 }}>
                  <Chip 
                    size="small" 
                    label={`v${event.version}`}
                    variant="outlined"
                  />
                  <Chip 
                    size="small" 
                    label={event.status}
                    color={event.status === 'GRANTED' ? 'success' : 
                           event.status === 'WITHDRAWN' ? 'error' : 
                           event.status === 'EXPIRED' ? 'warning' : 'default'}
                    variant="outlined"
                  />
                </Box>
              </Paper>
            </TimelineContent>
          </TimelineItem>
        ))}
      </Timeline>

      <Alert severity="info" icon={<InfoIcon />} sx={{ mt: 2 }}>
        <Typography variant="body2">
          This history shows all consent changes you've made. We keep this record 
          to demonstrate compliance with GDPR requirements.
        </Typography>
      </Alert>
    </Box>
  );
};

export default ConsentHistory;