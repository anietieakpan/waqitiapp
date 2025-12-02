import React, { useState, useEffect } from 'react';
import {
  Box,
  Typography,
  Card,
  CardContent,
  Grid,
  Button,
  Tabs,
  Tab,
  List,
  ListItem,
  ListItemIcon,
  ListItemText,
  ListItemSecondaryAction,
  Switch,
  IconButton,
  Chip,
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  Alert,
  CircularProgress,
  Paper,
  Divider,
  TextField,
  FormControl,
  InputLabel,
  Select,
  MenuItem,
  Tooltip,
  Badge,
  useTheme,
  alpha,
} from '@mui/material';
import PrivacyIcon from '@mui/icons-material/Privacy';
import SecurityIcon from '@mui/icons-material/Security';
import DownloadIcon from '@mui/icons-material/GetApp';
import DeleteIcon from '@mui/icons-material/Delete';
import InfoIcon from '@mui/icons-material/Info';
import CheckIcon from '@mui/icons-material/Check';
import WarningIcon from '@mui/icons-material/Warning';
import MailIcon from '@mui/icons-material/Mail';
import NotificationsIcon from '@mui/icons-material/Notifications';
import AnalyticsIcon from '@mui/icons-material/Analytics';
import ShareIcon from '@mui/icons-material/Share';
import PersonIcon from '@mui/icons-material/Person';
import LocationIcon from '@mui/icons-material/LocationOn';
import DevicesIcon from '@mui/icons-material/Devices';
import PsychologyIcon from '@mui/icons-material/Psychology';
import CreditCardIcon from '@mui/icons-material/CreditCard';
import HistoryIcon from '@mui/icons-material/History';
import PolicyIcon from '@mui/icons-material/Policy';
import HelpIcon from '@mui/icons-material/Help';
import ExportIcon from '@mui/icons-material/Download';
import EraseIcon from '@mui/icons-material/DeleteForever';
import EditIcon from '@mui/icons-material/Edit';
import BlockIcon from '@mui/icons-material/Block';
import GavelIcon from '@mui/icons-material/Gavel';;
import { useGDPRService } from '../../hooks/useGDPRService';
import { ConsentPurpose, RequestType, ExportFormat } from '../../types/gdpr';
import ConsentHistory from './ConsentHistory';
import DataExportDialog from './DataExportDialog';
import DataErasureDialog from './DataErasureDialog';

interface TabPanelProps {
  children?: React.ReactNode;
  index: number;
  value: number;
}

const TabPanel: React.FC<TabPanelProps> = ({ children, value, index }) => {
  return (
    <div hidden={value !== index}>
      {value === index && <Box sx={{ pt: 3 }}>{children}</Box>}
    </div>
  );
};

const consentCategories = [
  {
    purpose: ConsentPurpose.ESSENTIAL_SERVICE,
    title: 'Essential Services',
    description: 'Core functionality required to use Waqiti',
    icon: <SecurityIcon />,
    required: true,
  },
  {
    purpose: ConsentPurpose.MARKETING_EMAILS,
    title: 'Marketing Emails',
    description: 'Promotional offers and product updates via email',
    icon: <MailIcon />,
    required: false,
  },
  {
    purpose: ConsentPurpose.PUSH_NOTIFICATIONS,
    title: 'Push Notifications',
    description: 'Mobile and browser push notifications',
    icon: <NotificationsIcon />,
    required: false,
  },
  {
    purpose: ConsentPurpose.ANALYTICS,
    title: 'Usage Analytics',
    description: 'Help us improve by analyzing how you use our services',
    icon: <AnalyticsIcon />,
    required: false,
  },
  {
    purpose: ConsentPurpose.PERSONALIZATION,
    title: 'Personalization',
    description: 'Customize your experience based on your preferences',
    icon: <PsychologyIcon />,
    required: false,
  },
  {
    purpose: ConsentPurpose.THIRD_PARTY_SHARING,
    title: 'Partner Sharing',
    description: 'Share data with trusted partners for enhanced services',
    icon: <ShareIcon />,
    required: false,
  },
];

const dataCategories = [
  { id: 'PERSONAL_INFO', label: 'Personal Information', icon: <PersonIcon /> },
  { id: 'FINANCIAL_DATA', label: 'Financial Data', icon: <CreditCardIcon /> },
  { id: 'TRANSACTION_HISTORY', label: 'Transaction History', icon: <HistoryIcon /> },
  { id: 'LOCATION_DATA', label: 'Location Data', icon: <LocationIcon /> },
  { id: 'DEVICE_DATA', label: 'Device Information', icon: <DevicesIcon /> },
  { id: 'BEHAVIORAL_DATA', label: 'Behavioral Data', icon: <PsychologyIcon /> },
];

const PrivacyDashboard: React.FC = () => {
  const theme = useTheme();
  const {
    consents,
    requests,
    loadConsents,
    loadRequests,
    updateConsentPreferences,
    createDataRequest,
    exportData,
    isLoading,
    error,
  } = useGDPRService();

  const [currentTab, setCurrentTab] = useState(0);
  const [consentMap, setConsentMap] = useState<Map<ConsentPurpose, boolean>>(new Map());
  const [showExportDialog, setShowExportDialog] = useState(false);
  const [showErasureDialog, setShowErasureDialog] = useState(false);
  const [showHistoryDialog, setShowHistoryDialog] = useState(false);
  const [selectedCategories, setSelectedCategories] = useState<string[]>([]);

  useEffect(() => {
    loadConsents();
    loadRequests();
  }, []);

  useEffect(() => {
    // Build consent map from loaded consents
    const map = new Map<ConsentPurpose, boolean>();
    consents.forEach(consent => {
      if (consent.isActive) {
        map.set(consent.purpose, true);
      }
    });
    setConsentMap(map);
  }, [consents]);

  const handleTabChange = (event: React.SyntheticEvent, newValue: number) => {
    setCurrentTab(newValue);
  };

  const handleConsentToggle = async (purpose: ConsentPurpose, checked: boolean) => {
    const newMap = new Map(consentMap);
    newMap.set(purpose, checked);
    setConsentMap(newMap);

    try {
      await updateConsentPreferences({
        preferences: Object.fromEntries(newMap),
      });
    } catch (error) {
      // Revert on error
      newMap.set(purpose, !checked);
      setConsentMap(newMap);
    }
  };

  const handleDataExport = async (format: ExportFormat, categories: string[]) => {
    try {
      await exportData(format, categories);
      setShowExportDialog(false);
    } catch (error) {
      console.error('Export failed:', error);
    }
  };

  const handleDataErasure = async (categories: string[]) => {
    try {
      await createDataRequest({
        requestType: RequestType.ERASURE,
        dataCategories: categories,
        notes: 'User requested data erasure via privacy dashboard',
      });
      setShowErasureDialog(false);
    } catch (error) {
      console.error('Erasure request failed:', error);
    }
  };

  const renderConsentManagement = () => (
    <Box>
      <Typography variant="h6" gutterBottom>
        Privacy Preferences
      </Typography>
      <Typography variant="body2" color="text.secondary" sx={{ mb: 3 }}>
        Control how we use your data. You can change these settings at any time.
      </Typography>

      <List>
        {consentCategories.map((category) => (
          <ListItem
            key={category.purpose}
            sx={{
              mb: 2,
              border: 1,
              borderColor: 'divider',
              borderRadius: 2,
              bgcolor: category.required ? alpha(theme.palette.info.main, 0.05) : 'background.paper',
            }}
          >
            <ListItemIcon>{category.icon}</ListItemIcon>
            <ListItemText
              primary={category.title}
              secondary={
                <>
                  {category.description}
                  {category.required && (
                    <Chip
                      size="small"
                      label="Required"
                      color="info"
                      sx={{ ml: 1, height: 20 }}
                    />
                  )}
                </>
              }
            />
            <ListItemSecondaryAction>
              <Switch
                edge="end"
                checked={category.required || consentMap.get(category.purpose) || false}
                onChange={(e) => handleConsentToggle(category.purpose, e.target.checked)}
                disabled={category.required || isLoading}
              />
            </ListItemSecondaryAction>
          </ListItem>
        ))}
      </List>

      <Box sx={{ mt: 3, display: 'flex', gap: 2 }}>
        <Button
          variant="outlined"
          startIcon={<HistoryIcon />}
          onClick={() => setShowHistoryDialog(true)}
        >
          View Consent History
        </Button>
        <Button
          variant="outlined"
          startIcon={<PolicyIcon />}
          href="/privacy-policy"
          target="_blank"
        >
          Privacy Policy
        </Button>
      </Box>
    </Box>
  );

  const renderDataRequests = () => (
    <Box>
      <Typography variant="h6" gutterBottom>
        Your Data Rights
      </Typography>
      <Typography variant="body2" color="text.secondary" sx={{ mb: 3 }}>
        Exercise your GDPR rights to access, export, or delete your personal data.
      </Typography>

      <Grid container spacing={3}>
        <Grid item xs={12} md={6}>
          <Card>
            <CardContent>
              <Box sx={{ display: 'flex', alignItems: 'center', mb: 2 }}>
                <ExportIcon sx={{ mr: 2, color: 'primary.main' }} />
                <Typography variant="h6">Export Your Data</Typography>
              </Box>
              <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
                Download a copy of all your personal data in a portable format.
              </Typography>
              <Button
                variant="contained"
                fullWidth
                startIcon={<DownloadIcon />}
                onClick={() => setShowExportDialog(true)}
              >
                Export Data
              </Button>
            </CardContent>
          </Card>
        </Grid>

        <Grid item xs={12} md={6}>
          <Card>
            <CardContent>
              <Box sx={{ display: 'flex', alignItems: 'center', mb: 2 }}>
                <EraseIcon sx={{ mr: 2, color: 'error.main' }} />
                <Typography variant="h6">Delete Your Data</Typography>
              </Box>
              <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
                Permanently delete your account and personal data.
              </Typography>
              <Button
                variant="outlined"
                fullWidth
                color="error"
                startIcon={<DeleteIcon />}
                onClick={() => setShowErasureDialog(true)}
              >
                Request Deletion
              </Button>
            </CardContent>
          </Card>
        </Grid>

        <Grid item xs={12} md={6}>
          <Card>
            <CardContent>
              <Box sx={{ display: 'flex', alignItems: 'center', mb: 2 }}>
                <EditIcon sx={{ mr: 2, color: 'warning.main' }} />
                <Typography variant="h6">Correct Your Data</Typography>
              </Box>
              <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
                Request corrections to inaccurate personal information.
              </Typography>
              <Button
                variant="outlined"
                fullWidth
                startIcon={<EditIcon />}
                onClick={() => {
                  createDataRequest({
                    requestType: RequestType.RECTIFICATION,
                    dataCategories: ['PERSONAL_INFO'],
                    notes: 'Request to correct personal information',
                  });
                }}
              >
                Request Correction
              </Button>
            </CardContent>
          </Card>
        </Grid>

        <Grid item xs={12} md={6}>
          <Card>
            <CardContent>
              <Box sx={{ display: 'flex', alignItems: 'center', mb: 2 }}>
                <BlockIcon sx={{ mr: 2, color: 'info.main' }} />
                <Typography variant="h6">Restrict Processing</Typography>
              </Box>
              <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
                Limit how we process your personal data.
              </Typography>
              <Button
                variant="outlined"
                fullWidth
                startIcon={<BlockIcon />}
                onClick={() => {
                  createDataRequest({
                    requestType: RequestType.RESTRICTION,
                    dataCategories: selectedCategories,
                    notes: 'Request to restrict data processing',
                  });
                }}
              >
                Request Restriction
              </Button>
            </CardContent>
          </Card>
        </Grid>
      </Grid>

      {/* Active Requests */}
      {requests.length > 0 && (
        <Box sx={{ mt: 4 }}>
          <Typography variant="h6" gutterBottom>
            Active Requests
          </Typography>
          <List>
            {requests.map((request) => (
              <ListItem
                key={request.id}
                sx={{
                  border: 1,
                  borderColor: 'divider',
                  borderRadius: 2,
                  mb: 1,
                }}
              >
                <ListItemIcon>
                  {request.status === 'COMPLETED' ? (
                    <CheckIcon color="success" />
                  ) : request.status === 'REJECTED' ? (
                    <WarningIcon color="error" />
                  ) : (
                    <CircularProgress size={20} />
                  )}
                </ListItemIcon>
                <ListItemText
                  primary={`${request.requestType} Request`}
                  secondary={
                    <>
                      Status: {request.status} • Submitted: {new Date(request.submittedAt).toLocaleDateString()}
                      {request.deadline && ` • Deadline: ${new Date(request.deadline).toLocaleDateString()}`}
                    </>
                  }
                />
                {request.exportUrl && (
                  <ListItemSecondaryAction>
                    <IconButton href={request.exportUrl} download>
                      <DownloadIcon />
                    </IconButton>
                  </ListItemSecondaryAction>
                )}
              </ListItem>
            ))}
          </List>
        </Box>
      )}
    </Box>
  );

  const renderDataOverview = () => (
    <Box>
      <Typography variant="h6" gutterBottom>
        Your Data Overview
      </Typography>
      <Typography variant="body2" color="text.secondary" sx={{ mb: 3 }}>
        See what types of data we collect and how we use it.
      </Typography>

      <Grid container spacing={2}>
        {dataCategories.map((category) => (
          <Grid item xs={12} sm={6} md={4} key={category.id}>
            <Paper
              sx={{
                p: 2,
                display: 'flex',
                alignItems: 'center',
                cursor: 'pointer',
                transition: 'all 0.2s',
                border: selectedCategories.includes(category.id) ? 2 : 1,
                borderColor: selectedCategories.includes(category.id) ? 'primary.main' : 'divider',
                '&:hover': {
                  bgcolor: alpha(theme.palette.primary.main, 0.05),
                },
              }}
              onClick={() => {
                setSelectedCategories(prev =>
                  prev.includes(category.id)
                    ? prev.filter(id => id !== category.id)
                    : [...prev, category.id]
                );
              }}
            >
              <Box sx={{ color: 'primary.main', mr: 2 }}>
                {category.icon}
              </Box>
              <Typography variant="body2">{category.label}</Typography>
            </Paper>
          </Grid>
        ))}
      </Grid>

      <Alert severity="info" sx={{ mt: 3 }}>
        <Typography variant="body2">
          We collect and process your data in accordance with GDPR regulations. 
          You have the right to access, correct, delete, or restrict the processing 
          of your personal data at any time.
        </Typography>
      </Alert>

      <Box sx={{ mt: 3 }}>
        <Typography variant="subtitle2" gutterBottom>
          Data Retention
        </Typography>
        <Typography variant="body2" color="text.secondary">
          • Transaction data: 7 years (legal requirement)
          <br />
          • Account information: Until account closure + 1 year
          <br />
          • Marketing data: Until consent withdrawal
          <br />
          • Analytics data: 2 years
        </Typography>
      </Box>
    </Box>
  );

  return (
    <Box>
      <Box sx={{ display: 'flex', alignItems: 'center', mb: 3 }}>
        <PrivacyIcon sx={{ mr: 2, fontSize: 32, color: 'primary.main' }} />
        <Box>
          <Typography variant="h4">Privacy Center</Typography>
          <Typography variant="body2" color="text.secondary">
            Manage your privacy settings and data rights
          </Typography>
        </Box>
      </Box>

      {error && (
        <Alert severity="error" sx={{ mb: 3 }}>
          {error.message}
        </Alert>
      )}

      <Card>
        <Tabs
          value={currentTab}
          onChange={handleTabChange}
          indicatorColor="primary"
          textColor="primary"
          variant="fullWidth"
        >
          <Tab label="Privacy Settings" icon={<PrivacyIcon />} />
          <Tab label="Data Rights" icon={<GavelIcon />} />
          <Tab label="Data Overview" icon={<InfoIcon />} />
        </Tabs>

        <CardContent>
          <TabPanel value={currentTab} index={0}>
            {renderConsentManagement()}
          </TabPanel>
          <TabPanel value={currentTab} index={1}>
            {renderDataRequests()}
          </TabPanel>
          <TabPanel value={currentTab} index={2}>
            {renderDataOverview()}
          </TabPanel>
        </CardContent>
      </Card>

      {/* Dialogs */}
      <DataExportDialog
        open={showExportDialog}
        onClose={() => setShowExportDialog(false)}
        onExport={handleDataExport}
        categories={dataCategories}
      />

      <DataErasureDialog
        open={showErasureDialog}
        onClose={() => setShowErasureDialog(false)}
        onConfirm={handleDataErasure}
        categories={dataCategories}
      />

      <Dialog
        open={showHistoryDialog}
        onClose={() => setShowHistoryDialog(false)}
        maxWidth="md"
        fullWidth
      >
        <DialogTitle>Consent History</DialogTitle>
        <DialogContent>
          <ConsentHistory />
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setShowHistoryDialog(false)}>Close</Button>
        </DialogActions>
      </Dialog>
    </Box>
  );
};

export default PrivacyDashboard;