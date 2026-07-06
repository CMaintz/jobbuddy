import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { PromptApiService } from '../../../../core/api/prompt.api';
import { PromptTemplate } from '../../../../core/models/prompt-template.model';
import { EmptyStateComponent } from '../../../../shared/components/ui/empty-state.component';
import { runAction } from '../../../../shared/utils/async-ui';

@Component({
  selector: 'app-prompt-list',
  standalone: true,
  imports: [CommonModule, RouterLink, EmptyStateComponent],
  templateUrl: './prompt-list.component.html'
})
export class PromptListComponent implements OnInit {
  private api = inject(PromptApiService);

  templates: PromptTemplate[] = [];
  loading = true;

  ngOnInit(): void {
    runAction({
      action$: this.api.getAll(),
      setLoading: value => this.loading = value,
      next: ts => this.templates = ts
    });
  }

  duplicate(t: PromptTemplate): void {
    this.api.duplicate(t.id, t.name + ' (copy)').subscribe(copy => {
      this.templates.push(copy);
    });
  }
}
