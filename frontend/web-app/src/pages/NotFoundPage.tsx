import React from 'react';
import { useNavigate } from 'react-router-dom';
import {
  Container,
  Box,
  Typography,
  Button,
  Paper,
} from '@mui/material';
import HomeIcon from '@mui/icons-material/Home';
import ArrowBackIcon from '@mui/icons-material/ArrowBack';;

const NotFoundPage: React.FC = () => {
  const navigate = useNavigate();

  return (
    <Container maxWidth="sm">
      <Box
        display="flex"
        flexDirection="column"
        justifyContent="center"
        alignItems="center"
        minHeight="100vh"
      >
        <Paper
          elevation={0}
          sx={{
            p: 4,
            textAlign: 'center',
            backgroundColor: 'transparent',
          }}
        >
          <Typography
            variant="h1"
            component="h1"
            sx={{
              fontSize: '8rem',
              fontWeight: 'bold',
              color: 'primary.main',
              mb: 2,
            }}
          >
            404
          </Typography>
          
          <Typography
            variant="h4"
            component="h2"
            gutterBottom
            sx={{ mb: 2 }}
          >
            Page Not Found
          </Typography>
          
          <Typography
            variant="body1"
            color="text.secondary"
            sx={{ mb: 4 }}
          >
            The page you're looking for doesn't exist or has been moved.
          </Typography>
          
          <Box
            display="flex"
            gap={2}
            justifyContent="center"
            flexWrap="wrap"
          >
            <Button
              variant="contained"
              startIcon={<HomeIcon />}
              onClick={() => navigate('/dashboard')}
              size="large"
            >
              Go to Dashboard
            </Button>
            
            <Button
              variant="outlined"
              startIcon={<ArrowBackIcon />}
              onClick={() => navigate(-1)}
              size="large"
            >
              Go Back
            </Button>
          </Box>
        </Paper>
      </Box>
    </Container>
  );
};

export default NotFoundPage;