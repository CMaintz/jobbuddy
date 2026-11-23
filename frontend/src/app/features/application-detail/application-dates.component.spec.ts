import { TestBed } from '@angular/core/testing';
import { TranslateModule } from '@ngx-translate/core';
import { of } from 'rxjs';
import { ApplicationsApiService } from '../../core/api/applications.api';
import { Application } from '../../core/models/application.model';
import { ApplicationDatesComponent } from './application-dates.component';

describe('ApplicationDatesComponent', () => {
  let api: jasmine.SpyObj<ApplicationsApiService>;

  function create(app: Partial<Application>): ApplicationDatesComponent {
    api = jasmine.createSpyObj<ApplicationsApiService>('ApplicationsApiService', ['updateDates']);
    api.updateDates.and.callFake((_id, dates) => of({ ...app, ...dates } as Application));
    TestBed.configureTestingModule({
      imports: [ApplicationDatesComponent, TranslateModule.forRoot()],
      providers: [{ provide: ApplicationsApiService, useValue: api }],
    });
    const fixture = TestBed.createComponent(ApplicationDatesComponent);
    fixture.componentRef.setInput('application', { id: 'a1', ...app } as Application);
    fixture.detectChanges();
    return fixture.componentInstance;
  }

  it('sends the interview time exactly as typed', () => {
    const component = create({});
    component.deadline = '2026-11-18';
    component.interviewAt = '2026-11-20T09:00';

    component.save();

    expect(api.updateDates).toHaveBeenCalledWith('a1', { deadline: '2026-11-18', interviewAt: '2026-11-20T09:00' });
  });

  it('sends an emptied field as no date, so the server clears it', () => {
    const component = create({ deadline: '2026-11-18', interviewAt: '2026-11-20T09:00:00' });
    component.deadline = '';

    component.save();

    expect(api.updateDates).toHaveBeenCalledWith('a1', { deadline: null, interviewAt: '2026-11-20T09:00' });
  });
});
