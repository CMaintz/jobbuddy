import { Injectable, inject } from '@angular/core';
import { ProfileSectionsApiService } from '../../../core/api/profile-sections.api';
import { SkillsApiService } from '../../../core/api/skills.api';
import { deleteAndReload } from '../../../shared/utils/crud-actions';
import * as profileActions from './profile.actions';
import type { ProfileComponent } from './profile.component';

@Injectable({ providedIn: 'root' })
export class ProfileSectionsFacade {
  private sectionsApi = inject(ProfileSectionsApiService);
  private skillsApi = inject(SkillsApiService);

  load(vm: ProfileComponent): void {
    profileActions.loadSections(vm, this.sectionsApi, this.skillsApi);
  }

  saveExperience(vm: ProfileComponent): void {
    profileActions.saveExperience(vm, this.sectionsApi);
  }

  deleteExperience(vm: ProfileComponent, id: string): void {
    deleteAndReload(this.sectionsApi.deleteExperience(id), this.sectionsApi.getExperience(), d => vm.experiences = d);
  }

  saveProject(vm: ProfileComponent): void {
    profileActions.saveProject(vm, this.sectionsApi);
  }

  deleteProject(vm: ProfileComponent, id: string): void {
    deleteAndReload(this.sectionsApi.deleteProject(id), this.sectionsApi.getProjects(), d => vm.projects = d);
  }

  saveEducation(vm: ProfileComponent): void {
    profileActions.saveEducation(vm, this.sectionsApi);
  }

  deleteEducation(vm: ProfileComponent, id: string): void {
    deleteAndReload(this.sectionsApi.deleteEducation(id), this.sectionsApi.getEducation(), d => vm.educations = d);
  }

  saveCertification(vm: ProfileComponent): void {
    profileActions.saveCertification(vm, this.sectionsApi);
  }

  deleteCertification(vm: ProfileComponent, id: string): void {
    deleteAndReload(this.sectionsApi.deleteCertification(id), this.sectionsApi.getCertifications(), d => vm.certifications = d);
  }

  saveLanguage(vm: ProfileComponent): void {
    profileActions.saveLanguage(vm, this.sectionsApi);
  }

  deleteLanguage(vm: ProfileComponent, id: string): void {
    deleteAndReload(this.sectionsApi.deleteLanguage(id), this.sectionsApi.getLanguages(), d => vm.spokenLanguages = d);
  }
}
