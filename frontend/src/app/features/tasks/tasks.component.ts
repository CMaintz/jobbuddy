import { Component, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { JbIconComponent } from '../../shared/components/jb-icon/jb-icon.component';
import { JbButtonComponent } from '../../shared/components/jb-button/jb-button.component';
import { JbPillComponent } from '../../shared/components/jb-pill/jb-pill.component';
import { CompanyMarkComponent } from '../../shared/components/company-mark/company-mark.component';

interface Task {
  id: string;
  company: string;
  role: string;
  what: string;
  detail: string;
  due: string;
  action: string;
  kind: string;
  tone: 'accent' | 'info' | 'danger' | 'neutral' | 'violet' | 'success';
  group: 'overdue' | 'today' | 'tomorrow' | 'week';
}

@Component({
  selector: 'app-tasks',
  standalone: true,
  imports: [CommonModule, JbIconComponent, JbButtonComponent, JbPillComponent, CompanyMarkComponent],
  templateUrl: './tasks.component.html'
})
export class TasksComponent {
  done = new Set<string>();
  activeFilter = signal('All');
  filters = ['All', 'Follow-ups', 'Deadlines', 'Interviews'];
  groups = [
    { key: 'overdue' as const, label: 'Overdue' },
    { key: 'today' as const, label: 'Today' },
    { key: 'tomorrow' as const, label: 'Tomorrow' },
    { key: 'week' as const, label: 'This week' },
  ];

  // Mock task data
  tasks: Task[] = [
    { id: '1', company: 'Linear', role: 'Product Eng', what: 'Follow up — no reply', detail: 'Applied 5d ago', due: 'overdue', action: 'Reply', kind: 'Follow-up', tone: 'info', group: 'overdue' },
    { id: '2', company: 'Stripe', role: 'Frontend Eng', what: 'Interview prep — 2nd round', detail: '09:30 tomorrow', due: 'today', action: 'Prep', kind: 'Interview', tone: 'accent', group: 'today' },
    { id: '3', company: 'Vercel', role: 'Senior FE', what: 'Reply to recruiter', detail: 'DM 2 days ago', due: 'today', action: 'Reply', kind: 'Follow-up', tone: 'danger', group: 'today' },
    { id: '4', company: 'Anthropic', role: 'Design Eng', what: 'Polish CV for role', detail: 'AI · closes Friday', due: 'tomorrow', action: 'Draft', kind: 'Deadline', tone: 'violet', group: 'tomorrow' },
    { id: '5', company: 'Notion', role: 'Web Eng', what: 'Apply before deadline', detail: 'Closes Thursday', due: 'week', action: 'Apply', kind: 'Deadline', tone: 'neutral', group: 'week' },
    { id: '6', company: 'Figma', role: 'Product Eng', what: 'Send follow-up', detail: 'Interview was 3d ago', due: 'week', action: 'Draft', kind: 'Follow-up', tone: 'info', group: 'week' },
  ];

  get overdueCount(): number { return this.tasks.filter(t => t.group === 'overdue').length; }
  get todayCount(): number { return this.tasks.filter(t => t.group === 'today').length; }
  get weekCount(): number { return this.tasks.filter(t => t.group === 'week').length; }
  get doneCount(): number { return this.done.size; }

  tasksForGroup(group: string): Task[] {
    return this.tasks.filter(t => t.group === group && !this.done.has(t.id));
  }

  toggleDone(id: string): void {
    if (this.done.has(id)) this.done.delete(id);
    else this.done.add(id);
    this.done = new Set(this.done);
  }
}
