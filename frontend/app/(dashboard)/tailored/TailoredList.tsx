'use client';

import { useEffect, useState } from 'react';
import Link from 'next/link';
import { getTailoredResumes, ApiError, type TailoredResume } from '../../../lib/api';
import { FileCheck, ArrowRight, AlertCircle } from 'lucide-react';

const STATE_STYLES: Record<string, string> = {
  READY: 'bg-proof text-proof-ink font-mono font-bold',
  NEEDS_REVIEW: 'bg-brand-soft text-brand font-mono font-bold',
  APPROVED: 'bg-foreground text-white font-mono font-bold',
  QUEUED: 'bg-black/[0.05] text-muted font-mono',
  GENERATING: 'bg-brand-soft text-brand font-mono animate-pulse',
  VALIDATING: 'bg-brand-soft text-brand font-mono animate-pulse',
};

export default function TailoredList() {
  const [resumes, setResumes] = useState<TailoredResume[] | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    getTailoredResumes()
      .then(setResumes)
      .catch((err) => {
        setError(err instanceof ApiError ? err.detail ?? err.message : String(err));
      });
  }, []);

  return (
    <div className="mt-8 space-y-10">
      {error && (
        <div className="flex items-center gap-2 rounded-xl border border-coral/30 bg-coral-soft p-4 text-sm text-coral font-medium">
          <AlertCircle className="h-4 w-4 shrink-0" />
          <span>{error}</span>
        </div>
      )}

      {/* Tailored Resumes List */}
      <section>
        <div className="mb-4 flex items-center justify-between">
          <h2 className="text-lg font-bold tracking-tight text-foreground">Generated Tailored Resumes</h2>
          <span className="font-mono text-xs font-semibold rounded-full border border-black/10 bg-surface px-3 py-1 text-muted shadow-2xs">
            {resumes === null ? '…' : resumes.length} tailored runs
          </span>
        </div>

        {resumes === null ? (
          <div className="py-8 text-center font-mono text-xs text-faint animate-pulse">
            Loading tailored resumes…
          </div>
        ) : resumes.length === 0 ? (
          <div className="rounded-[24px] border border-dashed border-black/20 p-12 text-center text-muted">
            <FileCheck className="mx-auto h-8 w-8 text-faint mb-3" />
            <p className="font-mono text-xs uppercase tracking-widest text-faint">No Tailored Resumes Yet</p>
            <p className="mt-1 text-sm">
              Run a match analysis on the Analyses page and click &quot;Tailor Resume&quot; to generate your first role-tailored resume.
            </p>
          </div>
        ) : (
          <ul className="space-y-4">
            {resumes.map((item) => (
              <li
                key={item.id}
                className="group rounded-[24px] border border-black/10 bg-white p-5 sm:p-6 transition duration-200 hover:border-black/20 shadow-xs"
              >
                <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
                  <div className="min-w-0 flex-1">
                    <div className="flex items-center gap-2">
                      <span className="font-mono text-xs font-bold text-foreground">
                        Run {item.id.slice(0, 8)}
                      </span>
                      <span
                        className={`rounded-full px-2.5 py-0.5 text-[10px] uppercase tracking-wider ${
                          STATE_STYLES[item.state] ?? 'bg-black/[0.05] text-muted'
                        }`}
                      >
                        {item.state}
                      </span>
                    </div>
                    <p className="mt-1.5 text-xs text-muted">
                      Created {new Date(item.created_at).toLocaleString()} · Analysis reference:{' '}
                      <span className="font-mono text-faint">{item.analysis_id.slice(0, 8)}</span>
                    </p>
                  </div>

                  <div className="flex flex-wrap items-center gap-4">
                    {/* Score Delta */}
                    <div className="flex items-center gap-2 rounded-xl bg-surface px-3 py-1.5 border border-black/10">
                      <div className="text-right">
                        <p className="font-mono text-[9px] uppercase tracking-widest text-faint">Score</p>
                        <p className="font-mono text-xs font-bold text-foreground">
                          {item.score_before ?? '—'} →{' '}
                          <span className="text-brand font-bold">{item.score_after ?? '—'}</span>
                        </p>
                      </div>
                      {item.score_before !== null && item.score_after !== null && (
                        <span
                          className={`rounded-full px-2 py-0.5 font-mono text-[10px] font-bold ${
                            item.score_after >= item.score_before
                              ? 'bg-proof text-proof-ink'
                              : 'bg-coral-soft text-coral'
                          }`}
                        >
                          {item.score_after >= item.score_before ? '+' : ''}
                          {item.score_after - item.score_before}
                        </span>
                      )}
                    </div>

                    <Link
                      href={`/tailored/${item.id}`}
                      className="inline-flex items-center gap-1.5 rounded-lg bg-foreground px-4 py-2 text-xs font-bold text-white transition hover:bg-brand hover:-translate-y-0.5"
                    >
                      <span>Review & Export</span>
                      <ArrowRight className="h-3.5 w-3.5" />
                    </Link>
                  </div>
                </div>
              </li>
            ))}
          </ul>
        )}
      </section>
    </div>
  );
}
