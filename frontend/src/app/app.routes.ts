import { Routes } from '@angular/router';
import { authGuard } from './core/auth/auth.guard';

export const routes: Routes = [
  { path: '', redirectTo: '/dashboard', pathMatch: 'full' },

  // ── Public routes ───────────────────────────────────────
  {
    path: 'login',
    loadComponent: () => import('./features/auth/login/login.component').then(m => m.LoginComponent)
  },
  {
    path: 'register',
    loadComponent: () => import('./features/auth/register/register.component').then(m => m.RegisterComponent)
  },
  {
    path: 'auth/linkedin/callback',
    loadComponent: () => import('./features/auth/linkedin-callback/linkedin-callback.component').then(m => m.LinkedInCallbackComponent)
  },

  // ── Protected routes ────────────────────────────────────
  {
    path: '',
    canActivate: [authGuard],
    children: [
      // Onboarding
      {
        path: 'onboarding',
        loadComponent: () => import('./features/onboarding/onboarding.component').then(m => m.OnboardingComponent)
      },

      // ── Core screens (new design) ────────────────────
      {
        path: 'dashboard',
        loadComponent: () => import('./features/dashboard/dashboard.component').then(m => m.DashboardComponent)
      },
      {
        path: 'jobs/feed',
        loadComponent: () => import('./features/job-feed/job-feed.component').then(m => m.JobFeedComponent)
      },
      {
        path: 'jobs/saved',
        loadComponent: () => import('./features/saved-roles/saved-roles.component').then(m => m.SavedRolesComponent)
      },
      {
        path: 'jobs/:id',
        loadComponent: () => import('./features/job-details/job-details.component').then(m => m.JobDetailsComponent)
      },
      {
        path: 'pipeline',
        loadComponent: () => import('./features/pipeline/pipeline.component').then(m => m.PipelineComponent)
      },
      {
        path: 'apply',
        loadComponent: () => import('./features/apply-new/apply-new.component').then(m => m.ApplyNewComponent)
      },
      {
        path: 'applications',
        loadComponent: () => import('./features/applications-new/applications-list.component').then(m => m.ApplicationsListNewComponent)
      },
      {
        path: 'applications/:id',
        loadComponent: () => import('./features/application-detail-new/application-detail.component').then(m => m.ApplicationDetailNewComponent)
      },

      // Master CV
      {
        path: 'cv',
        loadComponent: () => import('./features/master-cv/master-cv-builder.component').then(m => m.MasterCvBuilderComponent)
      },
      {
        path: 'cv/import',
        loadComponent: () => import('./features/master-cv/master-cv-import.component').then(m => m.MasterCvImportComponent)
      },

      // Prompts
      {
        path: 'prompts-new',
        loadComponent: () => import('./features/prompts-new/prompts-library.component').then(m => m.PromptsLibraryComponent)
      },

      // Track
      {
        path: 'tasks',
        loadComponent: () => import('./features/tasks/tasks.component').then(m => m.TasksComponent)
      },
      {
        path: 'interviews',
        loadComponent: () => import('./features/interviews/interviews.component').then(m => m.InterviewsComponent)
      },
      {
        path: 'contacts',
        loadComponent: () => import('./features/contacts/contacts.component').then(m => m.ContactsComponent)
      },

      // Settings
      {
        path: 'settings',
        loadComponent: () => import('./features/settings-new/settings.component').then(m => m.SettingsNewComponent)
      },

      // Resume Builder (kept intact)
      {
        path: 'resume-builder',
        loadComponent: () => import('./features/resume-builder/resume-builder.component').then(m => m.ResumeBuilderComponent)
      },
      {
        path: 'resume-builder/:draftId',
        loadComponent: () => import('./features/resume-builder/resume-builder.component').then(m => m.ResumeBuilderComponent)
      },

      // ── Old frontend (kept for reference) ───────────
      {
        path: 'old/jobs',
        loadComponent: () => import('./features/jobs/jobs-list/jobs-list.component').then(m => m.JobsListComponent)
      },
      {
        path: 'old/jobs/add',
        loadComponent: () => import('./features/jobs/job-add/job-add.component').then(m => m.JobAddComponent)
      },
      {
        path: 'old/jobs/ignored',
        loadComponent: () => import('./features/jobs/ignored-jobs/ignored-jobs.component').then(m => m.IgnoredJobsComponent)
      },
      {
        path: 'old/jobs/:id',
        loadComponent: () => import('./features/jobs/job-detail/job-detail.component').then(m => m.JobDetailComponent)
      },
      {
        path: 'old/applications',
        loadComponent: () => import('./features/applications/application-list/application-list.component').then(m => m.ApplicationListComponent)
      },
      {
        path: 'old/applications/pipeline',
        loadComponent: () => import('./features/applications/application-pipeline/application-pipeline.component').then(m => m.ApplicationPipelineComponent)
      },
      {
        path: 'old/applications/:id',
        loadComponent: () => import('./features/applications/application-detail/application-detail.component').then(m => m.ApplicationDetailComponent)
      },
      {
        path: 'old/profile',
        loadComponent: () => import('./features/profile/profile.component').then(m => m.ProfileComponent)
      },
      {
        path: 'old/profile/writing-style',
        loadComponent: () => import('./features/profile/writing-style/writing-style.component').then(m => m.WritingStyleComponent)
      },
      {
        path: 'old/ai/cv',
        loadComponent: () => import('./features/ai/cv-upload/cv-upload.component').then(m => m.CvUploadComponent)
      },
      {
        path: 'old/ai/analyze',
        loadComponent: () => import('./features/ai/cv-analysis/cv-analysis.component').then(m => m.CvAnalysisComponent)
      },
      {
        path: 'old/ai/generate',
        loadComponent: () => import('./features/ai/application-generator/application-generator.component').then(m => m.ApplicationGeneratorComponent)
      },
      {
        path: 'old/ai/documents',
        loadComponent: () => import('./features/ai/documents/documents-history.component').then(m => m.DocumentsHistoryComponent)
      },
      {
        path: 'old/analytics',
        loadComponent: () => import('./features/analytics/analytics.component').then(m => m.AnalyticsComponent)
      },
      {
        path: 'old/companies',
        loadComponent: () => import('./features/companies/companies-list.component').then(m => m.CompaniesListComponent)
      },
      {
        path: 'old/templates',
        loadComponent: () => import('./features/pdf-templates/pdf-templates.component').then(m => m.PdfTemplatesComponent)
      },
      {
        path: 'old/prompts',
        loadComponent: () => import('./features/prompts/prompt-list/prompt-list.component').then(m => m.PromptListComponent)
      },
      {
        path: 'old/prompts/new',
        loadComponent: () => import('./features/prompts/prompt-editor/prompt-editor.component').then(m => m.PromptEditorComponent)
      },
      {
        path: 'old/prompts/:id/edit',
        loadComponent: () => import('./features/prompts/prompt-editor/prompt-editor.component').then(m => m.PromptEditorComponent)
      },
      {
        path: 'old/apply/:jobId',
        loadComponent: () => import('./features/apply/apply-wizard.component').then(m => m.ApplyWizardComponent)
      },
      {
        path: 'old/cv',
        loadComponent: () => import('./features/cv/cv-page.component').then(m => m.CvPageComponent)
      },

      // Redirects for old paths
      { path: 'jobs/search', redirectTo: 'jobs/feed', pathMatch: 'full' },
      { path: 'applications/pipeline', redirectTo: 'pipeline', pathMatch: 'full' },
      { path: 'prompts', redirectTo: 'prompts-new', pathMatch: 'full' },
      { path: 'templates', redirectTo: 'old/templates', pathMatch: 'full' },
      { path: 'analytics', redirectTo: 'old/analytics', pathMatch: 'full' },
      { path: 'companies', redirectTo: 'old/companies', pathMatch: 'full' },
      { path: 'profile', redirectTo: 'old/profile', pathMatch: 'full' },
      { path: 'ai/generate', redirectTo: 'old/ai/generate', pathMatch: 'full' },
      { path: 'ai/documents', redirectTo: 'old/ai/documents', pathMatch: 'full' },
      { path: 'ai/cv', redirectTo: 'old/ai/cv', pathMatch: 'full' },
      { path: 'ai/analyze', redirectTo: 'old/ai/analyze', pathMatch: 'full' },
    ]
  },
  { path: '**', redirectTo: '/dashboard' }
];
