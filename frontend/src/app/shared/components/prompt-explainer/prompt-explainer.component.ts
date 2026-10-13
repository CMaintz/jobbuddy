import { Component, Input, ChangeDetectionStrategy } from '@angular/core';
import { TranslateModule } from '@ngx-translate/core';
import { PromptCategory, PromptTemplate } from '../../../core/models/prompt-template.model';

/**
 * What explains a template to its user: the translated explainer for an app-shipped one, else the
 * owner's own description. App descriptions are single-language seed text, so they are only the
 * fallback.
 */
export function promptExplainer(template: PromptTemplate | null | undefined): { key?: string; text?: string } {
  if (!template) return {};
  if (template.explainerKey) return { key: template.explainerKey };
  return template.description ? { text: template.description } : {};
}

/**
 * The template a picker will actually use: the explicit choice, else the caller's default for the
 * category, so the help text describes the prompt that "Default" stands for rather than nothing.
 */
export function chosenPrompt(templates: PromptTemplate[], selectedId: string | null,
                             category: PromptCategory | undefined): PromptTemplate | undefined {
  if (selectedId) return templates.find(t => t.id === selectedId);
  return templates.find(t => t.isSelectedDefault && t.category === category);
}

/** Help text under a prompt picker, saying what the chosen prompt does and when to pick it. */
@Component({
  selector: 'app-prompt-explainer',
  imports: [TranslateModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (explainer.key || explainer.text) {
      <div class="text-xs text-jb-text-mid leading-relaxed whitespace-pre-line">
        {{ explainer.key ? (explainer.key | translate) : explainer.text }}
      </div>
    }
  `,
})
export class PromptExplainerComponent {
  @Input() template: PromptTemplate | null | undefined;

  get explainer(): { key?: string; text?: string } {
    return promptExplainer(this.template);
  }
}
