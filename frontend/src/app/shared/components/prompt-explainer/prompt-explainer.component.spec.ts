import { promptExplainer } from './prompt-explainer.component';
import { PromptTemplate } from '../../../core/models/prompt-template.model';

function template(overrides: Partial<PromptTemplate>): PromptTemplate {
  return {
    id: 't1', name: 'T', userPrompt: 'p', isPublic: false, isSystem: false, isProtected: false,
    isDefault: false, isSelectedDefault: false, versionNumber: 1, createdAt: '', updatedAt: '',
    ...overrides,
  };
}

describe('promptExplainer', () => {
  it('prefers the translated explainer of an app template over its seed description', () => {
    expect(promptExplainer(template({ explainerKey: 'prompts.explainers.x', description: 'raw' })))
      .toEqual({ key: 'prompts.explainers.x' });
  });

  it("falls back to the owner's description for a user template", () => {
    expect(promptExplainer(template({ description: 'mine' }))).toEqual({ text: 'mine' });
  });

  it('explains nothing when no template is chosen', () => {
    expect(promptExplainer(null)).toEqual({});
  });
});
