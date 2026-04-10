'use client';

import { useRouter } from 'next/navigation';
import { X, PenLine } from 'lucide-react';
import type { DocModel } from '../../../lib/resumeModel';
import { ResumePreview } from '../tailored/[id]/ResumePreview';

interface Props {
  doc: DocModel | null;
  onClose: () => void;
}

export default function PreviewModal({ doc, onClose }: Props) {
  const router = useRouter();

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center bg-foreground/50 p-4 backdrop-blur-xs"
      onClick={onClose}
    >
      <div
        className="flex max-h-[90vh] w-full max-w-5xl flex-col rounded-[28px] border border-black/10 bg-white shadow-2xl"
        onClick={(e) => e.stopPropagation()}
      >
        <div className="flex items-start justify-between gap-4 border-b border-black/10 p-6 pb-4">
          <div>
            <p className="font-mono text-[10px] font-bold uppercase tracking-widest text-brand">
              Master Resume · preview only
            </p>
            <h3 className="mt-1 text-xl font-bold tracking-tight text-foreground">Resume Preview</h3>
          </div>
          <button
            onClick={onClose}
            className="rounded-lg border border-black/10 p-1.5 text-faint transition hover:border-black/20 hover:text-foreground"
            aria-label="Close preview"
          >
            <X className="h-4 w-4" />
          </button>
        </div>

        <div className="min-h-0 flex-1 overflow-auto p-6">
          <ResumePreview doc={doc} />
        </div>

        <div className="flex items-center justify-end gap-2 border-t border-black/10 p-4">
          <button
            onClick={onClose}
            className="rounded-lg border border-black/15 px-4 py-2 text-xs font-semibold text-muted transition hover:text-foreground"
          >
            Close
          </button>
          <button
            onClick={() => router.push('/resume/edit')}
            className="inline-flex items-center gap-1.5 rounded-lg bg-foreground px-5 py-2 text-xs font-bold text-white transition hover:bg-brand"
          >
            <PenLine className="h-3.5 w-3.5" />
            Edit instead
          </button>
        </div>
      </div>
    </div>
  );
}