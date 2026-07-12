import { Component, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ResumeStateService } from '../../services/resume-state.service';
import { RichTextPipe } from '../../shared/rich-text.pipe';
import { SectionTypographyDirective } from '../../shared/section-typography.directive';
import { SkillChipListComponent } from '../../shared/skill-chip-list.component';
import { CONTACT_ICONS } from '../../data/social-platforms';

@Component({
  selector: 'app-minimal-layout',
  standalone: true,
  imports: [CommonModule, SkillChipListComponent, RichTextPipe, SectionTypographyDirective],
  templateUrl: './minimal-layout.component.html',
})
export class MinimalLayoutComponent {
  private svc = inject(ResumeStateService);
  get pi() { return this.svc.displayPersonalInfo(); }
  get experience() { return this.svc.experience(); }
  get education() { return this.svc.education(); }
  get skills() { return this.svc.skills(); }
  get customSections() { return this.svc.customSections(); }
  get themeColor() { return this.svc.settings().themeColor; }
  readonly contactIcons = CONTACT_ICONS;
}
