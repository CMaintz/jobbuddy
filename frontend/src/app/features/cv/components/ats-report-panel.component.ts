import { Component, Input } from '@angular/core';
import { CommonModule } from '@angular/common';
import { AtsReport } from '../../../core/models/structured-document.model';

@Component({
  selector: 'app-ats-report-panel',
  standalone: true,
  imports: [CommonModule],
  template: `
    @if (report) {
      <div class="bg-white border border-gray-200 rounded-lg p-4 space-y-4">
        <div class="flex items-center justify-between">
          <h3 class="font-semibold text-gray-900 text-sm">ATS Report</h3>
          <div class="flex items-center gap-2">
            <span class="text-2xl font-bold" [ngClass]="scoreColor">{{ report.score }}</span>
            <span class="text-xs text-gray-500">/ 96</span>
          </div>
        </div>

        <div>
          <div class="flex justify-between text-xs text-gray-600 mb-1">
            <span>Keyword coverage</span>
            <span>{{ report.keywordCoverage }}%</span>
          </div>
          <div class="w-full bg-gray-100 rounded-full h-2">
            <div class="h-2 rounded-full transition-all"
                 [ngClass]="coverageBarColor"
                 [style.width.%]="report.keywordCoverage"></div>
          </div>
        </div>

        @if ((report.matchedKeywords ?? []).length > 0) {
          <div>
            <p class="text-xs font-medium text-gray-600 mb-1.5">Matched keywords</p>
            <div class="flex flex-wrap gap-1">
              @for (kw of report.matchedKeywords; track kw) {
                <span class="bg-green-50 text-green-700 border border-green-200 text-xs px-2 py-0.5 rounded">{{ kw }}</span>
              }
            </div>
          </div>
        }

        @if ((report.missingKeywords ?? []).length > 0) {
          <div>
            <p class="text-xs font-medium text-gray-600 mb-1.5">Missing keywords</p>
            <div class="flex flex-wrap gap-1">
              @for (kw of report.missingKeywords; track kw) {
                <span class="bg-red-50 text-red-600 border border-red-200 text-xs px-2 py-0.5 rounded">{{ kw }}</span>
              }
            </div>
          </div>
        }

        @if ((report.checks ?? []).length > 0) {
          <div class="space-y-1.5 pt-1 border-t border-gray-100">
            @for (check of report.checks; track check.code) {
              <div class="flex items-start gap-2 text-xs">
                <span [ngClass]="checkIconClass(check.status)" class="mt-0.5 shrink-0 text-base leading-none">
                  {{ checkIcon(check.status) }}
                </span>
                <div>
                  <span class="font-medium text-gray-700">{{ check.label }}</span>
                  @if (check.detail) {
                    <span class="text-gray-500"> — {{ check.detail }}</span>
                  }
                </div>
              </div>
            }
          </div>
        }
      </div>
    }
  `
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
