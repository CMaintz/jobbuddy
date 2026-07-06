import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { JobsApiService } from '../../../../core/api/jobs.api';
import { Job } from '../../../../core/models/job.model';
import { EmptyStateComponent } from '../../../../shared/components/ui/empty-state.component';
import { runAction } from '../../../../shared/utils/async-ui';

@Component({
  selector: 'app-saved-jobs',
  standalone: true,
  imports: [CommonModule, RouterLink, EmptyStateComponent],
  templateUrl: './saved-jobs.component.html'
})
export class SavedJobsComponent implements OnInit {
  private jobsApi = inject(JobsApiService);
  jobs: Job[] = [];
  loading = true;

  ngOnInit(): void {
    runAction({
      action$: this.jobsApi.getSaved(),
      setLoading: value => this.loading = value,
      next: jobs => this.jobs = jobs
    });
  }

  unsave(job: Job): void {
    this.jobsApi.unsave(job.id).subscribe(() => {
      this.jobs = this.jobs.filter(j => j.id !== job.id);
    });
  }
}
