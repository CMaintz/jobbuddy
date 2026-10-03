import { Component, Input } from '@angular/core';
import { TranslateModule } from '@ngx-translate/core';

@Component({
  selector: 'app-empty-state',
  imports: [TranslateModule],
  templateUrl: './empty-state.component.html'
})
export class EmptyStateComponent {
  @Input({ required: true }) message = '';
}
