import { CommonModule } from '@angular/common';
import { Component, EventEmitter, Input, Output } from '@angular/core';

@Component({
  selector: 'app-file-upload-button',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './file-upload-button.component.html'
})
export class FileUploadButtonComponent {
  @Input() accept = '';
  @Input() label = 'Upload';
  @Input() loadingLabel = 'Uploading...';
  @Input() loading = false;
  @Input() buttonClass = 'btn-secondary text-sm px-3 py-1.5';
  @Output() fileSelected = new EventEmitter<File>();

  onFileSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    if (file) this.fileSelected.emit(file);
    input.value = '';
  }
}
