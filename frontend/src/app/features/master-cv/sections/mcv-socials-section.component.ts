import { Component, EventEmitter, Input, Output } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { TranslateModule } from '@ngx-translate/core';
import { JbIconComponent } from '../../../shared/components/jb-icon/jb-icon.component';
import { JbButtonComponent } from '../../../shared/components/jb-button/jb-button.component';
import { ProfileSocial } from '../../../core/models/profile-section.model';

const SOCIAL_PLATFORMS = ['GitHub', 'LinkedIn', 'Website', 'X', 'Mastodon', 'Other'];

/** Master CV editor — social/profile links. */
@Component({
  selector: 'app-mcv-socials-section',
  standalone: true,
  imports: [CommonModule, FormsModule, TranslateModule, JbIconComponent, JbButtonComponent],
  template: `
    <div class="flex flex-col gap-3">
      <div class="text-xs text-jb-text-dim">{{ 'masterCv.form.socialsHint' | translate }}</div>
      @for (social of list; track social.id || $index) {
        <div class="card p-3.5">
          <div class="flex gap-2 items-start">
            <div class="flex-1 grid gap-2 grid-cols-[0.8fr_1.8fr]">
              <label class="flex flex-col gap-[5px]">
                <span class="label">{{ 'masterCv.form.platform' | translate }}</span>
                <select [(ngModel)]="social.platform" (ngModelChange)="onPlatformChange(social)" class="input">
                  @for (p of platforms; track p) {
                    <option [value]="p">{{ p }}</option>
                  }
                </select>
              </label>
              <label class="flex flex-col gap-[5px]">
                <span class="label">{{ 'masterCv.form.url' | translate }}</span>
                <input [(ngModel)]="social.url" class="input mono" placeholder="https://github.com/you" />
              </label>
            </div>
            <button class="icon-btn mt-5" [title]="'common.remove' | translate" (click)="removeAt.emit($index)"><jb-icon name="x" [size]="12" /></button>
          </div>
        </div>
      }
      @if (list.length === 0) {
        <div class="p-8 text-center text-jb-text-dim text-base">{{ 'masterCv.form.noLinks' | translate }}</div>
      }
      <jb-button small icon="plus" (clicked)="add.emit()">{{ 'masterCv.form.addLink' | translate }}</jb-button>
    </div>
  `,
})
export class McvSocialsSectionComponent {
  @Input({ required: true }) list: ProfileSocial[] = [];
  @Output() add = new EventEmitter<void>();
  @Output() removeAt = new EventEmitter<number>();
  @Output() dirty = new EventEmitter<void>();
  platforms = SOCIAL_PLATFORMS;

  onPlatformChange(social: ProfileSocial): void {
    social.iconKey = social.platform.toLowerCase();
    this.dirty.emit();
  }
}
