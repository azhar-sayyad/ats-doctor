'use client';

import { useCallback, useEffect, useRef, useState } from 'react';
import { useRouter } from 'next/navigation';
import { ApiError, apiGet, apiUpload, type ResumeVersion } from '../../../lib/api';
import type { StructuredResume } from '../tailored/types';
import { parseStructured, buildDocumentModel } from '../../../lib/resumeModel';
import PreviewModal from './PreviewModal';
import { UploadCloud, CheckCircle2, AlertCircle, Loader2, FileText, Eye, PenLine, ArrowRight } from 'lucide-react';

const PROCESSING = new Set(['UPLOADED', 'EXTRACTING', 'PARSING']);
const ACCEPTED_EXTENSIONS = ['.pdf', '.docx', '.txt'];

export default function ResumePage() {
  const router = useRouter();
  const [version, setVersion] = useState<ResumeVersion | null>(null);
  const [draft, setDraft] = useState<StructuredResume | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [uploading, setUploading] = useState(false);
  const [file, setFile] = useState<File | null>(null);
  const [previewOpen, setPreviewOpen] = useState(false);
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

  const state = version?.state ?? 'NONE';
  const processing = PROCESSING.has(state);

  return (
    <div className="mx-auto max-w-6xl px-4 py-10 sm:px-6">
      {/* <StepGuide
        title="Step 1: Upload & Structure Master Resume"
        description="Add your core resume once. ATSDoctor extracts, structures, and evidence-traces your experience."
        nextHref="/jobs"
        nextLabel="Step 2: Target Roles →"
      /> */}

      {/* Header Bar */}
      <div className="mb-8 flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between border-b border-black/10 pb-6">
        <div>
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

          {/* Master Resume actions — View (preview dialog) or Edit (dedicated page) */}
          <section className="mb-10 grid grid-cols-1 gap-4 sm:grid-cols-2">
            <button
              onClick={() => setPreviewOpen(true)}
              className="group rounded-[24px] border border-black/10 bg-surface p-6 text-left transition hover:-translate-y-0.5 hover:border-brand/40 hover:shadow-md"
            >
              <div className="flex h-12 w-12 items-center justify-center rounded-xl bg-brand-soft text-brand">
                <Eye className="h-6 w-6" />
              </div>
              <h2 className="mt-4 text-lg font-bold tracking-tight text-foreground">View Resume</h2>
              <p className="mt-1 text-sm text-muted">
                Read-only preview of your structured master resume in a dialog.
              </p>
              <span className="mt-4 inline-flex items-center gap-1.5 font-mono text-[11px] font-bold uppercase tracking-widest text-brand">
                Open preview
                <ArrowRight className="h-3.5 w-3.5 transition group-hover:translate-x-0.5" />
              </span>
            </button>

            <button
              onClick={() => router.push('/resume/edit')}
              className="group rounded-[24px] border border-black/10 bg-surface p-6 text-left transition hover:-translate-y-0.5 hover:border-brand/40 hover:shadow-md"
            >
              <div className="flex h-12 w-12 items-center justify-center rounded-xl bg-foreground text-white">
                <PenLine className="h-6 w-6" />
              </div>
              <h2 className="mt-4 text-lg font-bold tracking-tight text-foreground">Edit Resume</h2>
              <p className="mt-1 text-sm text-muted">
                Edit on a dedicated page — Structured, LaTeX or JSON mode with a live preview.
              </p>
              <span className="mt-4 inline-flex items-center gap-1.5 font-mono text-[11px] font-bold uppercase tracking-widest text-brand">
                Open editor
                <ArrowRight className="h-3.5 w-3.5 transition group-hover:translate-x-0.5" />
              </span>
            </button>
          </section>
        </>
      )}

      {previewOpen && draft && (
        <PreviewModal doc={buildDocumentModel(draft, null, [])} onClose={() => setPreviewOpen(false)} />
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

