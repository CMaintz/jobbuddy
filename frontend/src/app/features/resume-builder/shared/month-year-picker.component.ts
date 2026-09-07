import { Component, Input, Output, EventEmitter, OnInit } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { CommonModule } from '@angular/common';

@Component({
  selector: 'app-month-year-picker',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <div class="flex gap-2">
      <select
        class="flex-1 rounded-md border border-gray-300 px-2 py-1.5 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
        [(ngModel)]="selectedMonth"
        (ngModelChange)="emitChange()"
      >
        <option value="">Month</option>
        @for (m of months; track m.value) {
          <option [value]="m.value">{{ m.label }}</option>
        }
      </select>
      <select
        class="flex-1 rounded-md border border-gray-300 px-2 py-1.5 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
        [(ngModel)]="selectedYear"
        (ngModelChange)="emitChange()"
      >
        <option value="">Year</option>
        @for (y of years; track y) {
          <option [value]="y">{{ y }}</option>
        }
      </select>
    </div>
  `,
})
export class MonthYearPickerComponent implements OnInit {
  @Input() value = ''; // YYYY-MM format
  @Output() valueChange = new EventEmitter<string>();

  selectedMonth = '';
  selectedYear = '';

  months = [
    { value: '01', label: 'January' }, { value: '02', label: 'February' },
    { value: '03', label: 'March' }, { value: '04', label: 'April' },
    { value: '05', label: 'May' }, { value: '06', label: 'June' },
    { value: '07', label: 'July' }, { value: '08', label: 'August' },
    { value: '09', label: 'September' }, { value: '10', label: 'October' },
    { value: '11', label: 'November' }, { value: '12', label: 'December' },
  ];

  years: number[] = [];

  ngOnInit(): void {
    const now = new Date();
    for (let y = now.getFullYear() + 2; y >= 1950; y--) this.years.push(y);
    if (this.value) {
      const [y, m] = this.value.split('-');
      this.selectedYear = y ?? '';
      this.selectedMonth = m ?? '';
    }
  }

  emitChange(): void {
    if (this.selectedYear && this.selectedMonth) {
      this.valueChange.emit(`${this.selectedYear}-${this.selectedMonth}`);
    } else if (this.selectedYear) {
      this.valueChange.emit(`${this.selectedYear}-01`);
    } else {
      this.valueChange.emit('');
    }
  }
}
