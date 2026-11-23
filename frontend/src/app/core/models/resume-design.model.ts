/**
 * A CV as the resume builder lays it out, in the shape the backend renders (`ResumeDesign`).
 * Built from the display variant of the builder's state, so anonymisation already applies, and
 * with headings already translated: the backend prints what it is given.
 */
export interface ResumeDesign {
  layout: string;
  pageSize: 'A4' | 'Letter';
  theme: {
    color: string;
    textColor?: string;
    font: string;
    scale: number;
    lineSpacing?: number;
    sectionSpacing?: number;
  };
  photo: { url?: string; shape?: string; size?: number; placement?: string };
  showSkillLevel: boolean;
  labels: Record<string, string>;
  person: { name: string; title: string; email: string; phone: string; location: string };
  summary: string;
  experience: ResumeDesignEntry[];
  education: ResumeDesignEntry[];
  projects: { title: string; date: string; description: string; skills: string[] }[];
  skillGroups: { category: string | null; skills: { name: string; level?: number }[] }[];
  languages: { name: string; proficiency: string }[];
  certifications: { name: string; issuer: string; date: string }[];
  strengths: { title: string; description: string }[];
  socials: string[];
  customSections: { heading: string; body?: string; items: string[] }[];
  mainOrder: string[];
  sideOrder: string[];
  hidden: string[];
  sectionStyles: Record<string, {
    font?: string; bold?: boolean; italic?: boolean; color?: string; scale?: number;
  }>;
}

export interface ResumeDesignEntry {
  title: string;
  subtitle: string;
  location: string;
  start: string;
  end: string;
  description?: string;
  skills: string[];
}
