import { Component, ChangeDetectionStrategy } from '@angular/core';
import { RouterLink } from '@angular/router';
import { TranslateModule } from '@ngx-translate/core';

/**
 * Public privacy policy page (GDPR art. 13/14 information duties).
 * The wording is a working draft — have it reviewed by counsel before launch.
 */
@Component({
  selector: 'app-privacy-policy',
  imports: [RouterLink, TranslateModule],
  changeDetection: ChangeDetectionStrategy.Eager,
  templateUrl: './privacy-policy.component.html',
})
export class PrivacyPolicyComponent {
  readonly lastUpdated = '21 July 2026';
}
