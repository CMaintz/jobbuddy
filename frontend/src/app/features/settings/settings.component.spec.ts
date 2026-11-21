import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ActivatedRoute, convertToParamMap } from '@angular/router';
import { TranslateModule } from '@ngx-translate/core';
import { of } from 'rxjs';
import { AiApiService } from '../../core/api/ai.api';
import { StructuredDocumentTemplatesApiService } from '../../core/api/structured-document-templates.api';
import { WritingProfileApiService } from '../../core/api/writing-profile.api';
import { AuthService } from '../../core/auth/auth.service';
import { UserPreferences } from '../../core/models/user.model';
import { NotifyService } from '../../shared/services/notify.service';
import { SettingsComponent } from './settings.component';

const PREFERENCES_URL = '/api/v1/users/me/preferences';

const SAVED: UserPreferences = {
  preferredLocations: [], preferredMunicipalities: [], positiveSignals: [], negativeSignals: [],
  excludedCompanies: [], preferredRemoteTypes: [], preferredEmploymentTypes: [], preferredSeniority: [],
  preferredIndustries: [], excludedCategories: [], enrichmentArea: null, salaryMin: null,
  salaryMax: null, maxCommuteKm: null, notificationEnabled: false, notificationFrequency: null,
  weeklyApplicationGoal: 5,
};

/** The preference fields Settings saves, driven through the component with the page left out. */
describe('SettingsComponent preferences', () => {
  let http: HttpTestingController;
  let component: SettingsComponent;
  let notify: jasmine.SpyObj<NotifyService>;

  beforeEach(() => {
    notify = jasmine.createSpyObj<NotifyService>('NotifyService', ['success', 'error', 'errorText', 'onError']);
    notify.onError.and.returnValue(() => undefined);
    TestBed.configureTestingModule({
      imports: [SettingsComponent, TranslateModule.forRoot()],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: AuthService, useValue: { currentUser$: of(null), isEmailVerified: () => true, signInProvider: () => 'password', canSignOut: () => true } },
        { provide: ActivatedRoute, useValue: { snapshot: { queryParamMap: convertToParamMap({}) } } },
        { provide: WritingProfileApiService, useValue: { get: () => of({}) } },
        { provide: StructuredDocumentTemplatesApiService, useValue: { getActive: () => of([]) } },
        { provide: AiApiService, useValue: {} },
        { provide: NotifyService, useValue: notify },
      ],
    });
    TestBed.overrideComponent(SettingsComponent, { set: { template: '', imports: [] } });
    const fixture = TestBed.createComponent(SettingsComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
    http = TestBed.inject(HttpTestingController);
    http.expectOne(PREFERENCES_URL).flush(SAVED);
  });

  afterEach(() => http.verify());

  function savedBody(): Partial<UserPreferences> {
    component.save();
    const request = http.expectOne(r => r.method === 'PUT' && r.url === PREFERENCES_URL);
    const body = request.request.body as Partial<UserPreferences>;
    request.flush(SAVED);
    return body;
  }

  it('sends a category she toggles off as excludedCategories', () => {
    // Bug: the toggle only changes the page, so the backend never learns what to skip.
    component.toggleExcludedCategory('SALES');

    expect(savedBody().excludedCategories).toEqual(['SALES']);
  });

  it('sends the area she types, trimmed, with its radius', () => {
    // Bug: the area fields are not in the payload, so the backend keeps the default area.
    component.areaHome = '  8000 Aarhus C ';
    component.areaRadiusKm = 60;

    expect(savedBody().enrichmentArea).toEqual({ home: '8000 Aarhus C', radiusKm: 60 });
  });

  it('shows the backend message as it is when it rejects her home', () => {
    // Bug: every failed save shows the generic toast, so she never learns that the town she
    // typed was not found or what to type instead.
    const message = 'Vi kan ikke finde "Atlantis". Skriv et postnummer eller et bynavn, fx 8260 Viby J.';
    component.areaHome = 'Atlantis';
    component.save();

    http.expectOne(r => r.method === 'PUT' && r.url === PREFERENCES_URL)
      .flush({ message }, { status: 400, statusText: 'Bad Request' });

    expect(notify.errorText).toHaveBeenCalledWith(message);
    expect(notify.error).not.toHaveBeenCalled();
  });

  it('keeps the generic toast for any other failure', () => {
    // Bug: a server error body is shown raw to her.
    component.save();

    http.expectOne(r => r.method === 'PUT' && r.url === PREFERENCES_URL)
      .flush({ message: 'NullPointerException' }, { status: 500, statusText: 'Server Error' });

    expect(notify.error).toHaveBeenCalledWith('settings.toast.saveFailed');
    expect(notify.errorText).not.toHaveBeenCalled();
  });
});
