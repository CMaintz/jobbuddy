export interface Job {
  id: string;
  source?: string;
  url: string;
  title: string;
  companyName?: string;
  /** In a list response this is only the opening — see descriptionTruncated. */
  descriptionClean?: string;
  /** True when descriptionClean holds only the opening and the rest is a fetch away. */
  descriptionTruncated?: boolean;
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
  /**
   * What the posting asks for, in its own words. A list response carries the demands only;
   * the preferences arrive with the full posting.
   */
  requirements?: JobRequirement[];
}

/**
 * One ask from the posting, as enrichment extracted it. `kind` says how it could be
 * verified at all — only SKILL is something a keyword check can settle, which is why the
 * rest are listed rather than scored.
 */
export interface JobRequirement {
  text: string;
  tier: 'REQUIRED' | 'PREFERRED';
  kind: 'SKILL' | 'EXPERIENCE' | 'EDUCATION' | 'LANGUAGE' | 'CERTIFICATION' | 'OTHER';
  /** The short label, when the ask is a nameable skill. */
  skill?: string;
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
  job: Job;
  hardConstraintPassed: boolean;
  totalScore: number;
  matchLabel: 'EXCELLENT' | 'STRONG' | 'MODERATE' | 'WEAK';
  matchReasons: string[];
}
