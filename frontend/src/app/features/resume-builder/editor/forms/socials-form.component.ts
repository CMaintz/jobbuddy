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
  templateUrl: './socials-form.component.html',
  styleUrls: ['./socials-form.component.css'],
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
