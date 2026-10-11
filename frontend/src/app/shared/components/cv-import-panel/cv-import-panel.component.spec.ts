import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TranslateModule } from '@ngx-translate/core';
import { CvImportPanelComponent } from './cv-import-panel.component';

describe('CvImportPanelComponent parse errors', () => {
  const CV_TEXT = 'Jane Doe, senior engineer. '.repeat(5);
  const SERVER_SENTENCE = 'An English sentence from the server';
  let panel: CvImportPanelComponent;
  let backend: HttpTestingController;
  let emitted: string[];

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [CvImportPanelComponent, TranslateModule.forRoot()],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    panel = TestBed.createComponent(CvImportPanelComponent).componentInstance;
    backend = TestBed.inject(HttpTestingController);
    emitted = [];
    panel.failed.subscribe(message => emitted.push(message));
    panel.pastedText = CV_TEXT;
    panel.parsePastedText();
  });

  function failWith(status: number, code?: string): void {
    backend.expectOne('/api/v1/ai/parse-cv')
      .flush({ status, code, message: SERVER_SENTENCE }, { status, statusText: 'Error' });
  }

  it('translates the reason code of a 422', () => {
    failWith(422, 'NO_TEXT');

    expect(emitted).toEqual(['cvImport.error.reason.NO_TEXT']);
    expect(panel.parsing()).toBeFalse();
  });

  it('translates the reason code of a 503', () => {
    failWith(503, 'AI_UNAVAILABLE');

    expect(emitted).toEqual(['cvImport.error.reason.AI_UNAVAILABLE']);
  });

  it('falls back to the generic message for an unknown code', () => {
    failWith(422, 'SOMETHING_NEW');

    expect(emitted).toEqual(['cvImport.error.parseFailed']);
  });

  it('falls back to the generic message when there is no code', () => {
    failWith(500);

    expect(emitted).toEqual(['cvImport.error.parseFailed']);
  });
});
