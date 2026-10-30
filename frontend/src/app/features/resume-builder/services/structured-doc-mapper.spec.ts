import { StructuredDocument } from '../../../core/models/structured-document.model';
import { structuredDocToResumeData, structuredDocToLayout } from './structured-doc-mapper';

type Section = StructuredDocument['sections'][number];
type Item = NonNullable<Section['items']>[number];

/** Minimal document shaped like the backend's CV render model. */
function doc(
  sections: StructuredDocument['sections'],
  identity: StructuredDocument['identity'] = { name: 'Test Person' },
): StructuredDocument {
  return {
    documentType: 'CV',
    exportMode: 'ATS',
    templateId: 'cv-ats-classic',
    identity,
    sections,
    atsReport: undefined,
  };
}

function section(type: string, heading: string, extra: { body?: string; items?: Item[] } = {}): Section {
  return { id: type, type, heading, body: extra.body, items: extra.items ?? [] };
}

describe('structuredDocToResumeData', () => {
  // Every source value is distinct, so mapping a field from the wrong source (company from
  // title instead of subtitle, say) changes the result.
  it('maps every identity field and the profile body onto personalInfo', () => {
    const data = structuredDocToResumeData(doc(
      [section('profile', 'Profil', { body: 'Backend-udvikler med fokus på drift.' })],
      {
        name: 'Ada Lovelace',
        headline: 'Platform engineer',
        email: 'ada@example.dk',
        phone: '+45 12 34 56 78',
        location: 'Aarhus',
        websiteUrl: 'https://ada.dk',
        linkedinUrl: 'https://linkedin.com/in/ada',
        githubUrl: 'https://github.com/ada',
        profileImageUrl: 'https://cdn.example/ada.png',
      },
    ));

    expect(data.personalInfo).toEqual({
      fullName: 'Ada Lovelace',
      title: 'Platform engineer',
      email: 'ada@example.dk',
      phone: '+45 12 34 56 78',
      location: 'Aarhus',
      website: 'https://ada.dk',
      linkedin: 'https://linkedin.com/in/ada',
      github: 'https://github.com/ada',
      twitter: '',
      summary: 'Backend-udvikler med fokus på drift.',
      photoUrl: 'https://cdn.example/ada.png',
    });
  });

  it('maps an experience item field by field, preferring description and skills', () => {
    const data = structuredDocToResumeData(doc([section('experience', 'Erhvervserfaring', {
      items: [{
        sourceId: 'exp-1', title: 'Platform engineer', subtitle: 'Netcompany', location: 'Aarhus',
        dateRange: '2021 - 2024', description: 'Ran the platform.', bullets: ['Unused bullet'],
        skills: ['Kubernetes'], technologies: ['Unused tech'],
      }],
    })]));

    expect(data.experience).toEqual([{
      id: 'exp-1', title: 'Platform engineer', company: 'Netcompany', location: 'Aarhus',
      startDate: '', endDate: '2021 - 2024', current: false,
      description: 'Ran the platform.', skills: ['Kubernetes'],
    }]);
  });

  it('falls back to joined bullets and technologies when description and skills are absent', () => {
    const data = structuredDocToResumeData(doc([section('experience', 'Erhvervserfaring', {
      items: [{
        sourceId: 'exp-1', title: 'Engineer', bullets: ['Cut deploys to minutes', 'Owned on-call'],
        technologies: ['Go', 'Terraform'],
      }],
    })]));

    expect(data.experience[0].description).toBe('Cut deploys to minutes\nOwned on-call');
    expect(data.experience[0].skills).toEqual(['Go', 'Terraform']);
  });

  it('maps an education item onto degree and school', () => {
    const data = structuredDocToResumeData(doc([section('education', 'Uddannelse', {
      items: [{
        sourceId: 'edu-1', title: 'MSc Computer Science', subtitle: 'Aarhus Universitet',
        location: 'Aarhus', dateRange: '2019 - 2021', skills: ['Algorithms'],
      }],
    })]));

    expect(data.education).toEqual([{
      id: 'edu-1', degree: 'MSc Computer Science', school: 'Aarhus Universitet', location: 'Aarhus',
      startDate: '', endDate: '2019 - 2021', current: false, skills: ['Algorithms'],
    }]);
  });

  it('maps a project using its first link and the bullet and technology fallbacks', () => {
    const data = structuredDocToResumeData(doc([section('projects', 'Projekter', {
      items: [{
        sourceId: 'proj-1', title: 'Jobbuddy', links: ['https://jobbuddy.dk', 'https://github.com/x'],
        dateRange: '2025', bullets: ['Built the apply flow'], technologies: ['Angular'],
      }],
    })]));

    expect(data.projects).toEqual([{
      id: 'proj-1', name: 'Jobbuddy', link: 'https://jobbuddy.dk', date: '2025',
      description: 'Built the apply flow', skills: ['Angular'],
    }]);
  });

  it('maps a certification onto name, issuer and date', () => {
    const data = structuredDocToResumeData(doc([section('certifications', 'Certificeringer', {
      items: [{ sourceId: 'cert-1', title: 'CKA', subtitle: 'CNCF', dateRange: '2023' }],
    })]));

    expect(data.certifications).toEqual([{
      id: 'cert-1', name: 'CKA', issuer: 'CNCF', date: '2023', link: undefined,
    }]);
  });

  it('keeps each skill name with its category', () => {
    const data = structuredDocToResumeData(doc([section('skills', 'Kompetencer', {
      items: [{ title: 'Java', category: 'Sprog' }, { title: 'Kubernetes' }],
    })]));

    expect(data.skills.map(s => [s.name, s.category])).toEqual([['Java', 'Sprog'], ['Kubernetes', undefined]]);
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
