import { INITIAL_RESUME_DATA, INITIAL_SETTINGS, ResumeData, ResumeSettings } from '../models/resume-builder.models';
import { toResumeDesign } from './resume-design';

describe('toResumeDesign', () => {
  const t = (key: string) => `[${key}]`;

  function design(data: Partial<ResumeData>, settings: Partial<ResumeSettings> = {}) {
    const full = { ...INITIAL_RESUME_DATA, ...data };
    return toResumeDesign({
      personalInfo: full.personalInfo, socials: full.socials, data: full,
      settings: { ...INITIAL_SETTINGS, ...settings }, skillGroups: [],
    }, t);
  }

  it('prints "present" in the user\'s language for a current job instead of its blank end date', () => {
    // Bug: `current` is ignored, so an ongoing job shows "2019 -" with nothing after it.
    const d = design({ experience: [{
      id: 'e1', title: 'Konsulent', company: 'Firma', location: 'Aarhus', startDate: '2019', endDate: '',
      current: true, description: '<p>Ledte</p>',
    }] });

    expect(d.experience[0]).toEqual(expect.objectContaining({
      title: 'Konsulent', subtitle: 'Firma', start: '2019', end: '[resumeBuilder.cv.present]',
      description: '<p>Ledte</p>',
    }));
  });

  it('keeps the column order and marks switched-off sections hidden', () => {
    // Bug: hidden sections are dropped from the order lists, or visible ones are reported hidden,
    // so the PDF shows a section the user turned off or loses one she kept.
    const d = design({}, {
      leftColumn: [{ id: 'experience', name: 'E', visible: true }, { id: 'summary', name: 'S', visible: false }],
      rightColumn: [{ id: 'skills', name: 'K', visible: true }],
    });

    expect(d.mainOrder).toEqual(['experience', 'summary']);
    expect(d.sideOrder).toEqual(['skills']);
    expect(d.hidden).toEqual(['summary']);
  });

  it('sends the size setting as the multiplier the layouts scale by', () => {
    // Bug: the size keyword is sent as-is or mapped wrongly, so "large" text prints at default size.
    expect(design({}, { fontSize: 'lg' }).theme.scale).toBe(1.15);
    expect(design({}, { fontSize: 'sm' }).theme.scale).toBe(0.85);
  });

  it('uses a social\'s username when it has one, else its URL', () => {
    // Bug: the raw URL is always printed, so "linkedin.com/in/x" becomes a long tracking URL.
    const d = design({ socials: [
      { id: 's1', platform: 'LinkedIn', url: 'https://linkedin.com/in/mette?trk=x', username: 'mette', iconKey: 'linkedin' },
      { id: 's2', platform: 'Website', url: 'maintz.dev', iconKey: 'globe' },
    ] });

    expect(d.socials).toEqual(['mette', 'maintz.dev']);
  });
});
