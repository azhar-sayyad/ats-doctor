'use client';

import Link from 'next/link';
import { useCallback, useEffect, useState } from 'react';
import { ApiError, editMasterResume, getCurrentResume, type ResumeVersion } from '../../../../lib/api';
import type { StructuredResume, TailoredResume } from '../../tailored/types';
import { parseStructured, buildDocumentModel, toDocument } from '../../../../lib/resumeModel';
import EditWorkspace from '../../tailored/[id]/EditWorkspace';
import { ArrowLeft, Loader2, AlertCircle } from 'lucide-react';

const PROCESSING = new Set(['UPLOADED', 'EXTRACTING', 'PARSING']);

export default function ResumeEditPage() {
  const [version, setVersion] = useState<ResumeVersion | null>(null);
  const [draft, setDraft] = useState<StructuredResume | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    getCurrentResume()
      .then((v) => {
        setVersion(v);
        setDraft(parseStructured(v.structured_data));
      })
      .catch((err) => {
        setError(err instanceof ApiError ? err.detail ?? err.message : String(err));
      })
      .finally(() => setLoading(false));
  }, []);

  const persistEdits = useCallback(
    async (payload: StructuredResume): Promise<ResumeVersion> => {
      if (!version) throw new Error('No master resume loaded.');
      return editMasterResume(version.id, payload);
    },
    [version],
  );

  const handleDocumentSaved = useCallback((updated: TailoredResume) => {
    const persisted = updated as unknown as ResumeVersion | null;
    if (persisted && persisted.id) {
      setVersion(persisted);
      setDraft(parseStructured(persisted.structured_data));
    }
  }, []);

  const ready = version?.state === 'READY';
  const processing = version ? PROCESSING.has(version.state) : false;

  return (
    <div className="mx-auto max-w-7xl px-4 py-10 sm:px-6 lg:px-8">
      <Link
        href="/resume"
        className="inline-flex items-center gap-1.5 font-mono text-xs font-semibold uppercase tracking-[0.2em] text-muted transition hover:text-brand"
      >
        <ArrowLeft className="h-3.5 w-3.5" />
        Master Resume
      </Link>

      <div className="mb-8 mt-3 border-b border-black/10 pb-6">
        <h1 className="mt-1 text-3xl font-bold tracking-[-0.04em]">Edit Master Resume</h1>
        <p className="mt-1 text-sm text-muted">
          Edit in Structured, LaTeX or JSON mode — the live preview updates as you type.
        </p>
      </div>

      {error && (
        <div className="mb-6 flex items-center gap-2 rounded-xl border border-coral/30 bg-coral-soft p-4 text-sm font-medium text-coral">
          <AlertCircle className="h-4 w-4 shrink-0" />
          <div className="min-w-0">
            <span>{error}</span>
            {error.toLowerCase().includes('404') || error.toLowerCase().includes('not found') ? (
              <Link href="/resume" className="ml-2 font-semibold underline decoration-coral/40 underline-offset-2 hover:decoration-coral">
                Upload a resume first →
              </Link>
            ) : null}
          </div>
        </div>
      )}

      {loading && (
        <div className="flex items-center justify-center gap-2 rounded-[24px] border border-black/10 bg-surface p-12 font-mono text-xs text-brand">
          <Loader2 className="h-4 w-4 animate-spin" />
          <span>Loading master resume…</span>
        </div>
      )}

      {version && processing && (
        <div className="rounded-[24px] border border-black/10 bg-surface p-12 text-center">
          <Loader2 className="mx-auto h-6 w-6 animate-spin text-brand" />
          <p className="mt-3 text-sm font-semibold text-foreground">
            Resume is still {version.state.toLowerCase()} — wait for parsing to finish.
          </p>
          <Link href="/resume" className="mt-2 inline-block font-mono text-xs font-bold uppercase tracking-widest text-brand">
            ← Back to Master Resume
          </Link>
        </div>
      )}

      {version && version.state === 'FAILED' && (
        <div className="rounded-[24px] border border-coral/20 bg-coral-soft p-8 text-center">
          <p className="text-sm font-semibold text-coral">
            {version.error ?? 'This resume failed to parse — re-upload it to continue.'}
          </p>
          <Link href="/resume" className="mt-2 inline-block font-mono text-xs font-bold uppercase tracking-widest text-brand">
            ← Back to Master Resume
          </Link>
        </div>
      )}

      {ready && version && draft && (
        <EditWorkspace
          docId={version.id}
          doc={buildDocumentModel(draft, null, [])}
          onDocumentSaved={handleDocumentSaved}
          onSaveStructured={persistEdits}
          onSaveLatex={(doc) => persistEdits(toDocument(doc))}
          onSaveJson={persistEdits}
          emptyLabel="Nothing to edit yet — upload a resume from the Master Resume page."
        />
      )}
    </div>
  );
}