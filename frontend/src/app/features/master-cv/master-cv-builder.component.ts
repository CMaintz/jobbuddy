import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { JbIconComponent } from '../../shared/components/jb-icon/jb-icon.component';
import { JbButtonComponent } from '../../shared/components/jb-button/jb-button.component';
import { ProfileSectionsApiService, FullProfileResponse } from '../../core/api/profile-sections.api';

interface CvSection {
  key: string;
  label: string;
  count: string;
  empty?: boolean;
}

@Component({
  selector: 'app-master-cv-builder',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, JbIconComponent, JbButtonComponent],
  templateUrl: './master-cv-builder.component.html'
})
export class MasterCvBuilderComponent implements OnInit {
  private profileApi = inject(ProfileSectionsApiService);

  loading = true;
  activeSec = signal('Experience');

  profileData = {
    name: '', title: '', email: '', phone: '', website: '', location: '', summary: ''
  };

  experienceList: any[] = [];
  educationList: any[] = [];
  projectsList: any[] = [];
  languagesList: any[] = [];
  skillsList: string[] = [];
  strengthsList: string[] = [];

  get coverage(): number {
    const checks = [
      this.profileData.summary.length > 60,
      this.strengthsList.length >= 3,
      this.experienceList.length >= 2,
      this.skillsList.length >= 6,
      this.educationList.length >= 1,
      this.languagesList.length >= 1,
      this.experienceList.some((j: any) => j.description?.length > 50),
      this.projectsList.length >= 1,
    ];
    return Math.round((checks.filter(Boolean).length / 8) * 100);
  }

  sections(): CvSection[] {
    return [
      { key: 'Header', label: 'Header', count: '6 fields' },
      { key: 'Profile', label: 'Profile', count: `${this.profileData.summary.trim().split(/\s+/).filter(Boolean).length} words` },
      { key: 'Strengths', label: 'Strengths', count: `${this.strengthsList.length}` },
      { key: 'Experience', label: 'Experience', count: `${this.experienceList.length} roles` },
      { key: 'Skills', label: 'Skills', count: `${this.skillsList.length}` },
      { key: 'Education', label: 'Education', count: `${this.educationList.length}` },
      { key: 'Projects', label: 'Projects', count: `${this.projectsList.length}`, empty: this.projectsList.length === 0 },
      { key: 'Languages', label: 'Languages', count: `${this.languagesList.length}` },
    ];
  }

  sectionHint(): string {
    const hints: Record<string, string> = {
      Header: 'Your name and contact details',
      Profile: 'The opening summary',
      Strengths: 'Punchy strengths as pills',
      Experience: 'Roles & achievements',
      Skills: 'Searchable skill tags',
      Education: 'Degrees & schools',
      Projects: 'Side projects & open source',
      Languages: 'Languages & levels',
    };
    return hints[this.activeSec()] || '';
  }

  ngOnInit(): void {
    this.profileApi.getFullProfile().subscribe({
      next: (resp: FullProfileResponse) => {
        if (resp.profile) {
          this.profileData.title = resp.profile.headline || '';
          this.profileData.summary = resp.profile.summary || '';
          this.skillsList = resp.profile.skills || [];
        }
        this.experienceList = resp.experience || [];
        this.educationList = resp.education || [];
        this.projectsList = resp.projects || [];
        this.languagesList = resp.languages || [];
        this.strengthsList = (resp.strengths || []).map((s: any) => s.text || s.name || '');
        this.loading = false;
      },
      error: () => this.loading = false
    });
  }
}
