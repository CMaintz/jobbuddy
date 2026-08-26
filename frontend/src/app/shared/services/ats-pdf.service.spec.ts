import { resumeDataToAts, structuredDocToAts } from './ats-pdf.service';
import { INITIAL_RESUME_DATA, INITIAL_SETTINGS, ResumeData, ResumeSettings }
  from '../../features/resume-builder/models/resume-builder.models';
import { StructuredDocument } from '../../core/models/structured-document.model';

function resume(overrides: Partial<ResumeData> = {}): ResumeData {
  return { ...structuredClone(INITIAL_RESUME_DATA), ...overrides };
}

function settings(overrides: Partial<ResumeSettings> = {}): ResumeSettings {
  return { ...structuredClone(INITIAL_SETTINGS), ...overrides };
}

describe('resumeDataToAts', () => {
  it('exports custom sections, so a section the builder holds still reaches the PDF', () => {
    const model = resumeDataToAts(resume({
      customSections: [
        { id: 'i', heading: 'Fritidsinteresser', items: [{ id: '1', text: 'Klatring' }] },
        { id: 'r', heading: 'Referencer', body: 'Referencer oplyses gerne efter aftale.' },
      ],
    }));

    const interests = model.sections.find(s => s.heading === 'Fritidsinteresser');
    expect(interests?.lines).toEqual(['•  Klatring']);
    const references = model.sections.find(s => s.heading === 'Referencer');
    expect(references?.lines).toEqual(['Referencer oplyses gerne efter aftale.']);
  });

  it('omits a custom section with neither body nor items', () => {
    const model = resumeDataToAts(resume({ customSections: [{ id: 'x', heading: 'Empty' }] }));
    expect(model.sections.find(s => s.heading === 'Empty')).toBeUndefined();
  });

  it('follows the builder column order rather than a fixed one', () => {
    const data = resume({
      experience: [{ id: 'e', title: 'Engineer', company: 'Acme', location: '', startDate: '2020',
        endDate: '2024', current: false, description: '' }],
      skills: [{ id: 's', name: 'Java' }],
    });
    const educationFirst = settings({
      leftColumn: [
        { id: 'skills', name: 'Skills', visible: true },
        { id: 'experience', name: 'Experience', visible: true },
      ],
      rightColumn: [],
    });
    const model = resumeDataToAts(data, data.personalInfo, educationFirst);
    expect(model.sections.map(s => s.heading)).toEqual(['Skills', 'Experience']);
  });

  it('drops sections the user hid in the builder', () => {
    const data = resume({ skills: [{ id: 's', name: 'Java' }] });
    const hidden = settings({
      leftColumn: [], rightColumn: [{ id: 'skills', name: 'Skills', visible: false }],
    });
    expect(resumeDataToAts(data, data.personalInfo, hidden).sections).toEqual([]);
  });
});

describe('structuredDocToAts', () => {
  const doc = {
    documentType: 'CV',
    exportMode: 'ATS',
    templateId: 'cv-ats-classic',
    identity: { name: 'Test Person', email: 'a@b.dk' },
    sections: [
      { id: 'profile', type: 'profile', heading: 'Profil', body: 'Kort profiltekst.', items: [] },
      { id: 'interests', type: 'interests', heading: 'Fritidsinteresser',
        items: [{ title: 'Klatring' }] },
      { id: 'references', type: 'references', heading: 'Referencer',
        body: 'Referencer oplyses gerne efter aftale.', items: [] },
    ],
  } as unknown as StructuredDocument;

  it('lifts the profile into the summary and keeps the untyped sections', () => {
    const model = structuredDocToAts(doc);
    expect(model.summary).toBe('Kort profiltekst.');
    expect(model.sections.map(s => s.heading)).toEqual(['Fritidsinteresser', 'Referencer']);
    expect(model.sections[1].lines).toEqual(['Referencer oplyses gerne efter aftale.']);
  });

  it('falls back to a placeholder name rather than rendering an empty header', () => {
    const nameless = { ...doc, identity: {} } as unknown as StructuredDocument;
    expect(structuredDocToAts(nameless).name).toBe('Curriculum Vitae');
  });
});
