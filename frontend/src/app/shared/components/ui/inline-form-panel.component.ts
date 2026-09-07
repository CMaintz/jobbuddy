import { Component } from '@angular/core';

@Component({
  selector: 'app-inline-form-panel',
  standalone: true,
  template: `<div class="form-panel"><ng-content></ng-content></div>`
})
export class InlineFormPanelComponent {}
