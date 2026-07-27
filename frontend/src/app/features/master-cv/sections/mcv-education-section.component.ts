import { Component, EventEmitter, Input, Output } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { TranslateModule } from '@ngx-translate/core';
import { JbIconComponent } from '../../../shared/components/jb-icon/jb-icon.component';
import { JbButtonComponent } from '../../../shared/components/jb-button/jb-button.component';
import { Education } from '../../../core/models/profile-section.model';

/** Master CV editor — education list. */
@Component({
  selector: 'app-mcv-education-section',
  standalone: true,
  imports: [CommonModule, FormsModule, TranslateModule, JbIconComponent, JbButtonComponent],
  template: `
    <div class="flex flex-col gap-3">
      @for (edu of list; track edu.id || $index) {
        <div class="card p-3.5">
          <div class="flex gap-2 items-start">
            <div class="flex-1 grid gap-2 grid-cols-[1.4fr_1.4fr]">
              <label class="flex flex-col gap-[5px]">
                <span class="label">{{ 'masterCv.form.school' | translate }}</span>
                <input [(ngModel)]="edu.institution" class="input" [placeholder]="'masterCv.form.schoolPlaceholder' | translate" />
              </label>
              <label class="flex flex-col gap-[5px]">
                <span class="label">{{ 'masterCv.form.degree' | translate }}</span>
                <input [(ngModel)]="edu.degree" class="input" [placeholder]="'masterCv.form.degreePlaceholder' | translate" />
              </label>
              <label class="flex flex-col gap-[5px]">
                <span class="label">{{ 'masterCv.form.startDate' | translate }}</span>
                <input [(ngModel)]="edu.startDate" class="input mono" placeholder="2016-09-01" />
              </label>
              <label class="flex flex-col gap-[5px]">
                <span class="label">{{ 'masterCv.form.endDate' | translate }}</span>
                <input [(ngModel)]="edu.endDate" class="input mono" placeholder="2019-06-30" />
              </label>
            </div>
            <button class="icon-btn mt-5" [title]="'common.remove' | translate" (click)="removeAt.emit($index)"><jb-icon name="x" [size]="12" /></button>
          </div>
        </div>
      }
      @if (list.length === 0) {
        <div class="p-8 text-center text-jb-text-dim text-base">{{ 'masterCv.form.noEducation' | translate }}</div>
      }
      <jb-button small icon="plus" (clicked)="add.emit()">{{ 'masterCv.form.addEducation' | translate }}</jb-button>
    </div>
  `,
})
export class McvEducationSectionComponent {
  @Input({ required: true }) list: Education[] = [];
  @Output() add = new EventEmitter<void>();
  @Output() removeAt = new EventEmitter<number>();
}
