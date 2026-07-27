import { Component, EventEmitter, Input, Output } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { TranslateModule } from '@ngx-translate/core';
import { JbIconComponent } from '../../../shared/components/jb-icon/jb-icon.component';
import { JbButtonComponent } from '../../../shared/components/jb-button/jb-button.component';
import { ProfileStrength } from '../../../core/models/profile-section.model';

/** Master CV editor — strengths list. Items are edited in place (parent owns the array). */
@Component({
  selector: 'app-mcv-strengths-section',
  standalone: true,
  imports: [CommonModule, FormsModule, TranslateModule, JbIconComponent, JbButtonComponent],
  template: `
    <div class="flex flex-col gap-3">
      <div class="text-xs text-jb-text-dim">{{ 'masterCv.form.strengthsHint' | translate }}</div>
      @for (s of list; track s.id || $index) {
        <div class="card p-3.5">
          <div class="flex gap-2 items-start">
            <div class="flex-1 grid gap-2 grid-cols-[1fr_1.6fr]">
              <label class="flex flex-col gap-[5px]">
                <span class="label">{{ 'masterCv.form.strength' | translate }}</span>
                <input [(ngModel)]="s.title" class="input" [placeholder]="'masterCv.form.strengthPlaceholder' | translate" />
              </label>
              <label class="flex flex-col gap-[5px]">
                <span class="label">{{ 'masterCv.form.whyMatters' | translate }}</span>
                <input [(ngModel)]="s.description" class="input" [placeholder]="'masterCv.form.whyPlaceholder' | translate" />
              </label>
            </div>
            <button class="icon-btn mt-5" [title]="'common.remove' | translate" (click)="removeAt.emit($index)"><jb-icon name="x" [size]="12" /></button>
          </div>
        </div>
      }
      <jb-button small icon="plus" (clicked)="add.emit()">{{ 'masterCv.form.addStrength' | translate }}</jb-button>
    </div>
  `,
})
export class McvStrengthsSectionComponent {
  @Input({ required: true }) list: ProfileStrength[] = [];
  @Output() add = new EventEmitter<void>();
  @Output() removeAt = new EventEmitter<number>();
}
