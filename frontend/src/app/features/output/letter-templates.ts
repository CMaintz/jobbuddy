/** Visual templates + format metadata for the letter output screen. */

export interface LetterTemplate {
  key: string;
  label: string;
  sub: string;
  bg: string;
  fg: string;
  muted: string;
  divider: string;
  font: string;
  size: number;
  lineH: number;
  headerStyle: 'columns' | 'centered' | 'rail';
  rail: boolean;
  swatchBg: string;
  swatchAccent: string;
}

export const LETTER_TEMPLATES: LetterTemplate[] = [
  {
    key: 'editorial', label: 'output.tpl.editorial.label', sub: 'output.tpl.editorial.sub',
    bg: '#f6f3ec', fg: '#1a1714', muted: '#6b6660', divider: '#d9d2c4',
    font: '"Geist", ui-sans-serif, system-ui', size: 12.5, lineH: 1.65,
    headerStyle: 'columns', rail: false, swatchBg: '#f6f3ec', swatchAccent: '#1a1714',
  },
  {
    key: 'classic', label: 'output.tpl.classic.label', sub: 'output.tpl.classic.sub',
    bg: '#fbfaf6', fg: '#1c1a16', muted: '#5a554e', divider: '#cfc8b9',
    font: '"Source Serif Pro", Charter, Cambria, Georgia, serif', size: 13, lineH: 1.78,
    headerStyle: 'centered', rail: false, swatchBg: '#fbfaf6', swatchAccent: '#5a554e',
  },
  {
    key: 'bold', label: 'output.tpl.bold.label', sub: 'output.tpl.bold.sub',
    bg: '#ffffff', fg: '#0e0e0e', muted: '#5a5a5a', divider: '#e5e0d4',
    font: '"Geist", ui-sans-serif, system-ui', size: 12.5, lineH: 1.62,
    headerStyle: 'rail', rail: true, swatchBg: '#ffffff', swatchAccent: '#f5a623',
  },
];

export type FormatKey = 'app' | 'cl' | 'ua' | 'dm' | 'fu';

export const FORMAT_TO_DOC_TYPE: Record<FormatKey, string> = {
  app: 'APPLICATION_TEXT',
  cl: 'COVER_LETTER',
  ua: 'UNSOLICITED_APPLICATION',
  dm: 'RECRUITER_MESSAGE',
  fu: 'FOLLOW_UP_MESSAGE',
};

/** Sensible word-count ranges per format, used for the length hint. */
export const WORD_TARGETS: Record<FormatKey, [number, number]> = {
  app: [250, 450],
  cl: [200, 400],
  ua: [250, 400],
  dm: [60, 150],
  fu: [40, 120],
};
