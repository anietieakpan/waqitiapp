export enum ConsentPurpose {
  ESSENTIAL_SERVICE = 'ESSENTIAL_SERVICE',
  MARKETING_EMAILS = 'MARKETING_EMAILS',
  PROMOTIONAL_SMS = 'PROMOTIONAL_SMS',
  PUSH_NOTIFICATIONS = 'PUSH_NOTIFICATIONS',
  ANALYTICS = 'ANALYTICS',
  PERSONALIZATION = 'PERSONALIZATION',
  THIRD_PARTY_SHARING = 'THIRD_PARTY_SHARING',
  PROFILING = 'PROFILING',
  AUTOMATED_DECISIONS = 'AUTOMATED_DECISIONS',
  LOCATION_TRACKING = 'LOCATION_TRACKING',
  BIOMETRIC_DATA = 'BIOMETRIC_DATA',
  CROSS_BORDER_TRANSFER = 'CROSS_BORDER_TRANSFER',
}

export enum ConsentStatus {
  GRANTED = 'GRANTED',
  WITHDRAWN = 'WITHDRAWN',
  EXPIRED = 'EXPIRED',
  PENDING = 'PENDING',
}

export enum RequestType {
  ACCESS = 'ACCESS',
  PORTABILITY = 'PORTABILITY',
  RECTIFICATION = 'RECTIFICATION',
  ERASURE = 'ERASURE',
  RESTRICTION = 'RESTRICTION',
  OBJECTION = 'OBJECTION',
}

export enum RequestStatus {
  PENDING_VERIFICATION = 'PENDING_VERIFICATION',
  VERIFIED = 'VERIFIED',
  IN_PROGRESS = 'IN_PROGRESS',
  COMPLETED = 'COMPLETED',
  REJECTED = 'REJECTED',
  EXPIRED = 'EXPIRED',
}

export enum ExportFormat {
  JSON = 'JSON',
  CSV = 'CSV',
  PDF = 'PDF',
  EXCEL = 'EXCEL',
}

export enum CollectionMethod {
  EXPLICIT_CHECKBOX = 'EXPLICIT_CHECKBOX',
  IMPLICIT_SIGNUP = 'IMPLICIT_SIGNUP',
  EMAIL_CONFIRMATION = 'EMAIL_CONFIRMATION',
  IN_APP_PROMPT = 'IN_APP_PROMPT',
  SETTINGS_PAGE = 'SETTINGS_PAGE',
  CUSTOMER_SUPPORT = 'CUSTOMER_SUPPORT',
  IMPORTED = 'IMPORTED',
}

export enum LawfulBasis {
  CONSENT = 'CONSENT',
  CONTRACT = 'CONTRACT',
  LEGAL_OBLIGATION = 'LEGAL_OBLIGATION',
  VITAL_INTERESTS = 'VITAL_INTERESTS',
  PUBLIC_TASK = 'PUBLIC_TASK',
  LEGITIMATE_INTERESTS = 'LEGITIMATE_INTERESTS',
}

export interface ConsentRecord {
  id: string;
  userId: string;
  purpose: ConsentPurpose;
  status: ConsentStatus;
  consentVersion: string;
  grantedAt?: string;
  withdrawnAt?: string;
  expiresAt?: string;
  collectionMethod: CollectionMethod;
  consentText: string;
  lawfulBasis: LawfulBasis;
  thirdParties?: string;
  dataRetentionDays: number;
  isActive: boolean;
}

export interface DataSubjectRequest {
  id: string;
  userId: string;
  requestType: RequestType;
  status: RequestStatus;
  submittedAt: string;
  completedAt?: string;
  deadline: string;
  dataCategories: string[];
  exportFormat?: ExportFormat;
  exportUrl?: string;
  exportExpiresAt?: string;
  rejectionReason?: string;
  notes?: string;
  isOverdue: boolean;
  auditLogs: AuditLog[];
}

export interface AuditLog {
  id: string;
  action: string;
  details: string;
  performedBy: string;
  performedAt: string;
}

export interface GrantConsentDTO {
  purpose: ConsentPurpose;
  collectionMethod: CollectionMethod;
  thirdParties?: string;
  retentionDays?: number;
  expiresInDays?: number;
  isMinor?: boolean;
  parentalConsentId?: string;
}

export interface CreateRequestDTO {
  requestType: RequestType;
  dataCategories: string[];
  exportFormat?: ExportFormat;
  notes?: string;
}

export interface UpdateConsentPreferencesDTO {
  preferences: Record<ConsentPurpose, boolean>;
  collectionMethod?: CollectionMethod;
  request?: any;
}

export interface ConsentHistoryEvent {
  eventType: 'GRANTED' | 'WITHDRAWN';
  purpose: ConsentPurpose;
  timestamp: string;
  version: string;
  status: ConsentStatus;
}

export interface ConsentHistory {
  userId: string;
  events: ConsentHistoryEvent[];
  totalEvents: number;
}

export interface ConsentForm {
  purpose: ConsentPurpose;
  version: string;
  title: string;
  consentText: string;
  dataCategories: string[];
  processingPurposes: string[];
  thirdParties: string[];
  retentionPeriod: string;
  userRights: string[];
  contactInfo: string;
  lastUpdated: string;
  isRequired: boolean;
  lawfulBasis: LawfulBasis;
}

export interface DataExport {
  id: string;
  userId: string;
  format: ExportFormat;
  categories: string[];
  status: 'PENDING' | 'PROCESSING' | 'COMPLETED' | 'FAILED';
  requestedAt: string;
  completedAt?: string;
  downloadUrl?: string;
  expiresAt?: string;
  size?: number;
}

export interface PrivacyPolicy {
  version: string;
  effectiveDate: string;
  content: string;
  sections: PolicySection[];
  lastUpdated: string;
  language: string;
}

export interface PolicySection {
  id: string;
  title: string;
  content: string;
  order: number;
}

export interface DataSubjectRights {
  rights: Right[];
  contactInfo: ContactInfo;
  processingTime: string;
  complaintInfo: ComplaintInfo;
}

export interface Right {
  name: string;
  description: string;
  howToExercise: string;
  limitations?: string[];
}

export interface ContactInfo {
  dataController: string;
  dpoEmail: string;
  dpoPhone?: string;
  address: string;
}

export interface ComplaintInfo {
  authority: string;
  website: string;
  email: string;
  phone?: string;
}

export interface ProcessingActivity {
  id: string;
  activityName: string;
  description: string;
  processingPurpose: string;
  lawfulBasis: LawfulBasis;
  dataController: string;
  dataProcessor?: string;
  dataCategories: string[];
  dataSubjects: string[];
  recipients: string[];
  retentionPeriod: string;
  securityMeasures: string;
  thirdCountryTransfers: boolean;
  transferSafeguards?: string;
  isHighRisk: boolean;
  dpiaRequired: boolean;
  dpiaCompleted?: boolean;
}