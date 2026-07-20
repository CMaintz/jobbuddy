export type StructuredDocumentType =
  | 'CV'
  | 'COVER_LETTER'
  | 'APPLICATION_TEXT'
  | 'UNSOLICITED_APPLICATION'
  | 'RECRUITER_MESSAGE'
  | 'FOLLOW_UP_MESSAGE'
  | 'CV_ANALYSIS_REPORT';

export interface DocumentIdentity {
  name?: string;
  headline?: string;
  email?: string;
  phone?: string;
  location?: string;
  linkedinUrl?: string;
  githubUrl?: string;
  websiteUrl?: string;
  profileImageUrl?: string;
}

export interface StructuredDocumentItem {
  sourceId?: string;
  title?: string;
  subtitle?: string;
  location?: string;
  dateRange?: string;
  description?: string;
  bullets?: string[];
  technologies?: string[];
  links?: string[];
  skills?: string[];
}

export interface StructuredDocumentSection {
  id: string;
  type: string;
  heading: string;
  body?: string;
  items?: StructuredDocumentItem[];
}

export interface AtsCheck {
  code: string;
  label: string;
  status: 'PASS' | 'WARN' | 'INFO' | 'FAIL';
  detail: string;
}

export interface AtsReport {
  score: number;
  keywordCoverage: number;
  matchedKeywords: string[];
  missingKeywords: string[];
  checks: AtsCheck[];
}

export interface StructuredDocument {
  generatedDocumentId?: string;
  documentType: StructuredDocumentType;
  exportMode: 'ATS' | 'DESIGNED' | string;
  templateId: string;
  identity: DocumentIdentity;
  options?: DocumentRenderOptions;
  sections: StructuredDocumentSection[];
  bodyContent?: string;
  atsReport?: AtsReport;
}

export interface DocumentRenderOptions {
  showProfileImage: boolean;
  theme?: DocumentTheme;
}

export interface DocumentTheme {
  primaryColor: string;
  accentColor: string;
  fontFamily: string;
  fontScale: 'small' | 'normal' | 'large' | string;
}

export interface DocumentTemplateOption {
  id: string;
  familyId?: string;
  familyName?: string;
  label: string;
  name?: string;
  description?: string;
  documentTypes: StructuredDocumentType[];
  layoutType: 'single-column' | 'two-column';
  exportMode?: 'ATS' | 'DESIGNED' | string;
  supportsProfileImage?: boolean;
  atsSafe?: boolean;
  displayOrder?: number;
  defaultTheme?: DocumentTheme;
}

export const STRUCTURED_DOCUMENT_TEMPLATES: DocumentTemplateOption[] = [
  { id: 'cv-ats-classic', familyId: 'classic-ats', familyName: 'Classic ATS', label: 'ATS Classic CV', documentTypes: ['CV'], layoutType: 'single-column', exportMode: 'ATS', supportsProfileImage: false, atsSafe: true, defaultTheme: { primaryColor: '#111111', accentColor: '#d1d5db', fontFamily: 'Arial', fontScale: 'normal' } },
  { id: 'application-ats', familyId: 'classic-ats', familyName: 'Classic ATS', label: 'ATS Plain Application', documentTypes: ['COVER_LETTER', 'APPLICATION_TEXT'], layoutType: 'single-column', exportMode: 'ATS', supportsProfileImage: false, atsSafe: true, defaultTheme: { primaryColor: '#111111', accentColor: '#d1d5db', fontFamily: 'Arial', fontScale: 'normal' } },
  { id: 'cv-modern-professional', familyId: 'modern-professional', familyName: 'Modern Professional', label: 'Modern Professional CV', documentTypes: ['CV'], layoutType: 'two-column', exportMode: 'DESIGNED', supportsProfileImage: true, atsSafe: false, defaultTheme: { primaryColor: '#18324a', accentColor: '#cbd8e3', fontFamily: 'Inter', fontScale: 'normal' } },
  { id: 'application-modern', familyId: 'modern-professional', familyName: 'Modern Professional', label: 'Modern Professional Application', documentTypes: ['COVER_LETTER', 'APPLICATION_TEXT'], layoutType: 'single-column', exportMode: 'DESIGNED', supportsProfileImage: true, atsSafe: false, defaultTheme: { primaryColor: '#18324a', accentColor: '#cbd8e3', fontFamily: 'Inter', fontScale: 'normal' } },
  { id: 'cv-compact-tech', familyId: 'compact-tech', familyName: 'Compact Tech', label: 'Compact Tech CV', documentTypes: ['CV'], layoutType: 'two-column', exportMode: 'DESIGNED', supportsProfileImage: true, atsSafe: false, defaultTheme: { primaryColor: '#0f3d3e', accentColor: '#b7d8d6', fontFamily: 'Inter', fontScale: 'small' } },
  { id: 'application-compact-tech', familyId: 'compact-tech', familyName: 'Compact Tech', label: 'Compact Tech Application', documentTypes: ['COVER_LETTER', 'APPLICATION_TEXT'], layoutType: 'single-column', exportMode: 'DESIGNED', supportsProfileImage: true, atsSafe: false, defaultTheme: { primaryColor: '#0f3d3e', accentColor: '#b7d8d6', fontFamily: 'Inter', fontScale: 'small' } },
  { id: 'cv-executive', familyId: 'executive', familyName: 'Executive', label: 'Executive CV', documentTypes: ['CV'], layoutType: 'two-column', exportMode: 'DESIGNED', supportsProfileImage: true, atsSafe: false, defaultTheme: { primaryColor: '#2b2338', accentColor: '#d8cedf', fontFamily: 'Georgia', fontScale: 'normal' } },
  { id: 'application-executive', familyId: 'executive', familyName: 'Executive', label: 'Executive Application', documentTypes: ['COVER_LETTER', 'APPLICATION_TEXT'], layoutType: 'single-column', exportMode: 'DESIGNED', supportsProfileImage: true, atsSafe: false, defaultTheme: { primaryColor: '#2b2338', accentColor: '#d8cedf', fontFamily: 'Georgia', fontScale: 'normal' } },
  { id: 'cv-minimal-scandinavian', familyId: 'minimal-scandinavian', familyName: 'Minimal Scandinavian', label: 'Minimal Scandinavian CV', documentTypes: ['CV'], layoutType: 'single-column', exportMode: 'DESIGNED', supportsProfileImage: false, atsSafe: false, defaultTheme: { primaryColor: '#2f3a36', accentColor: '#d9e4df', fontFamily: 'Calibri', fontScale: 'normal' } },
  { id: 'application-minimal-scandinavian', familyId: 'minimal-scandinavian', familyName: 'Minimal Scandinavian', label: 'Minimal Scandinavian Application', documentTypes: ['COVER_LETTER', 'APPLICATION_TEXT'], layoutType: 'single-column', exportMode: 'DESIGNED', supportsProfileImage: false, atsSafe: false, defaultTheme: { primaryColor: '#2f3a36', accentColor: '#d9e4df', fontFamily: 'Calibri', fontScale: 'normal' } }
];
