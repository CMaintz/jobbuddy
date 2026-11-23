import { Component, ChangeDetectionStrategy, OnInit, inject, input, output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { TranslateModule } from '@ngx-translate/core';
import { ApplicationsApiService } from '../../core/api/applications.api';
import { Application } from '../../core/models/application.model';
import { JbButtonComponent } from '../../shared/components/jb-button/jb-button.component';
import { NotifyService } from '../../shared/services/notify.service';

/** A datetime-local input takes `YYYY-MM-DDTHH:mm`; the server sends seconds too. */
const DATETIME_LOCAL_LENGTH = 16;

/** The user's own deadline and interview time, which the daily reminder email uses. */
@Component({
  selector: 'app-application-dates',
  imports: [FormsModule, TranslateModule, JbButtonComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './application-dates.component.html',
})
export class ApplicationDatesComponent implements OnInit {
  private api = inject(ApplicationsApiService);
  private notify = inject(NotifyService);

  readonly application = input.required<Application>();
  readonly saved = output<Application>();

  deadline = '';
  interviewAt = '';
  saving = signal(false);

  ngOnInit(): void {
    const app = this.application();
    this.deadline = app.deadline ?? '';
    this.interviewAt = (app.interviewAt ?? '').slice(0, DATETIME_LOCAL_LENGTH);
  }

  save(): void {
    if (this.saving()) return;
    this.saving.set(true);
    // An emptied input is '', which the server cannot parse as a date; null is what clears it.
    this.api.updateDates(this.application().id, {
      deadline: this.deadline || null,
      interviewAt: this.interviewAt || null,
    }).subscribe({
      next: updated => {
        this.saving.set(false);
        this.notify.success('applicationDetail.dates.saved');
        this.saved.emit(updated);
      },
      error: () => {
        this.saving.set(false);
        this.notify.error('applicationDetail.dates.saveFailed');
      },
    });
  }
}
