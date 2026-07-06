export interface User {
  id: string;
  email: string;
  role: 'USER' | 'ADMIN';
  onboardingComplete: boolean;
}

export interface Profile {
  id?: string;
  userId?: string;
  headline?: string;
  summary?: string;
  yearsExperience?: number;
  skills?: string[];
  technologies?: string[];
  languages?: string[];
  desiredSalaryMin?: number;
  desiredSalaryMax?: number;
  desiredCurrency?: string;
  remotePreference?: string;
}

export interface UserPreferences {
  preferredLocations: string[];
  preferredMunicipalities: string[];
  positiveSignals: string[];
  negativeSignals: string[];
  excludedCompanies: string[];
  preferredRemoteTypes: string[];
  preferredEmploymentTypes: string[];
  preferredSeniority: string[];
  preferredIndustries: string[];
  salaryMin: number | null;
  salaryMax: number | null;
  maxCommuteKm: number | null;
  notificationEnabled: boolean;
  notificationFrequency: string | null;
  weeklyApplicationGoal: number | null;
}

export interface ProfilePrivateInfo {
  id?: string;
  userId?: string;
  fullName?: string;
  phone?: string;
  photoUrl?: string;
  location?: string;
  municipality?: string;
  contactEmail?: string;
}
