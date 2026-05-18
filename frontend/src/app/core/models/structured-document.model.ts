export type StructuredDocumentType =
  | 'CV'
  | 'COVER_LETTER'
  | 'APPLICATION_TEXT'
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
  documentType: StructuredDocumentType;
  exportMode: 'ATS' | 'DESIGNED' | string;
  templateId: string;
  identity: DocumentIdentity;
  sections: StructuredDocumentSection[];
  bodyContent?: string;
  atsReport?: AtsReport;
}

export interface DocumentTemplateOption {
  id: string;
  label: string;
  documentTypes: StructuredDocumentType[];
  layoutType: 'single-column' | 'two-column';
}

export const STRUCTURED_DOCUMENT_TEMPLATES: DocumentTemplateOption[] = [
  { id: 'cv-ats-classic', label: 'ATS Classic', documentTypes: ['CV'], layoutType: 'single-column' },
  { id: 'cv-modern-professional', label: 'Modern Professional', documentTypes: ['CV'], layoutType: 'two-column' },
  { id: 'cv-compact-tech', label: 'Compact Tech', documentTypes: ['CV'], layoutType: 'two-column' },
  { id: 'cv-executive', label: 'Executive', documentTypes: ['CV'], layoutType: 'two-column' },
  { id: 'cv-minimal-scandinavian', label: 'Minimal Scandinavian', documentTypes: ['CV'], layoutType: 'single-column' },
  { id: 'application-modern', label: 'Modern Application', documentTypes: ['COVER_LETTER', 'APPLICATION_TEXT'], layoutType: 'single-column' },
  { id: 'application-formal', label: 'Formal Letter', documentTypes: ['COVER_LETTER', 'APPLICATION_TEXT'], layoutType: 'single-column' },
  { id: 'application-ats', label: 'ATS Plain Letter', documentTypes: ['COVER_LETTER', 'APPLICATION_TEXT'], layoutType: 'single-column' }
];
