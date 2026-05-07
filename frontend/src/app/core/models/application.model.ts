export type ApplicationStatus =
  | 'SAVED' | 'PREPARING' | 'APPLIED' | 'RECRUITER_CONTACT'
  | 'INTERVIEW' | 'TECHNICAL_TEST' | 'FINAL_ROUND' | 'OFFER'
  | 'REJECTED' | 'ARCHIVED';

export interface Application {
  id: string;
  jobId: string;
  status: ApplicationStatus;
  appliedAt?: string;
  recruiterName?: string;
  recruiterEmail?: string;
  matchScore?: number;
  notes?: string;
  createdAt: string;
  updatedAt: string;
}
