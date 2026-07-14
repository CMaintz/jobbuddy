import { Component, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
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
    { id: 'ai-tailor', label: 'AI Tailoring & ATS', icon: '✦', open: true },
    { id: 'personal', label: 'Personal Info', icon: '👤', open: true },
    { id: 'experience', label: 'Work Experience', icon: '💼', open: false },
    { id: 'education', label: 'Education', icon: '🎓', open: false },
    { id: 'skills', label: 'Skills', icon: '⚡', open: false },
    { id: 'projects', label: 'Projects', icon: '🚀', open: false },
    { id: 'languages', label: 'Languages', icon: '🌍', open: false },
    { id: 'certifications', label: 'Certifications', icon: '🏅', open: false },
    { id: 'strengths', label: 'Strengths', icon: '💪', open: false },
    { id: 'socials', label: 'Social Links', icon: '🔗', open: false },
    { id: 'settings', label: 'Settings', icon: '⚙️', open: false },
    { id: 'layout', label: 'Layout & Template', icon: '🎨', open: false },
    { id: 'custom', label: 'Custom Sections', icon: '✏️', open: false },
  ];
}
