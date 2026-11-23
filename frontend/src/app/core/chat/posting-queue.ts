import { Injectable, computed, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { ChatActionPosting } from '../models/chat.model';

@Injectable({ providedIn: 'root' })
export class PostingQueue {
  private router = inject(Router);
  private readonly postings = signal<ChatActionPosting[]>([]);
  private readonly index = signal(0);

  readonly current = computed(() => this.postings()[this.index()] ?? null);
  readonly position = computed(() =>
    this.postings().length ? { index: this.index(), total: this.postings().length } : null);

  start(postings: ChatActionPosting[]): void {
    this.postings.set(postings);
    this.goTo(0);
  }

  next(): void {
    this.goTo(this.index() + 1);
  }

  previous(): void {
    this.goTo(this.index() - 1);
  }

  end(): void {
    this.postings.set([]);
    this.index.set(0);
  }

  private goTo(index: number): void {
    const posting = this.postings()[index];
    if (!posting) return;
    this.index.set(index);
    void this.router.navigate(['/jobs', posting.id]);
  }
}
