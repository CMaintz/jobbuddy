import { ResumeDesign, ResumeDesignEntry } from '../../../core/models/resume-design.model';
import {
  PersonalInfo, ResumeData, ResumeSettings, ResumeSkill, ResumeSocial,
} from '../models/resume-builder.models';

/** What the builder shows: the display variants, so an anonymised CV renders anonymised. */
export interface ResumeDesignSource {
  personalInfo: PersonalInfo;
  socials: ResumeSocial[];
  data: ResumeData;
  settings: ResumeSettings;
  skillGroups: { category: string | null; skills: ResumeSkill[] }[];
}

const FONT_SCALES: Record<ResumeSettings['fontSize'], number> = { sm: 0.85, md: 1, lg: 1.15, xl: 1.3 };

/** The headings the layouts print, by the key `resume.typ` reads them under. */
const LABEL_KEYS = [
  'summary', 'experience', 'professionalExperience', 'education', 'projects', 'skills', 'coreSkills',
  'languages', 'certifications', 'strengths', 'links', 'contact',
] as const;

/**
 * The builder's state as the design the backend renders for both the preview and the PDF.
 * `t` translates a key; headings and "present" are resolved here because the backend prints
 * exactly what it receives.
 */
export function toResumeDesign(src: ResumeDesignSource, t: (key: string) => string): ResumeDesign {
  const { personalInfo: pi, data, settings } = src;
  const present = t('resumeBuilder.cv.present');
  const columnIds = (cols: ResumeSettings['leftColumn']) => (cols ?? []).map(c => c.id);
  const entry = (title: string, subtitle: string, e: {
    location: string; startDate: string; endDate: string; current: boolean; skills?: string[];
  }, description?: string): ResumeDesignEntry => ({
    title, subtitle, location: e.location, start: e.startDate,
    end: e.current ? present : e.endDate, description, skills: e.skills ?? [],
  });

  return {
    layout: settings.template,
    pageSize: settings.documentSize,
    theme: {
      color: settings.themeColor,
      textColor: settings.textColor,
      font: settings.fontFamily,
      scale: FONT_SCALES[settings.fontSize] ?? 1,
      lineSpacing: settings.lineSpacing,
      sectionSpacing: settings.sectionSpacing,
    },
    photo: {
      url: pi.photoUrl, shape: settings.photoStyle, size: settings.photoSize, placement: settings.photoPlacement,
    },
    showSkillLevel: settings.showSkillLevel,
    labels: Object.fromEntries(LABEL_KEYS.map(key => [key, t(`resumeBuilder.cv.${key}`)])),
    person: { name: pi.fullName, title: pi.title, email: pi.email, phone: pi.phone, location: pi.location },
    summary: pi.summary,
    experience: data.experience.map(e => entry(e.title, e.company, e, e.description)),
    education: data.education.map(e => entry(e.degree, e.school, e)),
    projects: data.projects.map(p => ({
      title: p.name, date: p.date, description: p.description, skills: p.skills ?? [],
    })),
    skillGroups: src.skillGroups.map(g => ({
      category: g.category, skills: g.skills.map(s => ({ name: s.name, level: s.level })),
    })),
    languages: data.languages.map(l => ({ name: l.name, proficiency: l.proficiency })),
    certifications: data.certifications.map(c => ({ name: c.name, issuer: c.issuer, date: c.date })),
    strengths: data.strengths.map(s => ({ title: s.title, description: s.description })),
    socials: src.socials.map(s => s.username || s.url).filter(Boolean),
    customSections: (data.customSections ?? []).map(c => ({
      heading: c.heading, body: c.body, items: (c.items ?? []).map(i => i.text),
    })),
    mainOrder: columnIds(settings.leftColumn),
    sideOrder: columnIds(settings.rightColumn),
    hidden: [...(settings.leftColumn ?? []), ...(settings.rightColumn ?? [])].filter(c => !c.visible).map(c => c.id),
    sectionStyles: Object.fromEntries(Object.entries(settings.sectionTypography ?? {}).map(([id, s]) => [id, {
      font: s.fontFamily, bold: s.bold, italic: s.italic, color: s.color, scale: s.sizeScale,
    }])),
  };
}
