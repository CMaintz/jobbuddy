import { Component, Input, Output, EventEmitter, OnInit } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { CommonModule } from '@angular/common';
import { TranslateModule } from '@ngx-translate/core';

@Component({
  selector: 'app-month-year-picker',
  standalone: true,
  imports: [CommonModule, FormsModule, TranslateModule],
  templateUrl: './month-year-picker.component.html',
})
export class MonthYearPickerComponent implements OnInit {
  @Input() value = ''; // YYYY-MM format
  @Output() valueChange = new EventEmitter<string>();

  selectedMonth = '';
  selectedYear = '';

  months = [
    { value: '01', label: 'resumeBuilder.monthPicker.months.jan' }, { value: '02', label: 'resumeBuilder.monthPicker.months.feb' },
    { value: '03', label: 'resumeBuilder.monthPicker.months.mar' }, { value: '04', label: 'resumeBuilder.monthPicker.months.apr' },
    { value: '05', label: 'resumeBuilder.monthPicker.months.may' }, { value: '06', label: 'resumeBuilder.monthPicker.months.jun' },
    { value: '07', label: 'resumeBuilder.monthPicker.months.jul' }, { value: '08', label: 'resumeBuilder.monthPicker.months.aug' },
    { value: '09', label: 'resumeBuilder.monthPicker.months.sep' }, { value: '10', label: 'resumeBuilder.monthPicker.months.oct' },
    { value: '11', label: 'resumeBuilder.monthPicker.months.nov' }, { value: '12', label: 'resumeBuilder.monthPicker.months.dec' },
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
