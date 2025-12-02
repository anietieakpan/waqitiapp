import React, { useState } from 'react';
import { Card, CardContent, Typography, Box, ToggleButtonGroup, ToggleButton } from '@mui/material';
import {
  LineChart,
  Line,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
  ResponsiveContainer,
  Legend,
} from 'recharts';
import { formatCurrency } from '@/utils/formatters';

interface PerformanceDataPoint {
  date: string;
  value: number;
}

interface PerformanceChartProps {
  data: PerformanceDataPoint[];
}

type TimeRange = '1D' | '1W' | '1M' | '3M' | '1Y' | 'ALL';

const PerformanceChart: React.FC<PerformanceChartProps> = ({ data }) => {
  const [timeRange, setTimeRange] = useState<TimeRange>('1M');

  const handleTimeRangeChange = (
    event: React.MouseEvent<HTMLElement>,
    newTimeRange: TimeRange | null
  ) => {
    if (newTimeRange !== null) {
      setTimeRange(newTimeRange);
    }
  };

  // Filter data based on time range
  const filterDataByTimeRange = (data: PerformanceDataPoint[], range: TimeRange) => {
    const now = new Date();
    let cutoffDate = new Date();

    switch (range) {
      case '1D':
        cutoffDate.setDate(now.getDate() - 1);
        break;
      case '1W':
        cutoffDate.setDate(now.getDate() - 7);
        break;
      case '1M':
        cutoffDate.setMonth(now.getMonth() - 1);
        break;
      case '3M':
        cutoffDate.setMonth(now.getMonth() - 3);
        break;
      case '1Y':
        cutoffDate.setFullYear(now.getFullYear() - 1);
        break;
      case 'ALL':
        return data;
    }

    return data.filter((point) => new Date(point.date) >= cutoffDate);
  };

  const filteredData = filterDataByTimeRange(data, timeRange);

  if (!data || data.length === 0) {
    return (
      <Card>
        <CardContent>
          <Typography color="text.secondary">No performance data available</Typography>
        </CardContent>
      </Card>
    );
  }

  return (
    <Card>
      <CardContent>
        <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 3 }}>
          <Typography variant="h6">Performance</Typography>
          <ToggleButtonGroup
            value={timeRange}
            exclusive
            onChange={handleTimeRangeChange}
            aria-label="time range"
            size="small"
          >
            <ToggleButton value="1D" aria-label="1 day">
              1D
            </ToggleButton>
            <ToggleButton value="1W" aria-label="1 week">
              1W
            </ToggleButton>
            <ToggleButton value="1M" aria-label="1 month">
              1M
            </ToggleButton>
            <ToggleButton value="3M" aria-label="3 months">
              3M
            </ToggleButton>
            <ToggleButton value="1Y" aria-label="1 year">
              1Y
            </ToggleButton>
            <ToggleButton value="ALL" aria-label="all time">
              ALL
            </ToggleButton>
          </ToggleButtonGroup>
        </Box>

        <ResponsiveContainer width="100%" height={400}>
          <LineChart data={filteredData}>
            <CartesianGrid strokeDasharray="3 3" />
            <XAxis
              dataKey="date"
              tickFormatter={(value) => new Date(value).toLocaleDateString()}
            />
            <YAxis tickFormatter={(value) => formatCurrency(value)} />
            <Tooltip
              labelFormatter={(value) => new Date(value).toLocaleString()}
              formatter={(value: number) => [formatCurrency(value), 'Portfolio Value']}
            />
            <Legend />
            <Line
              type="monotone"
              dataKey="value"
              stroke="#8884d8"
              strokeWidth={2}
              dot={false}
              name="Portfolio Value"
            />
          </LineChart>
        </ResponsiveContainer>
      </CardContent>
    </Card>
  );
};

export default PerformanceChart;
