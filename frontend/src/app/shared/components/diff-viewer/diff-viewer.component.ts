import { Component, Input, computed, signal } from '@angular/core';
import { CommonModule } from '@angular/common';

interface DiffSegment {
  text: string;
  type: 'keep' | 'add' | 'del';
}

@Component({
  selector: 'jb-diff-viewer',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './diff-viewer.component.html',
})
export class DiffViewerComponent {
  @Input() before = '';
  @Input() after = '';

  mode = signal<'diff' | 'revised' | 'original'>('diff');

  segments = computed(() => this.computeDiff(this.before, this.after));

  addCount = computed(() => this.segments().filter(s => s.type === 'add').length);
  delCount = computed(() => this.segments().filter(s => s.type === 'del').length);

  private computeDiff(before: string, after: string): DiffSegment[] {
    if (!before || !after) return [{ text: after || before || '', type: 'keep' }];

    const oldWords = this.tokenize(before);
    const newWords = this.tokenize(after);

    // Simple word-level LCS diff
    const lcs = this.lcs(oldWords, newWords);
    const segments: DiffSegment[] = [];

    let oi = 0, ni = 0, li = 0;
    while (oi < oldWords.length || ni < newWords.length) {
      if (li < lcs.length && oi < oldWords.length && ni < newWords.length
          && oldWords[oi] === lcs[li] && newWords[ni] === lcs[li]) {
        // Common word
        segments.push({ text: oldWords[oi], type: 'keep' });
        oi++; ni++; li++;
      } else {
        // Deleted words from old
        while (oi < oldWords.length && (li >= lcs.length || oldWords[oi] !== lcs[li])) {
          segments.push({ text: oldWords[oi], type: 'del' });
          oi++;
        }
        // Added words in new
        while (ni < newWords.length && (li >= lcs.length || newWords[ni] !== lcs[li])) {
          segments.push({ text: newWords[ni], type: 'add' });
          ni++;
        }
      }
    }

    return this.mergeSegments(segments);
  }

  private tokenize(text: string): string[] {
    // Split by whitespace but preserve the whitespace as part of the following token
    return text.split(/(\s+)/).filter(Boolean);
  }

  private lcs(a: string[], b: string[]): string[] {
    const m = a.length, n = b.length;
    // For very long texts, use a simpler approach to avoid memory issues
    if (m * n > 500000) return this.simpleLcs(a, b);

    const dp: number[][] = Array.from({ length: m + 1 }, () => Array(n + 1).fill(0));
    for (let i = 1; i <= m; i++) {
      for (let j = 1; j <= n; j++) {
        dp[i][j] = a[i - 1] === b[j - 1] ? dp[i - 1][j - 1] + 1 : Math.max(dp[i - 1][j], dp[i][j - 1]);
      }
    }

    const result: string[] = [];
    let i = m, j = n;
    while (i > 0 && j > 0) {
      if (a[i - 1] === b[j - 1]) {
        result.unshift(a[i - 1]);
        i--; j--;
      } else if (dp[i - 1][j] > dp[i][j - 1]) {
        i--;
      } else {
        j--;
      }
    }
    return result;
  }

  private simpleLcs(a: string[], b: string[]): string[] {
    // For long texts, use a greedy approach
    const result: string[] = [];
    let j = 0;
    for (let i = 0; i < a.length && j < b.length; i++) {
      const idx = b.indexOf(a[i], j);
      if (idx !== -1) {
        result.push(a[i]);
        j = idx + 1;
      }
    }
    return result;
  }

  private mergeSegments(segments: DiffSegment[]): DiffSegment[] {
    if (segments.length === 0) return segments;
    const merged: DiffSegment[] = [segments[0]];
    for (let i = 1; i < segments.length; i++) {
      const last = merged[merged.length - 1];
      if (last.type === segments[i].type) {
        last.text += segments[i].text;
      } else {
        merged.push({ ...segments[i] });
      }
    }
    return merged;
  }
}
