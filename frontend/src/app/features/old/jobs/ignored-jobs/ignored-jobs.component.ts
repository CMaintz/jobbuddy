import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { JobsApiService, IgnoredJob } from '../../../../core/api/jobs.api';
import { EmptyStateComponent } from '../../../../shared/components/ui/empty-state.component';
import { runAction } from '../../../../shared/utils/async-ui';

@Component({
  selector: 'app-ignored-jobs',
  standalone: true,
  imports: [CommonModule, RouterLink, EmptyStateComponent],
  templateUrl: './ignored-jobs.component.html'
})
export class IgnoredJobsComponent implements OnInit {
  private api = inject(JobsApiService);

  items: IgnoredJob[] = [];
  loading = true;

  ngOnInit(): void {
    runAction({
      action$: this.api.getIgnored(),
      setLoading: value => this.loading = value,
      next: items => this.items = items
    });
  }

  unignore(item: IgnoredJob): void {
    this.api.unignore(item.jobId).subscribe(() => {
      this.items = this.items.filter(i => i.id !== item.id);
    });
  }
}
