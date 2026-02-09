'use client';

import { useState } from 'react';
import type { Analysis, ScoreCategory } from '../../lib/api';
import { ScoreRing } from '../ui';
import { Sparkles, Check, AlertTriangle, ArrowRight, Layers } from 'lucide-react';

interface Props {
  analysis: Analysis;
  onTailor: () => void;
  tailoring: boolean;
}

const CATEGORY_LABELS: Record<string, string> = {
  skills: 'Skills Alignment',
  keywords: 'Role Keywords',
  responsibilities: 'Responsibilities',
  experience: 'Domain Experience',
  seniority: 'Seniority Match',
  education: 'Education Criteria',
};

const MATCH_STYLES: Record<string, string> = {
  matched: 'bg-proof text-proof-ink font-mono font-bold',
  partial: 'bg-brand-soft text-brand font-mono font-bold',
  unmatched: 'bg-coral-soft text-coral font-mono font-bold',
};

export default function AnalysisDetail({ analysis, onTailor, tailoring }: Props) {
  const [tab, setTab] = useState<'matches' | 'gaps'>('matches');

  if (analysis.state !== 'READY') {
    return (
      <div className="mt-4 rounded-xl border border-black/10 bg-surface p-4 text-xs font-mono text-muted">
        {analysis.state === 'FAILED' ? (
          <span className="font-semibold text-coral">Analysis failed: {analysis.error ?? 'Unknown error.'}</span>
        ) : (
          <span>Processing match algorithms ({analysis.state.toLowerCase()})…</span>
        )}
      </div>
    );
  }

  const breakdown = analysis.score_breakdown;
  const matches = analysis.matches ?? [];
  const gaps = analysis.gaps ?? [];
  const matchedCount = matches.filter((m) => m.status === 'matched').length;
  const partialCount = matches.filter((m) => m.status === 'partial').length;

  return (
    <div className="mt-5 rounded-[24px] border border-black/10 bg-white p-6 sm:p-7 shadow-xs">
      {/* Top Score Box */}
      <div className="flex flex-col sm:flex-row items-start sm:items-center gap-6 border-b border-black/10 pb-6">
        {analysis.score !== null && breakdown && (
          <ScoreRing score={analysis.score} size={110} />
        )}
        <div className="min-w-0 flex-1">
          <p className="font-mono text-[10px] font-bold uppercase tracking-[0.2em] text-brand">
            Job Match Signal
          </p>
          <p className="mt-1 font-editorial text-4xl font-normal text-foreground">
            {analysis.score === null ? '…' : `${analysis.score} / 100`}
          </p>
          <p className="mt-1 text-xs text-muted">
            Directional alignment index based on extracted keywords, requirements, and domain evidence.
          </p>
          {analysis.generation?.matching && (
            <p className="mt-1 font-mono text-[10px] text-faint">
              engine: {analysis.generation.matching.mode}
              {analysis.generation.scoring?.formula && ` · formula: ${analysis.generation.scoring.formula}`}
            </p>
          )}
        </div>
      </div>

      {/* Category Breakdown Bars */}
      {breakdown && (
        <div className="mt-6 space-y-4">
          <p className="font-mono text-[10px] font-bold uppercase tracking-widest text-faint">
            Category Breakdown
          </p>
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
            {Object.entries(CATEGORY_LABELS).map(([key, label]) => {
              const cat: ScoreCategory | undefined = breakdown[key as keyof typeof breakdown] as
                | ScoreCategory
                | undefined;
              if (!cat) return null;
              return (
                <div key={key} className="rounded-xl border border-black/10 bg-surface p-3.5">
                  <div className="flex items-center justify-between text-xs font-semibold text-foreground">
                    <span>{label}</span>
                    <span className="font-mono">
                      {cat.score}
                      <span className="text-faint text-[10px]"> ({Math.round(cat.weight * 100)}% wt)</span>
                    </span>
                  </div>
                  <div className="mt-2 h-2 w-full overflow-hidden rounded-full bg-black/[0.06]">
                    <div
                      className={`h-full rounded-full transition-all duration-500 ${
                        cat.score >= 80
                          ? 'bg-brand'
                          : cat.score >= 60
                            ? 'bg-proof'
                            : 'bg-coral'
                      }`}
                      style={{ width: `${cat.score}%` }}
                    />
                  </div>
                  <div className="mt-2 flex justify-between font-mono text-[10px] text-muted">
                    <span>{cat.matched.length} matched</span>
                    <span>{cat.missing.length} missing</span>
                  </div>
                </div>
              );
            })}
          </div>
        </div>
      )}

      {/* Tabs Bar */}
      <div className="mt-8 border-b border-black/10 pb-3">
        <div className="flex gap-2">
          <button
            onClick={() => setTab('matches')}
            className={`rounded-lg px-3 py-1.5 text-xs font-semibold transition ${
              tab === 'matches'
                ? 'bg-foreground text-white shadow-2xs'
                : 'text-muted hover:text-foreground hover:bg-surface'
            }`}
          >
            Matched Signals ({matchedCount + partialCount})
          </button>
          <button
            onClick={() => setTab('gaps')}
            className={`rounded-lg px-3 py-1.5 text-xs font-semibold transition ${
              tab === 'gaps'
                ? 'bg-foreground text-white shadow-2xs'
                : 'text-muted hover:text-foreground hover:bg-surface'
            }`}
          >
            Missing Gaps ({gaps.length})
          </button>
        </div>
      </div>

      {/* Tab Content */}
      {tab === 'matches' ? (
        matches.length === 0 ? (
          <p className="mt-4 text-xs font-mono text-faint">No requirement-level matches recorded.</p>
        ) : (
          <ul className="mt-4 max-h-80 space-y-3 overflow-y-auto pr-1">
            {matches.map((m) => (
              <li key={m.requirement_id} className="rounded-xl border border-black/10 bg-white p-4">
                <div className="flex items-start gap-3">
                  <span
                    className={`mt-0.5 shrink-0 rounded-full px-2.5 py-0.5 text-[9px] uppercase tracking-wider ${
                      MATCH_STYLES[m.status] ?? 'bg-black/[0.05] text-muted'
                    }`}
                  >
                    {m.status}
                  </span>
                  <div className="min-w-0 flex-1">
                    <p className="text-sm font-semibold text-foreground">{m.requirement_text}</p>
                    <p className="mt-0.5 font-mono text-[10px] text-faint">
                      {m.match_type}
                      {m.similarity !== null && m.similarity !== undefined && (
                        <> · similarity {Math.round(m.similarity * 100)}%</>
                      )}
                    </p>
                    {m.keywords_matched.length > 0 && (
                      <div className="mt-2 flex flex-wrap gap-1.5">
                        {m.keywords_matched.map((k) => (
                          <span key={k} className="rounded-md bg-brand-soft px-2 py-0.5 font-mono text-[10px] font-bold text-brand">
                            + {k}
                          </span>
                        ))}
                      </div>
                    )}
                  </div>
                </div>
                {m.evidence.length > 0 && (
                  <ul className="mt-3 space-y-1 border-t border-black/10 pt-3">
                    {m.evidence.map((e) => (
                      <li key={e.id} className="flex items-start gap-2 text-xs text-muted">
                        <span className="mt-1.5 h-1.5 w-1.5 shrink-0 rounded-full bg-brand" />
                        <span className="flex-1">{e.text}</span>
                      </li>
                    ))}
                  </ul>
                )}
              </li>
            ))}
          </ul>
        )
      ) : gaps.length === 0 ? (
        <p className="mt-4 text-xs font-mono text-brand font-semibold">
          ✓ No gaps detected — resume fully covers extracted requirements.
        </p>
      ) : (
        <ul className="mt-4 max-h-80 space-y-3 overflow-y-auto pr-1">
          {gaps.map((g, i) => (
            <li key={i} className="rounded-xl border border-black/10 bg-surface p-4">
              <div className="flex items-start gap-3">
                <span
                  className={`mt-0.5 shrink-0 rounded-full px-2.5 py-0.5 font-mono text-[9px] font-bold uppercase tracking-wider ${
                    g.type === 'requirement'
                      ? 'bg-coral-soft text-coral'
                      : 'bg-black/[0.06] text-muted'
                  }`}
                >
                  {g.type}
                </span>
                <p className="flex-1 text-sm font-semibold text-foreground">{g.text}</p>
              </div>
              {g.suggestions && g.suggestions.length > 0 && (
                <ul className="mt-2.5 space-y-1 border-t border-black/10 pt-2.5">
                  {g.suggestions.map((s) => (
                    <li key={s} className="text-xs text-muted flex items-center gap-1.5">
                      <span className="text-brand font-bold">→</span>
                      <span>{s}</span>
                    </li>
                  ))}
                </ul>
              )}
            </li>
          ))}
        </ul>
      )}

      {/* Tailor CTA Container */}
      {analysis.state === 'READY' && (
        <div className="mt-8 rounded-[20px] bg-foreground p-6 text-white shadow-lg">
          <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
            <div>
              <p className="font-mono text-xs font-semibold uppercase tracking-[0.2em] text-proof">
                Next Step Synthesis
              </p>
              <h3 className="mt-1 text-lg font-bold">Tailor resume with AI rewrites</h3>
              <p className="mt-1 text-xs text-white/60">
                Rewrite experience bullets toward this job with full evidence tracing and real-time review.
              </p>
            </div>
            <button
              onClick={onTailor}
              disabled={tailoring}
              className="inline-flex shrink-0 items-center justify-center gap-2 rounded-[10px] bg-proof px-5 py-3 text-xs font-bold text-proof-ink transition hover:bg-white hover:-translate-y-0.5 disabled:opacity-40"
            >
              <Sparkles className="h-4 w-4 text-brand" />
              <span>{tailoring ? 'Queuing run…' : 'Generate Tailored Resume'}</span>
              <ArrowRight className="h-3.5 w-3.5" />
            </button>
          </div>
        </div>
      )}
    </div>
  );
}