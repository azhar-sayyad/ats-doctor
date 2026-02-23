'use client';

import { useCallback, useEffect, useRef, useState } from 'react';
import { apiGet, ApiError } from '../../../../lib/api';
import type { TailoredChange, TailoredResume } from '../types';
import ReviewPanel from './ReviewPanel';
import { AlertCircle } from 'lucide-react';

interface Props {
  tailoredId: string;
  initialTailored: TailoredResume;
  initialChanges: TailoredChange[];
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

export default function TailoredView({ tailoredId, initialTailored, initialChanges }: Props) {
  const [tailored, setTailored] = useState<TailoredResume>(initialTailored);
  const [changes, setChanges] = useState<TailoredChange[]>(initialChanges);
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

  const active = ACTIVE.has(tailored.state);
  const steps = [
    { key: 'QUEUED', label: 'Queued — preparing the tailoring run' },
    { key: 'GENERATING', label: 'Generating — rewriting bullets with AI' },
    { key: 'VALIDATING', label: 'Validating — fact-grounding every change' },
  ];
  const stepIndex = STATE_STEPS[tailored.state] ?? 0;

  return (
    <div className="mt-6">
      {/* Header Bar */}
      <div className="mb-8 flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between border-b border-black/10 pb-6">
        <div>
          <p className="font-mono text-xs font-semibold uppercase tracking-[0.2em] text-brand">
            04 / REVIEW & EXPORT
          </p>
          <h1 className="mt-1 text-3xl font-bold tracking-[-0.04em]">Tailored Resume</h1>
          <p className="mt-1 text-sm text-muted">
            Inspect evidence-backed rewrites, resolve every change, then approve and export.
          </p>
          <p className="mt-2 font-mono text-xs text-faint">{tailoredId}</p>
        </div>
        <span
          className={`self-start sm:self-center rounded-full px-3 py-1 text-[11px] uppercase tracking-wider ${
            STATE_STYLES[tailored.state] ?? 'bg-black/[0.05] text-muted font-mono font-semibold'
          }`}
        >
          {tailored.state}
        </span>
      </div>

      {tailored.error && (
        <div className="mb-6 flex items-center gap-2 rounded-xl border border-coral/30 bg-coral-soft p-4 text-sm text-coral font-medium">
          <AlertCircle className="h-4 w-4 shrink-0" />
          <span>{tailored.error}</span>
        </div>
      )}
      {error && (
        <div className="mb-6 flex items-center gap-2 rounded-xl border border-coral/30 bg-coral-soft p-4 text-sm text-coral font-medium">
          <AlertCircle className="h-4 w-4 shrink-0" />
          <span>{error}</span>
        </div>
      )}

      {active && (
        <div className="rounded-[20px] border border-black/10 bg-surface p-6 sm:p-8 shadow-2xs">
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
                      done
                        ? 'bg-proof text-proof-ink'
                        : current
                          ? 'bg-foreground text-white'
                          : 'bg-black/[0.06] text-faint'
                    }`}
                  >
                    {done ? '✓' : i + 1}
                  </span>
                  <span
                    className={`text-sm ${
                      done
                        ? 'text-faint line-through decoration-black/30'
                        : current
                          ? 'font-semibold text-foreground'
                          : 'text-faint'
                    }`}
                  >
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

      {!active && (
        <ReviewPanel
          tailoredId={tailoredId}
          tailored={tailored}
          initialChanges={changes}
          key={tailored.state}
        />
      )}
    </div>
  );
}