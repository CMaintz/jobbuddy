import { SkillTaxonomy } from './skill-taxonomy.model';

export interface WorkExperience {
  id?: string;
  userId?: string;
  companyName: string;
  title: string;
  location?: string;
  description?: string;
  startDate: string;
  endDate?: string;
  isCurrent: boolean;
  technologies?: string[];
  achievements?: string[];
  displayOrder?: number;
  skills?: SkillTaxonomy[];
}

export interface Project {
  id?: string;
  userId?: string;
  name: string;
  description?: string;
  technologies?: string[];
  githubUrl?: string;
  liveUrl?: string;
  architectureNotes?: string;
  measurableOutcomes?: string;
  businessImpact?: string;
  startDate?: string;
  endDate?: string;
  isFeatured?: boolean;
  displayOrder?: number;
  skills?: SkillTaxonomy[];
}

export interface Education {
  id?: string;
  userId?: string;
  institution: string;
  degree?: string;
  fieldOfStudy?: string;
  startDate?: string;
  endDate?: string;
  description?: string;
  grade?: string;
  displayOrder?: number;
  skills?: SkillTaxonomy[];
}

export interface Certification {
  id?: string;
  userId?: string;
  name: string;
  issuer?: string;
  issuedAt?: string;
  expiresAt?: string;
  credentialUrl?: string;
}

export type LanguageProficiency = 'NATIVE' | 'FLUENT' | 'PROFESSIONAL' | 'CONVERSATIONAL' | 'ELEMENTARY';

export interface SpokenLanguage {
  id?: string;
  userId?: string;
  language: string;
  proficiency: LanguageProficiency;
  displayOrder: number;
}

export type FeedbackType = 'LIKE' | 'DISLIKE' | 'HIDE' | 'MORE_LIKE_THIS' | 'FEWER_LIKE_THIS';

export interface RecommendationFeedback {
  id?: string;
  userId?: string;
  jobId: string;
  feedbackType: FeedbackType;
}

export interface ProfileSocial {
  id?: string;
  userId?: string;
  platform: string;
  url: string;
  username?: string;
  iconKey: string;
  displayOrder: number;
}

export interface ProfileStrength {
  id?: string;
  userId?: string;
  title: string;
  description?: string;
  iconKey: string;
  displayOrder: number;
}
