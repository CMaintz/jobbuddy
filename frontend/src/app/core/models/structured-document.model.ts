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
  /** Present on a gated letter: the needs it was written from and the gate's verdict. */
  letterReview?: LetterReview;
}

/** One checklist point of the letter gate (writing guide KORREKTUR 1, 8, 13 or 14). */
export interface LetterGatePoint {
  point: number;
  passed: boolean;
  quotes: string[];
  reason?: string;
}

type LetterGateStop =
  'OUT_OF_ROUNDS' | 'OUT_OF_TIME' | 'REWRITE_FAILED' | 'NO_VERDICT' | 'REWRITE_NOT_JUDGED';

export interface LetterGateResult {
  verdict: 'PASSED' | 'FAILED' | 'NOT_COMPLETED';
  points: LetterGatePoint[];
  rounds: number;
  stop?: LetterGateStop | null;
}

export interface LetterReview {
  needs: string[];
  /** Null for a letter written without a posting: it has needs but nothing to gate against. */
  gate: LetterGateResult | null;
  gatedBodyFingerprint?: string;
  /** True once the letter has been edited since the gate judged it. */
  stale: boolean;
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
