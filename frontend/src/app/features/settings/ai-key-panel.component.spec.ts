import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TranslateModule } from '@ngx-translate/core';
import { of } from 'rxjs';
import { AiApiService } from '../../core/api/ai.api';
import { AuthService } from '../../core/auth/auth.service';
import { AiKeyPanelComponent } from './ai-key-panel.component';

describe('AiKeyPanelComponent', () => {
  let api: jasmine.SpyObj<AiApiService>;
  let fixture: ComponentFixture<AiKeyPanelComponent>;

  function render(isAdmin: boolean, hasStoredKey = false): HTMLElement {
    api = jasmine.createSpyObj<AiApiService>('AiApiService', ['getCredentialStatus', 'clearCredential']);
    api.getCredentialStatus.and.returnValue(of({
      configured: hasStoredKey, provider: hasStoredKey ? 'OPENAI' : null, hint: hasStoredKey ? 'sk-...abcd' : null,
      model: null, updatedAt: null, storageAvailable: true,
    }));
    api.clearCredential.and.returnValue(of(undefined));
    TestBed.configureTestingModule({
      imports: [AiKeyPanelComponent, TranslateModule.forRoot()],
      providers: [
        { provide: AiApiService, useValue: api },
        { provide: AuthService, useValue: { isAdmin: () => isAdmin } },
      ],
    });
    fixture = TestBed.createComponent(AiKeyPanelComponent);
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  }

  it('shows a job seeker no provider, API key or model fields', () => {
    const el = render(false);

    expect(el.querySelector('#ai-key-value')).toBeNull();
    expect(el.querySelector('#ai-key-model')).toBeNull();
    expect(el.querySelector('[data-testid="remove-stored-key"]')).toBeNull();
  });

  it('lets a job seeker who already stored a key remove it, without the developer fields', () => {
    const el = render(false, true);
    const remove = el.querySelector('[data-testid="remove-stored-key"]') as HTMLElement;

    expect(el.querySelector('#ai-key-value')).toBeNull();
    expect(remove).not.toBeNull();

    remove.click();
    fixture.detectChanges();

    expect(api.clearCredential).toHaveBeenCalled();
    expect(el.querySelector('[data-testid="remove-stored-key"]')).toBeNull();
  });

  it('gives an admin the key form', () => {
    expect(render(true).querySelector('#ai-key-value')).not.toBeNull();
  });
});
