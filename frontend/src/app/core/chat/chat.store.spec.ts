import { TestBed } from '@angular/core/testing';
import { HttpEventType, provideHttpClient, withXhr } from '@angular/common/http';
import { HttpTestingController, TestRequest, provideHttpClientTesting } from '@angular/common/http/testing';
import { ChatStore } from './chat.store';
import { ChatSummary } from '../models/chat.model';

const CHAT: ChatSummary = {
  id: 'c1', title: null, createdAt: '2026-11-21T10:00:00Z', lastActivityAt: '2026-11-21T10:00:00Z',
};

describe('ChatStore', () => {
  let store: ChatStore;
  let backend: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(withXhr()), provideHttpClientTesting()] });
    store = TestBed.inject(ChatStore);
    backend = TestBed.inject(HttpTestingController);
  });

  afterEach(() => backend.verify());

  function openNewChat(): void {
    store.newChat();
    backend.expectOne(r => r.method === 'POST' && r.url === '/api/v1/chats').flush(CHAT);
  }

  function progress(req: TestRequest, partialText: string): void {
    req.event({ type: HttpEventType.DownloadProgress, loaded: partialText.length, partialText });
  }

  it('streams the answer into one growing message after hers', () => {
    openNewChat();
    store.send('Hej');
    const req = backend.expectOne('/api/v1/chats/c1/messages');
    expect(req.request.body).toEqual({ text: 'Hej' });

    progress(req, 'event:delta\ndata:{"text":"Hej "}\n\n');
    expect(store.messages().map(m => [m.role, m.text])).toEqual([['USER', 'Hej'], ['ASSISTANT', 'Hej ']]);
    expect(store.streaming()).toBeTrue();

    progress(req, 'event:delta\ndata:{"text":"Hej "}\n\nevent:delta\ndata:{"text":"med dig"}\n\n');
    req.flush('event:delta\ndata:{"text":"Hej "}\n\nevent:delta\ndata:{"text":"med dig"}\n\nevent:done\ndata:{}\n\n');

    expect(store.messages().map(m => [m.role, m.text])).toEqual([['USER', 'Hej'], ['ASSISTANT', 'Hej med dig']]);
    expect(store.streaming()).toBeFalse();
    backend.expectOne(r => r.method === 'GET' && r.url === '/api/v1/chats').flush([{ ...CHAT, title: 'Hej' }]);
    expect(store.chats()[0].title).toBe('Hej');
  });

  it('keeps a running answer on the chat it was sent from while she opens another', () => {
    openNewChat();
    store.send('Hej');
    const turn = backend.expectOne(r => r.method === 'POST' && r.url === '/api/v1/chats/c1/messages');

    store.open('c2');
    backend.expectOne(r => r.method === 'GET' && r.url === '/api/v1/chats/c2/messages')
      .flush({ messages: [{ role: 'USER', text: 'Anden samtale', at: CHAT.createdAt }], contextLost: false });
    progress(turn, 'event:delta\ndata:{"text":"Svar til den første"}\n\n');

    expect(store.messages().map(m => m.text)).toEqual(['Anden samtale']);
    expect(store.streaming()).toBeFalse();
    store.stop();
    backend.expectNone(r => r.url.endsWith('/stop'));

    store.open('c1');
    expect(store.messages().map(m => m.text)).toEqual(['Hej', 'Svar til den første']);
    store.stop();
    backend.expectOne(r => r.method === 'POST' && r.url === '/api/v1/chats/c1/stop').flush(null);
    turn.flush('event:stopped\ndata:{}\n\n');
    backend.expectOne(r => r.method === 'GET' && r.url === '/api/v1/chats').flush([CHAT]);
    expect(store.streaming()).toBeFalse();
  });

  it('says when an old chat lost its context, until the next answer starts afresh', () => {
    store.open('c1');
    backend.expectOne(r => r.method === 'GET' && r.url === '/api/v1/chats/c1/messages')
      .flush({ messages: [{ role: 'USER', text: 'Gammel besked', at: CHAT.createdAt }], contextLost: true });
    expect(store.messages().map(m => m.text)).toEqual(['Gammel besked']);
    expect(store.contextLost()).toBeTrue();

    store.send('Hej igen');
    backend.expectOne(r => r.method === 'POST' && r.url === '/api/v1/chats/c1/messages')
      .flush('event:delta\ndata:{"text":"Hej"}\n\nevent:done\ndata:{}\n\n');
    backend.expectOne(r => r.method === 'GET' && r.url === '/api/v1/chats').flush([CHAT]);

    expect(store.contextLost()).toBeFalse();
  });

  it('attaches an offered button to the answer it came with', () => {
    openNewChat();
    store.send('Find opslag');
    backend.expectOne(r => r.method === 'POST' && r.url === '/api/v1/chats/c1/messages').flush(
      'event:delta\ndata:{"text":"Her er et."}\n\n'
      + 'event:action\ndata:{"action":{"kind":"OPEN_POSTING","postings":[{"id":"j1","title":"Rådgiver","company":"Arla"}]}}\n\n'
      + 'event:done\ndata:{}\n\n');
    backend.expectOne(r => r.method === 'GET' && r.url === '/api/v1/chats').flush([CHAT]);

    const answer = store.messages()[1];
    expect(answer.text).toBe('Her er et.');
    expect(answer.actions?.map(a => [a.kind, a.postings[0].id])).toEqual([['OPEN_POSTING', 'j1']]);
  });

  it('shows why a turn failed and keeps her message', () => {
    openNewChat();
    store.send('Hej');
    backend.expectOne('/api/v1/chats/c1/messages')
      .flush('event:error\ndata:{"code":"NOT_LOGGED_IN"}\n\n');

    expect(store.error()).toBe('NOT_LOGGED_IN');
    expect(store.messages().map(m => m.role)).toEqual(['USER']);
    expect(store.streaming()).toBeFalse();
  });

  it('shows the code of a turn the server refused', () => {
    openNewChat();
    store.send('Hej');
    backend.expectOne('/api/v1/chats/c1/messages')
      .flush('{"status":409,"code":"CHAT_BUSY"}', { status: 409, statusText: 'Conflict' });

    expect(store.error()).toBe('CHAT_BUSY');
    expect(store.streaming()).toBeFalse();
  });

  it('asks the server to stop the running answer', () => {
    openNewChat();
    store.send('Hej');
    const turn = backend.expectOne('/api/v1/chats/c1/messages');

    store.stop();

    backend.expectOne(r => r.method === 'POST' && r.url === '/api/v1/chats/c1/stop').flush(null);
    turn.flush('event:stopped\ndata:{}\n\n');
    expect(store.streaming()).toBeFalse();
    expect(store.error()).toBeNull();
    backend.expectOne(r => r.method === 'GET' && r.url === '/api/v1/chats').flush([CHAT]);
  });

  it('turns the chat off when this backend has none', () => {
    store.load();
    backend.expectOne('/api/v1/chats').flush(null, { status: 404, statusText: 'Not Found' });

    expect(store.available()).toBeFalse();
  });
});
