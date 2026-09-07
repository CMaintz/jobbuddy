import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
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
  template: `
    <div class="divide-y divide-gray-100">
      @for (section of sections; track section.id) {
        <div>
          <button
            class="w-full flex items-center justify-between px-4 py-3 text-left hover:bg-gray-50 transition-colors"
            (click)="section.open = !section.open"
          >
            <div class="flex items-center gap-2.5">
              <span class="text-base">{{ section.icon }}</span>
              <span class="text-sm font-medium text-gray-800">{{ section.label }}</span>
            </div>
            <svg
              class="w-4 h-4 text-gray-400 transition-transform"
              [class.rotate-180]="section.open"
              fill="none" stroke="currentColor" viewBox="0 0 24 24"
            >
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M19 9l-7 7-7-7"/>
            </svg>
          </button>
          @if (section.open) {
            <div class="px-4 pb-4 pt-1">
              @switch (section.id) {
                @case ('personal') { <app-personal-info-form /> }
                @case ('experience') { <app-experience-form /> }
                @case ('education') { <app-education-form /> }
                @case ('skills') { <app-skills-form /> }
                @case ('projects') { <app-projects-form /> }
                @case ('languages') { <app-languages-form /> }
                @case ('certifications') { <app-certifications-form /> }
                @case ('strengths') { <app-strengths-form /> }
                @case ('socials') { <app-socials-form /> }
                @case ('settings') { <app-settings-form /> }
                @case ('layout') { <app-layout-form /> }
                @case ('custom') { <app-custom-sections-form /> }
              }
            </div>
          }
        </div>
      }
    </div>
  `,
})
export class ResumeEditorComponent {
  sections: EditorSection[] = [
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
