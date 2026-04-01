'use client';

import { useEffect, useMemo, useState } from 'react';
import type { DocModel } from '../../../../lib/resumeModel';
import { buildDocumentModel, parseLatex, toLatex } from '../../../../lib/resumeModel';
import { ToolbarButton, editorTextareaClass } from '../../../../components/ui';
import { Check, ClipboardCopy, Download, RotateCcw } from 'lucide-react';

interface Props {
  tailoredId: string;
  initial: DocModel | null;
  /** Live-preview feed: reports the parsed document model, or null while the LaTeX can't be parsed. */
  onPreviewChange?: (doc: DocModel | null) => void;
}

export default function LatexEdit({ tailoredId, initial, onPreviewChange }: Props) {
  const [text, setText] = useState(() => toLatex(initial));
  const [copied, setCopied] = useState(false);

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

  function download() {
    const blob = new Blob([text], { type: 'application/x-tex;charset=utf-8' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `tailored-resume-${tailoredId}.tex`;
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
          <ToolbarButton onClick={download} variant="primary" size="sm">
            <Download className="h-3.5 w-3.5" />
            <span>Download .tex</span>
          </ToolbarButton>
        </div>
      </div>

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
