import { Component, OnInit, inject, signal, ChangeDetectionStrategy } from '@angular/core';
import { RouterLink } from '@angular/router';
import { TranslateModule } from '@ngx-translate/core';
import { JbIconComponent } from '../../shared/components/jb-icon/jb-icon.component';
import { JbTopbarComponent } from '../../shared/components/jb-topbar/jb-topbar.component';
import { JbButtonComponent } from '../../shared/components/jb-button/jb-button.component';
import { JbPillComponent } from '../../shared/components/jb-pill/jb-pill.component';
import { CompanyMarkComponent } from '../../shared/components/company-mark/company-mark.component';
import { JobsApiService } from '../../core/api/jobs.api';
import { Job } from '../../core/models/job.model';

@Component({
  selector: 'app-saved-roles',
  imports: [
    JbTopbarComponent, RouterLink, TranslateModule, JbIconComponent, JbButtonComponent,
    JbPillComponent, CompanyMarkComponent,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './saved-roles.component.html'
})
export class SavedRolesComponent implements OnInit {
  private jobsApi = inject(JobsApiService);

  savedJobs = signal<Job[]>([]);
  loading = signal(true);

  ngOnInit(): void {
    this.jobsApi.getSaved().subscribe({
      next: (jobs) => { this.savedJobs.set(jobs); this.loading.set(false); },
      error: () => this.loading.set(false)
    });
  }

  remove(job: Job, event: Event): void {
    event.preventDefault();
    event.stopPropagation();
    this.savedJobs.update(jobs => jobs.filter(j => j.id !== job.id));
    this.jobsApi.unsave(job.id).subscribe({
      error: () => this.savedJobs.update(jobs => [...jobs, job])
    });
  }
}
