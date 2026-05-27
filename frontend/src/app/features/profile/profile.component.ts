import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ReactiveFormsModule, FormBuilder } from '@angular/forms';
import { FormsModule } from '@angular/forms';
import { Subject } from 'rxjs';
import { debounceTime, distinctUntilChanged, switchMap } from 'rxjs/operators';
import { ProfileSocialApiService } from '../../core/api/profile-social.api';
import { ProfileStrengthApiService } from '../../core/api/profile-strength.api';
import { SkillsApiService } from '../../core/api/skills.api';
import { ProfileSocial, ProfileStrength, WorkExperience, Project, Education, Certification, SpokenLanguage, LanguageProficiency } from '../../core/models/profile-section.model';
import { ProfileSkill, SkillTaxonomy, TECH_CATEGORIES } from '../../core/models/skill-taxonomy.model';
import { TabNavComponent } from '../../shared/components/ui/tab-nav.component';
import { SOCIAL_PLATFORMS, SocialPlatform, getSocialPlatformIcon } from '../resume-builder/data/social-platforms';
import {
  languageProficiencyLabel,
  PROFILE_TABS,
  ProfileTab,
  profileProficiencyBadgeClass,
  profileSkillChipClass
} from './profile.constants';
import {
  createCertificationForm,
  createEducationForm,
  createExperienceForm,
  createProfileForm,
  createProjectForm
} from './profile.forms';
import { ProfileOverviewFacade } from './profile-overview.facade';
import { ProfileSectionsFacade } from './profile-sections.facade';
import { ProfileSkillsFacade } from './profile-skills.facade';
import { ProfileCertificationsTabComponent } from './profile-certifications-tab.component';
import { ProfileEducationTabComponent } from './profile-education-tab.component';
import { ProfileExperienceTabComponent } from './profile-experience-tab.component';
import { SpokenLanguagesTabComponent } from './profile-languages-tab.component';
import { ProfileNewSkillModalComponent } from './profile-new-skill-modal.component';
import { ProfileOverviewTabComponent } from './profile-overview-tab.component';
import { ProfileProjectsTabComponent } from './profile-projects-tab.component';
import { ProfileSkillsTabComponent } from './profile-skills-tab.component';
import { ProfileSocialsTabComponent } from './profile-socials-tab.component';
import { ProfileStrengthsTabComponent } from './profile-strengths-tab.component';

@Component({
  selector: 'app-profile',
  standalone: true,
  imports: [
    CommonModule,
    ReactiveFormsModule,
    FormsModule,
    ProfileOverviewTabComponent,
    ProfileExperienceTabComponent,
    ProfileProjectsTabComponent,
    ProfileEducationTabComponent,
    ProfileCertificationsTabComponent,
    SpokenLanguagesTabComponent,
    ProfileNewSkillModalComponent,
    ProfileSkillsTabComponent,
    ProfileSocialsTabComponent,
    ProfileStrengthsTabComponent,
    TabNavComponent
  ],
  templateUrl: './profile.component.html'
})
export class ProfileComponent implements OnInit {
  private fb = inject(FormBuilder);
  private skillsApi = inject(SkillsApiService);
  private socialsApi = inject(ProfileSocialApiService);
  private strengthsApi = inject(ProfileStrengthApiService);
  private overviewFacade = inject(ProfileOverviewFacade);
  private sectionsFacade = inject(ProfileSectionsFacade);
  private skillsFacade = inject(ProfileSkillsFacade);

  readonly vm = this;
  activeTab: ProfileTab = 'overview';
  saveSuccess = false;
  showExpForm = false;
  showProjectForm = false;
  showEduForm = false;
  showCertForm = false;

  // Section-level skill picker state
  expEditSkills: SkillTaxonomy[] = [];
  projectEditSkills: SkillTaxonomy[] = [];
  educationEditSkills: SkillTaxonomy[] = [];
  pendingNewSkill: { name: string; section: 'exp' | 'project' | 'edu' } | null = null;
  pendingNewSkillCategory = '';
  availableCategories: string[] = [];

  importingLinkedIn = false;
  linkedInImportError = '';
  linkedInPreview: any = null;

  importingCvPdf = false;
  cvPdfImportError = '';
  cvPdfPreview: any = null;

  photoUrl = '';
  uploadingPhoto = false;
  photoError = '';

  tabs = PROFILE_TABS;

  experiences: WorkExperience[] = [];
  projects: Project[] = [];
  educations: Education[] = [];
  certifications: Certification[] = [];
  profileSkills: ProfileSkill[] = [];
  spokenLanguages: SpokenLanguage[] = [];

  // Languages tab state
  showLangForm = false;
  newLang: Partial<SpokenLanguage> = { language: '', proficiency: 'FLUENT', displayOrder: 0 };

  // Socials tab state
  socials: ProfileSocial[] = [];
  showSocialForm = false;
  socialPlatforms: SocialPlatform[] = SOCIAL_PLATFORMS;
  newSocial: Partial<ProfileSocial> = { platform: 'LinkedIn', url: '', username: '', iconKey: 'linkedin', displayOrder: 0 };

  // Strengths tab state
  strengths: ProfileStrength[] = [];
  showStrengthForm = false;
  newStrength: Partial<ProfileStrength> = { title: '', description: '', iconKey: 'star', displayOrder: 0 };

  // Skills tab state
  readonly TECH_CATEGORIES = TECH_CATEGORIES;

  get techSkills(): ProfileSkill[] {
    return this.profileSkills.filter(s => s.category && TECH_CATEGORIES.has(s.category));
  }
  get softSkills(): ProfileSkill[] {
    return this.profileSkills.filter(s => !s.category || !TECH_CATEGORIES.has(s.category));
  }

  editingSkillId: string | null = null;
  editingSkill: Partial<ProfileSkill> = {};

  toggleEditSkill(id: string): void {
    if (this.editingSkillId === id) {
      this.editingSkillId = null;
      return;
    }
    const skill = this.profileSkills.find(s => s.id === id);
    if (skill) {
      this.editingSkill = { proficiencyLevel: skill.proficiencyLevel, yearsExperience: skill.yearsExperience };
    }
    this.editingSkillId = id;
  }

  saveEditSkill(original: ProfileSkill): void {
    this.skillsFacade.saveEditSkill(this, original);
  }

  chipClass(skill: ProfileSkill): string {
    return profileSkillChipClass(skill);
  }

  newSkill: Partial<ProfileSkill> = {
    skillName: '',
    proficiencyLevel: 'INTERMEDIATE',
    yearsExperience: undefined,
    usedInProduction: false,
  };
  taxonomySuggestions: SkillTaxonomy[] = [];
  showDropdown = false;
  skillSaveError = '';
  private skillSearchSubject = new Subject<string>();

  profileForm = createProfileForm(this.fb);
  expForm = createExperienceForm(this.fb);
  projectForm = createProjectForm(this.fb);
  eduForm = createEducationForm(this.fb);
  certForm = createCertificationForm(this.fb);

  ngOnInit(): void {
    this.overviewFacade.load(this);
    this.sectionsFacade.load(this);
    this.socialsApi.getSocials().subscribe(d => this.socials = d);
    this.strengthsApi.getStrengths().subscribe(d => this.strengths = d);

    // Set up debounced skill search (profile skills tab)
    this.skillSearchSubject.pipe(
      debounceTime(300),
      distinctUntilChanged(),
      switchMap(q => q.length >= 1 ? this.skillsApi.searchTaxonomy(q) : [])
    ).subscribe({
      next: results => {
        this.taxonomySuggestions = results.slice(0, 8);
        this.showDropdown = this.taxonomySuggestions.length > 0;
      },
      error: () => { this.taxonomySuggestions = []; this.showDropdown = false; }
    });

    this.skillsFacade.loadCategories(this);
  }

  onSkillNameChange(value: string): void {
    this.showDropdown = false;
    if (value && value.length >= 1) {
      this.skillSearchSubject.next(value);
    } else {
      this.taxonomySuggestions = [];
    }
  }

  selectTaxonomySuggestion(suggestion: SkillTaxonomy): void {
    this.newSkill.skillName = suggestion.name;
    this.newSkill = { ...this.newSkill, taxonomyId: suggestion.id };
    this.taxonomySuggestions = [];
    this.showDropdown = false;
  }

  hideDropdownDelayed(): void {
    // Use a short timeout so mousedown on suggestion fires before blur hides it
    setTimeout(() => { this.showDropdown = false; }, 150);
  }

  proficiencyBadgeClass(level: string): string {
    return profileProficiencyBadgeClass(level);
  }

  // ── Section skill picker ─────────────────────────────────────────────────

  promptCreateSkillForSection(section: 'exp' | 'project' | 'edu', name: string): void {
    if (!name) return;
    this.pendingNewSkill = { name, section };
    this.pendingNewSkillCategory = '';
  }

  createSkillForSection(): void {
    this.skillsFacade.createSkillForSection(this);
  }

  // ── Profile skills tab ───────────────────────────────────────────────────

  addProfileSkill(): void {
    this.skillsFacade.addProfileSkill(this);
  }

  deleteProfileSkill(id: string): void {
    this.skillsFacade.deleteProfileSkill(this, id);
  }

  onPhotoSelected(file: File): void {
    this.overviewFacade.uploadPhoto(this, file);
  }

  onLinkedInPdfSelected(file: File): void {
    this.overviewFacade.importLinkedInPdf(this, file);
  }

  onCvPdfSelected(file: File): void {
    this.overviewFacade.importCvPdf(this, file);
  }

  applyCvPdfPreview(): void {
    this.overviewFacade.applyCvPdfPreview(this);
  }

  applyLinkedInPreview(): void {
    this.overviewFacade.applyLinkedInPreview(this);
  }

  saveProfile(): void {
    this.overviewFacade.save(this);
  }

  saveExperience(): void {
    this.sectionsFacade.saveExperience(this);
  }

  deleteExperience(id: string): void {
    this.sectionsFacade.deleteExperience(this, id);
  }

  saveProject(): void {
    this.sectionsFacade.saveProject(this);
  }

  deleteProject(id: string): void {
    this.sectionsFacade.deleteProject(this, id);
  }

  saveEducation(): void {
    this.sectionsFacade.saveEducation(this);
  }

  deleteEducation(id: string): void {
    this.sectionsFacade.deleteEducation(this, id);
  }

  saveCertification(): void {
    this.sectionsFacade.saveCertification(this);
  }

  deleteCertification(id: string): void {
    this.sectionsFacade.deleteCertification(this, id);
  }

  // ── Spoken Languages ─────────────────────────────────────────────────────

  saveLanguage(): void {
    this.sectionsFacade.saveLanguage(this);
  }

  deleteLanguage(id: string): void {
    this.sectionsFacade.deleteLanguage(this, id);
  }

  proficiencyLabel(p: LanguageProficiency): string {
    return languageProficiencyLabel(p);
  }

  // ── Social Links ─────────────────────────────────────────────────────────

  onSocialPlatformChange(): void {
    const platform = SOCIAL_PLATFORMS.find(p => p.platform === this.newSocial.platform);
    if (platform) this.newSocial.iconKey = platform.iconKey;
  }

  saveSocial(): void {
    if (!this.newSocial.url?.trim()) return;
    const payload: Omit<ProfileSocial, 'id' | 'userId'> = {
      platform: this.newSocial.platform!,
      url: this.newSocial.url!.trim(),
      username: this.newSocial.username?.trim() || undefined,
      iconKey: this.newSocial.iconKey!,
      displayOrder: this.socials.length,
    };
    this.socialsApi.createSocial(payload).subscribe(() => {
      this.socialsApi.getSocials().subscribe(d => this.socials = d);
      this.newSocial = { platform: 'LinkedIn', url: '', username: '', iconKey: 'linkedin', displayOrder: 0 };
      this.showSocialForm = false;
    });
  }

  deleteSocial(id: string): void {
    this.socialsApi.deleteSocial(id).subscribe(() => {
      this.socialsApi.getSocials().subscribe(d => this.socials = d);
    });
  }

  getSocialIcon(iconKey: string): string {
    return getSocialPlatformIcon(iconKey);
  }

  // ── Strengths ─────────────────────────────────────────────────────────────

  saveStrength(): void {
    if (!this.newStrength.title?.trim()) return;
    const payload: Omit<ProfileStrength, 'id' | 'userId'> = {
      title: this.newStrength.title!.trim(),
      description: this.newStrength.description?.trim() || undefined,
      iconKey: this.newStrength.iconKey ?? 'star',
      displayOrder: this.strengths.length,
    };
    this.strengthsApi.createStrength(payload).subscribe(() => {
      this.strengthsApi.getStrengths().subscribe(d => this.strengths = d);
      this.newStrength = { title: '', description: '', iconKey: 'star', displayOrder: 0 };
      this.showStrengthForm = false;
    });
  }

  deleteStrength(id: string): void {
    this.strengthsApi.deleteStrength(id).subscribe(() => {
      this.strengthsApi.getStrengths().subscribe(d => this.strengths = d);
    });
  }
}
