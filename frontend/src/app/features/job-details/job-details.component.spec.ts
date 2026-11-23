import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { BehaviorSubject, of } from 'rxjs';
import { TranslateModule } from '@ngx-translate/core';
import { JobDetailsComponent } from './job-details.component';
import { JobsApiService } from '../../core/api/jobs.api';
import { ApplicationsApiService } from '../../core/api/applications.api';
import { NotesApiService } from '../../core/api/notes.api';
import { Job } from '../../core/models/job.model';

describe('JobDetailsComponent', () => {
  it('shows the new posting when only the id in the URL changes, as stepping through does', () => {
    const params = new BehaviorSubject(convertToParamMap({ id: 'a' }));
    const jobs = jasmine.createSpyObj<JobsApiService>('JobsApiService',
      ['getById', 'getSaved', 'getSimilar', 'getDocumentsForJob']);
    jobs.getById.and.callFake((id: string) => of({ id, title: 'Opslag ' + id } as Job));
    jobs.getSaved.and.returnValue(of([]));
    jobs.getSimilar.and.returnValue(of([]));
    jobs.getDocumentsForJob.and.returnValue(of([]));
    const apps = jasmine.createSpyObj<ApplicationsApiService>('ApplicationsApiService', ['getAll']);
    apps.getAll.and.returnValue(of([]));
    const notes = jasmine.createSpyObj<NotesApiService>('NotesApiService', ['getForJob']);
    notes.getForJob.and.returnValue(of([]));
    TestBed.configureTestingModule({
      imports: [JobDetailsComponent, TranslateModule.forRoot()],
      providers: [
        provideRouter([]),
        { provide: ActivatedRoute, useValue: { paramMap: params, snapshot: { paramMap: params.value } } },
        { provide: JobsApiService, useValue: jobs },
        { provide: ApplicationsApiService, useValue: apps },
        { provide: NotesApiService, useValue: notes },
      ],
    });
    const fixture = TestBed.createComponent(JobDetailsComponent);
    fixture.detectChanges();

    params.next(convertToParamMap({ id: 'b' }));
    fixture.detectChanges();

    expect(fixture.componentInstance.job()?.id).toBe('b');
  });
});
