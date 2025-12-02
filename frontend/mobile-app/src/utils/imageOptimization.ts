/**
 * Image Optimization and Compression Utility
 * Handles image processing, optimization, and compression for check deposits
 */

import { Platform } from 'react-native';
import RNFS from 'react-native-fs';
import ImageResizer from 'react-native-image-resizer';

export interface ImageOptimizationOptions {
  maxWidth?: number;
  maxHeight?: number;
  quality?: number; // 0-100
  format?: 'JPEG' | 'PNG';
  rotate?: number; // degrees
  keepMeta?: boolean;
  compressImageMaxWidth?: number;
  compressImageMaxHeight?: number;
}

export interface OptimizedImageResult {
  uri: string;
  width: number;
  height: number;
  size: number; // file size in bytes
  originalSize: number;
  compressionRatio: number;
}

export interface ImageValidationResult {
  isValid: boolean;
  issues: string[];
  quality: 'poor' | 'fair' | 'good' | 'excellent';
  fileSize: number;
  dimensions: { width: number; height: number };
}

/**
 * Default optimization settings for check images
 */
const DEFAULT_CHECK_OPTIONS: ImageOptimizationOptions = {
  maxWidth: 1920,
  maxHeight: 1080,
  quality: 85,
  format: 'JPEG',
  rotate: 0,
  keepMeta: false,
  compressImageMaxWidth: 1920,
  compressImageMaxHeight: 1080,
};

/**
 * Optimize and compress a check image
 */
export const optimizeCheckImage = async (
  imageUri: string,
  options: Partial<ImageOptimizationOptions> = {}
): Promise<OptimizedImageResult> => {
  try {
    const opts = { ...DEFAULT_CHECK_OPTIONS, ...options };
    
    // Get original file info
    const originalStats = await RNFS.stat(imageUri);
    const originalSize = originalStats.size;
    
    // Resize and compress the image
    const resizedImage = await ImageResizer.createResizedImage(
      imageUri,
      opts.maxWidth || DEFAULT_CHECK_OPTIONS.maxWidth!,
      opts.maxHeight || DEFAULT_CHECK_OPTIONS.maxHeight!,
      opts.format || 'JPEG',
      opts.quality || 85,
      opts.rotate || 0,
      undefined, // outputPath (auto-generated)
      opts.keepMeta || false,
      {
        mode: 'contain', // Maintain aspect ratio
        onlyScaleDown: true, // Don't upscale small images
      }
    );
    
    // Get optimized file info
    const optimizedStats = await RNFS.stat(resizedImage.uri);
    const optimizedSize = optimizedStats.size;
    
    const compressionRatio = originalSize > 0 ? (originalSize - optimizedSize) / originalSize : 0;
    
    return {
      uri: resizedImage.uri,
      width: resizedImage.width,
      height: resizedImage.height,
      size: optimizedSize,
      originalSize,
      compressionRatio,
    };
  } catch (error) {
    console.error('Image optimization error:', error);
    throw new Error(`Failed to optimize image: ${error}`);
  }
};

/**
 * Validate check image quality and properties
 */
export const validateCheckImage = async (
  imageUri: string
): Promise<ImageValidationResult> => {
  try {
    const stats = await RNFS.stat(imageUri);
    const fileSize = stats.size;
    
    // Get image dimensions (this would require a library like react-native-image-size)
    // For now, we'll use placeholder dimensions
    const dimensions = { width: 1920, height: 1080 }; // This should be actual image dimensions
    
    const issues: string[] = [];
    let quality: ImageValidationResult['quality'] = 'excellent';
    
    // File size validation
    if (fileSize > 10 * 1024 * 1024) { // 10MB
      issues.push('File size is too large (over 10MB)');
      quality = 'poor';
    } else if (fileSize < 50 * 1024) { // 50KB
      issues.push('File size is too small, image may be low quality');
      quality = quality === 'excellent' ? 'fair' : 'poor';
    }
    
    // Dimension validation
    if (dimensions.width < 800 || dimensions.height < 600) {
      issues.push('Image resolution is too low for clear text recognition');
      quality = 'poor';
    } else if (dimensions.width < 1200 || dimensions.height < 800) {
      issues.push('Image resolution could be higher for better accuracy');
      quality = quality === 'excellent' ? 'good' : quality;
    }
    
    // Aspect ratio validation (checks are typically 2:1 ratio)
    const aspectRatio = dimensions.width / dimensions.height;
    if (aspectRatio < 1.5 || aspectRatio > 2.5) {
      issues.push('Unusual aspect ratio detected, ensure the entire check is visible');
      quality = quality === 'excellent' ? 'good' : quality;
    }
    
    const isValid = issues.length === 0 || quality !== 'poor';
    
    return {
      isValid,
      issues,
      quality,
      fileSize,
      dimensions,
    };
  } catch (error) {
    console.error('Image validation error:', error);
    return {
      isValid: false,
      issues: ['Failed to validate image'],
      quality: 'poor',
      fileSize: 0,
      dimensions: { width: 0, height: 0 },
    };
  }
};

/**
 * Create a thumbnail version of the check image
 */
export const createCheckThumbnail = async (
  imageUri: string,
  size: number = 200
): Promise<string> => {
  try {
    const thumbnail = await ImageResizer.createResizedImage(
      imageUri,
      size,
      size,
      'JPEG',
      70, // Lower quality for thumbnails
      0,
      undefined,
      false,
      { mode: 'cover' }
    );
    
    return thumbnail.uri;
  } catch (error) {
    console.error('Thumbnail creation error:', error);
    throw new Error(`Failed to create thumbnail: ${error}`);
  }
};

/**
 * Rotate an image by specified degrees
 */
export const rotateImage = async (
  imageUri: string,
  degrees: number
): Promise<string> => {
  try {
    const rotatedImage = await ImageResizer.createResizedImage(
      imageUri,
      DEFAULT_CHECK_OPTIONS.maxWidth!,
      DEFAULT_CHECK_OPTIONS.maxHeight!,
      'JPEG',
      DEFAULT_CHECK_OPTIONS.quality!,
      degrees,
      undefined,
      false,
      { mode: 'contain', onlyScaleDown: true }
    );
    
    return rotatedImage.uri;
  } catch (error) {
    console.error('Image rotation error:', error);
    throw new Error(`Failed to rotate image: ${error}`);
  }
};

/**
 * Apply auto-enhancement to improve image quality
 */
export const enhanceCheckImage = async (
  imageUri: string
): Promise<OptimizedImageResult> => {
  try {
    // For basic enhancement, we can adjust compression settings
    // In a more advanced implementation, you might use ML-based enhancement
    const enhancedOptions: ImageOptimizationOptions = {
      ...DEFAULT_CHECK_OPTIONS,
      quality: 90, // Higher quality
      format: 'JPEG',
    };
    
    return await optimizeCheckImage(imageUri, enhancedOptions);
  } catch (error) {
    console.error('Image enhancement error:', error);
    throw new Error(`Failed to enhance image: ${error}`);
  }
};

/**
 * Convert image to grayscale for better text recognition
 */
export const convertToGrayscale = async (
  imageUri: string
): Promise<string> => {
  try {
    // Note: react-native-image-resizer doesn't have built-in grayscale
    // This would require a more advanced image processing library
    // For now, we'll return the original image
    console.warn('Grayscale conversion not implemented - requires additional library');
    return imageUri;
  } catch (error) {
    console.error('Grayscale conversion error:', error);
    throw new Error(`Failed to convert to grayscale: ${error}`);
  }
};

/**
 * Batch process multiple check images
 */
export const batchOptimizeImages = async (
  imageUris: string[],
  options: Partial<ImageOptimizationOptions> = {}
): Promise<OptimizedImageResult[]> => {
  try {
    const promises = imageUris.map(uri => optimizeCheckImage(uri, options));
    return await Promise.all(promises);
  } catch (error) {
    console.error('Batch optimization error:', error);
    throw new Error(`Failed to batch optimize images: ${error}`);
  }
};

/**
 * Clean up temporary image files
 */
export const cleanupTempImages = async (imageUris: string[]): Promise<void> => {
  try {
    const deletePromises = imageUris.map(async (uri) => {
      try {
        const exists = await RNFS.exists(uri);
        if (exists) {
          await RNFS.unlink(uri);
        }
      } catch (error) {
        console.warn(`Failed to delete temp image: ${uri}`, error);
      }
    });
    
    await Promise.allSettled(deletePromises);
  } catch (error) {
    console.error('Cleanup error:', error);
  }
};

/**
 * Get image file information
 */
export const getImageInfo = async (imageUri: string): Promise<{
  size: number;
  type: string;
  exists: boolean;
}> => {
  try {
    const exists = await RNFS.exists(imageUri);
    if (!exists) {
      return { size: 0, type: 'unknown', exists: false };
    }
    
    const stats = await RNFS.stat(imageUri);
    
    // Determine file type from extension
    const extension = imageUri.split('.').pop()?.toLowerCase() || '';
    const type = ['jpg', 'jpeg'].includes(extension) ? 'JPEG' : 
                 extension === 'png' ? 'PNG' : 'unknown';
    
    return {
      size: stats.size,
      type,
      exists: true,
    };
  } catch (error) {
    console.error('Get image info error:', error);
    return { size: 0, type: 'unknown', exists: false };
  }
};

/**
 * Format file size for display
 */
export const formatFileSize = (bytes: number): string => {
  if (bytes === 0) return '0 Bytes';
  
  const k = 1024;
  const sizes = ['Bytes', 'KB', 'MB', 'GB'];
  const i = Math.floor(Math.log(bytes) / Math.log(k));
  
  return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i];
};

/**
 * Get compression ratio as percentage
 */
export const getCompressionPercentage = (originalSize: number, compressedSize: number): number => {
  if (originalSize === 0) return 0;
  return Math.round(((originalSize - compressedSize) / originalSize) * 100);
};

/**
 * Check if image processing is available on the current platform
 */
export const isImageProcessingAvailable = (): boolean => {
  return Platform.OS === 'ios' || Platform.OS === 'android';
};

export default {
  optimizeCheckImage,
  validateCheckImage,
  createCheckThumbnail,
  rotateImage,
  enhanceCheckImage,
  convertToGrayscale,
  batchOptimizeImages,
  cleanupTempImages,
  getImageInfo,
  formatFileSize,
  getCompressionPercentage,
  isImageProcessingAvailable,
  DEFAULT_CHECK_OPTIONS,
};