export interface PdfTemplate {
  id: string;
  userId?: string;
  name: string;
  description?: string;
  documentType: string;
  htmlTemplate: string;
  cssStyles?: string;
  isSystem: boolean;
  isActive: boolean;
  createdAt?: string;
}
