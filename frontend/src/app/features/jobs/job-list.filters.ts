export const JOB_FILTERS_KEY = 'aa_job_filters';

export interface JobFilters {
  remoteTypes: string[];
  employmentTypes: string[];
  seniorities: string[];
  industries: string[];
  salaryMin: number | null;
  salaryMax: number | null;
  technologies: string;
  hideApplied: boolean;
}

export const DEFAULT_JOB_FILTERS: JobFilters = {
  remoteTypes: [],
  employmentTypes: [],
  seniorities: [],
  industries: [],
  salaryMin: null,
  salaryMax: null,
  technologies: '',
  hideApplied: false,
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

export const INDUSTRY_OPTIONS = [
  { value: 'SOFTWARE_IT', label: 'Software / IT' },
  { value: 'DATA_ANALYTICS', label: 'Data & Analytics' },
  { value: 'DESIGN_UX', label: 'Design / UX' },
  { value: 'MARKETING', label: 'Marketing' },
  { value: 'SALES', label: 'Sales' },
  { value: 'FINANCE', label: 'Finance' },
  { value: 'HR', label: 'HR' },
  { value: 'ENGINEERING', label: 'Engineering' },
  { value: 'OPERATIONS_LOGISTICS', label: 'Operations / Logistics' },
  { value: 'CUSTOMER_SERVICE', label: 'Customer Service' },
  { value: 'LEGAL', label: 'Legal' },
  { value: 'HEALTHCARE', label: 'Healthcare' },
  { value: 'MANAGEMENT', label: 'Management' },
  { value: 'EDUCATION', label: 'Education' },
  { value: 'CREATIVE_MEDIA', label: 'Creative / Media' },
];
