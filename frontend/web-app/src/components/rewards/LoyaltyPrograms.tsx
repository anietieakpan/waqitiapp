import React, { useState } from 'react';
import {
  Box,
  Grid,
  Paper,
  Typography,
  Card,
  CardContent,
  CardActions,
  Button,
  IconButton,
  Chip,
  Avatar,
  List,
  ListItem,
  ListItemAvatar,
  ListItemText,
  ListItemSecondaryAction,
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  TextField,
  Stack,
  Alert,
  LinearProgress,
  Divider,
  Badge,
  Tooltip,
  Tab,
  Tabs,
  FormControlLabel,
  Switch,
  Stepper,
  Step,
  StepLabel,
  StepContent,
  useTheme,
  alpha,
} from '@mui/material';
import CardMembershipIcon from '@mui/icons-material/CardMembership';
import FlightIcon from '@mui/icons-material/Flight';
import HotelIcon from '@mui/icons-material/Hotel';
import RestaurantIcon from '@mui/icons-material/Restaurant';
import LocalGroceryStoreIcon from '@mui/icons-material/LocalGroceryStore';
import LocalCafeIcon from '@mui/icons-material/LocalCafe';
import DirectionsCarIcon from '@mui/icons-material/DirectionsCar';
import MovieIcon from '@mui/icons-material/Movie';
import SpaIcon from '@mui/icons-material/Spa';
import FitnessCenterIcon from '@mui/icons-material/FitnessCenter';
import AddIcon from '@mui/icons-material/Add';
import LinkIcon from '@mui/icons-material/Link';
import CheckCircleIcon from '@mui/icons-material/CheckCircle';
import WarningIcon from '@mui/icons-material/Warning';
import InfoIcon from '@mui/icons-material/Info';
import QrCodeIcon from '@mui/icons-material/QrCode';
import CreditCardIcon from '@mui/icons-material/CreditCard';
import PhoneIcon from '@mui/icons-material/Phone';
import EmailIcon from '@mui/icons-material/Email';
import StarIcon from '@mui/icons-material/Star';
import EmojiEventsIcon from '@mui/icons-material/EmojiEvents';
import TrendingUpIcon from '@mui/icons-material/TrendingUp';
import SyncIcon from '@mui/icons-material/Sync';
import ScheduleIcon from '@mui/icons-material/Schedule';
import LockIcon from '@mui/icons-material/Lock';
import LockOpenIcon from '@mui/icons-material/LockOpen';
import AutoAwesomeIcon from '@mui/icons-material/AutoAwesome';
import RedeemIcon from '@mui/icons-material/Redeem';
import AccountBalanceWalletIcon from '@mui/icons-material/AccountBalanceWallet';
import SwapHorizIcon from '@mui/icons-material/SwapHoriz';
import ArrowForwardIcon from '@mui/icons-material/ArrowForward';
import ContentCopyIcon from '@mui/icons-material/ContentCopy';
import ShareIcon from '@mui/icons-material/Share';
import MoreVertIcon from '@mui/icons-material/MoreVert';;
import { format, addDays } from 'date-fns';

interface LoyaltyProgram {
  id: string;
  name: string;
  company: string;
  category: string;
  icon: React.ReactNode;
  memberNumber?: string;
  status: 'linked' | 'pending' | 'available';
  tier?: string;
  tierColor?: string;
  points: number;
  pointsName: string;
  pointsValue?: number;
  expiringPoints?: {
    amount: number;
    date: string;
  };
  benefits: string[];
  lastSync?: string;
  autoSync: boolean;
}

interface PointsActivity {
  id: string;
  programId: string;
  type: 'earned' | 'redeemed' | 'transferred' | 'expired';
  description: string;
  points: number;
  date: string;
  merchant?: string;
}

interface TransferPartner {
  id: string;
  name: string;
  icon: React.ReactNode;
  ratio: string;
  minimumTransfer: number;
  transferTime: string;
  bonus?: number;
}

interface RedemptionOption {
  id: string;
  programId: string;
  name: string;
  pointsCost: number;
  cashValue?: number;
  category: string;
  availability: 'available' | 'limited' | 'out_of_stock';
  featured?: boolean;
}

const mockPrograms: LoyaltyProgram[] = [
  {
    id: '1',
    name: 'MileagePlus',
    company: 'United Airlines',
    category: 'Travel',
    icon: <Flight />,
    memberNumber: '1234567890',
    status: 'linked',
    tier: 'Premier Gold',
    tierColor: '#FFD700',
    points: 85420,
    pointsName: 'miles',
    pointsValue: 1190.00,
    expiringPoints: {
      amount: 5000,
      date: '2024-12-31',
    },
    benefits: ['Priority boarding', 'Free checked bags', 'Lounge access'],
    lastSync: '2024-01-20T10:30:00Z',
    autoSync: true,
  },
  {
    id: '2',
    name: 'Marriott Bonvoy',
    company: 'Marriott',
    category: 'Hotel',
    icon: <Hotel />,
    memberNumber: '9876543210',
    status: 'linked',
    tier: 'Platinum Elite',
    tierColor: '#E5E4E2',
    points: 125000,
    pointsName: 'points',
    pointsValue: 875.00,
    benefits: ['Room upgrades', 'Late checkout', '50% bonus points'],
    lastSync: '2024-01-19T15:45:00Z',
    autoSync: true,
  },
  {
    id: '3',
    name: 'Starbucks Rewards',
    company: 'Starbucks',
    category: 'Dining',
    icon: <LocalCafe />,
    memberNumber: '5551234567',
    status: 'linked',
    tier: 'Gold',
    tierColor: '#FFD700',
    points: 450,
    pointsName: 'stars',
    benefits: ['Free drinks', 'Birthday reward', 'Double star days'],
    lastSync: '2024-01-20T08:15:00Z',
    autoSync: false,
  },
  {
    id: '4',
    name: 'AMC Stubs',
    company: 'AMC Theatres',
    category: 'Entertainment',
    icon: <Movie />,
    status: 'pending',
    points: 0,
    pointsName: 'points',
    benefits: ['Free popcorn upgrades', 'Priority lanes', 'Birthday rewards'],
    autoSync: false,
  },
  {
    id: '5',
    name: 'CVS ExtraCare',
    company: 'CVS',
    category: 'Retail',
    icon: <LocalGroceryStore />,
    status: 'available',
    points: 0,
    pointsName: 'points',
    benefits: ['2% back', 'Exclusive coupons', 'Free shipping'],
    autoSync: false,
  },
];

const mockActivity: PointsActivity[] = [
  {
    id: 'a1',
    programId: '1',
    type: 'earned',
    description: 'Flight SFO to NYC',
    points: 2500,
    date: '2024-01-18T14:30:00Z',
    merchant: 'United Airlines',
  },
  {
    id: 'a2',
    programId: '2',
    type: 'earned',
    description: '3 night stay - Marriott Downtown',
    points: 5000,
    date: '2024-01-15T12:00:00Z',
    merchant: 'Marriott',
  },
  {
    id: 'a3',
    programId: '3',
    type: 'redeemed',
    description: 'Free drink reward',
    points: -150,
    date: '2024-01-20T09:30:00Z',
    merchant: 'Starbucks',
  },
];

const mockTransferPartners: TransferPartner[] = [
  {
    id: 't1',
    name: 'Chase Ultimate Rewards',
    icon: <CreditCard />,
    ratio: '1:1',
    minimumTransfer: 1000,
    transferTime: 'Instant',
    bonus: 25,
  },
  {
    id: 't2',
    name: 'Amex Membership Rewards',
    icon: <CardMembership />,
    ratio: '1:0.8',
    minimumTransfer: 1000,
    transferTime: '1-2 days',
  },
  {
    id: 't3',
    name: 'Citi ThankYou Points',
    icon: <AccountBalanceWallet />,
    ratio: '1:1',
    minimumTransfer: 500,
    transferTime: 'Instant',
  },
];

interface TabPanelProps {
  children?: React.ReactNode;
  index: number;
  value: number;
}

const TabPanel: React.FC<TabPanelProps> = ({ children, value, index }) => (
  <Box hidden={value !== index} pt={3}>
    {value === index && children}
  </Box>
);

const LoyaltyPrograms: React.FC = () => {
  const theme = useTheme();
  const [activeTab, setActiveTab] = useState(0);
  const [selectedProgram, setSelectedProgram] = useState<LoyaltyProgram | null>(null);
  const [showLinkDialog, setShowLinkDialog] = useState(false);
  const [showTransferDialog, setShowTransferDialog] = useState(false);
  const [linkStep, setLinkStep] = useState(0);
  const [linkData, setLinkData] = useState({
    memberNumber: '',
    email: '',
    password: '',
    phone: '',
  });

  const linkedPrograms = mockPrograms.filter(p => p.status === 'linked');
  const pendingPrograms = mockPrograms.filter(p => p.status === 'pending');
  const availablePrograms = mockPrograms.filter(p => p.status === 'available');

  const totalPointsValue = linkedPrograms.reduce((sum, p) => sum + (p.pointsValue || 0), 0);
  const expiringPointsCount = linkedPrograms.filter(p => p.expiringPoints).length;

  const getCategoryColor = (category: string) => {
    const colors: { [key: string]: string } = {
      Travel: theme.palette.info.main,
      Hotel: theme.palette.secondary.main,
      Dining: theme.palette.warning.main,
      Entertainment: theme.palette.error.main,
      Retail: theme.palette.success.main,
    };
    return colors[category] || theme.palette.grey[500];
  };

  const handleLinkProgram = () => {
    // Handle linking program
    setShowLinkDialog(false);
    setLinkStep(0);
    setLinkData({
      memberNumber: '',
      email: '',
      password: '',
      phone: '',
    });
  };

  const renderProgramCard = (program: LoyaltyProgram) => (
    <Card key={program.id}>
      <CardContent>
        <Box display="flex" justifyContent="space-between" alignItems="flex-start" mb={2}>
          <Box display="flex" alignItems="center" gap={2}>
            <Avatar
              sx={{
                bgcolor: alpha(getCategoryColor(program.category), 0.2),
                color: getCategoryColor(program.category),
                width: 48,
                height: 48,
              }}
            >
              {program.icon}
            </Avatar>
            <Box>
              <Typography variant="subtitle1" fontWeight="bold">
                {program.name}
              </Typography>
              <Typography variant="body2" color="text.secondary">
                {program.company}
              </Typography>
            </Box>
          </Box>
          {program.status === 'linked' && (
            <Chip
              label={program.autoSync ? 'Auto-sync' : 'Manual'}
              size="small"
              icon={program.autoSync ? <Sync /> : <Schedule />}
              color={program.autoSync ? 'success' : 'default'}
            />
          )}
        </Box>

        {program.status === 'linked' && (
          <>
            <Box mb={2}>
              <Box display="flex" alignItems="baseline" gap={1}>
                <Typography variant="h5" fontWeight="bold">
                  {program.points.toLocaleString()}
                </Typography>
                <Typography variant="body2" color="text.secondary">
                  {program.pointsName}
                </Typography>
              </Box>
              {program.pointsValue && (
                <Typography variant="body2" color="primary">
                  ≈ ${program.pointsValue.toFixed(2)}
                </Typography>
              )}
            </Box>

            {program.tier && (
              <Box display="flex" alignItems="center" gap={1} mb={2}>
                <EmojiEvents sx={{ color: program.tierColor || theme.palette.warning.main }} />
                <Typography variant="body2" fontWeight="bold">
                  {program.tier}
                </Typography>
              </Box>
            )}

            {program.expiringPoints && (
              <Alert severity="warning" sx={{ py: 0.5, mb: 2 }}>
                <Typography variant="caption">
                  {program.expiringPoints.amount.toLocaleString()} {program.pointsName} expiring {format(new Date(program.expiringPoints.date), 'MMM dd')}
                </Typography>
              </Alert>
            )}

            <Typography variant="body2" color="text.secondary" mb={1}>
              Member #{program.memberNumber}
            </Typography>

            {program.lastSync && (
              <Typography variant="caption" color="text.secondary">
                Last synced: {format(new Date(program.lastSync), 'MMM dd, HH:mm')}
              </Typography>
            )}
          </>
        )}

        {program.status === 'pending' && (
          <Alert severity="info" icon={<Schedule />}>
            <Typography variant="body2">
              Linking in progress...
            </Typography>
          </Alert>
        )}

        {program.status === 'available' && (
          <Box>
            <Typography variant="body2" color="text.secondary" mb={2}>
              Link your account to track points and unlock exclusive offers
            </Typography>
            <Stack spacing={1}>
              {program.benefits.slice(0, 2).map((benefit, index) => (
                <Box key={index} display="flex" alignItems="center" gap={1}>
                  <CheckCircle fontSize="small" color="success" />
                  <Typography variant="caption">{benefit}</Typography>
                </Box>
              ))}
            </Stack>
          </Box>
        )}
      </CardContent>
      <CardActions>
        {program.status === 'linked' && (
          <>
            <Button size="small" startIcon={<Redeem />}>
              Redeem
            </Button>
            <Button size="small" startIcon={<SwapHoriz />}>
              Transfer
            </Button>
            <IconButton size="small">
              <MoreVert />
            </IconButton>
          </>
        )}
        {program.status === 'pending' && (
          <Button size="small" disabled>
            Linking...
          </Button>
        )}
        {program.status === 'available' && (
          <Button
            size="small"
            variant="contained"
            startIcon={<Link />}
            onClick={() => {
              setSelectedProgram(program);
              setShowLinkDialog(true);
            }}
          >
            Link Account
          </Button>
        )}
      </CardActions>
    </Card>
  );

  return (
    <Box>
      <Box display="flex" justifyContent="space-between" alignItems="center" mb={3}>
        <Typography variant="h5" fontWeight="bold">
          Loyalty Programs
        </Typography>
        <Stack direction="row" spacing={2}>
          <Chip
            label={`${linkedPrograms.length} linked`}
            icon={<CheckCircle />}
            color="success"
          />
          {expiringPointsCount > 0 && (
            <Chip
              label={`${expiringPointsCount} expiring`}
              icon={<Warning />}
              color="warning"
            />
          )}
          <Button variant="contained" startIcon={<Add />}>
            Add Program
          </Button>
        </Stack>
      </Box>

      {/* Summary Cards */}
      <Grid container spacing={3} mb={3}>
        <Grid item xs={12} sm={6} md={3}>
          <Card>
            <CardContent>
              <Box display="flex" alignItems="center" gap={2}>
                <Avatar sx={{ bgcolor: theme.palette.primary.main }}>
                  <CardMembership />
                </Avatar>
                <Box>
                  <Typography variant="body2" color="text.secondary">
                    Total Programs
                  </Typography>
                  <Typography variant="h5" fontWeight="bold">
                    {linkedPrograms.length}
                  </Typography>
                </Box>
              </Box>
            </CardContent>
          </Card>
        </Grid>

        <Grid item xs={12} sm={6} md={3}>
          <Card>
            <CardContent>
              <Box display="flex" alignItems="center" gap={2}>
                <Avatar sx={{ bgcolor: theme.palette.success.main }}>
                  <AccountBalanceWallet />
                </Avatar>
                <Box>
                  <Typography variant="body2" color="text.secondary">
                    Points Value
                  </Typography>
                  <Typography variant="h5" fontWeight="bold">
                    ${totalPointsValue.toFixed(0)}
                  </Typography>
                </Box>
              </Box>
            </CardContent>
          </Card>
        </Grid>

        <Grid item xs={12} sm={6} md={3}>
          <Card>
            <CardContent>
              <Box display="flex" alignItems="center" gap={2}>
                <Avatar sx={{ bgcolor: theme.palette.info.main }}>
                  <TrendingUp />
                </Avatar>
                <Box>
                  <Typography variant="body2" color="text.secondary">
                    This Month
                  </Typography>
                  <Typography variant="h5" fontWeight="bold">
                    +12.5k
                  </Typography>
                </Box>
              </Box>
            </CardContent>
          </Card>
        </Grid>

        <Grid item xs={12} sm={6} md={3}>
          <Card>
            <CardContent>
              <Box display="flex" alignItems="center" gap={2}>
                <Avatar sx={{ bgcolor: theme.palette.warning.main }}>
                  <AutoAwesome />
                </Avatar>
                <Box>
                  <Typography variant="body2" color="text.secondary">
                    Elite Status
                  </Typography>
                  <Typography variant="h5" fontWeight="bold">
                    3
                  </Typography>
                </Box>
              </Box>
            </CardContent>
          </Card>
        </Grid>
      </Grid>

      <Paper>
        <Tabs
          value={activeTab}
          onChange={(_, value) => setActiveTab(value)}
          sx={{ borderBottom: 1, borderColor: 'divider' }}
        >
          <Tab label={`Linked (${linkedPrograms.length})`} />
          <Tab label={`Available (${availablePrograms.length})`} />
          <Tab label="Activity" />
          <Tab label="Transfer Partners" />
        </Tabs>

        <TabPanel value={activeTab} index={0}>
          <Box p={3}>
            <Grid container spacing={3}>
              {linkedPrograms.map(renderProgramCard)}
            </Grid>

            {linkedPrograms.length === 0 && (
              <Box textAlign="center" py={8}>
                <CardMembership sx={{ fontSize: 64, color: 'text.secondary', mb: 2 }} />
                <Typography variant="h6" color="text.secondary" gutterBottom>
                  No linked programs yet
                </Typography>
                <Typography variant="body2" color="text.secondary" mb={3}>
                  Link your loyalty accounts to track all your points in one place
                </Typography>
                <Button variant="contained" onClick={() => setActiveTab(1)}>
                  Browse Programs
                </Button>
              </Box>
            )}
          </Box>
        </TabPanel>

        <TabPanel value={activeTab} index={1}>
          <Box p={3}>
            {pendingPrograms.length > 0 && (
              <>
                <Typography variant="h6" gutterBottom>
                  Pending Connections
                </Typography>
                <Grid container spacing={3} mb={3}>
                  {pendingPrograms.map(renderProgramCard)}
                </Grid>
                <Divider sx={{ my: 3 }} />
              </>
            )}

            <Typography variant="h6" gutterBottom>
              Available Programs
            </Typography>
            <Grid container spacing={3}>
              {availablePrograms.map(renderProgramCard)}
            </Grid>
          </Box>
        </TabPanel>

        <TabPanel value={activeTab} index={2}>
          <Box p={3}>
            <Typography variant="h6" gutterBottom>
              Recent Activity
            </Typography>
            <List>
              {mockActivity.map((activity) => {
                const program = mockPrograms.find(p => p.id === activity.programId);
                return (
                  <ListItem key={activity.id} divider>
                    <ListItemAvatar>
                      <Avatar sx={{ bgcolor: 
                        activity.type === 'earned' ? theme.palette.success.main :
                        activity.type === 'redeemed' ? theme.palette.primary.main :
                        activity.type === 'transferred' ? theme.palette.info.main :
                        theme.palette.error.main
                      }}>
                        {program?.icon}
                      </Avatar>
                    </ListItemAvatar>
                    <ListItemText
                      primary={activity.description}
                      secondary={
                        <Box>
                          <Typography variant="caption" color="text.secondary">
                            {program?.name} • {format(new Date(activity.date), 'MMM dd, yyyy')}
                          </Typography>
                          {activity.merchant && (
                            <Typography variant="caption" color="text.secondary" sx={{ ml: 1 }}>
                              {activity.merchant}
                            </Typography>
                          )}
                        </Box>
                      }
                    />
                    <Typography
                      variant="body1"
                      fontWeight="bold"
                      color={activity.points > 0 ? 'success.main' : 'text.primary'}
                    >
                      {activity.points > 0 ? '+' : ''}{activity.points.toLocaleString()} {program?.pointsName}
                    </Typography>
                  </ListItem>
                );
              })}
            </List>
          </Box>
        </TabPanel>

        <TabPanel value={activeTab} index={3}>
          <Box p={3}>
            <Alert severity="info" sx={{ mb: 3 }}>
              <Typography variant="body2">
                Transfer points between programs to maximize value. Some partners offer transfer bonuses!
              </Typography>
            </Alert>

            <Grid container spacing={3}>
              {mockTransferPartners.map((partner) => (
                <Grid item xs={12} md={6} key={partner.id}>
                  <Card variant="outlined">
                    <CardContent>
                      <Box display="flex" justifyContent="space-between" alignItems="flex-start">
                        <Box display="flex" gap={2}>
                          <Avatar sx={{ bgcolor: theme.palette.grey[200] }}>
                            {partner.icon}
                          </Avatar>
                          <Box>
                            <Typography variant="subtitle1" fontWeight="bold">
                              {partner.name}
                            </Typography>
                            <Stack direction="row" spacing={1} mt={1}>
                              <Chip label={`Ratio ${partner.ratio}`} size="small" />
                              <Chip label={partner.transferTime} size="small" variant="outlined" />
                              {partner.bonus && (
                                <Chip
                                  label={`+${partner.bonus}% bonus`}
                                  size="small"
                                  color="success"
                                />
                              )}
                            </Stack>
                            <Typography variant="caption" color="text.secondary" sx={{ mt: 1, display: 'block' }}>
                              Min transfer: {partner.minimumTransfer.toLocaleString()} points
                            </Typography>
                          </Box>
                        </Box>
                      </Box>
                    </CardContent>
                    <CardActions>
                      <Button
                        size="small"
                        variant="outlined"
                        fullWidth
                        startIcon={<SwapHoriz />}
                        onClick={() => setShowTransferDialog(true)}
                      >
                        Transfer
                      </Button>
                    </CardActions>
                  </Card>
                </Grid>
              ))}
            </Grid>
          </Box>
        </TabPanel>
      </Paper>

      {/* Link Program Dialog */}
      <Dialog
        open={showLinkDialog}
        onClose={() => setShowLinkDialog(false)}
        maxWidth="sm"
        fullWidth
      >
        <DialogTitle>
          Link {selectedProgram?.name}
        </DialogTitle>
        <DialogContent>
          <Box mt={2}>
            <Stepper activeStep={linkStep} orientation="vertical">
              <Step>
                <StepLabel>Enter Member Information</StepLabel>
                <StepContent>
                  <Stack spacing={2}>
                    <TextField
                      label="Member Number"
                      fullWidth
                      value={linkData.memberNumber}
                      onChange={(e) => setLinkData({ ...linkData, memberNumber: e.target.value })}
                    />
                    <TextField
                      label="Email"
                      type="email"
                      fullWidth
                      value={linkData.email}
                      onChange={(e) => setLinkData({ ...linkData, email: e.target.value })}
                    />
                    <Box display="flex" gap={2}>
                      <Button onClick={() => setShowLinkDialog(false)}>
                        Cancel
                      </Button>
                      <Button
                        variant="contained"
                        onClick={() => setLinkStep(1)}
                        disabled={!linkData.memberNumber || !linkData.email}
                      >
                        Continue
                      </Button>
                    </Box>
                  </Stack>
                </StepContent>
              </Step>

              <Step>
                <StepLabel>Verify Account</StepLabel>
                <StepContent>
                  <Stack spacing={2}>
                    <Alert severity="info">
                      <Typography variant="body2">
                        We'll send a verification code to your email
                      </Typography>
                    </Alert>
                    <TextField
                      label="Verification Code"
                      fullWidth
                    />
                    <Box display="flex" gap={2}>
                      <Button onClick={() => setLinkStep(0)}>
                        Back
                      </Button>
                      <Button
                        variant="contained"
                        onClick={() => setLinkStep(2)}
                      >
                        Verify
                      </Button>
                    </Box>
                  </Stack>
                </StepContent>
              </Step>

              <Step>
                <StepLabel>Set Preferences</StepLabel>
                <StepContent>
                  <Stack spacing={2}>
                    <FormControlLabel
                      control={<Switch defaultChecked />}
                      label="Enable auto-sync"
                    />
                    <FormControlLabel
                      control={<Switch defaultChecked />}
                      label="Points expiration alerts"
                    />
                    <FormControlLabel
                      control={<Switch />}
                      label="Transfer opportunity notifications"
                    />
                    <Box display="flex" gap={2}>
                      <Button onClick={() => setLinkStep(1)}>
                        Back
                      </Button>
                      <Button variant="contained" onClick={handleLinkProgram}>
                        Complete
                      </Button>
                    </Box>
                  </Stack>
                </StepContent>
              </Step>
            </Stepper>
          </Box>
        </DialogContent>
      </Dialog>

      {/* Transfer Dialog */}
      <Dialog
        open={showTransferDialog}
        onClose={() => setShowTransferDialog(false)}
        maxWidth="sm"
        fullWidth
      >
        <DialogTitle>Transfer Points</DialogTitle>
        <DialogContent>
          <Stack spacing={3} mt={2}>
            <FormControl fullWidth>
              <InputLabel>From Program</InputLabel>
              <Select native>
                <option value="">Select program</option>
                {linkedPrograms.map(p => (
                  <option key={p.id} value={p.id}>
                    {p.name} ({p.points.toLocaleString()} {p.pointsName})
                  </option>
                ))}
              </Select>
            </FormControl>

            <FormControl fullWidth>
              <InputLabel>To Partner</InputLabel>
              <Select native>
                <option value="">Select partner</option>
                {mockTransferPartners.map(p => (
                  <option key={p.id} value={p.id}>
                    {p.name} ({p.ratio})
                  </option>
                ))}
              </Select>
            </FormControl>

            <TextField
              label="Points to Transfer"
              type="number"
              fullWidth
              inputProps={{ min: 1000, step: 500 }}
            />

            <Alert severity="success">
              <Typography variant="body2">
                <strong>25% Transfer Bonus!</strong> Transfer now and receive 1,250 partner points
              </Typography>
            </Alert>
          </Stack>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setShowTransferDialog(false)}>Cancel</Button>
          <Button variant="contained" startIcon={<SwapHoriz />}>
            Transfer Points
          </Button>
        </DialogActions>
      </Dialog>
    </Box>
  );
};

export default LoyaltyPrograms;