export interface PersonalInfo {
  fullName: string;
  title: string;
  email: string;
  phone: string;
  location: string;
  website: string;
  linkedin: string;
  github: string;
  twitter: string;
  summary: string;
  photoUrl?: string;
}

export interface ResumeExperience {
  id: string;
  title: string;
  company: string;
  location: string;
  startDate: string;
  endDate: string;
  current: boolean;
  description: string;
  bulletStyle?: string;
  skills?: string[];  // skill names only (no level)
}

export interface ResumeEducation {
  id: string;
  degree: string;
  school: string;
  location: string;
  startDate: string;
  endDate: string;
  current: boolean;
  skills?: string[];
}

export interface ResumeProject {
  id: string;
  name: string;
  link: string;
  date: string;
  description: string;
  skills?: string[];
}

export interface ResumeSkill {
  id: string;
  name: string;
  level?: number; // 1-5
  /** Optional grouping label (e.g. "Languages", "Frameworks", "Tools"). */
  category?: string;
}

export interface ResumeLanguage {
  id: string;
  name: string;
  proficiency: 'Native' | 'Fluent' | 'Proficient' | 'Intermediate' | 'Basic';
}

export interface ResumeCertification {
  id: string;
  name: string;
  issuer: string;
  date: string;
  link?: string;
  description?: string;
}

export interface ResumeStrength {
  id: string;
  title: string;
  description: string;
  iconKey: string;
}

export interface ResumeSocial {
  id: string;
  platform: string;
  url: string;
  username?: string;
  iconKey: string;
}

export interface ResumeCustomSectionItem {
  id: string;
  text: string;
}

export interface ResumeCustomSection {
  id: string;
  heading: string;
  body?: string;
  items?: ResumeCustomSectionItem[];
}

export interface ResumeData {
  personalInfo: PersonalInfo;
  experience: ResumeExperience[];
  education: ResumeEducation[];
  projects: ResumeProject[];
  skills: ResumeSkill[];
  languages: ResumeLanguage[];
  certifications: ResumeCertification[];
  strengths: ResumeStrength[];
  socials: ResumeSocial[];
  customSections?: ResumeCustomSection[];
}

export type TemplateType = 'classic' | 'modern' | 'modern-2col' | 'minimal' | 'executive' | 'creative';

export interface SectionConfig {
  id: string;
  name: string;
  visible: boolean;
}

export interface SectionTypography {
  fontSize?: 'sm' | 'md' | 'lg' | 'xl';
  headingSize?: 'sm' | 'md' | 'lg' | 'xl';
  fontFamily?: string;
  /** Text size multiplier for the whole section (1 = template default). */
  sizeScale?: number;
  bold?: boolean;
  italic?: boolean;
  color?: string;
}

export interface ResumeSettings {
  themeColor: string;
  fontFamily: string;
  fontSize: 'sm' | 'md' | 'lg' | 'xl';
  documentSize: 'A4' | 'Letter';
  template: TemplateType;
  leftColumn: SectionConfig[];
  rightColumn: SectionConfig[];
  sectionTypography: Record<string, SectionTypography>;
  photoStyle?: 'square' | 'rounded' | 'circle';
  showSkillLevel: boolean;
  /** Body text colour (headings keep the theme colour). Optional for drafts saved before this existed. */
  textColor?: string;
  /** Unitless line-height multiplier for body text, e.g. 1.5. */
  lineSpacing?: number;
  /** Vertical gap between sections in px. */
  sectionSpacing?: number;
  /** Photo edge length in px; unset = the layout's default size. */
  photoSize?: number;
  /** Photo side within the header, where the layout supports it. */
  photoPlacement?: 'left' | 'right';
  /** Mask personal details (name → initials, no contact/photo/links) in preview & PDF. */
  anonymise?: boolean;
}

export interface ResumeDraft {
  id?: string;
  userId?: string;
  name: string;
  jobId?: string;
  applicationId?: string;
  resumeData: ResumeData;
  settings: ResumeSettings;
  status: 'DRAFT' | 'PUBLISHED';
  createdAt?: string;
  updatedAt?: string;
}

export const DEFAULT_LEFT_COLUMN: SectionConfig[] = [
  { id: 'summary', name: 'Summary', visible: true },
  { id: 'experience', name: 'Experience', visible: true },
  { id: 'education', name: 'Education', visible: true },
  { id: 'projects', name: 'Projects', visible: true },
];

export const DEFAULT_RIGHT_COLUMN: SectionConfig[] = [
  { id: 'skills', name: 'Skills', visible: true },
  { id: 'languages', name: 'Languages', visible: true },
  { id: 'certifications', name: 'Certifications', visible: true },
  { id: 'strengths', name: 'Strengths', visible: true },
  { id: 'socials', name: 'Socials', visible: true },
];

export const INITIAL_RESUME_DATA: ResumeData = {
  personalInfo: {
    fullName: '',
    title: '',
    email: '',
    phone: '',
    location: '',
    website: '',
    linkedin: '',
    github: '',
    twitter: '',
    summary: '',
    photoUrl: undefined,
  },
  experience: [],
  education: [],
  projects: [],
  skills: [],
  languages: [],
  certifications: [],
  strengths: [],
  socials: [],
  customSections: [],
};

export const INITIAL_SETTINGS: ResumeSettings = {
  themeColor: '#18324a',
  fontFamily: 'Inter',
  fontSize: 'md',
  documentSize: 'A4',
  template: 'classic',
  leftColumn: DEFAULT_LEFT_COLUMN,
  rightColumn: DEFAULT_RIGHT_COLUMN,
  sectionTypography: {},
  photoStyle: 'circle',
  showSkillLevel: true,
  textColor: '#1f2937',
  lineSpacing: 1.5,
  sectionSpacing: 20,
};
