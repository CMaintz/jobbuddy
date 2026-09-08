import { Component, EventEmitter, Input, Output } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { TranslateModule } from '@ngx-translate/core';
import { JbIconComponent } from '../../../shared/components/jb-icon/jb-icon.component';
import { JbButtonComponent } from '../../../shared/components/jb-button/jb-button.component';
import { SpokenLanguage, LanguageProficiency } from '../../../core/models/profile-section.model';

const PROFICIENCIES: LanguageProficiency[] = ['NATIVE', 'FLUENT', 'PROFESSIONAL', 'CONVERSATIONAL', 'ELEMENTARY'];

/** Master CV editor — spoken languages list. */
@Component({
  selector: 'app-mcv-languages-section',
  standalone: true,
  imports: [CommonModule, FormsModule, TranslateModule, JbIconComponent, JbButtonComponent],
  template: `
    <div class="flex flex-col gap-3">
      @for (lang of list; track lang.id || $index) {
        <div class="card p-3.5">
          <div class="flex gap-2 items-start">
            <div class="flex-1 grid gap-2 grid-cols-[1fr_1.4fr]">
              <label class="flex flex-col gap-[5px]">
                <span class="label">{{ 'masterCv.form.language' | translate }}</span>
                <input [(ngModel)]="lang.language" class="input" placeholder="English" />
              </label>
              <label class="flex flex-col gap-[5px]">
                <span class="label">{{ 'masterCv.form.level' | translate }}</span>
                <select [(ngModel)]="lang.proficiency" class="input">
                  @for (p of proficiencies; track p) {
                    <option [value]="p">{{ ('masterCv.form.proficiency.' + p.toLowerCase()) | translate }}</option>
                  }
                </select>
              </label>
            </div>
            <button class="icon-btn mt-5" [title]="'common.remove' | translate" (click)="removeAt.emit($index)"><jb-icon name="x" [size]="12" /></button>
          </div>
        </div>
      }
      @if (list.length === 0) {
        <div class="p-8 text-center text-jb-text-dim text-base">{{ 'masterCv.form.noLanguages' | translate }}</div>
      }
      <jb-button small icon="plus" (clicked)="add.emit()">{{ 'masterCv.form.addLanguage' | translate }}</jb-button>
    </div>
  `,
})
export class McvLanguagesSectionComponent {
  @Input({ required: true }) list: SpokenLanguage[] = [];
  @Output() add = new EventEmitter<void>();
  @Output() removeAt = new EventEmitter<number>();
  proficiencies = PROFICIENCIES;
}
