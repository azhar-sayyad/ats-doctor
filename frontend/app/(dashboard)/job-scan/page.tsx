'use client';

import { useCallback, useEffect, useRef, useState } from 'react';
import { useRouter } from 'next/navigation';
import {
  ApiError,
  createJobFromText,
  createAnalysis,
  getAnalysis,
  getCurrentResume,
  tailorAnalysis,
  waitForJobReady,
  type ResumeVersion,
  type Analysis,
} from '../../../lib/api';
// import StepGuide from '../../../components/StepGuide';
import AnalysisDetail from '../../../components/analyses/AnalysisDetail';
import { Search, FileText, Loader2, AlertCircle } from 'lucide-react';

const inputClass =
  'w-full rounded-xl border border-black/15 bg-white px-3.5 py-2.5 text-sm text-foreground placeholder:text-faint focus:border-brand focus:outline-none focus:ring-1 focus:ring-brand transition';

export default function JobScanPage() {
  const router = useRouter();
  const [resume, setResume] = useState<ResumeVersion | null>(null);
  const [jdText, setJdText] = useState('');
  const [scanning, setScanning] = useState(false);
  const [scanResult, setScanResult] = useState<Analysis | null>(null);
  const [error, setError] = useState<string | null>(null);
  const pollRef = useRef<ReturnType<typeof setInterval> | null>(null);

  const stopPolling = useCallback(() => {
    if (pollRef.current) {
      clearInterval(pollRef.current);
      pollRef.current = null;
    }
  }, []);

  useEffect(() => stopPolling, [stopPolling]);

  useEffect(() => {
    getCurrentResume()
      .then(setResume)
      .catch(() => setResume(null));
  }, []);

  const watchAnalysis = useCallback(
    (id: string) => {
      stopPolling();
      pollRef.current = setInterval(() => {
        void getAnalysis(id)
          .then((a) => {
            setScanResult(a);
            if (a.state === 'READY' || a.state === 'FAILED') {
              stopPolling();
              setScanning(false);
              if (a.state === 'FAILED') {
                setError(`Analysis failed: ${a.error ?? 'unknown error'}`);
              }
            }
          })
          .catch((err: unknown) => {
            stopPolling();
            setScanning(false);
            setError(err instanceof ApiError ? err.detail ?? err.message : String(err));
          });
      }, 1500);
    },
    [stopPolling],
  );

  const runScan = async () => {
    if (!resume) {
      setError('Upload a Master Resume first before scanning jobs.');
      return;
    }
    if (!jdText.trim()) {
      setError('Paste a target job description text first.');
      return;
    }
    setError(null);
    setScanResult(null);
    setScanning(true);
    try {
      // 1. Create temporary job and wait for it to finish parsing
      const job = await createJobFromText(jdText.trim());
      await waitForJobReady(job.id);
      // 2. Queue analysis, then watch it until the match completes
      const analysis = await createAnalysis(job.id, resume.id);
      setScanResult(analysis);
      watchAnalysis(analysis.id);
    } catch (err) {
      setError(err instanceof ApiError ? err.detail ?? err.message : String(err));
      setScanning(false);
    }
  };

  const tailor = async () => {
    if (!scanResult || scanResult.state !== 'READY') return;
    setError(null);
    try {
      const tailored = await tailorAnalysis(scanResult.id);
      router.push(`/tailored/${tailored.id}`);
    } catch (err) {
      setError(err instanceof ApiError ? err.detail ?? err.message : String(err));
    }
  };

  return (
    <div className="mx-auto max-w-6xl px-4 py-10 sm:px-6">
      {/* <StepGuide
        title="Job Scan Diagnostic Tool"
        description="Scan any job description against your master resume to detect missing hard skills, ATS compatibility warnings, and match score."
        nextHref="/tailored"
        nextLabel="Proceed to Tailor →"
      /> */}

      {/* Header Bar */}
      <div className="mb-8 border-b border-black/10 pb-6">
        <h1 className="mt-1 text-3xl font-bold tracking-[-0.04em]">Job Scan</h1>
        <p className="mt-1 text-sm text-muted">
          Instant compatibility check — see matched vs missing hard skills before tailoring.
        </p>
      </div>

      {error && (
        <div className="mb-6 flex items-center gap-2 rounded-xl border border-coral/30 bg-coral-soft p-4 text-sm text-coral font-medium">
          <AlertCircle className="h-4 w-4 shrink-0" />
          <span>{error}</span>
        </div>
      )}

      {/* Dual Input Cards */}
      <div className="grid grid-cols-1 md:grid-cols-2 gap-6 mb-10">
        {/* Box 1: Select Master Resume */}
        <div className="rounded-[24px] border border-black/10 bg-white p-6 shadow-xs">
          <div className="flex items-center gap-2 mb-3">
            <FileText className="h-5 w-5 text-brand" />
            <h2 className="text-base font-bold text-foreground">1. Active Master Resume</h2>
          </div>
          {resume ? (
            <div className="rounded-xl bg-surface p-4 border border-black/10">
              <div className="flex items-center justify-between">
                <span className="font-bold text-sm text-foreground">
                  Master Resume v{resume.version}
                </span>
                <span className="rounded-full bg-proof px-2.5 py-0.5 font-mono text-[10px] font-bold text-proof-ink">
                  {resume.state}
                </span>
              </div>
              <p className="mt-1 text-xs text-muted">
                Source file: {resume.source_filename ?? 'Pasted text'} · {resume.evidence_summary.total} evidence claims
              </p>
            </div>
          ) : (
            <p className="text-xs text-coral font-semibold italic">
              No master resume loaded — upload one on the Master Resume page first.
            </p>
          )}
        </div>

        {/* Box 2: Target Job Description */}
        <div className="rounded-[24px] border border-black/10 bg-white p-6 shadow-xs">
          <div className="flex items-center gap-2 mb-3">
            <Search className="h-5 w-5 text-brand" />
            <h2 className="text-base font-bold text-foreground">2. Target Job Description</h2>
          </div>
          <textarea
            value={jdText}
            onChange={(e) => setJdText(e.target.value)}
            rows={4}
            placeholder="Paste target job posting text here..."
            className={inputClass}
          />
        </div>
      </div>

      <div className="mb-10 flex justify-center">
        <button
          onClick={runScan}
          disabled={scanning || !resume || !jdText.trim()}
          className="inline-flex items-center gap-2 rounded-[12px] bg-foreground px-8 py-3.5 text-sm font-bold text-white transition hover:bg-brand hover:-translate-y-0.5 disabled:opacity-40 shadow-md"
        >
          {scanning ? <Loader2 className="h-4 w-4 animate-spin" /> : <Search className="h-4 w-4 text-proof" />}
          <span>{scanning ? 'Scanning compatibility…' : 'Run Job Scan Diagnostic'}</span>
        </button>
      </div>

      {/* Analysis Output View */}
      {scanResult && (
        <div>
          <div className="mb-4 flex flex-wrap items-center justify-between gap-3">
            <div>
              <p className="font-mono text-xs font-bold uppercase tracking-widest text-faint">
                ATS Compatibility Gauge
              </p>
              <h3 className="mt-1 text-2xl font-bold text-foreground">Scan Summary Results</h3>
            </div>
            {scanResult.state === 'READY' && scanResult.score !== null && (
              <div className="flex items-center gap-3 bg-surface px-5 py-3 rounded-2xl border border-black/10">
                <span className="font-editorial text-4xl font-normal text-brand">{scanResult.score}%</span>
                <span className="font-mono text-xs font-bold uppercase tracking-wider text-muted">
                  Match Score
                </span>
              </div>
            )}
          </div>
          <AnalysisDetail analysis={scanResult} onTailor={tailor} tailoring={false} />
        </div>
      )}
    </div>
  );
}
