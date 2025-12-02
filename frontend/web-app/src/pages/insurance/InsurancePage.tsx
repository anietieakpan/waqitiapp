import React from 'react';
import {
  Container,
  Typography,
  Box,
  Button,
  Grid,
  Card,
  CardContent,
  Chip,
} from '@mui/material';
import AddIcon from '@mui/icons-material/Add';
import ShieldIcon from '@mui/icons-material/Shield';
import { formatCurrency } from '@/utils/formatters';

const InsurancePage: React.FC = () => {
  const policies = [
    {
      id: '1',
      type: 'Purchase Protection',
      provider: 'Waqiti Insurance',
      coverageAmount: 5000,
      monthlyPremium: 19.99,
      status: 'ACTIVE',
      expiryDate: '2025-12-31',
    },
  ];

  return (
    <Container maxWidth="lg" sx={{ mt: 4, mb: 4 }}>
      <Box sx={{ display: 'flex', justifyContent: 'space-between', mb: 4 }}>
        <Box>
          <Typography variant="h4">Insurance Policies</Typography>
          <Typography variant="body2" color="text.secondary">
            Manage your insurance coverage
          </Typography>
        </Box>
        <Button variant="contained" startIcon={<AddIcon />}>
          Get Coverage
        </Button>
      </Box>

      <Grid container spacing={3}>
        {policies.map((policy) => (
          <Grid item xs={12} md={6} key={policy.id}>
            <Card>
              <CardContent>
                <Box sx={{ display: 'flex', alignItems: 'center', mb: 2 }}>
                  <ShieldIcon color="primary" sx={{ mr: 1, fontSize: 40 }} />
                  <Box>
                    <Typography variant="h6">{policy.type}</Typography>
                    <Typography variant="caption" color="text.secondary">
                      {policy.provider}
                    </Typography>
                  </Box>
                </Box>

                <Chip label={policy.status} color="success" size="small" sx={{ mb: 2 }} />

                <Grid container spacing={2}>
                  <Grid item xs={6}>
                    <Typography variant="caption" color="text.secondary">
                      Coverage Amount
                    </Typography>
                    <Typography variant="h6">{formatCurrency(policy.coverageAmount)}</Typography>
                  </Grid>
                  <Grid item xs={6}>
                    <Typography variant="caption" color="text.secondary">
                      Monthly Premium
                    </Typography>
                    <Typography variant="h6">{formatCurrency(policy.monthlyPremium)}</Typography>
                  </Grid>
                  <Grid item xs={12}>
                    <Typography variant="caption" color="text.secondary">
                      Valid Until
                    </Typography>
                    <Typography variant="body2">
                      {new Date(policy.expiryDate).toLocaleDateString()}
                    </Typography>
                  </Grid>
                </Grid>

                <Button fullWidth variant="outlined" sx={{ mt: 2 }}>
                  File Claim
                </Button>
              </CardContent>
            </Card>
          </Grid>
        ))}
      </Grid>
    </Container>
  );
};

export default InsurancePage;
