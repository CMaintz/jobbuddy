import { StructuredDocument } from '../../../core/models/structured-document.model';
import { ResumeData } from '../models/resume-builder.models';

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
      id:    crypto.randomUUID(),
      name:  item.title ?? '',
      level: 3,
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
