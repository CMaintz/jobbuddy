import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { TranslateModule } from '@ngx-translate/core';
import { JbIconComponent } from '../../shared/components/jb-icon/jb-icon.component';
import { JbButtonComponent } from '../../shared/components/jb-button/jb-button.component';
import { StatusChipComponent, stageLabelKey } from '../../shared/components/status-chip/status-chip.component';
import { CompanyMarkComponent } from '../../shared/components/company-mark/company-mark.component';
import { FitBarComponent } from '../../shared/components/fit-bar/fit-bar.component';
import { ApplicationsApiService } from '../../core/api/applications.api';
import { Application, ApplicationStatus } from '../../core/models/application.model';


@Component({
  selector: 'app-application-detail',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, TranslateModule, JbIconComponent, JbButtonComponent, StatusChipComponent, CompanyMarkComponent, FitBarComponent],
  templateUrl: './application-detail.component.html'
})
export class ApplicationDetailComponent implements OnInit {
  private route = inject(ActivatedRoute);
  private api = inject(ApplicationsApiService);

  app: Application | null = null;
  loading = true;

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
  }

  stageLabel(status: ApplicationStatus): string { return stageLabelKey(status); }

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
