export interface GeneratedDocument {
  id: string;
  userId: string;
  applicationId?: string;
  jobId?: string;
  documentType: string;
  content: string;
  promptTemplateId?: string;
  cvVersionId?: string;
  modelUsed?: string;
  createdAt: string;
}
