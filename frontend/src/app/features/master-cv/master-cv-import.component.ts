import { Component, signal, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router, RouterLink } from '@angular/router';
import { TranslateModule } from '@ngx-translate/core';
import { JbIconComponent } from '../../shared/components/jb-icon/jb-icon.component';
import { JbToastComponent } from '../../shared/components/jb-toast/jb-toast.component';
import { CvImportPanelComponent } from '../../shared/components/cv-import-panel/cv-import-panel.component';

@Component({
  selector: 'app-master-cv-import',
  standalone: true,
  imports: [CommonModule, RouterLink, TranslateModule, JbIconComponent, JbToastComponent, CvImportPanelComponent],
  templateUrl: './master-cv-import.component.html'
})
export class MasterCvImportComponent {
  private router = inject(Router);

  toast = signal('');

  onApplied(): void {
    this.router.navigate(['/cv']);
  }
}
