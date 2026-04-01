'use client';

import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { apiGet, apiPost, ApiError, API_BASE_URL, type Analysis, type Job, type ResumeVersion } from '../../../../lib/api';
import type { StructuredResume, TailoredChange, TailoredResume, ReviewBusy } from '../types';
import { buildDocumentModel, computeAnalytics, parseStructured } from '../../../../lib/resumeModel';
import DocumentViewer from './DocumentViewer';
import AnalyticsPanel from './AnalyticsPanel';
import JdModal from './JdModal';
import TraceDrawer from '../TraceDrawer';
import { AlertCircle, ShieldCheck, Download, ArrowRight, CheckCircle2 } from 'lucide-react';

interface Props {
  tailoredId: string;
  initialTailored: TailoredResume;
  initialChanges: TailoredChange[];
  initialAnalysis: Analysis | null;
  initialJob: Job | null;
  initialResumeVersion: ResumeVersion | null;
}

const ACTIVE = new Set(['QUEUED', 'GENERATING', 'VALIDATING']);

const STATE_STYLES: Record<string, string> = {
  READY: 'bg-proof text-proof-ink font-mono font-bold',
  NEEDS_REVIEW: 'bg-brand-soft text-brand font-mono font-bold',
  APPROVED: 'bg-foreground text-white font-mono font-bold',
  QUEUED: 'bg-brand-soft text-brand font-mono font-bold',
  GENERATING: 'bg-brand-soft text-brand font-mono font-bold animate-pulse',
  VALIDATING: 'bg-brand-soft text-brand font-mono font-bold animate-pulse',
};

const STATE_STEPS: Record<string, number> = {
  QUEUED: 0,
  GENERATING: 1,
  VALIDATING: 2,
};

interface ValidationReport {
  valid: boolean;
  checked_changes?: number;
  validated_at?: string;
  issues?: { type?: string; code?: string; text?: string; suggestion?: string; context?: string }[];
}

export default function Workspace({
  tailoredId,
  initialTailored,
  initialChanges,
  initialAnalysis,
  initialJob,
  initialResumeVersion,
}: Props) {
  const [tailored, setTailored] = useState<TailoredResume>(initialTailored);
  const [changes, setChanges] = useState<TailoredChange[]>(initialChanges);
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [busy, setBusy] = useState<ReviewBusy | null>(null);
  const [openChange, setOpenChange] = useState<TailoredChange | null>(null);
  const [approveOpen, setApproveOpen] = useState(false);
  const [validation, setValidation] = useState<ValidationReport | null>(null);
  const [validationError, setValidationError] = useState<string | null>(null);
  const [jdOpen, setJdOpen] = useState(false);
  const pollRef = useRef<ReturnType<typeof setInterval> | null>(null);

  const stopPolling = useCallback(() => {
    if (pollRef.current) {
      clearInterval(pollRef.current);
      pollRef.current = null;
    }
  }, []);

  useEffect(() => stopPolling, [stopPolling]);

  useEffect(() => {
    if (!ACTIVE.has(tailored.state)) return;
    stopPolling();
    pollRef.current = setInterval(() => {
      apiGet<TailoredResume>(`/tailored/${tailoredId}`)
        .then((t) => {
          setTailored(t);
          if (!ACTIVE.has(t.state)) {
            stopPolling();
          }
        })
        .catch((err: unknown) => {
          setError(err instanceof ApiError ? err.detail ?? err.message : String(err));
          stopPolling();
        });
      apiGet<TailoredChange[]>(`/tailored/${tailoredId}/changes`)
        .then(setChanges)
        .catch(() => {
          // changes unlock once the run finishes — retry next tick
        });
    }, 1500);
    return stopPolling;
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [tailored.state, tailoredId]);

  // After a full-document edit the saved `document` is authoritative; otherwise
  // the viewer reconciles master structured data + tailored content + changes.
  const resume = useMemo(
    () =>
      tailored.document
        ? parseStructured<StructuredResume>(tailored.document)
        : parseStructured<StructuredResume>(initialResumeVersion?.structured_data ?? null),
    [tailored.document, initialResumeVersion],
  );

  const doc = useMemo(
    () => buildDocumentModel(resume, tailored.document ? null : tailored.content, changes),
    [resume, tailored.document, tailored.content, changes],
  );

  const analytics = useMemo(
    () => computeAnalytics(tailored, initialAnalysis, initialJob, changes, tailored.content, resume),
    [tailored, initialAnalysis, initialJob, changes, resume],
  );

  const pending = changes.filter((c) => c.status === 'PENDING').length;
  const active = ACTIVE.has(tailored.state);

  async function act(change: TailoredChange, action: string, newText?: string) {
    setBusy({ changeId: change.id, action });
    setError(null);
    setNotice(null);
    try {
      const body: Record<string, unknown> = { action };
      if (action === 'edit' && newText !== undefined) {
        body.new_text = newText;
      }
      const updated = await apiPost<TailoredChange>(
        `/tailored/${tailoredId}/changes/${change.id}`,
        body,
      );
      setChanges((prev) => prev.map((c) => (c.id === change.id ? updated : c)));
      if (action === 'edit' || action === 'regenerate') {
        setNotice('Resume re-validated — resolve remaining pending items before final approval.');
      }
    } catch (err) {
      setError(err instanceof ApiError ? err.detail ?? err.message : String(err));
    } finally {
      setBusy(null);
    }
  }

  async function checkValidation() {
    setApproveOpen(true);
    setValidationError(null);
    setValidation(null);
    try {
      const report = await apiPost<ValidationReport>(`/tailored/${tailoredId}/validate`);
      setValidation(report);
    } catch (err) {
      setValidationError(err instanceof ApiError ? err.detail ?? err.message : String(err));
    }
  }

  async function approve() {
    setBusy({ changeId: '', action: 'approve' });
    setError(null);
    setNotice(null);
    try {
      await apiPost<{ status: string }>(`/tailored/${tailoredId}/approve`);
      window.location.reload();
    } catch (err) {
      setError(err instanceof ApiError ? err.detail ?? err.message : String(err));
      setBusy(null);
    }
  }

  function handleExport(format: 'pdf' | 'docx' | 'json') {
    if (pending > 0) {
      setError(`Export blocked: ${pending} change(s) still pending review. Please accept, reject, or edit all changes first.`);
      void checkValidation();
      return;
    }
    window.open(`${API_BASE_URL}/tailored/${tailoredId}/export/${format}`, '_blank');
  }

  const steps = [
    { key: 'QUEUED', label: 'Queued — preparing the tailoring run' },
    { key: 'GENERATING', label: 'Generating — rewriting bullets with AI' },
    { key: 'VALIDATING', label: 'Validating — fact-grounding every change' },
  ];
  const stepIndex = STATE_STEPS[tailored.state] ?? 0;

  return (
    <>
      {/* Header Bar */}
      <div className="mb-6 mt-6 flex flex-col gap-4 border-b border-black/10 pb-6 sm:flex-row sm:items-center sm:justify-between">
        <div>
          <p className="font-mono text-xs font-semibold uppercase tracking-[0.2em] text-brand">
            04 / REVIEW & EXPORT
          </p>
          <h1 className="mt-1 text-3xl font-bold tracking-[-0.04em]">Tailored Workspace</h1>
          <p className="mt-1 text-sm text-muted">
            Review evidence-backed rewrites inline, resolve every change, then approve and export.
          </p>
          <p className="mt-2 font-mono text-xs text-faint">{tailoredId}</p>
        </div>
        <div className="flex flex-col items-start gap-2 sm:items-end">
          <span
            className={`rounded-full px-3 py-1 text-[11px] uppercase tracking-wider ${
              STATE_STYLES[tailored.state] ?? 'bg-black/[0.05] text-muted font-mono font-semibold'
            }`}
          >
            {tailored.state}
          </span>
          {!active && (
            <span className="font-mono text-[10px] text-faint">
              {pending} pending · {changes.length - pending} resolved
            </span>
          )}
        </div>
      </div>

      {tailored.error && (
        <Banner tone="error">{tailored.error}</Banner>
      )}
      {error && <Banner tone="error">{error}</Banner>}
      {notice && <Banner tone="success">{notice}</Banner>}

      {active && (
        <div className="mb-6 rounded-[20px] border border-black/10 bg-surface p-6 sm:p-8 shadow-2xs">
          <div className="flex items-center gap-3">
            <span className="h-3 w-3 animate-ping rounded-full bg-brand" />
            <h2 className="text-lg font-bold tracking-tight text-foreground">Generation in progress</h2>
          </div>
          <ol className="mt-6 space-y-3">
            {steps.map((step, i) => {
              const done = i < stepIndex;
              const current = i === stepIndex;
              return (
                <li key={step.key} className="flex items-center gap-3">
                  <span
                    className={`flex h-6 w-6 items-center justify-center rounded-full text-xs font-semibold ${
                      done ? 'bg-proof text-proof-ink' : current ? 'bg-foreground text-white' : 'bg-black/[0.06] text-faint'
                    }`}
                  >
                    {done ? '✓' : i + 1}
                  </span>
                  <span className={`text-sm ${done ? 'text-faint line-through decoration-black/30' : current ? 'font-semibold text-foreground' : 'text-faint'}`}>
                    {step.label}
                    {current && (
                      <span className="ml-2 inline-block h-3 w-3 animate-spin rounded-full border-2 border-brand border-t-transparent align-middle" />
                    )}
                  </span>
                </li>
              );
            })}
          </ol>
          <p className="mt-6 text-sm text-muted">Refreshing automatically — no action needed.</p>
        </div>
      )}

      {/* Two-column workspace */}
      <div className="grid grid-cols-1 gap-6 xl:grid-cols-[minmax(0,1fr)_330px]">
        <DocumentViewer doc={doc} busy={busy} onAct={act} onTrace={setOpenChange} />
        <AnalyticsPanel analytics={analytics} onOpenJd={() => setJdOpen(true)} />
      </div>

      {/* Approve & Export bar */}
      <div className="mt-8 flex flex-col items-stretch justify-between gap-4 rounded-[20px] bg-foreground p-5 text-white shadow-md sm:flex-row sm:items-center">
        <button
          onClick={checkValidation}
          disabled={busy !== null}
          className="inline-flex items-center justify-center gap-2 rounded-[10px] bg-proof px-5 py-2.5 text-xs font-bold text-proof-ink transition hover:bg-white"
        >
          <ShieldCheck className="h-4 w-4 text-brand" />
          <span>Approve & Finalize Resume</span>
        </button>
        <div className="flex items-center gap-2">
          <span className="font-mono text-[10px] uppercase tracking-widest text-white/50">Exports:</span>
          {(['pdf', 'docx', 'json'] as const).map((format) => (
            <button
              key={format}
              onClick={() => handleExport(format)}
              className="inline-flex items-center gap-1 rounded-lg border border-white/20 px-3 py-1.5 font-mono text-xs font-bold uppercase text-white transition hover:bg-white/10"
            >
              <Download className="h-3 w-3 text-proof" />
              <span>{format}</span>
            </button>
          ))}
        </div>
      </div>

      {/* Approval modal */}
      {approveOpen && (
        <div
          className="fixed inset-0 z-50 flex items-center justify-center bg-foreground/50 p-4 backdrop-blur-xs"
          onClick={() => setApproveOpen(false)}
        >
          <div
            className="w-full max-w-lg rounded-[28px] border border-black/10 bg-white p-7 shadow-2xl"
            onClick={(e) => e.stopPropagation()}
          >
            <h3 className="text-xl font-bold tracking-tight text-foreground">Approve Tailored Resume</h3>
            {pending > 0 && (
              <div className="mt-4 rounded-xl border border-coral/30 bg-coral-soft p-4 text-xs font-medium text-coral">
                {pending} change{pending === 1 ? '' : 's'} still pending review. All changes must be accepted, rejected, or edited before approval.
              </div>
            )}
            {validationError && (
              <div className="mt-4 rounded-xl border border-coral/30 bg-coral-soft p-4 text-xs font-semibold text-coral">
                {validationError}
              </div>
            )}
            {validation && (
              <div className="mt-4 space-y-2">
                <p className="text-xs text-muted">
                  Fact-grounding check:{' '}
                  <span className={validation.valid ? 'font-bold text-brand' : 'font-bold text-coral'}>
                    {validation.valid ? '✓ Passed (All claims grounded)' : '⚠ Issues detected'}
                  </span>
                  {validation.checked_changes !== undefined && (
                    <span className="font-mono text-faint"> · {validation.checked_changes} checked</span>
                  )}
                </p>
                {validation.issues && validation.issues.length > 0 && (
                  <ul className="max-h-48 space-y-2 overflow-y-auto pr-1">
                    {validation.issues.map((issue, idx) => (
                      <li key={idx} className="rounded-xl border border-coral/20 bg-coral-soft p-3 text-xs">
                        <p className="font-bold text-coral">
                          {issue.type} ({issue.code})
                        </p>
                        {issue.text && <p className="mt-1 text-muted">{issue.text}</p>}
                        {issue.suggestion && (
                          <p className="mt-1 font-mono text-[10px] text-brand">Suggestion: {issue.suggestion}</p>
                        )}
                      </li>
                    ))}
                  </ul>
                )}
              </div>
            )}
            <div className="mt-6 flex items-center justify-end gap-3">
              <button
                onClick={() => setApproveOpen(false)}
                className="rounded-lg border border-black/15 px-4 py-2 text-xs font-semibold text-muted hover:text-foreground"
              >
                Cancel
              </button>
              <button
                onClick={approve}
                disabled={busy !== null || pending > 0}
                className="inline-flex items-center gap-1.5 rounded-lg bg-foreground px-5 py-2 text-xs font-bold text-white transition hover:bg-brand disabled:opacity-40"
              >
                <span>{busy?.action === 'approve' ? 'Approving…' : 'Approve & Finalize'}</span>
                <ArrowRight className="h-3.5 w-3.5" />
              </button>
            </div>
          </div>
        </div>
      )}

      {jdOpen && <JdModal analytics={analytics} onClose={() => setJdOpen(false)} />}
      {openChange && (
        <TraceDrawer tailoredId={tailoredId} change={openChange} onClose={() => setOpenChange(null)} />
      )}
    </>
  );
}

function Banner({ tone, children }: { tone: 'error' | 'success'; children: React.ReactNode }) {
  return (
    <div
      className={`mb-4 flex items-center gap-2 rounded-xl border p-4 text-sm font-medium ${
        tone === 'error'
          ? 'border-coral/30 bg-coral-soft text-coral'
          : 'border-proof/30 bg-proof/20 text-proof-ink'
      }`}
    >
      {tone === 'error' ? <AlertCircle className="h-4 w-4 shrink-0" /> : <CheckCircle2 className="h-4 w-4 shrink-0 text-brand" />}
      <span>{children}</span>
    </div>
  );
}
