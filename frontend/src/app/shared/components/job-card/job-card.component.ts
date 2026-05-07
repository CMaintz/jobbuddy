import { Component, Input, Output, EventEmitter } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { Job } from '../../../core/models/job.model';

@Component({
  selector: 'app-job-card',
  standalone: true,
  imports: [CommonModule, RouterLink],
  template: `
    <div class="card hover:shadow-md transition-shadow">
      <div class="flex items-start justify-between">
        <div class="flex-1 min-w-0">
          <a [routerLink]="['/jobs', job.id]"
             class="text-base font-semibold text-blue-600 hover:underline block truncate">
            {{ job.title }}
          </a>
          <div class="text-sm text-gray-500 mt-0.5">
            {{ job.companyName }}
            @if (job.location) { &bull; {{ job.location }} }
          </div>
          @if (job.technologies?.length) {
            <div class="flex flex-wrap gap-1 mt-2">
              @for (tech of job.technologies!.slice(0, 4); track tech) {
                <span class="text-xs bg-blue-50 text-blue-700 px-1.5 py-0.5 rounded">{{ tech }}</span>
              }
            </div>
          }
        </div>
        <div class="ml-3 flex flex-col items-end gap-1 shrink-0">
          @if (job.salaryMin) {
            <span class="text-xs text-gray-600">{{ job.salaryMin | number:'1.0-0' }}+</span>
          }
          <button (click)="save.emit(job.id)"
                  class="text-xs text-gray-400 hover:text-blue-600">Save</button>
        </div>
      </div>
    </div>
  `
})
export class JobCardComponent {
  @Input() job!: Job;
  @Output() save = new EventEmitter<string>();
}
