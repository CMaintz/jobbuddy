import { Pipe, PipeTransform } from '@angular/core';

@Pipe({ name: 'matchLabel', standalone: true })
export class MatchLabelPipe implements PipeTransform {
  transform(label: string): string {
    const map: Record<string, string> = {
      EXCELLENT: 'Excellent match',
      STRONG: 'Strong match',
      MODERATE: 'Moderate match',
      WEAK: 'Weak match'
    };
    return map[label] ?? label;
  }
}
