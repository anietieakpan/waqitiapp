import React, { useState } from 'react';
import {
  Box,
  Paper,
  Typography,
  Grid,
  Card,
  CardContent,
  Button,
  Tabs,
  Tab,
  Chip,
  Alert,
  AlertTitle,
  List,
  ListItem,
  ListItemText,
  ListItemIcon,
  ListItemSecondaryAction,
  IconButton,
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  TextField,
  Select,
  MenuItem,
  FormControl,
  InputLabel,
  LinearProgress,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  Badge,
  Divider,
  Stack,
  useTheme,
} from '@mui/material';
import {
  Shield,
  VerifiedUser,
  Warning,
  Error,
  CheckCircle,
  Assignment,
  Description,
  FileDownload,
  Search,
  FilterList,
  Visibility,
  Send,
  Flag,
  AccountBalance,
  Policy,
  Gavel,
  Assessment,
  TrendingUp,
  Schedule,
  Group,
} from '@mui/icons-material';
import { DatePicker } from '@mui/x-date-pickers/DatePicker';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { format } from 'date-fns';

import { complianceService } from '../services/complianceService';
import KYCVerification from '../components/compliance/KYCVerification';
import AMLMonitoring from '../components/compliance/AMLMonitoring';
import SARManagement from '../components/compliance/SARManagement';
import CTRReporting from '../components/compliance/CTRReporting';
import ComplianceCalendar from '../components/compliance/ComplianceCalendar';
import RegulatoryUpdates from '../components/compliance/RegulatoryUpdates';
import ComplianceTraining from '../components/compliance/ComplianceTraining';
import RiskAssessment from '../components/compliance/RiskAssessment';
import { useNotification } from '../hooks/useNotification';

interface TabPanelProps {
  children?: React.ReactNode;
  index: number;
  value: number;
}

function TabPanel(props: TabPanelProps) {
  const { children, value, index, ...other } = props;
  return (
    <div hidden={value !== index} {...other}>
      {value === index && <Box sx={{ pt: 3 }}>{children}</Box>}
    </div>
  );
}

const Compliance: React.FC = () => {
  const theme = useTheme();
  const queryClient = useQueryClient();
  const { showNotification } = useNotification();
  
  const [selectedTab, setSelectedTab] = useState(0);
  const [selectedReport, setSelectedReport] = useState<any>(null);
  const [reportDialogOpen, setReportDialogOpen] = useState(false);
  const [filterDialogOpen, setFilterDialogOpen] = useState(false);

  // Fetch compliance overview
  const { data: overviewData, isLoading: overviewLoading } = useQuery({
    queryKey: ['compliance-overview'],
    queryFn: () => complianceService.getComplianceOverview(),
  });

  // Fetch pending items
  const { data: pendingItems, isLoading: pendingLoading } = useQuery({
    queryKey: ['compliance-pending'],
    queryFn: () => complianceService.getPendingItems(),
    refetchInterval: 60000, // Refresh every minute
  });

  // Fetch compliance score
  const { data: complianceScore } = useQuery({
    queryKey: ['compliance-score'],
    queryFn: () => complianceService.getComplianceScore(),
  });

  // Submit report mutation
  const submitReportMutation = useMutation({
    mutationFn: (data: any) => complianceService.submitReport(data),
    onSuccess: () => {
      queryClient.invalidateQueries(['compliance-pending']);
      showNotification('Report submitted successfully', 'success');
      setReportDialogOpen(false);
    },
  });

  const getComplianceColor = (score: number) => {
    if (score >= 90) return 'success';
    if (score >= 70) return 'warning';
    return 'error';
  };

  const getComplianceLabel = (score: number) => {
    if (score >= 90) return 'Excellent';
    if (score >= 70) return 'Good';
    if (score >= 50) return 'Fair';
    return 'Poor';
  };

  return (
    <Box>
      {/* Header */}
      <Box sx={{ mb: 3, display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
        <Typography variant="h4" fontWeight="bold">
          Compliance Management
        </Typography>
        <Stack direction="row" spacing={2}>
          <Button
            variant="outlined"
            startIcon={<FilterList />}
            onClick={() => setFilterDialogOpen(true)}
          >
            Filters
          </Button>
          <Button
            variant="contained"
            startIcon={<Assignment />}
            onClick={() => setReportDialogOpen(true)}
          >
            New Report
          </Button>
        </Stack>
      </Box>

      {/* Compliance Score Alert */}
      {complianceScore && complianceScore.overall < 70 && (
        <Alert 
          severity="warning" 
          sx={{ mb: 3 }}
          action={
            <Button color="inherit" size="small">
              View Details
            </Button>
          }
        >
          <AlertTitle>Compliance Score Below Threshold</AlertTitle>
          Your compliance score is {complianceScore.overall}%. Immediate action required to meet regulatory standards.
        </Alert>
      )}

      {/* Overview Cards */}
      <Grid container spacing={3} sx={{ mb: 3 }}>
        <Grid item xs={12} sm={6} md={3}>
          <Card>
            <CardContent>
              <Box sx={{ display: 'flex', alignItems: 'center', mb: 2 }}>
                <Shield sx={{ fontSize: 40, color: theme.palette.primary.main, mr: 2 }} />
                <Box>
                  <Typography color="text.secondary" variant="body2">
                    Compliance Score
                  </Typography>
                  <Typography variant="h4" fontWeight="bold">
                    {complianceScore?.overall || 0}%
                  </Typography>
                </Box>
              </Box>
              <Chip
                label={getComplianceLabel(complianceScore?.overall || 0)}
                color={getComplianceColor(complianceScore?.overall || 0)}
                size="small"
              />
            </CardContent>
          </Card>
        </Grid>

        <Grid item xs={12} sm={6} md={3}>
          <Card>
            <CardContent>
              <Box sx={{ display: 'flex', alignItems: 'center', mb: 2 }}>
                <VerifiedUser sx={{ fontSize: 40, color: theme.palette.success.main, mr: 2 }} />
                <Box>
                  <Typography color="text.secondary" variant="body2">
                    KYC Verified
                  </Typography>
                  <Typography variant="h4" fontWeight="bold">
                    {overviewData?.kycVerified || 0}
                  </Typography>
                </Box>
              </Box>
              <Typography variant="body2" color="text.secondary">
                {overviewData?.kycPending || 0} pending
              </Typography>
            </CardContent>
          </Card>
        </Grid>

        <Grid item xs={12} sm={6} md={3}>
          <Card>
            <CardContent>
              <Box sx={{ display: 'flex', alignItems: 'center', mb: 2 }}>
                <Warning sx={{ fontSize: 40, color: theme.palette.warning.main, mr: 2 }} />
                <Box>
                  <Typography color="text.secondary" variant="body2">
                    SARs Filed
                  </Typography>
                  <Typography variant="h4" fontWeight="bold">
                    {overviewData?.sarsFiled || 0}
                  </Typography>
                </Box>
              </Box>
              <Typography variant="body2" color="text.secondary">
                This month
              </Typography>
            </CardContent>
          </Card>
        </Grid>

        <Grid item xs={12} sm={6} md={3}>
          <Card>
            <CardContent>
              <Box sx={{ display: 'flex', alignItems: 'center', mb: 2 }}>
                <Assignment sx={{ fontSize: 40, color: theme.palette.info.main, mr: 2 }} />
                <Box>
                  <Typography color="text.secondary" variant="body2">
                    CTRs Filed
                  </Typography>
                  <Typography variant="h4" fontWeight="bold">
                    {overviewData?.ctrsFiled || 0}
                  </Typography>
                </Box>
              </Box>
              <Typography variant="body2" color="text.secondary">
                This month
              </Typography>
            </CardContent>
          </Card>
        </Grid>
      </Grid>

      {/* Pending Actions */}
      {pendingItems && pendingItems.length > 0 && (
        <Alert severity="info" sx={{ mb: 3 }}>
          <AlertTitle>Pending Actions Required</AlertTitle>
          You have {pendingItems.length} compliance items requiring attention.
        </Alert>
      )}

      {/* Main Content Tabs */}
      <Paper sx={{ mb: 3 }}>
        <Tabs
          value={selectedTab}
          onChange={(_, value) => setSelectedTab(value)}
          indicatorColor="primary"
          textColor="primary"
          variant="scrollable"
          scrollButtons="auto"
        >
          <Tab label="Overview" icon={<Assessment />} />
          <Tab 
            label="KYC Management" 
            icon={<Badge badgeContent={overviewData?.kycPending || 0} color="error"><Group /></Badge>}
          />
          <Tab label="AML Monitoring" icon={<Shield />} />
          <Tab 
            label="SAR Management" 
            icon={<Badge badgeContent={overviewData?.sarsPending || 0} color="warning"><Flag /></Badge>}
          />
          <Tab label="CTR Reporting" icon={<Description />} />
          <Tab label="Risk Assessment" icon={<Warning />} />
          <Tab label="Regulatory Updates" icon={<Policy />} />
          <Tab label="Training" icon={<School />} />
        </Tabs>
      </Paper>

      {/* Tab Panels */}
      <TabPanel value={selectedTab} index={0}>
        {/* Compliance Overview Dashboard */}
        <Grid container spacing={3}>
          <Grid item xs={12} md={8}>
            <Paper sx={{ p: 3, height: 400 }}>
              <Typography variant="h6" gutterBottom>
                Compliance Trends
              </Typography>
              {/* Add compliance trends chart */}
              <Box sx={{ height: 320 }}>
                {/* Chart component would go here */}
              </Box>
            </Paper>
          </Grid>
          
          <Grid item xs={12} md={4}>
            <Paper sx={{ p: 3, height: 400 }}>
              <Typography variant="h6" gutterBottom>
                Upcoming Deadlines
              </Typography>
              <List>
                {overviewData?.upcomingDeadlines?.map((deadline: any, index: number) => (
                  <ListItem key={index}>
                    <ListItemIcon>
                      <Schedule color={deadline.daysRemaining < 7 ? 'error' : 'action'} />
                    </ListItemIcon>
                    <ListItemText
                      primary={deadline.title}
                      secondary={`Due: ${format(new Date(deadline.dueDate), 'MMM dd, yyyy')}`}
                    />
                    <ListItemSecondaryAction>
                      <Chip
                        label={`${deadline.daysRemaining}d`}
                        size="small"
                        color={deadline.daysRemaining < 7 ? 'error' : 'default'}
                      />
                    </ListItemSecondaryAction>
                  </ListItem>
                ))}
              </List>
            </Paper>
          </Grid>

          <Grid item xs={12}>
            <Paper sx={{ p: 3 }}>
              <Typography variant="h6" gutterBottom>
                Recent Compliance Activities
              </Typography>
              <TableContainer>
                <Table>
                  <TableHead>
                    <TableRow>
                      <TableCell>Date</TableCell>
                      <TableCell>Type</TableCell>
                      <TableCell>Description</TableCell>
                      <TableCell>Status</TableCell>
                      <TableCell>Action By</TableCell>
                      <TableCell align="right">Actions</TableCell>
                    </TableRow>
                  </TableHead>
                  <TableBody>
                    {overviewData?.recentActivities?.map((activity: any) => (
                      <TableRow key={activity.id}>
                        <TableCell>
                          {format(new Date(activity.date), 'MMM dd, yyyy HH:mm')}
                        </TableCell>
                        <TableCell>
                          <Chip label={activity.type} size="small" />
                        </TableCell>
                        <TableCell>{activity.description}</TableCell>
                        <TableCell>
                          <Chip
                            label={activity.status}
                            size="small"
                            color={
                              activity.status === 'COMPLETED' ? 'success' :
                              activity.status === 'PENDING' ? 'warning' : 'default'
                            }
                          />
                        </TableCell>
                        <TableCell>{activity.actionBy}</TableCell>
                        <TableCell align="right">
                          <IconButton size="small">
                            <Visibility />
                          </IconButton>
                        </TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              </TableContainer>
            </Paper>
          </Grid>
        </Grid>
      </TabPanel>

      <TabPanel value={selectedTab} index={1}>
        <KYCVerification />
      </TabPanel>

      <TabPanel value={selectedTab} index={2}>
        <AMLMonitoring />
      </TabPanel>

      <TabPanel value={selectedTab} index={3}>
        <SARManagement />
      </TabPanel>

      <TabPanel value={selectedTab} index={4}>
        <CTRReporting />
      </TabPanel>

      <TabPanel value={selectedTab} index={5}>
        <RiskAssessment />
      </TabPanel>

      <TabPanel value={selectedTab} index={6}>
        <RegulatoryUpdates />
      </TabPanel>

      <TabPanel value={selectedTab} index={7}>
        <ComplianceTraining />
      </TabPanel>

      {/* New Report Dialog */}
      <Dialog
        open={reportDialogOpen}
        onClose={() => setReportDialogOpen(false)}
        maxWidth="md"
        fullWidth
      >
        <DialogTitle>Create Compliance Report</DialogTitle>
        <DialogContent>
          <Grid container spacing={2} sx={{ mt: 1 }}>
            <Grid item xs={12}>
              <FormControl fullWidth>
                <InputLabel>Report Type</InputLabel>
                <Select
                  value=""
                  label="Report Type"
                >
                  <MenuItem value="SAR">Suspicious Activity Report (SAR)</MenuItem>
                  <MenuItem value="CTR">Currency Transaction Report (CTR)</MenuItem>
                  <MenuItem value="AUDIT">Audit Report</MenuItem>
                  <MenuItem value="RISK">Risk Assessment Report</MenuItem>
                </Select>
              </FormControl>
            </Grid>
            <Grid item xs={12} sm={6}>
              <DatePicker
                label="Report Period Start"
                value={null}
                onChange={() => {}}
                renderInput={(params) => <TextField {...params} fullWidth />}
              />
            </Grid>
            <Grid item xs={12} sm={6}>
              <DatePicker
                label="Report Period End"
                value={null}
                onChange={() => {}}
                renderInput={(params) => <TextField {...params} fullWidth />}
              />
            </Grid>
            <Grid item xs={12}>
              <TextField
                fullWidth
                multiline
                rows={4}
                label="Description"
                placeholder="Enter report description..."
              />
            </Grid>
          </Grid>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setReportDialogOpen(false)}>Cancel</Button>
          <Button variant="contained" onClick={() => {}}>
            Create Report
          </Button>
        </DialogActions>
      </Dialog>
    </Box>
  );
};

// Add missing import
import { School } from '@mui/icons-material';

export default Compliance;