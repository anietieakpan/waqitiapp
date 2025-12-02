/**
 * Gallery Picker Component - Enhanced image selection with validation
 * Supports multiple selection modes and image quality validation
 */

import React, { useState } from 'react';
import {
  View,
  Text,
  StyleSheet,
  TouchableOpacity,
  Image,
  Alert,
  FlatList,
  Dimensions,
} from 'react-native';
import {
  Modal,
  Portal,
  Button,
  Card,
  Title,
  ActivityIndicator,
  Chip,
  FAB,
} from 'react-native-paper';
import { 
  launchImageLibrary, 
  launchCamera, 
  ImagePickerOptions, 
  MediaType,
  ImagePickerResponse,
  Asset,
} from 'react-native-image-picker';
import Icon from 'react-native-vector-icons/MaterialCommunityIcons';
import { check, request, PERMISSIONS, RESULTS } from 'react-native-permissions';
import { Platform } from 'react-native';

import OCRService from '../../services/OCRService';

const { width } = Dimensions.get('window');
const imageSize = (width - 48) / 2; // Account for padding and margins

export interface SelectedImage {
  id: string;
  uri: string;
  base64?: string;
  width?: number;
  height?: number;
  fileSize?: number;
  fileName?: string;
  type?: string;
  qualityScore?: number;
  validationResult?: {
    isValid: boolean;
    issues: string[];
    suggestions: string[];
  };
}

interface GalleryPickerProps {
  visible: boolean;
  onClose: () => void;
  onImagesSelected: (images: SelectedImage[]) => void;
  maxImages?: number;
  imageType?: 'any' | 'receipt' | 'check' | 'document';
  validateQuality?: boolean;
  title?: string;
  subtitle?: string;
}

const GalleryPicker: React.FC<GalleryPickerProps> = ({
  visible,
  onClose,
  onImagesSelected,
  maxImages = 2,
  imageType = 'any',
  validateQuality = true,
  title = 'Select Images',
  subtitle = 'Choose images from your gallery or take new photos',
}) => {
  const [selectedImages, setSelectedImages] = useState<SelectedImage[]>([]);
  const [loading, setLoading] = useState(false);
  const [validating, setValidating] = useState<string | null>(null);

  const checkPermissions = async (type: 'camera' | 'gallery'): Promise<boolean> => {
    try {
      const permission = type === 'camera' 
        ? Platform.OS === 'ios' ? PERMISSIONS.IOS.CAMERA : PERMISSIONS.ANDROID.CAMERA
        : Platform.OS === 'ios' ? PERMISSIONS.IOS.PHOTO_LIBRARY : PERMISSIONS.ANDROID.READ_EXTERNAL_STORAGE;

      const result = await check(permission);
      
      if (result === RESULTS.GRANTED) {
        return true;
      }

      if (result === RESULTS.DENIED) {
        const requestResult = await request(permission);
        return requestResult === RESULTS.GRANTED;
      }

      if (result === RESULTS.BLOCKED) {
        Alert.alert(
          'Permission Required',
          `Please enable ${type} permission in your device settings to use this feature.`,
          [{ text: 'OK' }]
        );
        return false;
      }

      return false;
    } catch (error) {
      console.error('Permission check failed:', error);
      return false;
    }
  };

  const validateImageQuality = async (image: SelectedImage): Promise<SelectedImage> => {
    if (!validateQuality) {
      return { ...image, qualityScore: 1.0 };
    }

    try {
      setValidating(image.id);
      
      const qualityCheck = await OCRService.checkImageQuality(
        image.base64 ? `data:image/jpeg;base64,${image.base64}` : image.uri
      );

      return {
        ...image,
        qualityScore: qualityCheck.score,
        validationResult: {
          isValid: qualityCheck.acceptable,
          issues: qualityCheck.issues,
          suggestions: qualityCheck.suggestions,
        },
      };
    } catch (error) {
      console.error('Image validation failed:', error);
      return {
        ...image,
        qualityScore: 0.5,
        validationResult: {
          isValid: false,
          issues: ['Validation failed'],
          suggestions: ['Please try again'],
        },
      };
    } finally {
      setValidating(null);
    }
  };

  const processSelectedImages = async (assets: Asset[]): Promise<void> => {
    if (assets.length === 0) return;

    setLoading(true);
    const newImages: SelectedImage[] = [];

    for (const asset of assets) {
      if (asset.uri) {
        const image: SelectedImage = {
          id: asset.uri,
          uri: asset.uri,
          base64: asset.base64,
          width: asset.width,
          height: asset.height,
          fileSize: asset.fileSize,
          fileName: asset.fileName,
          type: asset.type,
        };

        const validatedImage = await validateImageQuality(image);
        newImages.push(validatedImage);
      }
    }

    const updatedImages = [...selectedImages, ...newImages].slice(0, maxImages);
    setSelectedImages(updatedImages);
    setLoading(false);
  };

  const handleCameraCapture = async (): Promise<void> => {
    const hasPermission = await checkPermissions('camera');
    if (!hasPermission) return;

    const options: ImagePickerOptions = {
      mediaType: 'photo' as MediaType,
      quality: 0.8,
      includeBase64: true,
      maxWidth: 1920,
      maxHeight: 1080,
    };

    launchCamera(options, (response: ImagePickerResponse) => {
      if (response.assets && response.assets.length > 0) {
        processSelectedImages(response.assets);
      }
    });
  };

  const handleGallerySelection = async (): Promise<void> => {
    const hasPermission = await checkPermissions('gallery');
    if (!hasPermission) return;

    const options: ImagePickerOptions = {
      mediaType: 'photo' as MediaType,
      quality: 0.8,
      includeBase64: true,
      maxWidth: 1920,
      maxHeight: 1080,
      selectionLimit: maxImages - selectedImages.length,
    };

    launchImageLibrary(options, (response: ImagePickerResponse) => {
      if (response.assets && response.assets.length > 0) {
        processSelectedImages(response.assets);
      }
    });
  };

  const removeImage = (imageId: string): void => {
    setSelectedImages(prev => prev.filter(img => img.id !== imageId));
  };

  const handleConfirm = (): void => {
    if (selectedImages.length === 0) {
      Alert.alert('No Images Selected', 'Please select at least one image to continue.');
      return;
    }

    // Check if any images have validation issues
    const invalidImages = selectedImages.filter(
      img => img.validationResult && !img.validationResult.isValid
    );

    if (invalidImages.length > 0 && validateQuality) {
      Alert.alert(
        'Image Quality Issues',
        `${invalidImages.length} image(s) have quality issues. Continue anyway?`,
        [
          { text: 'Fix Issues', style: 'cancel' },
          { 
            text: 'Continue', 
            onPress: () => {
              onImagesSelected(selectedImages);
              handleClose();
            }
          },
        ]
      );
      return;
    }

    onImagesSelected(selectedImages);
    handleClose();
  };

  const handleClose = (): void => {
    setSelectedImages([]);
    setLoading(false);
    setValidating(null);
    onClose();
  };

  const getQualityColor = (score?: number): string => {
    if (!score) return '#757575';
    if (score >= 0.8) return '#4CAF50';
    if (score >= 0.6) return '#FF9800';
    return '#F44336';
  };

  const renderImageItem = ({ item }: { item: SelectedImage }) => (
    <Card style={styles.imageCard}>
      <View style={styles.imageContainer}>
        <Image source={{ uri: item.uri }} style={styles.selectedImage} />
        
        {/* Quality indicator */}
        {item.qualityScore !== undefined && (
          <Chip
            style={[
              styles.qualityChip,
              { backgroundColor: getQualityColor(item.qualityScore) }
            ]}
            textStyle={styles.qualityText}
          >
            {Math.round(item.qualityScore * 100)}%
          </Chip>
        )}

        {/* Loading indicator for validation */}
        {validating === item.id && (
          <View style={styles.validatingOverlay}>
            <ActivityIndicator size="small" color="white" />
          </View>
        )}

        {/* Remove button */}
        <TouchableOpacity
          style={styles.removeButton}
          onPress={() => removeImage(item.id)}
        >
          <Icon name="close-circle" size={24} color="#F44336" />
        </TouchableOpacity>

        {/* Validation issues indicator */}
        {item.validationResult && !item.validationResult.isValid && (
          <TouchableOpacity
            style={styles.warningButton}
            onPress={() => {
              Alert.alert(
                'Image Quality Issues',
                `Issues:\n${item.validationResult!.issues.join('\n')}\n\nSuggestions:\n${item.validationResult!.suggestions.join('\n')}`
              );
            }}
          >
            <Icon name="alert-circle" size={20} color="#FF9800" />
          </TouchableOpacity>
        )}
      </View>
    </Card>
  );

  const renderEmptyState = () => (
    <View style={styles.emptyState}>
      <Icon name="image-multiple" size={64} color="#ccc" />
      <Text style={styles.emptyText}>No images selected</Text>
      <Text style={styles.emptySubtext}>
        {imageType === 'check' 
          ? 'Take photos of both sides of your check'
          : imageType === 'receipt'
          ? 'Capture a clear photo of your receipt'
          : 'Select images from your gallery or camera'
        }
      </Text>
    </View>
  );

  const getImageTypeInstructions = () => {
    switch (imageType) {
      case 'check':
        return 'For best results, ensure the entire check is visible, well-lit, and placed on a flat surface.';
      case 'receipt':
        return 'Make sure all text is clearly readable and the receipt is not folded or crumpled.';
      case 'document':
        return 'Ensure the document is flat, well-lit, and all text is clearly visible.';
      default:
        return 'Select high-quality images for best processing results.';
    }
  };

  return (
    <Portal>
      <Modal visible={visible} onDismiss={handleClose} contentContainerStyle={styles.modal}>
        <Card style={styles.container}>
          <Card.Content>
            <Title>{title}</Title>
            <Text style={styles.subtitle}>{subtitle}</Text>
            
            {validateQuality && (
              <Text style={styles.instructions}>
                {getImageTypeInstructions()}
              </Text>
            )}

            <View style={styles.actionButtons}>
              <Button
                mode="outlined"
                icon="camera"
                onPress={handleCameraCapture}
                disabled={selectedImages.length >= maxImages || loading}
                style={styles.actionButton}
              >
                Camera
              </Button>
              <Button
                mode="outlined"
                icon="image-multiple"
                onPress={handleGallerySelection}
                disabled={selectedImages.length >= maxImages || loading}
                style={styles.actionButton}
              >
                Gallery
              </Button>
            </View>

            <View style={styles.selectedSection}>
              <Text style={styles.selectedHeader}>
                Selected Images ({selectedImages.length}/{maxImages})
              </Text>
              
              {selectedImages.length > 0 ? (
                <FlatList
                  data={selectedImages}
                  renderItem={renderImageItem}
                  keyExtractor={(item) => item.id}
                  numColumns={2}
                  columnWrapperStyle={styles.row}
                  style={styles.imageList}
                />
              ) : (
                renderEmptyState()
              )}
            </View>

            <View style={styles.buttonRow}>
              <Button
                mode="outlined"
                onPress={handleClose}
                style={styles.button}
              >
                Cancel
              </Button>
              <Button
                mode="contained"
                onPress={handleConfirm}
                disabled={selectedImages.length === 0 || loading}
                style={styles.button}
              >
                {loading ? <ActivityIndicator size="small" color="white" /> : 'Confirm'}
              </Button>
            </View>
          </Card.Content>
        </Card>
      </Modal>
    </Portal>
  );
};

const styles = StyleSheet.create({
  modal: {
    padding: 20,
  },
  container: {
    maxHeight: '90%',
  },
  subtitle: {
    color: '#666',
    marginBottom: 16,
  },
  instructions: {
    fontSize: 12,
    color: '#888',
    fontStyle: 'italic',
    marginBottom: 16,
  },
  actionButtons: {
    flexDirection: 'row',
    justifyContent: 'space-around',
    marginBottom: 20,
  },
  actionButton: {
    flex: 0.45,
  },
  selectedSection: {
    marginBottom: 20,
  },
  selectedHeader: {
    fontSize: 16,
    fontWeight: 'bold',
    marginBottom: 12,
  },
  imageList: {
    maxHeight: 300,
  },
  row: {
    justifyContent: 'space-between',
  },
  imageCard: {
    width: imageSize,
    marginBottom: 12,
  },
  imageContainer: {
    position: 'relative',
  },
  selectedImage: {
    width: '100%',
    height: 120,
    borderRadius: 8,
  },
  qualityChip: {
    position: 'absolute',
    top: 8,
    left: 8,
    paddingHorizontal: 6,
  },
  qualityText: {
    fontSize: 10,
    color: 'white',
  },
  validatingOverlay: {
    position: 'absolute',
    top: 0,
    left: 0,
    right: 0,
    bottom: 0,
    backgroundColor: 'rgba(0,0,0,0.5)',
    alignItems: 'center',
    justifyContent: 'center',
    borderRadius: 8,
  },
  removeButton: {
    position: 'absolute',
    top: -8,
    right: -8,
    backgroundColor: 'white',
    borderRadius: 12,
  },
  warningButton: {
    position: 'absolute',
    bottom: 8,
    left: 8,
    backgroundColor: 'white',
    borderRadius: 10,
    width: 20,
    height: 20,
    alignItems: 'center',
    justifyContent: 'center',
  },
  emptyState: {
    alignItems: 'center',
    padding: 40,
  },
  emptyText: {
    fontSize: 16,
    fontWeight: 'bold',
    color: '#666',
    marginTop: 16,
  },
  emptySubtext: {
    fontSize: 12,
    color: '#888',
    textAlign: 'center',
    marginTop: 8,
  },
  buttonRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
  },
  button: {
    flex: 0.45,
  },
});

export default GalleryPicker;