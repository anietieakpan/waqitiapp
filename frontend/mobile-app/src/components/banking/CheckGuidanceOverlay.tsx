/**
 * CheckGuidanceOverlay - Camera overlay with guidelines for check capture
 * Provides visual guidance and instructions for proper check positioning
 */

import React, { useEffect, useRef } from 'react';
import {
  View,
  Text,
  StyleSheet,
  Dimensions,
  Animated,
  TouchableOpacity,
} from 'react-native';
import { useTheme } from 'react-native-paper';
import Icon from 'react-native-vector-icons/MaterialCommunityIcons';

interface CheckGuidanceOverlayProps {
  side: 'front' | 'back';
  onClose?: () => void;
  showCloseButton?: boolean;
}

const { width: screenWidth, height: screenHeight } = Dimensions.get('window');

// Check frame dimensions (aspect ratio 2:1 for checks)
const CHECK_ASPECT_RATIO = 2.0;
const FRAME_WIDTH = screenWidth * 0.85;
const FRAME_HEIGHT = FRAME_WIDTH / CHECK_ASPECT_RATIO;

/**
 * Check Guidance Overlay Component
 */
const CheckGuidanceOverlay: React.FC<CheckGuidanceOverlayProps> = ({
  side,
  onClose,
  showCloseButton = true,
}) => {
  const theme = useTheme();
  
  // Animation refs
  const fadeAnim = useRef(new Animated.Value(0)).current;
  const scaleAnim = useRef(new Animated.Value(0.9)).current;
  const cornerAnim = useRef(new Animated.Value(0)).current;
  
  // Start animations
  useEffect(() => {
    Animated.parallel([
      Animated.timing(fadeAnim, {
        toValue: 1,
        duration: 300,
        useNativeDriver: true,
      }),
      Animated.spring(scaleAnim, {
        toValue: 1,
        tension: 50,
        friction: 8,
        useNativeDriver: true,
      }),
      Animated.loop(
        Animated.sequence([
          Animated.timing(cornerAnim, {
            toValue: 1,
            duration: 1000,
            useNativeDriver: true,
          }),
          Animated.timing(cornerAnim, {
            toValue: 0,
            duration: 1000,
            useNativeDriver: true,
          }),
        ])
      ),
    ]).start();
  }, [fadeAnim, scaleAnim, cornerAnim]);
  
  // Get guidance text based on side
  const getGuidanceText = () => {
    if (side === 'front') {
      return {
        title: 'Position Check Front',
        instructions: [
          'Place the front of your check within the frame',
          'Ensure all four corners are visible',
          'Make sure the text is clear and readable',
          'Avoid shadows and glare',
        ],
      };
    } else {
      return {
        title: 'Position Check Back',
        instructions: [
          'Flip your check and position the back side',
          'Include your endorsement signature',
          'Ensure all endorsements are visible',
          'Keep the check flat and steady',
        ],
      };
    }
  };
  
  const guidance = getGuidanceText();
  
  // Corner indicators
  const renderCornerIndicator = (position: 'topLeft' | 'topRight' | 'bottomLeft' | 'bottomRight') => {
    const cornerStyles = {
      topLeft: { top: 0, left: 0 },
      topRight: { top: 0, right: 0 },
      bottomLeft: { bottom: 0, left: 0 },
      bottomRight: { bottom: 0, right: 0 },
    };
    
    return (
      <Animated.View
        style={[
          styles.cornerIndicator,
          cornerStyles[position],
          {
            opacity: cornerAnim,
            transform: [{ scale: cornerAnim }],
          },
        ]}
      >
        <View style={styles.cornerLines}>
          {(position === 'topLeft' || position === 'topRight') && (
            <View style={[styles.cornerLine, styles.cornerLineHorizontalTop]} />
          )}
          {(position === 'bottomLeft' || position === 'bottomRight') && (
            <View style={[styles.cornerLine, styles.cornerLineHorizontalBottom]} />
          )}
          {(position === 'topLeft' || position === 'bottomLeft') && (
            <View style={[styles.cornerLine, styles.cornerLineVerticalLeft]} />
          )}
          {(position === 'topRight' || position === 'bottomRight') && (
            <View style={[styles.cornerLine, styles.cornerLineVerticalRight]} />
          )}
        </View>
      </Animated.View>
    );
  };
  
  return (
    <Animated.View
      style={[
        styles.overlay,
        {
          opacity: fadeAnim,
        },
      ]}
    >
      {/* Semi-transparent background */}
      <View style={styles.background} />
      
      {/* Close button */}
      {showCloseButton && onClose && (
        <TouchableOpacity style={styles.closeButton} onPress={onClose}>
          <Icon name="close" size={24} color="white" />
        </TouchableOpacity>
      )}
      
      {/* Check frame area */}
      <View style={styles.frameContainer}>
        {/* Top overlay */}
        <View style={[styles.overlaySection, styles.topOverlay]} />
        
        {/* Middle section with frame */}
        <View style={styles.middleSection}>
          {/* Left overlay */}
          <View style={[styles.overlaySection, styles.sideOverlay]} />
          
          {/* Check frame */}
          <Animated.View
            style={[
              styles.checkFrame,
              {
                transform: [{ scale: scaleAnim }],
              },
            ]}
          >
            {/* Frame border */}
            <View style={styles.frameBorder} />
            
            {/* Corner indicators */}
            {renderCornerIndicator('topLeft')}
            {renderCornerIndicator('topRight')}
            {renderCornerIndicator('bottomLeft')}
            {renderCornerIndicator('bottomRight')}
            
            {/* Center crosshair */}
            <View style={styles.crosshair}>
              <View style={styles.crosshairHorizontal} />
              <View style={styles.crosshairVertical} />
            </View>
            
            {/* Side indicator */}
            <View style={styles.sideIndicator}>
              <Text style={styles.sideIndicatorText}>
                {side.toUpperCase()}
              </Text>
            </View>
          </Animated.View>
          
          {/* Right overlay */}
          <View style={[styles.overlaySection, styles.sideOverlay]} />
        </View>
        
        {/* Bottom overlay */}
        <View style={[styles.overlaySection, styles.bottomOverlay]} />
      </View>
      
      {/* Guidance text */}
      <Animated.View
        style={[
          styles.guidanceContainer,
          {
            opacity: fadeAnim,
            transform: [{ translateY: Animated.multiply(fadeAnim, -20) }],
          },
        ]}
      >
        <Text style={styles.guidanceTitle}>{guidance.title}</Text>
        <View style={styles.instructionsList}>
          {guidance.instructions.map((instruction, index) => (
            <View key={index} style={styles.instructionItem}>
              <View style={styles.bulletPoint} />
              <Text style={styles.instructionText}>{instruction}</Text>
            </View>
          ))}
        </View>
        
        {/* Tips */}
        <View style={styles.tipsContainer}>
          <Icon name="lightbulb-outline" size={16} color="#FFA500" />
          <Text style={styles.tipsText}>
            Tap anywhere to dismiss this guide
          </Text>
        </View>
      </Animated.View>
      
      {/* Tap to dismiss */}
      {onClose && (
        <TouchableOpacity
          style={styles.dismissArea}
          onPress={onClose}
          activeOpacity={1}
        />
      )}
    </Animated.View>
  );
};

const styles = StyleSheet.create({
  overlay: {
    position: 'absolute',
    top: 0,
    left: 0,
    right: 0,
    bottom: 0,
    zIndex: 1000,
  },
  background: {
    position: 'absolute',
    top: 0,
    left: 0,
    right: 0,
    bottom: 0,
    backgroundColor: 'rgba(0, 0, 0, 0.7)',
  },
  closeButton: {
    position: 'absolute',
    top: 50,
    right: 20,
    width: 44,
    height: 44,
    borderRadius: 22,
    backgroundColor: 'rgba(0, 0, 0, 0.5)',
    justifyContent: 'center',
    alignItems: 'center',
    zIndex: 10,
  },
  frameContainer: {
    flex: 1,
    justifyContent: 'center',
    alignItems: 'center',
  },
  overlaySection: {
    backgroundColor: 'rgba(0, 0, 0, 0.7)',
  },
  topOverlay: {
    height: (screenHeight - FRAME_HEIGHT) / 2 - 60,
    width: screenWidth,
  },
  bottomOverlay: {
    height: (screenHeight - FRAME_HEIGHT) / 2 - 60,
    width: screenWidth,
  },
  middleSection: {
    flexDirection: 'row',
    height: FRAME_HEIGHT,
    width: screenWidth,
  },
  sideOverlay: {
    flex: 1,
  },
  checkFrame: {
    width: FRAME_WIDTH,
    height: FRAME_HEIGHT,
    position: 'relative',
    justifyContent: 'center',
    alignItems: 'center',
  },
  frameBorder: {
    position: 'absolute',
    top: 0,
    left: 0,
    right: 0,
    bottom: 0,
    borderWidth: 2,
    borderColor: '#4CAF50',
    borderRadius: 8,
  },
  cornerIndicator: {
    position: 'absolute',
    width: 30,
    height: 30,
  },
  cornerLines: {
    flex: 1,
    position: 'relative',
  },
  cornerLine: {
    position: 'absolute',
    backgroundColor: '#4CAF50',
  },
  cornerLineHorizontalTop: {
    top: 0,
    left: 0,
    width: 20,
    height: 3,
  },
  cornerLineHorizontalBottom: {
    bottom: 0,
    left: 0,
    width: 20,
    height: 3,
  },
  cornerLineVerticalLeft: {
    top: 0,
    left: 0,
    width: 3,
    height: 20,
  },
  cornerLineVerticalRight: {
    top: 0,
    right: 0,
    width: 3,
    height: 20,
  },
  crosshair: {
    position: 'absolute',
    justifyContent: 'center',
    alignItems: 'center',
  },
  crosshairHorizontal: {
    width: 40,
    height: 2,
    backgroundColor: 'rgba(255, 255, 255, 0.7)',
  },
  crosshairVertical: {
    position: 'absolute',
    width: 2,
    height: 40,
    backgroundColor: 'rgba(255, 255, 255, 0.7)',
  },
  sideIndicator: {
    position: 'absolute',
    top: 10,
    right: 10,
    backgroundColor: 'rgba(76, 175, 80, 0.9)',
    paddingHorizontal: 8,
    paddingVertical: 4,
    borderRadius: 4,
  },
  sideIndicatorText: {
    color: 'white',
    fontSize: 12,
    fontWeight: 'bold',
    letterSpacing: 1,
  },
  guidanceContainer: {
    position: 'absolute',
    bottom: 120,
    left: 20,
    right: 20,
    backgroundColor: 'rgba(0, 0, 0, 0.8)',
    borderRadius: 12,
    padding: 16,
  },
  guidanceTitle: {
    color: 'white',
    fontSize: 18,
    fontWeight: 'bold',
    textAlign: 'center',
    marginBottom: 12,
  },
  instructionsList: {
    marginBottom: 12,
  },
  instructionItem: {
    flexDirection: 'row',
    alignItems: 'flex-start',
    marginBottom: 8,
  },
  bulletPoint: {
    width: 6,
    height: 6,
    borderRadius: 3,
    backgroundColor: '#4CAF50',
    marginTop: 6,
    marginRight: 12,
  },
  instructionText: {
    color: 'white',
    fontSize: 14,
    flex: 1,
    lineHeight: 20,
  },
  tipsContainer: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    paddingTop: 8,
    borderTopWidth: 1,
    borderTopColor: 'rgba(255, 255, 255, 0.2)',
  },
  tipsText: {
    color: 'rgba(255, 255, 255, 0.8)',
    fontSize: 12,
    marginLeft: 6,
  },
  dismissArea: {
    position: 'absolute',
    top: 0,
    left: 0,
    right: 0,
    bottom: 0,
    zIndex: -1,
  },
});

export default CheckGuidanceOverlay;