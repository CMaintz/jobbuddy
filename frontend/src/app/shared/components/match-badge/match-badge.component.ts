import { Component, Input } from '@angular/core';
import { CommonModule } from '@angular/common';

@Component({
  selector: 'app-match-badge',
  standalone: true,
  imports: [CommonModule],
  template: `
    <span [class]="badgeClass" class="text-xs font-medium px-2 py-0.5 rounded-full">{{ label }}</span>
  `
})
export class MatchBadgeComponent {
  @Input() label: 'EXCELLENT' | 'STRONG' | 'MODERATE' | 'WEAK' = 'WEAK';

  get badgeClass(): string {
    const map: Record<string, string> = {
      EXCELLENT: 'bg-green-100 text-green-800',
      STRONG: 'bg-blue-100 text-blue-800',
      MODERATE: 'bg-yellow-100 text-yellow-800',
      WEAK: 'bg-gray-100 text-gray-600'
    };
    return map[this.label] ?? 'bg-gray-100 text-gray-600';
  }
}
