'use client';

import { useCallback, useEffect, useRef, useState } from 'react';
import { apiGet, ApiError } from '../../../lib/api';
import type { TailoredChange, TailoredResume } from '../types';
import ReviewPanel from './ReviewPanel';

interface Props {
  tailoredId: string;
  initialTailored: TailoredResume;
  initialChanges: TailoredChange[];
}

const ACTIVE = new Set(['QUEUED', 'GENERATING', 'VALIDATING']);

const STATE_STYLES: Record<string, string> = {
  READY: 'bg-emerald-100 text-emerald-800',
  NEEDS_REVIEW: 'bg-amber-100 text-amber-800',
  APPROVED: 'bg-slate-900 text-white',
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
    <div className="mt-4">
      <div className="flex items-start justify-between gap-4">
        <div>
          <h1 className="text-2xl font-bold">Tailored resume</h1>
          <p className="mt-1 font-mono text-xs text-slate-500">{tailoredId}</p>
        </div>
        <span
          className={`rounded-full px-3 py-1 text-xs font-medium ${
            STATE_STYLES[tailored.state] ?? 'bg-slate-100 text-slate-700'
          }`}
        >
          {tailored.state}
        </span>
      </div>

      {tailored.error && (
        <div className="mt-6 rounded-md border border-red-200 bg-red-50 p-3 text-sm text-red-700">
          {tailored.error}
        </div>
      )}
      {error && (
        <div className="mt-6 rounded-md border border-red-200 bg-red-50 p-3 text-sm text-red-700">
          {error}
        </div>
      )}

      {active && (
        <div className="mt-6 rounded-lg border border-slate-200 bg-white p-8 shadow-sm">
          <div className="flex items-center gap-3">
            <span className="h-3 w-3 animate-ping rounded-full bg-emerald-500" />
            <h2 className="text-lg font-semibold text-slate-900">Generation in progress</h2>
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
                        ? 'bg-emerald-100 text-emerald-700'
                        : current
                          ? 'bg-slate-900 text-white'
                          : 'bg-slate-100 text-slate-400'
                    }`}
                  >
                    {done ? '✓' : i + 1}
                  </span>
                  <span
                    className={`text-sm ${
                      done
                        ? 'text-slate-500 line-through decoration-slate-300'
                        : current
                          ? 'font-medium text-slate-900'
                          : 'text-slate-400'
                    }`}
                  >
                    {step.label}
                    {current && (
                      <span className="ml-2 inline-block h-3 w-3 animate-spin rounded-full border-2 border-slate-400 border-t-transparent align-middle" />
                    )}
                  </span>
                </li>
              );
            })}
          </ol>
          <p className="mt-6 text-sm text-slate-500">Refreshing automatically — no action needed.</p>
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