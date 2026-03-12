'use client';

import { useMemo, useState } from 'react';
import type { TailoredChange, ReviewBusy } from '../types';
import type { DocModel, DocSection, DocBullet } from '../../../../lib/resumeModel';
import { rewrittenBullets } from '../../../../lib/resumeModel';
import DiffText from '../../../../components/DiffText';
import { Check, Edit3, RefreshCw, HelpCircle, FileText, GitCompareArrows, PenLine } from 'lucide-react';

interface Props {
  doc: DocModel | null;
  busy: ReviewBusy | null;
  onAct: (change: TailoredChange, action: string, newText?: string) => void;
  onTrace: (change: TailoredChange) => void;
}

type Tab = 'document' | 'changes' | 'edit';

const STATUS_STYLES: Record<string, string> = {
  PENDING: 'bg-coral-soft text-coral font-mono font-bold',
  ACCEPTED: 'bg-proof text-proof-ink font-mono font-bold',
  EDITED: 'bg-brand-soft text-brand font-mono font-bold',
  REGENERATED: 'bg-brand-soft text-brand font-mono font-bold',
  REJECTED: 'bg-black/[0.06] text-muted font-mono font-semibold',
};

const TABS: { key: Tab; label: string; icon: typeof FileText }[] = [
  { key: 'document', label: 'Document', icon: FileText },
  { key: 'changes', label: 'Changes', icon: GitCompareArrows },
  { key: 'edit', label: 'Edit', icon: PenLine },
];

export default function DocumentViewer({ doc, busy, onAct, onTrace }: Props) {
  const [tab, setTab] = useState<Tab>('document');
  const [editingId, setEditingId] = useState<string | null>(null);
  const [draft, setDraft] = useState('');
  const [edits, setEdits] = useState<Record<string, string>>({});

  const rewritten = rewrittenBullets(doc);
  const changeCount = rewritten.length;

  return (
    <div className="rounded-[24px] border border-black/10 bg-white shadow-xs">
      {/* Tab bar */}
      <div className="flex items-center gap-1 border-b border-black/10 px-4 pt-3">
        {TABS.map(({ key, label, icon: Icon }) => (
          <button
            key={key}
            onClick={() => setTab(key)}
            className={`inline-flex items-center gap-1.5 rounded-t-xl px-4 py-2.5 text-xs font-semibold transition ${
              tab === key
                ? 'bg-surface text-foreground border border-b-0 border-black/10 -mb-px'
                : 'text-faint hover:text-foreground'
            }`}
          >
            <Icon className="h-3.5 w-3.5" />
            {label}
            {key === 'changes' && changeCount > 0 && (
              <span
                className={`ml-0.5 rounded-full px-1.5 py-0.5 text-[9px] font-bold ${
                  tab === key ? 'bg-brand text-white' : 'bg-black/[0.07] text-faint'
                }`}
              >
                {changeCount}
              </span>
            )}
          </button>
        ))}
      </div>

      <div className="p-4 sm:p-6">
        {tab === 'document' && <DocumentPage doc={doc} />}
        {tab === 'changes' && (
          <ChangesList
            rewritten={rewritten}
            busy={busy}
            onAct={onAct}
            onTrace={onTrace}
            editingId={editingId}
            setEditingId={setEditingId}
            draft={draft}
            setDraft={setDraft}
          />
        )}
        {tab === 'edit' && (
          <EditList
            doc={doc}
            rewritten={rewritten}
            busy={busy}
            onAct={onAct}
            edits={edits}
            setEdits={setEdits}
          />
        )}
      </div>
    </div>
  );
}

// ---------------------------------------------------------------------------
// Document tab — clean A4 preview of the effective (final) text.
// ---------------------------------------------------------------------------

function DocumentPage({ doc }: { doc: DocModel | null }) {
  if (!doc) {
    return <EmptyState label="No resume document available." />;
  }
  const contact = [doc.basics.email, doc.basics.phone, doc.basics.location, doc.basics.linkedin, doc.basics.github]
    .filter(Boolean)
    .join('  ·  ');

  return (
    <div className="a4-scroll mx-auto w-full max-w-[620px] aspect-[210/297] overflow-y-auto rounded-lg border border-black/10 bg-white shadow-lg">
      <div className="flex min-h-full flex-col px-10 py-9">
        {doc.basics.name && (
          <h1 className="font-editorial text-2xl font-semibold tracking-tight text-foreground">
            {doc.basics.name}
          </h1>
        )}
        {contact && <p className="mt-1 font-mono text-[9px] uppercase tracking-widest text-muted">{contact}</p>}

        <div className="mt-5 space-y-4">
          {doc.summary && (
            <div>
              <SectionRule />
              <p className="text-[11px] leading-relaxed text-foreground">{doc.summary.effective}</p>
            </div>
          )}

          {doc.skills.length > 0 && (
            <div>
              <SectionRule label="Skills" />
              <div className="flex flex-wrap gap-1.5">
                {doc.skills.map((skill, i) => (
                  <span
                    key={i}
                    className="rounded-md border border-black/10 bg-surface px-2 py-0.5 text-[9px] font-mono text-foreground"
                  >
                    {skill.name}
                  </span>
                ))}
              </div>
            </div>
          )}

          {doc.sections.length > 0 && (
            <div>
              <SectionRule label="Experience" />
              <div className="space-y-4">
                {doc.sections.map((section, i) => (
                  <ExperienceBlock key={i} section={section} />
                ))}
              </div>
            </div>
          )}

          {doc.projects.length > 0 && (
            <div>
              <SectionRule label="Projects" />
              <div className="space-y-2">
                {doc.projects.map((project, i) => (
                  <div key={i}>
                    <p className="text-[11px] font-bold text-foreground">{project.name}</p>
                    {project.description && (
                      <p className="text-[11px] leading-relaxed text-muted">{project.description}</p>
                    )}
                    {(project.technologies ?? []).length > 0 && (
                      <p className="mt-0.5 font-mono text-[8px] uppercase tracking-wider text-faint">
                        {project.technologies!.join(', ')}
                      </p>
                    )}
                  </div>
                ))}
              </div>
            </div>
          )}

          {doc.education.length > 0 && (
            <div>
              <SectionRule label="Education" />
              <div className="space-y-1.5">
                {doc.education.map((edu, i) => (
                  <p key={i} className="text-[11px] text-foreground">
                    <span className="font-semibold">{[edu.degree, edu.field].filter(Boolean).join(' — ')}</span>
                    {edu.institution ? `, ${edu.institution}` : ''}
                    {[edu.start, edu.end].filter(Boolean).length > 0 && (
                      <span className="text-muted"> · {[edu.start, edu.end].filter(Boolean).join(' – ')}</span>
                    )}
                  </p>
                ))}
              </div>
            </div>
          )}
        </div>
      </div>
    </div>
  );
}

function SectionRule({ label }: { label?: string }) {
  return (
    <div className="mb-1.5 flex items-center gap-2">
      {label && (
        <span className="font-mono text-[8px] font-bold uppercase tracking-[0.2em] text-brand">{label}</span>
      )}
      <div className="h-px flex-1 bg-black/15" />
    </div>
  );
}

function ExperienceBlock({ section }: { section: DocSection }) {
  const dates = [section.start, section.end].filter(Boolean).join(' – ');
  return (
    <div>
      <div className="flex items-baseline justify-between gap-3">
        <p className="text-[11px] font-bold text-foreground">
          {[section.title, section.company].filter(Boolean).join(' · ')}
          {section.location ? <span className="font-normal text-muted"> — {section.location}</span> : null}
        </p>
        {dates && <p className="shrink-0 font-mono text-[8px] text-faint">{dates}</p>}
      </div>
      <ul className="mt-1 space-y-1">
        {section.bullets.map((bullet, j) => (
          <li key={j} className="flex gap-1.5 text-[10.5px] leading-relaxed">
            <span className="mt-[0.35em] h-[3px] w-[3px] shrink-0 rounded-full bg-foreground/70" />
            <span className="text-foreground">{bullet.effectiveText}</span>
          </li>
        ))}
      </ul>
    </div>
  );
}

// ---------------------------------------------------------------------------
// Changes tab — review controls for every rewritten bullet.
// ---------------------------------------------------------------------------

function ChangesList({
  rewritten,
  busy,
  onAct,
  onTrace,
  editingId,
  setEditingId,
  draft,
  setDraft,
}: {
  rewritten: { section: DocSection; bullet: DocBullet }[];
  busy: ReviewBusy | null;
  onAct: (change: TailoredChange, action: string, newText?: string) => void;
  onTrace: (change: TailoredChange) => void;
  editingId: string | null;
  setEditingId: (id: string | null) => void;
  draft: string;
  setDraft: (v: string) => void;
}) {
  if (rewritten.length === 0) {
    return <EmptyState label="No tailored changes yet. This tab unlocks once the run finishes." />;
  }

  let lastSection: DocSection | null = null;
  return (
    <div className="space-y-4">
      {rewritten.map(({ section, bullet }) => {
        const change = bullet.change;
        const newSection = lastSection !== section;
        lastSection = section;
        if (!change) return null;
        return (
          <div key={change.id}>
            {newSection && (
              <p className="mb-2 font-mono text-[10px] font-bold uppercase tracking-widest text-brand">
                {section.company}
                {section.title ? ` · ${section.title}` : ''}
              </p>
            )}
            <div className="rounded-2xl border border-black/10 bg-surface p-4">
              {isNewBullet(bullet) && (
                <span className="mb-2 inline-block rounded-full bg-brand text-white px-2.5 py-0.5 text-[9px] font-bold uppercase tracking-wider">
                  New bullet
                </span>
              )}
              <div className={`grid gap-3 sm:grid-cols-2 ${isNewBullet(bullet) ? 'mt-2' : ''}`}>
                <div className="rounded-xl bg-white p-3">
                  <p className="mb-1.5 font-mono text-[8px] font-bold uppercase tracking-widest text-faint">
                    01 / Original
                  </p>
                  {isNewBullet(bullet) ? (
                    <p className="text-xs italic leading-relaxed text-faint">— none (brand-new bullet) —</p>
                  ) : (
                    <p className="text-xs leading-relaxed text-muted line-through decoration-black/30">
                      {bullet.originalText}
                    </p>
                  )}
                </div>
                <div className="rounded-xl border-l-4 border-brand bg-white p-3">
                  <p className="mb-1.5 font-mono text-[8px] font-bold uppercase tracking-widest text-brand">
                    02 / Rewrite
                  </p>
                  {editingId === change.id ? (
                    <textarea
                      value={draft}
                      onChange={(e) => setDraft(e.target.value)}
                      rows={3}
                      className="w-full rounded-lg border border-black/15 bg-white p-2.5 text-xs text-foreground focus:border-brand focus:outline-none"
                    />
                  ) : (
                    <p className="text-xs font-semibold leading-relaxed text-foreground">
                      <DiffText original={bullet.originalText} tailored={bullet.effectiveText} />
                    </p>
                  )}
                </div>
              </div>

              <div className="mt-3 flex flex-wrap items-center gap-2 border-t border-black/10 pt-3">
                <span className={`rounded-full px-2.5 py-0.5 text-[9px] uppercase tracking-wider ${STATUS_STYLES[bullet.status ?? ''] ?? 'bg-black/[0.05] text-muted'}`}>
                  {bullet.status ?? '—'}
                </span>
                {bullet.claimCategory && (
                  <span className="rounded-md bg-black/[0.05] px-2 py-0.5 font-mono text-[10px] text-muted">
                    {bullet.claimCategory}
                  </span>
                )}
                {bullet.reason && <span className="max-w-[220px] truncate font-mono text-[10px] text-faint">{bullet.reason}</span>}

                {editingId === change.id ? (
                  <div className="ml-auto flex items-center gap-2">
                    <button
                      onClick={() => onAct(change, 'edit', draft.trim())}
                      disabled={busy !== null || draft.trim() === ''}
                      className="inline-flex items-center gap-1 rounded-lg bg-brand px-3 py-1 text-xs font-semibold text-white hover:bg-brand-hover disabled:opacity-50"
                    >
                      <Check className="h-3 w-3" /> Save
                    </button>
                    <button
                      onClick={() => setEditingId(null)}
                      className="rounded-lg border border-black/15 px-3 py-1 text-xs font-semibold text-muted hover:text-foreground"
                    >
                      Cancel
                    </button>
                  </div>
                ) : (
                  <div className="ml-auto flex items-center gap-1.5">
                    {bullet.status === 'PENDING' && (
                      <>
                        <ActionBtn onClick={() => onAct(change, 'accept')} disabled={busy !== null} className="bg-proof text-proof-ink hover:bg-white">
                          <Check className="h-3 w-3" /> Accept
                        </ActionBtn>
                        <ActionBtn onClick={() => onAct(change, 'reject')} disabled={busy !== null} className="hover:text-coral hover:bg-coral-soft">
                          Reject
                        </ActionBtn>
                        <ActionBtn onClick={() => onAct(change, 'regenerate')} disabled={busy !== null} className="hover:text-brand hover:bg-brand-soft">
                          <RefreshCw className="h-3 w-3" /> Re-generate
                        </ActionBtn>
                      </>
                    )}
                    <ActionBtn
                      onClick={() => {
                        setEditingId(change.id);
                        setDraft(bullet.effectiveText);
                      }}
                      disabled={busy !== null}
                      className="hover:text-foreground hover:bg-white"
                    >
                      <Edit3 className="h-3 w-3" /> Re-edit
                    </ActionBtn>
                    <button
                      onClick={() => onTrace(change)}
                      className="inline-flex items-center gap-1 rounded-lg bg-brand-soft px-3 py-1 font-mono text-[10px] font-bold text-brand hover:bg-brand hover:text-white transition"
                    >
                      <HelpCircle className="h-3 w-3" />
                      <span>Why?</span>
                    </button>
                  </div>
                )}
              </div>
            </div>
          </div>
        );
      })}
    </div>
  );
}

function ActionBtn({
  onClick,
  disabled,
  className,
  children,
}: {
  onClick: () => void;
  disabled: boolean;
  className: string;
  children: React.ReactNode;
}) {
  return (
    <button
      onClick={onClick}
      disabled={disabled}
      className={`inline-flex items-center gap-1 rounded-lg border border-black/15 px-3 py-1 text-xs font-semibold text-muted transition disabled:opacity-40 ${className}`}
    >
      {children}
    </button>
  );
}

// ---------------------------------------------------------------------------
// Edit tab — live-preview bullet editor (reuses reviewChange 'edit').
// ---------------------------------------------------------------------------

function EditList({
  doc,
  rewritten,
  busy,
  onAct,
  edits,
  setEdits,
}: {
  doc: DocModel | null;
  rewritten: { section: DocSection; bullet: DocBullet }[];
  busy: ReviewBusy | null;
  onAct: (change: TailoredChange, action: string, newText?: string) => void;
  edits: Record<string, string>;
  setEdits: (v: Record<string, string>) => void;
}) {
  const previewDoc = useMemo(() => {
    if (!doc) return null;
    const preview = structuredClone(doc);
    const byId = new Map<string, string>();
    for (const [id, text] of Object.entries(edits)) {
      if (text.trim() !== '') byId.set(id, text);
    }
    for (const section of preview.sections) {
      for (const bullet of section.bullets) {
        const change = bullet.change;
        if (change && byId.has(change.id)) {
          bullet.effectiveText = byId.get(change.id) ?? bullet.effectiveText;
        }
      }
    }
    return preview;
  }, [doc, edits]);

  const dirtyCount = rewritten.filter(({ bullet }) => {
    const change = bullet.change;
    return !!change && edits[change.id] !== undefined && edits[change.id] !== bullet.effectiveText;
  }).length;

  if (rewritten.length === 0) {
    return <EmptyState label="No bullets to edit yet. Bullets appear here after the tailoring run." />;
  }

  return (
    <div className="grid grid-cols-1 gap-5 xl:grid-cols-[minmax(0,1fr)_380px]">
      <div className="space-y-3">
        <p className="text-xs text-muted">
          Edit any rewritten bullet directly — the preview updates live. Each save re-runs the
          fact-grounding validation.
        </p>
        {rewritten.map(({ section, bullet }) => {
          const change = bullet.change;
          if (!change) return null;
          const committed = bullet.effectiveText;
          const draft = edits[change.id] ?? committed;
          const dirty = draft !== committed;
          return (
            <div key={change.id} className="rounded-2xl border border-black/10 bg-surface p-4">
              <div className="mb-2 flex items-center justify-between gap-2">
                <p className="font-mono text-[10px] font-bold uppercase tracking-widest text-brand">
                  {section.company}
                  {section.title ? ` · ${section.title}` : ''}
                </p>
                <div className="flex items-center gap-2">
                  {dirty && (
                    <span className="rounded-full bg-brand-soft px-2 py-0.5 font-mono text-[9px] font-bold uppercase tracking-wider text-brand">
                      Unsaved
                    </span>
                  )}
                  <span className={`rounded-full px-2.5 py-0.5 text-[9px] uppercase tracking-wider ${STATUS_STYLES[bullet.status ?? ''] ?? 'bg-black/[0.05] text-muted'}`}>
                    {bullet.status ?? '—'}
                  </span>
                </div>
              </div>
              <textarea
                value={draft}
                onChange={(e) => setEdits({ ...edits, [change.id]: e.target.value })}
                rows={3}
                className="w-full rounded-lg border border-black/15 bg-white p-2.5 text-xs leading-relaxed text-foreground focus:border-brand focus:outline-none"
              />
              <div className="mt-2 flex items-center justify-between">
                {bullet.status === 'PENDING' ? (
                  <span className="text-[10px] font-mono text-faint">Unresolved — edit and save to resolve it.</span>
                ) : (
                  <span className="text-[10px] font-mono text-faint">Already {bullet.status?.toLowerCase()}.</span>
                )}
                <div className="flex items-center gap-2">
                  {dirty && (
                    <button
                      onClick={() => {
                        const next = { ...edits };
                        delete next[change.id];
                        setEdits(next);
                      }}
                      disabled={busy !== null}
                      className="rounded-lg border border-black/15 px-3 py-1.5 text-xs font-semibold text-muted transition hover:text-foreground"
                    >
                      Reset
                    </button>
                  )}
                  <button
                    onClick={() => onAct(change, 'edit', draft.trim())}
                    disabled={busy !== null || draft.trim() === ''}
                    className="inline-flex items-center gap-1 rounded-lg bg-brand px-3 py-1.5 text-xs font-semibold text-white hover:bg-brand-hover disabled:opacity-50"
                  >
                    <Check className="h-3 w-3" /> Save edit
                  </button>
                </div>
              </div>
            </div>
          );
        })}
      </div>

      <div className="xl:sticky xl:top-20">
        <div className="mb-2 flex items-center justify-between px-1">
          <p className="font-mono text-[10px] font-bold uppercase tracking-widest text-brand">Live preview</p>
          {dirtyCount > 0 && (
            <span className="font-mono text-[9px] text-faint">{dirtyCount} unsaved change(s)</span>
          )}
        </div>
        <DocumentPage doc={previewDoc} />
      </div>
    </div>
  );
}

function isNewBullet(bullet: DocBullet): boolean {
  return bullet.originalText.trim() === '';
}

function EmptyState({ label }: { label: string }) {
  return (
    <div className="rounded-2xl border border-dashed border-black/20 p-10 text-center">
      <p className="font-mono text-[10px] uppercase tracking-widest text-faint">{label}</p>
    </div>
  );
}
