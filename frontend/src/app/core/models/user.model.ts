export interface User {
  id: string;
  email: string;
  role: 'USER' | 'ADMIN';
}

export interface Profile {
  id?: string;
  userId?: string;
  fullName?: string;
  headline?: string;
  summary?: string;
  location?: string;
  municipality?: string;
  linkedinUrl?: string;
  githubUrl?: string;
  websiteUrl?: string;
  yearsExperience?: number;
  skills?: string[];
  technologies?: string[];
  languages?: string[];
  desiredSalaryMin?: number;
  desiredSalaryMax?: number;
  desiredCurrency?: string;
  remotePreference?: string;
}

export interface AuthResponse {
  token: string;
  userId: string;
  email: string;
  role: 'USER' | 'ADMIN';
}
