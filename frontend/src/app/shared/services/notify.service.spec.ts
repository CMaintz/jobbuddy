import { TestBed } from '@angular/core/testing';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { NotifyService } from './notify.service';

describe('NotifyService', () => {
  let notify: NotifyService;

  beforeEach(() => {
    TestBed.configureTestingModule({ imports: [TranslateModule.forRoot()] });
    const translate = TestBed.inject(TranslateService);
    translate.setTranslation('en', { notify: { loadFailed: 'Could not load', saved: 'Saved {{name}}' } });
    translate.use('en');
    notify = TestBed.inject(NotifyService);
  });

  it('shows a translated error', () => {
    notify.error('notify.loadFailed');

    expect(notify.notice()).toEqual({ message: 'Could not load', tone: 'error' });
  });

  it('interpolates params', () => {
    notify.success('notify.saved', { name: 'CV' });

    expect(notify.notice()).toEqual({ message: 'Saved CV', tone: 'success' });
  });

  it('builds an error callback for subscribe', () => {
    notify.onError('notify.loadFailed')();

    expect(notify.notice()?.tone).toBe('error');
  });

  it('shows a server-provided message as-is', () => {
    notify.errorText('Quota exceeded');

    expect(notify.notice()).toEqual({ message: 'Quota exceeded', tone: 'error' });
  });

  it('clears on dismiss', () => {
    notify.error('notify.loadFailed');
    notify.dismiss();

    expect(notify.notice()).toBeNull();
  });
});
