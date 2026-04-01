'use client';

import { useEffect, useState } from 'react';
import { ApiError, editTailoredDocument, type TailoredResume } from '../../../../lib/api';
import type { StructuredResume } from '../types';
import type { DocModel } from '../../../../lib/resumeModel';
import { buildDocumentModel, cloneResume } from '../../../../lib/resumeModel';
import ResumeForm from '../../../../components/ResumeForm';
import { Alert, ToolbarButton } from '../../../../components/ui';
import { Loader2, Save, X } from 'lucide-react';

interface Props {
  tailoredId: string;
  initial: StructuredResume;
  onSaved: (updated: TailoredResume) => void;
  /** Live-preview feed: reports the document model of the current draft. */
  onPreviewChange?: (doc: DocModel | null) => void;
}

export default function TailoredEditor({ tailoredId, initial, onSaved, onPreviewChange }: Props) {
  const [draft, setDraft] = useState<StructuredResume>(() => cloneResume(initial));
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [saved, setSaved] = useState(false);

  const patch = (updater: (d: StructuredResume) => void) =>
    setDraft((prev) => {
      const next = cloneResume(prev);
      updater(next);
      return next;
    });

  function reset() {
    setDraft(cloneResume(initial));
    setError(null);
    setSaved(false);
  }

  useEffect(() => {
    onPreviewChange?.(buildDocumentModel(draft, null, []));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [draft]);

  async function save() {
    setSaving(true);
    setError(null);
    setSaved(false);
    try {
      const updated = await editTailoredDocument(tailoredId, draft);
      setSaved(true);
      onSaved(updated);
    } catch (err) {
      setError(err instanceof ApiError ? err.detail ?? err.message : String(err));
    } finally {
      setSaving(false);
    }
  }

  return (
    <div className="rounded-[24px] border border-black/10 bg-white p-6 sm:p-8 shadow-xs">
      <div className="mb-6 border-b border-black/10 pb-5">
        <div className="flex flex-wrap items-center justify-end gap-2">
          <ToolbarButton onClick={reset} disabled={saving}>
            <X className="h-3.5 w-3.5" />
            <span>Cancel</span>
          </ToolbarButton>
          <ToolbarButton variant="primary" onClick={save} disabled={saving}>
            {saving ? <Loader2 className="h-3.5 w-3.5 animate-spin" /> : <Save className="h-3.5 w-3.5" />}
            <span>{saving ? 'Saving…' : 'Save & Apply'}</span>
          </ToolbarButton>
        </div>
      </div>

      {error && (
        <Alert tone="error" className="mb-4">
          {error}
        </Alert>
      )}
      {saved && (
        <Alert tone="success" className="mb-4">
          Document saved — the viewer and exports now use your edited content.
        </Alert>
      )}

      <ResumeForm draft={draft} patch={patch} />
    </div>
  );
}
