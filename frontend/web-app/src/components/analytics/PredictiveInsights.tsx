import React, { useState, useEffect } from 'react';
import {
  Box,
  Grid,
  Card,
  CardContent,
  Typography,
  Alert,
  Chip,
  LinearProgress,
  Button,
  FormControl,
  InputLabel,
  Select,
  MenuItem,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  Paper,
  Tooltip,
  IconButton,
  useTheme,
} from '@mui/material';
import TrendingUpIcon from '@mui/icons-material/TrendingUp';
import TrendingDownIcon from '@mui/icons-material/TrendingDown';
import WarningIcon from '@mui/icons-material/Warning';
import InfoIcon from '@mui/icons-material/Info';
import LightbulbIcon from '@mui/icons-material/Lightbulb';
import PsychologyIcon from '@mui/icons-material/Psychology';
import TimelineIcon from '@mui/icons-material/Timeline';
import SpeedIcon from '@mui/icons-material/Speed';
import AssessmentIcon from '@mui/icons-material/Assessment';
import RefreshIcon from '@mui/icons-material/Refresh';;
import {
  LineChart,
  Line,
  AreaChart,
  Area,
  BarChart,
  Bar,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip as ChartTooltip,
  Legend,
  ResponsiveContainer,
  ScatterChart,
  Scatter,
  RadarChart,
  PolarGrid,
  PolarAngleAxis,
  PolarRadiusAxis,
  Radar,
} from 'recharts';
import { useDispatch, useSelector } from 'react-redux';
import { formatCurrency, formatNumber, formatPercentage } from '../../utils/formatters';

interface PredictiveInsightsProps {
  predictions?: PredictionData;
  timeRange?: string;
  refreshing?: boolean;
  onRefresh?: () => void;
}

interface PredictionData {
  shortTerm: ShortTermPredictions;
  longTerm: LongTermPredictions;
  anomalies: AnomalyPrediction[];
  recommendations: Recommendation[];
  confidence: ConfidenceMetrics;
  trends: TrendPrediction[];
  riskAssessment: RiskPrediction;
  seasonality: SeasonalityAnalysis;
}

interface ShortTermPredictions {
  nextHour: {
    transactionVolume: number;
    revenue: number;
    userActivity: number;
    confidence: number;
  };
  next24Hours: {
    transactionVolume: number;
    revenue: number;
    userActivity: number;
    confidence: number;
  };
  nextWeek: {
    transactionVolume: number;
    revenue: number;
    userActivity: number;
    confidence: number;
  };
}

interface LongTermPredictions {
  nextMonth: MonthlyPrediction;
  nextQuarter: QuarterlyPrediction;
  nextYear: YearlyPrediction;
}

interface MonthlyPrediction {
  revenue: number;
  userGrowth: number;
  marketExpansion: number;
  confidence: number;
}

interface QuarterlyPrediction {
  revenue: number;
  userGrowth: number;
  marketExpansion: number;
  confidence: number;
}

interface YearlyPrediction {
  revenue: number;
  userGrowth: number;
  marketExpansion: number;
  confidence: number;
}

interface AnomalyPrediction {
  type: string;
  probability: number;
  severity: 'low' | 'medium' | 'high' | 'critical';
  timeWindow: string;
  description: string;
  mitigation: string[];
}

interface Recommendation {
  category: string;
  priority: 'low' | 'medium' | 'high' | 'critical';
  title: string;
  description: string;
  impact: string;
  effort: string;
  roi: number;
  implementation: string[];
}

interface ConfidenceMetrics {
  overall: number;
  shortTerm: number;
  longTerm: number;
  factorsAffecting: string[];
}

interface TrendPrediction {
  metric: string;
  direction: 'up' | 'down' | 'stable';
  magnitude: number;
  timeline: string;
  drivers: string[];
}

interface RiskPrediction {
  overall: number;
  categories: {
    fraud: number;
    operational: number;
    market: number;
    regulatory: number;
  };
  mitigation: string[];
}

interface SeasonalityAnalysis {
  patterns: SeasonalPattern[];
  upcomingEvents: SeasonalEvent[];
  recommendations: string[];
}

interface SeasonalPattern {
  period: string;
  impact: number;
  description: string;
}

interface SeasonalEvent {
  event: string;
  expectedImpact: number;
  timeframe: string;
  preparation: string[];
}

const PredictiveInsights: React.FC<PredictiveInsightsProps> = ({
  predictions,
  timeRange = '7d',
  refreshing = false,
  onRefresh
}) => {
  const theme = useTheme();
  const [selectedTimeframe, setSelectedTimeframe] = useState('short');
  const [selectedCategory, setSelectedCategory] = useState('all');

  // Mock data for demonstration
  const mockPredictions: PredictionData = {
    shortTerm: {
      nextHour: { transactionVolume: 1250, revenue: 125000, userActivity: 850, confidence: 0.92 },
      next24Hours: { transactionVolume: 28500, revenue: 2850000, userActivity: 18500, confidence: 0.87 },
      nextWeek: { transactionVolume: 185000, revenue: 18500000, userActivity: 125000, confidence: 0.78 }
    },
    longTerm: {
      nextMonth: { revenue: 75000000, userGrowth: 0.12, marketExpansion: 0.08, confidence: 0.72 },
      nextQuarter: { revenue: 235000000, userGrowth: 0.18, marketExpansion: 0.15, confidence: 0.68 },
      nextYear: { revenue: 980000000, userGrowth: 0.25, marketExpansion: 0.22, confidence: 0.62 }
    },
    anomalies: [
      {
        type: 'Volume Spike',
        probability: 0.15,
        severity: 'medium',
        timeWindow: 'Next 3 days',
        description: 'Predicted transaction volume spike due to upcoming holiday',
        mitigation: ['Scale infrastructure', 'Increase monitoring', 'Prepare customer support']
      },
      {
        type: 'Fraud Pattern',
        probability: 0.08,
        severity: 'high',
        timeWindow: 'Next 24 hours',
        description: 'Elevated fraud risk in mobile channel',
        mitigation: ['Enhanced screening', 'Real-time monitoring', 'User verification']
      }
    ],
    recommendations: [
      {
        category: 'Infrastructure',
        priority: 'high',
        title: 'Scale Payment Processing Capacity',
        description: 'Increase processing capacity to handle predicted 40% volume growth',
        impact: 'High',
        effort: 'Medium',
        roi: 3.2,
        implementation: ['Add 3 processing nodes', 'Configure auto-scaling', 'Update load balancers']
      },
      {
        category: 'Marketing',
        priority: 'medium',
        title: 'Target High-Value User Segments',
        description: 'Focus marketing on segments with 85% higher transaction values',
        impact: 'Medium',
        effort: 'Low',
        roi: 2.8,
        implementation: ['Create targeted campaigns', 'Update user segmentation', 'A/B test messaging']
      }
    ],
    confidence: {
      overall: 0.81,
      shortTerm: 0.87,
      longTerm: 0.68,
      factorsAffecting: ['Historical data quality', 'Market volatility', 'Seasonal variations']
    },
    trends: [
      {
        metric: 'Mobile Transactions',
        direction: 'up',
        magnitude: 0.25,
        timeline: '3 months',
        drivers: ['Mobile app improvements', 'QR code adoption', 'User preference shift']
      },
      {
        metric: 'International Volume',
        direction: 'up',
        magnitude: 0.18,
        timeline: '6 months',
        drivers: ['Market expansion', 'Partnership growth', 'Regulatory improvements']
      }
    ],
    riskAssessment: {
      overall: 0.23,
      categories: {
        fraud: 0.15,
        operational: 0.28,
        market: 0.32,
        regulatory: 0.18
      },
      mitigation: ['Enhanced fraud detection', 'Infrastructure redundancy', 'Market diversification']
    },
    seasonality: {
      patterns: [
        { period: 'Holiday Season', impact: 1.45, description: 'Nov-Dec transaction surge' },
        { period: 'Back to School', impact: 1.22, description: 'Aug-Sep payment increase' },
        { period: 'Tax Season', impact: 0.88, description: 'Apr transaction dip' }
      ],
      upcomingEvents: [
        {
          event: 'Black Friday',
          expectedImpact: 2.1,
          timeframe: '2 weeks',
          preparation: ['Scale infrastructure', 'Increase support staff', 'Monitor fraud']
        }
      ],
      recommendations: ['Prepare for holiday scaling', 'Optimize fraud detection', 'Plan marketing campaigns']
    }
  };

  const currentPredictions = predictions || mockPredictions;

  const renderShortTermPredictions = () => (
    <Grid container spacing={3}>
      <Grid item xs={12} md={4}>
        <Card>
          <CardContent>
            <Box display="flex" alignItems="center" mb={2}>
              <SpeedIcon color="primary" sx={{ mr: 1 }} />
              <Typography variant="h6">Next Hour</Typography>
              <Chip 
                label={`${(currentPredictions.shortTerm.nextHour.confidence * 100).toFixed(0)}% confident`}
                size="small"
                color="success"
                sx={{ ml: 'auto' }}
              />
            </Box>
            <Box mb={2}>
              <Typography variant="body2" color="textSecondary">Transaction Volume</Typography>
              <Typography variant="h4">{formatNumber(currentPredictions.shortTerm.nextHour.transactionVolume)}</Typography>
            </Box>
            <Box mb={2}>
              <Typography variant="body2" color="textSecondary">Revenue</Typography>
              <Typography variant="h4">{formatCurrency(currentPredictions.shortTerm.nextHour.revenue)}</Typography>
            </Box>
            <Box>
              <Typography variant="body2" color="textSecondary">User Activity</Typography>
              <Typography variant="h4">{formatNumber(currentPredictions.shortTerm.nextHour.userActivity)}</Typography>
            </Box>
          </CardContent>
        </Card>
      </Grid>

      <Grid item xs={12} md={4}>
        <Card>
          <CardContent>
            <Box display="flex" alignItems="center" mb={2}>
              <TimelineIcon color="primary" sx={{ mr: 1 }} />
              <Typography variant="h6">Next 24 Hours</Typography>
              <Chip 
                label={`${(currentPredictions.shortTerm.next24Hours.confidence * 100).toFixed(0)}% confident`}
                size="small"
                color="warning"
                sx={{ ml: 'auto' }}
              />
            </Box>
            <Box mb={2}>
              <Typography variant="body2" color="textSecondary">Transaction Volume</Typography>
              <Typography variant="h4">{formatNumber(currentPredictions.shortTerm.next24Hours.transactionVolume)}</Typography>
            </Box>
            <Box mb={2}>
              <Typography variant="body2" color="textSecondary">Revenue</Typography>
              <Typography variant="h4">{formatCurrency(currentPredictions.shortTerm.next24Hours.revenue)}</Typography>
            </Box>
            <Box>
              <Typography variant="body2" color="textSecondary">User Activity</Typography>
              <Typography variant="h4">{formatNumber(currentPredictions.shortTerm.next24Hours.userActivity)}</Typography>
            </Box>
          </CardContent>
        </Card>
      </Grid>

      <Grid item xs={12} md={4}>
        <Card>
          <CardContent>
            <Box display="flex" alignItems="center" mb={2}>
              <AssessmentIcon color="primary" sx={{ mr: 1 }} />
              <Typography variant="h6">Next Week</Typography>
              <Chip 
                label={`${(currentPredictions.shortTerm.nextWeek.confidence * 100).toFixed(0)}% confident`}
                size="small"
                color="info"
                sx={{ ml: 'auto' }}
              />
            </Box>
            <Box mb={2}>
              <Typography variant="body2" color="textSecondary">Transaction Volume</Typography>
              <Typography variant="h4">{formatNumber(currentPredictions.shortTerm.nextWeek.transactionVolume)}</Typography>
            </Box>
            <Box mb={2}>
              <Typography variant="body2" color="textSecondary">Revenue</Typography>
              <Typography variant="h4">{formatCurrency(currentPredictions.shortTerm.nextWeek.revenue)}</Typography>
            </Box>
            <Box>
              <Typography variant="body2" color="textSecondary">User Activity</Typography>
              <Typography variant="h4">{formatNumber(currentPredictions.shortTerm.nextWeek.userActivity)}</Typography>
            </Box>
          </CardContent>
        </Card>
      </Grid>
    </Grid>
  );

  const renderLongTermPredictions = () => (
    <Grid container spacing={3}>
      <Grid item xs={12} md={6}>
        <Card>
          <CardContent>
            <Typography variant="h6" gutterBottom>Long-term Growth Projections</Typography>
            <ResponsiveContainer width="100%" height={300}>
              <AreaChart data={[
                { period: 'Current', revenue: 65000000, users: 125000 },
                { period: 'Next Month', revenue: currentPredictions.longTerm.nextMonth.revenue, users: 140000 },
                { period: 'Next Quarter', revenue: currentPredictions.longTerm.nextQuarter.revenue, users: 185000 },
                { period: 'Next Year', revenue: currentPredictions.longTerm.nextYear.revenue, users: 310000 }
              ]}>
                <CartesianGrid strokeDasharray="3 3" />
                <XAxis dataKey="period" />
                <YAxis yAxisId="left" />
                <YAxis yAxisId="right" orientation="right" />
                <ChartTooltip 
                  formatter={(value, name) => [
                    name === 'revenue' ? formatCurrency(value) : formatNumber(value),
                    name === 'revenue' ? 'Revenue' : 'Users'
                  ]}
                />
                <Area yAxisId="left" type="monotone" dataKey="revenue" stackId="1" stroke="#8884d8" fill="#8884d8" />
                <Area yAxisId="right" type="monotone" dataKey="users" stackId="2" stroke="#82ca9d" fill="#82ca9d" />
              </AreaChart>
            </ResponsiveContainer>
          </CardContent>
        </Card>
      </Grid>

      <Grid item xs={12} md={6}>
        <Card>
          <CardContent>
            <Typography variant="h6" gutterBottom>Confidence Levels</Typography>
            <ResponsiveContainer width="100%" height={300}>
              <RadarChart data={[
                {
                  metric: 'Overall',
                  confidence: currentPredictions.confidence.overall * 100,
                },
                {
                  metric: 'Short Term',
                  confidence: currentPredictions.confidence.shortTerm * 100,
                },
                {
                  metric: 'Long Term',
                  confidence: currentPredictions.confidence.longTerm * 100,
                },
                {
                  metric: 'Market',
                  confidence: 75,
                },
                {
                  metric: 'Technology',
                  confidence: 88,
                },
                {
                  metric: 'User Behavior',
                  confidence: 82,
                }
              ]}>
                <PolarGrid />
                <PolarAngleAxis dataKey="metric" />
                <PolarRadiusAxis domain={[0, 100]} />
                <Radar name="Confidence %" dataKey="confidence" stroke="#8884d8" fill="#8884d8" fillOpacity={0.3} />
              </RadarChart>
            </ResponsiveContainer>
          </CardContent>
        </Card>
      </Grid>
    </Grid>
  );

  const renderAnomalies = () => (
    <Card>
      <CardContent>
        <Typography variant="h6" gutterBottom>Predicted Anomalies</Typography>
        <TableContainer>
          <Table>
            <TableHead>
              <TableRow>
                <TableCell>Type</TableCell>
                <TableCell>Probability</TableCell>
                <TableCell>Severity</TableCell>
                <TableCell>Time Window</TableCell>
                <TableCell>Mitigation</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {currentPredictions.anomalies.map((anomaly, index) => (
                <TableRow key={index}>
                  <TableCell>
                    <Box display="flex" alignItems="center">
                      <WarningIcon 
                        color={anomaly.severity === 'high' ? 'error' : 'warning'} 
                        sx={{ mr: 1 }} 
                      />
                      <Box>
                        <Typography variant="subtitle2">{anomaly.type}</Typography>
                        <Typography variant="body2" color="textSecondary">
                          {anomaly.description}
                        </Typography>
                      </Box>
                    </Box>
                  </TableCell>
                  <TableCell>
                    <Box>
                      <Typography variant="body2">{formatPercentage(anomaly.probability)}</Typography>
                      <LinearProgress 
                        variant="determinate" 
                        value={anomaly.probability * 100} 
                        sx={{ width: 80 }}
                      />
                    </Box>
                  </TableCell>
                  <TableCell>
                    <Chip 
                      label={anomaly.severity}
                      color={
                        anomaly.severity === 'critical' ? 'error' :
                        anomaly.severity === 'high' ? 'warning' :
                        anomaly.severity === 'medium' ? 'info' : 'default'
                      }
                      size="small"
                    />
                  </TableCell>
                  <TableCell>{anomaly.timeWindow}</TableCell>
                  <TableCell>
                    <Box>
                      {anomaly.mitigation.slice(0, 2).map((action, idx) => (
                        <Chip 
                          key={idx}
                          label={action}
                          size="small"
                          variant="outlined"
                          sx={{ mr: 0.5, mb: 0.5 }}
                        />
                      ))}
                      {anomaly.mitigation.length > 2 && (
                        <Chip 
                          label={`+${anomaly.mitigation.length - 2} more`}
                          size="small"
                          variant="outlined"
                        />
                      )}
                    </Box>
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </TableContainer>
      </CardContent>
    </Card>
  );

  const renderRecommendations = () => (
    <Card>
      <CardContent>
        <Typography variant="h6" gutterBottom>AI Recommendations</Typography>
        <Grid container spacing={2}>
          {currentPredictions.recommendations.map((rec, index) => (
            <Grid item xs={12} md={6} key={index}>
              <Box 
                p={2} 
                border={1} 
                borderColor="divider" 
                borderRadius={1}
                bgcolor={theme.palette.action.hover}
              >
                <Box display="flex" alignItems="center" mb={1}>
                  <LightbulbIcon color="primary" sx={{ mr: 1 }} />
                  <Typography variant="subtitle1">{rec.title}</Typography>
                  <Chip 
                    label={rec.priority}
                    color={
                      rec.priority === 'critical' ? 'error' :
                      rec.priority === 'high' ? 'warning' :
                      rec.priority === 'medium' ? 'info' : 'default'
                    }
                    size="small"
                    sx={{ ml: 'auto' }}
                  />
                </Box>
                <Typography variant="body2" color="textSecondary" mb={2}>
                  {rec.description}
                </Typography>
                <Box display="flex" justifyContent="space-between" mb={1}>
                  <Typography variant="caption">Impact: {rec.impact}</Typography>
                  <Typography variant="caption">Effort: {rec.effort}</Typography>
                  <Typography variant="caption">ROI: {rec.roi}x</Typography>
                </Box>
                <Box>
                  <Typography variant="caption" display="block" mb={1}>Implementation:</Typography>
                  {rec.implementation.slice(0, 2).map((step, idx) => (
                    <Typography key={idx} variant="caption" display="block" color="textSecondary">
                      • {step}
                    </Typography>
                  ))}
                </Box>
              </Box>
            </Grid>
          ))}
        </Grid>
      </CardContent>
    </Card>
  );

  return (
    <Box>
      {/* Header Controls */}
      <Box display="flex" justifyContent="space-between" alignItems="center" mb={3}>
        <Typography variant="h5">Predictive Insights</Typography>
        <Box display="flex" gap={2}>
          <FormControl size="small" sx={{ minWidth: 150 }}>
            <InputLabel>Timeframe</InputLabel>
            <Select
              value={selectedTimeframe}
              onChange={(e) => setSelectedTimeframe(e.target.value)}
              label="Timeframe"
            >
              <MenuItem value="short">Short Term</MenuItem>
              <MenuItem value="long">Long Term</MenuItem>
              <MenuItem value="both">Both</MenuItem>
            </Select>
          </FormControl>
          <FormControl size="small" sx={{ minWidth: 120 }}>
            <InputLabel>Category</InputLabel>
            <Select
              value={selectedCategory}
              onChange={(e) => setSelectedCategory(e.target.value)}
              label="Category"
            >
              <MenuItem value="all">All</MenuItem>
              <MenuItem value="revenue">Revenue</MenuItem>
              <MenuItem value="users">Users</MenuItem>
              <MenuItem value="risk">Risk</MenuItem>
            </Select>
          </FormControl>
          <Tooltip title="Refresh Predictions">
            <IconButton onClick={onRefresh} disabled={refreshing}>
              <RefreshIcon />
            </IconButton>
          </Tooltip>
        </Box>
      </Box>

      {/* Confidence Overview */}
      <Alert 
        severity="info" 
        icon={<PsychologyIcon />}
        sx={{ mb: 3 }}
      >
        <Typography variant="subtitle2">
          Overall Prediction Confidence: {formatPercentage(currentPredictions.confidence.overall)}
        </Typography>
        <Typography variant="body2">
          Factors affecting accuracy: {currentPredictions.confidence.factorsAffecting.join(', ')}
        </Typography>
      </Alert>

      {/* Short Term Predictions */}
      {(selectedTimeframe === 'short' || selectedTimeframe === 'both') && (
        <Box mb={4}>
          <Typography variant="h6" gutterBottom>Short-term Predictions</Typography>
          {renderShortTermPredictions()}
        </Box>
      )}

      {/* Long Term Predictions */}
      {(selectedTimeframe === 'long' || selectedTimeframe === 'both') && (
        <Box mb={4}>
          <Typography variant="h6" gutterBottom>Long-term Forecasts</Typography>
          {renderLongTermPredictions()}
        </Box>
      )}

      {/* Anomaly Predictions */}
      <Box mb={4}>
        <Typography variant="h6" gutterBottom>Anomaly Detection</Typography>
        {renderAnomalies()}
      </Box>

      {/* AI Recommendations */}
      <Box mb={4}>
        <Typography variant="h6" gutterBottom>Strategic Recommendations</Typography>
        {renderRecommendations()}
      </Box>

      {/* Risk Assessment */}
      <Grid container spacing={3}>
        <Grid item xs={12} md={6}>
          <Card>
            <CardContent>
              <Typography variant="h6" gutterBottom>Risk Assessment</Typography>
              <ResponsiveContainer width="100%" height={250}>
                <BarChart data={[
                  { category: 'Fraud', risk: currentPredictions.riskAssessment.categories.fraud * 100 },
                  { category: 'Operational', risk: currentPredictions.riskAssessment.categories.operational * 100 },
                  { category: 'Market', risk: currentPredictions.riskAssessment.categories.market * 100 },
                  { category: 'Regulatory', risk: currentPredictions.riskAssessment.categories.regulatory * 100 }
                ]}>
                  <CartesianGrid strokeDasharray="3 3" />
                  <XAxis dataKey="category" />
                  <YAxis />
                  <ChartTooltip formatter={(value) => `${value}%`} />
                  <Bar dataKey="risk" fill="#ff7c7c" />
                </BarChart>
              </ResponsiveContainer>
            </CardContent>
          </Card>
        </Grid>

        <Grid item xs={12} md={6}>
          <Card>
            <CardContent>
              <Typography variant="h6" gutterBottom>Seasonal Patterns</Typography>
              {currentPredictions.seasonality.patterns.map((pattern, index) => (
                <Box key={index} mb={2}>
                  <Box display="flex" justifyContent="space-between" alignItems="center">
                    <Typography variant="subtitle2">{pattern.period}</Typography>
                    <Chip 
                      label={`${(pattern.impact * 100 - 100).toFixed(0)}%`}
                      color={pattern.impact > 1 ? 'success' : 'warning'}
                      size="small"
                    />
                  </Box>
                  <Typography variant="body2" color="textSecondary">
                    {pattern.description}
                  </Typography>
                  <LinearProgress 
                    variant="determinate" 
                    value={pattern.impact * 50} // Normalize for display
                    sx={{ mt: 1 }}
                  />
                </Box>
              ))}
            </CardContent>
          </Card>
        </Grid>
      </Grid>
    </Box>
  );
};

export default PredictiveInsights;