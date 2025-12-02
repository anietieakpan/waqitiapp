import React from 'react';
import QRCodeScreen from './QRCodeScreen';

// This is a wrapper/alias for QRCodeScreen with the scan tab active by default
const ScanQRScreen: React.FC = () => {
  return <QRCodeScreen />;
};

export default ScanQRScreen;