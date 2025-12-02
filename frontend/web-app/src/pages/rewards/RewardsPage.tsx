import React from 'react';
import {
  Container,
  Typography,
  Box,
  Card,
  CardContent,
  Grid,
  Button,
  List,
  ListItem,
  ListItemText,
  Chip,
} from '@mui/material';
import StarIcon from '@mui/icons-material/Star';
import EmojiEventsIcon from '@mui/icons-material/EmojiEvents';

const RewardsPage: React.FC = () => {
  const pointsBalance = 12450;
  const tier = 'Gold';

  const recentActivities = [
    { id: '1', description: 'Payment to Amazon', points: 50, date: '2025-11-23' },
    { id: '2', description: 'Bill payment', points: 25, date: '2025-11-22' },
  ];

  const redeemOptions = [
    { id: '1', name: 'Cash Back', points: 5000, value: '$50' },
    { id: '2', name: 'Gift Card', points: 10000, value: '$100' },
  ];

  return (
    <Container maxWidth="lg" sx={{ mt: 4, mb: 4 }}>
      <Typography variant="h4" gutterBottom>
        Rewards & Points
      </Typography>

      <Grid container spacing={3}>
        <Grid item xs={12} md={6}>
          <Card sx={{ background: 'linear-gradient(135deg, #f093fb 0%, #f5576c 100%)', color: 'white' }}>
            <CardContent>
              <Box sx={{ display: 'flex', alignItems: 'center', mb: 2 }}>
                <StarIcon sx={{ fontSize: 40, mr: 1 }} />
                <Typography variant="h6">Your Points</Typography>
              </Box>
              <Typography variant="h3">{pointsBalance.toLocaleString()}</Typography>
              <Chip label={`${tier} Member`} sx={{ mt: 2, bgcolor: 'rgba(255,255,255,0.3)', color: 'white' }} />
            </CardContent>
          </Card>
        </Grid>

        <Grid item xs={12} md={6}>
          <Card>
            <CardContent>
              <Typography variant="h6" gutterBottom>
                Recent Activity
              </Typography>
              <List>
                {recentActivities.map((activity) => (
                  <ListItem key={activity.id}>
                    <ListItemText
                      primary={activity.description}
                      secondary={activity.date}
                    />
                    <Chip label={`+${activity.points} pts`} color="success" size="small" />
                  </ListItem>
                ))}
              </List>
            </CardContent>
          </Card>
        </Grid>

        <Grid item xs={12}>
          <Card>
            <CardContent>
              <Typography variant="h6" gutterBottom>
                Redeem Points
              </Typography>
              <Grid container spacing={2}>
                {redeemOptions.map((option) => (
                  <Grid item xs={12} sm={6} md={4} key={option.id}>
                    <Card variant="outlined">
                      <CardContent>
                        <Typography variant="h6">{option.name}</Typography>
                        <Typography variant="body2" color="text.secondary">
                          {option.points.toLocaleString()} points = {option.value}
                        </Typography>
                        <Button fullWidth variant="contained" sx={{ mt: 2 }}>
                          Redeem
                        </Button>
                      </CardContent>
                    </Card>
                  </Grid>
                ))}
              </Grid>
            </CardContent>
          </Card>
        </Grid>
      </Grid>
    </Container>
  );
};

export default RewardsPage;
