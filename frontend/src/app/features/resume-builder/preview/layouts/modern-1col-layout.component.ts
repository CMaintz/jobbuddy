import { Component, inject } from '@angular/core';
import { LucideDynamicIcon, LucideIconInput } from '@lucide/angular';
import { TranslateModule } from '@ngx-translate/core';
import { ResumeStateService } from '../../services/resume-state.service';
import { RichTextPipe } from '../../shared/rich-text.pipe';
import { SectionTypographyDirective } from '../../shared/section-typography.directive';
import { ResumePhotoDirective } from '../../shared/resume-photo.directive';
import { SkillChipListComponent } from '../../shared/skill-chip-list.component';
import { getSocialIcon, CONTACT_ICONS } from '../../data/social-platforms';

@Component({
  selector: 'app-modern-1col-layout',
  imports: [
    LucideDynamicIcon, TranslateModule, SkillChipListComponent, RichTextPipe,
    SectionTypographyDirective, ResumePhotoDirective,
  ],
  templateUrl: './modern-1col-layout.component.html',
})
export class Modern1ColLayoutComponent {
  private svc = inject(ResumeStateService);
  get pi() { return this.svc.displayPersonalInfo(); }
  get experience() { return this.svc.experience(); }
  get education() { return this.svc.education(); }
  get skills() { return this.svc.skills(); }
  get skillGroups() { return this.svc.skillGroups(); }
  get languages() { return this.svc.languages(); }
  get certifications() { return this.svc.certifications(); }
  get socials() { return this.svc.displaySocials(); }
  get customSections() { return this.svc.customSections(); }
  get themeColor() { return this.svc.settings().themeColor; }
  get photoStyle() { return this.svc.settings().photoStyle ?? 'circle'; }
  readonly contactIcons = CONTACT_ICONS;
  getSocialIcon(key: string): LucideIconInput { return getSocialIcon(key); }
}
