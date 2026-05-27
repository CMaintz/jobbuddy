import { Component, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { CommonModule } from '@angular/common';
import { ResumeStateService } from '../../services/resume-state.service';
import { IconPickerComponent } from '../../shared/icon-picker.component';
import { SOCIAL_PLATFORMS, SOCIAL_ICON_LIBRARY } from '../../data/social-platforms';

@Component({
  selector: 'app-socials-form',
  standalone: true,
  imports: [CommonModule, FormsModule, IconPickerComponent],
  template: `
    <div class="flex flex-col gap-4">
      @for (s of socials; track s.id) {
        <div class="border border-gray-200 rounded-lg p-3 flex flex-col gap-2">
          <div class="flex justify-between items-center">
            <span class="text-xs font-medium text-gray-700">{{ s.platform || 'New Social' }}</span>
            <button class="text-red-400 hover:text-red-600 text-xs" (click)="remove(s.id)">Remove</button>
          </div>
          <label class="form-label">Platform</label>
          <select class="form-input" [value]="s.platform" (change)="onPlatformChange(s.id, $any($event.target).value)">
            @for (p of platforms; track p.platform) {
              <option [value]="p.platform">{{ p.platform }}</option>
            }
          </select>
          <label class="form-label">URL</label>
          <input class="form-input" [value]="s.url" (input)="update(s.id, 'url', $any($event.target).value)" placeholder="https://linkedin.com/in/you" />
          <label class="form-label">Username (optional)</label>
          <input class="form-input" [value]="s.username ?? ''" (input)="update(s.id, 'username', $any($event.target).value)" placeholder="@yourhandle" />
          <app-icon-picker
            label="Icon"
            [icons]="iconLibrary"
            [selected]="s.iconKey"
            (iconSelected)="update(s.id, 'iconKey', $event)"
          />
        </div>
      }
      <button class="add-btn" (click)="add()">+ Add Social Link</button>
    </div>
  `,
  styles: [`
    .form-label { @apply block text-xs font-medium text-gray-600 mb-1; }
    .form-input { @apply w-full rounded-md border border-gray-300 px-3 py-1.5 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500; }
    .add-btn { @apply w-full py-2 text-sm text-blue-600 border border-dashed border-blue-300 rounded-lg hover:bg-blue-50 transition-colors; }
  `],
})
export class SocialsFormComponent {
  private state = inject(ResumeStateService);
  readonly platforms = SOCIAL_PLATFORMS;
  readonly iconLibrary = SOCIAL_ICON_LIBRARY;

  get socials() { return this.state.socials(); }

  add(): void {
    this.state.addSocial({ platform: 'LinkedIn', url: '', username: '', iconKey: 'linkedin' });
  }

  remove(id: string): void { this.state.removeSocial(id); }

  update(id: string, field: string, value: string): void {
    this.state.updateSocial(id, { [field]: value } as never);
  }

  onPlatformChange(id: string, platform: string): void {
    const found = this.platforms.find(p => p.platform === platform);
    if (found) {
      this.state.updateSocial(id, { platform: found.platform, iconKey: found.iconKey });
    }
  }
}
