import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { forkJoin } from 'rxjs';
import { JbIconComponent } from '../../shared/components/jb-icon/jb-icon.component';
import { JbButtonComponent } from '../../shared/components/jb-button/jb-button.component';
import { JbPillComponent } from '../../shared/components/jb-pill/jb-pill.component';
import { JbToastComponent } from '../../shared/components/jb-toast/jb-toast.component';
import { CompanyMarkComponent } from '../../shared/components/company-mark/company-mark.component';
import { RemindersApiService, FollowUpReminder } from '../../core/api/reminders.api';
import { ApplicationsApiService } from '../../core/api/applications.api';
import { Application } from '../../core/models/application.model';

type Group = 'overdue' | 'today' | 'tomorrow' | 'week' | 'later';

interface TaskRow {
  reminder: FollowUpReminder;
  company: string;
  role: string;
  group: Group;
  dueLabel: string;
}

@Component({
  selector: 'app-tasks',
  standalone: true,
  imports: [CommonModule, FormsModule, JbIconComponent, JbButtonComponent, JbPillComponent, JbToastComponent, CompanyMarkComponent],
  templateUrl: './tasks.component.html'
})
export class TasksComponent implements OnInit {
  private remindersApi = inject(RemindersApiService);
  private appsApi = inject(ApplicationsApiService);
  private router = inject(Router);

  loading = signal(true);
  toast = signal('');
  rows = signal<TaskRow[]>([]);
  doneToday = signal(0);
  applications: Application[] = [];

  // Add-reminder form
  showForm = signal(false);
  newAppId = '';
  newNote = '';
  newDue = '';

  groups: { key: Group; label: string }[] = [
    { key: 'overdue', label: 'Overdue' },
    { key: 'today', label: 'Today' },
    { key: 'tomorrow', label: 'Tomorrow' },
    { key: 'week', label: 'This week' },
    { key: 'later', label: 'Later' },
  ];

  get overdueCount(): number { return this.rows().filter(r => r.group === 'overdue').length; }
  get todayCount(): number { return this.rows().filter(r => r.group === 'today').length; }
  get weekCount(): number { return this.rows().filter(r => r.group === 'week' || r.group === 'tomorrow').length; }

  ngOnInit(): void {
    this.load();
  }

  private load(): void {
    forkJoin({
      reminders: this.remindersApi.getOpenReminders(),
      apps: this.appsApi.getAll(),
    }).subscribe({
      next: ({ reminders, apps }) => {
        this.applications = apps;
        const appMap = new Map(apps.map(a => [a.id, a]));
        this.rows.set(reminders.map(r => this.toRow(r, appMap.get(r.applicationId))));
        this.loading.set(false);
      },
      error: () => {
        this.loading.set(false);
        this.toast.set('Could not load reminders');
      }
    });
  }

  private toRow(reminder: FollowUpReminder, app?: Application): TaskRow {
    const due = new Date(reminder.dueAt);
    const now = new Date();
    const startOfDay = (d: Date) => new Date(d.getFullYear(), d.getMonth(), d.getDate());
    const dayDiff = Math.floor((startOfDay(due).getTime() - startOfDay(now).getTime()) / 86400000);

    let group: Group;
    if (due.getTime() < now.getTime() && dayDiff < 0) group = 'overdue';
    else if (dayDiff <= 0) group = 'today';
    else if (dayDiff === 1) group = 'tomorrow';
    else if (dayDiff <= 7) group = 'week';
    else group = 'later';

    const dueLabel = dayDiff < 0 ? `${-dayDiff}d overdue`
      : dayDiff === 0 ? 'today'
      : dayDiff === 1 ? 'tomorrow'
      : due.toLocaleDateString(undefined, { weekday: 'short', day: 'numeric', month: 'short' });

    return {
      reminder,
      company: app?.jobCompanyName ?? 'Unknown',
      role: app?.jobTitle ?? '',
      group,
      dueLabel,
    };
  }

  tasksForGroup(group: Group): TaskRow[] {
    return this.rows().filter(r => r.group === group);
  }

  complete(row: TaskRow): void {
    this.remindersApi.complete(row.reminder.id).subscribe({
      next: () => {
        this.rows.update(rows => rows.filter(r => r.reminder.id !== row.reminder.id));
        this.doneToday.update(n => n + 1);
      },
      error: () => this.toast.set('Could not complete the reminder')
    });
  }

  remove(row: TaskRow): void {
    this.remindersApi.delete(row.reminder.id).subscribe({
      next: () => this.rows.update(rows => rows.filter(r => r.reminder.id !== row.reminder.id)),
      error: () => this.toast.set('Could not delete the reminder')
    });
  }

  openApplication(row: TaskRow): void {
    this.router.navigate(['/applications', row.reminder.applicationId]);
  }

  createReminder(): void {
    if (!this.newAppId || !this.newNote.trim() || !this.newDue) return;
    this.remindersApi.create(this.newAppId, this.newNote.trim(), new Date(this.newDue).toISOString()).subscribe({
      next: () => {
        this.showForm.set(false);
        this.newAppId = '';
        this.newNote = '';
        this.newDue = '';
        this.load();
      },
      error: () => this.toast.set('Could not create the reminder')
    });
  }
}
