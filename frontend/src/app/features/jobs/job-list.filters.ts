export const JOB_FILTERS_KEY = 'aa_job_filters';

export interface JobFilters {
  remoteTypes: string[];
  employmentTypes: string[];
  seniorities: string[];
  salaryMin: number | null;
  salaryMax: number | null;
  technologies: string;
}

export const DEFAULT_JOB_FILTERS: JobFilters = {
  remoteTypes: [],
  employmentTypes: [],
  seniorities: [],
  salaryMin: null,
  salaryMax: null,
  technologies: ''
};

export const REMOTE_OPTIONS = [
  { value: 'FULLY_REMOTE', label: 'Fully remote' },
  { value: 'HYBRID', label: 'Hybrid' },
  { value: 'ON_SITE', label: 'On-site' }
];

export const EMPLOYMENT_OPTIONS = [
  { value: 'FULL_TIME', label: 'Full-time' },
  { value: 'PART_TIME', label: 'Part-time' },
  { value: 'CONTRACT', label: 'Contract' },
  { value: 'FREELANCE', label: 'Freelance' }
];

export const SENIORITY_OPTIONS = [
  { value: 'JUNIOR', label: 'Junior' },
  { value: 'MID', label: 'Mid' },
  { value: 'SENIOR', label: 'Senior' },
  { value: 'LEAD', label: 'Lead' },
  { value: 'PRINCIPAL', label: 'Principal' }
];
