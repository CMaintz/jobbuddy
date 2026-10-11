type StructuredDocumentType =
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

interface StructuredDocumentItem {
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
  /** Grouping label for skill items (e.g. "Languages"); absent for other item types. */
  category?: string;
}

interface StructuredDocumentSection {
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
