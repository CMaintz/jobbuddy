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

export type FeedbackType = 'LIKE' | 'DISLIKE' | 'HIDE' | 'MORE_LIKE_THIS' | 'FEWER_LIKE_THIS';

export interface RecommendationFeedback {
  id?: string;
  userId?: string;
  jobId: string;
  feedbackType: FeedbackType;
}
