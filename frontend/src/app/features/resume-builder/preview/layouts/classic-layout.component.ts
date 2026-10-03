import { Component, inject, ChangeDetectionStrategy } from '@angular/core';
import { LucideDynamicIcon, LucideIconInput } from '@lucide/angular';
import { TranslateModule } from '@ngx-translate/core';
import { ResumeStateService } from '../../services/resume-state.service';
import { RichTextPipe } from '../../shared/rich-text.pipe';
import { SectionTypographyDirective } from '../../shared/section-typography.directive';
import { ResumePhotoDirective } from '../../shared/resume-photo.directive';
import { SkillChipListComponent } from '../../shared/skill-chip-list.component';
import { getSocialIcon, CONTACT_ICONS } from '../../data/social-platforms';
import { getStrengthIcon } from '../../data/strength-icons';

@Component({
  selector: 'app-classic-layout',
  imports: [
    LucideDynamicIcon, TranslateModule, SkillChipListComponent, RichTextPipe,
    SectionTypographyDirective, ResumePhotoDirective,
  ],
  templateUrl: './classic-layout.component.html',
  changeDetection: ChangeDetectionStrategy.Eager,
  styleUrls: ['./classic-layout.component.css'],
})
export class ClassicLayoutComponent {
  private stateService = inject(ResumeStateService);

  get pi() { return this.stateService.displayPersonalInfo(); }
  get experience() { return this.stateService.experience(); }
  get education() { return this.stateService.education(); }
  get projects() { return this.stateService.projects(); }
  get skills() { return this.stateService.skills(); }
  get skillGroups() { return this.stateService.skillGroups(); }
  get languages() { return this.stateService.languages(); }
  get certifications() { return this.stateService.certifications(); }
  get strengths() { return this.stateService.strengths(); }
  get socials() { return this.stateService.displaySocials(); }
  get customSections() { return this.stateService.customSections(); }
  get themeColor() { return this.stateService.settings().themeColor; }
  get photoStyle() { return this.stateService.settings().photoStyle ?? 'circle'; }
  get showSkillLevel() { return this.stateService.settings().showSkillLevel; }

  readonly contactIcons = CONTACT_ICONS;
  getSocialIcon(key: string): LucideIconInput { return getSocialIcon(key); }
  getStrengthIcon(key: string): LucideIconInput { return getStrengthIcon(key); }
}
