import { Component, Input, Output, EventEmitter } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { Job, MatchResult } from '../../../core/models/job.model';
import { FeedbackType } from '../../../core/models/profile-section.model';

@Component({
  selector: 'app-job-card',
  standalone: true,
  imports: [CommonModule, RouterLink],
  templateUrl: './job-card.component.html'
})
export class JobCardComponent {
  @Input() job!: Job;
  @Input() matchResult?: MatchResult;
  @Output() save = new EventEmitter<string>();
  @Output() feedback = new EventEmitter<{ jobId: string; type: FeedbackType }>();

  matchLabelClass(label: string): string {
    const map: Record<string, string> = {
      EXCELLENT: 'bg-green-100 text-green-700',
      STRONG:    'bg-blue-100 text-blue-700',
      MODERATE:  'bg-yellow-100 text-yellow-700',
      WEAK:      'bg-gray-100 text-gray-600',
    };
    return map[label] ?? 'bg-gray-100 text-gray-600';
  }
}
