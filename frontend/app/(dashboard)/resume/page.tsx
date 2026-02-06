'use client';

import { useCallback, useEffect, useRef, useState } from 'react';
import { ApiError, apiGet, apiPut, apiUpload } from '../../../lib/api';
import { UploadCloud, CheckCircle2, AlertCircle, Loader2, Save, FileText, Award } from 'lucide-react';

interface ResumeVersion {
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

interface Bullet {
  id?: string;
  text: string;
  technologies?: string[];
  metrics?: string[];
  domains?: string[];
  evidence_level?: string;
}

interface Experience {
  id?: string;
  company: string;
  title?: string;
  start?: string;
  end?: string;
  location?: string;
  description?: string;
  bullets?: Bullet[];
}

interface Project {
  id?: string;
  name: string;
  description?: string;
  technologies?: string[];
  outcomes?: string[];
}

interface StructuredResume {
  basics: { name?: string; email?: string; phone?: string; location?: string; linkedin?: string; github?: string };
  summary?: string;
  skills?: { name: string; category?: string; years?: number }[];
  experience?: Experience[];
  projects?: Project[];
  education?: { institution?: string; degree?: string; field?: string; start?: string; end?: string }[];
}

const PROCESSING = new Set(['UPLOADED', 'EXTRACTING', 'PARSING']);
const ACCEPTED_EXTENSIONS = ['.pdf', '.docx', '.txt'];

function parseStructured(raw: string | null): StructuredResume | null {
  if (!raw) return null;
  try {
    return JSON.parse(raw) as StructuredResume;
  } catch {
    return null;
  }
}

export default function ResumePage() {
  const [version, setVersion] = useState<ResumeVersion | null>(null);
  const [draft, setDraft] = useState<StructuredResume | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [uploading, setUploading] = useState(false);
  const [file, setFile] = useState<File | null>(null);
  const pollRef = useRef<ReturnType<typeof setInterval> | null>(null);

  const stopPolling = useCallback(() => {
    if (pollRef.current) {
      clearInterval(pollRef.current);
      pollRef.current = null;
    }
  }, []);

  const applyVersion = useCallback((v: ResumeVersion) => {
    setVersion(v);
    setDraft(parseStructured(v.structured_data));
  }, []);

  const fetchVersion = useCallback(
    async (id: string) => {
      try {
        const v = await apiGet<ResumeVersion>(`/resumes/${id}`);
        applyVersion(v);
        if (v.state === 'READY' || v.state === 'FAILED') {
          stopPolling();
        }
      } catch (err) {
        setError(err instanceof ApiError ? err.detail ?? err.message : String(err));
        stopPolling();
      }
    },
    [applyVersion, stopPolling],
  );

  const pollUntilDone = useCallback(
    (id: string) => {
      stopPolling();
      pollRef.current = setInterval(() => {
        void fetchVersion(id);
      }, 1500);
    },
    [fetchVersion, stopPolling],
  );

  useEffect(() => stopPolling, [stopPolling]);

  const loadCurrent = useCallback(async () => {
    try {
      const v = await apiGet<ResumeVersion>('/resumes/current');
      applyVersion(v);
      if (PROCESSING.has(v.state)) pollUntilDone(v.id);
    } catch (err) {
      if (err instanceof ApiError && err.status === 404) {
        setVersion(null);
      } else {
        setError(err instanceof ApiError ? err.detail ?? err.message : String(err));
      }
    }
  }, [applyVersion, pollUntilDone]);

  useEffect(() => {
    void loadCurrent();
  }, [loadCurrent]);

  const onFileChange = (f: File | null) => {
    setError(null);
    setFile(f);
    if (f && !ACCEPTED_EXTENSIONS.some((ext) => f.name.toLowerCase().endsWith(ext))) {
      setError('Only PDF, DOCX or TXT resumes are accepted.');
    }
    if (f && f.size > 10 * 1024 * 1024) {
      setError('File exceeds the 10MB limit.');
    }
  };

  const upload = async () => {
    if (!file) return;
    setError(null);
    setUploading(true);
    try {
      const v = await apiUpload<ResumeVersion>('/resumes/upload', file);
      applyVersion(v);
      pollUntilDone(v.id);
    } catch (err) {
      setError(err instanceof ApiError ? err.detail ?? err.message : String(err));
    } finally {
      setUploading(false);
    }
  };

  const saveEdits = async () => {
    if (!version || !draft) return;
    setError(null);
    try {
      const v = await apiPut<ResumeVersion>(`/resumes/${version.id}/edit`, draft);
      applyVersion(v);
    } catch (err) {
      setError(err instanceof ApiError ? err.detail ?? err.message : String(err));
    }
  };

  const patch = (updater: (d: StructuredResume) => void) => {
    setDraft((current) => {
      if (!current) return current;
      const next = structuredClone(current);
      updater(next);
      return next;
    });
  };

  const state = version?.state ?? 'NONE';
  const processing = PROCESSING.has(state);

  return (
    <div className="mx-auto max-w-5xl px-4 py-10 sm:px-6">
      {/* Header Bar */}
      <div className="mb-8 flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between border-b border-black/10 pb-6">
        <div>
          <p className="font-mono text-xs font-semibold uppercase tracking-[0.2em] text-brand">
            01 / CAREER SOURCE
          </p>
          <h1 className="mt-1 text-3xl font-bold tracking-[-0.04em]">Master Resume</h1>
          <p className="mt-1 text-sm text-muted">
            Upload your master resume once. ATSDoctor extracts, structures, and evidence-traces your experience.
          </p>
        </div>
        {version && (
          <div className="flex items-center gap-2 font-mono text-xs">
            <span
              className={`inline-flex items-center gap-1.5 rounded-full px-3 py-1 font-semibold ${
                state === 'READY'
                  ? 'bg-proof text-proof-ink'
                  : state === 'FAILED'
                    ? 'bg-coral-soft text-coral'
                    : 'bg-brand-soft text-brand'
              }`}
            >
              {processing && <Loader2 className="h-3 w-3 animate-spin" />}
              {state === 'READY' && <CheckCircle2 className="h-3 w-3" />}
              {state === 'FAILED' && <AlertCircle className="h-3 w-3" />}
              {state}
            </span>
            {state === 'READY' && (
              <span className="rounded-md border border-black/10 bg-surface px-2.5 py-1 text-faint">
                v{version.version}
              </span>
            )}
          </div>
        )}
      </div>

      {error && (
        <div className="mb-6 flex items-center gap-2 rounded-xl border border-coral/30 bg-coral-soft p-4 text-sm text-coral font-medium">
          <AlertCircle className="h-4 w-4 shrink-0" />
          <span>{error}</span>
        </div>
      )}

      {/* Upload Zone */}
      <section className="mb-10 rounded-[24px] border border-black/10 bg-surface p-6 sm:p-8 shadow-xs">
        <div className="flex flex-col sm:flex-row items-start sm:items-center gap-5">
          <div className="flex h-12 w-12 shrink-0 items-center justify-center rounded-xl bg-brand-soft text-brand">
            <UploadCloud className="h-6 w-6" />
          </div>
          <div className="min-w-0 flex-1">
            <label className="mb-1 block text-sm font-semibold text-foreground">
              Upload Master Resume
            </label>
            <p className="text-xs text-muted">
              Supported formats: PDF, DOCX or TXT (Max 10MB)
            </p>
            <input
              type="file"
              accept=".pdf,.docx,.txt"
              disabled={processing || uploading}
              onChange={(e) => onFileChange(e.target.files?.[0] ?? null)}
              className="mt-3 block w-full text-xs text-muted file:mr-3 file:rounded-lg file:border-0 file:bg-foreground file:px-3 file:py-1.5 file:text-xs file:font-semibold file:text-white hover:file:bg-brand disabled:opacity-50"
            />
          </div>
          <button
            onClick={upload}
            disabled={!file || processing || uploading}
            className="inline-flex items-center gap-2 rounded-[10px] bg-foreground px-5 py-2.5 text-sm font-semibold text-white transition hover:bg-brand hover:-translate-y-0.5 disabled:opacity-40"
          >
            {uploading ? <Loader2 className="h-4 w-4 animate-spin" /> : <UploadCloud className="h-4 w-4 text-proof" />}
            <span>{uploading ? 'Parsing…' : 'Parse Resume'}</span>
          </button>
        </div>
        {processing && (
          <div className="mt-4 flex items-center gap-2 text-xs font-mono text-brand font-semibold animate-pulse">
            <Loader2 className="h-3.5 w-3.5 animate-spin" />
            <span>Parsing resume and categorizing evidence claims...</span>
          </div>
        )}
      </section>

      {version && state === 'FAILED' && (
        <section className="rounded-[24px] border border-coral/20 bg-coral-soft p-6">
          <h2 className="text-base font-bold text-coral flex items-center gap-2">
            <AlertCircle className="h-5 w-5" />
            Processing Failed
          </h2>
          <p className="mt-2 text-sm text-muted">
            {version.error ?? 'Unknown error — please check the file format and try uploading again.'}
          </p>
        </section>
      )}

      {version && state === 'READY' && draft && (
        <>
          <ReviewSummary evidence={version.evidence_summary} />
          <ResumeReview draft={draft} patch={patch} />
          <div className="mt-8 flex justify-end">
            <button
              onClick={saveEdits}
              className="inline-flex items-center gap-2 rounded-[10px] bg-brand px-6 py-3 text-sm font-semibold text-white transition hover:-translate-y-0.5 hover:bg-brand-hover shadow-sm"
            >
              <Save className="h-4 w-4" />
              <span>Save edits & update source</span>
            </button>
          </div>
        </>
      )}

      {!version && !error && (
        <div className="rounded-[24px] border border-dashed border-black/20 p-12 text-center text-muted">
          <FileText className="mx-auto h-8 w-8 text-faint mb-3" />
          <p className="font-mono text-xs uppercase tracking-widest text-faint">No Master Resume Loaded</p>
          <p className="mt-1 text-sm">Upload a resume file above to start structuring your career profile.</p>
        </div>
      )}
    </div>
  );
}

function ReviewSummary({ evidence }: { evidence: ResumeVersion['evidence_summary'] }) {
  const rows = [
    { label: 'A — Fact-backed', value: evidence.a, color: 'bg-proof text-proof-ink' },
    { label: 'B — Context supported', value: evidence.b, color: 'bg-brand-soft text-brand' },
    { label: 'C — Inferred', value: evidence.c, color: 'bg-coral-soft text-coral' },
  ];
  return (
    <section className="mb-8 grid grid-cols-1 gap-4 sm:grid-cols-4">
      {rows.map((row) => (
        <div key={row.label} className="rounded-2xl border border-black/10 bg-white p-5 shadow-2xs">
          <div className="font-editorial text-3xl font-normal text-foreground">{row.value}</div>
          <div className={`mt-2 inline-block rounded-full px-2.5 py-0.5 font-mono text-[10px] font-bold uppercase tracking-wider ${row.color}`}>
            {row.label}
          </div>
        </div>
      ))}
      <div className="rounded-2xl border border-black/10 bg-white p-5 shadow-2xs">
        <div className="font-editorial text-3xl font-normal text-foreground">{evidence.total}</div>
        <div className="mt-2 inline-block rounded-full bg-black/[0.05] px-2.5 py-0.5 font-mono text-[10px] font-bold uppercase tracking-wider text-muted">
          Total Claims
        </div>
      </div>
    </section>
  );
}

const inputClass =
  'w-full rounded-xl border border-black/15 bg-white px-3.5 py-2 text-sm text-foreground placeholder:text-faint focus:border-brand focus:outline-none focus:ring-1 focus:ring-brand transition';

function ResumeReview({
  draft,
  patch,
}: {
  draft: StructuredResume;
  patch: (updater: (d: StructuredResume) => void) => void;
}) {
  return (
    <div className="space-y-6">
      {/* Basics Section */}
      <section className="rounded-[24px] border border-black/10 bg-white p-6 sm:p-7 shadow-xs">
        <h2 className="mb-4 text-lg font-bold tracking-tight text-foreground flex items-center gap-2">
          <Award className="h-5 w-5 text-brand" />
          Basics & Profile
        </h2>
        <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
          <input
            className={inputClass}
            value={draft.basics.name ?? ''}
            placeholder="Full Name"
            onChange={(e) => patch((d) => void (d.basics.name = e.target.value))}
          />
          <input
            className={inputClass}
            value={draft.basics.email ?? ''}
            placeholder="Email Address"
            onChange={(e) => patch((d) => void (d.basics.email = e.target.value))}
          />
          <input
            className={inputClass}
            value={draft.basics.location ?? ''}
            placeholder="Location (e.g. San Francisco, CA)"
            onChange={(e) => patch((d) => void (d.basics.location = e.target.value))}
          />
          <input
            className={inputClass}
            value={draft.basics.linkedin ?? ''}
            placeholder="LinkedIn Profile URL"
            onChange={(e) => patch((d) => void (d.basics.linkedin = e.target.value))}
          />
        </div>
        <textarea
          className={`${inputClass} mt-3`}
          rows={3}
          value={draft.summary ?? ''}
          placeholder="Professional Summary"
          onChange={(e) => patch((d) => void (d.summary = e.target.value))}
        />
      </section>

      {/* Skills Section */}
      <section className="rounded-[24px] border border-black/10 bg-white p-6 sm:p-7 shadow-xs">
        <h2 className="mb-4 text-lg font-bold tracking-tight text-foreground">Skills Architecture</h2>
        <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
          {(draft.skills ?? []).map((skill, i) => (
            <div key={i} className="flex items-center gap-2">
              <input
                className={inputClass}
                value={skill.name}
                placeholder="Skill name"
                onChange={(e) =>
                  patch((d) => {
                    if (d.skills?.[i]) d.skills[i].name = e.target.value;
                  })
                }
              />
              <input
                className={`${inputClass} max-w-[9rem] font-mono text-xs`}
                value={skill.category ?? ''}
                placeholder="Category"
                onChange={(e) =>
                  patch((d) => {
                    if (d.skills?.[i]) d.skills[i].category = e.target.value;
                  })
                }
              />
            </div>
          ))}
        </div>
        {(draft.skills ?? []).length === 0 && (
          <p className="text-sm text-faint italic">No skills extracted yet.</p>
        )}
      </section>

      {/* Experience Section */}
      <section className="rounded-[24px] border border-black/10 bg-white p-6 sm:p-7 shadow-xs">
        <h2 className="mb-4 text-lg font-bold tracking-tight text-foreground">Work Experience</h2>
        {(draft.experience ?? []).map((exp, i) => (
          <div key={i} className="mb-6 border-b border-black/10 pb-6 last:border-0 last:pb-0">
            <div className="mb-3 grid grid-cols-1 gap-3 sm:grid-cols-2">
              <input
                className={inputClass}
                value={exp.company}
                placeholder="Company Name"
                onChange={(e) =>
                  patch((d) => {
                    if (d.experience?.[i]) d.experience[i].company = e.target.value;
                  })
                }
              />
              <input
                className={inputClass}
                value={exp.title ?? ''}
                placeholder="Role / Title"
                onChange={(e) =>
                  patch((d) => {
                    if (d.experience?.[i]) d.experience[i].title = e.target.value;
                  })
                }
              />
            </div>
            <div className="mb-3 grid grid-cols-1 gap-3 sm:grid-cols-2">
              <input
                className={inputClass}
                value={exp.start ?? ''}
                placeholder="Start Date (YYYY-MM)"
                onChange={(e) =>
                  patch((d) => {
                    if (d.experience?.[i]) d.experience[i].start = e.target.value;
                  })
                }
              />
              <input
                className={inputClass}
                value={exp.end ?? ''}
                placeholder="End Date (YYYY-MM or Present)"
                onChange={(e) =>
                  patch((d) => {
                    if (d.experience?.[i]) d.experience[i].end = e.target.value;
                  })
                }
              />
            </div>
            <div className="space-y-2 mt-3">
              <p className="font-mono text-[10px] font-bold uppercase tracking-widest text-faint">Bullets</p>
              {(exp.bullets ?? []).map((bullet, j) => (
                <textarea
                  key={j}
                  className={inputClass}
                  rows={2}
                  value={bullet.text}
                  placeholder="Achievement bullet..."
                  onChange={(e) =>
                    patch((d) => {
                      const target = d.experience?.[i]?.bullets?.[j];
                      if (target) target.text = e.target.value;
                    })
                  }
                />
              ))}
            </div>
          </div>
        ))}
        {(draft.experience ?? []).length === 0 && (
          <p className="text-sm text-faint italic">No work experience entries parsed.</p>
        )}
      </section>

      {/* Projects Section */}
      <section className="rounded-[24px] border border-black/10 bg-white p-6 sm:p-7 shadow-xs">
        <h2 className="mb-4 text-lg font-bold tracking-tight text-foreground">Projects</h2>
        {(draft.projects ?? []).map((project, i) => (
          <div key={i} className="mb-5 border-b border-black/10 pb-5 last:border-0 last:pb-0">
            <input
              className={inputClass}
              value={project.name}
              placeholder="Project Name"
              onChange={(e) =>
                patch((d) => {
                  if (d.projects?.[i]) d.projects[i].name = e.target.value;
                })
              }
            />
            <textarea
              className={`${inputClass} mt-2`}
              rows={2}
              value={project.description ?? ''}
              placeholder="Project summary..."
              onChange={(e) =>
                patch((d) => {
                  if (d.projects?.[i]) d.projects[i].description = e.target.value;
                })
              }
            />
            <div className="mt-2 grid grid-cols-1 sm:grid-cols-2 gap-2">
              <input
                className={`${inputClass} font-mono text-xs`}
                value={(project.technologies ?? []).join(', ')}
                placeholder="Tech stack (comma separated)"
                onChange={(e) =>
                  patch((d) => {
                    if (d.projects?.[i]) {
                      d.projects[i].technologies = e.target.value
                        .split(',')
                        .map((t) => t.trim())
                        .filter(Boolean);
                    }
                  })
                }
              />
              <input
                className={`${inputClass} font-mono text-xs`}
                value={(project.outcomes ?? []).join(', ')}
                placeholder="Outcomes (comma separated)"
                onChange={(e) =>
                  patch((d) => {
                    if (d.projects?.[i]) {
                      d.projects[i].outcomes = e.target.value
                        .split(',')
                        .map((t) => t.trim())
                        .filter(Boolean);
                    }
                  })
                }
              />
            </div>
          </div>
        ))}
        {(draft.projects ?? []).length === 0 && (
          <p className="text-sm text-faint italic">No projects entries parsed.</p>
        )}
      </section>
    </div>
  );
}