'use client';

import { useState, type ReactNode } from 'react';
import type { TailoredChange, ReviewBusy } from '../types';
import type { DocModel, DocSection, DocBullet } from '../../../../lib/resumeModel';
import { rewrittenBullets } from '../../../../lib/resumeModel';
import DiffText from '../../../../components/DiffText';
import { EmptyState } from '../../../../components/ui';
import { ResumePreview, ResumeShell } from './ResumePreview';
import {
  Check,
  Edit3,
  RefreshCw,
  HelpCircle,
  ChevronDown,
  ChevronRight,
  Sparkles,
} from 'lucide-react';

interface Props {
  doc: DocModel | null;
  busy: ReviewBusy | null;
  tab: Tab;
  onTabChange: (tab: Tab) => void;
  onAct: (change: TailoredChange, action: string, newText?: string) => void;
  onTrace: (change: TailoredChange) => void;
}

type Tab = 'document' | 'changes';

const STATUS_STYLES: Record<string, string> = {
  PENDING: 'bg-coral-soft text-coral font-mono font-bold',
  ACCEPTED: 'bg-proof text-proof-ink font-mono font-bold',
  EDITED: 'bg-brand-soft text-brand font-mono font-bold',
  REGENERATED: 'bg-brand-soft text-brand font-mono font-bold',
  REJECTED: 'bg-black/[0.06] text-muted font-mono font-semibold',
};

export function rewrittenChangeCount(doc: DocModel | null): number {
  return doc ? rewrittenBullets(doc).length : 0;
}

export default function DocumentViewer({ doc, busy, tab, onTabChange, onAct, onTrace }: Props) {
  const [editingId, setEditingId] = useState<string | null>(null);
  const [draft, setDraft] = useState('');
  const [openBullet, setOpenBullet] = useState<string | null>(null);

  return (
    <div className="rounded-[24px] border border-black/10 bg-white p-4 sm:p-6 shadow-xs">
      {tab === 'document' && <ResumePreview doc={doc} />}
      {tab === 'changes' && (
        <ChangesReview
          doc={doc}
          busy={busy}
          onAct={onAct}
          onTrace={onTrace}
          editingId={editingId}
          setEditingId={setEditingId}
          draft={draft}
          setDraft={setDraft}
          openBullet={openBullet}
          setOpenBullet={setOpenBullet}
        />
      )}
    </div>
  );
}

// ---------------------------------------------------------------------------
// Changes tab — the same resume view with inline tracked changes
// (green = added, struck-through = removed, ★ = new bullet) and inline
// review controls on every changed bullet.
// ---------------------------------------------------------------------------

function ChangesReview({
  doc,
  busy,
  onAct,
  onTrace,
  editingId,
  setEditingId,
  draft,
  setDraft,
  openBullet,
  setOpenBullet,
}: {
  doc: DocModel | null;
  busy: ReviewBusy | null;
  onAct: (change: TailoredChange, action: string, newText?: string) => void;
  onTrace: (change: TailoredChange) => void;
  editingId: string | null;
  setEditingId: (id: string | null) => void;
  draft: string;
  setDraft: (v: string) => void;
  openBullet: string | null;
  setOpenBullet: (id: string | null) => void;
}) {
  if (!doc) {
    return <EmptyState label="No resume document available." />;
  }
  return (
    <div>
      <div className="mb-3 flex flex-wrap items-center gap-2 px-1">
        <span className="inline-flex items-center gap-1 rounded-md bg-brand/10 px-2 py-1 font-mono text-[9px] font-bold text-brand">
          <span className="rounded-[3px] bg-brand/20 px-1">+ added</span>
        </span>
        <span className="rounded-md bg-black/[0.05] px-2 py-1 font-mono text-[9px] text-muted line-through decoration-black/30">
          removed
        </span>
        <span className="inline-flex items-center gap-1 rounded-md bg-brand/10 px-2 py-1 font-mono text-[9px] font-bold text-brand">
          <Sparkles className="h-2.5 w-2.5" />
          new bullet
        </span>
        <span className="ml-auto hidden text-[10px] text-faint sm:inline">
          Click a highlighted bullet to review it.
        </span>
      </div>
      <ResumeShell
        doc={doc}
        renderBullet={(section, bullet) => {
          const change = bullet.change;
          const open = change !== null && openBullet === change.id;
          return (
            <DiffBullet
              section={section}
              bullet={bullet}
              open={open}
              onToggle={() => setOpenBullet(change === null ? null : open ? null : change.id)}
              busy={busy}
              onAct={onAct}
              onTrace={onTrace}
              editing={editingId !== null && change !== null && editingId === change.id}
              setEditing={setEditingId}
              draft={draft}
              setDraft={setDraft}
            />
          );
        }}
      />
    </div>
  );
}

function DiffBullet({
  section,
  bullet,
  open,
  onToggle,
  busy,
  onAct,
  onTrace,
  editing,
  setEditing,
  draft,
  setDraft,
}: {
  section: DocSection;
  bullet: DocBullet;
  open: boolean;
  onToggle: () => void;
  busy: ReviewBusy | null;
  onAct: (change: TailoredChange, action: string, newText?: string) => void;
  onTrace: (change: TailoredChange) => void;
  editing: boolean;
  setEditing: (id: string | null) => void;
  draft: string;
  setDraft: (v: string) => void;
}) {
  const change = bullet.change;
  const hasChange = change !== null;
  const isNew = isNewBullet(bullet);

  return (
    <span className="flex items-start gap-1.5">
      <span className="mt-[0.35em] h-[3px] w-[3px] shrink-0 rounded-full bg-foreground/70" />
      <span className="min-w-0 flex-1">
        <button
          type="button"
          onClick={hasChange ? onToggle : undefined}
          title={hasChange ? (open ? 'Hide review controls' : 'Show review controls') : undefined}
          className={`block w-full text-left ${
            hasChange ? '-mx-1 rounded-md px-1 transition hover:bg-brand-soft/50' : ''
          }`}
        >
          {hasChange && (
            <span className="mr-1 inline-block -translate-y-px align-middle">
              {open ? (
                <ChevronDown className="h-3 w-3 text-brand" />
              ) : (
                <ChevronRight className="h-3 w-3 text-faint" />
              )}
            </span>
          )}
          {isNew ? (
            <span className="rounded-[3px] bg-brand/10 font-semibold text-brand">{bullet.effectiveText}</span>
          ) : hasChange && bullet.status === 'REJECTED' ? (
            <span className="text-muted">{bullet.effectiveText}</span>
          ) : hasChange ? (
            <DiffText original={bullet.originalText} tailored={bullet.effectiveText} mode="full" />
          ) : (
            <span className="text-foreground">{bullet.effectiveText}</span>
          )}
        </button>

        {hasChange && open && (
          <div className="mt-1.5 rounded-xl border border-black/10 bg-surface p-3">
            <div className="flex flex-wrap items-center gap-2">
              <span
                className={`rounded-full px-2.5 py-0.5 text-[8px] uppercase tracking-wider ${
                  STATUS_STYLES[bullet.status ?? ''] ?? 'bg-black/[0.05] text-muted'
                }`}
              >
                {bullet.status ?? '—'}
              </span>
              {isNew && (
                <span className="inline-flex items-center gap-1 rounded-full bg-brand px-2.5 py-0.5 text-[8px] font-bold uppercase tracking-wider text-white">
                  <Sparkles className="h-2.5 w-2.5" />
                  New bullet
                </span>
              )}
              {bullet.claimCategory && (
                <span className="rounded-md bg-black/[0.05] px-2 py-0.5 font-mono text-[9px] text-muted">
                  {bullet.claimCategory}
                </span>
              )}
              {bullet.reason && (
                <span className="max-w-[220px] truncate font-mono text-[9px] text-faint">{bullet.reason}</span>
              )}
            </div>

            {editing ? (
              <div className="mt-2">
                <textarea
                  value={draft}
                  onChange={(e) => setDraft(e.target.value)}
                  rows={3}
                  className="w-full rounded-lg border border-black/15 bg-white p-2.5 text-xs text-foreground focus:border-brand focus:outline-none"
                />
                <div className="mt-2 flex items-center justify-end gap-2">
                  <button
                    onClick={() => setEditing(null)}
                    className="rounded-lg border border-black/15 px-3 py-1 text-xs font-semibold text-muted hover:text-foreground"
                  >
                    Cancel
                  </button>
                  <button
                    onClick={() => onAct(change, 'edit', draft.trim())}
                    disabled={busy !== null || draft.trim() === ''}
                    className="inline-flex items-center gap-1 rounded-lg bg-brand px-3 py-1 text-xs font-semibold text-white hover:bg-brand-hover disabled:opacity-50"
                  >
                    <Check className="h-3 w-3" /> Save
                  </button>
                </div>
              </div>
            ) : (
              <div className="mt-2 flex flex-wrap items-center gap-1.5 border-t border-black/10 pt-2">
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
                    setEditing(change.id);
                    setDraft(bullet.effectiveText);
                  }}
                  disabled={busy !== null}
                  className="hover:text-foreground hover:bg-white"
                >
                  <Edit3 className="h-3 w-3" /> Re-edit
                </ActionBtn>
                <button
                  onClick={() => onTrace(change)}
                  className="inline-flex items-center gap-1 rounded-lg bg-brand-soft px-3 py-1 font-mono text-[9px] font-bold text-brand hover:bg-brand hover:text-white transition"
                >
                  <HelpCircle className="h-3 w-3" />
                  <span>Why?</span>
                </button>
              </div>
            )}
          </div>
        )}
      </span>
    </span>
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
  children: ReactNode;
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
