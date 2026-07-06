import { Component, Input } from '@angular/core';
import { CommonModule } from '@angular/common';
import { AtsReport } from '../../../../core/models/structured-document.model';

@Component({
  selector: 'app-ats-report-panel',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './ats-report-panel.component.html'
})
export class AtsReportPanelComponent {
  @Input({ required: true }) report!: AtsReport | undefined | null;

  get scoreColor(): string {
    if (!this.report) return 'text-gray-400';
    if (this.report.score >= 80) return 'text-green-600';
    if (this.report.score >= 65) return 'text-yellow-600';
    return 'text-red-500';
  }

  get coverageBarColor(): string {
    if (!this.report) return 'bg-gray-300';
    if (this.report.keywordCoverage >= 70) return 'bg-green-500';
    if (this.report.keywordCoverage >= 40) return 'bg-yellow-400';
    return 'bg-red-400';
  }

  checkIcon(status: string): string {
    switch (status) {
      case 'PASS': return '✓';
      case 'WARN': return '⚠';
      case 'FAIL': return '✗';
      default: return 'ℹ';
    }
  }

  checkIconClass(status: string): string {
    switch (status) {
      case 'PASS': return 'text-green-500';
      case 'WARN': return 'text-yellow-500';
      case 'FAIL': return 'text-red-500';
      default: return 'text-blue-500';
    }
  }
}
