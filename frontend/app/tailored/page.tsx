import Link from 'next/link';
import TailoredLookupForm from './TailoredLookupForm';
import { ArrowLeft } from 'lucide-react';

export default function TailoredIndex() {
  return (
    <div className="mx-auto max-w-5xl px-4 py-10 sm:px-6">
      <Link
        href="/"
        className="inline-flex items-center gap-1.5 text-xs font-semibold text-muted hover:text-foreground transition"
      >
        <ArrowLeft className="h-3.5 w-3.5" />
        <span>Back to Home</span>
      </Link>

      <div className="mt-6 border-b border-black/10 pb-6">
        <p className="font-mono text-xs font-semibold uppercase tracking-[0.2em] text-brand">
          04 / SYNTHESIS & REWRITES
        </p>
        <h1 className="mt-1 text-3xl font-bold tracking-[-0.04em]">Tailored Resumes</h1>
        <p className="mt-1 text-sm text-muted">
          Review role-optimized rewrites, inspect evidence-based claims, and approve final exports.
        </p>
      </div>

      <TailoredLookupForm />
    </div>
  );
}