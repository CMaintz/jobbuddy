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
  selector: 'app-creative-layout',
  standalone: true,
  imports: [CommonModule, LucideAngularModule, SkillChipListComponent, RichTextPipe, SectionTypographyDirective, ResumePhotoDirective],
  templateUrl: './creative-layout.component.html',
})
export class CreativeLayoutComponent {
  private svc = inject(ResumeStateService);
  get pi() { return this.svc.personalInfo(); }
  get experience() { return this.svc.experience(); }
  get education() { return this.svc.education(); }
  get projects() { return this.svc.projects(); }
  get skills() { return this.svc.skills(); }
  get languages() { return this.svc.languages(); }
  get strengths() { return this.svc.strengths(); }
  get socials() { return this.svc.socials(); }
  get customSections() { return this.svc.customSections(); }
  get themeColor() { return this.svc.settings().themeColor; }
  get photoStyle() { return this.svc.settings().photoStyle ?? 'circle'; }
  get showSkillLevel() { return this.svc.settings().showSkillLevel; }
  readonly contactIcons = CONTACT_ICONS;
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  getSocialIcon(key: string): any { return getSocialIcon(key); }
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  getStrengthIcon(key: string): any { return getStrengthIcon(key); }
}
