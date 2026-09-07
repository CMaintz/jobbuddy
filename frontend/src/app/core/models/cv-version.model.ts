export interface CvVersion {
  id: string;
  userId: string;
  name: string;
  content: string;
  format: string;
  fileUrl?: string;
  isPrimary: boolean;
  versionNumber: number;
  createdAt: string;
  updatedAt: string;
}
