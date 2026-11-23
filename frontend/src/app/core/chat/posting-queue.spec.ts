import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { PostingQueue } from './posting-queue';
import { ChatActionPosting } from '../models/chat.model';
import type { MockInstance } from 'vitest';

const POSTINGS: ChatActionPosting[] = [
  { id: 'a', title: 'Rådgiver', company: 'Arla' },
  { id: 'b', title: 'Redaktør', company: 'Coop' },
  { id: 'c', title: 'Konsulent', company: 'Netto' },
];

describe('PostingQueue', () => {
  let queue: PostingQueue;
  let navigate: MockInstance;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideRouter([])] });
    queue = TestBed.inject(PostingQueue);
    navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
  });

  it('opens the first posting and steps through the rest in order', () => {
    queue.start(POSTINGS);
    expect(navigate).toHaveBeenCalledWith(['/jobs', 'a']);

    queue.next();
    queue.next();
    expect(navigate).toHaveBeenCalledWith(['/jobs', 'c']);
    expect(queue.position()).toEqual({ index: 2, total: 3 });
  });

  it('stops at either end instead of wrapping or running past it', () => {
    queue.start(POSTINGS);
    queue.previous();
    expect(queue.current()?.id).toBe('a');

    queue.next();
    queue.next();
    queue.next();
    expect(queue.current()?.id).toBe('c');
    expect(navigate).toHaveBeenCalledTimes(3);
  });

  it('is gone once she ends it', () => {
    queue.start(POSTINGS);
    queue.end();

    expect(queue.current()).toBeNull();
    expect(queue.position()).toBeNull();
  });
});
