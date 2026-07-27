import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { CommonModule } from '@angular/common';
import { ResumeStateService } from '../../services/resume-state.service';
import { PersonalInfo } from '../../models/resume-builder.models';
import { TranslateModule } from '@ngx-translate/core';
import { RichTextEditorComponent } from '../../shared/rich-text-editor.component';
import { AiRefineMenuComponent } from '../../shared/ai-refine-menu.component';
import { PhotoCropDialogComponent } from '../../shared/photo-crop-dialog.component';

@Component({
  selector: 'app-personal-info-form',
  standalone: true,
  imports: [CommonModule, FormsModule, TranslateModule, RichTextEditorComponent, PhotoCropDialogComponent, AiRefineMenuComponent],
  templateUrl: './personal-info-form.component.html',
})
export class PersonalInfoFormComponent {
  private state = inject(ResumeStateService);

  cropSrc = signal<string | null>(null);

  get pi() { return this.state.personalInfo(); }
  get photoShape() { return this.state.settings().photoStyle ?? 'circle'; }
  get jobDescription() { return this.state.jobDescription() ?? undefined; }

  update(field: string, value: string): void {
    this.state.updatePersonalInfo({ [field]: value } as Partial<PersonalInfo>);
  }

  onPhotoSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    input.value = '';
    if (!file) return;
    const reader = new FileReader();
    reader.onload = () => this.cropSrc.set(reader.result as string);
    reader.readAsDataURL(file);
  }

  recrop(): void {
    if (this.pi.photoUrl) this.cropSrc.set(this.pi.photoUrl);
  }

  onCropped(dataUrl: string): void {
    this.cropSrc.set(null);
    this.state.updatePersonalInfo({ photoUrl: dataUrl });
  }

  removePhoto(): void {
    this.state.updatePersonalInfo({ photoUrl: undefined });
  }
}
