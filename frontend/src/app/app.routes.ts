import { Routes } from '@angular/router';
import { authGuard } from './core/auth/auth.guard';

export const routes: Routes = [
  { path: '', redirectTo: '/dashboard', pathMatch: 'full' },
  {
    path: 'login',
    loadComponent: () => import('./features/auth/login/login.component').then(m => m.LoginComponent)
  },
  {
    path: 'register',
    loadComponent: () => import('./features/auth/register/register.component').then(m => m.RegisterComponent)
  },
  {
    path: '',
    canActivate: [authGuard],
    children: [
      {
        path: 'dashboard',
        loadComponent: () => import('./features/dashboard/dashboard.component').then(m => m.DashboardComponent)
      },
      {
        path: 'jobs',
        loadComponent: () => import('./features/jobs/jobs-list/jobs-list.component').then(m => m.JobsListComponent)
      },
      {
        path: 'jobs/search',
        loadComponent: () => import('./features/jobs/jobs-list/jobs-list.component').then(m => m.JobsListComponent)
      },
      {
        path: 'jobs/saved',
        loadComponent: () => import('./features/jobs/saved-jobs/saved-jobs.component').then(m => m.SavedJobsComponent)
      },
      {
        path: 'jobs/ignored',
        loadComponent: () => import('./features/jobs/ignored-jobs/ignored-jobs.component').then(m => m.IgnoredJobsComponent)
      },
      {
        path: 'jobs/:id',
        loadComponent: () => import('./features/jobs/job-detail/job-detail.component').then(m => m.JobDetailComponent)
      },
      {
        path: 'applications',
        loadComponent: () => import('./features/applications/application-list/application-list.component').then(m => m.ApplicationListComponent)
      },
      {
        path: 'applications/pipeline',
        loadComponent: () => import('./features/applications/application-pipeline/application-pipeline.component').then(m => m.ApplicationPipelineComponent)
      },
      {
        path: 'applications/:id',
        loadComponent: () => import('./features/applications/application-detail/application-detail.component').then(m => m.ApplicationDetailComponent)
      },
      {
        path: 'ai/cv',
        loadComponent: () => import('./features/ai/cv-upload/cv-upload.component').then(m => m.CvUploadComponent)
      },
      {
        path: 'ai/analyze',
        loadComponent: () => import('./features/ai/cv-analysis/cv-analysis.component').then(m => m.CvAnalysisComponent)
      },
      {
        path: 'ai/generate',
        loadComponent: () => import('./features/ai/application-generator/application-generator.component').then(m => m.ApplicationGeneratorComponent)
      },
      {
        path: 'ai/documents',
        loadComponent: () => import('./features/ai/documents/documents-history.component').then(m => m.DocumentsHistoryComponent)
      },
      {
        path: 'prompts',
        loadComponent: () => import('./features/prompts/prompt-list/prompt-list.component').then(m => m.PromptListComponent)
      },
      {
        path: 'prompts/new',
        loadComponent: () => import('./features/prompts/prompt-editor/prompt-editor.component').then(m => m.PromptEditorComponent)
      },
      {
        path: 'prompts/:id/edit',
        loadComponent: () => import('./features/prompts/prompt-editor/prompt-editor.component').then(m => m.PromptEditorComponent)
      },
      {
        path: 'profile',
        loadComponent: () => import('./features/profile/profile.component').then(m => m.ProfileComponent)
      },
      {
        path: 'profile/writing-style',
        loadComponent: () => import('./features/profile/writing-style/writing-style.component').then(m => m.WritingStyleComponent)
      }
    ]
  },
  { path: '**', redirectTo: '/dashboard' }
];
