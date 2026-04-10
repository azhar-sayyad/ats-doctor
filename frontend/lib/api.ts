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

/** Page envelope returned by the paginated list endpoints (page is 0-based). */
export interface Paginated<T> {
  items: T[];
  page: number;
  size: number;
  total: number;
  has_more: boolean;
}

/** Optional list pagination params (07-api-contract: page/size). */
export interface ListParams {
  page?: number;
  size?: number;
}

function queryString(params?: ListParams): string {
  if (!params) return '';
  const parts: string[] = [];
  if (params.page !== undefined) parts.push(`page=${params.page}`);
  if (params.size !== undefined) parts.push(`size=${params.size}`);
  return parts.length === 0 ? '' : `?${parts.join('&')}`;
}

// ---------------------------------------------------------------------------
// Typed helpers
// ---------------------------------------------------------------------------

// Jobs
export const getJobs = (params?: ListParams) => apiGet<Paginated<Job>>(`/jobs${queryString(params)}`);
export const getJob = (id: string) => apiGet<Job>(`/jobs/${id}`);
export const createJobFromText = (text: string) => {
  const form = new FormData();
  form.append('text', text);
  return apiForm<Job>('/jobs', form);
};
export const createJobFromFile = (file: File) => apiUpload<Job>('/jobs', file);
export const deleteJob = (id: string) => apiDelete<{ status: string }>(`/jobs/${id}`);

// Analyses
export const getAnalyses = (params?: ListParams) => apiGet<Paginated<Analysis>>(`/analyses${queryString(params)}`);
export const getAnalysis = (id: string) => apiGet<Analysis>(`/analyses/${id}`);
export const createAnalysis = (jobId: string, resumeVersionId: string) =>
  apiPost<Analysis>('/analyses', { job_id: jobId, resume_version_id: resumeVersionId });
export const reanalyze = (id: string) => apiPost<Analysis>(`/analyses/${id}/reanalyze`);

const sleep = (ms: number) => new Promise<void>((resolve) => setTimeout(resolve, ms));

const POLL_INTERVAL_MS = 1500;
const POLL_TIMEOUT_MS = 60_000;

async function pollUntil<T>(next: () => Promise<T>, isDone: (value: T) => boolean, label: string): Promise<T> {
  const deadline = Date.now() + POLL_TIMEOUT_MS;
  for (;;) {
    const value = await next();
    if (isDone(value)) return value;
    if (Date.now() >= deadline) {
      throw new ApiError(408, `Timed out waiting for ${label} to become ready`);
    }
    await sleep(POLL_INTERVAL_MS);
  }
}

/** Waits (polling) until a freshly-created job finishes parsing (READY/FAILED). */
export async function waitForJobReady(jobId: string): Promise<Job> {
  return pollUntil(
    () => getJob(jobId),
    (job) => job.state === 'READY' || job.state === 'FAILED',
    'job',
  ).then((job) => {
    if (job.state === 'FAILED') {
      throw new ApiError(400, 'Job parsing failed', job.error ?? 'The job description could not be parsed.');
    }
    return job;
  });
}

/** Waits (polling) until an analysis finishes matching/scoring (READY/FAILED). */
export async function waitForAnalysisReady(analysisId: string): Promise<Analysis> {
  return pollUntil(
    () => getAnalysis(analysisId),
    (a) => a.state === 'READY' || a.state === 'FAILED',
    'analysis',
  ).then((a) => {
    if (a.state === 'FAILED') {
      throw new ApiError(400, 'Analysis failed', a.error ?? 'The match analysis could not be completed.');
    }
    return a;
  });
}

// Tailoring
export const getTailoredResumes = (params?: ListParams) =>
  apiGet<Paginated<TailoredResume>>(`/tailored${queryString(params)}`);
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
export const editTailoredDocument = (id: string, document: unknown) =>
  apiPut<TailoredResume>(`/tailored/${id}/edit`, document);
export const exportTailoredUrl = (id: string, format: string, template?: string) =>
  `${API_BASE_URL}/tailored/${id}/export/${format}${template ? `?template=${encodeURIComponent(template)}` : ''}`;

// Resume templates (CL-020): app-side catalog, per-resume selection persisted.
export interface ResumeTemplateInfo {
  slug: string;
  name: string;
  description: string;
}
export const getResumeTemplates = () => apiGet<ResumeTemplateInfo[]>('/resume-templates');
export const setTailoredTemplate = (id: string, template: string) =>
  apiPut<TailoredResume>(`/tailored/${id}/template`, { template });

// AI transparency
export const getAiConfig = () => apiGet<AiConfig>('/ai/config');

// Resume (current master version + specific versions)
export const getCurrentResume = () => apiGet<ResumeVersion>('/resumes/current');
export const getResumeVersion = (id: string) => apiGet<ResumeVersion>(`/resumes/${id}`);
export const editMasterResume = (versionId: string, document: unknown) =>
  apiPut<ResumeVersion>(`/resumes/${versionId}/edit`, document);