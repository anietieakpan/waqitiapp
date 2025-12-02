/**
 * PERFORMANCE MONITORING DASHBOARD
 *
 * Real-time performance metrics display using Web Vitals
 * Shows LCP, INP, CLS, FCP, TTFB and custom metrics
 *
 * Usage: Add to dev/admin panel to monitor app performance
 */

import React, { useState, useEffect } from 'react';
import {
  Box,
  Card,
  CardContent,
  Typography,
  Grid,
  Chip,
  LinearProgress,
  Tooltip,
  IconButton,
  Collapse,
} from '@mui/material';
import RefreshIcon from '@mui/icons-material/Refresh';
import ExpandMoreIcon from '@mui/icons-material/ExpandMore';
import CheckCircleIcon from '@mui/icons-material/CheckCircle';
import WarningIcon from '@mui/icons-material/Warning';
import ErrorIcon from '@mui/icons-material/Error';
import InfoIcon from '@mui/icons-material/Info';

// Import performance monitor
import { performanceMonitor } from '../../utils/performanceMonitor';

// ============================================================================
// TYPES
// ============================================================================
interface WebVitalMetric {
  name: string;
  label: string;
  value: number | null;
  rating: 'good' | 'needs-improvement' | 'poor' | 'unknown';
  threshold: {
    good: number;
    needsImprovement: number;
  };
  description: string;
}

// ============================================================================
// WEB VITALS THRESHOLDS
// ============================================================================
const WEB_VITALS_CONFIG = {
  lcp: {
    label: 'Largest Contentful Paint',
    good: 2500,
    needsImprovement: 4000,
    description: 'Time until largest content element is visible',
    unit: 'ms',
  },
  inp: {
    label: 'Interaction to Next Paint',
    good: 200,
    needsImprovement: 500,
    description: 'Responsiveness to user interactions',
    unit: 'ms',
  },
  cls: {
    label: 'Cumulative Layout Shift',
    good: 0.1,
    needsImprovement: 0.25,
    description: 'Visual stability - lower is better',
    unit: '',
  },
  fcp: {
    label: 'First Contentful Paint',
    good: 1800,
    needsImprovement: 3000,
    description: 'Time until first content is rendered',
    unit: 'ms',
  },
  ttfb: {
    label: 'Time to First Byte',
    good: 800,
    needsImprovement: 1800,
    description: 'Server response time',
    unit: 'ms',
  },
};

// ============================================================================
// PERFORMANCE DASHBOARD COMPONENT
// ============================================================================
export const PerformanceDashboard: React.FC = () => {
  const [metrics, setMetrics] = useState<Record<string, WebVitalMetric>>({});
  const [expanded, setExpanded] = useState(false);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    // Get current metrics from performance monitor
    const currentMetrics = performanceMonitor.getMetrics();

    const processedMetrics: Record<string, WebVitalMetric> = {};

    Object.entries(WEB_VITALS_CONFIG).forEach(([key, config]) => {
      const value = currentMetrics[key as keyof typeof currentMetrics] ?? null;
      let rating: WebVitalMetric['rating'] = 'unknown';

      if (value !== null) {
        if (value <= config.good) {
          rating = 'good';
        } else if (value <= config.needsImprovement) {
          rating = 'needs-improvement';
        } else {
          rating = 'poor';
        }
      }

      processedMetrics[key] = {
        name: key,
        label: config.label,
        value,
        rating,
        threshold: {
          good: config.good,
          needsImprovement: config.needsImprovement,
        },
        description: config.description,
      };
    });

    setMetrics(processedMetrics);
    setLoading(false);
  }, []);

  const handleRefresh = () => {
    setLoading(true);
    // Trigger a refresh (in real app, this would re-fetch metrics)
    window.location.reload();
  };

  const getRatingIcon = (rating: WebVitalMetric['rating']) => {
    switch (rating) {
      case 'good':
        return <CheckCircleIcon color="success" />;
      case 'needs-improvement':
        return <WarningIcon color="warning" />;
      case 'poor':
        return <ErrorIcon color="error" />;
      default:
        return <InfoIcon color="disabled" />;
    }
  };

  const getRatingColor = (rating: WebVitalMetric['rating']) => {
    switch (rating) {
      case 'good':
        return 'success';
      case 'needs-improvement':
        return 'warning';
      case 'poor':
        return 'error';
      default:
        return 'default';
    }
  };

  const getProgressValue = (metric: WebVitalMetric): number => {
    if (metric.value === null) return 0;

    const max = metric.threshold.needsImprovement * 1.5;
    return Math.min((metric.value / max) * 100, 100);
  };

  const overallScore = Object.values(metrics).reduce((score, metric) => {
    if (metric.rating === 'good') return score + 20;
    if (metric.rating === 'needs-improvement') return score + 10;
    return score;
  }, 0);

  return (
    <Card>
      <CardContent>
        <Box display="flex" justifyContent="space-between" alignItems="center" mb={2}>
          <Box>
            <Typography variant="h6" gutterBottom>
              Performance Metrics
            </Typography>
            <Typography variant="body2" color="text.secondary">
              Web Vitals - Real User Monitoring
            </Typography>
          </Box>
          <Box display="flex" gap={1} alignItems="center">
            <Chip
              label={`Score: ${overallScore}/100`}
              color={overallScore >= 80 ? 'success' : overallScore >= 50 ? 'warning' : 'error'}
              variant="filled"
            />
            <IconButton onClick={handleRefresh} size="small">
              <RefreshIcon />
            </IconButton>
            <IconButton onClick={() => setExpanded(!expanded)} size="small">
              <ExpandMoreIcon
                sx={{
                  transform: expanded ? 'rotate(180deg)' : 'rotate(0deg)',
                  transition: 'transform 0.3s',
                }}
              />
            </IconButton>
          </Box>
        </Box>

        {loading ? (
          <LinearProgress />
        ) : (
          <>
            <Grid container spacing={2}>
              {Object.values(metrics).map((metric) => (
                <Grid item xs={12} sm={6} md={4} key={metric.name}>
                  <Card variant="outlined">
                    <CardContent>
                      <Box display="flex" justifyContent="space-between" alignItems="start" mb={1}>
                        <Box flex={1}>
                          <Tooltip title={metric.description}>
                            <Typography variant="caption" color="text.secondary">
                              {metric.label}
                            </Typography>
                          </Tooltip>
                          <Typography variant="h5" fontWeight={600}>
                            {metric.value !== null
                              ? `${metric.value.toFixed(metric.name === 'cls' ? 3 : 0)}`
                              : 'N/A'}
                            <Typography component="span" variant="caption" color="text.secondary" ml={0.5}>
                              {WEB_VITALS_CONFIG[metric.name as keyof typeof WEB_VITALS_CONFIG].unit}
                            </Typography>
                          </Typography>
                        </Box>
                        {getRatingIcon(metric.rating)}
                      </Box>

                      <LinearProgress
                        variant="determinate"
                        value={getProgressValue(metric)}
                        color={
                          metric.rating === 'good'
                            ? 'success'
                            : metric.rating === 'needs-improvement'
                            ? 'warning'
                            : 'error'
                        }
                        sx={{ mb: 1 }}
                      />

                      <Box display="flex" justifyContent="space-between">
                        <Typography variant="caption" color="text.secondary">
                          Good: ≤{metric.threshold.good}
                        </Typography>
                        <Chip
                          label={metric.rating.replace('-', ' ')}
                          size="small"
                          color={getRatingColor(metric.rating) as any}
                          variant="outlined"
                        />
                      </Box>
                    </CardContent>
                  </Card>
                </Grid>
              ))}
            </Grid>

            <Collapse in={expanded}>
              <Box mt={3}>
                <Typography variant="subtitle2" gutterBottom>
                  About Web Vitals
                </Typography>
                <Typography variant="body2" color="text.secondary" paragraph>
                  Web Vitals are a set of metrics that measure real-world user experience. These metrics are
                  used by Google for search ranking and provide insight into how users experience your
                  application.
                </Typography>

                <Grid container spacing={2}>
                  <Grid item xs={12} md={6}>
                    <Typography variant="caption" fontWeight={600} color="success.main">
                      ✓ Good (0-20 points each)
                    </Typography>
                    <Typography variant="caption" display="block" color="text.secondary">
                      Optimal performance, excellent user experience
                    </Typography>
                  </Grid>
                  <Grid item xs={12} md={6}>
                    <Typography variant="caption" fontWeight={600} color="warning.main">
                      ⚠ Needs Improvement (10 points each)
                    </Typography>
                    <Typography variant="caption" display="block" color="text.secondary">
                      Acceptable but could be better
                    </Typography>
                  </Grid>
                  <Grid item xs={12} md={6}>
                    <Typography variant="caption" fontWeight={600} color="error.main">
                      ✗ Poor (0 points)
                    </Typography>
                    <Typography variant="caption" display="block" color="text.secondary">
                      Needs optimization, poor user experience
                    </Typography>
                  </Grid>
                  <Grid item xs={12} md={6}>
                    <Typography variant="caption" fontWeight={600}>
                      Overall Score
                    </Typography>
                    <Typography variant="caption" display="block" color="text.secondary">
                      80+ Excellent • 50-79 Good • 0-49 Needs Work
                    </Typography>
                  </Grid>
                </Grid>
              </Box>
            </Collapse>
          </>
        )}
      </CardContent>
    </Card>
  );
};

// ============================================================================
// USAGE EXAMPLE
// ============================================================================
/**
 * Add to developer/admin dashboard:
 *
 * import { PerformanceDashboard } from '@/components/performance/PerformanceDashboard';
 *
 * function DevDashboard() {
 *   return (
 *     <Container>
 *       <Typography variant="h4" gutterBottom>
 *         Developer Dashboard
 *       </Typography>
 *       <PerformanceDashboard />
 *     </Container>
 *   );
 * }
 *
 * Or add as a floating widget:
 *
 * function App() {
 *   const [showPerf, setShowPerf] = useState(false);
 *
 *   // Press Ctrl+Shift+P to toggle
 *   useEffect(() => {
 *     const handler = (e: KeyboardEvent) => {
 *       if (e.ctrlKey && e.shiftKey && e.key === 'P') {
 *         setShowPerf(prev => !prev);
 *       }
 *     };
 *     window.addEventListener('keydown', handler);
 *     return () => window.removeEventListener('keydown', handler);
 *   }, []);
 *
 *   return (
 *     <>
 *       {showPerf && (
 *         <Box
 *           position="fixed"
 *           bottom={16}
 *           right={16}
 *           zIndex={9999}
 *           maxWidth={600}
 *         >
 *           <PerformanceDashboard />
 *         </Box>
 *       )}
 *     </>
 *   );
 * }
 */

export default PerformanceDashboard;
