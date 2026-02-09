'use client';

import { useEffect, useState } from 'react';
import { apiGet, ApiError, type Job, type JobDto } from '../../lib/api';
import { Badge } from '../ui';
import { X, ExternalLink, Briefcase, Tag } from 'lucide-react';

interface Props {
  job: Job | null;
  jobId: string;
  onClose: () => void;
  onJobUpdate: (job: Job) => void;
}

const IMPORTANCE_STYLES: Record<string, string> = {
  high: 'bg-coral-soft text-coral font-mono font-bold',
  medium: 'bg-brand-soft text-brand font-mono font-bold',
  low: 'bg-black/[0.05] text-muted font-mono font-semibold',
};

function parseStructured(raw: string | null): JobDto | null {
  if (!raw) return null;
  try {
    return JSON.parse(raw) as JobDto;
  } catch {
    return null;
  }
}

export default function JobDrawer({ job, jobId, onClose, onJobUpdate }: Props) {
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let active = true;
    let timer: ReturnType<typeof setInterval> | null = null;

    const load = (id: string) =>
      apiGet<Job>(`/jobs/${id}`)
        .then((next) => {
          if (!active) return;
          onJobUpdate(next);
          if (next.state === 'READY' || next.state === 'FAILED') {
            if (timer) {
              clearInterval(timer);
              timer = null;
            }
          }
        })
        .catch((err: unknown) => {
          if (!active) return;
          setError(err instanceof ApiError ? (err.detail ?? err.message) : String(err));
          if (timer) {
            clearInterval(timer);
            timer = null;
          }
        });

    void load(jobId);
    if (!job || job.state === 'CREATED' || job.state === 'EXTRACTING' || job.state === 'PARSING') {
      timer = setInterval(() => void load(jobId), 1500);
    }
    return () => {
      active = false;
      if (timer) clearInterval(timer);
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [jobId]);

  const dto = job ? parseStructured(job.structured_data) : null;
  const processing =
    !!job && (job.state === 'CREATED' || job.state === 'EXTRACTING' || job.state === 'PARSING');

  const skillBuckets: Record<string, { text: string; importance: string }[]> = { high: [], medium: [], low: [] };
  const experience: { text: string; importance: string }[] = [];
  const education: { text: string; importance: string }[] = [];
  for (const req of dto?.requirements ?? []) {
    const item = { text: req.text, importance: req.importance ?? 'medium' };
    if (req.type === 'experience') experience.push(item);
    else if (req.type === 'education') education.push(item);
    else skillBuckets[item.importance]?.push(item) ?? skillBuckets.medium.push(item);
  }

  return (
    <div className="fixed inset-0 z-50 flex justify-end bg-foreground/40 backdrop-blur-xs" onClick={onClose}>
      <aside
        className="h-full w-full max-w-2xl overflow-y-auto rounded-l-[32px] bg-white p-6 sm:p-8 shadow-[0_24px_70px_rgba(17,19,24,.2)]"
        onClick={(e) => e.stopPropagation()}
      >
        <div className="flex items-start justify-between gap-4 border-b border-black/10 pb-5">
          <div>
            <p className="font-mono text-xs font-semibold uppercase tracking-[0.2em] text-brand">
              02 / ROLE REQUIREMENTS
            </p>
            {job && (
              <>
                <h2 className="mt-1 text-2xl font-bold tracking-[-0.03em] text-foreground">
                  {job.title ?? job.source_filename ?? '(Untitled Job)'}
                </h2>
                {(job.company || job.location || job.seniority) && (
                  <p className="mt-1 text-sm font-semibold text-muted flex items-center gap-1.5">
                    <Briefcase className="h-3.5 w-3.5 text-brand" />
                    {[job.company, job.location, job.seniority].filter(Boolean).join(' · ')}
                  </p>
                )}
              </>
            )}
          </div>
          <div className="flex items-center gap-3">
            {job && <Badge state={job.state} />}
            <button
              onClick={onClose}
              className="rounded-full border border-black/15 p-1.5 text-muted hover:text-foreground hover:bg-surface transition"
            >
              <X className="h-4 w-4" />
            </button>
          </div>
        </div>

        {error && (
          <div className="mt-5 rounded-xl border border-coral/30 bg-coral-soft p-4 text-xs font-semibold text-coral">
            {error}
          </div>
        )}

        {job?.state === 'FAILED' && (
          <div className="mt-5 rounded-xl border border-coral/30 bg-coral-soft p-4 text-xs font-semibold text-coral">
            Processing failed: {job.error ?? 'Unknown error'}
          </div>
        )}

        {processing && (
          <p className="mt-5 font-mono text-xs text-brand font-semibold animate-pulse">
            Extracting role requirements ({job.state.toLowerCase()})… refreshing automatically.
          </p>
        )}

        {job?.state === 'READY' && !dto && (
          <p className="mt-5 text-sm text-faint">No structured requirements stored for this job.</p>
        )}

        {job?.state === 'READY' && dto && (
          <>
            {dto.job?.url && (
              <p className="mt-4 text-xs text-muted">
                Original Posting:{' '}
                <a
                  href={dto.job.url}
                  target="_blank"
                  rel="noreferrer"
                  className="font-semibold text-brand underline underline-offset-2 inline-flex items-center gap-1"
                >
                  <span>Link</span>
                  <ExternalLink className="h-3 w-3" />
                </a>
              </p>
            )}

            <section className="mt-6">
              <h3 className="text-sm font-bold text-foreground">Extracted Requirements</h3>
              <p className="mt-0.5 text-xs text-muted">
                {dto.requirements?.length ?? 0} requirements categorized by priority
              </p>

              <div className="mt-4 space-y-4">
                {(['high', 'medium', 'low'] as const).map((bucket) =>
                  skillBuckets[bucket].length > 0 ? (
                    <GroupBlock key={bucket} title={`Hard & Soft Skills — ${bucket} priority`} items={skillBuckets[bucket]} />
                  ) : null,
                )}
                {experience.length > 0 && (
                  <GroupBlock title="Domain Experience" items={experience} />
                )}
                {education.length > 0 && <GroupBlock title="Education" items={education} />}
                {dto.requirements?.length === 0 && (
                  <p className="text-sm text-faint italic">No requirements extracted.</p>
                )}
              </div>
            </section>

            {(dto.skills?.required?.length || dto.skills?.preferred?.length || dto.skills?.niceToHave?.length) && (
              <section className="mt-6 border-t border-black/10 pt-5">
                <h3 className="text-sm font-bold text-foreground mb-3">Skill Groups</h3>
                <div className="space-y-3">
                  {dto.skills?.required && dto.skills.required.length > 0 && (
                    <ChipGroup label="Required Skills" items={dto.skills.required} style="bg-foreground text-white" />
                  )}
                  {dto.skills?.preferred && dto.skills.preferred.length > 0 && (
                    <ChipGroup label="Preferred Skills" items={dto.skills.preferred} style="bg-brand-soft text-brand font-bold" />
                  )}
                  {dto.skills?.niceToHave && dto.skills.niceToHave.length > 0 && (
                    <ChipGroup label="Nice to Have" items={dto.skills.niceToHave} style="bg-proof text-proof-ink font-bold" />
                  )}
                </div>
              </section>
            )}

            {dto.keywords && dto.keywords.length > 0 && (
              <section className="mt-6 border-t border-black/10 pt-5">
                <h3 className="text-sm font-bold text-foreground mb-2 flex items-center gap-1.5">
                  <Tag className="h-3.5 w-3.5 text-brand" />
                  Role Keywords
                </h3>
                <div className="flex flex-wrap gap-1.5">
                  {dto.keywords.map((k) => (
                    <span key={k} className="rounded-md bg-brand-soft px-2.5 py-1 font-mono text-[11px] font-bold text-brand">
                      + {k}
                    </span>
                  ))}
                </div>
              </section>
            )}

            {dto.responsibilities && dto.responsibilities.length > 0 && (
              <section className="mt-6 border-t border-black/10 pt-5">
                <h3 className="text-sm font-bold text-foreground mb-2">Key Responsibilities</h3>
                <ul className="space-y-2">
                  {dto.responsibilities.map((r, i) => (
                    <li key={i} className="text-xs text-muted flex items-start gap-2">
                      <span className="text-brand font-bold">·</span>
                      <span className="flex-1">{r}</span>
                    </li>
                  ))}
                </ul>
              </section>
            )}
          </>
        )}

        {!job && !error && <p className="mt-5 font-mono text-xs text-faint animate-pulse">Loading job requirements…</p>}
      </aside>
    </div>
  );
}

function GroupBlock({ title, items }: { title: string; items: { text: string; importance: string }[] }) {
  return (
    <div>
      <p className="font-mono text-[10px] font-bold uppercase tracking-widest text-faint">{title}</p>
      <ul className="mt-2 space-y-2">
        {items.map((item, i) => (
          <li key={i} className="flex items-start gap-3 rounded-xl border border-black/10 bg-white p-3 shadow-2xs">
            <span className="flex-1 text-xs font-semibold text-foreground leading-snug">{item.text}</span>
            <span
              className={`rounded-full px-2.5 py-0.5 text-[9px] uppercase tracking-wider ${
                IMPORTANCE_STYLES[item.importance] ?? IMPORTANCE_STYLES.medium
              }`}
            >
              {item.importance}
            </span>
          </li>
        ))}
      </ul>
    </div>
  );
}

function ChipGroup({ label, items, style }: { label: string; items: string[]; style: string }) {
  return (
    <div>
      <p className="font-mono text-[10px] font-bold uppercase tracking-widest text-faint mb-1.5">{label}</p>
      <div className="flex flex-wrap gap-1.5">
        {items.map((s) => (
          <span key={s} className={`rounded-full px-3 py-1 font-mono text-[10px] uppercase tracking-wider ${style}`}>
            {s}
          </span>
        ))}
      </div>
    </div>
  );
}