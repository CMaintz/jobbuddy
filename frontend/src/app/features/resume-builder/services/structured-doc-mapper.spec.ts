import { StructuredDocument } from '../../../core/models/structured-document.model';
import { structuredDocToResumeData, structuredDocToLayout } from './structured-doc-mapper';

/** Minimal document shaped like the backend's CV render model. */
function doc(sections: StructuredDocument['sections']): StructuredDocument {
  return {
    documentType: 'CV',
    exportMode: 'ATS',
    templateId: 'cv-ats-classic',
    identity: { name: 'Test Person' } as StructuredDocument['identity'],
    sections,
    atsReport: undefined,
  } as StructuredDocument;
}

function section(
  type: string, heading: string,
  extra: { body?: string; items?: { title?: string; sourceId?: string }[] } = {},
) {
  return {
    id: type, type, heading,
    body: extra.body ?? undefined,
    items: (extra.items ?? []) as never,
  } as StructuredDocument['sections'][number];
}

describe('structuredDocToResumeData', () => {
  it('maps the typed sections onto their fields', () => {
    const data = structuredDocToResumeData(doc([
      section('profile', 'Profil', { body: 'Backend-udvikler med fokus på drift.' }),
      section('experience', 'Erhvervserfaring', {
        items: [{ title: 'Platform engineer', sourceId: 'exp-1' }],
      }),
      section('skills', 'Kompetencer', { items: [{ title: 'Java' }, { title: 'Kubernetes' }] }),
    ]));

    expect(data.personalInfo.summary).toBe('Backend-udvikler med fokus på drift.');
    expect(data.experience.length).toBe(1);
    expect(data.experience[0].id).toBe('exp-1');
    expect(data.skills.map(s => s.name)).toEqual(['Java', 'Kubernetes']);
  });

  it('carries a section it has no typed home for into customSections', () => {
    // Interests and the Danish references line were silently dropped before this.
    const data = structuredDocToResumeData(doc([
      section('interests', 'Fritidsinteresser', { items: [{ title: 'Klatring' }, { title: 'Brætspil' }] }),
      section('references', 'Referencer', { body: 'Referencer oplyses gerne efter aftale.' }),
    ]));

    expect(data.customSections?.length).toBe(2);
    const interests = data.customSections?.find(s => s.heading === 'Fritidsinteresser');
    expect(interests?.items?.map(i => i.text)).toEqual(['Klatring', 'Brætspil']);
    const references = data.customSections?.find(s => s.heading === 'Referencer');
    expect(references?.body).toBe('Referencer oplyses gerne efter aftale.');
  });

  it('does not duplicate a typed section into customSections', () => {
    const data = structuredDocToResumeData(doc([
      section('skills', 'Kompetencer', { items: [{ title: 'Java' }] }),
      section('languages', 'Sprog', { items: [{ title: 'Dansk (Native)' }] }),
    ]));
    expect(data.customSections).toEqual([]);
  });

  it('drops a section that would render as a bare heading', () => {
    const data = structuredDocToResumeData(doc([section('interests', 'Fritidsinteresser')]));
    expect(data.customSections).toEqual([]);
  });

  it('survives a document with no sections at all', () => {
    const data = structuredDocToResumeData(doc([]));
    expect(data.experience).toEqual([]);
    expect(data.customSections).toEqual([]);
    expect(data.personalInfo.summary).toBe('');
  });
});

describe('structuredDocToLayout', () => {
  it('takes the main-column order from the document, so stage-aware ordering wins', () => {
    // A new grad's document leads with education; the builder default leads with experience.
    const layout = structuredDocToLayout(doc([
      section('profile', 'Profile'),
      section('education', 'Education'),
      section('projects', 'Projects'),
      section('experience', 'Experience'),
    ]));
    expect(layout.leftColumn.map(c => c.id)).toEqual(['summary', 'education', 'projects', 'experience']);
  });

  it('appends main-column sections the document did not mention', () => {
    const layout = structuredDocToLayout(doc([section('experience', 'Experience')]));
    expect(layout.leftColumn.map(c => c.id)).toEqual(['experience', 'summary', 'education', 'projects']);
  });

  it('ignores sections that do not belong to the main column', () => {
    const layout = structuredDocToLayout(doc([
      section('skills', 'Skills'),
      section('interests', 'Interests'),
      section('experience', 'Experience'),
    ]));
    expect(layout.leftColumn.map(c => c.id)).toEqual(['experience', 'summary', 'education', 'projects']);
  });
});
