import { Component, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { LucideAngularModule } from 'lucide-angular';
import { ResumeStateService } from '../../services/resume-state.service';
import { RichTextPipe } from '../../shared/rich-text.pipe';
import { SectionTypographyDirective } from '../../shared/section-typography.directive';
import { ResumePhotoDirective } from '../../shared/resume-photo.directive';
import { SkillChipListComponent } from '../../shared/skill-chip-list.component';
import { getSocialIcon, CONTACT_ICONS } from '../../data/social-platforms';
import { getStrengthIcon } from '../../data/strength-icons';

@Component({
  selector: 'app-classic-layout',
  standalone: true,
  imports: [CommonModule, LucideAngularModule, SkillChipListComponent, RichTextPipe, SectionTypographyDirective, ResumePhotoDirective],
  templateUrl: './classic-layout.component.html',
  styleUrls: ['./classic-layout.component.css'],
})
export class ClassicLayoutComponent {
  private stateService = inject(ResumeStateService);

  get pi() { return this.stateService.displayPersonalInfo(); }
  get experience() { return this.stateService.experience(); }
  get education() { return this.stateService.education(); }
  get projects() { return this.stateService.projects(); }
  get skills() { return this.stateService.skills(); }
  get languages() { return this.stateService.languages(); }
  get certifications() { return this.stateService.certifications(); }
  get strengths() { return this.stateService.strengths(); }
  get socials() { return this.stateService.displaySocials(); }
  get customSections() { return this.stateService.customSections(); }
  get themeColor() { return this.stateService.settings().themeColor; }
  get photoStyle() { return this.stateService.settings().photoStyle ?? 'circle'; }
  get showSkillLevel() { return this.stateService.settings().showSkillLevel; }

  readonly contactIcons = CONTACT_ICONS;
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  getSocialIcon(key: string): any { return getSocialIcon(key); }
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  getStrengthIcon(key: string): any { return getStrengthIcon(key); }
}
