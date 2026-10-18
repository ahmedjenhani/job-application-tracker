export type ApplicationStatus = 'WISHLIST' | 'APPLIED' | 'INTERVIEW' | 'OFFER' | 'REJECTED';

export const STATUSES: readonly ApplicationStatus[] = ['WISHLIST', 'APPLIED', 'INTERVIEW', 'OFFER', 'REJECTED'];

export const STATUS_LABEL: Record<ApplicationStatus, string> = {
  WISHLIST: 'Wishlist',
  APPLIED: 'Applied',
  INTERVIEW: 'Interview',
  OFFER: 'Offer',
  REJECTED: 'Rejected',
};

export interface User {
  id: number;
  email: string;
  fullName: string;
}

export interface AuthResponse {
  token: string;
  user: User;
}

export interface JobApplication {
  id: number;
  company: string;
  role: string;
  location?: string;
  jobUrl?: string;
  salaryRange?: string;
  status: ApplicationStatus;
  appliedDate?: string;
  followUpDate?: string;
  skills: string[];
  notes?: string;
  jobDescription?: string;
  createdAt: string;
  updatedAt: string;
}

export interface ApplicationRequest {
  company: string;
  role: string;
  location?: string | null;
  jobUrl?: string | null;
  salaryRange?: string | null;
  status: ApplicationStatus;
  appliedDate?: string | null;
  followUpDate?: string | null;
  skills: string[];
  notes?: string | null;
  jobDescription?: string | null;
}

export interface HistoryEntry {
  from?: ApplicationStatus;
  to: ApplicationStatus;
  changedAt: string;
}

export interface Analytics {
  total: number;
  byStatus: Record<ApplicationStatus, number>;
  responseRate: number;
  interviewRate: number;
  avgDaysToResponse?: number;
  followUpsDue: number;
  weekly: { weekStart: string; count: number }[];
  funnel: { label: string; count: number }[];
}

export interface AppNotification {
  id: number;
  applicationId?: number;
  message: string;
  read: boolean;
  createdAt: string;
}

export interface JobPostingExtraction {
  company: string;
  role: string;
  location?: string;
  salaryRange?: string;
  skills: string[];
  summary?: string;
}

export interface InterviewQuestion {
  question: string;
  category: 'TECHNICAL' | 'BEHAVIORAL' | 'COMPANY';
  tip?: string;
}
