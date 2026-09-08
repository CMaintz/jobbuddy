import { Component, EventEmitter, Input, Output } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { TranslateModule } from '@ngx-translate/core';
import { JbIconComponent } from '../../../shared/components/jb-icon/jb-icon.component';
import { JbButtonComponent } from '../../../shared/components/jb-button/jb-button.component';
import { AiRefineMenuComponent } from '../../resume-builder/shared/ai-refine-menu.component';
import { Project } from '../../../core/models/profile-section.model';

/** Master CV editor — projects list with per-entry AI polish. */
@Component({
  selector: 'app-mcv-projects-section',
  standalone: true,
  imports: [CommonModule, FormsModule, TranslateModule, JbIconComponent, JbButtonComponent, AiRefineMenuComponent],
  template: `
    <div class="flex flex-col gap-3">
      @for (proj of list; track proj.id || $index) {
        <div class="card p-3.5">
          <div class="flex gap-2 items-start">
            <div class="flex-1 grid gap-2 grid-cols-[1fr_1.6fr]">
              <label class="flex flex-col gap-[5px]">
                <span class="label">{{ 'masterCv.form.project' | translate }}</span>
                <input [(ngModel)]="proj.name" class="input" [placeholder]="'masterCv.form.projectPlaceholder' | translate" />
              </label>
              <div class="flex flex-col gap-[5px]">
                <div class="flex items-center justify-between">
                  <span class="label">{{ 'masterCv.form.description' | translate }}</span>
                  <app-ai-refine-menu [plainOutput]="true" [content]="proj.description || ''"
                    (refined)="proj.description = $event; dirty.emit()" />
                </div>
                <input [(ngModel)]="proj.description" class="input" [placeholder]="'masterCv.form.descShortPlaceholder' | translate" />
              </div>
              <label class="flex flex-col gap-[5px]">
                <span class="label">{{ 'masterCv.form.github' | translate }}</span>
                <input [(ngModel)]="proj.githubUrl" class="input mono" placeholder="https://github.com/..." />
              </label>
              <label class="flex flex-col gap-[5px]">
                <span class="label">{{ 'masterCv.form.liveUrl' | translate }}</span>
                <input [(ngModel)]="proj.liveUrl" class="input mono" placeholder="https://..." />
              </label>
            </div>
            <button class="icon-btn mt-5" [title]="'common.remove' | translate" (click)="removeAt.emit($index)"><jb-icon name="x" [size]="12" /></button>
          </div>
        </div>
      }
      @if (list.length === 0) {
        <div class="p-8 text-center text-jb-text-dim text-base">{{ 'masterCv.form.noProjects' | translate }}</div>
      }
      <jb-button small icon="plus" (clicked)="add.emit()">{{ 'masterCv.form.addProject' | translate }}</jb-button>
    </div>
  `,
})
export class McvProjectsSectionComponent {
  @Input({ required: true }) list: Project[] = [];
  @Output() add = new EventEmitter<void>();
  @Output() removeAt = new EventEmitter<number>();
  /** AI-applied changes don't fire DOM input events — signal dirtiness explicitly. */
  @Output() dirty = new EventEmitter<void>();
}
