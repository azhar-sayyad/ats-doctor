'use client';

import { useCallback, useEffect, useRef, useState } from 'react';
import { useRouter } from 'next/navigation';
import {
  ApiError,
  createAnalysis,
  getAnalysis,
  getAnalyses,
  getCurrentResume,
  getJobs,
  reanalyze,
  tailorAnalysis,
  type Analysis,
  type Job,
  type ResumeVersion,
} from '../../../lib/api';
import { AnalysisBadge, ScoreBadge } from '../../../components/ui';
import StepGuide from '../../../components/StepGuide';
import AnalysisDetail from '../../../components/analyses/AnalysisDetail';
import { Target, Sparkles, RefreshCw, Eye, ArrowRight, Loader2, AlertCircle, CheckCircle2 } from 'lucide-react';

const PROCESSING = new Set(['QUEUED', 'MATCHING', 'SCORING']);

export default function AnalysesPage() {
  const router = useRouter();
  const [jobs, setJobs] = useState<Job[] | null>(null);
  const [resume, setResume] = useState<ResumeVersion | null>(null);
  const [resumeError, setResumeError] = useState<string | null>(null);
  const [analyses, setAnalyses] = useState<Analysis[] | null>(null);
  const [selectedJobId, setSelectedJobId] = useState('');
  const [selectedResumeId, setSelectedResumeId] = useState('');
  const [activeId, setActiveId] = useState<string | null>(null);
  const [running, setRunning] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const pollRef = useRef<ReturnType<typeof setInterval> | null>(null);

  const stopPolling = useCallback(() => {
    if (pollRef.current) {
      clearInterval(pollRef.current);
      pollRef.current = null;
    }
  }, []);

  useEffect(() => stopPolling, [stopPolling]);

  const applyAnalysis = useCallback((a: Analysis) => {
    setAnalyses((prev) =>
      prev ? prev.map((x) => (x.id === a.id ? a : x)) : prev,
    );
  }, []);

  const startWatching = useCallback(
    (id: string) => {
      setActiveId(id);
      stopPolling();
      pollRef.current = setInterval(() => {
        void getAnalysis(id)
          .then((a) => {
            applyAnalysis(a);
            if (!PROCESSING.has(a.state)) {
              stopPolling();
              if (a.state === 'FAILED') {
                setError(`Analysis failed: ${a.error ?? 'unknown error'}`);
              }
            }
          })
          .catch(() => stopPolling());
      }, 1500);
    },
    [applyAnalysis, stopPolling],
  );

  const loadAll = useCallback(async () => {
    try {
      const [jobList, analysisList] = await Promise.all([getJobs(), getAnalyses()]);
      setJobs(jobList);
      setAnalyses(analysisList);
      setError(null);
    } catch (err) {
      setError(err instanceof ApiError ? err.detail ?? err.message : String(err));
    }
    try {
      const r = await getCurrentResume();
      setResume(r);
      setSelectedResumeId(r.id);
    } catch (err) {
      if (err instanceof ApiError && err.status === 404) {
        setResume(null);
        setResumeError('No master resume yet — upload one on the Resume page first.');
      } else {
        setError(err instanceof ApiError ? err.detail ?? err.message : String(err));
      }
    }
  }, []);

  useEffect(() => {
    void loadAll();
  }, [loadAll]);

  useEffect(() => {
    const params = new URLSearchParams(window.location.search);
    const jobId = params.get('job');
    if (jobId) setSelectedJobId(jobId);
  }, []);

  const readyJobs = (jobs ?? []).filter((j) => j.state === 'READY');

  const runAnalysis = async () => {
    if (!selectedJobId || !selectedResumeId) {
      setError('Select both a job description and a resume version.');
      return;
    }
    setError(null);
    setNotice(null);
    setRunning(true);
    try {
      const a = await createAnalysis(selectedJobId, selectedResumeId);
      setAnalyses((prev) => (prev ? [a, ...prev] : [a]));
      startWatching(a.id);
    } catch (err) {
      setError(err instanceof ApiError ? err.detail ?? err.message : String(err));
    } finally {
      setRunning(false);
    }
  };

  const rerun = async (a: Analysis) => {
    setError(null);
    setNotice(null);
    try {
      const next = await reanalyze(a.id);
      applyAnalysis(next);
      startWatching(next.id);
    } catch (err) {
      setError(err instanceof ApiError ? err.detail ?? err.message : String(err));
    }
  };

  const select = (a: Analysis) => {
    setError(null);
    setNotice(null);
    if (activeId === a.id) {
      setActiveId(null);
      return;
    }
    if (PROCESSING.has(a.state)) {
      startWatching(a.id);
    } else {
      setActiveId(a.id);
    }
  };

  const tailor = async (a: Analysis) => {
    if (a.state !== 'READY') return;
    setError(null);
    setNotice(null);
    setRunning(true);
    try {
      const tailored = await tailorAnalysis(a.id);
      setNotice(`Tailoring queued (${tailored.state}) — opening review…`);
      router.push(`/tailored/${tailored.id}`);
    } catch (err) {
      setError(err instanceof ApiError ? err.detail ?? err.message : String(err));
    } finally {
      setRunning(false);
    }
  };

  const selectClass =
    'w-full rounded-xl border border-black/15 bg-white px-3.5 py-2.5 text-sm text-foreground focus:border-brand focus:outline-none focus:ring-1 focus:ring-brand disabled:opacity-50 transition';

  return (
    <div className="mx-auto max-w-5xl px-4 py-10 sm:px-6">
      <StepGuide
        step={3}
        title="Step 3: Precision Match Analysis"
        description="Select a target Job Description and your Master Resume Version to calculate fit score and missing gaps."
        nextHref="/tailored"
        nextLabel="Step 4: Tailor & Review →"
      />

      {/* Header Bar */}
      <div className="mb-8 flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between border-b border-black/10 pb-6">
        <div>
          <p className="font-mono text-xs font-semibold uppercase tracking-[0.2em] text-brand">
            03 / MATCH EVALUATION
          </p>
          <h1 className="mt-1 text-3xl font-bold tracking-[-0.04em]">Job Match Analyses</h1>
          <p className="mt-1 text-sm text-muted">
            Score your master resume against a target role — evaluate match breakdown, keyword coverage, and gaps.
          </p>
        </div>
        <span className="self-start sm:self-center font-mono text-xs font-semibold rounded-full border border-black/10 bg-surface px-3 py-1 text-muted shadow-2xs">
          {analyses === null ? '…' : analyses.length} match runs
        </span>
      </div>

      {(error || resumeError) && (
        <div className="mb-6 flex items-center gap-2 rounded-xl border border-coral/30 bg-coral-soft p-4 text-sm text-coral font-medium">
          <AlertCircle className="h-4 w-4 shrink-0" />
          <span>{error ?? resumeError}</span>
        </div>
      )}
      {notice && (
        <div className="mb-6 flex items-center gap-2 rounded-xl border border-proof/30 bg-proof/20 p-4 text-sm text-proof-ink font-semibold">
          <CheckCircle2 className="h-4 w-4 shrink-0 text-brand" />
          <span>{notice}</span>
        </div>
      )}

      {/* New Analysis Card */}
      <section className="mb-10 rounded-[24px] border border-black/10 bg-surface p-6 sm:p-8 shadow-xs">
        <h2 className="text-lg font-bold tracking-tight text-foreground flex items-center gap-2">
          <Target className="h-5 w-5 text-brand" />
          Run Precision Match Analysis
        </h2>

        <div className="mt-5 grid grid-cols-1 gap-4 sm:grid-cols-2">
          <div>
            <label className="mb-1 block text-xs font-semibold text-muted">Target Job Description</label>
            <select
              value={selectedJobId}
              onChange={(e) => setSelectedJobId(e.target.value)}
              disabled={jobs === null}
              className={selectClass}
            >
              <option value="">{jobs === null ? 'Loading saved jobs…' : 'Select a target job…'}</option>
              {readyJobs.map((j) => (
                <option key={j.id} value={j.id}>
                  {j.title ?? j.source_filename ?? j.id.slice(0, 8)} — {j.requirement_summary.total} requirements
                </option>
              ))}
            </select>
            {jobs !== null && readyJobs.length === 0 && (
              <p className="mt-1 text-xs text-faint">No parsed jobs yet — add one on the Jobs page.</p>
            )}
          </div>

          <div>
            <label className="mb-1 block text-xs font-semibold text-muted">Master Resume Version</label>
            <select
              value={selectedResumeId}
              onChange={(e) => setSelectedResumeId(e.target.value)}
              disabled={resume === null}
              className={selectClass}
            >
              {resume ? (
                <option value={resume.id}>
                  v{resume.version} {resume.source_filename ?? ''} ({resume.state})
                </option>
              ) : (
                <option value="">No master resume loaded</option>
              )}
            </select>
          </div>
        </div>

        <button
          onClick={runAnalysis}
          disabled={running || !selectedJobId || !selectedResumeId}
          className="mt-6 inline-flex items-center gap-2 rounded-[10px] bg-foreground px-6 py-3 text-sm font-semibold text-white transition hover:bg-brand hover:-translate-y-0.5 disabled:opacity-40"
        >
          {running ? <Loader2 className="h-4 w-4 animate-spin" /> : <Sparkles className="h-4 w-4 text-proof" />}
          <span>{running ? 'Running algorithm…' : 'Run Match Analysis'}</span>
        </button>

        {activeId && (
          <div className="mt-4 flex items-center gap-2 text-xs font-mono text-brand font-semibold animate-pulse">
            <Loader2 className="h-3.5 w-3.5 animate-spin" />
            <span>Calculating match vector scores… results update automatically.</span>
          </div>
        )}
      </section>

      {/* Analysis History */}
      <section>
        <h2 className="mb-4 text-lg font-bold tracking-tight text-foreground">Analysis History</h2>
        {analyses === null ? (
          <div className="py-8 text-center font-mono text-xs text-faint animate-pulse">
            Loading match history…
          </div>
        ) : analyses.length === 0 ? (
          <div className="rounded-[24px] border border-dashed border-black/20 p-12 text-center text-muted">
            <Target className="mx-auto h-8 w-8 text-faint mb-3" />
            <p className="font-mono text-xs uppercase tracking-widest text-faint">No Analyses Yet</p>
            <p className="mt-1 text-sm">Run your first match analysis above to see fit score and missing gaps.</p>
          </div>
        ) : (
          <ul className="space-y-4">
            {analyses.map((a) => {
              const job = jobs?.find((j) => j.id === a.job_id);
              const active = activeId === a.id;
              return (
                <li
                  key={a.id}
                  className={`rounded-[24px] border bg-white p-5 sm:p-6 transition duration-200 shadow-xs ${
                    active ? 'border-brand ring-2 ring-brand/10' : 'border-black/10 hover:border-black/20'
                  }`}
                >
                  <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
                    <div className="flex items-center gap-4 min-w-0 flex-1">
                      <ScoreBadge score={a.score} />
                      <div className="min-w-0 flex-1">
                        <div className="flex items-center gap-2">
                          <p className="truncate text-base font-bold text-foreground">
                            {job?.title ?? job?.source_filename ?? `Job ${a.job_id.slice(0, 8)}`}
                          </p>
                          <AnalysisBadge state={a.state} />
                        </div>
                        <p className="mt-1 text-xs text-muted">
                          Resume v{versionOf(resume, a.resume_version_id)} ·{' '}
                          {new Date(a.created_at).toLocaleString()}
                        </p>
                      </div>
                    </div>

                    <div className="flex items-center gap-2">
                      <button
                        onClick={() => select(a)}
                        disabled={PROCESSING.has(a.state)}
                        className="inline-flex items-center gap-1.5 rounded-lg border border-black/15 px-3.5 py-2 text-xs font-semibold text-foreground transition hover:bg-surface disabled:opacity-40"
                      >
                        <Eye className="h-3.5 w-3.5" />
                        <span>{active ? 'Watching…' : PROCESSING.has(a.state) ? 'Running…' : 'View Report'}</span>
                      </button>
                      <button
                        onClick={() => rerun(a)}
                        disabled={PROCESSING.has(a.state)}
                        className="inline-flex items-center gap-1.5 rounded-lg border border-black/15 px-3 py-2 text-xs font-semibold text-muted transition hover:text-foreground hover:bg-surface disabled:opacity-40"
                        title="Re-analyze"
                      >
                        <RefreshCw className="h-3.5 w-3.5" />
                      </button>
                    </div>
                  </div>

                  {active && (
                    <div className="mt-6 border-t border-black/10 pt-6">
                      <AnalysisDetail analysis={a} onTailor={() => tailor(a)} tailoring={running} />
                    </div>
                  )}
                </li>
              );
            })}
          </ul>
        )}
      </section>
    </div>
  );
}

function versionOf(resume: ResumeVersion | null, versionId: string): string {
  if (resume && resume.id === versionId) return String(resume.version);
  return `${versionId.slice(0, 8)}…`;
}