import React, { useState, useCallback } from 'react';
import {
  View,
  StyleSheet,
  Text,
  Alert,
  SafeAreaView,
  TouchableOpacity,
  ScrollView,
} from 'react-native';
import { useNavigation } from '@react-navigation/native';
import { useDispatch } from 'react-redux';
import { Icon } from 'react-native-elements';
import { PhotoFile } from 'react-native-vision-camera';
import { VisionCameraScanner } from '../../components/camera/VisionCameraScanner';
import { colors } from '../../theme';
import { uploadCheckImage } from '../../services/checkDepositService';

interface CheckImages {
  front?: PhotoFile;
  back?: PhotoFile;
}

export const CheckDepositCaptureScreen: React.FC = () => {
  const navigation = useNavigation();
  const dispatch = useDispatch();
  const [checkImages, setCheckImages] = useState<CheckImages>({});
  const [currentSide, setCurrentSide] = useState<'front' | 'back'>('front');
  const [isProcessing, setIsProcessing] = useState(false);

  const handleCapture = useCallback(async (photo: PhotoFile) => {
    try {
      setIsProcessing(true);

      // Store the captured image
      setCheckImages(prev => ({
        ...prev,
        [currentSide]: photo,
      }));

      // If front side captured, switch to back
      if (currentSide === 'front') {
        Alert.alert(
          'Front Captured',
          'Please capture the back of the check',
          [
            {
              text: 'Continue',
              onPress: () => setCurrentSide('back'),
            },
          ]
        );
      } else {
        // Both sides captured, proceed to review
        navigation.navigate('CheckReview', {
          frontImage: checkImages.front,
          backImage: photo,
        });
      }
    } catch (error) {
      console.error('Capture error:', error);
      Alert.alert('Error', 'Failed to capture check image. Please try again.');
    } finally {
      setIsProcessing(false);
    }
  }, [currentSide, checkImages, navigation]);

  const retakePhoto = useCallback(() => {
    setCheckImages(prev => ({
      ...prev,
      [currentSide]: undefined,
    }));
  }, [currentSide]);

  return (
    <SafeAreaView style={styles.container}>
      <View style={styles.header}>
        <TouchableOpacity
          style={styles.backButton}
          onPress={() => navigation.goBack()}
        >
          <Icon name="arrow-back" type="material" size={24} color="white" />
        </TouchableOpacity>
        <Text style={styles.headerTitle}>
          Capture Check - {currentSide === 'front' ? 'Front' : 'Back'}
        </Text>
        <View style={styles.placeholder} />
      </View>

      <View style={styles.cameraContainer}>
        <VisionCameraScanner
          onCapture={handleCapture}
          mode="photo"
          showFrame={false}
        />

        {/* Check alignment guide */}
        <View style={styles.alignmentGuide}>
          <View style={styles.checkFrame}>
            <View style={[styles.corner, styles.topLeft]} />
            <View style={[styles.corner, styles.topRight]} />
            <View style={[styles.corner, styles.bottomLeft]} />
            <View style={[styles.corner, styles.bottomRight]} />
          </View>
        </View>
      </View>

      <View style={styles.bottomSection}>
        <View style={styles.progressIndicator}>
          <View style={[styles.progressDot, currentSide === 'front' && styles.activeDot]} />
          <View style={[styles.progressDot, currentSide === 'back' && styles.activeDot]} />
        </View>

        <Text style={styles.instructionTitle}>
          {currentSide === 'front' ? 'Front of Check' : 'Back of Check'}
        </Text>
        
        <ScrollView style={styles.instructionList}>
          <View style={styles.instructionItem}>
            <Icon name="check-circle" type="material" size={20} color={colors.success} />
            <Text style={styles.instructionText}>
              Place check on a dark, flat surface
            </Text>
          </View>
          
          <View style={styles.instructionItem}>
            <Icon name="check-circle" type="material" size={20} color={colors.success} />
            <Text style={styles.instructionText}>
              Ensure all four corners are visible
            </Text>
          </View>
          
          <View style={styles.instructionItem}>
            <Icon name="check-circle" type="material" size={20} color={colors.success} />
            <Text style={styles.instructionText}>
              {currentSide === 'front' 
                ? 'Make sure routing and account numbers are clear' 
                : 'Endorse check with signature and "For Mobile Deposit Only"'}
            </Text>
          </View>
          
          <View style={styles.instructionItem}>
            <Icon name="check-circle" type="material" size={20} color={colors.success} />
            <Text style={styles.instructionText}>
              Avoid shadows and ensure good lighting
            </Text>
          </View>
        </ScrollView>

        {checkImages[currentSide] && (
          <TouchableOpacity
            style={styles.retakeButton}
            onPress={retakePhoto}
          >
            <Icon name="refresh" type="material" size={20} color={colors.primary} />
            <Text style={styles.retakeButtonText}>Retake Photo</Text>
          </TouchableOpacity>
        )}
      </View>
    </SafeAreaView>
  );
};

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: 'black',
  },
  header: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    paddingHorizontal: 16,
    paddingVertical: 12,
    backgroundColor: 'rgba(0, 0, 0, 0.7)',
    position: 'absolute',
    top: 0,
    left: 0,
    right: 0,
    zIndex: 1,
  },
  backButton: {
    width: 40,
    height: 40,
    borderRadius: 20,
    backgroundColor: 'rgba(255, 255, 255, 0.2)',
    justifyContent: 'center',
    alignItems: 'center',
  },
  headerTitle: {
    fontSize: 18,
    fontWeight: '600',
    color: 'white',
  },
  placeholder: {
    width: 40,
  },
  cameraContainer: {
    flex: 1,
  },
  alignmentGuide: {
    ...StyleSheet.absoluteFillObject,
    justifyContent: 'center',
    alignItems: 'center',
  },
  checkFrame: {
    width: 320,
    height: 160,
    position: 'relative',
  },
  corner: {
    position: 'absolute',
    width: 40,
    height: 40,
    borderColor: colors.primary,
    borderWidth: 3,
  },
  topLeft: {
    top: 0,
    left: 0,
    borderRightWidth: 0,
    borderBottomWidth: 0,
  },
  topRight: {
    top: 0,
    right: 0,
    borderLeftWidth: 0,
    borderBottomWidth: 0,
  },
  bottomLeft: {
    bottom: 0,
    left: 0,
    borderRightWidth: 0,
    borderTopWidth: 0,
  },
  bottomRight: {
    bottom: 0,
    right: 0,
    borderLeftWidth: 0,
    borderTopWidth: 0,
  },
  bottomSection: {
    position: 'absolute',
    bottom: 0,
    left: 0,
    right: 0,
    backgroundColor: 'white',
    borderTopLeftRadius: 20,
    borderTopRightRadius: 20,
    paddingTop: 20,
    paddingBottom: 34,
    paddingHorizontal: 20,
    maxHeight: '40%',
  },
  progressIndicator: {
    flexDirection: 'row',
    justifyContent: 'center',
    marginBottom: 16,
  },
  progressDot: {
    width: 8,
    height: 8,
    borderRadius: 4,
    backgroundColor: colors.backgroundMedium,
    marginHorizontal: 4,
  },
  activeDot: {
    backgroundColor: colors.primary,
  },
  instructionTitle: {
    fontSize: 18,
    fontWeight: '600',
    color: colors.text,
    marginBottom: 12,
    textAlign: 'center',
  },
  instructionList: {
    maxHeight: 120,
  },
  instructionItem: {
    flexDirection: 'row',
    alignItems: 'center',
    marginBottom: 8,
  },
  instructionText: {
    marginLeft: 8,
    fontSize: 14,
    color: colors.textSecondary,
    flex: 1,
  },
  retakeButton: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    marginTop: 16,
    paddingVertical: 8,
  },
  retakeButtonText: {
    marginLeft: 8,
    fontSize: 16,
    color: colors.primary,
    fontWeight: '500',
  },
});