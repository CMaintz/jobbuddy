export interface Note {
  id: string;
  userId: string;
  jobId: string | null;
  applicationId: string | null;
  content: string;
  createdAt: string;
  updatedAt: string;
}
