export interface DocumentSizeOption {
  label: string;
  value: 'A4' | 'Letter';
  width: number;   // px at 96dpi
  height: number;
}

export const DOCUMENT_SIZES: DocumentSizeOption[] = [
  { label: 'A4', value: 'A4', width: 794, height: 1123 },
  { label: 'Letter', value: 'Letter', width: 816, height: 1056 },
];
