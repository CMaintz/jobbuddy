import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { TranslateModule } from '@ngx-translate/core';

/**
 * Public privacy policy page (GDPR art. 13/14 information duties).
 * The wording is a working draft — have it reviewed by counsel before launch.
 */
@Component({
  selector: 'app-privacy-policy',
  standalone: true,
  imports: [CommonModule, RouterLink, TranslateModule],
  templateUrl: './privacy-policy.component.html',
})
export class PrivacyPolicyComponent {
  readonly lastUpdated = '21 July 2026';
}
