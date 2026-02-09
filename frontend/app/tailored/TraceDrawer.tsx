'use client';

import { useEffect, useState } from 'react';
import { apiGet, ApiError } from '../../lib/api';
import type { ChangeTrace, TailoredChange } from './types';
import { X, HelpCircle, CheckCircle2, ShieldAlert, FileText } from 'lucide-react';

interface Props {
  tailoredId: string;
  change: TailoredChange;
  onClose: () => void;
}

export default function TraceDrawer({ tailoredId, change, onClose }: Props) {
  const [trace, setTrace] = useState<ChangeTrace | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let active = true;
    apiGet<ChangeTrace>(`/tailored/${tailoredId}/changes/${change.id}/trace`)
      .then((data) => {
        if (active) {
          setTrace(data);
        }
      })
      .catch((err: unknown) => {
        if (!active) {
          return;
        }
        const detail = err instanceof ApiError ? err.detail : undefined;
        setError(detail ?? 'Could not load the trace.');
      });
    return () => {
      active = false;
    };
  }, [tailoredId, change.id]);

  return (
    <div className="fixed inset-0 z-50 flex justify-end bg-foreground/40 backdrop-blur-xs" onClick={onClose}>
      <aside
        className="h-full w-full max-w-lg overflow-y-auto rounded-l-[32px] bg-white p-6 sm:p-8 shadow-[0_24px_70px_rgba(17,19,24,.2)]"
        onClick={(e) => e.stopPropagation()}
      >
        <div className="flex items-start justify-between gap-4 border-b border-black/10 pb-5">
          <div>
            <p className="font-mono text-xs font-semibold uppercase tracking-[0.2em] text-brand flex items-center gap-1.5">
              <HelpCircle className="h-4 w-4" />
              Evidence Trace Chain
            </p>
            <p className="mt-1 text-lg font-bold tracking-[-0.03em] text-foreground">
              {change.claim_category} · <span className="font-mono text-xs text-muted">{change.status}</span>
            </p>
          </div>
          <button
            onClick={onClose}
            className="rounded-full border border-black/15 p-1.5 text-muted hover:text-foreground hover:bg-surface transition"
          >
            <X className="h-4 w-4" />
          </button>
        </div>

        {error && (
          <div className="mt-5 rounded-xl border border-coral/30 bg-coral-soft p-4 text-xs font-semibold text-coral">
            {error}
          </div>
        )}

        {!error && !trace && (
          <p className="mt-5 font-mono text-xs text-faint animate-pulse">Loading evidence trace…</p>
        )}

        {trace && (
          <div className="mt-5 space-y-6">
            <section className="rounded-xl border border-black/10 bg-surface p-4">
              <p className="font-mono text-[9px] font-bold uppercase tracking-widest text-faint mb-2">Rewrite Diff</p>
              <p className="text-xs text-muted line-through decoration-black/30 leading-relaxed mb-2">
                {trace.original_text}
              </p>
              <p className="text-xs font-semibold text-foreground leading-relaxed">
                {trace.tailored_text}
              </p>
              {trace.reason && (
                <p className="mt-2 font-mono text-[10px] text-brand">Reason: {trace.reason}</p>
              )}
            </section>

            <section>
              <h3 className="text-xs font-bold text-foreground mb-2 flex items-center gap-1.5">
                <ShieldAlert className="h-3.5 w-3.5 text-brand" />
                Validation Grounding Status
              </h3>
              {trace.validation_issues.length === 0 ? (
                <div className="rounded-xl border border-proof/30 bg-proof/20 p-3 text-xs font-bold text-proof-ink flex items-center gap-1.5">
                  <CheckCircle2 className="h-4 w-4 text-brand" />
                  <span>Verified: Clean grounding match to master resume facts.</span>
                </div>
              ) : (
                <ul className="space-y-2">
                  {trace.validation_issues.map((issue, i) => (
                    <li key={i} className="rounded-xl border border-coral/20 bg-coral-soft p-3 text-xs">
                      <p className="font-bold text-coral">
                        {issue.type} ({issue.code})
                      </p>
                      <p className="mt-1 text-muted">{issue.suggestion}</p>
                    </li>
                  ))}
                </ul>
              )}
            </section>

            <section>
              <h3 className="text-xs font-bold text-foreground mb-2 flex items-center gap-1.5">
                <FileText className="h-3.5 w-3.5 text-brand" />
                Source Master Evidence
              </h3>
              {trace.evidence.length === 0 ? (
                <p className="text-xs text-faint italic">No direct linked evidence row (summary rewrite).</p>
              ) : (
                <ul className="space-y-2">
                  {trace.evidence.map((row) => (
                    <li key={row.id} className="rounded-xl border border-black/10 bg-white p-3.5 shadow-2xs">
                      <p className="text-xs font-semibold text-foreground leading-snug">{row.text}</p>
                      <p className="mt-1 font-mono text-[10px] text-faint">
                        {row.section}
                        {trace.section && ` — ${trace.section.company}${trace.section.title ? ' (' + trace.section.title + ')' : ''}`}
                      </p>
                      {row.source_refs.length > 0 && (
                        <div className="mt-2 flex flex-wrap items-center gap-1">
                          <span className="font-mono text-[9px] text-faint">refs:</span>
                          {row.source_refs.map((ref) => (
                            <span key={ref} className="rounded-md bg-brand-soft px-1.5 py-0.5 font-mono text-[9px] font-bold text-brand">
                              {ref.slice(0, 8)}
                            </span>
                          ))}
                        </div>
                      )}
                    </li>
                  ))}
                </ul>
              )}
            </section>

            {trace.bullet && (
              <section className="rounded-xl border border-black/10 bg-white p-4">
                <h3 className="text-xs font-bold text-foreground mb-1">Tailored Bullet Reference</h3>
                <p className="font-mono text-[9px] text-faint mb-2">ID: {trace.bullet.original_id}</p>
                <p className="text-xs text-foreground font-medium">{trace.bullet.tailored_text}</p>
              </section>
            )}
          </div>
        )}
      </aside>
    </div>
  );
}