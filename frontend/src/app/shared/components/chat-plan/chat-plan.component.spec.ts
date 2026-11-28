import { TestBed } from '@angular/core/testing';
import { provideHttpClient, withXhr } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TranslateModule } from '@ngx-translate/core';
import { Router, provideRouter } from '@angular/router';
import { ChatPlanComponent } from './chat-plan.component';
import { ChatStore } from '../../../core/chat/chat.store';
import { ChatPlan, PlanItem } from '../../../core/models/chat.model';

const ITEM: PlanItem = {
  id: 'i1', kind: 'IGNORE_POSTING', state: 'PENDING', failure: null, resultApplicationId: null,
  decidedAt: null, finishedAt: null,
  details: { postingId: 'j1', title: 'Rådgiver', company: 'Arla' },
};

describe('ChatPlanComponent', () => {
  let backend: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [ChatPlanComponent, TranslateModule.forRoot()],
      providers: [provideRouter([]), provideHttpClient(withXhr()), provideHttpClientTesting()],
    });
    backend = TestBed.inject(HttpTestingController);
  });

  function render(plan: ChatPlan): HTMLElement {
    const store = TestBed.inject(ChatStore);
    store.open('c1');
    backend.expectOne('/api/v1/chats/c1/messages').flush({
      messages: [{ role: 'ASSISTANT', text: '', at: '2026-11-22T10:00:00Z', planIds: [plan.id] }],
      contextLost: false,
      plans: [plan],
    });
    const fixture = TestBed.createComponent(ChatPlanComponent);
    fixture.componentRef.setInput('plan', plan);
    fixture.detectChanges();
    return fixture.nativeElement;
  }

  it('sends her approval of exactly the item she clicked', () => {
    const card = render({ id: 'p1', createdAt: '2026-11-22T10:00:00Z', items: [ITEM] });

    card.querySelector<HTMLButtonElement>('[data-plan-approve]')!.click();

    const sent = backend.expectOne(r => r.url === '/api/v1/chats/plans/p1/items/i1/approve');
    expect(sent.request.method).toBe('POST');
  });

  it('approves every waiting posting of a batch with one click', () => {
    const card = render({ id: 'p1', createdAt: '2026-11-22T10:00:00Z', items: [
      { ...ITEM, kind: 'GENERATE_DOCUMENTS' }, { ...ITEM, id: 'i2', kind: 'GENERATE_DOCUMENTS' }] });

    card.querySelector<HTMLButtonElement>('[data-plan-approve-all]')!.click();

    const sent = backend.expectOne(r => r.url === '/api/v1/chats/plans/p1/approve');
    expect(sent.request.method).toBe('POST');
  });

  it('opens written documents in the application review, not anywhere else', () => {
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    const card = render({ id: 'p1', createdAt: '2026-11-22T10:00:00Z', items: [
      { ...ITEM, kind: 'GENERATE_DOCUMENTS', state: 'DONE', resultApplicationId: 'a1' }] });

    card.querySelector<HTMLButtonElement>('[data-plan-open-documents]')!.click();

    expect(navigate).toHaveBeenCalledWith(['/applications', 'a1', 'pack'], { queryParams: { jobId: 'j1' } });
  });

  it('offers no buttons on a proposal she has already decided', () => {
    const card = render({ id: 'p1', createdAt: '2026-11-22T10:00:00Z', items: [{ ...ITEM, state: 'REJECTED' }] });

    expect(card.querySelector('[data-plan-approve]')).toBeNull();
    expect(card.querySelector('[data-plan-reject]')).toBeNull();
  });
});
