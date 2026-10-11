import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TranslateModule } from '@ngx-translate/core';
import { CvImportPanelComponent } from './cv-import-panel.component';

describe('CvImportPanelComponent parse errors', () => {
  const CV_TEXT = 'Jane Doe, senior engineer. '.repeat(5);
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

  function failWith(status: number, message: string): void {
    backend.expectOne('/api/v1/ai/parse-cv').flush({ status, message }, { status, statusText: 'Error' });
  }

  it('shows the server message when the CV could not be parsed', () => {
    failWith(422, 'The CV could not be parsed right now.');

    expect(emitted).toEqual(['The CV could not be parsed right now.']);
    expect(panel.parsing()).toBeFalse();
  });

  it('falls back to the translated message for any other failure', () => {
    failWith(500, 'Internal server error');

    expect(emitted).toEqual(['cvImport.error.parseFailed']);
  });
});
