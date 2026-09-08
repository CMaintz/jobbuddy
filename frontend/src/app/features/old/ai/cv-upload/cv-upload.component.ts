import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { DocumentApiService } from '../../../../core/api/document.api';
import { AiApiService } from '../../../../core/api/ai.api';
import { CvVersion } from '../../../../core/models/cv-version.model';
import { FormActionsComponent } from '../../../../shared/components/ui/form-actions.component';
import { runAction } from '../../../../shared/utils/async-ui';

@Component({
  selector: 'app-cv-upload',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, RouterLink, FormActionsComponent],
  templateUrl: './cv-upload.component.html'
})
export class CvUploadComponent implements OnInit {
  private fb = inject(FormBuilder);
  private docApi = inject(DocumentApiService);
  private aiApi = inject(AiApiService);

  cvVersions: CvVersion[] = [];
  loading = false;
  success = false;
  error = '';
  analyzing: string | null = null;
  analysisResult: { score: number; suggestions: string[] } | null = null;

  form = this.fb.group({
    name: ['', Validators.required],
    content: ['', Validators.required]
  });

  ngOnInit(): void {
    this.docApi.getCvVersions().subscribe(cvs => this.cvVersions = cvs);
  }

  submit(): void {
    if (this.form.invalid) return;
    this.success = false;
    const { name, content } = this.form.value;
    runAction({
      action$: this.docApi.uploadCv(name!, content!),
      setLoading: value => this.loading = value,
      setError: message => this.error = message,
      errorMessage: error => (error as any)?.error?.message || 'Upload failed',
      next: cv => {
        this.cvVersions.unshift(cv);
        this.form.reset();
        this.success = true;
      }
    });
  }

  analyze(cv: CvVersion): void {
    this.analyzing = cv.id;
    this.analysisResult = null;
    runAction({
      action$: this.aiApi.analyzeCv(cv.id),
      next: r => {
        this.analysisResult = { score: r.score, suggestions: r.suggestions };
        this.analyzing = null;
      },
      error: () => this.analyzing = null
    });
  }
}
