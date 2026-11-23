import { Component, OnInit, inject, signal, ChangeDetectionStrategy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { TranslateModule } from '@ngx-translate/core';
import { JbIconComponent } from '../../shared/components/jb-icon/jb-icon.component';
import { JbButtonComponent } from '../../shared/components/jb-button/jb-button.component';
import { stageLabelKey } from '../../shared/components/status-chip/status-chip.component';
import { StatusPickerComponent } from '../../shared/components/status-picker/status-picker.component';
import { NotifyService } from '../../shared/services/notify.service';
import { ApplicationDatesComponent } from './application-dates.component';
import { CompanyMarkComponent } from '../../shared/components/company-mark/company-mark.component';
import { FitBarComponent } from '../../shared/components/fit-bar/fit-bar.component';
import { ApplicationsApiService, ApplicationTimelineEntry } from '../../core/api/applications.api';
import { Application, ApplicationStatus } from '../../core/models/application.model';


@Component({
  selector: 'app-application-detail',
  imports: [
    CommonModule, FormsModule, RouterLink, TranslateModule, JbIconComponent, JbButtonComponent,
    StatusPickerComponent, ApplicationDatesComponent, CompanyMarkComponent, FitBarComponent,
  ],
  changeDetection: ChangeDetectionStrategy.Eager,
  templateUrl: './application-detail.component.html'
})
export class ApplicationDetailComponent implements OnInit {
  private route = inject(ActivatedRoute);
  private api = inject(ApplicationsApiService);
  private notify = inject(NotifyService);

  app: Application | null = null;
  loading = true;

  /** Every step this application has taken, oldest first. */
  timeline = signal<ApplicationTimelineEntry[]>([]);
  timelineLoading = signal(true);

  outcomeFeedback = '';
  outcomeLessons = '';
  savingOutcome = signal(false);
  outcomeSaved = signal(false);

  ngOnInit(): void {
    const id = this.route.snapshot.paramMap.get('id');
    if (!id) { this.loading = false; return; }
    this.api.getById(id).subscribe({
      next: (app) => {
        this.app = app;
        this.outcomeFeedback = app.outcomeFeedback ?? '';
        this.outcomeLessons = app.outcomeLessons ?? '';
        this.loading = false;
      },
      error: () => this.loading = false
    });
    this.api.timeline(id).subscribe({
      next: (events) => { this.timeline.set(events); this.timelineLoading.set(false); },
      error: () => this.timelineLoading.set(false)
    });
  }

  stageLabel(status: ApplicationStatus): string { return stageLabelKey(status); }

  /** A step is named by the stage it reached, using the app's one set of stage labels. */
  stepLabel(entry: ApplicationTimelineEntry): string {
    return stageLabelKey(entry.toStatus);
  }

  /**
   * The user's own moves stay quiet; the employer's replies are the news, so they
   * carry the colour — green for an offer, red for a rejection.
   */
  stepTone(entry: ApplicationTimelineEntry): string {
    if (!entry.employerResponse) return 'var(--jb-text-faint)';
    if (entry.toStatus === 'REJECTED') return 'var(--jb-danger)';
    if (entry.toStatus === 'OFFER') return 'var(--jb-success)';
    return 'var(--jb-accent-2)';
  }

  statusPending = signal(false);

  changeStatus(status: ApplicationStatus): void {
    const app = this.app;
    if (!app) return;
    this.statusPending.set(true);
    this.api.updateStatus(app.id, status).subscribe({
      next: updated => {
        this.app = updated;
        this.statusPending.set(false);
        // The move is a new step on the timeline, so reload it rather than show a stale one.
        this.api.timeline(app.id).subscribe(events => this.timeline.set(events));
      },
      error: () => {
        this.statusPending.set(false);
        this.notify.error('notify.actionFailed');
      },
    });
  }

  /** Outcome capture makes sense once the application has actually gone out. */
  showOutcome(): boolean {
    return !!this.app && !['SAVED', 'PREPARING'].includes(this.app.status);
  }

  saveOutcome(): void {
    if (!this.app || this.savingOutcome()) return;
    this.savingOutcome.set(true);
    this.outcomeSaved.set(false);
    this.api.updateOutcome(this.app.id, {
      outcomeFeedback: this.outcomeFeedback.trim() || undefined,
      outcomeLessons: this.outcomeLessons.trim() || undefined,
    }).subscribe({
      next: (updated) => {
        this.app = updated;
        this.savingOutcome.set(false);
        this.outcomeSaved.set(true);
      },
      error: () => this.savingOutcome.set(false)
    });
  }
}
