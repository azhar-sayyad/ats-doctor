'use client';

import { useRouter } from 'next/navigation';
import { useState } from 'react';
import { FileCheck, ArrowRight } from 'lucide-react';

export default function TailoredLookupForm() {
  const router = useRouter();
  const [id, setId] = useState('');
  const [error, setError] = useState(false);

  function open() {
    const trimmed = id.trim();
    if (!trimmed) {
      setError(true);
      return;
    }
    setError(false);
    router.push(`/tailored/${trimmed}`);
  }

  return (
    <div className="mt-8 rounded-[24px] border border-black/10 bg-surface p-6 sm:p-8 shadow-xs">
      <h2 className="text-lg font-bold tracking-tight text-foreground flex items-center gap-2">
        <FileCheck className="h-5 w-5 text-brand" />
        Inspect Tailored Resume Run
      </h2>
      <p className="mt-1 text-sm text-muted">
        Enter a tailored resume ID to inspect rewrites, evidence tracing, diffs, and export final PDF/DOCX versions.
      </p>
      <div className="mt-5 flex flex-col sm:flex-row gap-3">
        <input
          value={id}
          onChange={(e) => setId(e.target.value)}
          onKeyDown={(e) => e.key === 'Enter' && open()}
          placeholder="e.g. 7c9e6679-7425-40de-944b-e07fc1f90ae7"
          className="w-full max-w-lg rounded-xl border border-black/15 bg-white px-3.5 py-2.5 font-mono text-xs text-foreground placeholder:text-faint focus:border-brand focus:outline-none focus:ring-1 focus:ring-brand transition"
        />
        <button
          onClick={open}
          className="inline-flex items-center justify-center gap-2 rounded-[10px] bg-foreground px-5 py-2.5 text-sm font-semibold text-white transition hover:bg-brand hover:-translate-y-0.5"
        >
          <span>Inspect Resume</span>
          <ArrowRight className="h-4 w-4" />
        </button>
      </div>
      {error && <p className="mt-2 text-xs font-semibold text-coral">Please paste a valid tailored resume ID first.</p>}
    </div>
  );
}