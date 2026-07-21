import { Routes } from '@angular/router';
import { authGuard } from './core/auth/auth.guard';
import { onboardingGuard } from './core/auth/onboarding.guard';

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
  {
    path: 'privacy',
    loadComponent: () => import('./features/legal/privacy-policy.component').then(m => m.PrivacyPolicyComponent)
  },

  // ── Protected routes ────────────────────────────────────
  {
    path: '',
    canActivate: [authGuard],
    children: [
      // Onboarding
      {
        path: 'onboarding',
        canActivate: [onboardingGuard],
        loadComponent: () => import('./features/onboarding/onboarding.component').then(m => m.OnboardingComponent)
      },

      // Redirects for old paths — before the :id routes, which would otherwise swallow them
      { path: 'jobs/search', redirectTo: 'jobs/feed', pathMatch: 'full' },
      { path: 'applications/pipeline', redirectTo: 'pipeline', pathMatch: 'full' },
      { path: 'profile', redirectTo: 'settings', pathMatch: 'full' },
      { path: 'ai/generate', redirectTo: 'apply', pathMatch: 'full' },
      { path: 'ai/documents', redirectTo: 'documents', pathMatch: 'full' },
      { path: 'ai/cv', redirectTo: 'cv/import', pathMatch: 'full' },
      { path: 'ai/analyze', redirectTo: 'analysis', pathMatch: 'full' },

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
        loadComponent: () => import('./features/apply/apply.component').then(m => m.ApplyComponent)
      },
      {
        path: 'applications',
        loadComponent: () => import('./features/applications/applications-list.component').then(m => m.ApplicationsListComponent)
      },
      {
        path: 'applications/:id',
        loadComponent: () => import('./features/application-detail/application-detail.component').then(m => m.ApplicationDetailComponent)
      },
      {
        path: 'applications/:id/output',
        loadComponent: () => import('./features/output/application-output.component').then(m => m.ApplicationOutputComponent)
      },
      {
        path: 'applications/:id/cv',
        loadComponent: () => import('./features/output/tailored-cv.component').then(m => m.TailoredCvComponent)
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
        path: 'prompts',
        loadComponent: () => import('./features/prompts/prompts-library.component').then(m => m.PromptsLibraryComponent)
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
      // TODO(recruiter-contacts): a lightweight contacts screen is wanted here eventually.
      // The data largely exists already — applications carry recruiterName/recruiterEmail/
      // recruiterMessage/recruiterReply (see Application record + application-detail's
      // recruiter section) — so a first version can simply aggregate those per person
      // across applications (name, company via the job, last touch, linked applications).
      // No new backend entity needed until we want contacts that aren't tied to an
      // application (or email integration). A fully mocked standalone CRM screen used to
      // live at features/contacts/ (deleted — recover from git history for layout ideas).

      // Insights & library
      {
        path: 'analysis',
        loadComponent: () => import('./features/analysis/cv-analysis.component').then(m => m.CvAnalysisComponent)
      },
      {
        path: 'analytics',
        loadComponent: () => import('./features/analytics/analytics.component').then(m => m.AnalyticsComponent)
      },
      {
        path: 'companies',
        loadComponent: () => import('./features/companies/companies.component').then(m => m.CompaniesComponent)
      },
      {
        path: 'documents',
        loadComponent: () => import('./features/documents/documents.component').then(m => m.DocumentsComponent)
      },

      // Settings
      {
        path: 'settings',
        loadComponent: () => import('./features/settings/settings.component').then(m => m.SettingsComponent)
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
        loadComponent: () => import('./features/old/jobs/jobs-list/jobs-list.component').then(m => m.JobsListComponent)
      },
      {
        path: 'old/jobs/add',
        loadComponent: () => import('./features/old/jobs/job-add/job-add.component').then(m => m.JobAddComponent)
      },
      {
        path: 'old/jobs/ignored',
        loadComponent: () => import('./features/old/jobs/ignored-jobs/ignored-jobs.component').then(m => m.IgnoredJobsComponent)
      },
      {
        path: 'old/jobs/:id',
        loadComponent: () => import('./features/old/jobs/job-detail/job-detail.component').then(m => m.JobDetailComponent)
      },
      {
        path: 'old/applications',
        loadComponent: () => import('./features/old/applications/application-list/application-list.component').then(m => m.ApplicationListComponent)
      },
      {
        path: 'old/applications/pipeline',
        loadComponent: () => import('./features/old/applications/application-pipeline/application-pipeline.component').then(m => m.ApplicationPipelineComponent)
      },
      {
        path: 'old/applications/:id',
        loadComponent: () => import('./features/old/applications/application-detail/application-detail.component').then(m => m.ApplicationDetailComponent)
      },
      {
        path: 'old/profile',
        loadComponent: () => import('./features/old/profile/profile.component').then(m => m.ProfileComponent)
      },
      {
        path: 'old/profile/writing-style',
        loadComponent: () => import('./features/old/profile/writing-style/writing-style.component').then(m => m.WritingStyleComponent)
      },
      {
        path: 'old/ai/cv',
        loadComponent: () => import('./features/old/ai/cv-upload/cv-upload.component').then(m => m.CvUploadComponent)
      },
      {
        path: 'old/ai/analyze',
        loadComponent: () => import('./features/old/ai/cv-analysis/cv-analysis.component').then(m => m.CvAnalysisComponent)
      },
      {
        path: 'old/ai/generate',
        loadComponent: () => import('./features/old/ai/application-generator/application-generator.component').then(m => m.ApplicationGeneratorComponent)
      },
      {
        path: 'old/ai/documents',
        loadComponent: () => import('./features/old/ai/documents/documents-history.component').then(m => m.DocumentsHistoryComponent)
      },
      {
        path: 'old/analytics',
        loadComponent: () => import('./features/old/analytics/analytics.component').then(m => m.AnalyticsComponent)
      },
      {
        path: 'old/companies',
        loadComponent: () => import('./features/old/companies/companies-list.component').then(m => m.CompaniesListComponent)
      },
      {
        path: 'old/templates',
        loadComponent: () => import('./features/old/pdf-templates/pdf-templates.component').then(m => m.PdfTemplatesComponent)
      },
      {
        path: 'old/prompts',
        loadComponent: () => import('./features/old/prompts/prompt-list/prompt-list.component').then(m => m.PromptListComponent)
      },
      {
        path: 'old/prompts/new',
        loadComponent: () => import('./features/old/prompts/prompt-editor/prompt-editor.component').then(m => m.PromptEditorComponent)
      },
      {
        path: 'old/prompts/:id/edit',
        loadComponent: () => import('./features/old/prompts/prompt-editor/prompt-editor.component').then(m => m.PromptEditorComponent)
      },
      {
        path: 'old/apply/:jobId',
        loadComponent: () => import('./features/old/apply/apply-wizard.component').then(m => m.ApplyWizardComponent)
      },
      {
        path: 'old/cv',
        loadComponent: () => import('./features/old/cv/cv-page.component').then(m => m.CvPageComponent)
      },
    ]
  },
  { path: '**', redirectTo: '/dashboard' }
];
