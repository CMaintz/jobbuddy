import { Component, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { ResumeStateService } from '../../services/resume-state.service';
import { RichTextEditorComponent } from '../../shared/rich-text-editor.component';
import { ResumeCustomSectionItem } from '../../models/resume-builder.models';

const STARTER_TEMPLATES: { label: string; heading: string; style: 'paragraph' | 'bullets' }[] = [
  { label: 'resumeBuilder.custom.starter.aboutMe', heading: 'resumeBuilder.custom.starter.aboutMe', style: 'paragraph' },
  { label: 'resumeBuilder.custom.starter.awards', heading: 'resumeBuilder.custom.starter.awards', style: 'bullets' },
  { label: 'resumeBuilder.custom.starter.volunteer', heading: 'resumeBuilder.custom.starter.volunteer', style: 'bullets' },
  { label: 'resumeBuilder.custom.starter.publications', heading: 'resumeBuilder.custom.starter.publications', style: 'bullets' },
  { label: 'resumeBuilder.custom.starter.blank', heading: '', style: 'paragraph' },
];

@Component({
  selector: 'app-custom-sections-form',
  standalone: true,
  imports: [CommonModule, FormsModule, TranslateModule, RichTextEditorComponent],
  templateUrl: './custom-sections-form.component.html',
})
export class CustomSectionsFormComponent {
  readonly stateService = inject(ResumeStateService);
  private translate = inject(TranslateService);
  readonly starterTemplates = STARTER_TEMPLATES;

  addingSection = signal(false);
  newHeading = '';
  newStyle: 'paragraph' | 'bullets' = 'paragraph';

  applyTemplate(t: typeof STARTER_TEMPLATES[0]): void {
    this.newHeading = t.heading ? this.translate.instant(t.heading) : '';
    this.newStyle = t.style;
  }

  confirmAdd(): void {
    if (!this.newHeading.trim()) return;
    this.stateService.addCustomSection({
      heading: this.newHeading.trim(),
      body: this.newStyle === 'paragraph' ? '' : undefined,
      items: this.newStyle === 'bullets' ? [{ id: crypto.randomUUID(), text: '' }] : undefined,
    });
    this.newHeading = '';
    this.newStyle = 'paragraph';
    this.addingSection.set(false);
  }

  updateHeading(id: string, heading: string): void {
    this.stateService.updateCustomSection(id, { heading });
  }

  updateBody(id: string, body: string): void {
    this.stateService.updateCustomSection(id, { body });
  }

  updateItem(sectionId: string, itemId: string, text: string): void {
    const section = this.stateService.customSections().find(s => s.id === sectionId);
    if (!section?.items) return;
    const items = section.items.map(i => i.id === itemId ? { ...i, text } : i);
    this.stateService.updateCustomSection(sectionId, { items });
  }

  addItem(sectionId: string): void {
    const section = this.stateService.customSections().find(s => s.id === sectionId);
    if (!section) return;
    const items: ResumeCustomSectionItem[] = [...(section.items ?? []), { id: crypto.randomUUID(), text: '' }];
    this.stateService.updateCustomSection(sectionId, { items });
  }

  removeItem(sectionId: string, itemId: string): void {
    const section = this.stateService.customSections().find(s => s.id === sectionId);
    if (!section?.items) return;
    const items = section.items.filter(i => i.id !== itemId);
    this.stateService.updateCustomSection(sectionId, { items });
  }
}
