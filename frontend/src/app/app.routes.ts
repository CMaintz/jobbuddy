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
  // Needs a Firebase session but not a verified one, so it sits outside the authGuard'd shell.
  {
    path: 'verify-email',
    loadComponent: () => import('./features/auth/verify-email/verify-email.component').then(m => m.VerifyEmailComponent)
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
      {
        path: 'applications/:id/pack',
        loadComponent: () => import('./features/output/application-pack.component').then(m => m.ApplicationPackComponent)
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
      {
        path: 'career-profile',
        loadComponent: () => import('./features/career-profile/career-profile.component').then(m => m.CareerProfileComponent)
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
    ]
  },
  { path: '**', redirectTo: '/dashboard' }
];
