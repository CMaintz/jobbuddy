import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { BehaviorSubject, of } from 'rxjs';
import { TranslateModule } from '@ngx-translate/core';
import { JobDetailsComponent } from './job-details.component';
import { JobsApiService } from '../../core/api/jobs.api';
import { ApplicationsApiService } from '../../core/api/applications.api';
import { NotesApiService } from '../../core/api/notes.api';
import { Job } from '../../core/models/job.model';
import { spyObj } from '../../../testing/spy-obj';

describe('JobDetailsComponent', () => {
  it('shows the new posting when only the id in the URL changes, as stepping through does', () => {
    const params = new BehaviorSubject(convertToParamMap({ id: 'a' }));
    const jobs = spyObj<JobsApiService>(['getById', 'getSaved', 'getSimilar', 'getDocumentsForJob']);
    jobs.getById.mockImplementation((id: string) => of({ id, title: 'Opslag ' + id } as Job));
    jobs.getSaved.mockReturnValue(of([]));
    jobs.getSimilar.mockReturnValue(of([]));
    jobs.getDocumentsForJob.mockReturnValue(of([]));
    const apps = spyObj<ApplicationsApiService>(['getAll']);
    apps.getAll.mockReturnValue(of([]));
    const notes = spyObj<NotesApiService>(['getForJob']);
    notes.getForJob.mockReturnValue(of([]));
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
