import { of } from 'rxjs';
import { inUseTemplate, makeDefault } from './cv-section-templates-panel.component';
import { PromptApiService } from '../../core/api/prompt.api';
import { PromptTemplate } from '../../core/models/prompt-template.model';

function template(overrides: Partial<PromptTemplate>): PromptTemplate {
  return {
    id: 't1', name: 'T', userPrompt: 'p', isPublic: false, isSystem: false, isProtected: false,
    isDefault: false, isSelectedDefault: false, versionNumber: 1, createdAt: '', updatedAt: '',
    category: 'CV_EXPERIENCE', ...overrides,
  };
}

describe('cv section templates panel', () => {
  let api: jasmine.SpyObj<PromptApiService>;

  beforeEach(() => {
    api = jasmine.createSpyObj<PromptApiService>('PromptApiService', ['selectDefault', 'resetDefault']);
    api.selectDefault.and.returnValue(of(undefined));
    api.resetDefault.and.returnValue(of(undefined));
  });

  it("clears the user's choice when the app default is picked", () => {
    makeDefault(api, template({ id: 'app', isDefault: true })).subscribe();
    expect(api.resetDefault).toHaveBeenCalledWith('CV_EXPERIENCE');
    expect(api.selectDefault).not.toHaveBeenCalled();
  });

  it('records any other template as the choice', () => {
    makeDefault(api, template({ id: 'star' })).subscribe();
    expect(api.selectDefault).toHaveBeenCalledWith('star');
    expect(api.resetDefault).not.toHaveBeenCalled();
  });

  it('preselects the template in use, falling back to the app default', () => {
    const app = template({ id: 'app', isDefault: true });
    const star = template({ id: 'star', isSelectedDefault: true });
    expect(inUseTemplate([app, star])?.id).toBe('star');
    expect(inUseTemplate([app, template({ id: 'xyz' })])?.id).toBe('app');
  });
});
