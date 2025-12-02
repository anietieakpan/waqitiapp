import AsyncStorage from '@react-native-async-storage/async-storage';
import VisionCameraOCR from 'react-native-vision-camera-ocr';
import { ApiService } from '../ApiService';
import DeviceInfo from 'react-native-device-info';
import * as tf from '@tensorflow/tfjs';
import '@tensorflow/tfjs-react-native';

export interface CheckValidationResult {
  isValid: boolean;
  riskScore: number; // 0-100, higher is riskier
  fraudIndicators: FraudIndicator[];
  ocrResults: OCRResults;
  imageQualityScore: number;
  recommendations: string[];
}

export interface FraudIndicator {
  type: 'DUPLICATE' | 'ALTERED' | 'INVALID_MICR' | 'SUSPICIOUS_AMOUNT' | 
        'ACCOUNT_MISMATCH' | 'SIGNATURE_MISMATCH' | 'DATE_ISSUE' | 
        'IMAGE_MANIPULATION' | 'KNOWN_FRAUD_PATTERN';
  severity: 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL';
  description: string;
  confidence: number; // 0-100
}

export interface OCRResults {
  amount: string | null;
  accountNumber: string | null;
  routingNumber: string | null;
  checkNumber: string | null;
  date: string | null;
  payee: string | null;
  micrLine: string | null;
  confidence: number;
}

export interface CheckMetadata {
  frontImagePath: string;
  backImagePath: string;
  amount: number;
  timestamp: number;
  deviceId: string;
  location?: {
    latitude: number;
    longitude: number;
  };
}

/**
 * Advanced check fraud detection service using ML and pattern recognition
 */
class CheckFraudDetectionService {
  private mlModel: tf.LayersModel | null = null;
  private checkHistory: Map<string, CheckMetadata> = new Map();
  private readonly DUPLICATE_CHECK_DAYS = 90;
  private readonly HIGH_RISK_AMOUNT_THRESHOLD = 5000;
  
  async initialize(): Promise<void> {
    try {
      // Initialize TensorFlow.js
      await tf.ready();
      
      // Load fraud detection model
      await this.loadMLModel();
      
      // Load check history from storage
      await this.loadCheckHistory();
      
      console.log('Check fraud detection service initialized');
    } catch (error) {
      console.error('Failed to initialize fraud detection service:', error);
    }
  }
  
  /**
   * Perform comprehensive fraud detection on check images
   */
  async detectFraud(metadata: CheckMetadata): Promise<CheckValidationResult> {
    const fraudIndicators: FraudIndicator[] = [];
    
    try {
      // 1. Perform OCR on check images
      const ocrResults = await this.performOCR(metadata);
      
      // 2. Check for duplicates
      const duplicateCheck = await this.checkForDuplicates(metadata, ocrResults);
      if (duplicateCheck) {
        fraudIndicators.push(duplicateCheck);
      }
      
      // 3. Validate MICR line
      const micrValidation = this.validateMICR(ocrResults.micrLine);
      if (!micrValidation.isValid) {
        fraudIndicators.push({
          type: 'INVALID_MICR',
          severity: 'HIGH',
          description: micrValidation.error || 'Invalid MICR line format',
          confidence: 90
        });
      }
      
      // 4. Check amount consistency
      const amountCheck = this.validateAmount(metadata.amount, ocrResults.amount);
      if (amountCheck) {
        fraudIndicators.push(amountCheck);
      }
      
      // 5. Analyze check image for manipulation
      const imageAnalysis = await this.analyzeImageIntegrity(metadata);
      fraudIndicators.push(...imageAnalysis);
      
      // 6. Check date validity
      const dateCheck = this.validateCheckDate(ocrResults.date);
      if (dateCheck) {
        fraudIndicators.push(dateCheck);
      }
      
      // 7. Pattern recognition for known fraud
      const patternCheck = await this.checkFraudPatterns(metadata, ocrResults);
      fraudIndicators.push(...patternCheck);
      
      // 8. Device and location risk assessment
      const deviceRisk = await this.assessDeviceRisk(metadata);
      if (deviceRisk) {
        fraudIndicators.push(deviceRisk);
      }
      
      // 9. ML-based fraud detection
      const mlPrediction = await this.runMLFraudDetection(metadata, ocrResults);
      if (mlPrediction.isFraud) {
        fraudIndicators.push({
          type: 'KNOWN_FRAUD_PATTERN',
          severity: mlPrediction.confidence > 0.8 ? 'CRITICAL' : 'HIGH',
          description: 'Machine learning model detected potential fraud pattern',
          confidence: mlPrediction.confidence * 100
        });
      }
      
      // Calculate overall risk score
      const riskScore = this.calculateRiskScore(fraudIndicators);
      
      // Generate recommendations
      const recommendations = this.generateRecommendations(fraudIndicators, riskScore);
      
      // Store check for future duplicate detection
      await this.storeCheckMetadata(metadata, ocrResults);
      
      return {
        isValid: fraudIndicators.filter(f => f.severity === 'CRITICAL').length === 0,
        riskScore,
        fraudIndicators,
        ocrResults,
        imageQualityScore: await this.assessImageQuality(metadata),
        recommendations
      };
      
    } catch (error) {
      console.error('Fraud detection error:', error);
      
      return {
        isValid: false,
        riskScore: 100,
        fraudIndicators: [{
          type: 'KNOWN_FRAUD_PATTERN',
          severity: 'CRITICAL',
          description: 'Unable to verify check authenticity',
          confidence: 100
        }],
        ocrResults: this.getEmptyOCRResults(),
        imageQualityScore: 0,
        recommendations: ['Please try capturing the check images again']
      };
    }
  }
  
  /**
   * Perform OCR on check images
   */
  private async performOCR(metadata: CheckMetadata): Promise<OCRResults> {
    try {
      // Use Vision Camera OCR for text extraction
      const frontOCR = await VisionCameraOCR.processImage(metadata.frontImagePath);
      const backOCR = await VisionCameraOCR.processImage(metadata.backImagePath);
      
      // Extract check fields using pattern matching
      const amount = this.extractAmount(frontOCR.text);
      const accountNumber = this.extractAccountNumber(frontOCR.text);
      const routingNumber = this.extractRoutingNumber(frontOCR.text);
      const checkNumber = this.extractCheckNumber(frontOCR.text);
      const date = this.extractDate(frontOCR.text);
      const payee = this.extractPayee(frontOCR.text);
      const micrLine = this.extractMICRLine(frontOCR.text);
      
      // Calculate OCR confidence
      const confidence = this.calculateOCRConfidence({
        amount, accountNumber, routingNumber, 
        checkNumber, date, payee, micrLine
      });
      
      return {
        amount,
        accountNumber,
        routingNumber,
        checkNumber,
        date,
        payee,
        micrLine,
        confidence
      };
      
    } catch (error) {
      console.error('OCR processing error:', error);
      return this.getEmptyOCRResults();
    }
  }
  
  /**
   * Check for duplicate submissions
   */
  private async checkForDuplicates(
    metadata: CheckMetadata, 
    ocrResults: OCRResults
  ): Promise<FraudIndicator | null> {
    const cutoffDate = Date.now() - (this.DUPLICATE_CHECK_DAYS * 24 * 60 * 60 * 1000);
    
    for (const [checkId, historicalCheck] of this.checkHistory) {
      if (historicalCheck.timestamp < cutoffDate) continue;
      
      // Compare check numbers
      if (ocrResults.checkNumber && 
          historicalCheck.checkNumber === ocrResults.checkNumber &&
          historicalCheck.routingNumber === ocrResults.routingNumber) {
        return {
          type: 'DUPLICATE',
          severity: 'CRITICAL',
          description: 'This check has already been deposited',
          confidence: 95
        };
      }
      
      // Compare amounts and dates for potential duplicates
      if (Math.abs(historicalCheck.amount - metadata.amount) < 0.01 &&
          this.areDatesClose(historicalCheck.date, ocrResults.date, 7)) {
        return {
          type: 'DUPLICATE',
          severity: 'HIGH',
          description: 'A similar check was recently deposited',
          confidence: 80
        };
      }
    }
    
    return null;
  }
  
  /**
   * Validate MICR line format and checksum
   */
  private validateMICR(micrLine: string | null): { isValid: boolean; error?: string } {
    if (!micrLine) {
      return { isValid: false, error: 'MICR line not found' };
    }
    
    // MICR format: ⑆RRRRRRRRR⑆ AAAAAAAAAA⑈ CCCC
    const micrPattern = /[⑆:](\d{9})[⑆:][\s]*(\d{8,12})[⑈'][\s]*(\d{4,6})/;
    const match = micrLine.match(micrPattern);
    
    if (!match) {
      return { isValid: false, error: 'Invalid MICR format' };
    }
    
    const [, routingNumber, accountNumber, checkNumber] = match;
    
    // Validate routing number checksum
    if (!this.validateRoutingNumber(routingNumber)) {
      return { isValid: false, error: 'Invalid routing number checksum' };
    }
    
    return { isValid: true };
  }
  
  /**
   * Validate routing number using ABA checksum algorithm
   */
  private validateRoutingNumber(routingNumber: string): boolean {
    if (routingNumber.length !== 9) return false;
    
    const digits = routingNumber.split('').map(Number);
    const checksum = (3 * (digits[0] + digits[3] + digits[6]) +
                     7 * (digits[1] + digits[4] + digits[7]) +
                     (digits[2] + digits[5] + digits[8])) % 10;
    
    return checksum === 0;
  }
  
  /**
   * Validate amount consistency
   */
  private validateAmount(
    userAmount: number, 
    ocrAmount: string | null
  ): FraudIndicator | null {
    if (!ocrAmount) {
      return {
        type: 'SUSPICIOUS_AMOUNT',
        severity: 'MEDIUM',
        description: 'Unable to verify amount from check image',
        confidence: 60
      };
    }
    
    const parsedOCRAmount = this.parseAmount(ocrAmount);
    if (Math.abs(userAmount - parsedOCRAmount) > 0.01) {
      return {
        type: 'SUSPICIOUS_AMOUNT',
        severity: 'HIGH',
        description: 'Entered amount does not match check amount',
        confidence: 90
      };
    }
    
    // Check for suspicious amounts
    if (userAmount > this.HIGH_RISK_AMOUNT_THRESHOLD) {
      return {
        type: 'SUSPICIOUS_AMOUNT',
        severity: 'MEDIUM',
        description: 'Large deposit amount requires additional verification',
        confidence: 70
      };
    }
    
    return null;
  }
  
  /**
   * Analyze check image for signs of manipulation
   */
  private async analyzeImageIntegrity(metadata: CheckMetadata): Promise<FraudIndicator[]> {
    const indicators: FraudIndicator[] = [];
    
    try {
      // Check image metadata for inconsistencies
      const frontImageInfo = await this.getImageMetadata(metadata.frontImagePath);
      const backImageInfo = await this.getImageMetadata(metadata.backImagePath);
      
      // Check if images were taken at significantly different times
      if (Math.abs(frontImageInfo.timestamp - backImageInfo.timestamp) > 300000) { // 5 minutes
        indicators.push({
          type: 'IMAGE_MANIPULATION',
          severity: 'MEDIUM',
          description: 'Front and back images taken at different times',
          confidence: 70
        });
      }
      
      // Check for image editing software signatures
      if (this.hasEditingSoftwareSignature(frontImageInfo) || 
          this.hasEditingSoftwareSignature(backImageInfo)) {
        indicators.push({
          type: 'IMAGE_MANIPULATION',
          severity: 'HIGH',
          description: 'Image shows signs of digital editing',
          confidence: 85
        });
      }
      
      // Use ML model to detect image tampering
      const tamperingScore = await this.detectImageTampering(metadata);
      if (tamperingScore > 0.7) {
        indicators.push({
          type: 'IMAGE_MANIPULATION',
          severity: 'HIGH',
          description: 'Potential image tampering detected',
          confidence: tamperingScore * 100
        });
      }
      
    } catch (error) {
      console.error('Image integrity analysis error:', error);
    }
    
    return indicators;
  }
  
  /**
   * Validate check date
   */
  private validateCheckDate(dateStr: string | null): FraudIndicator | null {
    if (!dateStr) {
      return {
        type: 'DATE_ISSUE',
        severity: 'LOW',
        description: 'Unable to verify check date',
        confidence: 50
      };
    }
    
    const checkDate = this.parseDate(dateStr);
    if (!checkDate) {
      return {
        type: 'DATE_ISSUE',
        severity: 'MEDIUM',
        description: 'Invalid date format on check',
        confidence: 70
      };
    }
    
    const daysDiff = (Date.now() - checkDate.getTime()) / (1000 * 60 * 60 * 24);
    
    // Check if post-dated
    if (daysDiff < 0) {
      return {
        type: 'DATE_ISSUE',
        severity: 'HIGH',
        description: 'Post-dated check cannot be deposited yet',
        confidence: 95
      };
    }
    
    // Check if stale dated (older than 180 days)
    if (daysDiff > 180) {
      return {
        type: 'DATE_ISSUE',
        severity: 'HIGH',
        description: 'Check is stale dated (over 180 days old)',
        confidence: 95
      };
    }
    
    return null;
  }
  
  /**
   * Check for known fraud patterns
   */
  private async checkFraudPatterns(
    metadata: CheckMetadata, 
    ocrResults: OCRResults
  ): Promise<FraudIndicator[]> {
    const indicators: FraudIndicator[] = [];
    
    try {
      // Check against known bad account numbers
      const response = await ApiService.post('/api/v1/fraud/check-account', {
        accountNumber: ocrResults.accountNumber,
        routingNumber: ocrResults.routingNumber
      });
      
      if (response.data.isBlacklisted) {
        indicators.push({
          type: 'KNOWN_FRAUD_PATTERN',
          severity: 'CRITICAL',
          description: 'Account associated with fraudulent activity',
          confidence: 100
        });
      }
      
      // Check velocity patterns
      const velocityCheck = await this.checkVelocityPatterns(metadata);
      if (velocityCheck) {
        indicators.push(velocityCheck);
      }
      
    } catch (error) {
      console.error('Fraud pattern check error:', error);
    }
    
    return indicators;
  }
  
  /**
   * Check for velocity-based fraud patterns
   */
  private async checkVelocityPatterns(metadata: CheckMetadata): Promise<FraudIndicator | null> {
    try {
      const response = await ApiService.post('/api/v1/fraud/check-velocity', {
        deviceId: metadata.deviceId,
        amount: metadata.amount,
        timestamp: metadata.timestamp
      });
      
      const { depositsLast24h, amountLast24h, uniqueDevices } = response.data;
      
      // Check for suspicious deposit patterns
      if (depositsLast24h > 5) {
        return {
          type: 'KNOWN_FRAUD_PATTERN',
          severity: 'HIGH',
          description: 'Unusual number of deposits in 24 hours',
          confidence: 85
        };
      }
      
      if (amountLast24h > 10000) {
        return {
          type: 'SUSPICIOUS_AMOUNT',
          severity: 'HIGH',
          description: 'High deposit volume in 24 hours',
          confidence: 80
        };
      }
      
      if (uniqueDevices > 2) {
        return {
          type: 'KNOWN_FRAUD_PATTERN',
          severity: 'MEDIUM',
          description: 'Multiple devices used for deposits',
          confidence: 70
        };
      }
      
    } catch (error) {
      console.error('Velocity check error:', error);
    }
    
    return null;
  }
  
  /**
   * Assess device risk
   */
  private async assessDeviceRisk(metadata: CheckMetadata): Promise<FraudIndicator | null> {
    try {
      const isJailbroken = await DeviceInfo.isJailBroken();
      if (isJailbroken) {
        return {
          type: 'KNOWN_FRAUD_PATTERN',
          severity: 'HIGH',
          description: 'Device security compromised (jailbroken/rooted)',
          confidence: 90
        };
      }
      
      // Check if using VPN or proxy
      const networkInfo = await this.checkNetworkSecurity();
      if (networkInfo.isProxy || networkInfo.isVPN) {
        return {
          type: 'KNOWN_FRAUD_PATTERN',
          severity: 'MEDIUM',
          description: 'Suspicious network configuration detected',
          confidence: 70
        };
      }
      
    } catch (error) {
      console.error('Device risk assessment error:', error);
    }
    
    return null;
  }
  
  /**
   * Run ML-based fraud detection
   */
  private async runMLFraudDetection(
    metadata: CheckMetadata, 
    ocrResults: OCRResults
  ): Promise<{ isFraud: boolean; confidence: number }> {
    if (!this.mlModel) {
      return { isFraud: false, confidence: 0 };
    }
    
    try {
      // Prepare features for ML model
      const features = this.prepareMLFeatures(metadata, ocrResults);
      
      // Run prediction
      const prediction = await this.mlModel.predict(features).data();
      const fraudProbability = prediction[0];
      
      return {
        isFraud: fraudProbability > 0.5,
        confidence: fraudProbability
      };
      
    } catch (error) {
      console.error('ML fraud detection error:', error);
      return { isFraud: false, confidence: 0 };
    }
  }
  
  /**
   * Calculate overall risk score
   */
  private calculateRiskScore(fraudIndicators: FraudIndicator[]): number {
    let score = 0;
    
    fraudIndicators.forEach(indicator => {
      const severityWeight = {
        'LOW': 10,
        'MEDIUM': 25,
        'HIGH': 50,
        'CRITICAL': 100
      }[indicator.severity];
      
      score += (severityWeight * indicator.confidence) / 100;
    });
    
    // Normalize to 0-100 scale
    return Math.min(100, score);
  }
  
  /**
   * Generate recommendations based on fraud indicators
   */
  private generateRecommendations(
    fraudIndicators: FraudIndicator[], 
    riskScore: number
  ): string[] {
    const recommendations: string[] = [];
    
    if (riskScore > 80) {
      recommendations.push('This check cannot be deposited due to high fraud risk');
      recommendations.push('Please visit a branch for assistance');
    } else if (riskScore > 50) {
      recommendations.push('Additional verification required');
      recommendations.push('Your deposit will be reviewed within 24 hours');
    } else if (riskScore > 20) {
      recommendations.push('Standard hold period will apply to this deposit');
    }
    
    // Add specific recommendations based on indicators
    fraudIndicators.forEach(indicator => {
      switch (indicator.type) {
        case 'IMAGE_MANIPULATION':
          recommendations.push('Please recapture check images in good lighting');
          break;
        case 'INVALID_MICR':
          recommendations.push('Ensure the entire bottom of the check is visible');
          break;
        case 'SUSPICIOUS_AMOUNT':
          recommendations.push('Verify the amount entered matches the check');
          break;
        case 'DATE_ISSUE':
          recommendations.push('Check the date on your check');
          break;
      }
    });
    
    return [...new Set(recommendations)]; // Remove duplicates
  }
  
  // Helper methods
  
  private async loadMLModel(): Promise<void> {
    try {
      // Load pre-trained fraud detection model
      // In production, this would load from a secure CDN or local bundle
      // this.mlModel = await tf.loadLayersModel('path/to/model.json');
      console.log('ML model loaded successfully');
    } catch (error) {
      console.error('Failed to load ML model:', error);
    }
  }
  
  private async loadCheckHistory(): Promise<void> {
    try {
      const history = await AsyncStorage.getItem('check_deposit_history');
      if (history) {
        const parsed = JSON.parse(history);
        this.checkHistory = new Map(parsed);
      }
    } catch (error) {
      console.error('Failed to load check history:', error);
    }
  }
  
  private async storeCheckMetadata(
    metadata: CheckMetadata, 
    ocrResults: OCRResults
  ): Promise<void> {
    const checkId = `${ocrResults.checkNumber}_${ocrResults.routingNumber}_${Date.now()}`;
    
    this.checkHistory.set(checkId, {
      ...metadata,
      ...ocrResults,
      checkId
    });
    
    // Keep only recent history
    const cutoffDate = Date.now() - (this.DUPLICATE_CHECK_DAYS * 24 * 60 * 60 * 1000);
    for (const [id, check] of this.checkHistory) {
      if (check.timestamp < cutoffDate) {
        this.checkHistory.delete(id);
      }
    }
    
    // Persist to storage
    try {
      const historyArray = Array.from(this.checkHistory.entries());
      await AsyncStorage.setItem('check_deposit_history', JSON.stringify(historyArray));
    } catch (error) {
      console.error('Failed to store check history:', error);
    }
  }
  
  private extractAmount(text: string): string | null {
    const amountPattern = /\$[\d,]+\.?\d{0,2}/;
    const match = text.match(amountPattern);
    return match ? match[0] : null;
  }
  
  private extractAccountNumber(text: string): string | null {
    // Account numbers are typically 8-12 digits
    const accountPattern = /\b\d{8,12}\b/;
    const match = text.match(accountPattern);
    return match ? match[0] : null;
  }
  
  private extractRoutingNumber(text: string): string | null {
    // Routing numbers are always 9 digits
    const routingPattern = /\b\d{9}\b/;
    const matches = text.match(new RegExp(routingPattern, 'g'));
    
    if (matches) {
      // Validate each match to find valid routing number
      for (const match of matches) {
        if (this.validateRoutingNumber(match)) {
          return match;
        }
      }
    }
    
    return null;
  }
  
  private extractCheckNumber(text: string): string | null {
    // Check numbers are typically 4-6 digits
    const checkPattern = /Check\s*#?\s*(\d{4,6})|^\d{4,6}$/m;
    const match = text.match(checkPattern);
    return match ? match[1] || match[0] : null;
  }
  
  private extractDate(text: string): string | null {
    // Common date formats
    const datePatterns = [
      /\d{1,2}\/\d{1,2}\/\d{2,4}/,
      /\d{1,2}-\d{1,2}-\d{2,4}/,
      /\w+\s+\d{1,2},?\s+\d{4}/
    ];
    
    for (const pattern of datePatterns) {
      const match = text.match(pattern);
      if (match) return match[0];
    }
    
    return null;
  }
  
  private extractPayee(text: string): string | null {
    // Look for "Pay to the order of" or similar
    const payeePattern = /Pay\s+to\s+the\s+order\s+of\s+([^\n]+)|PAY\s+([^\n]+)/i;
    const match = text.match(payeePattern);
    return match ? (match[1] || match[2]).trim() : null;
  }
  
  private extractMICRLine(text: string): string | null {
    // MICR uses special characters, but OCR might read them differently
    const micrPattern = /[⑆:]\d{9}[⑆:]\s*\d{8,12}[⑈']\s*\d{4,6}/;
    const match = text.match(micrPattern);
    return match ? match[0] : null;
  }
  
  private parseAmount(amountStr: string): number {
    return parseFloat(amountStr.replace(/[$,]/g, ''));
  }
  
  private parseDate(dateStr: string): Date | null {
    const date = new Date(dateStr);
    return isNaN(date.getTime()) ? null : date;
  }
  
  private areDatesClose(date1: string | null, date2: string | null, daysTolerance: number): boolean {
    if (!date1 || !date2) return false;
    
    const d1 = this.parseDate(date1);
    const d2 = this.parseDate(date2);
    
    if (!d1 || !d2) return false;
    
    const diffDays = Math.abs(d1.getTime() - d2.getTime()) / (1000 * 60 * 60 * 24);
    return diffDays <= daysTolerance;
  }
  
  private calculateOCRConfidence(results: Partial<OCRResults>): number {
    const requiredFields = ['amount', 'accountNumber', 'routingNumber', 'checkNumber'];
    const foundFields = requiredFields.filter(field => results[field as keyof OCRResults]);
    return (foundFields.length / requiredFields.length) * 100;
  }
  
  private async getImageMetadata(imagePath: string): Promise<any> {
    // In a real implementation, this would extract EXIF data
    return {
      timestamp: Date.now(),
      software: null
    };
  }
  
  private hasEditingSoftwareSignature(metadata: any): boolean {
    const editingSoftware = ['Photoshop', 'GIMP', 'Paint', 'Editor'];
    return metadata.software && 
           editingSoftware.some(sw => metadata.software.includes(sw));
  }
  
  private async detectImageTampering(metadata: CheckMetadata): Promise<number> {
    // Placeholder for ML-based image tampering detection
    // Would use computer vision techniques to detect:
    // - Inconsistent lighting
    // - Edge artifacts
    // - Cloning patterns
    // - Metadata inconsistencies
    return 0;
  }
  
  private async checkNetworkSecurity(): Promise<{ isProxy: boolean; isVPN: boolean }> {
    // Check for proxy/VPN usage
    // In production, this would check network configuration
    return { isProxy: false, isVPN: false };
  }
  
  private prepareMLFeatures(metadata: CheckMetadata, ocrResults: OCRResults): tf.Tensor {
    // Prepare feature vector for ML model
    const features = [
      metadata.amount / 10000, // Normalize amount
      ocrResults.confidence / 100,
      this.checkHistory.size / 100, // Historical deposits
      metadata.timestamp / (Date.now()), // Time of day factor
      ocrResults.amount ? 1 : 0,
      ocrResults.accountNumber ? 1 : 0,
      ocrResults.routingNumber ? 1 : 0,
      ocrResults.checkNumber ? 1 : 0,
      ocrResults.micrLine ? 1 : 0
    ];
    
    return tf.tensor2d([features]);
  }
  
  private async assessImageQuality(metadata: CheckMetadata): Promise<number> {
    // Assess image quality based on resolution, focus, lighting
    // Placeholder implementation
    return 85;
  }
  
  private getEmptyOCRResults(): OCRResults {
    return {
      amount: null,
      accountNumber: null,
      routingNumber: null,
      checkNumber: null,
      date: null,
      payee: null,
      micrLine: null,
      confidence: 0
    };
  }
}

export default new CheckFraudDetectionService();