import { Component, inject, ChangeDetectionStrategy } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { TranslateModule } from '@ngx-translate/core';
import { JbIconComponent } from '../../shared/components/jb-icon/jb-icon.component';
import { CvImportPanelComponent } from '../../shared/components/cv-import-panel/cv-import-panel.component';

import { NotifyService } from '../../shared/services/notify.service';
@Component({
  selector: 'app-master-cv-import',
  imports: [RouterLink, TranslateModule, JbIconComponent, CvImportPanelComponent],
  changeDetection: ChangeDetectionStrategy.Eager,
  templateUrl: './master-cv-import.component.html'
})
export class MasterCvImportComponent {
  private router = inject(Router);
  protected notify = inject(NotifyService);


  onApplied(): void {
    this.router.navigate(['/cv']);
  }
}
