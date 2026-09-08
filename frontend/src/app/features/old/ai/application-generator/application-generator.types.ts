export interface ChatMessage {
  role: 'user' | 'assistant';
  text: string;
}

export interface LanguageOption {
  code: string;
  label: string;
}

export type ActiveGeneratedDocument = 'single' | 'application' | 'cv';

export const APPLICATION_GENERATOR_LANGUAGES: LanguageOption[] = [
  { code: 'Danish', label: 'Danish' },
  { code: 'English', label: 'English' },
  { code: 'Swedish', label: 'Swedish' },
  { code: 'Norwegian', label: 'Norwegian' },
  { code: 'German', label: 'German' },
];
