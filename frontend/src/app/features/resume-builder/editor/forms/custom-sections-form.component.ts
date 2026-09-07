import { Component, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ResumeStateService } from '../../services/resume-state.service';
import { ResumeCustomSection, ResumeCustomSectionItem } from '../../models/resume-builder.models';

const STARTER_TEMPLATES: { label: string; heading: string; style: 'paragraph' | 'bullets' }[] = [
  { label: 'About Me', heading: 'About Me', style: 'paragraph' },
  { label: 'Awards & Recognition', heading: 'Awards & Recognition', style: 'bullets' },
  { label: 'Volunteer Work', heading: 'Volunteer Work', style: 'bullets' },
  { label: 'Publications', heading: 'Publications', style: 'bullets' },
  { label: 'Blank', heading: '', style: 'paragraph' },
];

@Component({
  selector: 'app-custom-sections-form',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <div class="flex flex-col gap-4">

      <!-- Existing custom sections -->
      @for (section of stateService.customSections(); track section.id) {
        <div class="border border-gray-200 rounded-lg overflow-hidden">
          <div class="flex items-center justify-between px-3 py-2 bg-gray-50">
            <input
              class="text-sm font-medium bg-transparent border-none outline-none flex-1 text-gray-800"
              [value]="section.heading"
              (change)="updateHeading(section.id, $any($event.target).value)"
              placeholder="Section heading"
            />
            <button
              class="text-gray-400 hover:text-red-500 transition-colors ml-2 flex-shrink-0"
              (click)="stateService.removeCustomSection(section.id)"
              title="Remove section"
            >
              <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12"/>
              </svg>
            </button>
          </div>
          <div class="px-3 pb-3 pt-2">
            @if (section.body !== undefined || (!section.items || section.items.length === 0)) {
              <textarea
                class="w-full text-xs border border-gray-200 rounded px-2 py-1.5 resize-none focus:outline-none focus:ring-1 focus:ring-blue-400"
                rows="3"
                placeholder="Write a paragraph..."
                [value]="section.body ?? ''"
                (input)="updateBody(section.id, $any($event.target).value)"
              ></textarea>
            }
            @if (section.items && section.items.length > 0) {
              <div class="flex flex-col gap-1.5 mt-1">
                @for (item of section.items; track item.id) {
                  <div class="flex items-center gap-2">
                    <input
                      class="flex-1 text-xs border border-gray-200 rounded px-2 py-1 focus:outline-none focus:ring-1 focus:ring-blue-400"
                      [value]="item.text"
                      (change)="updateItem(section.id, item.id, $any($event.target).value)"
                      placeholder="Bullet item"
                    />
                    <button class="text-gray-400 hover:text-red-500 flex-shrink-0"
                            (click)="removeItem(section.id, item.id)">
                      <svg class="w-3.5 h-3.5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                        <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12"/>
                      </svg>
                    </button>
                  </div>
                }
                <button class="text-xs text-blue-600 hover:underline text-left mt-0.5"
                        (click)="addItem(section.id)">+ Add item</button>
              </div>
            }
          </div>
        </div>
      }

      <!-- Add section form -->
      @if (addingSection()) {
        <div class="border border-blue-200 rounded-lg p-3 bg-blue-50 flex flex-col gap-3">
          <p class="text-xs font-medium text-gray-700">Quick-fill templates</p>
          <div class="flex flex-wrap gap-1.5">
            @for (t of starterTemplates; track t.label) {
              <button
                class="text-xs px-2.5 py-1 rounded-full border transition-all"
                [class.border-blue-500]="newHeading === t.heading && newStyle === t.style"
                [class.bg-blue-500]="newHeading === t.heading && newStyle === t.style"
                [class.text-white]="newHeading === t.heading && newStyle === t.style"
                [class.border-gray-300]="!(newHeading === t.heading && newStyle === t.style)"
                [class.bg-white]="!(newHeading === t.heading && newStyle === t.style)"
                (click)="applyTemplate(t)"
              >{{ t.label }}</button>
            }
          </div>
          <input
            class="text-sm border border-gray-300 rounded px-2 py-1.5 focus:outline-none focus:ring-1 focus:ring-blue-400"
            [(ngModel)]="newHeading"
            placeholder="Section heading (required)"
          />
          <div class="flex gap-3 text-xs">
            <label class="flex items-center gap-1.5 cursor-pointer">
              <input type="radio" name="newStyle" value="paragraph" [(ngModel)]="newStyle" />
              Paragraph
            </label>
            <label class="flex items-center gap-1.5 cursor-pointer">
              <input type="radio" name="newStyle" value="bullets" [(ngModel)]="newStyle" />
              Bullet list
            </label>
          </div>
          <div class="flex gap-2">
            <button
              class="text-xs bg-blue-600 text-white px-3 py-1.5 rounded hover:bg-blue-700 disabled:opacity-50"
              [disabled]="!newHeading.trim()"
              (click)="confirmAdd()"
            >Add section</button>
            <button class="text-xs text-gray-500 hover:text-gray-700 px-2 py-1.5" (click)="addingSection.set(false)">Cancel</button>
          </div>
        </div>
      } @else {
        <button
          class="w-full border-2 border-dashed border-gray-300 rounded-lg py-2.5 text-sm text-gray-500 hover:border-blue-400 hover:text-blue-600 transition-colors"
          (click)="addingSection.set(true)"
        >+ Add custom section</button>
      }
    </div>
  `,
})
export class CustomSectionsFormComponent {
  readonly stateService = inject(ResumeStateService);
  readonly starterTemplates = STARTER_TEMPLATES;

  addingSection = signal(false);
  newHeading = '';
  newStyle: 'paragraph' | 'bullets' = 'paragraph';

  applyTemplate(t: typeof STARTER_TEMPLATES[0]): void {
    this.newHeading = t.heading;
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
