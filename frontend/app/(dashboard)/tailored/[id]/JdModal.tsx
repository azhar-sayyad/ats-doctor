'use client';

import { useState } from 'react';
import { X, Building2, MapPin, Briefcase } from 'lucide-react';
import type { Analytics } from '../../../../lib/resumeModel';

interface Props {
  analytics: Analytics;
  onClose: () => void;
}

const IMPORTANCE_LABEL: Record<string, string> = {
  high: 'Must-have',
  medium: 'Preferred',
  low: 'Nice-to-have',
};

const IMPORTANCE_STYLE: Record<string, string> = {
  high: 'bg-coral-soft text-coral',
  medium: 'bg-brand-soft text-brand',
  low: 'bg-black/[0.06] text-muted',
};

export default function JdModal({ analytics, onClose }: Props) {
  const [showRaw, setShowRaw] = useState(false);
  const jd = analytics.jdRequirements;

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center bg-foreground/50 p-4 backdrop-blur-xs"
      onClick={onClose}
    >
      <div
        className="flex max-h-[85vh] w-full max-w-2xl flex-col rounded-[28px] border border-black/10 bg-white shadow-2xl"
        onClick={(e) => e.stopPropagation()}
      >
        <div className="flex items-start justify-between gap-4 border-b border-black/10 p-6 pb-4">
          <div>
            <p className="font-mono text-[10px] font-bold uppercase tracking-widest text-brand">Job description</p>
            <h3 className="mt-1 text-xl font-bold tracking-tight text-foreground">
              {analytics.roleTitle ?? 'Target role'}
            </h3>
            <div className="mt-1.5 flex flex-wrap items-center gap-x-3 gap-y-1 text-xs text-muted">
              {analytics.company && (
                <span className="inline-flex items-center gap-1">
                  <Building2 className="h-3 w-3" /> {analytics.company}
                </span>
              )}
              {analytics.jobLocation && (
                <span className="inline-flex items-center gap-1">
                  <MapPin className="h-3 w-3" /> {analytics.jobLocation}
                </span>
              )}
              {analytics.seniority && (
                <span className="inline-flex items-center gap-1">
                  <Briefcase className="h-3 w-3" /> {analytics.seniority}
                </span>
              )}
            </div>
          </div>
          <button onClick={onClose} className="rounded-lg p-2 text-faint transition hover:bg-surface hover:text-foreground">
            <X className="h-5 w-5" />
          </button>
        </div>

        <div className="min-h-0 flex-1 overflow-y-auto p-6">
          {jd.length > 0 ? (
            <ul className="space-y-2">
              {jd.map((req, i) => (
                <li key={i} className="flex items-start justify-between gap-3 rounded-xl border border-black/10 bg-surface px-3 py-2.5">
                  <span className="text-xs leading-snug text-foreground">{req.text}</span>
                  <span className={`shrink-0 rounded-full px-2 py-0.5 font-mono text-[9px] font-bold uppercase tracking-wider ${IMPORTANCE_STYLE[req.importance] ?? 'bg-black/[0.06] text-muted'}`}>
                    {IMPORTANCE_LABEL[req.importance] ?? req.importance}
                  </span>
                </li>
              ))}
            </ul>
          ) : (
            <p className="text-xs text-faint">No structured requirements were extracted for this job.</p>
          )}

          {analytics.jdRawText && (
            <div className="mt-5">
              <button
                onClick={() => setShowRaw((s) => !s)}
                className="font-mono text-[10px] font-bold uppercase tracking-widest text-brand hover:underline"
              >
                {showRaw ? 'Hide raw text' : 'View raw text'}
              </button>
              {showRaw && (
                <pre className="mt-2 max-h-72 overflow-y-auto whitespace-pre-wrap rounded-xl border border-black/10 bg-surface p-4 font-mono text-[10px] leading-relaxed text-muted">
                  {analytics.jdRawText}
                </pre>
              )}
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
