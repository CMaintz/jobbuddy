import { Component, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { TranslateModule } from '@ngx-translate/core';
import { ResumeStateService } from '../services/resume-state.service';
import { AiTailorFormComponent } from './forms/ai-tailor-form.component';
import { PersonalInfoFormComponent } from './forms/personal-info-form.component';
import { ExperienceFormComponent } from './forms/experience-form.component';
import { EducationFormComponent } from './forms/education-form.component';
import { SkillsFormComponent } from './forms/skills-form.component';
import { ProjectsFormComponent } from './forms/projects-form.component';
import { LanguagesFormComponent } from './forms/languages-form.component';
import { CertificationsFormComponent } from './forms/certifications-form.component';
import { StrengthsFormComponent } from './forms/strengths-form.component';
import { SocialsFormComponent } from './forms/socials-form.component';
import { SettingsFormComponent } from './forms/settings-form.component';
import { LayoutFormComponent } from './forms/layout-form.component';
import { CustomSectionsFormComponent } from './forms/custom-sections-form.component';

interface EditorSection {
  id: string;
  label: string;
  icon: string;
  open: boolean;
}

@Component({
  selector: 'app-resume-editor',
  standalone: true,
  imports: [
    CommonModule,
    TranslateModule,
    AiTailorFormComponent,
    PersonalInfoFormComponent,
    ExperienceFormComponent,
    EducationFormComponent,
    SkillsFormComponent,
    ProjectsFormComponent,
    LanguagesFormComponent,
    CertificationsFormComponent,
    StrengthsFormComponent,
    SocialsFormComponent,
    SettingsFormComponent,
    LayoutFormComponent,
    CustomSectionsFormComponent,
  ],
  templateUrl: './resume-editor.component.html',
})
export class ResumeEditorComponent {
  private state = inject(ResumeStateService);

  /** The AI panel only makes sense for job-linked (tailored) drafts. */
  get visibleSections(): EditorSection[] {
    return this.state.draftJobId()
      ? this.sections
      : this.sections.filter(s => s.id !== 'ai-tailor');
  }

  sections: EditorSection[] = [
    { id: 'ai-tailor', label: 'resumeBuilder.section.aiTailor', icon: '✦', open: true },
    { id: 'personal', label: 'resumeBuilder.section.personal', icon: '👤', open: true },
    { id: 'experience', label: 'resumeBuilder.section.experience', icon: '💼', open: false },
    { id: 'education', label: 'resumeBuilder.section.education', icon: '🎓', open: false },
    { id: 'skills', label: 'resumeBuilder.section.skills', icon: '⚡', open: false },
    { id: 'projects', label: 'resumeBuilder.section.projects', icon: '🚀', open: false },
    { id: 'languages', label: 'resumeBuilder.section.languages', icon: '🌍', open: false },
    { id: 'certifications', label: 'resumeBuilder.section.certifications', icon: '🏅', open: false },
    { id: 'strengths', label: 'resumeBuilder.section.strengths', icon: '💪', open: false },
    { id: 'socials', label: 'resumeBuilder.section.socials', icon: '🔗', open: false },
    { id: 'settings', label: 'resumeBuilder.section.settings', icon: '⚙️', open: false },
    { id: 'layout', label: 'resumeBuilder.section.layout', icon: '🎨', open: false },
    { id: 'custom', label: 'resumeBuilder.section.custom', icon: '✏️', open: false },
  ];
}
