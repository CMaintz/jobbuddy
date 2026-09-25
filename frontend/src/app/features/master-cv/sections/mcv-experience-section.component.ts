import { Component, EventEmitter, Input, Output } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { TranslateModule } from '@ngx-translate/core';
import { JbIconComponent } from '../../../shared/components/jb-icon/jb-icon.component';
import { JbButtonComponent } from '../../../shared/components/jb-button/jb-button.component';
import { AiRefineMenuComponent } from '../../resume-builder/shared/ai-refine-menu.component';
import { WorkExperience } from '../../../core/models/profile-section.model';

/** Master CV editor — work experience list with per-entry AI polish. */
@Component({
  selector: 'app-mcv-experience-section',
  standalone: true,
  imports: [CommonModule, FormsModule, TranslateModule, JbIconComponent, JbButtonComponent, AiRefineMenuComponent],
  template: `
    <div class="flex flex-col gap-4">
      @for (exp of list; track exp.id || $index) {
        <div class="card p-3.5">
          <div class="flex gap-2 items-start">
            <div class="flex-1 grid gap-2 mb-3 grid-cols-[1.3fr_1fr]">
              <label class="flex flex-col gap-[5px]">
                <span class="label">{{ 'masterCv.form.company' | translate }}</span>
                <input [(ngModel)]="exp.companyName" class="input" [placeholder]="'masterCv.form.company' | translate" />
              </label>
              <label class="flex flex-col gap-[5px]">
                <span class="label">{{ 'masterCv.form.role' | translate }}</span>
                <input [(ngModel)]="exp.title" class="input" [placeholder]="'masterCv.form.role' | translate" />
              </label>
            </div>
            <button class="icon-btn mt-5" [title]="'common.remove' | translate" (click)="removeAt.emit($index)"><jb-icon name="x" [size]="12" /></button>
          </div>
          <div class="grid gap-2 mb-3 grid-cols-[1fr_1fr_1fr]">
            <label class="flex flex-col gap-[5px]">
              <span class="label">{{ 'masterCv.form.startDate' | translate }}</span>
              <input [(ngModel)]="exp.startDate" class="input mono" placeholder="2022-03-01" />
            </label>
            <label class="flex flex-col gap-[5px]">
              <span class="label">{{ 'masterCv.form.endDate' | translate }}</span>
              <input [(ngModel)]="exp.endDate" class="input mono" placeholder="2024-06-30" [disabled]="exp.isCurrent" />
            </label>
            <label class="flex items-center gap-2 mt-5 cursor-pointer">
              <input type="checkbox" [(ngModel)]="exp.isCurrent" />
              <span class="text-sm text-jb-text-mid">{{ 'masterCv.form.currentRole' | translate }}</span>
            </label>
          </div>
          <div class="flex items-center justify-between mb-2">
            <span class="text-2xs text-jb-text-dim tracking-widest uppercase">{{ 'masterCv.form.description' | translate }}</span>
            <app-ai-refine-menu [plainOutput]="true" [content]="exp.description || ''"
              [sectionKey]="'experience'"
              (refined)="exp.description = $event; dirty.emit()" />
          </div>
          <textarea [(ngModel)]="exp.description" rows="3" class="input resize-y text-sm leading-normal"
            [placeholder]="'masterCv.form.descPlaceholder' | translate"></textarea>
        </div>
      }
      @if (list.length === 0) {
        <div class="p-8 text-center text-jb-text-dim text-base">
          {{ 'masterCv.form.noExperience' | translate }}
        </div>
      }
      <jb-button small icon="plus" (clicked)="add.emit()">{{ 'masterCv.form.addExperience' | translate }}</jb-button>
    </div>
  `,
})
export class McvExperienceSectionComponent {
  @Input({ required: true }) list: WorkExperience[] = [];
  @Output() add = new EventEmitter<void>();
  @Output() removeAt = new EventEmitter<number>();
  /** AI-applied changes don't fire DOM input events — signal dirtiness explicitly. */
  @Output() dirty = new EventEmitter<void>();
}
