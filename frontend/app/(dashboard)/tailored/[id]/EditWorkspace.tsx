'use client';

import { useEffect, useState } from 'react';
import type { TailoredResume } from '../types';
import { EmptyState } from '../../../../components/ui';
import type { DocModel } from '../../../../lib/resumeModel';
import { toDocument } from '../../../../lib/resumeModel';
import { ResumePreview } from './ResumePreview';
import TailoredEditor from './TailoredEditor';
import LatexEdit from './LatexEdit';
import JsonEdit from './JsonEdit';
import { Braces, FileCode2, FileEdit } from 'lucide-react';

type EditMode = 'structured' | 'latex' | 'json';

interface Props {
  tailoredId: string;
  doc: DocModel | null;
  onDocumentSaved: (updated: TailoredResume) => void;
}

const EDIT_MODES: { key: EditMode; label: string; icon: typeof FileEdit }[] = [
  { key: 'structured', label: 'Structured', icon: FileEdit },
  { key: 'latex', label: 'LaTeX', icon: FileCode2 },
  { key: 'json', label: 'JSON', icon: Braces },
];

export default function EditWorkspace({ tailoredId, doc, onDocumentSaved }: Props) {
  const [mode, setMode] = useState<EditMode>('structured');
  const [preview, setPreview] = useState<DocModel | null>(doc);

  useEffect(() => {
    setPreview(doc);
  }, [mode, doc]);

  return (
    <div>
      {!doc ? (
        <EmptyState label="No document to edit yet. Bullets appear here after the tailoring run." />
      ) : (
        <div className="grid grid-cols-1 gap-6 lg:grid-cols-[minmax(0,1fr)_420px]">
          {/* Left — mode selector + editor */}
          <div>
            <div className="mb-4 flex items-center gap-1 border-b border-black/10">
              {EDIT_MODES.map(({ key, label, icon: Icon }) => (
                <button
                  key={key}
                  onClick={() => setMode(key)}
                  className={`inline-flex items-center gap-1.5 rounded-t-lg px-3.5 py-2 text-xs font-semibold transition ${
                    mode === key
                      ? 'bg-brand-soft text-brand border-b-2 border-brand'
                      : 'text-faint hover:text-foreground'
                  }`}
                >
                  <Icon className="h-3.5 w-3.5" />
                  {label}
                </button>
              ))}
            </div>

            {mode === 'structured' && (
              <TailoredEditor
                tailoredId={tailoredId}
                initial={toDocument(doc)}
                onSaved={onDocumentSaved}
                onPreviewChange={setPreview}
              />
            )}
            {mode === 'latex' && <LatexEdit tailoredId={tailoredId} initial={doc} onPreviewChange={setPreview} />}
            {mode === 'json' && (
              <JsonEdit
                tailoredId={tailoredId}
                initial={toDocument(doc)}
                onSaved={onDocumentSaved}
                onPreviewChange={setPreview}
              />
            )}
          </div>

          {/* Right — sticky live preview */}
          <div className="lg:sticky lg:top-8 lg:self-start">
            <div className="mb-2 flex items-center justify-between px-1">
              <p className="font-mono text-[10px] font-bold uppercase tracking-widest text-brand">Live preview</p>
              {preview === null && (
                <span className="font-mono text-[9px] text-coral">preview unavailable</span>
              )}
            </div>
            {preview ? (
              <ResumePreview doc={preview} />
            ) : (
              <EmptyState label="Preview unavailable — check the JSON validity of your document." />
            )}
          </div>
        </div>
      )}
    </div>
  );
}
