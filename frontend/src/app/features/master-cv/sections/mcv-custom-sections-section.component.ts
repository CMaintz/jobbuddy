import { Component, EventEmitter, Input, Output, ChangeDetectionStrategy } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { TranslateModule } from '@ngx-translate/core';
import { CustomSection } from '../../../core/models/profile-section.model';

@Component({
  selector: 'app-mcv-custom-sections-section',
  imports: [FormsModule, TranslateModule],
  changeDetection: ChangeDetectionStrategy.Eager,
  template: `
    <div class="flex flex-col gap-3">
      <div class="text-xs text-jb-text-dim">{{ 'masterCv.form.customHint' | translate }}</div>
      @for (section of list; track section.id || $index) {
        <div class="card p-3.5 flex flex-col gap-2.5">
          <div class="flex gap-2 items-center">
            <input [(ngModel)]="section.heading" class="input flex-1 font-medium"
              [placeholder]="'masterCv.form.customHeadingPlaceholder' | translate" />
            <button type="button" class="icon-btn" [title]="'common.remove' | translate"
              (click)="removeAt.emit($index)">✕</button>
          </div>
          @for (item of section.items; track item.id || $index) {
            <div class="flex gap-2 items-center ml-1">
              <input [(ngModel)]="item.text" class="input flex-1"
                [placeholder]="'masterCv.form.customItemPlaceholder' | translate" />
              <button type="button" class="icon-btn" [title]="'common.remove' | translate"
                (click)="removeItem(section, $index)">✕</button>
            </div>
          }
          <button type="button" class="self-start text-xs text-jb-accent-2 cursor-pointer bg-transparent border-none px-1"
            (click)="addItem(section)">+ {{ 'masterCv.form.addCustomItem' | translate }}</button>
        </div>
      }
      <button type="button" class="self-start px-2.5 py-[6px] bg-jb-surface-2 border border-jb-accent-border rounded-md text-jb-text text-sm cursor-pointer"
        (click)="add.emit()">+ {{ 'masterCv.form.addCustomSection' | translate }}</button>
    </div>
  `,
})
export class McvCustomSectionsSectionComponent {
  @Input({ required: true }) list: CustomSection[] = [];
  @Output() add = new EventEmitter<void>();
  @Output() removeAt = new EventEmitter<number>();
  @Output() changed = new EventEmitter<void>();

  addItem(section: CustomSection): void {
    section.items.push({ text: '' });
    this.changed.emit();
  }

  removeItem(section: CustomSection, index: number): void {
    section.items.splice(index, 1);
    this.changed.emit();
  }
}
