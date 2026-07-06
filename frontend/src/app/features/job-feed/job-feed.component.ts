import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { JbIconComponent } from '../../shared/components/jb-icon/jb-icon.component';
import { JbButtonComponent } from '../../shared/components/jb-button/jb-button.component';
import { JbPillComponent } from '../../shared/components/jb-pill/jb-pill.component';
import { CompanyMarkComponent } from '../../shared/components/company-mark/company-mark.component';
import { FitBarComponent } from '../../shared/components/fit-bar/fit-bar.component';
import { JobsApiService } from '../../core/api/jobs.api';

interface FeedJob {
  id: string;
  title: string;
  company: string;
  location: string;
  salary?: string;
  matchScore: number;
  matchReasons: string[];
  keywords: string[];
  source: string;
  posted: string;
  remote: boolean;
}

@Component({
  selector: 'app-job-feed',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, JbIconComponent, JbButtonComponent, JbPillComponent, CompanyMarkComponent, FitBarComponent],
  templateUrl: './job-feed.component.html'
})
export class JobFeedComponent implements OnInit {
  private jobsApi = inject(JobsApiService);

  query = '';
  minMatch = 60;
  locationFilter = 'all';
  activeSources = new Set(['Careers', 'Greenhouse', 'Lever']);
  selectedJob = signal<FeedJob | null>(null);
  mobilePanel = signal<'list' | 'detail'>('list');

  locationFilters = [
    { label: 'All', value: 'all' },
    { label: 'Remote only', value: 'remote' },
    { label: 'On-site', value: 'onsite' },
  ];

  sourceFilters = ['Careers', 'Greenhouse', 'Lever', 'Ashby', 'Extension'];

  // Mock feed data — will be replaced by real API
  feedJobs: FeedJob[] = [
    { id: '1', title: 'Design Engineer', company: 'Anthropic', location: 'San Francisco · Remote', salary: '$200–250k', matchScore: 92, matchReasons: ['React', 'TypeScript', 'Design systems', 'AI company'], keywords: ['React', 'TypeScript', 'Figma', 'CSS', 'Design systems'], source: 'Careers', posted: '2d ago', remote: true },
    { id: '2', title: 'Senior Frontend Engineer', company: 'Vercel', location: 'Remote · EU', salary: '$180–220k', matchScore: 88, matchReasons: ['React', 'TypeScript', 'Performance'], keywords: ['Next.js', 'React', 'TypeScript', 'Edge'], source: 'Greenhouse', posted: '3d ago', remote: true },
    { id: '3', title: 'Product Engineer', company: 'Linear', location: 'Remote', salary: '$170–210k', matchScore: 85, matchReasons: ['TypeScript', 'Product focus', 'DevTools'], keywords: ['TypeScript', 'React', 'GraphQL', 'Postgres'], source: 'Lever', posted: '1d ago', remote: true },
    { id: '4', title: 'Frontend Engineer', company: 'Stripe', location: 'Dublin · Hybrid', salary: '€130–160k', matchScore: 81, matchReasons: ['React', 'Accessibility', 'Scale'], keywords: ['React', 'Ruby', 'Accessibility', 'Payments'], source: 'Careers', posted: '5d ago', remote: false },
    { id: '5', title: 'Staff Engineer', company: 'Notion', location: 'San Francisco', salary: '$220–280k', matchScore: 76, matchReasons: ['TypeScript', 'Productivity'], keywords: ['TypeScript', 'React', 'Collaboration', 'CRDT'], source: 'Greenhouse', posted: '1w ago', remote: false },
    { id: '6', title: 'Web Engineer', company: 'Figma', location: 'London · Hybrid', salary: '£120–150k', matchScore: 73, matchReasons: ['Design tools', 'WebGL'], keywords: ['WebGL', 'Canvas', 'TypeScript', 'C++'], source: 'Careers', posted: '4d ago', remote: false },
    { id: '7', title: 'Frontend Platform', company: 'Datadog', location: 'Paris · Remote EU', salary: '€110–140k', matchScore: 69, matchReasons: ['TypeScript', 'Monitoring'], keywords: ['TypeScript', 'Angular', 'RxJS', 'Performance'], source: 'Lever', posted: '6d ago', remote: true },
  ];

  ngOnInit(): void {
    // Try to load recommended jobs from API
    this.jobsApi.getRecommendations?.()?.subscribe({
      next: () => {}, // Could merge with mock data
      error: () => {}
    });
  }

  filteredJobs(): FeedJob[] {
    return this.feedJobs.filter(job => {
      if (job.matchScore < this.minMatch) return false;
      if (this.locationFilter === 'remote' && !job.remote) return false;
      if (this.locationFilter === 'onsite' && job.remote) return false;
      if (this.query) {
        const q = this.query.toLowerCase();
        if (!job.title.toLowerCase().includes(q) && !job.company.toLowerCase().includes(q)) return false;
      }
      return true;
    });
  }

  toggleSource(src: string): void {
    if (this.activeSources.has(src)) this.activeSources.delete(src);
    else this.activeSources.add(src);
    this.activeSources = new Set(this.activeSources);
  }
}
