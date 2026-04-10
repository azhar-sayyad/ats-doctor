'use client';

import { useEffect, useMemo, useState } from 'react';
import { ApiError, editTailoredDocument, type TailoredResume } from '../../../../lib/api';
import type { DocModel } from '../../../../lib/resumeModel';
import {
  buildDocumentModel,
  parseLatex,
  toDocument,
  toLatex,
} from '../../../../lib/resumeModel';
import { Alert, ToolbarButton, editorTextareaClass } from '../../../../components/ui';
import {
  Check,
  ClipboardCopy,
  Download,
  Loader2,
  RotateCcw,
  Save,
} from 'lucide-react';

interface Props {
  /** Id used by the default persistence (PUT /tailored/{id}/edit) and download filename. */
  docId?: string;
  initial: DocModel | null;
  /** Live-preview feed: reports the parsed document model, or null while the LaTeX can't be parsed. */
  onPreviewChange?: (doc: DocModel | null) => void;
  /** Called with the parsed document after a successful save. */
  onSaved?: (updated: TailoredResume) => void;
  /** Custom persistence; overrides the default tailored-resume endpoint. */
  onSave?: (doc: DocModel) => Promise<unknown>;
}

export default function LatexEdit({ docId, initial, onPreviewChange, onSaved, onSave }: Props) {
  const [text, setText] = useState(() => toLatex(initial));
  const [copied, setCopied] = useState(false);
  const [saving, setSaving] = useState(false);
  const [saved, setSaved] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const original = useMemo(() => toLatex(initial), [initial]);
  const dirty = text !== original;

  const preview = useMemo(() => {
    const parsed = parseLatex(text);
    return parsed ? buildDocumentModel(parsed, null, []) : null;
  }, [text]);

  useEffect(() => {
    onPreviewChange?.(preview);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [preview]);

  async function save() {
    if (!preview) {
      setError('LaTeX is not valid — fix the highlighted errors before saving.');
      return;
    }
    setSaving(true);
    setError(null);
    setSaved(false);
    try {
      const updated = onSave ? await onSave(preview) : await editTailoredDocument(docId!, toDocument(preview));
      setSaved(true);
      onSaved?.(updated as TailoredResume);
    } catch (err) {
      setError(err instanceof ApiError ? err.detail ?? err.message : String(err));
    } finally {
      setSaving(false);
    }
  }

  function download() {
    const blob = new Blob([text], { type: 'application/x-tex;charset=utf-8' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `resume-${docId ?? 'master'}.tex`;
    a.click();
    URL.revokeObjectURL(url);
  }

  async function copy() {
    try {
      await navigator.clipboard.writeText(text);
      setCopied(true);
      setTimeout(() => setCopied(false), 1500);
    } catch {
      // clipboard unavailable — ignore
    }
  }

  return (
    <div>
      <div className="mb-3">
        <div className="flex flex-wrap items-center justify-end gap-2">
          <ToolbarButton onClick={() => setText(original)} disabled={!dirty} size="sm">
            <RotateCcw className="h-3.5 w-3.5" />
            <span>Regenerate</span>
          </ToolbarButton>
          <ToolbarButton onClick={copy} size="sm">
            {copied ? <Check className="h-3.5 w-3.5 text-brand" /> : <ClipboardCopy className="h-3.5 w-3.5" />}
            <span>{copied ? 'Copied' : 'Copy'}</span>
          </ToolbarButton>
          <ToolbarButton onClick={download} size="sm">
            <Download className="h-3.5 w-3.5" />
            <span>Download .tex</span>
          </ToolbarButton>
          <ToolbarButton onClick={save} disabled={saving || !preview} variant="primary" size="sm">
            {saving ? <Loader2 className="h-3.5 w-3.5 animate-spin" /> : <Save className="h-3.5 w-3.5" />}
            <span>{saving ? 'Saving…' : 'Save & Apply'}</span>
          </ToolbarButton>
        </div>
      </div>

      {error && (
        <Alert tone="error" size="sm" className="mb-3">
          {error}
        </Alert>
      )}
      {saved && (
        <Alert tone="success" size="sm" className="mb-3">
          Document saved — the viewer and exports now use your edited content.
        </Alert>
      )}

      <textarea
        value={text}
        onChange={(e) => setText(e.target.value)}
        spellCheck={false}
        rows={24}
        className={editorTextareaClass}
        aria-label="Tailored document LaTeX"
      />
    </div>
  );
}
