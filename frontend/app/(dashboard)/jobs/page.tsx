'use client';

import { useCallback, useEffect, useRef, useState } from 'react';
import { useRouter } from 'next/navigation';
import { ApiError, createJobFromFile, createJobFromText, deleteJob, getJob, getJobs, type Job } from '../../../lib/api';
import { Badge } from '../../../components/ui';
import Pagination from '../../../components/Pagination';
// import StepGuide from '../../../components/StepGuide';
import JobDrawer from '../../../components/jobs/JobDrawer';
import { Briefcase, FileText, Plus, Trash2, ArrowRight, Loader2, Sparkles, AlertCircle } from 'lucide-react';

const PROCESSING = new Set(['CREATED', 'EXTRACTING', 'PARSING']);
const ACCEPTED_EXTENSIONS = ['.pdf', '.docx', '.txt'];
const PAGE_SIZE = 10;

export default function JobsPage() {
  const router = useRouter();
  const [jobs, setJobs] = useState<Job[] | null>(null);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(0);
  const [listError, setListError] = useState<string | null>(null);

  const [mode, setMode] = useState<'paste' | 'file'>('paste');
  const [title, setTitle] = useState('');
  const [company, setCompany] = useState('');
  const [jdText, setJdText] = useState('');
  const [file, setFile] = useState<File | null>(null);
  const [formError, setFormError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  const [pendingId, setPendingId] = useState<string | null>(null);
  const [drawerJobId, setDrawerJobId] = useState<string | null>(null);
  const [drawerJob, setDrawerJob] = useState<Job | null>(null);
  const pollRef = useRef<ReturnType<typeof setInterval> | null>(null);

  const stopPolling = useCallback(() => {
    if (pollRef.current) {
      clearInterval(pollRef.current);
      pollRef.current = null;
    }
  }, []);

  useEffect(() => stopPolling, [stopPolling]);

  const loadJobs = useCallback(async (targetPage: number) => {
    try {
      const result = await getJobs({ page: targetPage, size: PAGE_SIZE });
      setJobs(result.items);
      setTotal(result.total);
      setPage(result.page);
      setListError(null);
    } catch (err) {
      setListError(err instanceof ApiError ? err.detail ?? err.message : String(err));
    }
  }, []);

  useEffect(() => {
    void loadJobs(0);
  }, [loadJobs]);

  const watchAfterCreate = useCallback(
    (id: string) => {
      setPendingId(id);
      stopPolling();
      pollRef.current = setInterval(() => {
        void getJob(id)
          .then((job) => {
            if (job.state === 'READY' || job.state === 'FAILED') {
              stopPolling();
              setPendingId(null);
              void loadJobs(0);
            }
          })
          .catch(() => {
            stopPolling();
            setPendingId(null);
          });
      }, 1500);
    },
    [loadJobs, stopPolling],
  );

  const submitText = async () => {
    if (!jdText.trim()) {
      setFormError('Paste a job description first.');
      return;
    }
    if (jdText.length > 1024 * 1024) {
      setFormError('Pasted JD exceeds the 1MB limit.');
      return;
    }
    setFormError(null);
    setSubmitting(true);
    try {
      const job = await createJobFromText(jdText.trim());
      setJdText('');
      setTitle('');
      setCompany('');
      setMode('paste');
      watchAfterCreate(job.id);
    } catch (err) {
      setFormError(err instanceof ApiError ? err.detail ?? err.message : String(err));
    } finally {
      setSubmitting(false);
    }
  };

  const submitFile = async () => {
    if (!file) {
      setFormError('Choose a file first.');
      return;
    }
    setFormError(null);
    setSubmitting(true);
    try {
      const job = await createJobFromFile(file);
      setFile(null);
      setMode('paste');
      watchAfterCreate(job.id);
    } catch (err) {
      setFormError(err instanceof ApiError ? err.detail ?? err.message : String(err));
    } finally {
      setSubmitting(false);
    }
  };

  const onFileChange = (f: File | null) => {
    setFormError(null);
    setFile(f);
    if (f && !ACCEPTED_EXTENSIONS.some((ext) => f.name.toLowerCase().endsWith(ext))) {
      setFormError('Only PDF, DOCX or TXT job descriptions are accepted.');
    }
    if (f && f.size > 10 * 1024 * 1024) {
      setFormError('File exceeds the 10MB limit.');
    }
  };

  const remove = async (job: Job) => {
    if (!window.confirm(`Delete "${job.title ?? job.source_filename ?? job.id}"?`)) return;
    try {
      await deleteJob(job.id);
      if (drawerJobId === job.id) setDrawerJobId(null);
      if (jobs && jobs.length === 1 && page > 0) {
        void loadJobs(page - 1);
      } else {
        void loadJobs(page);
      }
    } catch (err) {
      setFormError(err instanceof ApiError ? err.detail ?? err.message : String(err));
    }
  };

  const openDrawer = async (job: Job) => {
    setDrawerJobId(job.id);
    setDrawerJob(job);
  };

  const updateDrawerJob = (job: Job) => setDrawerJob(job);

  const pendingJob = jobs?.find((j) => j.id === pendingId);
  const pendingState = pendingJob?.state ?? (pendingId ? 'CREATED' : null);

  const inputClass =
    'w-full rounded-xl border border-black/15 bg-white px-3.5 py-2.5 text-sm text-foreground placeholder:text-faint focus:border-brand focus:outline-none focus:ring-1 focus:ring-brand transition';

  return (
    <div className="mx-auto max-w-6xl px-4 py-10 sm:px-6">
      {/* <StepGuide
        title="Step 2: Add Target Job Postings"
        description="Paste job description text or upload posting files to extract role requirements and skill signals."
        nextHref="/analyses"
        nextLabel="Step 3: Run Fit Analysis →"
      /> */}

      {/* Header Bar */}
      <div className="mb-8 flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between border-b border-black/10 pb-6">
        <div>
          <h1 className="mt-1 text-3xl font-bold tracking-[-0.04em]">Job Descriptions</h1>
          <p className="mt-1 text-sm text-muted">
            Paste a job description or upload a file — ATSDoctor extracts requirements and skill signals automatically.
          </p>
        </div>
        <span className="self-start sm:self-center font-mono text-xs font-semibold rounded-full border border-black/10 bg-surface px-3 py-1 text-muted shadow-2xs">
          {jobs === null ? '…' : total} saved roles
        </span>
      </div>

      {(formError || listError) && (
        <div className="mb-6 flex items-center gap-2 rounded-xl border border-coral/30 bg-coral-soft p-4 text-sm text-coral font-medium">
          <AlertCircle className="h-4 w-4 shrink-0" />
          <span>{formError ?? listError}</span>
        </div>
      )}

      {/* Input Section */}
      <section className="mb-10 rounded-[24px] border border-black/10 bg-surface p-6 sm:p-8 shadow-xs">
        <div className="mb-6 flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
          <h2 className="text-lg font-bold tracking-tight text-foreground flex items-center gap-2">
            <Briefcase className="h-5 w-5 text-brand" />
            Add Target Job Description
          </h2>

          <div className="inline-flex rounded-lg border border-black/10 bg-white p-1">
            <button
              onClick={() => setMode('paste')}
              className={`rounded-md px-3 py-1.5 text-xs font-semibold transition ${
                mode === 'paste' ? 'bg-foreground text-white shadow-2xs' : 'text-muted hover:text-foreground'
              }`}
            >
              Paste Text
            </button>
            <button
              onClick={() => setMode('file')}
              className={`rounded-md px-3 py-1.5 text-xs font-semibold transition ${
                mode === 'file' ? 'bg-foreground text-white shadow-2xs' : 'text-muted hover:text-foreground'
              }`}
            >
              Upload File
            </button>
          </div>
        </div>

        {mode === 'paste' ? (
          <>
            <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
              <input
                value={title}
                onChange={(e) => setTitle(e.target.value)}
                placeholder="Job title (e.g. Senior Software Engineer)"
                className={inputClass}
              />
              <input
                value={company}
                onChange={(e) => setCompany(e.target.value)}
                placeholder="Company name (e.g. Acme Corp)"
                className={inputClass}
              />
            </div>
            <textarea
              value={jdText}
              onChange={(e) => setJdText(e.target.value)}
              rows={7}
              placeholder="Paste the full job description text here…"
              className={`${inputClass} mt-3`}
            />
            <div className="mt-4 flex items-center justify-between">
              <button
                onClick={submitText}
                disabled={submitting || !jdText.trim()}
                className="inline-flex items-center gap-2 rounded-[10px] bg-foreground px-5 py-2.5 text-sm font-semibold text-white transition hover:bg-brand hover:-translate-y-0.5 disabled:opacity-40"
              >
                {submitting ? <Loader2 className="h-4 w-4 animate-spin" /> : <Plus className="h-4 w-4 text-proof" />}
                <span>{submitting ? 'Analyzing…' : 'Extract Role Signals'}</span>
              </button>
              {jdText.length > 0 && (
                <span className="font-mono text-xs text-faint">
                  {jdText.length.toLocaleString()} chars
                </span>
              )}
            </div>
          </>
        ) : (
          <>
            <div className="flex flex-col sm:flex-row items-start sm:items-center gap-4">
              <div className="min-w-0 flex-1">
                <label className="mb-1 block text-xs font-semibold text-muted">
                  Job description document (PDF / DOCX / TXT, ≤10MB)
                </label>
                <input
                  type="file"
                  accept=".pdf,.docx,.txt"
                  onChange={(e) => onFileChange(e.target.files?.[0] ?? null)}
                  className="block w-full text-xs text-muted file:mr-3 file:rounded-lg file:border-0 file:bg-foreground file:px-3 file:py-1.5 file:text-xs file:font-semibold file:text-white hover:file:bg-brand"
                />
              </div>
              <button
                onClick={submitFile}
                disabled={submitting || !file}
                className="inline-flex items-center gap-2 rounded-[10px] bg-foreground px-5 py-2.5 text-sm font-semibold text-white transition hover:bg-brand hover:-translate-y-0.5 disabled:opacity-40"
              >
                {submitting ? <Loader2 className="h-4 w-4 animate-spin" /> : <Sparkles className="h-4 w-4 text-proof" />}
                <span>{submitting ? 'Uploading…' : 'Upload & Parse'}</span>
              </button>
            </div>
          </>
        )}

        {pendingState && (
          <div className="mt-4 flex items-center gap-2 text-xs font-mono text-brand font-semibold animate-pulse">
            <Loader2 className="h-3.5 w-3.5 animate-spin" />
            <span>Extracting role requirements ({pendingState.toLowerCase()})…</span>
          </div>
        )}
      </section>

      {/* Saved Jobs List */}
      <section>
        <h2 className="mb-4 text-lg font-bold tracking-tight text-foreground">Saved Target Roles</h2>
        {jobs === null ? (
          <div className="py-8 text-center font-mono text-xs text-faint animate-pulse">
            Loading saved jobs…
          </div>
        ) : jobs.length === 0 ? (
          <div className="rounded-[24px] border border-dashed border-black/20 p-12 text-center text-muted">
            <Briefcase className="mx-auto h-8 w-8 text-faint mb-3" />
            <p className="font-mono text-xs uppercase tracking-widest text-faint">No Roles Added</p>
            <p className="mt-1 text-sm">Add a job description above to start matching your resume.</p>
          </div>
        ) : (
          <ul className="space-y-3">
            {jobs.map((job) => (
              <li
                key={job.id}
                className="group rounded-2xl border border-black/10 bg-white p-5 transition duration-200 hover:border-black/20 shadow-2xs"
              >
                <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
                  <div className="min-w-0 flex-1">
                    <div className="flex items-center gap-2">
                      <p className="truncate text-base font-bold text-foreground">
                        {job.title ?? job.source_filename ?? '(Untitled Job)'}
                      </p>
                      {job.company && (
                        <span className="rounded-md bg-black/[0.04] px-2 py-0.5 font-mono text-xs text-muted">
                          {job.company}
                        </span>
                      )}
                    </div>
                    <p className="mt-1 text-xs text-muted">
                      Added {new Date(job.created_at).toLocaleDateString()} ·{' '}
                      <span className="font-mono text-faint">{job.id.slice(0, 8)}</span>
                    </p>
                  </div>

                  <div className="flex flex-wrap items-center gap-2.5">
                    <Badge state={job.state} />
                    {job.state === 'READY' && (
                      <span className="rounded-full bg-brand-soft px-3 py-1 font-mono text-[11px] font-bold text-brand">
                        {job.requirement_summary.total} signals
                        {job.requirement_summary.high > 0 && (
                          <span className="ml-1 text-coral font-bold">({job.requirement_summary.high} high)</span>
                        )}
                      </span>
                    )}

                    <div className="flex items-center gap-2">
                      <button
                        onClick={() => openDrawer(job)}
                        className="rounded-lg border border-black/15 px-3 py-1.5 text-xs font-semibold text-foreground transition hover:bg-surface"
                      >
                        View Requirements
                      </button>
                      <button
                        onClick={() => router.push(`/analyses?job=${job.id}`)}
                        disabled={job.state !== 'READY'}
                        className="inline-flex items-center gap-1 rounded-lg bg-foreground px-3.5 py-1.5 text-xs font-semibold text-white transition hover:bg-brand disabled:opacity-40"
                      >
                        <span>Analyze Match</span>
                        <ArrowRight className="h-3 w-3" />
                      </button>
                      <button
                        onClick={() => remove(job)}
                        className="rounded-lg border border-coral/20 p-1.5 text-coral transition hover:bg-coral-soft"
                        title="Delete job"
                      >
                        <Trash2 className="h-4 w-4" />
                      </button>
                    </div>
                  </div>
                </div>
              </li>
            ))}
          </ul>
        )}
        {jobs !== null && jobs.length > 0 && (
          <Pagination page={page} size={PAGE_SIZE} total={total} onChange={(p) => loadJobs(p)} />
        )}
      </section>

      {drawerJobId && (
        <JobDrawer
          job={drawerJob}
          jobId={drawerJobId}
          onClose={() => setDrawerJobId(null)}
          onJobUpdate={updateDrawerJob}
        />
      )}
    </div>
  );
}