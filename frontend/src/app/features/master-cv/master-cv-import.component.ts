import { Component, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { JbIconComponent } from '../../shared/components/jb-icon/jb-icon.component';
import { JbButtonComponent } from '../../shared/components/jb-button/jb-button.component';

@Component({
  selector: 'app-master-cv-import',
  standalone: true,
  imports: [CommonModule, RouterLink, JbIconComponent, JbButtonComponent],
  templateUrl: './master-cv-import.component.html'
})
export class MasterCvImportComponent {
  selectedMode = signal<'pdf' | 'paste' | 'linkedin'>('pdf');

  importOptions = [
    { key: 'pdf' as const, icon: 'upload', label: 'Upload PDF', description: 'Upload an existing CV and we\'ll parse it into structured sections.' },
    { key: 'paste' as const, icon: 'copy', label: 'Paste text', description: 'Paste your CV text and we\'ll extract the structure.' },
    { key: 'linkedin' as const, icon: 'link', label: 'LinkedIn', description: 'Connect your LinkedIn profile to auto-import.' },
  ];
}
