import { isSectionCategory, sectionCategory, templatesForSection } from './cv-section-category';
import { PromptTemplate } from './prompt-template.model';

function template(overrides: Partial<PromptTemplate>): PromptTemplate {
  return {
    id: 't1', name: 'T', userPrompt: 'p', isPublic: false, isSystem: false, isProtected: false,
    isDefault: false, isSelectedDefault: false, versionNumber: 1, createdAt: '', updatedAt: '',
    ...overrides,
  };
}

describe('cv-section-category', () => {
  it('maps every CV section key to its category', () => {
    expect(sectionCategory('profile')).toBe('CV_PROFILE');
    expect(sectionCategory('skills')).toBe('CV_SKILLS');
    expect(sectionCategory('experience')).toBe('CV_EXPERIENCE');
    expect(sectionCategory('projects')).toBe('CV_PROJECTS');
    expect(sectionCategory('education')).toBe('CV_EDUCATION');
    expect(sectionCategory('certifications')).toBe('CV_CERTIFICATIONS');
  });

  it('has no category for an unknown or missing section key', () => {
    expect(sectionCategory('hobbies')).toBeUndefined();
    expect(sectionCategory(undefined)).toBeUndefined();
  });

  it('tells section categories apart from document categories', () => {
    expect(isSectionCategory('CV_EXPERIENCE')).toBeTrue();
    expect(isSectionCategory('CV_TAILORING')).toBeFalse();
    expect(isSectionCategory(undefined)).toBeFalse();
  });

  it("lists only the section's templates, the one in use first", () => {
    const all = [
      template({ id: 'star', category: 'CV_EXPERIENCE' }),
      template({ id: 'letter', category: 'COVER_LETTER', isSelectedDefault: true }),
      template({ id: 'xyz', category: 'CV_EXPERIENCE', isSelectedDefault: true }),
    ];
    expect(templatesForSection(all, 'experience').map(t => t.id)).toEqual(['xyz', 'star']);
    expect(templatesForSection(all, undefined)).toEqual([]);
  });

  it('puts a starred template above the one in use and the most used', () => {
    const all = [
      template({ id: 'used', category: 'CV_EXPERIENCE', usageCount: 9 }),
      template({ id: 'inUse', category: 'CV_EXPERIENCE', isSelectedDefault: true }),
      template({ id: 'starred', category: 'CV_EXPERIENCE', favourite: true }),
    ];
    expect(templatesForSection(all, 'experience').map(t => t.id)).toEqual(['starred', 'inUse', 'used']);
  });
});
