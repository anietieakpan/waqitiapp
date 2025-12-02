import React from 'react';
import {
  Box,
  Container,
  Paper,
  Typography,
  useTheme,
  useMediaQuery,
  Grid,
} from '@mui/material';
import { styled } from '@mui/material/styles';

const BackgroundBox = styled(Box)(({ theme }) => ({
  minHeight: '100vh',
  background: `linear-gradient(135deg, ${theme.palette.primary.main} 0%, ${theme.palette.primary.dark} 100%)`,
  display: 'flex',
  alignItems: 'center',
  justifyContent: 'center',
  padding: theme.spacing(2),
}));

const FormPaper = styled(Paper)(({ theme }) => ({
  padding: theme.spacing(4),
  borderRadius: theme.spacing(2),
  boxShadow: '0 8px 32px rgba(0, 0, 0, 0.1)',
  backdropFilter: 'blur(10px)',
  border: '1px solid rgba(255, 255, 255, 0.2)',
  width: '100%',
  maxWidth: 400,
}));

const LogoBox = styled(Box)(({ theme }) => ({
  textAlign: 'center',
  marginBottom: theme.spacing(3),
}));

const FeatureBox = styled(Box)(({ theme }) => ({
  padding: theme.spacing(3),
  textAlign: 'center',
  color: 'white',
  '& .MuiTypography-root': {
    color: 'white',
  },
}));

interface AuthLayoutProps {
  children: React.ReactNode;
  title: string;
  subtitle?: string;
}

const AuthLayout: React.FC<AuthLayoutProps> = ({ children, title, subtitle }) => {
  const theme = useTheme();
  const isMobile = useMediaQuery(theme.breakpoints.down('md'));

  const features = [
    {
      title: 'Secure Payments',
      description: 'Bank-grade security with end-to-end encryption for all your transactions.',
    },
    {
      title: 'Instant Transfers',
      description: 'Send money to friends and family instantly with just their phone number.',
    },
    {
      title: 'Multi-Currency',
      description: 'Support for multiple currencies with real-time exchange rates.',
    },
  ];

  if (isMobile) {
    return (
      <BackgroundBox>
        <Container maxWidth="sm">
          <FormPaper elevation={3}>
            <LogoBox>
              <Typography
                variant="h4"
                component="h1"
                fontWeight="bold"
                color="primary"
                gutterBottom
              >
                Waqiti
              </Typography>
              <Typography variant="h5" component="h2" gutterBottom>
                {title}
              </Typography>
              {subtitle && (
                <Typography variant="body2" color="text.secondary">
                  {subtitle}
                </Typography>
              )}
            </LogoBox>
            {children}
          </FormPaper>
        </Container>
      </BackgroundBox>
    );
  }

  return (
    <BackgroundBox>
      <Container maxWidth="lg">
        <Grid container spacing={4} alignItems="center">
          {/* Left side - Features */}
          <Grid item xs={12} md={6}>
            <Box sx={{ pl: { md: 4 } }}>
              <Typography
                variant="h2"
                component="h1"
                fontWeight="bold"
                color="white"
                gutterBottom
              >
                Waqiti
              </Typography>
              <Typography
                variant="h5"
                color="white"
                sx={{ mb: 4, opacity: 0.9 }}
              >
                The future of digital payments
              </Typography>
              
              <Box sx={{ mt: 4 }}>
                {features.map((feature, index) => (
                  <FeatureBox key={index}>
                    <Typography variant="h6" gutterBottom>
                      {feature.title}
                    </Typography>
                    <Typography variant="body1" sx={{ opacity: 0.8 }}>
                      {feature.description}
                    </Typography>
                  </FeatureBox>
                ))}
              </Box>
            </Box>
          </Grid>
          
          {/* Right side - Form */}
          <Grid item xs={12} md={6}>
            <Box sx={{ display: 'flex', justifyContent: 'center' }}>
              <FormPaper elevation={3}>
                <LogoBox>
                  <Typography
                    variant="h4"
                    component="h1"
                    fontWeight="bold"
                    color="primary"
                    gutterBottom
                  >
                    Waqiti
                  </Typography>
                  <Typography variant="h5" component="h2" gutterBottom>
                    {title}
                  </Typography>
                  {subtitle && (
                    <Typography variant="body2" color="text.secondary">
                      {subtitle}
                    </Typography>
                  )}
                </LogoBox>
                {children}
              </FormPaper>
            </Box>
          </Grid>
        </Grid>
      </Container>
    </BackgroundBox>
  );
};

export default AuthLayout;