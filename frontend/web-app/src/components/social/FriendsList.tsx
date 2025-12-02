import React, { useState, useEffect } from 'react';
import {
  Box,
  Paper,
  Typography,
  List,
  ListItem,
  ListItemAvatar,
  ListItemText,
  ListItemSecondaryAction,
  Avatar,
  IconButton,
  Button,
  TextField,
  InputAdornment,
  Tabs,
  Tab,
  Badge,
  Chip,
  Menu,
  MenuItem,
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  Divider,
  Alert,
  Skeleton,
  CircularProgress,
  useTheme,
  alpha,
  Tooltip,
  Grid,
  Card,
  CardContent,
  Switch,
  FormControlLabel,
} from '@mui/material';
import SearchIcon from '@mui/icons-material/Search';
import PersonAddIcon from '@mui/icons-material/PersonAdd';
import SendIcon from '@mui/icons-material/Send';
import MoneyIcon from '@mui/icons-material/AttachMoney';
import MoreIcon from '@mui/icons-material/MoreVert';
import BlockIcon from '@mui/icons-material/Block';
import UnfriendIcon from '@mui/icons-material/PersonRemove';
import FavoriteIcon from '@mui/icons-material/Star';
import FavoriteOutlineIcon from '@mui/icons-material/StarBorder';
import VerifiedIcon from '@mui/icons-material/Verified';
import GroupIcon from '@mui/icons-material/Group';
import QrCodeIcon from '@mui/icons-material/QrCode';
import ShareIcon from '@mui/icons-material/Share';
import CopyIcon from '@mui/icons-material/ContentCopy';
import CheckIcon from '@mui/icons-material/Check';
import CloseIcon from '@mui/icons-material/Close';
import MuteIcon from '@mui/icons-material/NotificationsOff';
import ReportIcon from '@mui/icons-material/Flag';
import ProfileIcon from '@mui/icons-material/AccountCircle';
import MessageIcon from '@mui/icons-material/Message';
import RequestIcon from '@mui/icons-material/RequestQuote';
import SplitIcon from '@mui/icons-material/SplitscreenRounded';
import ContactsIcon from '@mui/icons-material/ContactsRounded';
import MutualIcon from '@mui/icons-material/PeopleAlt';
import LocationIcon from '@mui/icons-material/LocationOn';
import CalendarIcon from '@mui/icons-material/CalendarToday';;
import { format } from 'date-fns';
import { useNavigate } from 'react-router-dom';
import { useAppSelector } from '../../hooks/redux';
import { QRCodeSVG } from 'qrcode.react';
import toast from 'react-hot-toast';

interface FriendsListProps {
  onSelectFriend?: (friend: Friend) => void;
  selectionMode?: boolean;
  selectedFriends?: string[];
}

interface Friend {
  id: string;
  firstName: string;
  lastName: string;
  username: string;
  avatar?: string;
  verified?: boolean;
  favorite?: boolean;
  muted?: boolean;
  lastActive?: string;
  memberSince?: string;
  mutualFriends?: number;
  location?: string;
  bio?: string;
  status?: 'online' | 'offline' | 'away';
  totalTransactions?: number;
  totalAmount?: number;
}

interface FriendRequest {
  id: string;
  from: Friend;
  timestamp: string;
  message?: string;
  status: 'pending' | 'accepted' | 'rejected';
}

const FriendsList: React.FC<FriendsListProps> = ({
  onSelectFriend,
  selectionMode = false,
  selectedFriends = [],
}) => {
  const theme = useTheme();
  const navigate = useNavigate();
  const { user } = useAppSelector((state) => state.auth);
  
  const [selectedTab, setSelectedTab] = useState(0);
  const [searchQuery, setSearchQuery] = useState('');
  const [friends, setFriends] = useState<Friend[]>([]);
  const [requests, setRequests] = useState<FriendRequest[]>([]);
  const [suggestions, setSuggestions] = useState<Friend[]>([]);
  const [loading, setLoading] = useState(false);
  const [selectedFriend, setSelectedFriend] = useState<Friend | null>(null);
  const [menuAnchor, setMenuAnchor] = useState<null | HTMLElement>(null);
  const [detailsDialogOpen, setDetailsDialogOpen] = useState(false);
  const [qrDialogOpen, setQrDialogOpen] = useState(false);
  const [addFriendDialogOpen, setAddFriendDialogOpen] = useState(false);
  const [friendUsername, setFriendUsername] = useState('');
  const [copied, setCopied] = useState(false);

  // Mock data
  const mockFriends: Friend[] = [
    {
      id: '1',
      firstName: 'John',
      lastName: 'Doe',
      username: 'johndoe',
      avatar: 'https://i.pravatar.cc/150?img=1',
      verified: true,
      favorite: true,
      status: 'online',
      lastActive: new Date().toISOString(),
      memberSince: '2023-01-15',
      mutualFriends: 12,
      location: 'New York, NY',
      bio: 'Love traveling and trying new restaurants!',
      totalTransactions: 45,
      totalAmount: 1250.50,
    },
    {
      id: '2',
      firstName: 'Jane',
      lastName: 'Smith',
      username: 'janesmith',
      avatar: 'https://i.pravatar.cc/150?img=2',
      favorite: false,
      status: 'offline',
      lastActive: new Date(Date.now() - 3600000).toISOString(),
      memberSince: '2023-03-20',
      mutualFriends: 8,
      location: 'Los Angeles, CA',
      totalTransactions: 23,
      totalAmount: 567.25,
    },
    {
      id: '3',
      firstName: 'Mike',
      lastName: 'Johnson',
      username: 'mikej',
      avatar: 'https://i.pravatar.cc/150?img=3',
      verified: true,
      status: 'away',
      lastActive: new Date(Date.now() - 1800000).toISOString(),
      memberSince: '2022-11-10',
      mutualFriends: 15,
      location: 'Chicago, IL',
      totalTransactions: 67,
      totalAmount: 2340.00,
    },
  ];

  const mockRequests: FriendRequest[] = [
    {
      id: '1',
      from: {
        id: '4',
        firstName: 'Sarah',
        lastName: 'Wilson',
        username: 'sarahw',
        avatar: 'https://i.pravatar.cc/150?img=4',
        verified: true,
        status: 'online',
      },
      timestamp: new Date(Date.now() - 86400000).toISOString(),
      message: 'Hey! Let\'s connect on Waqiti',
      status: 'pending',
    },
  ];

  const mockSuggestions: Friend[] = [
    {
      id: '5',
      firstName: 'Tom',
      lastName: 'Brown',
      username: 'tombrown',
      avatar: 'https://i.pravatar.cc/150?img=5',
      mutualFriends: 5,
      location: 'Boston, MA',
      status: 'online',
    },
    {
      id: '6',
      firstName: 'Emily',
      lastName: 'Davis',
      username: 'emilyd',
      avatar: 'https://i.pravatar.cc/150?img=6',
      verified: true,
      mutualFriends: 3,
      location: 'Seattle, WA',
      status: 'offline',
    },
  ];

  useEffect(() => {
    loadFriends();
  }, []);

  const loadFriends = async () => {
    setLoading(true);
    try {
      // Simulate API call
      await new Promise(resolve => setTimeout(resolve, 1000));
      setFriends(mockFriends);
      setRequests(mockRequests);
      setSuggestions(mockSuggestions);
    } catch (error) {
      console.error('Failed to load friends:', error);
    } finally {
      setLoading(false);
    }
  };

  const handleSearch = (query: string) => {
    setSearchQuery(query);
    // Filter friends based on search query
  };

  const handleSendMoney = (friend: Friend) => {
    navigate('/send', { state: { recipient: friend } });
  };

  const handleRequestMoney = (friend: Friend) => {
    navigate('/request', { state: { recipient: friend } });
  };

  const handleSplitBill = (friend: Friend) => {
    navigate('/split', { state: { participants: [friend] } });
  };

  const handleToggleFavorite = (friendId: string) => {
    setFriends(prev =>
      prev.map(f =>
        f.id === friendId ? { ...f, favorite: !f.favorite } : f
      )
    );
    toast.success('Favorite status updated');
  };

  const handleAcceptRequest = async (requestId: string) => {
    try {
      // Simulate API call
      await new Promise(resolve => setTimeout(resolve, 500));
      setRequests(prev => prev.filter(r => r.id !== requestId));
      toast.success('Friend request accepted');
    } catch (error) {
      toast.error('Failed to accept request');
    }
  };

  const handleRejectRequest = async (requestId: string) => {
    try {
      // Simulate API call
      await new Promise(resolve => setTimeout(resolve, 500));
      setRequests(prev => prev.filter(r => r.id !== requestId));
      toast.success('Friend request rejected');
    } catch (error) {
      toast.error('Failed to reject request');
    }
  };

  const handleAddFriend = async () => {
    if (!friendUsername.trim()) return;
    
    try {
      // Simulate API call
      await new Promise(resolve => setTimeout(resolve, 1000));
      toast.success(`Friend request sent to @${friendUsername}`);
      setAddFriendDialogOpen(false);
      setFriendUsername('');
    } catch (error) {
      toast.error('Failed to send friend request');
    }
  };

  const handleCopyUsername = () => {
    navigator.clipboard.writeText(`@${user?.username || 'username'}`);
    setCopied(true);
    setTimeout(() => setCopied(false), 2000);
  };

  const handleMenuOpen = (event: React.MouseEvent<HTMLElement>, friend: Friend) => {
    setMenuAnchor(event.currentTarget);
    setSelectedFriend(friend);
  };

  const handleMenuClose = () => {
    setMenuAnchor(null);
  };

  const handleViewDetails = () => {
    setDetailsDialogOpen(true);
    handleMenuClose();
  };

  const getStatusColor = (status?: Friend['status']) => {
    switch (status) {
      case 'online':
        return theme.palette.success.main;
      case 'away':
        return theme.palette.warning.main;
      default:
        return theme.palette.action.disabled;
    }
  };

  const filteredFriends = friends.filter(friend =>
    `${friend.firstName} ${friend.lastName} ${friend.username}`
      .toLowerCase()
      .includes(searchQuery.toLowerCase())
  );

  const favoriteFriends = filteredFriends.filter(f => f.favorite);
  const regularFriends = filteredFriends.filter(f => !f.favorite);

  const renderFriendItem = (friend: Friend) => (
    <ListItem
      key={friend.id}
      sx={{
        '&:hover': {
          bgcolor: alpha(theme.palette.primary.main, 0.05),
        },
      }}
    >
      <ListItemAvatar>
        <Badge
          overlap="circular"
          anchorOrigin={{ vertical: 'bottom', horizontal: 'right' }}
          badgeContent={
            <Box
              sx={{
                width: 12,
                height: 12,
                borderRadius: '50%',
                bgcolor: getStatusColor(friend.status),
                border: `2px solid ${theme.palette.background.paper}`,
              }}
            />
          }
        >
          <Avatar src={friend.avatar} sx={{ cursor: 'pointer' }}>
            {friend.firstName[0]}{friend.lastName[0]}
          </Avatar>
        </Badge>
      </ListItemAvatar>
      <ListItemText
        primary={
          <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
            <Typography variant="subtitle2" sx={{ fontWeight: 600 }}>
              {friend.firstName} {friend.lastName}
            </Typography>
            {friend.verified && (
              <VerifiedIcon sx={{ fontSize: 16, color: theme.palette.primary.main }} />
            )}
            {friend.muted && (
              <MuteIcon sx={{ fontSize: 16, color: theme.palette.action.disabled }} />
            )}
          </Box>
        }
        secondary={
          <Box>
            <Typography variant="caption" color="text.secondary">
              @{friend.username}
            </Typography>
            {friend.mutualFriends && friend.mutualFriends > 0 && (
              <Typography variant="caption" color="text.secondary">
                {' • '}{friend.mutualFriends} mutual friends
              </Typography>
            )}
          </Box>
        }
      />
      <ListItemSecondaryAction>
        {selectionMode ? (
          <Checkbox
            checked={selectedFriends.includes(friend.id)}
            onChange={() => onSelectFriend?.(friend)}
          />
        ) : (
          <Box sx={{ display: 'flex', gap: 1 }}>
            <Tooltip title="Send Money">
              <IconButton size="small" onClick={() => handleSendMoney(friend)}>
                <SendIcon />
              </IconButton>
            </Tooltip>
            <Tooltip title={friend.favorite ? 'Remove from favorites' : 'Add to favorites'}>
              <IconButton size="small" onClick={() => handleToggleFavorite(friend.id)}>
                {friend.favorite ? <FavoriteIcon /> : <FavoriteOutlineIcon />}
              </IconButton>
            </Tooltip>
            <IconButton size="small" onClick={(e) => handleMenuOpen(e, friend)}>
              <MoreIcon />
            </IconButton>
          </Box>
        )}
      </ListItemSecondaryAction>
    </ListItem>
  );

  const renderRequestItem = (request: FriendRequest) => (
    <ListItem key={request.id}>
      <ListItemAvatar>
        <Avatar src={request.from.avatar}>
          {request.from.firstName[0]}{request.from.lastName[0]}
        </Avatar>
      </ListItemAvatar>
      <ListItemText
        primary={
          <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
            <Typography variant="subtitle2" sx={{ fontWeight: 600 }}>
              {request.from.firstName} {request.from.lastName}
            </Typography>
            {request.from.verified && (
              <VerifiedIcon sx={{ fontSize: 16, color: theme.palette.primary.main }} />
            )}
          </Box>
        }
        secondary={
          <Box>
            <Typography variant="caption" color="text.secondary">
              @{request.from.username}
            </Typography>
            {request.message && (
              <Typography variant="body2" sx={{ mt: 0.5 }}>
                "{request.message}"
              </Typography>
            )}
          </Box>
        }
      />
      <ListItemSecondaryAction>
        <Box sx={{ display: 'flex', gap: 1 }}>
          <Button
            size="small"
            variant="contained"
            onClick={() => handleAcceptRequest(request.id)}
          >
            Accept
          </Button>
          <Button
            size="small"
            variant="outlined"
            onClick={() => handleRejectRequest(request.id)}
          >
            Decline
          </Button>
        </Box>
      </ListItemSecondaryAction>
    </ListItem>
  );

  const renderSuggestionItem = (suggestion: Friend) => (
    <Grid item xs={12} sm={6} md={4} key={suggestion.id}>
      <Card>
        <CardContent sx={{ textAlign: 'center' }}>
          <Avatar
            src={suggestion.avatar}
            sx={{ width: 80, height: 80, mx: 'auto', mb: 2 }}
          >
            {suggestion.firstName[0]}{suggestion.lastName[0]}
          </Avatar>
          <Box sx={{ display: 'flex', justifyContent: 'center', alignItems: 'center', gap: 1, mb: 1 }}>
            <Typography variant="subtitle1" sx={{ fontWeight: 600 }}>
              {suggestion.firstName} {suggestion.lastName}
            </Typography>
            {suggestion.verified && (
              <VerifiedIcon sx={{ fontSize: 18, color: theme.palette.primary.main }} />
            )}
          </Box>
          <Typography variant="body2" color="text.secondary" gutterBottom>
            @{suggestion.username}
          </Typography>
          {suggestion.mutualFriends && suggestion.mutualFriends > 0 && (
            <Chip
              icon={<MutualIcon />}
              label={`${suggestion.mutualFriends} mutual friends`}
              size="small"
              sx={{ mb: 2 }}
            />
          )}
          <Button
            fullWidth
            variant="contained"
            startIcon={<PersonAddIcon />}
            onClick={() => {/* Send friend request */}}
          >
            Add Friend
          </Button>
        </CardContent>
      </Card>
    </Grid>
  );

  return (
    <Box>
      {/* Header */}
      <Box sx={{ mb: 3 }}>
        <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 2 }}>
          <Typography variant="h5" sx={{ fontWeight: 600 }}>
            Friends
          </Typography>
          <Box sx={{ display: 'flex', gap: 1 }}>
            <Button
              variant="outlined"
              startIcon={<QrCodeIcon />}
              onClick={() => setQrDialogOpen(true)}
            >
              My QR Code
            </Button>
            <Button
              variant="contained"
              startIcon={<PersonAddIcon />}
              onClick={() => setAddFriendDialogOpen(true)}
            >
              Add Friend
            </Button>
          </Box>
        </Box>
        
        <TextField
          fullWidth
          placeholder="Search friends..."
          value={searchQuery}
          onChange={(e) => handleSearch(e.target.value)}
          InputProps={{
            startAdornment: (
              <InputAdornment position="start">
                <SearchIcon />
              </InputAdornment>
            ),
          }}
          sx={{ mb: 2 }}
        />
        
        <Tabs value={selectedTab} onChange={(_, value) => setSelectedTab(value)}>
          <Tab
            label={
              <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
                <span>All Friends</span>
                <Chip label={friends.length} size="small" />
              </Box>
            }
          />
          <Tab
            label={
              <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
                <span>Requests</span>
                {requests.length > 0 && (
                  <Chip label={requests.length} size="small" color="primary" />
                )}
              </Box>
            }
          />
          <Tab label="Suggestions" />
        </Tabs>
      </Box>
      
      {/* Content */}
      {loading ? (
        <Box>
          {[...Array(3)].map((_, index) => (
            <Box key={index} sx={{ display: 'flex', alignItems: 'center', p: 2, gap: 2 }}>
              <Skeleton variant="circular" width={56} height={56} />
              <Box sx={{ flex: 1 }}>
                <Skeleton variant="text" width="30%" />
                <Skeleton variant="text" width="20%" />
              </Box>
            </Box>
          ))}
        </Box>
      ) : (
        <>
          {selectedTab === 0 && (
            <Paper>
              <List>
                {favoriteFriends.length > 0 && (
                  <>
                    <ListItem>
                      <ListItemIcon>
                        <FavoriteIcon color="primary" />
                      </ListItemIcon>
                      <ListItemText
                        primary={
                          <Typography variant="subtitle2" color="primary">
                            Favorites
                          </Typography>
                        }
                      />
                    </ListItem>
                    <Divider />
                    {favoriteFriends.map(renderFriendItem)}
                    <Divider sx={{ my: 2 }} />
                  </>
                )}
                
                {regularFriends.length > 0 && (
                  <>
                    {regularFriends.map(renderFriendItem)}
                  </>
                )}
                
                {filteredFriends.length === 0 && (
                  <Box sx={{ textAlign: 'center', py: 8 }}>
                    <GroupIcon sx={{ fontSize: 64, color: 'text.disabled', mb: 2 }} />
                    <Typography variant="h6" color="text.secondary">
                      {searchQuery ? 'No friends found' : 'No friends yet'}
                    </Typography>
                    <Typography variant="body2" color="text.secondary" sx={{ mb: 3 }}>
                      {searchQuery ? 'Try a different search' : 'Start by adding some friends'}
                    </Typography>
                    {!searchQuery && (
                      <Button
                        variant="contained"
                        startIcon={<PersonAddIcon />}
                        onClick={() => setAddFriendDialogOpen(true)}
                      >
                        Add Friend
                      </Button>
                    )}
                  </Box>
                )}
              </List>
            </Paper>
          )}
          
          {selectedTab === 1 && (
            <Paper>
              <List>
                {requests.length > 0 ? (
                  requests.map(renderRequestItem)
                ) : (
                  <Box sx={{ textAlign: 'center', py: 8 }}>
                    <PersonAddIcon sx={{ fontSize: 64, color: 'text.disabled', mb: 2 }} />
                    <Typography variant="h6" color="text.secondary">
                      No pending requests
                    </Typography>
                  </Box>
                )}
              </List>
            </Paper>
          )}
          
          {selectedTab === 2 && (
            <Grid container spacing={2}>
              {suggestions.length > 0 ? (
                suggestions.map(renderSuggestionItem)
              ) : (
                <Grid item xs={12}>
                  <Box sx={{ textAlign: 'center', py: 8 }}>
                    <GroupIcon sx={{ fontSize: 64, color: 'text.disabled', mb: 2 }} />
                    <Typography variant="h6" color="text.secondary">
                      No suggestions available
                    </Typography>
                  </Box>
                </Grid>
              )}
            </Grid>
          )}
        </>
      )}
      
      {/* Friend Menu */}
      <Menu
        anchorEl={menuAnchor}
        open={Boolean(menuAnchor)}
        onClose={handleMenuClose}
      >
        <MenuItem onClick={handleViewDetails}>
          <ListItemIcon>
            <ProfileIcon />
          </ListItemIcon>
          <ListItemText primary="View Profile" />
        </MenuItem>
        <MenuItem onClick={() => selectedFriend && handleRequestMoney(selectedFriend)}>
          <ListItemIcon>
            <RequestIcon />
          </ListItemIcon>
          <ListItemText primary="Request Money" />
        </MenuItem>
        <MenuItem onClick={() => selectedFriend && handleSplitBill(selectedFriend)}>
          <ListItemIcon>
            <SplitIcon />
          </ListItemIcon>
          <ListItemText primary="Split Bill" />
        </MenuItem>
        <Divider />
        <MenuItem onClick={handleMenuClose}>
          <ListItemIcon>
            <MessageIcon />
          </ListItemIcon>
          <ListItemText primary="Send Message" />
        </MenuItem>
        <MenuItem onClick={handleMenuClose}>
          <ListItemIcon>
            <MuteIcon />
          </ListItemIcon>
          <ListItemText primary="Mute Notifications" />
        </MenuItem>
        <Divider />
        <MenuItem onClick={handleMenuClose} sx={{ color: 'error.main' }}>
          <ListItemIcon>
            <BlockIcon color="error" />
          </ListItemIcon>
          <ListItemText primary="Block User" />
        </MenuItem>
      </Menu>
      
      {/* Friend Details Dialog */}
      <Dialog
        open={detailsDialogOpen}
        onClose={() => setDetailsDialogOpen(false)}
        maxWidth="sm"
        fullWidth
      >
        <DialogTitle>
          <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
            <Typography variant="h6">Friend Details</Typography>
            <IconButton onClick={() => setDetailsDialogOpen(false)}>
              <CloseIcon />
            </IconButton>
          </Box>
        </DialogTitle>
        <DialogContent>
          {selectedFriend && (
            <Box>
              <Box sx={{ display: 'flex', alignItems: 'center', gap: 2, mb: 3 }}>
                <Avatar src={selectedFriend.avatar} sx={{ width: 80, height: 80 }}>
                  {selectedFriend.firstName[0]}{selectedFriend.lastName[0]}
                </Avatar>
                <Box>
                  <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
                    <Typography variant="h6">
                      {selectedFriend.firstName} {selectedFriend.lastName}
                    </Typography>
                    {selectedFriend.verified && (
                      <VerifiedIcon color="primary" />
                    )}
                  </Box>
                  <Typography variant="body2" color="text.secondary">
                    @{selectedFriend.username}
                  </Typography>
                  {selectedFriend.bio && (
                    <Typography variant="body2" sx={{ mt: 1 }}>
                      {selectedFriend.bio}
                    </Typography>
                  )}
                </Box>
              </Box>
              
              <Grid container spacing={2}>
                {selectedFriend.location && (
                  <Grid item xs={12} sm={6}>
                    <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
                      <LocationIcon color="action" />
                      <Typography variant="body2">{selectedFriend.location}</Typography>
                    </Box>
                  </Grid>
                )}
                {selectedFriend.memberSince && (
                  <Grid item xs={12} sm={6}>
                    <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
                      <CalendarIcon color="action" />
                      <Typography variant="body2">
                        Since {format(new Date(selectedFriend.memberSince), 'MMM yyyy')}
                      </Typography>
                    </Box>
                  </Grid>
                )}
              </Grid>
              
              <Divider sx={{ my: 3 }} />
              
              <Typography variant="subtitle2" sx={{ mb: 2 }}>
                Transaction History
              </Typography>
              <Grid container spacing={2}>
                <Grid item xs={6}>
                  <Card sx={{ textAlign: 'center' }}>
                    <CardContent>
                      <Typography variant="h6">
                        {selectedFriend.totalTransactions || 0}
                      </Typography>
                      <Typography variant="body2" color="text.secondary">
                        Transactions
                      </Typography>
                    </CardContent>
                  </Card>
                </Grid>
                <Grid item xs={6}>
                  <Card sx={{ textAlign: 'center' }}>
                    <CardContent>
                      <Typography variant="h6">
                        ${selectedFriend.totalAmount?.toFixed(2) || '0.00'}
                      </Typography>
                      <Typography variant="body2" color="text.secondary">
                        Total Amount
                      </Typography>
                    </CardContent>
                  </Card>
                </Grid>
              </Grid>
            </Box>
          )}
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setDetailsDialogOpen(false)}>Close</Button>
          <Button
            variant="contained"
            startIcon={<SendIcon />}
            onClick={() => selectedFriend && handleSendMoney(selectedFriend)}
          >
            Send Money
          </Button>
        </DialogActions>
      </Dialog>
      
      {/* QR Code Dialog */}
      <Dialog
        open={qrDialogOpen}
        onClose={() => setQrDialogOpen(false)}
        maxWidth="sm"
        fullWidth
      >
        <DialogTitle>My QR Code</DialogTitle>
        <DialogContent>
          <Box sx={{ textAlign: 'center', py: 3 }}>
            <QRCodeSVG
              value={`waqiti://user/${user?.username || 'username'}`}
              size={200}
              level="H"
              includeMargin
            />
            <Typography variant="h6" sx={{ mt: 3, mb: 1 }}>
              @{user?.username || 'username'}
            </Typography>
            <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
              Others can scan this code to add you as a friend
            </Typography>
            <Box sx={{ display: 'flex', gap: 1, justifyContent: 'center' }}>
              <Button
                variant="outlined"
                startIcon={copied ? <CheckIcon /> : <CopyIcon />}
                onClick={handleCopyUsername}
              >
                {copied ? 'Copied!' : 'Copy Username'}
              </Button>
              <Button
                variant="outlined"
                startIcon={<ShareIcon />}
              >
                Share
              </Button>
            </Box>
          </Box>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setQrDialogOpen(false)}>Close</Button>
        </DialogActions>
      </Dialog>
      
      {/* Add Friend Dialog */}
      <Dialog
        open={addFriendDialogOpen}
        onClose={() => setAddFriendDialogOpen(false)}
        maxWidth="sm"
        fullWidth
      >
        <DialogTitle>Add Friend</DialogTitle>
        <DialogContent>
          <Typography variant="body2" sx={{ mb: 3 }}>
            Enter your friend's username or scan their QR code
          </Typography>
          <TextField
            fullWidth
            label="Username"
            placeholder="Enter username (without @)"
            value={friendUsername}
            onChange={(e) => setFriendUsername(e.target.value)}
            InputProps={{
              startAdornment: (
                <InputAdornment position="start">@</InputAdornment>
              ),
            }}
            sx={{ mb: 2 }}
          />
          <Button
            fullWidth
            variant="outlined"
            startIcon={<QrCodeIcon />}
            sx={{ mb: 2 }}
          >
            Scan QR Code
          </Button>
          <Alert severity="info">
            You can also share your username <strong>@{user?.username || 'username'}</strong> with friends
          </Alert>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setAddFriendDialogOpen(false)}>Cancel</Button>
          <Button
            variant="contained"
            onClick={handleAddFriend}
            disabled={!friendUsername.trim()}
          >
            Send Request
          </Button>
        </DialogActions>
      </Dialog>
    </Box>
  );
};

export default FriendsList;