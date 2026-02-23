'use client';

import { useState } from 'react';
import type { TailoredChange, TailoredResume } from '../types';
import TraceDrawer from '../TraceDrawer';
import { apiPost, ApiError, API_BASE_URL } from '../../../../lib/api';
import { Check, Edit3, RefreshCw, X, HelpCircle, Download, ShieldCheck, ArrowRight, AlertCircle } from 'lucide-react';

interface Props {
  tailoredId: string;
  tailored: TailoredResume;
  initialChanges: TailoredChange[];
}

interface Busy {
  changeId: string;
  action: string;
}

interface ValidationReport {
  valid: boolean;
  checked_changes?: number;
  validated_at?: string;
  issues?: { type?: string; code?: string; text?: string; suggestion?: string; context?: string }[];
}

const STATUS_STYLES: Record<string, string> = {
  PENDING: 'bg-coral-soft text-coral font-mono font-bold',
  ACCEPTED: 'bg-proof text-proof-ink font-mono font-bold',
  EDITED: 'bg-brand-soft text-brand font-mono font-bold',
  REGENERATED: 'bg-brand-soft text-brand font-mono font-bold',
  REJECTED: 'bg-black/[0.06] text-muted font-mono font-semibold',
};

export default function ReviewPanel({ tailoredId, tailored, initialChanges }: Props) {
  const [changes, setChanges] = useState<TailoredChange[]>(initialChanges);
  const [busy, setBusy] = useState<Busy | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [editingId, setEditingId] = useState<string | null>(null);
  const [draft, setDraft] = useState('');
  const [openChange, setOpenChange] = useState<TailoredChange | null>(null);
  const [approveOpen, setApproveOpen] = useState(false);
  const [validation, setValidation] = useState<ValidationReport | null>(null);
  const [validationError, setValidationError] = useState<string | null>(null);

  const pending = changes.filter((c) => c.status === 'PENDING').length;
  const allResolved = pending === 0;

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
      setEditingId(null);
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

  const startEdit = (change: TailoredChange) => {
    setEditingId(change.id);
    setDraft(change.tailored_text);
  };

  async function handleExport(format: 'pdf' | 'docx' | 'json') {
    if (pending > 0) {
      setError(`Export blocked: ${pending} change(s) still pending review. Please accept, reject, or edit all changes first.`);
      void checkValidation();
      return;
    }
    try {
      window.open(`${API_BASE_URL}/tailored/${tailoredId}/export/${format}`, '_blank');
    } catch (err) {
      setError(err instanceof ApiError ? err.detail ?? err.message : String(err));
    }
  }

  return (
    <>
      {error && (
        <div className="mt-4 flex items-center gap-2 rounded-xl border border-coral/30 bg-coral-soft p-4 text-xs font-semibold text-coral">
          <AlertCircle className="h-4 w-4 shrink-0" />
          <span>{error}</span>
        </div>
      )}
      {notice && (
        <div className="mt-4 flex items-center gap-2 rounded-xl border border-proof/30 bg-proof/20 p-4 text-xs font-semibold text-proof-ink">
          <span>{notice}</span>
        </div>
      )}

      {/* Metrics Bar */}
      <div className="mt-6 grid grid-cols-1 sm:grid-cols-3 gap-4">
        <div className="rounded-2xl border border-black/10 bg-white p-5 shadow-2xs">
          <p className="font-mono text-[10px] font-bold uppercase tracking-widest text-faint">Score Before</p>
          <p className="font-editorial mt-1 text-3xl font-normal text-foreground">{tailored.score_before ?? '—'}</p>
        </div>
        <div className="rounded-2xl border border-black/10 bg-white p-5 shadow-2xs">
          <p className="font-mono text-[10px] font-bold uppercase tracking-widest text-brand">Score After</p>
          <div className="flex items-baseline gap-2">
            <p className="font-editorial mt-1 text-3xl font-normal text-brand">{tailored.score_after ?? '—'}</p>
            {tailored.score_before !== null && tailored.score_after !== null && (
              <span
                className={`font-mono text-xs font-bold ${
                  tailored.score_after >= tailored.score_before ? 'text-brand' : 'text-coral'
                }`}
              >
                {tailored.score_after >= tailored.score_before ? '+' : ''}
                {tailored.score_after - tailored.score_before} pts
              </span>
            )}
          </div>
        </div>
        <div className="rounded-2xl border border-black/10 bg-white p-5 shadow-2xs">
          <p className="font-mono text-[10px] font-bold uppercase tracking-widest text-faint">Pending Review</p>
          <p className="font-editorial mt-1 text-3xl font-normal text-foreground">{pending}</p>
        </div>
      </div>

      <div className="mt-8">
        <h2 className="text-xl font-bold tracking-[-0.03em] text-foreground">Tailored Rewrites</h2>
        <p className="mt-1 text-xs text-muted">
          {changes.length} bullet rewrite{changes.length === 1 ? '' : 's'} — inspect evidence tracing and approve changes.
        </p>
      </div>

      {changes.length === 0 ? (
        <p className="mt-4 text-xs font-mono text-faint">No tailored changes recorded.</p>
      ) : (
        <ul className="mt-4 space-y-4">
          {changes.map((change) => (
            <li key={change.id} className="rounded-[24px] border border-black/10 bg-white p-5 sm:p-6 shadow-xs">
              {/* Before / After Comparison Grid */}
              <div className="grid gap-4 sm:grid-cols-2">
                <div className="rounded-xl border border-black/10 bg-surface p-4">
                  <p className="font-mono text-[9px] font-bold uppercase tracking-widest text-faint mb-2">
                    01 / Original Bullet
                  </p>
                  <p className="text-xs text-muted line-through decoration-black/30 leading-relaxed">
                    {change.original_text}
                  </p>
                </div>

                <div className="relative overflow-hidden rounded-xl border-l-4 border-brand bg-brand-soft p-4">
                  <p className="font-mono text-[9px] font-bold uppercase tracking-widest text-brand mb-2">
                    02 / ATSDoctor Rewrite
                  </p>
                  {editingId === change.id ? (
                    <textarea
                      value={draft}
                      onChange={(e) => setDraft(e.target.value)}
                      rows={3}
                      className="w-full rounded-lg border border-black/15 bg-white p-2.5 text-xs text-foreground focus:border-brand focus:outline-none"
                    />
                  ) : (
                    <p className="text-xs font-semibold text-foreground leading-relaxed">
                      {change.tailored_text}
                    </p>
                  )}
                </div>
              </div>

              {/* Status Bar & Action Controls */}
              <div className="mt-4 flex flex-wrap items-center gap-2 pt-2 border-t border-black/10">
                <span
                  className={`rounded-full px-2.5 py-0.5 text-[9px] uppercase tracking-wider ${
                    STATUS_STYLES[change.status] ?? 'bg-black/[0.05] text-muted'
                  }`}
                >
                  {change.status}
                </span>
                <span className="rounded-md bg-black/[0.05] px-2 py-0.5 font-mono text-[10px] text-muted">
                  {change.claim_category}
                </span>
                {change.prompt_version && (
                  <span className="font-mono text-[10px] text-faint">
                    {change.prompt_version}
                  </span>
                )}

                {editingId === change.id && (
                  <div className="ml-auto flex items-center gap-2">
                    <button
                      onClick={() => act(change, 'edit', draft.trim())}
                      disabled={busy !== null || draft.trim() === ''}
                      className="inline-flex items-center gap-1 rounded-lg bg-brand px-3 py-1 text-xs font-semibold text-white hover:bg-brand-hover disabled:opacity-50"
                    >
                      Save Edit
                    </button>
                    <button
                      onClick={() => setEditingId(null)}
                      className="rounded-lg border border-black/15 px-3 py-1 text-xs font-semibold text-muted hover:text-foreground"
                    >
                      Cancel
                    </button>
                  </div>
                )}

                {editingId !== change.id && (
                  <>
                    {change.status === 'PENDING' && (
                      <div className="ml-auto flex items-center gap-1.5">
                        <button
                          onClick={() => act(change, 'accept')}
                          disabled={busy !== null}
                          className="inline-flex items-center gap-1 rounded-lg bg-proof px-3 py-1 text-xs font-bold text-proof-ink transition hover:bg-white border border-black/10"
                        >
                          <Check className="h-3 w-3" />
                          <span>Accept</span>
                        </button>
                        <button
                          onClick={() => act(change, 'reject')}
                          disabled={busy !== null}
                          className="rounded-lg border border-black/15 px-3 py-1 text-xs font-semibold text-muted hover:text-coral hover:bg-coral-soft"
                        >
                          Reject
                        </button>
                        <button
                          onClick={() => startEdit(change)}
                          disabled={busy !== null}
                          className="rounded-lg border border-black/15 px-3 py-1 text-xs font-semibold text-foreground hover:bg-surface"
                        >
                          Edit
                        </button>
                        <button
                          onClick={() => act(change, 'regenerate')}
                          disabled={busy !== null}
                          className="rounded-lg border border-black/15 px-3 py-1 text-xs font-semibold text-muted hover:text-brand hover:bg-brand-soft"
                        >
                          Re-generate
                        </button>
                      </div>
                    )}
                    {(change.status === 'EDITED' || change.status === 'REGENERATED' || change.status === 'ACCEPTED') && (
                      <button
                        onClick={() => startEdit(change)}
                        disabled={busy !== null}
                        className="ml-auto rounded-lg border border-black/15 px-3 py-1 text-xs font-semibold text-muted hover:text-foreground hover:bg-surface"
                      >
                        Re-edit
                      </button>
                    )}
                    <button
                      onClick={() => setOpenChange(change)}
                      className="inline-flex items-center gap-1 rounded-lg bg-brand-soft px-3 py-1 font-mono text-[10px] font-bold text-brand hover:bg-brand hover:text-white transition"
                    >
                      <HelpCircle className="h-3 w-3" />
                      <span>Why is this claim here?</span>
                    </button>
                  </>
                )}
              </div>
            </li>
          ))}
        </ul>
      )}

      {/* Export & Approval Bar */}
      <div className="mt-8 flex flex-col sm:flex-row items-stretch sm:items-center justify-between gap-4 rounded-[20px] bg-foreground p-5 text-white shadow-md">
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
          <button
            onClick={() => handleExport('pdf')}
            className="inline-flex items-center gap-1 rounded-lg border border-white/20 px-3 py-1.5 font-mono text-xs font-bold text-white hover:bg-white/10"
          >
            <Download className="h-3 w-3 text-proof" />
            <span>PDF</span>
          </button>
          <button
            onClick={() => handleExport('docx')}
            className="inline-flex items-center gap-1 rounded-lg border border-white/20 px-3 py-1.5 font-mono text-xs font-bold text-white hover:bg-white/10"
          >
            <Download className="h-3 w-3 text-proof" />
            <span>DOCX</span>
          </button>
          <button
            onClick={() => handleExport('json')}
            className="inline-flex items-center gap-1 rounded-lg border border-white/20 px-3 py-1.5 font-mono text-xs font-bold text-white hover:bg-white/10"
          >
            <span>JSON</span>
          </button>
        </div>
      </div>

      {/* Approval Modal Dialog */}
      {approveOpen && (
        <div
          className="fixed inset-0 z-50 flex items-center justify-center bg-foreground/50 backdrop-blur-xs p-4"
          onClick={() => setApproveOpen(false)}
        >
          <div
            className="w-full max-w-lg rounded-[28px] bg-white p-7 shadow-2xl border border-black/10"
            onClick={(e) => e.stopPropagation()}
          >
            <h3 className="text-xl font-bold tracking-tight text-foreground">Approve Tailored Resume</h3>

            {pending > 0 && (
              <div className="mt-4 rounded-xl border border-coral/30 bg-coral-soft p-4 text-xs text-coral font-medium">
                {pending} change{pending === 1 ? '' : 's'} still pending review. All changes must be accepted, rejected, or edited before approval — the Approve button is disabled until then.
              </div>
            )}

            {validationError && (
              <div className="mt-4 rounded-xl border border-coral/30 bg-coral-soft p-4 text-xs text-coral font-semibold">
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
                        <p className="font-bold text-coral">{issue.type} ({issue.code})</p>
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

      {openChange && (
        <TraceDrawer
          tailoredId={tailoredId}
          change={openChange}
          onClose={() => setOpenChange(null)}
        />
      )}
    </>
  );
}