export interface FontOption {
  label: string;
  value: string;
  category: 'sans' | 'serif' | 'mono' | 'display';
}

export const FONT_FAMILIES: FontOption[] = [
  { label: 'Inter', value: 'Inter', category: 'sans' },
  { label: 'Roboto', value: 'Roboto', category: 'sans' },
  { label: 'Open Sans', value: 'Open Sans', category: 'sans' },
  { label: 'Lato', value: 'Lato', category: 'sans' },
  { label: 'Nunito', value: 'Nunito', category: 'sans' },
  { label: 'Poppins', value: 'Poppins', category: 'sans' },
  { label: 'Montserrat', value: 'Montserrat', category: 'sans' },
  { label: 'Source Sans 3', value: 'Source Sans 3', category: 'sans' },
  { label: 'Noto Sans', value: 'Noto Sans', category: 'sans' },
  { label: 'Khand', value: 'Khand', category: 'display' },
  { label: 'Georgia', value: 'Georgia', category: 'serif' },
  { label: 'Times New Roman', value: 'Times New Roman', category: 'serif' },
  { label: 'Merriweather', value: 'Merriweather', category: 'serif' },
  { label: 'Playfair Display', value: 'Playfair Display', category: 'serif' },
  { label: 'Lora', value: 'Lora', category: 'serif' },
  { label: 'Arial', value: 'Arial', category: 'sans' },
  { label: 'Helvetica', value: 'Helvetica', category: 'sans' },
  { label: 'Courier New', value: 'Courier New', category: 'mono' },
  { label: 'JetBrains Mono', value: 'JetBrains Mono', category: 'mono' },
];
