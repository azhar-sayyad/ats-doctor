'use client';

import type { Analytics, RequirementBucket } from '../../../../lib/resumeModel';
import { BookOpen, Check, TrendingUp, Sparkles, FileSearch, AlertTriangle, RotateCcw, ArrowRight } from 'lucide-react';

interface Props {
  analytics: Analytics;
  onOpenJd: () => void;
}

const COVERED = new Set(['matched', 'partial']);

export default function AnalyticsPanel({ analytics, onOpenJd }: Props) {
  return (
    <aside className="space-y-4 lg:sticky lg:top-20">
      {/* Role card */}
      <Card>
        <div className="flex items-start justify-between gap-3">
          <div className="min-w-0">
            <p className="font-mono text-[9px] font-bold uppercase tracking-widest text-brand">Target role</p>
            <h2 className="mt-1 truncate text-base font-bold tracking-tight text-foreground">
              {analytics.roleTitle ?? 'Unavailable'}
            </h2>
            <p className="truncate text-xs text-muted">
              {[analytics.company, analytics.jobLocation].filter(Boolean).join(' · ') || '—'}
            </p>
          </div>
          {analytics.seniority && (
            <span className="shrink-0 rounded-full bg-brand-soft px-2.5 py-0.5 font-mono text-[9px] font-bold uppercase tracking-wider text-brand">
              {analytics.seniority}
            </span>
          )}
        </div>
        <button
          onClick={onOpenJd}
          disabled={!analytics.jdRawText}
          className="mt-3 inline-flex w-full items-center justify-center gap-1.5 rounded-lg border border-black/15 px-3 py-2 text-xs font-semibold text-foreground transition hover:bg-surface disabled:opacity-40"
        >
          <BookOpen className="h-3.5 w-3.5 text-brand" />
          View job description
        </button>
      </Card>

      {/* Score trio */}
      <div className="grid grid-cols-3 gap-3">
        <MiniCard label="ATS readiness">
          <div className="flex items-baseline gap-1">
            <span className="font-editorial text-2xl font-normal text-foreground">{analytics.scoreBefore ?? '—'}</span>
            <ArrowRight className="h-3 w-3 text-faint" />
            <span className="font-editorial text-2xl font-normal text-brand">{analytics.scoreAfter ?? '—'}</span>
          </div>
          {analytics.scoreDelta !== null && (
            <span className={`mt-1 inline-block font-mono text-[10px] font-bold ${analytics.scoreDelta >= 0 ? 'text-brand' : 'text-coral'}`}>
              {analytics.scoreDelta >= 0 ? '+' : ''}
              {analytics.scoreDelta} pts
            </span>
          )}
        </MiniCard>
        <MiniCard label="Role coverage">
          <div className="flex items-baseline gap-1">
            <span className="font-editorial text-2xl font-normal text-foreground">{analytics.roleCoverage ?? '—'}</span>
            {analytics.roleCoverage !== null && <span className="text-xs font-bold text-muted">%</span>}
          </div>
          <span className="mt-1 inline-block font-mono text-[10px] text-faint">
            {analytics.mustHaves.covered + analytics.preferred.covered + analytics.niceToHave.covered} /{' '}
            {analytics.mustHaves.total + analytics.preferred.total + analytics.niceToHave.total} req
          </span>
        </MiniCard>
        <MiniCard label="Strength">
          <div className="flex items-baseline gap-1">
            <span className="font-editorial text-2xl font-normal text-foreground">{analytics.resumeStrength ?? '—'}</span>
            {analytics.resumeStrength !== null && <span className="text-xs font-bold text-muted">%</span>}
          </div>
          <span className="mt-1 inline-block font-mono text-[10px] text-faint">current score</span>
        </MiniCard>
      </div>

      {/* Requirement buckets */}
      <RequirementCard bucket={analytics.mustHaves} accent="coral" />
      <RequirementCard bucket={analytics.preferred} accent="brand" />
      {analytics.niceToHave.total > 0 && <RequirementCard bucket={analytics.niceToHave} accent="faint" />}

      {/* Keywords */}
      <Card>
        <SectionLabel icon={<FileSearch className="h-3.5 w-3.5 text-brand" />} title="Surfaced keywords" />
        {analytics.surfacedKeywords.length > 0 ? (
          <div className="mt-2 flex flex-wrap gap-1.5">
            {analytics.surfacedKeywords.map((k) => (
              <span key={k} className="rounded-md bg-proof/50 px-2 py-0.5 text-[10px] font-semibold text-foreground">
                {k}
              </span>
            ))}
          </div>
        ) : (
          <p className="mt-2 text-[11px] text-faint">No surfaced keywords detected.</p>
        )}
        {analytics.missingKeywords.length > 0 && (
          <>
            <SectionLabel icon={<AlertTriangle className="h-3.5 w-3.5 text-coral" />} title="Still missing" className="mt-4" />
            <div className="mt-2 flex flex-wrap gap-1.5">
              {analytics.missingKeywords.map((k) => (
                <span key={k} className="rounded-md border border-coral/30 bg-coral-soft px-2 py-0.5 text-[10px] font-semibold text-coral">
                  {k}
                </span>
              ))}
            </div>
          </>
        )}
      </Card>

      {/* What changed */}
      <Card>
        <SectionLabel icon={<TrendingUp className="h-3.5 w-3.5 text-brand" />} title="What changed" />
        <ul className="mt-2 space-y-1.5 text-[11px] text-foreground">
          <li className="flex items-center gap-2">
            <Check className="h-3 w-3 shrink-0 text-brand" />
            {analytics.changedCount} bullet{analytics.changedCount === 1 ? '' : 's'} rewritten
          </li>
          <li className="flex items-center gap-2">
            {analytics.summaryRewritten ? <Check className="h-3 w-3 shrink-0 text-brand" /> : <span className="w-3 shrink-0" />}
            {analytics.summaryRewritten ? 'Summary rewritten' : 'Summary unchanged'}
          </li>
          <li className="flex items-center gap-2">
            {analytics.reordered ? <Check className="h-3 w-3 shrink-0 text-brand" /> : <span className="w-3 shrink-0" />}
            {analytics.reordered ? 'Experience reordered (most relevant first)' : 'Experience order kept'}
          </li>
        </ul>
      </Card>

      {/* Gaps */}
      {(analytics.topGaps.length > 0 || analytics.topStrengths.length > 0) && (
        <Card>
          <SectionLabel icon={<Sparkles className="h-3.5 w-3.5 text-brand" />} title="Fit summary" />
          {analytics.topStrengths.length > 0 && (
            <div className="mt-2">
              <p className="font-mono text-[9px] font-bold uppercase tracking-widest text-faint">Strong points</p>
              <ul className="mt-1 space-y-1">
                {analytics.topStrengths.map((s, i) => (
                  <li key={i} className="flex items-start gap-1.5 text-[11px] text-foreground">
                    <span className="mt-[0.35em] h-1 w-1 shrink-0 rounded-full bg-proof" />
                    {s}
                  </li>
                ))}
              </ul>
            </div>
          )}
          {analytics.topGaps.length > 0 && (
            <div className="mt-3">
              <p className="font-mono text-[9px] font-bold uppercase tracking-widest text-faint">Watch-outs</p>
              <ul className="mt-1 space-y-1">
                {analytics.topGaps.map((g, i) => (
                  <li key={i} className="flex items-start gap-1.5 text-[11px] text-muted">
                    <span className="mt-[0.35em] h-1 w-1 shrink-0 rounded-full bg-coral" />
                    {g}
                  </li>
                ))}
              </ul>
            </div>
          )}
        </Card>
      )}

      <p className="px-1 font-mono text-[9px] uppercase tracking-widest text-faint">
        Metrics derived from score breakdown · {analytics.mustHaves.total + analytics.preferred.total + analytics.niceToHave.total} requirements
      </p>
    </aside>
  );
}

function RequirementCard({ bucket, accent }: { bucket: RequirementBucket; accent: 'coral' | 'brand' | 'faint' }) {
  const covered = bucket.covered;
  const pct = bucket.total > 0 ? Math.round((covered / bucket.total) * 100) : 0;
  const accentBg = accent === 'coral' ? 'bg-coral-soft text-coral' : accent === 'brand' ? 'bg-brand-soft text-brand' : 'bg-black/[0.06] text-muted';
  return (
    <Card>
      <div className="flex items-center justify-between gap-2">
        <SectionLabel title={bucket.label} />
        <span className="font-mono text-[10px] font-bold text-foreground">
          {covered}/{bucket.total}
        </span>
      </div>
      <div className="mt-2 h-1.5 w-full overflow-hidden rounded-full bg-black/[0.06]">
        <div className={`h-full rounded-full ${accent === 'coral' ? 'bg-coral' : accent === 'brand' ? 'bg-brand' : 'bg-black/25'}`} style={{ width: `${pct}%` }} />
      </div>
      {bucket.items.length > 0 && (
        <ul className="mt-2.5 space-y-1.5">
          {bucket.items.map((item, i) => (
            <li key={i} className="flex items-start gap-1.5">
              <span className={`mt-[0.3em] h-1.5 w-1.5 shrink-0 rounded-full ${COVERED.has(item.status) ? 'bg-proof' : 'bg-coral'}`} />
              <span className={`text-[11px] leading-snug ${COVERED.has(item.status) ? 'text-foreground' : 'text-muted'}`}>
                {item.text}
              </span>
            </li>
          ))}
        </ul>
      )}
      {bucket.items.length === 0 && <p className="mt-2 text-[11px] text-faint">No {bucket.label.toLowerCase()} extracted from the JD.</p>}
      <span className={`mt-2 inline-block rounded-full px-2 py-0.5 font-mono text-[9px] font-bold uppercase tracking-wider ${accentBg}`}>
        {pct}% covered
      </span>
    </Card>
  );
}

function SectionLabel({ icon, title, className }: { icon?: React.ReactNode; title: string; className?: string }) {
  return (
    <p className={`flex items-center gap-1.5 font-mono text-[10px] font-bold uppercase tracking-widest text-foreground ${className ?? ''}`}>
      {icon}
      {title}
    </p>
  );
}

function Card({ children }: { children: React.ReactNode }) {
  return <div className="rounded-2xl border border-black/10 bg-white p-4 shadow-2xs">{children}</div>;
}

function MiniCard({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <div className="rounded-2xl border border-black/10 bg-white p-3.5 shadow-2xs">
      <p className="font-mono text-[8px] font-bold uppercase tracking-widest text-faint">{label}</p>
      <div className="mt-1">{children}</div>
    </div>
  );
}
