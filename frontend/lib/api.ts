import type { TailoredResume, TailoredChange } from '../app/(dashboard)/tailored/types';
export type { TailoredResume, TailoredChange };

const API_URL = process.env.NEXT_PUBLIC_API_URL ?? 'http://localhost:8000/api/v1';

/** Base API URL for direct links (export downloads, review UI). */
export const API_BASE_URL = API_URL;

export class ApiError extends Error {
  readonly status: number;
  readonly detail?: string;

  constructor(status: number, message: string, detail?: string) {
    super(message);
    this.status = status;
    this.detail = detail;
  }
}

async function request(path: string, init: RequestInit): Promise<Response> {
  return fetch(`${API_URL}${path}`, { cache: 'no-store', ...init });
}

async function json<T>(res: Response, method: string, path: string): Promise<T> {
  if (!res.ok) {
    let detail: string | undefined;
    try {
      const body = (await res.json()) as { detail?: string };
      detail = body.detail;
    } catch {
      // non-JSON error body
    }
    throw new ApiError(res.status, `${method} ${path} failed: ${res.status}`, detail);
  }
  return res.json() as Promise<T>;
}

export async function apiGet<T>(path: string): Promise<T> {
  const res = await request(path, {});
  return json<T>(res, 'GET', path);
}

export async function apiPost<T>(path: string, body?: unknown): Promise<T> {
  const res = await request(path, {
    method: 'POST',
    headers: body === undefined ? undefined : { 'Content-Type': 'application/json' },
    body: body === undefined ? undefined : JSON.stringify(body),
  });
  return json<T>(res, 'POST', path);
}

export async function apiPut<T>(path: string, body: unknown): Promise<T> {
  const res = await request(path, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  });
  return json<T>(res, 'PUT', path);
}

export async function apiDelete<T>(path: string): Promise<T> {
  const res = await request(path, { method: 'DELETE' });
  return json<T>(res, 'DELETE', path);
}

/** Multipart file upload (resume uploads, FEAT-010). */
export async function apiUpload<T>(path: string, file: File): Promise<T> {
  const form = new FormData();
  form.append('file', file);
  const res = await request(path, { method: 'POST', body: form });
  return json<T>(res, 'POST', path);
}

/** Multipart upload with an extra text part (JD paste mode, POST /jobs). */
export async function apiForm<T>(path: string, form: FormData): Promise<T> {
  const res = await request(path, { method: 'POST', body: form });
  return json<T>(res, 'POST', path);
}

// ---------------------------------------------------------------------------
// Domain types (mirror the snake_case JSON of 07-api-contract)
// ---------------------------------------------------------------------------

export interface Job {
  id: string;
  state: string;
  title: string | null;
  company: string | null;
  location: string | null;
  seniority: string | null;
  raw_text: string | null;
  structured_data: string | null;
  source_filename: string | null;
  model_used: string | null;
  model_version: string | null;
  prompt_version: string | null;
  temperature: number | null;
  error: string | null;
  requirement_summary: { total: number; high: number };
  created_at: string;
  updated_at: string;
}

/** §4.3 structured JD payload persisted as job.structured_data (camelCase). */
export interface JobDto {
  metadata?: { id?: string; source?: string; filename?: string; parsed_at?: string };
  job?: { title?: string; company?: string; location?: string; seniority?: string; post_date?: string; url?: string };
  requirements?: { id?: string; text: string; type?: string; importance?: string; keywords?: string[] }[];
  skills?: { required?: string[]; preferred?: string[]; niceToHave?: string[] };
  responsibilities?: string[];
  keywords?: string[];
}

export interface ResumeVersion {
  id: string;
  resume_id: string;
  version: number;
  state: string;
  raw_text: string | null;
  structured_data: string | null;
  source_filename: string | null;
  model_used: string | null;
  model_version: string | null;
  prompt_version: string | null;
  temperature: number | null;
  error: string | null;
  evidence_summary: { a: number; b: number; c: number; total: number };
  created_at: string;
  updated_at: string;
}

export interface ScoreCategory {
  score: number;
  weight: number;
  matched: string[];
  missing: string[];
}

export interface ScoreBreakdown {
  total: number;
  skills: ScoreCategory;
  keywords: ScoreCategory;
  responsibilities: ScoreCategory;
  experience: ScoreCategory;
  education: ScoreCategory;
  seniority: ScoreCategory;
}

export interface AnalysisMatch {
  requirement_id: string;
  requirement_text: string;
  status: string;
  match_type: string;
  keywords_matched: string[];
  similarity: number | null;
  evidence: { id: string; text: string }[];
}

export interface AnalysisGap {
  status: string;
  type: string;
  text: string;
  suggestions?: string[];
}

export interface Analysis {
  id: string;
  job_id: string;
  resume_version_id: string;
  state: string;
  score: number | null;
  score_breakdown: ScoreBreakdown | null;
  matches: AnalysisMatch[] | null;
  gaps: AnalysisGap[] | null;
  generation: { matching?: { mode?: string; semantic_enabled?: boolean }; scoring?: { formula?: string } } | null;
  error: string | null;
  created_at: string;
  updated_at: string;
}

export interface AiConfig {
  mode: string;
  provider: string;
  tasks: Record<string, { profile: string }>;
  data_leaves_machine: boolean;
  note: string;
}

// ---------------------------------------------------------------------------
// Typed helpers
// ---------------------------------------------------------------------------

// Jobs
export const getJobs = () => apiGet<Job[]>('/jobs');
export const getJob = (id: string) => apiGet<Job>(`/jobs/${id}`);
export const createJobFromText = (text: string) => {
  const form = new FormData();
  form.append('text', text);
  return apiForm<Job>('/jobs', form);
};
export const createJobFromFile = (file: File) => apiUpload<Job>('/jobs', file);
export const deleteJob = (id: string) => apiDelete<{ status: string }>(`/jobs/${id}`);

// Analyses
export const getAnalyses = () => apiGet<Analysis[]>('/analyses');
export const getAnalysis = (id: string) => apiGet<Analysis>(`/analyses/${id}`);
export const createAnalysis = (jobId: string, resumeVersionId: string) =>
  apiPost<Analysis>('/analyses', { job_id: jobId, resume_version_id: resumeVersionId });
export const reanalyze = (id: string) => apiPost<Analysis>(`/analyses/${id}/reanalyze`);

// Tailoring
export const getTailoredResumes = () => apiGet<TailoredResume[]>('/tailored');
export const tailorAnalysis = (analysisId: string) =>
  apiPost<TailoredResume>(`/analyses/${analysisId}/tailor`);
export const getTailoredResume = (id: string) => apiGet<TailoredResume>(`/tailored/${id}`);
export const getChanges = (id: string) => apiGet<TailoredChange[]>(`/tailored/${id}/changes`);
export const reviewChange = (tailoredId: string, changeId: string, action: string, newText?: string) =>
  apiPost<TailoredChange>(`/tailored/${tailoredId}/changes/${changeId}`, {
    action,
    ...(newText === undefined ? {} : { new_text: newText }),
  });
export const approveTailored = (id: string) => apiPost<{ status: string }>(`/tailored/${id}/approve`);

// AI transparency
export const getAiConfig = () => apiGet<AiConfig>('/ai/config');

// Resume (current master version + specific versions)
export const getCurrentResume = () => apiGet<ResumeVersion>('/resumes/current');
export const getResumeVersion = (id: string) => apiGet<ResumeVersion>(`/resumes/${id}`);