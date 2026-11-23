import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TranslateModule } from '@ngx-translate/core';
import { ApplicationStatus } from '../../../core/models/application.model';
import { StatusPickerComponent } from './status-picker.component';

describe('StatusPickerComponent', () => {
  let fixture: ComponentFixture<StatusPickerComponent>;

  function render(status: ApplicationStatus, nextStatuses: ApplicationStatus[]): HTMLElement {
    TestBed.configureTestingModule({ imports: [StatusPickerComponent, TranslateModule.forRoot()] });
    fixture = TestBed.createComponent(StatusPickerComponent);
    fixture.componentRef.setInput('status', status);
    fixture.componentRef.setInput('nextStatuses', nextStatuses);
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  }

  it('offers only the current status and the moves the server accepts', () => {
    const el = render('APPLIED', ['RECRUITER_CONTACT', 'REJECTED', 'ARCHIVED']);

    const values = Array.from(el.querySelectorAll('option')).map(o => o.value);
    expect(values).toEqual(['APPLIED', 'RECRUITER_CONTACT', 'REJECTED', 'ARCHIVED']);
  });

  it('reports the status the user picks', () => {
    const el = render('APPLIED', ['RECRUITER_CONTACT', 'REJECTED']);
    const picked: ApplicationStatus[] = [];
    fixture.componentInstance.changed.subscribe(s => picked.push(s));

    const select = el.querySelector('select')!;
    select.value = 'REJECTED';
    select.dispatchEvent(new Event('change'));

    expect(picked).toEqual(['REJECTED']);
  });

  it('shows a closed application without a control', () => {
    expect(render('REJECTED', []).querySelector('select')).toBeNull();
  });
});
