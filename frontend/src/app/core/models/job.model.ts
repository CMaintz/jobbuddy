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
  /** ISO date of the stated application deadline. Null/absent = unknown or ASAP. */
  applicationDeadline?: string;
  /** Person the posting names to answer questions about the role. Absent when it names nobody. */
  contact?: JobContact;
  /** false once the posting disappears from crawls or is expired. */
  isActive?: boolean;
}

/** Extracted verbatim from the posting — never inferred, so any field may be missing. */
export interface JobContact {
  name?: string;
  title?: string;
  email?: string;
  phone?: string;
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
