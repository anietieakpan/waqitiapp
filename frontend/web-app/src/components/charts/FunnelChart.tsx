import React from 'react';
import { Box, Typography } from '@mui/material';

export interface FunnelChartProps {
  data: Array<{ name: string; value: number }>;
  title?: string;
}

const FunnelChart: React.FC<FunnelChartProps> = ({ data, title }) => {
  return (
    <Box>
      {title && (
        <Typography variant="h6" gutterBottom>
          {title}
        </Typography>
      )}
      <Box>
        {/* Placeholder for actual funnel chart implementation */}
        <Typography variant="body2" color="text.secondary">
          Funnel Chart - {data.length} stages
        </Typography>
      </Box>
    </Box>
  );
};

export default FunnelChart;
