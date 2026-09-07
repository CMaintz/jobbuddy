export interface Job {
  id: string;
  source?: string;
  url: string;
  title: string;
  companyName?: string;
  descriptionClean?: string;
  employmentType?: string;
  seniority?: string;
  remoteType?: string;
  location?: string;
  municipality?: string;
  salaryMin?: number;
  salaryMax?: number;
  currency?: string;
  technologies?: string[];
  skills?: string[];
  languages?: string[];
  postedAt?: string;
  aiSummary?: string;
  aiTags?: string[];
  duplicateGroupId?: string;
  jobCategory?: string;
}

export interface JobSearchResult {
  jobs: Job[];
  total: number;
  page: number;
  size: number;
  facets?: Record<string, unknown>;
}

export interface MatchResult {
  jobId: string;
  userId: string;
  job: Job;
  hardConstraintPassed: boolean;
  semanticScore: number;
  totalScore: number;
  matchLabel: 'EXCELLENT' | 'STRONG' | 'MODERATE' | 'WEAK';
  matchReasons: string[];
}
