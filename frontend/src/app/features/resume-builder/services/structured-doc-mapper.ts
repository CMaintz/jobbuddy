import { StructuredDocument } from '../../../core/models/structured-document.model';
import {
  ResumeData, ResumeSettings, SectionConfig, DEFAULT_LEFT_COLUMN,
} from '../models/resume-builder.models';

/** Section ids that live in the resume-builder's main (left) column. */
const MAIN_SECTION_IDS = DEFAULT_LEFT_COLUMN.map(c => c.id);

/**
 * Derives the builder's default main-column order from the backend document's section order.
 * The backend orders sections by the candidate's career stage (new grads lead with education +
 * projects), so that stage-aware ordering becomes the initial layout — the user can still drag
 * to reorder. Only the main column is derived; the sidebar keeps its default order.
 */
export function structuredDocToLayout(doc: StructuredDocument): Pick<ResumeSettings, 'leftColumn'> {
  const docOrder = (doc.sections ?? []).map(s => (s.type === 'profile' ? 'summary' : s.type));
  const ordered: string[] = [];
  const seen = new Set<string>();
  for (const id of docOrder) {
    if (MAIN_SECTION_IDS.includes(id) && !seen.has(id)) { ordered.push(id); seen.add(id); }
  }
  for (const c of DEFAULT_LEFT_COLUMN) if (!seen.has(c.id)) ordered.push(c.id);
  const byId = new Map(DEFAULT_LEFT_COLUMN.map(c => [c.id, c]));
  const leftColumn: SectionConfig[] = ordered.map(
    id => byId.get(id) ?? { id, name: id, visible: true });
  return { leftColumn };
}

/**
 * Maps an AI-generated structured CV onto the resume builder's data model so
 * a tailored CV can be opened as an editable draft.
 */
export function structuredDocToResumeData(doc: StructuredDocument): ResumeData {
  const id = doc.identity ?? {};
  const sections = doc.sections ?? [];
  const find = (type: string) => sections.find(s => s.type === type);

  return {
    personalInfo: {
      fullName: id.name      ?? '',
      title:    id.headline  ?? '',
      email:    id.email     ?? '',
      phone:    id.phone     ?? '',
      location: id.location  ?? '',
      website:  id.websiteUrl  ?? '',
      linkedin: id.linkedinUrl ?? '',
      github:   id.githubUrl   ?? '',
      twitter:  '',
      summary:  find('profile')?.body ?? '',
      photoUrl: id.profileImageUrl,
    },
    experience: (find('experience')?.items ?? []).map(item => ({
      id:          item.sourceId ?? crypto.randomUUID(),
      title:       item.title    ?? '',
      company:     item.subtitle ?? '',
      location:    item.location ?? '',
      startDate:   '',
      endDate:     item.dateRange ?? '',
      current:     false,
      description: item.description ?? (item.bullets ?? []).join('\n'),
      skills:      item.skills ?? item.technologies ?? [],
    })),
    education: (find('education')?.items ?? []).map(item => ({
      id:        item.sourceId ?? crypto.randomUUID(),
      degree:    item.title    ?? '',
      school:    item.subtitle ?? '',
      location:  item.location ?? '',
      startDate: '',
      endDate:   item.dateRange ?? '',
      current:   false,
      skills:    item.skills ?? [],
    })),
    projects: (find('projects')?.items ?? []).map(item => ({
      id:          item.sourceId ?? crypto.randomUUID(),
      name:        item.title    ?? '',
      link:        (item.links ?? [])[0] ?? '',
      date:        item.dateRange ?? '',
      description: item.description ?? (item.bullets ?? []).join('\n'),
      skills:      item.skills ?? item.technologies ?? [],
    })),
    skills: (find('skills')?.items ?? []).map(item => ({
      id:       crypto.randomUUID(),
      name:     item.title ?? '',
      level:    3,
      category: item.category,
    })),
    languages: (find('languages')?.items ?? []).map(item => ({
      id:          crypto.randomUUID(),
      name:        item.title ?? '',
      proficiency: 'Intermediate' as const,
    })),
    certifications: (find('certifications')?.items ?? []).map(item => ({
      id:     item.sourceId ?? crypto.randomUUID(),
      name:   item.title    ?? '',
      issuer: item.subtitle ?? '',
      date:   item.dateRange ?? '',
      link:   undefined,
    })),
    strengths: [],
    socials:   [],
  };
}
