import type { ButtonHTMLAttributes, ReactNode } from 'react';
import { AlertCircle, CheckCircle2 } from 'lucide-react';

const JOB_STYLES: Record<string, string> = {
  READY: 'bg-proof text-proof-ink font-mono font-bold',
  FAILED: 'bg-coral-soft text-coral font-mono font-bold',
  CREATED: 'bg-brand-soft text-brand font-mono font-bold',
  PARSING: 'bg-brand-soft text-brand font-mono font-bold animate-pulse',
};

const ANALYSIS_STYLES: Record<string, string> = {
  READY: 'bg-proof text-proof-ink font-mono font-bold',
  FAILED: 'bg-coral-soft text-coral font-mono font-bold',
  QUEUED: 'bg-brand-soft text-brand font-mono font-bold',
  MATCHING: 'bg-brand-soft text-brand font-mono font-bold animate-pulse',
  SCORING: 'bg-brand-soft text-brand font-mono font-bold animate-pulse',
};

export function Badge({ state }: { state: string }) {
  return (
    <span
      className={`rounded-full px-3 py-1 text-[11px] uppercase tracking-wider ${
        JOB_STYLES[state] ?? 'bg-black/[0.05] text-muted font-mono font-semibold'
      }`}
    >
      {state}
    </span>
  );
}

export function AnalysisBadge({ state }: { state: string }) {
  return (
    <span
      className={`rounded-full px-3 py-1 text-[11px] uppercase tracking-wider ${
        ANALYSIS_STYLES[state] ?? 'bg-black/[0.05] text-muted font-mono font-semibold'
      }`}
    >
      {state}
    </span>
  );
}

export function ScoreBadge({ score }: { score: number | null }) {
  if (score === null || score === undefined) {
    return (
      <span className="rounded-full bg-black/[0.05] px-3 py-1 font-mono text-xs font-semibold text-faint">—</span>
    );
  }
  const style =
    score >= 80
      ? 'bg-brand-soft text-brand border border-brand/20'
      : score >= 60
        ? 'bg-proof text-proof-ink'
        : 'bg-coral-soft text-coral border border-coral/20';
  return (
    <span className={`rounded-full px-3 py-1 font-mono text-xs font-bold ${style}`}>{score}/100</span>
  );
}

export function ScoreRing({ score, size = 130 }: { score: number; size?: number }) {
  const strokeWidth = 8;
  const radius = (size - strokeWidth * 2) / 2;
  const circumference = 2 * Math.PI * radius;
  const offset = circumference * (1 - Math.min(score, 100) / 100);
  const strokeColor = score >= 80 ? '#3157f6' : score >= 60 ? '#cff52b' : '#ef4444';

  return (
    <div className="relative inline-flex items-center justify-center shrink-0">
      <svg width={size} height={size} viewBox={`0 0 ${size} ${size}`} className="shrink-0">
        <circle
          cx={size / 2}
          cy={size / 2}
          r={radius}
          fill="none"
          stroke="#f1f1f4"
          strokeWidth={strokeWidth}
        />
        <circle
          cx={size / 2}
          cy={size / 2}
          r={radius}
          fill="none"
          stroke={strokeColor}
          strokeWidth={strokeWidth}
          strokeLinecap="round"
          strokeDasharray={circumference}
          strokeDashoffset={offset}
          transform={`rotate(-90 ${size / 2} ${size / 2})`}
          className="transition-all duration-700 ease-out"
        />
      </svg>
      <div className="absolute inset-0 flex flex-col items-center justify-center text-center">
        <span className="font-editorial text-3xl font-normal leading-none text-foreground">{score}</span>
        <span className="font-mono text-[9px] uppercase tracking-widest text-faint mt-0.5">match</span>
      </div>
    </div>
  );
}

export const inputClass =
  'w-full rounded-xl border border-black/15 bg-white px-3.5 py-2.5 text-sm text-foreground placeholder:text-faint focus:border-brand focus:outline-none focus:ring-1 focus:ring-brand transition';

export const editorTextareaClass =
  'w-full resize-y rounded-xl border border-black/15 bg-[#0d0d0d] p-4 font-mono text-[11px] leading-relaxed text-emerald-300 placeholder:text-emerald-300/30 focus:border-brand focus:outline-none focus:ring-1 focus:ring-brand';

const TAILORED_STYLES: Record<string, string> = {
  READY: 'bg-proof text-proof-ink font-mono font-bold',
  NEEDS_REVIEW: 'bg-brand-soft text-brand font-mono font-bold',
  APPROVED: 'bg-foreground text-white font-mono font-bold',
  QUEUED: 'bg-brand-soft text-brand font-mono font-bold',
  GENERATING: 'bg-brand-soft text-brand font-mono font-bold animate-pulse',
  VALIDATING: 'bg-brand-soft text-brand font-mono font-bold animate-pulse',
};

export function TailoredBadge({ state }: { state: string }) {
  return (
    <span
      className={`rounded-full px-3 py-1 text-[11px] uppercase tracking-wider ${
        TAILORED_STYLES[state] ?? 'bg-black/[0.05] text-muted font-mono font-semibold'
      }`}
    >
      {state}
    </span>
  );
}

export function EmptyState({ label }: { label: string }) {
  return (
    <div className="rounded-2xl border border-dashed border-black/20 p-10 text-center">
      <p className="font-mono text-[10px] uppercase tracking-widest text-faint">{label}</p>
    </div>
  );
}

export function Alert({
  tone,
  size = 'md',
  className = '',
  children,
}: {
  tone: 'error' | 'success';
  size?: 'sm' | 'md';
  className?: string;
  children: ReactNode;
}) {
  const toneCls =
    tone === 'error'
      ? 'border-coral/30 bg-coral-soft text-coral'
      : 'border-proof/30 bg-proof/20 text-proof-ink';
  const sizeCls = size === 'sm' ? 'p-3 text-xs' : 'p-4 text-sm';
  const Icon = tone === 'error' ? AlertCircle : CheckCircle2;
  return (
    <div className={`flex items-start gap-2 rounded-xl border font-medium ${toneCls} ${sizeCls} ${className}`}>
      <Icon className={`mt-0.5 h-4 w-4 shrink-0 ${tone === 'success' ? 'text-brand' : ''}`} />
      <span>{children}</span>
    </div>
  );
}

type ToolbarButtonProps = ButtonHTMLAttributes<HTMLButtonElement> & {
  variant?: 'secondary' | 'primary';
  size?: 'sm' | 'md';
};

export function ToolbarButton({ variant = 'secondary', size = 'md', className = '', ...rest }: ToolbarButtonProps) {
  const base =
    'inline-flex items-center gap-1.5 whitespace-nowrap rounded-lg text-xs font-semibold transition disabled:opacity-40';
  const variantCls =
    variant === 'primary'
      ? 'bg-foreground text-white font-bold hover:bg-brand'
      : 'border border-black/15 text-muted hover:text-foreground';
  const sizeCls = size === 'sm' ? 'px-3 py-1.5' : 'px-4 py-2';
  return <button className={`${base} ${variantCls} ${sizeCls} ${className}`} {...rest} />;
}

export function Section({ title, subtitle, children }: { title: string; subtitle?: string; children: ReactNode }) {
  return (
    <section className="rounded-[24px] border border-black/10 bg-white p-6 sm:p-7 shadow-xs">
      <h2 className="text-lg font-bold tracking-tight text-foreground">{title}</h2>
      {subtitle && <p className="mt-1 text-sm text-muted">{subtitle}</p>}
      <div className="mt-4">{children}</div>
    </section>
  );
}