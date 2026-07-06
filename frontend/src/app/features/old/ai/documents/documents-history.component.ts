import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { AiApiService } from '../../../../core/api/ai.api';
import { GeneratedDocument } from '../../../../core/models/generated-document.model';
import { EmptyStateComponent } from '../../../../shared/components/ui/empty-state.component';
import { resetFlagAfter, runAction } from '../../../../shared/utils/async-ui';
import { copyText } from '../../../../shared/utils/file-download';

@Component({
  selector: 'app-documents-history',
  standalone: true,
  imports: [CommonModule, EmptyStateComponent],
  templateUrl: './documents-history.component.html'
})
export class DocumentsHistoryComponent implements OnInit {
  private api = inject(AiApiService);

  docs: GeneratedDocument[] = [];
  loading = true;
  expanded: string | null = null;
  copiedId: string | null = null;

  ngOnInit(): void {
    runAction({
      action$: this.api.getDocuments(),
      setLoading: value => this.loading = value,
      next: docs => this.docs = docs
    });
  }

  toggle(id: string): void {
    this.expanded = this.expanded === id ? null : id;
  }

  copy(doc: GeneratedDocument): void {
    copyText(doc.content).then(() => {
      this.copiedId = doc.id;
      resetFlagAfter(value => this.copiedId = value ? doc.id : null);
    });
  }

  docTypeLabel(type: string): string {
    return type.replace(/_/g, ' ').toLowerCase()
      .replace(/\b\w/g, c => c.toUpperCase());
  }
}
