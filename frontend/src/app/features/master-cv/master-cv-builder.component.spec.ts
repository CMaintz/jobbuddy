import { TestBed } from '@angular/core/testing';
import { HttpClient } from '@angular/common/http';
import { provideRouter } from '@angular/router';
import { TranslateModule } from '@ngx-translate/core';
import { of, throwError } from 'rxjs';
import { MasterCvBuilderComponent } from './master-cv-builder.component';
import { ProfileSectionsApiService, FullProfileResponse } from '../../core/api/profile-sections.api';
import { ProfilePrivateApiService } from '../../core/api/profile-private.api';
import { ProfileStrengthApiService } from '../../core/api/profile-strength.api';
import { ProfileSocialApiService } from '../../core/api/profile-social.api';
import { SkillsApiService } from '../../core/api/skills.api';
import { AiApiService } from '../../core/api/ai.api';
import { AtsPdfService } from '../../shared/services/ats-pdf.service';
import { NotifyService } from '../../shared/services/notify.service';
import { WorkExperience } from '../../core/models/profile-section.model';

const role = (id: string): WorkExperience =>
  ({ id, companyName: `Company ${id}`, title: 'Engineer', startDate: '2020-01-01' }) as WorkExperience;

function setup(experience: WorkExperience[] = []) {
  const full: FullProfileResponse = {
    profile: {}, experience, education: [], projects: [], certifications: [],
    languages: [], socials: [], strengths: [], customSections: [],
  };
  TestBed.configureTestingModule({
    imports: [MasterCvBuilderComponent, TranslateModule.forRoot()],
    providers: [
      provideRouter([]),
      { provide: HttpClient, useValue: {} },
      { provide: ProfileSectionsApiService, useValue: {
        getFullProfile: () => of(full),
        deleteExperience: () => throwError(() => new Error('offline')),
      } },
      { provide: ProfilePrivateApiService, useValue: { getPrivateInfo: () => of({}) } },
      { provide: SkillsApiService, useValue: {
        getProfileSkills: () => of([]),
        addProfileSkill: () => throwError(() => new Error('offline')),
      } },
      { provide: ProfileStrengthApiService, useValue: {} },
      { provide: ProfileSocialApiService, useValue: {} },
      { provide: AiApiService, useValue: {} },
      { provide: AtsPdfService, useValue: {} },
      { provide: NotifyService, useValue: { error: () => undefined, success: () => undefined } },
    ],
  });
  const fixture = TestBed.createComponent(MasterCvBuilderComponent);
  fixture.detectChanges();
  return fixture;
}

describe('MasterCvBuilderComponent', () => {
  // The preview only re-renders when it is handed a new object, so an edit that writes into the
  // existing one leaves it showing the old text.
  it('shows a header edit in the preview', () => {
    const fixture = setup();
    const page: HTMLElement = fixture.nativeElement;
    const nameInput = page.querySelector('input[placeholder="onboarding.profile.fullNamePlaceholder"]') as HTMLInputElement;

    nameInput.value = 'Ada Lovelace';
    nameInput.dispatchEvent(new Event('input'));
    fixture.detectChanges();

    expect(page.querySelector('app-master-cv-preview')?.textContent).toContain('Ada Lovelace');
  });

  it('puts an entry back where it was when deleting it fails', () => {
    const fixture = setup([role('a'), role('b'), role('c')]);
    const builder = fixture.componentInstance;

    builder.removeExperience(1);

    expect(builder.experienceList().map(e => e.id)).toEqual(['a', 'b', 'c']);
  });

  it('drops a skill chip that could not be saved', () => {
    const fixture = setup();
    fixture.componentInstance.activeSec.set('Skills');
    fixture.detectChanges();
    const skillsInput = fixture.nativeElement.querySelector('jb-tag-input input') as HTMLInputElement;

    skillsInput.value = 'Kotlin';
    skillsInput.dispatchEvent(new Event('input'));
    skillsInput.dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter' }));
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('jb-tag-input')?.textContent).not.toContain('Kotlin');
  });
});
