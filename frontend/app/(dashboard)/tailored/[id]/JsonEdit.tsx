'use client';

import { useEffect, useMemo, useState } from 'react';
import { ApiError, editTailoredDocument, type TailoredResume } from '../../../../lib/api';
import type { StructuredResume } from '../types';
import type { DocModel } from '../../../../lib/resumeModel';
import { buildDocumentModel } from '../../../../lib/resumeModel';
import { Alert, ToolbarButton, editorTextareaClass } from '../../../../components/ui';
import { Loader2, Save, Wand2 } from 'lucide-react';

interface Props {
  tailoredId: string;
  initial: StructuredResume;
  onSaved: (updated: TailoredResume) => void;
  /** Live-preview feed: reports the parsed document model, or null while invalid. */
  onPreviewChange?: (doc: DocModel | null) => void;
}

export default function JsonEdit({ tailoredId, initial, onSaved, onPreviewChange }: Props) {
  const [text, setText] = useState(() => JSON.stringify(initial, null, 2));
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [saved, setSaved] = useState(false);

  const parsed = useMemo(() => {
    if (text.trim() === '') return { ok: false as const, reason: 'Document is empty.' };
    try {
      return { ok: true as const, value: JSON.parse(text) as unknown };
    } catch (err) {
      return { ok: false as const, reason: `Invalid JSON: ${err instanceof Error ? err.message : String(err)}` };
    }
  }, [text]);

  useEffect(() => {
    onPreviewChange?.(
      parsed.ok ? buildDocumentModel(parsed.value as StructuredResume, null, []) : null,
    );
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [parsed]);

  async function save() {
    if (!parsed.ok) return;
    setSaving(true);
    setError(null);
    setSaved(false);
    try {
      const updated = await editTailoredDocument(tailoredId, parsed.value);
      setSaved(true);
      onSaved(updated);
    } catch (err) {
      setError(err instanceof ApiError ? err.detail ?? err.message : String(err));
    } finally {
      setSaving(false);
    }
  }

  function format() {
    if (!parsed.ok) return;
    setText(JSON.stringify(parsed.value, null, 2));
    setError(null);
  }

  return (
    <div>
      <div className="mb-3">
        <div className="flex flex-wrap items-center justify-end gap-2">
          <ToolbarButton onClick={format} disabled={!parsed.ok} size="sm">
            <Wand2 className="h-3.5 w-3.5" />
            <span>Format</span>
          </ToolbarButton>
          <ToolbarButton onClick={save} disabled={saving || !parsed.ok} variant="primary" size="sm">
            {saving ? <Loader2 className="h-3.5 w-3.5 animate-spin" /> : <Save className="h-3.5 w-3.5" />}
            <span>{saving ? 'Saving…' : 'Save document'}</span>
          </ToolbarButton>
        </div>
      </div>

      {!parsed.ok && (
        <Alert tone="error" size="sm" className="mb-3">
          {parsed.reason}
        </Alert>
      )}
      {error && (
        <Alert tone="error" size="sm" className="mb-3">
          {error}
        </Alert>
      )}
      {saved && (
        <Alert tone="success" size="sm" className="mb-3">
          Document saved — the viewer, exports and validation now use your edited content.
        </Alert>
      )}

      <textarea
        value={text}
        onChange={(e) => setText(e.target.value)}
        spellCheck={false}
        rows={24}
        className={editorTextareaClass}
        aria-label="Tailored document JSON"
      />
    </div>
  );
}
