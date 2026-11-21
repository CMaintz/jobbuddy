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
  // Skills are not here — they are their own resource (see SkillsApiService.getProfileSkills),
  // one row each with taxonomy category, proficiency and years. They used to also live on the
  // profile as two bare string arrays, which meant a skill's attributes depended on which of the
  // two write paths had created it.
  languages?: string[];
  /** Leisure interests — the closing section of a Danish CV ("fritidsinteresser"). */
  interests?: string[];
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
  /** Job categories left out of the feed, matching and enrichment. */
  excludedCategories: string[];
  /** Where enrichment looks for postings; null means the default (8260 Viby J, 85 km). */
  /** home is a postcode, place name or kommune, as typed. */
  enrichmentArea: { home: string; radiusKm: number } | null;
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
