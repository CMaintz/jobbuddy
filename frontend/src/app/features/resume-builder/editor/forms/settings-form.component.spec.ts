import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TranslateModule } from '@ngx-translate/core';
import { ResumeDraftApiService } from '../../services/resume-draft-api.service';
import { ResumeStateService } from '../../services/resume-state.service';
import { SettingsFormComponent } from './settings-form.component';

const PHOTO_LABELS = [
  'resumeBuilder.settings.photoStyle', 'resumeBuilder.settings.photoSize', 'resumeBuilder.settings.photoPlacement',
];

describe('SettingsFormComponent photo settings', () => {
  let fixture: ComponentFixture<SettingsFormComponent>;
  let state: ResumeStateService;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [SettingsFormComponent, TranslateModule.forRoot()],
      providers: [{ provide: ResumeDraftApiService, useValue: {} }],
    });
    state = TestBed.inject(ResumeStateService);
    fixture = TestBed.createComponent(SettingsFormComponent);
  });

  function labels(): string[] {
    fixture.detectChanges();
    return [...(fixture.nativeElement as HTMLElement).querySelectorAll('label')].map(l => l.textContent?.trim() ?? '');
  }

  function photoLabelsShown(): string[] {
    return labels().filter(text => PHOTO_LABELS.some(key => text.startsWith(key)));
  }

  it('hides the photo settings for the layout that prints no photo', () => {
    // Bug: the minimal layout has no photo, so its photo controls change nothing she can see.
    state.updateSettings({ template: 'minimal' });

    expect(photoLabelsShown()).toEqual([]);
  });

  it('offers the photo settings for a layout that prints one', () => {
    // Bug: the check is inverted or matches every layout, so the photo can no longer be styled.
    state.updateSettings({ template: 'classic' });

    expect(photoLabelsShown()).toHaveLength(PHOTO_LABELS.length);
  });
});
