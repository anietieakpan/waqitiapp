/**
 * SecuritySettingsScreen - Main security settings screen
 * Integrates the BiometricSecuritySettings component with navigation
 */

import React from 'react';
import {
  SafeAreaView,
  StyleSheet,
  ScrollView,
} from 'react-native';
import { Appbar } from 'react-native-paper';
import { useNavigation } from '@react-navigation/native';
import { BiometricSecuritySettings } from '../../components/settings';

export const SecuritySettingsScreen: React.FC = () => {
  const navigation = useNavigation();

  const handleNavigateToSetup = () => {
    // Navigate to biometric setup screen
    navigation.navigate('BiometricSetup' as never);
  };

  const handleNavigateToDeviceManagement = () => {
    // Navigate to device management screen
    navigation.navigate('DeviceManagement' as never);
  };

  return (
    <SafeAreaView style={styles.container}>
      <Appbar.Header>
        <Appbar.BackAction onPress={() => navigation.goBack()} />
        <Appbar.Content title="Security Settings" />
      </Appbar.Header>
      
      <BiometricSecuritySettings
        onNavigateToSetup={handleNavigateToSetup}
        onNavigateToDeviceManagement={handleNavigateToDeviceManagement}
      />
    </SafeAreaView>
  );
};

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#f5f5f5',
  },
});

export default SecuritySettingsScreen;