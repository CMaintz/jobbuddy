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
  coverLetterText?: string;
  applicationText?: string;
  recruiterMessage?: string;
  recruiterReply?: string;
  matchScore?: number;
  notes?: string;
  /** Feedback received from the company (rejection reasons, interviewer comments). */
  outcomeFeedback?: string;
  /** What to do differently next time — feeds back into future generations. */
  outcomeLessons?: string;
  jobTitle?: string;
  jobCompanyName?: string;
  /** The user's own application deadline, `YYYY-MM-DD`. */
  deadline?: string | null;
  /** Interview time as the user typed it, local wall-clock `YYYY-MM-DDTHH:mm[:ss]` with no zone. */
  interviewAt?: string | null;
  /** The statuses the server accepts as the next move; empty once the application is closed. */
  nextStatuses?: ApplicationStatus[];
  createdAt: string;
  updatedAt: string;
}
