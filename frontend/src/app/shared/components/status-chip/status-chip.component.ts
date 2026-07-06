import { Component, Input } from '@angular/core';
import { CommonModule } from '@angular/common';

@Component({
  selector: 'app-status-chip',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './status-chip.component.html'
})
export class StatusChipComponent {
  @Input() status = '';

  get chipClass(): string {
    const map: Record<string, string> = {
      SAVED: 'bg-gray-100 text-gray-600',
      PREPARING: 'bg-yellow-100 text-yellow-800',
      APPLIED: 'bg-blue-100 text-blue-800',
      RECRUITER_CONTACT: 'bg-indigo-100 text-indigo-800',
      INTERVIEW: 'bg-purple-100 text-purple-800',
      TECHNICAL_TEST: 'bg-orange-100 text-orange-800',
      FINAL_ROUND: 'bg-pink-100 text-pink-800',
      OFFER: 'bg-green-100 text-green-800',
      REJECTED: 'bg-red-100 text-red-700',
      ARCHIVED: 'bg-gray-100 text-gray-400'
    };
    return map[this.status] ?? 'bg-gray-100 text-gray-600';
  }
}
